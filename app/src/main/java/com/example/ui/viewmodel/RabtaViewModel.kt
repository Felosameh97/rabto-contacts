package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiCommandAction
import com.example.ai.LocalTranscriptAnalyzer
import com.example.ai.RabtaAiAssistant
import com.example.data.device.CallLogReader
import com.example.data.device.DeviceContactsManager
import com.example.data.local.CallTranscriptEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.ContactEntity
import com.example.data.local.DialHistoryEntity
import com.example.data.local.LocalMediaCacheManager
import com.example.data.local.PaymentTemplateEntity
import com.example.data.local.RabtaDatabase
import com.example.data.local.SyncPrefsDataStore
import com.example.data.local.WhatsAppLogEntity
import com.example.data.remote.FirestoreContactsRepository
import com.example.data.remote.GitHubUpdateService
import com.example.data.remote.GoogleContactsPhotoSync
import com.example.data.remote.GoogleDriveScanner
import com.example.data.sync.OfflineFirstSyncEngine
import com.example.domain.model.CallActionItem
import com.example.domain.model.CallLogEntry
import com.example.domain.model.CallTranscriptRecord
import com.example.domain.model.CategoryNode
import com.example.domain.model.CategorySummary
import com.example.domain.model.ContactRecord
import com.example.domain.model.CsvImportSummary
import com.example.domain.model.DialHistoryEntry
import com.example.domain.model.GitHubReleaseInfo
import com.example.domain.model.GlobalSearchResult
import com.example.domain.model.LocalMediaCacheStats
import com.example.domain.model.PaymentTemplate
import com.example.domain.model.PreCallContext
import com.example.domain.model.SyncEngineState
import com.example.domain.model.SyncState
import com.example.domain.model.WhatsAppLog
import com.example.telecom.CallRecorderManager
import com.example.telecom.CallRecordingItem
import com.example.telecom.PostCallSummary
import com.example.telecom.RabtaInCallService
import com.example.telecom.RecordingSourceMode
import com.example.ui.theme.CalmPalette
import com.example.ui.theme.ThemeMode
import com.example.util.AppLockSecurity
import com.example.util.BilingualNameEngine
import com.example.util.ContactDeduplicator
import com.example.util.CsvParser
import com.example.util.EgyptGeoLookup
import com.example.util.FollowUpReminderScheduler
import com.example.util.JsonContactExporter
import com.example.util.PaymentUtils
import com.example.util.PhoneUtils
import com.example.util.SimPreference
import com.example.util.SmartCategoryEngine
import com.example.util.SystemIntents
import com.example.util.T9SearchMatcher
import com.example.util.VCardUtils
import com.example.widget.RabtaWidget
import com.google.firebase.firestore.DocumentSnapshot
import java.io.BufferedInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
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
    private val syncEngine: OfflineFirstSyncEngine = OfflineFirstSyncEngine(
        context = appContext,
        roomDb = db,
        firestoreRepo = firestoreRepo,
        prefs = prefs,
    ),
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

    private val bulkWorkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val syncEngineState: StateFlow<SyncEngineState> = syncEngine.syncState

    val allContactsAlphabetical: StateFlow<List<ContactRecord>> = contactDao.observeAllAlphabeticalLight()
        .map { list -> list.map { it.toDomain() } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callTranscripts: StateFlow<List<CallTranscriptRecord>> = transcriptDao.observeAll()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appCallLogs: StateFlow<List<CallLogEntry>> = syncEngine.observeRecentCallLogsEnriched(250)
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showContactPhotos: StateFlow<Boolean> = prefs.showContactPhotosFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val callPhotoBackground: StateFlow<Boolean> = prefs.callPhotoBackgroundFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val strictPhonebookIsolation: StateFlow<Boolean> = prefs.strictPhonebookIsolationFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoTranscribeCalls: StateFlow<Boolean> = prefs.autoTranscribeCallsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val simPreference: StateFlow<SimPreference> = prefs.simPreferenceFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SimPreference.ASK_OR_BOTH)

    val recordingSourceMode: StateFlow<RecordingSourceMode> = prefs.recordingSourceFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecordingSourceMode.SMART_AUTO)

    val vibrateOnAnswer: StateFlow<Boolean> = prefs.vibrateOnAnswerFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoSpeakerForRecord: StateFlow<Boolean> = prefs.autoSpeakerForRecordFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lockedCategories: StateFlow<Set<String>> = prefs.lockedCategoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val whatsappTemplates: StateFlow<List<String>> = prefs.whatsappTemplatesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncPrefsDataStore.DEFAULT_WHATSAPP_TEMPLATES)

    val appLockEnabled: StateFlow<Boolean> = prefs.appLockEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appLockPinHash: StateFlow<String> = prefs.appLockPinHashFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val biometricLockEnabled: StateFlow<Boolean> = prefs.biometricLockEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val hideLockscreenCallerDetails: StateFlow<Boolean> = prefs.hideLockscreenCallerDetailsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val speedDialMap: StateFlow<Map<String, String>> = prefs.speedDialMapFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val selectedAiModelId: StateFlow<String> = prefs.aiModelIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RabtaAiAssistant.DEFAULT_MODEL_ID)

    private var dialpadContactsCache: List<ContactRecord> = emptyList()
    private var dialpadCacheUpdatedMs: Long = 0L

    private val _sessionUnlockedCategories = MutableStateFlow<Set<String>>(emptySet())
    val sessionUnlockedCategories = _sessionUnlockedCategories.asStateFlow()

    val postCallSummary: StateFlow<PostCallSummary?> = RabtaInCallService.lastEndedCallSummary

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

    private val _globalSearchResult = MutableStateFlow(GlobalSearchResult())
    val globalSearchResult = _globalSearchResult.asStateFlow()

    private val _preCallContext = MutableStateFlow<PreCallContext?>(null)
    val preCallContext = _preCallContext.asStateFlow()

    private val _cacheStats = MutableStateFlow(LocalMediaCacheStats())
    val cacheStats = _cacheStats.asStateFlow()

    private val _duplicateClusters = MutableStateFlow<List<List<ContactRecord>>>(emptyList())
    val duplicateClusters = _duplicateClusters.asStateFlow()

    private val _aiDiagnosticStatus = MutableStateFlow<String?>(null)
    val aiDiagnosticStatus = _aiDiagnosticStatus.asStateFlow()

    private var searchJob: Job? = null
    private var dialpadJob: Job? = null
    private var cloudUploadJob: Job? = null

    init {
        initializeSession()
    }

    private fun initializeSession() {
        syncEngine.startTwoWaySync(currentUserId, viewModelScope)
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
            refreshCacheStats()

            launch {
                prefs.aiModelIdFlow.collect { modelId ->
                    aiAssistant.activeModelId = modelId
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
                        entities
                            .filter {
                                (it.status == "PENDING_AI" || (autoTranscribeCalls.value && it.status == "PENDING")) &&
                                    it.audioFilePath.isNotBlank()
                            }
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

    override fun onCleared() {
        syncEngine.stop()
        super.onCleared()
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

    fun setSelectedAiModelId(modelId: String) {
        val clean = modelId.trim().ifBlank { RabtaAiAssistant.DEFAULT_MODEL_ID }
        aiAssistant.activeModelId = clean
        _aiDiagnosticStatus.value = null
        viewModelScope.launch {
            prefs.setAiModelId(clean)
            _statusMessage.value = "تم تعيين موديل Gemini النشط: $clean"
        }
    }

    fun testGeminiConnection() {
        viewModelScope.launch {
            _aiDiagnosticStatus.value = "جاري اختبار الاتصال بالموديل ${aiAssistant.activeModelId}..."
            aiAssistant.testConnection()
                .onSuccess { ok ->
                    _aiDiagnosticStatus.value = "✅ $ok"
                }
                .onFailure { err ->
                    _aiDiagnosticStatus.value = "❌ ${err.localizedMessage ?: err.message ?: "تعذر الاتصال بـ Gemini"}"
                }
        }
    }

    fun setRecordingSourceMode(mode: RecordingSourceMode) {
        viewModelScope.launch { prefs.setRecordingSourceMode(mode) }
    }

    fun setVibrateOnAnswer(enabled: Boolean) {
        viewModelScope.launch { prefs.setVibrateOnAnswer(enabled) }
    }

    fun setAutoSpeakerForRecord(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoSpeakerForRecord(enabled) }
    }

    fun setAppLockPin(pin: String) {
        if (!AppLockSecurity.isValidPinFormat(pin)) {
            _statusMessage.value = "الرمز يجب أن يكون من 4 إلى 6 أرقام"
            return
        }
        viewModelScope.launch {
            prefs.setAppLockPin(AppLockSecurity.hashPin(pin), enabled = true)
            _statusMessage.value = "تم تفعيل قفل التطبيق برمز المرور بنجاح"
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) {
                prefs.disableAppLock()
                _statusMessage.value = "تم إيقاف قفل التطبيق"
            } else {
                val existingHash = appLockPinHash.value
                if (existingHash.isNotBlank()) {
                    prefs.setAppLockPin(existingHash, enabled = true)
                    _statusMessage.value = "تم تفعيل قفل التطبيق"
                }
            }
        }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setBiometricLockEnabled(enabled) }
    }

    fun setHideLockscreenCallerDetails(enabled: Boolean) {
        viewModelScope.launch { prefs.setHideLockscreenCallerDetails(enabled) }
    }

    fun toggleCategoryLock(categoryPath: String) {
        if (categoryPath.isBlank()) return
        viewModelScope.launch {
            val currentlyLocked = lockedCategories.value.contains(categoryPath)
            prefs.toggleCategoryLock(categoryPath)
            _statusMessage.value = if (currentlyLocked) {
                "تم إلغاء قفل فئة: $categoryPath"
            } else {
                "تم قفل فئة ($categoryPath) بالبصمة بنجاح"
            }
        }
    }

    fun markCategoryUnlockedForSession(categoryPath: String) {
        val next = _sessionUnlockedCategories.value.toMutableSet()
        next.add(categoryPath)
        _sessionUnlockedCategories.value = next
    }

    fun addWhatsAppTemplate(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            prefs.saveWhatsAppTemplates((whatsappTemplates.value + clean).distinct())
            _statusMessage.value = "تمت إضافة قالب واتساب"
        }
    }

    fun removeWhatsAppTemplate(text: String) {
        viewModelScope.launch {
            val remaining = whatsappTemplates.value.filterNot { it == text }
            prefs.saveWhatsAppTemplates(remaining.ifEmpty { SyncPrefsDataStore.DEFAULT_WHATSAPP_TEMPLATES })
        }
    }

    fun dismissPostCallSummary() {
        RabtaInCallService.clearLastEndedCallSummary()
    }

    fun scheduleFollowUpReminder(
        contactName: String,
        phone: String,
        contactId: String?,
        delayMinutes: Int?,
        note: String = "",
    ) {
        val label = contactName.ifBlank { phone }
        val triggerEpoch = if (delayMinutes == null) {
            FollowUpReminderScheduler.scheduleTomorrowMorning(
                context = appContext,
                contactName = label,
                phone = phone,
                contactId = contactId,
                hourOfDay = 10,
                note = note,
            )
        } else {
            FollowUpReminderScheduler.scheduleAfterMinutes(
                context = appContext,
                contactName = label,
                phone = phone,
                contactId = contactId,
                delayMinutes = delayMinutes,
                note = note,
            )
        }
        val fmt = SimpleDateFormat("dd/MM hh:mm a", Locale.getDefault())
        _statusMessage.value = "تم جدولة تذكير لمتابعة $label (${fmt.format(Date(triggerEpoch))})"
    }

    fun openContactByPhoneOrId(contactId: String?, phone: String?) {
        viewModelScope.launch {
            val matched = withContext(Dispatchers.IO) {
                contactId?.let { contactDao.getById(it)?.toDomain() }
                    ?: run {
                        val key = PhoneUtils.phoneSearchKey(phone.orEmpty())
                        if (key.length >= 4) contactDao.findByExactPhoneKey(key)?.toDomain() else null
                    }
            }
            if (matched != null) {
                _selectedContact.value = matched
            }
        }
    }

    fun launchWhatsAppQuick(phone: String, name: String, contactId: String?, prefillMessage: String? = null) {
        val dummyOrReal = ContactRecord(
            id = contactId ?: "quick_${phone.hashCode()}",
            displayName = name.ifBlank { phone },
            primaryPhone = phone,
            phones = listOf(phone),
        )
        launchWhatsAppAndLog(dummyOrReal, phone, prefillMessage)
    }

    fun refreshRecordings() {
        viewModelScope.launch(Dispatchers.IO) {
            _callRecordings.value = CallRecorderManager.listRecordings(appContext)
            _cacheStats.value = LocalMediaCacheManager.computeCacheStats(appContext)
        }
    }

    fun refreshCacheStats() {
        viewModelScope.launch(Dispatchers.IO) {
            _cacheStats.value = LocalMediaCacheManager.computeCacheStats(appContext)
        }
    }

    fun triggerCloudSyncNow() {
        bulkWorkScope.launch {
            syncEngine.syncNow(currentUserId, resetFailed = true)
        }
    }

    fun loadPreCallContext(contactId: String?, phone: String) {
        if (contactId.isNullOrBlank() && phone.count { it.isDigit() } < 4) {
            _preCallContext.value = null
            return
        }
        viewModelScope.launch {
            _preCallContext.value = syncEngine.getPreCallContext(contactId, phone)
        }
    }

    fun clearPreCallContext() {
        _preCallContext.value = null
    }

    fun deleteRecording(item: CallRecordingItem) {
        viewModelScope.launch(Dispatchers.IO) {
            CallRecorderManager.deleteRecording(item.file)
            _callRecordings.value = CallRecorderManager.listRecordings(appContext)
            _cacheStats.value = LocalMediaCacheManager.computeCacheStats(appContext)
        }
        _statusMessage.value = "تم حذف التسجيل"
    }

    fun purgeOrphanedMediaCache() {
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) {
                val allTranscripts = transcriptDao.observeAll().firstOrNull().orEmpty()
                val referenced = buildSet {
                    allTranscripts.forEach { entity ->
                        if (entity.audioFilePath.isNotBlank()) add(entity.audioFilePath)
                        if (entity.audioFileName.isNotBlank()) add(entity.audioFileName)
                    }
                }
                val purged = LocalMediaCacheManager.purgeOrphanedAudioFiles(appContext, referenced)
                _callRecordings.value = CallRecorderManager.listRecordings(appContext)
                _cacheStats.value = LocalMediaCacheManager.computeCacheStats(appContext)
                purged
            }
            _statusMessage.value = if (removed > 0) {
                "تم تنظيف $removed ملف صوتي غير مرتبط من الكاش"
            } else {
                "الكاش المحلي نظيف بالفعل (كل التسجيلات مرتبطة بتفريغاتها)"
            }
        }
    }

    fun scheduleActionItemReminder(record: CallTranscriptRecord, item: CallActionItem) {
        val now = System.currentTimeMillis()
        val dueMs = item.dueEpochMs
        val delayMinutes = if (dueMs != null && dueMs > now + 60_000L) {
            ((dueMs - now) / 60_000L).toInt().coerceAtLeast(1)
        } else {
            null // Tomorrow 10:00 AM smart default
        }
        scheduleFollowUpReminder(
            contactName = record.contactName.ifBlank { record.phone },
            phone = record.phone,
            contactId = record.contactId,
            delayMinutes = delayMinutes,
            note = item.title,
        )
    }

    fun updateContactPhoto(contact: ContactRecord, photoPath: String?) {
        viewModelScope.launch {
            val updated = contact.copy(
                photoUri = photoPath?.takeIf { it.isNotBlank() },
                syncedToDevice = false,
            )
            syncEngine.saveContactLocalFirst(updated)
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
                audioFileName = item.file.name,
                audioSizeBytes = item.file.length(),
                audioCachedLocally = item.file.exists() && item.file.length() > 256L,
                timestampMs = item.timestampMs,
                durationSec = 0L,
                status = "PENDING",
                updatedAtEpochMs = System.currentTimeMillis(),
                syncState = SyncState.PENDING.name,
            )
            syncEngine.saveTranscriptLocalFirst(rec, enqueueCloudSync = true)
            analyzeCallTranscript(rec)
        }
    }

    private suspend fun analyzeCallTranscriptInternal(record: CallTranscriptRecord) {
        try {
            val audioFile = LocalMediaCacheManager.resolvePlayableAudioFile(
                context = appContext,
                audioFilePath = record.audioFilePath,
                audioFileName = record.audioFileName,
            ) ?: File(record.audioFilePath)
            if (!audioFile.exists() || audioFile.length() == 0L) {
                syncEngine.saveTranscriptLocalFirst(
                    record.copy(status = "ERROR", transcript = "ملف الصوت غير موجود أو فارغ"),
                    enqueueCloudSync = false,
                )
                return
            }
            val cachedAudio = LocalMediaCacheManager.ensureRecordingCachedLocally(appContext, audioFile) ?: audioFile
            syncEngine.saveTranscriptLocalFirst(
                record.copy(
                    status = "PROCESSING",
                    audioFilePath = cachedAudio.absolutePath,
                    audioFileName = cachedAudio.name,
                    audioSizeBytes = cachedAudio.length(),
                    audioCachedLocally = true,
                ),
                enqueueCloudSync = false,
            )
            val res = aiAssistant.analyzeCallRecording(
                audioFile = cachedAudio,
                contactName = record.contactName,
                phone = record.phone,
            )
            res.onSuccess { analysis ->
                val localEnrich = LocalTranscriptAnalyzer.analyze(
                    rawTranscript = analysis.transcript,
                    contactName = record.contactName,
                    phone = record.phone,
                    referenceTimeMs = record.timestampMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    durationSec = record.durationSec,
                )
                val bullets = analysis.summaryBullets.take(3)
                    .ifEmpty { analysis.highlights.take(3) }
                    .ifEmpty { localEnrich.summaryBullets }
                val headline = analysis.oneLineSummary.ifBlank {
                    bullets.firstOrNull() ?: localEnrich.oneLineSummary
                }
                val finalSegments = analysis.segments.ifEmpty { localEnrich.segments }
                val finalActionItems = (analysis.actionItems + localEnrich.actionItems)
                    .distinctBy { it.title.trim().lowercase(Locale.getDefault()) }
                    .take(6)
                val readyRecord = record.copy(
                    audioFilePath = cachedAudio.absolutePath,
                    audioFileName = cachedAudio.name,
                    audioSizeBytes = cachedAudio.length(),
                    audioCachedLocally = true,
                    transcript = analysis.transcript,
                    highlights = analysis.highlights,
                    summaryBullets = bullets,
                    oneLineSummary = headline,
                    segments = finalSegments,
                    actionItems = finalActionItems,
                    extractedNotes = analysis.extractedNotes.ifBlank { localEnrich.extractedNotes },
                    suggestedEventTitle = analysis.eventTitle.ifBlank { localEnrich.eventTitle },
                    suggestedEventDateIso = analysis.eventDateIso.ifBlank { localEnrich.eventDateIso },
                    suggestedEventEpochMs = analysis.eventEpochMs ?: localEnrich.eventEpochMs,
                    suggestedCategory = analysis.suggestedCategory,
                    status = "READY",
                )
                syncEngine.saveTranscriptLocalFirst(readyRecord, enqueueCloudSync = true)
                if (readyRecord.extractedNotes.isNotBlank() && !record.notesSavedToContact) {
                    saveTranscriptNotesToContact(readyRecord, silent = true)
                }
                _statusMessage.value = "تم تفريغ وتحليل مكالمة ${record.contactName} بالذكاء الاصطناعي"
            }.onFailure { err ->
                if (record.transcript.isNotBlank()) {
                    val localOnly = LocalTranscriptAnalyzer.analyze(
                        rawTranscript = record.transcript,
                        contactName = record.contactName,
                        phone = record.phone,
                        referenceTimeMs = record.timestampMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                        durationSec = record.durationSec,
                    )
                    syncEngine.saveTranscriptLocalFirst(
                        record.copy(
                            summaryBullets = localOnly.summaryBullets,
                            oneLineSummary = localOnly.oneLineSummary,
                            segments = localOnly.segments,
                            actionItems = localOnly.actionItems,
                            highlights = localOnly.highlights,
                            extractedNotes = localOnly.extractedNotes,
                            status = "READY",
                        ),
                        enqueueCloudSync = true,
                    )
                } else {
                    syncEngine.saveTranscriptLocalFirst(
                        record.copy(
                            status = "ERROR",
                            transcript = err.localizedMessage ?: "تعذر تفريغ التسجيل",
                        ),
                        enqueueCloudSync = true,
                    )
                }
            }
        } finally {
            processingTranscriptIds.remove(record.id)
        }
    }

    fun saveLiveSpeechTranscript(
        rawText: String,
        contactNameInput: String = "",
        phoneInput: String = "",
    ) {
        val clean = rawText.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val cleanPhone = PhoneUtils.normalizePhone(phoneInput).ifBlank { phoneInput.trim() }
            val matchedContact = withContext(Dispatchers.IO) {
                val key = PhoneUtils.phoneSearchKey(cleanPhone)
                if (key.length >= 6) contactDao.findByExactPhoneKey(key)?.toDomain() else null
            }
            val displayName = contactNameInput.trim().ifBlank {
                matchedContact?.displayName ?: cleanPhone.ifBlank { "تفريغ صوتي مباشر" }
            }
            val nowMs = System.currentTimeMillis()
            val analysis = LocalTranscriptAnalyzer.analyze(
                rawTranscript = clean,
                contactName = displayName,
                phone = cleanPhone,
                referenceTimeMs = nowMs,
            )
            val record = CallTranscriptRecord(
                id = "tr_" + UUID.randomUUID().toString().replace("-", "").take(14),
                contactId = matchedContact?.id,
                phone = cleanPhone.ifBlank { matchedContact?.primaryPhone.orEmpty() },
                contactName = displayName,
                transcript = analysis.transcript,
                highlights = analysis.highlights,
                summaryBullets = analysis.summaryBullets,
                oneLineSummary = analysis.oneLineSummary,
                segments = analysis.segments,
                actionItems = analysis.actionItems,
                extractedNotes = analysis.extractedNotes,
                suggestedEventTitle = analysis.eventTitle,
                suggestedEventDateIso = analysis.eventDateIso,
                suggestedEventEpochMs = analysis.eventEpochMs,
                timestampMs = nowMs,
                durationSec = 0L,
                status = "READY",
                updatedAtEpochMs = nowMs,
                syncState = SyncState.PENDING.name,
            )
            syncEngine.saveTranscriptLocalFirst(record, enqueueCloudSync = true)
            if (matchedContact != null && analysis.extractedNotes.isNotBlank()) {
                saveTranscriptNotesToContact(record, silent = true)
            }
            _statusMessage.value = "تم حفظ التفريغ المباشر واستخراج الملاحظات والمواعيد (مجاناً بدون Gemini)"
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
            syncEngine.saveContactLocalFirst(updatedContact)
            syncEngine.markTranscriptNotesSaved(record.id)
            if (_selectedContact.value?.id == updatedContact.id) {
                _selectedContact.value = updatedContact
            }
            if (!silent) _statusMessage.value = "تمت إضافة ملاحظات المكالمة إلى ${updatedContact.displayName}"
        }
    }

    fun markCalendarEventAdded(transcriptId: String) {
        viewModelScope.launch {
            syncEngine.markTranscriptCalendarAdded(transcriptId)
        }
    }

    fun softDeleteCallLog(callLogId: String) {
        viewModelScope.launch {
            syncEngine.softDeleteCallLogLocalFirst(callLogId)
        }
    }

    fun restoreCallLog(callLogId: String) {
        viewModelScope.launch {
            syncEngine.restoreCallLogLocalFirst(callLogId)
        }
    }

    fun softDeleteTranscript(transcriptId: String) {
        viewModelScope.launch {
            syncEngine.softDeleteTranscriptLocalFirst(transcriptId)
            refreshRecordings()
            _statusMessage.value = "تم حذف التسجيل والملخص من الخزنة"
        }
    }

    fun toggleTranscriptActionItem(transcriptId: String, actionItemId: String) {
        viewModelScope.launch {
            val current = withContext(Dispatchers.IO) {
                transcriptDao.getById(transcriptId)?.toDomain()
            } ?: return@launch
            val updatedItems = current.actionItems.map { item ->
                if (item.id == actionItemId) item.copy(completed = !item.completed) else item
            }
            syncEngine.saveTranscriptLocalFirst(
                record = current.copy(actionItems = updatedItems),
                enqueueCloudSync = true,
            )
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

    private fun CallTranscriptRecord.toEntity(): CallTranscriptEntity = CallTranscriptEntity.fromDomain(this)

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
                val entities = callLogReader.readDeviceCallLogsAsEntities(limit = 250)
                syncEngine.ingestDeviceCallLogsIntoLocalDb(entities)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.trim().isEmpty()) {
            _searchResults.value = emptyList()
            _globalSearchResult.value = GlobalSearchResult()
            return
        }
        searchJob = viewModelScope.launch {
            delay(150L)
            val global = syncEngine.searchGlobal(query)
            _globalSearchResult.value = global
            _searchResults.value = global.contacts
        }
    }

    private suspend fun loadAllContactsPaged(): List<ContactRecord> = withContext(Dispatchers.IO) {
        val total = contactDao.getTotalCount()
        val all = ArrayList<ContactRecord>(total)
        var offset = 0
        val pageSize = 1500
        while (true) {
            val page = contactDao.getRecentOrAllPage(limit = pageSize, offset = offset)
            if (page.isEmpty()) break
            page.forEach { all += it.toDomain() }
            offset += page.size
        }
        all
    }

    fun searchForDialpad(query: String) {
        dialpadJob?.cancel()
        val raw = query.trim()
        val trimmed = ContactEntity.normalizeForSearch(raw)
        if (trimmed.isEmpty()) {
            _dialpadMatches.value = emptyList()
            return
        }
        dialpadJob = viewModelScope.launch {
            delay(65L)
            val results = withContext(Dispatchers.IO) {
                val roomHits = contactDao.searchContacts(trimmed, limit = 20, offset = 0).map { it.toDomain() }
                val flowContacts = allContactsAlphabetical.value
                val candidatePool = if (flowContacts.isNotEmpty()) {
                    dialpadContactsCache = flowContacts
                    dialpadCacheUpdatedMs = System.currentTimeMillis()
                    flowContacts
                } else {
                    val now = System.currentTimeMillis()
                    if (dialpadContactsCache.isEmpty() || now - dialpadCacheUpdatedMs > 15_000L) {
                        dialpadContactsCache = loadAllContactsPaged()
                        dialpadCacheUpdatedMs = now
                    }
                    dialpadContactsCache
                }
                val t9Hits = if (candidatePool.isNotEmpty()) {
                    T9SearchMatcher.filterAndRank(raw, candidatePool, limit = 20)
                } else {
                    emptyList()
                }
                (t9Hits + roomHits).distinctBy { it.id }.take(20)
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
            syncEngine.saveCategoriesLocalFirst(nodes)
            loadCategoryLevel(_currentCategoryPath.value)
            _statusMessage.value = "تم إنشاء الفئة بنجاح"
        }
    }

    fun openContact(contact: ContactRecord) {
        _selectedContact.value = contact
        loadPreCallContext(contact.id, contact.primaryPhone.ifBlank { contact.phones.firstOrNull().orEmpty() })
    }

    fun closeContact() {
        _selectedContact.value = null
    }

    fun saveContact(contact: ContactRecord, syncToCloud: Boolean = true) {
        viewModelScope.launch {
            val id = contact.id.ifBlank { "c_" + UUID.randomUUID().toString().replace("-", "").take(16) }
            val incoming = ContactDeduplicator.enrichSingleContact(contact.copy(id = id, syncedToDevice = false))
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
                    mergedWinner = (dedup.mergedContacts.firstOrNull() ?: incoming).copy(syncedToDevice = false)
                    supersededIds = (dedup.supersededIds + incoming.id + existingMatches.map { it.id })
                        .filter { it != mergedWinner.id }
                        .distinct()
                }
                if (mergedWinner.categoryPaths.isNotEmpty()) {
                    catNodesToSync = buildCategoryNodes(mergedWinner.categoryPaths.toSet())
                }
            }

            syncEngine.saveContactLocalFirst(
                contact = mergedWinner,
                categories = catNodesToSync,
                supersededIds = supersededIds,
                syncToCloud = syncToCloud,
            )

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
            syncEngine.softDeleteContactLocalFirst(contact.id)
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
            syncEngine.toggleFavoriteLocalFirst(contact.id, next)
            if (_selectedContact.value?.id == contact.id) {
                _selectedContact.value = contact.copy(isFavorite = next)
            }
            loadCategoryLevel(_currentCategoryPath.value)
            launch { RabtaWidget.refreshAll(appContext) }
        }
    }

    fun toggleBlocked(contact: ContactRecord) {
        val next = !contact.blocked
        viewModelScope.launch {
            syncEngine.toggleBlockedLocalFirst(contact.id, next)
            if (_selectedContact.value?.id == contact.id) {
                _selectedContact.value = contact.copy(blocked = next)
            }
            _statusMessage.value = if (next) "تم حظر ${contact.displayName}" else "تم إلغاء حظر ${contact.displayName}"
        }
    }

    fun toggleMuted(contact: ContactRecord) {
        val next = !contact.muted
        viewModelScope.launch {
            syncEngine.toggleMutedLocalFirst(contact.id, next)
            if (_selectedContact.value?.id == contact.id) {
                _selectedContact.value = contact.copy(muted = next)
            }
            _statusMessage.value = if (next) "تم كتم إشعارات ${contact.displayName}" else "تم تفعيل إشعارات ${contact.displayName}"
        }
    }

    fun launchWhatsAppAndLog(contact: ContactRecord, phone: String, prefillMessage: String? = null) {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val logId = "wa_" + UUID.randomUUID().toString().replace("-", "").take(16)
            syncEngine.saveWhatsAppLogLocalFirst(
                WhatsAppLog(
                    id = logId,
                    contactId = contact.id,
                    contactName = contact.displayName,
                    phone = phone,
                    loggedAtEpochMs = now,
                    syncedToPhone = false,
                )
            )
        }
        SystemIntents.openWhatsApp(appContext, phone, prefillMessage)
    }

    fun toggleDeviceContactSync(@Suppress("UNUSED_PARAMETER") contact: ContactRecord) {
        _statusMessage.value = "وضع العزل الكامل مفعّل دائماً: جهات الاتصال محمية داخل التطبيق فقط ولا تُزامن مع سجل الهاتف أو واتساب."
    }

    fun syncWeeklyActiveWhatsAppToPhonebook() {
        _statusMessage.value = "وضع العزل الكامل مفعّل دائماً: لا يتم تصدير أي أرقام إلى سجل الهاتف أو واتساب."
    }

    fun purgeAllSyncedContactsFromPhonebook() {
        _statusMessage.value = "كل جهات اتصالك معزولة بالكامل داخل Felo Contacts ولا توجد أرقام متزامنة مع الهاتف."
    }

    fun importBundledDatasets(uploadToCloud: Boolean = true) {
        if (_isBusy.value) return
        bulkWorkScope.launch {
            _isBusy.value = true
            _importProgressText.value = "جاري قراءة ملفات جهات الاتصال المرفقة..."
            try {
                val (allContacts, allCatPaths) = withContext(Dispatchers.IO) {
                    val s1 = appContext.assets.open("contacts_seed.csv").use {
                        CsvParser.parseStream(it, skipEnrich = true)
                    }
                    val s2 = appContext.assets.open("eaeat_2023_leader.csv").use {
                        CsvParser.parseStream(it, defaultCategory = "الجامعة > الأكاديمية (EAEAT) > دفعة 2023", skipEnrich = true)
                    }
                    (s1.contacts + s2.contacts) to (s1.categoryPaths + s2.categoryPaths)
                }
                val result = persistImportedDataset(allContacts, allCatPaths, uploadToCloud)
                prefs.setBundledImported(true)
                val verifiedTotal = contactDao.getTotalCount()
                _statusMessage.value = "تم استيراد ${result.importedCount} جهة اتصال (الإجمالي الآن: $verifiedTotal • ${result.categoriesCreated} فئة)"
            } catch (e: Exception) {
                _statusMessage.value = "فشل الاستيراد: ${e.localizedMessage}"
            } finally {
                _isBusy.value = false
                _importProgressText.value = null
            }
        }
    }

    fun importDevicePhonebookContacts(uploadToCloud: Boolean = true) {
        if (_isBusy.value) return
        bulkWorkScope.launch {
            _isBusy.value = true
            _importProgressText.value = "جاري قراءة كل جهات الاتصال من سجل الهاتف (People)..."
            try {
                val res = deviceContactsManager.readAllDeviceContacts()
                res.onSuccess { (contacts, cats) ->
                    if (contacts.isEmpty()) {
                        _statusMessage.value = "لم يتم العثور على جهات اتصال في الهاتف"
                    } else {
                        val summary = persistImportedDataset(contacts, cats, uploadToCloud)
                        val verifiedTotal = contactDao.getTotalCount()
                        _statusMessage.value = "تم حفظ ${summary.importedCount} جهة من الهاتف (الإجمالي الآن: $verifiedTotal)"
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
        if (_isBusy.value) return
        bulkWorkScope.launch {
            _isBusy.value = true
            _importProgressText.value = "جاري البحث داخل Google Drive عن جداول وملفات بها أرقام..."
            try {
                val outcomeRes = GoogleDriveScanner.scanAndExtractContacts(accessToken)
                outcomeRes.onSuccess { outcome ->
                    if (outcome.contacts.isEmpty()) {
                        _statusMessage.value = "لم يتم العثور على أرقام جديدة داخل ملفات Google Drive النصية/الجداول"
                    } else {
                        val summary = persistImportedDataset(outcome.contacts, outcome.categoryPaths, uploadToCloud = true)
                        val verifiedTotal = contactDao.getTotalCount()
                        _statusMessage.value = "تم استخراج وحفظ ${summary.importedCount} رقم من ${outcome.filesScanned} ملف في Drive (الإجمالي: $verifiedTotal)"
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
        if (_isBusy.value) return
        bulkWorkScope.launch {
            _isBusy.value = true
            val cleanTitle = fileName?.substringAfterLast('/')?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "ملف مستورد"
            _importProgressText.value = "جاري قراءة وتحليل ($cleanTitle)..."
            try {
                val parsed = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { rawStream ->
                        val buffered = BufferedInputStream(rawStream, 32 * 1024)
                        buffered.mark(8192)
                        val probe = ByteArray(4096)
                        val probeLen = buffered.read(probe).coerceAtLeast(0)
                        buffered.reset()
                        val head = String(probe, 0, probeLen, StandardCharsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
                        val looksJson = (fileName?.endsWith(".json", ignoreCase = true) == true) ||
                            head.startsWith("{") || head.startsWith("[")
                        if (looksJson) {
                            val text = buffered.bufferedReader(StandardCharsets.UTF_8).readText()
                            val jsonContacts = JsonContactExporter.parseFromJson(text).getOrElse { emptyList() }
                            if (jsonContacts.isNotEmpty()) {
                                val cats = LinkedHashSet<String>()
                                jsonContacts.forEach { c ->
                                    c.categoryPaths.forEach { CsvParser.addHierarchySegments(it, cats) }
                                }
                                CsvParser.ParsedDataset(jsonContacts, 0, cats)
                            } else {
                                CsvParser.parseArbitrarySheetOrText(text, cleanTitle)
                            }
                        } else {
                            val streamParsed = CsvParser.parseStreamAuto(
                                inputStream = buffered,
                                sourceTitle = cleanTitle,
                                skipEnrich = true,
                            )
                            if (streamParsed.contacts.isNotEmpty()) {
                                streamParsed
                            } else {
                                // Fallback for messy plain-text dumps with no CSV delimiters
                                appContext.contentResolver.openInputStream(uri)?.use { retryStream ->
                                    val rawText = retryStream.bufferedReader(StandardCharsets.UTF_8).readText()
                                    CsvParser.parseArbitrarySheetOrText(rawText, cleanTitle)
                                } ?: streamParsed
                            }
                        }
                    } ?: CsvParser.ParsedDataset(emptyList(), 0, emptySet())
                }
                if (parsed.contacts.isEmpty()) {
                    _statusMessage.value = "لم يتم العثور على جهات اتصال صالحة في الملف"
                } else {
                    val result = persistImportedDataset(parsed.contacts, parsed.categoryPaths, uploadToCloud)
                    val verifiedTotal = contactDao.getTotalCount()
                    _statusMessage.value = "تم حفظ ${result.importedCount} جهة اتصال (إجمالي قاعدتك الآن: $verifiedTotal) وجاري الرفع السريع للسحابة"
                }
            } catch (e: Exception) {
                _statusMessage.value = "تعذر قراءة الملف: ${e.localizedMessage}"
            } finally {
                _isBusy.value = false
                _importProgressText.value = null
            }
        }
    }

    fun scanDuplicateClusters() {
        viewModelScope.launch(Dispatchers.IO) {
            val allDomains = loadAllContactsPaged()
            _duplicateClusters.value = ContactDeduplicator.findDuplicateClusters(allDomains)
        }
    }

    fun mergeDuplicateCluster(cluster: List<ContactRecord>) {
        if (cluster.size < 2) return
        viewModelScope.launch {
            val dedup = ContactDeduplicator.deduplicateAndMerge(cluster, enforceBilingual = true)
            val winner = dedup.mergedContacts.firstOrNull() ?: return@launch
            val droppedIds = cluster.map { it.id }.filter { it.isNotBlank() && it != winner.id }
            val cats = buildCategoryNodes(winner.categoryPaths.toSet())
            syncEngine.saveContactLocalFirst(
                contact = winner,
                categories = cats,
                supersededIds = droppedIds,
                syncToCloud = true,
            )
            _duplicateClusters.value = _duplicateClusters.value.filterNot { it == cluster }
            loadCategoryLevel(_currentCategoryPath.value)
            _statusMessage.value = "تم دمج ${cluster.size} سجلات في ${winner.displayName}"
        }
    }

    fun mergeAllDuplicateClusters() {
        reorganizeAllCategories()
        _duplicateClusters.value = emptyList()
    }

    /**
     * Full-database anti-duplicate merge + nested category builder + English primary / Arabic nickname
     * normalization + searchable bilingual tags across ALL stored contacts.
     */
    fun reorganizeAllCategories() {
        if (_isBusy.value) return
        bulkWorkScope.launch {
            _isBusy.value = true
            _importProgressText.value = "جاري فحص ودمج كل الأسماء المكررة، توحيد English/العربي، وبناء التاجز..."
            try {
                val outcome = withContext(Dispatchers.IO) {
                    val allDomains = loadAllContactsPaged()

                    val dedup = ContactDeduplicator.deduplicateAndMerge(allDomains, enforceBilingual = true)
                    val allCatPaths = LinkedHashSet<String>()
                    dedup.mergedContacts.forEach { c ->
                        c.categoryPaths.forEach { CsvParser.addHierarchySegments(it, allCatPaths) }
                    }
                    val catNodes = buildCategoryNodes(allCatPaths)
                    val mergedCount = dedup.mergedContacts.size

                    contactDao.clearCategoryRefs()
                    contactDao.clearPhoneKeys()
                    contactDao.clearAll()
                    categoryDao.clearAll()

                    syncEngine.saveContactsBatchChunkedLocalFirst(
                        contacts = dedup.mergedContacts,
                        categories = catNodes,
                        supersededIds = dedup.supersededIds,
                        syncToCloud = true,
                        chunkSize = 500,
                    ) { saved, total ->
                        _importProgressText.value = "جاري حفظ التنظيم الجديد: $saved / $total..."
                    }
                    dialpadCacheUpdatedMs = 0L
                    Triple(dedup, catNodes, mergedCount)
                }

                val dedup = outcome.first
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
        _importProgressText.value = "جاري فحص التكرار لـ ${contacts.size} جهة اتصال..."

        val (finalMerged, supersededIds, allCats) = withContext(Dispatchers.Default) {
            // Collect phone keys directly from incoming batch in a single fast pass
            val incomingKeys = HashSet<String>(contacts.size)
            for (c in contacts) {
                val pk = PhoneUtils.phoneSearchKey(c.primaryPhone)
                if (pk.length >= 7) incomingKeys += pk
                for (p in c.phones) {
                    val k = PhoneUtils.phoneSearchKey(p)
                    if (k.length >= 7) incomingKeys += k
                }
            }

            // Pull only existing Room contacts that share a phone key so we merge cleanly without duplicates
            val existingMatched = withContext(Dispatchers.IO) {
                if (incomingKeys.isNotEmpty()) {
                    val found = LinkedHashMap<String, ContactRecord>()
                    for (chunk in incomingKeys.chunked(450)) {
                        for (match in contactDao.findAllByPhoneKeys(chunk)) {
                            found.putIfAbsent(match.contact.id, match.contact.toDomain())
                        }
                    }
                    found.values.toList()
                } else {
                    emptyList()
                }
            }

            _importProgressText.value = "جاري دمج وفهرسة ${contacts.size} جهة اتصال باللغتين والتاجز..."
            // Single-pass dedup + bilingual enrichment (eliminates 3x redundant passes on 16k+ files)
            val fullDedup = ContactDeduplicator.deduplicateAndMerge(
                existingMatched + contacts,
                enforceBilingual = true,
            )

            val catSet = LinkedHashSet(catPaths)
            fullDedup.mergedContacts.forEach { c ->
                c.categoryPaths.forEach { CsvParser.addHierarchySegments(it, catSet) }
            }
            Triple(fullDedup.mergedContacts, fullDedup.supersededIds, catSet)
        }

        val entitiesCount = finalMerged.size
        val catNodes = buildCategoryNodes(allCats)

        _importProgressText.value = "جاري حفظ الجهات محلياً: 0 / $entitiesCount..."
        syncEngine.saveContactsBatchChunkedLocalFirst(
            contacts = finalMerged,
            categories = catNodes,
            supersededIds = supersededIds,
            syncToCloud = uploadToCloud,
            chunkSize = 500,
        ) { savedSoFar, total ->
            _importProgressText.value = "جاري حفظ الجهات محلياً: $savedSoFar / $total..."
        }
        dialpadCacheUpdatedMs = 0L

        loadCategoryLevel(_currentCategoryPath.value)
        refreshCallLogs()
        bulkWorkScope.launch { RabtaWidget.refreshAll(appContext) }

        if (uploadToCloud && finalMerged.isNotEmpty()) {
            cloudUploadJob?.cancel()
            cloudUploadJob = bulkWorkScope.launch {
                _cloudSyncStatus.value = "جاري الرفع السريع للسحابة..."
                syncEngine.syncNow(currentUserId, resetFailed = false)
                _cloudSyncStatus.value = null
            }
        }

        return CsvImportSummary(
            importedCount = entitiesCount,
            skippedCount = supersededIds.size,
            categoriesCreated = catNodes.size,
            geoMatchedCount = finalMerged.count { it.latitude != null },
        )
    }

    fun pullAllFromCloud() {
        if (_isBusy.value) return
        bulkWorkScope.launch {
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
                val (mergedAll, supersededIds, dupRemoved) = withContext(Dispatchers.Default) {
                    val existingAll = loadAllContactsPaged()
                    val dedup = ContactDeduplicator.deduplicateAndMerge(
                        existingAll + pulledAll,
                        enforceBilingual = true,
                    )
                    val catSet = LinkedHashSet<String>()
                    dedup.mergedContacts.forEach { c ->
                        c.categoryPaths.forEach { CsvParser.addHierarchySegments(it, catSet) }
                    }
                    val builtCats = buildCategoryNodes(catSet)

                    syncEngine.saveContactsBatchChunkedLocalFirst(
                        contacts = dedup.mergedContacts,
                        categories = builtCats,
                        supersededIds = dedup.supersededIds,
                        syncToCloud = false,
                        chunkSize = 500,
                    ) { saved, total ->
                        _importProgressText.value = "جاري حفظ بيانات السحابة محلياً: $saved / $total..."
                    }
                    dialpadCacheUpdatedMs = 0L
                    Triple(dedup.mergedContacts, dedup.supersededIds, dedup.totalDuplicatesRemoved)
                }

                if (supersededIds.isNotEmpty()) {
                    bulkWorkScope.launch { firestoreRepo.batchDeleteContacts(currentUserId, supersededIds) }
                }

                prefs.setLastSyncMs(System.currentTimeMillis())
                loadCategoryLevel(_currentCategoryPath.value)
                refreshCallLogs()
                bulkWorkScope.launch { RabtaWidget.refreshAll(appContext) }
                _statusMessage.value = if (dupRemoved > 0) {
                    "تمت المزامنة بدون تكرار: ${mergedAll.size} جهة (ودمج $dupRemoved مكرر تلقائياً)"
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
    ) {
        val clean = userPrompt.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _aiUiState.value = AiAssistantUiState.Loading
            try {
                val topCats = withContext(Dispatchers.IO) {
                    categoryDao.getChildren("").map { it.name }
                }
                val localPlan = LocalTranscriptAnalyzer.tryParseCommandLocally(clean, topCats)
                val plan = localPlan ?: run {
                    val planRes = aiAssistant.parseCommand(clean, topCats)
                    planRes.getOrElse { err ->
                        val fallbackMatches = findMatchesInRoom(clean)
                        if (fallbackMatches.isNotEmpty()) {
                            _aiUiState.value = AiAssistantUiState.Success(
                                reply = "نتائج البحث الفوري لـ '$clean' (محلياً بدون استهلاك رصيد Gemini):",
                                actionBadge = "بحث فوري (${fallbackMatches.size})",
                                matchedContacts = fallbackMatches,
                            )
                        } else {
                            _aiUiState.value = AiAssistantUiState.Error(err.localizedMessage ?: "تعذر الاتصال بـ Gemini AI")
                        }
                        return@launch
                    }
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
                        _aiUiState.value = AiAssistantUiState.Success(
                            reply = "أرقام منطقة $q (${results.size} نتيجة) — افتح أي شخص للضغط على Google Maps:",
                            actionBadge = "منطقة: $q",
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
                var usedLocalFallback = false
                val geminiErrMsg = mapRes.exceptionOrNull()?.localizedMessage
                val assignments = mapRes.getOrElse {
                    usedLocalFallback = true
                    candidates.associate { c ->
                        val (nestedCats, _) = SmartCategoryEngine.normalizeAndNestLabels(
                            rawLabels = c.categoryPaths,
                            displayName = c.displayName,
                            phones = c.phones,
                            emails = c.emails,
                            organization = c.organization,
                            jobTitle = c.jobTitle,
                            notes = c.notes,
                            address = c.address,
                        )
                        val smartPath = nestedCats.firstOrNull { !it.startsWith("جهات اتصال تلقائية") }
                            ?: nestedCats.firstOrNull()
                            ?: "جهات اتصال تلقائية > تصنيف ذكي"
                        c.id to smartPath
                    }
                }
                if (assignments.isEmpty()) {
                    _aiUiState.value = AiAssistantUiState.Error("لم أجد تصنيفات جديدة لهذه الدفعة")
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
                syncEngine.saveContactsBatchLocalFirst(
                    contacts = updatedContacts,
                    categories = catNodes,
                    syncToCloud = true,
                )
                loadCategoryLevel(_currentCategoryPath.value)
                _aiUiState.value = AiAssistantUiState.Success(
                    reply = if (usedLocalFallback) {
                        "تم تصنيف ${updatedContacts.size} جهة اتصال بالمحرك المحلي الذكي (تعذر Gemini: ${geminiErrMsg ?: "خطأ اتصال"})."
                    } else {
                        "تم تصنيف ${updatedContacts.size} جهة اتصال بموديل ${aiAssistant.activeModelId} داخل ${catNodes.size} فئة متداخلة!"
                    },
                    actionBadge = if (usedLocalFallback) "تصنيف محلي ذكي" else "تصنيف ${aiAssistant.activeModelId}",
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
                var usedLocalFallback = false
                val geminiEnrichErr = enrichRes.exceptionOrNull()?.localizedMessage
                val updated = ArrayList<ContactRecord>()
                val map = enrichRes.getOrNull()
                if (map != null) {
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
                } else {
                    usedLocalFallback = true
                    for (c in candidates) {
                        val (enName, arNick) = BilingualNameEngine.resolveEnglishAndNickname(
                            listOfNotNull(c.displayName, c.nickname)
                        )
                        val tags = SmartCategoryEngine.generateTags(
                            displayName = enName.ifBlank { c.displayName },
                            nickname = arNick,
                            phones = c.phones,
                            emails = c.emails,
                            categoryPaths = c.categoryPaths,
                            organization = c.organization,
                            jobTitle = c.jobTitle,
                            notes = c.notes,
                            address = c.address,
                            district = c.district,
                            existingTags = c.tags,
                        )
                        updated += c.copy(
                            displayName = enName.ifBlank { c.displayName },
                            nickname = arNick,
                            tags = tags,
                        )
                    }
                }
                if (updated.isNotEmpty()) {
                    syncEngine.saveContactsBatchLocalFirst(
                        contacts = updated,
                        categories = emptyList(),
                        syncToCloud = true,
                    )
                    if (_searchQuery.value.isNotBlank()) updateSearchQuery(_searchQuery.value)
                    loadCategoryLevel(_currentCategoryPath.value)
                }
                _aiUiState.value = AiAssistantUiState.Success(
                    reply = if (usedLocalFallback) {
                        "تم تحديث ${updated.size} جهة اتصال محلياً (تعذر Gemini: ${geminiEnrichErr ?: "خطأ اتصال"})."
                    } else {
                        "تم تحويل ${updated.size} جهة اتصال عبر ${aiAssistant.activeModelId} مع حفظ Nickname عربي وتاجز البحث!"
                    },
                    actionBadge = if (usedLocalFallback) "ثنائي اللغة محلي" else "${aiAssistant.activeModelId}",
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
                    else -> allContactsAlphabetical.value.ifEmpty { favoriteContacts.value }
                }
            }
            if (contacts.isEmpty()) {
                _statusMessage.value = "اختر فئة أو جهات اتصال أولاً للمشاركة"
                return@launch
            }
            if (asVCard) {
                SystemIntents.shareExportFile(
                    appContext,
                    "felo_contacts_${contacts.size}.vcf",
                    "text/x-vcard",
                    VCardUtils.exportAll(contacts),
                )
            } else {
                SystemIntents.shareExportFile(
                    appContext,
                    "felo_contacts_${contacts.size}.csv",
                    "text/csv",
                    CsvParser.exportToCsv(contacts),
                )
            }
            clearBulkSelection()
        }
    }

    fun exportAllContactsAsJson() {
        viewModelScope.launch {
            val contacts = withContext(Dispatchers.IO) {
                val total = contactDao.getTotalCount()
                val all = ArrayList<ContactRecord>(total)
                var offset = 0
                while (true) {
                    val page = contactDao.getRecentOrAllPage(limit = 1000, offset = offset)
                    if (page.isEmpty()) break
                    page.forEach { all += it.toDomain() }
                    offset += page.size
                }
                all
            }
            if (contacts.isEmpty()) {
                _statusMessage.value = "لا توجد جهات اتصال لتصديرها"
                return@launch
            }
            val payload = JsonContactExporter.exportToJson(contacts)
            SystemIntents.shareExportFile(
                appContext,
                "felo_contacts_isolated_${contacts.size}.json",
                "application/json",
                payload,
            )
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
            syncEngine.savePaymentTemplateLocalFirst(tpl)
            _statusMessage.value = "تمت إضافة خدمة الدفع: ${tpl.name}"
        }
    }

    fun deletePaymentTemplate(templateId: String) {
        viewModelScope.launch {
            syncEngine.deletePaymentTemplateLocalFirst(templateId)
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

    fun assignSpeedDial(digit: String, phone: String?) {
        val cleanDigit = digit.trim()
        if (cleanDigit.length != 1 || cleanDigit[0] !in '1'..'9') return
        viewModelScope.launch {
            prefs.setSpeedDialSlot(cleanDigit, phone?.trim()?.takeIf { it.isNotBlank() })
            _statusMessage.value = if (phone.isNullOrBlank()) {
                "تم مسح الاختصار السريع للرقم $cleanDigit"
            } else {
                "تم تعيين الاتصال السريع ($cleanDigit ← $phone)"
            }
        }
    }

    fun syncGoogleContactsPhotos(accessToken: String) {
        if (_isBusy.value) return
        bulkWorkScope.launch {
            _isBusy.value = true
            _importProgressText.value = "جاري سحب صور جهات الاتصال من Google Contacts..."
            try {
                val existing = loadAllContactsPaged()
                if (existing.isEmpty()) {
                    _statusMessage.value = "لا توجد جهات اتصال محفوظة داخل التطبيق لتحديث صورها"
                    return@launch
                }
                val result = GoogleContactsPhotoSync.fetchAndCachePhotos(
                    context = appContext,
                    accessToken = accessToken,
                    existingContacts = existing,
                )
                result.onSuccess { syncRes ->
                    if (syncRes.photoUpdatesByContactId.isEmpty()) {
                        _statusMessage.value = "تم فحص ${syncRes.scannedConnections} جهة في Google Contacts ولم يتطابق أي رقم مع صور جديدة"
                        return@onSuccess
                    }
                    val updatedList = existing.mapNotNull { c ->
                        val path = syncRes.photoUpdatesByContactId[c.id] ?: return@mapNotNull null
                        c.copy(photoUri = path)
                    }
                    syncEngine.saveContactsBatchChunkedLocalFirst(
                        contacts = updatedList,
                        categories = emptyList(),
                        syncToCloud = false,
                    )
                    dialpadCacheUpdatedMs = 0L
                    _statusMessage.value = "تم سحب ${syncRes.photosDownloaded} صورة من Google Contacts لـ ${updatedList.size} جهة اتصال بنجاح!"
                }.onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "تعذر سحب صور Google Contacts"
                }
            } finally {
                _isBusy.value = false
                _importProgressText.value = null
            }
        }
    }

    fun syncDeviceContactsPhotosOnly() {
        if (_isBusy.value) return
        bulkWorkScope.launch {
            if (!deviceContactsManager.hasReadPermission()) {
                _statusMessage.value = "يلزم منح صلاحية قراءة جهات الاتصال لسحب الصور"
                return@launch
            }
            _isBusy.value = true
            _importProgressText.value = "جاري سحب صور جهات الاتصال من الهاتف..."
            try {
                val existing = loadAllContactsPaged()
                val photoRes = deviceContactsManager.readDevicePhotosByPhoneKey()
                photoRes.onSuccess { map ->
                    if (map.isEmpty()) {
                        _statusMessage.value = "لم يتم العثور على صور جهات اتصال محفوظة على الهاتف"
                        return@onSuccess
                    }
                    val updated = existing.mapNotNull { contact ->
                        val keys = (listOf(contact.primaryPhone) + contact.phones)
                            .map { PhoneUtils.phoneSearchKey(it) }
                            .filter { it.length >= 6 }
                        val foundPhoto = keys.firstNotNullOfOrNull { map[it] } ?: return@mapNotNull null
                        if (contact.photoUri == foundPhoto) return@mapNotNull null
                        contact.copy(photoUri = foundPhoto)
                    }
                    if (updated.isNotEmpty()) {
                        syncEngine.saveContactsBatchChunkedLocalFirst(updated, emptyList(), syncToCloud = false)
                        dialpadCacheUpdatedMs = 0L
                    }
                    _statusMessage.value = "تم تحديث صور ${updated.size} جهة اتصال من الهاتف مع بقاء العزل الكامل"
                }.onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "تعذر قراءة صور الهاتف"
                }
            } finally {
                _isBusy.value = false
                _importProgressText.value = null
            }
        }
    }

    companion object {
        private const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
