package com.example.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CallRecordingItem(
    val file: File,
    val displayName: String,
    val timestampMs: Long,
    val sizeKb: Long,
)

object CallRecorderManager {
    private const val TAG = "CallRecorderManager"

    @Volatile
    private var recorder: MediaRecorder? = null

    @Volatile
    private var currentFile: File? = null

    fun hasRecordPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    fun recordingsDir(context: Context): File {
        return File(context.filesDir, "call_recordings").apply { mkdirs() }
    }

    @Synchronized
    fun startRecording(context: Context, labelOrNumber: String): Result<File> {
        if (!hasRecordPermission(context)) {
            return Result.failure(SecurityException("صلاحية الميكروفون (RECORD_AUDIO) غير ممنوحة"))
        }
        if (recorder != null) {
            currentFile?.let { return Result.success(it) }
        }

        val cleanLabel = labelOrNumber
            .replace(Regex("[^\\p{L}\\p{N}_+-]"), "_")
            .trim('_')
            .take(36)
            .ifBlank { "call" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(recordingsDir(context), "rabta_${cleanLabel}_$stamp.m4a")

        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
        )

        var lastError: Throwable? = null
        for (source in sources) {
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context.applicationContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            try {
                mr.setAudioSource(source)
                mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mr.setAudioEncodingBitRate(96_000)
                mr.setAudioSamplingRate(44_100)
                mr.setOutputFile(outFile.absolutePath)
                mr.prepare()
                mr.start()
                recorder = mr
                currentFile = outFile
                return Result.success(outFile)
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "AudioSource $source failed: ${e.message}")
                runCatching { mr.release() }
            }
        }
        runCatching { outFile.delete() }
        return Result.failure(lastError ?: IllegalStateException("تعذر بدء تسجيل المكالمة على هذا الجهاز"))
    }

    @Synchronized
    fun stopRecording(): File? {
        val mr = recorder ?: return null
        val file = currentFile
        recorder = null
        currentFile = null
        runCatching {
            mr.stop()
        }.onFailure {
            Log.w(TAG, "MediaRecorder stop warning: ${it.message}")
        }
        runCatching { mr.release() }
        return if (file != null && file.exists() && file.length() > 512) {
            file
        } else {
            file?.delete()
            null
        }
    }

    fun listRecordings(context: Context): List<CallRecordingItem> {
        val files = recordingsDir(context).listFiles()?.filter { it.isFile && it.length() > 0 }.orEmpty()
        return files
            .sortedByDescending { it.lastModified() }
            .map { file ->
                CallRecordingItem(
                    file = file,
                    displayName = file.nameWithoutExtension.removePrefix("rabta_").replace('_', ' '),
                    timestampMs = file.lastModified(),
                    sizeKb = (file.length() / 1024L).coerceAtLeast(1L),
                )
            }
    }

    fun deleteRecording(file: File): Boolean {
        return runCatching { file.delete() }.getOrDefault(false)
    }
}
