package com.example.util

import com.example.domain.model.ContactRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase3FeaturesTest {

    @Test
    fun appLockPinValidationAndHashingWorkCorrectly() {
        assertTrue(AppLockSecurity.isValidPinFormat("1234"))
        assertTrue(AppLockSecurity.isValidPinFormat("123456"))
        assertFalse(AppLockSecurity.isValidPinFormat("123"))
        assertFalse(AppLockSecurity.isValidPinFormat("1234567"))
        assertFalse(AppLockSecurity.isValidPinFormat("12ab"))

        val hash1 = AppLockSecurity.hashPin("4829")
        val hash2 = AppLockSecurity.hashPin("4829")
        val hashOther = AppLockSecurity.hashPin("4820")

        assertEquals(64, hash1.length)
        assertEquals(hash1, hash2)
        assertNotEquals(hash1, hashOther)

        assertTrue(AppLockSecurity.verifyPin("4829", hash1))
        assertFalse(AppLockSecurity.verifyPin("4820", hash1))
        assertFalse(AppLockSecurity.verifyPin("", hash1))
        assertFalse(AppLockSecurity.verifyPin("4829", ""))
    }

    @Test
    fun durationClockAndRelativeTimeFormatCorrectly() {
        assertEquals("00:00", AppLockSecurity.formatDurationClock(0))
        assertEquals("01:05", AppLockSecurity.formatDurationClock(65))
        assertEquals("1:02:03", AppLockSecurity.formatDurationClock(3723))

        val now = 1_750_000_000_000L
        assertEquals("منذ لحظات", AppLockSecurity.formatRelativeTimeAr(now - 30_000L, now))
        assertEquals("منذ ساعة", AppLockSecurity.formatRelativeTimeAr(now - 3_600_000L, now))
        assertEquals("أمس", AppLockSecurity.formatRelativeTimeAr(now - 24 * 3_600_000L, now))
        assertEquals("منذ 3 أيام", AppLockSecurity.formatRelativeTimeAr(now - 3 * 24 * 3_600_000L, now))
    }

    @Test
    fun formatContactShareTextContainsKeyDetailsWithoutSecrets() {
        val contact = ContactRecord(
            id = "c1",
            displayName = "Ahmed Hassan",
            nickname = "أحمد حسن",
            primaryPhone = "+201012345678",
            phones = listOf("+201012345678", "01112223344"),
            emails = listOf("ahmed@example.com"),
            organization = "FeloTech",
            jobTitle = "Engineer",
            district = "المعادي",
            notes = "عميل مهم",
        )
        val formatted = AppLockSecurity.formatContactShareText(contact)
        assertTrue(formatted.contains("Ahmed Hassan (أحمد حسن)"))
        assertTrue(formatted.contains("+201012345678"))
        assertTrue(formatted.contains("01112223344"))
        assertTrue(formatted.contains("ahmed@example.com"))
        assertTrue(formatted.contains("Engineer - FeloTech"))
        assertTrue(formatted.contains("المعادي"))
        assertTrue(formatted.contains("عميل مهم"))
    }

    @Test
    fun jsonContactExporterRoundTripPreservesSandboxIsolationAndFlags() {
        val original = listOf(
            ContactRecord(
                id = "c_1",
                displayName = "Kareem Ali",
                nickname = "كريم علي",
                primaryPhone = "+201099887766",
                phones = listOf("+201099887766"),
                categoryPaths = listOf("VIP", "Work/Cairo"),
                tags = listOf("vip", "cairo"),
                isFavorite = true,
                muted = true,
                blocked = false,
                syncedToDevice = true, // even if true in memory, export/re-import enforces isolation
                avatarColorSeed = 5,
                lastContactedEpochMs = 1_740_000_000_000L,
            ),
            ContactRecord(
                id = "c_2",
                displayName = "Spam Caller",
                primaryPhone = "01200001111",
                phones = listOf("01200001111"),
                blocked = true,
            ),
        )

        val jsonText = JsonContactExporter.exportToJson(original, nowMs = 1_745_000_000_000L)
        assertTrue(jsonText.contains("\"sandboxIsolated\": true"))

        val parsed = JsonContactExporter.parseFromJson(jsonText).getOrThrow()
        assertEquals(2, parsed.size)
        val p1 = parsed[0]
        assertEquals("Kareem Ali", p1.displayName)
        assertEquals("كريم علي", p1.nickname)
        assertTrue(p1.isFavorite)
        assertTrue(p1.muted)
        assertFalse(p1.blocked)
        assertFalse(p1.syncedToDevice)
        assertEquals(5, p1.avatarColorSeed)
        assertEquals(1_740_000_000_000L, p1.lastContactedEpochMs)

        val p2 = parsed[1]
        assertTrue(p2.blocked)
        assertFalse(p2.syncedToDevice)
    }

    @Test
    fun jsonContactExporterHandlesPlainArrayAndRejectsInvalidJson() {
        val plainArrayJson = """
            [
              {"displayName": "Sara Nabil", "primaryPhone": "01055556666", "muted": true}
            ]
        """.trimIndent()
        val parsed = JsonContactExporter.parseFromJson(plainArrayJson).getOrThrow()
        assertEquals(1, parsed.size)
        assertEquals("Sara Nabil", parsed[0].displayName)
        assertTrue(parsed[0].muted)
        assertFalse(parsed[0].syncedToDevice)

        assertTrue(JsonContactExporter.parseFromJson("").isFailure)
        assertTrue(JsonContactExporter.parseFromJson("{not-valid-json").isFailure)
    }

    @Test
    fun deduplicatorFindsClustersByPhoneAndNameAndPreservesMuteBlock() {
        val c1 = ContactRecord(
            id = "c_a",
            displayName = "Mohamed Tarek",
            primaryPhone = "01012345678",
            phones = listOf("01012345678"),
            muted = true,
            blocked = false,
            lastContactedEpochMs = 1000L,
            syncedToDevice = true,
        )
        val c2 = ContactRecord(
            id = "c_b",
            displayName = "محمد طارق",
            primaryPhone = "+201012345678",
            phones = listOf("+201012345678", "01199887766"),
            blocked = true,
            lastContactedEpochMs = 5000L,
        )
        val c3 = ContactRecord(
            id = "c_c",
            displayName = "Unique Person",
            primaryPhone = "01555554444",
            phones = listOf("01555554444"),
        )

        val clusters = ContactDeduplicator.findDuplicateClusters(listOf(c1, c2, c3))
        assertEquals(1, clusters.size)
        assertEquals(2, clusters[0].size)

        val mergedResult = ContactDeduplicator.deduplicateAndMerge(clusters[0])
        assertEquals(1, mergedResult.mergedContacts.size)
        val winner = mergedResult.mergedContacts[0]
        assertTrue(winner.muted)
        assertTrue(winner.blocked)
        assertEquals(5000L, winner.lastContactedEpochMs)
        assertFalse(winner.syncedToDevice)
    }
}
