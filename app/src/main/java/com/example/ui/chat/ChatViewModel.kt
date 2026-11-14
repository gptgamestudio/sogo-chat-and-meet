package com.example.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.model.UserProfile
import com.example.data.repository.SogoDataSource
import com.example.data.repository.SogoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CallType { NONE, AUDIO, VIDEO }
enum class CallStatus { NOT_IMPLEMENTED, CONNECTING, IN_CALL, ENDED }

data class CallState(
    val callType: CallType = CallType.NONE,
    val callStatus: CallStatus = CallStatus.NOT_IMPLEMENTED,
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

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    application: Application,
    private val repository: SogoDataSource
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, SogoRepository(application))

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val activePartner = MutableStateFlow<UserProfile?>(null)
    private val markedReadMessageIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            activePartner.flatMapLatest { partner ->
                if (partner == null) {
                    emptyFlow()
                } else {
                    flow {
                        repository.getCurrentUserId() ?: return@flow
                        val convId = repository.ensureConversationExists(partner.userId)
                        // Verify active partner has not changed before applying conversationId
                        if (activePartner.value?.userId != partner.userId) return@flow
                        _uiState.update { state ->
                            if (state.partnerProfile?.userId == partner.userId) {
                                state.copy(conversationId = convId)
                            } else state
                        }
                        emitAll(repository.observeMessages(convId))
                    }.catch { error ->
                        if (error is CancellationException) throw error
                        if (activePartner.value?.userId == partner.userId) {
                            _uiState.update { it.copy(errorMessage = error.message ?: "Unable to load messages.") }
                        }
                    }
                }
            }.collect { msgList ->
                val partnerId = activePartner.value?.userId ?: return@collect
                val convId = _uiState.value.conversationId ?: return@collect
                _uiState.update { state ->
                    if (state.partnerProfile?.userId == partnerId) {
                        state.copy(messages = msgList)
                    } else state
                }

                // Idempotently mark unread partner messages as read once per messageId
                msgList
                    .filter { it.senderId == partnerId && !it.isRead }
                    .forEach { msg ->
                        if (markedReadMessageIds.add(msg.messageId)) {
                            viewModelScope.launch {
                                try {
                                    repository.markMessageAsRead(convId, msg.messageId)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    markedReadMessageIds.remove(msg.messageId)
                                }
                            }
                        }
                    }
            }
        }
    }

    fun initializeChat(partner: UserProfile) {
        val previousPartnerId = activePartner.value?.userId
        if (previousPartnerId != partner.userId) {
            markedReadMessageIds.clear()
            _uiState.value = ChatUiState(partnerProfile = partner)
            activePartner.value = partner
        } else {
            _uiState.update { it.copy(partnerProfile = partner) }
        }
    }

    fun clearActiveChat() {
        markedReadMessageIds.clear()
        activePartner.value = null
        _uiState.value = ChatUiState()
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun reportFeatureNotImplemented(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendTextMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        val convId = _uiState.value.conversationId ?: return
        val partnerId = _uiState.value.partnerProfile?.userId ?: return

        _uiState.update { it.copy(inputText = "", isSending = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                repository.sendMessage(
                    conversationId = convId,
                    receiverId = partnerId,
                    content = text,
                    type = "TEXT"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to send message.") }
            } finally {
                _uiState.update { it.copy(isSending = false) }
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to send sticker.") }
            }
        }
    }

    // --- Audio / Video Calling Interface State (Signaling not yet implemented) ---

    fun startCall(type: CallType) {
        _uiState.update {
            it.copy(
                callState = CallState(
                    callType = type,
                    callStatus = CallStatus.NOT_IMPLEMENTED,
                    isMuted = false,
                    isSpeakerOn = type == CallType.VIDEO,
                    isVideoEnabled = type == CallType.VIDEO,
                    durationSeconds = 0
                )
            )
        }
    }

    fun toggleMute() {
        val curr = _uiState.value.callState
        _uiState.update { it.copy(callState = curr.copy(isMuted = !curr.isMuted)) }
    }

    fun toggleSpeaker() {
        val curr = _uiState.value.callState
        _uiState.update { it.copy(callState = curr.copy(isSpeakerOn = !curr.isSpeakerOn)) }
    }

    fun toggleVideo() {
        val curr = _uiState.value.callState
        _uiState.update { it.copy(callState = curr.copy(isVideoEnabled = !curr.isVideoEnabled)) }
    }

    fun endCall() {
        _uiState.update { it.copy(callState = CallState(callType = CallType.NONE)) }
    }

    // --- Safety: Block & Report ---

    fun blockCurrentMember(onBlocked: () -> Unit) {
        val partnerId = _uiState.value.partnerProfile?.userId ?: return
        viewModelScope.launch {
            try {
                repository.blockUser(partnerId)
                _uiState.update { it.copy(isBlocked = true) }
                onBlocked()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to block member.") }
            }
        }
    }

    fun reportCurrentMember(reason: String, details: String) {
        val partnerId = _uiState.value.partnerProfile?.userId ?: return
        viewModelScope.launch {
            try {
                repository.reportUser(partnerId, reason, details)
                _uiState.update { it.copy(reportSubmitted = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to submit report.") }
            }
        }
    }
}
