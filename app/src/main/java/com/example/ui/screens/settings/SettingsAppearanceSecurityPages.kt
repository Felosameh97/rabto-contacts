package com.example.ui.screens.settings

import android.Manifest
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.ContactRecord
import com.example.ui.theme.CalmPalette
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.WhatsAppGreen

@Composable
internal fun AppearanceSettingsPage(
    themeMode: ThemeMode,
    calmPalette: CalmPalette,
    showContactPhotos: Boolean,
    callPhotoBackground: Boolean,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetCalmPalette: (CalmPalette) -> Unit,
    onSetShowContactPhotos: (Boolean) -> Unit,
    onSetCallPhotoBackground: (Boolean) -> Unit,
) {
    SettingsSubPageContainer {
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

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("عرض الصور الشخصية", style = MaterialTheme.typography.titleMedium)
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
                }
            }
        }
    }
}

@Composable
internal fun SecurityPrivacySettingsPage(
    appLockEnabled: Boolean,
    hasAppLockPin: Boolean,
    biometricLockEnabled: Boolean,
    hideLockscreenCallerDetails: Boolean,
    strictPhonebookIsolation: Boolean,
    syncedDeviceContacts: List<ContactRecord>,
    isBusy: Boolean,
    onOpenSetPinDialog: () -> Unit,
    onToggleAppLockEnabled: (Boolean) -> Unit,
    onSetBiometricLockEnabled: (Boolean) -> Unit,
    onSetHideLockscreenCallerDetails: (Boolean) -> Unit,
    onLockAppNow: () -> Unit,
    onSetStrictPhonebookIsolation: (Boolean) -> Unit,
    onRemoveFromPhonebook: (ContactRecord) -> Unit,
    contactsSyncPermLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    purgeContactsPermLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
) {
    SettingsSubPageContainer {
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
                                    onOpenSetPinDialog()
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
                                onClick = onOpenSetPinDialog,
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

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("العزل التام عن سجل الهاتف", style = MaterialTheme.typography.titleMedium)
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
                                purgeContactsPermLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                            },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth().testTag("tools_purge_phonebook_btn"),
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("فصل وإزالة (${syncedDeviceContacts.size}) رقم متزامن من سجل الموبايل")
                        }
                    }
                }
            }
        }

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
                            onClick = { contactsSyncPermLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS)) },
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
    }
}
