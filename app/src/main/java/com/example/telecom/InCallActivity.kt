package com.example.telecom

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AddIcCall
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapCalls
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.data.local.RabtaDatabase
import com.example.data.local.SyncPrefsDataStore
import com.example.domain.model.ContactRecord
import com.example.ui.components.ContactAvatarBadge
import com.example.ui.theme.CalmPalette
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.RabtaContactsTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.WhatsAppGreen
import com.example.util.AppLockSecurity
import com.example.util.PhoneUtils
import com.example.util.SystemIntents
import com.example.util.T9SearchMatcher
import com.example.util.rememberDialpadTonePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
                    onFinish = { finish() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun InCallScreen(
    usePhotoBackground: Boolean,
    hideLockscreenDetailsPref: Boolean,
    busyReplyMessage: String,
    onMinimize: () -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val state by RabtaInCallService.uiState.collectAsStateWithLifecycle()
    val tonePlayer = rememberDialpadTonePlayer()
    var showKeypad by remember { mutableStateOf(false) }
    var showAudioRouteMenu by remember { mutableStateOf(false) }
    var showAddCallPicker by remember { mutableStateOf(false) }

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

    val rawDisplayName = when {
        state.isConferenceCall -> "مكالمة جماعية (${state.conferenceParticipants.size.coerceAtLeast(2)})"
        else -> state.matchedContact?.displayName?.ifBlank { state.phoneNumber } ?: state.phoneNumber.ifBlank { "مكالمة" }
    }
    val displayName = if (maskCallerForPrivacy) "مكالمة واردة — مخفية للخصوصية" else rawDisplayName
    val photoPath = if (maskCallerForPrivacy) null else state.matchedContact?.photoUri?.takeIf { it.isNotBlank() && File(it).exists() }
    val showBgPhoto = usePhotoBackground && photoPath != null && !maskCallerForPrivacy && !state.isConferenceCall
    val statusText = when (state.callState) {
        Call.STATE_RINGING -> "مكالمة واردة..."
        Call.STATE_DIALING, Call.STATE_CONNECTING -> "جاري الاتصال..."
        Call.STATE_HOLDING -> "مكالمة معلقة"
        Call.STATE_ACTIVE -> AppLockSecurity.formatDurationClock(state.elapsedSec)
        else -> "إنهاء المكالمة"
    }

    val primaryTextColor = if (showBgPhoto) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (showBgPhoto) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant

    val audioRouteLabel = when (state.activeAudioRoute) {
        CallAudioState.ROUTE_BLUETOOTH -> "بلوتوث"
        CallAudioState.ROUTE_SPEAKER -> "سبيكر"
        CallAudioState.ROUTE_WIRED_HEADSET -> "سماعة"
        else -> "سماعة الأذن"
    }
    val audioRouteIcon = when (state.activeAudioRoute) {
        CallAudioState.ROUTE_BLUETOOTH -> Icons.Rounded.BluetoothAudio
        CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Rounded.Headset
        CallAudioState.ROUTE_SPEAKER -> Icons.Rounded.VolumeUp
        else -> Icons.Rounded.PhoneInTalk
    }
    val hasExtraAudioRoutes = ((state.supportedAudioMask and (CallAudioState.ROUTE_BLUETOOTH or CallAudioState.ROUTE_WIRED_HEADSET)) != 0)

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
                // Top Bar: Minimize Floating Bubble + Lockscreen Privacy Badge + Recording Pill
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

                // Caller Info + Secondary Call / Conference Panel + Pre-Call Context + Waveform Visualizer
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
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
                        } else if (state.isConferenceCall) {
                            Icon(
                                imageVector = Icons.Rounded.Groups,
                                contentDescription = "مكالمة جماعية",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(38.dp),
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
                    if (!maskCallerForPrivacy && !state.isConferenceCall && state.phoneNumber.isNotBlank() && state.phoneNumber != displayName) {
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

                    // Multi-Call / Secondary Call Banner (Swap, Merge, Answer Waiting, End)
                    if (!maskCallerForPrivacy && state.secondaryCall != null) {
                        val sec = state.secondaryCall!!
                        val secStatus = when (sec.callState) {
                            Call.STATE_RINGING -> "مكالمة ثانية واردة"
                            Call.STATE_HOLDING -> "معلق"
                            Call.STATE_DIALING, Call.STATE_CONNECTING -> "جاري الاتصال..."
                            Call.STATE_ACTIVE -> "نشط"
                            else -> "متصل"
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("incall_secondary_call_banner"),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = sec.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${sec.phoneNumber} • $secStatus",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                        )
                                    }
                                    IconButton(
                                        onClick = { RabtaInCallService.disconnectCallById(sec.callId) },
                                        modifier = Modifier.size(40.dp).testTag("incall_end_secondary_btn"),
                                    ) {
                                        Icon(Icons.Rounded.CallEnd, contentDescription = "إنهاء المكالمة الثانية", tint = ErrorRed)
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (sec.callState == Call.STATE_RINGING) {
                                        Button(
                                            onClick = { RabtaInCallService.answerCall() },
                                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                            modifier = Modifier.weight(1f).testTag("incall_answer_waiting_btn"),
                                        ) {
                                            Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("رد وتعليق الحالي", maxLines = 1)
                                        }
                                    } else {
                                        if (state.canSwapCalls) {
                                            OutlinedButton(
                                                onClick = { RabtaInCallService.swapCalls() },
                                                modifier = Modifier.weight(1f).testTag("incall_swap_calls_btn"),
                                            ) {
                                                Icon(Icons.Rounded.SwapCalls, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("تبديل", maxLines = 1)
                                            }
                                        }
                                        if (state.canMergeConference) {
                                            Button(
                                                onClick = { RabtaInCallService.mergeConference() },
                                                modifier = Modifier.weight(1f).testTag("incall_merge_conference_btn"),
                                            ) {
                                                Icon(Icons.Rounded.CallMerge, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("دمج جماعية", maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Conference Participants Card (Split / Disconnect each)
                    if (!maskCallerForPrivacy && state.isConferenceCall && state.conferenceParticipants.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("incall_conference_participants"),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "أعضاء المكالمة الجماعية",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                state.conferenceParticipants.take(4).forEach { member ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = "${member.displayName} (${member.phoneNumber})",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (member.canSeparate) {
                                            IconButton(
                                                onClick = { RabtaInCallService.splitConferenceParticipant(member.callId) },
                                                modifier = Modifier.size(36.dp),
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Rounded.CallSplit,
                                                    contentDescription = "فصل من الجماعية",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                        if (member.canDisconnect) {
                                            IconButton(
                                                onClick = { RabtaInCallService.disconnectCallById(member.callId) },
                                                modifier = Modifier.size(36.dp),
                                            ) {
                                                Icon(Icons.Rounded.CallEnd, contentDescription = "إنهاء العضو", tint = ErrorRed)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!maskCallerForPrivacy && !state.isConferenceCall) {
                        val categoryOrDistrict = listOfNotNull(
                            state.matchedContact?.categoryPaths?.firstOrNull(),
                            state.matchedContact?.district,
                            state.callerInsight,
                        ).joinToString(" • ")
                        if (categoryOrDistrict.isNotBlank()) {
                            AssistChip(onClick = {}, label = { Text(categoryOrDistrict) })
                        }

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

                    AnimatedVisibility(visible = state.isLiveTranscribing || state.liveTranscriptText.isNotBlank()) {
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
                                        onClick = {
                                            tonePlayer.play(ch)
                                            RabtaInCallService.sendDtmf(ch)
                                        },
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

                        Box {
                            CallActionToggle(
                                icon = audioRouteIcon,
                                label = audioRouteLabel,
                                active = state.activeAudioRoute != CallAudioState.ROUTE_EARPIECE,
                                labelColor = primaryTextColor,
                                onClick = {
                                    if (hasExtraAudioRoutes) {
                                        showAudioRouteMenu = true
                                    } else {
                                        RabtaInCallService.toggleSpeaker()
                                    }
                                },
                                onLongClick = { showAudioRouteMenu = true },
                            )
                            DropdownMenu(
                                expanded = showAudioRouteMenu,
                                onDismissRequest = { showAudioRouteMenu = false },
                            ) {
                                val routes = listOf(
                                    CallAudioState.ROUTE_EARPIECE to "سماعة الأذن",
                                    CallAudioState.ROUTE_SPEAKER to "مكبر الصوت (سبيكر)",
                                    CallAudioState.ROUTE_BLUETOOTH to "بلوتوث",
                                    CallAudioState.ROUTE_WIRED_HEADSET to "سماعة سلكية",
                                ).filter { (routeMask, _) ->
                                    routeMask == CallAudioState.ROUTE_SPEAKER ||
                                        routeMask == CallAudioState.ROUTE_EARPIECE ||
                                        (state.supportedAudioMask and routeMask) != 0
                                }
                                routes.forEach { (routeMask, title) ->
                                    val selected = state.activeAudioRoute == routeMask
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (selected) "✓ $title" else title,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        },
                                        onClick = {
                                            showAudioRouteMenu = false
                                            RabtaInCallService.selectAudioRoute(routeMask)
                                        },
                                    )
                                }
                            }
                        }

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
                            active = showAddCallPicker,
                            labelColor = primaryTextColor,
                            onClick = { showAddCallPicker = true },
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

    if (showAddCallPicker) {
        AddCallConferenceSheet(
            onDismiss = { showAddCallPicker = false },
            onDialNumber = { targetPhone ->
                showAddCallPicker = false
                SystemIntents.dialPhone(context, targetPhone)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCallConferenceSheet(
    onDismiss: () -> Unit,
    onDialNumber: (String) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<ContactRecord>>(emptyList()) }

    LaunchedEffect(Unit) {
        contacts = withContext(Dispatchers.IO) {
            runCatching {
                RabtaDatabase.getInstance(context)
                    .contactDao()
                    .getRecentOrAllPage(limit = 400, offset = 0)
                    .map { it.toDomain() }
            }.getOrDefault(emptyList())
        }
    }

    val filtered = remember(query, contacts) {
        if (query.isBlank()) contacts.take(30)
        else T9SearchMatcher.filterAndRank(query, contacts, limit = 30)
    }

    val directDialPhone = remember(query) {
        query.filter { it.isDigit() || it in setOf('+', '*', '#') }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "إضافة مكالمة / مكالمة جماعية (Conference)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("ابحث بالاسم أو اكتب الرقم...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("incall_add_call_search"),
            )

            if (directDialPhone.length >= 3) {
                Button(
                    onClick = { onDialNumber(directDialPhone) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("incall_add_call_direct_btn"),
                ) {
                    Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("اتصال بالرقم: $directDialPhone")
                }
            }

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filtered, key = { it.id }) { contact ->
                    val phone = contact.primaryPhone.ifBlank { contact.phones.firstOrNull().orEmpty() }
                    if (phone.isBlank()) return@items
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDialNumber(phone) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ContactAvatarBadge(
                                displayName = contact.displayName,
                                phone = phone,
                                photoUri = contact.photoUri,
                                colorSeed = contact.avatarColorSeed,
                                showPhoto = true,
                                size = 40.dp,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = phone,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { onDialNumber(phone) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(Icons.Rounded.Call, contentDescription = "اتصال", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("إغلاق")
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallActionToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color? = null,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            shape = CircleShape,
            color = if (active) {
                (activeColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = if (active) {
                activeColor ?: MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label)
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = labelColor, maxLines = 1)
    }
}
