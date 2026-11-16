package com.example.util

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.core.content.ContextCompat
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

    /**
     * Places a call directly via ACTION_CALL if CALL_PHONE is granted, otherwise opens dialer.
     * Supports selecting SIM 1 / SIM 2 via optional PhoneAccountHandle.
     */
    fun dialPhone(context: Context, phone: String, accountHandle: PhoneAccountHandle? = null) {
        if (phone.isBlank()) return
        val encoded = Uri.encode(phone)
        val hasCallPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val action = if (hasCallPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse("tel:$encoded")).apply {
            if (accountHandle != null && action == Intent.ACTION_CALL) {
                putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle)
            }
        }
        launchSafe(context, intent, "تعذر بدء المكالمة")
    }

    fun dialPhoneWithSimSlot(context: Context, phone: String, simSlotIndex: Int?) {
        if (phone.isBlank()) return
        val handle = if (simSlotIndex != null) {
            SimUtils.getAvailableSims(context).firstOrNull { it.slotIndex == simSlotIndex }?.accountHandle
        } else null
        dialPhone(context, phone, handle)
    }

    fun insertCalendarEvent(
        context: Context,
        title: String,
        description: String,
        startEpochMs: Long? = null,
    ) {
        if (title.isBlank()) return
        val start = startEpochMs ?: (System.currentTimeMillis() + 24L * 60L * 60L * 1000L)
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 60L * 60L * 1000L)
        }
        launchSafe(context, intent, "لا يوجد تطبيق تقويم متاح")
    }

    fun openWhatsApp(context: Context, phone: String, prefillMessage: String? = null) {
        val digits = PhoneUtils.toWhatsAppDigits(phone)
        if (digits.isBlank()) {
            Toast.makeText(context, "الرقم غير صالح لفتح واتساب", Toast.LENGTH_SHORT).show()
            return
        }
        val url = if (!prefillMessage.isNullOrBlank()) {
            "https://wa.me/$digits?text=${Uri.encode(prefillMessage)}"
        } else {
            "https://wa.me/$digits"
        }
        val uri = Uri.parse(url)
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

    fun openWebsiteOrPayment(context: Context, rawUrl: String, phoneToCopy: String? = null) {
        if (rawUrl.isBlank()) return
        if (rawUrl.startsWith("app://", ignoreCase = true)) {
            openDirectWalletApp(context, rawUrl, phoneToCopy)
            return
        }
        if (!phoneToCopy.isNullOrBlank() && !rawUrl.startsWith("tel:", ignoreCase = true)) {
            copyPhoneWithNotice(context, PhoneUtils.toLocalEgyptianPhone(phoneToCopy))
        }
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

    private fun openDirectWalletApp(context: Context, appUri: String, fallbackPhone: String?) {
        val withoutScheme = appUri.removePrefix("app://")
        val pkgsPart = withoutScheme.substringBefore("?")
        val phoneParam = Regex("""phone=([^&]+)""").find(withoutScheme)?.groupValues?.getOrNull(1)
            ?: fallbackPhone?.let { PhoneUtils.toLocalEgyptianPhone(it) }

        if (!phoneParam.isNullOrBlank()) {
            copyPhoneWithNotice(context, phoneParam)
        }

        val packages = pkgsPart.split("|").map { it.trim() }.filter { it.isNotBlank() }
        val pm = context.packageManager
        for (pkg in packages) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchSafe(context, launchIntent, "تعذر فتح التطبيق")
                return
            }
        }
        // Not installed -> open Google Play listing for primary package
        val primaryPkg = packages.firstOrNull() ?: return
        val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$primaryPkg"))
        launchSafe(context, storeIntent, "التطبيق غير مثبت على الجهاز")
    }

    private fun copyPhoneWithNotice(context: Context, phone: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Payment Number", phone))
        Toast.makeText(context, "تم نسخ الرقم $phone للصق داخل المحفظة", Toast.LENGTH_SHORT).show()
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
            context.startActivity(Intent.createChooser(shareIntent, "مشاركة"))
        }.onFailure {
            Toast.makeText(context, "تعذر مشاركة الملف: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareBinaryFile(context: Context, file: File, mimeType: String) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "مشاركة التسجيل"))
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
