package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ActivityPost
import com.example.data.model.BlockRecord
import com.example.data.model.CallSession
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.FriendRequest
import com.example.data.model.IceCandidateRecord
import com.example.data.model.NotificationRecord
import com.example.data.model.UserProfile
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class RecentLoginRequiredException(
    message: String = "Please reauthenticate with Google to confirm account deletion."
) : Exception(message)

interface SogoDataSource {
    fun getCurrentUserId(): String?
    fun requireUserId(): String
    fun observeCurrentUserProfile(userId: String): Flow<UserProfile?>
    fun observeMembers(): Flow<List<UserProfile>>
    suspend fun saveUserProfile(profile: UserProfile)
    suspend fun updatePresence(isOnline: Boolean, force: Boolean = false)
    fun getConversationIdForUsers(otherUid: String): String
    fun observeConversations(): Flow<List<Conversation>>
    suspend fun ensureConversationExists(otherUid: String): String
    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>
    suspend fun sendMessage(
        conversationId: String,
        receiverId: String,
        content: String,
        type: String = "TEXT",
        mediaUrl: String? = null
    )
    suspend fun markMessageAsRead(conversationId: String, messageId: String)
    suspend fun blockUser(blockedId: String)
    fun observeBlockedUserIds(): Flow<Set<String>>
    suspend fun reportUser(reportedId: String, reason: String, details: String?)
    fun observeNotifications(): Flow<List<NotificationRecord>>
    suspend fun markNotificationAsRead(notificationId: String)

    // --- Friend Requests & Friends ---
    fun getFriendRequestIdForUsers(otherUid: String): String
    fun observeFriendRequests(): Flow<List<FriendRequest>>
    suspend fun sendFriendRequest(targetUid: String)
    suspend fun acceptFriendRequest(requestId: String)
    suspend fun removeFriendOrRequest(otherUid: String)

    // --- Free Audio & Video Call Signaling ---
    fun observeCallSessions(): Flow<List<CallSession>>
    suspend fun createCallSession(
        callee: UserProfile,
        callerName: String,
        callerPhotoUrl: String?,
        callType: String,
        offerSdp: String
    ): String
    suspend fun answerCallSession(callId: String, answerSdp: String)
    suspend fun updateCallStatus(callId: String, status: String)
    suspend fun addIceCandidate(
        callId: String,
        participantUids: List<String>,
        sdpMid: String,
        sdpMLineIndex: Int,
        candidate: String
    )
    fun observeIceCandidates(callId: String): Flow<List<IceCandidateRecord>>

    // --- Everyday Activities Community Feed & Emoji Reactions ---
    fun observeActivityPosts(): Flow<List<ActivityPost>> = kotlinx.coroutines.flow.emptyFlow()
    fun observeMyActivityReactions(): Flow<Map<String, String>> = kotlinx.coroutines.flow.emptyFlow()
    suspend fun createActivityPost(
        authorProfile: UserProfile?,
        content: String,
        moodEmoji: String,
        activityTag: String
    ): String = ""
    suspend fun toggleActivityReaction(postId: String, emoji: String) {}
    suspend fun deleteActivityPost(postId: String) {}
    suspend fun toggleChatMessageReaction(conversationId: String, messageId: String, emoji: String) {}

    suspend fun deleteUserAccount(googleIdToken: String? = null)
}

class SogoRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : SogoDataSource {
    constructor(context: Context) : this(
        getFirestoreInstance(context),
        Firebase.auth
    )

    private val presenceMutex = Mutex()
    private val lastCommittedPresenceByUid = ConcurrentHashMap<String, Boolean>()

    override fun getCurrentUserId(): String? = auth.currentUser?.uid

