package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT COUNT(*) FROM contacts")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun getTotalCount(): Int

    @Query("SELECT * FROM contacts WHERE isFavorite = 1 ORDER BY displayName ASC LIMIT 30")
    fun observeFavorites(): Flow<List<ContactEntity>>

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
    suspend fun searchContacts(query: String, limit: Int = 60, offset: Int = 0): List<ContactEntity>

    @Query(
        """
        SELECT * FROM contacts
        WHERE categoryPathsFlat LIKE '%' || :categoryPath || '%'
        ORDER BY isFavorite DESC, displayName ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getByCategoryPath(categoryPath: String, limit: Int = 80, offset: Int = 0): List<ContactEntity>

    @Query("SELECT COUNT(*) FROM contacts WHERE categoryPathsFlat LIKE '%' || :categoryPath || '%'")
    suspend fun countByCategoryPath(categoryPath: String): Int

    @Query("SELECT * FROM contacts WHERE latitude IS NOT NULL AND longitude IS NOT NULL LIMIT 500")
    fun observeGeoContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE syncedToDevice = 1 ORDER BY displayName ASC")
    fun observeSyncedContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE primaryPhone LIKE '%' || :suffix OR phonesJoined LIKE '%' || :suffix || '%' LIMIT 1")
    suspend fun findByPhoneSuffix(suffix: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ContactEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ContactEntity)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE contacts SET syncedToDevice = :synced WHERE id = :id")
    suspend fun updateSyncedFlag(id: String, synced: Boolean)

    @Query("UPDATE contacts SET isFavorite = :favorite WHERE id = :id")
    suspend fun updateFavoriteFlag(id: String, favorite: Boolean)

    @Query("SELECT categoryPathsFlat FROM contacts WHERE categoryPathsFlat != ''")
    suspend fun getAllCategoryStrings(): List<String>

    @Query("DELETE FROM contacts")
    suspend fun clearAll()
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
