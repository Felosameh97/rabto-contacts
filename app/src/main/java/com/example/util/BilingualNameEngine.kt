package com.example.util

import java.util.Locale

object BilingualNameEngine {

    private val arabicRegex = Regex("[\\u0600-\\u06FF]")
    private val latinRegex = Regex("[A-Za-z]")

    fun containsArabic(text: String): Boolean = arabicRegex.containsMatchIn(text)
    fun containsLatin(text: String): Boolean = latinRegex.containsMatchIn(text)

    fun normalizeArabic(raw: String): String {
        return raw.trim()
            .replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ى', 'ي')
            .replace('ة', 'ه')
            .replace('ؤ', 'و')
            .replace('ئ', 'ي')
    }

    private val arToEnWords: Map<String, String> = mapOf(
        // Titles & Professions
        "دكتور" to "Dr.",
        "دكتوره" to "Dr.",
        "د" to "Dr.",
        "مهندس" to "Eng.",
        "مهندسه" to "Eng.",
        "م" to "Eng.",
        "استاذ" to "Mr.",
        "استاذه" to "Ms.",
        "ا" to "Mr.",
        "مستشار" to "Counselor",
        "معيد" to "TA",
        "معيده" to "TA",
        "عميد" to "Dean",
        "كابتن" to "Capt.",
        "شيخ" to "Sheikh",
        "حاج" to "Hajj",
        "عم" to "Uncle",
        "عمو" to "Uncle",
        "خالي" to "Uncle",
        "خالتو" to "Aunt",
        "بابا" to "Dad",
        "ابي" to "Dad",
        "ماما" to "Mom",
        "امي" to "Mom",
        "اخويا" to "Brother",
        "اختي" to "Sister",
        "البيت" to "Home",
        "امين" to "Amin",
        "شرطه" to "Police",
        "ضابط" to "Officer",
        "ظابط" to "Officer",
        "عقيد" to "Col.",
        "مقدم" to "Lt. Col.",
        "رائد" to "Maj.",
        "نقيب" to "Capt.",
        "مباحث" to "Investigation",
        "مرور" to "Traffic",
        "محامي" to "Lawyer",
        "محاميه" to "Lawyer",
        "محاسب" to "Accountant",
        "سباك" to "Plumber",
        "كهربائي" to "Electrician",
        "نجار" to "Carpenter",
        "نقاش" to "Painter",
        "حداد" to "Blacksmith",
        "ميكانيكي" to "Mechanic",
        "تكييف" to "AC Tech",
        "صيانه" to "Maintenance",
        "فني" to "Technician",
        "سمسار" to "Broker",
        "مقاول" to "Contractor",
        "صيدليه" to "Pharmacy",
        "مستشفي" to "Hospital",
        "عياده" to "Clinic",
        "مطعم" to "Restaurant",
        "كافيه" to "Cafe",
        "ماركت" to "Market",
        "سوبر" to "Super",
        "بقاله" to "Grocery",
        "توصيل" to "Delivery",
        "دليفري" to "Delivery",
        "مندوب" to "Courier",
        "شحن" to "Shipping",
        "اوبر" to "Uber",
        "كريم" to "Kareem",
        "شركه" to "Company",
        "مكتب" to "Office",
        "سنتر" to "Center",
        "اكاديميه" to "Academy",
        "جامعه" to "University",
        "كليه" to "Faculty",
        "دفعه" to "Batch",
        "مجموعه" to "Group",
        "طالب" to "Student",
        // Common Egyptian Names
        "احمد" to "Ahmed",
        "محمد" to "Mohamed",
        "محمود" to "Mahmoud",
        "مصطفي" to "Mostafa",
        "علي" to "Ali",
        "عمر" to "Omar",
        "عمرو" to "Amr",
        "خالد" to "Khaled",
        "يوسف" to "Youssef",
        "ابراهيم" to "Ibrahim",
        "حسن" to "Hassan",
        "حسين" to "Hussein",
        "طارق" to "Tarek",
        "شريف" to "Sherif",
        "كريم" to "Kareem",
        "هشام" to "Hesham",
        "تامر" to "Tamer",
        "وليد" to "Walid",
        "وائل" to "Wael",
        "ياسر" to "Yasser",
        "رامي" to "Ramy",
        "علاء" to "Alaa",
        "اشرف" to "Ashraf",
        "يحيي" to "Yehia",
        "زياد" to "Ziad",
        "مازن" to "Mazen",
        "سيف" to "Seif",
        "نور" to "Nour",
        "ايمن" to "Ayman",
        "اسامه" to "Osama",
        "اسلام" to "Islam",
        "هاني" to "Hany",
        "عماد" to "Emad",
        "باسم" to "Bassem",
        "ماجد" to "Maged",
        "مجدي" to "Magdy",
        "سامح" to "Sameh",
        "سمير" to "Samir",
        "عادل" to "Adel",
        "نبيل" to "Nabil",
        "رفعت" to "Refaat",
        "سعيد" to "Saeed",
        "فتحي" to "Fathy",
        "صلاح" to "Salah",
        "ممدوح" to "Mamdouh",
        "منصور" to "Mansour",
        "سليمان" to "Soliman",
        "عثمان" to "Osman",
        "كمال" to "Kamal",
        "جمال" to "Gamal",
        "رمضان" to "Ramadan",
        "شعبان" to "Shaban",
        "رجب" to "Ragab",
        "سالم" to "Salem",
        "ناصر" to "Nasser",
        "منير" to "Mounir",
        "حازم" to "Hazem",
        "توفيق" to "Tawfik",
        "عصام" to "Essam",
        "حمدي" to "Hamdy",
        "فاروق" to "Farouk",
        "زكريا" to "Zakaria",
        "مصطفي" to "Mostafa",
        "عبدالله" to "Abdallah",
        "عبدالرحمن" to "Abdelrahman",
        "عبد" to "Abdel",
        "الله" to "Allah",
        "الرحمن" to "Rahman",
        "العزيز" to "Aziz",
        "الكريم" to "Kareem",
        "السيد" to "El-Sayed",
        "سيد" to "Sayed",
        "فيلوباتير" to "Filopateer",
        "فيلو" to "Filo",
        "مينا" to "Mina",
        "كيرلس" to "Kirollos",
        "بيشوي" to "Bishoy",
        "جرجس" to "Gerges",
        "مرقس" to "Marcus",
        "ابرام" to "Abram",
        "بولا" to "Bola",
        "فادي" to "Fady",
        "شادي" to "Shady",
        "جورج" to "George",
        "مايكل" to "Michael",
        "بيتر" to "Peter",
        "مارينا" to "Marina",
        "مريم" to "Mariam",
        "ساره" to "Sara",
        "ياسمين" to "Yasmine",
        "هدي" to "Hoda",
        "ندي" to "Nada",
        "سلمي" to "Salma",
        "هبه" to "Heba",
        "دينا" to "Dina",
        "امل" to "Amal",
        "ايمان" to "Eman",
        "شيماء" to "Shaimaa",
        "ايه" to "Aya",
        "فاطمه" to "Fatma",
        "زينب" to "Zeinab",
        "حبيبه" to "Habiba",
        "نورهان" to "Nourhan",
        "اسراء" to "Esraa",
        "الاء" to "Alaa",
        "رحمه" to "Rahma",
        "ملك" to "Malak",
        "جني" to "Jana",
        "منه" to "Menna",
        "مي" to "Mai",
        "رنا" to "Rana",
        "ريهام" to "Reham",
        "نهي" to "Noha",
        "اميره" to "Amira",
        "ولاء" to "Walaa",
        "هند" to "Hend",
        "رانيا" to "Rania",
        "دعاء" to "Doaa",
        "عبير" to "Abeer",
        "مروه" to "Marwa",
        // Common Districts / Cities
        "المطريه" to "El-Matariya",
        "مطريه" to "Matariya",
        "المرج" to "El-Marg",
        "الخصوص" to "El-Khosos",
        "عين" to "Ain",
        "شمس" to "Shams",
        "الزيتون" to "El-Zaitoun",
        "القبه" to "El-Kobba",
        "مصر" to "Masr",
        "الجديده" to "El-Gedida",
        "مدينه" to "City",
        "نصر" to "Nasr",
        "السلام" to "El-Salam",
        "العباسيه" to "Abbassia",
        "شبرا" to "Shubra",
        "المعادي" to "Maadi",
        "حلوان" to "Helwan",
        "التجمع" to "Tagamoa",
        "الشروق" to "El-Shorouk",
        "الزمالك" to "Zamalek",
        "الدقي" to "Dokki",
        "المهندسين" to "Mohandessin",
        "الهرم" to "Haram",
        "فيصل" to "Faisal",
        "اكتوبر" to "October",
        "العاشر" to "10th of Ramadan",
        "العبور" to "Obour",
        "ابوحماد" to "Abu Hammad",
        "بلبيس" to "Belbeis",
        "الزقازيق" to "Zagazig",
        "بنها" to "Banha",
        "طنطا" to "Tanta",
        "المنصوره" to "Mansoura",
        "الاسكندريه" to "Alexandria",
        "اسكندريه" to "Alex",
        "القاهره" to "Cairo",
        "الجيزه" to "Giza",
        "السويس" to "Suez",
        "الاسماعيليه" to "Ismailia",
        "بورسعيد" to "Port Said",
        "اسوان" to "Aswan",
        "الاقصر" to "Luxor",
        "اسيوط" to "Asyut",
        "سوهاج" to "Sohag",
    )

