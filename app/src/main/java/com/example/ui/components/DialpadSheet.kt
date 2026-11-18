package com.example.ui.components

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.domain.model.ContactRecord
import com.example.domain.model.DialHistoryEntry
import com.example.domain.model.PaymentTemplate
import com.example.ui.theme.InstaPayPurple
import com.example.ui.theme.WhatsAppGreen
import com.example.util.PaymentUtils
import com.example.util.PhoneUtils
import com.example.util.QrUtils
import com.example.util.SimUtils
import com.example.util.SystemIntents
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DialpadScreen(
    initialDigits: String = "",
    matches: List<ContactRecord>,
    dialHistory: List<DialHistoryEntry>,
    recentlyDeletedHistory: List<DialHistoryEntry>,
    paymentTemplates: List<PaymentTemplate>,
    showContactPhotos: Boolean,
    preferredSimSlot: Int?,
    onQueryChange: (String) -> Unit,
    onRecordDial: (String, Boolean) -> Unit,
    onSoftDeleteHistory: (String) -> Unit,
    onRestoreHistory: (String) -> Unit,
    onEmptyTrash: () -> Unit,
    onOpenContact: (ContactRecord) -> Unit,
    onWhatsApp: (ContactRecord, String) -> Unit,
    onSaveNewNumber: (String) -> Unit,
) {
    DialpadContent(
        initialDigits = initialDigits,
        matches = matches,
        dialHistory = dialHistory,
        recentlyDeletedHistory = recentlyDeletedHistory,
        paymentTemplates = paymentTemplates,
        showContactPhotos = showContactPhotos,
        preferredSimSlot = preferredSimSlot,
        onQueryChange = onQueryChange,
        onRecordDial = onRecordDial,
        onSoftDeleteHistory = onSoftDeleteHistory,
        onRestoreHistory = onRestoreHistory,
        onEmptyTrash = onEmptyTrash,
        onOpenContact = onOpenContact,
        onWhatsApp = onWhatsApp,
        onSaveNewNumber = onSaveNewNumber,
        modifier = Modifier.fillMaxSize(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialpadSheet(
    initialDigits: String = "",
    matches: List<ContactRecord>,
    dialHistory: List<DialHistoryEntry> = emptyList(),
    recentlyDeletedHistory: List<DialHistoryEntry> = emptyList(),
    paymentTemplates: List<PaymentTemplate> = PaymentUtils.defaultTemplates,
    showContactPhotos: Boolean = true,
    preferredSimSlot: Int? = null,
    onQueryChange: (String) -> Unit,
    onRecordDial: (String, Boolean) -> Unit = { _, _ -> },
    onSoftDeleteHistory: (String) -> Unit = {},
    onRestoreHistory: (String) -> Unit = {},
    onEmptyTrash: () -> Unit = {},
    onDismiss: () -> Unit,
    onOpenContact: (ContactRecord) -> Unit,
    onWhatsApp: (ContactRecord, String) -> Unit,
    onSaveNewNumber: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        DialpadContent(
            initialDigits = initialDigits,
            matches = matches,
            dialHistory = dialHistory,
            recentlyDeletedHistory = recentlyDeletedHistory,
            paymentTemplates = paymentTemplates,
            showContactPhotos = showContactPhotos,
            preferredSimSlot = preferredSimSlot,
            onQueryChange = onQueryChange,
            onRecordDial = onRecordDial,
            onSoftDeleteHistory = onSoftDeleteHistory,
            onRestoreHistory = onRestoreHistory,
            onEmptyTrash = onEmptyTrash,
            onOpenContact = onOpenContact,
            onWhatsApp = onWhatsApp,
            onSaveNewNumber = onSaveNewNumber,
            modifier = Modifier
                .fillMaxWidth()
                .height(620.dp),
        )
    }
}

@Composable
fun IntegratedKeypadPanel(
    initialDigits: String = "",
    matches: List<ContactRecord>,
    dialHistory: List<DialHistoryEntry>,
    recentlyDeletedHistory: List<DialHistoryEntry>,
    paymentTemplates: List<PaymentTemplate>,
    showContactPhotos: Boolean,
    preferredSimSlot: Int?,
    onQueryChange: (String) -> Unit,
    onRecordDial: (String, Boolean) -> Unit,
    onSoftDeleteHistory: (String) -> Unit,
    onRestoreHistory: (String) -> Unit,
    onEmptyTrash: () -> Unit,
    onOpenContact: (ContactRecord) -> Unit,
    onWhatsApp: (ContactRecord, String) -> Unit,
    onSaveNewNumber: (String) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var digits by remember(initialDigits) { mutableStateOf(initialDigits) }
    var activeHistoryMode by remember { mutableStateOf<String?>(null) } // null = compact, "HISTORY", "TRASH"
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(
        targetValue = dragOffsetY.coerceAtLeast(0f),
        animationSpec = spring(),
        label = "keypad_drag_y",
    )
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

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 14.dp,
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, animatedOffsetY.roundToInt()) }
            .testTag("integrated_keypad_panel"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(top = 6.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Draggable Handle + Quick Toggle Chips + Collapse Arrow
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragOffsetY > 70f) {
                                    onCollapse()
                                }
                                dragOffsetY = 0f
                            },
                            onDragCancel = { dragOffsetY = 0f },
                            onVerticalDrag = { _, dragAmount ->
                                dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                            },
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .size(width = 42.dp, height = 4.dp),
                ) {}

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = activeHistoryMode == "HISTORY",
                        onClick = {
                            activeHistoryMode = if (activeHistoryMode == "HISTORY") null else "HISTORY"
                        },
                        leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("السجل (${dialHistory.size})", maxLines = 1) },
                        modifier = Modifier.testTag("keypad_history_chip"),
                    )
                    FilterChip(
                        selected = activeHistoryMode == "TRASH",
                        onClick = {
                            activeHistoryMode = if (activeHistoryMode == "TRASH") null else "TRASH"
                        },
                        leadingIcon = { Icon(Icons.Rounded.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("المحذوف (${recentlyDeletedHistory.size})", maxLines = 1) },
                        modifier = Modifier.testTag("keypad_trash_chip"),
                    )
                    Spacer(Modifier.weight(1f))
                    if (activeHistoryMode == "TRASH" && recentlyDeletedHistory.isNotEmpty()) {
                        IconButton(onClick = onEmptyTrash, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "تفريغ السلة", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    FilledTonalIconButton(
                        onClick = onCollapse,
                        modifier = Modifier.size(36.dp).testTag("keypad_collapse_btn"),
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "إخفاء الكيباد")
                    }
                }
            }

            // Live Matched Contacts Strip or Expandable Typed/Trash History
            val showMatchesStrip = activeHistoryMode == null && digits.isNotBlank() && matches.isNotEmpty()
            AnimatedVisibility(visible = showMatchesStrip || activeHistoryMode != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp, max = 132.dp),
                ) {
                    if (showMatchesStrip) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(vertical = 2.dp),
                        ) {
                            items(matches.take(8), key = { it.id }) { contact ->
                                val phone = contact.primaryPhone.ifBlank { contact.phones.firstOrNull().orEmpty() }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenContact(contact) },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        DialpadAvatar(contact = contact, showPhoto = showContactPhotos)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(contact.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (phone.isNotBlank()) {
                                            IconButton(onClick = { placeCallWithSim(phone, preferredSimSlot) }, modifier = Modifier.size(36.dp)) {
                                                Icon(Icons.Rounded.Call, contentDescription = "اتصال", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            if (!PhoneUtils.isUssdOrShortCode(phone)) {
                                                IconButton(onClick = { onWhatsApp(contact, phone) }, modifier = Modifier.size(36.dp)) {
                                                    Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = "واتساب", tint = WhatsAppGreen)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val isTrash = activeHistoryMode == "TRASH"
                        val activeList = if (isTrash) recentlyDeletedHistory else dialHistory
                        if (activeList.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            ) {
                                Text(
                                    text = if (isTrash) "سلة المحذوفات فارغة" else "أي رقم تكتبه يتحفظ تلقائياً هنا",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(14.dp),
                                )
                            }
                        } else {
                            val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(vertical = 2.dp),
                            ) {
                                items(activeList, key = { it.id }) { entry ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                digits = entry.phoneNumber
                                                activeHistoryMode = null
                                            },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                                                    text = "${entry.phoneNumber} • ${fmt.format(Date(entry.typedAtEpochMs))}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                )
                                            }
                                            if (!PhoneUtils.isUssdOrShortCode(entry.phoneNumber)) {
                                                IconButton(
                                                    onClick = {
                                                        paymentTargetPhone = entry.phoneNumber
                                                        showPaymentPopup = true
                                                    },
                                                    modifier = Modifier.size(34.dp),
                                                ) {
                                                    Icon(Icons.Rounded.Payments, contentDescription = "دفع", tint = InstaPayPurple, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                            IconButton(
                                                onClick = { placeCallWithSim(entry.phoneNumber, preferredSimSlot) },
                                                modifier = Modifier.size(34.dp),
                                            ) {
                                                Icon(Icons.Rounded.Call, contentDescription = "اتصال", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            }
                                            if (isTrash) {
                                                IconButton(onClick = { onRestoreHistory(entry.id) }, modifier = Modifier.size(34.dp)) {
                                                    Icon(Icons.Rounded.RestoreFromTrash, contentDescription = "استرجاع", modifier = Modifier.size(18.dp))
                                                }
                                            } else {
                                                IconButton(onClick = { onSoftDeleteHistory(entry.id) }, modifier = Modifier.size(34.dp)) {
                                                    Icon(Icons.Rounded.Delete, contentDescription = "حذف", modifier = Modifier.size(18.dp))
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

            // Number display bar
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipText = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        val cleaned = clipText.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                        if (cleaned.isNotBlank()) digits = cleaned
                    }) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = "لصق")
                    }

                    Text(
                        text = digits.ifBlank { "اكتب الرقم أو التاج..." },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (digits.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).testTag("integrated_keypad_display"),
                    )

                    if (digits.isNotBlank()) {
                        IconButton(onClick = { digits = digits.dropLast(1) }) {
                            Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "مسح")
                        }
                        IconButton(onClick = { digits = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "تفريغ")
                        }
                    }
                }
            }

            // Smooth Tactile Keypad Grid
            val keypadRows = listOf(
                listOf("1" to "", "2" to "أ ب ج", "3" to "د هـ و"),
                listOf("4" to "ز ح ط", "5" to "ي ك ل", "6" to "م ن س"),
                listOf("7" to "ع ف ص", "8" to "ق ر ش", "9" to "ت ث خ"),
                listOf("*" to "", "0" to "+", "#" to ""),
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                keypadRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { (digit, sub) ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        digits += digit
                                    }
                                    .testTag("integrated_key_$digit"),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(digit, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                    if (sub.isNotBlank()) {
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            sub,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Actions: Save | SIM 1 | SIM 2 | WhatsApp | Single Payment Popup
            val sim1 = sims.getOrNull(0)
            val sim2 = sims.getOrNull(1)
            val hasDigits = digits.isNotBlank()
            val canSocialOrPay = hasDigits && !PhoneUtils.isUssdOrShortCode(digits)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { if (hasDigits) onSaveNewNumber(digits) },
                    enabled = hasDigits,
                    modifier = Modifier.size(48.dp).testTag("integrated_keypad_save"),
                ) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = "حفظ")
                }

                Button(
                    onClick = { placeCallWithSim(digits, sim1?.slotIndex ?: 0) },
                    enabled = hasDigits,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (preferredSimSlot == 1) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.height(48.dp).testTag("integrated_keypad_sim1"),
                ) {
                    Icon(Icons.Rounded.SimCard, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("SIM 1", maxLines = 1)
                }

                Button(
                    onClick = { placeCallWithSim(digits, sim2?.slotIndex ?: 1) },
                    enabled = hasDigits,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (preferredSimSlot == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.height(48.dp).testTag("integrated_keypad_sim2"),
                ) {
                    Icon(Icons.Rounded.SimCard, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("SIM 2", maxLines = 1)
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
                    modifier = Modifier.size(48.dp).testTag("integrated_keypad_wa"),
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
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.height(48.dp).testTag("integrated_keypad_pay"),
                ) {
                    Icon(Icons.Rounded.Payments, contentDescription = "دفع", tint = InstaPayPurple, modifier = Modifier.size(18.dp))
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

@Composable
fun DialpadContent(
    initialDigits: String = "",
    matches: List<ContactRecord>,
    dialHistory: List<DialHistoryEntry>,
    recentlyDeletedHistory: List<DialHistoryEntry>,
    paymentTemplates: List<PaymentTemplate>,
    showContactPhotos: Boolean,
    preferredSimSlot: Int?,
    onQueryChange: (String) -> Unit,
    onRecordDial: (String, Boolean) -> Unit,
    onSoftDeleteHistory: (String) -> Unit,
    onRestoreHistory: (String) -> Unit,
    onEmptyTrash: () -> Unit,
    onOpenContact: (ContactRecord) -> Unit,
    onWhatsApp: (ContactRecord, String) -> Unit,
    onSaveNewNumber: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var digits by remember(initialDigits) { mutableStateOf(initialDigits) }
    var showTrashTab by remember { mutableStateOf(false) }
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
            delay(500L)
            onRecordDial(digits, false)
        }
    }

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Header toggle between Recent/Typed History and Recently Deleted (shown when not typing or user toggles)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = !showTrashTab,
                onClick = { showTrashTab = false },
                leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("السجل والمكتوب (${dialHistory.size})") },
                modifier = Modifier.testTag("dialpad_tab_history"),
            )
            FilterChip(
                selected = showTrashTab,
                onClick = { showTrashTab = true },
                leadingIcon = { Icon(Icons.Rounded.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("المحذوفات مؤخراً (${recentlyDeletedHistory.size})") },
                modifier = Modifier.testTag("dialpad_tab_trash"),
            )
            Spacer(Modifier.weight(1f))
            if (showTrashTab && recentlyDeletedHistory.isNotEmpty()) {
                IconButton(onClick = onEmptyTrash, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = "تفريغ السلة", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Scrollable top pane: either live matching contacts when typing, or Dial History / Trash
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (!showTrashTab && digits.isNotBlank() && matches.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    items(matches, key = { it.id }) { contact ->
                        val phone = contact.primaryPhone.ifBlank { contact.phones.firstOrNull().orEmpty() }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenContact(contact) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                DialpadAvatar(contact = contact, showPhoto = showContactPhotos)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(contact.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        phone,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (phone.isNotBlank()) {
                                    IconButton(
                                        onClick = { placeCallWithSim(phone, preferredSimSlot) },
                                        modifier = Modifier.size(38.dp),
                                    ) {
                                        Icon(Icons.Rounded.Call, contentDescription = "اتصال", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    if (!PhoneUtils.isUssdOrShortCode(phone)) {
                                        IconButton(
                                            onClick = { onWhatsApp(contact, phone) },
                                            modifier = Modifier.size(38.dp),
                                        ) {
                                            Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = "واتساب", tint = WhatsAppGreen)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val activeList = if (showTrashTab) recentlyDeletedHistory else dialHistory
                if (activeList.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = if (showTrashTab) Icons.Rounded.RestoreFromTrash else Icons.Rounded.Dialpad,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (showTrashTab) {
                                    "سلة المحذوفات مؤخراً فارغة"
                                } else {
                                    "أي رقم تكتبه هنا يتحفظ تلقائياً في السجل حتى لو لم تتصل"
                                },
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
                                    .clickable { digits = entry.phoneNumber },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.matchedName ?: entry.phoneNumber,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val timeStr = fmt.format(Date(entry.typedAtEpochMs))
                                        val tagStr = if (entry.didCall) "اتصال" else "مكتوب"
                                        Text(
                                            text = "${entry.phoneNumber} • $tagStr • $timeStr",
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
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(Icons.Rounded.Payments, contentDescription = "دفع", tint = InstaPayPurple, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    IconButton(
                                        onClick = { placeCallWithSim(entry.phoneNumber, preferredSimSlot) },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(Icons.Rounded.Call, contentDescription = "اتصال", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                    if (showTrashTab) {
                                        FilledTonalIconButton(
                                            onClick = { onRestoreHistory(entry.id) },
                                            modifier = Modifier.size(36.dp).testTag("restore_dial_${entry.id}"),
                                        ) {
                                            Icon(Icons.Rounded.RestoreFromTrash, contentDescription = "استرجاع", modifier = Modifier.size(18.dp))
                                        }
                                    } else {
                                        IconButton(
                                            onClick = { onSoftDeleteHistory(entry.id) },
                                            modifier = Modifier.size(36.dp).testTag("delete_dial_${entry.id}"),
                                        ) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "نقل للمحذوفات", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Number display bar
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipText = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                    val cleaned = clipText.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                    if (cleaned.isNotBlank()) digits = cleaned
                }) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = "لصق رقم")
                }

                Text(
                    text = digits.ifBlank { "أدخل الرقم هنا..." },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (digits.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("dialpad_display_text"),
                )

                if (digits.isNotBlank()) {
                    IconButton(onClick = { digits = digits.dropLast(1) }) {
                        Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "مسح")
                    }
                    IconButton(onClick = { digits = "" }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "تفريغ")
                    }
                }
            }
        }

        // Keypad Grid
        val keypadRows = listOf(
            listOf("1" to "", "2" to "أ ب ج", "3" to "د هـ و"),
            listOf("4" to "ز ح ط", "5" to "ي ك ل", "6" to "م ن س"),
            listOf("7" to "ع ف ص", "8" to "ق ر ش", "9" to "ت ث خ"),
            listOf("*" to "", "0" to "+", "#" to ""),
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            keypadRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    row.forEach { (digit, sub) ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .clickable { digits += digit }
                                .testTag("dialpad_key_$digit"),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(digit, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                if (sub.isNotBlank()) {
                                    Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // Action Bar: Save | SIM 1 | SIM 2 | WhatsApp | Single Payment Popup Button (without saving!)
        val sim1 = sims.getOrNull(0)
        val sim2 = sims.getOrNull(1)
        val hasDigits = digits.isNotBlank()
        val canSocialOrPay = hasDigits && !PhoneUtils.isUssdOrShortCode(digits)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = { if (hasDigits) onSaveNewNumber(digits) },
                enabled = hasDigits,
                modifier = Modifier.size(48.dp).testTag("dialpad_save_btn"),
            ) {
                Icon(Icons.Rounded.PersonAdd, contentDescription = "حفظ الرقم")
            }

            Button(
                onClick = { placeCallWithSim(digits, sim1?.slotIndex ?: 0) },
                enabled = hasDigits,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (preferredSimSlot == 1) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.height(48.dp).testTag("dialpad_sim1_btn"),
            ) {
                Icon(Icons.Rounded.SimCard, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (sim1 != null && sim1.carrierName.isNotBlank()) "SIM 1" else "SIM 1",
                    maxLines = 1,
                )
            }

            Button(
                onClick = { placeCallWithSim(digits, sim2?.slotIndex ?: 1) },
                enabled = hasDigits,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (preferredSimSlot == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.height(48.dp).testTag("dialpad_sim2_btn"),
            ) {
                Icon(Icons.Rounded.SimCard, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("SIM 2", maxLines = 1)
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
                modifier = Modifier.size(48.dp).testTag("dialpad_wa_btn"),
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
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.height(48.dp).testTag("dialpad_pay_btn"),
            ) {
                Icon(Icons.Rounded.Payments, contentDescription = "دفع", tint = InstaPayPurple, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("دفع", color = InstaPayPurple, maxLines = 1)
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

@Composable
private fun DialpadAvatar(contact: ContactRecord, showPhoto: Boolean) {
    ContactAvatarBadge(
        displayName = contact.displayName,
        phone = contact.primaryPhone,
        photoUri = contact.photoUri,
        colorSeed = contact.avatarColorSeed,
        showPhoto = showPhoto,
        size = 40.dp,
        isBlocked = contact.blocked,
        isMuted = contact.muted,
    )
}

@Composable
fun UnsavedNumberPaymentPopup(
    phone: String,
    paymentTemplates: List<PaymentTemplate>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var amount by remember { mutableStateOf("") }
    var showQr by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("خيارات الدفع لـ ${PhoneUtils.toLocalEgyptianPhone(phone)}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "تحويل فوري من غير ما تسجل الرقم في جهات الاتصال",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("المبلغ (اختياري)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                val ussdTemplates = paymentTemplates.filter { it.urlTemplate.startsWith("tel:", ignoreCase = true) }
                val appTemplates = paymentTemplates.filter { !it.urlTemplate.startsWith("tel:", ignoreCase = true) }

                if (ussdTemplates.isNotEmpty()) {
                    Text("أكواد USSD المباشرة", style = MaterialTheme.typography.labelLarge, color = InstaPayPurple)
                    ussdTemplates.forEach { tpl ->
                        val url = PaymentUtils.buildPaymentUrl(tpl.urlTemplate, phone, amount)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { SystemIntents.openWebsiteOrPayment(context, url, phone) },
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Rounded.Dialpad, contentDescription = null, tint = InstaPayPurple, modifier = Modifier.size(18.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tpl.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        url.removePrefix("tel:").replace("%23", "#"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("USSD", url.removePrefix("tel:").replace("%23", "#")))
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "نسخ", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                if (appTemplates.isNotEmpty()) {
                    Text("تطبيقات المحافظ وإنستاباي", style = MaterialTheme.typography.labelLarge, color = InstaPayPurple)
                    appTemplates.forEach { tpl ->
                        val url = PaymentUtils.buildPaymentUrl(tpl.urlTemplate, phone, amount)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { SystemIntents.openWebsiteOrPayment(context, url, phone) },
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = InstaPayPurple, modifier = Modifier.size(18.dp))
                                Text(tpl.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                FilledTonalButton(
                    onClick = { showQr = !showQr },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (showQr) "إخفاء QR ميزة" else "عرض QR ميزة للتحويل")
                }

                if (showQr) {
                    val payload = remember(phone, amount) {
                        PaymentUtils.buildMeezaQrPayload(phone, phone, amount.takeIf { it.isNotBlank() })
                    }
                    val bitmap = remember(payload) { QrUtils.generateQrBitmap(payload, 420) }
                    if (bitmap != null) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Meeza QR",
                                modifier = Modifier.size(180.dp).clip(RoundedCornerShape(12.dp)),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        },
    )
}
