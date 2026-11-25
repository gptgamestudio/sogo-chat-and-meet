package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String? = null,
    val displayName: String = "",
    val photoUrl: String? = null,
    val coverPhotoUrl: String? = null,
    val birthday: String? = null,
    val age: Int = 18,
    val gender: String = "Female",
    val country: String = "Philippines",
    val city: String? = "Manila",
    val aboutMe: String? = "",
    val personality: String? = null,
    val hobbies: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val lookingFor: String? = null,
    val preferredGender: String = "ALL",
    val preferredMinAge: Int = 18,
    val preferredMaxAge: Int = 80,
    val preferredCountry: String = "ALL",
    @get:PropertyName("isOnline") @field:PropertyName("isOnline")
    val isOnline: Boolean = false,
    val accountStatus: String = "ACTIVE", // "ACTIVE", "SUSPENDED"
    val hideFromDiscovery: Boolean = false,
    val hideOnlineStatus: Boolean = false,
    val hideAge: Boolean = false,
    val hideLocation: Boolean = false,
    val role: String = "member",
    @ServerTimestamp val lastActive: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GalleryPhoto(
    @DocumentId val id: String = "",
    val photoId: String = "",
    val userId: String = "",
    val photoUrl: String = "",
    val caption: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserPresence(
    @DocumentId val id: String = "",
    val userId: String = "",
    @get:PropertyName("isOnline") @field:PropertyName("isOnline")
    val isOnline: Boolean = false,
    @ServerTimestamp val lastSeen: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val participantUids: List<String> = emptyList(),
    val lastMessage: String? = null,
    val lastMessageSenderId: String? = null,
    val unreadCountByUid: Map<String, Long> = emptyMap(),
    val typingByUid: Map<String, Timestamp?> = emptyMap(),
    @ServerTimestamp val lastMessageTimestamp: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val messageId: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val participantUids: List<String> = emptyList(),
    val content: String = "",
    val type: String = "TEXT", // "TEXT", "IMAGE", "VOICE", "STICKER"
    val mediaUrl: String? = null,
    @get:PropertyName("isRead") @field:PropertyName("isRead")
    val isRead: Boolean = false,
    val reactions: Map<String, String> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BlockRecord(
    @DocumentId val id: String = "",
    val blockId: String = "",
    val blockerId: String = "",
    val blockedId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReportRecord(
    @DocumentId val id: String = "",
    val reportId: String = "",
    val reporterId: String = "",
    val reportedId: String = "",
    val reason: String = "",
    val details: String? = null,
    val status: String = "PENDING",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ContentReportRecord(
    @DocumentId val id: String = "",
    val reportId: String = "",
    val reporterId: String = "",
    val reportedUserId: String = "",
    val conversationId: String = "",
    val messageId: String = "",
    val messageType: String = "TEXT",
    val messageExcerpt: String = "",
    val reason: String = "",
    val details: String? = null,
    val status: String = "PENDING",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ActivityReportRecord(
    @DocumentId val id: String = "",
    val reportId: String = "",
    val reporterId: String = "",
    val postId: String = "",
    val authorId: String = "",
    val postExcerpt: String = "",
    val reason: String = "",
    val details: String? = null,
    val status: String = "PENDING",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class NotificationRecord(
    @DocumentId val id: String = "",
    val notificationId: String = "",
    val userId: String = "",
    val senderId: String? = null,
    val title: String = "",
    val body: String = "",
    val type: String = "CHAT_MESSAGE", // "CHAT_MESSAGE", "PROFILE_VISIT", "PROFILE_LIKE", "FRIEND_REQUEST", "SAY_HI", "SYSTEM"
    val data: String? = null,
    @get:PropertyName("isRead") @field:PropertyName("isRead")
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class FriendStatus {
    NONE,
    REQUEST_SENT,
    REQUEST_RECEIVED,
    FRIENDS
}

@Keep
@IgnoreExtraProperties
data class FriendRequest(
    @DocumentId val id: String = "",
    val requestId: String = "",
    val requesterId: String = "",
    val recipientId: String = "",
    val participantUids: List<String> = emptyList(),
    val status: String = "PENDING", // "PENDING", "ACCEPTED"
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FollowRecord(
    @DocumentId val id: String = "",
    val followId: String = "",
    val followerId: String = "",
    val followingId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FavoriteRecord(
    @DocumentId val id: String = "",
    val favoriteId: String = "",
    val userId: String = "",
    val targetUserId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ProfileLikeRecord(
    @DocumentId val id: String = "",
    val likeId: String = "",
    val likerId: String = "",
    val likedUserId: String = "",
    val likerName: String? = null,
    val likerPhotoUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ProfileVisitorRecord(
    @DocumentId val id: String = "",
    val visitId: String = "",
    val visitorId: String = "",
    val visitedUserId: String = "",
    val visitorName: String = "",
    val visitorPhotoUrl: String? = null,
    @ServerTimestamp val visitedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val callId: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val callerPhotoUrl: String? = null,
    val calleeId: String = "",
    val calleeName: String = "",
    val calleePhotoUrl: String? = null,
    val participantUids: List<String> = emptyList(),
    val callType: String = "VIDEO", // "VIDEO", "AUDIO"
    val status: String = "RINGING", // "RINGING", "ANSWERED", "DECLINED", "ENDED"
    val offerSdp: String = "",
    val answerSdp: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class IceCandidateRecord(
    @DocumentId val id: String = "",
    val candidateId: String = "",
    val senderId: String = "",
    val participantUids: List<String> = emptyList(),
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val candidate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ActivityPost(
    @DocumentId val id: String = "",
    val postId: String = "",
    val userId: String = "",
    val authorId: String = "",
    val username: String = "",
    val authorName: String = "",
    val authorUsername: String? = null,
    val authorPhotoUrl: String? = null,
    val authorCity: String? = null,
    val authorCountry: String? = null,
    val activityType: String = "",
    val content: String = "",
    val imageUrl: String? = null,
    val moodEmoji: String = "☕",
    val activityTag: String = "Coffee Time",
    val likes: List<String> = emptyList(),
    val commentIds: List<String> = emptyList(),
    val reactionCounts: Map<String, Long> = emptyMap(),
    val totalReactions: Long = 0L,
    val commentCount: Long = 0L,
    val shareCount: Long = 0L,
    @ServerTimestamp val timestamp: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val resolvedPostId: String get() = postId.ifBlank { id }
    val resolvedUserId: String get() = userId.ifBlank { authorId }
    val resolvedAuthorName: String get() = authorName.ifBlank { username.ifBlank { "SOGO Member" } }
    val resolvedUsername: String get() = username.ifBlank { authorUsername?.takeIf { it.isNotBlank() } ?: authorName }
    val resolvedActivityType: String get() = activityType.ifBlank { activityTag }
    val resolvedTimestamp: Timestamp? get() = timestamp ?: createdAt
}

@Keep
@IgnoreExtraProperties
data class ActivityReaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val postId: String = "",
    val emoji: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ActivityComment(
    @DocumentId val id: String = "",
    val commentId: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String? = null,
    val content: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminAuditLog(
    @DocumentId val id: String = "",
    val logId: String = "",
    val adminUid: String = "",
    val action: String = "",
    val targetUid: String? = null,
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
