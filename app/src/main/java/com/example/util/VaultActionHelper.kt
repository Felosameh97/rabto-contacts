package com.example.util

import com.example.domain.model.CallActionItem
import com.example.domain.model.CallTranscriptRecord
import java.util.Locale

data class VaultOpenTaskItem(
    val record: CallTranscriptRecord,
    val actionItem: CallActionItem,
    val resolvedKind: String,
)

/**
 * Pure helper functions for cross-call AI Vault action-item aggregation,
 * kind inference, and human-readable local cache formatting.
 */
object VaultActionHelper {

    fun inferActionKind(item: CallActionItem): String {
        val raw = item.kind.trim().uppercase(Locale.US)
        if (raw == "CALENDAR" || raw == "PAYMENT" || raw == "FOLLOW_UP") return raw

        val text = item.title.lowercase(Locale.getDefault())
        return when {
            text.contains("موعد") || text.contains("ميعاد") || text.contains("اجتماع") ||
                text.contains("تقويم") || text.contains("meeting") || text.contains("calendar") ||
                item.dueEpochMs != null -> "CALENDAR"
            text.contains("دفع") || text.contains("تحويل") || text.contains("جنيه") ||
                text.contains("فودافون") || text.contains("انستاباي") || text.contains("حساب") ||
                text.contains("payment") || text.contains("pay") -> "PAYMENT"
            text.contains("اتصال") || text.contains("اكلم") || text.contains("هكلم") ||
                text.contains("متابعة") || text.contains("تذكير") || text.contains("follow") ||
                text.contains("remind") -> "FOLLOW_UP"
            else -> "TASK"
        }
    }

    fun extractOpenActionItems(transcripts: List<CallTranscriptRecord>): List<VaultOpenTaskItem> {
        return transcripts
            .flatMap { rec ->
                rec.actionItems
                    .filter { !it.completed && it.title.isNotBlank() }
                    .map { item ->
                        VaultOpenTaskItem(
                            record = rec,
                            actionItem = item,
                            resolvedKind = inferActionKind(item),
                        )
                    }
            }
            .sortedWith(
                compareBy<VaultOpenTaskItem> { it.actionItem.dueEpochMs ?: Long.MAX_VALUE }
                    .thenByDescending { it.record.timestampMs }
            )
    }

    fun formatBytesReadable(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = bytes / 1024.0
        return if (kb < 1024.0) {
            String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
        } else {
            String.format(Locale.US, "%.1f MB", kb / 1024.0)
        }
    }
}
