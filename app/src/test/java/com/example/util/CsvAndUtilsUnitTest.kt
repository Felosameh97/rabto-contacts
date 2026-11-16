package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class CsvAndUtilsUnitTest {

    @Test
    fun normalizePhone_handlesEgyptianAndUssdAndShortcodes() {
        assertEquals("+201012345678", PhoneUtils.normalizePhone("010-123-45678"))
        assertEquals("+201228303819", PhoneUtils.normalizePhone("'01228303819"))
        assertEquals("+201271021648", PhoneUtils.normalizePhone("201271021648"))
        assertEquals("111", PhoneUtils.normalizePhone("111"))
        assertEquals("**67*01112345678#", PhoneUtils.normalizePhone("**67*01112345678#"))
        assertEquals("01012345678", PhoneUtils.toLocalEgyptianPhone("+201012345678"))
        assertEquals("201012345678", PhoneUtils.toWhatsAppDigits("010-123-45678"))
    }

    @Test
    fun paymentUtils_concatenatesPhoneWithTemplates() {
        assertEquals(
            "https://ipn.eg/S/01012345678/instapay",
            PaymentUtils.buildPaymentUrl("https://ipn.eg/S/{phone_local}/instapay", "+20 101 234 5678")
        )
        assertEquals(
            "tel:*7115*01228303819%23",
            PaymentUtils.buildPaymentUrl("tel:*7115*{phone_local}%23", "+201228303819")
        )
        assertEquals(
            "https://pay.custom.eg/01099887766",
            PaymentUtils.buildPaymentUrl("https://pay.custom.eg/", "+201099887766")
        )
    }

    @Test
    fun csvParser_parsesGoogleContactsMultiValueAndDistrictGeo() {
        val sampleCsv = """
            First Name,Middle Name,Last Name,Organization Name,Notes,Labels,E-mail 1 - Value,Phone 1 - Value,Address 1 - Formatted
            أحمد,,علي,شركة النور,"سطر أول
            المطرية القاهرة",الجامعة / هندسة ::: * myContacts,ahmed@test.com,+201023694045 ::: 010-168-09852,شارع الحرية المطرية
        """.trimIndent()

        val parsed = CsvParser.parseStream(ByteArrayInputStream(sampleCsv.toByteArray()))
        assertEquals(1, parsed.contacts.size)
        val c = parsed.contacts.first()
        assertEquals("أحمد علي", c.displayName)
        assertEquals(2, c.phones.size)
        assertEquals("+201023694045", c.primaryPhone)
        assertTrue(c.categoryPaths.contains("الجامعة > هندسة"))
        assertEquals("المطرية", c.district)
        assertTrue(c.latitude != null && c.longitude != null)
    }

    @Test
    fun smartCategoryEngine_mergesDuplicateYearsAndAutoBucketsUnlabeled() {
        val (merged, starred) = SmartCategoryEngine.normalizeAndNestLabels(
            rawLabels = listOf("2023", "Eae&at 2023", "* starred", "* myContacts"),
            displayName = "كريم محمد",
            phones = listOf("+201012345678"),
            emails = emptyList(),
            organization = null,
            jobTitle = null,
            notes = null,
            address = null,
        )
        assertTrue(starred)
        assertEquals(1, merged.count { it == "الجامعة > الأكاديمية (EAEAT) > دفعة 2023" })
        assertTrue(merged.contains("الدائرة الخاصة > مميزون بنجمة"))

        val (unlabeledCategories, notStarred) = SmartCategoryEngine.normalizeAndNestLabels(
            rawLabels = emptyList(),
            displayName = "رقم جديد",
            phones = listOf("+201099887766"),
            emails = emptyList(),
            organization = null,
            jobTitle = null,
            notes = null,
            address = null,
        )
        assertTrue(!notStarred)
        assertTrue(unlabeledCategories.contains("جهات اتصال تلقائية > شبكات المحمول > فودافون (010)"))
    }

    @Test
    fun paymentUtils_buildsVodafoneAmountUssdAppSchemeAndMeezaQr() {
        assertEquals(
            "tel:*9*7*01012345678*150%23",
            PaymentUtils.buildPaymentUrl("tel:*9*7*{phone_local}%23", "+201012345678", amount = "150")
        )
        assertEquals(
            "app://com.orange.orangecash|com.orange.myorange.egypt?phone=01228303819",
            PaymentUtils.buildPaymentUrl(
                "app://com.orange.orangecash|com.orange.myorange.egypt?phone={phone_local}",
                "+201228303819"
            )
        )
        val qr = PaymentUtils.buildMeezaQrPayload(name = "Ahmed", rawPhone = "+201012345678", amount = "250")
        assertTrue(qr.contains("eg.meeza.wallet"))
        assertTrue(qr.contains("5303818"))
        assertTrue(qr.contains("5802EG"))
        assertTrue(qr.contains("6304"))
        assertEquals(4, qr.substringAfterLast("6304").length)
    }

    @Test
    fun egyptGeoLookup_infersFromLandlineAndInternationalCodes() {
        assertEquals("القاهرة الكبرى (أرضي 02)", EgyptGeoLookup.inferLocationFromPhone("0224567890")?.key)
        assertEquals("الشرقية (أرضي 055)", EgyptGeoLookup.inferLocationFromPhone("0552345678")?.key)
        assertEquals("السعودية (+966)", EgyptGeoLookup.inferLocationFromPhone("+966501234567")?.key)
    }
}

