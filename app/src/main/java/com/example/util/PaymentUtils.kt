package com.example.util

import com.example.domain.model.PaymentTemplate

object PaymentUtils {

    val defaultTemplates = listOf(
        // Option 1: Direct USSD Dial Codes
        PaymentTemplate(
            id = "tpl_vodafone_ussd",
            name = "فودافون كاش (كود اتصال)",
            urlTemplate = "tel:*9*7*{phone_local}%23",
            isDefault = true,
        ),
        PaymentTemplate(
            id = "tpl_orange_ussd",
            name = "أورانج كاش (كود اتصال)",
            urlTemplate = "tel:*7115*{phone_local}%23",
            isDefault = true,
        ),
        PaymentTemplate(
            id = "tpl_etisalat_ussd",
            name = "اتصالات كاش (كود اتصال)",
            urlTemplate = "tel:*777*{phone_local}%23",
            isDefault = true,
        ),
        PaymentTemplate(
            id = "tpl_wepay_ussd",
            name = "WE Pay (كود اتصال)",
            urlTemplate = "tel:*322%23",
            isDefault = true,
        ),
        // Option 2: Direct App Launchers + Auto-Copied Phone / Deep Links
        PaymentTemplate(
            id = "tpl_orange_app",
            name = "أورانج كاش / Max It (فتح التطبيق)",
            urlTemplate = "app://com.orange.orangecash|com.orange.myorange.egypt?phone={phone_local}",
            isDefault = true,
        ),
        PaymentTemplate(
            id = "tpl_instapay_app",
            name = "InstaPay (فتح التطبيق / رابط)",
            urlTemplate = "https://ipn.eg/S/{phone_local}/instapay",
            isDefault = true,
        ),
        PaymentTemplate(
            id = "tpl_vodafone_app",
            name = "أنا فودافون / كاش (فتح التطبيق)",
            urlTemplate = "app://com.emeint.android.myservices?phone={phone_local}",
            isDefault = true,
        ),
        PaymentTemplate(
            id = "tpl_etisalat_app",
            name = "e& money (فتح التطبيق)",
            urlTemplate = "app://com.etisalat.etisalatcash?phone={phone_local}",
            isDefault = true,
        ),
        PaymentTemplate(
            id = "tpl_axis_pay",
            name = "Axis Pay (رابط مباشر)",
            urlTemplate = "https://axisapp.com/pay/{phone_local}",
            isDefault = true,
        ),
    )

    fun buildPaymentUrl(template: String, rawPhone: String, amount: String? = null): String {
        val localPhone = PhoneUtils.toLocalEgyptianPhone(rawPhone)
        val e164 = PhoneUtils.normalizePhone(rawPhone)
        val digitsOnly = PhoneUtils.toWhatsAppDigits(rawPhone).ifBlank { localPhone }
        val cleanAmount = amount?.trim()?.filter { it.isDigit() || it == '.' }.orEmpty()

        // Special support for Vodafone Cash amount insertion: *9*7*phone*amount#
        if (cleanAmount.isNotBlank() && template.contains("*9*7*{phone_local}")) {
            return "tel:*9*7*$localPhone*$cleanAmount%23"
        }

        return when {
            template.contains("{phone_local}") || template.contains("{phone}") || template.contains("{phone_e164}") -> {
                template
                    .replace("{phone_local}", localPhone)
                    .replace("{phone_e164}", e164)
                    .replace("{phone}", digitsOnly)
            }
            else -> template.trimEnd() + localPhone
        }
    }

    /**
     * Generates an EMVCo / Meeza Digital compatible QR payload string for Egyptian mobile wallets.
     */
    fun buildMeezaQrPayload(name: String, rawPhone: String, amount: String? = null): String {
        val local = PhoneUtils.toLocalEgyptianPhone(rawPhone)
        fun tlv(tag: String, value: String): String {
            val len = value.length.toString().padStart(2, '0')
            return "$tag$len$value"
        }
        val merchantAccount = tlv("00", "eg.meeza.wallet") + tlv("01", local)
        val body = buildString {
            append(tlv("00", "01"))
            append(tlv("01", if (amount.isNullOrBlank()) "11" else "12"))
            append(tlv("26", merchantAccount))
            append(tlv("52", "0000"))
            append(tlv("53", "818")) // EGP
            amount?.trim()?.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                append(tlv("54", it.toString()))
            }
            append(tlv("58", "EG"))
            append(tlv("59", name.ifBlank { local }.take(25)))
            append(tlv("60", "Cairo"))
            append("6304")
        }
        val crc = crc16Ccitt(body)
        return body + crc
    }

    private fun crc16Ccitt(input: String): String {
        var crc = 0xFFFF
        for (b in input.toByteArray(Charsets.UTF_8)) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) ((crc shl 1) xor 0x1021) and 0xFFFF else (crc shl 1) and 0xFFFF
            }
        }
        return crc.toString(16).uppercase().padStart(4, '0')
    }
}
