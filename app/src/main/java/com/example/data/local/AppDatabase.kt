package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.telecom.RecordingSourceMode
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
        AppCallLogEntity::class,
        PendingSyncEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class RabtaDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun categoryDao(): CategoryDao
    abstract fun whatsAppLogDao(): WhatsAppLogDao
    abstract fun paymentTemplateDao(): PaymentTemplateDao
    abstract fun callTranscriptDao(): CallTranscriptDao
    abstract fun dialHistoryDao(): DialHistoryDao
    abstract fun appCallLogDao(): AppCallLogDao
    abstract fun pendingSyncDao(): PendingSyncDao

    companion object {
        @Volatile
        private var INSTANCE: RabtaDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN nickname TEXT")
                db.execSQL("ALTER TABLE contacts ADD COLUMN tagsJoined TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Upgrade contacts with offline-first sync metadata, sandbox flags, and avatar color seed
                db.execSQL("ALTER TABLE contacts ADD COLUMN avatarColorSeed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE contacts ADD COLUMN isMuted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE contacts ADD COLUMN isBlocked INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE contacts ADD COLUMN lastContactedEpochMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE contacts ADD COLUMN syncState TEXT NOT NULL DEFAULT 'SYNCED'")
                db.execSQL("ALTER TABLE contacts ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE contacts ADD COLUMN deletedAtEpochMs INTEGER")
                db.execSQL("UPDATE contacts SET syncedToDevice = 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_contacts_isDeleted` ON `contacts` (`isDeleted`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_contacts_syncState` ON `contacts` (`syncState`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_contacts_isBlocked` ON `contacts` (`isBlocked`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_contacts_lastContactedEpochMs` ON `contacts` (`lastContactedEpochMs`)")

                // Upgrade call_transcripts with 3-bullet summaries, segments JSON, action items JSON, and sync metadata
                db.execSQL("ALTER TABLE call_transcripts ADD COLUMN summaryBulletsJoined TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE call_transcripts ADD COLUMN oneLineSummary TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE call_transcripts ADD COLUMN segmentsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE call_transcripts ADD COLUMN actionItemsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE call_transcripts ADD COLUMN audioFileName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE call_transcripts ADD COLUMN audioSizeBytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE call_transcripts ADD COLUMN audioCachedLocally INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE call_transcripts ADD COLUMN updatedAtEpochMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE call_transcripts ADD COLUMN syncState TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE call_transcripts ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE call_transcripts ADD COLUMN deletedAtEpochMs INTEGER")
                db.execSQL("UPDATE call_transcripts SET updatedAtEpochMs = timestampMs WHERE updatedAtEpochMs = 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_transcripts_isDeleted` ON `call_transcripts` (`isDeleted`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_transcripts_syncState` ON `call_transcripts` (`syncState`)")

                // Create isolated local app_call_logs table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_call_logs` (
                        `id` TEXT NOT NULL,
                        `contactId` TEXT,
                        `phoneNumber` TEXT NOT NULL,
                        `phoneKey` TEXT NOT NULL,
                        `cachedName` TEXT,
                        `callType` INTEGER NOT NULL,
                        `simSlot` INTEGER,
                        `timestampMs` INTEGER NOT NULL,
                        `durationSec` INTEGER NOT NULL,
                        `transcriptId` TEXT,
                        `hasRecording` INTEGER NOT NULL DEFAULT 0,
                        `notes` TEXT,
                        `isDeleted` INTEGER NOT NULL DEFAULT 0,
                        `deletedAtEpochMs` INTEGER,
                        `updatedAtEpochMs` INTEGER NOT NULL DEFAULT 0,
                        `syncState` TEXT NOT NULL DEFAULT 'PENDING',
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_call_logs_contactId` ON `app_call_logs` (`contactId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_call_logs_phoneKey` ON `app_call_logs` (`phoneKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_call_logs_timestampMs` ON `app_call_logs` (`timestampMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_call_logs_isDeleted` ON `app_call_logs` (`isDeleted`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_call_logs_syncState` ON `app_call_logs` (`syncState`)")

                // Create persistent pending_sync_queue table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_sync_queue` (
                        `queueKey` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `entityId` TEXT NOT NULL,
                        `operation` TEXT NOT NULL,
                        `enqueuedAtEpochMs` INTEGER NOT NULL,
                        `entityUpdatedAtEpochMs` INTEGER NOT NULL,
                        `retryCount` INTEGER NOT NULL DEFAULT 0,
                        `lastError` TEXT,
                        PRIMARY KEY(`queueKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_sync_queue_entityType` ON `pending_sync_queue` (`entityType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_sync_queue_enqueuedAtEpochMs` ON `pending_sync_queue` (`enqueuedAtEpochMs`)")
            }
        }

        fun getInstance(context: Context): RabtaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RabtaDatabase::class.java,
                    "rabta_contacts.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
    private val recordingSourceKey = stringPreferencesKey("recording_source_mode")
    private val vibrateOnAnswerKey = booleanPreferencesKey("vibrate_on_answer")
    private val autoSpeakerRecordKey = booleanPreferencesKey("auto_speaker_for_record")
    private val lockedCategoriesKey = stringSetPreferencesKey("locked_categories")
    private val whatsappTemplatesKey = stringPreferencesKey("whatsapp_quick_templates")
    private val appLockEnabledKey = booleanPreferencesKey("app_lock_enabled")
    private val appLockPinHashKey = stringPreferencesKey("app_lock_pin_hash")
    private val biometricLockKey = booleanPreferencesKey("biometric_lock_enabled")
    private val hideLockscreenDetailsKey = booleanPreferencesKey("hide_lockscreen_caller_details")

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
        prefs[autoRecordKey] ?: true
    }

    val showContactPhotosFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[showPhotosKey] ?: true
    }

    val callPhotoBackgroundFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[callBgPhotoKey] ?: true
    }

    // Complete Sandbox Isolation is permanently enforced: contacts never sync to native phonebook
    val strictPhonebookIsolationFlow: Flow<Boolean> = context.dataStore.data.map { true }

    val autoTranscribeCallsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[autoTranscribeKey] ?: false
    }

    val simPreferenceFlow: Flow<SimPreference> = context.dataStore.data.map { prefs ->
        runCatching { SimPreference.valueOf(prefs[simPrefKey] ?: SimPreference.ASK_OR_BOTH.name) }
            .getOrDefault(SimPreference.ASK_OR_BOTH)
    }

    val recordingSourceFlow: Flow<RecordingSourceMode> = context.dataStore.data.map { prefs ->
        runCatching { RecordingSourceMode.valueOf(prefs[recordingSourceKey] ?: RecordingSourceMode.SMART_AUTO.name) }
            .getOrDefault(RecordingSourceMode.SMART_AUTO)
    }

    val vibrateOnAnswerFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[vibrateOnAnswerKey] ?: true
    }

    val autoSpeakerForRecordFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[autoSpeakerRecordKey] ?: false
    }

    val lockedCategoriesFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[lockedCategoriesKey] ?: emptySet()
    }

    val whatsappTemplatesFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[whatsappTemplatesKey]
        if (raw.isNullOrBlank()) {
            DEFAULT_WHATSAPP_TEMPLATES
        } else {
            raw.split(TEMPLATE_DELIM).map { it.trim() }.filter { it.isNotBlank() }
                .ifEmpty { DEFAULT_WHATSAPP_TEMPLATES }
        }
    }

    val appLockEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[appLockEnabledKey] ?: false
    }

    val appLockPinHashFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[appLockPinHashKey] ?: ""
    }

    val biometricLockEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[biometricLockKey] ?: true
    }

    val hideLockscreenCallerDetailsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[hideLockscreenDetailsKey] ?: false
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

    suspend fun setStrictPhonebookIsolation(@Suppress("UNUSED_PARAMETER") enabled: Boolean) {
        context.dataStore.edit { it[strictIsolationKey] = true }
    }

    suspend fun setAutoTranscribeCalls(enabled: Boolean) {
        context.dataStore.edit { it[autoTranscribeKey] = enabled }
    }

    suspend fun setSimPreference(pref: SimPreference) {
        context.dataStore.edit { it[simPrefKey] = pref.name }
    }

    suspend fun setRecordingSourceMode(mode: RecordingSourceMode) {
        context.dataStore.edit { it[recordingSourceKey] = mode.name }
    }

    suspend fun setVibrateOnAnswer(enabled: Boolean) {
        context.dataStore.edit { it[vibrateOnAnswerKey] = enabled }
    }

    suspend fun setAutoSpeakerForRecord(enabled: Boolean) {
        context.dataStore.edit { it[autoSpeakerRecordKey] = enabled }
    }

    suspend fun toggleCategoryLock(categoryPath: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[lockedCategoriesKey] ?: emptySet()).toMutableSet()
            if (!current.add(categoryPath)) current.remove(categoryPath)
            prefs[lockedCategoriesKey] = current
        }
    }

    suspend fun saveWhatsAppTemplates(templates: List<String>) {
        val cleaned = templates.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        context.dataStore.edit { prefs ->
            prefs[whatsappTemplatesKey] = cleaned.joinToString(TEMPLATE_DELIM)
        }
    }

    suspend fun setAppLockPin(pinHash: String, enabled: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[appLockPinHashKey] = pinHash
            prefs[appLockEnabledKey] = enabled
        }
    }

    suspend fun disableAppLock() {
        context.dataStore.edit { prefs ->
            prefs[appLockEnabledKey] = false
        }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[biometricLockKey] = enabled
        }
    }

    suspend fun setHideLockscreenCallerDetails(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[hideLockscreenDetailsKey] = enabled
        }
    }

    companion object {
        private const val TEMPLATE_DELIM = "\u001F"
        val DEFAULT_WHATSAPP_TEMPLATES = listOf(
            "السلام عليكم، معاك بخصوص الشغل — متاح نتكلم؟",
            "أنا مشغول/في اجتماع حالياً، هكلمك أول ما أخلص 👍",
            "ممكن تبعتلي اللوكيشن أو التفاصيل هنا على واتساب؟",
            "تمام، تم تأكيد الموعد وهكون على تواصل معاك إن شاء الله.",
            "مساء الخير، ده رقمي للتواصل السريع في أي وقت.",
        )
    }
}
