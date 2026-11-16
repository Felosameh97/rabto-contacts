package com.example.util

import java.util.Locale

object SmartCategoryEngine {

    /**
     * Normalizes raw labels (merging duplicates like "2023" and "Eae&at 2023") into a clean,
     * multi-level nested hierarchy using " > " separators, and detects favorite ("* starred").
     */
    fun normalizeAndNestLabels(
        rawLabels: List<String>,
        displayName: String,
        phones: List<String>,
        emails: List<String>,
        organization: String?,
        jobTitle: String?,
        notes: String?,
        address: String?,
    ): Pair<List<String>, Boolean> {
        var isStarred = false
        val result = LinkedHashSet<String>()

        for (raw in rawLabels) {
            val cleaned = raw.trim()
            if (cleaned.isBlank()) continue
            val lower = cleaned.lowercase(Locale.ROOT)

            if (lower == "* mycontacts" || lower == "mycontacts" || lower.startsWith("imported")) {
                continue
            }
            if (lower == "* starred" || lower == "starred") {
                isStarred = true
                result += "الدائرة الخاصة > مميزون بنجمة"
                continue
            }

            // Already explicitly nested with ">" or "/"
            if (cleaned.contains(">") || cleaned.contains(" / ") || cleaned.contains("::")) {
                val normalized = cleaned
                    .replace(" / ", " > ")
                    .replace("::", " > ")
                    .split(">")
                    .map { canonicalSegment(it) }
                    .filter { it.isNotBlank() }
                    .joinToString(" > ")
                if (normalized.isNotBlank()) result += normalized
                continue
            }

            mapSingleRawLabel(cleaned)?.let { result += it }
        }

        // Auto-infer additional meaningful categories from contact content when labels are missing/sparse
        val inferred = inferCategoriesFromContent(
            displayName = displayName,
            phones = phones,
            emails = emails,
            organization = organization,
            jobTitle = jobTitle,
            notes = notes,
            address = address,
            hasExplicitNonGroupCategory = result.any { !it.startsWith("تصنيفات أخرى") },
        )
        result.addAll(inferred)

        // Guarantee every contact lives inside a clean, browsable nested folder
        if (result.isEmpty()) {
            result += fallbackBucket(phones, displayName)
        }

        return result.toList().take(12) to isStarred
    }

    private fun canonicalSegment(segment: String): String {
        val s = segment.trim()
        return when (s.lowercase(Locale.ROOT)) {
            "2021", "eae&at 2021", "eaeat 2021" -> "دفعة 2021"
            "2022", "eae&at 2022", "eaeat 2022" -> "دفعة 2022"
            "2023", "eae&at 2023", "eaeat 2023" -> "دفعة 2023"
            "2024", "eae&at 2024", "eaeat 2024" -> "دفعة 2024"
            "2025", "eae&at 2025", "eaeat 2025" -> "دفعة 2025"
            else -> s
        }
    }

