package com.example.ui.profile

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.data.model.UserProfile
import com.example.ui.theme.SogoChatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MemberProfileSetupScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun memberProfileSetupScreen_preservesSavedBirthdayWhenEditingOtherFields() {
        val existingProfile = UserProfile(
            userId = "uid_me",
            displayName = "Clara",
            birthday = "1995-11-03",
            age = 30,
            gender = "Female",
            country = "Philippines",
            city = "Cebu City",
            aboutMe = "Loves beaches",
            personality = "Romantic & Thoughtful",
            interests = listOf("Coffee & Kape")
        )

        var savedAboutMe: String? = null
        var savedBirthday: String? = null
        var savedAge: Int? = null

        composeTestRule.setContent {
            SogoChatTheme {
                MemberProfileSetupScreen(
                    initialProfile = existingProfile,
                    onSaveProfile = { _, bday, age, _, _, _, _, about, _, _ ->
                        savedBirthday = bday
                        savedAge = age
                        savedAboutMe = about
                    }
                )
            }
        }

        composeTestRule.onNodeWithTag("setup_about_me_input")
            .performScrollTo()
            .performTextReplacement("Loves sunset beaches and coffee")
        composeTestRule.onNodeWithTag("submit_profile_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals("Loves sunset beaches and coffee", savedAboutMe)
        assertEquals("1995-11-03", savedBirthday)
        val expectedAge = BirthdayValidator.calculateAge(1995, 11, 3)
        assertNotNull(expectedAge)
        assertEquals(expectedAge, savedAge)
    }

    @Test
    fun memberProfileSetupScreen_clampsLongDefaultDisplayNameTo50Chars() {
        val longGoogleName = "A".repeat(75)
        var savedName: String? = null

        composeTestRule.setContent {
            SogoChatTheme {
                MemberProfileSetupScreen(
                    initialProfile = null,
                    defaultDisplayName = longGoogleName,
                    onSaveProfile = { name, _, _, _, _, _, _, _, _, _ ->
                        savedName = name
                    }
                )
            }
        }

        composeTestRule.onNodeWithTag("submit_profile_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(50, savedName?.length)
    }
}
