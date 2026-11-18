package com.example.telecom

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.CallLog
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.example.R
import com.example.ai.LocalTranscriptAnalyzer
import com.example.data.local.AppCallLogEntity
import com.example.data.local.CallTranscriptEntity
import com.example.data.local.LocalMediaCacheManager
import com.example.data.local.PendingSyncEntity
import com.example.data.local.RabtaDatabase
import com.example.data.local.SyncPrefsDataStore
import com.example.data.local.TranscriptConverters
import com.example.domain.model.ContactRecord
import com.example.domain.model.PreCallContext
import com.example.domain.model.SyncEntityType
import com.example.domain.model.SyncOperation
import com.example.domain.model.SyncState
import com.example.util.EgyptGeoLookup
import com.example.util.PhoneUtils
import com.example.util.SystemIntents
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class OtherCallSummary(
    val callId: String,
    val phoneNumber: String,
    val displayName: String,
    val callState: Int,
)

data class ConferenceParticipantUi(
    val callId: String,
    val phoneNumber: String,
    val displayName: String,
    val canSeparate: Boolean,
    val canDisconnect: Boolean,
)

data class ActiveCallUiState(
    val hasActiveCall: Boolean = false,
    val callState: Int = Call.STATE_DISCONNECTED,
    val phoneNumber: String = "",
    val matchedContact: ContactRecord? = null,
    val callerInsight: String? = null,
    val preCallContext: PreCallContext? = null,
    val hideLockscreenDetails: Boolean = false,
    val isBlockedCaller: Boolean = false,
    val elapsedSec: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeaker: Boolean = false,
    val activeAudioRoute: Int = CallAudioState.ROUTE_EARPIECE,
    val supportedAudioMask: Int = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER,
    val isOnHold: Boolean = false,
    val isNearEar: Boolean = false,
    val isRecording: Boolean = false,
    val isRecordAndAiMode: Boolean = false,
    val recordingAmplitude: Int = 0,
    val recordingSourceName: String = "",
    val isLiveTranscribing: Boolean = false,
    val liveTranscriptText: String = "",
    val recordingStatusNote: String? = null,
    val secondaryCall: OtherCallSummary? = null,
    val canSwapCalls: Boolean = false,
    val canMergeConference: Boolean = false,
    val isConferenceCall: Boolean = false,
    val conferenceParticipants: List<ConferenceParticipantUi> = emptyList(),
)

data class PostCallSummary(
    val phone: String,
    val contactId: String?,
    val contactName: String,
    val durationSec: Long,
    val hasRecording: Boolean,
    val endedAtMs: Long,
    val transcriptId: String? = null,
    val oneLineSummary: String? = null,
    val aiRequested: Boolean = false,
)

