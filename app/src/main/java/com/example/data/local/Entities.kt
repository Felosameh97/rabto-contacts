package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.CallActionItem
import com.example.domain.model.CallLogEntry
import com.example.domain.model.CallTranscriptRecord
import com.example.domain.model.CategoryNode
import com.example.domain.model.CloudCallLogRecord
import com.example.domain.model.CloudCallTranscriptRecord
import com.example.domain.model.ContactRecord
import com.example.domain.model.DialHistoryEntry
import com.example.domain.model.PaymentTemplate
import com.example.domain.model.SyncState
import com.example.domain.model.TranscriptSegment
import com.example.domain.model.WhatsAppLog
import com.example.util.PhoneUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.abs

object TranscriptConverters {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeSegments(segments: List<TranscriptSegment>): String {
        if (segments.isEmpty()) return "[]"
        return runCatching { json.encodeToString(segments) }.getOrDefault("[]")
    }

    fun decodeSegments(raw: String): List<TranscriptSegment> {
        if (raw.isBlank() || raw == "[]") return emptyList()
        return runCatching { json.decodeFromString<List<TranscriptSegment>>(raw) }.getOrDefault(emptyList())
    }

    fun encodeActionItems(items: List<CallActionItem>): String {
        if (items.isEmpty()) return "[]"
        return runCatching { json.encodeToString(items) }.getOrDefault("[]")
    }

    fun decodeActionItems(raw: String): List<CallActionItem> {
        if (raw.isBlank() || raw == "[]") return emptyList()
        return runCatching { json.decodeFromString<List<CallActionItem>>(raw) }.getOrDefault(emptyList())
    }
}

