package com.example.util

import com.example.domain.model.ContactRecord
import java.security.MessageDigest

object AppLockSecurity {

    private const val SALT = "felo_contacts_pin_v1:"

    fun isValidPinFormat(pin: String): Boolean {
        return pin.length in 4..6 && pin.all { it in '0'..'9' }
    }

    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((SALT + pin).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(pin: String, storedHash: String): Boolean {
        if (storedHash.isBlank() || pin.isBlank()) return false
        val candidate = hashPin(pin)
        if (candidate.length != storedHash.length) return false
        var diff = 0
        for (i in candidate.indices) {
            diff = diff or (candidate[i].code xor storedHash[i].code)
        }
        return diff == 0
    }

    fun formatDurationClock(durationSec: Long): String {
        val safe = durationSec.coerceAtLeast(0L)
        val hours = safe / 3600L
        val mins = (safe % 3600L) / 60L
        val secs = safe % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, mins, secs)
        } else {
            "%02d:%02d".format(mins, secs)
        }
    }

    fun formatRelativeTimeAr(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (epochMs <= 0L) return ""
        val diffMs = (nowMs - epochMs).coerceAtLeast(0L)
        val minutes = diffMs / 60_000L
        val hours = diffMs / 3_600_000L
        val days = diffMs / 86_400_000L
        return when {
            minutes < 2 -> "منذ لحظات"
            minutes < 60 -> "منذ $minutes دقيقة"
            hours == 1L -> "منذ ساعة"
            hours == 2L -> "منذ ساعتين"
            hours < 24 -> "منذ $hours ساعات"
            days == 1L -> "أمس"
            days == 2L -> "منذ يومين"
            days < 11 -> "منذ $days أيام"
            days < 30 -> "منذ $days يوماً"
            else -> {
                val months = days / 30L
                if (months == 1L) "منذ شهر" else "منذ $months شهور"
            }
        }
    }

    fun formatContactShareText(contact: ContactRecord): String = buildString {
        append(contact.displayName.ifBlank { contact.primaryPhone })
        contact.nickname?.takeIf { it.isNotBlank() }?.let { append(" ($it)") }
        val phones = (listOf(contact.primaryPhone) + contact.phones)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (phones.isNotEmpty()) {
            append("\nالهاتف: ").append(phones.joinToString(" | "))
        }
        if (contact.emails.isNotEmpty()) {
            append("\nالبريد: ").append(contact.emails.joinToString(", "))
        }
        val role = listOfNotNull(contact.jobTitle, contact.organization).filter { it.isNotBlank() }.joinToString(" - ")
        if (role.isNotBlank()) {
            append("\nالعمل: ").append(role)
        }
        val loc = listOfNotNull(contact.district, contact.address).filter { it.isNotBlank() }.distinct().joinToString("، ")
        if (loc.isNotBlank()) {
            append("\nالعنوان: ").append(loc)
        }
        contact.notes?.takeIf { it.isNotBlank() }?.let {
            append("\nملاحظات: ").append(it.take(280))
        }
    }.trim()
}
