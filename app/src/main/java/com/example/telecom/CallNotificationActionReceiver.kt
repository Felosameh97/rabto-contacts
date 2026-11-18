package com.example.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles direct Answer / Decline / Hangup / Quick Audio actions from the
 * heads-up CallStyle notification without forcing the full-screen UI open first.
 */
class CallNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_ANSWER_CALL -> {
                RabtaInCallService.answerCall()
                if (intent.getBooleanExtra(EXTRA_OPEN_CALL_UI, false)) {
                    val callIntent = Intent(context, InCallActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    runCatching { context.startActivity(callIntent) }
                }
            }
            ACTION_DECLINE_CALL, ACTION_HANGUP_CALL -> {
                RabtaInCallService.endCall()
            }
            ACTION_TOGGLE_MUTE -> {
                RabtaInCallService.toggleMute()
            }
            ACTION_TOGGLE_SPEAKER -> {
                RabtaInCallService.toggleSpeaker()
            }
        }
    }

    companion object {
        const val ACTION_ANSWER_CALL = "com.example.telecom.ACTION_ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "com.example.telecom.ACTION_DECLINE_CALL"
        const val ACTION_HANGUP_CALL = "com.example.telecom.ACTION_HANGUP_CALL"
        const val ACTION_TOGGLE_MUTE = "com.example.telecom.ACTION_TOGGLE_MUTE"
        const val ACTION_TOGGLE_SPEAKER = "com.example.telecom.ACTION_TOGGLE_SPEAKER"
        const val EXTRA_OPEN_CALL_UI = "extra_open_call_ui"
    }
}
