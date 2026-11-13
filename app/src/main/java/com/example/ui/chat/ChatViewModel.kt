package com.example.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.model.UserProfile
import com.example.data.repository.SogoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CallType { NONE, AUDIO, VIDEO }
enum class CallStatus { CONNECTING, IN_CALL, ENDED }

data class CallState(
    val callType: CallType = CallType.NONE,
    val callStatus: CallStatus = CallStatus.CONNECTING,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isVideoEnabled: Boolean = true,
    val durationSeconds: Int = 0
)

data class ChatUiState(
    val partnerProfile: UserProfile? = null,
    val conversationId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val callState: CallState = CallState(),
    val isBlocked: Boolean = false,
    val reportSubmitted: Boolean = false,
    val errorMessage: String? = null
)

class ChatViewModel(
    application: Application,
    private val repository: SogoRepository = SogoRepository(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun initializeChat(partner: UserProfile) {
        _uiState.value = _uiState.value.copy(partnerProfile = partner)
        val currentUid = repository.getCurrentUserId() ?: return

        viewModelScope.launch {
            try {
                // Ensure conversation document exists
                val convId = repository.ensureConversationExists(partner.userId)
                _uiState.value = _uiState.value.copy(conversationId = convId)

                // Observe messages in real time
                repository.observeMessages(convId).collect { msgList ->
                    _uiState.value = _uiState.value.copy(messages = msgList)

                    // Mark unread messages from partner as read
                    msgList.filter { it.senderId == partner.userId && !it.isRead }.forEach { msg ->
                        repository.markMessageAsRead(convId, msg.messageId)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun onInputTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendTextMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        val convId = _uiState.value.conversationId ?: return
        val partnerId = _uiState.value.partnerProfile?.userId ?: return

        _uiState.value = _uiState.value.copy(inputText = "")
        viewModelScope.launch {
            try {
                repository.sendMessage(
                    conversationId = convId,
                    receiverId = partnerId,
                    content = text,
                    type = "TEXT"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun sendSticker(sticker: String) {
        val convId = _uiState.value.conversationId ?: return
        val partnerId = _uiState.value.partnerProfile?.userId ?: return

        viewModelScope.launch {
            try {
                repository.sendMessage(
                    conversationId = convId,
                    receiverId = partnerId,
                    content = sticker,
                    type = "STICKER"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun sendPhotoMessage(imageUrl: String, caption: String = "Sent a photo") {
        val convId = _uiState.value.conversationId ?: return
        val partnerId = _uiState.value.partnerProfile?.userId ?: return

        viewModelScope.launch {
            try {
                repository.sendMessage(
                    conversationId = convId,
                    receiverId = partnerId,
                    content = caption,
                    type = "IMAGE",
                    mediaUrl = imageUrl
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun sendVoiceMessage(durationText: String = "0:14") {
        val convId = _uiState.value.conversationId ?: return
        val partnerId = _uiState.value.partnerProfile?.userId ?: return

        viewModelScope.launch {
            try {
                repository.sendMessage(
                    conversationId = convId,
                    receiverId = partnerId,
                    content = "Voice message ($durationText)",
                    type = "VOICE"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    // --- Audio / Video Calling Interface State ---

    fun startCall(type: CallType) {
        _uiState.value = _uiState.value.copy(
            callState = CallState(
                callType = type,
                callStatus = CallStatus.CONNECTING,
                isMuted = false,
                isSpeakerOn = type == CallType.VIDEO,
                isVideoEnabled = type == CallType.VIDEO,
                durationSeconds = 0
            )
        )
    }

    fun toggleMute() {
        val curr = _uiState.value.callState
        _uiState.value = _uiState.value.copy(callState = curr.copy(isMuted = !curr.isMuted))
    }

    fun toggleSpeaker() {
        val curr = _uiState.value.callState
        _uiState.value = _uiState.value.copy(callState = curr.copy(isSpeakerOn = !curr.isSpeakerOn))
    }

    fun toggleVideo() {
        val curr = _uiState.value.callState
        _uiState.value = _uiState.value.copy(callState = curr.copy(isVideoEnabled = !curr.isVideoEnabled))
    }

    fun endCall() {
        _uiState.value = _uiState.value.copy(callState = CallState(callType = CallType.NONE))
    }

    // --- Safety: Block & Report ---

    fun blockCurrentMember(onBlocked: () -> Unit) {
        val partnerId = _uiState.value.partnerProfile?.userId ?: return
        viewModelScope.launch {
            try {
                repository.blockUser(partnerId)
                _uiState.value = _uiState.value.copy(isBlocked = true)
                onBlocked()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun reportCurrentMember(reason: String, details: String) {
        val partnerId = _uiState.value.partnerProfile?.userId ?: return
        viewModelScope.launch {
            try {
                repository.reportUser(partnerId, reason, details)
                _uiState.value = _uiState.value.copy(reportSubmitted = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }
}
