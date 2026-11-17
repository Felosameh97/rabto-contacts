package com.example.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RecordingSourceMode(val labelAr: String) {
    SMART_AUTO("تلقائي ذكي (الأعلى جودة)"),
    VOICE_RECOGNITION("ميكروفون المكالمات (موصى به)"),
    CAMCORDER("ميكروفون الكاميرا الواسع (ممتاز للسبيكر)"),
    MIC("الميكروفون القياسي"),
    VOICE_CALL("خط المكالمة الداخلي (VOICE_CALL)"),
}

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

    @Volatile
    private var currentSourceName: String = ""

    @Volatile
    private var currentLabel: String = "call"

    @Volatile
    private var currentMode: RecordingSourceMode = RecordingSourceMode.SMART_AUTO

    @Volatile
    private var hasSwappedSilentSource: Boolean = false

    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var amplitudeJob: Job? = null

    private val _amplitudeFlow = MutableStateFlow(0)
    val amplitudeFlow = _amplitudeFlow.asStateFlow()

    private val _activeSourceFlow = MutableStateFlow("")
    val activeSourceFlow = _activeSourceFlow.asStateFlow()

    fun hasRecordPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    fun recordingsDir(context: Context): File {
        return File(context.filesDir, "call_recordings").apply { mkdirs() }
    }

    @Synchronized
    fun startRecording(
        context: Context,
        labelOrNumber: String,
        sourceMode: RecordingSourceMode = RecordingSourceMode.SMART_AUTO,
        allowSilentSwap: Boolean = true,
    ): Result<File> {
        if (!hasRecordPermission(context)) {
            return Result.failure(SecurityException("صلاحية الميكروفون (RECORD_AUDIO) غير ممنوحة — اضغط للتفعيل"))
        }
        if (recorder != null) {
            currentFile?.let { return Result.success(it) }
        }

        val cleanLabel = labelOrNumber
            .replace(Regex("[^\\p{L}\\p{N}_+-]"), "_")
            .trim('_')
            .take(36)
            .ifBlank { "call" }
        currentLabel = cleanLabel
        currentMode = sourceMode
        hasSwappedSilentSource = !allowSilentSwap

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(recordingsDir(context), "rabta_${cleanLabel}_$stamp.m4a")

        val sources = orderedSourcesFor(sourceMode)
        val started = startWithOrderedSources(context.applicationContext, outFile, sources)
        return if (started.isSuccess) {
            startAmplitudeMonitor(context.applicationContext, allowSilentSwap)
            started
        } else {
            runCatching { outFile.delete() }
            started
        }
    }

    fun orderedSourcesFor(mode: RecordingSourceMode): List<Pair<Int, String>> {
        return when (mode) {
            RecordingSourceMode.SMART_AUTO -> listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
                MediaRecorder.AudioSource.CAMCORDER to "CAMCORDER",
                MediaRecorder.AudioSource.MIC to "MIC",
                MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION",
                MediaRecorder.AudioSource.VOICE_CALL to "VOICE_CALL",
            )
            RecordingSourceMode.VOICE_RECOGNITION -> listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
                MediaRecorder.AudioSource.CAMCORDER to "CAMCORDER",
                MediaRecorder.AudioSource.MIC to "MIC",
            )
            RecordingSourceMode.CAMCORDER -> listOf(
                MediaRecorder.AudioSource.CAMCORDER to "CAMCORDER",
                MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
                MediaRecorder.AudioSource.MIC to "MIC",
            )
            RecordingSourceMode.MIC -> listOf(
                MediaRecorder.AudioSource.MIC to "MIC",
                MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
                MediaRecorder.AudioSource.CAMCORDER to "CAMCORDER",
            )
            RecordingSourceMode.VOICE_CALL -> listOf(
                MediaRecorder.AudioSource.VOICE_CALL to "VOICE_CALL",
                MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
                MediaRecorder.AudioSource.CAMCORDER to "CAMCORDER",
                MediaRecorder.AudioSource.MIC to "MIC",
            )
        }
    }

    @Synchronized
    private fun startWithOrderedSources(
        appContext: Context,
        outFile: File,
        sources: List<Pair<Int, String>>,
    ): Result<File> {
        // Prefer standard 44.1kHz / 96kbps AAC-LC first so Exynos/MediaTek encoders never mute audio;
        // fall back to 16kHz / 32kbps only if the hardware rejects 44.1kHz on that source.
        val audioProfiles = listOf(44_100 to 96_000, 16_000 to 32_000)
        var lastError: Throwable? = null

        for ((source, sourceName) in sources) {
            for ((sampleRate, bitRate) in audioProfiles) {
                val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(appContext)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                try {
                    mr.setAudioSource(source)
                    mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    mr.setAudioChannels(1)
                    mr.setAudioSamplingRate(sampleRate)
                    mr.setAudioEncodingBitRate(bitRate)
                    mr.setOutputFile(outFile.absolutePath)
                    mr.prepare()
                    mr.start()
                    runCatching { mr.maxAmplitude } // prime maxAmplitude baseline
                    recorder = mr
                    currentFile = outFile
                    currentSourceName = sourceName
                    _activeSourceFlow.value = sourceName
                    Log.d(TAG, "Recording started via $sourceName ($sampleRate Hz) -> ${outFile.name}")
                    return Result.success(outFile)
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "AudioSource $sourceName ($sampleRate Hz) failed: ${e.message}")
                    runCatching { mr.release() }
                }
            }
        }
        return Result.failure(
            IllegalStateException(
                "تعذر فتح مسار التسجيل (${lastError?.localizedMessage ?: "مقيد من النظام"}) — جرّب التفريغ المباشر أو السبيكر",
                lastError,
            )
        )
    }

    private fun startAmplitudeMonitor(appContext: Context, allowSilentSwap: Boolean) {
        amplitudeJob?.cancel()
        amplitudeJob = monitorScope.launch {
            var strictZeroCount = 0
            while (isActive && recorder != null) {
                delay(220L)
                val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                _amplitudeFlow.value = amp
                if (amp == 0) {
                    strictZeroCount++
                    // If strict digital zero for >2.6s (e.g. OEM blocked VOICE_CALL silently), hot-swap once to CAMCORDER/VOICE_RECOGNITION
                    if (allowSilentSwap && !hasSwappedSilentSource && strictZeroCount >= 12 && currentSourceName == "VOICE_CALL") {
                        hasSwappedSilentSource = true
                        Log.w(TAG, "Detected silent 0-amplitude stream on VOICE_CALL — hot-swapping to CAMCORDER")
                        hotSwapToFallbackSource(appContext)
                        break
                    }
                } else {
                    strictZeroCount = 0
                }
            }
        }
    }

    @Synchronized
    private fun hotSwapToFallbackSource(appContext: Context) {
        val oldMr = recorder ?: return
        val oldFile = currentFile
        recorder = null
        currentFile = null
        runCatching { oldMr.stop() }
        runCatching { oldMr.release() }
        oldFile?.let { if (it.exists() && it.length() < 4096L) runCatching { it.delete() } }

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val nextFile = File(recordingsDir(appContext), "rabta_${currentLabel}_$stamp.m4a")
        val fallbackSources = listOf(
            MediaRecorder.AudioSource.CAMCORDER to "CAMCORDER",
            MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
            MediaRecorder.AudioSource.MIC to "MIC",
        )
        startWithOrderedSources(appContext, nextFile, fallbackSources).onSuccess {
            startAmplitudeMonitor(appContext, allowSilentSwap = false)
        }
    }

    @Synchronized
    fun stopRecording(): File? {
        amplitudeJob?.cancel()
        amplitudeJob = null
        _amplitudeFlow.value = 0
        _activeSourceFlow.value = ""
        val mr = recorder ?: return null
        val file = currentFile
        recorder = null
        currentFile = null
        currentSourceName = ""
        runCatching {
            mr.stop()
        }.onFailure {
            Log.w(TAG, "MediaRecorder stop warning: ${it.message}")
        }
        runCatching { mr.release() }
        return if (file != null && file.exists() && file.length() > 256) {
            file
        } else {
            file?.delete()
            null
        }
    }

    /**
     * Unified reliable MediaPlayer helper enforcing USAGE_MEDIA + CONTENT_TYPE_SPEECH
     * and normal AudioManager routing so recorded voice files play audibly through speaker.
     */
    fun playAudioFile(
        context: Context,
        file: File,
        onStart: () -> Unit = {},
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {},
    ): MediaPlayer? {
        if (!file.exists() || file.length() <= 256L) {
            onError("ملف الصوت فارغ أو غير موجود")
            return null
        }
        return runCatching {
            val am = context.getSystemService(AudioManager::class.java)
            runCatching {
                if (am != null && am.mode != AudioManager.MODE_IN_CALL) {
                    am.mode = AudioManager.MODE_NORMAL
                }
            }
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setVolume(1.0f, 1.0f)
                setOnCompletionListener {
                    runCatching { release() }
                    onComplete()
                }
                setOnErrorListener { mp, what, extra ->
                    runCatching { mp.release() }
                    onError("تعذر تشغيل التسجيل (كود $what/$extra)")
                    true
                }
                prepare()
                start()
                onStart()
            }
        }.getOrElse { err ->
            onError(err.localizedMessage ?: "تعذر تشغيل التسجيل")
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