    private val enToArWords: Map<String, String> by lazy {
        val m = HashMap<String, String>(arToEnWords.size)
        for ((ar, en) in arToEnWords) {
            val cleanEn = en.lowercase(Locale.ROOT).trimEnd('.')
            m.putIfAbsent(cleanEn, ar)
        }
        m["doctor"] = "دكتور"
        m["dr"] = "د."
        m["engineer"] = "مهندس"
        m["eng"] = "م."
        m["police"] = "شرطة"
        m["officer"] = "ضابط"
        m["plumber"] = "سباك"
        m["electrician"] = "كهربائي"
        m["mechanic"] = "ميكانيكي"
        m["pharmacy"] = "صيدلية"
        m["mohammed"] = "محمد"
        m["muhammad"] = "محمد"
        m["mustafa"] = "مصطفى"
        m["ahmad"] = "أحمد"
        m["karim"] = "كريم"
        m["youssef"] = "يوسف"
        m["yusuf"] = "يوسف"
        m["ibrahim"] = "إبراهيم"
        m["khaled"] = "خالد"
        m["khalid"] = "خالد"
        m["tarek"] = "طارق"
        m["tariq"] = "طارق"
        m["amr"] = "عمرو"
        m["omar"] = "عمر"
        m["abdelrahman"] = "عبدالرحمن"
        m["abdullah"] = "عبدالله"
        m
    }

