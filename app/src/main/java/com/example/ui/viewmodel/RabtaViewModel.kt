package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiCommandAction
import com.example.ai.RabtaAiAssistant
import com.example.data.device.CallLogReader
import com.example.data.device.DeviceContactsManager
import com.example.data.local.CallTranscriptEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.ContactEntity
import com.example.data.local.DialHistoryEntity
import com.example.data.local.PaymentTemplateEntity
import com.example.data.local.RabtaDatabase
import com.example.data.local.SyncPrefsDataStore
import com.example.data.local.WhatsAppLogEntity
import com.example.data.remote.FirestoreContactsRepository
import com.example.data.remote.GitHubUpdateService
import com.example.data.remote.GoogleDriveScanner
import com.example.domain.model.CallLogEntry
import com.example.domain.model.CallTranscriptRecord
import com.example.domain.model.CategoryNode
import com.example.domain.model.CategorySummary
import com.example.domain.model.ContactRecord
import com.example.domain.model.CsvImportSummary
import com.example.domain.model.DialHistoryEntry
import com.example.domain.model.GitHubReleaseInfo
import com.example.domain.model.PaymentTemplate
import com.example.domain.model.WhatsAppLog
import com.example.telecom.CallRecorderManager
import com.example.telecom.CallRecordingItem
import com.example.ui.theme.CalmPalette
import com.example.ui.theme.ThemeMode
import com.example.util.ContactDeduplicator
import com.example.util.CsvParser
import com.example.util.EgyptGeoLookup
import com.example.util.PaymentUtils
import com.example.util.PhoneUtils
import com.example.util.SimPreference
import com.example.util.SmartCategoryEngine
import com.example.util.SystemIntents
import com.example.util.VCardUtils
import com.example.widget.RabtaWidget
import com.google.firebase.firestore.DocumentSnapshot
import java.io.File
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface AiAssistantUiState {
    data object Idle : AiAssistantUiState
    data object Loading : AiAssistantUiState
    data class Success(
        val reply: String,
        val actionBadge: String,
        val matchedContacts: List<ContactRecord> = emptyList(),
        val suggestedMessage: String? = null,
        val paymentAmount: String? = null,
    ) : AiAssistantUiState
    data class Error(val message: String) : AiAssistantUiState
}

