package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BlockRecord
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.NotificationRecord
import com.example.data.model.ReportRecord
import com.example.data.model.UserPresence
import com.example.data.model.UserProfile
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SogoRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        getFirestoreInstance(context),
        Firebase.auth
    )

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing SOGO CHAT AND MEET.")
    }

    // --- User Profile Operations ---

    fun observeCurrentUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
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

    fun observeMembers(): Flow<List<UserProfile>> = flow {
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

    suspend fun saveUserProfile(profile: UserProfile) {
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

        // Check if doc exists to set createdAt if creating
        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            } else {
                payload["createdAt"] = existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
            }
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun updatePresence(isOnline: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val userDoc = db.collection("users").document(uid)
        val presenceDoc = db.collection("user_presence").document(uid)
        try {
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
        } catch (e: Exception) {
            // Silently log presence failure
            handleFirestoreError(e, OperationType.UPDATE, presenceDoc.path)
        }
    }

    // --- Conversation & Messaging Operations ---

    fun getConversationIdForUsers(otherUid: String): String {
        val uid = requireUserId()
        val pair = listOf(uid, otherUid).sorted()
        return "conv_${pair[0]}_${pair[1]}"
    }

    fun observeConversations(): Flow<List<Conversation>> = flow {
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

    suspend fun ensureConversationExists(otherUid: String): String {
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

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = flow {
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

    suspend fun sendMessage(
        conversationId: String,
        receiverId: String,
        content: String,
        type: String = "TEXT",
        mediaUrl: String? = null
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
            // Write message and update conversation summary
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

    suspend fun markMessageAsRead(conversationId: String, messageId: String) {
        val uid = requireUserId()
        val docRef = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
        try {
            docRef.update("isRead", true).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
        }
    }

    // --- Safety: Blocking & Reporting ---

    suspend fun blockUser(blockedId: String) {
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

    fun observeBlockedUserIds(): Flow<Set<String>> = callbackFlow {
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

    suspend fun reportUser(reportedId: String, reason: String, details: String?) {
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

    fun observeNotifications(): Flow<List<NotificationRecord>> = flow {
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

    suspend fun markNotificationAsRead(notificationId: String) {
        val uid = requireUserId()
        val docRef = db.collection("notifications").document(notificationId)
        try {
            docRef.update("isRead", true).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
        }
    }

    suspend fun deleteUserAccount() {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val presenceRef = db.collection("user_presence").document(uid)
        try {
            try { presenceRef.delete().await() } catch (_: Exception) {}
            docRef.delete().await()
            auth.currentUser?.delete()?.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    companion object {
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
