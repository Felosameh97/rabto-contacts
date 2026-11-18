package com.example.ui.screens

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.LocalMediaCacheManager
import com.example.domain.model.CallActionItem
import com.example.domain.model.CallTranscriptRecord
import com.example.domain.model.LocalMediaCacheStats
import com.example.domain.model.PaymentTemplate
import com.example.telecom.CallRecorderManager
import com.example.telecom.CallRecordingItem
import com.example.ui.components.InteractiveTranscriptPlayer
import com.example.ui.components.UnsavedNumberPaymentPopup
import com.example.ui.theme.InstaPayPurple
import com.example.util.PhoneUtils
import com.example.util.SystemIntents
import com.example.util.VaultActionHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class VaultFilter(val labelAr: String) {
    ALL("الكل"),
    TASKS("المهام المفتوحة"),
    READY("ملخصات جاهزة"),
    PENDING("قيد التفريغ"),
    HAS_ACTIONS("بها مهام"),
}

@Composable
fun AiVaultScreen(
    transcripts: List<CallTranscriptRecord>,
    rawRecordings: List<CallRecordingItem>,
    cacheStats: LocalMediaCacheStats,
    paymentTemplates: List<PaymentTemplate> = emptyList(),
    onToggleActionItem: (String, String) -> Unit,
    onSaveCallNotes: (CallTranscriptRecord) -> Unit,
    onMarkCalendarAdded: (String) -> Unit,
    onScheduleActionReminder: (CallTranscriptRecord, CallActionItem) -> Unit = { _, _ -> },
    onAnalyzeTranscript: (CallTranscriptRecord) -> Unit,
    onDeleteTranscript: (String) -> Unit,
    onTranscribeRawRecording: (CallRecordingItem) -> Unit,
    onDeleteRawRecording: (CallRecordingItem) -> Unit,
    onPurgeOrphanedCache: () -> Unit = {},
    onRefreshVault: () -> Unit,
    onOpenLiveTranscriber: () -> Unit,
    onOpenAiAssistant: () -> Unit,
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(VaultFilter.ALL) }
    var activePlayerRecordId by remember { mutableStateOf<String?>(null) }
    var playingRawPath by remember { mutableStateOf<String?>(null) }
    var rawMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var paymentTargetPhone by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                rawMediaPlayer?.stop()
                rawMediaPlayer?.release()
            }
            rawMediaPlayer = null
        }
    }

    fun stopRawPlayback() {
        runCatching {
            rawMediaPlayer?.stop()
            rawMediaPlayer?.release()
        }
        rawMediaPlayer = null
        playingRawPath = null
    }

    fun playRawAudio(file: File) {
        if (playingRawPath == file.absolutePath) {
            stopRawPlayback()
            return
        }
        stopRawPlayback()
        activePlayerRecordId = null
        rawMediaPlayer = CallRecorderManager.playAudioFile(
            context = context,
            file = file,
            onStart = { playingRawPath = file.absolutePath },
            onComplete = {
                playingRawPath = null
                rawMediaPlayer = null
            },
            onError = { err ->
                playingRawPath = null
                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            },
        )
    }

    val openTasksAll = remember(transcripts) {
        VaultActionHelper.extractOpenActionItems(transcripts)
    }

    val filteredOpenTasks = remember(openTasksAll, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.getDefault())
        if (q.isEmpty()) {
            openTasksAll
        } else {
            openTasksAll.filter { task ->
                task.actionItem.title.lowercase(Locale.getDefault()).contains(q) ||
                    task.record.contactName.lowercase(Locale.getDefault()).contains(q) ||
                    task.record.phone.contains(q)
            }
        }
    }

    val filteredTranscripts = remember(transcripts, searchQuery, activeFilter) {
        val q = searchQuery.trim().lowercase(Locale.getDefault())
        transcripts.filter { rec ->
            val filterMatch = when (activeFilter) {
                VaultFilter.ALL -> true
                VaultFilter.TASKS -> false
                VaultFilter.READY -> rec.status == "READY"
                VaultFilter.PENDING -> rec.status != "READY"
                VaultFilter.HAS_ACTIONS -> rec.actionItems.any { !it.completed }
            }
            if (!filterMatch) return@filter false
            if (q.isEmpty()) return@filter true
            rec.contactName.lowercase(Locale.getDefault()).contains(q) ||
                rec.phone.contains(q) ||
                rec.transcript.lowercase(Locale.getDefault()).contains(q) ||
                rec.oneLineSummary.lowercase(Locale.getDefault()).contains(q) ||
                rec.extractedNotes.lowercase(Locale.getDefault()).contains(q) ||
                rec.highlights.any { it.lowercase(Locale.getDefault()).contains(q) }
        }
    }

    val linkedPaths = remember(transcripts) {
        transcripts.flatMap { listOfNotNull(it.audioFilePath.takeIf(String::isNotBlank), it.audioFileName.takeIf(String::isNotBlank)) }.toSet()
    }
    val unlinkedRecordings = remember(rawRecordings, linkedPaths) {
        rawRecordings.filterNot { linkedPaths.contains(it.file.absolutePath) || linkedPaths.contains(it.file.name) }
    }

    val fmt = remember { SimpleDateFormat("dd/MM/yyyy • hh:mm a", Locale.getDefault()) }

    fun executeSmartAction(record: CallTranscriptRecord, item: CallActionItem, kind: String) {
        when (kind) {
            "CALENDAR" -> {
                SystemIntents.insertCalendarEvent(
                    context = context,
                    title = item.title.ifBlank { record.suggestedEventTitle.ifBlank { "متابعة مكالمة" } },
                    description = "${record.contactName} (${record.phone})\n${record.oneLineSummary}",
                    startEpochMs = item.dueEpochMs ?: record.suggestedEventEpochMs,
                )
                onMarkCalendarAdded(record.id)
            }
            "PAYMENT" -> {
                if (record.phone.isNotBlank() && !PhoneUtils.isUssdOrShortCode(record.phone)) {
                    paymentTargetPhone = record.phone
                } else {
                    onScheduleActionReminder(record, item)
                }
            }
            else -> onScheduleActionReminder(record, item)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Vault Summary & Offline Media Cache Stats Card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)),
                    modifier = Modifier.fillMaxWidth().testTag("ai_vault_header_card"),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(
                                        text = "خزنة المكالمات والملخصات",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    val sizeLabel = VaultActionHelper.formatBytesReadable(cacheStats.totalBytes)
                                    val openActions = openTasksAll.size
                                    Text(
                                        text = buildString {
                                            append("${transcripts.size} تفريغ • ${cacheStats.recordingFilesCount} تسجيل ($sizeLabel)")
                                            if (openActions > 0) append(" • $openActions مهام مفتوحة")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (cacheStats.recordingFilesCount > 0 || unlinkedRecordings.isNotEmpty()) {
                                    IconButton(
                                        onClick = onPurgeOrphanedCache,
                                        modifier = Modifier.size(48.dp).testTag("ai_vault_purge_cache_btn"),
                                    ) {
                                        Icon(Icons.Rounded.CleaningServices, contentDescription = "تنظيف كاش الصوت اليتيم")
                                    }
                                }
                                IconButton(
                                    onClick = onRefreshVault,
                                    modifier = Modifier.size(48.dp).testTag("ai_vault_refresh_btn"),
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = "تحديث الخزنة")
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = onOpenLiveTranscriber,
                                modifier = Modifier.weight(1f).testTag("ai_vault_live_transcribe_btn"),
                            ) {
                                Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("تفريغ مباشر", maxLines = 1)
                            }
                            FilledTonalButton(
                                onClick = onOpenAiAssistant,
                                modifier = Modifier.weight(1f).testTag("ai_vault_assistant_btn"),
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("مساعد AI", maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Search & Filter Strip
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("ابحث داخل نصوص المكالمات والملخصات...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "مسح")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().testTag("ai_vault_search_input"),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VaultFilter.entries.forEach { filter ->
                            val chipLabel = if (filter == VaultFilter.TASKS) {
                                "${filter.labelAr} (${openTasksAll.size})"
                            } else {
                                filter.labelAr
                            }
                            FilterChip(
                                selected = activeFilter == filter,
                                onClick = { activeFilter = filter },
                                leadingIcon = if (filter == VaultFilter.TASKS) {
                                    { Icon(Icons.Rounded.Checklist, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                label = { Text(chipLabel, maxLines = 1) },
                                modifier = Modifier.testTag("ai_vault_filter_${filter.name}"),
                            )
                        }
                    }
                }
            }

            if (activeFilter == VaultFilter.TASKS) {
                if (filteredOpenTasks.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().testTag("ai_vault_tasks_empty"),
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Checklist,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                    modifier = Modifier.size(38.dp),
                                )
                                Text("لا توجد مهام متابعة مفتوحة حالياً", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "المواعيد والالتزامات والمدفوعات المستخرجة من مكالماتك تظهر هنا لتنفيذها بضغطة واحدة.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = filteredOpenTasks,
                        key = { "${it.record.id}_${it.actionItem.id}" },
                    ) { task ->
                        OpenTaskHubCard(
                            record = task.record,
                            item = task.actionItem,
                            kind = task.resolvedKind,
                            formattedCallDate = fmt.format(Date(task.record.timestampMs.takeIf { it > 0L } ?: task.record.updatedAtEpochMs)),
                            onToggleComplete = { onToggleActionItem(task.record.id, task.actionItem.id) },
                            onExecute = { executeSmartAction(task.record, task.actionItem, task.resolvedKind) },
                        )
                    }
                }
            } else if (filteredTranscripts.isEmpty() && unlinkedRecordings.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(40.dp),
                            )
                            Text("لا توجد تفريغات أو تسجيلات مطابقة", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "سجّل أي مكالمة أو افتح مفرغ الصوت المباشر لتخزين النص والملخص والمهام محلياً في الخزنة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(filteredTranscripts, key = { it.id }) { record ->
                    val playableFile = remember(record.audioFilePath, record.audioFileName) {
                        LocalMediaCacheManager.resolvePlayableAudioFile(
                            context = context,
                            audioFilePath = record.audioFilePath,
                            audioFileName = record.audioFileName,
                        )
                    }
                    VaultTranscriptCard(
                        record = record,
                        playableFile = playableFile,
                        isActivePlayer = activePlayerRecordId == record.id,
                        onRequestPlayerFocus = {
                            stopRawPlayback()
                            activePlayerRecordId = record.id
                        },
                        formattedDate = fmt.format(Date(record.timestampMs.takeIf { it > 0L } ?: record.updatedAtEpochMs)),
                        onToggleActionItem = { itemId -> onToggleActionItem(record.id, itemId) },
                        onExecuteActionItem = { item, kind -> executeSmartAction(record, item, kind) },
                        onSaveNotes = { onSaveCallNotes(record) },
                        onMarkCalendarAdded = { onMarkCalendarAdded(record.id) },
                        onAnalyze = { onAnalyzeTranscript(record) },
                        onDelete = { onDeleteTranscript(record.id) },
                    )
                }

                if (unlinkedRecordings.isNotEmpty()) {
                    item {
                        Text(
                            text = "تسجيلات صوتية محلية بانتظار التفريغ (${unlinkedRecordings.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(unlinkedRecordings, key = { it.file.absolutePath }) { rec ->
                        val isPlaying = playingRawPath == rec.file.absolutePath
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilledTonalIconButton(
                                    onClick = { playRawAudio(rec.file) },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlaying) "إيقاف" else "تشغيل",
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rec.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = "${fmt.format(Date(rec.timestampMs))} • ${rec.sizeKb} KB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { onTranscribeRawRecording(rec) }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = "تحليل وتفريغ", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(
                                    onClick = { SystemIntents.shareBinaryFile(context, rec.file, "audio/mp4") },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = "مشاركة")
                                }
                                IconButton(onClick = { onDeleteRawRecording(rec) }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    paymentTargetPhone?.let { targetPhone ->
        UnsavedNumberPaymentPopup(
            phone = targetPhone,
            paymentTemplates = paymentTemplates,
            onDismiss = { paymentTargetPhone = null },
        )
    }
}

@Composable
private fun OpenTaskHubCard(
    record: CallTranscriptRecord,
    item: CallActionItem,
    kind: String,
    formattedCallDate: String,
    onToggleComplete: () -> Unit,
    onExecute: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vault_hub_task_${record.id}_${item.id}"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = onToggleComplete,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckBoxOutlineBlank,
                    contentDescription = "تحديد كمكتمل",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(record.contactName.ifBlank { record.phone.ifBlank { "مكالمة" } })
                        if (item.dueDateIso.isNotBlank()) append(" • ${item.dueDateIso}")
                        else append(" • $formattedCallDate")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SmartActionExecuteButton(
                kind = kind,
                hasPhone = record.phone.isNotBlank() && !PhoneUtils.isUssdOrShortCode(record.phone),
                onExecute = onExecute,
            )
        }
    }
}

@Composable
private fun SmartActionExecuteButton(
    kind: String,
    hasPhone: Boolean,
    onExecute: () -> Unit,
) {
    val (label, icon, tintColor) = when {
        kind == "CALENDAR" -> Triple("للتقويم", Icons.Rounded.CalendarMonth, MaterialTheme.colorScheme.primary)
        kind == "PAYMENT" && hasPhone -> Triple("دفع", Icons.Rounded.Payments, InstaPayPurple)
        else -> Triple("تذكير", Icons.Rounded.NotificationsActive, MaterialTheme.colorScheme.secondary)
    }
    FilledTonalButton(
        onClick = onExecute,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tintColor, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tintColor, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VaultTranscriptCard(
    record: CallTranscriptRecord,
    playableFile: File?,
    isActivePlayer: Boolean,
    onRequestPlayerFocus: () -> Unit,
    formattedDate: String,
    onToggleActionItem: (String) -> Unit,
    onExecuteActionItem: (CallActionItem, String) -> Unit,
    onSaveNotes: () -> Unit,
    onMarkCalendarAdded: () -> Unit,
    onAnalyze: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var showSyncedTranscript by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vault_transcript_card_${record.id}"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.contactName.ifBlank { record.phone.ifBlank { "مكالمة" } },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            if (record.phone.isNotBlank()) append("${record.phone} • ")
                            append(formattedDate)
                            if (record.durationSec > 0L) append(" • ${record.durationSec}ث")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val statusLabel = when (record.status) {
                    "READY" -> "AI جاهز"
                    "PROCESSING", "PENDING_AI" -> "جاري التحليل..."
                    "ERROR" -> "تعذر"
                    else -> "بانتظار التفريغ"
                }
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = if (record.status == "READY") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (record.status == "READY") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            // 3-Bullet Smart Summary
            val bullets = record.summaryBullets.ifEmpty { listOfNotNull(record.oneLineSummary.takeIf(String::isNotBlank)) }
            if (bullets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    bullets.take(3).forEach { line ->
                        Text(
                            text = "• $line",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            if (record.highlights.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    record.highlights.take(5).forEach { tag ->
                        AssistChip(onClick = {}, label = { Text(tag, style = MaterialTheme.typography.labelSmall) })
                    }
                }
            }

            // Interactive Action Items with 1-Tap Execution
            if (record.actionItems.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "مهام المتابعة (${record.actionItems.count { !it.completed }}/${record.actionItems.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    record.actionItems.forEach { item ->
                        val resolvedKind = VaultActionHelper.inferActionKind(item)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleActionItem(item.id) }
                                .padding(vertical = 2.dp)
                                .testTag("vault_action_${record.id}_${item.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = if (item.completed) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                contentDescription = null,
                                tint = if (item.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None,
                                ),
                                color = if (item.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (!item.completed) {
                                SmartActionExecuteButton(
                                    kind = resolvedKind,
                                    hasPhone = record.phone.isNotBlank() && !PhoneUtils.isUssdOrShortCode(record.phone),
                                    onExecute = { onExecuteActionItem(item, resolvedKind) },
                                )
                            }
                        }
                    }
                }
            }

            if (playableFile != null || record.transcript.isNotBlank() || record.segments.isNotEmpty()) {
                TextButton(
                    onClick = { showSyncedTranscript = !showSyncedTranscript },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.testTag("vault_toggle_player_${record.id}"),
                ) {
                    Icon(if (showSyncedTranscript) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (showSyncedTranscript) "إخفاء المشغّل والنص المتزامن"
                        else if (playableFile != null && (record.transcript.isNotBlank() || record.segments.isNotEmpty())) "فتح المشغّل التفاعلي والنص المتزامن"
                        else if (playableFile != null) "تشغيل التسجيل الصوتي"
                        else "عرض النص والمقاطع",
                    )
                }

                AnimatedVisibility(visible = showSyncedTranscript) {
                    InteractiveTranscriptPlayer(
                        record = record,
                        playableFile = playableFile,
                        isActivePlayer = isActivePlayer,
                        onRequestFocus = onRequestPlayerFocus,
                    )
                }
            }

            // Footer Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (record.suggestedEventTitle.isNotBlank() && !record.eventAddedToCalendar) {
                    FilledTonalButton(
                        onClick = {
                            SystemIntents.insertCalendarEvent(
                                context = context,
                                title = record.suggestedEventTitle,
                                description = "${record.contactName} (${record.phone})\n${record.extractedNotes}",
                                startEpochMs = record.suggestedEventEpochMs,
                            )
                            onMarkCalendarAdded()
                        },
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("للتقويم")
                    }
                }
                if (record.extractedNotes.isNotBlank() && !record.notesSavedToContact) {
                    OutlinedButton(onClick = onSaveNotes) {
                        Icon(Icons.Rounded.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("حفظ ملاحظة")
                    }
                }
                if (playableFile != null && record.status != "READY") {
                    OutlinedButton(onClick = onAnalyze) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("تحليل AI")
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Delete, contentDescription = "حذف من الخزنة", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
