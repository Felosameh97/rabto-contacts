package com.example

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.domain.model.CallTranscriptRecord
import com.example.domain.model.ContactRecord
import com.example.domain.model.SyncEngineState
import com.example.domain.model.SyncStatusBadge
import com.example.telecom.ActiveCallUiState
import com.example.telecom.InCallActivity
import com.example.telecom.RabtaInCallService
import com.example.ui.components.AiAssistantSheet
import com.example.ui.components.AppLockScreen
import com.example.ui.components.ContactDetailBottomSheet
import com.example.ui.components.ContactEditorDialog
import com.example.ui.components.DuplicateMergeDialog
import com.example.ui.components.LiveTranscriberSheet
import com.example.ui.components.QrScannerDialog
import com.example.ui.components.TranscriptQuickDialog
import com.example.ui.components.WhatsAppCaptureDialog
import com.example.ui.screens.AiVaultScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.KeypadScreen
import com.example.ui.screens.RecentsScreen
import com.example.ui.screens.ToolsSyncScreen
import com.example.ui.theme.CalmPalette
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.RabtaContactsTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.viewmodel.RabtaViewModel
import com.example.util.AppLockSecurity
import com.example.util.FollowUpReminderScheduler
import com.example.util.PhoneUtils
import com.example.util.SystemIntents
import com.example.widget.RabtaWidget
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable object RouteKeypad
@Serializable object RouteRecents
@Serializable object RouteContacts
@Serializable object RouteAiVault
@Serializable object RouteCategories
@Serializable object RouteSettings

