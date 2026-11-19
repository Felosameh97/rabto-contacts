package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.domain.model.CategoryNode
import com.example.domain.model.CloudCallLogRecord
import com.example.domain.model.CloudCallTranscriptRecord
import com.example.domain.model.ContactRecord
import com.example.domain.model.PaymentTemplate
import com.example.domain.model.UserProfile
import com.example.domain.model.WhatsAppLog
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

data class BatchSyncOutcome(
    val succeededIds: List<String>,
    val failedErrorsById: Map<String, String>,
)

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
    private fun callLogsCol(uid: String) = userDoc(uid).collection("call_logs")
    private fun callTranscriptsCol(uid: String) = userDoc(uid).collection("call_transcripts")

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
        val id = sanitizeFirestoreDocId(contact.id, "c")
        val docRef = contactsCol(uid).document(id)
        val existing = docRef.get().await()
        val isCreate = !existing.exists()
        val createdTs = existing.getTimestamp("createdAt")
        val payload = contact.copy(id = id, createdAt = createdTs).toFirestoreMap(isCreate = isCreate)
        if (!isCreate && createdTs == null) {
            docRef.set(payload, SetOptions.merge()).await()
        } else {
            docRef.set(payload).await()
        }
        contact.copy(id = id, createdAt = createdTs)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid/contacts/${contact.id}")
    }

    /**
     * Batched high-throughput uploader for up to 30,000+ contacts (400 docs per Firestore WriteBatch).
     */
    suspend fun batchUpsertContacts(
        uid: String,
        contacts: List<ContactRecord>,
        onBatchProgress: (uploaded: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<Int> = runCatching {
        val outcome = batchUpsertContactsSmart(uid, contacts, onBatchProgress)
        if (outcome.failedErrorsById.isNotEmpty() && outcome.succeededIds.isEmpty()) {
            throw IllegalStateException(outcome.failedErrorsById.values.first())
        }
        outcome.succeededIds.size
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid/contacts")
    }

    /**
     * High-throughput parallel WriteBatch uploader (3 concurrent 400-doc batches = 1,200 docs/wave):
     * 1) Fast-path single WriteBatch for new contacts (1 RPC per 400 docs).
     * 2) On PERMISSION_DENIED (existing docs with immutable createdAt), probes existing IDs in
     *    parallel 30-item whereIn queries (~150ms) and writes existing vs new in 2 clean WriteBatches.
     * 3) On any remaining error, uses binary bisection to isolate only bad record(s) without ever
     *    degrading to 3-RPC sequential uploads.
     */
    suspend fun batchUpsertContactsSmart(
        uid: String,
        contacts: List<ContactRecord>,
        onBatchProgress: (uploaded: Int, total: Int) -> Unit = { _, _ -> },
    ): BatchSyncOutcome {
        if (contacts.isEmpty()) return BatchSyncOutcome(emptyList(), emptyMap())
        val semaphore = Semaphore(BATCH_CONCURRENCY)
        val total = contacts.size
        val succeeded = java.util.Collections.synchronizedList(ArrayList<String>(total))
        val failed = java.util.concurrent.ConcurrentHashMap<String, String>()
        val completedCount = java.util.concurrent.atomic.AtomicInteger(0)

        val chunks = contacts.chunked(WRITE_BATCH_LIMIT)
        coroutineScope {
            chunks.map { chunk ->
                async {
                    semaphore.withPermit {
                        val chunkOutcome = commitContactChunkSmart(uid, chunk)
                        succeeded.addAll(chunkOutcome.succeededIds)
                        failed.putAll(chunkOutcome.failedErrorsById)
                        val done = completedCount.addAndGet(chunkOutcome.succeededIds.size)
                        onBatchProgress(done, total)
                    }
                }
            }.awaitAll()
        }
        return BatchSyncOutcome(succeeded.toList(), failed.toMap())
    }

    private suspend fun commitContactChunkSmart(
        uid: String,
        chunk: List<ContactRecord>,
    ): BatchSyncOutcome {
        if (chunk.isEmpty()) return BatchSyncOutcome(emptyList(), emptyMap())
        val prepared = chunk.map { c ->
            val rawId = c.id
            val safeId = sanitizeFirestoreDocId(rawId, "c")
            Triple(rawId, safeId, c.copy(id = safeId, syncedToDevice = false))
        }

        // Fast path: 1 single WriteBatch assuming docs are new (or already carry createdAt)
        val fastResult = runCatching {
            val batch = db.batch()
            for ((_, safeId, contact) in prepared) {
                val ref = contactsCol(uid).document(safeId)
                val isCreate = contact.createdAt == null
                batch.set(ref, contact.toFirestoreMap(isCreate = isCreate))
            }
            batch.commit().await()
        }
        if (fastResult.isSuccess) {
            return BatchSyncOutcome(prepared.map { it.first }, emptyMap())
        }
        val fastErr = fastResult.exceptionOrNull()
        if (fastErr is CancellationException) throw fastErr

        // Smart recovery when some docs already exist on Firestore (createdAt immutability)
        if (isPermissionDenied(fastErr)) {
            val existingTimestamps = fetchExistingCreatedAt(
                collection = contactsCol(uid),
                docIds = prepared.map { it.second }.distinct(),
            )
            if (existingTimestamps != null && existingTimestamps.isNotEmpty()) {
                val resolvedResult = runCatching {
                    val batch = db.batch()
                    for ((_, safeId, contact) in prepared) {
                        val ref = contactsCol(uid).document(safeId)
                        if (existingTimestamps.containsKey(safeId)) {
                            val existingTs = existingTimestamps[safeId]
                            val payload = contact.copy(createdAt = existingTs).toFirestoreMap(isCreate = false)
                            if (existingTs != null) {
                                batch.set(ref, payload)
                            } else {
                                batch.set(ref, payload, SetOptions.merge())
                            }
                        } else {
                            batch.set(ref, contact.toFirestoreMap(isCreate = true))
                        }
                    }
                    batch.commit().await()
                }
                if (resolvedResult.isSuccess) {
                    return BatchSyncOutcome(prepared.map { it.first }, emptyMap())
                }
                val resErr = resolvedResult.exceptionOrNull()
                if (resErr is CancellationException) throw resErr
            }
        }

        // Network/offline errors fail the chunk cleanly without wasteful splitting
        if (isTransientNetworkError(fastErr)) {
            val msg = fastErr?.localizedMessage ?: "Network sync error"
            return BatchSyncOutcome(emptyList(), prepared.associate { it.first to msg })
        }

        // Binary bisection isolates any single malformed document in O(log N) batches
        if (chunk.size == 1) {
            val single = chunk.first()
            val singleRes = upsertContact(uid, single)
            return if (singleRes.isSuccess) {
                BatchSyncOutcome(listOf(single.id), emptyMap())
            } else {
                val err = singleRes.exceptionOrNull()
                if (err is CancellationException) throw err
                BatchSyncOutcome(emptyList(), mapOf(single.id to (err?.localizedMessage ?: "Contact sync error")))
            }
        }

        val mid = chunk.size / 2
        val left = commitContactChunkSmart(uid, chunk.subList(0, mid))
        val right = commitContactChunkSmart(uid, chunk.subList(mid, chunk.size))
        return BatchSyncOutcome(
            succeededIds = left.succeededIds + right.succeededIds,
            failedErrorsById = left.failedErrorsById + right.failedErrorsById,
        )
    }

    private suspend fun fetchExistingCreatedAt(
        collection: com.google.firebase.firestore.CollectionReference,
        docIds: List<String>,
    ): Map<String, Timestamp?>? {
        if (docIds.isEmpty()) return emptyMap()
        return runCatching {
            val result = java.util.concurrent.ConcurrentHashMap<String, Timestamp>()
            val existingSet = java.util.Collections.synchronizedSet(HashSet<String>())
            coroutineScope {
                docIds.chunked(WHERE_IN_LIMIT).map { sub ->
                    async {
                        val snap = collection.whereIn(FieldPath.documentId(), sub).get().await()
                        for (doc in snap.documents) {
                            existingSet.add(doc.id)
                            doc.getTimestamp("createdAt")?.let { result[doc.id] = it }
                        }
                    }
                }.awaitAll()
            }
            val map = HashMap<String, Timestamp?>(existingSet.size)
            for (id in existingSet) {
                map[id] = result[id]
            }
            map
        }.getOrNull()
    }

    private fun isPermissionDenied(err: Throwable?): Boolean {
        if (err is FirebaseFirestoreException) {
            return err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        val msg = err?.message.orEmpty()
        return msg.contains("PERMISSION_DENIED", ignoreCase = true)
    }

    private fun isTransientNetworkError(err: Throwable?): Boolean {
        if (err is FirebaseFirestoreException) {
            return err.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                err.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ||
                err.code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED
        }
        return false
    }

    suspend fun setContactSyncedToDevice(uid: String, contactId: String, @Suppress("UNUSED_PARAMETER") synced: Boolean): Result<Unit> = runCatching {
        contactsCol(uid).document(sanitizeFirestoreDocId(contactId, "c")).update(
            mapOf(
                "syncedToDevice" to false,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/$uid/contacts/$contactId")
    }

    suspend fun setContactFavorite(uid: String, contactId: String, favorite: Boolean): Result<Unit> = runCatching {
        contactsCol(uid).document(sanitizeFirestoreDocId(contactId, "c")).update(
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
        contactsCol(uid).document(sanitizeFirestoreDocId(contactId, "c")).delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "users/$uid/contacts/$contactId")
    }

    suspend fun batchDeleteContacts(uid: String, contactIds: List<String>): Result<Int> = runCatching {
        if (contactIds.isEmpty()) return@runCatching 0
        var deleted = 0
        for (chunk in contactIds.distinct().chunked(WRITE_BATCH_LIMIT)) {
            val batch = db.batch()
            for (cid in chunk) {
                batch.delete(contactsCol(uid).document(sanitizeFirestoreDocId(cid, "c")))
            }
            batch.commit().await()
            deleted += chunk.size
        }
        deleted
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "users/$uid/contacts")
    }

    suspend fun getContactById(uid: String, contactId: String): Result<ContactRecord?> = runCatching {
        val snap = contactsCol(uid).document(sanitizeFirestoreDocId(contactId, "c")).get().await()
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
        val outcome = upsertCategoriesSmart(uid, categories)
        if (outcome.failedErrorsById.isNotEmpty() && outcome.succeededIds.isEmpty()) {
            throw IllegalStateException(outcome.failedErrorsById.values.first())
        }
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid/categories")
    }

    /**
     * Existing-doc-aware batched category uploader so updating pre-existing categories never fails
     * Firestore's immutable createdAt rule.
     */
    suspend fun upsertCategoriesSmart(uid: String, categories: List<CategoryNode>): BatchSyncOutcome {
        if (categories.isEmpty()) return BatchSyncOutcome(emptyList(), emptyMap())
        val succeeded = ArrayList<String>(categories.size)
        val failed = LinkedHashMap<String, String>()

        for (chunk in categories.chunked(WRITE_BATCH_LIMIT)) {
            val prepared = chunk.map { cat ->
                val rawId = cat.id
                val fallbackId = "cat_" + cat.fullPath.hashCode().toUInt().toString(16)
                val safeId = sanitizeFirestoreDocId(rawId.ifBlank { fallbackId }, "cat")
                Triple(rawId, safeId, cat.copy(id = safeId))
            }

            val fastRes = runCatching {
                val batch = db.batch()
                for ((_, safeId, cat) in prepared) {
                    batch.set(categoriesCol(uid).document(safeId), cat.toFirestoreMap(isCreate = true))
                }
                batch.commit().await()
            }
            if (fastRes.isSuccess) {
                succeeded += prepared.map { it.first }
                continue
            }
            val err = fastRes.exceptionOrNull()
            if (err is CancellationException) throw err

            if (isPermissionDenied(err)) {
                val existingTsMap = fetchExistingCreatedAt(
                    collection = categoriesCol(uid),
                    docIds = prepared.map { it.second }.distinct(),
                )
                if (existingTsMap != null) {
                    val retryRes = runCatching {
                        val batch = db.batch()
                        for ((_, safeId, cat) in prepared) {
                            val ref = categoriesCol(uid).document(safeId)
                            if (existingTsMap.containsKey(safeId)) {
                                val ts = existingTsMap[safeId]
                                val map = cat.copy(createdAt = ts).toFirestoreMap(isCreate = false)
                                if (ts != null) batch.set(ref, map) else batch.set(ref, map, SetOptions.merge())
                            } else {
                                batch.set(ref, cat.toFirestoreMap(isCreate = true))
                            }
                        }
                        batch.commit().await()
                    }
                    if (retryRes.isSuccess) {
                        succeeded += prepared.map { it.first }
                        continue
                    }
                    val retryErr = retryRes.exceptionOrNull()
                    if (retryErr is CancellationException) throw retryErr
                }
            }

            // Per-doc fallback only if batch recovery still failed
            for ((rawId, safeId, cat) in prepared) {
                val single = runCatching {
                    val ref = categoriesCol(uid).document(safeId)
                    val snap = ref.get().await()
                    val isCreate = !snap.exists()
                    val ts = snap.getTimestamp("createdAt")
                    val payload = cat.copy(createdAt = ts).toFirestoreMap(isCreate = isCreate)
                    if (!isCreate && ts == null) ref.set(payload, SetOptions.merge()).await()
                    else ref.set(payload).await()
                }
                if (single.isSuccess) {
                    succeeded += rawId
                } else {
                    val se = single.exceptionOrNull()
                    if (se is CancellationException) throw se
                    failed[rawId] = se?.localizedMessage ?: "Category sync error"
                }
            }
        }
        return BatchSyncOutcome(succeeded, failed)
    }

    suspend fun createWhatsAppLog(uid: String, log: WhatsAppLog): Result<WhatsAppLog> = runCatching {
        val id = sanitizeFirestoreDocId(log.id, "wa")
        val ref = logsCol(uid).document(id)
        val snap = ref.get().await()
        if (!snap.exists()) {
            ref.set(log.copy(id = id).toFirestoreMap(isCreate = true)).await()
        } else {
            ref.update(
                mapOf(
                    "syncedToPhone" to log.syncedToPhone,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
        log.copy(id = id)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "users/$uid/whatsapp_logs/${log.id}")
    }

    suspend fun markWhatsAppLogSynced(uid: String, logId: String): Result<Unit> = runCatching {
        logsCol(uid).document(sanitizeFirestoreDocId(logId, "wa")).update(
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
        val id = sanitizeFirestoreDocId(template.id, "tpl")
        val ref = templatesCol(uid).document(id)
        val existing = ref.get().await()
        val isCreate = !existing.exists()
        val createdTs = existing.getTimestamp("createdAt")
        val payload = template.copy(id = id, createdAt = createdTs).toFirestoreMap(isCreate = isCreate)
        if (!isCreate && createdTs == null) ref.set(payload, SetOptions.merge()).await()
        else ref.set(payload).await()
        template.copy(id = id, createdAt = createdTs)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid/payment_templates/${template.id}")
    }

    suspend fun deletePaymentTemplate(uid: String, templateId: String): Result<Unit> = runCatching {
        templatesCol(uid).document(sanitizeFirestoreDocId(templateId, "tpl")).delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "users/$uid/payment_templates/$templateId")
    }

    fun observePaymentTemplates(uid: String): Flow<List<PaymentTemplate>> =
        templatesCol(uid)
            .snapshots()
            .map { it.toObjects(PaymentTemplate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    fun observeContacts(uid: String): Flow<List<ContactRecord>> =
        contactsCol(uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(1000)
            .snapshots()
            .map { it.toObjects(ContactRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    suspend fun pullCategories(uid: String): Result<List<CategoryNode>> = runCatching {
        val snap = categoriesCol(uid).get().await()
        snap.toObjects(CategoryNode::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.LIST, "users/$uid/categories")
    }

    suspend fun deleteCategory(uid: String, categoryId: String): Result<Unit> = runCatching {
        categoriesCol(uid).document(sanitizeFirestoreDocId(categoryId, "cat")).delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "users/$uid/categories/$categoryId")
    }

    suspend fun upsertCallLog(uid: String, log: CloudCallLogRecord): Result<CloudCallLogRecord> = runCatching {
        val id = sanitizeFirestoreDocId(log.id, "cl")
        val ref = callLogsCol(uid).document(id)
        val existing = ref.get().await()
        val isCreate = !existing.exists()
        val createdTs = existing.getTimestamp("createdAt")
        val payload = log.copy(id = id, createdAt = createdTs).toFirestoreMap(isCreate = isCreate)
        if (!isCreate && createdTs == null) ref.set(payload, SetOptions.merge()).await()
        else ref.set(payload).await()
        log.copy(id = id, createdAt = createdTs)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid/call_logs/${log.id}")
    }

    suspend fun batchUpsertCallLogs(uid: String, logs: List<CloudCallLogRecord>): Result<Int> = runCatching {
        val outcome = batchUpsertCallLogsSmart(uid, logs)
        if (outcome.failedErrorsById.isNotEmpty() && outcome.succeededIds.isEmpty()) {
            throw IllegalStateException(outcome.failedErrorsById.values.first())
        }
        outcome.succeededIds.size
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid/call_logs")
    }

    suspend fun batchUpsertCallLogsSmart(uid: String, logs: List<CloudCallLogRecord>): BatchSyncOutcome {
        if (logs.isEmpty()) return BatchSyncOutcome(emptyList(), emptyMap())
        val succeeded = ArrayList<String>(logs.size)
        val failed = LinkedHashMap<String, String>()

        for (chunk in logs.chunked(WRITE_BATCH_LIMIT)) {
            val prepared = chunk.map { l ->
                val rawId = l.id
                val safeId = sanitizeFirestoreDocId(rawId, "cl")
                Triple(rawId, safeId, l.copy(id = safeId))
            }
            val fastRes = runCatching {
                val batch = db.batch()
                for ((_, safeId, l) in prepared) {
                    val isCreate = l.createdAt == null
                    batch.set(callLogsCol(uid).document(safeId), l.toFirestoreMap(isCreate = isCreate))
                }
                batch.commit().await()
            }
            if (fastRes.isSuccess) {
                succeeded += prepared.map { it.first }
                continue
            }
            val err = fastRes.exceptionOrNull()
            if (err is CancellationException) throw err

            if (isPermissionDenied(err)) {
                val existingTsMap = fetchExistingCreatedAt(
                    collection = callLogsCol(uid),
                    docIds = prepared.map { it.second }.distinct(),
                )
                if (existingTsMap != null) {
                    val retryRes = runCatching {
                        val batch = db.batch()
                        for ((_, safeId, l) in prepared) {
                            val ref = callLogsCol(uid).document(safeId)
                            if (existingTsMap.containsKey(safeId)) {
                                val ts = existingTsMap[safeId]
                                val map = l.copy(createdAt = ts).toFirestoreMap(isCreate = false)
                                if (ts != null) batch.set(ref, map) else batch.set(ref, map, SetOptions.merge())
                            } else {
                                batch.set(ref, l.toFirestoreMap(isCreate = true))
                            }
                        }
                        batch.commit().await()
                    }
                    if (retryRes.isSuccess) {
                        succeeded += prepared.map { it.first }
                        continue
                    }
                }
            }

            for ((rawId, _, l) in prepared) {
                upsertCallLog(uid, l)
                    .onSuccess { succeeded += rawId }
                    .onFailure { se -> failed[rawId] = se.localizedMessage ?: "Call log error" }
            }
        }
        return BatchSyncOutcome(succeeded, failed)
    }

    suspend fun deleteCallLog(uid: String, callLogId: String): Result<Unit> = runCatching {
        callLogsCol(uid).document(sanitizeFirestoreDocId(callLogId, "cl")).delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "users/$uid/call_logs/$callLogId")
    }

    suspend fun pullCallLogs(uid: String, limit: Long = 350): Result<List<CloudCallLogRecord>> = runCatching {
        val snap = callLogsCol(uid)
            .orderBy("timestampMs", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()
        snap.toObjects(CloudCallLogRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.LIST, "users/$uid/call_logs")
    }

    fun observeCallLogs(uid: String): Flow<List<CloudCallLogRecord>> =
        callLogsCol(uid)
            .orderBy("timestampMs", Query.Direction.DESCENDING)
            .limit(350)
            .snapshots()
            .map { it.toObjects(CloudCallLogRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    suspend fun upsertCallTranscript(uid: String, transcript: CloudCallTranscriptRecord): Result<CloudCallTranscriptRecord> = runCatching {
        val id = sanitizeFirestoreDocId(transcript.id, "tr")
        val ref = callTranscriptsCol(uid).document(id)
        val existing = ref.get().await()
        val isCreate = !existing.exists()
        val createdTs = existing.getTimestamp("createdAt")
        val payload = transcript.copy(id = id, createdAt = createdTs).toFirestoreMap(isCreate = isCreate)
        if (!isCreate && createdTs == null) ref.set(payload, SetOptions.merge()).await()
        else ref.set(payload).await()
        transcript.copy(id = id, createdAt = createdTs)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid/call_transcripts/${transcript.id}")
    }

    companion object {
        const val WRITE_BATCH_LIMIT = 400
        private const val WHERE_IN_LIMIT = 30
        private const val BATCH_CONCURRENCY = 3
        private val VALID_DOC_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]{1,128}$")

        fun sanitizeFirestoreDocId(rawId: String, prefix: String): String {
            val trimmed = rawId.trim()
            if (trimmed.isNotEmpty() && VALID_DOC_ID_REGEX.matches(trimmed)) {
                return trimmed
            }
            if (trimmed.isEmpty()) {
                return "${prefix}_" + UUID.randomUUID().toString().replace("-", "").take(16)
            }
            val bytes = MessageDigest.getInstance("SHA-256").digest(trimmed.toByteArray())
            val hex = bytes.joinToString("") { "%02x".format(it) }.take(24)
            return "${prefix}_$hex"
        }
    }

    suspend fun deleteCallTranscript(uid: String, transcriptId: String): Result<Unit> = runCatching {
        callTranscriptsCol(uid).document(transcriptId).delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "users/$uid/call_transcripts/$transcriptId")
    }

    suspend fun pullCallTranscripts(uid: String, limit: Long = 250): Result<List<CloudCallTranscriptRecord>> = runCatching {
        val snap = callTranscriptsCol(uid)
            .orderBy("timestampMs", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()
        snap.toObjects(CloudCallTranscriptRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.LIST, "users/$uid/call_transcripts")
    }

    fun observeCallTranscripts(uid: String): Flow<List<CloudCallTranscriptRecord>> =
        callTranscriptsCol(uid)
            .orderBy("timestampMs", Query.Direction.DESCENDING)
            .limit(250)
            .snapshots()
            .map { it.toObjects(CloudCallTranscriptRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
}
