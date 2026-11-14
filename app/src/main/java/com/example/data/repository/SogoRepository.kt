package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BlockRecord
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
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
        val query = db.collection("users").orderBy("displayName", Query.Direction.ASCENDING)
        query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(UserProfile::class.java).filter { it.userId != uid }
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
        val docRef = db.collection("users").document(uid)
        val payload = mutableMapOf<String, Any?>(
            "userId" to uid,
            "displayName" to profile.displayName.trim(),
            "birthday" to profile.birthday,
            "age" to profile.age,
            "gender" to profile.gender,
            "country" to profile.country,
            "city" to (profile.city?.takeIf { it.isNotBlank() } ?: "Manila"),
            "aboutMe" to (profile.aboutMe ?: ""),
            "personality" to profile.personality,
            "photoUrl" to profile.photoUrl,
            "interests" to profile.interests,
            "isOnline" to true,
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
                handleFirestoreError(e, OperationType.UPDATE, presenceDoc.path)
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

        val messagePayload = mutableMapOf<String, Any?>(
            "messageId" to messageId,
            "conversationId" to conversationId,
            "senderId" to uid,
            "receiverId" to receiverId,
            "participantUids" to participants,
            "content" to content.trim(),
            "type" to type,
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (mediaUrl != null) {
            messagePayload["mediaUrl"] = mediaUrl
        }

        val convRef = db.collection("conversations").document(conversationId)

        val snippet = when (type) {
            "IMAGE" -> "📷 Photo"
            "VOICE" -> "🎤 Voice message"
            "STICKER" -> "✨ Sticker"
            else -> content.trim()
        }

        try {
            messageRef.set(messagePayload).await()
            convRef.update(
                mapOf(
                    "lastMessage" to snippet,
                    "lastMessageSenderId" to uid,
                    "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
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
            "reason" to reason,
            "status" to "PENDING",
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (!details.isNullOrBlank()) {
            payload["details"] = details.trim()
        }
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

    // --- Private Notifications ---

    override fun observeNotifications(): Flow<List<NotificationRecord>> = flow {
        val uid = requireUserId()
        val path = "notifications"
        db.collection("notifications")
            .whereEqualTo("userId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
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
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
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
            try { presenceRef.delete().await() } catch (_: Exception) {}

            val ownNotifications = try {
                db.collection("notifications").whereEqualTo("userId", uid).get().await()
            } catch (_: Exception) { null }
            ownNotifications?.documents?.forEach { doc ->
                try { doc.reference.delete().await() } catch (_: Exception) {}
            }

            val ownBlocks = try {
                db.collection("user_blocks").whereEqualTo("blockerId", uid).get().await()
            } catch (_: Exception) { null }
            ownBlocks?.documents?.forEach { doc ->
                try { doc.reference.delete().await() } catch (_: Exception) {}
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
