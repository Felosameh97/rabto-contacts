package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sync
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.domain.model.ContactRecord
import com.example.domain.model.GitHubReleaseInfo
import com.example.domain.model.PaymentTemplate
import com.example.domain.model.WhatsAppLog
import com.example.ui.components.AddPaymentTemplateDialog
import com.example.ui.theme.WhatsAppGreen
import com.example.util.SystemIntents
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
    isBusy: Boolean,
    onImportBundledCsv: () -> Unit,
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
) {
    val context = LocalContext.current
    var repoInput by remember(githubRepoSlug) { mutableStateOf(githubRepoSlug) }
    var showAddPayment by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportExternalFile(uri, uri.lastPathSegment)
        }
    }

    val contactsPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onPermissionsChanged()
        onSyncWeeklyWhatsApp()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Section 1: CSV / vCard Import & Cloud Sync
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("إدارة البيانات والسحابة ($totalContacts رقم)", style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = onImportBundledCsv,
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("tools_import_bundled_btn"),
                    ) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.import_bundled_csv))
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
                            onClick = onOpenQrScanner,
                            modifier = Modifier.weight(1f).testTag("tools_scan_qr_btn"),
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("مسح QR")
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
                }
            }
        }

        // Section 2: 7-Day WhatsApp Logger & Selective Phonebook Sync
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
                            contactsPermLauncher.launch(
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
            items(syncedDeviceContacts, key = { "dev_${it.id}" }) { c ->
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

        // Section 3: Payment Link Concatenation Templates
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
                            Text("قوالب دمج روابط الدفع", style = MaterialTheme.typography.titleMedium)
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

        // Section 4: GitHub Releases Auto-Update Checker
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
                        "الإصدار الحالي: ${BuildConfig.VERSION_NAME} • اربط مستودع GitHub ليتم فحص أحدث الإصدارات وتحميلها عند فتح التطبيق.",
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
                                    Text(if (releaseInfo.apkDownloadUrl != null) "تحميل APK الجديد" else "فتح صفحة الإصدار")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 5: Account & Sign Out
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
