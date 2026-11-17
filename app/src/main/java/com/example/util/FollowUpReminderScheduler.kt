package com.example.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import java.util.Calendar

object FollowUpReminderScheduler {
    const val CHANNEL_ID = "rabta_followups"
    const val ACTION_FOLLOWUP_CONTACT = "com.example.ACTION_FOLLOWUP_CONTACT"
    const val EXTRA_PHONE = "extra_followup_phone"
    const val EXTRA_NAME = "extra_followup_name"
    const val EXTRA_CONTACT_ID = "extra_followup_contact_id"
    const val EXTRA_NOTE = "extra_followup_note"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "تذكيرات المتابعة والاتصال",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "تذكيرات متابعة العملاء والمكالمات المجدولة من Felo Contacts"
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun scheduleAfterMinutes(
        context: Context,
        contactName: String,
        phone: String,
        contactId: String? = null,
        delayMinutes: Int,
        note: String = "",
    ): Long {
        val triggerAt = System.currentTimeMillis() + delayMinutes.coerceAtLeast(1) * 60_000L
        scheduleAtEpoch(context, contactName, phone, contactId, triggerAt, note)
        return triggerAt
    }

    fun scheduleTomorrowMorning(
        context: Context,
        contactName: String,
        phone: String,
        contactId: String? = null,
        hourOfDay: Int = 10,
        note: String = "",
    ): Long {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val triggerAt = cal.timeInMillis
        scheduleAtEpoch(context, contactName, phone, contactId, triggerAt, note)
        return triggerAt
    }

    fun scheduleAtEpoch(
        context: Context,
        contactName: String,
        phone: String,
        contactId: String? = null,
        triggerAtMs: Long,
        note: String = "",
    ) {
        ensureChannel(context)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val reqCode = ((phone + contactName).hashCode() and 0x7FFF) + 9000
        val intent = Intent(context, FollowUpReminderReceiver::class.java).apply {
            putExtra(EXTRA_NAME, contactName)
            putExtra(EXTRA_PHONE, phone)
            putExtra(EXTRA_CONTACT_ID, contactId)
            putExtra(EXTRA_NOTE, note)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        }.onFailure {
            am.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        }
    }
}

class FollowUpReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(FollowUpReminderScheduler.EXTRA_NAME).orEmpty()
        val phone = intent.getStringExtra(FollowUpReminderScheduler.EXTRA_PHONE).orEmpty()
        val contactId = intent.getStringExtra(FollowUpReminderScheduler.EXTRA_CONTACT_ID)
        val note = intent.getStringExtra(FollowUpReminderScheduler.EXTRA_NOTE).orEmpty()

        FollowUpReminderScheduler.ensureChannel(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            action = FollowUpReminderScheduler.ACTION_FOLLOWUP_CONTACT
            putExtra(FollowUpReminderScheduler.EXTRA_PHONE, phone)
            putExtra(FollowUpReminderScheduler.EXTRA_NAME, name)
            putExtra(FollowUpReminderScheduler.EXTRA_CONTACT_ID, contactId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val openPi = PendingIntent.getActivity(
            context,
            (phone.hashCode() and 0x7FFF) + 5000,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}"))
        val dialPi = PendingIntent.getActivity(
            context,
            (phone.hashCode() and 0x7FFF) + 6000,
            dialIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val displayTitle = "تذكير متابعة: ${name.ifBlank { phone }}"
        val body = buildString {
            if (phone.isNotBlank()) append(phone)
            if (note.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append(note)
            }
            if (isEmpty()) append("اضغط للاتصال أو فتح بطاقة الشخص")
        }

        val notif = NotificationCompat.Builder(context, FollowUpReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(displayTitle)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openPi)
            .addAction(0, "اتصال", dialPi)
            .build()

        runCatching {
            nm.notify(((phone + name).hashCode() and 0x7FFF) + 7000, notif)
        }
    }
}
