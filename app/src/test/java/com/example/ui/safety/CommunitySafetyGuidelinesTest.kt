package com.example.ui.safety

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.ui.theme.SogoChatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CommunitySafetyGuidelinesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun catalog_coversPrivateInteractionsAndReportingMechanisms() {
        val all = CommunitySafetyCatalog.guidelinesFor(SafetyCategory.ALL)
        assertTrue(all.size >= 8)

        val privateTips = CommunitySafetyCatalog.guidelinesFor(SafetyCategory.PRIVATE_INTERACTIONS)
        assertTrue(privateTips.size >= 3)
        assertTrue(privateTips.any { it.actionTarget == SafetyActionTarget.PRIVACY_SETTINGS })

        val reportingTools = CommunitySafetyCatalog.guidelinesFor(SafetyCategory.REPORTING_TOOLS)
        assertTrue(reportingTools.size >= 3)
        assertTrue(reportingTools.any { it.actionTarget == SafetyActionTarget.REPORT_IN_CHAT })
        assertTrue(reportingTools.any { it.actionTarget == SafetyActionTarget.BLOCKED_USERS })
    }

    @Test
    fun dialog_filtersGuidelinesAndTriggersQuickSafetyCallbacks() {
        var dismissCount = 0
        var blockedUsersClicks = 0
        var privacyClicks = 0
        var reportClicks = 0

        composeTestRule.setContent {
            SogoChatTheme {
                CommunitySafetyGuidelinesDialog(
                    onDismiss = { dismissCount++ },
                    onOpenBlockedUsers = { blockedUsersClicks++ },
                    onOpenPrivacySettings = { privacyClicks++ },
                    onReportMemberInChat = { reportClicks++ }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Community Safety Guidelines").assertIsDisplayed()
        composeTestRule.onNodeWithTag("safety_guidelines_list")
            .performScrollToNode(hasTestTag("safety_card_private_info_guard"))
        composeTestRule.onNodeWithText("Protect Financial & Personal Details").assertIsDisplayed()

        // Scroll back up to filter chips and filter to Reporting & Blocking tools
        composeTestRule.onNodeWithTag("safety_guidelines_list")
            .performScrollToNode(hasTestTag("safety_filter_reporting_tools"))
        composeTestRule.onNodeWithTag("safety_filter_reporting_tools").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Flag Private Chat Messages").assertIsDisplayed()
        composeTestRule.onNodeWithTag("safety_guidelines_list")
            .performScrollToNode(hasTestTag("safety_card_block_and_restrict"))
        composeTestRule.onNodeWithText("Block & Restrict Unwanted Members").assertIsDisplayed()
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag("safety_card_adults_only").fetchSemanticsNodes().size
        )

        // Expand collapsed "Report Profiles & Activity Posts" card
        composeTestRule.onNodeWithTag("safety_guidelines_list")
            .performScrollToNode(hasTestTag("safety_card_report_profiles_activities"))
        composeTestRule.onNodeWithText("Report Profiles & Activity Posts").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("safety_guidelines_list")
            .performScrollToNode(
                androidx.compose.ui.test.hasText(
                    "From any member profile or chat header menu",
                    substring = true
                )
            )
        composeTestRule.onNodeWithText(
            "From any member profile or chat header menu, choose Report Member to escalate misconduct or fake accounts."
        ).assertIsDisplayed()

        // Filter to Private Chat & Calls and verify tip visibility
        composeTestRule.onNodeWithTag("safety_guidelines_list")
            .performScrollToNode(hasTestTag("safety_filter_private_interactions"))
        composeTestRule.onNodeWithTag("safety_filter_private_interactions").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("safety_guidelines_list")
            .performScrollToNode(hasTestTag("safety_card_private_info_guard"))
        composeTestRule.onNodeWithText("Protect Financial & Personal Details").assertIsDisplayed()

        // Scroll to quick actions at the bottom and trigger all safety callbacks
        composeTestRule.onNodeWithTag("safety_guidelines_list")
            .performScrollToNode(hasTestTag("safety_open_privacy_button"))
        composeTestRule.onNodeWithTag("safety_open_privacy_button").performClick()
        composeTestRule.onNodeWithTag("safety_guidelines_list")
            .performScrollToNode(hasTestTag("safety_open_blocked_users_button"))
        composeTestRule.onNodeWithTag("safety_open_blocked_users_button").performClick()
        composeTestRule.onNodeWithTag("safety_guidelines_list")
            .performScrollToNode(hasTestTag("safety_report_member_button"))
        composeTestRule.onNodeWithTag("safety_report_member_button").performClick()
        composeTestRule.onNodeWithTag("safety_acknowledge_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, privacyClicks)
        assertEquals(1, blockedUsersClicks)
        assertEquals(1, reportClicks)
        assertEquals(1, dismissCount)
    }
}