class RabtaInCallService : InCallService(), SensorEventListener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timerJob: Job? = null
    private var liveJob: Job? = null
    private var amplitudeJob: Job? = null

    private val trackedCalls = ArrayList<Call>()
    private val callNameCache = HashMap<String, ContactRecord?>()
    private var activeCall: Call? = null
    private var callStartTimeMs: Long = 0L
    private var callAddedTimeMs: Long = 0L
    private var callTypeForCurrentCall: Int = CallLog.Calls.OUTGOING_TYPE
    private var callLoggedForCurrentCall: Boolean = false
    private var didVibrateOnActive: Boolean = false
    private var lastSavedRecordingFile: File? = null
    private var speechTranscriber: LocalSpeechTranscriber? = null
    private var collectedLiveText: String = ""

    private var activeRingtone: Ringtone? = null
    private var isRingVibrating: Boolean = false
    private var stopDtmfRunnable: Runnable? = null

    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state != Call.STATE_RINGING) {
                stopIncomingRingingAlert()
            }
            refreshPrimaryAndMultiCallState()
        }

        override fun onChildrenChanged(call: Call, children: List<Call>) {
            refreshPrimaryAndMultiCallState()
        }

        override fun onParentChanged(call: Call, parent: Call?) {
            refreshPrimaryAndMultiCallState()
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            refreshPrimaryAndMultiCallState()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        ensureCallNotificationChannel()
        initProximityHardware()
        speechTranscriber = LocalSpeechTranscriber(applicationContext)
        liveJob = serviceScope.launch {
            speechTranscriber?.state?.collect { st ->
                val txt = st.fullText
                if (txt.isNotBlank()) collectedLiveText = txt

                // Automatic fallback when device lacks offline SpeechRecognizer pack during call
                if (st.requiresAiRecordingFallback &&
                    _uiState.value.hasActiveCall &&
                    !_uiState.value.isRecording
                ) {
                    startRecordingInternal(enableAiTranscribe = true)
                    _uiState.value = _uiState.value.copy(
                        isLiveTranscribing = false,
                        isRecordAndAiMode = true,
                        liveTranscriptText = txt,
                        recordingStatusNote = st.errorNote
                            ?: "تفريغ ذكي تلقائي — جاري تسجيل المكالمة وتحويلها عبر Gemini AI",
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLiveTranscribing = st.isListening,
                        liveTranscriptText = txt,
                        recordingStatusNote = st.errorNote ?: _uiState.value.recordingStatusNote,
                    )
                }
            }
        }
        amplitudeJob = serviceScope.launch {
            launch {
                CallRecorderManager.amplitudeFlow.collect { amp ->
                    _uiState.value = _uiState.value.copy(recordingAmplitude = amp)
                }
            }
            launch {
                CallRecorderManager.activeSourceFlow.collect { src ->
                    _uiState.value = _uiState.value.copy(recordingSourceName = src)
                }
            }
        }
    }

    override fun onDestroy() {
        stopIncomingRingingAlert()
        restoreAudioAfterCalls()
        releaseProximityHardware()
        stopLiveTranscriptionInternal()
        stopRecordingInternal()
        amplitudeJob?.cancel()
        liveJob?.cancel()
        timerJob?.cancel()
        serviceScope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun callIdOf(call: Call): String = System.identityHashCode(call).toString()

    private fun phoneOf(call: Call): String {
        val raw = call.details?.handle?.schemeSpecificPart.orEmpty()
        return PhoneUtils.normalizePhone(raw).ifBlank { raw }
    }

    private fun initProximityHardware() {
        runCatching {
            sensorManager = getSystemService(SensorManager::class.java)
            proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            val pm = getSystemService(PowerManager::class.java)
            if (pm != null && pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                proximityWakeLock = pm.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "FeloContacts:CallProximityLock",
                ).apply {
                    setReferenceCounted(false)
                }
            }
        }
    }

    private fun updateProximityLockState(enable: Boolean) {
        runCatching {
            val sm = sensorManager
            val sensor = proximitySensor
            val wl = proximityWakeLock
            val curRoute = _uiState.value.activeAudioRoute
            val isOnSpeakerOrBt = _uiState.value.isSpeaker ||
                (curRoute and CallAudioState.ROUTE_BLUETOOTH) != 0 ||
                (curRoute and CallAudioState.ROUTE_WIRED_HEADSET) != 0
            if (enable && !isOnSpeakerOrBt) {
                if (sm != null && sensor != null) {
                    sm.unregisterListener(this)
                    sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                }
                if (wl != null && !wl.isHeld) {
                    wl.acquire(60 * 60 * 1000L)
                }
            } else {
                if (wl != null && wl.isHeld) {
                    wl.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
                }
                if (!enable || isOnSpeakerOrBt) {
                    sm?.unregisterListener(this)
                    _uiState.value = _uiState.value.copy(isNearEar = false)
                }
            }
        }
    }

    private fun releaseProximityHardware() {
        runCatching {
            sensorManager?.unregisterListener(this)
            proximityWakeLock?.let { wl ->
                if (wl.isHeld) wl.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
            }
        }
        _uiState.value = _uiState.value.copy(isNearEar = false)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val ev = event ?: return
        if (ev.sensor?.type != Sensor.TYPE_PROXIMITY) return
        val maxRange = ev.sensor.maximumRange.coerceAtMost(5.0f)
        val near = ev.values.firstOrNull()?.let { it < maxRange } ?: false
        _uiState.value = _uiState.value.copy(isNearEar = near && !_uiState.value.isSpeaker)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun startIncomingRingingAlert(isMutedContact: Boolean) {
        if (isMutedContact) return
        val am = getSystemService(AudioManager::class.java)
        val ringerMode = am?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) return

        // Vibrate pattern when normal or vibrate mode
        if (!isRingVibrating && (ringerMode == AudioManager.RINGER_MODE_VIBRATE || ringerMode == AudioManager.RINGER_MODE_NORMAL)) {
            runCatching {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                if (vibrator != null) {
                    val pattern = longArrayOf(0L, 650L, 650L)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(pattern, 0)
                    }
                    isRingVibrating = true
                }
            }
        }

        if (ringerMode == AudioManager.RINGER_MODE_NORMAL && activeRingtone == null) {
            runCatching {
                val ringUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                val ring = RingtoneManager.getRingtone(applicationContext, ringUri) ?: return
                ring.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ring.isLooping = true
                }
                ring.play()
                activeRingtone = ring
            }
        }
    }

    private fun stopIncomingRingingAlert() {
        runCatching {
            activeRingtone?.stop()
        }
        activeRingtone = null
        if (isRingVibrating) {
            runCatching {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                vibrator?.cancel()
            }
            isRingVibrating = false
        }
    }

    private fun restoreAudioAfterCalls() {
        runCatching {
            val am = getSystemService(AudioManager::class.java) ?: return
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = false
            am.mode = AudioManager.MODE_NORMAL
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val isFirstCall = trackedCalls.isEmpty()
        if (trackedCalls.none { it === call }) {
            trackedCalls.add(call)
        }
        call.registerCallback(callCallback)

        val normalized = phoneOf(call)
        val initialState = call.details?.state ?: Call.STATE_CONNECTING

        if (isFirstCall) {
            activeCall = call
            callStartTimeMs = 0L
            callAddedTimeMs = System.currentTimeMillis()
            callTypeForCurrentCall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                when (call.details?.callDirection) {
                    Call.Details.DIRECTION_INCOMING -> CallLog.Calls.INCOMING_TYPE
                    Call.Details.DIRECTION_OUTGOING -> CallLog.Calls.OUTGOING_TYPE
                    else -> if (initialState == Call.STATE_RINGING) CallLog.Calls.INCOMING_TYPE else CallLog.Calls.OUTGOING_TYPE
                }
            } else {
                if (initialState == Call.STATE_RINGING) CallLog.Calls.INCOMING_TYPE else CallLog.Calls.OUTGOING_TYPE
            }
            callLoggedForCurrentCall = false
            didVibrateOnActive = false
            lastSavedRecordingFile = null
            collectedLiveText = ""
            speechTranscriber?.clear()

            val initialInsight = EgyptGeoLookup.inferCallerInsight(normalized)
            _uiState.value = ActiveCallUiState(
                hasActiveCall = true,
                callState = initialState,
                phoneNumber = normalized,
                callerInsight = initialInsight,
                activeAudioRoute = callAudioState?.route ?: CallAudioState.ROUTE_EARPIECE,
                supportedAudioMask = callAudioState?.supportedRouteMask
                    ?: (CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER),
            )
            promoteForegroundMicrophoneIfPermitted()
            updateProximityLockState(enable = initialState != Call.STATE_RINGING)
        } else {
            refreshPrimaryAndMultiCallState()
        }

        serviceScope.launch(Dispatchers.IO) {
            val prefs = SyncPrefsDataStore(applicationContext)
            val hideDetails = prefs.hideLockscreenCallerDetailsFlow.firstOrNull() ?: false
            val db = RabtaDatabase.getInstance(applicationContext)
            val key = PhoneUtils.phoneSearchKey(normalized)
            val matched = if (key.length >= 4) {
                db.contactDao().findByExactPhoneKey(key)?.toDomain()
            } else null
            callNameCache[callIdOf(call)] = matched

            if (initialState == Call.STATE_RINGING && matched?.blocked == true) {
                _uiState.value = _uiState.value.copy(
                    matchedContact = matched,
                    hideLockscreenDetails = hideDetails,
                    isBlockedCaller = true,
                    callerInsight = "تم حظر هذه المكالمة تلقائياً",
                )
                serviceScope.launch(Dispatchers.Main.immediate) {
                    runCatching { call.reject(false, null) }
                }
                return@launch
            }

            if (isFirstCall && initialState == Call.STATE_RINGING) {
                serviceScope.launch(Dispatchers.Main.immediate) {
                    startIncomingRingingAlert(isMutedContact = matched?.muted == true)
                }
            }

            if (isFirstCall || activeCall === call) {
                val preCtx = if (matched != null || key.length >= 4) {
                    val logs = db.appCallLogDao().getForContactOrPhoneOnce(matched?.id, key, limit = 50)
                    val latestTr = db.callTranscriptDao().getLatestForContactOrPhone(
                        contactId = matched?.id.orEmpty(),
                        phoneSuffix = key,
                    )?.toDomain()
                    val lastLog = logs.firstOrNull()
                    val summary = latestTr?.oneLineSummary?.takeIf { it.isNotBlank() }
                        ?: latestTr?.highlights?.firstOrNull()?.takeIf { it.isNotBlank() }
                        ?: latestTr?.extractedNotes?.takeIf { it.isNotBlank() }
                    val pendingActions = latestTr?.actionItems?.count { !it.completed } ?: 0
                    if (lastLog != null || latestTr != null) {
                        PreCallContext(
                            contactId = matched?.id ?: latestTr?.contactId ?: lastLog?.contactId,
                            phoneNumber = normalized.ifBlank { lastLog?.phoneNumber.orEmpty() },
                            lastSpokeEpochMs = lastLog?.timestampMs ?: latestTr?.timestampMs,
                            lastCallDurationSec = lastLog?.durationSec ?: latestTr?.durationSec ?: 0L,
                            lastCallType = lastLog?.callType,
                            oneLineSummary = summary,
                            pendingActionItemsCount = pendingActions,
                            totalCallsCount = logs.size,
                        )
                    } else null
                } else null

                _uiState.value = _uiState.value.copy(
                    matchedContact = matched,
                    preCallContext = preCtx,
                    hideLockscreenDetails = hideDetails,
                    callerInsight = if (matched == null) EgyptGeoLookup.inferCallerInsight(normalized) else null,
                )
            }
            refreshPrimaryAndMultiCallState()
            postCallNotification(_uiState.value)
        }

        if (isFirstCall && initialState == Call.STATE_RINGING) {
            startIncomingRingingAlert(isMutedContact = false)
        }

        postCallNotification(_uiState.value)

        // Heads-Up Notification from Notch behavior:
        // If device is unlocked & screen is on during incoming ringing, show top heads-up CallStyle banner ONLY!
        // Open full-screen InCallActivity automatically when locked/screen-off, outgoing call, or added 2nd call.
        val km = getSystemService(KeyguardManager::class.java)
        val pm = getSystemService(PowerManager::class.java)
        val isUnlockedAndInteractive = (km?.isKeyguardLocked == false) && (pm?.isInteractive == true)
        if (!isFirstCall || initialState != Call.STATE_RINGING || !isUnlockedAndInteractive) {
            launchInCallScreen()
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        trackedCalls.removeAll { it === call }

        val remainingTopCalls = trackedCalls.filter {
            it.parent == null && (it.details?.state ?: Call.STATE_DISCONNECTED) != Call.STATE_DISCONNECTED
        }

        if (remainingTopCalls.isEmpty()) {
            stopIncomingRingingAlert()
            timerJob?.cancel()
            releaseProximityHardware()
            restoreAudioAfterCalls()
            stopLiveTranscriptionInternal()
            stopRecordingInternal()
            persistCompletedCallIfNeeded()
            activeCall = null
            trackedCalls.clear()
            callNameCache.clear()
            _uiState.value = ActiveCallUiState()
            cancelCallNotification()
        } else {
            refreshPrimaryAndMultiCallState()
        }
    }

    private fun refreshPrimaryAndMultiCallState() {
        val topLevel = trackedCalls.filter {
            it.parent == null && (it.details?.state ?: Call.STATE_DISCONNECTED) != Call.STATE_DISCONNECTED
        }
        if (topLevel.isEmpty()) return

        // Priority for primary display: Active > Ringing > Dialing/Connecting > Holding
        val primary = topLevel.firstOrNull { it.details?.state == Call.STATE_ACTIVE }
            ?: topLevel.firstOrNull { it.details?.state == Call.STATE_RINGING }
            ?: topLevel.firstOrNull {
                val st = it.details?.state
                st == Call.STATE_DIALING || st == Call.STATE_CONNECTING || st == Call.STATE_SELECT_PHONE_ACCOUNT
            }
            ?: topLevel.firstOrNull { it.details?.state == Call.STATE_HOLDING }
            ?: topLevel.first()

        activeCall = primary
        val pState = primary.details?.state ?: Call.STATE_DISCONNECTED
        val pPhone = phoneOf(primary)
        val pMatched = callNameCache[callIdOf(primary)] ?: _uiState.value.matchedContact

        val secondary = topLevel.firstOrNull { it !== primary }
        val secondarySummary = secondary?.let { sec ->
            val secPhone = phoneOf(sec)
            val secMatched = callNameCache[callIdOf(sec)]
            OtherCallSummary(
                callId = callIdOf(sec),
                phoneNumber = secPhone,
                displayName = secMatched?.displayName?.ifBlank { secPhone } ?: secPhone.ifBlank { "مكالمة أخرى" },
                callState = sec.details?.state ?: Call.STATE_HOLDING,
            )
        }

        val caps = primary.details?.callCapabilities ?: 0
        val props = primary.details?.callProperties ?: 0
        val children = primary.children
        val isConf = ((props and Call.Details.PROPERTY_CONFERENCE) != 0) || children.isNotEmpty()

        val participants = children.map { child ->
            val cPhone = phoneOf(child)
            val cMatched = callNameCache[callIdOf(child)]
            val cCaps = child.details?.callCapabilities ?: 0
            ConferenceParticipantUi(
                callId = callIdOf(child),
                phoneNumber = cPhone,
                displayName = cMatched?.displayName?.ifBlank { cPhone } ?: cPhone.ifBlank { "مشترك" },
                canSeparate = (cCaps and Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE) != 0,
                canDisconnect = (cCaps and Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE) != 0,
            )
        }

        val canMerge = ((caps and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0) ||
            (topLevel.size >= 2 && topLevel.all {
                val s = it.details?.state
                s == Call.STATE_ACTIVE || s == Call.STATE_HOLDING
            })
        val canSwap = ((caps and Call.Details.CAPABILITY_SWAP_CONFERENCE) != 0) || topLevel.size >= 2

        val primaryLabel = if (isConf && participants.isNotEmpty()) {
            "مكالمة جماعية (${participants.size})"
        } else {
            pPhone
        }

        _uiState.value = _uiState.value.copy(
            callState = pState,
            phoneNumber = primaryLabel,
            matchedContact = if (isConf) null else pMatched,
            isOnHold = pState == Call.STATE_HOLDING,
            secondaryCall = secondarySummary,
            canSwapCalls = canSwap,
            canMergeConference = canMerge,
            isConferenceCall = isConf,
            conferenceParticipants = participants,
        )

        postCallNotification(_uiState.value)
        updateProximityLockState(enable = pState != Call.STATE_RINGING && pState != Call.STATE_DISCONNECTED && !_uiState.value.isSpeaker)

        if (pState == Call.STATE_ACTIVE) {
            stopIncomingRingingAlert()
            if (callStartTimeMs == 0L) {
                callStartTimeMs = System.currentTimeMillis()
                startDurationTimer()
                serviceScope.launch {
                    val prefs = SyncPrefsDataStore(applicationContext)
                    val vibrateEnabled = prefs.vibrateOnAnswerFlow.firstOrNull() ?: true
                    if (vibrateEnabled && !didVibrateOnActive) {
                        didVibrateOnActive = true
                        vibrateBrieflyOnAnswer()
                    }
                    val autoSpeaker = prefs.autoSpeakerForRecordFlow.firstOrNull() ?: false
                    if (autoSpeaker && !_uiState.value.isSpeaker) {
                        toggleSpeaker()
                    }
                    val autoRecord = prefs.autoRecordCallsFlow.firstOrNull() ?: true
                    if (autoRecord && !_uiState.value.isRecording && !_uiState.value.isLiveTranscribing) {
                        startRecordingInternal()
                    }
                }
            }
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        audioState ?: return
        val speakerOn = (audioState.route and CallAudioState.ROUTE_SPEAKER) != 0
        _uiState.value = _uiState.value.copy(
            isMuted = audioState.isMuted,
            isSpeaker = speakerOn,
            activeAudioRoute = audioState.route,
            supportedAudioMask = audioState.supportedRouteMask,
        )
        if (_uiState.value.hasActiveCall && _uiState.value.callState != Call.STATE_DISCONNECTED) {
            updateProximityLockState(enable = !speakerOn && _uiState.value.callState != Call.STATE_RINGING)
        }
    }

    private fun vibrateBrieflyOnAnswer() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(75L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(75L)
            }
        }
    }

    private fun startDurationTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                val sec = ((System.currentTimeMillis() - callStartTimeMs) / 1000L).coerceAtLeast(0L)
                _uiState.value = _uiState.value.copy(elapsedSec = sec)
                delay(1000L)
            }
        }
    }

    private fun promoteForegroundMicrophoneIfPermitted() {
        if (!CallRecorderManager.hasRecordPermission(applicationContext)) return
        val notif = buildCallNotification(_uiState.value)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, notif)
            }
        }
    }

    private fun startRecordingInternal(enableAiTranscribe: Boolean = false) {
        if (_uiState.value.isLiveTranscribing) {
            stopLiveTranscriptionInternal()
        }
        promoteForegroundMicrophoneIfPermitted()
        val label = _uiState.value.matchedContact?.displayName
            ?.ifBlank { _uiState.value.phoneNumber }
            ?: _uiState.value.phoneNumber
        serviceScope.launch {
            val mode = SyncPrefsDataStore(applicationContext).recordingSourceFlow.firstOrNull()
                ?: RecordingSourceMode.SMART_AUTO
            CallRecorderManager.startRecording(applicationContext, label, sourceMode = mode)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isRecording = true,
                        isRecordAndAiMode = enableAiTranscribe || _uiState.value.isRecordAndAiMode,
                        recordingStatusNote = if (enableAiTranscribe || _uiState.value.isRecordAndAiMode) {
                            "جاري تسجيل الصوت + التفريغ بـ Gemini AI"
                        } else {
                            "جاري تسجيل المكالمة (.m4a)"
                        },
                    )
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        isRecording = false,
                        recordingStatusNote = err.localizedMessage ?: "تعذر التسجيل",
                    )
                }
        }
    }

    private fun stopRecordingInternal() {
        if (_uiState.value.isRecording) {
            val saved = CallRecorderManager.stopRecording()
            if (saved != null) {
                lastSavedRecordingFile = saved
            }
            _uiState.value = _uiState.value.copy(
                isRecording = false,
                recordingAmplitude = 0,
                recordingStatusNote = if (saved != null) "تم حفظ التسجيل: ${saved.name}" else null,
            )
        }
    }

    private fun startLiveTranscriptionInternal() {
        if (!CallRecorderManager.hasRecordPermission(applicationContext)) {
            _uiState.value = _uiState.value.copy(
                recordingStatusNote = "يرجى منح صلاحية الميكروفون للتفريغ",
            )
            return
        }
        if (speechTranscriber?.isAvailable() != true) {
            startRecordingInternal(enableAiTranscribe = true)
            _uiState.value = _uiState.value.copy(
                isLiveTranscribing = false,
                isRecordAndAiMode = true,
                recordingStatusNote = "تم التفعيل التلقائي للتسجيل + التفريغ الذكي بـ Gemini AI",
            )
            return
        }
        if (_uiState.value.isRecording) {
            stopRecordingInternal()
        }
        promoteForegroundMicrophoneIfPermitted()
        speechTranscriber?.start("ar-EG")
        _uiState.value = _uiState.value.copy(
            isLiveTranscribing = true,
            recordingStatusNote = "تفريغ مباشر فوري (مع تحويل تلقائي لـ Gemini AI إذا لزم)",
        )
    }

    private fun stopLiveTranscriptionInternal() {
        val text = speechTranscriber?.stop().orEmpty()
        if (text.isNotBlank()) collectedLiveText = text
        _uiState.value = _uiState.value.copy(isLiveTranscribing = false)
    }

    private fun persistCompletedCallIfNeeded() {
        if (callLoggedForCurrentCall) return
        val snapshot = _uiState.value
        val audioFile = lastSavedRecordingFile
        val liveText = collectedLiveText.ifBlank { snapshot.liveTranscriptText }.trim()
        if (snapshot.phoneNumber.isBlank() && audioFile == null && liveText.isBlank()) return
        callLoggedForCurrentCall = true

        val nowMs = System.currentTimeMillis()
        val duration = if (callStartTimeMs > 0L) {
            ((nowMs - callStartTimeMs) / 1000L).coerceAtLeast(1L)
        } else {
            snapshot.elapsedSec
        }
        val finalCallType = if (callTypeForCurrentCall == CallLog.Calls.INCOMING_TYPE && callStartTimeMs == 0L) {
            CallLog.Calls.MISSED_TYPE
        } else {
            callTypeForCurrentCall
        }

        val contactId = snapshot.matchedContact?.id
        val contactName = snapshot.matchedContact?.displayName
            ?.ifBlank { snapshot.phoneNumber }
            ?: snapshot.phoneNumber.ifBlank { "مكالمة" }
        val hasValidAudio = audioFile != null && audioFile.exists() && audioFile.length() > 256L
        val audioSizeBytes = if (hasValidAudio) audioFile!!.length() else 0L
        val audioFileName = audioFile?.name.orEmpty()
        val transcriptId = "tr_" + UUID.randomUUID().toString().replace("-", "").take(14)

        val entry = if (liveText.isNotBlank()) {
            val localAnalysis = LocalTranscriptAnalyzer.analyze(
                rawTranscript = liveText,
                contactName = contactName,
                phone = snapshot.phoneNumber,
                referenceTimeMs = nowMs,
                durationSec = duration,
            )
            CallTranscriptEntity(
                id = transcriptId,
                contactId = contactId,
                phone = snapshot.phoneNumber,
                contactName = contactName,
                audioFilePath = audioFile?.absolutePath.orEmpty(),
                transcript = localAnalysis.transcript,
                highlightsJoined = localAnalysis.highlights.joinToString("\u001F"),
                extractedNotes = localAnalysis.extractedNotes,
                suggestedEventTitle = localAnalysis.eventTitle,
                suggestedEventDateIso = localAnalysis.eventDateIso,
                suggestedEventEpochMs = localAnalysis.eventEpochMs,
                timestampMs = nowMs,
                durationSec = duration,
                status = "READY",
                summaryBulletsJoined = localAnalysis.summaryBullets.joinToString("\u001F"),
                oneLineSummary = localAnalysis.oneLineSummary,
                segmentsJson = TranscriptConverters.encodeSegments(localAnalysis.segments),
                actionItemsJson = TranscriptConverters.encodeActionItems(localAnalysis.actionItems),
                audioFileName = audioFileName,
                audioSizeBytes = audioSizeBytes,
                audioCachedLocally = hasValidAudio,
                updatedAtEpochMs = nowMs,
                syncState = SyncState.PENDING.name,
            )
        } else {
            CallTranscriptEntity(
                id = transcriptId,
                contactId = contactId,
                phone = snapshot.phoneNumber,
                contactName = contactName,
                audioFilePath = audioFile?.absolutePath.orEmpty(),
                timestampMs = nowMs,
                durationSec = duration,
                status = when {
                    hasValidAudio && snapshot.isRecordAndAiMode -> "PENDING_AI"
                    hasValidAudio -> "PENDING"
                    else -> "NO_AUDIO"
                },
                audioFileName = audioFileName,
                audioSizeBytes = audioSizeBytes,
                audioCachedLocally = hasValidAudio,
                updatedAtEpochMs = nowMs,
                syncState = SyncState.PENDING.name,
            )
        }

        val callPhone = snapshot.phoneNumber.ifBlank { contactName }
        val phoneKey = PhoneUtils.phoneSearchKey(callPhone).ifBlank { callPhone }
        val callTs = callAddedTimeMs.takeIf { it > 0L } ?: nowMs
        val safeKey = phoneKey.replace(Regex("[^a-zA-Z0-9]"), "").ifBlank { "call" }
        val callLogEntry = AppCallLogEntity(
            id = "cl_${callTs}_${safeKey}".take(120),
            contactId = contactId,
            phoneNumber = callPhone,
            phoneKey = phoneKey,
            cachedName = snapshot.matchedContact?.displayName,
            callType = finalCallType,
            simSlot = null,
            timestampMs = callTs,
            durationSec = duration,
            transcriptId = entry.id,
            hasRecording = hasValidAudio,
            notes = entry.oneLineSummary.takeIf { it.isNotBlank() },
            isDeleted = false,
            deletedAtEpochMs = null,
            updatedAtEpochMs = nowMs,
            syncState = SyncState.PENDING.name,
        )

        if (snapshot.phoneNumber.isNotBlank()) {
            _lastEndedCallSummary.value = PostCallSummary(
                phone = snapshot.phoneNumber,
                contactId = contactId,
                contactName = contactName,
                durationSec = duration,
                hasRecording = hasValidAudio,
                endedAtMs = nowMs,
                transcriptId = entry.id,
                oneLineSummary = entry.oneLineSummary.takeIf { it.isNotBlank() },
                aiRequested = snapshot.isRecordAndAiMode,
            )
        }

        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                val room = RabtaDatabase.getInstance(applicationContext)
                if (hasValidAudio && audioFile != null) {
                    LocalMediaCacheManager.ensureRecordingCachedLocally(applicationContext, audioFile)
                }
                room.callTranscriptDao().upsert(entry)
                room.appCallLogDao().upsert(callLogEntry)
                if (contactId != null) {
                    room.contactDao().updateLastContacted(contactId, nowMs)
                }
                room.pendingSyncDao().enqueueBatch(
                    listOfNotNull(
                        PendingSyncEntity(
                            queueKey = PendingSyncEntity.keyFor(SyncEntityType.CALL_TRANSCRIPT.name, entry.id),
                            entityType = SyncEntityType.CALL_TRANSCRIPT.name,
                            entityId = entry.id,
                            operation = SyncOperation.UPSERT.name,
                            enqueuedAtEpochMs = nowMs,
                            entityUpdatedAtEpochMs = nowMs,
                        ),
                        PendingSyncEntity(
                            queueKey = PendingSyncEntity.keyFor(SyncEntityType.CALL_LOG.name, callLogEntry.id),
                            entityType = SyncEntityType.CALL_LOG.name,
                            entityId = callLogEntry.id,
                            operation = SyncOperation.UPSERT.name,
                            enqueuedAtEpochMs = nowMs,
                            entityUpdatedAtEpochMs = nowMs,
                        ),
                        contactId?.let { cid ->
                            PendingSyncEntity(
                                queueKey = PendingSyncEntity.keyFor(SyncEntityType.CONTACT.name, cid),
                                entityType = SyncEntityType.CONTACT.name,
                                entityId = cid,
                                operation = SyncOperation.UPSERT.name,
                                enqueuedAtEpochMs = nowMs,
                                entityUpdatedAtEpochMs = nowMs,
                            )
                        },
                    )
                )
                LocalMediaCacheManager.cacheTranscriptPayloadLocally(applicationContext, entry.toDomain())
            }
        }
    }

    private fun launchInCallScreen() {
        val intent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching { startActivity(intent) }
    }

    private fun ensureCallNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "مكالمات Felo Contacts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "إشعار المكالمة الواردة والنشطة من الأعلى"
                // Sound and vibration are handled cleanly by RabtaInCallService ringtone controller
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun actionPendingIntent(action: String, requestCode: Int, openUi: Boolean = false): PendingIntent {
        val intent = Intent(this, CallNotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(CallNotificationActionReceiver.EXTRA_OPEN_CALL_UI, openUi)
        }
        return PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildCallNotification(state: ActiveCallUiState): Notification {
        val contentIntent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pi = PendingIntent.getActivity(
            this,
            101,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val isMutedContact = state.matchedContact?.muted == true
        val rawTitle = state.matchedContact?.displayName?.ifBlank { state.phoneNumber }
            ?: state.phoneNumber.ifBlank { "مكالمة Felo Contacts" }
        val displayTitle = if (state.hideLockscreenDetails) "مكالمة Felo Contacts" else rawTitle

        val personBuilder = Person.Builder()
            .setName(displayTitle)
            .setImportant(true)
        if (!state.hideLockscreenDetails) {
            val photoPath = state.matchedContact?.photoUri?.takeIf { it.isNotBlank() && File(it).exists() }
            if (photoPath != null) {
                runCatching { BitmapFactory.decodeFile(photoPath) }.getOrNull()?.let { bmp ->
                    personBuilder.setIcon(IconCompat.createWithBitmap(bmp))
                }
            }
        }
        val callerPerson = personBuilder.build()

        val declinePi = actionPendingIntent(CallNotificationActionReceiver.ACTION_DECLINE_CALL, 201)
        val answerPi = actionPendingIntent(CallNotificationActionReceiver.ACTION_ANSWER_CALL, 202, openUi = false)
        val hangupPi = actionPendingIntent(CallNotificationActionReceiver.ACTION_HANGUP_CALL, 203)

        val callStyle = if (state.callState == Call.STATE_RINGING) {
            NotificationCompat.CallStyle.forIncomingCall(callerPerson, declinePi, answerPi)
        } else {
            NotificationCompat.CallStyle.forOngoingCall(callerPerson, hangupPi)
        }

        val subtitle = when (state.callState) {
            Call.STATE_RINGING -> {
                val base = if (isMutedContact) "مكالمة واردة (صامت)" else "مكالمة واردة..."
                if (!state.hideLockscreenDetails && state.phoneNumber.isNotBlank() && state.phoneNumber != rawTitle) {
                    "$base • ${state.phoneNumber}"
                } else base
            }
            Call.STATE_HOLDING -> "مكالمة قيد الانتظار"
            Call.STATE_DIALING, Call.STATE_CONNECTING -> "جاري الاتصال..."
            else -> if (state.isConferenceCall) "مكالمة جماعية نشطة" else "مكالمة جارية"
        }

        val km = getSystemService(KeyguardManager::class.java)
        val pm = getSystemService(PowerManager::class.java)
        val shouldUseFullScreen = state.callState == Call.STATE_RINGING &&
            ((km?.isKeyguardLocked == true) || (pm?.isInteractive == false))

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(displayTitle)
            .setContentText(subtitle)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColorized(true)
            .setSilent(true)
            .setContentIntent(pi)
            .addPerson(callerPerson)
            .setStyle(callStyle)

        if (shouldUseFullScreen) {
            builder.setFullScreenIntent(pi, true)
        }

        if (state.hideLockscreenDetails) {
            val publicVersion = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("مكالمة Felo Contacts")
                .setContentText("هوية المتصل مخفية للخصوصية")
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .build()
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
        }
        return builder.build()
    }

    private fun postCallNotification(state: ActiveCallUiState) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.notify(NOTIF_ID, buildCallNotification(state)) }
    }

    private fun cancelCallNotification() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
    }

    companion object {
        private const val CHANNEL_ID = "rabta_calls_v2"
        private const val NOTIF_ID = 4401

        @Volatile
        private var instance: RabtaInCallService? = null

        private val _uiState = MutableStateFlow(ActiveCallUiState())
        val uiState = _uiState.asStateFlow()

        private val _lastEndedCallSummary = MutableStateFlow<PostCallSummary?>(null)
        val lastEndedCallSummary = _lastEndedCallSummary.asStateFlow()

        fun clearLastEndedCallSummary() {
            _lastEndedCallSummary.value = null
        }

        fun isDefaultDialer(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = context.getSystemService(RoleManager::class.java)
                rm?.isRoleHeld(RoleManager.ROLE_DIALER) == true
            } else {
                val tm = context.getSystemService(TelecomManager::class.java)
                tm?.defaultDialerPackage == context.packageName
            }
        }

        fun createRequestDefaultDialerIntent(context: Context): Intent? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = context.getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
                    rm.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                } else null
            } else {
                @Suppress("DEPRECATION")
                Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
                }
            }
        }

        fun answerCall() {
            val svc = instance ?: return
            svc.stopIncomingRingingAlert()
            val ringing = svc.trackedCalls.firstOrNull { it.details?.state == Call.STATE_RINGING } ?: svc.activeCall
            ringing?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }

        fun endCall() {
            val svc = instance ?: return
            svc.stopIncomingRingingAlert()
            val call = svc.activeCall ?: return
            if (call.details?.state == Call.STATE_RINGING) {
                call.reject(false, null)
            } else {
                call.disconnect()
            }
        }

        fun disconnectCallById(callId: String) {
            val svc = instance ?: return
            val target = svc.trackedCalls.firstOrNull { svc.callIdOf(it) == callId } ?: return
            if (target.details?.state == Call.STATE_RINGING) {
                target.reject(false, null)
            } else {
                target.disconnect()
            }
        }

        fun splitConferenceParticipant(callId: String) {
            val svc = instance ?: return
            val target = svc.trackedCalls.firstOrNull { svc.callIdOf(it) == callId } ?: return
            runCatching { target.splitFromConference() }
        }

        fun swapCalls() {
            val svc = instance ?: return
            val topLevel = svc.trackedCalls.filter {
                it.parent == null && (it.details?.state ?: Call.STATE_DISCONNECTED) != Call.STATE_DISCONNECTED
            }
            val primary = svc.activeCall
            if (primary != null && ((primary.details?.callCapabilities ?: 0) and Call.Details.CAPABILITY_SWAP_CONFERENCE) != 0) {
                runCatching { primary.swapConference() }
                return
            }
            val held = topLevel.firstOrNull { it.details?.state == Call.STATE_HOLDING }
            if (held != null) {
                runCatching { held.unhold() }
            } else if (topLevel.size >= 2) {
                topLevel.firstOrNull { it === primary }?.let { runCatching { it.hold() } }
            }
        }

        fun mergeConference() {
            val svc = instance ?: return
            val topLevel = svc.trackedCalls.filter {
                it.parent == null && (it.details?.state ?: Call.STATE_DISCONNECTED) != Call.STATE_DISCONNECTED
            }
            val primary = svc.activeCall ?: topLevel.firstOrNull() ?: return
            val caps = primary.details?.callCapabilities ?: 0
            if ((caps and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0) {
                runCatching { primary.mergeConference() }
                return
            }
            val other = topLevel.firstOrNull { it !== primary } ?: return
            runCatching { primary.conference(other) }
        }

        fun rejectWithWhatsApp(context: Context, busyMessage: String) {
            val svc = instance
            svc?.stopIncomingRingingAlert()
            val phone = _uiState.value.phoneNumber
            val call = svc?.trackedCalls?.firstOrNull { it.details?.state == Call.STATE_RINGING } ?: svc?.activeCall
            if (call?.details?.state == Call.STATE_RINGING) {
                runCatching { call.reject(false, null) }
            }
            if (phone.isNotBlank() && !PhoneUtils.isUssdOrShortCode(phone)) {
                SystemIntents.openWhatsApp(context, phone, busyMessage)
            }
        }

        fun toggleMute() {
            val svc = instance ?: return
            val next = !_uiState.value.isMuted
            svc.setMuted(next)
            _uiState.value = _uiState.value.copy(isMuted = next)
        }

        fun selectAudioRoute(route: Int) {
            val svc = instance ?: return
            svc.setAudioRoute(route)
            val speakerOn = (route and CallAudioState.ROUTE_SPEAKER) != 0
            val am = svc.getSystemService(AudioManager::class.java)
            @Suppress("DEPRECATION")
            runCatching { am?.isSpeakerphoneOn = speakerOn }
            _uiState.value = _uiState.value.copy(
                isSpeaker = speakerOn,
                activeAudioRoute = route,
            )
            svc.updateProximityLockState(enable = !speakerOn && _uiState.value.callState != Call.STATE_RINGING)
        }

        fun toggleSpeaker() {
            val next = !_uiState.value.isSpeaker
            val route = if (next) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
            selectAudioRoute(route)
        }

        fun toggleHold() {
            val call = instance?.activeCall ?: return
            if (_uiState.value.isOnHold) call.unhold() else call.hold()
        }

        fun sendDtmf(digit: Char) {
            val svc = instance ?: return
            val call = svc.activeCall ?: return
            svc.stopDtmfRunnable?.let { svc.mainHandler.removeCallbacks(it) }
            runCatching { call.playDtmfTone(digit) }
            val r = Runnable { runCatching { call.stopDtmfTone() } }
            svc.stopDtmfRunnable = r
            svc.mainHandler.postDelayed(r, 180L)
        }

        fun toggleRecording() {
            val svc = instance ?: return
            if (_uiState.value.isRecording) {
                svc.stopRecordingInternal()
                _uiState.value = _uiState.value.copy(isRecordAndAiMode = false)
            } else {
                svc.startRecordingInternal(enableAiTranscribe = false)
            }
        }

        fun toggleRecordAndTranscribe() {
            val svc = instance ?: return
            if (_uiState.value.isRecording) {
                svc.stopRecordingInternal()
                _uiState.value = _uiState.value.copy(isRecordAndAiMode = false)
            } else {
                svc.startRecordingInternal(enableAiTranscribe = true)
            }
        }

        fun toggleLiveTranscription() {
            val svc = instance ?: return
            if (_uiState.value.isLiveTranscribing) {
                svc.stopLiveTranscriptionInternal()
            } else {
                svc.startLiveTranscriptionInternal()
            }
        }
    }
}
