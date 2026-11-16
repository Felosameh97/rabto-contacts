package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val githubRepoSlug: String? = null,
    val lastSyncEpochMs: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toCreateMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("displayName", displayName.ifBlank { "User" }.take(120))
        email?.takeIf { it.isNotBlank() }?.let { put("email", it.take(180)) }
        githubRepoSlug?.takeIf { it.isNotBlank() }?.let { put("githubRepoSlug", it.take(160)) }
        lastSyncEpochMs?.let { put("lastSyncEpochMs", it) }
        put("createdAt", createdAt ?: FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@IgnoreExtraProperties
data class ContactRecord(
    @DocumentId val id: String = "",
    val displayName: String = "",
    val primaryPhone: String = "",
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val categoryPaths: List<String> = emptyList(),
    val organization: String? = null,
    val jobTitle: String? = null,
    val notes: String? = null,
    val address: String? = null,
    val district: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val telegramHandle: String? = null,
    val website: String? = null,
    val syncedToDevice: Boolean = false,
    val isFavorite: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toFirestoreMap(isCreate: Boolean): Map<String, Any> = buildMap {
        put("displayName", displayName.ifBlank { primaryPhone.ifBlank { "Unnamed" } }.take(200))
        put("primaryPhone", primaryPhone.take(60))
        put("phones", phones.distinct().take(10))
        put("emails", emails.distinct().take(5))
        put("categoryPaths", categoryPaths.distinct().take(15))
        organization?.takeIf { it.isNotBlank() }?.let { put("organization", it.take(200)) }
        jobTitle?.takeIf { it.isNotBlank() }?.let { put("jobTitle", it.take(160)) }
        notes?.takeIf { it.isNotBlank() }?.let { put("notes", it.take(4000)) }
        address?.takeIf { it.isNotBlank() }?.let { put("address", it.take(400)) }
        district?.takeIf { it.isNotBlank() }?.let { put("district", it.take(120)) }
        if (latitude != null && longitude != null && latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            put("latitude", latitude)
            put("longitude", longitude)
        }
        telegramHandle?.takeIf { it.isNotBlank() }?.let { put("telegramHandle", it.take(100)) }
        website?.takeIf { it.isNotBlank() }?.let { put("website", it.take(400)) }
        put("syncedToDevice", syncedToDevice)
        put("isFavorite", isFavorite)
        if (isCreate) {
            put("createdAt", FieldValue.serverTimestamp())
        } else if (createdAt != null) {
            put("createdAt", createdAt)
        }
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@IgnoreExtraProperties
data class CategoryNode(
    @DocumentId val id: String = "",
    val name: String = "",
    val parentPath: String = "",
    val fullPath: String = "",
    val depth: Long = 0L,
    val iconKey: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toFirestoreMap(isCreate: Boolean): Map<String, Any> = buildMap {
        put("name", name.ifBlank { "Category" }.take(120))
        put("parentPath", parentPath.take(300))
        put("fullPath", fullPath.ifBlank { name }.take(400))
        put("depth", depth.coerceIn(0L, 10L))
        iconKey?.takeIf { it.isNotBlank() }?.let { put("iconKey", it.take(60)) }
        if (isCreate) {
            put("createdAt", FieldValue.serverTimestamp())
        } else if (createdAt != null) {
            put("createdAt", createdAt)
        }
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@IgnoreExtraProperties
data class WhatsAppLog(
    @DocumentId val id: String = "",
    val contactId: String = "",
    val contactName: String = "",
    val phone: String = "",
    val loggedAtEpochMs: Long = 0L,
    val syncedToPhone: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toFirestoreMap(isCreate: Boolean): Map<String, Any> = buildMap {
        put("contactId", contactId.take(128))
        put("contactName", contactName.ifBlank { phone }.take(200))
        put("phone", phone.take(60))
        put("loggedAtEpochMs", loggedAtEpochMs.coerceAtLeast(0L))
        put("syncedToPhone", syncedToPhone)
        if (isCreate) {
            put("createdAt", FieldValue.serverTimestamp())
        } else if (createdAt != null) {
            put("createdAt", createdAt)
        }
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@IgnoreExtraProperties
data class PaymentTemplate(
    @DocumentId val id: String = "",
    val name: String = "",
    val urlTemplate: String = "",
    val isDefault: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toFirestoreMap(isCreate: Boolean): Map<String, Any> = buildMap {
        put("name", name.ifBlank { "Pay" }.take(80))
        put("urlTemplate", urlTemplate.take(400))
        put("isDefault", isDefault)
        if (isCreate) {
            put("createdAt", FieldValue.serverTimestamp())
        } else if (createdAt != null) {
            put("createdAt", createdAt)
        }
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

data class CallLogEntry(
    val id: String,
    val number: String,
    val cachedName: String?,
    val callType: Int,
    val timestampMs: Long,
    val durationSec: Long,
    val callCount: Int,
    val matchedContact: ContactRecord? = null,
)

data class CategorySummary(
    val name: String,
    val fullPath: String,
    val parentPath: String,
    val depth: Int,
    val contactCount: Int,
    val childCount: Int,
)

data class GitHubReleaseInfo(
    val tagName: String,
    val releaseName: String,
    val body: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val publishedAt: String,
    val isUpdateAvailable: Boolean,
)

data class CsvImportSummary(
    val importedCount: Int,
    val skippedCount: Int,
    val categoriesCreated: Int,
    val geoMatchedCount: Int,
)
