package com.example.util

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Pure cursor-aware editing helpers for the dialpad's movable-cursor input bar.
 */
object DialpadCursorHelper {

    fun insertAtCursor(current: TextFieldValue, insertion: String): TextFieldValue {
        if (insertion.isEmpty()) return current
        val text = current.text
        val start = current.selection.min.coerceIn(0, text.length)
        val end = current.selection.max.coerceIn(start, text.length)
        val newText = text.substring(0, start) + insertion + text.substring(end)
        val newCursor = (start + insertion.length).coerceIn(0, newText.length)
        return TextFieldValue(
            text = newText,
            selection = TextRange(newCursor),
        )
    }

    fun backspaceAtCursor(current: TextFieldValue): TextFieldValue {
        val text = current.text
        if (text.isEmpty()) return current
        val start = current.selection.min.coerceIn(0, text.length)
        val end = current.selection.max.coerceIn(start, text.length)
        return if (start != end) {
            val newText = text.substring(0, start) + text.substring(end)
            TextFieldValue(text = newText, selection = TextRange(start.coerceIn(0, newText.length)))
        } else if (start > 0) {
            val newText = text.substring(0, start - 1) + text.substring(start)
            TextFieldValue(text = newText, selection = TextRange((start - 1).coerceIn(0, newText.length)))
        } else {
            current
        }
    }

    fun pasteAtCursor(current: TextFieldValue, rawClip: String): TextFieldValue {
        val sanitized = rawClip.filter { it.isDigit() || it in setOf('+', '*', '#') }
        if (sanitized.isEmpty()) return current
        return insertAtCursor(current, sanitized)
    }

    fun clearAll(): TextFieldValue = TextFieldValue(text = "", selection = TextRange(0))

    fun toneForDialChar(ch: Char): Int? = when (ch) {
        '0' -> ToneGenerator.TONE_DTMF_0
        '1' -> ToneGenerator.TONE_DTMF_1
        '2' -> ToneGenerator.TONE_DTMF_2
        '3' -> ToneGenerator.TONE_DTMF_3
        '4' -> ToneGenerator.TONE_DTMF_4
        '5' -> ToneGenerator.TONE_DTMF_5
        '6' -> ToneGenerator.TONE_DTMF_6
        '7' -> ToneGenerator.TONE_DTMF_7
        '8' -> ToneGenerator.TONE_DTMF_8
        '9' -> ToneGenerator.TONE_DTMF_9
        '*' -> ToneGenerator.TONE_DTMF_S
        '#' -> ToneGenerator.TONE_DTMF_P
        '+' -> ToneGenerator.TONE_DTMF_0
        else -> null
    }
}

class DialpadTonePlayer {
    private val generator: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_DTMF, 65)
    }.getOrNull()

    fun play(ch: Char) {
        val tone = DialpadCursorHelper.toneForDialChar(ch) ?: return
        runCatching { generator?.startTone(tone, 110) }
    }

    fun release() {
        runCatching { generator?.release() }
    }
}

@Composable
fun rememberDialpadTonePlayer(): DialpadTonePlayer {
    val player = remember { DialpadTonePlayer() }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    return player
}
