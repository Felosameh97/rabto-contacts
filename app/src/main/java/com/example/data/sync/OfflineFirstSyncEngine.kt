package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.data.local.AppCallLogEntity
import com.example.data.local.CallTranscriptEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.ContactEntity
import com.example.data.local.LocalMediaCacheManager
import com.example.data.local.PaymentTemplateEntity
import com.example.data.local.PendingSyncEntity
import com.example.data.local.RabtaDatabase
import com.example.data.local.SyncPrefsDataStore
import com.example.data.local.WhatsAppLogEntity
import com.example.data.remote.FirestoreContactsRepository
import com.example.domain.model.CallLogEntry
import com.example.domain.model.CallTranscriptRecord
import com.example.domain.model.CategoryNode
import com.example.domain.model.CloudCallLogRecord
import com.example.domain.model.CloudCallTranscriptRecord
import com.example.domain.model.ContactRecord
import com.example.domain.model.GlobalSearchResult
import com.example.domain.model.PaymentTemplate
import com.example.domain.model.PreCallContext
import com.example.domain.model.SyncEntityType
import com.example.domain.model.SyncEngineState
import com.example.domain.model.SyncOperation
import com.example.domain.model.SyncState
import com.example.domain.model.SyncStatusBadge
import com.example.domain.model.WhatsAppLog
import com.example.util.PhoneUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Offline-First Single-Source-of-Truth Engine.
 * - Executes all CRUD operations locally in Room immediately (0ms UI latency).
 * - Persists mutations in an atomic coalesced outbox (pending_sync_queue).
 * - Flushes outbox and merges remote changes in both directions whenever online.
 * - Enforces Complete Sandbox Isolation (never writes to device ContactsContract).
 */