    /**
     * Translates/transliterates an Arabic or mixed name into clean English Title Case.
     */
    fun toEnglishName(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        if (!containsArabic(trimmed)) return tidyEnglishSpacing(trimmed)

        val rawTokens = trimmed
            .replace("/", " / ")
            .replace(Regex("\\s+"), " ")
            .split(" ")
            .filter { it.isNotBlank() }

        val translated = rawTokens.map { token ->
            if (!containsArabic(token)) {
                token
            } else {
                val strippedPunc = token.trim('.', '-', ':', '،', '؛', '(', ')')
                val norm = normalizeArabic(strippedPunc)
                arToEnWords[norm] ?: transliterateArabicToken(strippedPunc)
            }
        }
        return tidyEnglishSpacing(translated.joinToString(" "))
    }

    /**
     * Converts an English name into a natural Arabic representation for Nickname / Arabic search.
     */
    fun toArabicNickname(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        if (containsArabic(trimmed)) return trimmed

        val rawTokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        val converted = rawTokens.map { token ->
            if (containsArabic(token)) {
                token
            } else {
                val key = token.lowercase(Locale.ROOT).trim('.', '-', ',', '(', ')')
                enToArWords[key] ?: transliterateEnglishToken(key)
            }
        }
        return converted.joinToString(" ").trim()
    }

