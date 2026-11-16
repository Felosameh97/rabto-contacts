package com.example.util

import java.util.Locale

object PhoneUtils {

    private val whitespaceOrDash = Regex("[\\s\\-().'\"\\u200E\\u200F\\u202A-\\u202E]")

    fun cleanRawInput(raw: String): String {
        return raw.trim()
            .removePrefix("'")
            .replace(whitespaceOrDash, "")
    }

    fun isUssdOrShortCode(phone: String): Boolean {
        val cleaned = cleanRawInput(phone)
        return cleaned.startsWith("*") || cleaned.startsWith("#") || cleaned.length in 1..6
    }

    /**
     * Normalizes Egyptian and international phone numbers to E.164 when possible,
     * while preserving valid USSD / shortcodes cleanly.
     */
    fun normalizePhone(raw: String): String {
        val cleaned = cleanRawInput(raw)
        if (cleaned.isEmpty()) return ""
        if (cleaned.startsWith("*") || cleaned.startsWith("#")) return cleaned
        if (cleaned.length <= 6 && cleaned.all { it.isDigit() }) return cleaned

        var digits = cleaned
        if (digits.startsWith("00")) {
            digits = "+" + digits.drop(2)
        }
        if (digits.startsWith("+")) {
            // Handle +20010... accidental extra 0 after +20
            if (digits.startsWith("+200") && digits.length == 14) {
                return "+20" + digits.drop(4)
            }
            return digits
        }
        // Egyptian mobile numbers: 010, 011, 012, 015 (11 digits)
        if (digits.length == 11 && digits.startsWith("01")) {
            return "+2$digits"
        }
        // Egyptian landline e.g. 02xxxxxxxx (10 digits)
        if (digits.length in 9..10 && digits.startsWith("0")) {
            return "+2$digits"
        }
        // Missing + on 201...
        if (digits.length == 12 && digits.startsWith("201")) {
            return "+$digits"
        }
        return if (digits.all { it.isDigit() } && digits.length >= 7) digits else cleaned
    }

    /**
     * Local Egyptian 11-digit format (e.g. 01012345678) useful for local wallet links / USSD.
     */
    fun toLocalEgyptianPhone(raw: String): String {
        val norm = normalizePhone(raw)
        return when {
            norm.startsWith("+20") && norm.length >= 12 -> "0" + norm.removePrefix("+20")
            norm.startsWith("20") && norm.length == 12 -> "0" + norm.removePrefix("20")
            else -> norm.removePrefix("+")
        }
    }

    /**
     * Pure digits without '+' for https://wa.me/<digits>.
     */
    fun toWhatsAppDigits(raw: String): String {
        val norm = normalizePhone(raw)
        if (isUssdOrShortCode(norm)) return ""
        return norm.filter { it.isDigit() }
    }

    fun phoneSearchKey(raw: String): String {
        val norm = normalizePhone(raw).filter { it.isDigit() }
        return if (norm.length >= 9) norm.takeLast(9) else norm
    }

    fun isSamePhone(a: String, b: String): Boolean {
        val ka = phoneSearchKey(a)
        val kb = phoneSearchKey(b)
        return ka.isNotEmpty() && ka == kb
    }

    fun extractPhonesFromSharedText(sharedText: String): List<String> {
        val regex = Regex("""(\+?\d[\d\s\-().]{6,18}\d)""")
        return regex.findAll(sharedText)
            .map { normalizePhone(it.value) }
            .filter { it.isNotBlank() && it.length >= 7 }
            .distinct()
            .toList()
    }
}

object EgyptGeoLookup {
    data class DistrictPoint(
        val key: String,
        val displayLabel: String,
        val lat: Double,
        val lng: Double,
        val aliases: List<String>,
    )

