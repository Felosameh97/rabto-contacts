package com.example.ui.screens.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ai.RabtaAiAssistant
import com.example.auth.getAppCheckModeLabelAr
import com.example.auth.getManualDebugToken
import com.example.auth.getOrCreateLocalDebugUuid
import com.example.auth.saveManualDebugToken
import com.example.ui.theme.WhatsAppGreen
import com.example.domain.model.CallTranscriptRecord
import com.example.domain.model.LocalMediaCacheStats
import com.example.domain.model.SyncEngineState
import com.example.domain.model.SyncStatusBadge
import com.example.telecom.CallRecorderManager
import com.example.telecom.CallRecordingItem
import com.example.telecom.RabtaInCallService
import com.example.telecom.RecordingSourceMode
import com.example.util.AppLockSecurity
import com.example.util.SimPreference
import com.example.util.SystemIntents
import com.example.util.VaultActionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun CallsRecordingSettingsPage(
    isDefaultDialer: Boolean,
    hasMicPermission: Boolean,
    simPreference: SimPreference,
    autoRecordCalls: Boolean,
    autoTranscribeCalls: Boolean,
    vibrateOnAnswer: Boolean,
    autoSpeakerForRecord: Boolean,
    recordingSourceMode: RecordingSourceMode,
    callRecordings: List<CallRecordingItem>,
    callTranscripts: List<CallTranscriptRecord>,
    onSetSimPreference: (SimPreference) -> Unit,
    onSetAutoRecordCalls: (Boolean) -> Unit,
    onSetAutoTranscribeCalls: (Boolean) -> Unit,
    onSetVibrateOnAnswer: (Boolean) -> Unit,
    onSetAutoSpeakerForRecord: (Boolean) -> Unit,
    onSetRecordingSourceMode: (RecordingSourceMode) -> Unit,
    onOpenLiveTranscriber: () -> Unit,
    onRefreshRecordings: () -> Unit,
    onTranscribeRecording: (CallRecordingItem) -> Unit,
    onDeleteRecording: (CallRecordingItem) -> Unit,
    dialerRoleLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    recordAudioPermLauncher: ManagedActivityResultLauncher<String, Boolean>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playingPath by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isTestingMic by remember { mutableStateOf(false) }
    var micTestMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
        }
    }

    SettingsSubPageContainer {
        // Default Dialer + SIM card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("تطبيق الاتصال الافتراضي والشرائح", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        if (isDefaultDialer) {
                            "Felo Contacts هو تطبيق الهاتف الافتراضي حالياً — يعرض هوية المتصل وصورته مع التسجيل والتفريغ التلقائي."
                        } else {
                            "اجعل Felo Contacts تطبيق الهاتف الافتراضي للاستغناء عن تطبيق الاتصال الأساسي وعرض شاشة المكالمات مع التسجيل."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!isDefaultDialer) {
                        Button(
                            onClick = {
                                RabtaInCallService.createRequestDefaultDialerIntent(context)?.let {
                                    dialerRoleLauncher.launch(it)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("tools_set_default_dialer_btn"),
                        ) {
                            Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("تعيين Felo Contacts كتطبيق الاتصال الافتراضي")
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.SimCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("اختيار الشريحة (SIM)", style = MaterialTheme.typography.titleSmall)
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SimPreference.entries.forEach { pref ->
                            FilterChip(
                                selected = simPreference == pref,
                                onClick = { onSetSimPreference(pref) },
                                label = { Text(pref.labelAr) },
                                modifier = Modifier.testTag("settings_sim_${pref.name}"),
                            )
                        }
                    }
                }
            }
        }

        // Call Recording + AI Transcription + Mic Diagnostic Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("تسجيل المكالمات والتفريغ الذكي", style = MaterialTheme.typography.titleMedium)
                    }

                    if (!hasMicPermission) {
                        FilledTonalButton(
                            onClick = { recordAudioPermLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            modifier = Modifier.fillMaxWidth().testTag("tools_grant_mic_btn"),
                        ) {
                            Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("منح صلاحية الميكروفون للتسجيل والتفريغ الحي")
                        }
                    }

                    FilledTonalButton(
                        onClick = onOpenLiveTranscriber,
                        modifier = Modifier.fillMaxWidth().testTag("tools_open_live_transcriber_btn"),
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("فتح مفرغ الصوت المباشر (مجاناً بدون Gemini)")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تسجيل المكالمات تلقائياً (.m4a)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "يعمل عبر خدمة الميكروفون الأمامية مع تعدد مصادر الصوت؛ تشغيل السبيكر يوضح صوت الطرف الآخر على بعض الهواتف.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = autoRecordCalls,
                            onCheckedChange = { enable ->
                                if (!enable) {
                                    onSetAutoRecordCalls(false)
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                    if (granted) onSetAutoRecordCalls(true) else recordAudioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.testTag("tools_auto_record_switch"),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تفريغ المكالمات تلقائياً بـ Gemini (اختياري)", style = MaterialTheme.typography.titleSmall)
                            Text("يستهلك رصيد Gemini بعد كل مكالمة. مفرغ الصوت المباشر يعمل مجاناً وبدون حد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoTranscribeCalls,
                            onCheckedChange = onSetAutoTranscribeCalls,
                            modifier = Modifier.testTag("settings_auto_transcribe_switch"),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("اهتزاز خفيف عند رد الطرف الآخر", style = MaterialTheme.typography.titleSmall)
                            Text("يعطيك إشارة لمسية فور بدء المكالمة دون تثبيت الهاتف على الأذن.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = vibrateOnAnswer,
                            onCheckedChange = onSetVibrateOnAnswer,
                            modifier = Modifier.testTag("tools_vibrate_answer_switch"),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تفعيل السبيكر تلقائياً لوضوح التسجيل", style = MaterialTheme.typography.titleSmall)
                            Text("يضمن التقاط صوت الطرف الآخر بوضوح على كل هواتف أندرويد الحديثة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoSpeakerForRecord,
                            onCheckedChange = onSetAutoSpeakerForRecord,
                            modifier = Modifier.testTag("tools_auto_speaker_switch"),
                        )
                    }

                    Text("مسار التقاط الصوت:", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RecordingSourceMode.entries.forEach { mode ->
                            FilterChip(
                                selected = recordingSourceMode == mode,
                                onClick = { onSetRecordingSourceMode(mode) },
                                label = { Text(mode.labelAr) },
                            )
                        }
                    }

                    OutlinedButton(
                        enabled = !isTestingMic,
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                recordAudioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                return@OutlinedButton
                            }
                            scope.launch {
                                isTestingMic = true
                                micTestMessage = "جاري تسجيل عينة 3 ثواني — تحدث الآن..."
                                val startRes = CallRecorderManager.startRecording(
                                    context = context,
                                    labelOrNumber = "MicTest",
                                    sourceMode = recordingSourceMode,
                                    allowSilentSwap = false,
                                )
                                if (startRes.isFailure) {
                                    isTestingMic = false
                                    micTestMessage = startRes.exceptionOrNull()?.localizedMessage ?: "فشل اختبار الميكروفون"
                                    return@launch
                                }
                                delay(3200L)
                                val recorded = CallRecorderManager.stopRecording()
                                isTestingMic = false
                                onRefreshRecordings()
                                if (recorded != null) {
                                    micTestMessage = "جاري تشغيل العينة المسجلة (${recorded.length() / 1024} KB)..."
                                    runCatching {
                                        mediaPlayer?.stop()
                                        mediaPlayer?.release()
                                    }
                                    mediaPlayer = CallRecorderManager.playAudioFile(
                                        context = context,
                                        file = recorded,
                                        onStart = { playingPath = recorded.absolutePath },
                                        onComplete = {
                                            playingPath = null
                                            micTestMessage = "✓ الصوت والتسجيل يعملان بنجاح!"
                                        },
                                        onError = { err ->
                                            playingPath = null
                                            micTestMessage = err
                                        },
                                    )
                                } else {
                                    micTestMessage = "لم يتم حفظ عينة صالحة — غيّر مسار الصوت وجرب مجدداً"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("tools_mic_test_btn"),
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isTestingMic) "جاري اختبار الميكروفون (3 ثواني)..." else "اختبار التسجيل والصوت (3 ثواني)")
                    }
                    if (isTestingMic) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    micTestMessage?.let { msg ->
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        if (callRecordings.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("التسجيلات المحفوظة (${callRecordings.size})", style = MaterialTheme.typography.titleSmall)
                }
            }
            items(callRecordings.take(20), key = { it.file.absolutePath }) { rec ->
                val isPlaying = playingPath == rec.file.absolutePath
                val matchedTranscript = callTranscripts.firstOrNull { it.audioFilePath == rec.file.absolutePath }
                val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            IconButton(
                                onClick = {
                                    if (isPlaying) {
                                        runCatching {
                                            mediaPlayer?.stop()
                                            mediaPlayer?.release()
                                        }
                                        mediaPlayer = null
                                        playingPath = null
                                    } else {
                                        runCatching {
                                            mediaPlayer?.stop()
                                            mediaPlayer?.release()
                                        }
                                        mediaPlayer = CallRecorderManager.playAudioFile(
                                            context = context,
                                            file = rec.file,
                                            onStart = { playingPath = rec.file.absolutePath },
                                            onComplete = { playingPath = null },
                                            onError = { err ->
                                                playingPath = null
                                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                            },
                                        )
                                    }
                                },
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "إيقاف" else "تشغيل",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rec.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${fmt.format(Date(rec.timestampMs))} • ${rec.sizeKb} KB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onTranscribeRecording(rec) }) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = "تفريغ بـ Gemini", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { SystemIntents.shareBinaryFile(context, rec.file, "audio/mp4") }) {
                                Icon(Icons.Rounded.Share, contentDescription = "مشاركة", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDeleteRecording(rec) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "حذف", modifier = Modifier.size(18.dp))
                            }
                        }
                        if (matchedTranscript != null && matchedTranscript.transcript.isNotBlank()) {
                            Text(
                                text = matchedTranscript.transcript,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SyncCacheSettingsPage(
    syncEngineState: SyncEngineState,
    cacheStats: LocalMediaCacheStats,
    selectedAiModelId: String = RabtaAiAssistant.DEFAULT_MODEL_ID,
    aiDiagnosticStatus: String? = null,
    onSelectAiModelId: (String) -> Unit = {},
    onTestGeminiConnection: () -> Unit = {},
    onSyncNow: () -> Unit,
    onPurgeOrphanedCache: () -> Unit,
) {
    val context = LocalContext.current
    var manualTokenInput by remember { mutableStateOf(getManualDebugToken(context)) }
    var showTokenConfig by remember { mutableStateOf(manualTokenInput.isNotBlank()) }
    val localDebugUuid = remember { getOrCreateLocalDebugUuid(context) }
    var customModelInput by remember(selectedAiModelId) { mutableStateOf(selectedAiModelId) }

    SettingsSubPageContainer {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().testTag("settings_sync_health_card"),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val icon = when (syncEngineState.status) {
                            SyncStatusBadge.OFFLINE -> Icons.Rounded.CloudOff
                            SyncStatusBadge.SYNCING -> Icons.Rounded.CloudSync
                            else -> Icons.Rounded.CloudDone
                        }
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("صحة المزامنة والكاش المحلي", style = MaterialTheme.typography.titleMedium)
                    }
                    val lastSyncLabel = if (syncEngineState.lastSyncEpochMs > 0L) {
                        AppLockSecurity.formatRelativeTimeAr(syncEngineState.lastSyncEpochMs)
                    } else {
                        "لم تتم مزامنة بعد"
                    }
                    Text(
                        text = buildString {
                            append(if (syncEngineState.isOnline) "متصل بالإنترنت" else "وضع أوفلاين (الحفظ محلي فوري)")
                            append(" • بانتظار الرفع: ${syncEngineState.pendingCount}")
                            append(" • آخر مزامنة: $lastSyncLabel")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "الكاش المحلي: ${cacheStats.recordingFilesCount} تسجيل صوتي • ${cacheStats.photoFilesCount} صورة • ${cacheStats.transcriptCacheCount} ملخص (${VaultActionHelper.formatBytesReadable(cacheStats.totalBytes)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (syncEngineState.status == SyncStatusBadge.SYNCING) {
                        val totalRun = syncEngineState.totalInCurrentRun
                        val syncedRun = syncEngineState.syncedInCurrentRun
                        if (totalRun > 0) {
                            LinearProgressIndicator(
                                progress = { (syncedRun.toFloat() / totalRun.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().testTag("sync_live_progress_bar"),
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().testTag("sync_live_progress_bar"),
                            )
                        }
                        Text(
                            text = syncEngineState.progressMessage
                                ?: if (totalRun > 0) {
                                    "جاري الرفع السريع: $syncedRun / $totalRun • متبقي: ${syncEngineState.pendingCount}"
                                } else {
                                    "جاري المزامنة السريعة مع السحابة..."
                                },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (syncEngineState.failedCount > 0) {
                        Text(
                            text = "تعذر رفع ${syncEngineState.failedCount} عنصر بعد عدة محاولات — اضغط \"مزامنة الآن\" لإعادة المحاولة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    syncEngineState.lastError?.takeIf { it.isNotBlank() }?.let { err ->
                        Text(
                            text = "آخر ملاحظة مزامنة: $err",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = onSyncNow,
                            enabled = syncEngineState.status != SyncStatusBadge.SYNCING,
                            modifier = Modifier.weight(1f).testTag("tools_sync_now_btn"),
                        ) {
                            Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = when {
                                    syncEngineState.status == SyncStatusBadge.SYNCING -> "جاري الرفع..."
                                    syncEngineState.failedCount > 0 -> "إعادة المحاولة (${syncEngineState.failedCount})"
                                    else -> "مزامنة الآن"
                                },
                                maxLines = 1,
                            )
                        }
                        OutlinedButton(
                            onClick = onPurgeOrphanedCache,
                            modifier = Modifier.weight(1f).testTag("tools_purge_cache_btn"),
                        ) {
                            Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("تنظيف الكاش", maxLines = 1)
                        }
                    }
                }
            }
        }

        // Gemini Models & Firebase App Check Diagnostics
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("موديلات Gemini و App Check", style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(onClick = { showTokenConfig = !showTokenConfig }) {
                            Text(if (showTokenConfig) "إخفاء" else "خيارات App Check")
                        }
                    }
                    Text(
                        "الموديل النشط حالياً: $selectedAiModelId",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RabtaAiAssistant.PRESET_MODELS.forEach { opt ->
                            FilterChip(
                                selected = selectedAiModelId.equals(opt.id, ignoreCase = true),
                                onClick = {
                                    customModelInput = opt.id
                                    onSelectAiModelId(opt.id)
                                },
                                label = { Text(opt.title) },
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = customModelInput,
                            onValueChange = { customModelInput = it },
                            label = { Text("موديل مخصص (Model ID)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("settings_ai_model_input"),
                        )
                        Button(
                            onClick = {
                                if (customModelInput.isNotBlank()) {
                                    onSelectAiModelId(customModelInput.trim())
                                }
                            },
                            modifier = Modifier.testTag("settings_apply_ai_model_btn"),
                        ) {
                            Text("تعيين")
                        }
                    }
                    OutlinedButton(
                        onClick = onTestGeminiConnection,
                        modifier = Modifier.fillMaxWidth().testTag("settings_test_gemini_btn"),
                    ) {
                        Icon(Icons.Rounded.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("اختبار اتصال Gemini الآن")
                    }
                    if (!aiDiagnosticStatus.isNullOrBlank()) {
                        Text(
                            text = aiDiagnosticStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (aiDiagnosticStatus.startsWith("✅")) WhatsAppGreen else MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        "وضع App Check: ${getAppCheckModeLabelAr()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (showTokenConfig) {
                        Text(
                            "في الأجهزة الحقيقية يعمل reCAPTCHA تلقائياً. إذا أردت ربط الجهاز يدوياً بـ Debug Token من Firebase Console يمكنك نسخه أو لصقه هنا ثم إعادة فتح التطبيق:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (localDebugUuid.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("AppCheckDebugUUID", localDebugUuid))
                                    Toast.makeText(context, "تم نسخ Debug UUID المحلي", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth().testTag("copy_local_debug_uuid_btn"),
                            ) {
                                Text("نسخ Debug UUID الخاص بهذا الهاتف")
                            }
                        }
                        OutlinedTextField(
                            value = manualTokenInput,
                            onValueChange = { manualTokenInput = it },
                            label = { Text("Debug Token مخصص (اختياري)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("manual_appcheck_token_input"),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    saveManualDebugToken(context, manualTokenInput)
                                    Toast.makeText(
                                        context,
                                        "تم الحفظ. أغلق التطبيق وأعد فتحه لتطبيق الوضع الجديد.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                },
                                modifier = Modifier.weight(1f).testTag("save_manual_appcheck_btn"),
                            ) {
                                Text("حفظ التوكن")
                            }
                            OutlinedButton(
                                onClick = {
                                    manualTokenInput = ""
                                    saveManualDebugToken(context, "")
                                    Toast.makeText(
                                        context,
                                        "تم الترجيع إلى reCAPTCHA التلقائي. أعد فتح التطبيق.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                },
                                modifier = Modifier.weight(1f).testTag("clear_manual_appcheck_btn"),
                            ) {
                                Text("استخدام التلقائي")
                            }
                        }
                    }
                }
            }
        }
    }
}
