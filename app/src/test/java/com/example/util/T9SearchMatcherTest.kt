package com.example.util

import com.example.domain.model.ContactRecord
import com.example.ui.components.initialsForName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class T9SearchMatcherTest {

    private val contacts = listOf(
        ContactRecord(
            id = "c1",
            displayName = "Ahmed Hassan",
            nickname = "أحمد حسن",
            primaryPhone = "01012345678",
            phones = listOf("01012345678"),
            isFavorite = true,
        ),
        ContactRecord(
            id = "c2",
            displayName = "Mohamed Ali",
            nickname = "محمد علي",
            primaryPhone = "01198765432",
            phones = listOf("01198765432"),
            isFavorite = false,
        ),
        ContactRecord(
            id = "c3",
            displayName = "كريم محمود",
            primaryPhone = "01255500011",
            phones = listOf("01255500011"),
            isFavorite = false,
        ),
    )

    @Test
    fun t9MatchesEnglishNamePrefix() {
        // A-H-M -> 2-4-6
        val hits = T9SearchMatcher.filterAndRank("246", contacts)
        assertTrue(hits.any { it.id == "c1" })
        assertEquals("c1", hits.first().id)
    }

    @Test
    fun t9MatchesArabicNamePrefix() {
        // ك-ر-ي-م -> 5-3-5-6
        val hits = T9SearchMatcher.filterAndRank("535", contacts)
        assertTrue(hits.any { it.id == "c3" })
    }

    @Test
    fun phoneDigitPrefixRanksAboveArbitraryMatch() {
        val hits = T9SearchMatcher.filterAndRank("0119", contacts)
        assertEquals(1, hits.size)
        assertEquals("c2", hits.first().id)
    }

    @Test
    fun textQueryStillMatchesNicknameAndDisplayName() {
        val hits = T9SearchMatcher.filterAndRank("محمد", contacts)
        assertEquals("c2", hits.first().id)
    }

    @Test
    fun initialsForNameHandlesBilingualNamesAndFallback() {
        assertEquals("AH", initialsForName("Ahmed Hassan"))
        assertEquals("M", initialsForName("Mohamed"))
        assertEquals("78", initialsForName("", "01012345678"))
        assertEquals("#", initialsForName("", ""))
    }

    @Test
    fun t9_323_matchesDadEnglishArabicNicknameAndTags() {
        val dadContact = ContactRecord(
            id = "dad_en",
            displayName = "Dad",
            primaryPhone = "01099998888",
            phones = listOf("01099998888"),
        )
        val dadArabicNickname = ContactRecord(
            id = "dad_ar",
            displayName = "أبو سارة",
            nickname = "دادي",
            primaryPhone = "01077776666",
            phones = listOf("01077776666"),
        )
        val taggedContact = ContactRecord(
            id = "dad_tag",
            displayName = "Samir Mansour",
            tags = listOf("dad"),
            primaryPhone = "01044443333",
            phones = listOf("01044443333"),
        )

        val hits = T9SearchMatcher.filterAndRank("323", contacts + listOf(dadContact, dadArabicNickname, taggedContact))
        val hitIds = hits.map { it.id }.toSet()
        assertTrue(hitIds.contains("dad_en"))
        assertTrue(hitIds.contains("dad_ar"))
        assertTrue(hitIds.contains("dad_tag"))
    }
}