@Entity(
    tableName = "contacts",
    indices = [
        Index(value = ["displayName"]),
        Index(value = ["primaryPhone"]),
        Index(value = ["isFavorite"]),
        Index(value = ["syncedToDevice"]),
        Index(value = ["district"]),
        Index(value = ["updatedAtEpochMs"]),
        Index(value = ["isDeleted"]),
        Index(value = ["syncState"]),
        Index(value = ["isBlocked"]),
        Index(value = ["lastContactedEpochMs"]),
    ]
)
data class ContactEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val nickname: String? = null,
    val primaryPhone: String,
    val phonesJoined: String,
    val emailsJoined: String,
    val categoryPathsFlat: String,
    val tagsJoined: String = "",
    val organization: String?,
    val jobTitle: String?,
    val notes: String?,
    val address: String?,
    val district: String?,
    val latitude: Double?,
    val longitude: Double?,
    val telegramHandle: String?,
    val website: String?,
    val photoUri: String? = null,
    val syncedToDevice: Boolean = false,
    val isFavorite: Boolean = false,
    val searchBlob: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val avatarColorSeed: Int = 0,
    val isMuted: Boolean = false,
    val isBlocked: Boolean = false,
    val lastContactedEpochMs: Long = 0L,
    val syncState: String = SyncState.SYNCED.name,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null,
) {
    fun toDomain(): ContactRecord = ContactRecord(
        id = id,
        displayName = displayName,
        nickname = nickname,
        primaryPhone = primaryPhone,
        phones = phonesJoined.split("\u001F").filter { it.isNotBlank() },
        emails = emailsJoined.split("\u001F").filter { it.isNotBlank() },
        categoryPaths = categoryPathsFlat.split("\u001F").filter { it.isNotBlank() },
        tags = tagsJoined.split("\u001F").filter { it.isNotBlank() },
        organization = organization,
        jobTitle = jobTitle,
        notes = notes,
        address = address,
        district = district,
        latitude = latitude,
        longitude = longitude,
        telegramHandle = telegramHandle,
        website = website,
        photoUri = photoUri,
        // Enforce sandbox isolation invariant: contacts never advertise device phonebook sync
        syncedToDevice = false,
        isFavorite = isFavorite,
        avatarColorSeed = avatarColorSeed,
        muted = isMuted,
        blocked = isBlocked,
        lastContactedEpochMs = lastContactedEpochMs,
        clientUpdatedAtMs = updatedAtEpochMs,
    )

    companion object {
        fun normalizeForSearch(raw: String): String {
            return raw.lowercase()
                .replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
                .replace('أ', 'ا')
                .replace('إ', 'ا')
                .replace('آ', 'ا')
                .replace('ى', 'ي')
                .replace('ة', 'ه')
                .replace('ؤ', 'و')
                .replace('ئ', 'ي')
        }

        fun deterministicColorSeed(id: String, name: String): Int {
            val raw = (id.ifBlank { name }).hashCode()
            return abs(raw) % 1000
        }

        fun fromDomain(
            record: ContactRecord,
            nowMs: Long = System.currentTimeMillis(),
            syncState: SyncState = SyncState.SYNCED,
            isDeleted: Boolean = false,
            deletedAtEpochMs: Long? = null,
        ): ContactEntity {
            val phones = record.phones.ifEmpty { listOfNotNull(record.primaryPhone.takeIf { it.isNotBlank() }) }
            val phonesJoined = phones.joinToString("\u001F")
            val emailsJoined = record.emails.joinToString("\u001F")
            val catsFlat = record.categoryPaths.joinToString("\u001F")
            val tagsJoined = record.tags.distinct().take(20).joinToString("\u001F")
            val localPhones = phones.map { PhoneUtils.toLocalEgyptianPhone(it) }.distinct()
            val rawBlob = buildString {
                append(record.displayName).append(' ')
                record.nickname?.let { append(it).append(' ') }
                append(phones.joinToString(" ")).append(' ')
                append(localPhones.joinToString(" ")).append(' ')
                append(record.emails.joinToString(" ")).append(' ')
                append(record.tags.joinToString(" ")).append(' ')
                append(record.categoryPaths.joinToString(" ")).append(' ')
                record.organization?.let { append(it).append(' ') }
                record.jobTitle?.let { append(it).append(' ') }
                record.district?.let { append(it).append(' ') }
                record.address?.let { append(it).append(' ') }
                record.notes?.let { append(it) }
            }
            val resolvedUpdatedMs = when {
                record.clientUpdatedAtMs > 0L -> record.clientUpdatedAtMs
                record.updatedAt != null -> record.updatedAt.toDate().time
                else -> nowMs
            }
            val colorSeed = if (record.avatarColorSeed > 0) {
                record.avatarColorSeed
            } else {
                deterministicColorSeed(record.id, record.displayName)
            }
            return ContactEntity(
                id = record.id,
                displayName = record.displayName,
                nickname = record.nickname?.takeIf { it.isNotBlank() },
                primaryPhone = record.primaryPhone.ifBlank { phones.firstOrNull().orEmpty() },
                phonesJoined = phonesJoined,
                emailsJoined = emailsJoined,
                categoryPathsFlat = catsFlat,
                tagsJoined = tagsJoined,
                organization = record.organization,
                jobTitle = record.jobTitle,
                notes = record.notes,
                address = record.address,
                district = record.district,
                latitude = record.latitude,
                longitude = record.longitude,
                telegramHandle = record.telegramHandle,
                website = record.website,
                photoUri = record.photoUri?.takeIf { it.isNotBlank() },
                syncedToDevice = false,
                isFavorite = record.isFavorite,
                searchBlob = normalizeForSearch(rawBlob).take(3200),
                createdAtEpochMs = record.createdAt?.toDate()?.time ?: nowMs,
                updatedAtEpochMs = resolvedUpdatedMs,
                avatarColorSeed = colorSeed,
                isMuted = record.muted,
                isBlocked = record.blocked,
                lastContactedEpochMs = record.lastContactedEpochMs,
                syncState = syncState.name,
                isDeleted = isDeleted,
                deletedAtEpochMs = deletedAtEpochMs,
            )
        }
    }
}

@Entity(
    tableName = "contact_categories",
    primaryKeys = ["contactId", "categoryPath"],
    indices = [
        Index(value = ["categoryPath"]),
        Index(value = ["contactId"]),
    ]
)
data class ContactCategoryCrossRef(
    val contactId: String,
    val categoryPath: String,
)

@Entity(
    tableName = "phone_lookup",
    primaryKeys = ["phoneKey", "contactId"],
    indices = [
        Index(value = ["phoneKey"]),
        Index(value = ["contactId"]),
    ]
)
data class PhoneLookupEntity(
    val phoneKey: String,
    val contactId: String,
)

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["parentPath"]),
        Index(value = ["fullPath"], unique = true),
    ]
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentPath: String,
    val fullPath: String,
    val depth: Int,
    val iconKey: String?,
) {
    fun toDomain() = CategoryNode(
        id = id,
        name = name,
        parentPath = parentPath,
        fullPath = fullPath,
        depth = depth.toLong(),
        iconKey = iconKey,
    )
}

