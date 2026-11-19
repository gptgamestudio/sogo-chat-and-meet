package com.example.ui.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.theme.SogoChatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuthValidatingCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun authValidatingCard_displaysCircularProgressAndStatusText() {
        composeTestRule.setContent {
            SogoChatTheme {
                AuthValidatingCard(statusLabel = "Validating Google Sign-In...")
            }
        }

        composeTestRule.onNodeWithTag("auth_validating_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auth_validating_progress").assertIsDisplayed()
        composeTestRule.onNodeWithText("Validating Google Sign-In...").assertIsDisplayed()
    }
}
