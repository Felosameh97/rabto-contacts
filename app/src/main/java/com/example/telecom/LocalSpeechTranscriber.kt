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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveTranscriptState(
    val isListening: Boolean = false,
    val confirmedText: String = "",
    val partialText: String = "",
    val errorNote: String? = null,
) {
    val fullText: String
        get() = listOf(confirmedText, partialText)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
}

/**
 * Continuous real-time speech-to-text transcriber powered by Android SpeechRecognizer.
 * Uses zero Gemini tokens and streams partial results live on the Main looper.
 */
class LocalSpeechTranscriber(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var shouldKeepListening = false

    private val _state = MutableStateFlow(LiveTranscriptState())
    val state = _state.asStateFlow()

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(languageTag: String = "ar-EG") {
        mainHandler.post {
            if (!isAvailable()) {
                _state.value = _state.value.copy(
                    isListening = false,
                    errorNote = "خدمة التعرف على الكلام غير متوفرة على هذا الجهاز",
                )
                return@post
            }
            shouldKeepListening = true
            _state.value = _state.value.copy(isListening = true, errorNote = null)
            startCycle(languageTag)
        }
    }

    fun stop(): String {
        shouldKeepListening = false
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
            )
        }
        return current
    }

    fun clear() {
        _state.value = LiveTranscriptState()
    }

    private fun startCycle(languageTag: String) {
        if (!shouldKeepListening) return
        runCatching { recognizer?.destroy() }
        val sr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }
                .getOrElse { SpeechRecognizer.createSpeechRecognizer(context) }
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer = sr

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
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
                val segment = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                val updatedConfirmed = if (segment.isNotBlank()) {
                    listOf(_state.value.confirmedText, segment)
                        .filter { it.isNotBlank() }
                        .joinToString(". ")
                } else {
                    _state.value.confirmedText
                }
                _state.value = _state.value.copy(
                    confirmedText = updatedConfirmed,
                    partialText = "",
                    errorNote = null,
                )
                if (shouldKeepListening) {
                    mainHandler.postDelayed({ startCycle(languageTag) }, 120L)
                }
            }

            override fun onError(error: Int) {
                val isRecoverable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                if (isRecoverable && shouldKeepListening) {
                    mainHandler.postDelayed({ startCycle(languageTag) }, 220L)
                } else {
                    Log.w(TAG, "SpeechRecognizer error=$error")
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "يرجى منح صلاحية الميكروفون"
                        SpeechRecognizer.ERROR_AUDIO -> "الميكروفون مشغول بتطبيق آخر (مكالمة/مسجل)"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "تحقق من الإنترنت أو حمّل حزمة اللغة العربية Offline"
                        else -> "توقف التفريغ المباشر (كود $error)"
                    }
                    shouldKeepListening = false
                    _state.value = _state.value.copy(
                        isListening = false,
                        errorNote = msg,
                    )
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { sr.startListening(intent) }.onFailure { err ->
            Log.w(TAG, "startListening failed: ${err.message}")
            shouldKeepListening = false
            _state.value = _state.value.copy(
                isListening = false,
                errorNote = err.localizedMessage ?: "تعذر بدء الميكروفون",
            )
        }
    }

    companion object {
        private const val TAG = "LocalSpeechTranscriber"
    }
}
