package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val birthday: String? = null,
    val age: Int = 18,
    val gender: String = "Female",
    val country: String = "Philippines",
    val city: String? = "Manila",
    val aboutMe: String? = "",
    val personality: String? = null,
    val interests: List<String> = emptyList(),
    val isOnline: Boolean = false,
    val role: String = "member",
    @ServerTimestamp val lastActive: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserPresence(
    @DocumentId val id: String = "",
    val userId: String = "",
    val isOnline: Boolean = false,
    @ServerTimestamp val lastSeen: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val participantUids: List<String> = emptyList(),
    val lastMessage: String? = null,
    val lastMessageSenderId: String? = null,
    @ServerTimestamp val lastMessageTimestamp: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

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
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BlockRecord(
    @DocumentId val id: String = "",
    val blockId: String = "",
    val blockerId: String = "",
    val blockedId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

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

@IgnoreExtraProperties
data class NotificationRecord(
    @DocumentId val id: String = "",
    val notificationId: String = "",
    val userId: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "CHAT_MESSAGE",
    val data: String? = null,
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)
