package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalMediaCacheManager
import com.example.domain.model.CallActionItem
import com.example.domain.model.CallTranscriptRecord
import com.example.ui.screens.computeAlphabetHeaderIndices
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase5FeaturesTest {

    @Test
    fun inferActionKindRespectsExplicitAndDetectsArabicKeywords() {
        assertEquals(
            "CALENDAR",
            VaultActionHelper.inferActionKind(CallActionItem(id = "1", title = "اتصال عادي", kind = "CALENDAR")),
        )
        assertEquals(
            "PAYMENT",
            VaultActionHelper.inferActionKind(CallActionItem(id = "2", title = "تحويل 500 جنيه انستاباي", kind = "TASK")),
        )
        assertEquals(
            "CALENDAR",
            VaultActionHelper.inferActionKind(CallActionItem(id = "3", title = "اجتماع الفريق غداً", kind = "TASK")),
        )
        assertEquals(
            "FOLLOW_UP",
            VaultActionHelper.inferActionKind(CallActionItem(id = "4", title = "متابعة العميل بعد الظهر", kind = "TASK")),
        )
        assertEquals(
            "TASK",
            VaultActionHelper.inferActionKind(CallActionItem(id = "5", title = "مراجعة العقد", kind = "TASK")),
        )
    }

    @Test
    fun extractOpenActionItemsFiltersCompletedAndSortsByDueThenTimestamp() {
        val recOlder = CallTranscriptRecord(
            id = "rec_old",
            contactName = "عمر",
            phone = "01011112222",
            timestampMs = 1_000L,
            actionItems = listOf(
                CallActionItem(id = "done", title = "تم التنفيذ", completed = true),
                CallActionItem(id = "due_soon", title = "موعد قريب", dueEpochMs = 5_000L, kind = "CALENDAR"),
                CallActionItem(id = "no_due_old", title = "متابعة بدون وقت", kind = "FOLLOW_UP"),
            ),
        )
        val recNewer = CallTranscriptRecord(
            id = "rec_new",
            contactName = "سارة",
            phone = "01033334444",
            timestampMs = 9_000L,
            actionItems = listOf(
                CallActionItem(id = "due_later", title = "دفع فاتورة", dueEpochMs = 8_000L, kind = "PAYMENT"),
                CallActionItem(id = "no_due_new", title = "إرسال الملف", kind = "TASK"),
            ),
        )

        val open = VaultActionHelper.extractOpenActionItems(listOf(recOlder, recNewer))
        assertEquals(listOf("due_soon", "due_later", "no_due_new", "no_due_old"), open.map { it.actionItem.id })
    }

    @Test
    fun computeAlphabetHeaderIndicesAccountsForImportBannerAndFavorites() {
        val grouped = linkedMapOf("A" to 3, "B" to 2, "C" to 4)

        val base = computeAlphabetHeaderIndices(
            groupedSizes = grouped,
            hasImportBanner = false,
            hasEmptyImportCard = false,
            hasHeaderFavorites = false,
        )
        assertEquals(mapOf("A" to 2, "B" to 6, "C" to 9), base)

        val withBannerAndFavorites = computeAlphabetHeaderIndices(
            groupedSizes = grouped,
            hasImportBanner = true,
            hasEmptyImportCard = false,
            hasHeaderFavorites = true,
        )
        assertEquals(mapOf("A" to 5, "B" to 9, "C" to 12), withBannerAndFavorites)
    }

    @Test
    fun formatBytesReadableFormatsZeroKbAndMb() {
        assertEquals("0 KB", VaultActionHelper.formatBytesReadable(0L))
        assertEquals("1 KB", VaultActionHelper.formatBytesReadable(512L))
        assertEquals("10 KB", VaultActionHelper.formatBytesReadable(10_240L))
        assertEquals("2.0 MB", VaultActionHelper.formatBytesReadable(2L * 1024L * 1024L))
    }

    @Test
    fun purgeOrphanedAudioFilesRemovesOnlyUnreferencedFiles() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dir = LocalMediaCacheManager.recordingsDir(context)
            dir.listFiles()?.forEach { it.delete() }

            val keptByPath = File(dir, "kept_path.m4a").apply { writeBytes(ByteArray(512) { 1 }) }
            val keptByName = File(dir, "kept_name.m4a").apply { writeBytes(ByteArray(512) { 2 }) }
            val orphan = File(dir, "orphan.m4a").apply { writeBytes(ByteArray(512) { 3 }) }

            val removed = LocalMediaCacheManager.purgeOrphanedAudioFiles(
                context = context,
                referencedAudioPathsOrNames = setOf(keptByPath.absolutePath, keptByName.name),
            )

            assertEquals(1, removed)
            assertTrue(keptByPath.exists())
            assertTrue(keptByName.exists())
            assertFalse(orphan.exists())

            // Cleanup
            keptByPath.delete()
            keptByName.delete()
        }
    }
}
