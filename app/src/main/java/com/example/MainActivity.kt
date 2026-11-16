package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.authStateFlow
import com.example.auth.configureAppCheck
import com.example.data.device.CallLogReader
import com.example.data.device.DeviceContactsManager
import com.example.data.local.RabtaDatabase
import com.example.data.local.SyncPrefsDataStore
import com.example.data.remote.FirestoreContactsRepository
import com.example.data.remote.GitHubUpdateService
import com.example.domain.model.ContactRecord
import com.example.ui.components.AiAssistantSheet
import com.example.ui.components.ContactDetailBottomSheet
import com.example.ui.components.ContactEditorDialog
import com.example.ui.components.DialpadSheet
import com.example.ui.components.QrScannerDialog
import com.example.ui.components.WhatsAppCaptureDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MapScreen
import com.example.ui.screens.ToolsSyncScreen
import com.example.ui.theme.CalmPalette
import com.example.ui.theme.RabtaContactsTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.RabtaViewModel
import com.example.util.SystemIntents
import com.example.widget.RabtaWidget
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.serialization.Serializable

@Serializable object RouteHome
@Serializable object RouteCategories
@Serializable object RouteMap
@Serializable object RouteTools

class MainActivity : ComponentActivity() {
    private var incomingShareText by mutableStateOf<String?>(null)
    private var incomingVCardText by mutableStateOf<String?>(null)
    private var requestedDialDigits by mutableStateOf<String?>(null)
    private var requestedOpenDialpad by mutableStateOf(false)
    private var requestedOpenAi by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // App Check MUST run before setContent, ViewModels, or any Firebase call
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        parseIncomingIntent(intent)

        setContent {
            val prefs = remember { SyncPrefsDataStore(applicationContext) }
            val themeMode by prefs.themeModeFlow.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val calmPalette by prefs.paletteFlow.collectAsStateWithLifecycle(initialValue = CalmPalette.SLATE)

            RabtaContactsTheme(themeMode = themeMode, palette = calmPalette) {
                RabtaRootApp(
                    incomingShareText = incomingShareText,
                    incomingVCardText = incomingVCardText,
                    requestedDialDigits = requestedDialDigits,
                    requestedOpenDialpad = requestedOpenDialpad,
                    requestedOpenAi = requestedOpenAi,
                    onConsumeIncoming = {
                        incomingShareText = null
                        incomingVCardText = null
                        requestedDialDigits = null
                        requestedOpenDialpad = false
                        requestedOpenAi = false
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseIncomingIntent(intent)
    }

    private fun parseIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            RabtaWidget.ACTION_OPEN_DIALPAD -> {
                requestedOpenDialpad = true
            }
            RabtaWidget.ACTION_OPEN_AI -> {
                requestedOpenAi = true
            }
            Intent.ACTION_DIAL -> {
                val raw = intent.data?.schemeSpecificPart.orEmpty()
                requestedDialDigits = raw
                requestedOpenDialpad = true
            }
            Intent.ACTION_SEND -> {
                val type = intent.type.orEmpty()
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (type.contains("vcard", ignoreCase = true) || text?.contains("BEGIN:VCARD") == true) {
                    incomingVCardText = text
                } else if (!text.isNullOrBlank()) {
                    incomingShareText = text
                }
            }
            Intent.ACTION_VIEW -> {
                val data = intent.dataString
                if (!data.isNullOrBlank() && (data.startsWith("tel:") || data.contains("wa.me"))) {
                    if (data.startsWith("tel:")) {
                        requestedDialDigits = data.removePrefix("tel:")
                        requestedOpenDialpad = true
                    } else {
                        incomingShareText = data
                    }
                }
            }
        }
    }
}

@Composable
private fun RabtaRootApp(
    incomingShareText: String?,
    incomingVCardText: String?,
    requestedDialDigits: String?,
    requestedOpenDialpad: Boolean,
    requestedOpenAi: Boolean,
    onConsumeIncoming: () -> Unit,
    auth: FirebaseAuth = Firebase.auth,
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(auth = auth)
    } else {
        AuthenticatedMainScreen(
            user = user,
            incomingShareText = incomingShareText,
            incomingVCardText = incomingVCardText,
            requestedDialDigits = requestedDialDigits,
            requestedOpenDialpad = requestedOpenDialpad,
            requestedOpenAi = requestedOpenAi,
            onConsumeIncoming = onConsumeIncoming,
            onSignOut = { auth.signOut() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedMainScreen(
    user: FirebaseUser,
    incomingShareText: String?,
    incomingVCardText: String?,
    requestedDialDigits: String?,
    requestedOpenDialpad: Boolean,
    requestedOpenAi: Boolean,
    onConsumeIncoming: () -> Unit,
    onSignOut: () -> Unit,
) {
    val viewModel: RabtaViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val firestore = FirebaseFirestore.getInstance(databaseId)
                val roomDb = RabtaDatabase.getInstance(app)
                RabtaViewModel(
                    appContext = app,
                    currentUserId = user.uid,
                    currentUserName = user.displayName ?: "User",
                    currentUserEmail = user.email,
                    db = roomDb,
                    firestoreRepo = FirestoreContactsRepository(firestore),
                    callLogReader = CallLogReader(app, roomDb.contactDao()),
                    deviceContactsManager = DeviceContactsManager(app),
                    prefs = SyncPrefsDataStore(app),
                    gitHubService = GitHubUpdateService(),
                )
            }
        },
    )

    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }

