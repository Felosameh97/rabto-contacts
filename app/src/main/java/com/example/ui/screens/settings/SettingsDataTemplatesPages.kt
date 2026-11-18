package com.example.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFolderUpload
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.domain.model.GitHubReleaseInfo
import com.example.domain.model.PaymentTemplate
import com.example.domain.model.WhatsAppLog
import com.example.ui.theme.WhatsAppGreen
import com.example.util.SystemIntents
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun DataImportSettingsPage(
    totalContacts: Int,
    duplicateClustersCount: Int,
    isBusy: Boolean,
    driveErrorMsg: String?,
    onSetDriveErrorMsg: (String?) -> Unit,
    onImportBundledCsv: () -> Unit,
    onImportDevicePhonebook: () -> Unit,
    onScanGoogleDrive: (String) -> Unit,
    onPullFromCloud: () -> Unit,
    onExportJson: () -> Unit,
    onShareExport: (Boolean) -> Unit,
    onOpenDuplicateMerge: () -> Unit,
    onReorganizeCategories: () -> Unit,
    onRunAiBilingualEnrich: () -> Unit,
    onOpenQrScanner: () -> Unit,
    filePicker: ManagedActivityResultLauncher<Array<String>, android.net.Uri?>,
    readPhonebookPermLauncher: ManagedActivityResultLauncher<String, Boolean>,
    driveAuthLauncher: ManagedActivityResultLauncher<IntentSenderRequest, androidx.activity.result.ActivityResult>,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    SettingsSubPageContainer {
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
                                                    onSetDriveErrorMsg(null)
                                                    onScanGoogleDrive(token)
                                                } else {
                                                    onSetDriveErrorMsg("تعذر الحصول على رمز دخول Google Drive")
                                                }
                                            }
                                        }
                                        .onFailure { err ->
                                            onSetDriveErrorMsg("فشل ربط Google Drive: ${err.localizedMessage}")
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
                            text = driveErrorMsg,
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
    }
}

@Composable
internal fun TemplatesSettingsPage(
    paymentTemplates: List<PaymentTemplate>,
    whatsappTemplates: List<String>,
    recentLogs: List<WhatsAppLog>,
    onOpenAddPayment: () -> Unit,
    onDeletePaymentTemplate: (String) -> Unit,
    onOpenAddWaTemplate: () -> Unit,
    onRemoveWhatsAppTemplate: (String) -> Unit,
) {
    SettingsSubPageContainer {
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
                        IconButton(onClick = onOpenAddPayment, modifier = Modifier.size(48.dp)) {
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
                                IconButton(onClick = { onDeletePaymentTemplate(tpl.id) }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "حذف")
                                }
                            }
                        }
                    }
                }
            }
        }

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
                        IconButton(onClick = onOpenAddWaTemplate, modifier = Modifier.size(48.dp)) {
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
                            IconButton(onClick = { onRemoveWhatsAppTemplate(tpl) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Rounded.Delete, contentDescription = "حذف القالب", modifier = Modifier.size(18.dp))
                            }
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
                val fmt = remember { SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault()) }
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
    }
}

@Composable
internal fun AccountUpdatesSettingsPage(
    userEmail: String?,
    githubRepoSlug: String,
    releaseInfo: GitHubReleaseInfo?,
    isBusy: Boolean,
    onCheckGithubUpdates: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    var repoInput by remember(githubRepoSlug) { mutableStateOf(githubRepoSlug) }

    SettingsSubPageContainer {
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
    }
}
