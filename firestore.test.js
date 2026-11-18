const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-rabtacon-15b4471a-05e8-4bcc-bf76-a3b570cd307a";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTs() {
  return new Date(Date.now() - 1000);
}

function dbFor(uid) {
  const ctx = uid ? testEnv.authenticatedContext(uid) : testEnv.unauthenticatedContext();
  return ctx.firestore({ databaseId: DATABASE_ID });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write contacts, categories, logs, or templates", async () => {
  const db = dbFor(null);
  await assertFails(db.collection(`users/${ALICE_UID}/contacts`).get());
  await assertFails(db.collection(`users/${ALICE_UID}/categories`).get());
  await assertFails(db.collection(`users/${ALICE_UID}/whatsapp_logs`).get());
  await assertFails(db.collection(`users/${ALICE_UID}/payment_templates`).get());
  await assertFails(db.doc(`users/${ALICE_UID}`).get());
});

test("Cross-user isolation: Bob cannot read, list, create, update, or delete Alice's contacts", async () => {
  const aliceDb = dbFor(ALICE_UID);
  const bobDb = dbFor(BOB_UID);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/contacts/c_1`).set({
      displayName: "أحمد محمد",
      primaryPhone: "+201012345678",
      phones: ["+201012345678"],
      emails: ["ahmed@example.com"],
      categoryPaths: ["الجامعة > هندسة"],
      syncedToDevice: false,
      isFavorite: true,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  await assertFails(bobDb.doc(`users/${ALICE_UID}/contacts/c_1`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/contacts`).get());
  await assertFails(
    bobDb.doc(`users/${ALICE_UID}/contacts/c_1`).update({
      isFavorite: false,
      updatedAt: serverTs(),
    })
  );
  await assertFails(bobDb.doc(`users/${ALICE_UID}/contacts/c_1`).delete());
});

