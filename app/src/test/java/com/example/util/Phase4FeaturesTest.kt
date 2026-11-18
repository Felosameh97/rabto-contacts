package com.example.util

import com.example.ai.LocalTranscriptAnalyzer
import com.example.domain.model.CallTranscriptRecord
import com.example.domain.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4FeaturesTest {

    @Test
    fun activeSegmentLookupTracksPlaybackPosition() {
        val segments = listOf(
            TranscriptSegment(startMs = 0L, endMs = 3000L, speaker = "أحمد", text = "السلام عليكم أخبارك إيه"),
            TranscriptSegment(startMs = 3000L, endMs = 7000L, speaker = "أنت", text = "الحمد لله تمام"),
            TranscriptSegment(startMs = 8000L, endMs = 12000L, speaker = "أحمد", text = "موعدنا بكرة الساعة 5"),
        )

        assertEquals(-1, TranscriptPlaybackHighlighter.findActiveSegmentIndex(segments, -100L))
        assertEquals(0, TranscriptPlaybackHighlighter.findActiveSegmentIndex(segments, 0L))
        assertEquals(0, TranscriptPlaybackHighlighter.findActiveSegmentIndex(segments, 2500L))
        assertEquals(1, TranscriptPlaybackHighlighter.findActiveSegmentIndex(segments, 3000L))
        assertEquals(1, TranscriptPlaybackHighlighter.findActiveSegmentIndex(segments, 7500L))
        assertEquals(2, TranscriptPlaybackHighlighter.findActiveSegmentIndex(segments, 9500L))
        assertEquals(2, TranscriptPlaybackHighlighter.findActiveSegmentIndex(segments, 15000L))
    }

    @Test
    fun activeWordRangeProgressesAcrossArabicAndEnglishSegments() {
        val seg = TranscriptSegment(
            startMs = 1000L,
            endMs = 5000L,
            speaker = "Kareem",
            text = "موعدنا بكرة الساعة خمسة مساءً",
        )

        assertNull(TranscriptPlaybackHighlighter.computeActiveWordRange(seg, 500L))

        val firstRange = TranscriptPlaybackHighlighter.computeActiveWordRange(seg, 1100L)
        assertNotNull(firstRange)
        assertEquals("موعدنا", seg.text.substring(firstRange!!))

        val midRange = TranscriptPlaybackHighlighter.computeActiveWordRange(seg, 3100L)
        assertNotNull(midRange)
        assertEquals("الساعة", seg.text.substring(midRange!!))

        val lastRange = TranscriptPlaybackHighlighter.computeActiveWordRange(seg, 4950L)
        assertNotNull(lastRange)
        assertEquals("مساءً", seg.text.substring(lastRange!!))
    }

    @Test
    fun fallbackSegmentsAreGeneratedWhenTranscriptHasOnlyPlainText() {
        val rec = CallTranscriptRecord(
            id = "tr_plain",
            contactName = "عمر",
            phone = "01012345678",
            durationSec = 12L,
            transcript = "السلام عليكم. اتفقنا نتقابل بكرة الساعة 4. هبعتلك التفاصيل",
        )
        val segments = TranscriptPlaybackHighlighter.resolveDisplaySegments(rec)
        assertEquals(3, segments.size)
        assertEquals("عمر", segments[0].speaker)
        assertEquals("أنت", segments[1].speaker)
        assertEquals(0L, segments[0].startMs)
        assertTrue(segments[2].endMs >= 12000L)
    }

    @Test
    fun formatPlaybackClockHandlesMinutesAndHours() {
        assertEquals("00:00", TranscriptPlaybackHighlighter.formatPlaybackClock(0L))
        assertEquals("01:05", TranscriptPlaybackHighlighter.formatPlaybackClock(65_000L))
        assertEquals("1:01:05", TranscriptPlaybackHighlighter.formatPlaybackClock(3_665_000L))
    }

    @Test
    fun localTranscriptAnalyzerExtractsBulletsSegmentsAndActionItems() {
        val analysis = LocalTranscriptAnalyzer.analyze(
            rawTranscript = "اتفقنا نبعت 500 جنيه فودافون كاش النهاردة. وهنتقابل بكرة الساعة 3 في المكتب.",
            contactName = "مصطفى",
            phone = "01098765432",
            referenceTimeMs = 1_750_000_000_000L,
            durationSec = 20L,
        )
        assertTrue(analysis.summaryBullets.isNotEmpty())
        assertTrue(analysis.oneLineSummary.isNotBlank())
        assertTrue(analysis.segments.size >= 2)
        assertTrue(analysis.actionItems.any { it.kind == "CALENDAR" })
        assertTrue(analysis.actionItems.any { it.kind == "PAYMENT" })
    }
}