    private fun mapSingleRawLabel(raw: String): String? {
        val l = raw.lowercase(Locale.ROOT).trim()
        return when {
            // Merge 2021..2025 with Eae&at 2021..2025
            l in setOf("2021", "eae&at 2021", "eaeat 2021") -> "الجامعة > الأكاديمية (EAEAT) > دفعة 2021"
            l in setOf("2022", "eae&at 2022", "eaeat 2022") -> "الجامعة > الأكاديمية (EAEAT) > دفعة 2022"
            l in setOf("2023", "eae&at 2023", "eaeat 2023", "2023 leader") -> "الجامعة > الأكاديمية (EAEAT) > دفعة 2023"
            l in setOf("2024", "eae&at 2024", "eaeat 2024") -> "الجامعة > الأكاديمية (EAEAT) > دفعة 2024"
            l in setOf("2025", "eae&at 2025", "eaeat 2025") -> "الجامعة > الأكاديمية (EAEAT) > دفعة 2025"
            l in setOf("eaeat", "eae&at") -> "الجامعة > الأكاديمية (EAEAT)"
            l in setOf("العام", "عام", "fy1") -> "الجامعة > الأكاديمية (EAEAT) > إعدادي / عام"

            // Engineering departments
            l.contains("الكهربائية") || l.contains("اتصالات") || l == "electrical" ->
                "الجامعة > الأقسام الهندسية > هندسة كهرباء واتصالات"
            l.contains("الميكانيكية") || l.contains("ميكاترون") || l.contains("mechatron") ->
                "الجامعة > الأقسام الهندسية > هندسة ميكاترونيات وميكانيكا"
            l.contains("الكيميائية") || l == "كيمياء" ->
                "الجامعة > الأقسام الهندسية > هندسة كيميائية"

            // Professions
            l == "doctor" || l.contains("دكتور") -> "المهن والخدمات > أطباء ودكاترة"
            l == "engineer" || l.contains("مهندس") -> "المهن والخدمات > مهندسون"
            l == "lawyer" || l.contains("محام") -> "المهن والخدمات > محامون واستشارات قانونية"
            l == "mechanics" || l == "technician" -> "المهن والخدمات > فنيون وصيانة وميكانيكا"
            l == "teacher" || l == "assistant" -> "المهن والخدمات > تدريس ومعيدون"

            // Personal Circle
            l.contains("family") || l.contains("العيلة") || l.contains("عائلة") -> "الدائرة الخاصة > العائلة"
            l.contains("friends") || l.contains("أصدقاء") || l.contains("اصدقاء") -> "الدائرة الخاصة > الأصدقاء"
            l.contains("vip") -> "الدائرة الخاصة > شخصيات مهمة (VIP)"

            // Known districts / cities
            EgyptGeoLookup.findExactOrAlias(raw) != null -> {
                val dist = EgyptGeoLookup.findExactOrAlias(raw)!!
                "المناطق والمدن > ${dist.key}"
            }

            // Codes & short numbers
            l == "codes" -> "أرقام سريعة وأكواد > أكواد وخدمات"

            // Groups / Housing / Misc projects
            l in setOf("شقق الاسكان", "مجموعة السبت", "ca", "bc", "g1", "gh") ->
                "مجموعات ومشروعات > $raw"

            // Religion / auxiliary tags
            l in setOf("مسلم", "مسيحي") -> "تصنيفات أخرى > $raw"
            l in setOf("phone 1", "phone so", "test") -> "تصنيفات أخرى > قوائم مستوردة ($raw)"

            else -> "مجموعات ومشروعات > $raw"
        }
    }

