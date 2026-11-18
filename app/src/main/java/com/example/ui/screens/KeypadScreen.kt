package com.example.ui.screens

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.domain.model.ContactRecord
import com.example.domain.model.DialHistoryEntry
import com.example.domain.model.PaymentTemplate
import com.example.domain.model.PreCallContext
import com.example.ui.components.ContactAvatarBadge
import com.example.ui.components.UnsavedNumberPaymentPopup
import com.example.ui.theme.InstaPayPurple
import com.example.ui.theme.WhatsAppGreen
import com.example.util.AppLockSecurity
import com.example.util.PhoneUtils
import com.example.util.SimUtils
import com.example.util.SystemIntents
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class KeypadButtonSpec(
    val digit: String,
    val englishLetters: String,
    val arabicLetters: String,
)

private val keypadMatrix = listOf(
    listOf(
        KeypadButtonSpec("1", "", "∞"),
        KeypadButtonSpec("2", "ABC", "أ ب ج"),
        KeypadButtonSpec("3", "DEF", "د هـ و"),
    ),
    listOf(
        KeypadButtonSpec("4", "GHI", "ز ح ط"),
        KeypadButtonSpec("5", "JKL", "ي ك ل"),
        KeypadButtonSpec("6", "MNO", "م ن س"),
    ),
    listOf(
        KeypadButtonSpec("7", "PQRS", "ع ف ص"),
        KeypadButtonSpec("8", "TUV", "ق ر ش"),
        KeypadButtonSpec("9", "WXYZ", "ت ث خ"),
    ),
    listOf(
        KeypadButtonSpec("*", "", ""),
        KeypadButtonSpec("0", "+", ""),
        KeypadButtonSpec("#", "", ""),
    ),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeypadScreen(
    initialDigits: String = "",
    matches: List<ContactRecord>,
    favorites: List<ContactRecord>,
    dialHistory: List<DialHistoryEntry>,
    recentlyDeletedHistory: List<DialHistoryEntry>,
    paymentTemplates: List<PaymentTemplate>,
    showContactPhotos: Boolean,
    preferredSimSlot: Int?,
    preCallContext: PreCallContext? = null,
    onLookupPreCallContext: (String?, String) -> Unit = { _, _ -> },
    onQueryChange: (String) -> Unit,
    onRecordDial: (String, Boolean) -> Unit,
    onSoftDeleteHistory: (String) -> Unit,
    onRestoreHistory: (String) -> Unit,
    onEmptyTrash: () -> Unit,
    onOpenContact: (ContactRecord) -> Unit,
    onWhatsApp: (ContactRecord, String) -> Unit,
    onSaveNewNumber: (String) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var digits by remember(initialDigits) { mutableStateOf(initialDigits) }
    var topListMode by remember { mutableStateOf("AUTO") } // AUTO | HISTORY | TRASH
    var pendingCallSlot by remember { mutableStateOf<Int?>(null) }
    var pendingCallNumber by remember { mutableStateOf("") }
    var showPaymentPopup by remember { mutableStateOf(false) }
    var paymentTargetPhone by remember { mutableStateOf("") }
    var sims by remember { mutableStateOf(SimUtils.getAvailableSims(context)) }

    val callPermsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        sims = SimUtils.getAvailableSims(context)
        val num = pendingCallNumber
        if (num.isNotBlank()) {
            onRecordDial(num, true)
            SystemIntents.dialPhoneWithSimSlot(context, num, pendingCallSlot)
        }
        pendingCallNumber = ""
    }

    fun placeCallWithSim(phone: String, slotIndex: Int?) {
        if (phone.isBlank()) return
        val hasCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val hasPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        if (hasCall && hasPhoneState) {
            onRecordDial(phone, true)
            SystemIntents.dialPhoneWithSimSlot(context, phone, slotIndex)
        } else {
            pendingCallSlot = slotIndex
            pendingCallNumber = phone
            callPermsLauncher.launch(
                arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
            )
        }
    }

    LaunchedEffect(digits) {
        onQueryChange(digits)
        val digitCount = digits.count { it.isDigit() }
        if (digitCount >= 4 || digits.startsWith("*")) {
            delay(450L)
            onRecordDial(digits, false)
        }
    }

    LaunchedEffect(digits, matches) {
        val topMatch = matches.firstOrNull()
        if (digits.isBlank()) {
            onLookupPreCallContext(null, "")
        } else {
            val targetPhone = topMatch?.primaryPhone?.ifBlank { topMatch.phones.firstOrNull().orEmpty() } ?: digits
            onLookupPreCallContext(topMatch?.id, targetPhone)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .padding(horizontal = 16.dp)
                .padding(top = 6.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Top Mode Strip: Speed Dial / Typed History / Recently Deleted
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = topListMode == "AUTO",
                    onClick = { topListMode = "AUTO" },
                    leadingIcon = { Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("السريع و T9", maxLines = 1) },
                    modifier = Modifier.testTag("keypad_mode_auto"),
                )
                FilterChip(
                    selected = topListMode == "HISTORY",
                    onClick = { topListMode = if (topListMode == "HISTORY") "AUTO" else "HISTORY" },
                    leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("المكتوب (${dialHistory.size})", maxLines = 1) },
                    modifier = Modifier.testTag("keypad_mode_history"),
                )
                FilterChip(
                    selected = topListMode == "TRASH",
                    onClick = { topListMode = if (topListMode == "TRASH") "AUTO" else "TRASH" },
                    leadingIcon = { Icon(Icons.Rounded.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("المحذوف (${recentlyDeletedHistory.size})", maxLines = 1) },
                    modifier = Modifier.testTag("keypad_mode_trash"),
                )
                Spacer(Modifier.weight(1f))
                if (topListMode == "TRASH" && recentlyDeletedHistory.isNotEmpty()) {
                    IconButton(
                        onClick = onEmptyTrash,
                        modifier = Modifier.size(48.dp).testTag("keypad_empty_trash_btn"),
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "تفريغ السلة", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Dynamic Upper Pane: T9 Matches, Speed Dial Favorites, or Dial Draft History / Trash
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    topListMode == "AUTO" && digits.isNotBlank() -> {
                        if (matches.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = "لا يوجد تطابق محفوظ لـ $digits",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    FilledTonalButton(
                                        onClick = { onSaveNewNumber(digits) },
                                        modifier = Modifier.testTag("keypad_quick_save_unmatched"),
                                    ) {
                                        Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("حفظ كجهة اتصال جديدة")
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(vertical = 2.dp),
                            ) {
                                items(matches, key = { it.id }) { contact ->
                                    val phone = contact.primaryPhone.ifBlank { contact.phones.firstOrNull().orEmpty() }
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenContact(contact) }
                                            .testTag("keypad_match_${contact.id}"),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            ContactAvatarBadge(
                                                displayName = contact.displayName,
                                                phone = phone,
                                                photoUri = contact.photoUri,
                                                colorSeed = contact.avatarColorSeed,
                                                showPhoto = showContactPhotos,
                                                size = 42.dp,
                                                isBlocked = contact.blocked,
                                                isMuted = contact.muted,
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = contact.displayName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = buildString {
                                                        append(phone)
                                                        if (!contact.nickname.isNullOrBlank()) append(" • ${contact.nickname}")
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            if (phone.isNotBlank()) {
                                                IconButton(
                                                    onClick = { placeCallWithSim(phone, preferredSimSlot) },
                                                    modifier = Modifier.size(48.dp),
                                                ) {
                                                    Icon(Icons.Rounded.Call, contentDescription = "اتصال", tint = MaterialTheme.colorScheme.primary)
                                                }
                                                if (!PhoneUtils.isUssdOrShortCode(phone)) {
                                                    IconButton(
                                                        onClick = { onWhatsApp(contact, phone) },
                                                        modifier = Modifier.size(48.dp),
                                                    ) {
                                                        Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = "واتساب", tint = WhatsAppGreen)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    topListMode == "AUTO" -> {
                        // Speed Dial Favorites & Prompt
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = if (favorites.isNotEmpty()) "الاتصال السريع (المفضلة)" else "بحث T9 الذكي بالاسم أو الرقم",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (favorites.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        favorites.take(12).forEach { fav ->
                                            val phone = fav.primaryPhone.ifBlank { fav.phones.firstOrNull().orEmpty() }
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier
                                                    .width(68.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .combinedClickable(
                                                        onClick = { placeCallWithSim(phone, preferredSimSlot) },
                                                        onLongClick = { onOpenContact(fav) },
                                                    )
                                                    .padding(vertical = 6.dp)
                                                    .testTag("speed_dial_${fav.id}"),
                                            ) {
                                                ContactAvatarBadge(
                                                    displayName = fav.displayName,
                                                    phone = phone,
                                                    photoUri = fav.photoUri,
                                                    colorSeed = fav.avatarColorSeed,
                                                    showPhoto = showContactPhotos,
                                                    size = 48.dp,
                                                )
                                                Text(
                                                    text = fav.displayName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center,
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "اكتب الرقم أو اضغط على الحروف (عربي/English) للبحث الفوري في جهات اتصالك المعزولة",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        val isTrash = topListMode == "TRASH"
                        val activeList = if (isTrash) recentlyDeletedHistory else dialHistory
                        if (activeList.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        imageVector = if (isTrash) Icons.Rounded.RestoreFromTrash else Icons.Rounded.Dialpad,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(30.dp),
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = if (isTrash) "سلة المحذوفات فارغة" else "أي رقم تكتبه هنا يتحفظ تلقائياً في مسودات الطلب",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        } else {
                            val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(vertical = 2.dp),
                            ) {
                                items(activeList, key = { it.id }) { entry ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                digits = entry.phoneNumber
                                                topListMode = "AUTO"
                                            },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = entry.matchedName ?: entry.phoneNumber,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = "${entry.phoneNumber} • ${if (entry.didCall) "اتصال" else "مكتوب"} • ${fmt.format(Date(entry.typedAtEpochMs))}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            if (!PhoneUtils.isUssdOrShortCode(entry.phoneNumber)) {
                                                IconButton(
                                                    onClick = {
                                                        paymentTargetPhone = entry.phoneNumber
                                                        showPaymentPopup = true
                                                    },
                                                    modifier = Modifier.size(48.dp),
                                                ) {
                                                    Icon(Icons.Rounded.Payments, contentDescription = "دفع", tint = InstaPayPurple, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                            IconButton(
                                                onClick = { placeCallWithSim(entry.phoneNumber, preferredSimSlot) },
                                                modifier = Modifier.size(48.dp),
                                            ) {
                                                Icon(Icons.Rounded.Call, contentDescription = "اتصال", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                            }
                                            if (isTrash) {
                                                IconButton(
                                                    onClick = { onRestoreHistory(entry.id) },
                                                    modifier = Modifier.size(48.dp),
                                                ) {
                                                    Icon(Icons.Rounded.RestoreFromTrash, contentDescription = "استرجاع", modifier = Modifier.size(20.dp))
                                                }
                                            } else {
                                                IconButton(
                                                    onClick = { onSoftDeleteHistory(entry.id) },
                                                    modifier = Modifier.size(48.dp),
                                                ) {
                                                    Icon(Icons.Rounded.Delete, contentDescription = "حذف", modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Pre-Call Context Banner when dialed digits or top match has call/AI summary history
            if (digits.isNotBlank() && preCallContext != null) {
                val lastSpoke = preCallContext.lastSpokeEpochMs?.let { AppLockSecurity.formatRelativeTimeAr(it) }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("keypad_pre_call_banner"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            val titleLine = buildString {
                                if (!lastSpoke.isNullOrBlank()) append("آخر تواصل: $lastSpoke")
                                if (preCallContext.pendingActionItemsCount > 0) {
                                    if (isNotEmpty()) append(" • ")
                                    append("${preCallContext.pendingActionItemsCount} مهمة")
                                }
                            }
                            if (titleLine.isNotBlank()) {
                                Text(
                                    text = titleLine,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            preCallContext.oneLineSummary?.takeIf { it.isNotBlank() }?.let { summary ->
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // Sleek Number Display Bar with Paste & Long-Press-to-Clear Backspace
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipText = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                            val cleaned = clipText.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                            if (cleaned.isNotBlank()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                digits = cleaned
                            }
                        },
                        modifier = Modifier.size(48.dp).testTag("keypad_paste_btn"),
                    ) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = "لصق رقم من الحافظة")
                    }

                    Text(
                        text = digits.ifBlank { "أدخل الرقم أو ابحث T9..." },
                        style = if (digits.length > 13) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (digits.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("keypad_display_text"),
                    )

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                enabled = digits.isNotEmpty(),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    digits = digits.dropLast(1)
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    digits = ""
                                },
                            )
                            .testTag("keypad_backspace_btn"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Backspace,
                            contentDescription = "مسح (اضغط مطولاً للتفريغ)",
                            tint = if (digits.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        )
                    }
                }
            }

            // Ergonomic Large Tactile Dialpad Matrix
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                keypadMatrix.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { key ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                tonalElevation = 1.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(58.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .combinedClickable(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            digits += key.digit
                                        },
                                        onLongClick = {
                                            if (key.digit == "0") {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                digits += "+"
                                            }
                                        },
                                    )
                                    .testTag("keypad_key_${key.digit}"),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = key.digit,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    val sub = buildString {
                                        if (key.englishLetters.isNotBlank()) append(key.englishLetters)
                                        if (key.englishLetters.isNotBlank() && key.arabicLetters.isNotBlank()) append(" ")
                                        if (key.arabicLetters.isNotBlank()) append(key.arabicLetters)
                                    }
                                    if (sub.isNotBlank()) {
                                        Text(
                                            text = sub,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Clip,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Ergonomic Action Bar: Save | SIM 1 | SIM 2 | WhatsApp | Single Payment Popup
            val sim1 = sims.getOrNull(0)
            val sim2 = sims.getOrNull(1)
            val hasDigits = digits.isNotBlank()
            val canSocialOrPay = hasDigits && !PhoneUtils.isUssdOrShortCode(digits)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { if (hasDigits) onSaveNewNumber(digits) },
                    enabled = hasDigits,
                    modifier = Modifier.size(48.dp).testTag("keypad_save_btn"),
                ) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = "حفظ كجهة اتصال")
                }

                Button(
                    onClick = { placeCallWithSim(digits, sim1?.slotIndex ?: 0) },
                    enabled = hasDigits,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (preferredSimSlot == 1) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.height(48.dp).testTag("keypad_sim1_btn"),
                ) {
                    Icon(Icons.Rounded.SimCard, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(sim1?.carrierName?.take(10)?.ifBlank { "SIM 1" } ?: "SIM 1", maxLines = 1)
                }

                Button(
                    onClick = { placeCallWithSim(digits, sim2?.slotIndex ?: 1) },
                    enabled = hasDigits,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (preferredSimSlot == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.height(48.dp).testTag("keypad_sim2_btn"),
                ) {
                    Icon(Icons.Rounded.SimCard, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(sim2?.carrierName?.take(10)?.ifBlank { "SIM 2" } ?: "SIM 2", maxLines = 1)
                }

                FilledTonalIconButton(
                    onClick = {
                        if (canSocialOrPay) {
                            val dummy = ContactRecord(
                                id = "dial_${digits.hashCode()}",
                                displayName = digits,
                                primaryPhone = digits,
                                phones = listOf(digits),
                            )
                            onWhatsApp(dummy, digits)
                        }
                    },
                    enabled = canSocialOrPay,
                    modifier = Modifier.size(48.dp).testTag("keypad_wa_btn"),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = "واتساب", tint = WhatsAppGreen)
                }

                FilledTonalButton(
                    onClick = {
                        if (canSocialOrPay) {
                            paymentTargetPhone = digits
                            showPaymentPopup = true
                        }
                    },
                    enabled = canSocialOrPay,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.height(48.dp).testTag("keypad_pay_btn"),
                ) {
                    Icon(Icons.Rounded.Payments, contentDescription = "دفع سريع", tint = InstaPayPurple, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("دفع", color = InstaPayPurple, maxLines = 1)
                }
            }
        }
    }

    if (showPaymentPopup && paymentTargetPhone.isNotBlank()) {
        UnsavedNumberPaymentPopup(
            phone = paymentTargetPhone,
            paymentTemplates = paymentTemplates,
            onDismiss = { showPaymentPopup = false },
        )
    }
}
