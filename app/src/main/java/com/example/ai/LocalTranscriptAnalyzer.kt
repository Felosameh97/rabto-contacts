package com.example.ai

import com.example.domain.model.ContactRecord
import com.example.util.PhoneUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class LocalTranscriptAnalysis(
    val transcript: String,
    val highlights: List<String>,
    val extractedNotes: String,
    val eventTitle: String,
    val eventDateIso: String,
    val eventEpochMs: Long?,
)

/**
 * Zero-token on-device analyzer for Arabic/English transcripts and assistant commands.
 * Extracts commitments, amounts, phones, calendar events, and notes without calling Gemini.
 */
object LocalTranscriptAnalyzer {

    private val importantKeywords = listOf(
        "بكرة", "بكره", "غدا", "غداً", "النهاردة", "النهارده", "بعد بكرة",
        "الساعة", "موعد", "ميعاد", "اجتماع", "مقابلة", "تذكير", "متنساش",
        "جنيه", "الف", "ألف", "مليون", "تحويل", "فودافون كاش", "انستاباي", "حساب",
        "عنوان", "شارع", "مكتب", "مستشفى", "كلية", "اتفقنا", "لازم", "هكلمك", "هبعتلك",
        "tomorrow", "today", "meeting", "appointment", "remind", "payment", "egp", "usd",
    )

    private val dayMap = mapOf(
        "الاحد" to Calendar.SUNDAY, "الأحد" to Calendar.SUNDAY, "حد" to Calendar.SUNDAY,
        "الاثنين" to Calendar.MONDAY, "الإثنين" to Calendar.MONDAY, "اتنين" to Calendar.MONDAY,
        "الثلاثاء" to Calendar.TUESDAY, "التلات" to Calendar.TUESDAY,
        "الاربعاء" to Calendar.WEDNESDAY, "الأربعاء" to Calendar.WEDNESDAY, "اربع" to Calendar.WEDNESDAY,
        "الخميس" to Calendar.THURSDAY, "خميس" to Calendar.THURSDAY,
        "الجمعة" to Calendar.FRIDAY, "جمعة" to Calendar.FRIDAY,
        "السبت" to Calendar.SATURDAY, "سبت" to Calendar.SATURDAY,
    )

    fun analyze(
        rawTranscript: String,
        contactName: String,
        phone: String,
        referenceTimeMs: Long = System.currentTimeMillis(),
    ): LocalTranscriptAnalysis {
        val clean = rawTranscript.trim()
        if (clean.isBlank()) {
            return LocalTranscriptAnalysis("", emptyList(), "", "", "", null)
        }

        val sentences = clean
            .split(Regex("[.\\n،؛!?]+"))
            .map { it.trim() }
            .filter { it.length >= 4 }

        val numberOrAmountRegex = Regex("""\b\d{2,}\b""")
        val highlights = sentences
            .filter { s ->
                val low = s.lowercase(Locale.getDefault())
                importantKeywords.any { low.contains(it) } || numberOrAmountRegex.containsMatchIn(s)
            }
            .distinct()
            .take(5)
            .ifEmpty { sentences.take(2) }

        val eventInfo = extractCalendarEvent(clean, contactName, sentences, referenceTimeMs)
        val notes = buildString {
            if (highlights.isNotEmpty()) {
                append(highlights.joinToString(" • "))
            } else {
                append(clean.take(220))
            }
        }

        return LocalTranscriptAnalysis(
            transcript = clean,
            highlights = highlights,
            extractedNotes = notes,
            eventTitle = eventInfo?.first.orEmpty(),
            eventDateIso = eventInfo?.second.orEmpty(),
            eventEpochMs = eventInfo?.third,
        )
    }