    private fun inferCategoriesFromContent(
        displayName: String,
        phones: List<String>,
        emails: List<String>,
        organization: String?,
        jobTitle: String?,
        notes: String?,
        address: String?,
        hasExplicitNonGroupCategory: Boolean,
    ): List<String> {
        val out = LinkedHashSet<String>()
        val blob = listOfNotNull(displayName, organization, jobTitle, notes, address, emails.joinToString(" "))
            .joinToString(" ")
            .lowercase(Locale.ROOT)

        // Geo district nesting
        val geo = EgyptGeoLookup.inferLocation(address, notes, displayName, organization)
        if (geo != null && geo.fromText) {
            out += "المناطق والمدن > ${geo.key}"
        }

        // University emails or mentions
        if (emails.any { it.contains("eaeat", ignoreCase = true) } || blob.contains("eaeat")) {
            val yearMatch = Regex("202[1-6]").find(blob)?.value
            out += if (yearMatch != null) {
                "الجامعة > الأكاديمية (EAEAT) > دفعة $yearMatch"
            } else {
                "الجامعة > الأكاديمية (EAEAT) > طلاب وخريجون"
            }
        } else if (containsAny(blob, "دكتور", "د.", "د/", "dr.", "dr ", "أستاذ", "معيد", "عميد", "كلية", "جامعة")) {
            out += if (containsAny(blob, "كلية", "جامعة", "معيد", "دكتور", "عميد")) {
                "الجامعة > هيئة التدريس والجامعة"
            } else {
                "المهن والخدمات > أطباء ودكاترة"
            }
        }

        // Security / Police / Gov
        if (containsAny(blob, "أمين شرطة", "امين شرطة", "ضابط", "ظابط", "عقيد", "مقدم", "رائد", "نقيب", "مباحث", "مرور", "شرطة", "النجدة", "قسم ", "نيابة", "محكمة")) {
            out += "الخدمات الحكومية والأمن > شرطة وأمن"
        } else if (containsAny(blob, "محامي", "مستشار", "شهر عقاري", "ضرائب", "تأمينات", "سجل مدني")) {
            out += "الخدمات الحكومية والأمن > جهات قانونية وحكومية"
        }

        // Medical & Pharmacy
        if (containsAny(blob, "صيدلية", "مستشفى", "عيادة", "تحاليل", "أشعة", "اسنان", "أسنان", "علاج")) {
            out += "المهن والخدمات > رعاية صحية وصيدليات"
        }

        // Trades, Delivery, Housing, Shops
        if (containsAny(blob, "سباك", "كهربائي", "نجار", "نقاش", "حداد", "ألوميتال", "تكييف", "صيانة", "ميكانيكي", "فني")) {
            out += "المهن والخدمات > فنيون وصيانة وميكانيكا"
        } else if (containsAny(blob, "سمسار", "عقار", "عقارات", "شقة", "شقق", "إسكان", "اسكان", "مقاول", "تشطيب")) {
            out += "المهن والخدمات > عقارات ومقاولات"
        } else if (containsAny(blob, "مطعم", "كافيه", "سوبر ماركت", "ماركت", "بقالة", "دليفري", "توصيل", "شحن", "مندوب", "اوبر", "أوبر", "كريم", "تاكسي")) {
            out += "المهن والخدمات > طلبات وتوصيل ومطاعم"
        } else if (containsAny(blob, "مهندس", "م.", "م/", "eng.", "eng ")) {
            out += "المهن والخدمات > مهندسون"
        }

        // Family / Friends hints in name
        if (containsAny(blob, "أبي", "ابي", "بابا", "أمي", "امي", "ماما", "أخويا", "اخويا", "أختي", "اختي", "خالي", "عمي", "عمو", "خالتو", "عمتو", "تيتا", "جدو", "ابن عم", "البيت")) {
            out += "الدائرة الخاصة > العائلة"
        }

        // International numbers
        val primary = phones.firstOrNull().orEmpty()
        if (primary.startsWith("+") && !primary.startsWith("+20")) {
            out += when {
                primary.startsWith("+966") -> "دولي وخارج مصر > السعودية"
                primary.startsWith("+971") -> "دولي وخارج مصر > الإمارات"
                primary.startsWith("+965") -> "دولي وخارج مصر > الكويت"
                primary.startsWith("+974") -> "دولي وخارج مصر > قطر"
                primary.startsWith("+1") -> "دولي وخارج مصر > أمريكا وكندا"
                primary.startsWith("+44") -> "دولي وخارج مصر > بريطانيا"
                else -> "دولي وخارج مصر > دول أخرى"
            }
        }

        if (!hasExplicitNonGroupCategory && primary.isNotBlank() && PhoneUtils.isUssdOrShortCode(primary)) {
            out += "أرقام سريعة وأكواد > أكواد وخدمات"
        }

        return out.toList()
    }

    private fun fallbackBucket(phones: List<String>, displayName: String): String {
        val local = phones.firstOrNull()?.let { PhoneUtils.toLocalEgyptianPhone(it) }.orEmpty()
        return when {
            local.startsWith("010") -> "جهات اتصال تلقائية > شبكات المحمول > فودافون (010)"
            local.startsWith("011") -> "جهات اتصال تلقائية > شبكات المحمول > اتصالات (011)"
            local.startsWith("012") -> "جهات اتصال تلقائية > شبكات المحمول > أورانج (012)"
            local.startsWith("015") -> "جهات اتصال تلقائية > شبكات المحمول > وي (015)"
            local.startsWith("0") -> "جهات اتصال تلقائية > خطوط أرضية"
            else -> {
                val first = displayName.trim().firstOrNull()?.uppercaseChar()
                if (first != null && first.isLetter()) {
                    "جهات اتصال تلقائية > أبجدياً > $first"
                } else {
                    "جهات اتصال تلقائية > بدون تصنيف"
                }
            }
        }
    }

    private fun containsAny(text: String, vararg needles: String): Boolean {
        return needles.any { text.contains(it) }
    }

