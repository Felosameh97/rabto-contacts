package com.example.ui.components

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.CallTranscriptRecord
import com.example.domain.model.TranscriptSegment
import com.example.util.SystemIntents
import com.example.util.TranscriptPlaybackHighlighter
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun InteractiveTranscriptPlayer(
    record: CallTranscriptRecord,
    playableFile: File?,
    isActivePlayer: Boolean = true,
    onRequestFocus: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var mediaPlayer by remember(record.id, playableFile?.absolutePath) { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember(record.id, playableFile?.absolutePath) { mutableStateOf(false) }
    var positionMs by remember(record.id, playableFile?.absolutePath) {
        mutableLongStateOf(0L)
    }
    var totalDurationMs by remember(record.id, playableFile?.absolutePath) {
        mutableLongStateOf((record.durationSec * 1000L).coerceAtLeast(0L))
    }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubPreviewMs by remember { mutableLongStateOf(0L) }

    fun releasePlayer() {
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        isPlaying = false
    }

    LaunchedEffect(isActivePlayer) {
        if (!isActivePlayer && isPlaying) {
            runCatching { mediaPlayer?.pause() }
            isPlaying = false
        }
    }

    DisposableEffect(record.id, playableFile?.absolutePath) {
        onDispose {
            releasePlayer()
        }
    }

    // Real-time playback polling (100ms) for smooth slider & word-by-word highlight sync
    LaunchedEffect(isPlaying, mediaPlayer) {
        while (isPlaying) {
            val mp = mediaPlayer
            if (mp != null && !isUserScrubbing) {
                val cur = runCatching { mp.currentPosition.toLong() }.getOrDefault(positionMs)
                val dur = runCatching { mp.duration.toLong() }.getOrDefault(totalDurationMs)
                positionMs = cur.coerceAtLeast(0L)
                if (dur > 0L) totalDurationMs = dur
            }
            delay(100L)
        }
    }

    fun applySpeed(mp: MediaPlayer, speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                mp.playbackParams = mp.playbackParams.setSpeed(speed)
            }
        }
    }

    fun startOrSeekPlayback(seekTargetMs: Long? = null) {
        val file = playableFile ?: return
        if (!file.exists() || file.length() <= 256L) {
            Toast.makeText(context, "ملف الصوت غير متاح محلياً", Toast.LENGTH_SHORT).show()
            return
        }
        onRequestFocus()
        val existing = mediaPlayer
        if (existing != null) {
            if (seekTargetMs != null) {
                val clamped = seekTargetMs.coerceIn(0L, totalDurationMs.coerceAtLeast(seekTargetMs))
                runCatching { existing.seekTo(clamped.toInt()) }
                positionMs = clamped
                if (!isPlaying) {
                    applySpeed(existing, playbackSpeed)
                    runCatching { existing.start() }
                    isPlaying = true
                }
            } else if (isPlaying) {
                runCatching { existing.pause() }
                isPlaying = false
            } else {
                applySpeed(existing, playbackSpeed)
                runCatching { existing.start() }
                isPlaying = true
            }
            return
        }

        runCatching {
            val am = context.getSystemService(AudioManager::class.java)
            runCatching {
                if (am != null && am.mode != AudioManager.MODE_IN_CALL) {
                    am.mode = AudioManager.MODE_NORMAL
                }
            }
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setVolume(1.0f, 1.0f)
                setOnCompletionListener {
                    isPlaying = false
                    positionMs = totalDurationMs
                }
                setOnErrorListener { failedMp, what, extra ->
                    runCatching { failedMp.release() }
                    mediaPlayer = null
                    isPlaying = false
                    Toast.makeText(context, "تعذر تشغيل التسجيل ($what/$extra)", Toast.LENGTH_SHORT).show()
                    true
                }
                prepare()
                val dur = runCatching { duration.toLong() }.getOrDefault(0L)
                if (dur > 0L) totalDurationMs = dur
                if (seekTargetMs != null && seekTargetMs > 0L) {
                    val clamped = seekTargetMs.coerceIn(0L, totalDurationMs.coerceAtLeast(seekTargetMs))
                    seekTo(clamped.toInt())
                    positionMs = clamped
                }
                applySpeed(this, playbackSpeed)
                start()
            }
            mediaPlayer = mp
            isPlaying = true
        }.onFailure { err ->
            isPlaying = false
            Toast.makeText(context, err.localizedMessage ?: "تعذر تشغيل التسجيل", Toast.LENGTH_SHORT).show()
        }
    }

    fun seekRelative(deltaMs: Long) {
        val base = if (isUserScrubbing) scrubPreviewMs else positionMs
        val maxDur = totalDurationMs.coerceAtLeast(1L)
        val target = (base + deltaMs).coerceIn(0L, maxDur)
        positionMs = target
        mediaPlayer?.let { mp ->
            runCatching { mp.seekTo(target.toInt()) }
        }
    }

    fun cycleSpeed() {
        val next = when {
            playbackSpeed < 1.2f -> 1.25f
            playbackSpeed < 1.45f -> 1.5f
            playbackSpeed < 1.9f -> 2.0f
            else -> 1.0f
        }
        playbackSpeed = next
        mediaPlayer?.let { applySpeed(it, next) }
    }

    val segments = remember(record.segments, record.transcript, totalDurationMs, record.durationSec) {
        TranscriptPlaybackHighlighter.resolveDisplaySegments(record, totalDurationMs)
    }

    val displayedPosMs = if (isUserScrubbing) scrubPreviewMs else positionMs
    val activeSegmentIdx = remember(segments, displayedPosMs, isPlaying, mediaPlayer) {
        if (mediaPlayer == null && displayedPosMs == 0L && !isPlaying) {
            -1
        } else {
            TranscriptPlaybackHighlighter.findActiveSegmentIndex(segments, displayedPosMs)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (playableFile != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("interactive_player_bar_${record.id}"),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalIconButton(
                            onClick = { startOrSeekPlayback(null) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("player_play_pause_${record.id}"),
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                            )
                        }

                        IconButton(
                            onClick = { seekRelative(-10_000L) },
                            enabled = mediaPlayer != null,
                            modifier = Modifier.size(40.dp).testTag("player_rew_10_${record.id}"),
                        ) {
                            Icon(Icons.Rounded.Replay10, contentDescription = "رجوع 10 ثواني")
                        }

                        IconButton(
                            onClick = { seekRelative(10_000L) },
                            enabled = mediaPlayer != null,
                            modifier = Modifier.size(40.dp).testTag("player_fwd_10_${record.id}"),
                        ) {
                            Icon(Icons.Rounded.Forward10, contentDescription = "تقديم 10 ثواني")
                        }

                        Spacer(Modifier.weight(1f))

                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .clickable { cycleSpeed() }
                                .testTag("player_speed_${record.id}"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Speed,
                                    contentDescription = "سرعة التشغيل",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text(
                                    text = if (playbackSpeed == 1.0f) "1x" else "${playbackSpeed}x",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }

                        IconButton(
                            onClick = { SystemIntents.shareBinaryFile(context, playableFile, "audio/mp4") },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = "مشاركة الصوت")
                        }
                    }

                    val sliderMax = totalDurationMs.coerceAtLeast(1000L).toFloat()
                    Slider(
                        value = displayedPosMs.coerceIn(0L, sliderMax.toLong()).toFloat(),
                        onValueChange = { next ->
                            isUserScrubbing = true
                            scrubPreviewMs = next.toLong()
                        },
                        onValueChangeFinished = {
                            isUserScrubbing = false
                            startOrSeekPlayback(scrubPreviewMs)
                        },
                        valueRange = 0f..sliderMax,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("player_slider_${record.id}"),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Rounded.OfflinePin,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "${(playableFile.length() / 1024L).coerceAtLeast(1L)} KB • محلي",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "${TranscriptPlaybackHighlighter.formatPlaybackClock(displayedPosMs)} / ${TranscriptPlaybackHighlighter.formatPlaybackClock(totalDurationMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        if (segments.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                segments.forEachIndexed { index, seg ->
                    val isSegmentActive = index == activeSegmentIdx
                    val activeWordRange = if (isSegmentActive) {
                        TranscriptPlaybackHighlighter.computeActiveWordRange(seg, displayedPosMs)
                    } else null

                    InteractiveSegmentRow(
                        segment = seg,
                        isActive = isSegmentActive,
                        activeWordRange = activeWordRange,
                        canSeek = playableFile != null,
                        testTag = "transcript_segment_${record.id}_$index",
                        onClick = {
                            if (playableFile != null) {
                                startOrSeekPlayback(seg.startMs)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun InteractiveSegmentRow(
    segment: TranscriptSegment,
    isActive: Boolean,
    activeWordRange: IntRange?,
    canSeek: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        label = "segment_bg",
    )
    val primaryColor = MaterialTheme.colorScheme.primary
    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val spokenColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f)

    val annotatedText = remember(segment.text, isActive, activeWordRange, primaryColor, highlightBg, spokenColor) {
        buildAnnotatedString {
            append(segment.text)
            if (isActive && activeWordRange != null && activeWordRange.first >= 0 && activeWordRange.last < segment.text.length) {
                if (activeWordRange.first > 0) {
                    addStyle(
                        style = SpanStyle(color = spokenColor, fontWeight = FontWeight.Medium),
                        start = 0,
                        end = activeWordRange.first,
                    )
                }
                addStyle(
                    style = SpanStyle(
                        color = primaryColor,
                        background = highlightBg,
                        fontWeight = FontWeight.Bold,
                    ),
                    start = activeWordRange.first,
                    end = activeWordRange.last + 1,
                )
            }
        }
    }

    val isSelfSpeaker = segment.speaker == "أنت" || segment.speaker.equals("You", ignoreCase = true)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = if (isActive) BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = canSeek, onClick = onClick)
            .testTag(testTag),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelfSpeaker) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                ) {
                    Text(
                        text = segment.speaker.ifBlank { "المتصل" },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelfSpeaker) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }

                Text(
                    text = TranscriptPlaybackHighlighter.formatPlaybackClock(segment.startMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                text = annotatedText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