    private fun extractCalendarEvent(
        text: String,
        contactName: String,
        sentences: List<String>,
        referenceTimeMs: Long,
    ): Triple<String, String, Long>? {
        val low = text.lowercase(Locale.getDefault())
        val hasTimeTrigger = low.contains("بكرة") || low.contains("بكره") ||
            low.contains("غدا") || low.contains("غداً") ||
            low.contains("بعد بكرة") || low.contains("النهاردة") || low.contains("النهارده") ||
            low.contains("موعد") || low.contains("ميعاد") || low.contains("اجتماع") ||
            low.contains("متنساش") || low.contains("tomorrow") || low.contains("meeting") ||
            dayMap.keys.any { low.contains(it) }

        if (!hasTimeTrigger) return null

        val cal = Calendar.getInstance().apply { timeInMillis = referenceTimeMs }
        when {
            low.contains("بعد بكرة") || low.contains("بعد غد") -> cal.add(Calendar.DAY_OF_YEAR, 2)
            low.contains("بكرة") || low.contains("بكره") || low.contains("غدا") || low.contains("غداً") || low.contains("tomorrow") ->
                cal.add(Calendar.DAY_OF_YEAR, 1)
            low.contains("النهاردة") || low.contains("النهارده") || low.contains("today") -> Unit
            else -> {
                val targetDow = dayMap.entries.firstOrNull { low.contains(it.key) }?.value
                if (targetDow != null) {
                    var daysAhead = (targetDow - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
                    if (daysAhead == 0) daysAhead = 7
                    cal.add(Calendar.DAY_OF_YEAR, daysAhead)
                } else {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }

        val hourMatch = Regex("""(?:الساعة|الساعه|at)\s*(\d{1,2})(?::(\d{2}))?""").find(low)
        var hour = hourMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 10
        val minute = hourMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
        if ((low.contains("مساء") || low.contains("بليل") || low.contains("العصر") || low.contains("pm")) && hour in 1..11) {
            hour += 12
        } else if (hour in 1..6 && !low.contains("صباح") && !low.contains("الفجر") && !low.contains("am")) {
            hour += 12
        }
        cal.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
        cal.set(Calendar.MINUTE, minute.coerceIn(0, 59))
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val triggerSentence = sentences.firstOrNull { s ->
            val sl = s.lowercase(Locale.getDefault())
            importantKeywords.any { sl.contains(it) }
        }?.take(60)
        val title = triggerSentence?.ifBlank { null }
            ?: "متابعة مع ${contactName.ifBlank { "عميل" }}"
        val iso = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(cal.timeInMillis))
        return Triple(title, iso, cal.timeInMillis)
    }

    /**
     * Parses common Arabic/English voice or typed commands locally without consuming Gemini quota.
     * Returns null only for open-ended reasoning that benefits from Gemini.
     */
    fun tryParseCommandLocally(
        userText: String,
        availableTopCategories: List<String>,
    ): AiCommandPlan? {
        val clean = userText.trim()
        if (clean.isBlank()) return null
        val low = clean.lowercase(Locale.getDefault())

        // Direct phone number in prompt
        val phoneMatch = Regex("""(?:\+?2?0?1[0125]\d{8}|\b0\d{8,10}\b)""").find(clean)?.value
        if (phoneMatch != null && (low.contains("سجل") || low.contains("احفظ") || low.contains("ضيف") || low.contains("اضف"))) {
            val withoutPhone = clean.replace(phoneMatch, "").replace(Regex("""^(سجل|احفظ|ضيف|اضف|رقم)\s*"""), "").trim()
            return AiCommandPlan(
                action = AiCommandAction.ADD_CONTACT,
                newContactName = withoutPhone.ifBlank { phoneMatch },
                newContactPhone = PhoneUtils.normalizePhone(phoneMatch),
                replyAr = "تم تجهيز حفظ الرقم محلياً بدون استهلاك رصيد Gemini.",
            )
        }

        if (low.contains("صنف") || low.contains("نظم") || low.contains("تقسيم") || low.contains("غير المصنف")) {
            return AiCommandPlan(
                action = AiCommandAction.AUTO_CATEGORIZE,
                replyAr = "جاري تصنيف الجهات غير المصنفة بالمحرك المحلي الذكي.",
            )
        }

        if (low.startsWith("اتصل") || low.startsWith("رن") || low.startsWith("كلم") || low.startsWith("call ")) {
            val q = clean.replaceFirst(Regex("""^(اتصل\s*بـ?|اتصل\s*على|رن\s*على|كلم|call)\s*""", RegexOption.IGNORE_CASE), "").trim()
            if (q.isNotBlank()) {
                return AiCommandPlan(
                    action = AiCommandAction.CALL,
                    queryNameOrPhone = phoneMatch ?: q,
                    replyAr = "جاري البحث للاتصال بـ $q",
                )
            }
        }

        if (low.contains("واتساب") || low.contains("واتس") || low.contains("ابعت رسالة") || low.contains("ابعت رساله") || low.contains("whatsapp")) {
            val parts = clean
                .replace(Regex("""^(ابعت|ارسل|بعت)?\s*(رسالة|رساله)?\s*(على|ع)?\s*(ال)?(واتساب|واتس|whatsapp)\s*(لـ?|الى|إلى|ل)?\s*""", RegexOption.IGNORE_CASE), "")
                .trim()
            if (parts.isNotBlank()) {
                return AiCommandPlan(
                    action = AiCommandAction.WHATSAPP,
                    queryNameOrPhone = phoneMatch ?: parts,
                    replyAr = "جاري البحث عن $parts لفتح واتساب",
                )
            }
        }

        if (low.contains("حول") || low.contains("دفع") || low.contains("فودافون كاش") || low.contains("انستاباي") || low.contains("اورانج كاش")) {
            val amount = Regex("""\b(\d+(?:\.\d+)?)\b""").find(clean)?.groupValues?.getOrNull(1).orEmpty()
            val q = clean.replace(Regex("""\b\d+(?:\.\d+)?\b"""), "")
                .replace(Regex("""(حول|ابعت|دفع|فلوس|جنيه|فودافون كاش|انستاباي|اورانج كاش|لـ|ل|إلى)"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
            return AiCommandPlan(
                action = AiCommandAction.PAYMENT,
                queryNameOrPhone = phoneMatch ?: q,
                amount = amount,
                replyAr = "تم تجهيز التحويل المالي من النتائج المطابقة",
            )
        }

        if (low.startsWith("افتح قسم") || low.startsWith("افتح فئة") || low.startsWith("قسم ") || low.startsWith("فئة ")) {
            val target = clean.replaceFirst(Regex("""^(افتح\s*)?(قسم|فئة|ليبل)\s*"""), "").trim()
            if (target.isNotBlank()) {
                return AiCommandPlan(
                    action = AiCommandAction.OPEN_CATEGORY,
                    categoryPath = target,
                    queryNameOrPhone = target,
                    replyAr = "جاري فتح فئة $target",
                )
            }
        }

        val matchingTop = availableTopCategories.firstOrNull { cat -> low == cat.lowercase(Locale.getDefault()) }
        if (matchingTop != null) {
            return AiCommandPlan(
                action = AiCommandAction.OPEN_CATEGORY,
                categoryPath = matchingTop,
                queryNameOrPhone = matchingTop,
                replyAr = "تم فتح قسم $matchingTop",
            )
        }

        if (low.startsWith("دور") || low.startsWith("ابحث") || low.startsWith("هات") || low.startsWith("فين") || low.startsWith("search ")) {
            val q = clean.replaceFirst(Regex("""^(دور\s*لي\s*على|دورلي\s*على|دور\s*على|ابحث\s*عن|هات\s*ارقام|هات\s*أرقام|هات|فين|search)\s*""", RegexOption.IGNORE_CASE), "").trim()
            if (q.isNotBlank()) {
                return AiCommandPlan(
                    action = AiCommandAction.SEARCH,
                    queryNameOrPhone = q,
                    replyAr = "نتائج البحث الفوري لـ $q",
                )
            }
        }

        // Default fast search for short 1-3 word queries
        val words = clean.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.size in 1..3 && !clean.contains("?") && !clean.contains("؟")) {
            return AiCommandPlan(
                action = AiCommandAction.SEARCH,
                queryNameOrPhone = clean,
                replyAr = "نتائج البحث المحلي عن $clean",
            )
        }

        return null
    }
}