class OfflineFirstSyncEngine(
    context: Context,
    private val roomDb: RabtaDatabase,
    private val firestoreRepo: FirestoreContactsRepository,
    private val prefs: SyncPrefsDataStore,
) {
    private val appContext = context.applicationContext
    private val contactDao = roomDb.contactDao()
    private val categoryDao = roomDb.categoryDao()
    private val whatsAppLogDao = roomDb.whatsAppLogDao()
    private val paymentDao = roomDb.paymentTemplateDao()
    private val callLogDao = roomDb.appCallLogDao()
    private val transcriptDao = roomDb.callTranscriptDao()
    private val pendingSyncDao = roomDb.pendingSyncDao()

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushMutex = Mutex()

    private var activeUserId: String? = null
    private var realtimeJobs: MutableList<Job> = mutableListOf()
    private var networkCallbackRegistered = false

    private val _isOnline = MutableStateFlow(checkCurrentConnectivity())
    private val _isSyncing = MutableStateFlow(false)
    private val _lastError = MutableStateFlow<String?>(null)

    val syncState: StateFlow<SyncEngineState> = combine(
        pendingSyncDao.observePendingCount(),
        _isOnline,
        _isSyncing,
        _lastError,
        prefs.lastSyncFlow,
    ) { pending, online, syncing, err, lastSync ->
        val badge = when {
            !online -> SyncStatusBadge.OFFLINE
            syncing -> SyncStatusBadge.SYNCING
            err != null && pending > 0 -> SyncStatusBadge.ERROR
            pending == 0 -> SyncStatusBadge.SYNCED
            else -> SyncStatusBadge.IDLE
        }
        SyncEngineState(
            status = badge,
            pendingCount = pending,
            lastSyncEpochMs = lastSync,
            isOnline = online,
            lastError = err,
        )
    }.stateIn(engineScope, SharingStarted.Eagerly, SyncEngineState(isOnline = _isOnline.value))

    private val connectivityManager: ConnectivityManager? =
        appContext.getSystemService(ConnectivityManager::class.java)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
            activeUserId?.let { uid ->
                engineScope.launch { flushPendingQueue(uid) }
            }
        }

        override fun onLost(network: Network) {
            _isOnline.value = checkCurrentConnectivity()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val valid = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            _isOnline.value = valid
            if (valid) {
                activeUserId?.let { uid ->
                    engineScope.launch { flushPendingQueue(uid) }
                }
            }
        }
    }

    private fun checkCurrentConnectivity(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun startTwoWaySync(userId: String, hostScope: CoroutineScope) {
        if (userId.isBlank()) return
        stop()
        activeUserId = userId
        registerConnectivityObserver()

        // 1) Initial outbox push + pull catch-up
        val bootstrapJob = hostScope.launch(Dispatchers.IO) {
            flushPendingQueue(userId)
            pullRemoteMetadataCatchUp(userId)
        }
        realtimeJobs += bootstrapJob

        // 2) Live Firestore -> Room observers
        realtimeJobs += hostScope.launch(Dispatchers.IO) {
            firestoreRepo.observeContacts(userId)
                .catch { }
                .collect { remoteContacts ->
                    if (remoteContacts.isNotEmpty()) {
                        mergePulledContacts(remoteContacts)
                    }
                }
        }

        realtimeJobs += hostScope.launch(Dispatchers.IO) {
            firestoreRepo.observeCallLogs(userId)
                .catch { }
                .collect { remoteLogs ->
                    if (remoteLogs.isNotEmpty()) {
                        mergePulledCallLogs(remoteLogs)
                    }
                }
        }

        realtimeJobs += hostScope.launch(Dispatchers.IO) {
            firestoreRepo.observeCallTranscripts(userId)
                .catch { }
                .collect { remoteTranscripts ->
                    if (remoteTranscripts.isNotEmpty()) {
                        mergePulledTranscripts(remoteTranscripts)
                    }
                }
        }

        realtimeJobs += hostScope.launch(Dispatchers.IO) {
            firestoreRepo.observeWhatsAppLogs(userId)
                .catch { }
                .collect { cloudLogs ->
                    if (cloudLogs.isNotEmpty()) {
                        whatsAppLogDao.upsertAll(
                            cloudLogs.map {
                                WhatsAppLogEntity(
                                    id = it.id,
                                    contactId = it.contactId,
                                    contactName = it.contactName,
                                    phone = it.phone,
                                    loggedAtEpochMs = it.loggedAtEpochMs,
                                    syncedToPhone = false,
                                )
                            }
                        )
                    }
                }
        }

        realtimeJobs += hostScope.launch(Dispatchers.IO) {
            firestoreRepo.observePaymentTemplates(userId)
                .catch { }
                .collect { templates ->
                    if (templates.isNotEmpty()) {
                        paymentDao.upsertAll(
                            templates.map { PaymentTemplateEntity(it.id, it.name, it.urlTemplate, it.isDefault) }
                        )
                    }
                }
        }
    }

    fun stop() {
        realtimeJobs.forEach { it.cancel() }
        realtimeJobs.clear()
        activeUserId = null
        unregisterConnectivityObserver()
    }

    private fun registerConnectivityObserver() {
        if (networkCallbackRegistered) return
        runCatching {
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(req, networkCallback)
            networkCallbackRegistered = true
        }
    }

    private fun unregisterConnectivityObserver() {
        if (!networkCallbackRegistered) return
        runCatching {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        }
        networkCallbackRegistered = false
    }

    // --- 0ms Local-First Write Operations + Atomic Outbox Queue ---

    suspend fun saveContactLocalFirst(
        contact: ContactRecord,
        categories: List<CategoryNode> = emptyList(),
        supersededIds: List<String> = emptyList(),
        syncToCloud: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        val isolated = contact.copy(syncedToDevice = false, clientUpdatedAtMs = nowMs)
        val entity = ContactEntity.fromDomain(isolated, nowMs = nowMs, syncState = SyncState.PENDING)

        if (supersededIds.isNotEmpty()) {
            contactDao.deleteContactsWithIndexes(supersededIds)
        }
        contactDao.upsertSingleWithIndexes(entity)
        if (categories.isNotEmpty()) {
            categoryDao.upsertAll(categories.map {
                CategoryEntity(it.id, it.name, it.parentPath, it.fullPath, it.depth.toInt(), it.iconKey)
            })
        }

        if (syncToCloud) {
            val queueItems = buildList {
                add(outboxUpsert(SyncEntityType.CONTACT, entity.id, nowMs))
                supersededIds.forEach { delId ->
                    add(outboxDelete(SyncEntityType.CONTACT, delId, nowMs))
                }
                categories.forEach { cat ->
                    add(outboxUpsert(SyncEntityType.CATEGORY, cat.id, nowMs))
                }
            }
            pendingSyncDao.enqueueBatch(queueItems)
            triggerBackgroundFlush()
        }
    }

    suspend fun saveContactsBatchLocalFirst(
        contacts: List<ContactRecord>,
        categories: List<CategoryNode>,
        supersededIds: List<String> = emptyList(),
        syncToCloud: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        val entities = contacts.map {
            ContactEntity.fromDomain(
                record = it.copy(syncedToDevice = false, clientUpdatedAtMs = nowMs),
                nowMs = nowMs,
                syncState = SyncState.PENDING,
            )
        }
        val catEntities = categories.map {
            CategoryEntity(it.id, it.name, it.parentPath, it.fullPath, it.depth.toInt(), it.iconKey)
        }

        if (supersededIds.isNotEmpty()) {
            contactDao.deleteContactsWithIndexes(supersededIds)
        }
        contactDao.upsertBatchWithIndexes(entities, catEntities, categoryDao)

        if (syncToCloud) {
            val queueItems = ArrayList<PendingSyncEntity>(entities.size + supersededIds.size + catEntities.size)
            entities.forEach { queueItems += outboxUpsert(SyncEntityType.CONTACT, it.id, nowMs) }
            supersededIds.forEach { queueItems += outboxDelete(SyncEntityType.CONTACT, it, nowMs) }
            categories.forEach { queueItems += outboxUpsert(SyncEntityType.CATEGORY, it.id, nowMs) }
            queueItems.chunked(400).forEach { pendingSyncDao.enqueueBatch(it) }
            triggerBackgroundFlush()
        }
    }

    suspend fun softDeleteContactLocalFirst(contactId: String) = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        contactDao.softDeleteContactWithIndexes(contactId, nowMs)
        pendingSyncDao.enqueue(outboxDelete(SyncEntityType.CONTACT, contactId, nowMs))
        triggerBackgroundFlush()
    }

    suspend fun toggleFavoriteLocalFirst(contactId: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        contactDao.updateFavoriteFlag(contactId, favorite, updatedAtMs = nowMs, syncState = SyncState.PENDING.name)
        pendingSyncDao.enqueue(outboxUpsert(SyncEntityType.CONTACT, contactId, nowMs))
        triggerBackgroundFlush()
    }

    suspend fun toggleBlockedLocalFirst(contactId: String, blocked: Boolean) = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        contactDao.updateBlockedFlag(contactId, blocked, updatedAtMs = nowMs, syncState = SyncState.PENDING.name)
        pendingSyncDao.enqueue(outboxUpsert(SyncEntityType.CONTACT, contactId, nowMs))
        triggerBackgroundFlush()
    }

    suspend fun toggleMutedLocalFirst(contactId: String, muted: Boolean) = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        contactDao.updateMutedFlag(contactId, muted, updatedAtMs = nowMs, syncState = SyncState.PENDING.name)
        pendingSyncDao.enqueue(outboxUpsert(SyncEntityType.CONTACT, contactId, nowMs))
        triggerBackgroundFlush()
    }

    suspend fun recordCallLogLocalFirst(entry: AppCallLogEntity, enqueueCloudSync: Boolean = true) =
        withContext(Dispatchers.IO) {
            callLogDao.upsert(entry.copy(syncState = SyncState.PENDING.name))
            entry.contactId?.takeIf { it.isNotBlank() }?.let { cid ->
                contactDao.updateLastContacted(cid, entry.timestampMs)
                if (enqueueCloudSync) {
                    pendingSyncDao.enqueue(outboxUpsert(SyncEntityType.CONTACT, cid, entry.updatedAtEpochMs))
                }
            }
            if (enqueueCloudSync) {
                pendingSyncDao.enqueue(outboxUpsert(SyncEntityType.CALL_LOG, entry.id, entry.updatedAtEpochMs))
                triggerBackgroundFlush()
            }
        }

    suspend fun ingestDeviceCallLogsIntoLocalDb(entries: List<AppCallLogEntity>) =
        withContext(Dispatchers.IO) {
            if (entries.isEmpty()) return@withContext
            callLogDao.insertIgnore(entries)
        }

    suspend fun softDeleteCallLogLocalFirst(callLogId: String) = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        callLogDao.softDelete(callLogId, nowMs)
        pendingSyncDao.enqueue(outboxDelete(SyncEntityType.CALL_LOG, callLogId, nowMs))
        triggerBackgroundFlush()
    }

    suspend fun restoreCallLogLocalFirst(callLogId: String) = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        callLogDao.restore(callLogId, nowMs)
        pendingSyncDao.enqueue(outboxUpsert(SyncEntityType.CALL_LOG, callLogId, nowMs))
        triggerBackgroundFlush()
    }

    suspend fun saveTranscriptLocalFirst(record: CallTranscriptRecord, enqueueCloudSync: Boolean = true) =
        withContext(Dispatchers.IO) {
            val nowMs = System.currentTimeMillis()
            val updated = record.copy(
                updatedAtEpochMs = nowMs,
                syncState = SyncState.PENDING.name,
                audioCachedLocally = LocalMediaCacheManager.isAudioCachedLocally(
                    appContext,
                    record.audioFilePath,
                    record.audioFileName,
                ),
            )
            transcriptDao.upsert(
                CallTranscriptEntity.fromDomain(
                    record = updated,
                    nowMs = nowMs,
                    syncState = SyncState.PENDING,
                )
            )
            LocalMediaCacheManager.cacheTranscriptPayloadLocally(appContext, updated)
            if (enqueueCloudSync) {
                pendingSyncDao.enqueue(outboxUpsert(SyncEntityType.CALL_TRANSCRIPT, updated.id, nowMs))
                triggerBackgroundFlush()
            }
        }

    suspend fun markTranscriptCalendarAdded(transcriptId: String) = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        transcriptDao.markCalendarAdded(transcriptId, nowMs)
        transcriptDao.getById(transcriptId)?.toDomain()?.let {
            LocalMediaCacheManager.cacheTranscriptPayloadLocally(appContext, it)
        }
        pendingSyncDao.enqueue(outboxUpsert(SyncEntityType.CALL_TRANSCRIPT, transcriptId, nowMs))
        triggerBackgroundFlush()
    }

    suspend fun markTranscriptNotesSaved(transcriptId: String) = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        transcriptDao.markNotesSaved(transcriptId, nowMs)
        transcriptDao.getById(transcriptId)?.toDomain()?.let {
            LocalMediaCacheManager.cacheTranscriptPayloadLocally(appContext, it)
        }
        pendingSyncDao.enqueue(outboxUpsert(SyncEntityType.CALL_TRANSCRIPT, transcriptId, nowMs))
        triggerBackgroundFlush()
    }

    suspend fun softDeleteTranscriptLocalFirst(transcriptId: String) = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        val existing = transcriptDao.getById(transcriptId)
        transcriptDao.softDelete(transcriptId, nowMs)
        LocalMediaCacheManager.deleteCachedMediaForTranscript(
            context = appContext,
            recordId = transcriptId,
            audioFilePath = existing?.audioFilePath,
            audioFileName = existing?.audioFileName,
        )
        pendingSyncDao.enqueue(outboxDelete(SyncEntityType.CALL_TRANSCRIPT, transcriptId, nowMs))
        triggerBackgroundFlush()
    }

    suspend fun saveCategoriesLocalFirst(nodes: List<CategoryNode>) = withContext(Dispatchers.IO) {
        if (nodes.isEmpty()) return@withContext
        val nowMs = System.currentTimeMillis()
        categoryDao.upsertAll(
            nodes.map { CategoryEntity(it.id, it.name, it.parentPath, it.fullPath, it.depth.toInt(), it.iconKey) }
        )
        pendingSyncDao.enqueueBatch(
            nodes.map { outboxUpsert(SyncEntityType.CATEGORY, it.id, nowMs) }
        )
        triggerBackgroundFlush()
    }

    suspend fun saveWhatsAppLogLocalFirst(log: WhatsAppLog) = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        val isolated = log.copy(syncedToPhone = false)
        whatsAppLogDao.insert(
            WhatsAppLogEntity(
                id = isolated.id,
                contactId = isolated.contactId,
                contactName = isolated.contactName,
                phone = isolated.phone,
                loggedAtEpochMs = isolated.loggedAtEpochMs,
                syncedToPhone = false,
            )
        )
        pendingSyncDao.enqueue(outboxUpsert(SyncEntityType.WHATSAPP_LOG, isolated.id, nowMs))
        triggerBackgroundFlush()
    }

    suspend fun savePaymentTemplateLocalFirst(template: PaymentTemplate) = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        paymentDao.upsert(PaymentTemplateEntity(template.id, template.name, template.urlTemplate, template.isDefault))
        pendingSyncDao.enqueue(outboxUpsert(SyncEntityType.PAYMENT_TEMPLATE, template.id, nowMs))
        triggerBackgroundFlush()
    }

    suspend fun deletePaymentTemplateLocalFirst(templateId: String) = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        paymentDao.deleteById(templateId)
        pendingSyncDao.enqueue(outboxDelete(SyncEntityType.PAYMENT_TEMPLATE, templateId, nowMs))
        triggerBackgroundFlush()
    }

    // --- Two-Way Cloud Flush & Conflict-Safe Pull ---

    fun triggerBackgroundFlush() {
        val uid = activeUserId ?: return
        if (!_isOnline.value) return
        engineScope.launch {
            flushPendingQueue(uid)
        }
    }

    suspend fun syncNow(userId: String): Result<Int> = withContext(Dispatchers.IO) {
        activeUserId = userId
        runCatching {
            val pushed = flushPendingQueue(userId)
            pullRemoteMetadataCatchUp(userId)
            prefs.setLastSyncMs(System.currentTimeMillis())
            pushed
        }
    }

    suspend fun flushPendingQueue(userId: String): Int = flushMutex.withLock {
        if (!_isOnline.value) return 0
        _isSyncing.value = true
        var syncedCount = 0
        try {
            while (true) {
                val batch = pendingSyncDao.getPendingBatch(limit = 60)
                if (batch.isEmpty()) break

                val contactUpserts = batch.filter {
                    it.entityType == SyncEntityType.CONTACT.name && it.operation == SyncOperation.UPSERT.name
                }
                val contactDeletes = batch.filter {
                    it.entityType == SyncEntityType.CONTACT.name && it.operation == SyncOperation.DELETE.name
                }
                val categoryUpserts = batch.filter {
                    it.entityType == SyncEntityType.CATEGORY.name && it.operation == SyncOperation.UPSERT.name
                }
                val callLogUpserts = batch.filter {
                    it.entityType == SyncEntityType.CALL_LOG.name && it.operation == SyncOperation.UPSERT.name
                }
                val others = batch.filter { item ->
                    item !in contactUpserts && item !in contactDeletes && item !in categoryUpserts && item !in callLogUpserts
                }

                var hadBlockingFailure = false

                // Batch Contact Upserts
                if (contactUpserts.isNotEmpty()) {
                    val entities = contactDao.getByIds(contactUpserts.map { it.entityId })
                    val domains = entities.map { it.toDomain().copy(syncedToDevice = false) }
                    if (domains.isNotEmpty()) {
                        val batchRes = firestoreRepo.batchUpsertContacts(userId, domains)
                        if (batchRes.isSuccess) {
                            contactDao.markSyncState(entities.map { it.id }, SyncState.SYNCED.name)
                            pendingSyncDao.removeBatch(contactUpserts.map { it.queueKey })
                            syncedCount += entities.size
                        } else {
                            // Fall back per-doc so existing docs preserve Firestore createdAt
                            var allFallbackOk = true
                            val byId = contactUpserts.associateBy { it.entityId }
                            domains.forEach { domain ->
                                val item = byId[domain.id]
                                firestoreRepo.upsertContact(userId, domain).onSuccess {
                                    contactDao.markSyncState(listOf(domain.id), SyncState.SYNCED.name)
                                    item?.let { pendingSyncDao.remove(it.queueKey) }
                                    syncedCount++
                                }.onFailure { err ->
                                    allFallbackOk = false
                                    item?.let {
                                        pendingSyncDao.markRetryFailure(it.queueKey, err.localizedMessage ?: "Sync error")
                                    }
                                    _lastError.value = err.localizedMessage
                                }
                            }
                            if (!allFallbackOk) hadBlockingFailure = true
                        }
                    } else {
                        pendingSyncDao.removeBatch(contactUpserts.map { it.queueKey })
                    }
                }

                // Batch Contact Deletes
                if (contactDeletes.isNotEmpty()) {
                    val ids = contactDeletes.map { it.entityId }
                    firestoreRepo.batchDeleteContacts(userId, ids).onSuccess {
                        pendingSyncDao.removeBatch(contactDeletes.map { it.queueKey })
                        syncedCount += ids.size
                    }.onFailure { err ->
                        hadBlockingFailure = true
                        contactDeletes.forEach {
                            pendingSyncDao.markRetryFailure(it.queueKey, err.localizedMessage ?: "Delete error")
                        }
                        _lastError.value = err.localizedMessage
                    }
                }

                // Batch Category Upserts
                if (categoryUpserts.isNotEmpty()) {
                    val nodes = categoryUpserts.mapNotNull { categoryDao.getById(it.entityId)?.toDomain() }
                    if (nodes.isNotEmpty()) {
                        firestoreRepo.upsertCategories(userId, nodes).onSuccess {
                            pendingSyncDao.removeBatch(categoryUpserts.map { it.queueKey })
                            syncedCount += nodes.size
                        }.onFailure { err ->
                            hadBlockingFailure = true
                            categoryUpserts.forEach {
                                pendingSyncDao.markRetryFailure(it.queueKey, err.localizedMessage ?: "Category sync error")
                            }
                        }
                    } else {
                        pendingSyncDao.removeBatch(categoryUpserts.map { it.queueKey })
                    }
                }

                // Batch CallLog Upserts
                if (callLogUpserts.isNotEmpty()) {
                    val logEntities = callLogUpserts.mapNotNull { callLogDao.getById(it.entityId) }
                    if (logEntities.isNotEmpty()) {
                        val batchRes = firestoreRepo.batchUpsertCallLogs(userId, logEntities.map { it.toCloudModel() })
                        if (batchRes.isSuccess) {
                            logEntities.forEach { callLogDao.markSyncState(it.id, SyncState.SYNCED.name) }
                            pendingSyncDao.removeBatch(callLogUpserts.map { it.queueKey })
                            syncedCount += logEntities.size
                        } else {
                            var allLogsOk = true
                            val byId = callLogUpserts.associateBy { it.entityId }
                            logEntities.forEach { logEntity ->
                                val item = byId[logEntity.id]
                                firestoreRepo.upsertCallLog(userId, logEntity.toCloudModel()).onSuccess {
                                    callLogDao.markSyncState(logEntity.id, SyncState.SYNCED.name)
                                    item?.let { pendingSyncDao.remove(it.queueKey) }
                                    syncedCount++
                                }.onFailure { err ->
                                    allLogsOk = false
                                    item?.let {
                                        pendingSyncDao.markRetryFailure(it.queueKey, err.localizedMessage ?: "Call log error")
                                    }
                                }
                            }
                            if (!allLogsOk) hadBlockingFailure = true
                        }
                    } else {
                        pendingSyncDao.removeBatch(callLogUpserts.map { it.queueKey })
                    }
                }

                // Process Remaining Single Operations (Transcripts, Deletes, WhatsApp Logs, Templates)
                for (item in others) {
                    val success = processSingleOutboxItem(userId, item)
                    if (success) {
                        pendingSyncDao.remove(item.queueKey)
                        syncedCount++
                    } else {
                        hadBlockingFailure = true
                    }
                }

                if (batch.size < 60 || hadBlockingFailure) break
            }
            if (syncedCount > 0) {
                prefs.setLastSyncMs(System.currentTimeMillis())
                _lastError.value = null
            }
            syncedCount
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _lastError.value = e.localizedMessage
            syncedCount
        } finally {
            _isSyncing.value = false
        }
    }

    private suspend fun processSingleOutboxItem(userId: String, item: PendingSyncEntity): Boolean {
        val type = runCatching { SyncEntityType.valueOf(item.entityType) }.getOrNull() ?: return true
        val op = runCatching { SyncOperation.valueOf(item.operation) }.getOrNull() ?: return true

        val res: Result<*> = when (type) {
            SyncEntityType.CONTACT -> {
                if (op == SyncOperation.DELETE) {
                    firestoreRepo.deleteContact(userId, item.entityId)
                } else {
                    val entity = contactDao.getById(item.entityId) ?: return true
                    firestoreRepo.upsertContact(userId, entity.toDomain().copy(syncedToDevice = false)).onSuccess {
                        contactDao.markSyncState(listOf(entity.id), SyncState.SYNCED.name)
                    }
                }
            }
            SyncEntityType.CALL_LOG -> {
                if (op == SyncOperation.DELETE) {
                    firestoreRepo.deleteCallLog(userId, item.entityId)
                } else {
                    val entity = callLogDao.getById(item.entityId) ?: return true
                    firestoreRepo.upsertCallLog(userId, entity.toCloudModel()).onSuccess {
                        callLogDao.markSyncState(entity.id, SyncState.SYNCED.name)
                    }
                }
            }
            SyncEntityType.CALL_TRANSCRIPT -> {
                if (op == SyncOperation.DELETE) {
                    firestoreRepo.deleteCallTranscript(userId, item.entityId)
                } else {
                    val entity = transcriptDao.getById(item.entityId) ?: return true
                    firestoreRepo.upsertCallTranscript(userId, entity.toCloudModel()).onSuccess {
                        transcriptDao.markSyncState(entity.id, SyncState.SYNCED.name)
                    }
                }
            }
            SyncEntityType.CATEGORY -> {
                if (op == SyncOperation.DELETE) {
                    firestoreRepo.deleteCategory(userId, item.entityId)
                } else {
                    val node = categoryDao.getById(item.entityId)?.toDomain() ?: return true
                    firestoreRepo.upsertCategories(userId, listOf(node))
                }
            }
            SyncEntityType.PAYMENT_TEMPLATE -> {
                if (op == SyncOperation.DELETE) {
                    firestoreRepo.deletePaymentTemplate(userId, item.entityId)
                } else {
                    val tpl = paymentDao.getById(item.entityId)?.toDomain() ?: return true
                    firestoreRepo.upsertPaymentTemplate(userId, tpl)
                }
            }
            SyncEntityType.WHATSAPP_LOG -> {
                val log = whatsAppLogDao.getById(item.entityId)?.toDomain() ?: return true
                firestoreRepo.createWhatsAppLog(userId, log)
            }
        }

        return res.onFailure { err ->
            pendingSyncDao.markRetryFailure(item.queueKey, err.localizedMessage ?: "Sync error")
            _lastError.value = err.localizedMessage
        }.isSuccess
    }

    private suspend fun pullRemoteMetadataCatchUp(userId: String) {
        firestoreRepo.pullCategories(userId).onSuccess { cats ->
            if (cats.isNotEmpty()) {
                categoryDao.upsertAll(
                    cats.map { CategoryEntity(it.id, it.name, it.parentPath, it.fullPath, it.depth.toInt(), it.iconKey) }
                )
            }
        }
        firestoreRepo.pullCallLogs(userId).onSuccess { mergePulledCallLogs(it) }
        firestoreRepo.pullCallTranscripts(userId).onSuccess { mergePulledTranscripts(it) }
    }

    private suspend fun mergePulledContacts(remoteContacts: List<ContactRecord>) {
        val toSave = ArrayList<ContactEntity>(remoteContacts.size)
        for (remote in remoteContacts) {
            val local = contactDao.getByIdEvenIfDeleted(remote.id)
            val hasPending = pendingSyncDao.getByEntity(SyncEntityType.CONTACT.name, remote.id) != null
            val (resolved, needsPush) = SyncConflictResolver.resolveContact(local, remote, hasPending)
            if (!resolved.isDeleted) {
                toSave += resolved
            }
            if (needsPush && !hasPending) {
                pendingSyncDao.enqueue(outboxUpsert(SyncEntityType.CONTACT, resolved.id, resolved.updatedAtEpochMs))
            }
        }
        if (toSave.isNotEmpty()) {
            contactDao.upsertBatchWithIndexes(toSave, emptyList(), categoryDao)
        }
    }

    private suspend fun mergePulledCallLogs(remoteLogs: List<CloudCallLogRecord>) {
        val merged = ArrayList<AppCallLogEntity>(remoteLogs.size)
        for (cloud in remoteLogs) {
            val local = callLogDao.getByIdEvenIfDeleted(cloud.id)
            val hasPending = pendingSyncDao.getByEntity(SyncEntityType.CALL_LOG.name, cloud.id) != null
            val remoteUpdated = cloud.clientUpdatedAtMs.takeIf { it > 0L }
                ?: cloud.updatedAt?.toDate()?.time
                ?: cloud.timestampMs
            if (local == null) {
                merged += AppCallLogEntity.fromCloud(cloud)
            } else {
                val decision = SyncConflictResolver.resolve(
                    localUpdatedAtMs = local.updatedAtEpochMs,
                    remoteUpdatedAtMs = remoteUpdated,
                    hasPendingLocalEdit = hasPending,
                    localIsDeleted = local.isDeleted,
                )
                if (decision == ConflictResolution.ACCEPT_REMOTE) {
                    merged += AppCallLogEntity.fromCloud(cloud)
                }
            }
        }
        if (merged.isNotEmpty()) {
            callLogDao.upsertAll(merged)
        }
    }

    private suspend fun mergePulledTranscripts(remoteTranscripts: List<CloudCallTranscriptRecord>) {
        val merged = ArrayList<CallTranscriptEntity>(remoteTranscripts.size)
        for (cloud in remoteTranscripts) {
            val local = transcriptDao.getByIdEvenIfDeleted(cloud.id)
            val hasPending = pendingSyncDao.getByEntity(SyncEntityType.CALL_TRANSCRIPT.name, cloud.id) != null
            val remoteUpdated = cloud.clientUpdatedAtMs.takeIf { it > 0L }
                ?: cloud.updatedAt?.toDate()?.time
                ?: cloud.timestampMs

            val cachedFile: File? = LocalMediaCacheManager.resolvePlayableAudioFile(
                context = appContext,
                audioFilePath = local?.audioFilePath,
                audioFileName = cloud.audioFileName,
            )

            if (local == null) {
                val entity = CallTranscriptEntity.fromCloud(
                    cloud = cloud,
                    existingLocalPath = cachedFile?.absolutePath,
                    audioCachedLocally = cachedFile != null,
                )
                merged += entity
                LocalMediaCacheManager.cacheTranscriptPayloadLocally(appContext, entity.toDomain())
            } else {
                val decision = SyncConflictResolver.resolve(
                    localUpdatedAtMs = local.updatedAtEpochMs,
                    remoteUpdatedAtMs = remoteUpdated,
                    hasPendingLocalEdit = hasPending,
                    localIsDeleted = local.isDeleted,
                )
                if (decision == ConflictResolution.ACCEPT_REMOTE) {
                    val entity = CallTranscriptEntity.fromCloud(
                        cloud = cloud,
                        existingLocalPath = cachedFile?.absolutePath ?: local.audioFilePath,
                        audioCachedLocally = cachedFile != null,
                    )
                    merged += entity
                    LocalMediaCacheManager.cacheTranscriptPayloadLocally(appContext, entity.toDomain())
                }
            }
        }
        if (merged.isNotEmpty()) {
            transcriptDao.upsertAll(merged)
        }
    }

    // --- Unified Offline Queries (Pre-Call Context, Enriched Recents, Global Search) ---

    fun observeRecentCallLogsEnriched(limit: Int = 250): Flow<List<CallLogEntry>> {
        return callLogDao.observeRecentCalls(limit).map { entities ->
            if (entities.isEmpty()) return@map emptyList()
            val keys = entities.map { it.phoneKey }.filter { it.length >= 4 }.distinct()
            val matchedByKey = mutableMapOf<String, ContactRecord>()
            for (chunk in keys.chunked(400)) {
                contactDao.findAllByPhoneKeys(chunk).forEach { match ->
                    matchedByKey.putIfAbsent(match.phoneKey, match.contact.toDomain())
                }
            }
            entities.map { entity ->
                entity.toDomain(matchedContact = matchedByKey[entity.phoneKey])
            }
        }
    }

    fun observeCallLogsForContact(contact: ContactRecord): Flow<List<CallLogEntry>> {
        val primaryKey = PhoneUtils.phoneSearchKey(contact.primaryPhone)
        return callLogDao.observeForContactOrPhone(contact.id, primaryKey).map { entities ->
            entities.map { it.toDomain(matchedContact = contact) }
        }
    }

    suspend fun searchGlobal(query: String): GlobalSearchResult = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext GlobalSearchResult()
        val norm = ContactEntity.normalizeForSearch(clean)

        val contacts = contactDao.searchContacts(norm, limit = 60, offset = 0).map { it.toDomain() }
        val transcripts = transcriptDao.searchTranscripts(clean, limit = 40).map { it.toDomain() }
        val rawLogs = callLogDao.searchCallLogs(clean, limit = 40)

        val keys = rawLogs.map { it.phoneKey }.filter { it.length >= 4 }.distinct()
        val matchedByKey = mutableMapOf<String, ContactRecord>()
        if (keys.isNotEmpty()) {
            contactDao.findAllByPhoneKeys(keys).forEach { m ->
                matchedByKey.putIfAbsent(m.phoneKey, m.contact.toDomain())
            }
        }
        val logs = rawLogs.map { it.toDomain(matchedContact = matchedByKey[it.phoneKey]) }

        GlobalSearchResult(
            contacts = contacts,
            matchedTranscripts = transcripts,
            matchedCallLogs = logs,
        )
    }

    suspend fun getPreCallContext(contactId: String?, phone: String): PreCallContext? = withContext(Dispatchers.IO) {
        val phoneKey = PhoneUtils.phoneSearchKey(phone)
        if (contactId.isNullOrBlank() && phoneKey.length < 4) return@withContext null

        val logs = callLogDao.getForContactOrPhoneOnce(contactId, phoneKey, limit = 50)
        val latestTranscript = transcriptDao.getLatestForContactOrPhone(
            contactId = contactId.orEmpty(),
            phoneSuffix = phoneKey,
        )?.toDomain()

        val lastLog = logs.firstOrNull()
        val summary = latestTranscript?.oneLineSummary?.takeIf { it.isNotBlank() }
            ?: latestTranscript?.highlights?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: latestTranscript?.extractedNotes?.takeIf { it.isNotBlank() }

        val pendingActions = latestTranscript?.actionItems?.count { !it.completed } ?: 0
        if (lastLog == null && latestTranscript == null) return@withContext null

        PreCallContext(
            contactId = contactId ?: latestTranscript?.contactId ?: lastLog?.contactId,
            phoneNumber = phone.ifBlank { lastLog?.phoneNumber.orEmpty() },
            lastSpokeEpochMs = lastLog?.timestampMs ?: latestTranscript?.timestampMs,
            lastCallDurationSec = lastLog?.durationSec ?: latestTranscript?.durationSec ?: 0L,
            lastCallType = lastLog?.callType,
            oneLineSummary = summary,
            pendingActionItemsCount = pendingActions,
            totalCallsCount = logs.size,
        )
    }

    private fun outboxUpsert(type: SyncEntityType, id: String, updatedAtMs: Long) = PendingSyncEntity(
        queueKey = PendingSyncEntity.keyFor(type.name, id),
        entityType = type.name,
        entityId = id,
        operation = SyncOperation.UPSERT.name,
        enqueuedAtEpochMs = System.currentTimeMillis(),
        entityUpdatedAtEpochMs = updatedAtMs,
    )

    private fun outboxDelete(type: SyncEntityType, id: String, deletedAtMs: Long) = PendingSyncEntity(
        queueKey = PendingSyncEntity.keyFor(type.name, id),
        entityType = type.name,
        entityId = id,
        operation = SyncOperation.DELETE.name,
        enqueuedAtEpochMs = System.currentTimeMillis(),
        entityUpdatedAtEpochMs = deletedAtMs,
    )
}