class RabtaViewModel(
    private val appContext: Context,
    private val currentUserId: String,
    private val currentUserName: String,
    private val currentUserEmail: String?,
    private val db: RabtaDatabase,
    private val firestoreRepo: FirestoreContactsRepository,
    private val callLogReader: CallLogReader,
    private val deviceContactsManager: DeviceContactsManager,
    private val prefs: SyncPrefsDataStore,
    private val gitHubService: GitHubUpdateService,
    private val aiAssistant: RabtaAiAssistant = RabtaAiAssistant(),
) : ViewModel() {

    private val contactDao = db.contactDao()
    private val categoryDao = db.categoryDao()
    private val logDao = db.whatsAppLogDao()
    private val paymentDao = db.paymentTemplateDao()
    private val transcriptDao = db.callTranscriptDao()
    private val dialHistoryDao = db.dialHistoryDao()
    private val processingTranscriptIds = Collections.synchronizedSet(HashSet<String>())

    val totalContactsCount: StateFlow<Int> = contactDao.observeTotalCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val favoriteContacts: StateFlow<List<ContactRecord>> = contactDao.observeFavorites()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val geoContacts: StateFlow<List<ContactRecord>> = contactDao.observeGeoContacts()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncedDeviceContacts: StateFlow<List<ContactRecord>> = contactDao.observeSyncedContacts()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentWhatsAppLogs: StateFlow<List<WhatsAppLog>> = logDao.observeRecentLogs()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weeklyActiveWhatsAppLogs: StateFlow<List<WhatsAppLog>> = logDao
        .observeLogsSince(System.currentTimeMillis() - SEVEN_DAYS_MS)
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val paymentTemplates: StateFlow<List<PaymentTemplate>> = paymentDao.observeAll()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PaymentUtils.defaultTemplates)

    val callTranscripts: StateFlow<List<CallTranscriptRecord>> = transcriptDao.observeAll()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dialHistory: StateFlow<List<DialHistoryEntry>> = dialHistoryDao.observeActiveHistory()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyDeletedDialHistory: StateFlow<List<DialHistoryEntry>> = dialHistoryDao.observeRecentlyDeleted()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val githubRepoSlug: StateFlow<String> = prefs.githubRepoFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val bundledImported: StateFlow<Boolean> = prefs.bundledImportedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val themeMode: StateFlow<ThemeMode> = prefs.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val calmPalette: StateFlow<CalmPalette> = prefs.paletteFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalmPalette.SLATE)

    val autoRecordCalls: StateFlow<Boolean> = prefs.autoRecordCallsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showContactPhotos: StateFlow<Boolean> = prefs.showContactPhotosFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val callPhotoBackground: StateFlow<Boolean> = prefs.callPhotoBackgroundFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val strictPhonebookIsolation: StateFlow<Boolean> = prefs.strictPhonebookIsolationFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoTranscribeCalls: StateFlow<Boolean> = prefs.autoTranscribeCallsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val simPreference: StateFlow<SimPreference> = prefs.simPreferenceFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SimPreference.ASK_OR_BOTH)

    val preferredSimSlot: StateFlow<Int?> = simPreference
        .map { pref ->
            when (pref) {
                SimPreference.ASK_OR_BOTH -> null
                SimPreference.SIM_1 -> 0
                SimPreference.SIM_2 -> 1
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ContactRecord>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _dialpadMatches = MutableStateFlow<List<ContactRecord>>(emptyList())
    val dialpadMatches = _dialpadMatches.asStateFlow()

    private val _frequentCalls = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val frequentCalls = _frequentCalls.asStateFlow()

    private val _recentCalls = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val recentCalls = _recentCalls.asStateFlow()

    private val _hasCallLogPermission = MutableStateFlow(callLogReader.hasPermission())
    val hasCallLogPermission = _hasCallLogPermission.asStateFlow()

    private val _currentCategoryPath = MutableStateFlow("")
    val currentCategoryPath = _currentCategoryPath.asStateFlow()

    private val _childCategories = MutableStateFlow<List<CategorySummary>>(emptyList())
    val childCategories = _childCategories.asStateFlow()

    private val _categoryContacts = MutableStateFlow<List<ContactRecord>>(emptyList())
    val categoryContacts = _categoryContacts.asStateFlow()

    private val _selectedContact = MutableStateFlow<ContactRecord?>(null)
    val selectedContact = _selectedContact.asStateFlow()

    private val _selectedForBulk = MutableStateFlow<Set<String>>(emptySet())
    val selectedForBulk = _selectedForBulk.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy = _isBusy.asStateFlow()

    private val _importProgressText = MutableStateFlow<String?>(null)
    val importProgressText = _importProgressText.asStateFlow()

    private val _cloudSyncStatus = MutableStateFlow<String?>(null)
    val cloudSyncStatus = _cloudSyncStatus.asStateFlow()

    private val _releaseInfo = MutableStateFlow<GitHubReleaseInfo?>(null)
    val releaseInfo = _releaseInfo.asStateFlow()

    private val _autoUpdateRelease = MutableStateFlow<GitHubReleaseInfo?>(null)
    val autoUpdateRelease = _autoUpdateRelease.asStateFlow()

    private val _pendingWhatsAppCapture = MutableStateFlow<ContactRecord?>(null)
    val pendingWhatsAppCapture = _pendingWhatsAppCapture.asStateFlow()

    private val _aiUiState = MutableStateFlow<AiAssistantUiState>(AiAssistantUiState.Idle)
    val aiUiState = _aiUiState.asStateFlow()

    private val _callRecordings = MutableStateFlow<List<CallRecordingItem>>(emptyList())
    val callRecordings = _callRecordings.asStateFlow()

    private var searchJob: Job? = null
    private var dialpadJob: Job? = null
    private var cloudUploadJob: Job? = null

    init {
        initializeSession()
    }

    private fun initializeSession() {
        viewModelScope.launch {
            firestoreRepo.ensureUserProfile(currentUserId, currentUserName, currentUserEmail)
            withContext(Dispatchers.IO) {
                paymentDao.upsertAll(
                    PaymentUtils.defaultTemplates.map {
                        PaymentTemplateEntity(it.id, it.name, it.urlTemplate, it.isDefault)
                    }
                )
            }
            refreshCallLogs()
            loadCategoryLevel("")
            refreshRecordings()

            launch {
                firestoreRepo.observeWhatsAppLogs(currentUserId)
                    .catch { }
                    .collect { cloudLogs ->
                        if (cloudLogs.isNotEmpty()) {
                            withContext(Dispatchers.IO) {
                                logDao.upsertAll(
                                    cloudLogs.map {
                                        WhatsAppLogEntity(
                                            id = it.id,
                                            contactId = it.contactId,
                                            contactName = it.contactName,
                                            phone = it.phone,
                                            loggedAtEpochMs = it.loggedAtEpochMs,
                                            syncedToPhone = it.syncedToPhone,
                                        )
                                    }
                                )
                            }
                        }
                    }
            }

            launch {
                firestoreRepo.observePaymentTemplates(currentUserId)
                    .catch { }
                    .collect { templates ->
                        if (templates.isNotEmpty()) {
                            withContext(Dispatchers.IO) {
                                paymentDao.upsertAll(
                                    templates.map { PaymentTemplateEntity(it.id, it.name, it.urlTemplate, it.isDefault) }
                                )
                            }
                        }
                    }
            }

            launch {
                val savedRepo = prefs.githubRepoFlow.firstOrNull().orEmpty().trim()
                if (savedRepo.isNotBlank()) {
                    gitHubService.checkLatestRelease(savedRepo).onSuccess { info ->
                        _releaseInfo.value = info
                        if (info.isUpdateAvailable) {
                            _autoUpdateRelease.value = info
                        }
                    }
                }
            }

            launch {
                transcriptDao.observeAll()
                    .catch { }
                    .collect { entities ->
                        if (autoTranscribeCalls.value) {
                            entities
                                .filter { it.status == "PENDING" && it.audioFilePath.isNotBlank() }
                                .filter { processingTranscriptIds.add(it.id) }
                                .take(2)
                                .forEach { pending ->
                                    launch {
                                        analyzeCallTranscriptInternal(pending.toDomain())
                                    }
                                }
                        }
                    }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setCalmPalette(palette: CalmPalette) {
        viewModelScope.launch { prefs.setPalette(palette) }
    }

    fun setAutoRecordCalls(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoRecordCalls(enabled) }
    }

    fun setShowContactPhotos(enabled: Boolean) {
        viewModelScope.launch { prefs.setShowContactPhotos(enabled) }
    }

    fun setCallPhotoBackground(enabled: Boolean) {
        viewModelScope.launch { prefs.setCallPhotoBackground(enabled) }
    }

    fun setStrictPhonebookIsolation(enabled: Boolean) {
        viewModelScope.launch { prefs.setStrictPhonebookIsolation(enabled) }
    }

    fun setAutoTranscribeCalls(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoTranscribeCalls(enabled) }
    }

    fun setSimPreference(pref: SimPreference) {
        viewModelScope.launch { prefs.setSimPreference(pref) }
    }

    fun refreshRecordings() {
        viewModelScope.launch(Dispatchers.IO) {
            _callRecordings.value = CallRecorderManager.listRecordings(appContext)
        }
    }

    fun deleteRecording(item: CallRecordingItem) {
        viewModelScope.launch(Dispatchers.IO) {
            CallRecorderManager.deleteRecording(item.file)
            _callRecordings.value = CallRecorderManager.listRecordings(appContext)
        }
        _statusMessage.value = "تم حذف التسجيل"
    }

    fun updateContactPhoto(contact: ContactRecord, photoPath: String?) {
        viewModelScope.launch {
            val updated = contact.copy(photoUri = photoPath?.takeIf { it.isNotBlank() })
            withContext(Dispatchers.IO) {
                contactDao.upsertSingleWithIndexes(ContactEntity.fromDomain(updated))
            }
            if (_selectedContact.value?.id == updated.id) {
                _selectedContact.value = updated
            }
            loadCategoryLevel(_currentCategoryPath.value)
            if (_searchQuery.value.isNotBlank()) updateSearchQuery(_searchQuery.value)
            _statusMessage.value = if (photoPath.isNullOrBlank()) "تمت إزالة الصورة" else "تم تحديث صورة ${updated.displayName}"
        }
    }

    fun analyzeCallTranscript(record: CallTranscriptRecord) {
        if (record.audioFilePath.isBlank()) {
            _statusMessage.value = "لا يوجد ملف صوتي مسجل لهذه المكالمة"
            return
        }
        viewModelScope.launch {
            processingTranscriptIds.add(record.id)
            analyzeCallTranscriptInternal(record)
        }
    }

    fun transcribeRecordingItem(item: CallRecordingItem) {
        viewModelScope.launch {
            val existing = withContext(Dispatchers.IO) {
                callTranscripts.value.firstOrNull { it.audioFilePath == item.file.absolutePath }
            }
            if (existing != null) {
                analyzeCallTranscript(existing)
                return@launch
            }
            val cleanPhone = item.file.nameWithoutExtension.substringBeforeLast("_").substringAfterLast("_", "")
            val rec = CallTranscriptRecord(
                id = "tr_" + UUID.randomUUID().toString().replace("-", "").take(14),
                contactId = null,
                phone = cleanPhone.ifBlank { item.displayName },
                contactName = item.displayName,
                audioFilePath = item.file.absolutePath,
                timestampMs = item.timestampMs,
                durationSec = 0L,
                status = "PENDING",
            )
            withContext(Dispatchers.IO) {
                transcriptDao.upsert(rec.toEntity())
            }
            analyzeCallTranscript(rec)
        }
    }

    private suspend fun analyzeCallTranscriptInternal(record: CallTranscriptRecord) {
        try {
            val audioFile = File(record.audioFilePath)
            if (!audioFile.exists() || audioFile.length() == 0L) {
                withContext(Dispatchers.IO) {
                    transcriptDao.upsert(record.copy(status = "ERROR", transcript = "ملف الصوت غير موجود أو فارغ").toEntity())
                }
                return
            }
            withContext(Dispatchers.IO) {
                transcriptDao.upsert(record.copy(status = "PROCESSING").toEntity())
            }
            val res = aiAssistant.analyzeCallRecording(
                audioFile = audioFile,
                contactName = record.contactName,
                phone = record.phone,
            )
            res.onSuccess { analysis ->
                val readyRecord = record.copy(
                    transcript = analysis.transcript,
                    highlights = analysis.highlights,
                    extractedNotes = analysis.extractedNotes,
                    suggestedEventTitle = analysis.eventTitle,
                    suggestedEventDateIso = analysis.eventDateIso,
                    suggestedEventEpochMs = analysis.eventEpochMs,
                    suggestedCategory = analysis.suggestedCategory,
                    status = "READY",
                )
                withContext(Dispatchers.IO) {
                    transcriptDao.upsert(readyRecord.toEntity())
                }
                if (analysis.extractedNotes.isNotBlank() && !record.notesSavedToContact) {
                    saveTranscriptNotesToContact(readyRecord, silent = true)
                }
                _statusMessage.value = "تم تفريغ وتحليل مكالمة ${record.contactName} بالذكاء الاصطناعي"
            }.onFailure { err ->
                withContext(Dispatchers.IO) {
                    transcriptDao.upsert(
                        record.copy(
                            status = "ERROR",
                            transcript = err.localizedMessage ?: "تعذر تفريغ التسجيل",
                        ).toEntity()
                    )
                }
            }
        } finally {
            processingTranscriptIds.remove(record.id)
        }
    }

    fun saveTranscriptNotesToContact(record: CallTranscriptRecord, silent: Boolean = false) {
        if (record.extractedNotes.isBlank()) return
        viewModelScope.launch {
            val targetContact = withContext(Dispatchers.IO) {
                record.contactId?.let { contactDao.getById(it)?.toDomain() }
                    ?: run {
                        val key = PhoneUtils.phoneSearchKey(record.phone)
                        if (key.length >= 6) contactDao.findByExactPhoneKey(key)?.toDomain() else null
                    }
            }
            if (targetContact == null) {
                if (!silent) _statusMessage.value = "احفظ الرقم كجهة اتصال أولاً لإرفاق الملاحظة له"
                return@launch
            }
            val datePrefix = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(record.timestampMs))
            val snippet = "[مكالمة $datePrefix] ${record.extractedNotes}"
            val currentNotes = targetContact.notes.orEmpty()
            val updatedNotes = if (currentNotes.contains(record.extractedNotes)) {
                currentNotes
            } else if (currentNotes.isBlank()) {
                snippet
            } else {
                "$currentNotes\n$snippet"
            }
            val updatedContact = targetContact.copy(notes = updatedNotes.take(2500))
            withContext(Dispatchers.IO) {
                contactDao.upsertSingleWithIndexes(ContactEntity.fromDomain(updatedContact))
                transcriptDao.markNotesSaved(record.id)
            }
            if (_selectedContact.value?.id == updatedContact.id) {
                _selectedContact.value = updatedContact
            }
            launch { firestoreRepo.upsertContact(currentUserId, updatedContact) }
            if (!silent) _statusMessage.value = "تمت إضافة ملاحظات المكالمة إلى ${updatedContact.displayName}"
        }
    }

    fun markCalendarEventAdded(transcriptId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            transcriptDao.markCalendarAdded(transcriptId)
        }
    }

    fun recordDialHistoryDraft(rawDigits: String, didCall: Boolean) {
        val cleaned = rawDigits.trim()
        val digitCount = cleaned.count { it.isDigit() }
        if (digitCount < 4 && !cleaned.startsWith("*")) return

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                val key = PhoneUtils.phoneSearchKey(cleaned)
                val matched = if (key.length >= 6) contactDao.findByExactPhoneKey(key)?.toDomain() else null
                val existing = dialHistoryDao.findActiveByPhone(cleaned)
                val entity = if (existing != null) {
                    existing.copy(
                        matchedContactId = matched?.id ?: existing.matchedContactId,
                        matchedName = matched?.displayName ?: existing.matchedName,
                        didCall = existing.didCall || didCall,
                        typedAtEpochMs = now,
                    )
                } else {
                    DialHistoryEntity(
                        id = "dh_" + UUID.randomUUID().toString().replace("-", "").take(14),
                        phoneNumber = cleaned,
                        matchedContactId = matched?.id,
                        matchedName = matched?.displayName,
                        didCall = didCall,
                        typedAtEpochMs = now,
                        isDeleted = false,
                        deletedAtEpochMs = null,
                    )
                }
                dialHistoryDao.upsert(entity)
            }
            if (didCall) {
                RabtaWidget.refreshAll(appContext)
            }
        }
    }

    fun softDeleteDialHistory(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dialHistoryDao.softDelete(id, System.currentTimeMillis())
        }
        _statusMessage.value = "تم النقل إلى المحذوفات مؤخراً"
    }

    fun restoreDialHistory(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dialHistoryDao.restore(id, System.currentTimeMillis())
        }
        _statusMessage.value = "تم استرجاع الرقم من المحذوفات مؤخراً"
    }

    fun deleteDialHistoryForever(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dialHistoryDao.deleteForever(id)
        }
    }

    fun emptyDialTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            dialHistoryDao.emptyTrash()
        }
        _statusMessage.value = "تم تفريغ سلة الأرقام المحذوفة"
    }

    private fun CallTranscriptRecord.toEntity(): CallTranscriptEntity = CallTranscriptEntity(
        id = id,
        contactId = contactId,
        phone = phone,
        contactName = contactName,
        audioFilePath = audioFilePath,
        timestampMs = timestampMs,
        durationSec = durationSec,
        transcript = transcript,
        highlightsJoined = highlights.joinToString("\u001F"),
        extractedNotes = extractedNotes,
        suggestedEventTitle = suggestedEventTitle,
        suggestedEventDateIso = suggestedEventDateIso,
        suggestedEventEpochMs = suggestedEventEpochMs,
        suggestedCategory = suggestedCategory,
        eventAddedToCalendar = eventAddedToCalendar,
        notesSavedToContact = notesSavedToContact,
        status = status,
    )

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun dismissAutoUpdatePrompt() {
        _autoUpdateRelease.value = null
    }

    fun clearPendingWhatsAppCapture() {
        _pendingWhatsAppCapture.value = null
    }

    fun handleIncomingShareOrDeepLink(rawText: String?, vCardText: String? = null) {
        viewModelScope.launch {
            if (!vCardText.isNullOrBlank()) {
                val parsed = VCardUtils.parseVCards(vCardText).firstOrNull()
                if (parsed != null) {
                    _pendingWhatsAppCapture.value = parsed
                    return@launch
                }
            }
            if (!rawText.isNullOrBlank()) {
                val parsed = VCardUtils.parseQrPayload(rawText) ?: run {
                    val phones = PhoneUtils.extractPhonesFromSharedText(rawText)
                    if (phones.isNotEmpty()) {
                        ContactRecord(
                            displayName = "",
                            primaryPhone = phones.first(),
                            phones = phones,
                            categoryPaths = listOf("واتساب"),
                            notes = rawText.take(300),
                        )
                    } else null
                }
                if (parsed != null) {
                    _pendingWhatsAppCapture.value = parsed
                }
            }
        }
    }

    fun refreshPermissionsState() {
        _hasCallLogPermission.value = callLogReader.hasPermission()
        if (_hasCallLogPermission.value) {
            refreshCallLogs()
        }
    }

    fun refreshCallLogs() {
        viewModelScope.launch {
            _hasCallLogPermission.value = callLogReader.hasPermission()
            if (_hasCallLogPermission.value) {
                val (freq, recent) = callLogReader.loadFrequentAndRecentCalls()
                _frequentCalls.value = freq
                _recentCalls.value = recent
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.trim().isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(150L)
            val cleaned = ContactEntity.normalizeForSearch(query.trim())
            val results = withContext(Dispatchers.IO) {
                contactDao.searchContacts(cleaned, limit = 80, offset = 0).map { it.toDomain() }
            }
            _searchResults.value = results
        }
    }

    fun searchForDialpad(query: String) {
        dialpadJob?.cancel()
        val trimmed = ContactEntity.normalizeForSearch(query.trim())
        if (trimmed.isEmpty()) {
            _dialpadMatches.value = emptyList()
            return
        }
        dialpadJob = viewModelScope.launch {
            delay(100L)
            val results = withContext(Dispatchers.IO) {
                contactDao.searchContacts(trimmed, limit = 15, offset = 0).map { it.toDomain() }
            }
            _dialpadMatches.value = results
        }
    }

    fun loadCategoryLevel(path: String) {
        _currentCategoryPath.value = path
        viewModelScope.launch(Dispatchers.IO) {
            val allCats = categoryDao.getAll()
            val directChildren = if (path.isEmpty()) {
                allCats.filter { it.depth == 0 }
            } else {
                allCats.filter { it.parentPath == path }
            }

            // Single GROUP BY query instead of N LIKE scans
            val paths = directChildren.map { it.fullPath }
            val countsMap = if (paths.isNotEmpty()) {
                paths.chunked(400).flatMap { contactDao.countForCategoryPaths(it) }
                    .associate { it.categoryPath to it.cnt }
            } else {
                emptyMap()
            }

            val summaries = directChildren.map { cat ->
                val childCnt = allCats.count { it.parentPath == cat.fullPath }
                CategorySummary(
                    name = cat.name,
                    fullPath = cat.fullPath,
                    parentPath = cat.parentPath,
                    depth = cat.depth,
                    contactCount = countsMap[cat.fullPath] ?: 0,
                    childCount = childCnt,
                )
            }.sortedByDescending { it.contactCount }

            _childCategories.value = summaries

            val contacts = if (path.isNotBlank()) {
                contactDao.getByCategoryPath(path, limit = 150, offset = 0).map { it.toDomain() }
            } else {
                emptyList()
            }
            _categoryContacts.value = contacts
        }
    }

    fun navigateCategoryUp() {
        val current = _currentCategoryPath.value
        if (current.isEmpty()) return
        val parent = current.substringBeforeLast(" > ", missingDelimiterValue = "")
        loadCategoryLevel(parent)
    }

    fun createCategoryPath(fullPathRaw: String) {
        val segments = fullPathRaw.split(">", "/")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (segments.isEmpty()) return
        viewModelScope.launch {
            val nodes = buildCategoryNodes(setOf(segments.joinToString(" > ")))
            withContext(Dispatchers.IO) {
                categoryDao.upsertAll(nodes.map {
                    CategoryEntity(it.id, it.name, it.parentPath, it.fullPath, it.depth.toInt(), it.iconKey)
                })
            }
            launch { firestoreRepo.upsertCategories(currentUserId, nodes) }
            loadCategoryLevel(_currentCategoryPath.value)
            _statusMessage.value = "تم إنشاء الفئة بنجاح"
        }
    }

    fun openContact(contact: ContactRecord) {
        _selectedContact.value = contact
    }

    fun closeContact() {
        _selectedContact.value = null
    }

    fun saveContact(contact: ContactRecord, syncToCloud: Boolean = true) {
        viewModelScope.launch {
            val id = contact.id.ifBlank { "c_" + UUID.randomUUID().toString().replace("-", "").take(16) }
            val incoming = ContactDeduplicator.enrichSingleContact(contact.copy(id = id))
            val phoneKeys = incoming.phones
                .map { PhoneUtils.phoneSearchKey(it) }
                .filter { it.length >= 7 }
                .distinct()

            var mergedWinner = incoming
            var supersededIds: List<String> = emptyList()
            var didMergeDuplicate = false
            var catNodesToSync: List<CategoryNode> = emptyList()

            withContext(Dispatchers.IO) {
                val existingMatches = if (phoneKeys.isNotEmpty()) {
                    contactDao.findAllByPhoneKeys(phoneKeys)
                        .map { it.contact.toDomain() }
                        .distinctBy { it.id }
                        .filter { it.id != incoming.id }
                } else emptyList()

                if (existingMatches.isNotEmpty()) {
                    didMergeDuplicate = true
                    val dedup = ContactDeduplicator.deduplicateAndMerge(existingMatches + incoming)
                    mergedWinner = dedup.mergedContacts.firstOrNull() ?: incoming
                    supersededIds = (dedup.supersededIds + incoming.id + existingMatches.map { it.id })
                        .filter { it != mergedWinner.id }
                        .distinct()
                    if (supersededIds.isNotEmpty()) {
                        contactDao.deleteContactsWithIndexes(supersededIds)
                    }
                }

                contactDao.upsertSingleWithIndexes(ContactEntity.fromDomain(mergedWinner))
                if (mergedWinner.categoryPaths.isNotEmpty()) {
                    catNodesToSync = buildCategoryNodes(mergedWinner.categoryPaths.toSet())
                    categoryDao.upsertAll(catNodesToSync.map {
                        CategoryEntity(it.id, it.name, it.parentPath, it.fullPath, it.depth.toInt(), it.iconKey)
                    })
                }
            }

            if (syncToCloud) {
                if (catNodesToSync.isNotEmpty()) {
                    launch { firestoreRepo.upsertCategories(currentUserId, catNodesToSync) }
                }
                if (supersededIds.isNotEmpty()) {
                    launch { firestoreRepo.batchDeleteContacts(currentUserId, supersededIds) }
                }
                launch { firestoreRepo.upsertContact(currentUserId, mergedWinner) }
            }
            if (_selectedContact.value?.id == mergedWinner.id || supersededIds.contains(_selectedContact.value?.id)) {
                _selectedContact.value = mergedWinner
            }
            loadCategoryLevel(_currentCategoryPath.value)
            if (_searchQuery.value.isNotBlank()) {
                updateSearchQuery(_searchQuery.value)
            }
            refreshCallLogs()
            launch { RabtaWidget.refreshAll(appContext) }
            _statusMessage.value = if (didMergeDuplicate) {
                "تم منع التكرار ودمج البيانات في: ${mergedWinner.displayName}"
            } else {
                "تم حفظ جهة الاتصال: ${mergedWinner.displayName}"
            }
        }
    }

    fun deleteContact(contact: ContactRecord) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { contactDao.deleteContactWithIndexes(contact.id) }
            launch { firestoreRepo.deleteContact(currentUserId, contact.id) }
            if (_selectedContact.value?.id == contact.id) {
                _selectedContact.value = null
            }
            loadCategoryLevel(_currentCategoryPath.value)
            if (_searchQuery.value.isNotBlank()) updateSearchQuery(_searchQuery.value)
            launch { RabtaWidget.refreshAll(appContext) }
            _statusMessage.value = "تم حذف ${contact.displayName}"
        }
    }

    fun toggleFavorite(contact: ContactRecord) {
        val next = !contact.isFavorite
        viewModelScope.launch {
            withContext(Dispatchers.IO) { contactDao.updateFavoriteFlag(contact.id, next) }
            launch { firestoreRepo.setContactFavorite(currentUserId, contact.id, next) }
            if (_selectedContact.value?.id == contact.id) {
                _selectedContact.value = contact.copy(isFavorite = next)
            }
            loadCategoryLevel(_currentCategoryPath.value)
            launch { RabtaWidget.refreshAll(appContext) }
        }
    }

    fun launchWhatsAppAndLog(contact: ContactRecord, phone: String, prefillMessage: String? = null) {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val logId = "wa_" + UUID.randomUUID().toString().replace("-", "").take(16)
            val entity = WhatsAppLogEntity(
                id = logId,
                contactId = contact.id,
                contactName = contact.displayName,
                phone = phone,
                loggedAtEpochMs = now,
                syncedToPhone = contact.syncedToDevice,
            )
            withContext(Dispatchers.IO) { logDao.insert(entity) }
            launch { firestoreRepo.createWhatsAppLog(currentUserId, entity.toDomain()) }
        }
        SystemIntents.openWhatsApp(appContext, phone, prefillMessage)
    }

    fun toggleDeviceContactSync(contact: ContactRecord) {
        viewModelScope.launch {
            if (!contact.syncedToDevice && strictPhonebookIsolation.value) {
                _statusMessage.value = "وضع الفصل الكامل عن سجل الهاتف مفعّل. عطّله من الإعدادات إذا أردت الحفظ بالهاتف."
                return@launch
            }
            if (!deviceContactsManager.hasPermissions()) {
                _statusMessage.value = "يرجى منح صلاحية جهات الاتصال للمزامنة الانتقائية"
                return@launch
            }
            val result = if (contact.syncedToDevice) {
                deviceContactsManager.removeContactFromPhonebook(contact).map { false }
            } else {
                deviceContactsManager.syncContactToPhonebook(contact).map { true }
            }
            result.onSuccess { nowSynced ->
                withContext(Dispatchers.IO) { contactDao.updateSyncedFlag(contact.id, nowSynced) }
                launch { firestoreRepo.setContactSyncedToDevice(currentUserId, contact.id, nowSynced) }
                if (_selectedContact.value?.id == contact.id) {
                    _selectedContact.value = contact.copy(syncedToDevice = nowSynced)
                }
                _statusMessage.value = if (nowSynced) {
                    "تم حفظ ${contact.displayName} في الهاتف ليتعرف عليه واتساب"
                } else {
                    "تمت إزالة ${contact.displayName} من سجل الهاتف مع بقائه في Felo Contacts"
                }
            }.onFailure {
                _statusMessage.value = "تعذرت المزامنة: ${it.localizedMessage}"
            }
        }
    }

    fun syncWeeklyActiveWhatsAppToPhonebook() {
        viewModelScope.launch {
            if (strictPhonebookIsolation.value) {
                _statusMessage.value = "تم حظر المزامنة للهاتف للحفاظ على الفصل الكامل عن جهات اتصال الموبايل وواتساب."
                return@launch
            }
            if (!deviceContactsManager.hasPermissions()) {
                _statusMessage.value = "يرجى منح صلاحية جهات الاتصال أولاً"
                return@launch
            }
            _isBusy.value = true
            try {
                val since = System.currentTimeMillis() - SEVEN_DAYS_MS
                val recentLogs = withContext(Dispatchers.IO) { logDao.getLogsSince(since) }
                val uniqueContactIds = recentLogs.map { it.contactId }.distinct()
                val contacts = withContext(Dispatchers.IO) { contactDao.getByIds(uniqueContactIds) }.map { it.toDomain() }
                var syncedCount = 0
                for (c in contacts) {
                    val res = deviceContactsManager.syncContactToPhonebook(c)
                    if (res.getOrDefault(false)) {
                        syncedCount++
                        withContext(Dispatchers.IO) {
                            contactDao.updateSyncedFlag(c.id, true)
                            logDao.markSyncedByContact(c.id)
                        }
                        launch { firestoreRepo.setContactSyncedToDevice(currentUserId, c.id, true) }
                    }
                }
                _statusMessage.value = "تمت مزامنة $syncedCount من نشطي واتساب آخر 7 أيام إلى الهاتف"
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun purgeAllSyncedContactsFromPhonebook() {
        viewModelScope.launch {
            val syncedList = syncedDeviceContacts.value
            if (syncedList.isEmpty()) {
                _statusMessage.value = "لا توجد أي أرقام متزامنة مع سجل الهاتف — كل بياناتك معزولة بالفعل."
                return@launch
            }
            if (!deviceContactsManager.hasPermissions()) {
                _statusMessage.value = "يرجى منح صلاحية جهات الاتصال لإزالة الأرقام المتزامنة سابقاً من الهاتف."
                return@launch
            }
            _isBusy.value = true
            try {
                var removed = 0
                for (c in syncedList) {
                    deviceContactsManager.removeContactFromPhonebook(c)
                    withContext(Dispatchers.IO) { contactDao.updateSyncedFlag(c.id, false) }
                    launch { firestoreRepo.setContactSyncedToDevice(currentUserId, c.id, false) }
                    removed++
                }
                _statusMessage.value = "تم فصل وإزالة $removed رقم من سجل الهاتف مع الإبقاء عليها كاملة داخل Felo Contacts"
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun importBundledDatasets(uploadToCloud: Boolean = true) {
        viewModelScope.launch {
            _isBusy.value = true
            _importProgressText.value = "جاري قراءة ملفات جهات الاتصال المرفقة..."
            try {
                val (allContacts, allCatPaths) = withContext(Dispatchers.IO) {
                    val s1 = appContext.assets.open("contacts_seed.csv").use { CsvParser.parseStream(it) }
                    val s2 = appContext.assets.open("eaeat_2023_leader.csv").use {
                        CsvParser.parseStream(it, defaultCategory = "الجامعة > الأكاديمية (EAEAT) > دفعة 2023")
                    }
                    (s1.contacts + s2.contacts) to (s1.categoryPaths + s2.categoryPaths)
                }
                val result = persistImportedDataset(allContacts, allCatPaths, uploadToCloud)
                prefs.setBundledImported(true)
                _statusMessage.value = "تم استيراد ${result.importedCount} جهة اتصال (${result.categoriesCreated} فئة متداخلة، ${result.geoMatchedCount} على الخريطة)"
            } catch (e: Exception) {
                _statusMessage.value = "فشل الاستيراد: ${e.localizedMessage}"
            } finally {
                _isBusy.value = false
                _importProgressText.value = null
            }
        }
    }

    fun importDevicePhonebookContacts(uploadToCloud: Boolean = true) {
        viewModelScope.launch {
            _isBusy.value = true
            _importProgressText.value = "جاري استيراد كل جهات الاتصال من سجل الهاتف (People)..."
            try {
                val res = deviceContactsManager.readAllDeviceContacts()
                res.onSuccess { (contacts, cats) ->
                    if (contacts.isEmpty()) {
                        _statusMessage.value = "لم يتم العثور على جهات اتصال في الهاتف"
                    } else {
                        val summary = persistImportedDataset(contacts, cats, uploadToCloud)
                        _statusMessage.value = "تم استيراد ${summary.importedCount} جهة من الهاتف وتصنيفهم في ${summary.categoriesCreated} فئة"
                    }
                }.onFailure { err ->
                    _statusMessage.value = "تعذر الاستيراد من الهاتف: ${err.localizedMessage}"
                }
            } finally {
                _isBusy.value = false
                _importProgressText.value = null
            }
        }
    }

    fun scanGoogleDriveWithToken(accessToken: String) {
        viewModelScope.launch {
            _isBusy.value = true
            _importProgressText.value = "جاري البحث داخل Google Drive عن جداول وملفات بها أرقام..."
            try {
                val outcomeRes = GoogleDriveScanner.scanAndExtractContacts(accessToken)
                outcomeRes.onSuccess { outcome ->
                    if (outcome.contacts.isEmpty()) {
                        _statusMessage.value = "لم يتم العثور على أرقام جديدة داخل ملفات Google Drive النصية/الجداول"
                    } else {
                        val summary = persistImportedDataset(outcome.contacts, outcome.categoryPaths, uploadToCloud = true)
                        _statusMessage.value = "تم استخراج ${summary.importedCount} رقم من ${outcome.filesScanned} ملف في Google Drive"
                    }
                }.onFailure { err ->
                    _statusMessage.value = "خطأ Google Drive: ${err.localizedMessage}"
                }
            } finally {
                _isBusy.value = false
                _importProgressText.value = null
            }
        }
    }

    fun importFromUri(uri: Uri, fileName: String?, uploadToCloud: Boolean = true) {
        viewModelScope.launch {
            _isBusy.value = true
            _importProgressText.value = "جاري تحليل الملف..."
            try {
                val parsed = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        val text = stream.bufferedReader().readText()
                        CsvParser.parseArbitrarySheetOrText(text, fileName ?: "ملف مستورد")
                    } ?: CsvParser.ParsedDataset(emptyList(), 0, emptySet())
                }
                if (parsed.contacts.isEmpty()) {
                    _statusMessage.value = "لم يتم العثور على جهات اتصال صالحة في الملف"
                } else {
                    val result = persistImportedDataset(parsed.contacts, parsed.categoryPaths, uploadToCloud)
                    _statusMessage.value = "تم استيراد ${result.importedCount} جهة اتصال بنجاح"
                }
            } catch (e: Exception) {
                _statusMessage.value = "تعذر قراءة الملف: ${e.localizedMessage}"
            } finally {
                _isBusy.value = false
                _importProgressText.value = null
            }
        }
    }

    /**
     * Full-database anti-duplicate merge + nested category builder + English primary / Arabic nickname
     * normalization + searchable bilingual tags across ALL stored contacts.
     */
    fun reorganizeAllCategories() {
        viewModelScope.launch {
            _isBusy.value = true
            _importProgressText.value = "جاري فحص ودمج كل الأسماء المكررة، توحيد English/العربي، وبناء التاجز..."
            try {
                val outcome = withContext(Dispatchers.IO) {
                    val total = contactDao.getTotalCount()
                    val allDomains = ArrayList<ContactRecord>(total)
                    var offset = 0
                    val pageSize = 1000
                    while (true) {
                        val page = contactDao.getRecentOrAllPage(limit = pageSize, offset = offset)
                        if (page.isEmpty()) break
                        page.forEach { allDomains += it.toDomain() }
                        offset += page.size
                    }

                    val dedup = ContactDeduplicator.deduplicateAndMerge(allDomains, enforceBilingual = true)
                    val allCatPaths = LinkedHashSet<String>()
                    dedup.mergedContacts.forEach { c ->
                        c.categoryPaths.forEach { CsvParser.addHierarchySegments(it, allCatPaths) }
                    }
                    val catNodes = buildCategoryNodes(allCatPaths)
                    val catEntities = catNodes.map {
                        CategoryEntity(it.id, it.name, it.parentPath, it.fullPath, it.depth.toInt(), it.iconKey)
                    }
                    val updatedEntities = dedup.mergedContacts.map { ContactEntity.fromDomain(it) }

                    contactDao.clearCategoryRefs()
                    contactDao.clearPhoneKeys()
                    contactDao.clearAll()
                    categoryDao.clearAll()
                    contactDao.upsertBatchWithIndexes(updatedEntities, catEntities, categoryDao)
                    Triple(dedup, catNodes, updatedEntities.size)
                }

                val dedup = outcome.first
                val catNodes = outcome.second
                if (dedup.supersededIds.isNotEmpty()) {
                    launch { firestoreRepo.batchDeleteContacts(currentUserId, dedup.supersededIds) }
                }
                if (catNodes.isNotEmpty()) {
                    launch { firestoreRepo.upsertCategories(currentUserId, catNodes) }
                }
                loadCategoryLevel("")
                if (_searchQuery.value.isNotBlank()) updateSearchQuery(_searchQuery.value)
                refreshCallLogs()
                launch { RabtaWidget.refreshAll(appContext) }
                _statusMessage.value =
                    "تم منع التكرار: دمج ${dedup.totalDuplicatesRemoved} مكرر، والنتيجة ${outcome.third} جهة بأسماء English + عربي + تاجز"
            } finally {
                _isBusy.value = false
                _importProgressText.value = null
            }
        }
    }

    private suspend fun persistImportedDataset(
        contacts: List<ContactRecord>,
        catPaths: Set<String>,
        uploadToCloud: Boolean,
    ): CsvImportSummary {
        _importProgressText.value = "منع التكرار وفهرسة ${contacts.size} جهة اتصال باللغتين والتاجز..."

        val (finalMerged, supersededIds, allCats) = withContext(Dispatchers.IO) {
            // 1) Deduplicate incoming batch internally
            val internalDedup = ContactDeduplicator.deduplicateAndMerge(contacts, enforceBilingual = true)
            val incomingKeys = internalDedup.mergedContacts
                .flatMap { c -> (listOf(c.primaryPhone) + c.phones).map { PhoneUtils.phoneSearchKey(it) } }
                .filter { it.length >= 7 }
                .distinct()

            // 2) Pull any existing contacts in Room that share phone keys to merge instead of duplicate
            val existingMatched = if (incomingKeys.isNotEmpty()) {
                incomingKeys.chunked(450).flatMap { chunk ->
                    contactDao.findAllByPhoneKeys(chunk).map { it.contact.toDomain() }
                }.distinctBy { it.id }
            } else emptyList()

            val fullDedup = ContactDeduplicator.deduplicateAndMerge(
                existingMatched + internalDedup.mergedContacts,
                enforceBilingual = true,
            )

            val catSet = LinkedHashSet(catPaths)
            fullDedup.mergedContacts.forEach { c ->
                c.categoryPaths.forEach { CsvParser.addHierarchySegments(it, catSet) }
            }
            Triple(fullDedup.mergedContacts, fullDedup.supersededIds, catSet)
        }

        val entities = finalMerged.map { ContactEntity.fromDomain(it) }
        val catNodes = buildCategoryNodes(allCats)
        val catEntities = catNodes.map {
            CategoryEntity(it.id, it.name, it.parentPath, it.fullPath, it.depth.toInt(), it.iconKey)
        }

        withContext(Dispatchers.IO) {
            if (supersededIds.isNotEmpty()) {
                contactDao.deleteContactsWithIndexes(supersededIds)
            }
            contactDao.upsertBatchWithIndexes(entities, catEntities, categoryDao)
        }

        loadCategoryLevel(_currentCategoryPath.value)
        refreshCallLogs()
        viewModelScope.launch { RabtaWidget.refreshAll(appContext) }

        if (uploadToCloud && finalMerged.isNotEmpty()) {
            cloudUploadJob?.cancel()
            cloudUploadJob = viewModelScope.launch {
                _cloudSyncStatus.value = "نسخ احتياطي سحابي في الخلفية (0 / ${finalMerged.size})..."
                runCatching {
                    if (supersededIds.isNotEmpty()) {
                        firestoreRepo.batchDeleteContacts(currentUserId, supersededIds)
                    }
                    firestoreRepo.upsertCategories(currentUserId, catNodes)
                    firestoreRepo.batchUpsertContacts(currentUserId, finalMerged) { done, total ->
                        _cloudSyncStatus.value = "النسخ السحابي في الخلفية ($done / $total)"
                    }
                    prefs.setLastSyncMs(System.currentTimeMillis())
                }
                _cloudSyncStatus.value = null
            }
        }

        return CsvImportSummary(
            importedCount = finalMerged.size,
            skippedCount = supersededIds.size,
            categoriesCreated = catNodes.size,
            geoMatchedCount = finalMerged.count { it.latitude != null },
        )
    }

    fun pullAllFromCloud() {
        viewModelScope.launch {
            _isBusy.value = true
            _importProgressText.value = "جاري جلب جهات الاتصال من Firestore بدون تكرار..."
            try {
                var cursor: DocumentSnapshot? = null
                val pulledAll = ArrayList<ContactRecord>()
                while (true) {
                    val res = firestoreRepo.pullContactsPage(currentUserId, limit = 500, lastSnapshot = cursor)
                    val (page, nextCursor) = res.getOrThrow()
                    if (page.isEmpty()) break
                    pulledAll += page
                    _importProgressText.value = "تم جلب ${pulledAll.size} جهة من السحابة، جاري فحص التكرار..."
                    if (page.size < 500 || nextCursor == null) break
                    cursor = nextCursor
                }

                if (pulledAll.isEmpty()) {
                    _statusMessage.value = "لا توجد جهات اتصال على السحابة حالياً"
                    return@launch
                }

                _importProgressText.value = "دمج ومنع تكرار ${pulledAll.size} جهة مع قاعدة البيانات..."
                val (mergedAll, supersededIds, catNodes) = withContext(Dispatchers.IO) {
                    val existingTotal = contactDao.getTotalCount()
                    val existingAll = ArrayList<ContactRecord>(existingTotal)
                    var offset = 0
                    val pageSize = 1000
                    while (true) {
                        val localPage = contactDao.getRecentOrAllPage(limit = pageSize, offset = offset)
                        if (localPage.isEmpty()) break
                        localPage.forEach { existingAll += it.toDomain() }
                        offset += localPage.size
                    }

                    val dedup = ContactDeduplicator.deduplicateAndMerge(
                        existingAll + pulledAll,
                        enforceBilingual = true,
                    )
                    val catSet = LinkedHashSet<String>()
                    dedup.mergedContacts.forEach { c ->
                        c.categoryPaths.forEach { CsvParser.addHierarchySegments(it, catSet) }
                    }
                    val builtCats = buildCategoryNodes(catSet)
                    val catEntities = builtCats.map {
                        CategoryEntity(it.id, it.name, it.parentPath, it.fullPath, it.depth.toInt(), it.iconKey)
                    }
                    val mergedEntities = dedup.mergedContacts.map { ContactEntity.fromDomain(it) }

                    if (dedup.supersededIds.isNotEmpty()) {
                        contactDao.deleteContactsWithIndexes(dedup.supersededIds)
                    }
                    contactDao.upsertBatchWithIndexes(
                        contacts = mergedEntities,
                        catEntities = catEntities,
                        categoryDao = categoryDao,
                    )
                    Triple(dedup.mergedContacts, dedup.supersededIds, dedup.totalDuplicatesRemoved)
                }

                if (supersededIds.isNotEmpty()) {
                    launch { firestoreRepo.batchDeleteContacts(currentUserId, supersededIds) }
                }

                prefs.setLastSyncMs(System.currentTimeMillis())
                loadCategoryLevel(_currentCategoryPath.value)
                refreshCallLogs()
                launch { RabtaWidget.refreshAll(appContext) }
                _statusMessage.value = if (catNodes > 0) {
                    "تمت المزامنة بدون تكرار: ${mergedAll.size} جهة (ودمج $catNodes مكرر تلقائياً)"
                } else {
                    "تمت المزامنة: ${mergedAll.size} جهة اتصال"
                }
            } catch (e: Exception) {
                _statusMessage.value = "تعذرت المزامنة: ${e.localizedMessage}"
            } finally {
                _isBusy.value = false
                _importProgressText.value = null
            }
        }
    }

    // --- Gemini AI Command Execution & Smart Batch Classification ---

    fun resetAiState() {
        _aiUiState.value = AiAssistantUiState.Idle
    }

    fun runAiCommand(
        userPrompt: String,
        onNavigateToCategory: (String) -> Unit,
        onNavigateToMap: () -> Unit,
    ) {
        val clean = userPrompt.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _aiUiState.value = AiAssistantUiState.Loading
            try {
                val topCats = withContext(Dispatchers.IO) {
                    categoryDao.getChildren("").map { it.name }
                }
                val planRes = aiAssistant.parseCommand(clean, topCats)
                val plan = planRes.getOrElse { err ->
                    _aiUiState.value = AiAssistantUiState.Error(err.localizedMessage ?: "تعذر الاتصال بـ Gemini AI")
                    return@launch
                }

                when (plan.action) {
                    AiCommandAction.ADD_CONTACT -> {
                        val name = plan.newContactName.ifBlank { plan.queryNameOrPhone }
                        val phone = PhoneUtils.normalizePhone(plan.newContactPhone)
                        if (phone.isNotBlank() || name.isNotBlank()) {
                            val newRecord = ContactRecord(
                                displayName = name.ifBlank { phone },
                                primaryPhone = phone,
                                phones = listOfNotNull(phone.takeIf { it.isNotBlank() }),
                                categoryPaths = listOfNotNull(plan.categoryPath.takeIf { it.isNotBlank() }),
                            )
                            saveContact(newRecord)
                            _aiUiState.value = AiAssistantUiState.Success(
                                reply = plan.replyAr.ifBlank { "تم حفظ ${newRecord.displayName}" },
                                actionBadge = "حفظ جهة اتصال",
                                matchedContacts = listOf(newRecord),
                            )
                        } else {
                            _aiUiState.value = AiAssistantUiState.Error("لم أتمكن من استخراج اسم أو رقم للحفظ")
                        }
                    }

                    AiCommandAction.OPEN_CATEGORY -> {
                        val target = plan.categoryPath.ifBlank { plan.queryNameOrPhone }
                        val allCats = withContext(Dispatchers.IO) { categoryDao.getAll() }
                        val matchedCat = allCats.firstOrNull {
                            it.fullPath.equals(target, ignoreCase = true) ||
                                it.fullPath.contains(target, ignoreCase = true) ||
                                it.name.contains(target, ignoreCase = true)
                        }
                        if (matchedCat != null) {
                            loadCategoryLevel(matchedCat.fullPath)
                            onNavigateToCategory(matchedCat.fullPath)
                            _aiUiState.value = AiAssistantUiState.Success(
                                reply = plan.replyAr.ifBlank { "تم فتح قسم ${matchedCat.fullPath}" },
                                actionBadge = "فتح قسم: ${matchedCat.name}",
                            )
                        } else {
                            val results = findMatchesInRoom(target)
                            _aiUiState.value = AiAssistantUiState.Success(
                                reply = "لم أجد قسماً مطابقاً تماماً؛ إليك أقرب النتائج لـ '$target':",
                                actionBadge = "نتائج البحث",
                                matchedContacts = results,
                            )
                        }
                    }

                    AiCommandAction.MAP_DISTRICT -> {
                        val q = plan.district.ifBlank { plan.queryNameOrPhone }
                        val results = findMatchesInRoom(q)
                        onNavigateToMap()
                        _aiUiState.value = AiAssistantUiState.Success(
                            reply = plan.replyAr.ifBlank { "تم فتح الخريطة لمنطقة $q (${results.size} نتيجة)" },
                            actionBadge = "الخريطة: $q",
                            matchedContacts = results,
                        )
                    }

                    AiCommandAction.AUTO_CATEGORIZE -> {
                        runAiAutoCategorizeBatch()
                    }

                    AiCommandAction.CALL, AiCommandAction.WHATSAPP, AiCommandAction.PAYMENT, AiCommandAction.SEARCH -> {
                        val q = listOf(plan.queryNameOrPhone, plan.district)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                            .ifBlank { clean }
                        val matches = findMatchesInRoom(q)
                        if (matches.size == 1 && plan.action == AiCommandAction.CALL && matches.first().primaryPhone.isNotBlank()) {
                            SystemIntents.dialPhone(appContext, matches.first().primaryPhone)
                        } else if (matches.size == 1 && plan.action == AiCommandAction.WHATSAPP && matches.first().primaryPhone.isNotBlank()) {
                            launchWhatsAppAndLog(matches.first(), matches.first().primaryPhone, plan.messageText.takeIf { it.isNotBlank() })
                        }
                        val badge = when (plan.action) {
                            AiCommandAction.CALL -> "اتصال ذكي"
                            AiCommandAction.WHATSAPP -> "واتساب ذكي"
                            AiCommandAction.PAYMENT -> "تحويل مالي ${plan.amount}".trim()
                            else -> "نتائج البحث (${matches.size})"
                        }
                        _aiUiState.value = AiAssistantUiState.Success(
                            reply = if (matches.isEmpty()) "لم أجد جهات اتصال مطابقة لـ '$q'" else plan.replyAr,
                            actionBadge = badge,
                            matchedContacts = matches,
                            suggestedMessage = plan.messageText.takeIf { it.isNotBlank() },
                            paymentAmount = plan.amount.takeIf { it.isNotBlank() },
                        )
                    }

                    AiCommandAction.ANSWER -> {
                        val extraMatches = if (plan.queryNameOrPhone.isNotBlank()) findMatchesInRoom(plan.queryNameOrPhone) else emptyList()
                        _aiUiState.value = AiAssistantUiState.Success(
                            reply = plan.replyAr,
                            actionBadge = "إجابة Gemini",
                            matchedContacts = extraMatches,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiUiState.value = AiAssistantUiState.Error(e.localizedMessage ?: "حدث خطأ غير متوقع")
            }
        }
    }

    fun runAiAutoCategorizeBatch() {
        viewModelScope.launch {
            _aiUiState.value = AiAssistantUiState.Loading
            _isBusy.value = true
            try {
                val candidates = withContext(Dispatchers.IO) {
                    contactDao.getAutoOrUnclassifiedContacts(limit = 40).map { it.toDomain() }
                }
                if (candidates.isEmpty()) {
                    _aiUiState.value = AiAssistantUiState.Success(
                        reply = "كل جهات اتصالك مصنفة بالفعل داخل فئات واضحة!",
                        actionBadge = "مكتمل",
                    )
                    return@launch
                }
                val mapRes = aiAssistant.classifyContactBatch(candidates)
                val assignments = mapRes.getOrElse { err ->
                    _aiUiState.value = AiAssistantUiState.Error(err.localizedMessage ?: "فشل التصنيف عبر Gemini")
                    return@launch
                }
                if (assignments.isEmpty()) {
                    _aiUiState.value = AiAssistantUiState.Error("لم يرجع Gemini تصنيفات لهذه الدفعة")
                    return@launch
                }

                val newCatPaths = LinkedHashSet<String>()
                val updatedContacts = ArrayList<ContactRecord>()
                for (c in candidates) {
                    val assignedPath = assignments[c.id] ?: continue
                    val filteredOld = c.categoryPaths.filter { !it.startsWith("جهات اتصال تلقائية") }
                    val merged = (filteredOld + assignedPath).distinct()
                    merged.forEach { CsvParser.addHierarchySegments(it, newCatPaths) }
                    updatedContacts += c.copy(categoryPaths = merged)
                }

                val catNodes = buildCategoryNodes(newCatPaths)
                withContext(Dispatchers.IO) {
                    contactDao.upsertBatchWithIndexes(
                        contacts = updatedContacts.map { ContactEntity.fromDomain(it) },
                        catEntities = catNodes.map { CategoryEntity(it.id, it.name, it.parentPath, it.fullPath, it.depth.toInt(), it.iconKey) },
                        categoryDao = categoryDao,
                    )
                }
                loadCategoryLevel(_currentCategoryPath.value)
                _aiUiState.value = AiAssistantUiState.Success(
                    reply = "تم تصنيف ${updatedContacts.size} جهة اتصال بالذكاء الاصطناعي داخل ${catNodes.size} فئة متداخلة!",
                    actionBadge = "تصنيف Gemini AI",
                    matchedContacts = updatedContacts.take(10),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiUiState.value = AiAssistantUiState.Error(e.localizedMessage ?: "تعذر التصنيف الذكي")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun runAiBilingualEnrichBatch() {
        viewModelScope.launch {
            _aiUiState.value = AiAssistantUiState.Loading
            _isBusy.value = true
            try {
                val candidates = withContext(Dispatchers.IO) {
                    contactDao.getContactsNeedingBilingualOrTags(limit = 35)
                        .ifEmpty { contactDao.getRecentOrAllPage(limit = 35, offset = 0) }
                        .map { it.toDomain() }
                }
                if (candidates.isEmpty()) {
                    _aiUiState.value = AiAssistantUiState.Success(
                        reply = "لا توجد جهات اتصال متاحة للتحديث حالياً.",
                        actionBadge = "فارغ",
                    )
                    return@launch
                }
                val enrichRes = aiAssistant.enrichContactsBilingualAndTags(candidates)
                val map = enrichRes.getOrElse { err ->
                    _aiUiState.value = AiAssistantUiState.Error(err.localizedMessage ?: "تعذر التحديث الذكي عبر Gemini")
                    return@launch
                }
                val updated = ArrayList<ContactRecord>()
                for (c in candidates) {
                    val item = map[c.id] ?: continue
                    val combinedNick = listOfNotNull(
                        item.arabicNickname.takeIf { it.isNotBlank() },
                        c.nickname?.takeIf { it.isNotBlank() && it != item.arabicNickname },
                        c.displayName.takeIf { it.isNotBlank() && !it.equals(item.englishName, true) },
                    ).distinct().joinToString(" • ").take(240).ifBlank { null }

                    val mergedTags = (c.tags + item.tags).map { it.trim() }.filter { it.isNotBlank() }.distinct().take(20)
                    updated += c.copy(
                        displayName = item.englishName.take(200),
                        nickname = combinedNick,
                        tags = mergedTags,
                    )
                }
                if (updated.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        contactDao.upsertBatchWithIndexes(
                            contacts = updated.map { ContactEntity.fromDomain(it) },
                            catEntities = emptyList(),
                            categoryDao = categoryDao,
                        )
                    }
                    if (_searchQuery.value.isNotBlank()) updateSearchQuery(_searchQuery.value)
                    loadCategoryLevel(_currentCategoryPath.value)
                }
                _aiUiState.value = AiAssistantUiState.Success(
                    reply = "تم تحويل ${updated.size} جهة اتصال لأسماء English مع حفظ الاسم العربي في Nickname وإضافة تاجز البحث!",
                    actionBadge = "ثنائي اللغة + Tags AI",
                    matchedContacts = updated.take(10),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiUiState.value = AiAssistantUiState.Error(e.localizedMessage ?: "فشل التحديث ثنائي اللغة")
            } finally {
                _isBusy.value = false
            }
        }
    }

    private suspend fun findMatchesInRoom(query: String): List<ContactRecord> = withContext(Dispatchers.IO) {
        val tokens = ContactEntity.normalizeForSearch(query.trim()).split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return@withContext emptyList()
        val primaryMatches = contactDao.searchContacts(tokens.first(), limit = 60, offset = 0).map { it.toDomain() }
        if (tokens.size == 1) return@withContext primaryMatches.take(15)
        val filtered = primaryMatches.filter { c ->
            val hay = ContactEntity.normalizeForSearch(
                buildString {
                    append(c.displayName).append(' ')
                    append(c.nickname.orEmpty()).append(' ')
                    append(c.primaryPhone).append(' ')
                    append(c.tags.joinToString(" ")).append(' ')
                    append(c.categoryPaths.joinToString(" ")).append(' ')
                    append(c.district.orEmpty()).append(' ')
                    append(c.address.orEmpty()).append(' ')
                    append(c.notes.orEmpty())
                }
            )
            tokens.all { hay.contains(it) }
        }
        filtered.ifEmpty { primaryMatches }.take(15)
    }

    fun toggleBulkSelection(contactId: String) {
        val current = _selectedForBulk.value.toMutableSet()
        if (!current.add(contactId)) current.remove(contactId)
        _selectedForBulk.value = current
    }

    fun clearBulkSelection() {
        _selectedForBulk.value = emptySet()
    }

    fun shareBulkOrCategory(asVCard: Boolean) {
        viewModelScope.launch {
            val selectedIds = _selectedForBulk.value
            val contacts = withContext(Dispatchers.IO) {
                when {
                    selectedIds.isNotEmpty() -> contactDao.getByIds(selectedIds.toList()).map { it.toDomain() }
                    _currentCategoryPath.value.isNotBlank() -> _categoryContacts.value
                    else -> _searchResults.value.ifEmpty { favoriteContacts.value }
                }
            }
            if (contacts.isEmpty()) {
                _statusMessage.value = "اختر فئة أو جهات اتصال أولاً للمشاركة"
                return@launch
            }
            if (asVCard) {
                SystemIntents.shareExportFile(
                    appContext,
                    "rabta_contacts_${contacts.size}.vcf",
                    "text/x-vcard",
                    VCardUtils.exportAll(contacts),
                )
            } else {
                SystemIntents.shareExportFile(
                    appContext,
                    "rabta_contacts_${contacts.size}.csv",
                    "text/csv",
                    CsvParser.exportToCsv(contacts),
                )
            }
            clearBulkSelection()
        }
    }

    fun savePaymentTemplate(name: String, urlTemplate: String) {
        if (name.isBlank() || urlTemplate.isBlank()) return
        viewModelScope.launch {
            val tpl = PaymentTemplate(
                id = "tpl_" + UUID.randomUUID().toString().replace("-", "").take(10),
                name = name.trim(),
                urlTemplate = urlTemplate.trim(),
                isDefault = false,
            )
            withContext(Dispatchers.IO) {
                paymentDao.upsert(PaymentTemplateEntity(tpl.id, tpl.name, tpl.urlTemplate, false))
            }
            launch { firestoreRepo.upsertPaymentTemplate(currentUserId, tpl) }
            _statusMessage.value = "تمت إضافة خدمة الدفع: ${tpl.name}"
        }
    }

    fun deletePaymentTemplate(templateId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { paymentDao.deleteById(templateId) }
            launch { firestoreRepo.deletePaymentTemplate(currentUserId, templateId) }
        }
    }

    fun saveGithubRepoAndCheckUpdates(repoSlug: String) {
        viewModelScope.launch {
            prefs.setGithubRepoSlug(repoSlug)
            if (repoSlug.isBlank()) {
                _statusMessage.value = "أدخل رابط أو اسم المستودع owner/repo"
                return@launch
            }
            _isBusy.value = true
            try {
                val res = gitHubService.checkLatestRelease(repoSlug)
                res.onSuccess { info ->
                    _releaseInfo.value = info
                    _statusMessage.value = if (info.isUpdateAvailable) {
                        "يتوفر تحديث جديد: ${info.tagName}"
                    } else {
                        "المستودع متصل (${info.tagName})"
                    }
                }.onFailure { err ->
                    _statusMessage.value = "تعذر فحص التحديثات: ${err.localizedMessage}"
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    private fun buildCategoryNodes(paths: Set<String>): List<CategoryNode> {
        val map = linkedMapOf<String, CategoryNode>()
        for (raw in paths) {
            val parts = raw.split(">").map { it.trim() }.filter { it.isNotBlank() }
            var current = ""
            parts.forEachIndexed { idx, part ->
                val parent = current
                current = if (current.isEmpty()) part else "$current > $part"
                if (!map.containsKey(current)) {
                    val safeId = "cat_" + current.hashCode().toUInt().toString(16)
                    map[current] = CategoryNode(
                        id = safeId,
                        name = part,
                        parentPath = parent,
                        fullPath = current,
                        depth = idx.toLong(),
                        iconKey = null,
                    )
                }
            }
        }
        return map.values.toList()
    }

    companion object {
        private const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