    override fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing SOGO CHAT AND MEET.")
    }

    // --- User Profile Operations ---

    override fun observeCurrentUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
        if (userId.isBlank()) {
            close(IllegalArgumentException("User ID cannot be blank."))
            return@callbackFlow
        }
        val path = "users/$userId"
        val registration = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(error, OperationType.GET, path)
                    }
                    close(error)
                    return@addSnapshotListener
                }
                val profile = snapshot?.toObject(UserProfile::class.java)
                trySend(profile)
            }
        awaitClose { registration.remove() }
    }

    override fun observeMembers(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        val path = "users"
        // Bounded real-time directory query keeps client memory and reads O(1) even at 50,000+ users
        val query = db.collection("users")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(MEMBERS_FEED_LIMIT)
        query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(UserProfile::class.java)
                    .filter { it.userId != uid }
                    .sortedWith(compareByDescending<UserProfile> { it.isOnline }.thenBy { it.displayName.lowercase() })
            }
            .catch { error ->
                if (error !is CancellationException && auth.currentUser != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    override suspend fun saveUserProfile(profile: UserProfile) {
        val uid = requireUserId()
        val safeDisplayName = profile.displayName.trim().ifBlank { "Member" }.take(50)
        val safeGender = profile.gender.trim().ifBlank { "Female" }.take(30)
        val safeCountry = profile.country.trim().ifBlank { "Philippines" }.take(60)
        val safeCity = profile.city?.trim()?.takeIf { it.isNotBlank() }?.take(60) ?: "Manila"
        val safeAboutMe = (profile.aboutMe ?: "").trim().take(1000)
        val safeBirthday = profile.birthday?.trim()?.takeIf { it.isNotBlank() }?.take(30)
        val safePersonality = profile.personality?.trim()?.takeIf { it.isNotBlank() }?.take(100)
        val safePhotoUrl = profile.photoUrl?.trim()?.takeIf { it.isNotBlank() && it.length <= 65000 }
        val safeInterests = profile.interests
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(25)

        val docRef = db.collection("users").document(uid)
        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "displayName" to safeDisplayName,
            "birthday" to safeBirthday,
            "age" to profile.age,
            "gender" to safeGender,
            "country" to safeCountry,
            "city" to safeCity,
            "aboutMe" to safeAboutMe,
            "personality" to safePersonality,
            "photoUrl" to safePhotoUrl,
            "interests" to safeInterests,
            "isOnline" to true,
            "role" to "member",
            "lastActive" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            } else {
                payload["createdAt"] = existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
            }
            docRef.set(payload).await()
            lastCommittedPresenceByUid[uid] = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    override suspend fun updatePresence(isOnline: Boolean, force: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        presenceMutex.withLock {
            if (!force && lastCommittedPresenceByUid[uid] == isOnline) {
                return
            }
            val userDoc = db.collection("users").document(uid)
            val presenceDoc = db.collection("user_presence").document(uid)
            try {
                val userSnapshot = userDoc.get().await()
                if (!userSnapshot.exists()) {
                    return
                }
                userDoc.update(
                    mapOf(
                        "isOnline" to isOnline,
                        "lastActive" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                presenceDoc.set(
                    mapOf(
                        "userId" to uid,
                        "isOnline" to isOnline,
                        "lastSeen" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                lastCommittedPresenceByUid[uid] = isOnline
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (auth.currentUser != null) {
                    handleFirestoreError(e, OperationType.UPDATE, presenceDoc.path)
                }
            }
        }
    }

    // --- Conversation & Messaging Operations ---

    override fun getConversationIdForUsers(otherUid: String): String {
        val uid = requireUserId()
        val pair = listOf(uid, otherUid).sorted()
        return "conv_${pair[0]}_${pair[1]}"
    }

    override fun observeConversations(): Flow<List<Conversation>> = flow {
        val uid = requireUserId()
        val path = "conversations"
        db.collection("conversations")
            .whereArrayContains("participantUids", uid)
            .limit(CONVERSATIONS_LIMIT)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(Conversation::class.java) }
            .catch { error ->
                if (error !is CancellationException && auth.currentUser != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    override suspend fun ensureConversationExists(otherUid: String): String {
        val uid = requireUserId()
        val convId = getConversationIdForUsers(otherUid)
        val docRef = db.collection("conversations").document(convId)
        try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val participants = listOf(uid, otherUid).sorted()
                val payload = mapOf(
                    "conversationId" to convId,
                    "participantUids" to participants,
                    "lastMessage" to "Started a conversation",
                    "lastMessageSenderId" to uid,
                    "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            return convId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        val path = "conversations/$conversationId/messages"
        db.collection("conversations").document(conversationId).collection("messages")
            .whereArrayContains("participantUids", uid)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(MESSAGES_LIMIT)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(ChatMessage::class.java) }
            .catch { error ->
                if (error !is CancellationException && auth.currentUser != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    override suspend fun sendMessage(
        conversationId: String,
        receiverId: String,
        content: String,
        type: String,
        mediaUrl: String?
    ) {
        val uid = requireUserId()
        val messageId = "msg_${UUID.randomUUID()}"
        val participants = listOf(uid, receiverId).sorted()
        val messageRef = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)

        val safeContent = content.trim().take(2000)
        val safeMediaUrl = mediaUrl?.trim()?.takeIf { it.isNotBlank() }?.take(1000)

        val messagePayload = mutableMapOf<String, Any?>(
            "messageId" to messageId,
            "conversationId" to conversationId,
            "senderId" to uid,
            "receiverId" to receiverId,
            "participantUids" to participants,
            "content" to safeContent,
            "type" to type,
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (safeMediaUrl != null) {
            messagePayload["mediaUrl"] = safeMediaUrl
        }

        val convRef = db.collection("conversations").document(conversationId)

        val snippet = when (type) {
            "IMAGE" -> "📷 Photo"
            "VOICE" -> "🎤 Voice message"
            "STICKER" -> "✨ Sticker"
            else -> safeContent
        }

        try {
            db.batch().apply {
                set(messageRef, messagePayload)
                update(
                    convRef,
                    mapOf(
                        "lastMessage" to snippet,
                        "lastMessageSenderId" to uid,
                        "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, messageRef.path)
            throw e
        }
    }

    override suspend fun markMessageAsRead(conversationId: String, messageId: String) {
        val uid = requireUserId()
        val docRef = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
        try {
            val snapshot = docRef.get().await()
            val message = snapshot.toObject(ChatMessage::class.java) ?: return
            if (message.receiverId != uid || message.isRead) {
                return
            }
            docRef.update("isRead", true).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val root = generateSequence(e as Throwable) { it.cause }
                .firstOrNull { it is FirebaseFirestoreException } as? FirebaseFirestoreException
            if (root?.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            }
        }
    }

    // --- Safety: Blocking & Reporting ---

    override suspend fun blockUser(blockedId: String) {
        val uid = requireUserId()
        val blockId = "block_${uid}_${blockedId}"
        val primaryDoc = db.collection("user_blocks").document(blockId)
        val aliasDoc = db.collection("blocks").document(blockId)
        val payload = mapOf(
            "blockId" to blockId,
            "blockerId" to uid,
            "blockedId" to blockedId,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            primaryDoc.set(payload).await()
            try { aliasDoc.set(payload).await() } catch (_: Exception) {}
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, primaryDoc.path)
            throw e
        }
    }

    override fun observeBlockedUserIds(): Flow<Set<String>> = callbackFlow {
        val uid = requireUserId()
        var myBlocks = emptySet<String>()
        var blockedBy = emptySet<String>()

        val sub1 = db.collection("user_blocks")
            .whereEqualTo("blockerId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (auth.currentUser != null) {
                        handleFirestoreError(error, OperationType.LIST, "user_blocks")
                        close(error)
                    } else {
                        close()
                    }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    myBlocks = snapshot.toObjects(BlockRecord::class.java).map { it.blockedId }.toSet()
                    trySend(myBlocks + blockedBy)
                }
            }

        val sub2 = db.collection("user_blocks")
            .whereEqualTo("blockedId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (auth.currentUser != null) {
                        handleFirestoreError(error, OperationType.LIST, "user_blocks")
                        close(error)
                    } else {
                        close()
                    }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    blockedBy = snapshot.toObjects(BlockRecord::class.java).map { it.blockerId }.toSet()
                    trySend(myBlocks + blockedBy)
                }
            }

        awaitClose {
            sub1.remove()
            sub2.remove()
        }
    }

    override suspend fun reportUser(reportedId: String, reason: String, details: String?) {
        val uid = requireUserId()
        val reportId = "report_${UUID.randomUUID()}"
        val primaryDoc = db.collection("user_reports").document(reportId)
        val aliasDoc = db.collection("reports").document(reportId)
        val payload = mutableMapOf<String, Any?>(
            "reportId" to reportId,
            "reporterId" to uid,
            "reportedId" to reportedId,
            "reason" to reason.trim().ifBlank { "Inappropriate behavior" }.take(100),
            "status" to "PENDING",
            "createdAt" to FieldValue.serverTimestamp()
        )
        val safeDetails = details?.trim()?.takeIf { it.isNotBlank() }?.take(1000)
        if (safeDetails != null) {
            payload["details"] = safeDetails
        }
        try {
            primaryDoc.set(payload).await()
            try {
                aliasDoc.set(payload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, primaryDoc.path)
            throw e
        }
    }

    // --- Private Notifications ---

    override fun observeNotifications(): Flow<List<NotificationRecord>> = flow {
        val uid = requireUserId()
        val path = "notifications"
        db.collection("notifications")
            .whereEqualTo("userId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(NOTIFICATIONS_LIMIT)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(NotificationRecord::class.java) }
            .catch { error ->
                if (error !is CancellationException && auth.currentUser != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    override suspend fun markNotificationAsRead(notificationId: String) {
        requireUserId()
        val docRef = db.collection("notifications").document(notificationId)
        try {
            docRef.update("isRead", true).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
        }
    }

    // --- Friend Requests & Friends ---

    override fun getFriendRequestIdForUsers(otherUid: String): String {
        val uid = requireUserId()
        val pair = listOf(uid, otherUid).sorted()
        return "fr_${pair[0]}_${pair[1]}"
    }

    override fun observeFriendRequests(): Flow<List<FriendRequest>> = flow {
        val uid = requireUserId()
        val path = "friend_requests"
        db.collection("friend_requests")
            .whereArrayContains("participantUids", uid)
            .limit(FRIEND_REQUESTS_LIMIT)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(FriendRequest::class.java) }
            .catch { error ->
                if (error !is CancellationException && auth.currentUser != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    override suspend fun sendFriendRequest(targetUid: String) {
        val uid = requireUserId()
        if (targetUid == uid) return
        val reqId = getFriendRequestIdForUsers(targetUid)
        val docRef = db.collection("friend_requests").document(reqId)
        val participants = listOf(uid, targetUid).sorted()

        try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val existing = existingSnap.toObject(FriendRequest::class.java)
                if (existing != null && existing.status == "PENDING" && existing.recipientId == uid) {
                    docRef.update(
                        mapOf(
                            "status" to "ACCEPTED",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                return
            }

            val payload = mapOf(
                "requestId" to reqId,
                "requesterId" to uid,
                "recipientId" to targetUid,
                "participantUids" to participants,
                "status" to "PENDING",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    override suspend fun acceptFriendRequest(requestId: String) {
        requireUserId()
        val docRef = db.collection("friend_requests").document(requestId)
        try {
            docRef.update(
                mapOf(
                    "status" to "ACCEPTED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    override suspend fun removeFriendOrRequest(otherUid: String) {
        requireUserId()
        val reqId = getFriendRequestIdForUsers(otherUid)
        val docRef = db.collection("friend_requests").document(reqId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Free Live Audio & Video Call Signaling (Open to all non-blocked members) ---

    override fun observeCallSessions(): Flow<List<CallSession>> = flow {
        val uid = requireUserId()
        val path = "call_sessions"
        db.collection("call_sessions")
            .whereArrayContains("participantUids", uid)
            .limit(CALL_SESSIONS_LIMIT)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(CallSession::class.java) }
            .catch { error ->
                if (error !is CancellationException && auth.currentUser != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    override suspend fun createCallSession(
        callee: UserProfile,
        callerName: String,
        callerPhotoUrl: String?,
        callType: String,
        offerSdp: String
    ): String {
        val uid = requireUserId()
        val callId = "call_${UUID.randomUUID().toString().replace("-", "")}"
        val participants = listOf(uid, callee.userId).sorted()
        val docRef = db.collection("call_sessions").document(callId)
        val payload = mutableMapOf<String, Any?>(
            "callId" to callId,
            "callerId" to uid,
            "callerName" to callerName.trim().ifBlank { "Member" }.take(60),
            "calleeId" to callee.userId,
            "calleeName" to callee.displayName.trim().ifBlank { "Member" }.take(60),
            "participantUids" to participants,
            "callType" to if (callType == "AUDIO") "AUDIO" else "VIDEO",
            "status" to "RINGING",
            "offerSdp" to offerSdp,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        callerPhotoUrl?.takeIf { it.isNotBlank() && it.length <= 65000 }?.let {
            payload["callerPhotoUrl"] = it
        }
        callee.photoUrl?.takeIf { it.isNotBlank() && it.length <= 65000 }?.let {
            payload["calleePhotoUrl"] = it
        }
        try {
            docRef.set(payload).await()
            return callId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    override suspend fun answerCallSession(callId: String, answerSdp: String) {
        requireUserId()
        val docRef = db.collection("call_sessions").document(callId)
        try {
            docRef.update(
                mapOf(
                    "status" to "ANSWERED",
                    "answerSdp" to answerSdp,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    override suspend fun updateCallStatus(callId: String, status: String) {
        requireUserId()
        val docRef = db.collection("call_sessions").document(callId)
        try {
            docRef.update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    override suspend fun addIceCandidate(
        callId: String,
        participantUids: List<String>,
        sdpMid: String,
        sdpMLineIndex: Int,
        candidate: String
    ) {
        val uid = requireUserId()
        val candId = "cand_${UUID.randomUUID().toString().replace("-", "")}"
        val docRef = db.collection("call_sessions").document(callId)
            .collection("candidates").document(candId)
        val payload = mapOf(
            "candidateId" to candId,
            "senderId" to uid,
            "participantUids" to participantUids,
            "sdpMid" to sdpMid.take(64),
            "sdpMLineIndex" to sdpMLineIndex.coerceIn(0, 32),
            "candidate" to candidate,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
        }
    }

    override fun observeIceCandidates(callId: String): Flow<List<IceCandidateRecord>> = flow {
        val uid = requireUserId()
        val path = "call_sessions/$callId/candidates"
        db.collection("call_sessions").document(callId).collection("candidates")
            .whereArrayContains("participantUids", uid)
            .limit(ICE_CANDIDATES_LIMIT)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(IceCandidateRecord::class.java) }
            .catch { error ->
                if (error !is CancellationException && auth.currentUser != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    // --- Everyday Activities Community Feed & Emoji Reactions ---

    override fun observeActivityPosts(): Flow<List<ActivityPost>> = flow {
        requireUserId()
        val path = "activity_posts"
        db.collection("activity_posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(ACTIVITY_POSTS_LIMIT)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(ActivityPost::class.java) }
            .catch { error ->
                if (error !is CancellationException && auth.currentUser != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    override fun observeMyActivityReactions(): Flow<Map<String, String>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/activity_reactions"
        db.collection("users").document(uid).collection("activity_reactions")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(ACTIVITY_REACTIONS_LIMIT)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val postId = doc.getString("postId") ?: doc.id
                    val emoji = doc.getString("emoji")
                    if (!emoji.isNullOrBlank()) postId to emoji else null
                }.toMap()
            }
            .catch { error ->
                if (error !is CancellationException && auth.currentUser != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    override suspend fun createActivityPost(
        authorProfile: UserProfile?,
        content: String,
        moodEmoji: String,
        activityTag: String
    ): String {
        val uid = requireUserId()
        val trimmedContent = content.trim().take(500)
        require(trimmedContent.isNotEmpty()) { "Activity post cannot be empty." }

        val postId = "post_${UUID.randomUUID().toString().replace("-", "")}"
        val docRef = db.collection("activity_posts").document(postId)
        val payload = mutableMapOf<String, Any?>(
            "postId" to postId,
            "authorId" to uid,
            "authorName" to (authorProfile?.displayName?.trim()?.ifBlank { "SOGO Member" } ?: "SOGO Member").take(60),
            "content" to trimmedContent,
            "moodEmoji" to moodEmoji.trim().ifBlank { "✨" }.take(16),
            "activityTag" to activityTag.trim().ifBlank { "Daily Moment" }.take(40),
            "reactionCounts" to emptyMap<String, Long>(),
            "totalReactions" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        authorProfile?.photoUrl?.takeIf { it.isNotBlank() && it.length <= 65000 }?.let {
            payload["authorPhotoUrl"] = it
        }
        if (!authorProfile?.city.isNullOrBlank()) {
            payload["authorCity"] = authorProfile?.city?.take(60)
        }

        try {
            docRef.set(payload).await()
            return postId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    override suspend fun toggleActivityReaction(postId: String, emoji: String) {
        val uid = requireUserId()
        val cleanEmoji = emoji.trim().take(16)
        if (cleanEmoji.isEmpty()) return

        val postRef = db.collection("activity_posts").document(postId)
        val reactionRef = db.collection("users").document(uid)
            .collection("activity_reactions").document(postId)

        try {
            db.runTransaction { transaction ->
                val postSnap = transaction.get(postRef)
                if (!postSnap.exists()) return@runTransaction null

                val reactionSnap = transaction.get(reactionRef)
                val existingEmoji = reactionSnap.getString("emoji")

                val currentCountsRaw = postSnap.get("reactionCounts") as? Map<*, *>
                val updatedCounts = mutableMapOf<String, Long>()
                currentCountsRaw?.forEach { (key, value) ->
                    val k = key as? String
                    val v = (value as? Number)?.toLong() ?: 0L
                    if (!k.isNullOrBlank() && v > 0L) {
                        updatedCounts[k] = v
                    }
                }

                if (existingEmoji == cleanEmoji) {
                    // Remove reaction
                    val nextCount = (updatedCounts[cleanEmoji] ?: 1L) - 1L
                    if (nextCount > 0L) {
                        updatedCounts[cleanEmoji] = nextCount
                    } else {
                        updatedCounts.remove(cleanEmoji)
                    }
                    transaction.delete(reactionRef)
                } else {
                    // Decrement previous emoji if switching reactions
                    if (!existingEmoji.isNullOrBlank()) {
                        val prevNext = (updatedCounts[existingEmoji] ?: 1L) - 1L
                        if (prevNext > 0L) {
                            updatedCounts[existingEmoji] = prevNext
                        } else {
                            updatedCounts.remove(existingEmoji)
                        }
                    }
                    // Increment new emoji (preserve up to 40 distinct emoji buckets)
                    if (updatedCounts.containsKey(cleanEmoji) || updatedCounts.size < 40) {
                        updatedCounts[cleanEmoji] = (updatedCounts[cleanEmoji] ?: 0L) + 1L
                    }
                    transaction.set(
                        reactionRef,
                        mapOf(
                            "userId" to uid,
                            "postId" to postId,
                            "emoji" to cleanEmoji,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                val newTotal = updatedCounts.values.sum().coerceAtLeast(0L)
                transaction.update(
                    postRef,
                    mapOf(
                        "reactionCounts" to updatedCounts,
                        "totalReactions" to newTotal,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                null
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, postRef.path)
            throw e
        }
    }

    override suspend fun deleteActivityPost(postId: String) {
        val uid = requireUserId()
        val postRef = db.collection("activity_posts").document(postId)
        val ownReactionRef = db.collection("users").document(uid)
            .collection("activity_reactions").document(postId)
        try {
            try {
                ownReactionRef.delete().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
            postRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, postRef.path)
            throw e
        }
    }

    override suspend fun toggleChatMessageReaction(conversationId: String, messageId: String, emoji: String) {
        val uid = requireUserId()
        val cleanEmoji = emoji.trim().take(16)
        if (cleanEmoji.isEmpty()) return

        val msgRef = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
        try {
            db.runTransaction { transaction ->
                val snap = transaction.get(msgRef)
                if (!snap.exists()) return@runTransaction null
                val rawReactions = snap.get("reactions") as? Map<*, *>
                val nextReactions = mutableMapOf<String, String>()
                rawReactions?.forEach { (k, v) ->
                    val key = k as? String
                    val value = v as? String
                    if (!key.isNullOrBlank() && !value.isNullOrBlank()) {
                        nextReactions[key] = value
                    }
                }
                if (nextReactions[uid] == cleanEmoji) {
                    nextReactions.remove(uid)
                } else {
                    nextReactions[uid] = cleanEmoji
                }
                transaction.update(msgRef, mapOf("reactions" to nextReactions))
                null
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            throw e
        }
    }

    /**
     * Deletes the signed-in user's owner-scoped records and Firebase Auth account.
     *
     * Safety sequence:
     * 1. Validate active Auth session and reauthenticate first (or enforce recent sign-in window)
     *    before mutating Firestore so failed/cancelled Auth verification preserves existing data.
     * 2. Remove owner-deletable Firestore documents: user_presence/{uid}, owner notifications,
     *    owner blocks, and users/{uid}. Note: Shared conversations (delete=false in rules) and
     *    immutable safety reports require a server-side Cloud Function for cross-user cleanup.
     * 3. Delete Firebase Auth account.
     */
    override suspend fun deleteUserAccount(googleIdToken: String?) {
        val currentUser = auth.currentUser
            ?: throw IllegalStateException("User must be signed in before deleting their account.")
        val uid = currentUser.uid

        // Step 1: Confirm Auth credential freshness BEFORE deleting any Firestore records
        if (!googleIdToken.isNullOrBlank()) {
            val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
            currentUser.reauthenticate(credential).await()
            currentUser.reload().await()
        } else {
            currentUser.reload().await()
            val lastSignInMs = currentUser.metadata?.lastSignInTimestamp ?: 0L
            val sessionAgeMs = if (lastSignInMs > 0L) System.currentTimeMillis() - lastSignInMs else Long.MAX_VALUE
            if (sessionAgeMs > RECENT_LOGIN_THRESHOLD_MS) {
                throw RecentLoginRequiredException()
            }
        }

        val docRef = db.collection("users").document(uid)
        val presenceRef = db.collection("user_presence").document(uid)
        var userBackupData: Map<String, Any>? = null
        try {
            userBackupData = docRef.get().await().data

            // Step 2: Delete owner-deletable Firestore documents while Auth token is still valid
            try {
                presenceRef.delete().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}

            val ownNotifications = try {
                db.collection("notifications").whereEqualTo("userId", uid).get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { null }
            ownNotifications?.documents?.forEach { doc ->
                try {
                    doc.reference.delete().await()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }

            val ownBlocks = try {
                db.collection("user_blocks").whereEqualTo("blockerId", uid).get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { null }
            ownBlocks?.documents?.forEach { doc ->
                try {
                    doc.reference.delete().await()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }

            val ownFriendRequests = try {
                db.collection("friend_requests").whereArrayContains("participantUids", uid).get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { null }
            ownFriendRequests?.documents?.forEach { doc ->
                try {
                    doc.reference.delete().await()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }

            val ownCallSessions = try {
                db.collection("call_sessions").whereArrayContains("participantUids", uid).get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { null }
            ownCallSessions?.documents?.forEach { doc ->
                try {
                    doc.reference.delete().await()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }

            val ownActivityPosts = try {
                db.collection("activity_posts").whereEqualTo("authorId", uid).get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { null }
            ownActivityPosts?.documents?.forEach { doc ->
                try {
                    doc.reference.delete().await()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }

            val ownActivityReactions = try {
                db.collection("users").document(uid).collection("activity_reactions").get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { null }
            ownActivityReactions?.documents?.forEach { doc ->
                try {
                    doc.reference.delete().await()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }

            docRef.delete().await()
            lastCommittedPresenceByUid.remove(uid)

            // Step 3: Delete Firebase Authentication account
            currentUser.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            if (userBackupData != null) {
                try { docRef.set(userBackupData).await() } catch (_: Exception) {}
            }
            throw RecentLoginRequiredException("Recent sign-in verification required before deleting account.")
        } catch (e: Exception) {
            if (userBackupData != null && auth.currentUser != null) {
                try { docRef.set(userBackupData).await() } catch (_: Exception) {}
            }
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    companion object {
        private const val RECENT_LOGIN_THRESHOLD_MS = 4 * 60 * 1000L
        private const val MEMBERS_FEED_LIMIT = 120L
        private const val CONVERSATIONS_LIMIT = 40L
        private const val MESSAGES_LIMIT = 100L
        private const val NOTIFICATIONS_LIMIT = 30L
        private const val FRIEND_REQUESTS_LIMIT = 150L
        private const val CALL_SESSIONS_LIMIT = 20L
        private const val ICE_CANDIDATES_LIMIT = 50L
        private const val ACTIVITY_POSTS_LIMIT = 50L
        private const val ACTIVITY_REACTIONS_LIMIT = 100L

        fun getFirestoreInstance(context: Context): FirebaseFirestore {
            val dbId = context.applicationContext.getString(R.string.firestore_database_id)
            return if (dbId.isBlank() || dbId == "(default)") {
                FirebaseFirestore.getInstance()
            } else {
                FirebaseFirestore.getInstance(dbId)
            }
        }
    }
}
