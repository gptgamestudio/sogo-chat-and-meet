package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ActivityReportRecord
import com.example.data.model.BillingCapabilityStatus
import com.example.data.model.ChatMessage
import com.example.data.model.ContentReportRecord
import com.example.data.model.Conversation
import com.example.data.model.FirestoreMonitoringState
import com.example.data.model.FriendRequest
import com.example.data.model.IceCandidateRecord
import com.example.data.model.MemberAnalyticsState
import com.example.data.model.MetricOrigin
import com.example.data.model.NotificationRecord
import com.example.data.model.OwnerAuthStatus
import com.example.data.model.OwnerDashboardSnapshot
import com.example.data.model.QuotaSeverity
import com.example.data.model.UserProfile
import com.example.data.repository.OwnerAdminDataSource
import com.example.data.repository.OwnerDashboardAggregator
import com.example.data.repository.SogoDataSource
import com.example.ui.admin.OwnerAdminViewModel
import com.example.ui.dashboard.MainDashboardViewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OwnerAdminTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeOwnerAdminDataSource(
        var authStatus: OwnerAuthStatus,
        var reports: List<ContentReportRecord> = emptyList(),
        var activityReports: List<ActivityReportRecord> = emptyList(),
        var memberDirectory: List<UserProfile> = emptyList()
    ) : OwnerAdminDataSource {
        val updatedStatuses = mutableListOf<Pair<String, String>>()
        val updatedActivityStatuses = mutableListOf<Pair<String, String>>()
        val verifiedChanges = mutableListOf<Pair<String, Boolean>>()
        val suspensionChanges = mutableListOf<Pair<String, Boolean>>()

        override suspend fun verifyOwnerAccess(forceTokenRefresh: Boolean): OwnerAuthStatus = authStatus

        override suspend fun loadOwnerDashboard(): OwnerDashboardSnapshot {
            if (!authStatus.isAuthorized) {
                return OwnerDashboardSnapshot(
                    authStatus = authStatus,
                    billingStatus = OwnerDashboardAggregator.defaultSparkBillingStatus(),
                    statusNotice = authStatus.denialReason
                )
            }
            return OwnerDashboardSnapshot(
                authStatus = authStatus,
                memberAnalytics = MemberAnalyticsState(
                    totalAuthUsers = 12L,
                    authMetricsOrigin = MetricOrigin.LIVE_ADMIN_SDK,
                    observedProfileUsers = 10,
                    profileMetricsOrigin = MetricOrigin.FIRESTORE_ESTIMATE,
                    dailyRegistrations = 2,
                    weeklyRegistrations = 5,
                    monthlyRegistrations = 12,
                    duplicatesExcluded = 1
                ),
                firestoreMonitoring = FirestoreMonitoringState(
                    readsToday = 420L,
                    writesToday = 65L,
                    deletesToday = 2L,
                    storageBytes = 1024L * 512L,
                    usageOrigin = MetricOrigin.UNAVAILABLE,
                    quotaWarnings = OwnerDashboardAggregator.evaluateSparkQuotaWarnings(
                        readsToday = null,
                        writesToday = null,
                        deletesToday = null,
                        storageBytes = null,
                        origin = MetricOrigin.UNAVAILABLE
                    )
                ),
                pendingContentReports = reports,
                activityReports = activityReports,
                memberDirectory = memberDirectory,
                billingStatus = BillingCapabilityStatus(
                    activePlan = "Firebase Spark Plan ($0/month)",
                    autoUpgradeDisabled = true
                )
            )
        }

        override suspend fun updateContentReportStatus(reportId: String, newStatus: String) {
            if (!authStatus.isAuthorized) {
                throw SecurityException("Owner access required")
            }
            updatedStatuses += reportId to newStatus
        }

        override suspend fun updateActivityReportStatus(reportId: String, newStatus: String) {
            if (!authStatus.isAuthorized) {
                throw SecurityException("Owner access required")
            }
            updatedActivityStatuses += reportId to newStatus
        }

        override suspend fun setMemberVerification(targetUid: String, verified: Boolean) {
            if (!authStatus.isAuthorized) {
                throw SecurityException("Owner access required")
            }
            verifiedChanges += targetUid to verified
        }

        override suspend fun setMemberAccountStatus(targetUid: String, suspended: Boolean) {
            if (!authStatus.isAuthorized) {
                throw SecurityException("Owner access required")
            }
            suspensionChanges += targetUid to suspended
        }
    }

    @Test
    fun evaluateOwnerClaims_requiresOwnerEmailEmailVerifiedAndServerClaims() {
        val valid = OwnerDashboardAggregator.evaluateOwnerClaims(
            email = "gptgamestudioph@gmail.com",
            emailVerified = true,
            claims = mapOf("owner" to true, "admin" to true)
        )
        assertTrue(valid.isAuthorized)
        assertNull(valid.denialReason)

        val unverifiedEmail = OwnerDashboardAggregator.evaluateOwnerClaims(
            email = "gptgamestudioph@gmail.com",
            emailVerified = false,
            claims = mapOf("owner" to true, "admin" to true)
        )
        assertFalse(unverifiedEmail.isAuthorized)
        assertTrue(unverifiedEmail.denialReason!!.contains("email_verified"))

        val missingOwnerClaim = OwnerDashboardAggregator.evaluateOwnerClaims(
            email = "gptgamestudioph@gmail.com",
            emailVerified = true,
            claims = mapOf("admin" to true)
        )
        assertFalse(missingOwnerClaim.isAuthorized)
        assertFalse(missingOwnerClaim.hasOwnerClaim)

        val spoofedEmail = OwnerDashboardAggregator.evaluateOwnerClaims(
            email = "member@gmail.com",
            emailVerified = true,
            claims = mapOf("owner" to true, "admin" to true)
        )
        assertFalse(spoofedEmail.isAuthorized)
    }

    @Test
    fun aggregator_deduplicatesProfilesComputesTrendsAndEvaluatesSparkQuotas() {
        val nowMs = 1_795_340_000_000L
        val profiles = listOf(
            UserProfile(userId = "u1", displayName = "Alice", createdAt = Timestamp(Date(nowMs - 60_000L))),
            UserProfile(userId = "u1", displayName = "Alice Duplicate", createdAt = Timestamp(Date(nowMs - 60_000L))),
            UserProfile(userId = "u2", displayName = "Bob", createdAt = Timestamp(Date(nowMs - 3L * 86_400_000L))),
            UserProfile(userId = "u3", displayName = "Carla", createdAt = Timestamp(Date(nowMs - 20L * 86_400_000L)))
        )

        val (unique, duplicates) = OwnerDashboardAggregator.deduplicateProfiles(profiles)
        assertEquals(3, unique.size)
        assertEquals(1, duplicates)

        val (daily, weekly, monthly) = OwnerDashboardAggregator.buildRegistrationTrendsFromProfiles(unique, nowMs = nowMs)
        assertEquals(1, daily)
        assertEquals(2, weekly)
        assertEquals(3, monthly)

        assertEquals("uid_...5678", OwnerDashboardAggregator.maskUserId("uid_12345678"))

        val unavailableQuotas = OwnerDashboardAggregator.evaluateSparkQuotaWarnings(
            readsToday = null,
            writesToday = null,
            deletesToday = null,
            storageBytes = null,
            origin = MetricOrigin.UNAVAILABLE
        )
        assertTrue(unavailableQuotas.all { it.severity == QuotaSeverity.UNAVAILABLE })

        val warningQuotas = OwnerDashboardAggregator.evaluateSparkQuotaWarnings(
            readsToday = 46_000L, // 92% of 50k -> CRITICAL
            writesToday = 16_000L, // 80% of 20k -> WARNING
            deletesToday = 200L, // 1% -> OK
            storageBytes = 1_000_000L, // OK
            origin = MetricOrigin.LIVE_MONITORING
        )
        assertEquals(QuotaSeverity.CRITICAL, warningQuotas[0].severity)
        assertEquals(QuotaSeverity.WARNING, warningQuotas[1].severity)
        assertEquals(QuotaSeverity.OK, warningQuotas[2].severity)
        assertEquals(QuotaSeverity.OK, warningQuotas[3].severity)
    }

    @Test
    fun ownerAdminViewModel_locksWithoutVerifiedClaimsAndUnlocksWithValidOwnerClaims() = runTest {
        val deniedSource = FakeOwnerAdminDataSource(
            authStatus = OwnerDashboardAggregator.evaluateOwnerClaims(
                email = "gptgamestudioph@gmail.com",
                emailVerified = true,
                claims = emptyMap()
            )
        )
        val deniedVm = OwnerAdminViewModel(app, deniedSource)
        advanceUntilIdle()
        assertFalse(deniedVm.uiState.value.isLoading)
        assertFalse(deniedVm.uiState.value.authStatus.isAuthorized)
        assertNull(deniedVm.uiState.value.snapshot.memberAnalytics.totalAuthUsers)
        assertNotNull(deniedVm.uiState.value.statusBanner)

        val report = ContentReportRecord(
            reportId = "creport_42",
            reporterId = "uid_alice",
            reportedUserId = "uid_bob",
            conversationId = "conv_1",
            messageId = "msg_1",
            messageType = "TEXT",
            messageExcerpt = "Spam message",
            reason = "Spam or scam",
            status = "PENDING"
        )
        val authorizedSource = FakeOwnerAdminDataSource(
            authStatus = OwnerDashboardAggregator.evaluateOwnerClaims(
                email = "gptgamestudioph@gmail.com",
                emailVerified = true,
                claims = mapOf("owner" to true, "admin" to true)
            ),
            reports = listOf(report)
        )
        val authorizedVm = OwnerAdminViewModel(app, authorizedSource)
        advanceUntilIdle()
        assertTrue(authorizedVm.uiState.value.authStatus.isAuthorized)
        assertEquals(12L, authorizedVm.uiState.value.snapshot.memberAnalytics.totalAuthUsers)
        assertEquals(MetricOrigin.LIVE_ADMIN_SDK, authorizedVm.uiState.value.snapshot.memberAnalytics.authMetricsOrigin)
        assertEquals(MetricOrigin.UNAVAILABLE, authorizedVm.uiState.value.snapshot.firestoreMonitoring.usageOrigin)

        authorizedVm.updateReportStatus("creport_42", "REVIEWED")
        advanceUntilIdle()
        assertEquals(listOf("creport_42" to "REVIEWED"), authorizedSource.updatedStatuses)
        assertEquals("REVIEWED", authorizedVm.uiState.value.snapshot.pendingContentReports.first().status)
    }

    @Test
    fun dashboardViewModel_onlyExposesOwnerCandidateEntryForVerifiedEmailCandidate() = runTest {
        val regularSource = object : SogoDataSource {
            override fun getCurrentUserId(): String? = "uid_member"
            override fun requireUserId(): String = "uid_member"
            override fun observeCurrentUserProfile(userId: String): Flow<UserProfile?> = MutableStateFlow(null)
            override fun observeMembers(): Flow<List<UserProfile>> = MutableStateFlow(emptyList())
            override suspend fun saveUserProfile(profile: UserProfile) = Unit
            override suspend fun updateProfilePhoto(photoUrl: String) = Unit
            override suspend fun updatePresence(isOnline: Boolean, force: Boolean) = Unit
            override fun getConversationIdForUsers(otherUid: String): String = ""
            override fun observeConversations(): Flow<List<Conversation>> = MutableStateFlow(emptyList())
            override suspend fun ensureConversationExists(otherUid: String): String = ""
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
        val memberVm = MainDashboardViewModel(app, regularSource)
        val job = backgroundScope.launch { memberVm.uiState.collect() }
        advanceUntilIdle()
        assertFalse(memberVm.uiState.value.isOwnerCandidate)
        job.cancel()
    }

    @Test
    fun ownerAdminViewModel_moderatesActivityReportsAndManagesVerificationAndSuspension() = runTest {
        val activityReport = ActivityReportRecord(
            reportId = "act_rep_1",
            reporterId = "uid_alice",
            postId = "post_9",
            authorId = "uid_bob",
            postExcerpt = "Inappropriate activity caption",
            reason = "Harassment",
            status = "PENDING"
        )
        val member = UserProfile(
            userId = "uid_bob",
            displayName = "Bob",
            age = 29,
            isVerifiedMember = false,
            verificationStatus = "PENDING",
            accountStatus = "ACTIVE"
        )
        val authorizedSource = FakeOwnerAdminDataSource(
            authStatus = OwnerDashboardAggregator.evaluateOwnerClaims(
                email = "gptgamestudioph@gmail.com",
                emailVerified = true,
                claims = mapOf("owner" to true, "admin" to true)
            ),
            activityReports = listOf(activityReport),
            memberDirectory = listOf(member)
        )
        val vm = OwnerAdminViewModel(app, authorizedSource)
        advanceUntilIdle()

        vm.updateActivityReportStatus("act_rep_1", "DISMISSED")
        advanceUntilIdle()
        assertEquals(listOf("act_rep_1" to "DISMISSED"), authorizedSource.updatedActivityStatuses)
        assertEquals("DISMISSED", vm.uiState.value.snapshot.activityReports.first().status)

        vm.toggleMemberVerification("uid_bob", true)
        advanceUntilIdle()
        assertEquals(listOf("uid_bob" to true), authorizedSource.verifiedChanges)
        assertTrue(vm.uiState.value.snapshot.memberDirectory.first().isVerifiedMember)
        assertEquals("VERIFIED", vm.uiState.value.snapshot.memberDirectory.first().verificationStatus)

        vm.toggleMemberSuspension("uid_bob", true)
        advanceUntilIdle()
        assertEquals(listOf("uid_bob" to true), authorizedSource.suspensionChanges)
        assertEquals("SUSPENDED", vm.uiState.value.snapshot.memberDirectory.first().accountStatus)

        // Revoke authorization mid-session -> admin mutations rejected safely
        authorizedSource.authStatus = OwnerDashboardAggregator.evaluateOwnerClaims(
            email = "member@gmail.com",
            emailVerified = true,
            claims = emptyMap()
        )
        vm.toggleMemberVerification("uid_bob", false)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.snapshot.memberDirectory.first().isVerifiedMember)
    }
}