    val knownDistricts = listOf(
        DistrictPoint("المطرية", "المطرية - القاهرة", 30.1213, 31.3140, listOf("المطرية", "مطريه", "المطريه", "matariya", "matareya", "el matareya")),
        DistrictPoint("المرج", "المرج - القاهرة", 30.1536, 31.3372, listOf("المرج", "مرج", "el marg", "marg")),
        DistrictPoint("الخصوص", "الخصوص - القليوبية", 30.1592, 31.3125, listOf("الخصوص", "خصوص", "khosos")),
        DistrictPoint("عين شمس", "عين شمس - القاهرة", 30.1306, 31.3177, listOf("عين شمس", "ain shams")),
        DistrictPoint("مصر الجديدة", "مصر الجديدة - القاهرة", 30.0914, 31.3249, listOf("مصر الجديدة", "هليوبوليس", "heliopolis", "masr el gedida")),
        DistrictPoint("مدينة نصر", "مدينة نصر - القاهرة", 30.0511, 31.3656, listOf("مدينة نصر", "مدينه نصر", "nasr city")),
        DistrictPoint("العباسية", "العباسية - القاهرة", 30.0725, 31.2837, listOf("العباسية", "عباسيه", "abbassia", "abbasia")),
        DistrictPoint("شبرا", "شبرا - القاهرة", 30.0764, 31.2458, listOf("شبرا", "شبرا الخيمة", "shubra")),
        DistrictPoint("وسط البلد", "وسط البلد - القاهرة", 30.0444, 31.2357, listOf("وسط البلد", "التحرير", "رمسيس", "downtown", "cairo")),
        DistrictPoint("المعادي", "المعادي - القاهرة", 29.9602, 31.2569, listOf("المعادي", "معادي", "maadi")),
        DistrictPoint("حلوان", "حلوان - القاهرة", 29.8414, 31.3342, listOf("حلوان", "helwan")),
        DistrictPoint("التجمع", "القاهرة الجديدة - التجمع", 30.0074, 31.4913, listOf("التجمع", "التجمع الخامس", "القاهرة الجديدة", "new cairo", "tagamoa")),
        DistrictPoint("الزمالك", "الزمالك - القاهرة", 30.0609, 31.2197, listOf("الزمالك", "zamalek")),
        DistrictPoint("الدقي", "الدقي - الجيزة", 30.0382, 31.2119, listOf("الدقي", "دقي", "dokki")),
        DistrictPoint("المهندسين", "المهندسين - الجيزة", 30.0561, 31.2001, listOf("المهندسين", "مهندسين", "mohandessin")),
        DistrictPoint("فيصل والهرم", "فيصل والهرم - الجيزة", 30.0019, 31.1704, listOf("الهرم", "فيصل", "الجيزة", "giza", "haram", "faisal")),
        DistrictPoint("6 أكتوبر", "6 أكتوبر والشيخ زايد", 29.9723, 30.9421, listOf("اكتوبر", "أكتوبر", "6 اكتوبر", "الشيخ زايد", "october", "zayed")),
        DistrictPoint("العاشر من رمضان", "العاشر من رمضان", 30.2950, 31.7417, listOf("العاشر", "العاشر من رمضان", "10th of ramadan", "eaeat")),
        DistrictPoint("العبور", "مدينة العبور", 30.2283, 31.4799, listOf("العبور", "obour")),
        DistrictPoint("بنها", "بنها - القليوبية", 30.4660, 31.1848, listOf("بنها", "banha")),
        DistrictPoint("طنطا", "طنطا - الغربية", 30.7865, 31.0004, listOf("طنطا", "tanta")),
        DistrictPoint("المنصورة", "المنصورة - الدقهلية", 31.0409, 31.3785, listOf("المنصورة", "منصوره", "mansoura")),
        DistrictPoint("الزقازيق", "الزقازيق - الشرقية", 30.5765, 31.5041, listOf("الزقازيق", "زقازيق", "zagazig")),
        DistrictPoint("الإسكندرية", "الإسكندرية", 31.2001, 29.9187, listOf("الاسكندرية", "الإسكندرية", "اسكندريه", "alexandria", "alex")),
        DistrictPoint("أسيوط", "أسيوط", 27.1809, 31.1837, listOf("اسيوط", "أسيوط", "asyut")),
        DistrictPoint("سوهاج", "سوهاج", 26.5569, 31.6948, listOf("سوهاج", "sohag")),
    )

    fun inferLocation(vararg texts: String?): DistrictPoint? {
        val combined = texts.filterNotNull().joinToString(" ").lowercase(Locale.ROOT)
        if (combined.isBlank()) return null
        return knownDistricts.firstOrNull { district ->
            district.aliases.any { alias -> combined.contains(alias.lowercase(Locale.ROOT)) }
        }
    }
}
