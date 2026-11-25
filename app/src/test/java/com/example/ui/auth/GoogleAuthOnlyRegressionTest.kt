package com.example.ui.auth

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.data.matching.RecommendedMember
import com.example.data.model.ActivityPost
import com.example.data.model.ActivityReportRecord
import com.example.data.model.BillingCapabilityStatus
import com.example.data.model.ChatMessage
import com.example.data.model.ContentReportRecord
import com.example.data.model.Conversation
import com.example.data.model.FirestoreMonitoringState
import com.example.data.model.FriendRequest
import com.example.data.model.FriendStatus
import com.example.data.model.IceCandidateRecord
import com.example.data.model.MemberAnalyticsState
import com.example.data.model.MetricOrigin
import com.example.data.model.NotificationRecord
import com.example.data.model.OwnerAuthStatus
import com.example.data.model.OwnerDashboardSnapshot
import com.example.data.model.UserProfile
import com.example.data.repository.OwnerAdminDataSource
import com.example.data.repository.OwnerDashboardAggregator
import com.example.data.repository.SogoDataSource
import com.example.ui.admin.OwnerAdminScreen
import com.example.ui.admin.OwnerAdminViewModel
import com.example.ui.chat.ChatViewModel
import com.example.ui.chat.ProfileAndChatScreen
import com.example.ui.dashboard.AllMembersDirectoryDialog
import com.example.ui.dashboard.DashboardUiState
import com.example.ui.dashboard.HomeTabContent
import com.example.ui.dashboard.MembersDiscoveryTab
import com.example.ui.dashboard.OwnProfileTab
import com.example.ui.safety.CommunitySafetyCatalog
import com.example.ui.theme.SogoChatTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.util.CustomClassMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GoogleAuthOnlyRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var firestore: FirebaseFirestore

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        val context = app as Context
        val fbApp = FirebaseApp.getApps(context).firstOrNull { it.name == "google_auth_only_test_app" }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setProjectId("sogo-test-project")
                    .setApplicationId("1:1234567890:android:abcdef123456")
                    .setApiKey("AIzaSyTestOnlyPlaceholderKey1234567890")
                    .build(),
                "google_auth_only_test_app"
            )
        firestore = FirebaseFirestore.getInstance(fbApp)
    }

    @Test
    fun legacyFirestoreDocuments_withObsoleteVerificationFields_deserializeCleanly() {
        val rawProfile = mapOf(
            "userId" to "uid_legacy_1",
            "username" to "maria_ph",
            "displayName" to "Maria Clara",
            "age" to 26,
            "gender" to "Female",
            "country" to "Philippines",
            "isOnline" to true,
            "isVerifiedMember" to true,
            "verificationStatus" to "VERIFIED",
            "accountStatus" to "ACTIVE"
        )
        val decodedProfile = CustomClassMapper.convertToCustomClass(
            rawProfile,
            UserProfile::class.java,
            firestore.document("users/uid_legacy_1")
        )
        assertEquals("uid_legacy_1", decodedProfile.userId)
        assertEquals("Maria Clara", decodedProfile.displayName)
        assertTrue(decodedProfile.isOnline)

        val encodedProfile = CustomClassMapper.convertToPlainJavaTypes(decodedProfile) as Map<*, *>
        assertFalse(encodedProfile.containsKey("isVerifiedMember"))
        assertFalse(encodedProfile.containsKey("verificationStatus"))

        val rawPost = mapOf(
            "postId" to "post_legacy_1",
            "authorId" to "uid_legacy_1",
            "authorName" to "Maria Clara",
            "authorVerified" to true,
            "content" to "BGC coffee!",
            "moodEmoji" to "☕",
            "activityTag" to "Coffee Time"
        )
        val decodedPost = CustomClassMapper.convertToCustomClass(
            rawPost,
            ActivityPost::class.java,
            firestore.document("activity_posts/post_legacy_1")
        )
        assertEquals("post_legacy_1", decodedPost.postId)
        assertEquals("Maria Clara", decodedPost.authorName)
        val encodedPost = CustomClassMapper.convertToPlainJavaTypes(decodedPost) as Map<*, *>
        assertFalse(encodedPost.containsKey("authorVerified"))
    }

    @Test
    fun trustedAuthState_andSafetyGuidelines_neverClaimGoogleVerifiesRealWorldIdentity() {
        val nonGoogleSource = object : AuthSessionSource {
            override fun currentSnapshotData(): Triple<String?, String?, String?> =
                Triple("uid_1", "User One", null)

            override fun currentProviderSnapshot(): AuthProviderSnapshot = AuthProviderSnapshot(
                userId = "uid_1",
                displayName = "User One",
                email = "user1@gmail.com",
                emailVerified = true,
                isGoogleSignIn = false
            )

            override fun registerListeners(onChanged: (source: String) -> Unit): () -> Unit = {}
        }
        val listener = FirebaseAuthListener(nonGoogleSource)
        assertTrue(listener.state.value.emailVerified)
        assertFalse(listener.state.value.isGoogleSignIn)

        val adultsRule = CommunitySafetyCatalog.guidelines.first { it.id == "adults_only" }
        assertTrue(adultsRule.details.any { it.contains("Google account rather than real-world identity") })
    }

    @Test
    fun homeAndProfileTabs_omitVerificationBadgeAndCtaWhilePreservingCoreProfileUi() {
        val currentUser = UserProfile(
            userId = "uid_me",
            username = "leann_ph",
            displayName = "Leann",
            age = 26,
            gender = "Female",
            country = "Philippines",
            city = "Manila",
            isOnline = true
        )
        val onlineCandidate = UserProfile(
            userId = "uid_marco",
            username = "marco_ph",
            displayName = "Marco",
            age = 28,
            gender = "Male",
            country = "Philippines",
            city = "Manila",
            isOnline = true,
            lastActive = Timestamp(Date())
        )
        val uiState = DashboardUiState(
            currentUserProfile = currentUser,
            members = listOf(onlineCandidate),
            filteredMembers = listOf(onlineCandidate),
            pagedFilteredMembers = listOf(onlineCandidate),
            onlineMembers = listOf(onlineCandidate),
            onlineMembersYouMayLike = listOf(
                RecommendedMember(
                    profile = onlineCandidate,
                    matchScore = 90,
                    mutualInterests = listOf("Coffee"),
                    isNewMember = false
                )
            ),
            actualOnlineCount = 1,
            friendsCount = 3,
            unreadMessagesCount = 2,
            receivedLikesCount = 5,
            isLoading = false,
            hasResolvedProfile = true
        )

        composeTestRule.setContent {
            SogoChatTheme {
                HomeTabContent(
                    uiState = uiState,
                    onEditProfile = {},
                    onOpenMembersTab = {},
                    onOpenActivitiesTab = {},
                    onOpenProfileTab = {},
                    onOpenConversationsInbox = {},
                    onUpdateSearchQuery = {},
                    onToggleOnlineOnly = {},
                    onToggleOfflineOnly = {},
                    onToggleFriendsOnly = {},
                    onSelectMember = {},
                    onVideoCallMember = {},
                    onSayHiMember = {},
                    onFriendAction = {},
                    onAcceptFriendRequest = {},
                    onDeclineFriendRequest = {},
                    onReactActivity = { _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Leann").assertIsDisplayed()
        composeTestRule.onNodeWithText("@leann_ph").assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithText("Get Verified", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Pending Review", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("VERIFIED", ignoreCase = false).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithTag("profile_request_verification_button").fetchSemanticsNodes().size)
    }

    @Test
    fun ownProfileDiscoveryAndDirectory_omitVerificationControlsAndBadges() {
        val currentUser = UserProfile(
            userId = "uid_me",
            username = "leann_ph",
            displayName = "Leann",
            age = 26,
            gender = "Female",
            country = "Philippines",
            city = "Manila",
            isOnline = true
        )
        val candidate = UserProfile(
            userId = "uid_marco",
            username = "marco_ph",
            displayName = "Marco",
            age = 28,
            gender = "Male",
            country = "Philippines",
            city = "Manila",
            isOnline = true
        )
        val uiState = DashboardUiState(
            currentUserProfile = currentUser,
            members = listOf(candidate),
            filteredMembers = listOf(candidate),
            pagedFilteredMembers = listOf(candidate),
            onlineMembers = listOf(candidate),
            isLoading = false,
            hasResolvedProfile = true
        )

        composeTestRule.setContent {
            SogoChatTheme {
                OwnProfileTab(
                    uiState = uiState,
                    rewardCredits = 0,
                    onEditProfile = {},
                    onUpdateAvatarPhoto = {},
                    onUpdateCoverPhoto = {},
                    onAddGalleryPhoto = {},
                    onDeleteGalleryPhoto = {},
                    onSaveDatingPreferences = { _, _, _, _, _, _, _, _, _, _, _ -> },
                    onSelectMember = {},
                    onOpenBlockedUsers = {},
                    onOpenSafetyGuidelines = {},
                    onWatchRewardedAd = {},
                    onOpenOwnerAdmin = {},
                    onDeleteAccount = {},
                    onSignOut = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(0, composeTestRule.onAllNodesWithTag("own_profile_verification_card").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithTag("profile_request_verification_button").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Get Verified", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Verification", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun membersDiscoveryAndDirectory_omitVerificationBadges() {
        val candidate = UserProfile(
            userId = "uid_marco",
            username = "marco_ph",
            displayName = "Marco",
            age = 28,
            gender = "Male",
            country = "Philippines",
            city = "Manila",
            isOnline = true
        )
        val uiState = DashboardUiState(
            currentUserProfile = UserProfile(userId = "uid_me", displayName = "Leann", age = 26),
            members = listOf(candidate),
            filteredMembers = listOf(candidate),
            pagedFilteredMembers = listOf(candidate),
            onlineMembers = listOf(candidate),
            isLoading = false,
            hasResolvedProfile = true
        )

        composeTestRule.setContent {
            SogoChatTheme {
                MembersDiscoveryTab(
                    uiState = uiState,
                    onUpdateSearchQuery = {},
                    onSetGenderFilter = {},
                    onSetCountryFilter = {},
                    onSetAgeRange = {},
                    onToggleOnlineOnly = {},
                    onToggleOfflineOnly = {},
                    onToggleFriendsOnly = {},
                    onToggleSuggestedOnly = {},
                    onTogglePreferredMatchOnly = {},
                    onToggleMutualInterestsOnly = {},
                    onToggleFollowingOnly = {},
                    onToggleFavoritesOnly = {},
                    onResetFilters = {},
                    onLoadMore = {},
                    onSelectMember = {},
                    onVideoCallMember = {},
                    onFriendAction = {},
                    onToggleFollow = {},
                    onToggleFavorite = {},
                    onToggleProfileLike = {},
                    onSayHi = {},
                    onBlockMember = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Marco, 28").assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithText("VERIFIED", ignoreCase = false).fetchSemanticsNodes().size)
    }

    @Test
    fun directoryDialog_profileAndChat_andOwnerAdmin_omitManualVerificationControls() {
        val partner = UserProfile(
            userId = "uid_marco",
            username = "marco_ph",
            displayName = "Marco",
            age = 28,
            gender = "Male",
            country = "Philippines",
            city = "Manila",
            isOnline = true
        )
        val fakeSogo = object : SogoDataSource {
            override fun getCurrentUserId(): String? = "uid_me"
            override fun requireUserId(): String = "uid_me"
            override fun observeCurrentUserProfile(userId: String): Flow<UserProfile?> = MutableStateFlow(null)
            override fun observeMembers(): Flow<List<UserProfile>> = MutableStateFlow(emptyList())
            override suspend fun saveUserProfile(profile: UserProfile) = Unit
            override suspend fun updateProfilePhoto(photoUrl: String) = Unit
            override suspend fun updatePresence(isOnline: Boolean, force: Boolean) = Unit
            override fun getConversationIdForUsers(otherUid: String): String = "conv_me_marco"
            override fun observeConversations(): Flow<List<Conversation>> = MutableStateFlow(emptyList())
            override suspend fun ensureConversationExists(otherUid: String): String = "conv_me_marco"
            override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = MutableStateFlow(emptyList())
            override suspend fun sendMessage(conversationId: String, receiverId: String, content: String, type: String, mediaUrl: String?) = Unit
            override suspend fun markMessageAsRead(conversationId: String, messageId: String) = Unit
            override suspend fun blockUser(blockedId: String) = Unit
            override fun observeBlockedUserIds(): Flow<Set<String>> = MutableStateFlow(emptySet())
            override suspend fun reportUser(reportedId: String, reason: String, details: String?) = Unit
            override fun isOwnerCandidateSignedIn(): Boolean = false
            override fun observeNotifications(): Flow<List<NotificationRecord>> = MutableStateFlow(emptyList())
            override suspend fun markNotificationAsRead(notificationId: String) = Unit
            override fun getFriendRequestIdForUsers(otherUid: String): String = ""
            override fun observeFriendRequests(): Flow<List<FriendRequest>> = MutableStateFlow(emptyList())
            override suspend fun sendFriendRequest(targetUid: String) = Unit
            override suspend fun acceptFriendRequest(requestId: String) = Unit
            override suspend fun removeFriendOrRequest(otherUid: String) = Unit
            override fun observeCallSessions() = MutableStateFlow(emptyList<com.example.data.model.CallSession>())
            override suspend fun createCallSession(callee: UserProfile, callerName: String, callerPhotoUrl: String?, callType: String, offerSdp: String): String = ""
            override suspend fun answerCallSession(callId: String, answerSdp: String) = Unit
            override suspend fun updateCallStatus(callId: String, status: String) = Unit
            override suspend fun addIceCandidate(callId: String, participantUids: List<String>, sdpMid: String, sdpMLineIndex: Int, candidate: String) = Unit
            override fun observeIceCandidates(callId: String): Flow<List<IceCandidateRecord>> = MutableStateFlow(emptyList())
            override suspend fun deleteUserAccount(googleIdToken: String?) = Unit
        }
        val chatVm = ChatViewModel(app, fakeSogo)

        composeTestRule.setContent {
            SogoChatTheme {
                ProfileAndChatScreen(
                    partner = partner,
                    viewModel = chatVm,
                    friendStatus = FriendStatus.NONE,
                    onBack = {},
                    onStartVideoCall = {},
                    onStartAudioCall = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("18+ COMMUNITY").assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithText("VERIFIED PRO").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("18+ VERIFIED").fetchSemanticsNodes().size)
    }

    @Test
    fun ownerAdminScreen_omitsVerificationRequestsQueueAndVerificationButtons() {
        val fakeOwnerSource = object : OwnerAdminDataSource {
            val authStatus = OwnerDashboardAggregator.evaluateOwnerClaims(
                email = "gptgamestudioph@gmail.com",
                emailVerified = true,
                claims = mapOf("owner" to true, "admin" to true)
            )

            override suspend fun verifyOwnerAccess(forceTokenRefresh: Boolean): OwnerAuthStatus = authStatus

            override suspend fun loadOwnerDashboard(): OwnerDashboardSnapshot = OwnerDashboardSnapshot(
                authStatus = authStatus,
                memberAnalytics = MemberAnalyticsState(
                    totalAuthUsers = 5L,
                    authMetricsOrigin = MetricOrigin.LIVE_ADMIN_SDK,
                    observedProfileUsers = 5
                ),
                firestoreMonitoring = FirestoreMonitoringState(),
                pendingContentReports = emptyList<ContentReportRecord>(),
                activityReports = emptyList<ActivityReportRecord>(),
                memberDirectory = listOf(
                    UserProfile(
                        userId = "uid_bob",
                        displayName = "Bob",
                        age = 29,
                        country = "Philippines",
                        accountStatus = "ACTIVE"
                    )
                ),
                billingStatus = BillingCapabilityStatus()
            )

            override suspend fun updateContentReportStatus(reportId: String, newStatus: String) = Unit
            override suspend fun updateActivityReportStatus(reportId: String, newStatus: String) = Unit
            override suspend fun setMemberAccountStatus(targetUid: String, suspended: Boolean) = Unit
        }
        val ownerVm = OwnerAdminViewModel(app, fakeOwnerSource)

        composeTestRule.setContent {
            SogoChatTheme {
                OwnerAdminScreen(
                    onBack = {},
                    viewModel = ownerVm
                )
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(0, composeTestRule.onAllNodesWithText("PROFILE VERIFICATION REQUESTS", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Approve Verified", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Grant Verified", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Revoke Verified", substring = true).fetchSemanticsNodes().size)
    }
}