    val totalCount by viewModel.totalContactsCount.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val dialpadMatches by viewModel.dialpadMatches.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteContacts.collectAsStateWithLifecycle()
    val frequentCalls by viewModel.frequentCalls.collectAsStateWithLifecycle()
    val recentCalls by viewModel.recentCalls.collectAsStateWithLifecycle()
    val hasCallLogPerm by viewModel.hasCallLogPermission.collectAsStateWithLifecycle()
    val weeklyLogs by viewModel.weeklyActiveWhatsAppLogs.collectAsStateWithLifecycle()
    val recentLogs by viewModel.recentWhatsAppLogs.collectAsStateWithLifecycle()
    val bundledImported by viewModel.bundledImported.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgressText.collectAsStateWithLifecycle()
    val cloudSyncStatus by viewModel.cloudSyncStatus.collectAsStateWithLifecycle()
    val currentCatPath by viewModel.currentCategoryPath.collectAsStateWithLifecycle()
    val childCategories by viewModel.childCategories.collectAsStateWithLifecycle()
    val categoryContacts by viewModel.categoryContacts.collectAsStateWithLifecycle()
    val selectedContact by viewModel.selectedContact.collectAsStateWithLifecycle()
    val selectedBulk by viewModel.selectedForBulk.collectAsStateWithLifecycle()
    val geoContacts by viewModel.geoContacts.collectAsStateWithLifecycle()
    val syncedContacts by viewModel.syncedDeviceContacts.collectAsStateWithLifecycle()
    val paymentTemplates by viewModel.paymentTemplates.collectAsStateWithLifecycle()
    val githubRepoSlug by viewModel.githubRepoSlug.collectAsStateWithLifecycle()
    val releaseInfo by viewModel.releaseInfo.collectAsStateWithLifecycle()
    val autoUpdateRelease by viewModel.autoUpdateRelease.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val calmPalette by viewModel.calmPalette.collectAsStateWithLifecycle()
    val autoRecordCalls by viewModel.autoRecordCalls.collectAsStateWithLifecycle()
    val callRecordings by viewModel.callRecordings.collectAsStateWithLifecycle()
    val aiUiState by viewModel.aiUiState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val statusMsg by viewModel.statusMessage.collectAsStateWithLifecycle()
    val pendingWaCapture by viewModel.pendingWhatsAppCapture.collectAsStateWithLifecycle()

    var editingContact by remember { mutableStateOf<ContactRecord?>(null) }
    var editorCategorySeed by remember { mutableStateOf("") }
    var showEditor by remember { mutableStateOf(false) }
    var showWaCapture by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showDialpad by remember { mutableStateOf(false) }
    var dialpadSeed by remember { mutableStateOf("") }
    var showAiSheet by remember { mutableStateOf(false) }

