package com.example.data.sync

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.ai.LocalTranscriptAnalyzer
import com.example.data.device.DeviceContactsManager
import com.example.data.local.AppCallLogEntity
import com.example.data.local.CallTranscriptEntity
import com.example.data.local.ContactEntity
import com.example.data.local.LocalMediaCacheManager
import com.example.data.local.PendingSyncEntity
import com.example.data.local.RabtaDatabase
import com.example.data.local.SyncPrefsDataStore
import com.example.data.remote.FirestoreContactsRepository
import com.example.domain.model.CallActionItem
import com.example.domain.model.CallTranscriptRecord
import com.example.domain.model.ContactRecord
import com.example.domain.model.SyncEntityType
import com.example.domain.model.SyncOperation
import com.example.domain.model.SyncState
import com.example.domain.model.TranscriptSegment
import com.example.util.PhoneUtils
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineFirstPhase1Test {

    private lateinit var context: Context
    private lateinit var db: RabtaDatabase
    private lateinit var syncEngine: OfflineFirstSyncEngine
    private lateinit var prefs: SyncPrefsDataStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, RabtaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = SyncPrefsDataStore(context)

        val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setApplicationId("com.aistudio.rabtacontacts.k7m2px")
                .setProjectId("demo-no-project")
                .setApiKey("fake-api-key-for-unit-test")
                .build()
        )
        val databaseId = "ai-studio-android-rabtacon-15b4471a-05e8-4bcc-bf76-a3b570cd307a"
        val firestore = FirebaseFirestore.getInstance(app, databaseId).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
        syncEngine = OfflineFirstSyncEngine(
            context = context,
            roomDb = db,
            firestoreRepo = FirestoreContactsRepository(firestore),
            prefs = prefs,
        )
    }

    @After
    fun tearDown() {
        syncEngine.stop()
        db.close()
        LocalMediaCacheManager.recordingsDir(context).deleteRecursively()
        LocalMediaCacheManager.transcriptsCacheDir(context).deleteRecursively()
        context.deleteDatabase("migration_v4_test.db")
    }

    @Test
    fun `sandbox isolation blocks phonebook export and clears synced flags`() = runBlocking<Unit> {
        val deviceContacts = DeviceContactsManager(context)
        val contact = ContactRecord(
            id = "c_isolated",
            displayName = "Ahmed Ali",
            primaryPhone = "+201012345678",
            phones = listOf("+201012345678"),
            syncedToDevice = true,
        )

        val syncRes = deviceContacts.syncContactToPhonebook(contact)
        assertTrue("syncContactToPhonebook must fail in sandbox mode", syncRes.isFailure)

        val removeRes = deviceContacts.removeContactFromPhonebook(contact)
        assertTrue(removeRes.isSuccess)
        assertEquals(false, removeRes.getOrNull())

        val cloudMap = contact.toFirestoreMap(isCreate = true)
        assertEquals(false, cloudMap["syncedToDevice"])

        val entity = ContactEntity.fromDomain(contact)
        assertFalse(entity.syncedToDevice)
        assertFalse(entity.toDomain().syncedToDevice)

        assertTrue(prefs.strictPhonebookIsolationFlow.first())
        assertTrue(db.contactDao().observeSyncedContacts().first().isEmpty())
    }

    @Test
    fun `room v5 migration preserves v4 rows resets syncedToDevice and creates new tables`() {
        val dbName = "migration_v4_test.db"
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `contacts` (
                                `id` TEXT NOT NULL PRIMARY KEY,
                                `displayName` TEXT NOT NULL,
                                `nickname` TEXT,
                                `primaryPhone` TEXT NOT NULL,
                                `phonesJoined` TEXT NOT NULL,
                                `emailsJoined` TEXT NOT NULL,
                                `categoryPathsFlat` TEXT NOT NULL,
                                `tagsJoined` TEXT NOT NULL DEFAULT '',
                                `organization` TEXT,
                                `jobTitle` TEXT,
                                `notes` TEXT,
                                `address` TEXT,
                                `district` TEXT,
                                `latitude` REAL,
                                `longitude` REAL,
                                `telegramHandle` TEXT,
                                `website` TEXT,
                                `photoUri` TEXT,
                                `syncedToDevice` INTEGER NOT NULL DEFAULT 0,
                                `isFavorite` INTEGER NOT NULL DEFAULT 0,
                                `searchBlob` TEXT NOT NULL,
                                `createdAtEpochMs` INTEGER NOT NULL,
                                `updatedAtEpochMs` INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `call_transcripts` (
                                `id` TEXT NOT NULL PRIMARY KEY,
                                `contactId` TEXT,
                                `phone` TEXT NOT NULL,
                                `contactName` TEXT NOT NULL,
                                `audioFilePath` TEXT NOT NULL,
                                `timestampMs` INTEGER NOT NULL,
                                `durationSec` INTEGER NOT NULL,
                                `transcript` TEXT NOT NULL DEFAULT '',
                                `highlightsJoined` TEXT NOT NULL DEFAULT '',
                                `extractedNotes` TEXT NOT NULL DEFAULT '',
                                `suggestedEventTitle` TEXT NOT NULL DEFAULT '',
                                `suggestedEventDateIso` TEXT NOT NULL DEFAULT '',
                                `suggestedEventEpochMs` INTEGER,
                                `suggestedCategory` TEXT NOT NULL DEFAULT '',
                                `eventAddedToCalendar` INTEGER NOT NULL DEFAULT 0,
                                `notesSavedToContact` INTEGER NOT NULL DEFAULT 0,
                                `status` TEXT NOT NULL DEFAULT 'PENDING'
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO contacts (id, displayName, primaryPhone, phonesJoined, emailsJoined, categoryPathsFlat, syncedToDevice, isFavorite, searchBlob, createdAtEpochMs, updatedAtEpochMs)
                            VALUES ('legacy_1', 'Legacy User', '01001112223', '01001112223', '', '', 1, 0, 'legacy user', 1000, 2000)
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO call_transcripts (id, contactId, phone, contactName, audioFilePath, timestampMs, durationSec)
                            VALUES ('tr_legacy', 'legacy_1', '01001112223', 'Legacy User', '/tmp/a.m4a', 5000, 30)
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )

        val sqliteDb = helper.writableDatabase
        RabtaDatabase.MIGRATION_4_5.migrate(sqliteDb)

        sqliteDb.query("SELECT syncedToDevice, isDeleted, syncState FROM contacts WHERE id = 'legacy_1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertEquals(0, c.getInt(1))
            assertEquals("SYNCED", c.getString(2))
        }
        sqliteDb.query("SELECT updatedAtEpochMs FROM call_transcripts WHERE id = 'tr_legacy'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(5000L, c.getLong(0))
        }
        sqliteDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('app_call_logs', 'pending_sync_queue')").use { c ->
            assertEquals(2, c.count)
        }
        helper.close()
    }

    @Test
    fun `daos enforce soft delete filtering outbox coalescing and ignore duplicate call logs`() = runBlocking<Unit> {
        val cDao = db.contactDao()
        val pDao = db.pendingSyncDao()
        val lDao = db.appCallLogDao()

        val contact = ContactRecord(
            id = "c_soft_1",
            displayName = "Mostafa",
            primaryPhone = "+201111222333",
            phones = listOf("+201111222333"),
        )
        cDao.upsertSingleWithIndexes(ContactEntity.fromDomain(contact))
        assertNotNull(cDao.getById("c_soft_1"))

        cDao.softDeleteContactWithIndexes("c_soft_1", 9999L)
        assertNull(cDao.getById("c_soft_1"))
        assertNotNull(cDao.getByIdEvenIfDeleted("c_soft_1"))
        assertTrue(cDao.searchContacts("mostafa", 20, 0).isEmpty())

        val key = PendingSyncEntity.keyFor(SyncEntityType.CONTACT.name, "c_soft_1")
        pDao.enqueue(PendingSyncEntity(key, SyncEntityType.CONTACT.name, "c_soft_1", SyncOperation.UPSERT.name, 100L, 100L))
        pDao.enqueue(PendingSyncEntity(key, SyncEntityType.CONTACT.name, "c_soft_1", SyncOperation.DELETE.name, 200L, 200L))
        assertEquals(1, pDao.getPendingCount())
        assertEquals(SyncOperation.DELETE.name, pDao.getByEntity(SyncEntityType.CONTACT.name, "c_soft_1")?.operation)

        val log1 = AppCallLogEntity(
            id = "cl_dedup_1",
            contactId = null,
            phoneNumber = "01009998877",
            phoneKey = PhoneUtils.phoneSearchKey("01009998877"),
            cachedName = "First",
            callType = 1,
            timestampMs = 10_000L,
            durationSec = 42L,
        )
        val logDup = log1.copy(cachedName = "Second")
        lDao.insertIgnore(listOf(log1))
        lDao.insertIgnore(listOf(logDup))
        val logs = lDao.getRecentCallsOnce(10)
        assertEquals(1, logs.size)
        assertEquals("First", logs.first().cachedName)
    }

    @Test
    fun `conflict resolver respects pending edits simultaneous merge remote updates and tombstones`() {
        val local = ContactEntity.fromDomain(
            ContactRecord(
                id = "c_conf",
                displayName = "Omar Hassan",
                primaryPhone = "+201001112222",
                phones = listOf("+201001112222"),
                categoryPaths = listOf("Work"),
                isFavorite = true,
                clientUpdatedAtMs = 10_000L,
            ),
            nowMs = 10_000L,
            syncState = SyncState.PENDING,
        )

        // 1) Local pending edit beats stale/close remote
        val staleRemote = ContactRecord(
            id = "c_conf",
            displayName = "Omar Old",
            primaryPhone = "+201001112222",
            phones = listOf("+201001112222"),
            isFavorite = false,
            clientUpdatedAtMs = 12_000L,
        )
        val (keptLocal, pushKeep) = SyncConflictResolver.resolveContact(local, staleRemote, hasPendingLocalEdit = true)
        assertTrue(pushKeep)
        assertEquals("Omar Hassan", keptLocal.displayName)
        assertTrue(keptLocal.isFavorite)

        // 2) Clean local accepts newer remote and maintains syncedToDevice = false
        val newerRemote = staleRemote.copy(
            displayName = "Omar Updated",
            syncedToDevice = true,
            clientUpdatedAtMs = 20_000L,
        )
        val (acceptedRemote, pushAccept) = SyncConflictResolver.resolveContact(local, newerRemote, hasPendingLocalEdit = false)
        assertFalse(pushAccept)
        assertEquals("Omar Updated", acceptedRemote.displayName)
        assertFalse(acceptedRemote.syncedToDevice)

        // 3) Simultaneous remote > local + 5000ms while pending merges and schedules push
        val concurrentRemote = ContactRecord(
            id = "c_conf",
            displayName = "Omar Hassan",
            primaryPhone = "+201001112222",
            phones = listOf("+201001112222", "+201223334444"),
            categoryPaths = listOf("VIP"),
            isFavorite = false,
            clientUpdatedAtMs = 25_000L,
        )
        val (merged, pushMerge) = SyncConflictResolver.resolveContact(local, concurrentRemote, hasPendingLocalEdit = true)
        assertTrue(pushMerge)
        assertTrue(merged.isFavorite)
        assertTrue(merged.phonesJoined.contains("+201223334444"))
        assertTrue(merged.categoryPathsFlat.contains("Work"))
        assertTrue(merged.categoryPathsFlat.contains("VIP"))

        // 4) Tombstone beats remote
        val tombstone = local.copy(isDeleted = true, deletedAtEpochMs = 30_000L)
        val (keptTombstone, pushTombstone) = SyncConflictResolver.resolveContact(tombstone, newerRemote, hasPendingLocalEdit = false)
        assertTrue(pushTombstone)
        assertTrue(keptTombstone.isDeleted)
    }

    @Test
    fun `local transcript analyzer produces summary bullets segments and action items`() {
        val sample = "بكرة عندنا اجتماع مهم الساعة 5 مساء. متنساش تحويل 2500 جنيه انستاباي للمورد. هبعتلك اللوكيشن على واتساب."
        val analysis = LocalTranscriptAnalyzer.analyze(
            rawTranscript = sample,
            contactName = "كريم المورد",
            phone = "+201001234567",
            referenceTimeMs = 1_700_000_000_000L,
            durationSec = 45L,
        )

        assertTrue(analysis.summaryBullets.isNotEmpty())
        assertTrue(analysis.summaryBullets.size <= 3)
        assertTrue(analysis.oneLineSummary.isNotBlank())
        assertTrue(analysis.segments.isNotEmpty())
        assertTrue(analysis.actionItems.isNotEmpty())
        assertNotNull(analysis.eventEpochMs)
    }

    @Test
    fun `local media cache manager caches audio and transcript json and cleans up properly`() = runBlocking<Unit> {
        val tempAudio = File(context.cacheDir, "sample_call.m4a").apply {
            writeBytes(ByteArray(1024) { 7 })
        }
        val cachedAudio = LocalMediaCacheManager.ensureRecordingCachedLocally(context, tempAudio)
        assertNotNull(cachedAudio)
        assertTrue(LocalMediaCacheManager.isAudioCachedLocally(context, cachedAudio!!.absolutePath, cachedAudio.name))

        val record = CallTranscriptRecord(
            id = "tr_cache_1",
            contactId = "c1",
            phone = "+201001234567",
            contactName = "Nadia",
            audioFilePath = cachedAudio.absolutePath,
            audioFileName = cachedAudio.name,
            timestampMs = 1_700_000_000_000L,
            durationSec = 20L,
            transcript = "اتفقنا على التسليم غدا",
            oneLineSummary = "تسليم غداً",
            summaryBullets = listOf("التسليم غداً"),
            segments = listOf(TranscriptSegment(0L, 4000L, "Nadia", "اتفقنا على التسليم غدا")),
            actionItems = listOf(CallActionItem("ai_1", "تجهيز التسليم", "CHECKLIST")),
        )
        val cachedJson = LocalMediaCacheManager.cacheTranscriptPayloadLocally(context, record)
        assertNotNull(cachedJson)
        assertTrue(cachedJson!!.exists())

        val stats = LocalMediaCacheManager.computeCacheStats(context)
        assertEquals(1, stats.recordingFilesCount)
        assertEquals(1, stats.transcriptCacheCount)
        assertTrue(stats.recordingBytes >= 1024L)

        // Orphan purge keeps referenced file and removes unreferenced file
        val orphan = File(LocalMediaCacheManager.recordingsDir(context), "orphan.m4a").apply {
            writeBytes(ByteArray(512) { 3 })
        }
        val removed = LocalMediaCacheManager.purgeOrphanedAudioFiles(context, setOf(cachedAudio.absolutePath))
        assertEquals(1, removed)
        assertFalse(orphan.exists())
        assertTrue(cachedAudio.exists())

        LocalMediaCacheManager.deleteCachedMediaForTranscript(context, record.id, cachedAudio.absolutePath, cachedAudio.name)
        assertFalse(cachedAudio.exists())
        assertFalse(cachedJson.exists())
        tempAudio.delete()
    }

    @Test
    fun `sync engine local first writes enqueue outbox and power searchGlobal and preCallContext`() = runBlocking<Unit> {
        val contact = ContactRecord(
            id = "c_eng_1",
            displayName = "Tarek Mansour",
            nickname = "طارق منصور",
            primaryPhone = "+201055566677",
            phones = listOf("+201055566677"),
            categoryPaths = listOf("Engineering"),
            syncedToDevice = true,
        )
        syncEngine.saveContactLocalFirst(contact, syncToCloud = true)

        val log = AppCallLogEntity(
            id = "cl_eng_1",
            contactId = "c_eng_1",
            phoneNumber = "+201055566677",
            phoneKey = PhoneUtils.phoneSearchKey("+201055566677"),
            cachedName = "Tarek Mansour",
            callType = 2,
            simSlot = 1,
            timestampMs = 50_000L,
            durationSec = 120L,
            transcriptId = "tr_eng_1",
            hasRecording = true,
        )
        syncEngine.recordCallLogLocalFirst(log, enqueueCloudSync = true)

        val transcript = CallTranscriptRecord(
            id = "tr_eng_1",
            contactId = "c_eng_1",
            phone = "+201055566677",
            contactName = "Tarek Mansour",
            audioFilePath = "",
            timestampMs = 50_000L,
            durationSec = 120L,
            transcript = "We confirmed the invoice delivery for Friday morning",
            oneLineSummary = "Invoice delivery confirmed for Friday",
            summaryBullets = listOf("Invoice delivery Friday morning"),
            actionItems = listOf(CallActionItem("a1", "Send invoice PDF", "REMINDER", completed = false)),
        )
        syncEngine.saveTranscriptLocalFirst(transcript, enqueueCloudSync = true)

        // Outbox has pending items for contact, call log, and transcript
        assertTrue(db.pendingSyncDao().getPendingCount() >= 3)

        // Global search hits across entities
        val searchInvoice = syncEngine.searchGlobal("invoice")
        assertTrue(searchInvoice.matchedTranscripts.any { it.id == "tr_eng_1" })

        val searchName = syncEngine.searchGlobal("Tarek")
        assertTrue(searchName.contacts.any { it.id == "c_eng_1" })
        assertTrue(searchName.matchedCallLogs.any { it.id == "cl_eng_1" })

        // Pre-call context shows last call + summary + pending action count
        val preCall = syncEngine.getPreCallContext("c_eng_1", "+201055566677")
        assertNotNull(preCall)
        assertEquals("Invoice delivery confirmed for Friday", preCall!!.oneLineSummary)
        assertEquals(1, preCall.pendingActionItemsCount)
        assertEquals(1, preCall.totalCallsCount)
        assertEquals(50_000L, preCall.lastSpokeEpochMs)

        // Soft delete hides contact & switches outbox operation to DELETE
        syncEngine.softDeleteContactLocalFirst("c_eng_1")
        assertNull(db.contactDao().getById("c_eng_1"))
        val outboxItem = db.pendingSyncDao().getByEntity(SyncEntityType.CONTACT.name, "c_eng_1")
        assertEquals(SyncOperation.DELETE.name, outboxItem?.operation)
    }
}
