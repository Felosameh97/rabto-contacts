package com.example.util

import com.example.domain.model.CallTranscriptRecord
import com.example.domain.model.TranscriptSegment
import java.util.Locale

object TranscriptPlaybackHighlighter {

    private val wordRegex = Regex("""\S+""")

    fun formatPlaybackClock(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    fun wordRanges(text: String): List<IntRange> {
        if (text.isBlank()) return emptyList()
        return wordRegex.findAll(text).map { it.range }.toList()
    }

    fun findActiveSegmentIndex(segments: List<TranscriptSegment>, positionMs: Long): Int {
        if (segments.isEmpty() || positionMs < 0L) return -1
        val exact = segments.indexOfFirst { seg ->
            val end = seg.endMs.coerceAtLeast(seg.startMs + 500L)
            positionMs in seg.startMs until end
        }
        if (exact >= 0) return exact
        // If position is slightly past the last segment or between gaps, pick the closest preceding segment
        var lastBefore = -1
        for (i in segments.indices) {
            if (positionMs >= segments[i].startMs) {
                lastBefore = i
            } else {
                break
            }
        }
        return lastBefore
    }

    fun computeActiveWordRange(segment: TranscriptSegment, positionMs: Long): IntRange? {
        val ranges = wordRanges(segment.text)
        if (ranges.isEmpty()) return null
        val start = segment.startMs.coerceAtLeast(0L)
        val end = segment.endMs.coerceAtLeast(start + 400L)
        if (positionMs < start) return null
        if (positionMs >= end) return ranges.last()
        val progress = ((positionMs - start).toDouble() / (end - start).toDouble()).coerceIn(0.0, 0.9999)
        val idx = (progress * ranges.size).toInt().coerceIn(0, ranges.lastIndex)
        return ranges[idx]
    }

    fun resolveDisplaySegments(
        record: CallTranscriptRecord,
        fallbackTotalDurationMs: Long = 0L,
    ): List<TranscriptSegment> {
        if (record.segments.isNotEmpty()) {
            return record.segments.sortedBy { it.startMs }
        }
        val clean = record.transcript.trim()
        if (clean.isBlank()) return emptyList()
        val sentences = clean
            .split(Regex("[.\\n،؛!?]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val lines = if (sentences.isEmpty()) listOf(clean) else sentences
        val durationMs = when {
            fallbackTotalDurationMs > 0L -> fallbackTotalDurationMs
            record.durationSec > 0L -> record.durationSec * 1000L
            else -> (lines.size * 3500L).coerceAtLeast(3500L)
        }
        val stepMs = (durationMs / lines.size.coerceAtLeast(1)).coerceAtLeast(1200L)
        val speakerLabel = record.contactName.ifBlank { record.phone.ifBlank { "المتصل" } }
        return lines.mapIndexed { idx, line ->
            val start = idx * stepMs
            val end = if (idx == lines.lastIndex) durationMs.coerceAtLeast(start + stepMs) else start + stepMs
            TranscriptSegment(
                startMs = start,
                endMs = end,
                speaker = if (idx % 2 == 0) speakerLabel else "أنت",
                text = line,
            )
        }
    }
}