    LaunchedEffect(incomingShareText, incomingVCardText, requestedOpenDialpad, requestedOpenAi, requestedDialDigits) {
        if (incomingShareText != null || incomingVCardText != null) {
            viewModel.handleIncomingShareOrDeepLink(incomingShareText, incomingVCardText)
            onConsumeIncoming()
        } else if (requestedOpenDialpad) {
            dialpadSeed = requestedDialDigits.orEmpty()
            showDialpad = true
            onConsumeIncoming()
        } else if (requestedOpenAi) {
            showAiSheet = true
            onConsumeIncoming()
        }
    }

    LaunchedEffect(statusMsg) {
        statusMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    val navItems = listOf(
        Triple(RouteHome, R.string.nav_home, Icons.Rounded.Home),
        Triple(RouteCategories, R.string.nav_categories, Icons.Rounded.FolderCopy),
        Triple(RouteMap, R.string.nav_map, Icons.Rounded.Map),
        Triple(RouteTools, R.string.nav_tools, Icons.Rounded.Sync),
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge) },
                    actions = {
                        IconButton(
                            onClick = {
                                dialpadSeed = ""
                                showDialpad = true
                            },
                            modifier = Modifier.testTag("topbar_dialpad_btn"),
                        ) {
                            Icon(Icons.Rounded.Dialpad, contentDescription = stringResource(R.string.dialpad_title))
                        }
                        IconButton(
                            onClick = { showAiSheet = true },
                            modifier = Modifier.testTag("topbar_ai_btn"),
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = stringResource(R.string.ai_assistant_title))
                        }
                    },
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        navItems.forEach { (route, labelRes, icon) ->
                            val selected = currentDest?.hasRoute(route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(route) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(RouteHome) { saveState = true }
                                    }
                                },
                                icon = { Icon(icon, contentDescription = stringResource(labelRes)) },
                                label = { Text(stringResource(labelRes)) },
                                modifier = Modifier.testTag("nav_tab_${route::class.simpleName}"),
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        editingContact = null
                        editorCategorySeed = currentCatPath
                        showEditor = true
                    },
                    modifier = Modifier.testTag("fab_add_contact"),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_contact))
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail) {
                    NavigationRail {
                        navItems.forEach { (route, labelRes, icon) ->
                            val selected = currentDest?.hasRoute(route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(route) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(RouteHome) { saveState = true }
                                    }
                                },
                                icon = { Icon(icon, contentDescription = stringResource(labelRes)) },
                                label = { Text(stringResource(labelRes)) },
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = RouteHome,
                    ) {
                        composable<RouteHome> {
                            HomeScreen(
                                totalCount = totalCount,
                                searchQuery = searchQuery,
                                searchResults = searchResults,
                                favorites = favorites,
                                frequentCalls = frequentCalls,
                                recentCalls = recentCalls,
                                hasCallLogPermission = hasCallLogPerm,
                                weeklyLogs = weeklyLogs,
                                bundledImported = bundledImported,
                                importProgressText = importProgress,
                                cloudSyncStatus = cloudSyncStatus,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onOpenContact = viewModel::openContact,
                                onWhatsApp = { c, p -> viewModel.launchWhatsAppAndLog(c, p) },
                                onAddContact = {
                                    editingContact = null
                                    editorCategorySeed = ""
                                    showEditor = true
                                },
                                onCaptureWhatsApp = { showWaCapture = true },
                                onOpenCategories = {
                                    navController.navigate(RouteCategories) { launchSingleTop = true }
                                },
                                onOpenQrScanner = { showQrScanner = true },
                                onOpenDialpad = {
                                    dialpadSeed = ""
                                    showDialpad = true
                                },
                                onOpenAiAssistant = { showAiSheet = true },
                                onImportBundledCsv = { viewModel.importBundledDatasets(uploadToCloud = true) },
                                onSyncWeeklyWhatsApp = viewModel::syncWeeklyActiveWhatsAppToPhonebook,
                                onPermissionsChanged = viewModel::refreshPermissionsState,
                            )
                        }
                        composable<RouteCategories> {
                            CategoriesScreen(
                                currentPath = currentCatPath,
                                childCategories = childCategories,
                                contactsInCategory = categoryContacts,
                                selectedBulkIds = selectedBulk,
                                isBusy = isBusy,
                                onSelectPath = viewModel::loadCategoryLevel,
                                onNavigateUp = viewModel::navigateCategoryUp,
                                onCreateCategory = viewModel::createCategoryPath,
                                onReorganizeCategories = viewModel::reorganizeAllCategories,
                                onAiAutoCategorize = {
                                    showAiSheet = true
                                    viewModel.runAiAutoCategorizeBatch()
                                },
                                onOpenContact = viewModel::openContact,
                                onWhatsApp = { c, p -> viewModel.launchWhatsAppAndLog(c, p) },
                                onAddContactInCategory = { catPath ->
                                    editingContact = null
                                    editorCategorySeed = catPath
                                    showEditor = true
                                },
                                onToggleBulk = viewModel::toggleBulkSelection,
                                onClearBulk = viewModel::clearBulkSelection,
                                onShareExport = viewModel::shareBulkOrCategory,
                            )
                        }
                        composable<RouteMap> {
                            MapScreen(
                                geoContacts = geoContacts,
                                onOpenContact = viewModel::openContact,
                            )
                        }
                        composable<RouteTools> {
                            ToolsSyncScreen(
                                userEmail = user.email,
                                totalContacts = totalCount,
                                recentLogs = recentLogs,
                                syncedDeviceContacts = syncedContacts,
                                paymentTemplates = paymentTemplates,
                                githubRepoSlug = githubRepoSlug,
                                releaseInfo = releaseInfo,
                                themeMode = themeMode,
                                calmPalette = calmPalette,
                                autoRecordCalls = autoRecordCalls,
                                callRecordings = callRecordings,
                                isBusy = isBusy,
                                onSetThemeMode = viewModel::setThemeMode,
                                onSetCalmPalette = viewModel::setCalmPalette,
                                onSetAutoRecordCalls = viewModel::setAutoRecordCalls,
                                onDeleteRecording = viewModel::deleteRecording,
                                onImportBundledCsv = { viewModel.importBundledDatasets(uploadToCloud = true) },
                                onImportDevicePhonebook = { viewModel.importDevicePhonebookContacts(uploadToCloud = true) },
                                onScanGoogleDrive = viewModel::scanGoogleDriveWithToken,
                                onReorganizeCategories = viewModel::reorganizeAllCategories,
                                onImportExternalFile = { uri, name -> viewModel.importFromUri(uri, name, true) },
                                onPullFromCloud = viewModel::pullAllFromCloud,
                                onShareExport = viewModel::shareBulkOrCategory,
                                onOpenQrScanner = { showQrScanner = true },
                                onSyncWeeklyWhatsApp = viewModel::syncWeeklyActiveWhatsAppToPhonebook,
                                onRemoveFromPhonebook = viewModel::toggleDeviceContactSync,
                                onAddPaymentTemplate = viewModel::savePaymentTemplate,
                                onDeletePaymentTemplate = viewModel::deletePaymentTemplate,
                                onCheckGithubUpdates = viewModel::saveGithubRepoAndCheckUpdates,
                                onPermissionsChanged = viewModel::refreshPermissionsState,
                                onSignOut = onSignOut,
                                onRunAiBilingualEnrich = {
                                    showAiSheet = true
                                    viewModel.runAiBilingualEnrichBatch()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    selectedContact?.let { contact ->
        ContactDetailBottomSheet(
            contact = contact,
            paymentTemplates = paymentTemplates,
            onDismiss = viewModel::closeContact,
            onToggleFavorite = viewModel::toggleFavorite,
            onEdit = { toEdit ->
                editingContact = toEdit
                editorCategorySeed = toEdit.categoryPaths.firstOrNull().orEmpty()
                showEditor = true
            },
            onDelete = viewModel::deleteContact,
            onWhatsApp = { c, p -> viewModel.launchWhatsAppAndLog(c, p) },
            onToggleDeviceSync = viewModel::toggleDeviceContactSync,
            onAddPaymentTemplate = viewModel::savePaymentTemplate,
            onSearchByTag = { tag ->
                viewModel.closeContact()
                viewModel.updateSearchQuery(tag)
                navController.navigate(RouteHome) { launchSingleTop = true }
            },
        )
    }

    if (showDialpad) {
        DialpadSheet(
            initialDigits = dialpadSeed,
            matches = dialpadMatches,
            onQueryChange = viewModel::searchForDialpad,
            onDismiss = { showDialpad = false },
            onOpenContact = {
                showDialpad = false
                viewModel.openContact(it)
            },
            onWhatsApp = { c, p -> viewModel.launchWhatsAppAndLog(c, p) },
            onSaveNewNumber = { num ->
                showDialpad = false
                editingContact = ContactRecord(primaryPhone = num, phones = listOf(num))
                editorCategorySeed = ""
                showEditor = true
            },
        )
    }

    if (showAiSheet) {
        AiAssistantSheet(
            uiState = aiUiState,
            onRunCommand = { prompt ->
                viewModel.runAiCommand(
                    userPrompt = prompt,
                    onNavigateToCategory = {
                        showAiSheet = false
                        navController.navigate(RouteCategories) { launchSingleTop = true }
                    },
                    onNavigateToMap = {
                        showAiSheet = false
                        navController.navigate(RouteMap) { launchSingleTop = true }
                    },
                )
            },
            onRunBatchCategorize = viewModel::runAiAutoCategorizeBatch,
            onRunBilingualEnrich = viewModel::runAiBilingualEnrichBatch,
            onOpenContact = {
                showAiSheet = false
                viewModel.openContact(it)
            },
            onWhatsAppWithMsg = { c, p, msg -> viewModel.launchWhatsAppAndLog(c, p, msg) },
            onDismiss = { showAiSheet = false },
        )
    }

    if (showEditor) {
        ContactEditorDialog(
            initialContact = editingContact,
            initialCategoryPath = editorCategorySeed,
            onDismiss = { showEditor = false },
            onSave = { saved ->
                viewModel.saveContact(saved)
                showEditor = false
            },
        )
    }

    if (showWaCapture || pendingWaCapture != null) {
        WhatsAppCaptureDialog(
            initialParsed = pendingWaCapture,
            onDismiss = {
                showWaCapture = false
                viewModel.clearPendingWhatsAppCapture()
            },
            onSave = { captured ->
                viewModel.saveContact(captured)
                showWaCapture = false
                viewModel.clearPendingWhatsAppCapture()
            },
        )
    }

    if (showQrScanner) {
        QrScannerDialog(
            onDismiss = { showQrScanner = false },
            onContactScanned = { scanned ->
                showQrScanner = false
                editingContact = scanned
                editorCategorySeed = scanned.categoryPaths.firstOrNull().orEmpty()
                showEditor = true
            },
        )
    }

    autoUpdateRelease?.let { update ->
        AlertDialog(
            onDismissRequest = viewModel::dismissAutoUpdatePrompt,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = {
                Text("تحديث جديد متاح (${update.tagName})")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = update.releaseName,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (update.body.isNotBlank()) {
                        Text(
                            text = update.body.take(280),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = "تم رصد إصدار جديد على GitHub. اضغط تحديث الآن لتحميل النسخة الجديدة وتثبيتها.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        SystemIntents.openWebsiteOrPayment(
                            context,
                            update.apkDownloadUrl ?: update.htmlUrl,
                        )
                        viewModel.dismissAutoUpdatePrompt()
                    },
                    modifier = Modifier.testTag("auto_update_confirm_btn"),
                ) {
                    Text("تحديث الآن")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissAutoUpdatePrompt,
                    modifier = Modifier.testTag("auto_update_later_btn"),
                ) {
                    Text("لاحقاً")
                }
            },
        )
    }
}