    /**
     * Given ALL name variants from one or more merged contact records (short names, long names,
     * Arabic names, English names), produces:
     * 1) Primary `displayName` in English, choosing the MOST DETAILED English variant (or converting
     *    the most detailed Arabic variant if no English name existed).
     * 2) `nickname` preserving the Arabic name(s) AND any distinct short/alternate name variants
     *    so both short and long names AND both Arabic and English searches always find the contact!
     */
    fun resolveEnglishAndNickname(variants: List<String>): Pair<String, String?> {
        val cleanedVariants = variants
            .flatMap { it.split("•", "|", ";") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { normalizeArabic(it).lowercase(Locale.ROOT) }

        if (cleanedVariants.isEmpty()) return "" to null

        val nonPhoneVariants = cleanedVariants.filter { v -> v.any { it.isLetter() } }
        if (nonPhoneVariants.isEmpty()) {
            return cleanedVariants.maxByOrNull { it.length }.orEmpty() to null
        }

        val englishVariants = nonPhoneVariants
            .filter { containsLatin(it) && !containsArabic(it) }
            .map { tidyEnglishSpacing(it) }

        val arabicVariants = nonPhoneVariants.filter { containsArabic(it) }

        // Choose most detailed English name; if none exists, convert the longest Arabic variant to English.
        val bestArabic = arabicVariants.maxByOrNull { it.length }
        val primaryEnglish = if (englishVariants.isNotEmpty()) {
            englishVariants.maxByOrNull { scoreDetail(it) }!!
        } else {
            toEnglishName(bestArabic ?: nonPhoneVariants.first())
        }.ifBlank { nonPhoneVariants.maxByOrNull { it.length }.orEmpty() }

        // Ensure Arabic variant is always present in nickname
        val nicknameParts = LinkedHashSet<String>()
        for (ar in arabicVariants.sortedByDescending { it.length }) {
            nicknameParts += ar
        }
        if (arabicVariants.isEmpty() && containsLatin(primaryEnglish)) {
            val autoAr = toArabicNickname(primaryEnglish)
            if (autoAr.isNotBlank()) nicknameParts += autoAr
        }

        // Preserve any short or alternate English variants that differ from primaryEnglish
        val normPrimary = primaryEnglish.lowercase(Locale.ROOT).trim()
        for (ev in englishVariants.sortedByDescending { it.length }) {
            if (ev.lowercase(Locale.ROOT).trim() != normPrimary) {
                nicknameParts += ev
            }
        }
        // Also keep mixed variants
        for (v in nonPhoneVariants) {
            if (containsArabic(v) && containsLatin(v)) {
                nicknameParts += v
            }
        }

        val nickname = nicknameParts.joinToString(" • ").take(240).ifBlank { null }
        return primaryEnglish.take(200) to nickname
    }

    private fun scoreDetail(name: String): Int {
        val wordCount = name.split(Regex("\\s+")).count { it.isNotBlank() }
        return wordCount * 100 + name.length
    }

    private fun tidyEnglishSpacing(raw: String): String {
        return raw.replace(Regex("\\s+"), " ")
            .replace(" .", ".")
            .trim()
            .split(" ")
            .joinToString(" ") { w ->
                when {
                    w.isBlank() -> w
                    w.equals("dr.", true) || w.equals("eng.", true) || w.equals("mr.", true) || w.equals("ms.", true) ->
                        w.replaceFirstChar { it.uppercaseChar() }
                    w.all { it.isUpperCase() } && w.length <= 5 -> w
                    else -> w.replaceFirstChar { it.uppercaseChar() }
                }
            }
    }

    private fun transliterateArabicToken(raw: String): String {
        val s = raw.replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
        if (s.isBlank()) return ""
        val sb = StringBuilder()
        var work = s
        if (work.startsWith("ال") && work.length > 3) {
            sb.append("El-")
            work = work.drop(2)
        }
        var i = 0
        while (i < work.length) {
            val ch = work[i]
            val next = work.getOrNull(i + 1)
            val mapped = when (ch) {
                'أ', 'إ', 'آ', 'ا' -> if (sb.isEmpty() || sb.endsWith("-")) "A" else "a"
                'ب' -> "b"
                'ت' -> "t"
                'ث' -> "th"
                'ج' -> "g"
                'ح' -> "h"
                'خ' -> "kh"
                'د' -> "d"
                'ذ' -> "z"
                'ر' -> "r"
                'ز' -> "z"
                'س' -> "s"
                'ش' -> "sh"
                'ص' -> "s"
                'ض' -> "d"
                'ط' -> "t"
                'ظ' -> "z"
                'ع' -> if (i == 0) "a" else "a"
                'غ' -> "gh"
                'ف' -> "f"
                'ق' -> "k"
                'ك' -> "k"
                'ل' -> "l"
                'م' -> "m"
                'ن' -> "n"
                'ه' -> "h"
                'ة' -> if (i == work.lastIndex) "a" else "t"
                'و', 'ؤ' -> if (i == 0) "w" else "o"
                'ي', 'ى', 'ئ' -> if (i == 0) "y" else if (next == null) "y" else "i"
                else -> ch.toString()
            }
            sb.append(mapped)
            i++
        }
        return sb.toString().replaceFirstChar { it.uppercaseChar() }
    }

    private fun transliterateEnglishToken(token: String): String {
        if (token.isBlank() || token.all { it.isDigit() }) return token
        val s = token.lowercase(Locale.ROOT)
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val two = if (i + 1 < s.length) s.substring(i, i + 2) else ""
            val step = when (two) {
                "sh" -> { sb.append('ش'); 2 }
                "ch" -> { sb.append('ش'); 2 }
                "kh" -> { sb.append('خ'); 2 }
                "gh" -> { sb.append('غ'); 2 }
                "th" -> { sb.append('ث'); 2 }
                "ph" -> { sb.append('ف'); 2 }
                "oo", "ou" -> { sb.append('و'); 2 }
                "ee", "ea", "ie" -> { sb.append('ي'); 2 }
                "aa" -> { sb.append('ا'); 2 }
                else -> {
                    val c = s[i]
                    val chAr = when (c) {
                        'a' -> if (i == 0) 'أ' else 'ا'
                        'b' -> 'ب'
                        'c' -> 'ك'
                        'd' -> 'د'
                        'e' -> if (i == 0) 'إ' else 'ي'
                        'f' -> 'ف'
                        'g' -> 'ج'
                        'h' -> 'ه'
                        'i' -> if (i == 0) 'إ' else 'ي'
                        'j' -> 'ج'
                        'k' -> 'ك'
                        'l' -> 'ل'
                        'm' -> 'م'
                        'n' -> 'ن'
                        'o' -> 'و'
                        'p' -> 'ب'
                        'q' -> 'ق'
                        'r' -> 'ر'
                        's' -> 'س'
                        't' -> 'ت'
                        'u' -> 'و'
                        'v' -> 'ف'
                        'w' -> 'و'
                        'x' -> 'ك'
                        'y' -> 'ي'
                        'z' -> 'ز'
                        else -> c
                    }
                    sb.append(chAr)
                    1
                }
            }
            i += step
        }
        return sb.toString()
    }
}
