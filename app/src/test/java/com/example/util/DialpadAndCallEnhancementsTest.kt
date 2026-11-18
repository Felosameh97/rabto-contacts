package com.example.util

import android.media.ToneGenerator
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.telecom.ActiveCallUiState
import com.example.telecom.ConferenceParticipantUi
import com.example.telecom.OtherCallSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DialpadAndCallEnhancementsTest {

    @Test
    fun insertAtCursorInsertsAtMiddleAndUpdatesSelection() {
        val initial = TextFieldValue(text = "0101234", selection = TextRange(3))
        val updated = DialpadCursorHelper.insertAtCursor(initial, "9")
        assertEquals("01091234", updated.text)
        assertEquals(TextRange(4), updated.selection)
    }

    @Test
    fun insertAtCursorReplacesSelectedRange() {
        val initial = TextFieldValue(text = "0105556789", selection = TextRange(3, 6))
        val updated = DialpadCursorHelper.insertAtCursor(initial, "123")
        assertEquals("0101236789", updated.text)
        assertEquals(TextRange(6), updated.selection)
    }

    @Test
    fun backspaceAtCursorDeletesBeforeCursorAndStopsAtZero() {
        val middle = TextFieldValue(text = "01099", selection = TextRange(2))
        val afterMiddle = DialpadCursorHelper.backspaceAtCursor(middle)
        assertEquals("0099", afterMiddle.text)
        assertEquals(TextRange(1), afterMiddle.selection)

        val atZero = TextFieldValue(text = "0099", selection = TextRange(0))
        val afterZero = DialpadCursorHelper.backspaceAtCursor(atZero)
        assertEquals("0099", afterZero.text)
        assertEquals(TextRange(0), afterZero.selection)
    }

    @Test
    fun backspaceAtCursorDeletesActiveSelection() {
        val selected = TextFieldValue(text = "+20101234", selection = TextRange(1, 4))
        val updated = DialpadCursorHelper.backspaceAtCursor(selected)
        assertEquals("+01234", updated.text)
        assertEquals(TextRange(1), updated.selection)
    }

    @Test
    fun pasteAtCursorSanitizesAndInsertsAtCursor() {
        val current = TextFieldValue(text = "01088", selection = TextRange(3))
        val pasted = DialpadCursorHelper.pasteAtCursor(current, "Tel: +20 (123)-45#*")
        assertEquals("010+2012345#*88", pasted.text)
        assertEquals(TextRange(13), pasted.selection)
    }

    @Test
    fun clearAllResetsTextAndCursor() {
        val cleared = DialpadCursorHelper.clearAll()
        assertEquals("", cleared.text)
        assertEquals(TextRange(0), cleared.selection)
    }

    @Test
    fun toneForDialCharMapsAllDialpadKeys() {
        assertEquals(ToneGenerator.TONE_DTMF_0, DialpadCursorHelper.toneForDialChar('0'))
        assertEquals(ToneGenerator.TONE_DTMF_9, DialpadCursorHelper.toneForDialChar('9'))
        assertEquals(ToneGenerator.TONE_DTMF_S, DialpadCursorHelper.toneForDialChar('*'))
        assertEquals(ToneGenerator.TONE_DTMF_P, DialpadCursorHelper.toneForDialChar('#'))
        assertNotNull(DialpadCursorHelper.toneForDialChar('+'))
        assertNull(DialpadCursorHelper.toneForDialChar('a'))
    }

    @Test
    fun activeCallUiStateSupportsConferenceAndSecondaryCall() {
        val state = ActiveCallUiState(
            hasActiveCall = true,
            callState = Call.STATE_ACTIVE,
            activeAudioRoute = CallAudioState.ROUTE_BLUETOOTH,
            supportedAudioMask = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER or CallAudioState.ROUTE_BLUETOOTH,
            secondaryCall = OtherCallSummary(
                callId = "call_2",
                phoneNumber = "01022223333",
                displayName = "Omar",
                callState = Call.STATE_HOLDING,
            ),
            canSwapCalls = true,
            canMergeConference = true,
            isConferenceCall = true,
            conferenceParticipants = listOf(
                ConferenceParticipantUi("p1", "01011112222", "Dad", canSeparate = true, canDisconnect = true),
                ConferenceParticipantUi("p2", "01022223333", "Omar", canSeparate = true, canDisconnect = true),
            ),
        )

        assertTrue(state.isConferenceCall)
        assertEquals(2, state.conferenceParticipants.size)
        assertTrue(state.canMergeConference)
        assertTrue(state.canSwapCalls)
        assertFalse(state.isRecording)
        assertEquals(CallAudioState.ROUTE_BLUETOOTH, state.activeAudioRoute)
    }
}
