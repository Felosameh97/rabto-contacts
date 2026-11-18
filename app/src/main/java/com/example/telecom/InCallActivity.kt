package com.example.telecom

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AddIcCall
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.MainActivity
import com.example.data.local.SyncPrefsDataStore
import com.example.ui.theme.CalmPalette
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.RabtaContactsTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.WhatsAppGreen
import com.example.util.AppLockSecurity
import com.example.util.PhoneUtils
import java.io.File

class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs = remember { SyncPrefsDataStore(applicationContext) }
            val themeMode by prefs.themeModeFlow.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val palette by prefs.paletteFlow.collectAsStateWithLifecycle(initialValue = CalmPalette.SLATE)
            val usePhotoBackground by prefs.callPhotoBackgroundFlow.collectAsStateWithLifecycle(initialValue = true)
            val hideLockscreenDetailsPref by prefs.hideLockscreenCallerDetailsFlow.collectAsStateWithLifecycle(initialValue = false)
            val waTemplates by prefs.whatsappTemplatesFlow.collectAsStateWithLifecycle(
                initialValue = SyncPrefsDataStore.DEFAULT_WHATSAPP_TEMPLATES
            )

            RabtaContactsTheme(themeMode = themeMode, palette = palette) {
                InCallScreen(
                    usePhotoBackground = usePhotoBackground,
                    hideLockscreenDetailsPref = hideLockscreenDetailsPref,
                    busyReplyMessage = waTemplates.getOrNull(1)
                        ?: "أنا في اجتماع/مشغول حالياً، هكلمك أول ما أخلص 👍",
                    onMinimize = {
                        val homeIntent = Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        runCatching { startActivity(homeIntent) }
                        moveTaskToBack(false)
                    },
                    onAddCall = {
                        val dialIntent = Intent(this, MainActivity::class.java).apply {
                            action = Intent.ACTION_DIAL
                            data = Uri.parse("tel:")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        runCatching { startActivity(dialIntent) }
                    },
                    onFinish = { finish() },
                )
            }
        }
    }
}

