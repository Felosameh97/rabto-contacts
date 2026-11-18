package com.example.util

import com.example.domain.model.ContactRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class IsolatedContactJsonDto(
    val id: String = "",
    val displayName: String = "",
    val nickname: String? = null,
    val primaryPhone: String = "",
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val categoryPaths: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val organization: String? = null,
    val jobTitle: String? = null,
    val notes: String? = null,
    val address: String? = null,
    val district: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val telegramHandle: String? = null,
    val website: String? = null,
    val isFavorite: Boolean = false,
    val avatarColorSeed: Int = 0,
    val muted: Boolean = false,
    val blocked: Boolean = false,
    val lastContactedEpochMs: Long = 0L,
) {
    fun toContactRecord(): ContactRecord {
        val mergedPhones = (listOf(primaryPhone) + phones)
            .map { PhoneUtils.normalizePhone(it) }
            .filter { it.isNotBlank() }
            .distinct()
        return ContactRecord(
            id = id,
            displayName = displayName.ifBlank { mergedPhones.firstOrNull().orEmpty() },
            nickname = nickname?.takeIf { it.isNotBlank() },
            primaryPhone = mergedPhones.firstOrNull().orEmpty(),
            phones = mergedPhones,
            emails = emails.filter { it.isNotBlank() }.distinct(),
            categoryPaths = categoryPaths.filter { it.isNotBlank() }.distinct(),
            tags = tags.filter { it.isNotBlank() }.distinct(),
            organization = organization?.takeIf { it.isNotBlank() },
            jobTitle = jobTitle?.takeIf { it.isNotBlank() },
            notes = notes?.takeIf { it.isNotBlank() },
            address = address?.takeIf { it.isNotBlank() },
            district = district?.takeIf { it.isNotBlank() },
            latitude = latitude,
            longitude = longitude,
            telegramHandle = telegramHandle?.takeIf { it.isNotBlank() },
            website = website?.takeIf { it.isNotBlank() },
            photoUri = null,
            syncedToDevice = false,
            isFavorite = isFavorite,
            avatarColorSeed = avatarColorSeed,
            muted = muted,
            blocked = blocked,
            lastContactedEpochMs = lastContactedEpochMs,
        )
    }

    companion object {
        fun fromContactRecord(c: ContactRecord) = IsolatedContactJsonDto(
            id = c.id,
            displayName = c.displayName,
            nickname = c.nickname,
            primaryPhone = c.primaryPhone,
            phones = c.phones,
            emails = c.emails,
            categoryPaths = c.categoryPaths,
            tags = c.tags,
            organization = c.organization,
            jobTitle = c.jobTitle,
            notes = c.notes,
            address = c.address,
            district = c.district,
            latitude = c.latitude,
            longitude = c.longitude,
            telegramHandle = c.telegramHandle,
            website = c.website,
            isFavorite = c.isFavorite,
            avatarColorSeed = c.avatarColorSeed,
            muted = c.muted,
            blocked = c.blocked,
            lastContactedEpochMs = c.lastContactedEpochMs,
        )
    }
}

@Serializable
data class IsolatedContactsBackupJson(
    val version: Int = 1,
    val app: String = "FeloContacts",
    val sandboxIsolated: Boolean = true,
    val exportedAtEpochMs: Long = 0L,
    val count: Int = 0,
    val contacts: List<IsolatedContactJsonDto> = emptyList(),
)

object JsonContactExporter {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun exportToJson(
        contacts: List<ContactRecord>,
        nowMs: Long = System.currentTimeMillis(),
    ): String {
        val dtos = contacts.map { IsolatedContactJsonDto.fromContactRecord(it) }
        val payload = IsolatedContactsBackupJson(
            version = 1,
            app = "FeloContacts",
            sandboxIsolated = true,
            exportedAtEpochMs = nowMs,
            count = dtos.size,
            contacts = dtos,
        )
        return json.encodeToString(IsolatedContactsBackupJson.serializer(), payload)
    }

    fun parseFromJson(rawText: String): Result<List<ContactRecord>> = runCatching {
        val trimmed = rawText.trim()
        require(trimmed.isNotEmpty()) { "ملف JSON فارغ" }
        val dtos = if (trimmed.startsWith("[")) {
            json.decodeFromString<List<IsolatedContactJsonDto>>(trimmed)
        } else {
            json.decodeFromString(IsolatedContactsBackupJson.serializer(), trimmed).contacts
        }
        dtos.map { it.toContactRecord() }
            .filter { it.displayName.isNotBlank() || it.primaryPhone.isNotBlank() || it.emails.isNotEmpty() }
    }
}
