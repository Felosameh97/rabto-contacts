package com.example.util

import com.example.data.local.ContactEntity
import com.example.domain.model.ContactRecord
import java.util.Locale

/**
 * Bilingual (English ITU E.161 + Arabic T9) smart matcher for the dialer keypad.
 * Typing digits such as 323 matches "dad", "دادي", phone numbers, nicknames, tags, and organizations.
 */
object T9SearchMatcher {

    /**
     * Checks whether a normalized character matches a typed keypad digit [2..9] under
     * standard English ITU E.161 OR Arabic keypad legends / phonetic Arabic T9 layouts.
     */
    fun charMatchesT9Digit(ch: Char, digit: Char): Boolean {
        if (ch == digit) return true
        return when (digit) {
            '2' -> ch in "abcأإآابتث" || ch == 'ا' || ch == 'ج'
            '3' -> ch in "defدذررزهو" || ch == 'ة'
            '4' -> ch in "ghiزحخطظجخ"
            '5' -> ch in "jklيكلىئ"
            '6' -> ch in "mnoمنسش"
            '7' -> ch in "pqrsعغفصضق"
            '8' -> ch in "tuvقرشهك"
            '9' -> ch in "wxyzتثخضظغ"
            else -> false
        }
    }

    /**
     * Deterministic canonical mapping aligned 1-to-1 with the dialer UI legends:
     * 2: ABC | أ ب ج
     * 3: DEF | د هـ و
     * 4: GHI | ز ح ط
     * 5: JKL | ي ك ل
     * 6: MNO | م ن س
     * 7: PQRS| ع ف ص
     * 8: TUV | ق ر ش
     * 9: WXYZ| ت ث خ
     */
    fun toT9Digits(text: String): String {
        if (text.isBlank()) return ""
        val norm = ContactEntity.normalizeForSearch(text)
        val sb = StringBuilder(norm.length)
        for (ch in norm) {
            when (ch) {
                'a', 'b', 'c', 'ا', 'ب', 'ج' -> sb.append('2')
                'd', 'e', 'f', 'د', 'ذ', 'ه', 'و' -> sb.append('3')
                'g', 'h', 'i', 'ز', 'ح', 'ط', 'ظ' -> sb.append('4')
                'j', 'k', 'l', 'ي', 'ك', 'ل' -> sb.append('5')
                'm', 'n', 'o', 'م', 'ن', 'س' -> sb.append('6')
                'p', 'q', 'r', 's', 'ع', 'غ', 'ف', 'ص', 'ض' -> sb.append('7')
                't', 'u', 'v', 'ق', 'ر', 'ش' -> sb.append('8')
                'w', 'x', 'y', 'z', 'ت', 'ث', 'خ' -> sb.append('9')
                in '0'..'9' -> sb.append(ch)
                ' ' -> sb.append(' ')
            }
        }
        return sb.toString()
    }

    /**
     * Multi-layout Arabic/English T9 matching index (handles alternate Arabic letter placements too).
     * Returns index of start match in normalized text (spaces removed/preserved as indicated), or -1.
     */
    private fun findFlexibleT9MatchIndex(rawText: String, queryDigits: String): Int {
        if (rawText.isBlank() || queryDigits.isBlank()) return -1
        val norm = ContactEntity.normalizeForSearch(rawText).filter { !it.isWhitespace() }
        if (norm.length < queryDigits.length) return -1
        for (start in 0..(norm.length - queryDigits.length)) {
            var matched = true
            for (offset in queryDigits.indices) {
                if (!charMatchesT9Digit(norm[start + offset], queryDigits[offset])) {
                    matched = false
                    break
                }
            }
            if (matched) return start
        }
        return -1
    }

    private fun wordMatchesFlexibleT9Prefix(rawText: String, queryDigits: String): Boolean {
        if (rawText.isBlank() || queryDigits.isBlank()) return false
        val words = ContactEntity.normalizeForSearch(rawText)
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        for (w in words) {
            if (w.length >= queryDigits.length) {
                var ok = true
                for (i in queryDigits.indices) {
                    if (!charMatchesT9Digit(w[i], queryDigits[i])) {
                        ok = false
                        break
                    }
                }
                if (ok) return true
            }
        }
        return false
    }

