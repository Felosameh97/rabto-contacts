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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.ui.theme.CalmPalette
import com.example.ui.theme.ThemeMode
import com.example.util.SimPreference
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
        CallTranscriptEntity::class,
        DialHistoryEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class RabtaDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun categoryDao(): CategoryDao
    abstract fun whatsAppLogDao(): WhatsAppLogDao
    abstract fun paymentTemplateDao(): PaymentTemplateDao
    abstract fun callTranscriptDao(): CallTranscriptDao
    abstract fun dialHistoryDao(): DialHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: RabtaDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN nickname TEXT")
                db.execSQL("ALTER TABLE contacts ADD COLUMN tagsJoined TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN photoUri TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `call_transcripts` (
                        `id` TEXT NOT NULL,
                        `contactId` TEXT,
                        `phone` TEXT NOT NULL,
                        `contactName` TEXT NOT NULL,
                        `audioFilePath` TEXT NOT NULL,
                        `timestampMs` INTEGER NOT NULL,
                        `durationSec` INTEGER NOT NULL,
                        `transcript` TEXT NOT NULL DEFAULT '',
                        `highlightsJoined` TEXT NOT NULL DEFAULT '',
                        `extractedNotes` TEXT NOT NULL DEFAULT '',
                        `suggestedEventTitle` TEXT NOT NULL DEFAULT '',
                        `suggestedEventDateIso` TEXT NOT NULL DEFAULT '',
                        `suggestedEventEpochMs` INTEGER,
                        `suggestedCategory` TEXT NOT NULL DEFAULT '',
                        `eventAddedToCalendar` INTEGER NOT NULL DEFAULT 0,
                        `notesSavedToContact` INTEGER NOT NULL DEFAULT 0,
                        `status` TEXT NOT NULL DEFAULT 'PENDING',
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_transcripts_contactId` ON `call_transcripts` (`contactId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_transcripts_phone` ON `call_transcripts` (`phone`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_transcripts_timestampMs` ON `call_transcripts` (`timestampMs`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `dial_history` (
                        `id` TEXT NOT NULL,
                        `phoneNumber` TEXT NOT NULL,
                        `matchedContactId` TEXT,
                        `matchedName` TEXT,
                        `didCall` INTEGER NOT NULL,
                        `typedAtEpochMs` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL DEFAULT 0,
                        `deletedAtEpochMs` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dial_history_typedAtEpochMs` ON `dial_history` (`typedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dial_history_isDeleted` ON `dial_history` (`isDeleted`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dial_history_phoneNumber` ON `dial_history` (`phoneNumber`)")
            }
        }

        fun getInstance(context: Context): RabtaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RabtaDatabase::class.java,
                    "rabta_contacts.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
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
    private val showPhotosKey = booleanPreferencesKey("show_contact_photos")
    private val callBgPhotoKey = booleanPreferencesKey("call_photo_background")
    private val strictIsolationKey = booleanPreferencesKey("strict_phonebook_isolation")
    private val autoTranscribeKey = booleanPreferencesKey("auto_transcribe_calls")
    private val simPrefKey = stringPreferencesKey("sim_preference")

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

    val showContactPhotosFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[showPhotosKey] ?: true
    }

    val callPhotoBackgroundFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[callBgPhotoKey] ?: true
    }

    val strictPhonebookIsolationFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[strictIsolationKey] ?: true
    }

    val autoTranscribeCallsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[autoTranscribeKey] ?: true
    }

    val simPreferenceFlow: Flow<SimPreference> = context.dataStore.data.map { prefs ->
        runCatching { SimPreference.valueOf(prefs[simPrefKey] ?: SimPreference.ASK_OR_BOTH.name) }
            .getOrDefault(SimPreference.ASK_OR_BOTH)
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

    suspend fun setShowContactPhotos(enabled: Boolean) {
        context.dataStore.edit { it[showPhotosKey] = enabled }
    }

    suspend fun setCallPhotoBackground(enabled: Boolean) {
        context.dataStore.edit { it[callBgPhotoKey] = enabled }
    }

    suspend fun setStrictPhonebookIsolation(enabled: Boolean) {
        context.dataStore.edit { it[strictIsolationKey] = enabled }
    }

    suspend fun setAutoTranscribeCalls(enabled: Boolean) {
        context.dataStore.edit { it[autoTranscribeKey] = enabled }
    }

    suspend fun setSimPreference(pref: SimPreference) {
        context.dataStore.edit { it[simPrefKey] = pref.name }
    }
}
