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
            if (digits.startsWith("+200") && digits.length == 14) {
                return "+20" + digits.drop(4)
            }
            return digits
        }
        // Egyptian mobile numbers: 010, 011, 012, 015 (11 digits)
        if (digits.length == 11 && digits.startsWith("01")) {
            return "+2$digits"
        }
        // 10-digit Egyptian mobile missing leading 0 (e.g. 10xxxxxxxx)
        if (digits.length == 10 && digits.startsWith("1") && digits[1] in charArrayOf('0', '1', '2', '5')) {
            return "+20$digits"
        }
        // Egyptian landline e.g. 02xxxxxxxx (10 digits) or 055xxxxxxx (9-10 digits)
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
        val fromText: Boolean = true,
    )

    val knownDistricts = listOf(
        DistrictPoint("المطرية", "المطرية - القاهرة", 30.1213, 31.3140, listOf("المطرية", "مطريه", "المطريه", "matariya", "matareya", "el matareya")),
        DistrictPoint("المرج", "المرج - القاهرة", 30.1536, 31.3372, listOf("المرج", "مرج", "el marg", "marg")),
        DistrictPoint("الخصوص", "الخصوص - القليوبية", 30.1592, 31.3125, listOf("الخصوص", "خصوص", "khosos")),
        DistrictPoint("عين شمس", "عين شمس - القاهرة", 30.1306, 31.3177, listOf("عين شمس", "ain shams")),
        DistrictPoint("الزيتون", "الزيتون وحلمية الزيتون", 30.1040, 31.3145, listOf("الزيتون", "حلمية الزيتون", "zaitoun", "zeitoun")),
        DistrictPoint("حدائق القبة", "حدائق القبة - القاهرة", 30.0882, 31.2901, listOf("حدائق القبة", "القبة", "kobba", "hadayek")),
        DistrictPoint("مصر الجديدة", "مصر الجديدة - القاهرة", 30.0914, 31.3249, listOf("مصر الجديدة", "هليوبوليس", "النزهة", "شيراتون", "heliopolis", "masr el gedida", "sheraton")),
        DistrictPoint("مدينة نصر", "مدينة نصر - القاهرة", 30.0511, 31.3656, listOf("مدينة نصر", "مدينه نصر", "مكرم عبيد", "عباس العقاد", "الحي العاشر", "nasr city")),
        DistrictPoint("مدينة السلام", "مدينة السلام وجسر السويس", 30.1636, 31.4048, listOf("السلام", "مدينة السلام", "جسر السويس", "النهضة", "el salam")),
        DistrictPoint("العباسية", "العباسية - القاهرة", 30.0725, 31.2837, listOf("العباسية", "عباسيه", "الوايلي", "abbassia", "abbasia")),
        DistrictPoint("شبرا", "شبرا - القاهرة", 30.0764, 31.2458, listOf("شبرا", "روض الفرج", "مسرة", "شبرا مصر", "shubra")),
        DistrictPoint("شبرا الخيمة", "شبرا الخيمة - القليوبية", 30.1286, 31.2422, listOf("شبرا الخيمة", "بهتيم", "مسطرد", "بيجام", "shubra el kheima")),
        DistrictPoint("وسط البلد", "وسط البلد - القاهرة", 30.0444, 31.2357, listOf("وسط البلد", "التحرير", "رمسيس", "السيدة زينب", "العتبة", "عابدين", "المنيل", "downtown", "cairo")),
        DistrictPoint("المعادي", "المعادي - القاهرة", 29.9602, 31.2569, listOf("المعادي", "معادي", "زهراء المعادي", "دار السلام", "maadi")),
        DistrictPoint("حلوان", "حلوان - القاهرة", 29.8414, 31.3342, listOf("حلوان", "15 مايو", "التبين", "المعصرة", "helwan")),
        DistrictPoint("التجمع", "القاهرة الجديدة - التجمع", 30.0074, 31.4913, listOf("التجمع", "التجمع الخامس", "الرحاب", "مدينتي", "القاهرة الجديدة", "new cairo", "tagamoa", "rehab", "madinaty")),
        DistrictPoint("الشروق وبدر", "الشروق وبدر", 30.1239, 31.6305, listOf("الشروق", "مدينة بدر", "بدر", "shorouk", "badr city")),
        DistrictPoint("الزمالك", "الزمالك - القاهرة", 30.0609, 31.2197, listOf("الزمالك", "zamalek")),
        DistrictPoint("الدقي", "الدقي - الجيزة", 30.0382, 31.2119, listOf("الدقي", "دقي", "العجوزة", "dokki")),
        DistrictPoint("المهندسين", "المهندسين - الجيزة", 30.0561, 31.2001, listOf("المهندسين", "مهندسين", "امبابة", "إمبابة", "الوراق", "mohandessin", "imbaba")),
        DistrictPoint("فيصل والهرم", "فيصل والهرم - الجيزة", 30.0019, 31.1704, listOf("الهرم", "فيصل", "حدائق الأهرام", "الجيزة", "بولاق الدكرور", "giza", "haram", "faisal")),
        DistrictPoint("6 أكتوبر", "6 أكتوبر والشيخ زايد", 29.9723, 30.9421, listOf("اكتوبر", "أكتوبر", "6 اكتوبر", "الشيخ زايد", "الحصري", "october", "zayed")),
        DistrictPoint("العاشر من رمضان", "العاشر من رمضان", 30.2950, 31.7417, listOf("العاشر", "العاشر من رمضان", "10th of ramadan", "eaeat", "eae&at")),
        DistrictPoint("العبور", "مدينة العبور", 30.2283, 31.4799, listOf("العبور", "obour")),
        DistrictPoint("أبوحماد", "أبوحماد - الشرقية", 30.5315, 31.6710, listOf("أبوحماد", "ابوحماد", "ابو حماد", "أبو حماد", "abu hammad")),
        DistrictPoint("بلبيس", "بلبيس - الشرقية", 30.4204, 31.5622, listOf("بلبيس", "belbeis")),
        DistrictPoint("الزقازيق", "الزقازيق - الشرقية", 30.5765, 31.5041, listOf("الزقازيق", "زقازيق", "الشرقية", "zagazig")),
        DistrictPoint("بنها", "بنها - القليوبية", 30.4660, 31.1848, listOf("بنها", "قليوب", "طوخ", "القناطر", "banha")),
        DistrictPoint("طنطا", "طنطا - الغربية", 30.7865, 31.0004, listOf("طنطا", "المحلة", "الغربية", "tanta", "mahalla")),
        DistrictPoint("المنصورة", "المنصورة - الدقهلية", 31.0409, 31.3785, listOf("المنصورة", "منصوره", "الدقهلية", "طلخا", "mansoura")),
        DistrictPoint("دمنهور", "دمنهور - البحيرة", 31.0379, 30.4726, listOf("دمنهور", "البحيرة", "كفر الدوار", "damanhur")),
        DistrictPoint("كفر الشيخ", "كفر الشيخ", 31.1107, 30.9388, listOf("كفر الشيخ", "دسوق", "kafr el sheikh")),
        DistrictPoint("المنوفية", "شبين الكوم - المنوفية", 30.5503, 31.0106, listOf("المنوفية", "شبين الكوم", "منوف", "أشمون", "السادات", "shebin")),
        DistrictPoint("دمياط", "دمياط", 31.4175, 31.8144, listOf("دمياط", "راس البر", "دمياط الجديدة", "damietta")),
        DistrictPoint("بورسعيد", "بورسعيد", 31.2653, 32.3019, listOf("بورسعيد", "بورفؤاد", "port said")),
        DistrictPoint("الإسماعيلية", "الإسماعيلية", 30.5965, 32.2715, listOf("الاسماعيلية", "الإسماعيلية", "اسماعيليه", "ismailia")),
        DistrictPoint("السويس", "السويس والعين السخنة", 29.9668, 32.5498, listOf("السويس", "العين السخنة", "suez")),
        DistrictPoint("الإسكندرية", "الإسكندرية", 31.2001, 29.9187, listOf("الاسكندرية", "الإسكندرية", "اسكندريه", "سموحة", "سيدي جابر", "ميامي", "العجمي", "alexandria", "alex")),
        DistrictPoint("الفيوم", "الفيوم", 29.3084, 30.8428, listOf("الفيوم", "fayoum")),
        DistrictPoint("بني سويف", "بني سويف", 29.0661, 31.0994, listOf("بني سويف", "beni suef")),
        DistrictPoint("المنيا", "المنيا", 28.1099, 30.7503, listOf("المنيا", "minya")),
        DistrictPoint("أسيوط", "أسيوط", 27.1809, 31.1837, listOf("اسيوط", "أسيوط", "asyut")),
        DistrictPoint("سوهاج", "سوهاج", 26.5569, 31.6948, listOf("سوهاج", "sohag")),
        DistrictPoint("قنا", "قنا", 26.1551, 32.7160, listOf("قنا", "نجع حمادي", "qena")),
        DistrictPoint("الأقصر", "الأقصر", 25.6872, 32.6396, listOf("الأقصر", "الاقصر", "luxor")),
        DistrictPoint("أسوان", "أسوان", 24.0889, 32.8998, listOf("أسوان", "اسوان", "aswan")),
        DistrictPoint("البحر الأحمر وسيناء", "الغردقة وشرم الشيخ", 27.2579, 33.8116, listOf("الغردقة", "شرم الشيخ", "دهب", "الجونة", "مطروح", "مرسى مطروح", "hurghada", "sharm")),
    )

    fun findExactOrAlias(raw: String): DistrictPoint? {
        val cleaned = raw.trim().lowercase(Locale.ROOT)
        if (cleaned.isBlank()) return null
        return knownDistricts.firstOrNull { d ->
            d.key.lowercase(Locale.ROOT) == cleaned || d.aliases.any { it.lowercase(Locale.ROOT) == cleaned }
        }
    }

    fun inferLocation(vararg texts: String?): DistrictPoint? {
        val combined = texts.filterNotNull().joinToString(" ").lowercase(Locale.ROOT)
        if (combined.isBlank()) return null
        return knownDistricts.firstOrNull { district ->
            district.aliases.any { alias -> combined.contains(alias.lowercase(Locale.ROOT)) }
        }
    }

    /**
     * Infers city/governorate from Egyptian landline area code or international country dialing code
     * when no explicit text address was provided.
     */
    fun inferLocationFromPhone(phone: String): DistrictPoint? {
        val norm = PhoneUtils.normalizePhone(phone)
        return when {
            norm.startsWith("+202") -> DistrictPoint("القاهرة الكبرى (أرضي 02)", "القاهرة الكبرى", 30.0444, 31.2357, emptyList(), fromText = false)
            norm.startsWith("+203") -> DistrictPoint("الإسكندرية (أرضي 03)", "الإسكندرية", 31.2001, 29.9187, emptyList(), fromText = false)
            norm.startsWith("+2055") -> DistrictPoint("الشرقية (أرضي 055)", "الزقازيق والشرقية", 30.5765, 31.5041, emptyList(), fromText = false)
            norm.startsWith("+2050") -> DistrictPoint("الدقهلية (أرضي 050)", "المنصورة", 31.0409, 31.3785, emptyList(), fromText = false)
            norm.startsWith("+2040") -> DistrictPoint("الغربية (أرضي 040)", "طنطا", 30.7865, 31.0004, emptyList(), fromText = false)
            norm.startsWith("+2013") -> DistrictPoint("القليوبية (أرضي 013)", "بنها والقليوبية", 30.4660, 31.1848, emptyList(), fromText = false)
            norm.startsWith("+2062") -> DistrictPoint("السويس (أرضي 062)", "السويس", 29.9668, 32.5498, emptyList(), fromText = false)
            norm.startsWith("+2064") -> DistrictPoint("الإسماعيلية (أرضي 064)", "الإسماعيلية", 30.5965, 32.2715, emptyList(), fromText = false)
            norm.startsWith("+2066") -> DistrictPoint("بورسعيد (أرضي 066)", "بورسعيد", 31.2653, 32.3019, emptyList(), fromText = false)
            norm.startsWith("+966") -> DistrictPoint("السعودية (+966)", "السعودية", 24.7136, 46.6753, emptyList(), fromText = false)
            norm.startsWith("+971") -> DistrictPoint("الإمارات (+971)", "دبي والإمارات", 25.2048, 55.2708, emptyList(), fromText = false)
            norm.startsWith("+965") -> DistrictPoint("الكويت (+965)", "الكويت", 29.3759, 47.9774, emptyList(), fromText = false)
            norm.startsWith("+974") -> DistrictPoint("قطر (+974)", "الدوحة", 25.2854, 51.5310, emptyList(), fromText = false)
            norm.startsWith("+1") && norm.length >= 11 -> DistrictPoint("أمريكا وكندا (+1)", "الولايات المتحدة", 40.7128, -74.0060, emptyList(), fromText = false)
            else -> null
        }
    }

    /**
     * Infers carrier or governorate/country badge for unknown callers & call log rows.
     */
    fun inferCallerInsight(phone: String): String? {
        if (phone.isBlank() || PhoneUtils.isUssdOrShortCode(phone)) return null
        inferLocationFromPhone(phone)?.let { return it.key }
        val local = PhoneUtils.toLocalEgyptianPhone(phone)
        return when {
            local.startsWith("010") && local.length == 11 -> "فودافون مصر"
            local.startsWith("011") && local.length == 11 -> "إي آند (اتصالات)"
            local.startsWith("012") && local.length == 11 -> "أورنج مصر"
            local.startsWith("015") && local.length == 11 -> "وي (WE)"
            else -> null
        }
    }
}
