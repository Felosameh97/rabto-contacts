package com.example.ui.screens

import android.provider.CallLog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.CallTranscriptRecord
import com.example.domain.model.ContactRecord
import com.example.domain.model.GlobalSearchResult
import com.example.ui.components.ContactAvatarBadge
import com.example.ui.components.ContactSummaryCard
import com.example.ui.theme.ErrorRed
import com.example.util.SystemIntents
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ContactsFilterMode {
    ALL,
    FAVORITES,
    RECENT,
}

private fun normalizeSectionBucket(displayName: String): String {
    val ch = displayName.trim().firstOrNull() ?: return "#"
    val normalizedAr = when (ch) {
        'أ', 'إ', 'آ' -> 'ا'
        'ى' -> 'ي'
        'ة' -> 'ه'
        else -> ch
    }
    return when {
        normalizedAr in 'a'..'z' || normalizedAr in 'A'..'Z' -> normalizedAr.uppercaseChar().toString()
        normalizedAr in '\u0621'..'\u064A' -> normalizedAr.toString()
        else -> "#"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    allContacts: List<ContactRecord>,
    favorites: List<ContactRecord>,
    totalCount: Int,
    bundledImported: Boolean,
    importProgressText: String?,
    searchQuery: String,
    globalSearchResult: GlobalSearchResult,
    showContactPhotos: Boolean,
    preferredSimSlot: Int?,
    onSearchQueryChange: (String) -> Unit,
    onRecordDial: (String, Boolean) -> Unit,
    onOpenContact: (ContactRecord) -> Unit,
    onOpenTranscript: (CallTranscriptRecord) -> Unit,
    onWhatsApp: (ContactRecord, String) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onImportBundledCsv: () -> Unit,
    onOpenDuplicates: () -> Unit = {},
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var filterMode by remember { mutableStateOf(ContactsFilterMode.ALL) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var scrubbingLetter by remember { mutableStateOf<String?>(null) }
    var scrubberHeightPx by remember { mutableStateOf(1) }

    val availableTags = remember(allContacts) {
        allContacts
            .flatMap { c -> c.tags + c.categoryPaths.map { it.substringBefore(" > ").trim() } }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key }
    }

    val filteredContacts by remember(allContacts, favorites, filterMode, selectedTag) {
        derivedStateOf {
            val base = when {
                selectedTag != null -> allContacts.filter { c ->
                    c.tags.any { it.equals(selectedTag, ignoreCase = true) } ||
                        c.categoryPaths.any { it.contains(selectedTag!!, ignoreCase = true) }
                }
                filterMode == ContactsFilterMode.FAVORITES -> favorites
                filterMode == ContactsFilterMode.RECENT -> allContacts
                    .sortedByDescending { maxOf(it.lastContactedEpochMs, it.clientUpdatedAtMs) }
                    .take(80)
                else -> allContacts
            }
            base
        }
    }

    val groupedAlphabetical = remember(filteredContacts, filterMode, selectedTag) {
        if (filterMode == ContactsFilterMode.RECENT) {
            emptyMap()
        } else {
            val map = linkedMapOf<String, MutableList<ContactRecord>>()
            filteredContacts
                .sortedBy { it.displayName.lowercase(Locale.getDefault()) }
                .forEach { c ->
                    val letter = normalizeSectionBucket(c.displayName)
                    map.getOrPut(letter) { mutableListOf() }.add(c)
                }
            map
        }
    }

    val sectionLetters = remember(groupedAlphabetical) {
        groupedAlphabetical.keys.toList()
    }

    // Track exact item index of every sticky header for instant scrubber jump
    val hasHeaderFavorites = filterMode == ContactsFilterMode.ALL && selectedTag == null && favorites.isNotEmpty()
    val headerIndexByLetter = remember(groupedAlphabetical, hasHeaderFavorites, totalCount, bundledImported) {
        val map = mutableMapOf<String, Int>()
        var idx = 2 // search + filter row
        if (totalCount == 0 && !bundledImported) idx++
        if (hasHeaderFavorites) idx += 2 // favorites title + horizontal strip
        groupedAlphabetical.forEach { (letter, list) ->
            map[letter] = idx
            idx += 1 + list.size
        }
        map
    }

    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = if (searchQuery.isBlank() && sectionLetters.size >= 3) 34.dp else 16.dp, top = 8.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Search Bar + Quick Tools (QR, Nested Folders)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = {
                                Text(
                                    "ابحث بالاسم، الرقم، الملاحظات، أو نص المكالمات...",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(Icons.Rounded.Clear, contentDescription = "مسح البحث")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("contacts_search_input"),
                        )
                        FilledTonalIconButton(
                            onClick = onOpenQrScanner,
                            modifier = Modifier.size(48.dp).testTag("contacts_qr_btn"),
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = stringResource(R.string.scan_qr))
                        }
                        FilledTonalIconButton(
                            onClick = onOpenCategories,
                            modifier = Modifier.size(48.dp).testTag("contacts_categories_btn"),
                        ) {
                            Icon(Icons.Rounded.FolderCopy, contentDescription = stringResource(R.string.nav_categories))
                        }
                        FilledTonalIconButton(
                            onClick = onOpenDuplicates,
                            modifier = Modifier.size(48.dp).testTag("contacts_duplicates_btn"),
                        ) {
                            Icon(Icons.Rounded.MergeType, contentDescription = "فحص ودمج التكرارات")
                        }
                    }
                }

                // Filter & Custom Tag Chips
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = filterMode == ContactsFilterMode.ALL && selectedTag == null,
                            onClick = {
                                filterMode = ContactsFilterMode.ALL
                                selectedTag = null
                            },
                            label = { Text("الكل ($totalCount)", maxLines = 1) },
                            modifier = Modifier.testTag("contacts_filter_all"),
                        )
                        FilterChip(
                            selected = filterMode == ContactsFilterMode.FAVORITES && selectedTag == null,
                            onClick = {
                                filterMode = ContactsFilterMode.FAVORITES
                                selectedTag = null
                            },
                            leadingIcon = { Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("المفضلة (${favorites.size})", maxLines = 1) },
                            modifier = Modifier.testTag("contacts_filter_favorites"),
                        )
                        FilterChip(
                            selected = filterMode == ContactsFilterMode.RECENT && selectedTag == null,
                            onClick = {
                                filterMode = ContactsFilterMode.RECENT
                                selectedTag = null
                            },
                            leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("الأخيرة", maxLines = 1) },
                            modifier = Modifier.testTag("contacts_filter_recent"),
                        )
                        availableTags.forEach { tag ->
                            FilterChip(
                                selected = selectedTag == tag,
                                onClick = {
                                    selectedTag = if (selectedTag == tag) null else tag
                                },
                                leadingIcon = { Icon(Icons.Rounded.Tag, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                label = { Text(tag, maxLines = 1) },
                                modifier = Modifier.testTag("contacts_filter_tag_$tag"),
                            )
                        }
                    }
                }

                if (!importProgressText.isNullOrBlank()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = importProgressText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                }

                if (searchQuery.isNotBlank()) {
                    // Global Semantic Search Results (Contacts + Transcripts + Call Logs)
                    val matchedContacts = globalSearchResult.contacts
                    val matchedTranscripts = globalSearchResult.matchedTranscripts
                    val matchedCalls = globalSearchResult.matchedCallLogs

                    if (matchedContacts.isEmpty() && matchedTranscripts.isEmpty() && matchedCalls.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "لا توجد نتائج مطابقة في جهات الاتصال أو المكالمات أو التفريغات لـ \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }
                    }

                    if (matchedContacts.isNotEmpty()) {
                        item {
                            Text(
                                text = "جهات الاتصال (${matchedContacts.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        items(matchedContacts, key = { "gs_c_${it.id}" }) { contact ->
                            ContactSummaryCard(
                                contact = contact,
                                showPhoto = showContactPhotos,
                                onClick = { onOpenContact(contact) },
                                onCall = { phone ->
                                    onRecordDial(phone, true)
                                    SystemIntents.dialPhoneWithSimSlot(context, phone, preferredSimSlot)
                                },
                                onWhatsApp = { phone -> onWhatsApp(contact, phone) },
                            )
                        }
                    }

                    if (matchedTranscripts.isNotEmpty()) {
                        item {
                            Text(
                                text = "نتائج داخل تفريغات المكالمات (${matchedTranscripts.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        items(matchedTranscripts, key = { "gs_tr_${it.id}" }) { tr ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenTranscript(tr) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tr.contactName.ifBlank { tr.phone },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = tr.oneLineSummary.ifBlank { tr.transcript }.take(120),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (matchedCalls.isNotEmpty()) {
                        item {
                            Text(
                                text = "سجل المكالمات المطابق (${matchedCalls.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        items(matchedCalls, key = { "gs_call_${it.id}" }) { call ->
                            val isMissed = call.callType == CallLog.Calls.MISSED_TYPE
                            val icon = when (call.callType) {
                                CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Rounded.CallMade
                                CallLog.Calls.MISSED_TYPE -> Icons.AutoMirrored.Rounded.CallMissed
                                else -> Icons.AutoMirrored.Rounded.CallReceived
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        call.matchedContact?.let(onOpenContact) ?: run {
                                            onRecordDial(call.number, true)
                                            SystemIntents.dialPhoneWithSimSlot(context, call.number, preferredSimSlot)
                                        }
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(icon, contentDescription = null, tint = if (isMissed) ErrorRed else MaterialTheme.colorScheme.primary)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(call.cachedName ?: call.number, style = MaterialTheme.typography.titleSmall)
                                        Text("${call.number} • ${fmt.format(Date(call.timestampMs))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(
                                        onClick = {
                                            onRecordDial(call.number, true)
                                            SystemIntents.dialPhoneWithSimSlot(context, call.number, preferredSimSlot)
                                        },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(Icons.Rounded.Call, contentDescription = "اتصال", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Normal browsing mode
                    if (totalCount == 0 && !bundledImported) {
                        item {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "قاعدة جهات الاتصال المعزولة جاهزة للاستيراد",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                    Text(
                                        text = "اضغط للاستيراد التلقائي وتنظيم الأرقام بدون تكرار وبدون كتابتها في سجل هاتفك.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                    Button(
                                        onClick = onImportBundledCsv,
                                        modifier = Modifier.fillMaxWidth().testTag("contacts_import_bundled_btn"),
                                    ) {
                                        Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.import_bundled_csv))
                                    }
                                }
                            }
                        }
                    }

                    if (hasHeaderFavorites) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                                Text("المفضلة (${favorites.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                favorites.forEach { fav ->
                                    val phone = fav.primaryPhone.ifBlank { fav.phones.firstOrNull().orEmpty() }
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier
                                            .width(142.dp)
                                            .clickable { onOpenContact(fav) },
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                ContactAvatarBadge(
                                                    displayName = fav.displayName,
                                                    phone = phone,
                                                    photoUri = fav.photoUri,
                                                    colorSeed = fav.avatarColorSeed,
                                                    showPhoto = showContactPhotos,
                                                    size = 38.dp,
                                                )
                                                IconButton(
                                                    onClick = {
                                                        onRecordDial(phone, true)
                                                        SystemIntents.dialPhoneWithSimSlot(context, phone, preferredSimSlot)
                                                    },
                                                    modifier = Modifier.size(36.dp),
                                                ) {
                                                    Icon(Icons.Rounded.Call, contentDescription = "اتصال", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                            Text(
                                                text = fav.displayName,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = phone,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (filterMode == ContactsFilterMode.RECENT) {
                        items(filteredContacts, key = { "rec_c_${it.id}" }) { contact ->
                            ContactSummaryCard(
                                contact = contact,
                                showPhoto = showContactPhotos,
                                onClick = { onOpenContact(contact) },
                                onCall = { phone ->
                                    onRecordDial(phone, true)
                                    SystemIntents.dialPhoneWithSimSlot(context, phone, preferredSimSlot)
                                },
                                onWhatsApp = { phone -> onWhatsApp(contact, phone) },
                            )
                        }
                    } else if (filteredContacts.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "لا توجد جهات اتصال في هذا التصنيف",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }
                    } else {
                        groupedAlphabetical.forEach { (letter, groupContacts) ->
                            stickyHeader(key = "alpha_$letter") {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(26.dp),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = letter,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${groupContacts.size} جهة اتصال",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            items(groupContacts, key = { it.id }) { contact ->
                                ContactSummaryCard(
                                    contact = contact,
                                    showPhoto = showContactPhotos,
                                    onClick = { onOpenContact(contact) },
                                    onCall = { phone ->
                                        onRecordDial(phone, true)
                                        SystemIntents.dialPhoneWithSimSlot(context, phone, preferredSimSlot)
                                    },
                                    onWhatsApp = { phone -> onWhatsApp(contact, phone) },
                                )
                            }
                        }
                    }
                }
            }

            // Fast-Scroll Side Alphabet Scrubber
            if (searchQuery.isBlank() && filterMode != ContactsFilterMode.RECENT && sectionLetters.size >= 3) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp, top = 76.dp, bottom = 76.dp)
                        .width(26.dp)
                        .fillMaxHeight()
                        .onSizeChanged { scrubberHeightPx = it.height.coerceAtLeast(1) }
                        .pointerInput(sectionLetters, headerIndexByLetter) {
                            fun jumpToOffsetY(y: Float) {
                                val fraction = (y / scrubberHeightPx.toFloat()).coerceIn(0f, 0.9999f)
                                val idx = (fraction * sectionLetters.size).toInt().coerceIn(0, sectionLetters.lastIndex)
                                val letter = sectionLetters[idx]
                                scrubbingLetter = letter
                                headerIndexByLetter[letter]?.let { itemIdx ->
                                    scope.launch { listState.scrollToItem(itemIdx) }
                                }
                            }
                            detectTapGestures(
                                onTap = { offset ->
                                    jumpToOffsetY(offset.y)
                                    scrubbingLetter = null
                                },
                            )
                        }
                        .pointerInput(sectionLetters, headerIndexByLetter) {
                            fun jumpToOffsetY(y: Float) {
                                val fraction = (y / scrubberHeightPx.toFloat()).coerceIn(0f, 0.9999f)
                                val idx = (fraction * sectionLetters.size).toInt().coerceIn(0, sectionLetters.lastIndex)
                                val letter = sectionLetters[idx]
                                scrubbingLetter = letter
                                headerIndexByLetter[letter]?.let { itemIdx ->
                                    scope.launch { listState.scrollToItem(itemIdx) }
                                }
                            }
                            detectVerticalDragGestures(
                                onDragStart = { offset -> jumpToOffsetY(offset.y) },
                                onDragEnd = { scrubbingLetter = null },
                                onDragCancel = { scrubbingLetter = null },
                                onVerticalDrag = { change, _ ->
                                    change.consume()
                                    jumpToOffsetY(change.position.y)
                                },
                            )
                        }
                        .testTag("contacts_alphabet_scrubber"),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        val displayLetters = if (sectionLetters.size <= 22) {
                            sectionLetters
                        } else {
                            sectionLetters.filterIndexed { i, _ -> i == 0 || i == sectionLetters.lastIndex || i % 2 == 0 }
                        }
                        displayLetters.forEach { letter ->
                            Text(
                                text = letter,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (scrubbingLetter == letter) FontWeight.Bold else FontWeight.Medium,
                                color = if (scrubbingLetter == letter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            // Floating active letter indicator while scrubbing
            AnimatedVisibility(
                visible = scrubbingLetter != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(68.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = scrubbingLetter.orEmpty(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
