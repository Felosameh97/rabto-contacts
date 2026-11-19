package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.domain.model.CallTranscriptRecord
import com.example.domain.model.ContactRecord
import com.example.domain.model.GitHubReleaseInfo
import com.example.domain.model.LocalMediaCacheStats
import com.example.domain.model.PaymentTemplate
import com.example.domain.model.SyncEngineState
import com.example.domain.model.WhatsAppLog
import com.example.telecom.CallRecordingItem
import com.example.telecom.RabtaInCallService
import com.example.telecom.RecordingSourceMode
import com.example.ui.components.AddPaymentTemplateDialog
import com.example.ui.components.SetPinDialog
import com.example.ui.screens.settings.AccountUpdatesSettingsPage
import com.example.ui.screens.settings.AppearanceSettingsPage
import com.example.ui.screens.settings.CallsRecordingSettingsPage
import com.example.ui.screens.settings.DataImportSettingsPage
import com.example.ui.screens.settings.SecurityPrivacySettingsPage
import com.example.ui.screens.settings.SettingsCategoryTile
import com.example.ui.screens.settings.SettingsSection
import com.example.ui.screens.settings.SettingsSubPageContainer
import com.example.ui.screens.settings.SyncCacheSettingsPage
import com.example.ui.screens.settings.TemplatesSettingsPage
import com.example.ui.theme.CalmPalette
import com.example.ui.theme.ThemeMode
import com.example.util.SimPreference
import com.example.util.VaultActionHelper
import com.google.android.gms.auth.api.identity.Identity