@Entity(
    tableName = "whatsapp_logs",
    indices = [
        Index(value = ["contactId"]),
        Index(value = ["loggedAtEpochMs"]),
    ]
)
data class WhatsAppLogEntity(
    @PrimaryKey val id: String,
    val contactId: String,
    val contactName: String,
    val phone: String,
    val loggedAtEpochMs: Long,
    val syncedToPhone: Boolean,
) {
    fun toDomain() = WhatsAppLog(
        id = id,
        contactId = contactId,
        contactName = contactName,
        phone = phone,
        loggedAtEpochMs = loggedAtEpochMs,
        syncedToPhone = syncedToPhone,
    )
}

@Entity(tableName = "payment_templates")
data class PaymentTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val urlTemplate: String,
    val isDefault: Boolean,
) {
    fun toDomain() = PaymentTemplate(
        id = id,
        name = name,
        urlTemplate = urlTemplate,
        isDefault = isDefault,
    )
}

@Entity(
    tableName = "app_call_logs",
    indices = [
        Index(value = ["contactId"]),
        Index(value = ["phoneKey"]),
        Index(value = ["timestampMs"]),
        Index(value = ["isDeleted"]),
        Index(value = ["syncState"]),
    ]
)
data class AppCallLogEntity(
    @PrimaryKey val id: String,
    val contactId: String? = null,
    val phoneNumber: String,
    val phoneKey: String,
    val cachedName: String? = null,
    val callType: Int,
    val simSlot: Int? = null,
    val timestampMs: Long,
    val durationSec: Long,
    val transcriptId: String? = null,
    val hasRecording: Boolean = false,
    val notes: String? = null,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val syncState: String = SyncState.PENDING.name,
) {
    fun toDomain(matchedContact: ContactRecord? = null, callCount: Int = 1) = CallLogEntry(
        id = id,
        number = phoneNumber,
        cachedName = cachedName,
        callType = callType,
        timestampMs = timestampMs,
        durationSec = durationSec,
        callCount = callCount,
        matchedContact = matchedContact,
        simSlot = simSlot,
        transcriptId = transcriptId,
        hasRecording = hasRecording,
        notes = notes,
    )

    fun toCloudModel() = CloudCallLogRecord(
        id = id,
        contactId = contactId,
        phoneNumber = phoneNumber,
        cachedName = cachedName,
        callType = callType.toLong(),
        simSlot = simSlot?.toLong(),
        timestampMs = timestampMs,
        durationSec = durationSec,
        transcriptId = transcriptId,
        hasRecording = hasRecording,
        notes = notes,
        clientUpdatedAtMs = updatedAtEpochMs,
    )

    companion object {
        fun fromCloud(cloud: CloudCallLogRecord): AppCallLogEntity {
            val phoneKey = PhoneUtils.phoneSearchKey(cloud.phoneNumber).ifBlank { cloud.phoneNumber }
            return AppCallLogEntity(
                id = cloud.id,
                contactId = cloud.contactId,
                phoneNumber = cloud.phoneNumber,
                phoneKey = phoneKey,
                cachedName = cloud.cachedName,
                callType = cloud.callType.toInt(),
                simSlot = cloud.simSlot?.toInt(),
                timestampMs = cloud.timestampMs,
                durationSec = cloud.durationSec,
                transcriptId = cloud.transcriptId,
                hasRecording = cloud.hasRecording,
                notes = cloud.notes,
                isDeleted = false,
                deletedAtEpochMs = null,
                updatedAtEpochMs = cloud.clientUpdatedAtMs.takeIf { it > 0L }
                    ?: cloud.updatedAt?.toDate()?.time
                    ?: cloud.timestampMs,
                syncState = SyncState.SYNCED.name,
            )
        }
    }
}

