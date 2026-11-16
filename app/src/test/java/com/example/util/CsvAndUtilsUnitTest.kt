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
        assertEquals("Ahmed Ali", c.displayName)
        assertTrue(c.nickname?.contains("أحمد علي") == true)
        assertEquals(2, c.phones.size)
        assertEquals("+201023694045", c.primaryPhone)
        assertTrue(c.categoryPaths.contains("الجامعة > هندسة"))
        assertTrue(c.tags.contains("هندسة"))
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

    @Test
    fun bilingualNameEngine_picksDetailedEnglishAndPreservesArabicAndShortVariants() {
        val (display, nickname) = BilingualNameEngine.resolveEnglishAndNickname(
            listOf("Ahmed", "أحمد محمد علي المطرية", "Dr. Ahmed Mohamed Ali")
        )
        assertEquals("Dr. Ahmed Mohamed Ali", display)
        assertTrue(nickname != null)
        assertTrue(nickname!!.contains("أحمد محمد علي المطرية"))
        assertTrue(nickname.contains("Ahmed"))

        val (arConverted, arNick) = BilingualNameEngine.resolveEnglishAndNickname(
            listOf("أحمد حسن")
        )
        assertEquals("Ahmed Hassan", arConverted)
        assertEquals("أحمد حسن", arNick)
    }

    @Test
    fun contactDeduplicator_mergesDuplicatesPreservingShortAndLongNamesAndTags() {
        val shortContact = com.example.domain.model.ContactRecord(
            id = "c_short",
            displayName = "أحمد",
            primaryPhone = "01012345678",
            phones = listOf("01012345678"),
            notes = "دفعة 2023",
            categoryPaths = listOf("2023"),
        )
        val detailedContact = com.example.domain.model.ContactRecord(
            id = "c_long",
            displayName = "Dr. Ahmed Ali El-Sayed",
            primaryPhone = "+201012345678",
            phones = listOf("+201012345678", "01122334455"),
            emails = listOf("ahmed@eaeat.edu.eg"),
            notes = "عيادة المطرية",
            address = "المطرية القاهرة",
            syncedToDevice = true,
        )

        val res = ContactDeduplicator.deduplicateAndMerge(listOf(shortContact, detailedContact))
        assertEquals(1, res.mergedContacts.size)
        assertEquals(1, res.supersededIds.size)
        assertEquals("c_short", res.supersededIds.first())
        assertEquals(1, res.duplicateGroupsMerged)

        val merged = res.mergedContacts.first()
        assertEquals("c_long", merged.id)
        assertEquals("Dr. Ahmed Ali El-Sayed", merged.displayName)
        assertTrue(merged.nickname?.contains("أحمد") == true)
        assertEquals(2, merged.phones.size)
        assertTrue(merged.notes?.contains("دفعة 2023") == true)
        assertTrue(merged.notes?.contains("عيادة المطرية") == true)
        assertTrue(merged.tags.contains("دكتور"))
        assertTrue(merged.tags.contains("Doctor"))
        assertTrue(merged.tags.contains("EAEAT"))
        assertTrue(merged.tags.contains("2023"))
        assertTrue(merged.tags.contains("Vodafone"))
    }

    @Test
    fun deterministicContactId_preventsReimportDuplicatesAndPreservesPhoto() {
        val id1 = ContactDeduplicator.deterministicContactId(listOf("+201012345678"), emptyList(), "Ahmed")
        val id2 = ContactDeduplicator.deterministicContactId(listOf("010-123-45678"), emptyList(), "أحمد")
        assertEquals(id1, id2)

        val cWithPhoto = com.example.domain.model.ContactRecord(
            id = "old_1",
            displayName = "Ahmed",
            primaryPhone = "01012345678",
            phones = listOf("01012345678"),
            photoUri = "/data/user/0/app/files/contact_photos/a.jpg",
        )
        val cCloud = com.example.domain.model.ContactRecord(
            id = "cloud_2",
            displayName = "Ahmed Mohamed Ali",
            primaryPhone = "+201012345678",
            phones = listOf("+201012345678"),
            notes = "مهندس مدني",
        )
        val mergedRes = ContactDeduplicator.deduplicateAndMerge(listOf(cWithPhoto, cCloud))
        assertEquals(1, mergedRes.mergedContacts.size)
        assertEquals("/data/user/0/app/files/contact_photos/a.jpg", mergedRes.mergedContacts.first().photoUri)
        assertTrue(com.example.ai.RabtaAiAssistant.parseIsoDateToEpochMs("2026-11-20T14:30") != null)
    }

    @Test
    fun localTranscriptAnalyzer_extractsHighlightsCalendarAndLocalCommands() {
        val sampleTranscript = "أهلاً يا أحمد، متنساش بكرة عندنا اجتماع تسليم الشغل الساعة 5 مساءً. الحساب المطلوب 1500 جنيه فودافون كاش."
        val analysis = com.example.ai.LocalTranscriptAnalyzer.analyze(
            rawTranscript = sampleTranscript,
            contactName = "Ahmed Ali",
            phone = "+201012345678",
            referenceTimeMs = 1763294400000L, // Fixed reference epoch
        )
        assertTrue(analysis.highlights.isNotEmpty())
        assertTrue(analysis.extractedNotes.contains("1500"))
        assertTrue(analysis.eventTitle.isNotBlank())
        assertTrue(analysis.eventEpochMs != null && analysis.eventEpochMs!! > 1763294400000L)
        assertTrue(analysis.eventDateIso.endsWith("17:00"))

        val callCmd = com.example.ai.LocalTranscriptAnalyzer.tryParseCommandLocally(
            userText = "اتصل بأحمد علي",
            availableTopCategories = listOf("الجامعة", "العمل"),
        )
        assertEquals(com.example.ai.AiCommandAction.CALL, callCmd?.action)
        assertEquals("أحمد علي", callCmd?.queryNameOrPhone)

        val payCmd = com.example.ai.LocalTranscriptAnalyzer.tryParseCommandLocally(
            userText = "حول 250 جنيه انستاباي لمحمد",
            availableTopCategories = emptyList(),
        )
        assertEquals(com.example.ai.AiCommandAction.PAYMENT, payCmd?.action)
        assertEquals("250", payCmd?.amount)

        val catCmd = com.example.ai.LocalTranscriptAnalyzer.tryParseCommandLocally(
            userText = "افتح قسم الجامعة",
            availableTopCategories = listOf("الجامعة"),
        )
        assertEquals(com.example.ai.AiCommandAction.OPEN_CATEGORY, catCmd?.action)
        assertEquals("الجامعة", catCmd?.categoryPath)
    }
}