@Composable
fun ToolsSyncScreen(
    userEmail: String?,
    totalContacts: Int,
    recentLogs: List<WhatsAppLog>,
    syncedDeviceContacts: List<ContactRecord>,
    paymentTemplates: List<PaymentTemplate>,
    githubRepoSlug: String,
    releaseInfo: GitHubReleaseInfo?,
    themeMode: ThemeMode,
    calmPalette: CalmPalette,
    autoRecordCalls: Boolean,
    callRecordings: List<CallRecordingItem>,
    isBusy: Boolean,
    selectedSection: SettingsSection? = null,
    onSelectSection: (SettingsSection?) -> Unit = {},
    showContactPhotos: Boolean = true,
    callPhotoBackground: Boolean = true,
    strictPhonebookIsolation: Boolean = true,
    autoTranscribeCalls: Boolean = false,
    simPreference: SimPreference = SimPreference.ASK_OR_BOTH,
    callTranscripts: List<CallTranscriptRecord> = emptyList(),
    recordingSourceMode: RecordingSourceMode = RecordingSourceMode.SMART_AUTO,
    vibrateOnAnswer: Boolean = true,
    autoSpeakerForRecord: Boolean = false,
    whatsappTemplates: List<String> = emptyList(),
    appLockEnabled: Boolean = false,
    hasAppLockPin: Boolean = false,
    biometricLockEnabled: Boolean = true,
    hideLockscreenCallerDetails: Boolean = false,
    duplicateClustersCount: Int = 0,
    syncEngineState: SyncEngineState = SyncEngineState(),
    cacheStats: LocalMediaCacheStats = LocalMediaCacheStats(),
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetCalmPalette: (CalmPalette) -> Unit,
    onSetAutoRecordCalls: (Boolean) -> Unit,
    onSetShowContactPhotos: (Boolean) -> Unit = {},
    onSetCallPhotoBackground: (Boolean) -> Unit = {},
    onSetStrictPhonebookIsolation: (Boolean) -> Unit = {},
    onSetAutoTranscribeCalls: (Boolean) -> Unit = {},
    onSetSimPreference: (SimPreference) -> Unit = {},
    onSetRecordingSourceMode: (RecordingSourceMode) -> Unit = {},
    onSetVibrateOnAnswer: (Boolean) -> Unit = {},
    onSetAutoSpeakerForRecord: (Boolean) -> Unit = {},
    onSetAppLockPin: (String) -> Unit = {},
    onToggleAppLockEnabled: (Boolean) -> Unit = {},
    onSetBiometricLockEnabled: (Boolean) -> Unit = {},
    onSetHideLockscreenCallerDetails: (Boolean) -> Unit = {},
    onLockAppNow: () -> Unit = {},
    onAddWhatsAppTemplate: (String) -> Unit = {},
    onRemoveWhatsAppTemplate: (String) -> Unit = {},
    onRefreshRecordings: () -> Unit = {},
    onDeleteRecording: (CallRecordingItem) -> Unit,
    onTranscribeRecording: (CallRecordingItem) -> Unit = {},
    onOpenLiveTranscriber: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onPurgeOrphanedCache: () -> Unit = {},
    onImportBundledCsv: () -> Unit,
    onImportDevicePhonebook: () -> Unit,
    onScanGoogleDrive: (String) -> Unit,
    onReorganizeCategories: () -> Unit,
    onImportExternalFile: (android.net.Uri, String?) -> Unit,
    onPullFromCloud: () -> Unit,
    onShareExport: (Boolean) -> Unit,
    onExportJson: () -> Unit = {},
    onOpenDuplicateMerge: () -> Unit = {},
    onOpenQrScanner: () -> Unit,
    onSyncWeeklyWhatsApp: () -> Unit,
    onRemoveFromPhonebook: (ContactRecord) -> Unit,
    onPurgeAllFromPhonebook: () -> Unit = {},
    onAddPaymentTemplate: (String, String) -> Unit,
    onDeletePaymentTemplate: (String) -> Unit,
    onCheckGithubUpdates: (String) -> Unit,
    onPermissionsChanged: () -> Unit,
    onSignOut: () -> Unit,
    onRunAiBilingualEnrich: () -> Unit = {},
    onSyncGoogleContactsPhotos: (String) -> Unit = {},
    onSyncDevicePhotosOnly: () -> Unit = {},
    selectedAiModelId: String = com.example.ai.RabtaAiAssistant.DEFAULT_MODEL_ID,
    aiDiagnosticStatus: String? = null,
    onSelectAiModelId: (String) -> Unit = {},
    onTestGeminiConnection: () -> Unit = {},
) {
    val context = LocalContext.current
    var showAddPayment by remember { mutableStateOf(false) }
    var showAddWaTemplate by remember { mutableStateOf(false) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var driveErrorMsg by remember { mutableStateOf<String?>(null) }
    var isDefaultDialer by remember { mutableStateOf(RabtaInCallService.isDefaultDialer(context)) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportExternalFile(uri, uri.lastPathSegment)
        }
    }

    val readPhonebookPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onPermissionsChanged()
            onImportDevicePhonebook()
        }
    }

    val contactsSyncPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onPermissionsChanged()
        onSyncWeeklyWhatsApp()
    }

    val purgeContactsPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onPermissionsChanged()
        onPurgeAllFromPhonebook()
    }

    val dialerRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isDefaultDialer = RabtaInCallService.isDefaultDialer(context)
    }

    val recordAudioPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) onSetAutoRecordCalls(true)
    }

    val driveAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            runCatching {
                val authRes = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(res.data)
                val token = authRes.accessToken
                if (!token.isNullOrBlank()) {
                    driveErrorMsg = null
                    onScanGoogleDrive(token)
                } else {
                    driveErrorMsg = "لم يرجع Google Drive رمز وصول (Access Token)"
                }
            }.onFailure {
                driveErrorMsg = "تعذر تفويض Google Drive: ${it.localizedMessage}"
            }
        }
    }

    val googlePhotosAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            runCatching {
                val authRes = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(res.data)
                val token = authRes.accessToken
                if (!token.isNullOrBlank()) {
                    driveErrorMsg = null
                    onSyncGoogleContactsPhotos(token)
                } else {
                    driveErrorMsg = "لم يرجع Google Contacts رمز وصول للصور"
                }
            }.onFailure {
                driveErrorMsg = "تعذر تفويض Google Contacts: ${it.localizedMessage}"
            }
        }
    }

    AnimatedContent(
        targetState = selectedSection,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
            }
        },
        label = "settings_subpage_transition",
    ) { section ->
        when (section) {
            null -> {
                SettingsHubOverview(
                    userEmail = userEmail,
                    totalContacts = totalContacts,
                    themeMode = themeMode,
                    calmPalette = calmPalette,
                    appLockEnabled = appLockEnabled,
                    strictPhonebookIsolation = strictPhonebookIsolation,
                    simPreference = simPreference,
                    callRecordingsCount = callRecordings.size,
                    syncEngineState = syncEngineState,
                    cacheStats = cacheStats,
                    duplicateClustersCount = duplicateClustersCount,
                    paymentTemplatesCount = paymentTemplates.size,
                    whatsappTemplatesCount = whatsappTemplates.size,
                    onSelectSection = onSelectSection,
                )
            }
            SettingsSection.APPEARANCE -> {
                AppearanceSettingsPage(
                    themeMode = themeMode,
                    calmPalette = calmPalette,
                    showContactPhotos = showContactPhotos,
                    callPhotoBackground = callPhotoBackground,
                    onSetThemeMode = onSetThemeMode,
                    onSetCalmPalette = onSetCalmPalette,
                    onSetShowContactPhotos = onSetShowContactPhotos,
                    onSetCallPhotoBackground = onSetCallPhotoBackground,
                )
            }
            SettingsSection.SECURITY -> {
                SecurityPrivacySettingsPage(
                    appLockEnabled = appLockEnabled,
                    hasAppLockPin = hasAppLockPin,
                    biometricLockEnabled = biometricLockEnabled,
                    hideLockscreenCallerDetails = hideLockscreenCallerDetails,
                    strictPhonebookIsolation = strictPhonebookIsolation,
                    syncedDeviceContacts = syncedDeviceContacts,
                    isBusy = isBusy,
                    onOpenSetPinDialog = { showSetPinDialog = true },
                    onToggleAppLockEnabled = onToggleAppLockEnabled,
                    onSetBiometricLockEnabled = onSetBiometricLockEnabled,
                    onSetHideLockscreenCallerDetails = onSetHideLockscreenCallerDetails,
                    onLockAppNow = onLockAppNow,
                    onSetStrictPhonebookIsolation = onSetStrictPhonebookIsolation,
                    onRemoveFromPhonebook = onRemoveFromPhonebook,
                    contactsSyncPermLauncher = contactsSyncPermLauncher,
                    purgeContactsPermLauncher = purgeContactsPermLauncher,
                )
            }
            SettingsSection.CALLS_RECORDING -> {
                CallsRecordingSettingsPage(
                    isDefaultDialer = isDefaultDialer,
                    hasMicPermission = hasMicPermission,
                    simPreference = simPreference,
                    autoRecordCalls = autoRecordCalls,
                    autoTranscribeCalls = autoTranscribeCalls,
                    vibrateOnAnswer = vibrateOnAnswer,
                    autoSpeakerForRecord = autoSpeakerForRecord,
                    recordingSourceMode = recordingSourceMode,
                    callRecordings = callRecordings,
                    callTranscripts = callTranscripts,
                    onSetSimPreference = onSetSimPreference,
                    onSetAutoRecordCalls = onSetAutoRecordCalls,
                    onSetAutoTranscribeCalls = onSetAutoTranscribeCalls,
                    onSetVibrateOnAnswer = onSetVibrateOnAnswer,
                    onSetAutoSpeakerForRecord = onSetAutoSpeakerForRecord,
                    onSetRecordingSourceMode = onSetRecordingSourceMode,
                    onOpenLiveTranscriber = onOpenLiveTranscriber,
                    onRefreshRecordings = onRefreshRecordings,
                    onTranscribeRecording = onTranscribeRecording,
                    onDeleteRecording = onDeleteRecording,
                    dialerRoleLauncher = dialerRoleLauncher,
                    recordAudioPermLauncher = recordAudioPermLauncher,
                )
            }
            SettingsSection.SYNC_CACHE -> {
                SyncCacheSettingsPage(
                    syncEngineState = syncEngineState,
                    cacheStats = cacheStats,
                    selectedAiModelId = selectedAiModelId,
                    aiDiagnosticStatus = aiDiagnosticStatus,
                    onSelectAiModelId = onSelectAiModelId,
                    onTestGeminiConnection = onTestGeminiConnection,
                    onSyncNow = onSyncNow,
                    onPurgeOrphanedCache = onPurgeOrphanedCache,
                )
            }
            SettingsSection.DATA_IMPORT -> {
                DataImportSettingsPage(
                    totalContacts = totalContacts,
                    duplicateClustersCount = duplicateClustersCount,
                    isBusy = isBusy,
                    driveErrorMsg = driveErrorMsg,
                    onSetDriveErrorMsg = { driveErrorMsg = it },
                    onImportBundledCsv = onImportBundledCsv,
                    onImportDevicePhonebook = onImportDevicePhonebook,
                    onScanGoogleDrive = onScanGoogleDrive,
                    onSyncGoogleContactsPhotos = onSyncGoogleContactsPhotos,
                    onSyncDevicePhotosOnly = onSyncDevicePhotosOnly,
                    onPullFromCloud = onPullFromCloud,
                    onExportJson = onExportJson,
                    onShareExport = onShareExport,
                    onOpenDuplicateMerge = onOpenDuplicateMerge,
                    onReorganizeCategories = onReorganizeCategories,
                    onRunAiBilingualEnrich = onRunAiBilingualEnrich,
                    onOpenQrScanner = onOpenQrScanner,
                    filePicker = filePicker,
                    readPhonebookPermLauncher = readPhonebookPermLauncher,
                    driveAuthLauncher = driveAuthLauncher,
                    googlePhotosAuthLauncher = googlePhotosAuthLauncher,
                )
            }
            SettingsSection.TEMPLATES -> {
                TemplatesSettingsPage(
                    paymentTemplates = paymentTemplates,
                    whatsappTemplates = whatsappTemplates,
                    recentLogs = recentLogs,
                    onOpenAddPayment = { showAddPayment = true },
                    onDeletePaymentTemplate = onDeletePaymentTemplate,
                    onOpenAddWaTemplate = { showAddWaTemplate = true },
                    onRemoveWhatsAppTemplate = onRemoveWhatsAppTemplate,
                )
            }
            SettingsSection.ACCOUNT_UPDATES -> {
                AccountUpdatesSettingsPage(
                    userEmail = userEmail,
                    githubRepoSlug = githubRepoSlug,
                    releaseInfo = releaseInfo,
                    isBusy = isBusy,
                    onCheckGithubUpdates = onCheckGithubUpdates,
                    onSignOut = onSignOut,
                )
            }
        }
    }

    if (showAddPayment) {
        AddPaymentTemplateDialog(
            onDismiss = { showAddPayment = false },
            onSave = { name, tpl ->
                onAddPaymentTemplate(name, tpl)
                showAddPayment = false
            },
        )
    }

    if (showAddWaTemplate) {
        var newTpl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddWaTemplate = false },
            title = { Text("إضافة رسالة واتساب جاهزة") },
            text = {
                OutlinedTextField(
                    value = newTpl,
                    onValueChange = { newTpl = it },
                    label = { Text("نص الرسالة") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newTpl.isNotBlank()) {
                        onAddWhatsAppTemplate(newTpl)
                        showAddWaTemplate = false
                    }
                }) { Text("إضافة") }
            },
            dismissButton = {
                TextButton(onClick = { showAddWaTemplate = false }) { Text("إلغاء") }
            },
        )
    }

    if (showSetPinDialog) {
        SetPinDialog(
            hasExistingPin = hasAppLockPin,
            onDismiss = { showSetPinDialog = false },
            onConfirmPin = { newPin ->
                onSetAppLockPin(newPin)
                showSetPinDialog = false
            },
        )
    }
}

