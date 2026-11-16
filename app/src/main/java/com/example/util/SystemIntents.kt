package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object SystemIntents {

    fun isWhatsAppInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return isPackageInstalled(pm, "com.whatsapp") || isPackageInstalled(pm, "com.whatsapp.w4b")
    }

    fun isTelegramInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return isPackageInstalled(pm, "org.telegram.messenger") || isPackageInstalled(pm, "org.telegram.plus")
    }

    private fun isPackageInstalled(pm: PackageManager, pkg: String): Boolean {
        return try {
            pm.getPackageInfo(pkg, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun dialPhone(context: Context, phone: String) {
        if (phone.isBlank()) return
        val encoded = Uri.encode(phone)
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$encoded"))
        launchSafe(context, intent, "تعذر فتح تطبيق الاتصال")
    }

    fun openWhatsApp(context: Context, phone: String) {
        val digits = PhoneUtils.toWhatsAppDigits(phone)
        if (digits.isBlank()) {
            Toast.makeText(context, "الرقم غير صالح لفتح واتساب", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = Uri.parse("https://wa.me/$digits")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            if (isPackageInstalled(context.packageManager, "com.whatsapp")) {
                setPackage("com.whatsapp")
            } else if (isPackageInstalled(context.packageManager, "com.whatsapp.w4b")) {
                setPackage("com.whatsapp.w4b")
            }
        }
        launchSafe(context, intent, "تعذر فتح واتساب")
    }

    fun openTelegram(context: Context, phoneOrHandle: String) {
        val clean = phoneOrHandle.trim()
        if (clean.isBlank()) return
        val uri = if (clean.startsWith("@") || clean.any { it.isLetter() }) {
            val user = clean.removePrefix("@").removePrefix("https://t.me/").removePrefix("t.me/")
            Uri.parse("https://t.me/$user")
        } else {
            val norm = PhoneUtils.normalizePhone(clean)
            Uri.parse("https://t.me/$norm")
        }
        launchSafe(context, Intent(Intent.ACTION_VIEW, uri), "تعذر فتح تيليجرام")
    }

    fun sendEmail(context: Context, email: String) {
        if (email.isBlank()) return
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(email)}"))
        launchSafe(context, intent, "لا يوجد تطبيق بريد إلكتروني")
    }

    fun openWebsiteOrPayment(context: Context, rawUrl: String) {
        if (rawUrl.isBlank()) return
        val url = when {
            rawUrl.startsWith("tel:", ignoreCase = true) ||
                rawUrl.startsWith("http://", ignoreCase = true) ||
                rawUrl.startsWith("https://", ignoreCase = true) ||
                rawUrl.startsWith("upi:", ignoreCase = true) -> rawUrl
            else -> "https://$rawUrl"
        }
        val action = if (url.startsWith("tel:", ignoreCase = true)) Intent.ACTION_DIAL else Intent.ACTION_VIEW
        launchSafe(context, Intent(action, Uri.parse(url)), "تعذر فتح الرابط")
    }

    fun openGoogleMaps(context: Context, latitude: Double?, longitude: Double?, label: String?) {
        val uri = if (latitude != null && longitude != null) {
            val q = Uri.encode(label ?: "$latitude,$longitude")
            Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($q)")
        } else if (!label.isNullOrBlank()) {
            Uri.parse("geo:0,0?q=${Uri.encode(label)}")
        } else {
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(context.packageManager) != null) {
            launchSafe(context, intent, "تعذر فتح خرائط جوجل")
        } else {
            val webUrl = if (latitude != null && longitude != null) {
                "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
            } else {
                "https://www.google.com/maps/search/?api=1&query=${Uri.encode(label)}"
            }
            launchSafe(context, Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)), "تعذر فتح الخرائط")
        }
    }

    fun shareExportFile(context: Context, fileName: String, mimeType: String, content: String) {
        runCatching {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, fileName)
            file.writeText(content)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "مشاركة جهات الاتصال"))
        }.onFailure {
            Toast.makeText(context, "تعذر مشاركة الملف: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchSafe(context: Context, intent: Intent, fallbackMsg: String) {
        runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, fallbackMsg, Toast.LENGTH_SHORT).show()
        }
    }
}
