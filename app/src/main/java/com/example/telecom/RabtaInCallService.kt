package com.example.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.local.RabtaDatabase
import com.example.data.local.SyncPrefsDataStore
import com.example.domain.model.ContactRecord
import com.example.util.PhoneUtils
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
    val elapsedSec: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeaker: Boolean = false,
    val isOnHold: Boolean = false,
    val isRecording: Boolean = false,
    val recordingStatusNote: String? = null,
)

class RabtaInCallService : InCallService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null
    private var activeCall: Call? = null
    private var callStartTimeMs: Long = 0L

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            updateCallState(call, state)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        ensureCallNotificationChannel()
    }

    override fun onDestroy() {
        stopRecordingInternal()
        timerJob?.cancel()
        serviceScope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        activeCall?.unregisterCallback(callCallback)
        activeCall = call
        call.registerCallback(callCallback)

        val rawNumber = call.details?.handle?.schemeSpecificPart.orEmpty()
        val normalized = PhoneUtils.normalizePhone(rawNumber).ifBlank { rawNumber }
        callStartTimeMs = 0L

        _uiState.value = ActiveCallUiState(
            hasActiveCall = true,
            callState = call.details?.state ?: Call.STATE_CONNECTING,
            phoneNumber = normalized,
        )

        // Instant indexed lookup in our 20k+ Room database
        serviceScope.launch(Dispatchers.IO) {
            val key = PhoneUtils.phoneSearchKey(normalized)
            val matched = if (key.length >= 4) {
                RabtaDatabase.getInstance(applicationContext)
                    .contactDao()
                    .findByExactPhoneKey(key)
                    ?.toDomain()
            } else null

            _uiState.value = _uiState.value.copy(matchedContact = matched)
            postCallNotification(_uiState.value)
        }

        postCallNotification(_uiState.value)
        launchInCallScreen()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        if (activeCall === call) {
            activeCall = null
            timerJob?.cancel()
            stopRecordingInternal()
            _uiState.value = ActiveCallUiState()
            cancelCallNotification()
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        audioState ?: return
        _uiState.value = _uiState.value.copy(
            isMuted = audioState.isMuted,
            isSpeaker = (audioState.route and CallAudioState.ROUTE_SPEAKER) != 0,
        )
    }

    private fun updateCallState(call: Call, state: Int) {
        _uiState.value = _uiState.value.copy(
            callState = state,
            isOnHold = state == Call.STATE_HOLDING,
        )
        postCallNotification(_uiState.value)

        if (state == Call.STATE_ACTIVE) {
            if (callStartTimeMs == 0L) {
                callStartTimeMs = System.currentTimeMillis()
                startDurationTimer()
                serviceScope.launch {
                    val autoRecord = SyncPrefsDataStore(applicationContext).autoRecordCallsFlow.firstOrNull() ?: false
                    if (autoRecord && !_uiState.value.isRecording) {
                        startRecordingInternal()
                    }
                }
            }
        } else if (state == Call.STATE_DISCONNECTED) {
            timerJob?.cancel()
            stopRecordingInternal()
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

    private fun startRecordingInternal() {
        val label = _uiState.value.matchedContact?.displayName
            ?.ifBlank { _uiState.value.phoneNumber }
            ?: _uiState.value.phoneNumber
        CallRecorderManager.startRecording(applicationContext, label)
            .onSuccess {
                _uiState.value = _uiState.value.copy(
                    isRecording = true,
                    recordingStatusNote = "جاري التسجيل محلياً (.m4a)",
                )
            }
            .onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isRecording = false,
                    recordingStatusNote = err.localizedMessage ?: "تعذر التسجيل",
                )
            }
    }

    private fun stopRecordingInternal() {
        if (_uiState.value.isRecording) {
            val saved = CallRecorderManager.stopRecording()
            _uiState.value = _uiState.value.copy(
                isRecording = false,
                recordingStatusNote = if (saved != null) "تم حفظ التسجيل: ${saved.name}" else null,
            )
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
                "مكالمات رابطة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "شاشة المكالمة الواردة والنشطة"
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun postCallNotification(state: ActiveCallUiState) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
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
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title.ifBlank { "مكالمة رابطة" })
            .setContentText(subtitle)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
        runCatching { nm.notify(NOTIF_ID, notif) }
    }

    private fun cancelCallNotification() {
        getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
    }

    companion object {
        private const val CHANNEL_ID = "rabta_calls"
        private const val NOTIF_ID = 4401

        @Volatile
        private var instance: RabtaInCallService? = null

        private val _uiState = MutableStateFlow(ActiveCallUiState())
        val uiState = _uiState.asStateFlow()

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
    }
}