    /**
     * Generates rich bilingual searchable tags describing everything known about a contact
     * (study/university, batch, department, profession, district, mobile network, custom tags).
     */
    fun generateTags(
        displayName: String,
        nickname: String?,
        phones: List<String>,
        emails: List<String>,
        categoryPaths: List<String>,
        organization: String?,
        jobTitle: String?,
        notes: String?,
        address: String?,
        district: String?,
        existingTags: List<String> = emptyList(),
    ): List<String> {
        val tags = LinkedHashSet<String>()
        existingTags.map { it.trim() }.filter { it.isNotBlank() }.forEach { tags += it }

        val blob = listOfNotNull(
            displayName,
            nickname,
            organization,
            jobTitle,
            notes,
            address,
            district,
            emails.joinToString(" "),
            categoryPaths.joinToString(" "),
        ).joinToString(" ").lowercase(Locale.ROOT)
        val normBlob = BilingualNameEngine.normalizeArabic(blob)

        // Category-derived concise tags
        for (path in categoryPaths) {
            val leaf = path.substringAfterLast(">").trim()
            if (leaf.isNotBlank() && !leaf.startsWith("فودافون") && !leaf.startsWith("بدون") && leaf.length <= 32) {
                tags += leaf
            }
        }

        // Bilingual profession & domain tags
        if (emails.any { it.contains("eaeat", true) } || normBlob.contains("eaeat") || normBlob.contains("الاكاديميه")) {
            tags += "EAEAT"
            tags += "الأكاديمية"
            tags += "جامعة"
            Regex("202[1-6]").find(normBlob)?.value?.let { yr ->
                tags += yr
                tags += "دفعة $yr"
            }
        }
        if (containsAny(normBlob, "دكتور", "dr.", "dr ", "doctor")) {
            tags += "دكتور"
            tags += "Doctor"
        }
        if (containsAny(normBlob, "مهندس", "eng.", "eng ", "engineer", "هندسه")) {
            tags += "مهندس"
            tags += "Engineer"
        }
        if (containsAny(normBlob, "كهرباء", "كهربائي", "electric")) {
            tags += "كهربائي"
            tags += "Electrician"
        }
        if (containsAny(normBlob, "ميكاترون", "mechatron", "ميكانيك", "mechanic")) {
            tags += "ميكانيكا"
            tags += "Mechanic"
        }
        if (containsAny(normBlob, "اتصالات", "telecom", "communication")) {
            tags += "اتصالات"
        }
        if (containsAny(normBlob, "سباك", "plumb")) {
            tags += "سباك"
            tags += "Plumber"
        }
        if (containsAny(normBlob, "شرطه", "ضابط", "ظابط", "مباحث", "police", "officer")) {
            tags += "شرطة"
            tags += "Police"
        }
        if (containsAny(normBlob, "محام", "lawyer", "counselor")) {
            tags += "محامي"
            tags += "Lawyer"
        }
        if (containsAny(normBlob, "صيدلي", "pharmacy", "عياده", "مستشفي", "اسنان")) {
            tags += "طبي"
            tags += "Medical"
        }
        if (containsAny(normBlob, "توصيل", "دليفري", "مندوب", "شحن", "delivery")) {
            tags += "توصيل"
            tags += "Delivery"
        }
        if (containsAny(normBlob, "سمسار", "عقار", "اسكان", "مقاول", "broker")) {
            tags += "عقارات"
            tags += "RealEstate"
        }
        if (containsAny(normBlob, "عائله", "عيله", "ابي", "بابا", "امي", "ماما", "اخويا", "اختي", "family")) {
            tags += "العائلة"
            tags += "Family"
        }
        if (containsAny(normBlob, "اصدقاء", "friends")) {
            tags += "أصدقاء"
            tags += "Friends"
        }

        // District tag
        val geo = district?.let { EgyptGeoLookup.findExactOrAlias(it) }
            ?: EgyptGeoLookup.inferLocation(address, notes, displayName, organization)
        if (geo != null) {
            tags += geo.key
            val enDist = geo.aliases.lastOrNull { it.all { ch -> ch.code < 128 } }
            if (!enDist.isNullOrBlank()) {
                tags += enDist.replaceFirstChar { it.uppercaseChar() }
            }
        }

        // Carrier / Country tag
        val primary = phones.firstOrNull().orEmpty()
        val local = PhoneUtils.toLocalEgyptianPhone(primary)
        when {
            local.startsWith("010") -> tags += "Vodafone"
            local.startsWith("011") -> tags += "Etisalat"
            local.startsWith("012") -> tags += "Orange"
            local.startsWith("015") -> tags += "WE"
            primary.startsWith("+966") -> tags += "KSA"
            primary.startsWith("+971") -> tags += "UAE"
        }

        return tags.filter { it.isNotBlank() }.take(18)
    }
}