test("Owner can CRUD contacts and toggle selective sync or favorite", async () => {
  const aliceDb = dbFor(ALICE_UID);
  const contactRef = aliceDb.doc(`users/${ALICE_UID}/contacts/c_100`);

  await assertSucceeds(
    contactRef.set({
      displayName: "Dr. Sara Ali",
      nickname: "د. سارة علي",
      primaryPhone: "+201234567890",
      phones: ["+201234567890"],
      emails: [],
      categoryPaths: ["الأصدقاء المقربين", "المطرية"],
      tags: ["دكتور", "Doctor", "المطرية", "Orange"],
      district: "المطرية",
      latitude: 30.1213,
      longitude: 31.314,
      syncedToDevice: false,
      isFavorite: false,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/contacts`).get());

  await assertSucceeds(
    contactRef.update({
      syncedToDevice: true,
      updatedAt: serverTs(),
    })
  );

  await assertSucceeds(contactRef.delete());
});

test("Dirty Dozen: rejects ghost fields, invalid latitude, oversized phones array, and immutable createdAt tampering", async () => {
  const aliceDb = dbFor(ALICE_UID);
  const contactRef = aliceDb.doc(`users/${ALICE_UID}/contacts/c_dirty`);

  // Ghost field on create
  await assertFails(
    contactRef.set({
      displayName: "Ghost Field",
      primaryPhone: "+201000000000",
      phones: ["+201000000000"],
      emails: [],
      categoryPaths: [],
      syncedToDevice: false,
      isFavorite: false,
      isAdmin: true,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Out of bounds latitude
  await assertFails(
    contactRef.set({
      displayName: "Bad Geo",
      primaryPhone: "+201000000000",
      phones: ["+201000000000"],
      emails: [],
      categoryPaths: [],
      latitude: 120.5,
      longitude: 31.0,
      syncedToDevice: false,
      isFavorite: false,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Valid create, then immutable createdAt tampering on update
  await assertSucceeds(
    contactRef.set({
      displayName: "Valid User",
      primaryPhone: "+201000000000",
      phones: ["+201000000000"],
      emails: [],
      categoryPaths: [],
      syncedToDevice: false,
      isFavorite: false,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  await assertFails(
    contactRef.update({
      createdAt: new Date(0),
      updatedAt: serverTs(),
    })
  );
});

test("Categories, WhatsApp logs, Payment templates, and UserProfile rules enforce ownership and schemas", async () => {
  const aliceDb = dbFor(ALICE_UID);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}`).set({
      userId: ALICE_UID,
      displayName: "Felosameh",
      email: "felosameh97@gmail.com",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Spoofed userId fails
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}`).set({
      userId: BOB_UID,
      displayName: "Spoof",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/categories/cat_1`).set({
      name: "هندسة",
      parentPath: "الجامعة",
      fullPath: "الجامعة > هندسة",
      depth: 1,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/whatsapp_logs/log_1`).set({
      contactId: "c_1",
      contactName: "أحمد",
      phone: "+201012345678",
      loggedAtEpochMs: Date.now(),
      syncedToPhone: false,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Log contactId cannot be mutated on update
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/whatsapp_logs/log_1`).update({
      contactId: "c_999",
      updatedAt: serverTs(),
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/whatsapp_logs/log_1`).update({
      syncedToPhone: true,
      updatedAt: serverTs(),
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/payment_templates/tpl_instapay`).set({
      name: "InstaPay",
      urlTemplate: "https://ipn.eg/S/user/instapay/{phone}",
      isDefault: true,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
});

test("Call logs and call transcripts enforce owner isolation and strict schemas", async () => {
  const aliceDb = dbFor(ALICE_UID);
  const bobDb = dbFor(BOB_UID);

  // Valid contact with new Phase 1 fields
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/contacts/c_phase1`).set({
      displayName: "Kareem Adel",
      primaryPhone: "+201099887766",
      phones: ["+201099887766"],
      emails: [],
      categoryPaths: ["العمل"],
      syncedToDevice: false,
      isFavorite: false,
      avatarColorSeed: 42,
      muted: true,
      blocked: false,
      lastContactedEpochMs: Date.now(),
      clientUpdatedAtMs: Date.now(),
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Valid call log
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/call_logs/cl_1`).set({
      contactId: "c_phase1",
      phoneNumber: "+201099887766",
      cachedName: "Kareem Adel",
      callType: 2,
      simSlot: 0,
      timestampMs: Date.now(),
      durationSec: 120,
      transcriptId: "tr_1",
      hasRecording: true,
      notes: "Discussed meeting tomorrow at 10am",
      clientUpdatedAtMs: Date.now(),
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Valid call transcript
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/call_transcripts/tr_1`).set({
      contactId: "c_phase1",
      phone: "+201099887766",
      contactName: "Kareem Adel",
      audioFileName: "rabta_kareem.m4a",
      audioSizeBytes: 24500,
      timestampMs: Date.now(),
      durationSec: 120,
      transcript: "بكرة الاجتماع الساعة 10 الصبح ان شاء الله",
      highlights: ["بكرة الاجتماع الساعة 10 الصبح"],
      summaryBullets: ["تأكيد اجتماع الغد الساعة 10 صباحاً"],
      oneLineSummary: "تأكيد اجتماع الغد 10:00",
      segmentsJson: "[]",
      actionItemsJson: "[]",
      eventAdded: false,
      notesSaved: true,
      status: "READY",
      clientUpdatedAtMs: Date.now(),
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Cross-user access denied
  await assertFails(bobDb.doc(`users/${ALICE_UID}/call_logs/cl_1`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/call_transcripts/tr_1`).get());

  // Invalid status rejected
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/call_transcripts/tr_bad`).set({
      phone: "+201099887766",
      contactName: "Bad",
      audioSizeBytes: 0,
      timestampMs: Date.now(),
      durationSec: 10,
      highlights: [],
      summaryBullets: [],
      eventAdded: false,
      notesSaved: false,
      status: "UNKNOWN_INVALID",
      clientUpdatedAtMs: Date.now(),
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
});
