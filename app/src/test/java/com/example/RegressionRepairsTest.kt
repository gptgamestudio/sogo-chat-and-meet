package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.call.CallPhase
import com.example.call.CallViewModel
import com.example.data.model.CallSession
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.FriendRequest
import com.example.data.model.FriendStatus
import com.example.data.model.IceCandidateRecord
import com.example.data.model.NotificationRecord
import com.example.data.model.UserProfile
import com.example.data.repository.RecentLoginRequiredException
import com.example.data.repository.SogoDataSource
import com.example.ui.chat.ChatViewModel
import com.example.ui.dashboard.MainDashboardViewModel
import com.example.ui.profile.BirthdayValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RegressionRepairsTest {

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

    private class FakeSogoDataSource(
        var currentUid: String? = "uid_me"
    ) : SogoDataSource {
        val currentUserProfileFlow = MutableStateFlow<UserProfile?>(null)
        val membersFlow = MutableStateFlow<List<UserProfile>>(emptyList())
        val blockedIdsFlow = MutableStateFlow<Set<String>>(emptySet())
        val messagesByConversation = mutableMapOf<String, MutableStateFlow<List<ChatMessage>>>()
        val friendRequestsFlow = MutableStateFlow<List<FriendRequest>>(emptyList())
        val callSessionsFlow = MutableStateFlow<List<CallSession>>(emptyList())
        val candidatesByCall = mutableMapOf<String, MutableStateFlow<List<IceCandidateRecord>>>()
        val updatedCallStatuses = mutableListOf<Pair<String, String>>()

        var updatePresenceCallCount = 0
        val recordedPresenceWrites = mutableListOf<Boolean>()
        var lastWrittenPresence: Boolean? = null

        var activeMessageListeners = mutableSetOf<String>()
        var totalMessageListenersStarted = 0
        var totalMessageListenersCancelled = 0

        val markReadCalls = mutableListOf<Pair<String, String>>()
        val markedMessageIdsOnServer = mutableSetOf<String>()

        var profileObserverError: Exception? = null
        var saveProfileError: Exception? = null
        var deleteAccountError: Exception? = null
        var savedProfiles = mutableListOf<UserProfile>()
        var deletedAccountCallCount = 0
        var profilePreserved = true

        override fun getCurrentUserId(): String? = currentUid
        override fun requireUserId(): String = currentUid ?: throw IllegalStateException("Not signed in")

        override fun observeCurrentUserProfile(userId: String): Flow<UserProfile?> =
            profileObserverError?.let { err ->
                kotlinx.coroutines.flow.flow { throw err }
            } ?: currentUserProfileFlow

        override fun observeMembers(): Flow<List<UserProfile>> = membersFlow

        override suspend fun saveUserProfile(profile: UserProfile) {
            saveProfileError?.let { throw it }
            savedProfiles += profile
            currentUserProfileFlow.value = profile
        }

        override suspend fun updatePresence(isOnline: Boolean, force: Boolean) {
            if (!force && lastWrittenPresence == isOnline) return
            updatePresenceCallCount++
            recordedPresenceWrites += isOnline
            lastWrittenPresence = isOnline
        }

        override fun getConversationIdForUsers(otherUid: String): String {
            val uid = requireUserId()
            val sorted = listOf(uid, otherUid).sorted()
            return "conv_${sorted[0]}_${sorted[1]}"
        }

        override fun observeConversations(): Flow<List<Conversation>> = MutableStateFlow(emptyList())

        override suspend fun ensureConversationExists(otherUid: String): String =
            getConversationIdForUsers(otherUid)

        override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> {
            val backing = messagesByConversation.getOrPut(conversationId) {
                MutableStateFlow(emptyList())
            }
            return backing
                .onStart {
                    totalMessageListenersStarted++
                    activeMessageListeners.add(conversationId)
                }
                .onCompletion {
                    totalMessageListenersCancelled++
                    activeMessageListeners.remove(conversationId)
                }
        }

        override suspend fun sendMessage(
            conversationId: String,
            receiverId: String,
            content: String,
            type: String,
            mediaUrl: String?
        ) = Unit

        override suspend fun markMessageAsRead(conversationId: String, messageId: String) {
            markReadCalls += conversationId to messageId
            markedMessageIdsOnServer += messageId
        }

        override suspend fun blockUser(blockedId: String) {
            blockedIdsFlow.value = blockedIdsFlow.value + blockedId
        }

        override fun observeBlockedUserIds(): Flow<Set<String>> = blockedIdsFlow

        override suspend fun reportUser(reportedId: String, reason: String, details: String?) = Unit

        override fun observeNotifications(): Flow<List<NotificationRecord>> = MutableStateFlow(emptyList())

        override suspend fun markNotificationAsRead(notificationId: String) = Unit

        override fun getFriendRequestIdForUsers(otherUid: String): String {
            val uid = requireUserId()
            val sorted = listOf(uid, otherUid).sorted()
            return "fr_${sorted[0]}_${sorted[1]}"
        }

        override fun observeFriendRequests(): Flow<List<FriendRequest>> = friendRequestsFlow

        override suspend fun sendFriendRequest(targetUid: String) {
            val uid = requireUserId()
            val reqId = getFriendRequestIdForUsers(targetUid)
            val existing = friendRequestsFlow.value.firstOrNull { it.requestId == reqId }
            if (existing != null) {
                if (existing.status == "PENDING" && existing.recipientId == uid) {
                    acceptFriendRequest(reqId)
                }
                return
            }
            friendRequestsFlow.value = friendRequestsFlow.value + FriendRequest(
                requestId = reqId,
                requesterId = uid,
                recipientId = targetUid,
                participantUids = listOf(uid, targetUid).sorted(),
                status = "PENDING"
            )
        }

        override suspend fun acceptFriendRequest(requestId: String) {
            friendRequestsFlow.value = friendRequestsFlow.value.map {
                if (it.requestId == requestId) it.copy(status = "ACCEPTED") else it
            }
        }

        override suspend fun removeFriendOrRequest(otherUid: String) {
            val reqId = getFriendRequestIdForUsers(otherUid)
            friendRequestsFlow.value = friendRequestsFlow.value.filterNot { it.requestId == reqId }
        }

        override fun observeCallSessions(): Flow<List<CallSession>> = callSessionsFlow

        override suspend fun createCallSession(
            callee: UserProfile,
            callerName: String,
            callerPhotoUrl: String?,
            callType: String,
            offerSdp: String
        ): String {
            val uid = requireUserId()
            val callId = "call_fake_${callSessionsFlow.value.size + 1}"
            val session = CallSession(
                callId = callId,
                callerId = uid,
                callerName = callerName,
                callerPhotoUrl = callerPhotoUrl,
                calleeId = callee.userId,
                calleeName = callee.displayName,
                calleePhotoUrl = callee.photoUrl,
                participantUids = listOf(uid, callee.userId).sorted(),
                callType = callType,
                status = "RINGING",
                offerSdp = offerSdp
            )
            callSessionsFlow.value = callSessionsFlow.value + session
            return callId
        }

        override suspend fun answerCallSession(callId: String, answerSdp: String) {
            callSessionsFlow.value = callSessionsFlow.value.map {
                if (it.callId == callId) it.copy(status = "ANSWERED", answerSdp = answerSdp) else it
            }
        }

        override suspend fun updateCallStatus(callId: String, status: String) {
            updatedCallStatuses += callId to status
            callSessionsFlow.value = callSessionsFlow.value.map {
                if (it.callId == callId) it.copy(status = status) else it
            }
        }

        override suspend fun addIceCandidate(
            callId: String,
            participantUids: List<String>,
            sdpMid: String,
            sdpMLineIndex: Int,
            candidate: String
        ) = Unit

        override fun observeIceCandidates(callId: String): Flow<List<IceCandidateRecord>> =
            candidatesByCall.getOrPut(callId) { MutableStateFlow(emptyList()) }

        override suspend fun deleteUserAccount(googleIdToken: String?) {
            deleteAccountError?.let { throw it }
            deletedAccountCallCount++
            profilePreserved = false
            currentUserProfileFlow.value = null
        }
    }

    @Test
    fun presenceUpdates_doNotCreateInfiniteLoopOnProfileEmissions() = runTest {
        val fake = FakeSogoDataSource()
        val initialProfile = UserProfile(
            userId = "uid_me",
            displayName = "Mate",
            birthday = "1998-04-12",
            age = 27,
            isOnline = false
        )
        fake.currentUserProfileFlow.value = initialProfile

        val vm = MainDashboardViewModel(app, fake)
        val collectJob = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        // Observing profile updates multiple times must NOT write presence
        repeat(5) { idx ->
            fake.currentUserProfileFlow.value = initialProfile.copy(displayName = "Mate $idx")
            advanceUntilIdle()
        }
        assertEquals(0, fake.updatePresenceCallCount)

        // Explicit lifecycle setPresence writes once, and identical repeats deduplicate
        vm.setPresence(true)
        vm.setPresence(true)
        vm.setPresence(true)
        advanceUntilIdle()
        assertEquals(1, fake.updatePresenceCallCount)
        assertEquals(listOf(true), fake.recordedPresenceWrites)

        vm.setPresence(false)
        vm.setPresence(false)
        advanceUntilIdle()
        assertEquals(2, fake.updatePresenceCallCount)
        assertEquals(listOf(true, false), fake.recordedPresenceWrites)

        collectJob.cancel()
    }

    @Test
    fun switchingConversations_cancelsPreviousListenerAndPreventsMessageLeak() = runTest {
        val fake = FakeSogoDataSource(currentUid = "uid_me")
        val vm = ChatViewModel(app, fake)

        val partnerA = UserProfile(userId = "uid_alice", displayName = "Alice", age = 24)
        val partnerB = UserProfile(userId = "uid_bob", displayName = "Bob", age = 26)
        val convA = fake.getConversationIdForUsers(partnerA.userId)
        val convB = fake.getConversationIdForUsers(partnerB.userId)

        fake.messagesByConversation[convA] = MutableStateFlow(
            listOf(
                ChatMessage(
                    messageId = "msg_a1",
                    conversationId = convA,
                    senderId = partnerA.userId,
                    receiverId = "uid_me",
                    content = "Hello from Alice",
                    isRead = true
                )
            )
        )
        fake.messagesByConversation[convB] = MutableStateFlow(
            listOf(
                ChatMessage(
                    messageId = "msg_b1",
                    conversationId = convB,
                    senderId = partnerB.userId,
                    receiverId = "uid_me",
                    content = "Kumusta from Bob",
                    isRead = true
                )
            )
        )

        vm.initializeChat(partnerA)
        advanceUntilIdle()
        assertEquals(setOf(convA), fake.activeMessageListeners)
        assertEquals(listOf("Hello from Alice"), vm.uiState.value.messages.map { it.content })

        // Switch to Bob: must cancel convA listener immediately and replace messages with convB
        vm.initializeChat(partnerB)
        advanceUntilIdle()
        assertEquals(setOf(convB), fake.activeMessageListeners)
        assertTrue(fake.totalMessageListenersCancelled >= 1)
        assertEquals(listOf("Kumusta from Bob"), vm.uiState.value.messages.map { it.content })

        // Late emission in old convA must NOT appear in Bob's chat state
        fake.messagesByConversation[convA]!!.value = listOf(
            ChatMessage(
                messageId = "msg_a2",
                conversationId = convA,
                senderId = partnerA.userId,
                receiverId = "uid_me",
                content = "Stale leaked message",
                isRead = true
            )
        )
        advanceUntilIdle()
        assertEquals(listOf("Kumusta from Bob"), vm.uiState.value.messages.map { it.content })

        // Clearing active chat cancels remaining listener and resets state
        vm.clearActiveChat()
        advanceUntilIdle()
        assertTrue(fake.activeMessageListeners.isEmpty())
        assertTrue(vm.uiState.value.messages.isEmpty())
    }

    @Test
    fun alreadyReadMessages_andRepeatedSnapshots_doNotRepeatedlyWriteReadStatus() = runTest {
        val fake = FakeSogoDataSource(currentUid = "uid_me")
        val vm = ChatViewModel(app, fake)
        val partner = UserProfile(userId = "uid_alice", displayName = "Alice", age = 24)
        val convId = fake.getConversationIdForUsers(partner.userId)

        val unreadMsg = ChatMessage(
            messageId = "msg_unread_1",
            conversationId = convId,
            senderId = partner.userId,
            receiverId = "uid_me",
            content = "First unread",
            isRead = false
        )
        val alreadyReadMsg = ChatMessage(
            messageId = "msg_read_2",
            conversationId = convId,
            senderId = partner.userId,
            receiverId = "uid_me",
            content = "Already read",
            isRead = true
        )
        val myOwnMsg = ChatMessage(
            messageId = "msg_mine_3",
            conversationId = convId,
            senderId = "uid_me",
            receiverId = partner.userId,
            content = "Sent by me",
            isRead = false
        )

        val stream = MutableStateFlow(listOf(unreadMsg, alreadyReadMsg, myOwnMsg))
        fake.messagesByConversation[convId] = stream

        vm.initializeChat(partner)
        advanceUntilIdle()

        // First emission marks only the unread message from partner
        assertEquals(listOf(convId to "msg_unread_1"), fake.markReadCalls)

        // Second snapshot before isRead updates on server (still isRead=false) must be deduplicated
        stream.value = listOf(
            unreadMsg,
            alreadyReadMsg,
            myOwnMsg,
            ChatMessage(
                messageId = "msg_read_4",
                conversationId = convId,
                senderId = partner.userId,
                receiverId = "uid_me",
                content = "Another already-read",
                isRead = true
            )
        )
        advanceUntilIdle()

        assertEquals(listOf(convId to "msg_unread_1"), fake.markReadCalls)
    }

    @Test
    fun dashboardErrorsAndBlockedIds_updateReactiveUiState() = runTest {
        val fake = FakeSogoDataSource(currentUid = "uid_me")
        val alice = UserProfile(userId = "uid_alice", displayName = "Alice", age = 25, isOnline = true)
        val bob = UserProfile(userId = "uid_bob", displayName = "Bob", age = 28, isOnline = true)
        fake.membersFlow.value = listOf(alice, bob)

        val vm = MainDashboardViewModel(app, fake)
        val collectJob = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.filteredMembers.size)
        assertNull(vm.uiState.value.errorMessage)

        // Reactive blocked user update filters out Alice without touching other flows
        fake.blockedIdsFlow.value = setOf("uid_alice")
        advanceUntilIdle()
        assertEquals(setOf("uid_alice"), vm.uiState.value.blockedUserIds)
        assertEquals(listOf("uid_bob"), vm.uiState.value.filteredMembers.map { it.userId })

        // Reactive error propagation & dismissal
        vm.reportError("Network unavailable while syncing")
        advanceUntilIdle()
        assertEquals("Network unavailable while syncing", vm.uiState.value.errorMessage)

        vm.clearError()
        advanceUntilIdle()
        assertNull(vm.uiState.value.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun failedProfileSubmission_setsErrorAndResetsLoadingSpinner() = runTest {
        val fake = FakeSogoDataSource(currentUid = "uid_me")
        fake.saveProfileError = IllegalStateException("Firestore write rejected")
        val vm = MainDashboardViewModel(app, fake)
        val collectJob = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        var successCalled = false
        var errorCallbackMessage: String? = null
        vm.createOrUpdateProfile(
            displayName = "Juan",
            birthday = "1998-06-15",
            age = 27,
            gender = "Male",
            country = "Philippines",
            city = "Makati",
            photoUrl = null,
            aboutMe = "Coffee fan",
            personality = "Chill & Laidback",
            interests = listOf("Coffee & Kape"),
            onSuccess = { successCalled = true },
            onError = { errorCallbackMessage = it }
        )
        advanceUntilIdle()

        assertFalse(successCalled)
        assertFalse(vm.uiState.value.isSavingProfile)
        assertEquals("Firestore write rejected", vm.uiState.value.errorMessage)
        assertEquals("Firestore write rejected", errorCallbackMessage)

        collectJob.cancel()
    }

    @Test
    fun birthdayValidator_rejectsInvalidDatesAndMinors_acceptsLeapYearsAndAdults() {
        val refNow = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 10, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Invalid calendar dates
        assertFalse(BirthdayValidator.isValidCalendarDate(2000, 2, 30))
        assertFalse(BirthdayValidator.isValidCalendarDate(2001, 2, 29)) // non-leap year
        assertFalse(BirthdayValidator.isValidCalendarDate(1900, 2, 29)) // divisible by 100 not 400
        assertFalse(BirthdayValidator.isValidCalendarDate(1999, 4, 31)) // April has 30 days
        assertNull(BirthdayValidator.parseBirthday("2001-02-29"))
        assertNull(BirthdayValidator.parseBirthday("1999-04-31"))

        // Valid leap years
        assertTrue(BirthdayValidator.isValidCalendarDate(2000, 2, 29))
        assertTrue(BirthdayValidator.isValidCalendarDate(2004, 2, 29))
        assertEquals(Triple(2000, 2, 29), BirthdayValidator.parseBirthday("2000-02-29"))

        // Underage & future dates must be rejected
        assertFalse(BirthdayValidator.isEligibleAdult(2012, 1, 1, refNow))
        assertFalse(BirthdayValidator.isEligibleAdult(2008, 5, 11, refNow)) // turns 18 tomorrow
        assertNull(BirthdayValidator.calculateAge(2028, 1, 1, refNow)) // future date

        // Exact 18th birthday is allowed
        assertTrue(BirthdayValidator.isEligibleAdult(2008, 5, 10, refNow))
        assertEquals(18, BirthdayValidator.calculateAge(2008, 5, 10, refNow))
    }

    @Test
    fun accountDeletionFailure_preservesUserProfileAndFlagsReauthRequired() = runTest {
        val fake = FakeSogoDataSource(currentUid = "uid_me")
        val existing = UserProfile(
            userId = "uid_me",
            displayName = "Ben",
            birthday = "1992-08-20",
            age = 33
        )
        fake.currentUserProfileFlow.value = existing
        fake.deleteAccountError = RecentLoginRequiredException()

        val vm = MainDashboardViewModel(app, fake)
        val collectJob = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        var reauthTriggered = false
        var completedTriggered = false
        vm.deleteAccount(
            googleIdToken = null,
            onReauthRequired = { reauthTriggered = true },
            onCompleted = { completedTriggered = true }
        )
        advanceUntilIdle()

        assertTrue(reauthTriggered)
        assertFalse(completedTriggered)
        assertTrue(fake.profilePreserved)
        assertEquals(0, fake.deletedAccountCallCount)
        assertEquals(existing, vm.uiState.value.currentUserProfile)
        assertTrue(vm.uiState.value.requiresReauthForDeletion)
        assertFalse(vm.uiState.value.isDeletingAccount)
        assertNotNull(vm.uiState.value.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun friendRequests_updateStatusIncomingListOnlineMembersAndFriendsFilter() = runTest {
        val fake = FakeSogoDataSource(currentUid = "uid_me")
        val alice = UserProfile(userId = "uid_alice", displayName = "Alice", age = 24, isOnline = true)
        val bob = UserProfile(userId = "uid_bob", displayName = "Bob", age = 27, isOnline = false)
        fake.membersFlow.value = listOf(alice, bob)

        val vm = MainDashboardViewModel(app, fake)
        val collectJob = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        // Online strip contains only online non-blocked members
        assertEquals(listOf("uid_alice"), vm.uiState.value.onlineMembers.map { it.userId })

        // Alice sends incoming request to me
        fake.friendRequestsFlow.value = listOf(
            FriendRequest(
                requestId = fake.getFriendRequestIdForUsers("uid_alice"),
                requesterId = "uid_alice",
                recipientId = "uid_me",
                participantUids = listOf("uid_alice", "uid_me"),
                status = "PENDING"
            )
        )
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.incomingFriendRequests.size)
        assertEquals(FriendStatus.REQUEST_RECEIVED, vm.uiState.value.friendStatusByUserId["uid_alice"])

        // Accepting moves status to FRIENDS and clears incoming list
        vm.acceptFriendRequest("uid_alice")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.incomingFriendRequests.isEmpty())
        assertEquals(1, vm.uiState.value.friendsCount)
        assertEquals(FriendStatus.FRIENDS, vm.uiState.value.friendStatusByUserId["uid_alice"])

        // I send friend request to Bob
        vm.sendFriendRequest("uid_bob")
        advanceUntilIdle()
        assertEquals(FriendStatus.REQUEST_SENT, vm.uiState.value.friendStatusByUserId["uid_bob"])

        // Friends-only filter shows only Alice
        vm.toggleFriendsOnly()
        advanceUntilIdle()
        assertEquals(listOf("uid_alice"), vm.uiState.value.filteredMembers.map { it.userId })

        collectJob.cancel()
    }

    @Test
    fun incomingFreeVideoCall_ringsInCallViewModelAndDeclineResetsToIdle() = runTest {
        val fake = FakeSogoDataSource(currentUid = "uid_me")
        val callVm = CallViewModel(app, fake)

        assertEquals(CallPhase.IDLE, callVm.uiState.value.phase)

        fake.callSessionsFlow.value = listOf(
            CallSession(
                callId = "call_incoming_1",
                callerId = "uid_alice",
                callerName = "Alice",
                calleeId = "uid_me",
                calleeName = "Me",
                participantUids = listOf("uid_alice", "uid_me"),
                callType = "VIDEO",
                status = "RINGING",
                offerSdp = "v=0\r\n"
            )
        )
        advanceUntilIdle()

        assertEquals(CallPhase.RINGING_INCOMING, callVm.uiState.value.phase)
        assertEquals("Alice", callVm.uiState.value.peerName)
        assertEquals("VIDEO", callVm.uiState.value.callType)

        callVm.declineIncomingCall()
        advanceUntilIdle()

        assertEquals(CallPhase.IDLE, callVm.uiState.value.phase)
        assertTrue(fake.updatedCallStatuses.contains("call_incoming_1" to "DECLINED"))
    }

    @Test
    fun profileLoadFailure_setsProfileLoadErrorAndRetrySessionRecovers() = runTest {
        val fake = FakeSogoDataSource(currentUid = "uid_me")
        fake.profileObserverError = IllegalStateException("Transient Firestore error")

        val vm = MainDashboardViewModel(app, fake)
        val collectJob = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.hasResolvedProfile)
        assertEquals("Transient Firestore error", vm.uiState.value.profileLoadError)
        assertNull(vm.uiState.value.currentUserProfile)

        // Recover via retrySession once error clears
        val existing = UserProfile(userId = "uid_me", displayName = "Maria", age = 26)
        fake.profileObserverError = null
        fake.currentUserProfileFlow.value = existing
        vm.retrySession("uid_me")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.hasResolvedProfile)
        assertNull(vm.uiState.value.profileLoadError)
        assertEquals(existing, vm.uiState.value.currentUserProfile)

        collectJob.cancel()
    }

    @Test
    fun reportSubmission_setsAndClearsConfirmationFlag() = runTest {
        val fake = FakeSogoDataSource(currentUid = "uid_me")
        val chatVm = ChatViewModel(app, fake)
        val partner = UserProfile(userId = "uid_alice", displayName = "Alice", age = 24)

        chatVm.initializeChat(partner)
        advanceUntilIdle()
        assertFalse(chatVm.uiState.value.reportSubmitted)

        chatVm.reportCurrentMember("Fake profile or impersonation", "Details here")
        advanceUntilIdle()
        assertTrue(chatVm.uiState.value.reportSubmitted)

        chatVm.clearReportSubmitted()
        advanceUntilIdle()
        assertFalse(chatVm.uiState.value.reportSubmitted)
    }

    @Test
    fun onlineAndOfflineFilters_toggleMutuallyExclusively() = runTest {
        val fake = FakeSogoDataSource(currentUid = "uid_me")
        val onlineAlice = UserProfile(userId = "uid_alice", displayName = "Alice", age = 24, isOnline = true)
        val offlineBob = UserProfile(userId = "uid_bob", displayName = "Bob", age = 27, isOnline = false)
        fake.membersFlow.value = listOf(onlineAlice, offlineBob)

        val vm = MainDashboardViewModel(app, fake)
        val collectJob = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        // Both online and offline members appear by default
        assertEquals(listOf("uid_alice", "uid_bob"), vm.uiState.value.filteredMembers.map { it.userId })

        // Toggle Online Only
        vm.toggleOnlineOnly()
        advanceUntilIdle()
        assertEquals(listOf("uid_alice"), vm.uiState.value.filteredMembers.map { it.userId })

        // Toggle Offline Only clears Online Only automatically
        vm.toggleOfflineOnly()
        advanceUntilIdle()
        assertEquals(listOf("uid_bob"), vm.uiState.value.filteredMembers.map { it.userId })
        assertFalse(vm.uiState.value.filter.onlineOnly)
        assertTrue(vm.uiState.value.filter.offlineOnly)

        collectJob.cancel()
    }
}
