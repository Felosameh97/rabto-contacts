package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.CallTranscriptRecord
import com.example.domain.model.ContactRecord
import com.example.telecom.CallRecordingItem
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.InstaPayPurple
import com.example.ui.theme.WhatsAppGreen
import com.example.util.PhoneUtils
import com.example.util.SimUtils
import com.example.util.SystemIntents
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactContextActionsSheet(
    contact: ContactRecord,
    showContactPhotos: Boolean,
    speedDialMap: Map<String, String> = emptyMap(),
    onDismiss: () -> Unit,
    onCall: (String, Int?) -> Unit,
    onWhatsApp: (ContactRecord, String) -> Unit,
    onOpenProfile: (ContactRecord) -> Unit,
    onEditContact: ((ContactRecord) -> Unit)? = null,
    onEditInKeypad: ((String) -> Unit)? = null,
    onAssignSpeedDial: ((String, String) -> Unit)? = null,
    onToggleFavorite: ((ContactRecord) -> Unit)? = null,
    onToggleMuted: ((ContactRecord) -> Unit)? = null,
    onToggleBlocked: ((ContactRecord) -> Unit)? = null,
    onDeleteContact: ((ContactRecord) -> Unit)? = null,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val phone = contact.primaryPhone.ifBlank { contact.phones.firstOrNull().orEmpty() }
    val sims = remember(context) { SimUtils.getAvailableSims(context) }
    var showSpeedDialPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ContactAvatarBadge(
                    displayName = contact.displayName,
                    phone = phone,
                    photoUri = contact.photoUri,
                    colorSeed = contact.avatarColorSeed,
                    showPhoto = showContactPhotos,
                    size = 48.dp,
                    isBlocked = contact.blocked,
                    isMuted = contact.muted,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contact.displayName.ifBlank { phone },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (phone.isNotBlank()) {
                        Text(
                            text = phone,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            if (phone.isNotBlank()) {
                ContextActionRow(
                    icon = Icons.Rounded.Call,
                    title = "اتصال بالرقم",
                    tint = MaterialTheme.colorScheme.primary,
                    tag = "ctx_contact_call",
                    onClick = {
                        onDismiss()
                        onCall(phone, null)
                    },
                )
                if (sims.size >= 2) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sims.take(2).forEach { sim ->
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onCall(phone, sim.slotIndex)
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("SIM ${sim.slotIndex + 1} (${sim.carrierName.ifBlank { "شريحة" }})", maxLines = 1)
                            }
                        }
                    }
                }
                if (!PhoneUtils.isUssdOrShortCode(phone)) {
                    ContextActionRow(
                        icon = Icons.Rounded.Sms,
                        title = "إرسال رسالة SMS",
                        tint = MaterialTheme.colorScheme.secondary,
                        tag = "ctx_contact_sms",
                        onClick = {
                            onDismiss()
                            SystemIntents.sendSms(context, phone)
                        },
                    )
                    ContextActionRow(
                        icon = Icons.AutoMirrored.Rounded.Chat,
                        title = "محادثة واتساب",
                        tint = WhatsAppGreen,
                        tag = "ctx_contact_wa",
                        onClick = {
                            onDismiss()
                            onWhatsApp(contact, phone)
                        },
                    )
                    val tgTarget = contact.telegramHandle?.takeIf { it.isNotBlank() } ?: phone
                    if (SystemIntents.isTelegramInstalled(context) || !contact.telegramHandle.isNullOrBlank()) {
                        ContextActionRow(
                            icon = Icons.AutoMirrored.Rounded.Send,
                            title = "فتح في تيليجرام",
                            tint = Color(0xFF0088CC),
                            tag = "ctx_contact_tg",
                            onClick = {
                                onDismiss()
                                SystemIntents.openTelegram(context, tgTarget)
                            },
                        )
                    }
                }
                ContextActionRow(
                    icon = Icons.Rounded.ContentCopy,
                    title = "نسخ رقم الهاتف",
                    tag = "ctx_contact_copy",
                    onClick = {
                        SystemIntents.copyText(context, "Phone", phone, "تم نسخ الرقم $phone")
                        onDismiss()
                    },
                )
                onEditInKeypad?.let { editFn ->
                    ContextActionRow(
                        icon = Icons.Rounded.Dialpad,
                        title = "تعديل الرقم في لوحة الاتصال",
                        tag = "ctx_contact_edit_dial",
                        onClick = {
                            onDismiss()
                            editFn(phone)
                        },
                    )
                }
                onAssignSpeedDial?.let {
                    val assignedSlot = speedDialMap.entries.firstOrNull {
                        PhoneUtils.phoneSearchKey(it.value) == PhoneUtils.phoneSearchKey(phone)
                    }?.key
                    ContextActionRow(
                        icon = Icons.Rounded.Speed,
                        title = if (assignedSlot != null) "الاتصال السريع (زر $assignedSlot حالياً)" else "تعيين للاتصال السريع (1..9)",
                        tag = "ctx_contact_speed_dial",
                        onClick = { showSpeedDialPicker = true },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            ContextActionRow(
                icon = Icons.Rounded.Person,
                title = "عرض الملف الشخصي والسجل",
                tag = "ctx_contact_profile",
                onClick = {
                    onDismiss()
                    onOpenProfile(contact)
                },
            )
            onEditContact?.let { editC ->
                ContextActionRow(
                    icon = Icons.Rounded.Edit,
                    title = "تعديل بيانات جهة الاتصال",
                    tag = "ctx_contact_edit",
                    onClick = {
                        onDismiss()
                        editC(contact)
                    },
                )
            }
            onToggleFavorite?.let { toggleFav ->
                ContextActionRow(
                    icon = if (contact.isFavorite) Icons.Rounded.StarOutline else Icons.Rounded.Star,
                    title = if (contact.isFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                    tint = Color(0xFFF59E0B),
                    tag = "ctx_contact_fav",
                    onClick = {
                        onDismiss()
                        toggleFav(contact)
                    },
                )
            }
            onToggleMuted?.let { toggleMute ->
                ContextActionRow(
                    icon = Icons.Rounded.NotificationsOff,
                    title = if (contact.muted) "إلغاء كتم التنبيهات" else "كتم تنبيهات المكالمات",
                    tag = "ctx_contact_mute",
                    onClick = {
                        onDismiss()
                        toggleMute(contact)
                    },
                )
            }
            onToggleBlocked?.let { toggleBlock ->
                ContextActionRow(
                    icon = Icons.Rounded.Block,
                    title = if (contact.blocked) "رفع الحظر عن جهة الاتصال" else "حظر جهة الاتصال",
                    tint = ErrorRed,
                    tag = "ctx_contact_block",
                    onClick = {
                        onDismiss()
                        toggleBlock(contact)
                    },
                )
            }
            ContextActionRow(
                icon = Icons.Rounded.Share,
                title = "مشاركة بيانات جهة الاتصال كنص",
                tag = "ctx_contact_share",
                onClick = {
                    onDismiss()
                    SystemIntents.shareContactAsText(
                        context = context,
                        displayName = contact.displayName,
                        phones = contact.phones.ifEmpty { listOfNotNull(phone.takeIf { it.isNotBlank() }) },
                        emails = contact.emails,
                        notes = contact.notes,
                    )
                },
            )
            onDeleteContact?.let { delC ->
                ContextActionRow(
                    icon = Icons.Rounded.Delete,
                    title = "حذف جهة الاتصال",
                    tint = ErrorRed,
                    tag = "ctx_contact_delete",
                    onClick = {
                        onDismiss()
                        delC(contact)
                    },
                )
            }
        }
    }

    if (showSpeedDialPicker && onAssignSpeedDial != null && phone.isNotBlank()) {
        SpeedDialSlotPickerDialog(
            targetName = contact.displayName.ifBlank { phone },
            targetPhone = phone,
            currentMap = speedDialMap,
            onAssign = { digit ->
                onAssignSpeedDial(digit, phone)
                showSpeedDialPicker = false
                onDismiss()
            },
            onDismiss = { showSpeedDialPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneNumberContextActionsSheet(
    title: String,
    phoneNumber: String,
    matchedContact: ContactRecord? = null,
    linkedTranscript: CallTranscriptRecord? = null,
    speedDialMap: Map<String, String> = emptyMap(),
    allowPasteIntoKeypad: Boolean = false,
    onPasteFromClipboard: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
    onCall: (String, Int?) -> Unit,
    onWhatsApp: (String) -> Unit,
    onEditInKeypad: ((String) -> Unit)? = null,
    onSaveNewNumber: ((String) -> Unit)? = null,
    onOpenContact: ((ContactRecord) -> Unit)? = null,
    onOpenTranscript: ((CallTranscriptRecord) -> Unit)? = null,
    onAssignSpeedDial: ((String, String) -> Unit)? = null,
    onDeleteEntry: (() -> Unit)? = null,
    deleteLabel: String = "حذف من السجل",
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sims = remember(context) { SimUtils.getAvailableSims(context) }
    var showSpeedDialPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title.ifBlank { phoneNumber },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (phoneNumber.isNotBlank() && title != phoneNumber) {
                Text(
                    text = phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (allowPasteIntoKeypad && onPasteFromClipboard != null) {
                ContextActionRow(
                    icon = Icons.Rounded.ContentPaste,
                    title = "لصق رقم من الحافظة عند المؤشر",
                    tint = MaterialTheme.colorScheme.primary,
                    tag = "ctx_phone_paste",
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        if (clip.isNotBlank()) onPasteFromClipboard(clip)
                        onDismiss()
                    },
                )
            }

            if (phoneNumber.isNotBlank()) {
                ContextActionRow(
                    icon = Icons.Rounded.Call,
                    title = "اتصال الآن",
                    tint = MaterialTheme.colorScheme.primary,
                    tag = "ctx_phone_call",
                    onClick = {
                        onDismiss()
                        onCall(phoneNumber, null)
                    },
                )
                if (sims.size >= 2) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sims.take(2).forEach { sim ->
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onCall(phoneNumber, sim.slotIndex)
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("SIM ${sim.slotIndex + 1} (${sim.carrierName.ifBlank { "شريحة" }})", maxLines = 1)
                            }
                        }
                    }
                }
                if (!PhoneUtils.isUssdOrShortCode(phoneNumber)) {
                    ContextActionRow(
                        icon = Icons.Rounded.Sms,
                        title = "إرسال رسالة SMS",
                        tag = "ctx_phone_sms",
                        onClick = {
                            onDismiss()
                            SystemIntents.sendSms(context, phoneNumber)
                        },
                    )
                    ContextActionRow(
                        icon = Icons.AutoMirrored.Rounded.Chat,
                        title = "إرسال رسالة واتساب",
                        tint = WhatsAppGreen,
                        tag = "ctx_phone_wa",
                        onClick = {
                            onDismiss()
                            onWhatsApp(phoneNumber)
                        },
                    )
                }
                ContextActionRow(
                    icon = Icons.Rounded.ContentCopy,
                    title = "نسخ الرقم",
                    tag = "ctx_phone_copy",
                    onClick = {
                        SystemIntents.copyText(context, "Phone", phoneNumber, "تم نسخ الرقم $phoneNumber")
                        onDismiss()
                    },
                )
                onEditInKeypad?.let { editFn ->
                    ContextActionRow(
                        icon = Icons.Rounded.Dialpad,
                        title = "تعديل الرقم في لوحة الاتصال",
                        tag = "ctx_phone_edit_dial",
                        onClick = {
                            onDismiss()
                            editFn(phoneNumber)
                        },
                    )
                }
                onAssignSpeedDial?.let {
                    ContextActionRow(
                        icon = Icons.Rounded.Speed,
                        title = "تعيين كاختصار اتصال سريع (1..9)",
                        tag = "ctx_phone_speed_dial",
                        onClick = { showSpeedDialPicker = true },
                    )
                }
            }

            if (matchedContact != null && onOpenContact != null) {
                ContextActionRow(
                    icon = Icons.Rounded.Person,
                    title = "فتح ملف ${matchedContact.displayName}",
                    tag = "ctx_phone_open_contact",
                    onClick = {
                        onDismiss()
                        onOpenContact(matchedContact)
                    },
                )
            } else if (phoneNumber.isNotBlank() && onSaveNewNumber != null) {
                ContextActionRow(
                    icon = Icons.Rounded.PersonAdd,
                    title = "حفظ كجهة اتصال جديدة معزولة",
                    tint = MaterialTheme.colorScheme.primary,
                    tag = "ctx_phone_save_new",
                    onClick = {
                        onDismiss()
                        onSaveNewNumber(phoneNumber)
                    },
                )
            }

            if (linkedTranscript != null && onOpenTranscript != null) {
                ContextActionRow(
                    icon = Icons.Rounded.GraphicEq,
                    title = "فتح التفريغ الصوتي وملخص AI",
                    tint = InstaPayPurple,
                    tag = "ctx_phone_open_transcript",
                    onClick = {
                        onDismiss()
                        onOpenTranscript(linkedTranscript)
                    },
                )
            }

            onDeleteEntry?.let { delFn ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                ContextActionRow(
                    icon = Icons.Rounded.Delete,
                    title = deleteLabel,
                    tint = ErrorRed,
                    tag = "ctx_phone_delete",
                    onClick = {
                        onDismiss()
                        delFn()
                    },
                )
            }
        }
    }

    if (showSpeedDialPicker && onAssignSpeedDial != null && phoneNumber.isNotBlank()) {
        SpeedDialSlotPickerDialog(
            targetName = title.ifBlank { phoneNumber },
            targetPhone = phoneNumber,
            currentMap = speedDialMap,
            onAssign = { digit ->
                onAssignSpeedDial(digit, phoneNumber)
                showSpeedDialPicker = false
                onDismiss()
            },
            onDismiss = { showSpeedDialPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptContextActionsSheet(
    transcript: CallTranscriptRecord? = null,
    recordingItem: CallRecordingItem? = null,
    onDismiss: () -> Unit,
    onOpenPlayer: ((CallTranscriptRecord) -> Unit)? = null,
    onReanalyzeTranscript: ((CallTranscriptRecord) -> Unit)? = null,
    onTranscribeRecording: ((CallRecordingItem) -> Unit)? = null,
    onSaveNotesToContact: ((CallTranscriptRecord) -> Unit)? = null,
    onDeleteTranscript: ((CallTranscriptRecord) -> Unit)? = null,
    onDeleteRecording: ((CallRecordingItem) -> Unit)? = null,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val title = transcript?.contactName?.ifBlank { transcript.phone }
        ?: recordingItem?.displayName
        ?: "تسجيل مكالمة"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (transcript != null) {
                onOpenPlayer?.let { open ->
                    ContextActionRow(
                        icon = Icons.Rounded.OpenInNew,
                        title = "فتح المشغّل التفاعلي والتفريغ",
                        tint = MaterialTheme.colorScheme.primary,
                        tag = "ctx_tr_open",
                        onClick = {
                            onDismiss()
                            open(transcript)
                        },
                    )
                }
                val summaryText = transcript.summaryBullets.joinToString("\n• ", prefix = "• ").takeIf { transcript.summaryBullets.isNotEmpty() }
                    ?: transcript.oneLineSummary
                if (!summaryText.isNullOrBlank()) {
                    ContextActionRow(
                        icon = Icons.Rounded.ContentCopy,
                        title = "نسخ الملخص الذكي",
                        tag = "ctx_tr_copy_summary",
                        onClick = {
                            SystemIntents.copyText(context, "AI Summary", summaryText, "تم نسخ الملخص")
                            onDismiss()
                        },
                    )
                }
                if (transcript.transcript.isNotBlank()) {
                    ContextActionRow(
                        icon = Icons.Rounded.ContentCopy,
                        title = "نسخ النص الكامل للمكالمة",
                        tag = "ctx_tr_copy_text",
                        onClick = {
                            SystemIntents.copyText(context, "Call Transcript", transcript.transcript, "تم نسخ نص المكالمة")
                            onDismiss()
                        },
                    )
                }
                if (transcript.extractedNotes.isNotBlank() && !transcript.notesSavedToContact && onSaveNotesToContact != null) {
                    ContextActionRow(
                        icon = Icons.Rounded.BookmarkAdd,
                        title = "حفظ الملاحظات في ملف جهة الاتصال",
                        tint = MaterialTheme.colorScheme.primary,
                        tag = "ctx_tr_save_notes",
                        onClick = {
                            onDismiss()
                            onSaveNotesToContact(transcript)
                        },
                    )
                }
                val audioPath = transcript.audioFilePath
                if (audioPath.isNotBlank() && File(audioPath).exists()) {
                    ContextActionRow(
                        icon = Icons.Rounded.Share,
                        title = "مشاركة ملف الصوت",
                        tag = "ctx_tr_share_audio",
                        onClick = {
                            onDismiss()
                            SystemIntents.shareBinaryFile(context, File(audioPath), "audio/mp4")
                        },
                    )
                }
                onReanalyzeTranscript?.let { reanalyze ->
                    ContextActionRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "إعادة التحليل والتلخيص بـ Gemini AI",
                        tint = InstaPayPurple,
                        tag = "ctx_tr_reanalyze",
                        onClick = {
                            onDismiss()
                            reanalyze(transcript)
                        },
                    )
                }
                onDeleteTranscript?.let { del ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ContextActionRow(
                        icon = Icons.Rounded.Delete,
                        title = "حذف التسجيل والملخص",
                        tint = ErrorRed,
                        tag = "ctx_tr_delete",
                        onClick = {
                            onDismiss()
                            del(transcript)
                        },
                    )
                }
            } else if (recordingItem != null) {
                onTranscribeRecording?.let { tr ->
                    ContextActionRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "تفريغ وتلخيص بـ Gemini AI",
                        tint = InstaPayPurple,
                        tag = "ctx_rec_transcribe",
                        onClick = {
                            onDismiss()
                            tr(recordingItem)
                        },
                    )
                }
                ContextActionRow(
                    icon = Icons.Rounded.Share,
                    title = "مشاركة ملف الصوت",
                    tag = "ctx_rec_share",
                    onClick = {
                        onDismiss()
                        SystemIntents.shareBinaryFile(context, recordingItem.file, "audio/mp4")
                    },
                )
                onDeleteRecording?.let { del ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ContextActionRow(
                        icon = Icons.Rounded.Delete,
                        title = "حذف ملف التسجيل",
                        tint = ErrorRed,
                        tag = "ctx_rec_delete",
                        onClick = {
                            onDismiss()
                            del(recordingItem)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SpeedDialSlotPickerDialog(
    targetName: String,
    targetPhone: String,
    currentMap: Map<String, String>,
    onAssign: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعيين الاتصال السريع") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "اختر الزر (1..9) للضغط المطوّل عليه للاتصال المباشر بـ $targetName ($targetPhone):",
                    style = MaterialTheme.typography.bodySmall,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (rowStart in listOf(1, 4, 7)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for (d in rowStart..(rowStart + 2)) {
                                val digit = d.toString()
                                val existing = currentMap[digit]
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (existing != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onAssign(digit) }
                                        .testTag("speed_dial_pick_$digit"),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(digit, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = existing ?: "فارغ",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}

@Composable
private fun ContextActionRow(
    icon: ImageVector,
    title: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    tag: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (tint == ErrorRed) ErrorRed else MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = tint)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag),
    )
}
