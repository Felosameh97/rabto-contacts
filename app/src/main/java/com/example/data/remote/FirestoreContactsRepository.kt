package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.domain.model.CategoryNode
import com.example.domain.model.ContactRecord
import com.example.domain.model.PaymentTemplate
import com.example.domain.model.UserProfile
import com.example.domain.model.WhatsAppLog
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirestoreContactsRepository(
    private val db: FirebaseFirestore,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun contactsCol(uid: String) = userDoc(uid).collection("contacts")
    private fun categoriesCol(uid: String) = userDoc(uid).collection("categories")
    private fun logsCol(uid: String) = userDoc(uid).collection("whatsapp_logs")
    private fun templatesCol(uid: String) = userDoc(uid).collection("payment_templates")

    suspend fun ensureUserProfile(
        uid: String,
        displayName: String,
        email: String?,
    ): Result<UserProfile> = runCatching {
        val docRef = userDoc(uid)
        val snap = docRef.get().await()
        if (!snap.exists()) {
            val profile = UserProfile(
                id = uid,
                userId = uid,
                displayName = displayName.ifBlank { "User" },
                email = email,
            )
            docRef.set(profile.toCreateMap()).await()
            profile
        } else {
            snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(id = uid, userId = uid, displayName = displayName, email = email)
        }
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid")
    }

    suspend fun upsertContact(uid: String, contact: ContactRecord): Result<ContactRecord> = runCatching {
        val id = contact.id.ifBlank { "c_" + UUID.randomUUID().toString().replace("-", "").take(16) }
        val docRef = contactsCol(uid).document(id)
        val existing = docRef.get().await()
        val isCreate = !existing.exists()
        val createdTs = existing.getTimestamp("createdAt")
        val payload = contact.copy(id = id, createdAt = createdTs).toFirestoreMap(isCreate = isCreate)
        docRef.set(payload).await()
        docRef.get().await()
            .toObject(ContactRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: contact.copy(id = id)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid/contacts/${contact.id}")
    }

    /**
     * Batched high-throughput uploader for up to 20,000+ contacts (400 docs per Firestore WriteBatch).
     */
    suspend fun batchUpsertContacts(
        uid: String,
        contacts: List<ContactRecord>,
        onBatchProgress: (uploaded: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<Int> = runCatching {
        var uploaded = 0
        val chunks = contacts.chunked(400)
        for (chunk in chunks) {
            val batch = db.batch()
            for (c in chunk) {
                val id = c.id.ifBlank { "c_" + UUID.randomUUID().toString().replace("-", "").take(16) }
                val ref = contactsCol(uid).document(id)
                batch.set(ref, c.copy(id = id).toFirestoreMap(isCreate = true))
            }
            batch.commit().await()
            uploaded += chunk.size
            onBatchProgress(uploaded, contacts.size)
        }
        uploaded
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid/contacts")
    }

    suspend fun setContactSyncedToDevice(uid: String, contactId: String, synced: Boolean): Result<Unit> = runCatching {
        contactsCol(uid).document(contactId).update(
            mapOf(
                "syncedToDevice" to synced,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/$uid/contacts/$contactId")
    }

    suspend fun setContactFavorite(uid: String, contactId: String, favorite: Boolean): Result<Unit> = runCatching {
        contactsCol(uid).document(contactId).update(
            mapOf(
                "isFavorite" to favorite,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/$uid/contacts/$contactId")
    }

    suspend fun deleteContact(uid: String, contactId: String): Result<Unit> = runCatching {
        contactsCol(uid).document(contactId).delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "users/$uid/contacts/$contactId")
    }

    suspend fun getContactById(uid: String, contactId: String): Result<ContactRecord?> = runCatching {
        val snap = contactsCol(uid).document(contactId).get().await()
        if (snap.exists()) {
            snap.toObject(ContactRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } else {
            null
        }
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.GET, "users/$uid/contacts/$contactId")
    }

    /**
     * Paginated pull of user contacts from Firestore to populate/sync local Room index.
     */
    suspend fun pullContactsPage(
        uid: String,
        limit: Long = 500,
        lastSnapshot: DocumentSnapshot? = null,
    ): Result<Pair<List<ContactRecord>, DocumentSnapshot?>> = runCatching {
        var query: Query = contactsCol(uid).orderBy("displayName").limit(limit)
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot)
        }
        val snap = query.get().await()
        val list = snap.toObjects(ContactRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        val cursor = snap.documents.lastOrNull()
        list to cursor
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.LIST, "users/$uid/contacts")
    }

    suspend fun upsertCategories(uid: String, categories: List<CategoryNode>): Result<Unit> = runCatching {
        for (chunk in categories.chunked(400)) {
            val batch = db.batch()
            for (cat in chunk) {
                val id = cat.id.ifBlank { "cat_" + cat.fullPath.hashCode().toUInt().toString(16) }
                batch.set(categoriesCol(uid).document(id), cat.copy(id = id).toFirestoreMap(isCreate = true))
            }
            batch.commit().await()
        }
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid/categories")
    }

    suspend fun createWhatsAppLog(uid: String, log: WhatsAppLog): Result<WhatsAppLog> = runCatching {
        val id = log.id.ifBlank { "wa_" + UUID.randomUUID().toString().replace("-", "").take(16) }
        val ref = logsCol(uid).document(id)
        ref.set(log.copy(id = id).toFirestoreMap(isCreate = true)).await()
        ref.get().await()
            .toObject(WhatsAppLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: log.copy(id = id)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "users/$uid/whatsapp_logs/${log.id}")
    }

    suspend fun markWhatsAppLogSynced(uid: String, logId: String): Result<Unit> = runCatching {
        logsCol(uid).document(logId).update(
            mapOf(
                "syncedToPhone" to true,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/$uid/whatsapp_logs/$logId")
    }

    fun observeWhatsAppLogs(uid: String): Flow<List<WhatsAppLog>> =
        logsCol(uid)
            .orderBy("loggedAtEpochMs", Query.Direction.DESCENDING)
            .limit(200)
            .snapshots()
            .map { it.toObjects(WhatsAppLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    suspend fun upsertPaymentTemplate(uid: String, template: PaymentTemplate): Result<PaymentTemplate> = runCatching {
        val id = template.id.ifBlank { "tpl_" + UUID.randomUUID().toString().replace("-", "").take(12) }
        val ref = templatesCol(uid).document(id)
        val existing = ref.get().await()
        val isCreate = !existing.exists()
        val createdTs = existing.getTimestamp("createdAt")
        ref.set(template.copy(id = id, createdAt = createdTs).toFirestoreMap(isCreate = isCreate)).await()
        ref.get().await()
            .toObject(PaymentTemplate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: template.copy(id = id)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid/payment_templates/${template.id}")
    }

    suspend fun deletePaymentTemplate(uid: String, templateId: String): Result<Unit> = runCatching {
        templatesCol(uid).document(templateId).delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "users/$uid/payment_templates/$templateId")
    }

    fun observePaymentTemplates(uid: String): Flow<List<PaymentTemplate>> =
        templatesCol(uid)
            .snapshots()
            .map { it.toObjects(PaymentTemplate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
}
