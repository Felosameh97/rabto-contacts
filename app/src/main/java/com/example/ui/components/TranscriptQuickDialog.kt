package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.LocalMediaCacheManager
import com.example.domain.model.CallTranscriptRecord
import com.example.util.SystemIntents
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranscriptQuickDialog(
    record: CallTranscriptRecord,
    onDismiss: () -> Unit,
    onToggleActionItem: (String, String) -> Unit,
    onSaveNotes: (CallTranscriptRecord) -> Unit,
    onMarkCalendarAdded: (String) -> Unit,
    onAnalyze: (CallTranscriptRecord) -> Unit,
    onDelete: (String) -> Unit,
) {
    val context = LocalContext.current
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault()) }
    val audioFile = remember(record.audioFilePath, record.audioFileName) {
        LocalMediaCacheManager.resolvePlayableAudioFile(
            context = context,
            audioFilePath = record.audioFilePath,
            audioFileName = record.audioFileName,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = record.contactName.ifBlank { record.phone.ifBlank { "مكالمة مسجلة" } },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        if (record.phone.isNotBlank()) append("${record.phone} • ")
                        if (record.timestampMs > 0L) append(fmt.format(Date(record.timestampMs)))
                        if (record.durationSec > 0L) append(" • ${record.durationSec}ث")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val bullets = record.summaryBullets.ifEmpty {
                    listOfNotNull(record.oneLineSummary.takeIf { it.isNotBlank() })
                }
                if (bullets.isNotEmpty()) {
                    Text(
                        text = "ملخص المكالمة الذكي",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    bullets.take(3).forEach { bullet ->
                        Text(
                            text = "• $bullet",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                if (record.highlights.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        record.highlights.take(6).forEach { tag ->
                            AssistChip(onClick = {}, label = { Text(tag, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                }

                if (record.actionItems.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = "مهام المتابعة (${record.actionItems.count { !it.completed }}/${record.actionItems.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    record.actionItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleActionItem(record.id, item.id) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = if (item.completed) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                contentDescription = if (item.completed) "مكتمل" else "غير مكتمل",
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
                        }
                    }
                }

                if (audioFile != null || record.transcript.isNotBlank() || record.segments.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = "المشغّل التفاعلي والنص المتزامن",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    InteractiveTranscriptPlayer(
                        record = record,
                        playableFile = audioFile,
                        isActivePlayer = true,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                onMarkCalendarAdded(record.id)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("للتقويم", maxLines = 1)
                        }
                    }
                    if (record.extractedNotes.isNotBlank() && !record.notesSavedToContact) {
                        OutlinedButton(
                            onClick = { onSaveNotes(record) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("حفظ ملاحظة", maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (audioFile != null && record.status != "READY") {
                    Button(
                        onClick = { onAnalyze(record) },
                        modifier = Modifier.testTag("transcript_dialog_analyze_btn"),
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("تحليل AI")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("إغلاق")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDelete(record.id)
                    onDismiss()
                },
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(4.dp))
                Text("حذف", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}
