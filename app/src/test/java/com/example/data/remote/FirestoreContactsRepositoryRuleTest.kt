package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.example.domain.model.ContactRecord
import com.example.domain.model.PaymentTemplate
import com.example.domain.model.WhatsAppLog
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.test.assertFailsWith

class FirestoreContactsRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun upsertAndPullContact_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = FirestoreContactsRepository(firestore)
        val contactId = "c_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val contact = ContactRecord(
            id = contactId,
            displayName = "محمد أحمد",
            primaryPhone = "+201012345678",
            phones = listOf("+201012345678"),
            emails = listOf("m.ahmed@example.com"),
            categoryPaths = listOf("الجامعة > هندسة"),
            district = "المطرية",
            latitude = 30.1213,
            longitude = 31.3140,
        )

        val saved = withTimeout(TIMEOUT_MS) { repo.upsertContact(uid, contact).getOrThrow() }
        assertEquals(contactId, saved.id)

        val pulled = withTimeout(TIMEOUT_MS) { repo.pullContactsPage(uid).getOrThrow().first }
        assertTrue(pulled.any { it.id == contactId && it.displayName == "محمد أحمد" })
    }

    @Test
    fun getContactById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = FirestoreContactsRepository(firestore)
        val contactId = "c_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        withTimeout(TIMEOUT_MS) {
            repo.upsertContact(
                aliceUid,
                ContactRecord(
                    id = contactId,
                    displayName = "سري",
                    primaryPhone = "+201112223334",
                    phones = listOf("+201112223334"),
                )
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val ex = assertFailsWith<FirebaseFirestoreException> {
            withTimeout(TIMEOUT_MS) { repo.getContactById(aliceUid, contactId).getOrThrow() }
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex.code)
    }

    @Test
    fun observeWhatsAppLogs_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = FirestoreContactsRepository(firestore)
        val ex = assertFailsWith<Exception> {
            withTimeout(FLOW_TIMEOUT_MS) { repo.observeWhatsAppLogs("alice_123").first() }
        }
        val fsEx = checkNotNull(ex.unwrapFirestoreException()) { "Expected FirebaseFirestoreException, got $ex" }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
    }

    @Test
    fun createWhatsAppLogAndPaymentTemplate_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = FirestoreContactsRepository(firestore)
        val logId = "wa_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val log = WhatsAppLog(
            id = logId,
            contactId = "c_abc",
            contactName = "عمر",
            phone = "+201200000001",
            loggedAtEpochMs = System.currentTimeMillis(),
            syncedToPhone = false,
        )
        val createdLog = withTimeout(TIMEOUT_MS) { repo.createWhatsAppLog(uid, log).getOrThrow() }
        assertEquals(logId, createdLog.id)

        val tplId = "tpl_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val tpl = PaymentTemplate(
            id = tplId,
            name = "InstaPay",
            urlTemplate = "https://ipn.eg/S/{phone_local}/instapay",
            isDefault = true,
        )
        val createdTpl = withTimeout(TIMEOUT_MS) { repo.upsertPaymentTemplate(uid, tpl).getOrThrow() }
        assertNotNull(createdTpl.id)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_rabta@test.com"
        const val BOB_EMAIL = "bob_rabta@test.com"
        const val TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