@Composable
private fun InCallScreen(
    usePhotoBackground: Boolean,
    hideLockscreenDetailsPref: Boolean,
    busyReplyMessage: String,
    onMinimize: () -> Unit,
    onAddCall: () -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val state by RabtaInCallService.uiState.collectAsStateWithLifecycle()
    var showKeypad by remember { mutableStateOf(false) }

    val keyguardLocked = remember(state.callState, state.hasActiveCall) {
        runCatching {
            val km = context.getSystemService(KeyguardManager::class.java)
            km?.isKeyguardLocked == true
        }.getOrDefault(false)
    }
    val maskCallerForPrivacy = keyguardLocked && (hideLockscreenDetailsPref || state.hideLockscreenDetails)

    var pendingPermAction by remember { mutableStateOf<String?>(null) }
    val recordPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            when (pendingPermAction) {
                "LIVE" -> RabtaInCallService.toggleLiveTranscription()
                "REC_AI" -> RabtaInCallService.toggleRecordAndTranscribe()
                else -> RabtaInCallService.toggleRecording()
            }
        }
        pendingPermAction = null
    }

    LaunchedEffect(state.hasActiveCall, state.callState) {
        if (!state.hasActiveCall || state.callState == Call.STATE_DISCONNECTED) {
            onFinish()
        }
    }

    val rawDisplayName = state.matchedContact?.displayName?.ifBlank { state.phoneNumber } ?: state.phoneNumber.ifBlank { "مكالمة" }
    val displayName = if (maskCallerForPrivacy) "مكالمة واردة — مخفية للخصوصية" else rawDisplayName
    val photoPath = if (maskCallerForPrivacy) null else state.matchedContact?.photoUri?.takeIf { it.isNotBlank() && File(it).exists() }
    val showBgPhoto = usePhotoBackground && photoPath != null && !maskCallerForPrivacy
    val statusText = when (state.callState) {
        Call.STATE_RINGING -> "مكالمة واردة..."
        Call.STATE_DIALING, Call.STATE_CONNECTING -> "جاري الاتصال..."
        Call.STATE_HOLDING -> "مكالمة معلقة"
        Call.STATE_ACTIVE -> AppLockSecurity.formatDurationClock(state.elapsedSec)
        else -> "إنهاء المكالمة"
    }

    val primaryTextColor = if (showBgPhoto) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (showBgPhoto) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (showBgPhoto && photoPath != null) {
                AsyncImage(
                    model = File(photoPath),
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.58f),
                                    Color.Black.copy(alpha = 0.42f),
                                    Color.Black.copy(alpha = 0.84f),
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top Bar: Minimize Floating Bubble + Lockscreen Privacy Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = onMinimize,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(40.dp).testTag("incall_minimize_btn"),
                    ) {
                        Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تصغير", style = MaterialTheme.typography.labelLarge)
                    }

                    if (maskCallerForPrivacy) {
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text("شاشة قفل محمية", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else if (state.isRecording || state.isRecordAndAiMode) {
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = ErrorRed.copy(alpha = 0.25f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Rounded.FiberManualRecord, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(12.dp))
                                Text(
                                    text = AppLockSecurity.formatDurationClock(state.elapsedSec),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryTextColor,
                                )
                            }
                        }
                    }
                }

                // Caller Info + Pre-Call Context + Waveform Visualizer
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (maskCallerForPrivacy) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "مخفي للخصوصية",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(34.dp),
                            )
                        } else if (photoPath != null) {
                            AsyncImage(
                                model = File(photoPath),
                                contentDescription = displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                text = displayName.firstOrNull()?.uppercase() ?: "#",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!maskCallerForPrivacy && state.phoneNumber.isNotBlank() && state.phoneNumber != displayName) {
                        Text(
                            text = state.phoneNumber,
                            style = MaterialTheme.typography.titleMedium,
                            color = secondaryTextColor,
                        )
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (showBgPhoto) Color.White else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (!maskCallerForPrivacy) {
                        val categoryOrDistrict = listOfNotNull(
                            state.matchedContact?.categoryPaths?.firstOrNull(),
                            state.matchedContact?.district,
                            state.callerInsight,
                        ).joinToString(" • ")
                        if (categoryOrDistrict.isNotBlank()) {
                            AssistChip(onClick = {}, label = { Text(categoryOrDistrict) })
                        }

                        // Pre-Call Context Banner ("Last spoke • 1-line AI summary")
                        state.preCallContext?.let { pre ->
                            val lastSpoke = pre.lastSpokeEpochMs?.let { AppLockSecurity.formatRelativeTimeAr(it) }
                            val headline = buildString {
                                if (!lastSpoke.isNullOrBlank()) append("آخر تواصل: $lastSpoke")
                                if (pre.totalCallsCount > 1) {
                                    if (isNotEmpty()) append(" • ")
                                    append("${pre.totalCallsCount} مكالمة")
                                }
                                if (pre.pendingActionItemsCount > 0) {
                                    if (isNotEmpty()) append(" • ")
                                    append("${pre.pendingActionItemsCount} مهمة معلقة")
                                }
                            }
                            if (headline.isNotBlank() || !pre.oneLineSummary.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (showBgPhoto) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("incall_pre_call_context"),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        if (headline.isNotBlank()) {
                                            Text(
                                                text = headline,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (showBgPhoto) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                        pre.oneLineSummary?.takeIf { it.isNotBlank() }?.let { summary ->
                                            Text(
                                                text = "ملخص: $summary",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (showBgPhoto) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSecondaryContainer,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Live Waveform Visualizer & Status Pill during Recording / Transcribing
                    state.recordingStatusNote?.let { note ->
                        val activeRecLabel = if (state.isRecording && state.recordingSourceName.isNotBlank()) {
                            "$note (${state.recordingSourceName})"
                        } else note
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (state.isRecording || state.isLiveTranscribing) ErrorRed.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = activeRecLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (state.isRecording || state.isLiveTranscribing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (state.isRecording) {
                                    LiveWaveformRow(amplitude = state.recordingAmplitude)
                                }
                            }
                        }
                    }

                    if (state.isLiveTranscribing || state.liveTranscriptText.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        ) {
                            Text(
                                text = state.liveTranscriptText.ifBlank { "جاري الاستماع وتفريغ الكلام مباشرة..." },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                maxLines = 4,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }

                if (showKeypad) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val rows = listOf(
                            listOf('1', '2', '3'),
                            listOf('4', '5', '6'),
                            listOf('7', '8', '9'),
                            listOf('*', '0', '#'),
                        )
                        rows.forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                row.forEach { ch ->
                                    FilledTonalIconButton(
                                        onClick = { RabtaInCallService.sendDtmf(ch) },
                                        modifier = Modifier.size(56.dp),
                                    ) {
                                        Text(ch.toString(), style = MaterialTheme.typography.titleLarge)
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Controls
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    // Prominent Record + AI Transcribe CTA
                    FilledTonalButton(
                        onClick = {
                            if (CallRecorderManager.hasRecordPermission(context)) {
                                RabtaInCallService.toggleRecordAndTranscribe()
                            } else {
                                pendingPermAction = "REC_AI"
                                recordPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (state.isRecording) ErrorRed.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (state.isRecording) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("incall_record_ai_btn"),
                    ) {
                        Icon(
                            imageVector = if (state.isRecording) Icons.Rounded.Stop else Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (state.isRecording) {
                                "إيقاف التسجيل (${AppLockSecurity.formatDurationClock(state.elapsedSec)})"
                            } else {
                                "تسجيل + تلخيص بالذكاء الاصطناعي"
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        CallActionToggle(
                            icon = if (state.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            label = if (state.isMuted) "صامت" else "كتم",
                            active = state.isMuted,
                            labelColor = primaryTextColor,
                            onClick = { RabtaInCallService.toggleMute() },
                        )
                        CallActionToggle(
                            icon = Icons.Rounded.VolumeUp,
                            label = "سبيكر",
                            active = state.isSpeaker,
                            labelColor = primaryTextColor,
                            onClick = { RabtaInCallService.toggleSpeaker() },
                        )
                        CallActionToggle(
                            icon = if (state.isOnHold) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            label = if (state.isOnHold) "استئناف" else "انتظار",
                            active = state.isOnHold,
                            labelColor = primaryTextColor,
                            onClick = { RabtaInCallService.toggleHold() },
                        )
                        CallActionToggle(
                            icon = Icons.Rounded.Dialpad,
                            label = "لوحة",
                            active = showKeypad,
                            labelColor = primaryTextColor,
                            onClick = { showKeypad = !showKeypad },
                        )
                        CallActionToggle(
                            icon = Icons.Rounded.AddIcCall,
                            label = "إضافة",
                            active = false,
                            labelColor = primaryTextColor,
                            onClick = onAddCall,
                        )
                        CallActionToggle(
                            icon = Icons.Rounded.RecordVoiceOver,
                            label = if (state.isLiveTranscribing) "إيقاف النص" else "تفريغ حي",
                            active = state.isLiveTranscribing,
                            activeColor = WhatsAppGreen,
                            labelColor = primaryTextColor,
                            onClick = {
                                if (CallRecorderManager.hasRecordPermission(context)) {
                                    RabtaInCallService.toggleLiveTranscription()
                                } else {
                                    pendingPermAction = "LIVE"
                                    recordPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        )
                    }

                    if (state.callState == Call.STATE_RINGING) {
                        if (!maskCallerForPrivacy && state.phoneNumber.isNotBlank() && !PhoneUtils.isUssdOrShortCode(state.phoneNumber)) {
                            FilledTonalButton(
                                onClick = { RabtaInCallService.rejectWithWhatsApp(context, busyReplyMessage) },
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("رفض + إرسال رسالة واتساب جاهزة")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            Button(
                                onClick = { RabtaInCallService.endCall() },
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier.height(58.dp).weight(1f).testTag("incall_reject_btn"),
                            ) {
                                Icon(Icons.Rounded.CallEnd, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("رفض")
                            }
                            Spacer(Modifier.width(16.dp))
                            Button(
                                onClick = { RabtaInCallService.answerCall() },
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier.height(58.dp).weight(1f).testTag("incall_answer_btn"),
                            ) {
                                Icon(Icons.Rounded.Call, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("رد")
                            }
                        }
                    } else {
                        Button(
                            onClick = { RabtaInCallService.endCall() },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                            shape = RoundedCornerShape(32.dp),
                            modifier = Modifier.height(58.dp).fillMaxWidth(0.68f).testTag("incall_end_btn"),
                        ) {
                            Icon(Icons.Rounded.CallEnd, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("إنهاء المكالمة")
                        }
                    }
                }
            }

            if (state.isNearEar && !state.isSpeaker && state.callState != Call.STATE_RINGING) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.94f))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "الشاشة محمية أثناء وضع الهاتف على الأذن",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveWaveformRow(amplitude: Int) {
    val norm = (amplitude / 12000f).coerceIn(0.12f, 1f)
    val multipliers = listOf(0.45f, 0.85f, 0.65f, 1.0f, 0.72f, 0.92f, 0.55f, 0.8f, 0.45f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(24.dp),
    ) {
        multipliers.forEach { factor ->
            val targetHeight by animateDpAsState(
                targetValue = (6f + 16f * norm * factor).dp,
                label = "waveform_bar",
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(targetHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .background(WhatsAppGreen),
            )
        }
    }
}

@Composable
private fun CallActionToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color? = null,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalIconButton(
            onClick = onClick,
            colors = if (active) {
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = (activeColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.28f),
                    contentColor = activeColor ?: MaterialTheme.colorScheme.primary,
                )
            } else {
                IconButtonDefaults.filledTonalIconButtonColors()
            },
            modifier = Modifier.size(50.dp),
        ) {
            Icon(icon, contentDescription = label)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = labelColor)
    }
}
