package com.example.util

import com.example.data.remote.FirestoreContactsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class SyncAndBulkImportTest {

    @Test
    fun sanitizeFirestoreDocId_alwaysMatchesFirestoreRulesPattern() {
        val ruleRegex = Regex("^[a-zA-Z0-9_\\-]{1,128}$")
        val samples = listOf(
            "",
            "   ",
            "c_1234567890abcdef",
            "bad/id with spaces & symbols!@#",
            "اسم عربي كامل",
            "x".repeat(250),
            "cat_valid-123_abc",
        )
        for (raw in samples) {
            val safe = FirestoreContactsRepository.sanitizeFirestoreDocId(raw, "c")
            assertTrue("Expected '$safe' (from '$raw') to match Firestore rule ID pattern", ruleRegex.matches(safe))
        }
        assertEquals("c_1234567890abcdef", FirestoreContactsRepository.sanitizeFirestoreDocId("c_1234567890abcdef", "c"))
    }

    @Test
    fun phoneUtils_normalizesArabicIndicAndPersianDigits() {
        assertEquals("+201012345678", PhoneUtils.normalizePhone("٠١٠١٢٣٤٥٦٧٨"))
        assertEquals("+201122334455", PhoneUtils.normalizePhone("۰۱۱۲۲۳۳۴۴۵۵"))
        val extracted = PhoneUtils.extractPhonesFromSharedText("رقم العميل: ٠١٢٣٤٥٦٧٨٩٠ أو 01098765432")
        assertTrue(extracted.contains("+201234567890"))
        assertTrue(extracted.contains("+201098765432"))
    }

    @Test
    fun csvParser_autoDetectsSemicolonTabAndHeaderless16kRows() {
        // Semicolon-delimited Excel CSV with Arabic digits
        val semiCsv = "الاسم;رقم الموبايل\nأحمد علي;٠١٠١٢٣٤٥٦٧٨\nسارة محمود;01122334455\n"
        val semiRes = CsvParser.parseStreamAuto(
            ByteArrayInputStream(semiCsv.toByteArray(StandardCharsets.UTF_8)),
            sourceTitle = "clients",
            skipEnrich = true,
        )
        assertEquals(2, semiRes.contacts.size)
        assertEquals("+201012345678", semiRes.contacts[0].primaryPhone)

        // Tab-delimited headerless file (row 0 is already a contact)
        val tsv = "Mohamed Hassan\t01000000001\nMona Adel\t01000000002\n"
        val tsvRes = CsvParser.parseStreamAuto(
            ByteArrayInputStream(tsv.toByteArray(StandardCharsets.UTF_8)),
            sourceTitle = "tsv_dump",
            skipEnrich = true,
        )
        assertEquals(2, tsvRes.contacts.size)
        assertEquals("Mohamed Hassan", tsvRes.contacts[0].displayName)

        // 16,000-row stress test verifying no drops and fast streaming
        val bulkCsv = buildString(16_000 * 32) {
            appendLine("Name,Phone")
            for (i in 1..16_000) {
                val num = String.format("010%08d", i)
                append("Client ").append(i).append(',').append(num).append('\n')
            }
        }
        val bulkRes = CsvParser.parseStreamAuto(
            ByteArrayInputStream(bulkCsv.toByteArray(StandardCharsets.UTF_8)),
            sourceTitle = "bulk16k",
            skipEnrich = true,
        )
        assertEquals(16_000, bulkRes.contacts.size)
    }

    @Test
    fun vCardStreamParser_handlesFoldedLinesAndDeterministicIds() {
        val vcf = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Karim
              Mansour
            TEL;TYPE=CELL:٠١٠٥٥٥٥٦٦٦٦
            EMAIL:karim@example.com
            END:VCARD
            BEGIN:VCARD
            VERSION:3.0
            FN:Karim Mansour
            TEL:+201055556666
            EMAIL:karim@example.com
            END:VCARD
        """.trimIndent()

        val parsed = CsvParser.parseStreamAuto(
            ByteArrayInputStream(vcf.toByteArray(StandardCharsets.UTF_8)),
            sourceTitle = "contacts_export",
            skipEnrich = true,
        )
        assertEquals(2, parsed.contacts.size)
        assertEquals("+201055556666", parsed.contacts[0].primaryPhone)
        assertEquals(parsed.contacts[0].id, parsed.contacts[1].id)
    }
}
