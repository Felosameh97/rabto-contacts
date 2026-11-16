package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.MediaPlayer
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
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFolderUpload
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
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
import com.example.data.remote.GoogleDriveScanner
import com.example.domain.model.ContactRecord
import com.example.domain.model.GitHubReleaseInfo
import com.example.domain.model.PaymentTemplate
import com.example.domain.model.WhatsAppLog
import com.example.telecom.CallRecordingItem
import com.example.telecom.RabtaInCallService
import com.example.ui.components.AddPaymentTemplateDialog
import com.example.ui.theme.CalmPalette
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.WhatsAppGreen
import com.example.util.SystemIntents
import com.google.android.gms.auth.api.identity.Identity
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
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetCalmPalette: (CalmPalette) -> Unit,
    onSetAutoRecordCalls: (Boolean) -> Unit,
    onDeleteRecording: (CallRecordingItem) -> Unit,
    onImportBundledCsv: () -> Unit,
    onImportDevicePhonebook: () -> Unit,
    onScanGoogleDrive: (String) -> Unit,
    onReorganizeCategories: () -> Unit,
    onImportExternalFile: (android.net.Uri, String?) -> Unit,
    onPullFromCloud: () -> Unit,
    onShareExport: (Boolean) -> Unit,
    onOpenQrScanner: () -> Unit,
    onSyncWeeklyWhatsApp: () -> Unit,
    onRemoveFromPhonebook: (ContactRecord) -> Unit,
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
    var driveErrorMsg by remember { mutableStateOf<String?>(null) }
    var isDefaultDialer by remember { mutableStateOf(RabtaInCallService.isDefaultDialer(context)) }
    var playingPath by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

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

    val dialerRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isDefaultDialer = RabtaInCallService.isDefaultDialer(context)
    }

    val recordAudioPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
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
                            "رابطة هو تطبيق الهاتف الافتراضي حالياً — يعرض هوية المتصل من الـ 20,000+ رقم ويدعم التسجيل المحلي."
                        } else {
                            "اجعل رابطة تطبيق الهاتف الافتراضي للاستغناء عن تطبيق الاتصال الأساسي وعرض شاشة المكالمات مع التسجيل."
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
                            Text("تعيين رابطة كتطبيق الاتصال الافتراضي")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تسجيل المكالمات تلقائياً (.m4a)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "ملاحظة: أندرويد 10+ قد يلتقط صوت الطرف الآخر بوضوح أكبر عند تفعيل السبيكر حسب نوع الهاتف.",
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
                val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
                                        mediaPlayer = MediaPlayer().apply {
                                            setDataSource(rec.file.absolutePath)
                                            prepare()
                                            start()
                                            setOnCompletionListener {
                                                playingPath = null
                                            }
                                        }
                                        playingPath = rec.file.absolutePath
                                    }
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
                        IconButton(onClick = { SystemIntents.shareBinaryFile(context, rec.file, "audio/mp4") }) {
                            Icon(Icons.Rounded.Share, contentDescription = "مشاركة", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onDeleteRecording(rec) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "حذف", modifier = Modifier.size(18.dp))
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
                            onClick = { filePicker.launch(arrayOf("text/*", "text/csv", "text/x-vcard", "*/*")) },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).testTag("tools_import_file_btn"),
                        ) {
                            Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ملف CSV/VCF")
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
                            onClick = onReorganizeCategories,
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).testTag("tools_reorganize_btn"),
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("منع التكرار + English + Tags")
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

        // Section 3: 7-Day WhatsApp Logger & Selective Phonebook Sync
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
                                arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
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
}
