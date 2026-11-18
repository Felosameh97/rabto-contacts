package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFolderUpload
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.R
import com.example.auth.getAppCheckModeLabelAr
import com.example.auth.getManualDebugToken
import com.example.auth.getOrCreateLocalDebugUuid
import com.example.auth.saveManualDebugToken
import com.example.data.remote.GoogleDriveScanner
import com.example.domain.model.CallTranscriptRecord
import com.example.domain.model.ContactRecord
import com.example.domain.model.GitHubReleaseInfo
import com.example.domain.model.PaymentTemplate
import com.example.domain.model.WhatsAppLog
import com.example.telecom.CallRecorderManager
import com.example.telecom.CallRecordingItem
import com.example.telecom.RabtaInCallService
import com.example.telecom.RecordingSourceMode
import com.example.ui.components.AddPaymentTemplateDialog
import com.example.ui.components.SetPinDialog
import com.example.ui.theme.CalmPalette
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.WhatsAppGreen
import com.example.util.SimPreference
import com.example.util.SystemIntents
import com.google.android.gms.auth.api.identity.Identity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var repoInput by remember(githubRepoSlug) { mutableStateOf(githubRepoSlug) }
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
    var playingPath by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isTestingMic by remember { mutableStateOf(false) }
    var micTestMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
        }
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Section 0: Calm Palette & Light/Dark Theme
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("المظهر والألوان الهادئة", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                label = { Text(mode.labelAr) },
                                modifier = Modifier.testTag("theme_mode_${mode.name}"),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CalmPalette.entries.forEach { pal ->
                            FilterChip(
                                selected = calmPalette == pal,
                                onClick = { onSetCalmPalette(pal) },
                                label = { Text(pal.labelAr) },
                                modifier = Modifier.testTag("theme_palette_${pal.name}"),
                            )
                        }
                    }
                }
            }
        }

        // Section 0.75: App Lock & Lockscreen Privacy
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("قفل التطبيق وخصوصية شاشة القفل", style = MaterialTheme.typography.titleMedium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("قفل التطبيق برمز PIN / البصمة", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "يحمي قائمة الأرقام والتسجيلات والتفريغ عند فتح التطبيق.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = appLockEnabled,
                            onCheckedChange = { wantEnable ->
                                if (wantEnable && !hasAppLockPin) {
                                    showSetPinDialog = true
                                } else {
                                    onToggleAppLockEnabled(wantEnable)
                                }
                            },
                            modifier = Modifier.testTag("settings_app_lock_switch"),
                        )
                    }

                    if (appLockEnabled || hasAppLockPin) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                Column {
                                    Text("الفتح بالبصمة / قفل الهاتف", style = MaterialTheme.typography.titleSmall)
                                    Text("يتيح تجاوز PIN بالبصمة الموثقة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = biometricLockEnabled,
                                onCheckedChange = onSetBiometricLockEnabled,
                                modifier = Modifier.testTag("settings_biometric_lock_switch"),
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { showSetPinDialog = true },
                                modifier = Modifier.weight(1f).testTag("settings_change_pin_btn"),
                            ) {
                                Text(if (hasAppLockPin) "تغيير رمز PIN" else "تعيين PIN")
                            }
                            if (appLockEnabled && hasAppLockPin) {
                                FilledTonalButton(
                                    onClick = onLockAppNow,
                                    modifier = Modifier.weight(1f).testTag("settings_lock_now_btn"),
                                ) {
                                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("قفل الآن")
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                            Column {
                                Text("إخفاء هوية المتصل على شاشة القفل", style = MaterialTheme.typography.titleSmall)
                                Text("يعرض \"مكالمة خاصة\" على الشاشة المغلقة دون كشف الاسم أو الرقم.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = hideLockscreenCallerDetails,
                            onCheckedChange = onSetHideLockscreenCallerDetails,
                            modifier = Modifier.testTag("settings_hide_lockscreen_switch"),
                        )
                    }
                }
            }
        }

        // Section 0.5: Photos, Privacy Isolation, Auto-Transcribe & SIM Preference
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("الصور، الشرائح، والخصوصية الكاملة", style = MaterialTheme.typography.titleMedium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("إظهار صور جهات الاتصال", style = MaterialTheme.typography.titleSmall)
                            Text("عرض الصورة الدائرية لكل شخص في الكروت والبحث.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = showContactPhotos,
                            onCheckedChange = onSetShowContactPhotos,
                            modifier = Modifier.testTag("settings_show_photos_switch"),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("صورة المتصل كخلفية لشاشة المكالمة", style = MaterialTheme.typography.titleSmall)
                            Text("عرض صورة الشخص كاملة خلفياً عند الاتصال أو الاستقبال.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = callPhotoBackground,
                            onCheckedChange = onSetCallPhotoBackground,
                            modifier = Modifier.testTag("settings_call_bg_photo_switch"),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تفريغ المكالمات تلقائياً بـ Gemini (اختياري)", style = MaterialTheme.typography.titleSmall)
                            Text("يستهلك رصيد Gemini بعد كل مكالمة. مفرغ الصوت المباشر يعمل مجاناً وبدون حد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoTranscribeCalls,
                            onCheckedChange = onSetAutoTranscribeCalls,
                            modifier = Modifier.testTag("settings_auto_transcribe_switch"),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("فصل كامل عن سجل الموبايل وواتساب", style = MaterialTheme.typography.titleSmall)
                            Text("يمنع نزول أي رقم إلى Contacts الهاتف حتى لا يظهر بأسمائه على واتساب.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = strictPhonebookIsolation,
                            onCheckedChange = onSetStrictPhonebookIsolation,
                            modifier = Modifier.testTag("settings_strict_isolation_switch"),
                        )
                    }

                    if (syncedDeviceContacts.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                purgeContactsPermLauncher.launch(
                                    arrayOf(Manifest.permission.READ_CONTACTS)
                                )
                            },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth().testTag("tools_purge_phonebook_btn"),
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("فصل وإزالة (${syncedDeviceContacts.size}) رقم متزامن من سجل الموبايل")
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.SimCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("اختيار الشريحة (SIM)", style = MaterialTheme.typography.titleSmall)
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SimPreference.entries.forEach { pref ->
                            FilterChip(
                                selected = simPreference == pref,
                                onClick = { onSetSimPreference(pref) },
                                label = { Text(pref.labelAr) },
                                modifier = Modifier.testTag("settings_sim_${pref.name}"),
                            )
                        }
                    }
                }
            }
        }

        // Section 1: Phone Dialer Replacement & Call Recording
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("تطبيق الاتصال الافتراضي وتسجيل المكالمات", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        if (isDefaultDialer) {
                            "Felo Contacts هو تطبيق الهاتف الافتراضي حالياً — يعرض هوية المتصل وصورته مع التسجيل والتفريغ التلقائي."
                        } else {
                            "اجعل Felo Contacts تطبيق الهاتف الافتراضي للاستغناء عن تطبيق الاتصال الأساسي وعرض شاشة المكالمات مع التسجيل."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!isDefaultDialer) {
                        Button(
                            onClick = {
                                val intent = RabtaInCallService.createRequestDefaultDialerIntent(context)
                                if (intent != null) {
                                    dialerRoleLauncher.launch(intent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("tools_set_default_dialer_btn"),
                        ) {
                            Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("تعيين Felo Contacts كتطبيق الاتصال الافتراضي")
                        }
                    }
                    if (!hasMicPermission) {
                        FilledTonalButton(
                            onClick = { recordAudioPermLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            modifier = Modifier.fillMaxWidth().testTag("tools_grant_mic_btn"),
                        ) {
                            Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("منح صلاحية الميكروفون للتسجيل والتفريغ الحي")
                        }
                    }
                    FilledTonalButton(
                        onClick = onOpenLiveTranscriber,
                        modifier = Modifier.fillMaxWidth().testTag("tools_open_live_transcriber_btn"),
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("فتح مفرغ الصوت المباشر (مجاناً بدون Gemini)")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تسجيل المكالمات تلقائياً (.m4a)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "يعمل عبر خدمة الميكروفون الأمامية مع تعدد مصادر الصوت؛ تشغيل السبيكر يوضح صوت الطرف الآخر على بعض الهواتف.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = autoRecordCalls,
                            onCheckedChange = { enable ->
                                if (!enable) {
                                    onSetAutoRecordCalls(false)
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                    if (granted) onSetAutoRecordCalls(true) else recordAudioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.testTag("tools_auto_record_switch"),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("اهتزاز خفيف عند رد الطرف الآخر", style = MaterialTheme.typography.titleSmall)
                            Text("يعطيك إشارة لمسية فور بدء المكالمة دون تثبيت الهاتف على الأذن.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = vibrateOnAnswer,
                            onCheckedChange = onSetVibrateOnAnswer,
                            modifier = Modifier.testTag("tools_vibrate_answer_switch"),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تفعيل السبيكر تلقائياً لوضوح التسجيل", style = MaterialTheme.typography.titleSmall)
                            Text("يضمن التقاط صوت الطرف الآخر بوضوح على كل هواتف أندرويد الحديثة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoSpeakerForRecord,
                            onCheckedChange = onSetAutoSpeakerForRecord,
                            modifier = Modifier.testTag("tools_auto_speaker_switch"),
                        )
                    }

                    Text("مسار التقاط الصوت:", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RecordingSourceMode.entries.forEach { mode ->
                            FilterChip(
                                selected = recordingSourceMode == mode,
                                onClick = { onSetRecordingSourceMode(mode) },
                                label = { Text(mode.labelAr) },
                            )
                        }
                    }

                    // 3-Second Mic & Playback Self-Test
                    OutlinedButton(
                        enabled = !isTestingMic,
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                recordAudioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                return@OutlinedButton
                            }
                            scope.launch {
                                isTestingMic = true
                                micTestMessage = "جاري تسجيل عينة 3 ثواني — تحدث الآن..."
                                val startRes = CallRecorderManager.startRecording(
                                    context = context,
                                    labelOrNumber = "MicTest",
                                    sourceMode = recordingSourceMode,
                                    allowSilentSwap = false,
                                )
                                if (startRes.isFailure) {
                                    isTestingMic = false
                                    micTestMessage = startRes.exceptionOrNull()?.localizedMessage ?: "فشل اختبار الميكروفون"
                                    return@launch
                                }
                                delay(3200L)
                                val recorded = CallRecorderManager.stopRecording()
                                isTestingMic = false
                                onRefreshRecordings()
                                if (recorded != null) {
                                    micTestMessage = "جاري تشغيل العينة المسجلة (${recorded.length() / 1024} KB)..."
                                    runCatching {
                                        mediaPlayer?.stop()
                                        mediaPlayer?.release()
                                    }
                                    mediaPlayer = CallRecorderManager.playAudioFile(
                                        context = context,
                                        file = recorded,
                                        onStart = { playingPath = recorded.absolutePath },
                                        onComplete = {
                                            playingPath = null
                                            micTestMessage = "✓ الصوت والتسجيل يعملان بنجاح!"
                                        },
                                        onError = { err ->
                                            playingPath = null
                                            micTestMessage = err
                                        },
                                    )
                                } else {
                                    micTestMessage = "لم يتم حفظ عينة صالحة — غيّر مسار الصوت وجرب مجدداً"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("tools_mic_test_btn"),
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isTestingMic) "جاري اختبار الميكروفون (3 ثواني)..." else "اختبار التسجيل والصوت (3 ثواني)")
                    }
                    if (isTestingMic) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    micTestMessage?.let { msg ->
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        if (callRecordings.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("التسجيلات المحفوظة (${callRecordings.size})", style = MaterialTheme.typography.titleSmall)
                }
            }
            items(callRecordings.take(12), key = { it.file.absolutePath }) { rec ->
                val isPlaying = playingPath == rec.file.absolutePath
                val matchedTranscript = callTranscripts.firstOrNull { it.audioFilePath == rec.file.absolutePath }
                val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            IconButton(
                                onClick = {
                                    if (isPlaying) {
                                        runCatching {
                                            mediaPlayer?.stop()
                                            mediaPlayer?.release()
                                        }
                                        mediaPlayer = null
                                        playingPath = null
                                    } else {
                                                runCatching {
                                                    mediaPlayer?.stop()
                                                    mediaPlayer?.release()
                                                }
                                                mediaPlayer = CallRecorderManager.playAudioFile(
                                                    context = context,
                                                    file = rec.file,
                                                    onStart = { playingPath = rec.file.absolutePath },
                                                    onComplete = { playingPath = null },
                                                    onError = { err ->
                                                        playingPath = null
                                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                                    },
                                                )
                                            }
                                        }
                                    ) {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "إيقاف" else "تشغيل",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rec.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${fmt.format(Date(rec.timestampMs))} • ${rec.sizeKb} KB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onTranscribeRecording(rec) }) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = "تفريغ بـ Gemini", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { SystemIntents.shareBinaryFile(context, rec.file, "audio/mp4") }) {
                                Icon(Icons.Rounded.Share, contentDescription = "مشاركة", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDeleteRecording(rec) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "حذف", modifier = Modifier.size(18.dp))
                            }
                        }
                        if (matchedTranscript != null && matchedTranscript.transcript.isNotBlank()) {
                            Text(
                                text = matchedTranscript.transcript,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Import (Bundled CSVs, Device Phonebook, Google Drive, Local File, Cloud Pull)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("استيراد المصادر والسحابة ($totalContacts رقم)", style = MaterialTheme.typography.titleMedium)

                    Button(
                        onClick = onImportBundledCsv,
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("tools_import_bundled_btn"),
                    ) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("استيراد ملفاتك المرفقة (contacts + 2023 leader)")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                                if (granted) onImportDevicePhonebook() else readPhonebookPermLauncher.launch(Manifest.permission.READ_CONTACTS)
                            },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).testTag("tools_import_phonebook_btn"),
                        ) {
                            Icon(Icons.Rounded.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("جهات اتصال الهاتف")
                        }
                        FilledTonalButton(
                            onClick = {
                                val act = activity ?: return@FilledTonalButton
                                scope.launch {
                                    GoogleDriveScanner.requestDriveAuthorization(act)
                                        .onSuccess { authResult ->
                                            if (authResult.hasResolution()) {
                                                val pending = authResult.pendingIntent
                                                if (pending != null) {
                                                    driveAuthLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                                                }
                                            } else {
                                                val token = authResult.accessToken
                                                if (!token.isNullOrBlank()) {
                                                    driveErrorMsg = null
                                                    onScanGoogleDrive(token)
                                                } else {
                                                    driveErrorMsg = "تعذر الحصول على رمز دخول Google Drive"
                                                }
                                            }
                                        }
                                        .onFailure { err ->
                                            driveErrorMsg = "فشل ربط Google Drive: ${err.localizedMessage}"
                                        }
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).testTag("tools_scan_drive_btn"),
                        ) {
                            Icon(Icons.Rounded.DriveFolderUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("بحث Google Drive")
                        }
                    }

                    if (!driveErrorMsg.isNullOrBlank()) {
                        Text(
                            text = driveErrorMsg.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = { filePicker.launch(arrayOf("application/json", "text/*", "text/csv", "text/x-vcard", "*/*")) },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).testTag("tools_import_file_btn"),
                        ) {
                            Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ملف JSON/CSV/VCF")
                        }
                        FilledTonalButton(
                            onClick = onPullFromCloud,
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).testTag("tools_pull_cloud_btn"),
                        ) {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("سحب السحابة")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onExportJson,
                            modifier = Modifier.weight(1f).testTag("tools_export_json_btn"),
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("تصدير JSON آمن")
                        }
                        OutlinedButton(
                            onClick = { onShareExport(true) },
                            modifier = Modifier.weight(1f).testTag("tools_export_vcf_btn"),
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("تصدير VCF")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onOpenDuplicateMerge,
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).testTag("tools_duplicate_merge_btn"),
                        ) {
                            Icon(Icons.Rounded.MergeType, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (duplicateClustersCount > 0) "دمج التكرارات ($duplicateClustersCount)" else "فحص ودمج التكرارات")
                        }
                        OutlinedButton(
                            onClick = onReorganizeCategories,
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).testTag("tools_reorganize_btn"),
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("تنظيم + Tags")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onRunAiBilingualEnrich,
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).testTag("tools_ai_bilingual_btn"),
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("تاجز وترجمة Gemini")
                        }
                        OutlinedButton(
                            onClick = onOpenQrScanner,
                            modifier = Modifier.weight(1f).testTag("tools_scan_qr_btn"),
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("مسح QR")
                        }
                    }
                }
            }
        }

        // Section 3: 7-Day WhatsApp Logger & Selective Phonebook Sync (Respects strict isolation)
        if (!strictPhonebookIsolation) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = WhatsAppGreen)
                            Text("سجل واتساب الأسبوعي والمزامنة الانتقائية", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "كل مرة تفتح فيها واتساب من التطبيق يتم تسجيل اليوم والساعة، ويمكنك حفظ هؤلاء الأشخاص فقط في سجل هاتفك ليتعرف عليهم واتساب.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                contactsSyncPermLauncher.launch(
                                    arrayOf(Manifest.permission.READ_CONTACTS)
                                )
                            },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth().testTag("tools_sync_weekly_wa_btn"),
                        ) {
                            Icon(Icons.Rounded.Sync, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("مزامنة نشطي آخر 7 أيام للهاتف")
                        }
                    }
                }
            }
        }

        if (recentLogs.isNotEmpty()) {
            item {
                Text("آخر محادثات واتساب المسجلة (${recentLogs.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            items(recentLogs.take(15), key = { it.id }) { log ->
                val fmt = SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault())
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.Chat, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(log.contactName, style = MaterialTheme.typography.titleSmall)
                            Text("${log.phone} • ${fmt.format(Date(log.loggedAtEpochMs))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (log.syncedToPhone) {
                            Icon(Icons.Rounded.PhoneAndroid, contentDescription = "محفوظ بالهاتف", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        if (syncedDeviceContacts.isNotEmpty()) {
            item {
                Text("المحفوظون حالياً بهاتفك (${syncedDeviceContacts.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
            }
            items(syncedDeviceContacts.take(20), key = { "dev_${it.id}" }) { c ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(c.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(c.primaryPhone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onRemoveFromPhonebook(c) }) {
                            Text("إزالة من الهاتف")
                        }
                    }
                }
            }
        }

        // Section 4: Payment Templates
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("قوالب الدفع والتحويل", style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(onClick = { showAddPayment = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = "إضافة خدمة دفع")
                        }
                    }
                    paymentTemplates.forEach { tpl ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tpl.name, style = MaterialTheme.typography.titleSmall)
                                Text(tpl.urlTemplate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!tpl.isDefault) {
                                IconButton(onClick = { onDeletePaymentTemplate(tpl.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "حذف")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 4B: Quick WhatsApp Message Templates
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = WhatsAppGreen)
                            Text("قوالب رسائل واتساب السريعة", style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(onClick = { showAddWaTemplate = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = "إضافة رسالة واتساب جاهزة")
                        }
                    }
                    whatsappTemplates.forEach { tpl ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = tpl,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onRemoveWhatsAppTemplate(tpl) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "حذف القالب", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // Section 5: GitHub Releases Auto-Update Checker
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("التحديث التلقائي من GitHub", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "الإصدار الحالي: ${BuildConfig.VERSION_NAME} • اربط مستودع GitHub (أو الرابط كاملاً) لفحص التحديثات تلقائياً عند فتح التطبيق.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = repoInput,
                        onValueChange = { repoInput = it },
                        label = { Text("مستودع GitHub (مثال: username/rabta-contacts)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("github_repo_input"),
                    )
                    Button(
                        onClick = { onCheckGithubUpdates(repoInput) },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("check_github_update_btn"),
                    ) {
                        Text(stringResource(R.string.check_updates))
                    }
                    if (releaseInfo != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${releaseInfo.releaseName} (${releaseInfo.tagName})", style = MaterialTheme.typography.titleSmall)
                                if (releaseInfo.body.isNotBlank()) {
                                    Text(releaseInfo.body.take(240), style = MaterialTheme.typography.bodySmall)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        SystemIntents.openWebsiteOrPayment(
                                            context,
                                            releaseInfo.apkDownloadUrl ?: releaseInfo.htmlUrl,
                                        )
                                    },
                                ) {
                                    Text(if (releaseInfo.apkDownloadUrl != null) "تحميل RabtaContacts.apk" else "فتح صفحة المستودع")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 5B: Firebase App Check & Gemini Diagnostics
        item {
            var manualTokenInput by remember { mutableStateOf(getManualDebugToken(context)) }
            var showTokenConfig by remember { mutableStateOf(manualTokenInput.isNotBlank()) }
            val localDebugUuid = remember { getOrCreateLocalDebugUuid(context) }

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("حماية Gemini و App Check", style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(onClick = { showTokenConfig = !showTokenConfig }) {
                            Text(if (showTokenConfig) "إخفاء" else "خيارات متقدمة")
                        }
                    }
                    Text(
                        "الوضع النشط: ${getAppCheckModeLabelAr()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (showTokenConfig) {
                        Text(
                            "في الأجهزة الحقيقية يعمل reCAPTCHA تلقائياً. إذا أردت ربط الجهاز يدوياً بـ Debug Token من Firebase Console يمكنك نسخه أو لصقه هنا ثم إعادة فتح التطبيق:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (localDebugUuid.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("AppCheckDebugUUID", localDebugUuid))
                                    Toast.makeText(context, "تم نسخ Debug UUID المحلي", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth().testTag("copy_local_debug_uuid_btn"),
                            ) {
                                Text("نسخ Debug UUID الخاص بهذا الهاتف")
                            }
                        }
                        OutlinedTextField(
                            value = manualTokenInput,
                            onValueChange = { manualTokenInput = it },
                            label = { Text("Debug Token مخصص (اختياري)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("manual_appcheck_token_input"),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    saveManualDebugToken(context, manualTokenInput)
                                    Toast.makeText(
                                        context,
                                        "تم الحفظ. أغلق التطبيق وأعد فتحه لتطبيق الوضع الجديد.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                },
                                modifier = Modifier.weight(1f).testTag("save_manual_appcheck_btn"),
                            ) {
                                Text("حفظ التوكن")
                            }
                            OutlinedButton(
                                onClick = {
                                    manualTokenInput = ""
                                    saveManualDebugToken(context, "")
                                    Toast.makeText(
                                        context,
                                        "تم الترجيع إلى reCAPTCHA التلقائي. أعد فتح التطبيق.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                },
                                modifier = Modifier.weight(1f).testTag("clear_manual_appcheck_btn"),
                            ) {
                                Text("استخدام التلقائي")
                            }
                        }
                    }
                }
            }
        }

        // Section 6: Account & Sign Out
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("الحساب السحابي المتصل", style = MaterialTheme.typography.titleSmall)
                        Text(userEmail ?: "Google Account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier.testTag("sign_out_btn"),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("خروج")
                    }
                }
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