@Entity(
    tableName = "call_transcripts",
    indices = [
        Index(value = ["contactId"]),
        Index(value = ["phone"]),
        Index(value = ["timestampMs"]),
        Index(value = ["isDeleted"]),
        Index(value = ["syncState"]),
    ]
)
data class CallTranscriptEntity(
    @PrimaryKey val id: String,
    val contactId: String?,
    val phone: String,
    val contactName: String,
    val audioFilePath: String,
    val timestampMs: Long,
    val durationSec: Long,
    val transcript: String = "",
    val highlightsJoined: String = "",
    val extractedNotes: String = "",
    val suggestedEventTitle: String = "",
    val suggestedEventDateIso: String = "",
    val suggestedEventEpochMs: Long? = null,
    val suggestedCategory: String = "",
    val eventAddedToCalendar: Boolean = false,
    val notesSavedToContact: Boolean = false,
    val status: String = "PENDING",
    val summaryBulletsJoined: String = "",
    val oneLineSummary: String = "",
    val segmentsJson: String = "[]",
    val actionItemsJson: String = "[]",
    val audioFileName: String = "",
    val audioSizeBytes: Long = 0L,
    val audioCachedLocally: Boolean = false,
    val updatedAtEpochMs: Long = 0L,
    val syncState: String = SyncState.PENDING.name,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null,
) {
    fun toDomain() = CallTranscriptRecord(
        id = id,
        contactId = contactId,
        phone = phone,
        contactName = contactName,
        audioFilePath = audioFilePath,
        timestampMs = timestampMs,
        durationSec = durationSec,
        transcript = transcript,
        highlights = highlightsJoined.split("\u001F").filter { it.isNotBlank() },
        summaryBullets = summaryBulletsJoined.split("\u001F").filter { it.isNotBlank() },
        oneLineSummary = oneLineSummary,
        segments = TranscriptConverters.decodeSegments(segmentsJson),
        actionItems = TranscriptConverters.decodeActionItems(actionItemsJson),
        extractedNotes = extractedNotes,
        suggestedEventTitle = suggestedEventTitle,
        suggestedEventDateIso = suggestedEventDateIso,
        suggestedEventEpochMs = suggestedEventEpochMs,
        suggestedCategory = suggestedCategory,
        eventAddedToCalendar = eventAddedToCalendar,
        notesSavedToContact = notesSavedToContact,
        status = status,
        audioFileName = audioFileName.ifBlank { File(audioFilePath).name },
        audioSizeBytes = audioSizeBytes,
        audioCachedLocally = audioCachedLocally,
        updatedAtEpochMs = updatedAtEpochMs,
        syncState = syncState,
    )

    fun toCloudModel() = CloudCallTranscriptRecord(
        id = id,
        contactId = contactId,
        phone = phone,
        contactName = contactName,
        audioFileName = audioFileName.ifBlank { File(audioFilePath).name },
        audioSizeBytes = audioSizeBytes,
        timestampMs = timestampMs,
        durationSec = durationSec,
        transcript = transcript,
        highlights = highlightsJoined.split("\u001F").filter { it.isNotBlank() },
        summaryBullets = summaryBulletsJoined.split("\u001F").filter { it.isNotBlank() },
        oneLineSummary = oneLineSummary,
        segmentsJson = segmentsJson,
        actionItemsJson = actionItemsJson,
        extractedNotes = extractedNotes,
        suggestedEventTitle = suggestedEventTitle,
        suggestedEventDateIso = suggestedEventDateIso,
        suggestedEventEpochMs = suggestedEventEpochMs,
        suggestedCategory = suggestedCategory,
        eventAdded = eventAddedToCalendar,
        notesSaved = notesSavedToContact,
        status = status,
        clientUpdatedAtMs = updatedAtEpochMs,
    )

    companion object {
        fun fromDomain(
            record: CallTranscriptRecord,
            nowMs: Long = System.currentTimeMillis(),
            syncState: SyncState = SyncState.valueOf(
                SyncState.entries.firstOrNull { it.name == record.syncState }?.name ?: SyncState.PENDING.name
            ),
            isDeleted: Boolean = false,
            deletedAtEpochMs: Long? = null,
        ): CallTranscriptEntity {
            val resolvedFileName = record.audioFileName.ifBlank {
                record.audioFilePath.takeIf { it.isNotBlank() }?.let { File(it).name }.orEmpty()
            }
            val localFile = record.audioFilePath.takeIf { it.isNotBlank() }?.let(::File)
            val cached = record.audioCachedLocally || (localFile != null && localFile.exists() && localFile.length() > 256L)
            val sizeBytes = if (record.audioSizeBytes > 0L) record.audioSizeBytes else (localFile?.takeIf { it.exists() }?.length() ?: 0L)
            return CallTranscriptEntity(
                id = record.id,
                contactId = record.contactId,
                phone = record.phone,
                contactName = record.contactName,
                audioFilePath = record.audioFilePath,
                timestampMs = record.timestampMs,
                durationSec = record.durationSec,
                transcript = record.transcript,
                highlightsJoined = record.highlights.joinToString("\u001F"),
                extractedNotes = record.extractedNotes,
                suggestedEventTitle = record.suggestedEventTitle,
                suggestedEventDateIso = record.suggestedEventDateIso,
                suggestedEventEpochMs = record.suggestedEventEpochMs,
                suggestedCategory = record.suggestedCategory,
                eventAddedToCalendar = record.eventAddedToCalendar,
                notesSavedToContact = record.notesSavedToContact,
                status = record.status,
                summaryBulletsJoined = record.summaryBullets.joinToString("\u001F"),
                oneLineSummary = record.oneLineSummary,
                segmentsJson = TranscriptConverters.encodeSegments(record.segments),
                actionItemsJson = TranscriptConverters.encodeActionItems(record.actionItems),
                audioFileName = resolvedFileName,
                audioSizeBytes = sizeBytes,
                audioCachedLocally = cached,
                updatedAtEpochMs = record.updatedAtEpochMs.takeIf { it > 0L } ?: nowMs,
                syncState = syncState.name,
                isDeleted = isDeleted,
                deletedAtEpochMs = deletedAtEpochMs,
            )
        }

        fun fromCloud(
            cloud: CloudCallTranscriptRecord,
            existingLocalPath: String? = null,
            audioCachedLocally: Boolean = false,
        ): CallTranscriptEntity {
            val resolvedUpdatedMs = cloud.clientUpdatedAtMs.takeIf { it > 0L }
                ?: cloud.updatedAt?.toDate()?.time
                ?: cloud.timestampMs
            return CallTranscriptEntity(
                id = cloud.id,
                contactId = cloud.contactId,
                phone = cloud.phone,
                contactName = cloud.contactName,
                audioFilePath = existingLocalPath.orEmpty(),
                timestampMs = cloud.timestampMs,
                durationSec = cloud.durationSec,
                transcript = cloud.transcript,
                highlightsJoined = cloud.highlights.joinToString("\u001F"),
                extractedNotes = cloud.extractedNotes,
                suggestedEventTitle = cloud.suggestedEventTitle,
                suggestedEventDateIso = cloud.suggestedEventDateIso,
                suggestedEventEpochMs = cloud.suggestedEventEpochMs,
                suggestedCategory = cloud.suggestedCategory,
                eventAddedToCalendar = cloud.eventAdded,
                notesSavedToContact = cloud.notesSaved,
                status = cloud.status,
                summaryBulletsJoined = cloud.summaryBullets.joinToString("\u001F"),
                oneLineSummary = cloud.oneLineSummary,
                segmentsJson = cloud.segmentsJson.ifBlank { "[]" },
                actionItemsJson = cloud.actionItemsJson.ifBlank { "[]" },
                audioFileName = cloud.audioFileName,
                audioSizeBytes = cloud.audioSizeBytes,
                audioCachedLocally = audioCachedLocally,
                updatedAtEpochMs = resolvedUpdatedMs,
                syncState = SyncState.SYNCED.name,
                isDeleted = false,
                deletedAtEpochMs = null,
            )
        }
    }
}

