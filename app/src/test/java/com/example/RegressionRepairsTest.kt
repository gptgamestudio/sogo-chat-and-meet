package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.call.CallPhase
import com.example.call.CallViewModel
import com.example.data.matching.MatchRecommender
import com.example.data.model.ActivityComment
import com.example.data.model.ActivityPost
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
import com.example.ui.common.MemberPresenceFormatter
import com.example.ui.dashboard.MainDashboardViewModel
import com.example.ui.profile.BirthdayValidator
import com.example.ui.profile.ProfilePhotoHelper
import com.google.firebase.Timestamp
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
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
        val myBlockedIdsFlow = MutableStateFlow<Set<String>>(emptySet())
        val messagesByConversation = mutableMapOf<String, MutableStateFlow<List<ChatMessage>>>()
        val sentMessages = mutableListOf<ChatMessage>()
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
        val updatedPhotoUrls = mutableListOf<String>()
        val reportedChatContents = mutableListOf<Triple<String, ChatMessage, String>>()
        val conversationsById = mutableMapOf<String, MutableStateFlow<Conversation?>>()
        val typingWrites = mutableListOf<Pair<String, Boolean>>()
        val activityPostsFlow = MutableStateFlow<List<ActivityPost>>(emptyList())
        val activityCommentsByPost = mutableMapOf<String, MutableStateFlow<List<ActivityComment>>>()
        var deletedAccountCallCount = 0
        var profilePreserved = true
        var refreshMembersCallCount = 0
        var loadMoreMembersCallCount = 0
        var boostMyProfileCallCount = 0
        var refreshActivityPostsCallCount = 0

        override fun getCurrentUserId(): String? = currentUid
        override fun requireUserId(): String = currentUid ?: throw IllegalStateException("Not signed in")

        override fun observeCurrentUserProfile(userId: String): Flow<UserProfile?> =
            profileObserverError?.let { err ->
                kotlinx.coroutines.flow.flow { throw err }
            } ?: currentUserProfileFlow

        override fun observeMembers(): Flow<List<UserProfile>> = membersFlow

        override suspend fun refreshMembers(force: Boolean) {
            refreshMembersCallCount++
        }

        override suspend fun loadMoreMembers(): Boolean {
            loadMoreMembersCallCount++
            return false
        }

        override suspend fun boostMyProfile() {
            boostMyProfileCallCount++
        }

        override suspend fun refreshActivityPosts(force: Boolean) {
            refreshActivityPostsCallCount++
        }

        override suspend fun saveUserProfile(profile: UserProfile) {
            saveProfileError?.let { throw it }
            savedProfiles += profile
            currentUserProfileFlow.value = profile
        }

        override suspend fun updateProfilePhoto(photoUrl: String) {
            val safe = photoUrl.trim().takeIf { it.isNotBlank() && it.length <= 65000 }
                ?: throw IllegalArgumentException("Invalid photo URI")
            updatedPhotoUrls += safe
            currentUserProfileFlow.value = currentUserProfileFlow.value?.copy(photoUrl = safe)
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

        override fun observeConversation(conversationId: String): Flow<Conversation?> =
            conversationsById.getOrPut(conversationId) { MutableStateFlow(null) }

        override suspend fun setTypingStatus(conversationId: String, isTyping: Boolean) {
            typingWrites += conversationId to isTyping
        }

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
        ) {
            val uid = requireUserId()
            sentMessages += ChatMessage(
                messageId = "sent_${sentMessages.size + 1}",
                conversationId = conversationId,
                senderId = uid,
                receiverId = receiverId,
                content = content,
                type = type,
                mediaUrl = mediaUrl
            )
        }

        override suspend fun markMessageAsRead(conversationId: String, messageId: String) {
            markReadCalls += conversationId to messageId
            markedMessageIdsOnServer += messageId
        }

        override suspend fun blockUser(blockedId: String) {
            blockedIdsFlow.value = blockedIdsFlow.value + blockedId
            myBlockedIdsFlow.value = myBlockedIdsFlow.value + blockedId
            val reqId = getFriendRequestIdForUsers(blockedId)
            friendRequestsFlow.value = friendRequestsFlow.value.filterNot { it.requestId == reqId }
        }

        override suspend fun unblockUser(blockedId: String) {
            myBlockedIdsFlow.value = myBlockedIdsFlow.value - blockedId
            blockedIdsFlow.value = blockedIdsFlow.value - blockedId
        }

        override fun observeBlockedUserIds(): Flow<Set<String>> = blockedIdsFlow

        override fun observeMyBlockedUserIds(): Flow<Set<String>> = myBlockedIdsFlow

        override suspend fun reportUser(reportedId: String, reason: String, details: String?) = Unit

        override suspend fun reportChatContent(
            conversationId: String,
            message: ChatMessage,
            reason: String,
            details: String?
        ) {
            val uid = requireUserId()
            val reportedUid = message.senderId.ifBlank { uid }
            if (reportedUid == uid) {
                throw IllegalArgumentException("You can only report content sent by another member.")
            }
            reportedChatContents += Triple(conversationId, message, reason)
        }

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

        override fun observeActivityPosts(): Flow<List<ActivityPost>> = activityPostsFlow

        override suspend fun createActivityPost(
            authorProfile: UserProfile?,
            content: String,
            moodEmoji: String,
            activityTag: String,
            imageUrl: String?
        ): String {
            val uid = requireUserId()
            val postId = "post_${activityPostsFlow.value.size + 1}"
            val post = ActivityPost(
                id = postId,
                postId = postId,
                userId = uid,
                authorId = uid,
                username = authorProfile?.username ?: authorProfile?.displayName ?: "Member",
                authorName = authorProfile?.displayName ?: "Member",
                authorUsername = authorProfile?.username,
                activityType = activityTag,
                activityTag = activityTag,
                moodEmoji = moodEmoji,
                content = content,
                imageUrl = imageUrl,
                likes = emptyList(),
                commentIds = emptyList(),
                timestamp = Timestamp.now(),
                createdAt = Timestamp.now()
            )
            activityPostsFlow.value = listOf(post) + activityPostsFlow.value
            return postId
        }

        override suspend fun toggleActivityLike(postId: String) {
            val uid = requireUserId()
            activityPostsFlow.value = activityPostsFlow.value.map { post ->
                if (post.resolvedPostId == postId) {
                    val nextLikes = if (post.likes.contains(uid)) post.likes - uid else post.likes + uid
                    post.copy(likes = nextLikes)
                } else {
                    post
                }
            }
        }

        override fun observeActivityComments(postId: String): Flow<List<ActivityComment>> =
            activityCommentsByPost.getOrPut(postId) { MutableStateFlow(emptyList()) }

        override suspend fun addActivityComment(postId: String, authorProfile: UserProfile?, content: String) {
            val uid = requireUserId()
            val listFlow = activityCommentsByPost.getOrPut(postId) { MutableStateFlow(emptyList()) }
            val commentId = "comment_${listFlow.value.size + 1}"
            val comment = ActivityComment(
                id = commentId,
                commentId = commentId,
                postId = postId,
                authorId = uid,
                authorName = authorProfile?.displayName ?: "Member",
                content = content,
                createdAt = Timestamp.now()
            )
            listFlow.value = listFlow.value + comment
            activityPostsFlow.value = activityPostsFlow.value.map { post ->
                if (post.resolvedPostId == postId) {
                    post.copy(
                        commentIds = post.commentIds + commentId,
                        commentCount = post.commentCount + 1L
                    )
                } else {
                    post
                }
            }
        }

        override suspend fun deleteActivityComment(postId: String, commentId: String) {
            val listFlow = activityCommentsByPost.getOrPut(postId) { MutableStateFlow(emptyList()) }
            listFlow.value = listFlow.value.filterNot { it.commentId == commentId }
            activityPostsFlow.value = activityPostsFlow.value.map { post ->
                if (post.resolvedPostId == postId) {
                    post.copy(
                        commentIds = post.commentIds.filterNot { it == commentId },
                        commentCount = (post.commentCount - 1L).coerceAtLeast(0L)
                    )
                } else {
                    post
                }
            }
        }

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

    @Test
    fun memberPresenceFormatter_formatsOnlineAndLastSeenTimestamps() {
        val tz = TimeZone.getTimeZone("Asia/Manila")
        val nowCal = Calendar.getInstance(tz, Locale.US).apply {
            set(2026, Calendar.NOVEMBER, 22, 15, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nowMs = nowCal.timeInMillis

        val onlineMember = UserProfile(
            userId = "uid_online",
            displayName = "Alice",
            isOnline = true,
            lastActive = Timestamp(Date(nowMs - 10 * 60_000L))
        )
        assertEquals("Online", MemberPresenceFormatter.formatMemberPresenceLabel(onlineMember, nowMs, Locale.US, tz))

        val justNowOffline = UserProfile(
            userId = "uid_just_now",
            displayName = "Bea",
            isOnline = false,
            lastActive = Timestamp(Date(nowMs - 30_000L))
        )
        assertEquals("Last seen just now", MemberPresenceFormatter.formatMemberPresenceLabel(justNowOffline, nowMs, Locale.US, tz))

        val minsOffline = UserProfile(
            userId = "uid_mins",
            displayName = "Carl",
            isOnline = false,
            lastActive = Timestamp(Date(nowMs - 15 * 60_000L))
        )
        assertEquals("Last seen 15m ago", MemberPresenceFormatter.formatMemberPresenceLabel(minsOffline, nowMs, Locale.US, tz))

        val hoursOffline = UserProfile(
            userId = "uid_hours",
            displayName = "Dana",
            isOnline = false,
            lastActive = Timestamp(Date(nowMs - 3 * 3_600_000L))
        )
        assertEquals("Last seen 3h ago", MemberPresenceFormatter.formatMemberPresenceLabel(hoursOffline, nowMs, Locale.US, tz))

        val yesterdayMs = Calendar.getInstance(tz, Locale.US).apply {
            set(2026, Calendar.NOVEMBER, 21, 9, 5, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yesterdayOffline = UserProfile(
            userId = "uid_yesterday",
            displayName = "Eli",
            isOnline = false,
            lastActive = Timestamp(Date(yesterdayMs))
        )
        assertEquals("Last seen Yesterday, 9:05 AM", MemberPresenceFormatter.formatMemberPresenceLabel(yesterdayOffline, nowMs, Locale.US, tz))

        val olderSameYearMs = Calendar.getInstance(tz, Locale.US).apply {
            set(2026, Calendar.OCTOBER, 14, 20, 15, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val fallbackUpdatedMember = UserProfile(
            userId = "uid_fallback",
            displayName = "Faye",
            isOnline = false,
            lastActive = null,
            updatedAt = Timestamp(Date(olderSameYearMs))
        )
        assertEquals("Last seen Oct 14, 8:15 PM", MemberPresenceFormatter.formatMemberPresenceLabel(fallbackUpdatedMember, nowMs, Locale.US, tz))

        val noTimestampMember = UserProfile(
            userId = "uid_none",
            displayName = "Gio",
            isOnline = false,
            lastActive = null,
            updatedAt = null
        )
        assertEquals("Last seen recently", MemberPresenceFormatter.formatMemberPresenceLabel(noTimestampMember, nowMs, Locale.US, tz))
    }

    @Test
    fun updateProfilePhoto_savesValidImageUriToFirestoreAndRejectsOversizedUri() = runTest {
        val fake = FakeSogoDataSource(currentUid = "uid_me")
        val existing = UserProfile(
            userId = "uid_me",
            displayName = "Leann",
            birthday = "1999-05-15",
            age = 27,
            photoUrl = null
        )
        fake.currentUserProfileFlow.value = existing

        val vm = MainDashboardViewModel(app, fake)
        val collectJob = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        val validDataUri = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD"
        var succeeded = false
        vm.updateProfilePhoto(validDataUri, onSuccess = { succeeded = true })
        advanceUntilIdle()

        assertTrue(succeeded)
        assertEquals(listOf(validDataUri), fake.updatedPhotoUrls)
        assertEquals(validDataUri, vm.uiState.value.currentUserProfile?.photoUrl)
        assertEquals("Profile picture saved to your profile.", vm.uiState.value.photoStatusMessage)
        assertNull(vm.uiState.value.errorMessage)

        // Oversized (>65000 chars) URI must be rejected cleanly before Firestore write
        val oversized = "data:image/jpeg;base64," + "A".repeat(66000)
        var errorReceived: String? = null
        vm.updateProfilePhoto(oversized, onError = { errorReceived = it })
        advanceUntilIdle()

        assertNotNull(errorReceived)
        assertEquals(1, fake.updatedPhotoUrls.size)
        assertEquals(validDataUri, vm.uiState.value.currentUserProfile?.photoUrl)

        collectJob.cancel()
    }

    @Test
    fun profilePhotoHelper_createsFileProviderCameraUriAndEncodesCompactJpegDataUri() {
        val cameraUri = ProfilePhotoHelper.createCameraOutputUri(app)
        assertEquals("content", cameraUri.scheme)
        assertTrue(cameraUri.authority?.endsWith(".fileprovider") == true)
        assertTrue(cameraUri.toString().contains("profile_photos"))

        val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(158, 27, 50))
        }
        val baos = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        bitmap.recycle()

        val encoded = ProfilePhotoHelper.encodeBytesToCompactDataUri(baos.toByteArray())
        assertNotNull(encoded)
        assertTrue(encoded!!.startsWith("data:image/jpeg;base64,"))
        assertTrue(encoded.length <= ProfilePhotoHelper.MAX_FIRESTORE_PHOTO_URI_LENGTH)
    }

    @Test
    fun blockAndUnblockUser_updatesMemberDiscoveryOnlineStripAndFriendRequests() = runTest {
        val fake = FakeSogoDataSource(currentUid = "uid_me")
        val alice = UserProfile(userId = "uid_alice", displayName = "Alice", age = 24, isOnline = true)
        val bob = UserProfile(userId = "uid_bob", displayName = "Bob", age = 27, isOnline = true)
        fake.membersFlow.value = listOf(alice, bob)
        fake.friendRequestsFlow.value = listOf(
            FriendRequest(
                requestId = fake.getFriendRequestIdForUsers("uid_alice"),
                requesterId = "uid_alice",
                recipientId = "uid_me",
                participantUids = listOf("uid_alice", "uid_me"),
                status = "PENDING"
            )
        )

        val vm = MainDashboardViewModel(app, fake)
        val collectJob = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        assertEquals(listOf("uid_alice", "uid_bob"), vm.uiState.value.filteredMembers.map { it.userId })
        assertEquals(listOf("uid_alice", "uid_bob"), vm.uiState.value.onlineMembers.map { it.userId })
        assertEquals(1, vm.uiState.value.incomingFriendRequests.size)
        assertTrue(vm.uiState.value.blockedMembers.isEmpty())

        // Block Alice from dashboard
        vm.blockUser("uid_alice", "Alice")
        advanceUntilIdle()

        assertEquals(listOf("uid_bob"), vm.uiState.value.members.map { it.userId })
        assertEquals(listOf("uid_bob"), vm.uiState.value.filteredMembers.map { it.userId })
        assertEquals(listOf("uid_bob"), vm.uiState.value.onlineMembers.map { it.userId })
        assertTrue(vm.uiState.value.incomingFriendRequests.isEmpty())
        assertEquals(listOf("uid_alice"), vm.uiState.value.blockedMembers.map { it.userId })
        assertEquals("Alice was blocked and removed from discovery and chat.", vm.uiState.value.blockStatusMessage)

        // Unblock Alice -> restored to discovery
        vm.unblockUser("uid_alice", "Alice")
        advanceUntilIdle()

        assertEquals(listOf("uid_alice", "uid_bob"), vm.uiState.value.filteredMembers.map { it.userId })
        assertTrue(vm.uiState.value.blockedMembers.isEmpty())
        assertEquals("Alice was unblocked.", vm.uiState.value.blockStatusMessage)

        collectJob.cancel()
    }

    @Test
    fun chatViewModel_restrictsIncomingMessagesReadReceiptsAndSendingWhenBlocked() = runTest {
        val fake = FakeSogoDataSource(currentUid = "uid_me")
        val chatVm = ChatViewModel(app, fake)
        val partner = UserProfile(userId = "uid_alice", displayName = "Alice", age = 24)
        val convId = fake.getConversationIdForUsers(partner.userId)

        val incomingMsg = ChatMessage(
            messageId = "msg_incoming_1",
            conversationId = convId,
            senderId = partner.userId,
            receiverId = "uid_me",
            content = "Hey there!",
            isRead = false
        )
        fake.messagesByConversation[convId] = MutableStateFlow(listOf(incomingMsg))

        // Open chat while unblocked -> sees message and marks read
        chatVm.initializeChat(partner)
        advanceUntilIdle()
        assertFalse(chatVm.uiState.value.isBlocked)
        assertEquals(listOf("msg_incoming_1"), chatVm.uiState.value.messages.map { it.messageId })
        assertEquals(1, fake.markReadCalls.size)

        // Block happens dynamically (either user or partner blocks)
        chatVm.blockCurrentMember()
        advanceUntilIdle()

        assertTrue(chatVm.uiState.value.isBlocked)
        assertTrue(chatVm.uiState.value.isBlockedByMe)
        assertTrue(chatVm.uiState.value.messages.isEmpty())
        assertTrue(fake.activeMessageListeners.isEmpty())

        // Even if partner somehow pushes a new incoming message in backend stream, it must NOT surface or trigger read receipt
        fake.messagesByConversation[convId]!!.value = listOf(
            incomingMsg,
            incomingMsg.copy(messageId = "msg_incoming_2", content = "Blocked follow-up")
        )
        advanceUntilIdle()
        assertTrue(chatVm.uiState.value.messages.isEmpty())
        assertEquals(1, fake.markReadCalls.size)

        // Outgoing text & stickers are restricted while blocked
        chatVm.onInputTextChanged("Trying to reply")
        chatVm.sendTextMessage()
        chatVm.sendSticker("👋 Kumusta!")
        advanceUntilIdle()
        assertTrue(fake.sentMessages.isEmpty())
        assertNotNull(chatVm.uiState.value.errorMessage)
    }

    @Test
    fun reportMessageContent_storesModerationFlagAndRejectsSelfMessages() = runTest {
        val fake = FakeSogoDataSource(currentUid = "uid_me")
        val chatVm = ChatViewModel(app, fake)
        val partner = UserProfile(userId = "uid_alice", displayName = "Alice", age = 24)
        val convId = fake.getConversationIdForUsers(partner.userId)
        val partnerMessage = ChatMessage(
            messageId = "msg_flag_1",
            conversationId = convId,
            senderId = partner.userId,
            receiverId = "uid_me",
            content = "Suspicious promotional spam link",
            type = "TEXT",
            isRead = true
        )
        fake.messagesByConversation[convId] = MutableStateFlow(listOf(partnerMessage))

        chatVm.initializeChat(partner)
        advanceUntilIdle()
        assertFalse(chatVm.uiState.value.contentReportSubmitted)

        chatVm.reportMessageContent(partnerMessage, "Spam or scam", "Sending external links")
        advanceUntilIdle()
        assertTrue(chatVm.uiState.value.contentReportSubmitted)
        assertEquals(1, fake.reportedChatContents.size)
        assertEquals(convId, fake.reportedChatContents[0].first)
        assertEquals("msg_flag_1", fake.reportedChatContents[0].second.messageId)
        assertEquals("Spam or scam", fake.reportedChatContents[0].third)

        chatVm.clearContentReportSubmitted()
        advanceUntilIdle()
        assertFalse(chatVm.uiState.value.contentReportSubmitted)

        // Reporting own message must be rejected cleanly
        val myMessage = ChatMessage(
            messageId = "msg_mine",
            conversationId = convId,
            senderId = "uid_me",
            receiverId = partner.userId,
            content = "Hello",
            type = "TEXT"
        )
        chatVm.reportMessageContent(myMessage, "Other", "")
        advanceUntilIdle()
        assertEquals(1, fake.reportedChatContents.size)
        assertNotNull(chatVm.uiState.value.errorMessage)
    }

    @Test
    fun activityPost_likesCommentsAndCategoryFilterUpdateStateInRealTime() = runTest {
        val fake = FakeSogoDataSource(currentUid = "uid_me")
        fake.currentUserProfileFlow.value = UserProfile(
            userId = "uid_me",
            username = "leann_pro",
            displayName = "Leann",
            age = 25
        )
        val vm = MainDashboardViewModel(app, fake)
        val collectJob = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        var created = false
        vm.createActivityPost(
            content = "Morning espresso in Makati",
            moodEmoji = "☕",
            activityTag = "Coffee Time",
            imageUrl = null,
            onSuccess = { created = true }
        )
        advanceUntilIdle()
        assertTrue(created)
        assertEquals(1, vm.uiState.value.activityPosts.size)

        val postId = vm.uiState.value.activityPosts.first().resolvedPostId
        assertEquals("Coffee Time", vm.uiState.value.activityPosts.first().resolvedActivityType)
        assertEquals("leann_pro", vm.uiState.value.activityPosts.first().resolvedUsername)

        // Toggle like adds & removes current user's UID in likes[]
        vm.toggleActivityLike(postId)
        advanceUntilIdle()
        assertEquals(listOf("uid_me"), vm.uiState.value.activityPosts.first().likes)

        vm.toggleActivityLike(postId)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.activityPosts.first().likes.isEmpty())

        // Comments open, add, and delete syncs commentIds
        vm.openCommentsForPost(postId)
        vm.addActivityComment(postId, "Save a cup for me!")
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.activePostComments.size)
        assertEquals(1, vm.uiState.value.activityPosts.first().commentIds.size)

        val commentId = vm.uiState.value.activePostComments.first().commentId
        vm.deleteActivityComment(postId, commentId)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.activePostComments.isEmpty())
        assertTrue(vm.uiState.value.activityPosts.first().commentIds.isEmpty())

        // Category filter
        vm.setActivityCategoryFilter("Gaming")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.filteredActivityPosts.isEmpty())

        vm.setActivityCategoryFilter("Coffee Time")
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.filteredActivityPosts.size)

        collectJob.cancel()
    }

    @Test
    fun matchRecommender_recommendsEligibleOnlineMembersAndExcludesRestrictedAccounts() {
        val nowMs = 1_795_400_000_000L
        val me = UserProfile(
            userId = "uid_me",
            displayName = "Leann",
            age = 26,
            gender = "Female",
            country = "Philippines",
            city = "Manila",
            interests = listOf("Coffee", "Travel", "Music"),
            preferredGender = "Male",
            preferredMinAge = 24,
            preferredMaxAge = 34,
            preferredCountry = "Philippines"
        )
        val bestMatch = UserProfile(
            userId = "uid_best",
            displayName = "Marco",
            age = 28,
            gender = "Male",
            country = "Philippines",
            city = "Manila",
            interests = listOf("Coffee", "Travel"),
            isOnline = true,
            lastActive = Timestamp(Date(nowMs - 30_000L))
        )
        val goodMatch = UserProfile(
            userId = "uid_good",
            displayName = "Ken",
            age = 27,
            gender = "Male",
            country = "Japan",
            interests = listOf("Gaming"),
            isOnline = true,
            lastActive = Timestamp(Date(nowMs - 60_000L))
        )
        val hiddenMember = bestMatch.copy(userId = "uid_hidden", hideFromDiscovery = true)
        val suspendedMember = bestMatch.copy(userId = "uid_suspended", accountStatus = "SUSPENDED")
        val blockedMember = bestMatch.copy(userId = "uid_blocked")
        val minorMember = bestMatch.copy(userId = "uid_minor", age = 17)
        val offlineMember = bestMatch.copy(userId = "uid_offline", isOnline = false)
        val hiddenStatusMember = bestMatch.copy(userId = "uid_stealth", hideOnlineStatus = true)

        val recommendations = MatchRecommender.recommendOnlineMembersYouMayLike(
            currentUser = me,
            allMembers = listOf(
                me,
                goodMatch,
                hiddenMember,
                suspendedMember,
                blockedMember,
                minorMember,
                offlineMember,
                hiddenStatusMember,
                bestMatch
            ),
            blockedUserIds = setOf("uid_blocked"),
            nowMs = nowMs
        )

        assertEquals(listOf("uid_best", "uid_good"), recommendations.map { it.profile.userId })
        assertEquals(listOf("Coffee", "Travel"), recommendations.first().mutualInterests)
        assertTrue(recommendations.first().matchScore > recommendations.last().matchScore)
    }

    @Test
    fun chatViewModel_tracksPartnerTypingAndValidatesImageMessages() = runTest {
        val fake = FakeSogoDataSource(currentUid = "uid_me")
        val chatVm = ChatViewModel(app, fake)
        val partner = UserProfile(userId = "uid_alice", displayName = "Alice", age = 24)
        val convId = fake.getConversationIdForUsers(partner.userId)
        fake.conversationsById[convId] = MutableStateFlow(Conversation(conversationId = convId))

        chatVm.initializeChat(partner)
        advanceUntilIdle()
        assertFalse(chatVm.uiState.value.isPartnerTyping)

        // Partner typing timestamp within TTL surfaces live indicator
        fake.conversationsById[convId]!!.value = Conversation(
            conversationId = convId,
            typingByUid = mapOf(partner.userId to Timestamp.now())
        )
        advanceUntilIdle()
        assertTrue(chatVm.uiState.value.isPartnerTyping)

        // Sending input writes typing indicator
        chatVm.onInputTextChanged("Hey Alice!")
        advanceUntilIdle()
        assertTrue(fake.typingWrites.any { it.first == convId && it.second })

        // Valid data URI photo sends as IMAGE message
        val validImage = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD"
        chatVm.sendImageMessage(validImage, "Coffee date?")
        advanceUntilIdle()
        assertEquals(1, fake.sentMessages.size)
        assertEquals("IMAGE", fake.sentMessages.first().type)
        assertEquals(validImage, fake.sentMessages.first().mediaUrl)
        assertEquals("Coffee date?", fake.sentMessages.first().content)

        // Oversized image URI rejected cleanly
        val oversized = "data:image/jpeg;base64," + "X".repeat(66000)
        chatVm.sendImageMessage(oversized)
        advanceUntilIdle()
        assertEquals(1, fake.sentMessages.size)
        assertNotNull(chatVm.uiState.value.errorMessage)
    }

    @Test
    fun dashboardScaleUpgrades_refreshLoadMoreAndProfileBoostWorkCorrectly() = runTest {
        val fake = FakeSogoDataSource(currentUid = "uid_me")
        fake.currentUserProfileFlow.value = UserProfile(
            userId = "uid_me",
            displayName = "Alex",
            age = 26,
            country = "Philippines"
        )
        fake.membersFlow.value = List(15) { idx ->
            UserProfile(userId = "uid_$idx", displayName = "Member $idx", age = 22 + idx)
        }

        val vm = MainDashboardViewModel(app, fake)
        val collectJob = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        vm.refreshDashboardFeed(force = true)
        advanceUntilIdle()
        assertEquals(1, fake.refreshMembersCallCount)
        assertEquals(1, fake.refreshActivityPostsCallCount)

        // INITIAL_MEMBERS_PAGE_SIZE = 20; 20 + 20 >= 15 triggers repository.loadMoreMembers()
        vm.loadMoreMembers()
        advanceUntilIdle()
        assertEquals(1, fake.loadMoreMembersCallCount)

        vm.boostProfile()
        advanceUntilIdle()
        assertEquals(1, fake.boostMyProfileCallCount)
        assertTrue(vm.uiState.value.actionStatusMessage?.contains("boosted", ignoreCase = true) == true)

        collectJob.cancel()
    }
}
