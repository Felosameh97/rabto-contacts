package com.example.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

@Dao
interface ContactDao {

    @Query("SELECT COUNT(*) FROM contacts")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun getTotalCount(): Int

    @Query("SELECT * FROM contacts WHERE isFavorite = 1 ORDER BY displayName ASC LIMIT 40")
    fun observeFavorites(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE isFavorite = 1 ORDER BY displayName ASC LIMIT :limit")
    suspend fun getFavoritesOnce(limit: Int = 8): List<ContactEntity>

    @Query("SELECT * FROM contacts ORDER BY updatedAtEpochMs DESC LIMIT :limit OFFSET :offset")
    suspend fun getRecentOrAllPage(limit: Int = 200, offset: Int = 0): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<ContactEntity>

    @Query(
        """
        SELECT * FROM contacts
        WHERE searchBlob LIKE '%' || :query || '%'
           OR primaryPhone LIKE '%' || :query || '%'
        ORDER BY isFavorite DESC, displayName ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun searchContacts(query: String, limit: Int = 80, offset: Int = 0): List<ContactEntity>

    @Query(
        """
        SELECT c.* FROM contacts c
        INNER JOIN contact_categories cc ON c.id = cc.contactId
        WHERE cc.categoryPath = :categoryPath
        ORDER BY c.isFavorite DESC, c.displayName ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getByCategoryPath(categoryPath: String, limit: Int = 120, offset: Int = 0): List<ContactEntity>

    @Query("SELECT categoryPath, COUNT(*) AS cnt FROM contact_categories WHERE categoryPath IN (:paths) GROUP BY categoryPath")
    suspend fun countForCategoryPaths(paths: List<String>): List<CategoryCountTuple>

    @Query("SELECT * FROM contacts WHERE latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY updatedAtEpochMs DESC LIMIT 2000")
    fun observeGeoContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE syncedToDevice = 1 ORDER BY displayName ASC")
    fun observeSyncedContacts(): Flow<List<ContactEntity>>

    @Query("SELECT c.* FROM phone_lookup p INNER JOIN contacts c ON p.contactId = c.id WHERE p.phoneKey = :phoneKey LIMIT 1")
    suspend fun findByExactPhoneKey(phoneKey: String): ContactEntity?

    @Query("SELECT p.phoneKey, c.* FROM phone_lookup p INNER JOIN contacts c ON p.contactId = c.id WHERE p.phoneKey IN (:phoneKeys)")
    suspend fun findAllByPhoneKeys(phoneKeys: List<String>): List<PhoneLookupMatch>

    @Query("SELECT * FROM contacts WHERE categoryPathsFlat LIKE '%جهات اتصال تلقائية%' OR categoryPathsFlat = '' LIMIT :limit")
    suspend fun getAutoOrUnclassifiedContacts(limit: Int = 120): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE nickname IS NULL OR nickname = '' OR tagsJoined = '' LIMIT :limit")
    suspend fun getContactsNeedingBilingualOrTags(limit: Int = 50): List<ContactEntity>

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

    @Query("UPDATE contacts SET syncedToDevice = :synced WHERE id = :id")
    suspend fun updateSyncedFlag(id: String, synced: Boolean)

    @Query("UPDATE contacts SET isFavorite = :favorite WHERE id = :id")
    suspend fun updateFavoriteFlag(id: String, favorite: Boolean)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: WhatsAppLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(logs: List<WhatsAppLogEntity>)

    @Query("UPDATE whatsapp_logs SET syncedToPhone = 1 WHERE contactId = :contactId")
    suspend fun markSyncedByContact(contactId: String)
}

@Dao
interface PaymentTemplateDao {
    @Query("SELECT * FROM payment_templates ORDER BY isDefault DESC, name ASC")
    fun observeAll(): Flow<List<PaymentTemplateEntity>>

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
interface CallTranscriptDao {
    @Query("SELECT * FROM call_transcripts ORDER BY timestampMs DESC LIMIT 200")
    fun observeAll(): Flow<List<CallTranscriptEntity>>

    @Query("SELECT * FROM call_transcripts WHERE status = 'PENDING' ORDER BY timestampMs DESC LIMIT 10")
    suspend fun getPending(): List<CallTranscriptEntity>

    @Query("SELECT * FROM call_transcripts WHERE contactId = :contactId OR phone LIKE '%' || :phoneSuffix ORDER BY timestampMs DESC")
    suspend fun getForContactOrPhone(contactId: String, phoneSuffix: String): List<CallTranscriptEntity>

    @Query("SELECT * FROM call_transcripts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CallTranscriptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CallTranscriptEntity)

    @Query("UPDATE call_transcripts SET eventAddedToCalendar = 1 WHERE id = :id")
    suspend fun markCalendarAdded(id: String)

    @Query("UPDATE call_transcripts SET notesSavedToContact = 1 WHERE id = :id")
    suspend fun markNotesSaved(id: String)

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