@Composable
private fun SettingsHubOverview(
    userEmail: String?,
    totalContacts: Int,
    themeMode: ThemeMode,
    calmPalette: CalmPalette,
    appLockEnabled: Boolean,
    strictPhonebookIsolation: Boolean,
    simPreference: SimPreference,
    callRecordingsCount: Int,
    syncEngineState: SyncEngineState,
    cacheStats: LocalMediaCacheStats,
    duplicateClustersCount: Int,
    paymentTemplatesCount: Int,
    whatsappTemplatesCount: Int,
    onSelectSection: (SettingsSection) -> Unit,
) {
    SettingsSubPageContainer {
        // Profile & Quick Status Banner
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth().testTag("settings_hub_banner"),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = (userEmail?.firstOrNull() ?: 'F').uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = userEmail ?: "حساب Felo Contacts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "$totalContacts جهة اتصال معزولة • ${VaultActionHelper.formatBytesReadable(cacheStats.totalBytes)} كاش محلي",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        item {
            SettingsCategoryTile(
                section = SettingsSection.APPEARANCE,
                icon = Icons.Rounded.Palette,
                subtitle = "${themeMode.labelAr} • ${calmPalette.labelAr} • صور المتصلين",
                iconTint = MaterialTheme.colorScheme.primary,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                onClick = { onSelectSection(SettingsSection.APPEARANCE) },
            )
        }

        item {
            SettingsCategoryTile(
                section = SettingsSection.SECURITY,
                icon = Icons.Rounded.Security,
                subtitle = buildString {
                    append(if (appLockEnabled) "قفل PIN مفعل" else "بدون قفل")
                    append(" • ")
                    append(if (strictPhonebookIsolation) "عزل كامل عن سجل الهاتف" else "مزامنة انتقائية")
                },
                badgeText = if (appLockEnabled) "محمي" else null,
                iconTint = MaterialTheme.colorScheme.secondary,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { onSelectSection(SettingsSection.SECURITY) },
            )
        }

        item {
            SettingsCategoryTile(
                section = SettingsSection.CALLS_RECORDING,
                icon = Icons.Rounded.Call,
                subtitle = "${simPreference.labelAr} • $callRecordingsCount تسجيل • اختبار الصوت",
                badgeText = if (callRecordingsCount > 0) "$callRecordingsCount" else null,
                iconTint = MaterialTheme.colorScheme.tertiary,
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                onClick = { onSelectSection(SettingsSection.CALLS_RECORDING) },
            )
        }

        item {
            SettingsCategoryTile(
                section = SettingsSection.SYNC_CACHE,
                icon = Icons.Rounded.CloudSync,
                subtitle = "${if (syncEngineState.isOnline) "متصل بالسحابة" else "أوفلاين"} • معلق: ${syncEngineState.pendingCount} • App Check",
                badgeText = if (syncEngineState.pendingCount > 0) "${syncEngineState.pendingCount}" else null,
                iconTint = MaterialTheme.colorScheme.primary,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                onClick = { onSelectSection(SettingsSection.SYNC_CACHE) },
            )
        }

        item {
            SettingsCategoryTile(
                section = SettingsSection.DATA_IMPORT,
                icon = Icons.Rounded.FolderShared,
                subtitle = "CSV / VCF / JSON • Google Drive • دمج التكرارات و QR",
                badgeText = if (duplicateClustersCount > 0) "$duplicateClustersCount مكرر" else null,
                iconTint = MaterialTheme.colorScheme.secondary,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { onSelectSection(SettingsSection.DATA_IMPORT) },
            )
        }

        item {
            SettingsCategoryTile(
                section = SettingsSection.TEMPLATES,
                icon = Icons.Rounded.Payments,
                subtitle = "$paymentTemplatesCount قالب دفع • $whatsappTemplatesCount رسالة واتساب سريعة",
                iconTint = MaterialTheme.colorScheme.tertiary,
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                onClick = { onSelectSection(SettingsSection.TEMPLATES) },
            )
        }

        item {
            SettingsCategoryTile(
                section = SettingsSection.ACCOUNT_UPDATES,
                icon = Icons.Rounded.AccountCircle,
                subtitle = "الإصدار ${BuildConfig.VERSION_NAME} • تحديثات GitHub وتسجيل الخروج",
                iconTint = MaterialTheme.colorScheme.primary,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                onClick = { onSelectSection(SettingsSection.ACCOUNT_UPDATES) },
            )
        }
    }
}
