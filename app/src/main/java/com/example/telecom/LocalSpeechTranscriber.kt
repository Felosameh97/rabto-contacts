package com.example.telecom

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveTranscriptState(
    val isListening: Boolean = false,
    val confirmedText: String = "",
    val partialText: String = "",
    val rmsDb: Float = 0f,
    val errorNote: String? = null,
    val requiresAiRecordingFallback: Boolean = false,
) {
    val fullText: String
        get() = listOf(confirmedText, partialText)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
}

/**
 * Continuous real-time speech-to-text transcriber powered by Android SpeechRecognizer.
 * Automatically falls back across Arabic/English locales and flags in-call recording + Gemini AI
 * fallback whenever the device lacks an offline speech pack or restricts mic capture during calls.
 */
class LocalSpeechTranscriber(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var shouldKeepListening = false
    private var activeLanguageTag = "ar-EG"
    private var preferOnDeviceFallback = false
    private var retryCount = 0

    private val _state = MutableStateFlow(LiveTranscriptState())
    val state = _state.asStateFlow()

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context) ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))

    fun start(languageTag: String = "ar-EG") {
        mainHandler.post {
            if (!isAvailable()) {
                _state.value = _state.value.copy(
                    isListening = false,
                    requiresAiRecordingFallback = true,
                    errorNote = "تم تفعيل التفريغ الذكي التلقائي عبر تسجيل الصوت + Gemini AI",
                )
                return@post
            }
            activeLanguageTag = languageTag
            preferOnDeviceFallback = false
            retryCount = 0
            shouldKeepListening = true
            _state.value = _state.value.copy(
                isListening = true,
                requiresAiRecordingFallback = false,
                errorNote = null,
            )
            startCycle(activeLanguageTag)
        }
    }

    fun stop(): String {
        shouldKeepListening = false
        commitPartialIntoConfirmed()
        val current = _state.value.fullText
        mainHandler.post {
            runCatching {
                recognizer?.stopListening()
                recognizer?.cancel()
                recognizer?.destroy()
            }
            recognizer = null
            _state.value = _state.value.copy(
                isListening = false,
                confirmedText = current,
                partialText = "",
                rmsDb = 0f,
            )
        }
        return current
    }

    fun clear() {
        _state.value = LiveTranscriptState()
    }

    fun appendExternalText(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val updated = listOf(_state.value.confirmedText, clean)
            .filter { it.isNotBlank() }
            .joinToString(". ")
        _state.value = _state.value.copy(
            confirmedText = updated,
            partialText = "",
            errorNote = null,
        )
    }

    private fun commitPartialIntoConfirmed() {
        val partial = _state.value.partialText.trim()
        if (partial.isBlank()) return
        val existing = _state.value.confirmedText
        val merged = if (existing.isBlank()) partial else "$existing. $partial"
        _state.value = _state.value.copy(confirmedText = merged, partialText = "")
    }

    private fun nextFallbackLanguage(current: String): String? {
        return when (current) {
            "ar-EG" -> "ar-SA"
            "ar-SA" -> "ar"
            "ar" -> {
                val sys = Locale.getDefault().toLanguageTag()
                if (sys.isNotBlank() && !sys.startsWith("ar")) sys else "en-US"
            }
            "en-US" -> null
            else -> if (current != "en-US") "en-US" else null
        }
    }

    private fun createRecognizer(): SpeechRecognizer {
        return if (preferOnDeviceFallback &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }
                .getOrElse { SpeechRecognizer.createSpeechRecognizer(context) }
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
    }

    private fun startCycle(languageTag: String) {
        if (!shouldKeepListening) return
        runCatching { recognizer?.destroy() }
        val sr = runCatching { createRecognizer() }.getOrElse {
            shouldKeepListening = false
            _state.value = _state.value.copy(
                isListening = false,
                requiresAiRecordingFallback = true,
                errorNote = "تم التحويل للتفريغ الذكي عبر التسجيل الصوتي",
            )
            return
        }
        recognizer = sr

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                retryCount = 0
                _state.value = _state.value.copy(errorNote = null)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                _state.value = _state.value.copy(rmsDb = rmsdB.coerceIn(0f, 12f))
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (partial.isNotBlank()) {
                    _state.value = _state.value.copy(partialText = partial, errorNote = null)
                }
            }

            override fun onResults(results: Bundle?) {
                retryCount = 0
                val segment = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                val base = _state.value.confirmedText
                val updatedConfirmed = if (segment.isNotBlank()) {
                    listOf(base, segment).filter { it.isNotBlank() }.joinToString(". ")
                } else {
                    base
                }
                _state.value = _state.value.copy(
                    confirmedText = updatedConfirmed,
                    partialText = "",
                    errorNote = null,
                )
                if (shouldKeepListening) {
                    mainHandler.postDelayed({ startCycle(activeLanguageTag) }, 140L)
                }
            }

            override fun onError(error: Int) {
                commitPartialIntoConfirmed()
                if (!shouldKeepListening) return

                // Automatic language fallback when ar-EG model is missing / unavailable (12 / 13)
                if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                ) {
                    val fallback = nextFallbackLanguage(activeLanguageTag)
                    if (fallback != null) {
                        Log.w(TAG, "Language $activeLanguageTag unsupported ($error), switching to $fallback")
                        activeLanguageTag = fallback
                        mainHandler.postDelayed({ startCycle(activeLanguageTag) }, 180L)
                        return
                    }
                }

                // Switch to on-device recognizer if online reports network error and on-device exists
                if ((error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) &&
                    !preferOnDeviceFallback &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                ) {
                    preferOnDeviceFallback = true
                    Log.w(TAG, "Network error ($error), trying on-device SpeechRecognizer")
                    mainHandler.postDelayed({ startCycle(activeLanguageTag) }, 220L)
                    return
                }

                val isRecoverable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                    error == SpeechRecognizer.ERROR_CLIENT ||
                    error == SpeechRecognizer.ERROR_SERVER ||
                    error == 11 // ERROR_SERVER_DISCONNECTED (API 31+)

                if (isRecoverable && retryCount < 8) {
                    retryCount++
                    val delayMs = if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        220L
                    } else {
                        500L
                    }
                    mainHandler.postDelayed({ startCycle(activeLanguageTag) }, delayMs)
                    return
                }

                Log.w(TAG, "SpeechRecognizer switched to AI fallback on error=$error")
                val needsRecordingAi = error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                    error == SpeechRecognizer.ERROR_AUDIO ||
                    error == SpeechRecognizer.ERROR_NETWORK ||
                    error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                    error == 14 // ERROR_CANNOT_CHECK_SUPPORT / server restriction

                val msg = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "يرجى منح صلاحية الميكروفون"
                    SpeechRecognizer.ERROR_AUDIO,
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "تم التحويل تلقائياً للتسجيل الصوتي + التفريغ بـ Gemini AI"
                    else -> "توقف التفريغ المباشر — جاري حفظ الصوت للتفريغ بـ Gemini AI"
                }
                shouldKeepListening = false
                _state.value = _state.value.copy(
                    isListening = false,
                    rmsDb = 0f,
                    requiresAiRecordingFallback = needsRecordingAi,
                    errorNote = msg,
                )
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, activeLanguageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, activeLanguageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        runCatching { sr.startListening(intent) }.onFailure {
            shouldKeepListening = false
            _state.value = _state.value.copy(
                isListening = false,
                rmsDb = 0f,
                requiresAiRecordingFallback = true,
                errorNote = "تم التحويل للتفريغ الذكي عبر التسجيل الصوتي + Gemini AI",
            )
        }
    }

    companion object {
        private const val TAG = "LocalSpeechTranscriber"
    }
}
