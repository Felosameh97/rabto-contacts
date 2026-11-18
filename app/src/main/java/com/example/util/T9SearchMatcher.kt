package com.example.util

import com.example.data.local.ContactEntity
import com.example.domain.model.ContactRecord
import java.util.Locale

/**
 * Bilingual (English + Arabic) T9 smart matcher for the dialer keypad.
 * Maps typed digits (2..9) against both phone digits and contact names/nicknames.
 */
object T9SearchMatcher {

    private fun charToT9Digit(ch: Char): Char? {
        if (ch in '0'..'9') return ch
        val lower = ch.lowercaseChar()
        return when (lower) {
            'a', 'b', 'c',
            'أ', 'إ', 'آ', 'ا', 'ب', 'ت', 'ث', 'ة' -> '2'
            'd', 'e', 'f',
            'د', 'ذ', 'ر', 'ز', 'و', 'ؤ' -> '3'
            'g', 'h', 'i',
            'ج', 'ح', 'خ', 'ط', 'ظ' -> '4'
            'j', 'k', 'l',
            'ي', 'ى', 'ئ', 'ك', 'ل' -> '5'
            'm', 'n', 'o',
            'م', 'ن', 'س', 'ش' -> '6'
            'p', 'q', 'r', 's',
            'ع', 'غ', 'ف', 'ص', 'ض' -> '7'
            't', 'u', 'v',
            'ق', 'ك', 'ش', 'ه' -> '8'
            'w', 'x', 'y', 'z',
            'ت', 'ث', 'خ', 'ه' -> '9'
            else -> null
        }
    }

    /**
     * Converts an Arabic or English name into standard T9 digit representation.
     * Note: Arabic characters use both the keypad's visual Arabic legend (rows 2..9)
     * and standard Arabic T9 grouping so lookups feel natural regardless of mental model.
     */
    fun toT9Digits(text: String): String {
        if (text.isBlank()) return ""
        val norm = ContactEntity.normalizeForSearch(text)
        val sb = StringBuilder(norm.length)
        for (ch in norm) {
            when (ch) {
                'a', 'b', 'c', 'ا', 'ب', 'ت', 'ث', 'ج' -> sb.append('2')
                'd', 'e', 'f', 'د', 'ذ', 'ر', 'ز', 'ه', 'و' -> sb.append('3')
                'g', 'h', 'i', 'ز', 'ح', 'ط', 'ظ' -> sb.append('4')
                'j', 'k', 'l', 'ي', 'ك', 'ل' -> sb.append('5')
                'm', 'n', 'o', 'م', 'ن', 'س', 'ش' -> sb.append('6')
                'p', 'q', 'r', 's', 'ع', 'غ', 'ف', 'ص', 'ض' -> sb.append('7')
                't', 'u', 'v', 'ق', 'ر', 'ش' -> sb.append('8')
                'w', 'x', 'y', 'z', 'ت', 'ث', 'خ' -> sb.append('9')
                in '0'..'9' -> sb.append(ch)
                ' ' -> sb.append(' ')
                else -> charToT9Digit(ch)?.let { sb.append(it) }
            }
        }
        return sb.toString()
    }

    /**
     * Builds initialism T9 digits (first letter of each word) + full T9 digits string.
     */
    private fun wordInitialsT9(text: String): String {
        val parts = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return ""
        val sb = StringBuilder(parts.size)
        for (part in parts) {
            val mapped = toT9Digits(part.take(1))
            if (mapped.isNotEmpty()) sb.append(mapped.first())
        }
        return sb.toString()
    }

    fun isPureDialDigits(query: String): Boolean {
        val clean = query.trim()
        return clean.isNotEmpty() && clean.all { it.isDigit() || it == '+' || it == '*' || it == '#' }
    }

    /**
     * Returns > 0 relevance score when [contact] matches [rawQuery] by phone digits,
     * T9 name digits, word initials, or normalized text. Returns 0 when not matched.
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
                    val base = if (pDigits.startsWith(digitsOnly) || PhoneUtils.toLocalEgyptianPhone(phone).startsWith(digitsOnly)) 120 else 95
                    return if (contact.isFavorite) base + 10 else base
                }
            }
        } else {
            // Non-digit symbol query like *858#
            for (phone in phones) {
                if (phone.contains(cleanQuery, ignoreCase = true)) return 110
            }
        }

        // 2. Text substring match
        val normQuery = ContactEntity.normalizeForSearch(cleanQuery)
        val normName = ContactEntity.normalizeForSearch(contact.displayName)
        val normNick = ContactEntity.normalizeForSearch(contact.nickname.orEmpty())
        if (normName.contains(normQuery) || normNick.contains(normQuery)) {
            return if (normName.startsWith(normQuery)) 100 else 80
        }

        // 3. T9 name matching when typing 2..9 digits
        if (digitsOnly.length >= 2 && digitsOnly.all { it in '2'..'9' }) {
            val t9Name = toT9Digits(contact.displayName).replace(" ", "")
            val t9Nick = toT9Digits(contact.nickname.orEmpty()).replace(" ", "")
            if (t9Name.startsWith(digitsOnly) || t9Nick.startsWith(digitsOnly)) {
                return if (contact.isFavorite) 90 else 75
            }
            if (wordInitialsT9(contact.displayName).startsWith(digitsOnly)) {
                return 68
            }
            val t9Words = toT9Digits(contact.displayName).split(' ')
            if (t9Words.any { it.startsWith(digitsOnly) }) {
                return 65
            }
            if (t9Name.contains(digitsOnly) || t9Nick.contains(digitsOnly)) {
                return 50
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
