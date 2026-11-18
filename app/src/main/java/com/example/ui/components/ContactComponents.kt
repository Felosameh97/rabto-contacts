package com.example.ui.components

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.provider.CallLog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.domain.model.CallLogEntry
import com.example.domain.model.CallTranscriptRecord
import com.example.domain.model.ContactRecord
import com.example.domain.model.PaymentTemplate
import com.example.domain.model.PreCallContext
import com.example.telecom.CallRecorderManager
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.InstaPayPurple
import com.example.ui.theme.TelegramBlue
import com.example.ui.theme.WhatsAppGreen
import com.example.util.AppLockSecurity
import com.example.util.ContactPhotoStorage
import com.example.util.EgyptGeoLookup
import com.example.util.PaymentUtils
import com.example.util.PhoneUtils
import com.example.util.QrUtils
import com.example.util.SimUtils
import com.example.util.SystemIntents
import com.example.util.VCardUtils
import android.os.Build
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactSummaryCard(
    contact: ContactRecord,
    onClick: () -> Unit,
    onCall: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    showPhoto: Boolean = true,
    bulkMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val primary = contact.primaryPhone.ifBlank { contact.phones.firstOrNull().orEmpty() }
    val canSwipeCall = primary.isNotBlank() && !bulkMode
    val canSwipeWa = canSwipeCall && !PhoneUtils.isUssdOrShortCode(primary)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (canSwipeCall) onCall(primary)
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    if (canSwipeWa) onWhatsApp(primary)
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = canSwipeCall,
        enableDismissFromEndToStart = canSwipeWa,
        backgroundContent = {
            val dir = dismissState.dismissDirection
            val bgColor = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                SwipeToDismissBoxValue.EndToStart -> WhatsAppGreen.copy(alpha = 0.22f)
                SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.surface
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
            ) {
                if (dir == SwipeToDismissBoxValue.StartToEnd) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("اتصال سريع", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                } else if (dir == SwipeToDismissBoxValue.EndToStart) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("واتساب", style = MaterialTheme.typography.labelLarge, color = WhatsAppGreen)
                        Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = WhatsAppGreen)
                    }
                }
            }
        },
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (bulkMode) onToggleSelect() else onClick() },
                    onLongClick = if (!bulkMode && onLongClick != null) {
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick()
                        }
                    } else null,
                )
                .testTag("contact_card_${contact.id}"),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (bulkMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                    )
                } else {
                    ContactAvatarBadge(
                        displayName = contact.displayName,
                        phone = primary,
                        photoUri = contact.photoUri,
                        colorSeed = contact.avatarColorSeed,
                        showPhoto = showPhoto,
                        size = 46.dp,
                        isBlocked = contact.blocked,
                        isMuted = contact.muted,
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = contact.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (contact.syncedToDevice) {
                            Icon(
                                imageVector = Icons.Rounded.PhoneAndroid,
                                contentDescription = "محفوظ بالهاتف",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    if (!contact.nickname.isNullOrBlank()) {
                        Text(
                            text = contact.nickname,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (primary.isNotBlank()) {
                        Text(
                            text = primary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    val subtitle = listOfNotNull(
                        contact.categoryPaths.firstOrNull(),
                        contact.tags.take(3).takeIf { it.isNotEmpty() }?.joinToString(" • ") { "#$it" },
                        contact.district,
                    ).firstOrNull()
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (primary.isNotBlank()) {
                    FilledTonalIconButton(
                        onClick = { onCall(primary) },
                        modifier = Modifier.size(48.dp).testTag("quick_call_${contact.id}"),
                    ) {
                        Icon(Icons.Rounded.Call, contentDescription = "اتصال")
                    }
                    if (!PhoneUtils.isUssdOrShortCode(primary)) {
                        FilledTonalIconButton(
                            onClick = { onWhatsApp(primary) },
                            modifier = Modifier.size(48.dp).testTag("quick_wa_${contact.id}"),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = "واتساب", tint = WhatsAppGreen)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContactDetailBottomSheet(
    contact: ContactRecord,
    paymentTemplates: List<PaymentTemplate>,
    contactTranscripts: List<CallTranscriptRecord> = emptyList(),
    contactCallLogs: List<CallLogEntry> = emptyList(),
    preCallContext: PreCallContext? = null,
    showPhoto: Boolean = true,
    preferredSimSlot: Int? = null,
    whatsappTemplates: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onToggleFavorite: (ContactRecord) -> Unit,
    onToggleBlocked: (ContactRecord) -> Unit = {},
    onToggleMuted: (ContactRecord) -> Unit = {},
    onEdit: (ContactRecord) -> Unit,
    onDelete: (ContactRecord) -> Unit,
    onWhatsApp: (ContactRecord, String) -> Unit,
    onWhatsAppWithTemplate: (ContactRecord, String, String) -> Unit = { c, p, _ -> onWhatsApp(c, p) },
    onScheduleFollowUp: (ContactRecord, String, Int?) -> Unit = { _, _, _ -> },
    onToggleDeviceSync: (ContactRecord) -> Unit = {},
    onAddPaymentTemplate: (String, String) -> Unit,
    onUpdatePhoto: (ContactRecord, String?) -> Unit = { _, _ -> },
    onAnalyzeCallAudio: (CallTranscriptRecord) -> Unit = {},
    onSaveCallNotes: (CallTranscriptRecord) -> Unit = {},
    onMarkCalendarAdded: (String) -> Unit = {},
    onRecordCall: (String) -> Unit = {},
    onSearchByTag: (String) -> Unit = {},
    onOpenTranscript: (CallTranscriptRecord) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showQrDialog by remember { mutableStateOf(false) }
    var showMeezaQrDialog by remember { mutableStateOf(false) }
    var showAddPaymentDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var waTemplatePhone by remember { mutableStateOf<String?>(null) }
    var pendingFollowUpPhone by remember { mutableStateOf<String?>(null) }
    var pendingFollowUpMinutes by remember { mutableStateOf<Int?>(null) }
    var paymentAmount by remember { mutableStateOf("") }
    var playingTranscriptId by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var sims by remember { mutableStateOf(SimUtils.getAvailableSims(context)) }
    var pendingCallPhone by remember { mutableStateOf("") }
    var pendingCallSlot by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val savedPath = ContactPhotoStorage.savePickedPhoto(context, uri, contact.id)
                if (savedPath != null) {
                    onUpdatePhoto(contact, savedPath)
                }
            }
        }
    }

    val callPermsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        sims = SimUtils.getAvailableSims(context)
        val num = pendingCallPhone
        if (num.isNotBlank()) {
            onRecordCall(num)
            SystemIntents.dialPhoneWithSimSlot(context, num, pendingCallSlot)
        }
        pendingCallPhone = ""
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        val phone = pendingFollowUpPhone
        if (!phone.isNullOrBlank()) {
            onScheduleFollowUp(contact, phone, pendingFollowUpMinutes)
        }
        pendingFollowUpPhone = null
    }

    fun triggerFollowUp(phone: String, delayMinutes: Int?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingFollowUpPhone = phone
            pendingFollowUpMinutes = delayMinutes
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onScheduleFollowUp(contact, phone, delayMinutes)
        }
    }

    fun placeCall(phone: String, slotIndex: Int?) {
        val hasCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val hasPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        if (hasCall && hasPhoneState) {
            onRecordCall(phone)
            SystemIntents.dialPhoneWithSimSlot(context, phone, slotIndex)
        } else {
            pendingCallPhone = phone
            pendingCallSlot = slotIndex
            callPermsLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE))
        }
    }

    val allPhones = remember(contact) {
        (listOf(contact.primaryPhone) + contact.phones).filter { it.isNotBlank() }.distinct()
    }
    var selectedPaymentPhone by remember(contact) {
        mutableStateOf(allPhones.firstOrNull().orEmpty())
    }
    val whatsAppInstalled = remember { SystemIntents.isWhatsAppInstalled(context) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header with Tappable Photo Picker Avatar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .testTag("detail_photo_picker_btn"),
                    contentAlignment = Alignment.Center,
                ) {
                    ContactAvatarBadge(
                        displayName = contact.displayName,
                        phone = contact.primaryPhone,
                        photoUri = contact.photoUri,
                        colorSeed = contact.avatarColorSeed,
                        showPhoto = showPhoto,
                        size = 64.dp,
                        isBlocked = contact.blocked,
                        isMuted = contact.muted,
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoCamera,
                            contentDescription = "تغيير الصورة",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(contact.displayName, style = MaterialTheme.typography.headlineSmall)
                    if (!contact.nickname.isNullOrBlank()) {
                        Text(
                            text = contact.nickname,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val meta = listOfNotNull(contact.jobTitle, contact.organization).joinToString(" • ")
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (contact.blocked || contact.muted) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            if (contact.blocked) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ErrorRed.copy(alpha = 0.16f),
                                ) {
                                    Text(
                                        text = "محظور",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ErrorRed,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            if (contact.muted) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        text = "مكتوم",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                IconButton(onClick = { onToggleFavorite(contact) }, modifier = Modifier.testTag("detail_fav_btn")) {
                    Icon(
                        imageVector = if (contact.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "تثبيت بالمفضلة",
                        tint = if (contact.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { showQrDialog = true }, modifier = Modifier.testTag("detail_qr_btn")) {
                    Icon(Icons.Rounded.QrCode2, contentDescription = "عرض QR")
                }
            }

            if (preCallContext != null && (preCallContext.lastSpokeEpochMs != null || !preCallContext.oneLineSummary.isNullOrBlank())) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detail_pre_call_context"),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val lastSpokeLabel = preCallContext.lastSpokeEpochMs?.let {
                            AppLockSecurity.formatRelativeTimeAr(it)
                        }
                        val headerParts = buildList {
                            lastSpokeLabel?.takeIf { it.isNotBlank() }?.let { add("آخر تواصل: $it") }
                            if (preCallContext.totalCallsCount > 0) add("${preCallContext.totalCallsCount} مكالمة")
                            if (preCallContext.pendingActionItemsCount > 0) add("${preCallContext.pendingActionItemsCount} مهام معلقة")
                        }.joinToString(" • ")
                        if (headerParts.isNotBlank()) {
                            Text(
                                text = headerParts,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        preCallContext.oneLineSummary?.takeIf { it.isNotBlank() }?.let { summary ->
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (contact.categoryPaths.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    contact.categoryPaths.forEach { cat ->
                        AssistChip(onClick = {}, label = { Text(cat, style = MaterialTheme.typography.bodySmall) })
                    }
                }
            }

            if (contact.tags.isNotEmpty()) {
                Text("التاجز (اضغط للبحث عن أشخاص مشابهين)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    contact.tags.forEach { tag ->
                        AssistChip(
                            onClick = { onSearchByTag(tag) },
                            label = { Text("#$tag", style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }

            // Phone numbers with direct multi-channel launchers
            if (allPhones.isNotEmpty()) {
                Text("الأرقام والتواصل المباشر", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                allPhones.forEach { phone ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = phone,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { copyToClipboard(context, phone) }) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "نسخ الرقم", modifier = Modifier.size(20.dp))
                                }
                            }
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val sim1 = sims.getOrNull(0)
                                val sim2 = sims.getOrNull(1)
                                FilledTonalButton(
                                    onClick = { placeCall(phone, sim1?.slotIndex ?: 0) },
                                    modifier = Modifier.testTag("detail_call_sim1_btn"),
                                ) {
                                    Icon(Icons.Rounded.SimCard, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (sim1 != null && sim1.carrierName.isNotBlank()) "SIM 1 (${sim1.carrierName.take(8)})" else "SIM 1")
                                }
                                FilledTonalButton(
                                    onClick = { placeCall(phone, sim2?.slotIndex ?: 1) },
                                    modifier = Modifier.testTag("detail_call_sim2_btn"),
                                ) {
                                    Icon(Icons.Rounded.SimCard, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (sim2 != null && sim2.carrierName.isNotBlank()) "SIM 2 (${sim2.carrierName.take(8)})" else "SIM 2")
                                }
                                if (!PhoneUtils.isUssdOrShortCode(phone)) {
                                    Button(
                                        onClick = { onWhatsApp(contact, phone) },
                                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                        modifier = Modifier.testTag("detail_whatsapp_btn"),
                                    ) {
                                        Icon(Icons.Rounded.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (whatsAppInstalled) "واتساب" else "واتساب ويب")
                                    }
                                    if (whatsappTemplates.isNotEmpty()) {
                                        FilledTonalButton(
                                            onClick = { waTemplatePhone = phone },
                                            modifier = Modifier.testTag("detail_wa_template_btn"),
                                        ) {
                                            Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("رسالة جاهزة")
                                        }
                                    }
                                    FilledTonalButton(
                                        onClick = { SystemIntents.openTelegram(context, contact.telegramHandle ?: phone) },
                                        modifier = Modifier.testTag("detail_telegram_btn"),
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("تيليجرام")
                                    }
                                }
                            }
                        }
                    }
                }

                // Follow-Up Snooze Quick Strip
                val primaryTargetPhone = allPhones.first()
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("فكّرني:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    AssistChip(onClick = { triggerFollowUp(primaryTargetPhone, 30) }, label = { Text("30د") })
                    AssistChip(onClick = { triggerFollowUp(primaryTargetPhone, 60) }, label = { Text("ساعة") })
                    AssistChip(onClick = { triggerFollowUp(primaryTargetPhone, 180) }, label = { Text("3 ساعات") })
                    AssistChip(onClick = { triggerFollowUp(primaryTargetPhone, null) }, label = { Text("بكرة 10ص") })
                }
            }

            // Emails, Telegram handle, Website, and always-accessible Google Maps link
            HorizontalDivider()
            Text("الموقع وبيانات التواصل", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            contact.emails.forEach { email ->
                ActionInfoRow(
                    icon = Icons.Rounded.Email,
                    label = email,
                    actionLabel = "مراسلة",
                    onClick = { SystemIntents.sendEmail(context, email) },
                )
            }
            contact.telegramHandle?.takeIf { it.isNotBlank() }?.let { handle ->
                ActionInfoRow(
                    icon = Icons.AutoMirrored.Rounded.Send,
                    label = "Telegram: $handle",
                    actionLabel = "فتح",
                    onClick = { SystemIntents.openTelegram(context, handle) },
                )
            }
            contact.website?.takeIf { it.isNotBlank() }?.let { url ->
                ActionInfoRow(
                    icon = Icons.Rounded.Language,
                    label = url,
                    actionLabel = "زيارة",
                    onClick = { SystemIntents.openWebsiteOrPayment(context, url) },
                )
            }
            val mapLabel = listOfNotNull(contact.district, contact.address)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" - ")
                .ifBlank {
                    if (contact.latitude != null && contact.longitude != null) {
                        "${contact.latitude}, ${contact.longitude}"
                    } else {
                        "فتح الموقع في خرائط Google"
                    }
                }
            val mapQuery = listOfNotNull(contact.address, contact.district, contact.displayName)
                .firstOrNull { !it.isNullOrBlank() }
            ActionInfoRow(
                icon = Icons.Rounded.LocationOn,
                label = mapLabel,
                actionLabel = "Google Maps",
                onClick = {
                    SystemIntents.openGoogleMaps(
                        context,
                        contact.latitude,
                        contact.longitude,
                        mapQuery,
                    )
                },
            )

            // Dual Payment Section: Option 1 (USSD Codes) + Option 2 (Direct Apps & Meeza QR)
            if (allPhones.isNotEmpty()) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("الدفع والتحويل (كود اتصال + تطبيقات)", style = MaterialTheme.typography.titleSmall, color = InstaPayPurple)
                    TextButton(onClick = { showAddPaymentDialog = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إضافة")
                    }
                }

                if (allPhones.size > 1) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        allPhones.forEach { phone ->
                            AssistChip(
                                onClick = { selectedPaymentPhone = phone },
                                label = { Text(phone) },
                                leadingIcon = if (selectedPaymentPhone == phone) {
                                    { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                            )
                        }
                    }
                }

                val targetPhone = selectedPaymentPhone.ifBlank { allPhones.first() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = paymentAmount,
                        onValueChange = { paymentAmount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("المبلغ (اختياري)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(
                        onClick = { showMeezaQrDialog = true },
                        modifier = Modifier.height(54.dp).testTag("payment_meeza_qr_btn"),
                    ) {
                        Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("QR ميزة")
                    }
                }

                val ussdTemplates = paymentTemplates.filter { it.urlTemplate.startsWith("tel:", ignoreCase = true) }
                val appTemplates = paymentTemplates.filter { !it.urlTemplate.startsWith("tel:", ignoreCase = true) }

                if (ussdTemplates.isNotEmpty()) {
                    Text("الخيار 1: أكواد اتصال USSD مباشرة", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ussdTemplates.forEach { tpl ->
                        val builtUrl = PaymentUtils.buildPaymentUrl(tpl.urlTemplate, targetPhone, paymentAmount)
                        PaymentActionRow(
                            title = tpl.name,
                            subtitle = builtUrl.removePrefix("tel:").replace("%23", "#"),
                            icon = Icons.Rounded.Dialpad,
                            onLaunch = { SystemIntents.openWebsiteOrPayment(context, builtUrl, targetPhone) },
                            onCopy = { copyToClipboard(context, builtUrl.removePrefix("tel:").replace("%23", "#")) },
                            tag = "payment_tpl_${tpl.id}",
                        )
                    }
                }

                if (appTemplates.isNotEmpty()) {
                    Text("الخيار 2: فتح تطبيقات المحافظ (مع نسخ الرقم تلقائياً)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    appTemplates.forEach { tpl ->
                        val builtUrl = PaymentUtils.buildPaymentUrl(tpl.urlTemplate, targetPhone, paymentAmount)
                        PaymentActionRow(
                            title = tpl.name,
                            subtitle = "ينسخ ${PhoneUtils.toLocalEgyptianPhone(targetPhone)} ويفتح التطبيق مباشرة",
                            icon = Icons.Rounded.OpenInNew,
                            onLaunch = { SystemIntents.openWebsiteOrPayment(context, builtUrl, targetPhone) },
                            onCopy = { copyToClipboard(context, PhoneUtils.toLocalEgyptianPhone(targetPhone)) },
                            tag = "payment_tpl_${tpl.id}",
                        )
                    }
                }
            }

            // Call Logs, Audio Playback & AI Transcripts / Calendar Organizer Section
            if (contactTranscripts.isNotEmpty()) {
                HorizontalDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "سجل المكالمات والتسجيلات والترانسكريبشن (${contactTranscripts.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val fmt = remember { SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault()) }
                contactTranscripts.forEach { rec ->
                    val audioFile = remember(rec.audioFilePath) {
                        rec.audioFilePath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
                    }
                    val isPlaying = playingTranscriptId == rec.id
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (audioFile != null) {
                                    FilledTonalIconButton(
                                        onClick = {
                                            if (isPlaying) {
                                                runCatching {
                                                    mediaPlayer?.stop()
                                                    mediaPlayer?.release()
                                                }
                                                mediaPlayer = null
                                                playingTranscriptId = null
                                            } else {
                                                runCatching {
                                                    mediaPlayer?.stop()
                                                    mediaPlayer?.release()
                                                }
                                                mediaPlayer = CallRecorderManager.playAudioFile(
                                                    context = context,
                                                    file = audioFile,
                                                    onStart = { playingTranscriptId = rec.id },
                                                    onComplete = { playingTranscriptId = null },
                                                    onError = { msg ->
                                                        playingTranscriptId = null
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    },
                                                )
                                            }
                                        },
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(
                                            if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                            contentDescription = if (isPlaying) "إيقاف" else "سماع المكالمة",
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fmt.format(Date(rec.timestampMs)) +
                                            if (rec.durationSec > 0) " • ${rec.durationSec} ثانية" else "",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    val statusLabel = when (rec.status) {
                                        "READY" -> "تم التفريغ والتحليل"
                                        "PROCESSING" -> "جاري تفريغ وتحليل الصوت..."
                                        "PENDING" -> "في انتظار التفريغ"
                                        "ERROR" -> "تعذر التفريغ — أعد المحاولة"
                                        else -> "مكالمة مسجلة باللوج"
                                    }
                                    Text(
                                        text = statusLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (audioFile != null && rec.status != "PROCESSING" && rec.status != "READY") {
                                    FilledTonalButton(onClick = { onAnalyzeCallAudio(rec) }) {
                                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("تفريغ")
                                    }
                                } else if (rec.status == "PROCESSING") {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                            }

                            if (rec.highlights.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    rec.highlights.forEach { hl ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                        ) {
                                            Text(
                                                text = "★ $hl",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            if (rec.transcript.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = rec.transcript,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(10.dp),
                                    )
                                }
                            }

                            if (rec.extractedNotes.isNotBlank() || rec.suggestedEventTitle.isNotBlank()) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (rec.extractedNotes.isNotBlank() && !rec.notesSavedToContact) {
                                        OutlinedButton(onClick = { onSaveCallNotes(rec) }) {
                                            Icon(Icons.Rounded.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("حفظ الملاحظات")
                                        }
                                    }
                                    if (rec.suggestedEventTitle.isNotBlank()) {
                                        FilledTonalButton(
                                            onClick = {
                                                SystemIntents.insertCalendarEvent(
                                                    context = context,
                                                    title = rec.suggestedEventTitle,
                                                    description = "${rec.contactName} (${rec.phone})\n${rec.extractedNotes}",
                                                    startEpochMs = rec.suggestedEventEpochMs,
                                                )
                                                onMarkCalendarAdded(rec.id)
                                            },
                                        ) {
                                            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                if (rec.eventAddedToCalendar) "تم للتقويم: ${rec.suggestedEventTitle}"
                                                else "إضافة للتقويم: ${rec.suggestedEventTitle}"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Dedicated Chronological Call History for this Contact
            if (contactCallLogs.isNotEmpty()) {
                HorizontalDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "سجل المكالمات مع ${contact.displayName} (${contactCallLogs.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val logFmt = remember { SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault()) }
                contactCallLogs.take(15).forEach { entry ->
                    val linkedTranscript = entry.transcriptId?.let { tid ->
                        contactTranscripts.firstOrNull { it.id == tid }
                    }
                    val isMissed = entry.callType == CallLog.Calls.MISSED_TYPE ||
                        entry.callType == CallLog.Calls.REJECTED_TYPE
                    val typeIcon = when (entry.callType) {
                        CallLog.Calls.INCOMING_TYPE -> Icons.AutoMirrored.Rounded.CallReceived
                        CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Rounded.CallMade
                        else -> Icons.AutoMirrored.Rounded.CallMissed
                    }
                    val typeLabel = when (entry.callType) {
                        CallLog.Calls.INCOMING_TYPE -> "واردة"
                        CallLog.Calls.OUTGOING_TYPE -> "صادرة"
                        CallLog.Calls.REJECTED_TYPE -> "مرفوضة"
                        else -> "فائتة"
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (linkedTranscript != null) {
                                    onOpenTranscript(linkedTranscript)
                                } else {
                                    placeCall(entry.number, preferredSimSlot ?: entry.simSlot)
                                }
                            }
                            .testTag("detail_call_log_${entry.id}"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = typeIcon,
                                contentDescription = typeLabel,
                                tint = if (isMissed) ErrorRed else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$typeLabel • ${entry.number}" + if (entry.callCount > 1) " (${entry.callCount})" else "",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isMissed) ErrorRed else MaterialTheme.colorScheme.onSurface,
                                )
                                val durationLabel = if (entry.durationSec > 0) {
                                    " • ${AppLockSecurity.formatDurationClock(entry.durationSec)}"
                                } else ""
                                Text(
                                    text = "${logFmt.format(Date(entry.timestampMs))}$durationLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (linkedTranscript != null) {
                                AssistChip(
                                    onClick = { onOpenTranscript(linkedTranscript) },
                                    label = { Text("تفريغ", style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    }
                }
            }

            if (!contact.notes.isNullOrBlank()) {
                HorizontalDivider()
                Text("ملاحظات", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = contact.notes.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { onToggleMuted(contact) },
                    modifier = Modifier.weight(1f).testTag("detail_mute_btn"),
                ) {
                    Icon(
                        imageVector = if (contact.muted) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (contact.muted) "إلغاء الكتم" else "كتم الرنين")
                }
                OutlinedButton(
                    onClick = { onToggleBlocked(contact) },
                    colors = if (contact.blocked) {
                        ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    } else {
                        ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                    },
                    modifier = Modifier.weight(1f).testTag("detail_block_btn"),
                ) {
                    Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (contact.blocked) "فك الحظر" else "حظر المتصل")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        val shareText = AppLockSecurity.formatContactShareText(contact)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة بيانات جهة الاتصال"))
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("detail_share_text_btn"),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("مشاركة كنص")
                }
                OutlinedButton(
                    onClick = {
                        SystemIntents.shareExportFile(
                            context,
                            "${contact.displayName.take(20).replace(" ", "_")}.vcf",
                            "text/x-vcard",
                            VCardUtils.toVCard(contact),
                        )
                    },
                    modifier = Modifier.weight(1f).testTag("detail_share_vcf_btn"),
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("مشاركة vCard")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onEdit(contact) },
                    modifier = Modifier.weight(1f).testTag("detail_edit_btn"),
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("تعديل")
                }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f).testTag("detail_delete_btn"),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("حذف")
                }
            }
        }
    }

    if (showQrDialog) {
        ContactQrDialog(contact = contact, onDismiss = { showQrDialog = false })
    }

    if (showMeezaQrDialog) {
        val targetPhone = selectedPaymentPhone.ifBlank { allPhones.firstOrNull().orEmpty() }
        MeezaPaymentQrDialog(
            contactName = contact.displayName,
            phone = targetPhone,
            amount = paymentAmount.takeIf { it.isNotBlank() },
            onDismiss = { showMeezaQrDialog = false },
        )
    }

    if (showAddPaymentDialog) {
        AddPaymentTemplateDialog(
            onDismiss = { showAddPaymentDialog = false },
            onSave = { name, tpl ->
                onAddPaymentTemplate(name, tpl)
                showAddPaymentDialog = false
            },
        )
    }

    if (waTemplatePhone != null) {
        val targetWaPhone = waTemplatePhone!!
        AlertDialog(
            onDismissRequest = { waTemplatePhone = null },
            title = { Text("اختر رسالة واتساب جاهزة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    whatsappTemplates.forEach { tpl ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onWhatsAppWithTemplate(contact, targetWaPhone, tpl)
                                    waTemplatePhone = null
                                },
                        ) {
                            Text(
                                text = tpl,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onWhatsApp(contact, targetWaPhone)
                    waTemplatePhone = null
                }) { Text("فتح محادثة فارغة") }
            },
            dismissButton = {
                TextButton(onClick = { waTemplatePhone = null }) { Text("إلغاء") }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف جهة الاتصال؟") },
            text = { Text("سيتم حذف ${contact.displayName} من التطبيق والسحابة.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(contact)
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") }
            },
        )
    }
}

@Composable
private fun PaymentActionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onLaunch: () -> Unit,
    onCopy: () -> Unit,
    tag: String,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLaunch)
            .testTag(tag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = InstaPayPurple)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "نسخ", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun MeezaPaymentQrDialog(
    contactName: String,
    phone: String,
    amount: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val payload = remember(contactName, phone, amount) {
        PaymentUtils.buildMeezaQrPayload(contactName, phone, amount)
    }
    val bitmap = remember(payload) { QrUtils.generateQrBitmap(payload, 540) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("QR تحويل ميزة / المحافظ") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Meeza Payment QR",
                        modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }
                Text(
                    text = "المستلم: $contactName (${PhoneUtils.toLocalEgyptianPhone(phone)})" +
                        if (!amount.isNullOrBlank()) "\nالمبلغ: $amount ج.م" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { copyToClipboard(context, PhoneUtils.toLocalEgyptianPhone(phone)) }) {
                Text("نسخ الرقم")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        },
    )
}

@Composable
private fun ActionInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ContactQrDialog(contact: ContactRecord, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val vCard = remember(contact) { VCardUtils.toVCard(contact) }
    val bitmap: Bitmap? = remember(vCard) { QrUtils.generateQrBitmap(vCard, 540) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("QR لـ ${contact.displayName}") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR جهة الاتصال",
                        modifier = Modifier.size(230.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }
                Text("امسح الكود من أي هاتف لإضافة كل الأرقام والإيميلات والفئات فوراً.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                SystemIntents.shareExportFile(context, "contact_qr.vcf", "text/x-vcard", vCard)
            }) { Text("مشاركة vCard") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        },
    )
}

@Composable
fun ContactEditorDialog(
    initialContact: ContactRecord?,
    initialCategoryPath: String = "",
    onDismiss: () -> Unit,
    onSave: (ContactRecord) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var displayName by remember { mutableStateOf(initialContact?.displayName.orEmpty()) }
    var nickname by remember { mutableStateOf(initialContact?.nickname.orEmpty()) }
    var photoUri by remember { mutableStateOf(initialContact?.photoUri) }
    var tagsText by remember { mutableStateOf(initialContact?.tags?.joinToString(", ").orEmpty()) }
    var phonesText by remember {
        mutableStateOf(
            initialContact?.phones?.joinToString("\n")?.ifBlank { initialContact.primaryPhone }
                ?: initialContact?.primaryPhone.orEmpty()
        )
    }
    var emailsText by remember { mutableStateOf(initialContact?.emails?.joinToString(", ").orEmpty()) }
    var categoriesText by remember {
        mutableStateOf(
            initialContact?.categoryPaths?.joinToString("\n")?.ifBlank { initialCategoryPath }
                ?: initialCategoryPath
        )
    }
    var organization by remember { mutableStateOf(initialContact?.organization.orEmpty()) }
    var district by remember { mutableStateOf(initialContact?.district.orEmpty()) }
    var address by remember { mutableStateOf(initialContact?.address.orEmpty()) }
    var latText by remember { mutableStateOf(initialContact?.latitude?.toString().orEmpty()) }
    var lngText by remember { mutableStateOf(initialContact?.longitude?.toString().orEmpty()) }
    var telegram by remember { mutableStateOf(initialContact?.telegramHandle.orEmpty()) }
    var website by remember { mutableStateOf(initialContact?.website.orEmpty()) }
    var notes by remember { mutableStateOf(initialContact?.notes.orEmpty()) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val path = ContactPhotoStorage.savePickedPhoto(context, uri, initialContact?.id)
                if (path != null) photoUri = path
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialContact?.id.isNullOrBlank()) "إضافة جهة اتصال" else "تعديل جهة الاتصال") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val hasPhoto = !photoUri.isNullOrBlank() && File(photoUri!!).exists()
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (hasPhoto) {
                            AsyncImage(
                                model = File(photoUri!!),
                                contentDescription = displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    FilledTonalButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("editor_pick_photo_btn"),
                    ) {
                        Text(if (hasPhoto) "تغيير الصورة" else "إضافة صورة")
                    }
                    if (hasPhoto) {
                        TextButton(onClick = { photoUri = null }) {
                            Text("إزالة", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("الاسم (English الأساسي)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("editor_name_input"),
                )
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("الاسم بالعربي / Nickname / الأسماء البديلة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("editor_nickname_input"),
                )
                OutlinedTextField(
                    value = phonesText,
                    onValueChange = { phonesText = it },
                    label = { Text("الأرقام (رقم في كل سطر)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("editor_phones_input"),
                )
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("التاجز (مفصولة بفاصلة، مثال: EAEAT, 2023, دكتور)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("editor_tags_input"),
                )
                OutlinedTextField(
                    value = categoriesText,
                    onValueChange = { categoriesText = it },
                    label = { Text("الفئات المتداخلة (مثال: الجامعة > هندسة)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("editor_categories_input"),
                )
                OutlinedTextField(
                    value = emailsText,
                    onValueChange = { emailsText = it },
                    label = { Text("البريد الإلكتروني") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = organization,
                    onValueChange = { organization = it },
                    label = { Text("الجهة / الجامعة / الوظيفة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = district,
                    onValueChange = { newDist ->
                        district = newDist
                        val match = EgyptGeoLookup.inferLocation(newDist)
                        if (match != null && latText.isBlank()) {
                            latText = match.lat.toString()
                            lngText = match.lng.toString()
                        }
                    },
                    label = { Text("المنطقة (مثال: المطرية، المرج، مدينة نصر)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("العنوان التفصيلي") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text("Lat") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = { lngText = it },
                        label = { Text("Lng") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = telegram,
                    onValueChange = { telegram = it },
                    label = { Text("تيليجرام (@username أو رقم)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = { Text("موقع ويب / رابط") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val phones = phonesText.split("\n", ",", ":::").map { PhoneUtils.normalizePhone(it) }.filter { it.isNotBlank() }.distinct()
                    val emails = emailsText.split("\n", ",", ":::").map { it.trim() }.filter { it.contains("@") }.distinct()
                    val cats = categoriesText.split("\n", ":::")
                        .map { raw -> raw.replace("/", ">").split(">").map { it.trim() }.filter { it.isNotBlank() }.joinToString(" > ") }
                        .filter { it.isNotBlank() }
                        .distinct()
                    val customTags = tagsText.split(",", "،", "\n", "#")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                    val finalName = displayName.trim().ifBlank { phones.firstOrNull().orEmpty() }.ifBlank { emails.firstOrNull().orEmpty() }
                    if (finalName.isBlank()) return@Button
                    onSave(
                        (initialContact ?: ContactRecord()).copy(
                            displayName = finalName,
                            nickname = nickname.trim().ifBlank { null },
                            photoUri = photoUri?.takeIf { it.isNotBlank() },
                            primaryPhone = phones.firstOrNull().orEmpty(),
                            phones = phones,
                            emails = emails,
                            categoryPaths = cats,
                            tags = customTags,
                            organization = organization.trim().ifBlank { null },
                            district = district.trim().ifBlank { null },
                            address = address.trim().ifBlank { null },
                            latitude = latText.toDoubleOrNull(),
                            longitude = lngText.toDoubleOrNull(),
                            telegramHandle = telegram.trim().ifBlank { null },
                            website = website.trim().ifBlank { null },
                            notes = notes.trim().ifBlank { null },
                        )
                    )
                },
                modifier = Modifier.testTag("editor_save_btn"),
            ) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}

@Composable
fun WhatsAppCaptureDialog(
    initialParsed: ContactRecord? = null,
    onDismiss: () -> Unit,
    onSave: (ContactRecord) -> Unit,
) {
    var rawPaste by remember {
        mutableStateOf(initialParsed?.primaryPhone.orEmpty())
    }
    var name by remember { mutableStateOf(initialParsed?.displayName.orEmpty()) }
    var category by remember {
        mutableStateOf(initialParsed?.categoryPaths?.firstOrNull() ?: "واتساب")
    }
    var notes by remember { mutableStateOf(initialParsed?.notes.orEmpty()) }

    val extractedPhones = remember(rawPaste) {
        val fromText = PhoneUtils.extractPhonesFromSharedText(rawPaste)
        if (fromText.isEmpty() && rawPaste.isNotBlank()) {
            listOfNotNull(PhoneUtils.normalizePhone(rawPaste).takeIf { it.isNotBlank() })
        } else {
            fromText
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة جهة اتصال من واتساب") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "الصق الرقم أو رابط wa.me أو رسالة من واتساب، أو شارك البطاقة مباشرةً إلى تطبيق Felo Contacts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = rawPaste,
                    onValueChange = { rawPaste = it },
                    label = { Text("الرقم أو نص واتساب المنسوخ") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("wa_capture_raw_input"),
                )
                if (extractedPhones.isNotEmpty()) {
                    Text(
                        "الأرقام المستخرجة: ${extractedPhones.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = WhatsAppGreen,
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("الاسم") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("wa_capture_name_input"),
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("الفئة (مثال: الجامعة > أصدقاء)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val primary = extractedPhones.firstOrNull().orEmpty()
                    val finalName = name.trim().ifBlank { primary }
                    if (finalName.isBlank()) return@Button
                    onSave(
                        ContactRecord(
                            displayName = finalName,
                            primaryPhone = primary,
                            phones = extractedPhones,
                            categoryPaths = listOfNotNull(category.trim().takeIf { it.isNotBlank() }),
                            notes = notes.trim().ifBlank { null },
                        )
                    )
                },
                modifier = Modifier.testTag("wa_capture_save_btn"),
            ) { Text("حفظ في الفئة") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}

@Composable
fun AddPaymentTemplateDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var template by remember { mutableStateOf("https://example.com/pay?phone={phone_local}") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة رابط خدمة دفع") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "استخدم {phone_local} للرقم المحلي (010...) أو {phone} للأرقام الدولية بدون +، أو tel:*9*7*{phone_local}%23 لأكواد الاتصال.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الخدمة (مثال: MyFawry)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = { Text("قالب الرابط") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank() && template.isNotBlank()) onSave(name, template) }) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Rabta", text))
    Toast.makeText(context, "تم النسخ", Toast.LENGTH_SHORT).show()
}
