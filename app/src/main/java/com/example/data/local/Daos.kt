package com.example.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.domain.model.ContactRecord
import com.example.domain.model.SyncState
import com.example.util.PhoneUtils
import kotlinx.coroutines.flow.Flow

data class CategoryCountTuple(
    val categoryPath: String,
    val cnt: Int,
)

data class PhoneLookupMatch(
    val phoneKey: String,
    @Embedded val contact: ContactEntity,
)

/**
 * Lightweight projection for displaying 25,000+ contacts smoothly without loading the heavy
 * searchBlob column into SQLite CursorWindow memory.
 */
data class ContactListRow(
    val id: String,
    val displayName: String,
    val nickname: String?,
    val primaryPhone: String,
    val phonesJoined: String,
    val emailsJoined: String,
    val categoryPathsFlat: String,
    val tagsJoined: String,
    val organization: String?,
    val jobTitle: String?,
    val notes: String?,
    val address: String?,
    val district: String?,
    val latitude: Double?,
    val longitude: Double?,
    val telegramHandle: String?,
    val website: String?,
    val photoUri: String?,
    val isFavorite: Boolean,
    val updatedAtEpochMs: Long,
    val avatarColorSeed: Int,
    val isMuted: Boolean,
    val isBlocked: Boolean,
    val lastContactedEpochMs: Long,
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
        syncedToDevice = false,
        isFavorite = isFavorite,
        avatarColorSeed = avatarColorSeed,
        muted = isMuted,
        blocked = isBlocked,
        lastContactedEpochMs = lastContactedEpochMs,
        clientUpdatedAtMs = updatedAtEpochMs,
    )
}

@Dao
interface ContactDao {

    @Query("SELECT COUNT(*) FROM contacts WHERE isDeleted = 0")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM contacts WHERE isDeleted = 0")
    suspend fun getTotalCount(): Int

    @Query("SELECT * FROM contacts WHERE isDeleted = 0 ORDER BY displayName COLLATE NOCASE ASC")
    fun observeAllAlphabetical(): Flow<List<ContactEntity>>

    @Query(
        """
        SELECT id, displayName, nickname, primaryPhone, phonesJoined, emailsJoined,
               categoryPathsFlat, tagsJoined, organization, jobTitle, notes, address,
               district, latitude, longitude, telegramHandle, website, photoUri,
               isFavorite, updatedAtEpochMs, avatarColorSeed, isMuted, isBlocked, lastContactedEpochMs
        FROM contacts
        WHERE isDeleted = 0
        ORDER BY displayName COLLATE NOCASE ASC
        """
    )
    fun observeAllAlphabeticalLight(): Flow<List<ContactListRow>>

