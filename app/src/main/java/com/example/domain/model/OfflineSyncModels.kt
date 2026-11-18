package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.serialization.Serializable

enum class SyncState {
    PENDING,
    SYNCED,
    CONFLICT,
    FAILED,
}

enum class SyncEntityType {
    CONTACT,
    CALL_LOG,
    CALL_TRANSCRIPT,
    CATEGORY,
    PAYMENT_TEMPLATE,
    WHATSAPP_LOG,
}

enum class SyncOperation {
    UPSERT,
    DELETE,
}

enum class SyncStatusBadge {
    OFFLINE,
    IDLE,
    SYNCING,
    SYNCED,
    ERROR,
}

data class SyncEngineState(
    val status: SyncStatusBadge = SyncStatusBadge.IDLE,
    val pendingCount: Int = 0,
    val lastSyncEpochMs: Long = 0L,
    val isOnline: Boolean = true,
    val lastError: String? = null,
)

@Serializable
data class TranscriptSegment(
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val speaker: String = "المتصل",
    val text: String = "",
)

@Serializable
data class CallActionItem(
    val id: String = "",
    val title: String = "",
    val dueDateIso: String = "",
    val dueEpochMs: Long? = null,
    val kind: String = "TASK",
    val completed: Boolean = false,
)

data class PreCallContext(
    val contactId: String?,
    val phoneNumber: String,
    val lastSpokeEpochMs: Long?,
    val lastCallDurationSec: Long,
    val lastCallType: Int?,
    val oneLineSummary: String?,
    val pendingActionItemsCount: Int,
    val totalCallsCount: Int,
)

data class GlobalSearchResult(
    val contacts: List<ContactRecord> = emptyList(),
    val matchedTranscripts: List<CallTranscriptRecord> = emptyList(),
    val matchedCallLogs: List<CallLogEntry> = emptyList(),
)

data class LocalMediaCacheStats(
    val recordingFilesCount: Int = 0,
    val recordingBytes: Long = 0L,
    val photoFilesCount: Int = 0,
    val photoBytes: Long = 0L,
    val transcriptCacheCount: Int = 0,
) {
    val totalBytes: Long get() = recordingBytes + photoBytes
}

@IgnoreExtraProperties
data class CloudCallLogRecord(
    @DocumentId val id: String = "",
    val contactId: String? = null,
    val phoneNumber: String = "",
    val cachedName: String? = null,
    val callType: Long = 2L,
    val simSlot: Long? = null,
    val timestampMs: Long = 0L,
    val durationSec: Long = 0L,
    val transcriptId: String? = null,
    val hasRecording: Boolean = false,
    val notes: String? = null,
    val clientUpdatedAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toFirestoreMap(isCreate: Boolean): Map<String, Any> = buildMap {
        contactId?.takeIf { it.isNotBlank() }?.let { put("contactId", it.take(128)) }
        put("phoneNumber", phoneNumber.take(60))
        cachedName?.takeIf { it.isNotBlank() }?.let { put("cachedName", it.take(200)) }
        put("callType", callType.coerceIn(1L, 10L))
        simSlot?.takeIf { it in 0L..4L }?.let { put("simSlot", it) }
        put("timestampMs", timestampMs.coerceAtLeast(0L))
        put("durationSec", durationSec.coerceAtLeast(0L))
        transcriptId?.takeIf { it.isNotBlank() }?.let { put("transcriptId", it.take(128)) }
        put("hasRecording", hasRecording)
        notes?.takeIf { it.isNotBlank() }?.let { put("notes", it.take(2000)) }
        put("clientUpdatedAtMs", clientUpdatedAtMs.coerceAtLeast(0L))
        if (isCreate) {
            put("createdAt", FieldValue.serverTimestamp())
        } else if (createdAt != null) {
            put("createdAt", createdAt)
        }
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@IgnoreExtraProperties
data class CloudCallTranscriptRecord(
    @DocumentId val id: String = "",
    val contactId: String? = null,
    val phone: String = "",
    val contactName: String = "",
    val audioFileName: String = "",
    val audioSizeBytes: Long = 0L,
    val timestampMs: Long = 0L,
    val durationSec: Long = 0L,
    val transcript: String = "",
    val highlights: List<String> = emptyList(),
    val summaryBullets: List<String> = emptyList(),
    val oneLineSummary: String = "",
    val segmentsJson: String = "",
    val actionItemsJson: String = "",
    val extractedNotes: String = "",
    val suggestedEventTitle: String = "",
    val suggestedEventDateIso: String = "",
    val suggestedEventEpochMs: Long? = null,
    val suggestedCategory: String = "",
    val eventAdded: Boolean = false,
    val notesSaved: Boolean = false,
    val status: String = "PENDING",
    val clientUpdatedAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toFirestoreMap(isCreate: Boolean): Map<String, Any> = buildMap {
        contactId?.takeIf { it.isNotBlank() }?.let { put("contactId", it.take(128)) }
        put("phone", phone.take(60))
        put("contactName", contactName.ifBlank { phone.ifBlank { "Call" } }.take(200))
        if (audioFileName.isNotBlank()) put("audioFileName", audioFileName.take(200))
        put("audioSizeBytes", audioSizeBytes.coerceAtLeast(0L))
        put("timestampMs", timestampMs.coerceAtLeast(0L))
        put("durationSec", durationSec.coerceAtLeast(0L))
        if (transcript.isNotBlank()) put("transcript", transcript.take(20000))
        put("highlights", highlights.distinct().take(10))
        put("summaryBullets", summaryBullets.distinct().take(5))
        if (oneLineSummary.isNotBlank()) put("oneLineSummary", oneLineSummary.take(400))
        if (segmentsJson.isNotBlank()) put("segmentsJson", segmentsJson.take(40000))
        if (actionItemsJson.isNotBlank()) put("actionItemsJson", actionItemsJson.take(10000))
        if (extractedNotes.isNotBlank()) put("extractedNotes", extractedNotes.take(4000))
        if (suggestedEventTitle.isNotBlank()) put("suggestedEventTitle", suggestedEventTitle.take(200))
        if (suggestedEventDateIso.isNotBlank()) put("suggestedEventDateIso", suggestedEventDateIso.take(60))
        suggestedEventEpochMs?.takeIf { it >= 0L }?.let { put("suggestedEventEpochMs", it) }
        if (suggestedCategory.isNotBlank()) put("suggestedCategory", suggestedCategory.take(300))
        put("eventAdded", eventAdded)
        put("notesSaved", notesSaved)
        put("status", status.ifBlank { "PENDING" }.take(32))
        put("clientUpdatedAtMs", clientUpdatedAtMs.coerceAtLeast(0L))
        if (isCreate) {
            put("createdAt", FieldValue.serverTimestamp())
        } else if (createdAt != null) {
            put("createdAt", createdAt)
        }
        put("updatedAt", FieldValue.serverTimestamp())
    }
}
