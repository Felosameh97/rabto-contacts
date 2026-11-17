package com.example.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.ai.LocalTranscriptAnalyzer
import com.example.data.local.CallTranscriptEntity
import com.example.data.local.RabtaDatabase
import com.example.data.local.SyncPrefsDataStore
import com.example.domain.model.ContactRecord
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

data class ActiveCallUiState(
    val hasActiveCall: Boolean = false,
    val callState: Int = Call.STATE_DISCONNECTED,
    val phoneNumber: String = "",
    val matchedContact: ContactRecord? = null,
    val callerInsight: String? = null,
    val elapsedSec: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeaker: Boolean = false,
    val isOnHold: Boolean = false,
    val isNearEar: Boolean = false,
    val isRecording: Boolean = false,
    val recordingAmplitude: Int = 0,
    val recordingSourceName: String = "",
    val isLiveTranscribing: Boolean = false,
    val liveTranscriptText: String = "",
    val recordingStatusNote: String? = null,
)

data class PostCallSummary(
    val phone: String,
    val contactId: String?,
    val contactName: String,
    val durationSec: Long,
    val hasRecording: Boolean,
    val endedAtMs: Long,
)

class RabtaInCallService : InCallService(), SensorEventListener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null
    private var liveJob: Job? = null
    private var amplitudeJob: Job? = null
    private var activeCall: Call? = null
    private var callStartTimeMs: Long = 0L
    private var callLoggedForCurrentCall: Boolean = false
    private var didVibrateOnActive: Boolean = false
    private var lastSavedRecordingFile: File? = null
    private var speechTranscriber: LocalSpeechTranscriber? = null
    private var collectedLiveText: String = ""

    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            updateCallState(call, state)
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
                _uiState.value = _uiState.value.copy(
                    isLiveTranscribing = st.isListening,
                    liveTranscriptText = txt,
                    recordingStatusNote = st.errorNote ?: _uiState.value.recordingStatusNote,
                )
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
            if (enable && !_uiState.value.isSpeaker) {
                if (sm != null && sensor != null) {
                    sm.unregisterListener(this)
                    sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                }
                if (wl != null && !wl.isHeld) {
                    wl.acquire(60 * 60 * 1000L) // up to 60 min max guard
                }
            } else {
                if (wl != null && wl.isHeld) {
                    wl.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
                }
                if (!enable) {
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

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        activeCall?.unregisterCallback(callCallback)
        activeCall = call
        call.registerCallback(callCallback)

        val rawNumber = call.details?.handle?.schemeSpecificPart.orEmpty()
        val normalized = PhoneUtils.normalizePhone(rawNumber).ifBlank { rawNumber }
        callStartTimeMs = 0L
        callLoggedForCurrentCall = false
        didVibrateOnActive = false
        lastSavedRecordingFile = null
        collectedLiveText = ""
        speechTranscriber?.clear()

        val initialState = call.details?.state ?: Call.STATE_CONNECTING
        val initialInsight = EgyptGeoLookup.inferCallerInsight(normalized)

        _uiState.value = ActiveCallUiState(
            hasActiveCall = true,
            callState = initialState,
            phoneNumber = normalized,
            callerInsight = initialInsight,
        )

        // Promote foreground type early so microphone capture is allowed throughout call
        promoteForegroundMicrophoneIfPermitted()
        updateProximityLockState(enable = initialState != Call.STATE_RINGING)

        serviceScope.launch(Dispatchers.IO) {
            val key = PhoneUtils.phoneSearchKey(normalized)
            val matched = if (key.length >= 4) {
                RabtaDatabase.getInstance(applicationContext)
                    .contactDao()
                    .findByExactPhoneKey(key)
                    ?.toDomain()
            } else null

            _uiState.value = _uiState.value.copy(
                matchedContact = matched,
                callerInsight = if (matched == null) initialInsight else null,
            )
            postCallNotification(_uiState.value)
        }

        postCallNotification(_uiState.value)
        launchInCallScreen()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        if (activeCall === call) {
            timerJob?.cancel()
            releaseProximityHardware()
            stopLiveTranscriptionInternal()
            stopRecordingInternal()
            persistCompletedCallIfNeeded()
            activeCall = null
            _uiState.value = ActiveCallUiState()
            cancelCallNotification()
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        audioState ?: return
        val speakerOn = (audioState.route and CallAudioState.ROUTE_SPEAKER) != 0
        _uiState.value = _uiState.value.copy(
            isMuted = audioState.isMuted,
            isSpeaker = speakerOn,
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

    private fun updateCallState(call: Call, state: Int) {
        _uiState.value = _uiState.value.copy(
            callState = state,
            isOnHold = state == Call.STATE_HOLDING,
        )
        postCallNotification(_uiState.value)
        updateProximityLockState(enable = state != Call.STATE_RINGING && state != Call.STATE_DISCONNECTED && !_uiState.value.isSpeaker)

        if (state == Call.STATE_ACTIVE) {
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
        } else if (state == Call.STATE_DISCONNECTED) {
            timerJob?.cancel()
            releaseProximityHardware()
            stopLiveTranscriptionInternal()
            stopRecordingInternal()
            persistCompletedCallIfNeeded()
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

    private fun startRecordingInternal() {
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
                        recordingStatusNote = "جاري تسجيل المكالمة (.m4a)",
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
                recordingStatusNote = "يرجى منح صلاحية الميكروفون للتفريغ المباشر",
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
            recordingStatusNote = "تفريغ مباشر فوري (بدون Gemini)",
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

        val duration = if (callStartTimeMs > 0L) {
            ((System.currentTimeMillis() - callStartTimeMs) / 1000L).coerceAtLeast(1L)
        } else {
            snapshot.elapsedSec
        }
        val contactId = snapshot.matchedContact?.id
        val contactName = snapshot.matchedContact?.displayName
            ?.ifBlank { snapshot.phoneNumber }
            ?: snapshot.phoneNumber.ifBlank { "مكالمة" }

        val entry = if (liveText.isNotBlank()) {
            val localAnalysis = LocalTranscriptAnalyzer.analyze(
                rawTranscript = liveText,
                contactName = contactName,
                phone = snapshot.phoneNumber,
            )
            CallTranscriptEntity(
                id = "tr_" + UUID.randomUUID().toString().replace("-", "").take(14),
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
                timestampMs = System.currentTimeMillis(),
                durationSec = duration,
                status = "READY",
            )
        } else {
            CallTranscriptEntity(
                id = "tr_" + UUID.randomUUID().toString().replace("-", "").take(14),
                contactId = contactId,
                phone = snapshot.phoneNumber,
                contactName = contactName,
                audioFilePath = audioFile?.absolutePath.orEmpty(),
                timestampMs = System.currentTimeMillis(),
                durationSec = duration,
                status = if (audioFile != null && audioFile.exists() && audioFile.length() > 256L) "PENDING" else "NO_AUDIO",
            )
        }

        if (snapshot.phoneNumber.isNotBlank()) {
            _lastEndedCallSummary.value = PostCallSummary(
                phone = snapshot.phoneNumber,
                contactId = contactId,
                contactName = contactName,
                durationSec = duration,
                hasRecording = audioFile != null && audioFile.exists() && audioFile.length() > 256L,
                endedAtMs = System.currentTimeMillis(),
            )
        }

        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                RabtaDatabase.getInstance(applicationContext).callTranscriptDao().upsert(entry)
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
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "شاشة المكالمة الواردة والنشطة"
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildCallNotification(state: ActiveCallUiState): Notification {
        val fullScreenIntent = Intent(this, InCallActivity::class.java)
        val pi = PendingIntent.getActivity(
            this,
            101,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = state.matchedContact?.displayName?.ifBlank { state.phoneNumber } ?: state.phoneNumber
        val subtitle = when (state.callState) {
            Call.STATE_RINGING -> "مكالمة واردة..."
            Call.STATE_ACTIVE -> "مكالمة جارية"
            Call.STATE_HOLDING -> "قيد الانتظار"
            else -> "جاري الاتصال..."
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title.ifBlank { "مكالمة Felo Contacts" })
            .setContentText(subtitle)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
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
        private const val CHANNEL_ID = "rabta_calls"
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
            instance?.activeCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }

        fun endCall() {
            val call = instance?.activeCall ?: return
            if (call.details?.state == Call.STATE_RINGING) {
                call.reject(false, null)
            } else {
                call.disconnect()
            }
        }

        fun rejectWithWhatsApp(context: Context, busyMessage: String) {
            val phone = _uiState.value.phoneNumber
            val call = instance?.activeCall
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

        fun toggleSpeaker() {
            val svc = instance ?: return
            val next = !_uiState.value.isSpeaker
            val route = if (next) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
            svc.setAudioRoute(route)
            val am = svc.getSystemService(AudioManager::class.java)
            @Suppress("DEPRECATION")
            runCatching { am?.isSpeakerphoneOn = next }
            _uiState.value = _uiState.value.copy(isSpeaker = next)
            svc.updateProximityLockState(enable = !next && _uiState.value.callState != Call.STATE_RINGING)
        }

        fun toggleHold() {
            val call = instance?.activeCall ?: return
            if (_uiState.value.isOnHold) call.unhold() else call.hold()
        }

        fun sendDtmf(digit: Char) {
            val call = instance?.activeCall ?: return
            call.playDtmfTone(digit)
            call.stopDtmfTone()
        }

        fun toggleRecording() {
            val svc = instance ?: return
            if (_uiState.value.isRecording) {
                svc.stopRecordingInternal()
            } else {
                svc.startRecordingInternal()
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
