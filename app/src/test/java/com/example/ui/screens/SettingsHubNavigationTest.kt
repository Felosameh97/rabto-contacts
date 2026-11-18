package com.example.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.ui.screens.settings.SettingsSection
import com.example.ui.theme.CalmPalette
import com.example.ui.theme.RabtaContactsTheme
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsHubNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsHubNavigatesIntoDedicatedSubPages() {
        var currentSection by mutableStateOf<SettingsSection?>(null)

        composeRule.setContent {
            RabtaContactsTheme {
                ToolsSyncScreen(
                    userEmail = "test@example.com",
                    totalContacts = 42,
                    recentLogs = emptyList(),
                    syncedDeviceContacts = emptyList(),
                    paymentTemplates = emptyList(),
                    githubRepoSlug = "",
                    releaseInfo = null,
                    themeMode = ThemeMode.SYSTEM,
                    calmPalette = CalmPalette.SLATE,
                    autoRecordCalls = true,
                    callRecordings = emptyList(),
                    isBusy = false,
                    selectedSection = currentSection,
                    onSelectSection = { currentSection = it },
                    onSetThemeMode = {},
                    onSetCalmPalette = {},
                    onSetAutoRecordCalls = {},
                    onDeleteRecording = {},
                    onImportBundledCsv = {},
                    onImportDevicePhonebook = {},
                    onScanGoogleDrive = {},
                    onReorganizeCategories = {},
                    onImportExternalFile = { _, _ -> },
                    onPullFromCloud = {},
                    onShareExport = {},
                    onOpenQrScanner = {},
                    onSyncWeeklyWhatsApp = {},
                    onRemoveFromPhonebook = {},
                    onAddPaymentTemplate = { _, _ -> },
                    onDeletePaymentTemplate = {},
                    onCheckGithubUpdates = {},
                    onPermissionsChanged = {},
                    onSignOut = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_hub_banner").assertIsDisplayed()
        SettingsSection.entries.forEach { sec ->
            composeRule.onNodeWithTag("settings_subpage_list")
                .performScrollToNode(hasTestTag("settings_nav_${sec.testTagKey}"))
            composeRule.onNodeWithTag("settings_nav_${sec.testTagKey}").assertIsDisplayed()
        }

        // Scroll back & Navigate into Appearance sub-page
        composeRule.onNodeWithTag("settings_subpage_list")
            .performScrollToNode(hasTestTag("settings_nav_appearance"))
        composeRule.onNodeWithTag("settings_nav_appearance").performClick()
        composeRule.waitForIdle()
        assertEquals(SettingsSection.APPEARANCE, currentSection)
        composeRule.onNodeWithTag("settings_show_photos_switch").assertIsDisplayed()

        // Navigate back to Hub and then into Sync & Cache sub-page
        currentSection = null
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_subpage_list")
            .performScrollToNode(hasTestTag("settings_nav_sync_cache"))
        composeRule.onNodeWithTag("settings_nav_sync_cache").performClick()
        composeRule.waitForIdle()
        assertEquals(SettingsSection.SYNC_CACHE, currentSection)
        composeRule.onNodeWithTag("settings_sync_health_card").assertIsDisplayed()
    }
}
