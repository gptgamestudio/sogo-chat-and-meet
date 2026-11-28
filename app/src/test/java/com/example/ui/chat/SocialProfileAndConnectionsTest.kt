package com.example.ui.chat

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CallSession
import com.example.data.model.ChatMessage
import com.example.data.model.FriendRequest
import com.example.data.model.FriendStatus
import com.example.data.model.IceCandidateRecord
import com.example.data.model.NotificationRecord
import com.example.data.model.UserProfile
import com.example.data.repository.SogoDataSource
import com.example.ui.common.CountryFlagHelper
import com.example.ui.theme.SogoChatTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SocialProfileAndConnectionsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun fakeSource(): SogoDataSource = object : SogoDataSource {
        override fun getCurrentUserId(): String = "uid_me"
        override fun requireUserId(): String = "uid_me"
        override fun observeCurrentUserProfile(userId: String): Flow<UserProfile?> = MutableStateFlow(null)
        override fun observeMembers(): Flow<List<UserProfile>> = MutableStateFlow(emptyList())
        override suspend fun saveUserProfile(profile: UserProfile) = Unit
        override suspend fun updateProfilePhoto(photoUrl: String) = Unit
        override suspend fun updatePresence(isOnline: Boolean, force: Boolean) = Unit
        override fun getConversationIdForUsers(otherUid: String): String = "conv_uid_me_$otherUid"
        override fun observeConversations() = MutableStateFlow(emptyList<com.example.data.model.Conversation>())
        override suspend fun ensureConversationExists(otherUid: String): String = getConversationIdForUsers(otherUid)
        override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = MutableStateFlow(emptyList())
        override suspend fun sendMessage(
            conversationId: String,
            receiverId: String,
            content: String,
            type: String,
            mediaUrl: String?
        ) = Unit
        override suspend fun markMessageAsRead(conversationId: String, messageId: String) = Unit
        override suspend fun blockUser(blockedId: String) = Unit
        override fun observeBlockedUserIds(): Flow<Set<String>> = MutableStateFlow(emptySet())
        override suspend fun reportUser(reportedId: String, reason: String, details: String?) = Unit
        override fun observeNotifications(): Flow<List<NotificationRecord>> = MutableStateFlow(emptyList())
        override suspend fun markNotificationAsRead(notificationId: String) = Unit
        override fun getFriendRequestIdForUsers(otherUid: String): String = "fr_uid_me_$otherUid"
        override fun observeFriendRequests(): Flow<List<FriendRequest>> = MutableStateFlow(emptyList())
        override suspend fun sendFriendRequest(targetUid: String) = Unit
        override suspend fun acceptFriendRequest(requestId: String) = Unit
        override suspend fun removeFriendOrRequest(otherUid: String) = Unit
        override fun observeCallSessions(): Flow<List<CallSession>> = MutableStateFlow(emptyList())
        override suspend fun createCallSession(
            callee: UserProfile,
            callerName: String,
            callerPhotoUrl: String?,
            callType: String,
            offerSdp: String
        ): String = "call_1"
        override suspend fun answerCallSession(callId: String, answerSdp: String) = Unit
        override suspend fun updateCallStatus(callId: String, status: String) = Unit
        override suspend fun addIceCandidate(
            callId: String,
            participantUids: List<String>,
            sdpMid: String,
            sdpMLineIndex: Int,
            candidate: String
        ) = Unit
        override fun observeIceCandidates(callId: String): Flow<List<IceCandidateRecord>> = MutableStateFlow(emptyList())
        override suspend fun deleteUserAccount(googleIdToken: String?) = Unit
    }

    @Test
    fun countryFlagHelper_mapsCountriesAndFormatsWithCity() {
        assertEquals("🇵🇭", CountryFlagHelper.flagForCountry("Philippines"))
        assertEquals("🇺🇸", CountryFlagHelper.flagForCountry("United States"))
        assertEquals("🇯🇵", CountryFlagHelper.flagForCountry("Japan"))
        assertEquals("🇬🇧", CountryFlagHelper.flagForCountry("UK"))
        assertEquals("🌍", CountryFlagHelper.flagForCountry("Overseas"))
        assertEquals("🇵🇭 Makati, Philippines", CountryFlagHelper.formatCountryWithFlag("Philippines", "Makati"))
    }

    @Test
    fun centeredSocialProfileCard_rendersAllProfileFieldsAndConnectionButtons() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val partner = UserProfile(
            userId = "uid_carmen",
            username = "carmen_ph",
            displayName = "Carmen",
            age = 26,
            gender = "Female",
            country = "Philippines",
            city = "Makati",
            relationshipStatus = "Open to Dating",
            lookingFor = "Serious Dating",
            interests = listOf("Coffee & Kape", "Travel"),
            hobbies = listOf("Hiking")
        )
        val chatVm = ChatViewModel(app, fakeSource())

        var addFriendClicks = 0
        var videoCallClicks = 0

        composeTestRule.setContent {
            SogoChatTheme {
                ProfileAndChatScreen(
                    partner = partner,
                    viewModel = chatVm,
                    friendStatus = FriendStatus.NONE,
                    onFriendActionClick = { addFriendClicks++ },
                    onStartVideoCall = { videoCallClicks++ },
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("centered_social_profile_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_username_text").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("@carmen_ph").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("🇵🇭 Makati, Philippines").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Open to Dating").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Serious Dating").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("26 yrs").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Coffee & Kape").onFirst().performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("profile_add_friend_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Add Friend").assertIsDisplayed()
        composeTestRule.onNodeWithTag("start_private_conversation_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Chat Now").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_video_call_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Video Call Now").assertIsDisplayed()

        composeTestRule.onNodeWithTag("profile_add_friend_button").performClick()
        composeTestRule.onNodeWithTag("profile_video_call_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, addFriendClicks)
        assertEquals(1, videoCallClicks)
    }

    @Test
    fun centeredSocialProfileCard_respectsPrivacyFlagsAndPendingAndIncomingFriendControls() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val partner = UserProfile(
            userId = "uid_hidden",
            username = "stealth_user",
            displayName = "Alex",
            age = 29,
            gender = "Male",
            country = "Japan",
            city = "Tokyo",
            hideAge = true,
            hideLocation = true
        )
        val chatVm = ChatViewModel(app, fakeSource())

        var acceptClicks = 0
        var rejectClicks = 0

        composeTestRule.setContent {
            SogoChatTheme {
                ProfileAndChatScreen(
                    partner = partner,
                    viewModel = chatVm,
                    friendStatus = FriendStatus.REQUEST_RECEIVED,
                    onFriendActionClick = { acceptClicks++ },
                    onDeclineFriendRequest = { rejectClicks++ },
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Location hidden").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Private").performScrollTo().assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText("29", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("Tokyo", substring = true).fetchSemanticsNodes().isEmpty())

        composeTestRule.onNodeWithTag("profile_add_friend_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("profile_reject_friend_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, acceptClicks)
        assertEquals(1, rejectClicks)
    }

    @Test
    fun centeredSocialProfileCard_pendingStateShowsPendingAndBlockedDisablesCallAndFriend() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val partner = UserProfile(
            userId = "uid_pending",
            displayName = "Mia",
            age = 25,
            country = "Philippines",
            city = "Cebu"
        )
        val blockedSet = MutableStateFlow(setOf("uid_pending"))
        val blockedSource = object : SogoDataSource by fakeSource() {
            override fun observeBlockedUserIds(): Flow<Set<String>> = blockedSet
        }
        val chatVm = ChatViewModel(app, blockedSource)

        composeTestRule.setContent {
            SogoChatTheme {
                MemberProfileDetailsView(
                    partner = partner,
                    friendStatus = FriendStatus.REQUEST_SENT,
                    isBlocked = true,
                    onStartChat = {},
                    onBlock = {},
                    onReport = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("profile_add_friend_button").performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("profile_video_call_button").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun centeredSocialProfileCard_showsPendingLabelWhenRequestSent() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val partner = UserProfile(
            userId = "uid_sent",
            displayName = "Sofia",
            age = 24,
            country = "Philippines"
        )
        val chatVm = ChatViewModel(app, fakeSource())
        var duplicateActionTriggered = false

        composeTestRule.setContent {
            SogoChatTheme {
                ProfileAndChatScreen(
                    partner = partner,
                    viewModel = chatVm,
                    friendStatus = FriendStatus.REQUEST_SENT,
                    onFriendActionClick = { duplicateActionTriggered = true },
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Pending").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_add_friend_button").performClick()
        composeTestRule.waitForIdle()
        // Clicking Pending opens confirmation dialog instead of firing duplicate request
        assertEquals(false, duplicateActionTriggered)
        composeTestRule.onNodeWithText("Friend Request Pending").assertIsDisplayed()
    }
}
