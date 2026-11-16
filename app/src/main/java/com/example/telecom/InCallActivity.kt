package com.example.telecom

import android.Manifest
import android.os.Bundle
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.SyncPrefsDataStore
import com.example.ui.theme.CalmPalette
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.RabtaContactsTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.WhatsAppGreen
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

            RabtaContactsTheme(themeMode = themeMode, palette = palette) {
                InCallScreen(
                    usePhotoBackground = usePhotoBackground,
                    onFinish = { finish() },
                )
            }
        }
    }
}

@Composable
private fun InCallScreen(
    usePhotoBackground: Boolean,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val state by RabtaInCallService.uiState.collectAsStateWithLifecycle()
    var showKeypad by remember { mutableStateOf(false) }

    var pendingPermAction by remember { mutableStateOf<String?>(null) }
    val recordPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            if (pendingPermAction == "LIVE") {
                RabtaInCallService.toggleLiveTranscription()
            } else {
                RabtaInCallService.toggleRecording()
            }
        }
        pendingPermAction = null
    }

    LaunchedEffect(state.hasActiveCall, state.callState) {
        if (!state.hasActiveCall || state.callState == Call.STATE_DISCONNECTED) {
            onFinish()
        }
    }

    val displayName = state.matchedContact?.displayName?.ifBlank { state.phoneNumber } ?: state.phoneNumber.ifBlank { "مكالمة" }
    val photoPath = state.matchedContact?.photoUri?.takeIf { it.isNotBlank() && File(it).exists() }
    val showBgPhoto = usePhotoBackground && photoPath != null
    val statusText = when (state.callState) {
        Call.STATE_RINGING -> "مكالمة واردة..."
        Call.STATE_DIALING, Call.STATE_CONNECTING -> "جاري الاتصال..."
        Call.STATE_HOLDING -> "مكالمة معلقة"
        Call.STATE_ACTIVE -> String.format("%02d:%02d", state.elapsedSec / 60, state.elapsedSec % 60)
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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (photoPath != null) {
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
                        style = MaterialTheme.typography.headlineMedium,
                        color = primaryTextColor,
                        textAlign = TextAlign.Center,
                    )
                    if (state.phoneNumber.isNotBlank() && state.phoneNumber != displayName) {
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
                    )

                    val categoryOrDistrict = listOfNotNull(
                        state.matchedContact?.categoryPaths?.firstOrNull(),
                        state.matchedContact?.district,
                    ).joinToString(" • ")
                    if (categoryOrDistrict.isNotBlank()) {
                        AssistChip(onClick = {}, label = { Text(categoryOrDistrict) })
                    }

                    state.recordingStatusNote?.let { note ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (state.isRecording || state.isLiveTranscribing) ErrorRed.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Text(
                                text = note,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (state.isRecording || state.isLiveTranscribing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }

                    if (state.isLiveTranscribing || state.liveTranscriptText.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
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
                                        modifier = Modifier.size(60.dp),
                                    ) {
                                        Text(ch.toString(), style = MaterialTheme.typography.titleLarge)
                                    }
                                }
                            }
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        CallActionToggle(
                            icon = if (state.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            label = if (state.isMuted) "صامت" else "الميكروفون",
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
                            icon = if (state.isRecording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord,
                            label = if (state.isRecording) "إيقاف" else "تسجيل",
                            active = state.isRecording,
                            activeColor = ErrorRed,
                            labelColor = primaryTextColor,
                            onClick = {
                                if (CallRecorderManager.hasRecordPermission(context)) {
                                    RabtaInCallService.toggleRecording()
                                } else {
                                    pendingPermAction = "REC"
                                    recordPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            Button(
                                onClick = { RabtaInCallService.endCall() },
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier.height(58.dp).weight(1f),
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
                                modifier = Modifier.height(58.dp).weight(1f),
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
                            modifier = Modifier.height(60.dp).fillMaxWidth(0.65f),
                        ) {
                            Icon(Icons.Rounded.CallEnd, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("إنهاء المكالمة")
                        }
                    }
                }
            }
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
            modifier = Modifier.size(52.dp),
        ) {
            Icon(icon, contentDescription = label)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = labelColor)
    }
}