    @Query("SELECT * FROM contacts WHERE isDeleted = 0 AND isFavorite = 1 ORDER BY displayName COLLATE NOCASE ASC LIMIT 60")
    fun observeFavorites(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE isDeleted = 0 AND isFavorite = 1 ORDER BY displayName COLLATE NOCASE ASC LIMIT :limit")
    suspend fun getFavoritesOnce(limit: Int = 8): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE isDeleted = 0 AND isBlocked = 1 ORDER BY displayName COLLATE NOCASE ASC")
    fun observeBlockedContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE isDeleted = 0 ORDER BY updatedAtEpochMs DESC LIMIT :limit OFFSET :offset")
    suspend fun getRecentOrAllPage(limit: Int = 200, offset: Int = 0): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getById(id: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getByIdEvenIfDeleted(id: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE id IN (:ids) AND isDeleted = 0")
    suspend fun getByIds(ids: List<String>): List<ContactEntity>

    suspend fun getByIdsChunked(ids: List<String>): List<ContactEntity> {
        if (ids.isEmpty()) return emptyList()
        if (ids.size <= 450) return getByIds(ids)
        val out = ArrayList<ContactEntity>(ids.size)
        for (chunk in ids.chunked(450)) {
            out += getByIds(chunk)
        }
        return out
    }

    @Query("SELECT id FROM contacts WHERE isDeleted = 0")
    suspend fun getAllActiveIds(): List<String>

    @Query(
        """
        SELECT * FROM contacts
        WHERE isDeleted = 0
          AND (searchBlob LIKE '%' || :query || '%' OR primaryPhone LIKE '%' || :query || '%')
        ORDER BY isFavorite DESC, displayName COLLATE NOCASE ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun searchContacts(query: String, limit: Int = 80, offset: Int = 0): List<ContactEntity>

    @Query(
        """
        SELECT c.* FROM contacts c
        INNER JOIN contact_categories cc ON c.id = cc.contactId
        WHERE c.isDeleted = 0 AND cc.categoryPath = :categoryPath
        ORDER BY c.isFavorite DESC, c.displayName COLLATE NOCASE ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getByCategoryPath(categoryPath: String, limit: Int = 120, offset: Int = 0): List<ContactEntity>

    @Query(
        """
        SELECT cc.categoryPath, COUNT(*) AS cnt
        FROM contact_categories cc
        INNER JOIN contacts c ON cc.contactId = c.id
        WHERE c.isDeleted = 0 AND cc.categoryPath IN (:paths)
        GROUP BY cc.categoryPath
        """
    )
    suspend fun countForCategoryPaths(paths: List<String>): List<CategoryCountTuple>

    @Query("SELECT * FROM contacts WHERE isDeleted = 0 AND latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY updatedAtEpochMs DESC LIMIT 2000")
    fun observeGeoContacts(): Flow<List<ContactEntity>>

    // Sandbox isolation invariant: always returns empty so no contacts are ever marked for device phonebook export
    @Query("SELECT * FROM contacts WHERE 0 = 1")
    fun observeSyncedContacts(): Flow<List<ContactEntity>>

    @Query("SELECT c.* FROM phone_lookup p INNER JOIN contacts c ON p.contactId = c.id WHERE p.phoneKey = :phoneKey AND c.isDeleted = 0 LIMIT 1")
    suspend fun findByExactPhoneKey(phoneKey: String): ContactEntity?

    @Query("SELECT p.phoneKey, c.* FROM phone_lookup p INNER JOIN contacts c ON p.contactId = c.id WHERE p.phoneKey IN (:phoneKeys) AND c.isDeleted = 0")
    suspend fun findAllByPhoneKeys(phoneKeys: List<String>): List<PhoneLookupMatch>

    @Query("SELECT * FROM contacts WHERE isDeleted = 0 AND (categoryPathsFlat LIKE '%جهات اتصال تلقائية%' OR categoryPathsFlat = '') LIMIT :limit")
    suspend fun getAutoOrUnclassifiedContacts(limit: Int = 120): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE isDeleted = 0 AND (nickname IS NULL OR nickname = '' OR tagsJoined = '') LIMIT :limit")
    suspend fun getContactsNeedingBilingualOrTags(limit: Int = 50): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE syncState != 'SYNCED' ORDER BY updatedAtEpochMs ASC LIMIT :limit")
    suspend fun getPendingSyncContacts(limit: Int = 200): List<ContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ContactEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategoryRefs(refs: List<ContactCategoryCrossRef>)

    @Query("DELETE FROM contact_categories WHERE contactId = :contactId")
    suspend fun deleteCategoryRefsForContact(contactId: String)

    @Query("DELETE FROM contact_categories WHERE contactId IN (:contactIds)")
    suspend fun deleteCategoryRefsForContacts(contactIds: List<String>)

    @Query("DELETE FROM contact_categories")
    suspend fun clearCategoryRefs()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPhoneKeys(keys: List<PhoneLookupEntity>)

    @Query("DELETE FROM phone_lookup WHERE contactId = :contactId")
    suspend fun deletePhoneKeysForContact(contactId: String)

    @Query("DELETE FROM phone_lookup WHERE contactId IN (:contactIds)")
    suspend fun deletePhoneKeysForContacts(contactIds: List<String>)

    @Query("DELETE FROM phone_lookup")
    suspend fun clearPhoneKeys()

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM contacts WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("UPDATE contacts SET syncedToDevice = (:synced AND 0) WHERE id = :id")
    suspend fun updateSyncedFlag(id: String, synced: Boolean)

    @Query("UPDATE contacts SET isFavorite = :favorite, updatedAtEpochMs = :updatedAtMs, syncState = :syncState WHERE id = :id")
    suspend fun updateFavoriteFlag(id: String, favorite: Boolean, updatedAtMs: Long = System.currentTimeMillis(), syncState: String = SyncState.PENDING.name)

    @Query("UPDATE contacts SET isBlocked = :blocked, updatedAtEpochMs = :updatedAtMs, syncState = :syncState WHERE id = :id")
    suspend fun updateBlockedFlag(id: String, blocked: Boolean, updatedAtMs: Long = System.currentTimeMillis(), syncState: String = SyncState.PENDING.name)

    @Query("UPDATE contacts SET isMuted = :muted, updatedAtEpochMs = :updatedAtMs, syncState = :syncState WHERE id = :id")
    suspend fun updateMutedFlag(id: String, muted: Boolean, updatedAtMs: Long = System.currentTimeMillis(), syncState: String = SyncState.PENDING.name)

    @Query("UPDATE contacts SET lastContactedEpochMs = :epochMs, updatedAtEpochMs = :epochMs, syncState = :syncState WHERE id = :id")
    suspend fun updateLastContacted(id: String, epochMs: Long, syncState: String = SyncState.PENDING.name)

    @Query("UPDATE contacts SET isDeleted = 1, deletedAtEpochMs = :deletedAtMs, updatedAtEpochMs = :deletedAtMs, syncState = :syncState WHERE id = :id")
    suspend fun softDeleteById(id: String, deletedAtMs: Long, syncState: String = SyncState.PENDING.name)

    @Query("UPDATE contacts SET isDeleted = 1, deletedAtEpochMs = :deletedAtMs, updatedAtEpochMs = :deletedAtMs, syncState = :syncState WHERE id IN (:ids)")
    suspend fun softDeleteByIds(ids: List<String>, deletedAtMs: Long, syncState: String = SyncState.PENDING.name)

    @Query("UPDATE contacts SET syncState = :syncState WHERE id IN (:ids)")
    suspend fun markSyncState(ids: List<String>, syncState: String)

    suspend fun markSyncStateChunked(ids: List<String>, syncState: String) {
        if (ids.isEmpty()) return
        for (chunk in ids.chunked(450)) {
            markSyncState(chunk, syncState)
        }
    }

    @Query("DELETE FROM contacts")
    suspend fun clearAll()

    @Transaction
    suspend fun upsertBatchWithIndexes(
        contacts: List<ContactEntity>,
        catEntities: List<CategoryEntity>,
        categoryDao: CategoryDao,
    ) {
        val ids = contacts.map { it.id }
        ids.chunked(500).forEach { chunk ->
            deleteCategoryRefsForContacts(chunk)
            deletePhoneKeysForContacts(chunk)
        }

        val catRefs = ArrayList<ContactCategoryCrossRef>(contacts.size * 2)
        val phoneRefs = ArrayList<PhoneLookupEntity>(contacts.size)

        for (c in contacts) {
            if (c.isDeleted) continue
            val cats = c.categoryPathsFlat.split("\u001F").filter { it.isNotBlank() }
            for (fullPath in cats) {
                val parts = fullPath.split(">").map { it.trim() }.filter { it.isNotBlank() }
                var accum = ""
                for (part in parts) {
                    accum = if (accum.isEmpty()) part else "$accum > $part"
                    catRefs += ContactCategoryCrossRef(c.id, accum)
                }
            }
            val phones = c.phonesJoined.split("\u001F").filter { it.isNotBlank() }
            for (p in phones) {
                val key = PhoneUtils.phoneSearchKey(p)
                if (key.length >= 4) {
                    phoneRefs += PhoneLookupEntity(key, c.id)
                }
            }
        }

        contacts.chunked(500).forEach { upsertAll(it) }
        catRefs.chunked(900).forEach { insertCategoryRefs(it) }
        phoneRefs.chunked(900).forEach { insertPhoneKeys(it) }
        if (catEntities.isNotEmpty()) {
            catEntities.chunked(500).forEach { categoryDao.upsertAll(it) }
        }
    }

    @Transaction
    suspend fun upsertSingleWithIndexes(contact: ContactEntity) {
        upsert(contact)
        deleteCategoryRefsForContact(contact.id)
        deletePhoneKeysForContact(contact.id)
        if (contact.isDeleted) return

        val catRefs = ArrayList<ContactCategoryCrossRef>()
        val cats = contact.categoryPathsFlat.split("\u001F").filter { it.isNotBlank() }
        for (fullPath in cats) {
            val parts = fullPath.split(">").map { it.trim() }.filter { it.isNotBlank() }
            var accum = ""
            for (part in parts) {
                accum = if (accum.isEmpty()) part else "$accum > $part"
                catRefs += ContactCategoryCrossRef(contact.id, accum)
            }
        }
        if (catRefs.isNotEmpty()) insertCategoryRefs(catRefs)

        val phoneRefs = contact.phonesJoined.split("\u001F")
            .filter { it.isNotBlank() }
            .mapNotNull { p ->
                val key = PhoneUtils.phoneSearchKey(p)
                if (key.length >= 4) PhoneLookupEntity(key, contact.id) else null
            }
        if (phoneRefs.isNotEmpty()) insertPhoneKeys(phoneRefs)
    }

    @Transaction
    suspend fun deleteContactWithIndexes(contactId: String) {
        deleteCategoryRefsForContact(contactId)
        deletePhoneKeysForContact(contactId)
        deleteById(contactId)
    }

    @Transaction
    suspend fun softDeleteContactWithIndexes(contactId: String, deletedAtMs: Long) {
        deleteCategoryRefsForContact(contactId)
        deletePhoneKeysForContact(contactId)
        softDeleteById(contactId, deletedAtMs)
    }

    @Transaction
    suspend fun deleteContactsWithIndexes(contactIds: List<String>) {
        if (contactIds.isEmpty()) return
        contactIds.chunked(500).forEach { chunk ->
            deleteCategoryRefsForContacts(chunk)
            deletePhoneKeysForContacts(chunk)
            deleteByIds(chunk)
        }
    }
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE parentPath = :parentPath ORDER BY name ASC")
    suspend fun getChildren(parentPath: String): List<CategoryEntity>

    @Query("SELECT * FROM categories ORDER BY depth ASC, fullPath ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY depth ASC, fullPath ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CategoryEntity)

    @Query("DELETE FROM categories")
    suspend fun clearAll()

    @Query("DELETE FROM categories WHERE fullPath = :fullPath OR fullPath LIKE :fullPath || ' > %'")
    suspend fun deleteSubtree(fullPath: String)
}

@Dao
interface WhatsAppLogDao {
    @Query("SELECT * FROM whatsapp_logs ORDER BY loggedAtEpochMs DESC LIMIT 200")
    fun observeRecentLogs(): Flow<List<WhatsAppLogEntity>>

    @Query("SELECT * FROM whatsapp_logs WHERE loggedAtEpochMs >= :sinceMs ORDER BY loggedAtEpochMs DESC")
    fun observeLogsSince(sinceMs: Long): Flow<List<WhatsAppLogEntity>>

    @Query("SELECT * FROM whatsapp_logs WHERE loggedAtEpochMs >= :sinceMs ORDER BY loggedAtEpochMs DESC")
    suspend fun getLogsSince(sinceMs: Long): List<WhatsAppLogEntity>

    @Query("SELECT * FROM whatsapp_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WhatsAppLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: WhatsAppLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(logs: List<WhatsAppLogEntity>)

    @Query("UPDATE whatsapp_logs SET syncedToPhone = 0 WHERE contactId = :contactId")
    suspend fun markSyncedByContact(contactId: String)
}

@Dao
interface PaymentTemplateDao {
    @Query("SELECT * FROM payment_templates ORDER BY isDefault DESC, name ASC")
    fun observeAll(): Flow<List<PaymentTemplateEntity>>

    @Query("SELECT * FROM payment_templates WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PaymentTemplateEntity?

    @Query("SELECT COUNT(*) FROM payment_templates")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PaymentTemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PaymentTemplateEntity)

    @Query("DELETE FROM payment_templates WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface AppCallLogDao {
    @Query("SELECT * FROM app_call_logs WHERE isDeleted = 0 ORDER BY timestampMs DESC LIMIT :limit")
    fun observeRecentCalls(limit: Int = 350): Flow<List<AppCallLogEntity>>

    @Query("SELECT * FROM app_call_logs WHERE isDeleted = 0 ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getRecentCallsOnce(limit: Int = 350): List<AppCallLogEntity>

    @Query(
        """
        SELECT * FROM app_call_logs
        WHERE isDeleted = 0
          AND ((:contactId IS NOT NULL AND contactId = :contactId) OR (:phoneKey != '' AND phoneKey = :phoneKey))
        ORDER BY timestampMs DESC
        LIMIT :limit
        """
    )
    fun observeForContactOrPhone(contactId: String?, phoneKey: String, limit: Int = 100): Flow<List<AppCallLogEntity>>

    @Query(
        """
        SELECT * FROM app_call_logs
        WHERE isDeleted = 0
          AND ((:contactId IS NOT NULL AND contactId = :contactId) OR (:phoneKey != '' AND phoneKey = :phoneKey))
        ORDER BY timestampMs DESC
        LIMIT :limit
        """
    )
    suspend fun getForContactOrPhoneOnce(contactId: String?, phoneKey: String, limit: Int = 100): List<AppCallLogEntity>

    @Query(
        """
        SELECT * FROM app_call_logs
        WHERE isDeleted = 0
          AND (phoneNumber LIKE '%' || :query || '%' OR cachedName LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%')
        ORDER BY timestampMs DESC
        LIMIT :limit
        """
    )
    suspend fun searchCallLogs(query: String, limit: Int = 50): List<AppCallLogEntity>

    @Query("SELECT * FROM app_call_logs WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getById(id: String): AppCallLogEntity?

    @Query("SELECT * FROM app_call_logs WHERE id = :id LIMIT 1")
    suspend fun getByIdEvenIfDeleted(id: String): AppCallLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: AppCallLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<AppCallLogEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(items: List<AppCallLogEntity>)

    @Query("UPDATE app_call_logs SET isDeleted = 1, deletedAtEpochMs = :deletedAtMs, updatedAtEpochMs = :deletedAtMs, syncState = :syncState WHERE id = :id")
    suspend fun softDelete(id: String, deletedAtMs: Long, syncState: String = SyncState.PENDING.name)

    @Query("UPDATE app_call_logs SET isDeleted = 0, deletedAtEpochMs = NULL, updatedAtEpochMs = :nowMs, syncState = :syncState WHERE id = :id")
    suspend fun restore(id: String, nowMs: Long, syncState: String = SyncState.PENDING.name)

    @Query("UPDATE app_call_logs SET syncState = :syncState WHERE id = :id")
    suspend fun markSyncState(id: String, syncState: String)

    @Query("DELETE FROM app_call_logs WHERE id = :id")
    suspend fun deleteForever(id: String)
}

@Dao
interface CallTranscriptDao {
    @Query("SELECT * FROM call_transcripts WHERE isDeleted = 0 ORDER BY timestampMs DESC LIMIT 250")
    fun observeAll(): Flow<List<CallTranscriptEntity>>

    @Query("SELECT * FROM call_transcripts WHERE isDeleted = 0 AND status = 'PENDING' ORDER BY timestampMs DESC LIMIT 10")
    suspend fun getPending(): List<CallTranscriptEntity>

    @Query(
        """
        SELECT * FROM call_transcripts
        WHERE isDeleted = 0
          AND ((:contactId != '' AND contactId = :contactId) OR (:phoneSuffix != '' AND phone LIKE '%' || :phoneSuffix))
        ORDER BY timestampMs DESC
        """
    )
    fun observeForContactOrPhone(contactId: String, phoneSuffix: String): Flow<List<CallTranscriptEntity>>

    @Query(
        """
        SELECT * FROM call_transcripts
        WHERE isDeleted = 0
          AND ((:contactId != '' AND contactId = :contactId) OR (:phoneSuffix != '' AND phone LIKE '%' || :phoneSuffix))
        ORDER BY timestampMs DESC
        """
    )
    suspend fun getForContactOrPhone(contactId: String, phoneSuffix: String): List<CallTranscriptEntity>

    @Query(
        """
        SELECT * FROM call_transcripts
        WHERE isDeleted = 0
          AND ((:contactId != '' AND contactId = :contactId) OR (:phoneSuffix != '' AND phone LIKE '%' || :phoneSuffix))
        ORDER BY timestampMs DESC
        LIMIT 1
        """
    )
    suspend fun getLatestForContactOrPhone(contactId: String, phoneSuffix: String): CallTranscriptEntity?

    @Query(
        """
        SELECT * FROM call_transcripts
        WHERE isDeleted = 0
          AND (
            transcript LIKE '%' || :query || '%'
            OR extractedNotes LIKE '%' || :query || '%'
            OR highlightsJoined LIKE '%' || :query || '%'
            OR summaryBulletsJoined LIKE '%' || :query || '%'
            OR oneLineSummary LIKE '%' || :query || '%'
            OR actionItemsJson LIKE '%' || :query || '%'
            OR contactName LIKE '%' || :query || '%'
            OR phone LIKE '%' || :query || '%'
          )
        ORDER BY timestampMs DESC
        LIMIT :limit
        """
    )
    suspend fun searchTranscripts(query: String, limit: Int = 60): List<CallTranscriptEntity>

    @Query("SELECT * FROM call_transcripts WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getById(id: String): CallTranscriptEntity?

    @Query("SELECT * FROM call_transcripts WHERE id = :id LIMIT 1")
    suspend fun getByIdEvenIfDeleted(id: String): CallTranscriptEntity?

    @Query("SELECT audioFilePath FROM call_transcripts WHERE isDeleted = 0 AND audioFilePath != ''")
    suspend fun getAllReferencedAudioPaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CallTranscriptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CallTranscriptEntity>)

    @Query("UPDATE call_transcripts SET eventAddedToCalendar = 1, updatedAtEpochMs = :updatedAtMs, syncState = :syncState WHERE id = :id")
    suspend fun markCalendarAdded(id: String, updatedAtMs: Long = System.currentTimeMillis(), syncState: String = SyncState.PENDING.name)

    @Query("UPDATE call_transcripts SET notesSavedToContact = 1, updatedAtEpochMs = :updatedAtMs, syncState = :syncState WHERE id = :id")
    suspend fun markNotesSaved(id: String, updatedAtMs: Long = System.currentTimeMillis(), syncState: String = SyncState.PENDING.name)

    @Query("UPDATE call_transcripts SET isDeleted = 1, deletedAtEpochMs = :deletedAtMs, updatedAtEpochMs = :deletedAtMs, syncState = :syncState WHERE id = :id")
    suspend fun softDelete(id: String, deletedAtMs: Long, syncState: String = SyncState.PENDING.name)

    @Query("UPDATE call_transcripts SET syncState = :syncState WHERE id = :id")
    suspend fun markSyncState(id: String, syncState: String)

    @Query("DELETE FROM call_transcripts WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface DialHistoryDao {
    @Query("SELECT * FROM dial_history WHERE isDeleted = 0 ORDER BY typedAtEpochMs DESC LIMIT 40")
    fun observeActiveHistory(): Flow<List<DialHistoryEntity>>

    @Query("SELECT * FROM dial_history WHERE isDeleted = 0 ORDER BY typedAtEpochMs DESC LIMIT :limit")
    suspend fun getActiveOnce(limit: Int = 6): List<DialHistoryEntity>

    @Query("SELECT * FROM dial_history WHERE isDeleted = 1 ORDER BY deletedAtEpochMs DESC LIMIT 60")
    fun observeRecentlyDeleted(): Flow<List<DialHistoryEntity>>

    @Query("SELECT * FROM dial_history WHERE phoneNumber = :phoneNumber AND isDeleted = 0 LIMIT 1")
    suspend fun findActiveByPhone(phoneNumber: String): DialHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DialHistoryEntity)

    @Query("UPDATE dial_history SET isDeleted = 1, deletedAtEpochMs = :deletedAtMs WHERE id = :id")
    suspend fun softDelete(id: String, deletedAtMs: Long)

    @Query("UPDATE dial_history SET isDeleted = 0, deletedAtEpochMs = NULL, typedAtEpochMs = :restoredAtMs WHERE id = :id")
    suspend fun restore(id: String, restoredAtMs: Long)

    @Query("DELETE FROM dial_history WHERE id = :id")
    suspend fun deleteForever(id: String)

    @Query("DELETE FROM dial_history WHERE isDeleted = 1")
    suspend fun emptyTrash()
}

@Dao
interface PendingSyncDao {
    @Query("SELECT COUNT(*) FROM pending_sync_queue")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_sync_queue WHERE retryCount >= :maxRetries")
    fun observeFailedCount(maxRetries: Int = 5): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_sync_queue")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM pending_sync_queue WHERE retryCount < :maxRetries")
    suspend fun getActivePendingCount(maxRetries: Int = 5): Int

    @Query("SELECT * FROM pending_sync_queue WHERE retryCount < :maxRetries ORDER BY retryCount ASC, enqueuedAtEpochMs ASC LIMIT :limit")
    suspend fun getPendingBatch(limit: Int = 400, maxRetries: Int = 5): List<PendingSyncEntity>

    @Query("SELECT * FROM pending_sync_queue WHERE entityType = :entityType AND entityId = :entityId LIMIT 1")
    suspend fun getByEntity(entityType: String, entityId: String): PendingSyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: PendingSyncEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueBatch(items: List<PendingSyncEntity>)

    @Query("DELETE FROM pending_sync_queue WHERE queueKey = :queueKey")
    suspend fun remove(queueKey: String)

    @Query("DELETE FROM pending_sync_queue WHERE queueKey IN (:queueKeys)")
    suspend fun removeBatch(queueKeys: List<String>)

    suspend fun removeBatchChunked(queueKeys: List<String>) {
        if (queueKeys.isEmpty()) return
        for (chunk in queueKeys.chunked(450)) {
            removeBatch(chunk)
        }
    }

    @Query("DELETE FROM pending_sync_queue WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun removeByEntity(entityType: String, entityId: String)

    @Query("UPDATE pending_sync_queue SET retryCount = retryCount + 1, lastError = :error WHERE queueKey = :queueKey")
    suspend fun markRetryFailure(queueKey: String, error: String)

    @Query("UPDATE pending_sync_queue SET retryCount = retryCount + 1, lastError = :error WHERE queueKey IN (:queueKeys)")
    suspend fun markRetryFailureBatch(queueKeys: List<String>, error: String)

    suspend fun markRetryFailureBatchChunked(queueKeys: List<String>, error: String) {
        if (queueKeys.isEmpty()) return
        val safeErr = error.take(300)
        for (chunk in queueKeys.chunked(450)) {
            markRetryFailureBatch(chunk, safeErr)
        }
    }

    @Query("UPDATE pending_sync_queue SET retryCount = 0, lastError = NULL WHERE retryCount > 0")
    suspend fun resetFailedRetries()

    @Query("DELETE FROM pending_sync_queue")
    suspend fun clearAll()
}
