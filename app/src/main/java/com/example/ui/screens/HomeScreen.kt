package com.example.ui.screens

import android.Manifest
import android.provider.CallLog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.CallLogEntry
import com.example.domain.model.CallTranscriptRecord
import com.example.domain.model.ContactRecord
import com.example.domain.model.WhatsAppLog
import com.example.ui.components.ContactSummaryCard
import com.example.ui.theme.WhatsAppGreen
import com.example.util.PhoneUtils
import com.example.util.SystemIntents
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    totalCount: Int,
    searchQuery: String,
    searchResults: List<ContactRecord>,
    favorites: List<ContactRecord>,
    frequentCalls: List<CallLogEntry>,
    recentCalls: List<CallLogEntry>,
    hasCallLogPermission: Boolean,
    weeklyLogs: List<WhatsAppLog>,
    bundledImported: Boolean,
    importProgressText: String?,
    cloudSyncStatus: String?,
    showContactPhotos: Boolean = true,
    preferredSimSlot: Int? = null,
    strictPhonebookIsolation: Boolean = true,
    recentTranscripts: List<CallTranscriptRecord> = emptyList(),
    onSearchQueryChange: (String) -> Unit,
    onOpenContact: (ContactRecord) -> Unit,
    onWhatsApp: (ContactRecord, String) -> Unit,
    onAddContact: () -> Unit,
    onCaptureWhatsApp: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onOpenDialpad: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    onImportBundledCsv: () -> Unit,
    onSyncWeeklyWhatsApp: () -> Unit,
    onSaveCallNotes: (CallTranscriptRecord) -> Unit = {},
    onMarkCalendarAdded: (String) -> Unit = {},
    onPermissionsChanged: () -> Unit,
) {
    val context = LocalContext.current

    val callLogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        onPermissionsChanged()
    }

    val contactsPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        onPermissionsChanged()
        onSyncWeeklyWhatsApp()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Global search bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.search_hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "مسح البحث")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_search_input"),
            )
        }

        if (importProgressText != null || cloudSyncStatus != null) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(importProgressText ?: cloudSyncStatus.orEmpty(), style = MaterialTheme.typography.titleSmall)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        // If searching, show ONLY matching search results lazily
        if (searchQuery.isNotBlank()) {
            item {
                Text(
                    text = "نتائج البحث (${searchResults.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (searchResults.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("لا توجد نتائج مطابقة", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "جرب البحث بجزء من الاسم أو الرقم أو اسم الفئة أو المنطقة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(searchResults, key = { it.id }) { contact ->
                    ContactSummaryCard(
                        contact = contact,
                        onClick = { onOpenContact(contact) },
                        onCall = { phone -> SystemIntents.dialPhoneWithSimSlot(context, phone, preferredSimSlot) },
                        onWhatsApp = { phone -> onWhatsApp(contact, phone) },
                        showPhoto = showContactPhotos,
                    )
                }
            }
            return@LazyColumn
        }

        // Post-Call AI Organizer card (Calendar events & Notes extracted from calls)
        val pendingOrganizer = recentTranscripts.firstOrNull {
            it.status == "READY" &&
                ((it.suggestedEventTitle.isNotBlank() && !it.eventAddedToCalendar) ||
                    (it.extractedNotes.isNotBlank() && !it.notesSavedToContact))
        }
        if (pendingOrganizer != null) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(
                                text = "تنظيم ذكي بعد مكالمة ${pendingOrganizer.contactName}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                        if (pendingOrganizer.suggestedEventTitle.isNotBlank()) {
                            Text(
                                text = "موعد مقترح: ${pendingOrganizer.suggestedEventTitle}" +
                                    if (pendingOrganizer.suggestedEventDateIso.isNotBlank()) " (${pendingOrganizer.suggestedEventDateIso})" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                        if (pendingOrganizer.extractedNotes.isNotBlank()) {
                            Text(
                                text = "ملخص: ${pendingOrganizer.extractedNotes}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (pendingOrganizer.suggestedEventTitle.isNotBlank() && !pendingOrganizer.eventAddedToCalendar) {
                                Button(
                                    onClick = {
                                        SystemIntents.insertCalendarEvent(
                                            context = context,
                                            title = pendingOrganizer.suggestedEventTitle,
                                            description = "${pendingOrganizer.contactName} (${pendingOrganizer.phone})\n${pendingOrganizer.extractedNotes}",
                                            startEpochMs = pendingOrganizer.suggestedEventEpochMs,
                                        )
                                        onMarkCalendarAdded(pendingOrganizer.id)
                                    },
                                    modifier = Modifier.testTag("home_add_calendar_btn"),
                                ) {
                                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("أضف للتقويم")
                                }
                            }
                            if (pendingOrganizer.extractedNotes.isNotBlank() && !pendingOrganizer.notesSavedToContact) {
                                OutlinedButton(onClick = { onSaveCallNotes(pendingOrganizer) }) {
                                    Icon(Icons.Rounded.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("حفظ الملاحظات")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Clean Home Status & Quick Actions (no 20,000 list on Home!)
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "قاعدة Felo Contacts السريعة",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = "$totalCount جهة اتصال مفهرسة • اسحب يمين للاتصال أو يسار لواتساب",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            )
                        }
                        FilledTonalIconButton(
                            onClick = onOpenCategories,
                            modifier = Modifier.testTag("home_open_categories_btn"),
                        ) {
                            Icon(Icons.Rounded.FolderCopy, contentDescription = "فتح الفئات")
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = onOpenDialpad,
                            modifier = Modifier.testTag("home_dialpad_btn"),
                        ) {
                            Icon(Icons.Rounded.Dialpad, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("لوحة الاتصال")
                        }
                        FilledTonalButton(
                            onClick = onOpenAiAssistant,
                            modifier = Modifier.testTag("home_ai_assistant_btn"),
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("مساعد Gemini")
                        }
                        FilledTonalButton(
                            onClick = onAddContact,
                            modifier = Modifier.testTag("home_add_contact_btn"),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.add_contact))
                        }
                        FilledTonalButton(
                            onClick = onCaptureWhatsApp,
                            modifier = Modifier.testTag("home_capture_whatsapp_btn"),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.capture_whatsapp))
                        }
                        FilledTonalButton(
                            onClick = onOpenQrScanner,
                            modifier = Modifier.testTag("home_scan_qr_btn"),
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.scan_qr))
                        }
                    }
                }
            }
        }

        // First-run one-tap import for user's attached CSVs
        if (totalCount == 0 && !bundledImported) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "ملفات جهات اتصالك المرفقة جاهزة للاستيراد",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            "اضغط للاستيراد التلقائي وتقسيم الأرقام إلى فئات متداخلة ورفعها لـ Firestore بدون إضافتها لسجل الهاتف.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Button(
                            onClick = onImportBundledCsv,
                            modifier = Modifier.fillMaxWidth().testTag("home_import_bundled_btn"),
                        ) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.import_bundled_csv))
                        }
                    }
                }
            }
        }

        // 7-day WhatsApp selective sync banner (hidden when strict phonebook isolation is enabled)
        val unsyncedWeeklyContacts = weeklyLogs.filter { !it.syncedToPhone }.distinctBy { it.contactId }
        if (!strictPhonebookIsolation && unsyncedWeeklyContacts.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Rounded.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.weekly_whatsapp_banner_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                text = "${unsyncedWeeklyContacts.size} شخص تواصلت معهم على واتساب هذا الأسبوع — احفظهم بالهاتف فقط ليظهروا في واتساب.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                contactsPermLauncher.launch(
                                    arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
                                )
                            },
                            modifier = Modifier.testTag("home_sync_weekly_btn"),
                        ) {
                            Text("مزامنة")
                        }
                    }
                }
            }
        }

        // Favorites strip
        if (favorites.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                    Text("المفضلة السريعة", style = MaterialTheme.typography.titleMedium)
                }
            }
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    favorites.forEach { fav ->
                        FavoriteChipCard(
                            contact = fav,
                            showPhoto = showContactPhotos,
                            onClick = { onOpenContact(fav) },
                            onCall = {
                                val num = fav.primaryPhone.ifBlank { fav.phones.firstOrNull().orEmpty() }
                                SystemIntents.dialPhoneWithSimSlot(context, num, preferredSimSlot)
                            },
                        )
                    }
                }
            }
        }

        // Call log permission prompt or frequent/recent callers
        if (!hasCallLogPermission) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("عرض الأكثر اتصالاً وسجل المكالمات", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "امنح التطبيق صلاحية قراءة سجل المكالمات ليقترح لك تلقائياً الأشخاص الذين تتصل بهم كثيراً ويربطهم بقاعدة أرقامك.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { callLogLauncher.launch(Manifest.permission.READ_CALL_LOG) },
                            modifier = Modifier.testTag("grant_call_log_btn"),
                        ) {
                            Text(stringResource(R.string.grant_call_log))
                        }
                    }
                }
            }
        } else {
            if (frequentCalls.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.frequent_callers_title), style = MaterialTheme.typography.titleMedium)
                }
                items(frequentCalls, key = { "freq_${it.id}" }) { entry ->
                    CallLogRowCard(
                        entry = entry,
                        onOpen = {
                            if (entry.matchedContact != null) {
                                onOpenContact(entry.matchedContact)
                            } else {
                                SystemIntents.dialPhoneWithSimSlot(context, entry.number, preferredSimSlot)
                            }
                        },
                        onDial = { SystemIntents.dialPhoneWithSimSlot(context, entry.number, preferredSimSlot) },
                        onWhatsApp = {
                            val target = entry.matchedContact ?: ContactRecord(
                                id = "call_${entry.number.hashCode()}",
                                displayName = entry.cachedName ?: entry.number,
                                primaryPhone = entry.number,
                                phones = listOf(entry.number),
                            )
                            onWhatsApp(target, entry.number)
                        },
                    )
                }
            }

            if (recentCalls.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.recent_calls_title), style = MaterialTheme.typography.titleMedium)
                }
                items(recentCalls.take(10), key = { "rec_${it.id}" }) { entry ->
                    CallLogRowCard(
                        entry = entry,
                        onOpen = {
                            if (entry.matchedContact != null) {
                                onOpenContact(entry.matchedContact)
                            } else {
                                SystemIntents.dialPhoneWithSimSlot(context, entry.number, preferredSimSlot)
                            }
                        },
                        onDial = { SystemIntents.dialPhoneWithSimSlot(context, entry.number, preferredSimSlot) },
                        onWhatsApp = {
                            val target = entry.matchedContact ?: ContactRecord(
                                id = "call_${entry.number.hashCode()}",
                                displayName = entry.cachedName ?: entry.number,
                                primaryPhone = entry.number,
                                phones = listOf(entry.number),
                            )
                            onWhatsApp(target, entry.number)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteChipCard(
    contact: ContactRecord,
    showPhoto: Boolean = true,
    onClick: () -> Unit,
    onCall: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                val hasPhoto = showPhoto && !contact.photoUri.isNullOrBlank() && File(contact.photoUri).exists()
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (hasPhoto) {
                        AsyncImage(
                            model = File(contact.photoUri!!),
                            contentDescription = contact.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            contact.displayName.firstOrNull()?.uppercase() ?: "#",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                IconButton(onClick = onCall, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Call, contentDescription = "اتصال", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
            Text(contact.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                contact.primaryPhone,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CallLogRowCard(
    entry: CallLogEntry,
    onOpen: () -> Unit,
    onDial: () -> Unit,
    onWhatsApp: () -> Unit,
) {
    val label = entry.matchedContact?.displayName ?: entry.cachedName ?: entry.number
    val formatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    val timeText = formatter.format(Date(entry.timestampMs))
    val callIcon = when (entry.callType) {
        CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Rounded.CallMade
        CallLog.Calls.MISSED_TYPE -> Icons.AutoMirrored.Rounded.CallMissed
        else -> Icons.AutoMirrored.Rounded.CallReceived
    }
    val canWa = !PhoneUtils.isUssdOrShortCode(entry.number)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onDial()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    if (canWa) onWhatsApp()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = entry.number.isNotBlank(),
        enableDismissFromEndToStart = canWa,
        backgroundContent = {
            val dir = dismissState.dismissDirection
            val bgColor = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                SwipeToDismissBoxValue.EndToStart -> WhatsAppGreen.copy(alpha = 0.2f)
                SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.surface
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(bgColor)
                    .padding(horizontal = 18.dp),
            ) {
                if (dir == SwipeToDismissBoxValue.StartToEnd) {
                    Row(modifier = Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("اتصال", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                } else if (dir == SwipeToDismissBoxValue.EndToStart) {
                    Row(modifier = Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("واتساب", style = MaterialTheme.typography.labelLarge, color = WhatsAppGreen)
                        Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = WhatsAppGreen)
                    }
                }
            }
        },
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = callIcon,
                    contentDescription = null,
                    tint = if (entry.callType == CallLog.Calls.MISSED_TYPE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${entry.number} • ${entry.callCount}× • $timeText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledTonalIconButton(onClick = onDial, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Call, contentDescription = "اتصال", modifier = Modifier.size(18.dp))
                }
                if (canWa) {
                    FilledTonalIconButton(onClick = onWhatsApp, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Chat, contentDescription = "واتساب", tint = WhatsAppGreen, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
