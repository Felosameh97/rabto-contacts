package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.CallTranscriptRecord
import com.example.domain.model.CategoryNode
import com.example.domain.model.ContactRecord
import com.example.domain.model.DialHistoryEntry
import com.example.domain.model.PaymentTemplate
import com.example.domain.model.WhatsAppLog

@Entity(
    tableName = "contacts",
    indices = [
        Index(value = ["displayName"]),
        Index(value = ["primaryPhone"]),
        Index(value = ["isFavorite"]),
        Index(value = ["syncedToDevice"]),
        Index(value = ["district"]),
        Index(value = ["updatedAtEpochMs"]),
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
    val syncedToDevice: Boolean,
    val isFavorite: Boolean,
    val searchBlob: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
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
        syncedToDevice = syncedToDevice,
        isFavorite = isFavorite,
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

        fun fromDomain(record: ContactRecord, nowMs: Long = System.currentTimeMillis()): ContactEntity {
            val phones = record.phones.ifEmpty { listOfNotNull(record.primaryPhone.takeIf { it.isNotBlank() }) }
            val phonesJoined = phones.joinToString("\u001F")
            val emailsJoined = record.emails.joinToString("\u001F")
            val catsFlat = record.categoryPaths.joinToString("\u001F")
            val tagsJoined = record.tags.distinct().take(20).joinToString("\u001F")
            val localPhones = phones.map { com.example.util.PhoneUtils.toLocalEgyptianPhone(it) }.distinct()
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
                syncedToDevice = record.syncedToDevice,
                isFavorite = record.isFavorite,
                searchBlob = normalizeForSearch(rawBlob).take(3200),
                createdAtEpochMs = record.createdAt?.toDate()?.time ?: nowMs,
                updatedAtEpochMs = record.updatedAt?.toDate()?.time ?: nowMs,
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
    tableName = "call_transcripts",
    indices = [
        Index(value = ["contactId"]),
        Index(value = ["phone"]),
        Index(value = ["timestampMs"]),
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
        extractedNotes = extractedNotes,
        suggestedEventTitle = suggestedEventTitle,
        suggestedEventDateIso = suggestedEventDateIso,
        suggestedEventEpochMs = suggestedEventEpochMs,
        suggestedCategory = suggestedCategory,
        eventAddedToCalendar = eventAddedToCalendar,
        notesSavedToContact = notesSavedToContact,
        status = status,
    )
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