    private fun initialsMatchFlexibleT9(rawText: String, queryDigits: String): Boolean {
        val words = ContactEntity.normalizeForSearch(rawText)
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (words.size < queryDigits.length) return false
        for (start in 0..(words.size - queryDigits.length)) {
            var ok = true
            for (i in queryDigits.indices) {
                if (!charMatchesT9Digit(words[start + i].first(), queryDigits[i])) {
                    ok = false
                    break
                }
            }
            if (ok) return true
        }
        return false
    }

    fun isPureDialDigits(query: String): Boolean {
        val clean = query.trim()
        return clean.isNotEmpty() && clean.all { it.isDigit() || it == '+' || it == '*' || it == '#' }
    }

    /**
     * Returns > 0 relevance score when [contact] matches [rawQuery] by phone digits,
     * T9 name/nickname/tag/org digits, word initials, or normalized text. Returns 0 when not matched.
     */
    fun scoreContact(rawQuery: String, contact: ContactRecord): Int {
        val cleanQuery = rawQuery.trim()
        if (cleanQuery.isBlank()) return 0

        val digitsOnly = cleanQuery.filter { it.isDigit() }
        val phones = (listOf(contact.primaryPhone) + contact.phones).filter { it.isNotBlank() }

        // 1. Direct phone match (highest priority)
        if (digitsOnly.isNotEmpty()) {
            for (phone in phones) {
                val pDigits = phone.filter { it.isDigit() }
                if (pDigits.contains(digitsOnly)) {
                    val base = if (pDigits.startsWith(digitsOnly) || PhoneUtils.toLocalEgyptianPhone(phone).startsWith(digitsOnly)) 125 else 96
                    return if (contact.isFavorite) base + 10 else base
                }
            }
        } else {
            // Non-digit symbol query like *858#
            for (phone in phones) {
                if (phone.contains(cleanQuery, ignoreCase = true)) return 110
            }
        }

        // 2. Direct text substring match (names, nicknames, tags, org)
        val normQuery = ContactEntity.normalizeForSearch(cleanQuery)
        val normName = ContactEntity.normalizeForSearch(contact.displayName)
        val normNick = ContactEntity.normalizeForSearch(contact.nickname.orEmpty())
        val normOrg = ContactEntity.normalizeForSearch(contact.organization.orEmpty())
        val normTags = contact.tags.joinToString(" ") { ContactEntity.normalizeForSearch(it) }

        if (normName.contains(normQuery) || normNick.contains(normQuery)) {
            return if (normName.startsWith(normQuery)) 105 else 82
        }
        if (normOrg.contains(normQuery) || normTags.contains(normQuery)) {
            return 72
        }

        // 3. T9 matching when typing digits 2..9 (supports both prefix from 1+ digits for favorites/tags or >=2 digits)
        if (digitsOnly.isNotEmpty() && digitsOnly.all { it in '2'..'9' }) {
            // Single digit only matches exact word start if len >= 2, OR avoid noisy 1-digit all-contact matches
            if (digitsOnly.length < 2) return 0

            val nameMatchIdx = findFlexibleT9MatchIndex(contact.displayName, digitsOnly)
            if (nameMatchIdx == 0) {
                return if (contact.isFavorite) 95 else 88
            }
            val nickMatchIdx = findFlexibleT9MatchIndex(contact.nickname.orEmpty(), digitsOnly)
            if (nickMatchIdx == 0) {
                return if (contact.isFavorite) 92 else 84
            }
            if (wordMatchesFlexibleT9Prefix(contact.displayName, digitsOnly)) {
                return if (contact.isFavorite) 80 else 74
            }
            if (initialsMatchFlexibleT9(contact.displayName, digitsOnly)) {
                return 70
            }
            if (nameMatchIdx > 0 || nickMatchIdx > 0) {
                return 58
            }
            if (findFlexibleT9MatchIndex(contact.organization.orEmpty(), digitsOnly) >= 0 ||
                contact.tags.any { findFlexibleT9MatchIndex(it, digitsOnly) >= 0 }
            ) {
                return 48
            }
        }

        return 0
    }

    fun filterAndRank(query: String, contacts: List<ContactRecord>, limit: Int = 20): List<ContactRecord> {
        if (query.isBlank()) return emptyList()
        return contacts.asSequence()
            .mapNotNull { c ->
                val s = scoreContact(query, c)
                if (s > 0) c to s else null
            }
            .sortedWith(
                compareByDescending<Pair<ContactRecord, Int>> { it.second }
                    .thenByDescending { it.first.isFavorite }
                    .thenBy { it.first.displayName.lowercase(Locale.getDefault()) }
            )
            .take(limit)
            .map { it.first }
            .toList()
    }
}
