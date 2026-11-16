package com.example.util

import com.example.domain.model.PaymentTemplate

object PaymentUtils {

    val defaultTemplates = listOf(
        PaymentTemplate(
            id = "tpl_instapay",
            name = "InstaPay",
            urlTemplate = "https://ipn.eg/S/{phone_local}/instapay",
            isDefault = true,
        ),
        PaymentTemplate(
            id = "tpl_orange_cash",
            name = "Orange Cash",
            urlTemplate = "tel:*7115*{phone_local}%23",
            isDefault = true,
        ),
        PaymentTemplate(
            id = "tpl_vodafone_cash",
            name = "Vodafone Cash",
            urlTemplate = "tel:*9*7*{phone_local}%23",
            isDefault = true,
        ),
        PaymentTemplate(
            id = "tpl_etisalat_cash",
            name = "Etisalat Cash",
            urlTemplate = "tel:*777*{phone_local}%23",
            isDefault = true,
        ),
        PaymentTemplate(
            id = "tpl_axis_pay",
            name = "Axis / Access Pay",
            urlTemplate = "https://axisapp.com/pay/{phone_local}",
            isDefault = true,
        ),
    )

    /**
     * Concatenates or interpolates the contact phone number with the configured payment service template.
     * Supports {phone}, {phone_local}, {phone_e164}, or direct suffix concatenation if no placeholder is given.
     */
    fun buildPaymentUrl(template: String, rawPhone: String): String {
        val localPhone = PhoneUtils.toLocalEgyptianPhone(rawPhone)
        val e164 = PhoneUtils.normalizePhone(rawPhone)
        val digitsOnly = PhoneUtils.toWhatsAppDigits(rawPhone).ifBlank { localPhone }
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
}
