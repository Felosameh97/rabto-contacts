package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.device.CallLogReader
import com.example.data.device.DeviceContactsManager
import com.example.data.local.CategoryEntity
import com.example.data.local.ContactEntity
import com.example.data.local.PaymentTemplateEntity
import com.example.data.local.RabtaDatabase
import com.example.data.local.SyncPrefsDataStore
import com.example.data.local.WhatsAppLogEntity
import com.example.data.remote.FirestoreContactsRepository
import com.example.data.remote.GitHubUpdateService
import com.example.domain.model.CallLogEntry
import com.example.domain.model.CategoryNode
import com.example.domain.model.CategorySummary
import com.example.domain.model.ContactRecord
import com.example.domain.model.CsvImportSummary
import com.example.domain.model.GitHubReleaseInfo
import com.example.domain.model.PaymentTemplate
import com.example.domain.model.WhatsAppLog
import com.example.util.CsvParser
import com.example.util.EgyptGeoLookup
import com.example.util.PaymentUtils
import com.example.util.PhoneUtils
import com.example.util.SystemIntents
import com.example.util.VCardUtils
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
) : ViewModel() {

    private val contactDao = db.contactDao()
    private val categoryDao = db.categoryDao()
    private val logDao = db.whatsAppLogDao()
    private val paymentDao = db.paymentTemplateDao()

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

    val githubRepoSlug: StateFlow<String> = prefs.githubRepoFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val lastCloudSyncMs: StateFlow<Long> = prefs.lastSyncFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val bundledImported: StateFlow<Boolean> = prefs.bundledImportedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ContactRecord>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _frequentCalls = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val frequentCalls = _frequentCalls.asStateFlow()

    private val _recentCalls = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val recentCalls = _recentCalls.asStateFlow()

    private val _hasCallLogPermission = MutableStateFlow(callLogReader.hasPermission())
    val hasCallLogPermission = _hasCallLogPermission.asStateFlow()

    private val _hasContactsPermission = MutableStateFlow(deviceContactsManager.hasPermissions())
    val hasContactsPermission = _hasContactsPermission.asStateFlow()

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

    private val _releaseInfo = MutableStateFlow<GitHubReleaseInfo?>(null)
    val releaseInfo = _releaseInfo.asStateFlow()

    private val _autoUpdateRelease = MutableStateFlow<GitHubReleaseInfo?>(null)
    val autoUpdateRelease = _autoUpdateRelease.asStateFlow()

    private val _pendingWhatsAppCapture = MutableStateFlow<ContactRecord?>(null)
    val pendingWhatsAppCapture = _pendingWhatsAppCapture.asStateFlow()

    private var searchJob: Job? = null

    init {
        initializeSession()
    }

    private fun initializeSession() {
        viewModelScope.launch {
            firestoreRepo.ensureUserProfile(currentUserId, currentUserName, currentUserEmail)
            if (paymentDao.count() == 0) {
                paymentDao.upsertAll(
                    PaymentUtils.defaultTemplates.map {
                        PaymentTemplateEntity(it.id, it.name, it.urlTemplate, it.isDefault)
                    }
                )
            }
            refreshCallLogs()
            loadCategoryLevel("")
            // Pull cloud payment templates & logs gently in background
            launch {
                firestoreRepo.observeWhatsAppLogs(currentUserId)
                    .catch { }
                    .collect { cloudLogs ->
                        if (cloudLogs.isNotEmpty()) {
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
            launch {
                firestoreRepo.observePaymentTemplates(currentUserId)
                    .catch { }
                    .collect { templates ->
                        if (templates.isNotEmpty()) {
                            paymentDao.upsertAll(
                                templates.map { PaymentTemplateEntity(it.id, it.name, it.urlTemplate, it.isDefault) }
                            )
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
        }
    }

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
                    } else {
                        null
                    }
                }
                if (parsed != null) {
                    _pendingWhatsAppCapture.value = parsed
                }
            }
        }
    }

    fun refreshPermissionsState() {
        _hasCallLogPermission.value = callLogReader.hasPermission()
        _hasContactsPermission.value = deviceContactsManager.hasPermissions()
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
            val cleaned = query.trim().lowercase()
            val results = withContext(Dispatchers.IO) {
                contactDao.searchContacts(cleaned, limit = 60, offset = 0).map { it.toDomain() }
            }
            _searchResults.value = results
        }
    }

    fun loadCategoryLevel(path: String) {
        _currentCategoryPath.value = path
        viewModelScope.launch(Dispatchers.IO) {
            val allCats = categoryDao.getAll()
            val prefix = if (path.isEmpty()) "" else "$path > "
            val directChildren = if (path.isEmpty()) {
                allCats.filter { it.depth == 0 }
            } else {
                allCats.filter { it.parentPath == path }
            }

            // Also discover dynamic category prefixes from contacts if category table lacks any leaf
            val summaries = directChildren.map { cat ->
                val count = contactDao.countByCategoryPath(cat.fullPath)
                val childCnt = allCats.count { it.parentPath == cat.fullPath }
                CategorySummary(
                    name = cat.name,
                    fullPath = cat.fullPath,
                    parentPath = cat.parentPath,
                    depth = cat.depth,
                    contactCount = count,
                    childCount = childCnt,
                )
            }
            _childCategories.value = summaries

            val contacts = if (path.isNotBlank()) {
                contactDao.getByCategoryPath(path, limit = 100, offset = 0).map { it.toDomain() }
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
            firestoreRepo.upsertCategories(currentUserId, nodes)
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
            val normalizedPhones = (listOf(contact.primaryPhone) + contact.phones)
                .map { PhoneUtils.normalizePhone(it) }
                .filter { it.isNotBlank() }
                .distinct()
                .take(10)
            val geo = if (contact.latitude == null || contact.longitude == null) {
                EgyptGeoLookup.inferLocation(contact.address, contact.district, contact.displayName, contact.categoryPaths.joinToString(" "))
            } else {
                null
            }
            val cleaned = contact.copy(
                id = id,
                primaryPhone = normalizedPhones.firstOrNull().orEmpty(),
                phones = normalizedPhones,
                district = contact.district ?: geo?.key,
                latitude = contact.latitude ?: geo?.lat,
                longitude = contact.longitude ?: geo?.lng,
            )

            withContext(Dispatchers.IO) {
                contactDao.upsert(ContactEntity.fromDomain(cleaned))
                if (cleaned.categoryPaths.isNotEmpty()) {
                    val catNodes = buildCategoryNodes(cleaned.categoryPaths.toSet())
                    categoryDao.upsertAll(catNodes.map {
                        CategoryEntity(it.id, it.name, it.parentPath, it.fullPath, it.depth.toInt(), it.iconKey)
                    })
                    if (syncToCloud) {
                        firestoreRepo.upsertCategories(currentUserId, catNodes)
                    }
                }
            }
            if (syncToCloud) {
                firestoreRepo.upsertContact(currentUserId, cleaned)
            }
            if (_selectedContact.value?.id == cleaned.id) {
                _selectedContact.value = cleaned
            }
            loadCategoryLevel(_currentCategoryPath.value)
            if (_searchQuery.value.isNotBlank()) {
                updateSearchQuery(_searchQuery.value)
            }
            refreshCallLogs()
            _statusMessage.value = "تم حفظ جهة الاتصال: ${cleaned.displayName}"
        }
    }

    fun deleteContact(contact: ContactRecord) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { contactDao.deleteById(contact.id) }
            firestoreRepo.deleteContact(currentUserId, contact.id)
            if (_selectedContact.value?.id == contact.id) {
                _selectedContact.value = null
            }
            loadCategoryLevel(_currentCategoryPath.value)
            if (_searchQuery.value.isNotBlank()) updateSearchQuery(_searchQuery.value)
            _statusMessage.value = "تم حذف ${contact.displayName}"
        }
    }

    fun toggleFavorite(contact: ContactRecord) {
        val next = !contact.isFavorite
        viewModelScope.launch {
            withContext(Dispatchers.IO) { contactDao.updateFavoriteFlag(contact.id, next) }
            firestoreRepo.setContactFavorite(currentUserId, contact.id, next)
            if (_selectedContact.value?.id == contact.id) {
                _selectedContact.value = contact.copy(isFavorite = next)
            }
            loadCategoryLevel(_currentCategoryPath.value)
        }
    }

    fun launchWhatsAppAndLog(contact: ContactRecord, phone: String) {
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
            firestoreRepo.createWhatsAppLog(currentUserId, entity.toDomain())
        }
        SystemIntents.openWhatsApp(appContext, phone)
    }

    fun toggleDeviceContactSync(contact: ContactRecord) {
        viewModelScope.launch {
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
                firestoreRepo.setContactSyncedToDevice(currentUserId, contact.id, nowSynced)
                if (_selectedContact.value?.id == contact.id) {
                    _selectedContact.value = contact.copy(syncedToDevice = nowSynced)
                }
                _statusMessage.value = if (nowSynced) {
                    "تم حفظ ${contact.displayName} في الهاتف ليتعرف عليه واتساب"
                } else {
                    "تمت إزالة ${contact.displayName} من سجل الهاتف مع بقائه في التطبيق"
                }
            }.onFailure {
                _statusMessage.value = "تعذرت المزامنة: ${it.localizedMessage}"
            }
        }
    }

    fun syncWeeklyActiveWhatsAppToPhonebook() {
        viewModelScope.launch {
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
                        firestoreRepo.setContactSyncedToDevice(currentUserId, c.id, true)
                    }
                }
                _statusMessage.value = "تمت مزامنة $syncedCount من نشطي واتساب آخر 7 أيام إلى الهاتف"
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
                val summary1 = withContext(Dispatchers.IO) {
                    appContext.assets.open("contacts_seed.csv").use { stream ->
                        CsvParser.parseStream(stream)
                    }
                }
                val summary2 = withContext(Dispatchers.IO) {
                    appContext.assets.open("eaeat_2023_leader.csv").use { stream ->
                        CsvParser.parseStream(stream, defaultCategory = "الجامعة > EAEAT")
                    }
                }
                val allContacts = (summary1.contacts + summary2.contacts)
                val allCatPaths = summary1.categoryPaths + summary2.categoryPaths
                val result = persistImportedDataset(allContacts, allCatPaths, uploadToCloud)
                prefs.setBundledImported(true)
                _statusMessage.value = "تم استيراد ${result.importedCount} جهة اتصال (${result.categoriesCreated} فئة، ${result.geoMatchedCount} موقع جغرافي)"
            } catch (e: Exception) {
                _statusMessage.value = "فشل الاستيراد: ${e.localizedMessage}"
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
                val isVcf = fileName?.endsWith(".vcf", ignoreCase = true) == true
                val parsed = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        if (isVcf) {
                            val text = stream.bufferedReader().readText()
                            val vcards = VCardUtils.parseVCards(text)
                            val cats = vcards.flatMap { it.categoryPaths }.toSet()
                            CsvParser.ParsedDataset(vcards, 0, cats)
                        } else {
                            CsvParser.parseStream(stream)
                        }
                    } ?: CsvParser.ParsedDataset(emptyList(), 0, emptySet())
                }
                val result = persistImportedDataset(parsed.contacts, parsed.categoryPaths, uploadToCloud)
                _statusMessage.value = "تم استيراد ${result.importedCount} جهة اتصال بنجاح"
            } catch (e: Exception) {
                _statusMessage.value = "تعذر قراءة الملف: ${e.localizedMessage}"
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
        _importProgressText.value = "فهرسة ${contacts.size} جهة اتصال محلياً..."
        val entities = contacts.map { ContactEntity.fromDomain(it) }
        val catNodes = buildCategoryNodes(catPaths)

        withContext(Dispatchers.IO) {
            entities.chunked(500).forEach { chunk ->
                contactDao.upsertAll(chunk)
            }
            categoryDao.upsertAll(
                catNodes.map { CategoryEntity(it.id, it.name, it.parentPath, it.fullPath, it.depth.toInt(), it.iconKey) }
            )
        }

        if (uploadToCloud && contacts.isNotEmpty()) {
            _importProgressText.value = "رفع البيانات إلى السحابة (0 / ${contacts.size})..."
            firestoreRepo.upsertCategories(currentUserId, catNodes)
            firestoreRepo.batchUpsertContacts(currentUserId, contacts) { done, total ->
                _importProgressText.value = "جاري المزامنة السحابية ($done / $total)..."
            }
            prefs.setLastSyncMs(System.currentTimeMillis())
        }

        loadCategoryLevel(_currentCategoryPath.value)
        refreshCallLogs()

        return CsvImportSummary(
            importedCount = contacts.size,
            skippedCount = 0,
            categoriesCreated = catNodes.size,
            geoMatchedCount = contacts.count { it.latitude != null },
        )
    }

    fun pullAllFromCloud() {
        viewModelScope.launch {
            _isBusy.value = true
            _importProgressText.value = "جاري جلب جهات الاتصال من Firestore..."
            try {
                var cursor: DocumentSnapshot? = null
                var total = 0
                val discoveredCats = linkedSetOf<String>()
                while (true) {
                    val res = firestoreRepo.pullContactsPage(currentUserId, limit = 500, lastSnapshot = cursor)
                    val (page, nextCursor) = res.getOrThrow()
                    if (page.isEmpty()) break
                    withContext(Dispatchers.IO) {
                        contactDao.upsertAll(page.map { ContactEntity.fromDomain(it) })
                    }
                    page.forEach { discoveredCats.addAll(it.categoryPaths) }
                    total += page.size
                    _importProgressText.value = "تم سحب $total جهة اتصال..."
                    if (page.size < 500 || nextCursor == null) break
                    cursor = nextCursor
                }
                if (discoveredCats.isNotEmpty()) {
                    val nodes = buildCategoryNodes(discoveredCats)
                    withContext(Dispatchers.IO) {
                        categoryDao.upsertAll(nodes.map {
                            CategoryEntity(it.id, it.name, it.parentPath, it.fullPath, it.depth.toInt(), it.iconKey)
                        })
                    }
                }
                prefs.setLastSyncMs(System.currentTimeMillis())
                loadCategoryLevel(_currentCategoryPath.value)
                _statusMessage.value = "تمت المزامنة: $total جهة اتصال"
            } catch (e: Exception) {
                _statusMessage.value = "تعذرت المزامنة: ${e.localizedMessage}"
            } finally {
                _isBusy.value = false
                _importProgressText.value = null
            }
        }
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
            firestoreRepo.upsertPaymentTemplate(currentUserId, tpl)
            _statusMessage.value = "تمت إضافة خدمة الدفع: ${tpl.name}"
        }
    }

    fun deletePaymentTemplate(templateId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { paymentDao.deleteById(templateId) }
            firestoreRepo.deletePaymentTemplate(currentUserId, templateId)
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
                        "التطبيق محدث على آخر إصدار (${info.tagName})"
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
