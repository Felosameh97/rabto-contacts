package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.CategoryNode
import com.example.domain.model.ContactRecord
import com.example.domain.model.PaymentTemplate
import com.example.domain.model.WhatsAppLog

@Entity(
    tableName = "contacts",
    indices = [
        Index(value = ["displayName"]),
        Index(value = ["primaryPhone"]),
        Index(value = ["searchBlob"]),
        Index(value = ["categoryPathsFlat"]),
        Index(value = ["district"]),
        Index(value = ["updatedAtEpochMs"]),
    ]
)
data class ContactEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val primaryPhone: String,
    val phonesJoined: String,
    val emailsJoined: String,
    val categoryPathsFlat: String,
    val organization: String?,
    val jobTitle: String?,
    val notes: String?,
    val address: String?,
    val district: String?,
    val latitude: Double?,
    val longitude: Double?,
    val telegramHandle: String?,
    val website: String?,
    val syncedToDevice: Boolean,
    val isFavorite: Boolean,
    val searchBlob: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    fun toDomain(): ContactRecord = ContactRecord(
        id = id,
        displayName = displayName,
        primaryPhone = primaryPhone,
        phones = phonesJoined.split("\u001F").filter { it.isNotBlank() },
        emails = emailsJoined.split("\u001F").filter { it.isNotBlank() },
        categoryPaths = categoryPathsFlat.split("\u001F").filter { it.isNotBlank() },
        organization = organization,
        jobTitle = jobTitle,
        notes = notes,
        address = address,
        district = district,
        latitude = latitude,
        longitude = longitude,
        telegramHandle = telegramHandle,
        website = website,
        syncedToDevice = syncedToDevice,
        isFavorite = isFavorite,
    )

    companion object {
        fun fromDomain(record: ContactRecord, nowMs: Long = System.currentTimeMillis()): ContactEntity {
            val phones = record.phones.ifEmpty { listOfNotNull(record.primaryPhone.takeIf { it.isNotBlank() }) }
            val phonesJoined = phones.joinToString("\u001F")
            val emailsJoined = record.emails.joinToString("\u001F")
            val catsFlat = record.categoryPaths.joinToString("\u001F")
            val blob = buildString {
                append(record.displayName.lowercase()).append(' ')
                append(phones.joinToString(" ")).append(' ')
                append(record.emails.joinToString(" ").lowercase()).append(' ')
                append(record.categoryPaths.joinToString(" ").lowercase()).append(' ')
                record.organization?.let { append(it.lowercase()).append(' ') }
                record.district?.let { append(it.lowercase()).append(' ') }
                record.address?.let { append(it.lowercase()).append(' ') }
                record.notes?.let { append(it.lowercase()) }
            }
            return ContactEntity(
                id = record.id,
                displayName = record.displayName,
                primaryPhone = record.primaryPhone.ifBlank { phones.firstOrNull().orEmpty() },
                phonesJoined = phonesJoined,
                emailsJoined = emailsJoined,
                categoryPathsFlat = catsFlat,
                organization = record.organization,
                jobTitle = record.jobTitle,
                notes = record.notes,
                address = record.address,
                district = record.district,
                latitude = record.latitude,
                longitude = record.longitude,
                telegramHandle = record.telegramHandle,
                website = record.website,
                syncedToDevice = record.syncedToDevice,
                isFavorite = record.isFavorite,
                searchBlob = blob.take(2000),
                createdAtEpochMs = record.createdAt?.toDate()?.time ?: nowMs,
                updatedAtEpochMs = record.updatedAt?.toDate()?.time ?: nowMs,
            )
        }
    }
}

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
