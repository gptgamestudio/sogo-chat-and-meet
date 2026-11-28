package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ActivityComment
import com.example.data.model.ActivityPost
import com.example.data.model.BlockRecord
import com.example.data.model.CallSession
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.FavoriteRecord
import com.example.data.model.FollowRecord
import com.example.data.model.FriendRequest
import com.example.data.model.GalleryPhoto
import com.example.data.model.IceCandidateRecord
import com.example.data.model.NotificationRecord
import com.example.data.model.ProfileLikeRecord
import com.example.data.model.ProfileVisitorRecord
import com.example.data.model.UserProfile
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.example.ui.auth.isGoogleAuthenticatedUser
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
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
    suspend fun refreshMembers(force: Boolean = false) {}
    suspend fun loadMoreMembers(): Boolean = false
    suspend fun fetchUsersByIds(userIds: List<String>): List<UserProfile> = emptyList()
    suspend fun boostMyProfile() {}
    suspend fun saveUserProfile(profile: UserProfile)
    suspend fun updateProfilePhoto(photoUrl: String)
    suspend fun updateCoverPhoto(photoUrl: String) {}
    suspend fun updateDatingPreferencesAndPrivacy(
        username: String?,
        hobbies: List<String>,
        lookingFor: String?,
        preferredGender: String,
        preferredMinAge: Int,
        preferredMaxAge: Int,
        preferredCountry: String,
        hideFromDiscovery: Boolean,
        hideOnlineStatus: Boolean,
        hideAge: Boolean,
        hideLocation: Boolean,
        relationshipStatus: String? = null
    ) {}
    suspend fun updatePresence(isOnline: Boolean, force: Boolean = false)
    fun getConversationIdForUsers(otherUid: String): String
    fun observeConversations(): Flow<List<Conversation>>
    fun observeConversation(conversationId: String): Flow<Conversation?> = emptyFlow()
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
    suspend fun markConversationRead(conversationId: String) {}
    suspend fun setTypingStatus(conversationId: String, isTyping: Boolean) {}
    suspend fun blockUser(blockedId: String)
    suspend fun unblockUser(blockedId: String) {}
    fun observeBlockedUserIds(): Flow<Set<String>>
    fun observeMyBlockedUserIds(): Flow<Set<String>> = emptyFlow()
    suspend fun reportUser(reportedId: String, reason: String, details: String?)
    suspend fun reportChatContent(
        conversationId: String,
        message: ChatMessage,
        reason: String,
        details: String?
    ) {}
    fun isOwnerCandidateSignedIn(): Boolean = false
    fun observeNotifications(): Flow<List<NotificationRecord>>
    suspend fun markNotificationAsRead(notificationId: String)
    suspend fun markAllNotificationsAsRead() {}

    // --- Gallery Photos & Social Connections ---
    fun observeGalleryPhotos(userId: String): Flow<List<GalleryPhoto>> = emptyFlow()
    suspend fun addGalleryPhoto(photoUrl: String, caption: String? = null): String = ""
    suspend fun deleteGalleryPhoto(photoId: String) {}
    fun observeFollowingIds(): Flow<Set<String>> = emptyFlow()
    suspend fun toggleFollow(targetUid: String, currentlyFollowing: Boolean) {}
    fun observeFavoriteIds(): Flow<Set<String>> = emptyFlow()
    suspend fun toggleFavorite(targetUid: String, currentlyFavorited: Boolean) {}
    fun observeProfileLikesReceived(): Flow<List<ProfileLikeRecord>> = emptyFlow()
    fun observeProfileLikesGiven(): Flow<Set<String>> = emptyFlow()
    suspend fun toggleProfileLike(targetMember: UserProfile, myProfile: UserProfile?, currentlyLiked: Boolean) {}
    fun observeProfileVisitors(): Flow<List<ProfileVisitorRecord>> = emptyFlow()
    suspend fun recordProfileVisit(targetUid: String, myProfile: UserProfile?) {}
    suspend fun sendSayHi(targetMember: UserProfile, myProfile: UserProfile?) {}

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

    // --- Everyday Activities Community Feed, Photo Upload, Comments, Shares & Emoji Reactions ---
    fun observeActivityPosts(): Flow<List<ActivityPost>> = emptyFlow()
    suspend fun refreshActivityPosts(force: Boolean = false) {}
    fun observeMyActivityReactions(): Flow<Map<String, String>> = emptyFlow()
    suspend fun createActivityPost(
        authorProfile: UserProfile?,
        content: String,
        moodEmoji: String,
        activityTag: String,
        imageUrl: String? = null
    ): String = ""
    suspend fun toggleActivityLike(postId: String) {}
    suspend fun toggleActivityReaction(postId: String, emoji: String) {}
    suspend fun deleteActivityPost(postId: String) {}
    fun observeActivityComments(postId: String): Flow<List<ActivityComment>> = emptyFlow()
    suspend fun addActivityComment(postId: String, authorProfile: UserProfile?, content: String) {}
    suspend fun deleteActivityComment(postId: String, commentId: String) {}
    suspend fun shareActivityPost(postId: String) {}
    suspend fun reportActivityPost(post: ActivityPost, reason: String, details: String?) {}
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
    private val membersMutex = Mutex()
    private val lastCommittedPresenceByUid = ConcurrentHashMap<String, Boolean>()
    private val lastPresenceAttemptMsByUid = ConcurrentHashMap<String, Long>()

    private val membersRefreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val activitiesRefreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val activityReactionsRefreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val galleryRefreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val followingRefreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val favoritesRefreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val profileLikesRefreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val profileVisitorsRefreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val cachedMemberProfiles = LinkedHashMap<String, UserProfile>()
    private var lastMemberSnapshot: DocumentSnapshot? = null
    private var hasMoreMemberPages = true
    private var lastMembersFetchMs = 0L
    private var lastActivitiesFetchMs = 0L
    private var cachedActivityPosts: List<ActivityPost> = emptyList()

    override fun getCurrentUserId(): String? = auth.currentUser?.uid

    override fun isOwnerCandidateSignedIn(): Boolean {
        val user = auth.currentUser ?: return false
        val hasGoogleProvider = user.providerData.any {
            it.providerId == GoogleAuthProvider.PROVIDER_ID
        }
        return user.isEmailVerified &&
            hasGoogleProvider &&
            user.email.equals("gptgamestudioph@gmail.com", ignoreCase = true)
    }

    override fun requireUserId(): String {
        val user = auth.currentUser
            ?: throw IllegalStateException("User must be signed in with Google before accessing SOGO CHAT AND MEET.")
        if (!isGoogleAuthenticatedUser(user)) {
            throw IllegalStateException("Unsupported non-Google session. Please sign out and sign in with Google.")
        }
        return user.uid
    }

    // --- User Profile Operations ---

    override fun observeCurrentUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
        if (userId.isBlank()) {
            close(IllegalArgumentException("User ID cannot be blank."))
            return@callbackFlow
        }
        val currentUser = auth.currentUser
        if (currentUser != null && !isGoogleAuthenticatedUser(currentUser)) {
            close(IllegalStateException("Unsupported non-Google session. Please sign out and sign in with Google."))
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
        emit(fetchInitialMembersPage(uid))
        membersRefreshSignal.collect {
            val activeUid = auth.currentUser?.uid ?: uid
            emit(snapshotCachedMembers(activeUid))
        }
    }

    private suspend fun fetchInitialMembersPage(uid: String): List<UserProfile> {
        val path = "users"
        return membersMutex.withLock {
            try {
                val snap = db.collection("users")
                    .orderBy("updatedAt", Query.Direction.DESCENDING)
                    .limit(MEMBERS_PAGE_SIZE)
                    .get()
                    .await()
                cachedMemberProfiles.clear()
                snap.toObjects(UserProfile::class.java).forEach { member ->
                    if (member.userId.isNotBlank()) {
                        cachedMemberProfiles[member.userId] = member
                    }
                }
                lastMemberSnapshot = snap.documents.lastOrNull()
                hasMoreMemberPages = snap.documents.size >= MEMBERS_PAGE_SIZE
                lastMembersFetchMs = System.currentTimeMillis()
                snapshotCachedMembersLocked(uid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (auth.currentUser != null) {
                    handleFirestoreError(e, OperationType.LIST, path)
                }
                throw e
            }
        }
    }

    private suspend fun snapshotCachedMembers(uid: String): List<UserProfile> =
        membersMutex.withLock { snapshotCachedMembersLocked(uid) }

    private fun snapshotCachedMembersLocked(uid: String): List<UserProfile> =
        cachedMemberProfiles.values
            .filter { it.userId != uid }
            .sortedWith(
                compareByDescending<UserProfile> { it.isOnline }
                    .thenByDescending { it.updatedAt?.toDate()?.time ?: 0L }
                    .thenBy { it.displayName.lowercase() }
            )

    override suspend fun refreshMembers(force: Boolean) {
        val uid = requireUserId()
        val now = System.currentTimeMillis()
        if (!force && (now - lastMembersFetchMs) < FEED_REFRESH_COOLDOWN_MS && cachedMemberProfiles.isNotEmpty()) {
            return
        }
        fetchInitialMembersPage(uid)
        membersRefreshSignal.tryEmit(Unit)
    }

    override suspend fun loadMoreMembers(): Boolean {
        val uid = requireUserId()
        val cursor = membersMutex.withLock {
            if (!hasMoreMemberPages || cachedMemberProfiles.size >= MAX_CACHED_MEMBERS) {
                null
            } else {
                lastMemberSnapshot
            }
        } ?: return false

        return try {
            val snap = db.collection("users")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .startAfter(cursor)
                .limit(MEMBERS_PAGE_SIZE)
                .get()
                .await()
            val added = membersMutex.withLock {
                var count = 0
                snap.toObjects(UserProfile::class.java).forEach { member ->
                    if (member.userId.isNotBlank() &&
                        member.userId != uid &&
                        cachedMemberProfiles.size < MAX_CACHED_MEMBERS &&
                        !cachedMemberProfiles.containsKey(member.userId)
                    ) {
                        cachedMemberProfiles[member.userId] = member
                        count++
                    }
                }
                lastMemberSnapshot = snap.documents.lastOrNull() ?: lastMemberSnapshot
                hasMoreMemberPages = snap.documents.size >= MEMBERS_PAGE_SIZE &&
                    cachedMemberProfiles.size < MAX_CACHED_MEMBERS
                count > 0
            }
            if (added) {
                membersRefreshSignal.tryEmit(Unit)
            }
            added
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth.currentUser != null) {
                handleFirestoreError(e, OperationType.LIST, "users")
            }
            false
        }
    }

    override suspend fun fetchUsersByIds(userIds: List<String>): List<UserProfile> {
        requireUserId()
        val cleanIds = userIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(30)
        if (cleanIds.isEmpty()) return emptyList()
        val results = mutableListOf<UserProfile>()
        for (chunk in cleanIds.chunked(10)) {
            try {
                val snap = db.collection("users")
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
                    .await()
                results += snap.toObjects(UserProfile::class.java)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        return results
    }

    override suspend fun boostMyProfile() {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf("updatedAt" to FieldValue.serverTimestamp())
            ).await()
            refreshMembers(force = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    override suspend fun saveUserProfile(profile: UserProfile) {
        val uid = requireUserId()
        val safeDisplayName = profile.displayName.trim().ifBlank { "Member" }.take(50)
        val safeUsername = profile.username?.trim()?.replace(Regex("[^a-zA-Z0-9_.]"), "")?.takeIf { it.isNotBlank() }?.take(32)
        val safeGender = profile.gender.trim().ifBlank { "Female" }.take(30)
        val safeCountry = profile.country.trim().ifBlank { "Philippines" }.take(60)
        val safeCity = profile.city?.trim()?.takeIf { it.isNotBlank() }?.take(60) ?: "Manila"
        val safeAboutMe = (profile.aboutMe ?: "").trim().take(1000)
        val safeBirthday = profile.birthday?.trim()?.takeIf { it.isNotBlank() }?.take(30)
        val safePersonality = profile.personality?.trim()?.takeIf { it.isNotBlank() }?.take(100)
        val safePhotoUrl = profile.photoUrl?.trim()?.takeIf { it.isNotBlank() && it.length <= 65000 }
        val safeCoverPhotoUrl = profile.coverPhotoUrl?.trim()?.takeIf { it.isNotBlank() && it.length <= 65000 }
        val safeLookingFor = profile.lookingFor?.trim()?.takeIf { it.isNotBlank() }?.take(60)
        val safeRelationshipStatus = profile.relationshipStatus?.trim()?.takeIf { it.isNotBlank() }?.take(40)
        val safePreferredGender = profile.preferredGender.trim().takeIf {
            it in setOf("ALL", "Female", "Male", "Non-Binary")
        } ?: "ALL"
        val safeMinAge = profile.preferredMinAge.coerceIn(18, 100)
        val safeMaxAge = profile.preferredMaxAge.coerceIn(safeMinAge, 120)
        val safePreferredCountry = profile.preferredCountry.trim().ifBlank { "ALL" }.take(60)
        val safeInterests = profile.interests
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(25)
        val safeHobbies = profile.hobbies
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(25)

        val docRef = db.collection("users").document(uid)
        val updatePayload = mutableMapOf<String, Any?>(
            "username" to safeUsername,
            "displayName" to safeDisplayName,
            "birthday" to safeBirthday,
            "age" to profile.age,
            "gender" to safeGender,
            "country" to safeCountry,
            "city" to safeCity,
            "aboutMe" to safeAboutMe,
            "personality" to safePersonality,
            "photoUrl" to safePhotoUrl,
            "coverPhotoUrl" to safeCoverPhotoUrl,
            "hobbies" to safeHobbies,
            "interests" to safeInterests,
            "lookingFor" to safeLookingFor,
            "relationshipStatus" to safeRelationshipStatus,
            "preferredGender" to safePreferredGender,
            "preferredMinAge" to safeMinAge,
            "preferredMaxAge" to safeMaxAge,
            "preferredCountry" to safePreferredCountry,
            "hideFromDiscovery" to profile.hideFromDiscovery,
            "hideOnlineStatus" to profile.hideOnlineStatus,
            "hideAge" to profile.hideAge,
            "hideLocation" to profile.hideLocation,
            "isOnline" to true,
            "role" to "member",
            "lastActive" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val createPayload = HashMap(updatePayload).apply {
                    put("userId", uid)
                    put("accountStatus", "ACTIVE")
                    put("createdAt", FieldValue.serverTimestamp())
                }
                docRef.set(createPayload).await()
            } else {
                // Update only allowed mutable keys so owner-managed accountStatus remains intact
                docRef.update(updatePayload).await()
            }
            lastCommittedPresenceByUid[uid] = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    override suspend fun updateProfilePhoto(photoUrl: String) {
        val uid = requireUserId()
        val safePhotoUrl = photoUrl.trim().takeIf { it.isNotBlank() && it.length <= 65000 }
            ?: throw IllegalArgumentException("Selected photo is too large or invalid. Please try another photo.")
        val docRef = db.collection("users").document(uid)
        try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                throw IllegalStateException("Please complete your profile setup before updating your profile picture.")
            }
            docRef.update(
                mapOf(
                    "photoUrl" to safePhotoUrl,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    override suspend fun updateCoverPhoto(photoUrl: String) {
        val uid = requireUserId()
        val safeCoverUrl = photoUrl.trim().takeIf { it.isNotBlank() && it.length <= 65000 }
            ?: throw IllegalArgumentException("Selected cover photo is too large or invalid. Please try another photo.")
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "coverPhotoUrl" to safeCoverUrl,
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

    override suspend fun updateDatingPreferencesAndPrivacy(
        username: String?,
        hobbies: List<String>,
        lookingFor: String?,
        preferredGender: String,
        preferredMinAge: Int,
        preferredMaxAge: Int,
        preferredCountry: String,
        hideFromDiscovery: Boolean,
        hideOnlineStatus: Boolean,
        hideAge: Boolean,
        hideLocation: Boolean,
        relationshipStatus: String?
    ) {
        val uid = requireUserId()
        val safeUsername = username?.trim()?.replace(Regex("[^a-zA-Z0-9_.]"), "")?.takeIf { it.isNotBlank() }?.take(32)
        val safeMinAge = preferredMinAge.coerceIn(18, 100)
        val safeMaxAge = preferredMaxAge.coerceIn(safeMinAge, 120)
        val safeGender = preferredGender.takeIf { it in setOf("ALL", "Female", "Male", "Non-Binary") } ?: "ALL"
        val docRef = db.collection("users").document(uid)
        val updateMap = mutableMapOf<String, Any?>(
            "username" to safeUsername,
            "hobbies" to hobbies.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(25),
            "lookingFor" to lookingFor?.trim()?.takeIf { it.isNotBlank() }?.take(60),
            "preferredGender" to safeGender,
            "preferredMinAge" to safeMinAge,
            "preferredMaxAge" to safeMaxAge,
            "preferredCountry" to preferredCountry.trim().ifBlank { "ALL" }.take(60),
            "hideFromDiscovery" to hideFromDiscovery,
            "hideOnlineStatus" to hideOnlineStatus,
            "hideAge" to hideAge,
            "hideLocation" to hideLocation,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (relationshipStatus != null) {
            updateMap["relationshipStatus"] = relationshipStatus.trim().takeIf { it.isNotBlank() }?.take(40)
        }
        try {
            docRef.update(updateMap).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    override suspend fun updatePresence(isOnline: Boolean, force: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        presenceMutex.withLock {
            val nowMs = System.currentTimeMillis()
            val lastAttemptMs = lastPresenceAttemptMsByUid[uid] ?: 0L
            if (!force) {
                if (lastCommittedPresenceByUid[uid] == isOnline) return
                if ((nowMs - lastAttemptMs) < PRESENCE_THROTTLE_MS) return
            }
            val userDoc = db.collection("users").document(uid)
            val presenceDoc = db.collection("user_presence").document(uid)
            try {
                val userSnapshot = userDoc.get().await()
                if (!userSnapshot.exists()) {
                    return
                }
                // Do NOT mutate users/{uid}.updatedAt on presence toggles so 50k users do not shuffle the discovery index
                userDoc.update(
                    mapOf(
                        "isOnline" to isOnline,
                        "lastActive" to FieldValue.serverTimestamp()
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
                lastPresenceAttemptMsByUid[uid] = nowMs
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

    override fun observeConversation(conversationId: String): Flow<Conversation?> = flow {
        requireUserId()
        if (conversationId.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "conversations/$conversationId"
        db.collection("conversations").document(conversationId)
            .snapshots()
            .map { snapshot -> snapshot.toObject(Conversation::class.java) }
            .catch { error ->
                if (error !is CancellationException && auth.currentUser != null) {
                    handleFirestoreError(error, OperationType.GET, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    override suspend fun ensureConversationExists(otherUid: String): String {
        val uid = requireUserId()
        if (otherUid.isBlank() || otherUid == uid) {
            throw IllegalArgumentException("Invalid conversation partner.")
        }
        val myBlockSnap = runCatching {
            db.collection("user_blocks").document("block_${uid}_${otherUid}").get().await()
        }.getOrNull()
        val theirBlockSnap = runCatching {
            db.collection("user_blocks").document("block_${otherUid}_${uid}").get().await()
        }.getOrNull()
        if (myBlockSnap?.exists() == true || theirBlockSnap?.exists() == true) {
            throw IllegalStateException("Chat is restricted because one of you has blocked the other.")
        }

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
                    "unreadCountByUid" to mapOf(uid to 0L, otherUid to 0L),
                    "typingByUid" to emptyMap<String, Any?>(),
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
        val safeMediaUrl = mediaUrl?.trim()?.takeIf { it.isNotBlank() && it.length <= 65000 }

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
                        "unreadCountByUid.$receiverId" to FieldValue.increment(1L),
                        "typingByUid.$uid" to FieldValue.delete(),
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

    override suspend fun markConversationRead(conversationId: String) {
        val uid = auth.currentUser?.uid ?: return
        if (conversationId.isBlank()) return
        val convRef = db.collection("conversations").document(conversationId)
        try {
            convRef.update(
                mapOf(
                    "unreadCountByUid.$uid" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    override suspend fun setTypingStatus(conversationId: String, isTyping: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        if (conversationId.isBlank()) return
        val convRef = db.collection("conversations").document(conversationId)
        try {
            convRef.update(
                mapOf(
                    "typingByUid.$uid" to if (isTyping) FieldValue.serverTimestamp() else FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    // --- Safety: Blocking & Reporting ---

    override suspend fun blockUser(blockedId: String) {
        val uid = requireUserId()
        if (blockedId.isBlank() || blockedId == uid) return
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
            try { aliasDoc.set(payload).await() } catch (e: CancellationException) { throw e } catch (_: Exception) {}
            // Remove any existing friend request or friendship between the two users
            try {
                val friendReqId = getFriendRequestIdForUsers(blockedId)
                db.collection("friend_requests").document(friendReqId).delete().await()
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

    override suspend fun unblockUser(blockedId: String) {
        val uid = requireUserId()
        if (blockedId.isBlank() || blockedId == uid) return
        val blockId = "block_${uid}_${blockedId}"
        val primaryDoc = db.collection("user_blocks").document(blockId)
        val aliasDoc = db.collection("blocks").document(blockId)
        try {
            primaryDoc.delete().await()
            try { aliasDoc.delete().await() } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, primaryDoc.path)
            throw e
        }
    }

    override fun observeBlockedUserIds(): Flow<Set<String>> = callbackFlow {
        val uid = requireUserId()
        var myBlocks = emptySet<String>()
        var blockedBy = emptySet<String>()

        val sub1 = db.collection("user_blocks")
            .whereEqualTo("blockerId", uid)
            .limit(BLOCKS_LIMIT)
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
            .limit(BLOCKS_LIMIT)
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

    override fun observeMyBlockedUserIds(): Flow<Set<String>> = callbackFlow {
        val uid = requireUserId()
        val registration = db.collection("user_blocks")
            .whereEqualTo("blockerId", uid)
            .limit(BLOCKS_LIMIT)
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
                    val myBlocks = snapshot.toObjects(BlockRecord::class.java)
                        .mapNotNull { it.blockedId.takeIf(String::isNotBlank) }
                        .toSet()
                    trySend(myBlocks)
                }
            }
        awaitClose { registration.remove() }
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

    override suspend fun reportChatContent(
        conversationId: String,
        message: ChatMessage,
        reason: String,
        details: String?
    ) {
        val uid = requireUserId()
        val reportedUid = message.senderId.ifBlank {
            message.participantUids.firstOrNull { it != uid }.orEmpty()
        }
        if (reportedUid.isBlank() || reportedUid == uid) {
            throw IllegalArgumentException("You can only report content sent by another member.")
        }
        if (conversationId.isBlank() || message.messageId.isBlank()) {
            throw IllegalArgumentException("Missing conversation or message identifier.")
        }

        val reportId = "creport_${UUID.randomUUID()}"
        val docRef = db.collection("content_reports").document(reportId)
        val safeExcerpt = message.content.trim().ifBlank { "[${message.type} attachment]" }.take(500)
        val safeType = message.type.trim().uppercase().takeIf {
            it in setOf("TEXT", "IMAGE", "VOICE", "STICKER")
        } ?: "TEXT"
        val payload = mutableMapOf<String, Any?>(
            "reportId" to reportId,
            "reporterId" to uid,
            "reportedUserId" to reportedUid,
            "conversationId" to conversationId.trim().take(128),
            "messageId" to message.messageId.trim().take(128),
            "messageType" to safeType,
            "messageExcerpt" to safeExcerpt,
            "reason" to reason.trim().ifBlank { "Inappropriate content" }.take(100),
            "status" to "PENDING",
            "createdAt" to FieldValue.serverTimestamp()
        )
        val safeDetails = details?.trim()?.takeIf { it.isNotBlank() }?.take(1000)
        if (safeDetails != null) {
            payload["details"] = safeDetails
        }
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
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

    override suspend fun markAllNotificationsAsRead() {
        val uid = requireUserId()
        try {
            val snap = db.collection("notifications")
                .whereEqualTo("userId", uid)
                .limit(NOTIFICATIONS_LIMIT)
                .get()
                .await()
            snap.documents.forEach { doc ->
                if (doc.getBoolean("isRead") == false) {
                    try {
                        doc.reference.update("isRead", true).await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {}
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
    }

    private suspend fun createCrossUserNotification(
        targetUid: String,
        type: String,
        title: String,
        body: String,
        data: String? = null
    ) {
        val uid = auth.currentUser?.uid ?: return
        if (targetUid.isBlank() || targetUid == uid) return
        val notifId = "notif_${UUID.randomUUID().toString().replace("-", "")}"
        val payload = mutableMapOf<String, Any?>(
            "notificationId" to notifId,
            "userId" to targetUid,
            "senderId" to uid,
            "title" to title.trim().ifBlank { "SOGO Activity" }.take(100),
            "body" to body.trim().ifBlank { "Tap to view" }.take(500),
            "type" to type,
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (!data.isNullOrBlank()) {
            payload["data"] = data.take(500)
        }
        try {
            db.collection("notifications").document(notifId).set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
    }

    // --- Gallery Photos & Social Connections ---

    override fun observeGalleryPhotos(userId: String): Flow<List<GalleryPhoto>> = flow {
        requireUserId()
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/gallery_photos"
        suspend fun fetchGallery(): List<GalleryPhoto> = try {
            db.collection("users").document(userId).collection("gallery_photos")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(30L)
                .get()
                .await()
                .toObjects(GalleryPhoto::class.java)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
        emit(fetchGallery())
        galleryRefreshSignal.collect {
            emit(fetchGallery())
        }
    }

    override suspend fun addGalleryPhoto(photoUrl: String, caption: String?): String {
        val uid = requireUserId()
        val safeUrl = photoUrl.trim().takeIf { it.isNotBlank() && it.length <= 65000 }
            ?: throw IllegalArgumentException("Selected gallery photo is too large or invalid.")
        val photoId = "gphoto_${UUID.randomUUID().toString().replace("-", "")}"
        val docRef = db.collection("users").document(uid).collection("gallery_photos").document(photoId)
        val payload = mutableMapOf<String, Any?>(
            "photoId" to photoId,
            "userId" to uid,
            "photoUrl" to safeUrl,
            "createdAt" to FieldValue.serverTimestamp()
        )
        caption?.trim()?.takeIf { it.isNotBlank() }?.take(200)?.let {
            payload["caption"] = it
        }
        try {
            docRef.set(payload).await()
            galleryRefreshSignal.tryEmit(Unit)
            return photoId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    override suspend fun deleteGalleryPhoto(photoId: String) {
        val uid = requireUserId()
        if (photoId.isBlank()) return
        val docRef = db.collection("users").document(uid).collection("gallery_photos").document(photoId)
        try {
            docRef.delete().await()
            galleryRefreshSignal.tryEmit(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    override fun observeFollowingIds(): Flow<Set<String>> = flow {
        val uid = requireUserId()
        suspend fun fetchFollowing(): Set<String> = try {
            db.collection("user_follows")
                .whereEqualTo("followerId", uid)
                .limit(150L)
                .get()
                .await()
                .toObjects(FollowRecord::class.java)
                .mapNotNull { it.followingId.takeIf(String::isNotBlank) }
                .toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, "user_follows")
            throw e
        }
        emit(fetchFollowing())
        followingRefreshSignal.collect { emit(fetchFollowing()) }
    }

    override suspend fun toggleFollow(targetUid: String, currentlyFollowing: Boolean) {
        val uid = requireUserId()
        if (targetUid.isBlank() || targetUid == uid) return
        val followId = "follow_${uid}_${targetUid}"
        val docRef = db.collection("user_follows").document(followId)
        try {
            if (currentlyFollowing) {
                docRef.delete().await()
            } else {
                docRef.set(
                    mapOf(
                        "followId" to followId,
                        "followerId" to uid,
                        "followingId" to targetUid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            followingRefreshSignal.tryEmit(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    override fun observeFavoriteIds(): Flow<Set<String>> = flow {
        val uid = requireUserId()
        suspend fun fetchFavorites(): Set<String> = try {
            db.collection("user_favorites")
                .whereEqualTo("userId", uid)
                .limit(150L)
                .get()
                .await()
                .toObjects(FavoriteRecord::class.java)
                .mapNotNull { it.targetUserId.takeIf(String::isNotBlank) }
                .toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, "user_favorites")
            throw e
        }
        emit(fetchFavorites())
        favoritesRefreshSignal.collect { emit(fetchFavorites()) }
    }

    override suspend fun toggleFavorite(targetUid: String, currentlyFavorited: Boolean) {
        val uid = requireUserId()
        if (targetUid.isBlank() || targetUid == uid) return
        val favId = "fav_${uid}_${targetUid}"
        val docRef = db.collection("user_favorites").document(favId)
        try {
            if (currentlyFavorited) {
                docRef.delete().await()
            } else {
                docRef.set(
                    mapOf(
                        "favoriteId" to favId,
                        "userId" to uid,
                        "targetUserId" to targetUid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            favoritesRefreshSignal.tryEmit(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    override fun observeProfileLikesReceived(): Flow<List<ProfileLikeRecord>> = flow {
        val uid = requireUserId()
        suspend fun fetchReceived(): List<ProfileLikeRecord> = try {
            db.collection("profile_likes")
                .whereEqualTo("likedUserId", uid)
                .limit(150L)
                .get()
                .await()
                .toObjects(ProfileLikeRecord::class.java)
                .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, "profile_likes")
            throw e
        }
        emit(fetchReceived())
        profileLikesRefreshSignal.collect { emit(fetchReceived()) }
    }

    override fun observeProfileLikesGiven(): Flow<Set<String>> = flow {
        val uid = requireUserId()
        suspend fun fetchGiven(): Set<String> = try {
            db.collection("profile_likes")
                .whereEqualTo("likerId", uid)
                .limit(150L)
                .get()
                .await()
                .toObjects(ProfileLikeRecord::class.java)
                .mapNotNull { it.likedUserId.takeIf(String::isNotBlank) }
                .toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, "profile_likes")
            throw e
        }
        emit(fetchGiven())
        profileLikesRefreshSignal.collect { emit(fetchGiven()) }
    }

    override suspend fun toggleProfileLike(
        targetMember: UserProfile,
        myProfile: UserProfile?,
        currentlyLiked: Boolean
    ) {
        val uid = requireUserId()
        val targetUid = targetMember.userId
        if (targetUid.isBlank() || targetUid == uid) return
        val likeId = "like_${uid}_${targetUid}"
        val docRef = db.collection("profile_likes").document(likeId)
        try {
            if (currentlyLiked) {
                docRef.delete().await()
            } else {
                val payload = mutableMapOf<String, Any?>(
                    "likeId" to likeId,
                    "likerId" to uid,
                    "likedUserId" to targetUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                myProfile?.displayName?.trim()?.takeIf { it.isNotBlank() }?.take(60)?.let {
                    payload["likerName"] = it
                }
                myProfile?.photoUrl?.trim()?.takeIf { it.isNotBlank() && it.length <= 65000 }?.let {
                    payload["likerPhotoUrl"] = it
                }
                docRef.set(payload).await()
                val senderLabel = myProfile?.displayName?.trim()?.ifBlank { "A member" } ?: "A member"
                createCrossUserNotification(
                    targetUid = targetUid,
                    type = "PROFILE_LIKE",
                    title = "❤️ $senderLabel liked your profile!",
                    body = "$senderLabel gave your SOGO profile a like.",
                    data = uid
                )
            }
            profileLikesRefreshSignal.tryEmit(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    override fun observeProfileVisitors(): Flow<List<ProfileVisitorRecord>> = flow {
        val uid = requireUserId()
        suspend fun fetchVisitors(): List<ProfileVisitorRecord> = try {
            db.collection("profile_visits")
                .whereEqualTo("visitedUserId", uid)
                .limit(100L)
                .get()
                .await()
                .toObjects(ProfileVisitorRecord::class.java)
                .sortedByDescending { it.visitedAt?.toDate()?.time ?: 0L }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, "profile_visits")
            throw e
        }
        emit(fetchVisitors())
        profileVisitorsRefreshSignal.collect { emit(fetchVisitors()) }
    }

    override suspend fun recordProfileVisit(targetUid: String, myProfile: UserProfile?) {
        val uid = auth.currentUser?.takeIf { isGoogleAuthenticatedUser(it) }?.uid ?: return
        if (targetUid.isBlank() || targetUid == uid) return
        val visitId = "visit_${uid}_${targetUid}"
        val docRef = db.collection("profile_visits").document(visitId)
        val visitorName = myProfile?.displayName?.trim()?.ifBlank { "Member" }?.take(60) ?: "Member"
        val payload = mutableMapOf<String, Any?>(
            "visitId" to visitId,
            "visitorId" to uid,
            "visitedUserId" to targetUid,
            "visitorName" to visitorName,
            "visitedAt" to FieldValue.serverTimestamp()
        )
        myProfile?.photoUrl?.trim()?.takeIf { it.isNotBlank() && it.length <= 65000 }?.let {
            payload["visitorPhotoUrl"] = it
        }
        try {
            docRef.set(payload).await()
            createCrossUserNotification(
                targetUid = targetUid,
                type = "PROFILE_VISIT",
                title = "👀 $visitorName visited your profile",
                body = "$visitorName checked out your profile on SOGO CHAT & MEET.",
                data = uid
            )
            profileVisitorsRefreshSignal.tryEmit(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    override suspend fun sendSayHi(targetMember: UserProfile, myProfile: UserProfile?) {
        val uid = requireUserId()
        val targetUid = targetMember.userId
        if (targetUid.isBlank() || targetUid == uid) return
        val convId = ensureConversationExists(targetUid)
        sendMessage(
            conversationId = convId,
            receiverId = targetUid,
            content = "👋 Hi ${targetMember.displayName}! Let's chat and be friends!",
            type = "TEXT"
        )
        val senderLabel = myProfile?.displayName?.trim()?.ifBlank { "Someone" } ?: "Someone"
        createCrossUserNotification(
            targetUid = targetUid,
            type = "SAY_HI",
            title = "👋 $senderLabel said Hi!",
            body = "$senderLabel sent you a friendly wave. Open chat to reply!",
            data = convId
        )
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
        val cleanTarget = targetUid.trim()
        if (cleanTarget.isBlank() || cleanTarget == uid) return
        val reqId = getFriendRequestIdForUsers(cleanTarget)
        val docRef = db.collection("friend_requests").document(reqId)
        val participants = listOf(uid, cleanTarget).sorted()

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
                // Request already PENDING or ACCEPTED; prevent duplicate friend requests
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
            createCrossUserNotification(
                targetUid = targetUid,
                type = "FRIEND_REQUEST",
                title = "🤝 New Friend Request",
                body = "A SOGO member wants to connect with you as a friend!",
                data = uid
            )
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
        val uid = requireUserId()
        val cleanOther = otherUid.trim()
        if (cleanOther.isBlank() || cleanOther == uid) return
        val reqId = getFriendRequestIdForUsers(cleanOther)
        val docRef = db.collection("friend_requests").document(reqId)
        try {
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) return
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

    // --- Everyday Activities Community Feed, Likes, Comments, Photo Upload & Emoji Reactions ---

    override fun observeActivityPosts(): Flow<List<ActivityPost>> = flow {
        requireUserId()
        emit(fetchActivityPostsSnapshot())
        activitiesRefreshSignal.collect {
            emit(fetchActivityPostsSnapshot())
        }
    }

    private suspend fun fetchActivityPostsSnapshot(): List<ActivityPost> {
        val path = ACTIVITIES_COLLECTION
        return try {
            val snap = db.collection(ACTIVITIES_COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(ACTIVITY_POSTS_LIMIT)
                .get()
                .await()
            cachedActivityPosts = snap.toObjects(ActivityPost::class.java)
            lastActivitiesFetchMs = System.currentTimeMillis()
            cachedActivityPosts
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth.currentUser != null) {
                handleFirestoreError(e, OperationType.LIST, path)
            }
            throw e
        }
    }

    override suspend fun refreshActivityPosts(force: Boolean) {
        requireUserId()
        val now = System.currentTimeMillis()
        if (!force && (now - lastActivitiesFetchMs) < FEED_REFRESH_COOLDOWN_MS && cachedActivityPosts.isNotEmpty()) {
            return
        }
        fetchActivityPostsSnapshot()
        activitiesRefreshSignal.tryEmit(Unit)
    }

    override fun observeMyActivityReactions(): Flow<Map<String, String>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/activity_reactions"
        suspend fun fetchReactions(): Map<String, String> = try {
            db.collection("users").document(uid).collection("activity_reactions")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(ACTIVITY_REACTIONS_LIMIT)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    val postId = doc.getString("postId") ?: doc.id
                    val emoji = doc.getString("emoji")
                    if (!emoji.isNullOrBlank()) postId to emoji else null
                }.toMap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
        emit(fetchReactions())
        activityReactionsRefreshSignal.collect { emit(fetchReactions()) }
    }

    override suspend fun createActivityPost(
        authorProfile: UserProfile?,
        content: String,
        moodEmoji: String,
        activityTag: String,
        imageUrl: String?
    ): String {
        val uid = requireUserId()
        val trimmedContent = content.trim().take(500)
        require(trimmedContent.isNotEmpty()) { "Activity post cannot be empty." }

        val postId = "post_${UUID.randomUUID().toString().replace("-", "")}"
        val docRef = db.collection(ACTIVITIES_COLLECTION).document(postId)
        val cleanAuthorName = (authorProfile?.displayName?.trim()?.ifBlank { "SOGO Member" } ?: "SOGO Member").take(60)
        val cleanUsername = (authorProfile?.username?.trim()?.takeIf { it.isNotBlank() } ?: cleanAuthorName).take(60)
        val cleanActivityType = activityTag.trim().ifBlank { "General Activities" }.take(40)
        val cleanMoodEmoji = moodEmoji.trim().ifBlank { "✨" }.take(16)
        val payload = mutableMapOf<String, Any?>(
            "postId" to postId,
            "userId" to uid,
            "authorId" to uid,
            "username" to cleanUsername,
            "authorName" to cleanAuthorName,
            "activityType" to cleanActivityType,
            "activityTag" to cleanActivityType,
            "moodEmoji" to cleanMoodEmoji,
            "content" to trimmedContent,
            "likes" to emptyList<String>(),
            "commentIds" to emptyList<String>(),
            "reactionCounts" to emptyMap<String, Long>(),
            "totalReactions" to 0L,
            "commentCount" to 0L,
            "shareCount" to 0L,
            "timestamp" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        authorProfile?.username?.trim()?.takeIf { it.isNotBlank() }?.take(32)?.let {
            payload["authorUsername"] = it
        }
        authorProfile?.photoUrl?.takeIf { it.isNotBlank() && it.length <= 65000 }?.let {
            payload["authorPhotoUrl"] = it
        }
        if (!authorProfile?.city.isNullOrBlank()) {
            payload["authorCity"] = authorProfile?.city?.take(60)
        }
        if (!authorProfile?.country.isNullOrBlank()) {
            payload["authorCountry"] = authorProfile?.country?.take(60)
        }
        imageUrl?.trim()?.takeIf { it.isNotBlank() && it.length <= 65000 }?.let {
            payload["imageUrl"] = it
        }

        try {
            docRef.set(payload).await()
            activitiesRefreshSignal.tryEmit(Unit)
            return postId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    override suspend fun toggleActivityLike(postId: String) {
        val uid = requireUserId()
        if (postId.isBlank()) return
        val postRef = db.collection(ACTIVITIES_COLLECTION).document(postId)
        try {
            db.runTransaction { transaction ->
                val postSnap = transaction.get(postRef)
                if (!postSnap.exists()) return@runTransaction null
                val currentLikes = (postSnap.get("likes") as? List<*>)
                    ?.filterIsInstance<String>()
                    ?.distinct()
                    ?.toMutableList()
                    ?: mutableListOf()
                if (currentLikes.contains(uid)) {
                    currentLikes.remove(uid)
                } else {
                    currentLikes.add(uid)
                }
                transaction.update(
                    postRef,
                    mapOf(
                        "likes" to currentLikes,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                null
            }.await()
            activitiesRefreshSignal.tryEmit(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, postRef.path)
            throw e
        }
    }

    override suspend fun toggleActivityReaction(postId: String, emoji: String) {
        val uid = requireUserId()
        val cleanEmoji = emoji.trim().take(16)
        if (cleanEmoji.isEmpty()) return

        val postRef = db.collection(ACTIVITIES_COLLECTION).document(postId)
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
            activitiesRefreshSignal.tryEmit(Unit)
            activityReactionsRefreshSignal.tryEmit(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, postRef.path)
            throw e
        }
    }

    override suspend fun deleteActivityPost(postId: String) {
        val uid = requireUserId()
        val postRef = db.collection(ACTIVITIES_COLLECTION).document(postId)
        val ownReactionRef = db.collection("users").document(uid)
            .collection("activity_reactions").document(postId)
        try {
            try {
                ownReactionRef.delete().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
            postRef.delete().await()
            activitiesRefreshSignal.tryEmit(Unit)
            activityReactionsRefreshSignal.tryEmit(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, postRef.path)
            throw e
        }
    }

    override fun observeActivityComments(postId: String): Flow<List<ActivityComment>> = flow {
        requireUserId()
        if (postId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "$ACTIVITIES_COLLECTION/$postId/comments"
        db.collection(ACTIVITIES_COLLECTION).document(postId).collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(100L)
            .snapshots()
            .map { snap -> snap.toObjects(ActivityComment::class.java) }
            .catch { error ->
                if (error !is CancellationException && auth.currentUser != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    override suspend fun addActivityComment(
        postId: String,
        authorProfile: UserProfile?,
        content: String
    ) {
        val uid = requireUserId()
        val trimmed = content.trim().take(500)
        require(trimmed.isNotEmpty()) { "Comment cannot be empty." }
        val commentId = "cmt_${UUID.randomUUID().toString().replace("-", "")}"
        val postRef = db.collection(ACTIVITIES_COLLECTION).document(postId)
        val commentRef = postRef.collection("comments").document(commentId)
        val payload = mutableMapOf<String, Any?>(
            "commentId" to commentId,
            "postId" to postId,
            "authorId" to uid,
            "authorName" to (authorProfile?.displayName?.trim()?.ifBlank { "Member" } ?: "Member").take(60),
            "content" to trimmed,
            "createdAt" to FieldValue.serverTimestamp()
        )
        authorProfile?.photoUrl?.trim()?.takeIf { it.isNotBlank() && it.length <= 65000 }?.let {
            payload["authorPhotoUrl"] = it
        }
        try {
            commentRef.set(payload).await()
            postRef.update(
                mapOf(
                    "commentIds" to FieldValue.arrayUnion(commentId),
                    "commentCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            activitiesRefreshSignal.tryEmit(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commentRef.path)
            throw e
        }
    }

    override suspend fun deleteActivityComment(postId: String, commentId: String) {
        requireUserId()
        val postRef = db.collection(ACTIVITIES_COLLECTION).document(postId)
        val commentRef = postRef.collection("comments").document(commentId)
        try {
            commentRef.delete().await()
            db.runTransaction { tx ->
                val postSnap = tx.get(postRef)
                val current = postSnap.getLong("commentCount") ?: 0L
                val existingIds = (postSnap.get("commentIds") as? List<*>)
                    ?.filterIsInstance<String>()
                    ?.filterNot { it == commentId }
                    ?: emptyList()
                tx.update(
                    postRef,
                    mapOf(
                        "commentIds" to existingIds,
                        "commentCount" to (current - 1L).coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                null
            }.await()
            activitiesRefreshSignal.tryEmit(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, commentRef.path)
            throw e
        }
    }

    override suspend fun shareActivityPost(postId: String) {
        requireUserId()
        if (postId.isBlank()) return
        val postRef = db.collection(ACTIVITIES_COLLECTION).document(postId)
        try {
            postRef.update(
                mapOf(
                    "shareCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            activitiesRefreshSignal.tryEmit(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, postRef.path)
        }
    }

    override suspend fun reportActivityPost(
        post: ActivityPost,
        reason: String,
        details: String?
    ) {
        val uid = requireUserId()
        val postOwnerId = post.resolvedUserId
        if (postOwnerId == uid || postOwnerId.isBlank()) {
            throw IllegalArgumentException("You cannot report your own post.")
        }
        val reportId = "areport_${UUID.randomUUID().toString().replace("-", "")}"
        val docRef = db.collection("activity_reports").document(reportId)
        val payload = mutableMapOf<String, Any?>(
            "reportId" to reportId,
            "reporterId" to uid,
            "postId" to post.resolvedPostId,
            "authorId" to postOwnerId,
            "postExcerpt" to post.content.trim().ifBlank { "[Activity photo]" }.take(500),
            "reason" to reason.trim().ifBlank { "Inappropriate content" }.take(100),
            "status" to "PENDING",
            "createdAt" to FieldValue.serverTimestamp()
        )
        details?.trim()?.takeIf { it.isNotBlank() }?.take(1000)?.let {
            payload["details"] = it
        }
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
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

            val ownActivities = try {
                db.collection(ACTIVITIES_COLLECTION).whereEqualTo("userId", uid).get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { null }
            ownActivities?.documents?.forEach { doc ->
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

            val ownGalleryPhotos = try {
                db.collection("users").document(uid).collection("gallery_photos").get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { null }
            ownGalleryPhotos?.documents?.forEach { doc ->
                try { doc.reference.delete().await() } catch (e: CancellationException) { throw e } catch (_: Exception) {}
            }

            val ownFollows = try {
                db.collection("user_follows").whereEqualTo("followerId", uid).get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { null }
            ownFollows?.documents?.forEach { doc ->
                try { doc.reference.delete().await() } catch (e: CancellationException) { throw e } catch (_: Exception) {}
            }

            val ownFavorites = try {
                db.collection("user_favorites").whereEqualTo("userId", uid).get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { null }
            ownFavorites?.documents?.forEach { doc ->
                try { doc.reference.delete().await() } catch (e: CancellationException) { throw e } catch (_: Exception) {}
            }

            val ownProfileLikes = try {
                db.collection("profile_likes").whereEqualTo("likerId", uid).get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { null }
            ownProfileLikes?.documents?.forEach { doc ->
                try { doc.reference.delete().await() } catch (e: CancellationException) { throw e } catch (_: Exception) {}
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
        private const val MEMBERS_PAGE_SIZE = 60L
        private const val MAX_CACHED_MEMBERS = 300
        private const val FEED_REFRESH_COOLDOWN_MS = 10_000L
        private const val PRESENCE_THROTTLE_MS = 15_000L
        private const val BLOCKS_LIMIT = 250L
        private const val CONVERSATIONS_LIMIT = 40L
        private const val MESSAGES_LIMIT = 100L
        private const val NOTIFICATIONS_LIMIT = 30L
        private const val FRIEND_REQUESTS_LIMIT = 150L
        private const val CALL_SESSIONS_LIMIT = 20L
        private const val ICE_CANDIDATES_LIMIT = 50L
        private const val ACTIVITIES_COLLECTION = "activities"
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