@Entity(
    tableName = "dial_history",
    indices = [
        Index(value = ["typedAtEpochMs"]),
        Index(value = ["isDeleted"]),
        Index(value = ["phoneNumber"]),
    ]
)
data class DialHistoryEntity(
    @PrimaryKey val id: String,
    val phoneNumber: String,
    val matchedContactId: String?,
    val matchedName: String?,
    val didCall: Boolean,
    val typedAtEpochMs: Long,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null,
) {
    fun toDomain() = DialHistoryEntry(
        id = id,
        phoneNumber = phoneNumber,
        matchedContactId = matchedContactId,
        matchedName = matchedName,
        didCall = didCall,
        typedAtEpochMs = typedAtEpochMs,
        isDeleted = isDeleted,
        deletedAtEpochMs = deletedAtEpochMs,
    )
}

@Entity(
    tableName = "pending_sync_queue",
    indices = [
        Index(value = ["entityType"]),
        Index(value = ["enqueuedAtEpochMs"]),
    ]
)
data class PendingSyncEntity(
    @PrimaryKey val queueKey: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val enqueuedAtEpochMs: Long,
    val entityUpdatedAtEpochMs: Long,
    val retryCount: Int = 0,
    val lastError: String? = null,
) {
    companion object {
        fun keyFor(entityType: String, entityId: String): String = "$entityType:$entityId"
    }
}
