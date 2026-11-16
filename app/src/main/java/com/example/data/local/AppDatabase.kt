package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.ui.theme.CalmPalette
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Database(
    entities = [
        ContactEntity::class,
        ContactCategoryCrossRef::class,
        PhoneLookupEntity::class,
        CategoryEntity::class,
        WhatsAppLogEntity::class,
        PaymentTemplateEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class RabtaDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun categoryDao(): CategoryDao
    abstract fun whatsAppLogDao(): WhatsAppLogDao
    abstract fun paymentTemplateDao(): PaymentTemplateDao

    companion object {
        @Volatile
        private var INSTANCE: RabtaDatabase? = null

        fun getInstance(context: Context): RabtaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RabtaDatabase::class.java,
                    "rabta_contacts.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rabta_prefs")

class SyncPrefsDataStore(private val context: Context) {
    private val githubRepoKey = stringPreferencesKey("github_repo_slug")
    private val lastSyncKey = longPreferencesKey("last_cloud_sync_ms")
    private val bundledImportedKey = booleanPreferencesKey("bundled_csv_imported")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val paletteKey = stringPreferencesKey("calm_palette")
    private val autoRecordKey = booleanPreferencesKey("auto_record_calls")

    val githubRepoFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[githubRepoKey] ?: ""
    }

    val lastSyncFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[lastSyncKey] ?: 0L
    }

    val bundledImportedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[bundledImportedKey] ?: false
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[themeModeKey] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    val paletteFlow: Flow<CalmPalette> = context.dataStore.data.map { prefs ->
        runCatching { CalmPalette.valueOf(prefs[paletteKey] ?: CalmPalette.SLATE.name) }
            .getOrDefault(CalmPalette.SLATE)
    }

    val autoRecordCallsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[autoRecordKey] ?: false
    }

    suspend fun setGithubRepoSlug(slug: String) {
        context.dataStore.edit { it[githubRepoKey] = slug.trim() }
    }

    suspend fun setLastSyncMs(epochMs: Long) {
        context.dataStore.edit { it[lastSyncKey] = epochMs }
    }

    suspend fun setBundledImported(imported: Boolean) {
        context.dataStore.edit { it[bundledImportedKey] = imported }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setPalette(palette: CalmPalette) {
        context.dataStore.edit { it[paletteKey] = palette.name }
    }

    suspend fun setAutoRecordCalls(enabled: Boolean) {
        context.dataStore.edit { it[autoRecordKey] = enabled }
    }
}