class MainActivity : ComponentActivity() {
    private var incomingShareText by mutableStateOf<String?>(null)
    private var incomingVCardText by mutableStateOf<String?>(null)
    private var requestedDialDigits by mutableStateOf<String?>(null)
    private var requestedOpenDialpad by mutableStateOf(false)
    private var requestedOpenAi by mutableStateOf(false)
    private var requestedFollowUpContactId by mutableStateOf<String?>(null)
    private var requestedFollowUpPhone by mutableStateOf<String?>(null)

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
                    requestedFollowUpContactId = requestedFollowUpContactId,
                    requestedFollowUpPhone = requestedFollowUpPhone,
                    onConsumeIncoming = {
                        incomingShareText = null
                        incomingVCardText = null
                        requestedDialDigits = null
                        requestedOpenDialpad = false
                        requestedOpenAi = false
                        requestedFollowUpContactId = null
                        requestedFollowUpPhone = null
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("FIREBASE_APPCHECK_DEBUG_TOKEN")) {
            configureAppCheck(this, intent)
        }
        parseIncomingIntent(intent)
    }

    private fun parseIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            FollowUpReminderScheduler.ACTION_FOLLOWUP_CONTACT -> {
                requestedFollowUpContactId = intent.getStringExtra(FollowUpReminderScheduler.EXTRA_CONTACT_ID)
                requestedFollowUpPhone = intent.getStringExtra(FollowUpReminderScheduler.EXTRA_PHONE)
            }
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
    requestedFollowUpContactId: String?,
    requestedFollowUpPhone: String?,
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
            requestedFollowUpContactId = requestedFollowUpContactId,
            requestedFollowUpPhone = requestedFollowUpPhone,
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
    requestedFollowUpContactId: String?,
    requestedFollowUpPhone: String?,
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
                val prefs = SyncPrefsDataStore(app)
                val firestoreRepo = FirestoreContactsRepository(firestore)
                val syncEngine = com.example.data.sync.OfflineFirstSyncEngine(
                    context = app,
                    roomDb = roomDb,
                    firestoreRepo = firestoreRepo,
                    prefs = prefs,
                )
                RabtaViewModel(
                    appContext = app,
                    currentUserId = user.uid,
                    currentUserName = user.displayName ?: "User",
                    currentUserEmail = user.email,
                    db = roomDb,
                    firestoreRepo = firestoreRepo,
                    callLogReader = CallLogReader(app, roomDb.contactDao()),
                    deviceContactsManager = DeviceContactsManager(app),
                    prefs = prefs,
                    gitHubService = GitHubUpdateService(),
                    syncEngine = syncEngine,
                )
            }
        },
    )

    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val totalCount by viewModel.totalContactsCount.collectAsStateWithLifecycle()
    val allContacts by viewModel.allContactsAlphabetical.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val globalSearchResult by viewModel.globalSearchResult.collectAsStateWithLifecycle()
    val dialpadMatches by viewModel.dialpadMatches.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteContacts.collectAsStateWithLifecycle()
    val appCallLogs by viewModel.appCallLogs.collectAsStateWithLifecycle()
    val hasCallLogPerm by viewModel.hasCallLogPermission.collectAsStateWithLifecycle()
    val recentLogs by viewModel.recentWhatsAppLogs.collectAsStateWithLifecycle()
    val bundledImported by viewModel.bundledImported.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgressText.collectAsStateWithLifecycle()
    val syncEngineState by viewModel.syncEngineState.collectAsStateWithLifecycle()
    val cacheStats by viewModel.cacheStats.collectAsStateWithLifecycle()
    val currentCatPath by viewModel.currentCategoryPath.collectAsStateWithLifecycle()
    val childCategories by viewModel.childCategories.collectAsStateWithLifecycle()
    val categoryContacts by viewModel.categoryContacts.collectAsStateWithLifecycle()
    val selectedContact by viewModel.selectedContact.collectAsStateWithLifecycle()
    val selectedBulk by viewModel.selectedForBulk.collectAsStateWithLifecycle()
    val syncedContacts by viewModel.syncedDeviceContacts.collectAsStateWithLifecycle()
    val paymentTemplates by viewModel.paymentTemplates.collectAsStateWithLifecycle()
    val githubRepoSlug by viewModel.githubRepoSlug.collectAsStateWithLifecycle()
    val releaseInfo by viewModel.releaseInfo.collectAsStateWithLifecycle()
    val autoUpdateRelease by viewModel.autoUpdateRelease.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val calmPalette by viewModel.calmPalette.collectAsStateWithLifecycle()
    val autoRecordCalls by viewModel.autoRecordCalls.collectAsStateWithLifecycle()
    val callRecordings by viewModel.callRecordings.collectAsStateWithLifecycle()
    val callTranscripts by viewModel.callTranscripts.collectAsStateWithLifecycle()
    val dialHistory by viewModel.dialHistory.collectAsStateWithLifecycle()
    val recentlyDeletedDial by viewModel.recentlyDeletedDialHistory.collectAsStateWithLifecycle()
    val showContactPhotos by viewModel.showContactPhotos.collectAsStateWithLifecycle()
    val callPhotoBackground by viewModel.callPhotoBackground.collectAsStateWithLifecycle()
    val strictIsolation by viewModel.strictPhonebookIsolation.collectAsStateWithLifecycle()
    val autoTranscribeCalls by viewModel.autoTranscribeCalls.collectAsStateWithLifecycle()
    val simPreference by viewModel.simPreference.collectAsStateWithLifecycle()
    val preferredSimSlot by viewModel.preferredSimSlot.collectAsStateWithLifecycle()
    val recordingSourceMode by viewModel.recordingSourceMode.collectAsStateWithLifecycle()
    val vibrateOnAnswer by viewModel.vibrateOnAnswer.collectAsStateWithLifecycle()
    val autoSpeakerForRecord by viewModel.autoSpeakerForRecord.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val appLockPinHash by viewModel.appLockPinHash.collectAsStateWithLifecycle()
    val biometricLockEnabled by viewModel.biometricLockEnabled.collectAsStateWithLifecycle()
    val hideLockscreenCallerDetails by viewModel.hideLockscreenCallerDetails.collectAsStateWithLifecycle()
    val preCallContext by viewModel.preCallContext.collectAsStateWithLifecycle()
    val duplicateClusters by viewModel.duplicateClusters.collectAsStateWithLifecycle()
    val activeCallUi by RabtaInCallService.uiState.collectAsStateWithLifecycle()
    val lockedCategories by viewModel.lockedCategories.collectAsStateWithLifecycle()
    val sessionUnlockedCategories by viewModel.sessionUnlockedCategories.collectAsStateWithLifecycle()
    val whatsappTemplates by viewModel.whatsappTemplates.collectAsStateWithLifecycle()
    val postCallSummary by viewModel.postCallSummary.collectAsStateWithLifecycle()
    val aiUiState by viewModel.aiUiState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val statusMsg by viewModel.statusMessage.collectAsStateWithLifecycle()
    val pendingWaCapture by viewModel.pendingWhatsAppCapture.collectAsStateWithLifecycle()

    var editingContact by remember { mutableStateOf<ContactRecord?>(null) }
    var editorCategorySeed by remember { mutableStateOf("") }
    var showEditor by remember { mutableStateOf(false) }
    var showWaCapture by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var dialpadSeed by remember { mutableStateOf("") }
    var showAiSheet by remember { mutableStateOf(false) }
    var showLiveTranscriber by remember { mutableStateOf(false) }
    var showDuplicateMerge by remember { mutableStateOf(false) }
    var activeTranscriptModal by remember { mutableStateOf<CallTranscriptRecord?>(null) }
    var isAppUnlocked by remember(user.uid, appLockEnabled, appLockPinHash) {
        mutableStateOf(!appLockEnabled || appLockPinHash.isBlank())
    }

    if (appLockEnabled && appLockPinHash.isNotBlank() && !isAppUnlocked) {
        AppLockScreen(
            pinHash = appLockPinHash,
            biometricEnabled = biometricLockEnabled,
            onUnlocked = { isAppUnlocked = true },
        )
        return
    }

    fun navigateTopTab(route: Any) {
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
            popUpTo(RouteKeypad) { saveState = true }
        }
    }

    LaunchedEffect(
        incomingShareText,
        incomingVCardText,
        requestedOpenDialpad,
        requestedOpenAi,
        requestedDialDigits,
        requestedFollowUpContactId,
        requestedFollowUpPhone,
    ) {
        if (incomingShareText != null || incomingVCardText != null) {
            viewModel.handleIncomingShareOrDeepLink(incomingShareText, incomingVCardText)
            onConsumeIncoming()
        } else if (requestedFollowUpContactId != null || !requestedFollowUpPhone.isNullOrBlank()) {
            viewModel.openContactByPhoneOrId(requestedFollowUpContactId, requestedFollowUpPhone)
            if (!requestedFollowUpPhone.isNullOrBlank()) {
                dialpadSeed = requestedFollowUpPhone
                navigateTopTab(RouteKeypad)
            }
            onConsumeIncoming()
        } else if (requestedOpenDialpad) {
            dialpadSeed = requestedDialDigits.orEmpty()
            navigateTopTab(RouteKeypad)
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
        Triple(RouteKeypad, R.string.nav_keypad, Icons.Rounded.Dialpad),
        Triple(RouteRecents, R.string.nav_recents, Icons.Rounded.History),
        Triple(RouteContacts, R.string.nav_contacts, Icons.Rounded.Contacts),
        Triple(RouteAiVault, R.string.nav_ai_vault, Icons.Rounded.GraphicEq),
    )

    val isOnCategories = currentDest?.hasRoute(RouteCategories::class) == true
    val isOnSettings = currentDest?.hasRoute(RouteSettings::class) == true
    val isOnContacts = currentDest?.hasRoute(RouteContacts::class) == true
    val isPrimaryTab = !isOnCategories && !isOnSettings

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        if (!isPrimaryTab) {
                            IconButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.testTag("topbar_back_btn"),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع")
                            }
                        } else {
                            SyncStatusPill(
                                syncState = syncEngineState,
                                onClick = viewModel::triggerCloudSyncNow,
                            )
                        }
                    },
                    title = {
                        Text(
                            text = when {
                                isOnCategories -> stringResource(R.string.nav_categories)
                                isOnSettings -> "الإعدادات والمزامنة"
                                else -> stringResource(R.string.app_name)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { showAiSheet = true },
                            modifier = Modifier.testTag("topbar_ai_btn"),
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = stringResource(R.string.ai_assistant_title))
                        }
                        if (!isOnSettings) {
                            IconButton(
                                onClick = {
                                    navController.navigate(RouteSettings) { launchSingleTop = true }
                                },
                                modifier = Modifier.testTag("topbar_settings_btn"),
                            ) {
                                Icon(Icons.Rounded.Settings, contentDescription = "الإعدادات")
                            }
                        }
                    },
                )
            },
            bottomBar = {
                if (!useRail && isPrimaryTab) {
                    NavigationBar {
                        navItems.forEach { (route, labelRes, icon) ->
                            val selected = currentDest?.hasRoute(route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopTab(route) },
                                icon = { Icon(icon, contentDescription = stringResource(labelRes)) },
                                label = {
                                    Text(
                                        text = stringResource(labelRes),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier.testTag("nav_tab_${route::class.simpleName}"),
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (isOnContacts || isOnCategories) {
                    FloatingActionButton(
                        onClick = {
                            editingContact = null
                            editorCategorySeed = if (isOnCategories) currentCatPath else ""
                            showEditor = true
                        },
                        modifier = Modifier.testTag("fab_add_contact"),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_contact))
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail && isPrimaryTab) {
                    NavigationRail {
                        navItems.forEach { (route, labelRes, icon) ->
                            val selected = currentDest?.hasRoute(route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopTab(route) },
                                icon = { Icon(icon, contentDescription = stringResource(labelRes)) },
                                label = { Text(stringResource(labelRes), maxLines = 1) },
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (activeCallUi.hasActiveCall && activeCallUi.callState != Call.STATE_DISCONNECTED) {
                            FloatingActiveCallBar(
                                callUi = activeCallUi,
                                hideLockscreenDetails = hideLockscreenCallerDetails,
                                onReturnToCall = {
                                    val callIntent = Intent(context, InCallActivity::class.java).apply {
                                        addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        )
                                    }
                                    runCatching { context.startActivity(callIntent) }
                                },
                                onEndCall = { RabtaInCallService.endCall() },
                            )
                        }

                        NavHost(
                            navController = navController,
                            startDestination = RouteKeypad,
                            modifier = Modifier.weight(1f),
                        ) {
                        composable<RouteKeypad> {
                            KeypadScreen(
                                initialDigits = dialpadSeed,
                                matches = dialpadMatches,
                                favorites = favorites,
                                dialHistory = dialHistory,
                                recentlyDeletedHistory = recentlyDeletedDial,
                                paymentTemplates = paymentTemplates,
                                showContactPhotos = showContactPhotos,
                                preferredSimSlot = preferredSimSlot,
                                preCallContext = preCallContext,
                                onLookupPreCallContext = viewModel::loadPreCallContext,
                                onQueryChange = viewModel::searchForDialpad,
                                onRecordDial = viewModel::recordDialHistoryDraft,
                                onSoftDeleteHistory = viewModel::softDeleteDialHistory,
                                onRestoreHistory = viewModel::restoreDialHistory,
                                onEmptyTrash = viewModel::emptyDialTrash,
                                onOpenContact = viewModel::openContact,
                                onWhatsApp = { c, p -> viewModel.launchWhatsAppAndLog(c, p) },
                                onSaveNewNumber = { num ->
                                    editingContact = ContactRecord(primaryPhone = num, phones = listOf(num))
                                    editorCategorySeed = ""
                                    showEditor = true
                                },
                            )
                        }

                        composable<RouteRecents> {
                            RecentsScreen(
                                callLogs = appCallLogs,
                                callTranscripts = callTranscripts,
                                hasCallLogPermission = hasCallLogPerm,
                                showContactPhotos = showContactPhotos,
                                preferredSimSlot = preferredSimSlot,
                                postCallSummary = postCallSummary,
                                whatsappTemplates = whatsappTemplates,
                                onRecordDial = viewModel::recordDialHistoryDraft,
                                onOpenContact = viewModel::openContact,
                                onSaveNumber = { num ->
                                    editingContact = ContactRecord(primaryPhone = num, phones = listOf(num))
                                    editorCategorySeed = ""
                                    showEditor = true
                                },
                                onOpenTranscript = { tr -> activeTranscriptModal = tr },
                                onDeleteCallLog = { entry ->
                                    viewModel.softDeleteCallLog(entry.id)
                                    scope.launch {
                                        val res = snackbarHostState.showSnackbar(
                                            message = "تم حذف المكالمة من السجل",
                                            actionLabel = "تراجع",
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (res == SnackbarResult.ActionPerformed) {
                                            viewModel.restoreCallLog(entry.id)
                                        }
                                    }
                                },
                                onWhatsApp = { c, p -> viewModel.launchWhatsAppAndLog(c, p) },
                                onWhatsAppQuick = viewModel::launchWhatsAppQuick,
                                onScheduleFollowUpQuick = { name, phone, cid, mins ->
                                    viewModel.scheduleFollowUpReminder(name, phone, cid, mins)
                                },
                                onDismissPostCallSummary = viewModel::dismissPostCallSummary,
                                onOpenLiveTranscriber = { showLiveTranscriber = true },
                                onRefreshCallLogs = viewModel::refreshCallLogs,
                                onPermissionsChanged = viewModel::refreshPermissionsState,
                            )
                        }

                        composable<RouteContacts> {
                            ContactsScreen(
                                allContacts = allContacts,
                                favorites = favorites,
                                totalCount = totalCount,
                                bundledImported = bundledImported,
                                importProgressText = importProgress,
                                searchQuery = searchQuery,
                                globalSearchResult = globalSearchResult,
                                showContactPhotos = showContactPhotos,
                                preferredSimSlot = preferredSimSlot,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onRecordDial = viewModel::recordDialHistoryDraft,
                                onOpenContact = viewModel::openContact,
                                onOpenTranscript = { tr -> activeTranscriptModal = tr },
                                onWhatsApp = { c, p -> viewModel.launchWhatsAppAndLog(c, p) },
                                onOpenCategories = {
                                    navController.navigate(RouteCategories) { launchSingleTop = true }
                                },
                                onOpenQrScanner = { showQrScanner = true },
                                onOpenDuplicates = {
                                    viewModel.scanDuplicateClusters()
                                    showDuplicateMerge = true
                                },
                                onImportBundledCsv = { viewModel.importBundledDatasets(uploadToCloud = true) },
                            )
                        }

                        composable<RouteAiVault> {
                            AiVaultScreen(
                                transcripts = callTranscripts,
                                rawRecordings = callRecordings,
                                cacheStats = cacheStats,
                                paymentTemplates = paymentTemplates,
                                onToggleActionItem = viewModel::toggleTranscriptActionItem,
                                onSaveCallNotes = { viewModel.saveTranscriptNotesToContact(it, silent = false) },
                                onMarkCalendarAdded = viewModel::markCalendarEventAdded,
                                onScheduleActionReminder = viewModel::scheduleActionItemReminder,
                                onAnalyzeTranscript = viewModel::analyzeCallTranscript,
                                onDeleteTranscript = viewModel::softDeleteTranscript,
                                onTranscribeRawRecording = viewModel::transcribeRecordingItem,
                                onDeleteRawRecording = viewModel::deleteRecording,
                                onPurgeOrphanedCache = viewModel::purgeOrphanedMediaCache,
                                onRefreshVault = {
                                    viewModel.refreshRecordings()
                                    viewModel.refreshCacheStats()
                                },
                                onOpenLiveTranscriber = { showLiveTranscriber = true },
                                onOpenAiAssistant = { showAiSheet = true },
                            )
                        }

                        composable<RouteCategories> {
                            BackHandler {
                                if (currentCatPath.isNotBlank()) {
                                    viewModel.navigateCategoryUp()
                                } else {
                                    navController.popBackStack()
                                }
                            }
                            CategoriesScreen(
                                currentPath = currentCatPath,
                                childCategories = childCategories,
                                contactsInCategory = categoryContacts,
                                selectedBulkIds = selectedBulk,
                                isBusy = isBusy,
                                showContactPhotos = showContactPhotos,
                                preferredSimSlot = preferredSimSlot,
                                lockedCategories = lockedCategories,
                                sessionUnlockedCategories = sessionUnlockedCategories,
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
                                onToggleCategoryLock = viewModel::toggleCategoryLock,
                                onUnlockCategorySession = viewModel::markCategoryUnlockedForSession,
                            )
                        }

                        composable<RouteSettings> {
                            BackHandler { navController.popBackStack() }
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
                                showContactPhotos = showContactPhotos,
                                callPhotoBackground = callPhotoBackground,
                                strictPhonebookIsolation = strictIsolation,
                                autoTranscribeCalls = autoTranscribeCalls,
                                simPreference = simPreference,
                                callTranscripts = callTranscripts,
                                recordingSourceMode = recordingSourceMode,
                                vibrateOnAnswer = vibrateOnAnswer,
                                autoSpeakerForRecord = autoSpeakerForRecord,
                                whatsappTemplates = whatsappTemplates,
                                appLockEnabled = appLockEnabled,
                                hasAppLockPin = appLockPinHash.isNotBlank(),
                                biometricLockEnabled = biometricLockEnabled,
                                hideLockscreenCallerDetails = hideLockscreenCallerDetails,
                                duplicateClustersCount = duplicateClusters.size,
                                syncEngineState = syncEngineState,
                                cacheStats = cacheStats,
                                onSetThemeMode = viewModel::setThemeMode,
                                onSetCalmPalette = viewModel::setCalmPalette,
                                onSetAutoRecordCalls = viewModel::setAutoRecordCalls,
                                onSetShowContactPhotos = viewModel::setShowContactPhotos,
                                onSetCallPhotoBackground = viewModel::setCallPhotoBackground,
                                onSetStrictPhonebookIsolation = viewModel::setStrictPhonebookIsolation,
                                onSetAutoTranscribeCalls = viewModel::setAutoTranscribeCalls,
                                onSetSimPreference = viewModel::setSimPreference,
                                onSetRecordingSourceMode = viewModel::setRecordingSourceMode,
                                onSetVibrateOnAnswer = viewModel::setVibrateOnAnswer,
                                onSetAutoSpeakerForRecord = viewModel::setAutoSpeakerForRecord,
                                onSetAppLockPin = viewModel::setAppLockPin,
                                onToggleAppLockEnabled = viewModel::setAppLockEnabled,
                                onSetBiometricLockEnabled = viewModel::setBiometricLockEnabled,
                                onSetHideLockscreenCallerDetails = viewModel::setHideLockscreenCallerDetails,
                                onLockAppNow = { isAppUnlocked = false },
                                onAddWhatsAppTemplate = viewModel::addWhatsAppTemplate,
                                onRemoveWhatsAppTemplate = viewModel::removeWhatsAppTemplate,
                                onRefreshRecordings = viewModel::refreshRecordings,
                                onDeleteRecording = viewModel::deleteRecording,
                                onTranscribeRecording = viewModel::transcribeRecordingItem,
                                onOpenLiveTranscriber = { showLiveTranscriber = true },
                                onSyncNow = viewModel::triggerCloudSyncNow,
                                onPurgeOrphanedCache = viewModel::purgeOrphanedMediaCache,
                                onImportBundledCsv = { viewModel.importBundledDatasets(uploadToCloud = true) },
                                onImportDevicePhonebook = { viewModel.importDevicePhonebookContacts(uploadToCloud = true) },
                                onScanGoogleDrive = viewModel::scanGoogleDriveWithToken,
                                onReorganizeCategories = viewModel::reorganizeAllCategories,
                                onImportExternalFile = { uri, name -> viewModel.importFromUri(uri, name, true) },
                                onPullFromCloud = viewModel::pullAllFromCloud,
                                onShareExport = viewModel::shareBulkOrCategory,
                                onExportJson = viewModel::exportAllContactsAsJson,
                                onOpenDuplicateMerge = {
                                    viewModel.scanDuplicateClusters()
                                    showDuplicateMerge = true
                                },
                                onOpenQrScanner = { showQrScanner = true },
                                onSyncWeeklyWhatsApp = viewModel::syncWeeklyActiveWhatsAppToPhonebook,
                                onRemoveFromPhonebook = viewModel::toggleDeviceContactSync,
                                onPurgeAllFromPhonebook = viewModel::purgeAllSyncedContactsFromPhonebook,
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
    }

    selectedContact?.let { contact ->
        val contactPhoneKeys = remember(contact) {
            (listOf(contact.primaryPhone) + contact.phones)
                .map { PhoneUtils.phoneSearchKey(it) }
                .filter { it.length >= 6 }
                .toSet()
        }
        val contactTranscripts = remember(callTranscripts, contact) {
            callTranscripts.filter { tr ->
                tr.contactId == contact.id ||
                    (PhoneUtils.phoneSearchKey(tr.phone).let { it.length >= 6 && contactPhoneKeys.contains(it) })
            }
        }
        val contactCallLogs = remember(appCallLogs, contact) {
            appCallLogs.filter { entry ->
                entry.matchedContact?.id == contact.id ||
                    (PhoneUtils.phoneSearchKey(entry.number).let { it.length >= 6 && contactPhoneKeys.contains(it) })
            }
        }
        ContactDetailBottomSheet(
            contact = contact,
            paymentTemplates = paymentTemplates,
            contactTranscripts = contactTranscripts,
            contactCallLogs = contactCallLogs,
            preCallContext = preCallContext,
            showPhoto = showContactPhotos,
            preferredSimSlot = preferredSimSlot,
            whatsappTemplates = whatsappTemplates,
            onDismiss = viewModel::closeContact,
            onToggleFavorite = viewModel::toggleFavorite,
            onToggleBlocked = viewModel::toggleBlocked,
            onToggleMuted = viewModel::toggleMuted,
            onEdit = { toEdit ->
                editingContact = toEdit
                editorCategorySeed = toEdit.categoryPaths.firstOrNull().orEmpty()
                showEditor = true
            },
            onDelete = viewModel::deleteContact,
            onWhatsApp = { c, p -> viewModel.launchWhatsAppAndLog(c, p) },
            onWhatsAppWithTemplate = { c, p, tpl -> viewModel.launchWhatsAppAndLog(c, p, tpl) },
            onScheduleFollowUp = { c, phone, mins ->
                viewModel.scheduleFollowUpReminder(c.displayName, phone, c.id, mins)
            },
            onToggleDeviceSync = viewModel::toggleDeviceContactSync,
            onAddPaymentTemplate = viewModel::savePaymentTemplate,
            onUpdatePhoto = viewModel::updateContactPhoto,
            onAnalyzeCallAudio = viewModel::analyzeCallTranscript,
            onSaveCallNotes = { viewModel.saveTranscriptNotesToContact(it, silent = false) },
            onMarkCalendarAdded = viewModel::markCalendarEventAdded,
            onRecordCall = { num -> viewModel.recordDialHistoryDraft(num, true) },
            onSearchByTag = { tag ->
                viewModel.closeContact()
                viewModel.updateSearchQuery(tag)
                navigateTopTab(RouteContacts)
            },
            onOpenTranscript = { tr ->
                viewModel.closeContact()
                activeTranscriptModal = tr
            },
        )
    }

    if (showDuplicateMerge) {
        DuplicateMergeDialog(
            clusters = duplicateClusters,
            onMergeCluster = viewModel::mergeDuplicateCluster,
            onMergeAll = {
                viewModel.mergeAllDuplicateClusters()
                showDuplicateMerge = false
            },
            onDismiss = { showDuplicateMerge = false },
        )
    }

    activeTranscriptModal?.let { initialRecord ->
        val liveRecord = remember(callTranscripts, initialRecord) {
            callTranscripts.firstOrNull { it.id == initialRecord.id } ?: initialRecord
        }
        TranscriptQuickDialog(
            record = liveRecord,
            onDismiss = { activeTranscriptModal = null },
            onToggleActionItem = viewModel::toggleTranscriptActionItem,
            onSaveNotes = { viewModel.saveTranscriptNotesToContact(it, silent = false) },
            onMarkCalendarAdded = viewModel::markCalendarEventAdded,
            onAnalyze = viewModel::analyzeCallTranscript,
            onDelete = viewModel::softDeleteTranscript,
        )
    }

    if (showLiveTranscriber) {
        LiveTranscriberSheet(
            onSaveTranscript = { raw, name, phone ->
                viewModel.saveLiveSpeechTranscript(
                    rawText = raw,
                    contactNameInput = name,
                    phoneInput = phone,
                )
            },
            onDismiss = { showLiveTranscriber = false },
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

@Composable
private fun SyncStatusPill(
    syncState: SyncEngineState,
    onClick: () -> Unit,
) {
    val (label, icon, containerColor, contentColor) = when (syncState.status) {
        SyncStatusBadge.OFFLINE -> Quad(
            if (syncState.pendingCount > 0) "أوفلاين (${syncState.pendingCount})" else "أوفلاين",
            Icons.Rounded.CloudOff,
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SyncStatusBadge.SYNCING -> Quad(
            "مزامنة...",
            Icons.Rounded.CloudSync,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        SyncStatusBadge.SYNCED -> Quad(
            "متزامن",
            Icons.Rounded.CloudDone,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        SyncStatusBadge.ERROR -> Quad(
            "إعادة المزامنة",
            Icons.Rounded.ErrorOutline,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        SyncStatusBadge.IDLE -> Quad(
            if (syncState.pendingCount > 0) "معلق (${syncState.pendingCount})" else "محلي آمن",
            Icons.Rounded.CloudDone,
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Surface(
        shape = RoundedCornerShape(99.dp),
        color = containerColor,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .testTag("topbar_sync_pill"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "حالة المزامنة",
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FloatingActiveCallBar(
    callUi: ActiveCallUiState,
    hideLockscreenDetails: Boolean,
    onReturnToCall: () -> Unit,
    onEndCall: () -> Unit,
) {
    val context = LocalContext.current
    val keyguardLocked = remember(context) {
        (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked == true
    }
    val maskDetails = keyguardLocked && (hideLockscreenDetails || callUi.hideLockscreenDetails)
    val callerLabel = if (maskDetails) {
        "مكالمة جارية (خاص)"
    } else {
        callUi.matchedContact?.displayName?.ifBlank { callUi.phoneNumber } ?: callUi.phoneNumber.ifBlank { "مكالمة نشطة" }
    }
    val stateLabel = when (callUi.callState) {
        Call.STATE_RINGING -> "يرن الآن"
        Call.STATE_DIALING, Call.STATE_CONNECTING -> "جاري الاتصال"
        Call.STATE_HOLDING -> "قيد الانتظار"
        else -> AppLockSecurity.formatDurationClock(callUi.elapsedSec)
    }

    Surface(
        color = WhatsAppGreen.copy(alpha = 0.16f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onReturnToCall)
            .testTag("floating_active_call_bar"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (callUi.isRecording) ErrorRed else WhatsAppGreen),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = "العودة للمكالمة",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = callerLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (callUi.isRecording) "$stateLabel • جاري التسجيل" else "$stateLabel • اضغط للعودة لشاشة المكالمة",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (callUi.isRecording) ErrorRed else WhatsAppGreen,
                    maxLines = 1,
                )
            }
            IconButton(
                onClick = onEndCall,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = ErrorRed,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier
                    .size(40.dp)
                    .testTag("floating_call_end_btn"),
            ) {
                Icon(Icons.Rounded.CallEnd, contentDescription = "إنهاء المكالمة", modifier = Modifier.size(18.dp))
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
