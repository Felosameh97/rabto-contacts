package com.example.data.local

import android.content.Context
import android.net.Uri
import com.example.domain.model.CallTranscriptRecord
import com.example.domain.model.LocalMediaCacheStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

/**
 * Manages app-private local media caching for call recordings, contact avatars,
 * and structured AI transcript snapshots so playback and summaries work instantaneously offline.
 */
object LocalMediaCacheManager {

    private const val DIR_RECORDINGS = "call_recordings"
    private const val DIR_PHOTOS = "contact_photos"
    private const val DIR_TRANSCRIPTS = "transcripts_cache"
    private const val MIN_AUDIO_BYTES = 256L

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class CachedTranscriptSnapshot(
        val id: String,
        val contactId: String? = null,
        val phone: String,
        val contactName: String,
        val audioFileName: String,
        val audioFilePath: String,
        val timestampMs: Long,
        val durationSec: Long,
        val transcript: String,
        val oneLineSummary: String,
        val summaryBullets: List<String>,
        val highlights: List<String>,
        val extractedNotes: String,
        val segmentsJson: String,
        val actionItemsJson: String,
        val updatedAtEpochMs: Long,
    )

    fun recordingsDir(context: Context): File =
        File(context.applicationContext.filesDir, DIR_RECORDINGS).apply { mkdirs() }

    fun photosDir(context: Context): File =
        File(context.applicationContext.filesDir, DIR_PHOTOS).apply { mkdirs() }

    fun transcriptsCacheDir(context: Context): File =
        File(context.applicationContext.filesDir, DIR_TRANSCRIPTS).apply { mkdirs() }

    fun resolvePlayableAudioFile(
        context: Context,
        audioFilePath: String?,
        audioFileName: String? = null,
    ): File? {
        if (!audioFilePath.isNullOrBlank()) {
            val direct = File(audioFilePath)
            if (direct.exists() && direct.isFile && direct.length() > MIN_AUDIO_BYTES) {
                return direct
            }
        }
        val cleanName = audioFileName?.trim()?.takeIf { it.isNotBlank() }
            ?: audioFilePath?.let { File(it).name }?.takeIf { it.isNotBlank() }
            ?: return null
        val cached = File(recordingsDir(context), cleanName)
        return if (cached.exists() && cached.isFile && cached.length() > MIN_AUDIO_BYTES) cached else null
    }

    fun isAudioCachedLocally(
        context: Context,
        audioFilePath: String?,
        audioFileName: String? = null,
    ): Boolean = resolvePlayableAudioFile(context, audioFilePath, audioFileName) != null

    suspend fun ensureRecordingCachedLocally(
        context: Context,
        sourceFile: File,
    ): File? = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() <= MIN_AUDIO_BYTES) return@withContext null
        val targetDir = recordingsDir(context)
        if (sourceFile.parentFile?.canonicalPath == targetDir.canonicalPath) {
            return@withContext sourceFile
        }
        val targetFile = File(targetDir, sourceFile.name)
        runCatching {
            sourceFile.copyTo(targetFile, overwrite = true)
            targetFile
        }.getOrNull()
    }

    suspend fun cacheRecordingFromUri(
        context: Context,
        sourceUri: Uri,
        targetFileName: String,
    ): File? = withContext(Dispatchers.IO) {
        val safeName = targetFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank {
            "cached_${System.currentTimeMillis()}.m4a"
        }
        val destFile = File(recordingsDir(context), safeName)
        runCatching {
            context.applicationContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
            if (destFile.exists() && destFile.length() > MIN_AUDIO_BYTES) destFile else {
                destFile.delete()
                null
            }
        }.getOrNull()
    }

    suspend fun cacheTranscriptPayloadLocally(
        context: Context,
        record: CallTranscriptRecord,
    ): File? = withContext(Dispatchers.IO) {
        if (record.id.isBlank()) return@withContext null
        val safeId = record.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(transcriptsCacheDir(context), "$safeId.json")
        runCatching {
            val payload = CachedTranscriptSnapshot(
                id = record.id,
                contactId = record.contactId,
                phone = record.phone,
                contactName = record.contactName,
                audioFileName = record.audioFileName.ifBlank { File(record.audioFilePath).name },
                audioFilePath = record.audioFilePath,
                timestampMs = record.timestampMs,
                durationSec = record.durationSec,
                transcript = record.transcript,
                oneLineSummary = record.oneLineSummary,
                summaryBullets = record.summaryBullets,
                highlights = record.highlights,
                extractedNotes = record.extractedNotes,
                segmentsJson = TranscriptConverters.encodeSegments(record.segments),
                actionItemsJson = TranscriptConverters.encodeActionItems(record.actionItems),
                updatedAtEpochMs = record.updatedAtEpochMs,
            )
            file.writeText(json.encodeToString(payload))
            file
        }.getOrNull()
    }

    suspend fun deleteCachedMediaForTranscript(
        context: Context,
        recordId: String,
        audioFilePath: String?,
        audioFileName: String?,
    ) = withContext(Dispatchers.IO) {
        val safeId = recordId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        runCatching { File(transcriptsCacheDir(context), "$safeId.json").delete() }
        resolvePlayableAudioFile(context, audioFilePath, audioFileName)?.let { file ->
            runCatching { file.delete() }
        }
    }

    suspend fun computeCacheStats(context: Context): LocalMediaCacheStats = withContext(Dispatchers.IO) {
        val recFiles = recordingsDir(context).listFiles()?.filter { it.isFile }.orEmpty()
        val photoFiles = photosDir(context).listFiles()?.filter { it.isFile }.orEmpty()
        val transcriptFiles = transcriptsCacheDir(context).listFiles()?.filter { it.isFile }.orEmpty()
        LocalMediaCacheStats(
            recordingFilesCount = recFiles.size,
            recordingBytes = recFiles.sumOf { it.length() },
            photoFilesCount = photoFiles.size,
            photoBytes = photoFiles.sumOf { it.length() },
            transcriptCacheCount = transcriptFiles.size,
        )
    }

    suspend fun purgeOrphanedAudioFiles(
        context: Context,
        referencedAudioPathsOrNames: Set<String>,
    ): Int = withContext(Dispatchers.IO) {
        val files = recordingsDir(context).listFiles()?.filter { it.isFile }.orEmpty()
        var removed = 0
        for (file in files) {
            if (!referencedAudioPathsOrNames.contains(file.absolutePath) &&
                !referencedAudioPathsOrNames.contains(file.name)
            ) {
                if (runCatching { file.delete() }.getOrDefault(false)) {
                    removed++
                }
            }
        }
        removed
    }
}
