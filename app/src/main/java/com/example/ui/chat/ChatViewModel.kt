package com.example.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.model.GalleryPhoto
import com.example.data.model.UserProfile
import com.example.data.repository.SogoDataSource
import com.example.data.repository.SogoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val partnerProfile: UserProfile? = null,
    val conversationId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val partnerGalleryPhotos: List<GalleryPhoto> = emptyList(),
    val isPartnerTyping: Boolean = false,
    val inputText: String = "",
    val isSending: Boolean = false,
    val isBlocked: Boolean = false,
    val isBlockedByMe: Boolean = false,
    val reportSubmitted: Boolean = false,
    val isSubmittingContentReport: Boolean = false,
    val contentReportSubmitted: Boolean = false,
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
    private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    private val _myBlockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    private val markedReadMessageIds = mutableSetOf<String>()

    private var typingIdleJob: Job? = null
    private var lastTypingSentAtMs: Long = 0L
    private var partnerGalleryJob: Job? = null

    init {
        if (repository.getCurrentUserId() != null) {
            viewModelScope.launch {
                try {
                    repository.observeBlockedUserIds().collect { blocked ->
                        _blockedUserIds.value = blocked
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
            viewModelScope.launch {
                try {
                    repository.observeMyBlockedUserIds().collect { myBlocked ->
                        _myBlockedUserIds.value = myBlocked
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }

        // Keep isBlocked / isBlockedByMe synced reactively with active partner
        viewModelScope.launch {
            combine(
                activePartner,
                _blockedUserIds,
                _myBlockedUserIds
            ) { partner, blocked, myBlocked ->
                Triple(partner?.userId, blocked, myBlocked)
            }.collect { (partnerId, blocked, myBlocked) ->
                if (partnerId == null) return@collect
                val blockedPair = blocked.contains(partnerId)
                val blockedByMe = myBlocked.contains(partnerId)
                _uiState.update { state ->
                    if (state.partnerProfile?.userId == partnerId) {
                        state.copy(
                            isBlocked = blockedPair,
                            isBlockedByMe = blockedByMe,
                            isPartnerTyping = if (blockedPair) false else state.isPartnerTyping,
                            messages = if (blockedPair) {
                                state.messages.filterNot { it.senderId == partnerId || blocked.contains(it.senderId) }
                            } else {
                                state.messages.filterNot { blocked.contains(it.senderId) }
                            }
                        )
                    } else state
                }
            }
        }

        // Observe messages only when partner is non-null AND not blocked
        viewModelScope.launch {
            combine(
                activePartner,
                _blockedUserIds
            ) { partner, blocked ->
                val isBlocked = partner != null && blocked.contains(partner.userId)
                partner to isBlocked
            }
                .distinctUntilChanged { old, new ->
                    old.first?.userId == new.first?.userId && old.second == new.second
                }
                .flatMapLatest { (partner, isBlocked) ->
                    if (partner == null) {
                        emptyFlow()
                    } else if (isBlocked) {
                        flow {
                            emit(emptyList<ChatMessage>())
                        }
                    } else {
                        flow {
                            repository.getCurrentUserId() ?: return@flow
                            val convId = repository.ensureConversationExists(partner.userId)
                            if (activePartner.value?.userId != partner.userId) return@flow
                            _uiState.update { state ->
                                if (state.partnerProfile?.userId == partner.userId) {
                                    state.copy(conversationId = convId)
                                } else state
                            }
                            try {
                                repository.markConversationRead(convId)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {}
                            emitAll(repository.observeMessages(convId))
                        }.catch { error ->
                            if (error is CancellationException) throw error
                            if (activePartner.value?.userId == partner.userId) {
                                val blockedNow = _blockedUserIds.value.contains(partner.userId)
                                _uiState.update {
                                    it.copy(
                                        isBlocked = it.isBlocked || blockedNow,
                                        messages = if (blockedNow) emptyList() else it.messages,
                                        errorMessage = if (blockedNow) null else (error.message ?: "Unable to load messages.")
                                    )
                                }
                            }
                        }
                    }
                }.collect { msgList ->
                    val partnerId = activePartner.value?.userId ?: return@collect
                    val convId = _uiState.value.conversationId
                    val blockedNow = _blockedUserIds.value
                    val isBlockedPair = blockedNow.contains(partnerId)
                    val filteredMessages = if (isBlockedPair) {
                        emptyList()
                    } else {
                        msgList.filterNot { blockedNow.contains(it.senderId) }
                    }

                    _uiState.update { state ->
                        if (state.partnerProfile?.userId == partnerId) {
                            state.copy(
                                isBlocked = isBlockedPair,
                                messages = filteredMessages
                            )
                        } else state
                    }

                    // Do not mark messages from a blocked user as read
                    if (convId != null && !isBlockedPair) {
                        var hasUnreadIncoming = false
                        filteredMessages
                            .filter { it.senderId == partnerId && !it.isRead }
                            .forEach { msg ->
                                hasUnreadIncoming = true
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
                        if (hasUnreadIncoming) {
                            viewModelScope.launch {
                                try {
                                    repository.markConversationRead(convId)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
        }

        // Observe live partner typing indicator on the active conversation
        viewModelScope.launch {
            _uiState
                .map { it.conversationId to (it.partnerProfile?.userId to it.isBlocked) }
                .distinctUntilChanged()
                .flatMapLatest { (convId, pair) ->
                    val partnerId = pair.first
                    val isBlocked = pair.second
                    if (convId.isNullOrBlank() || partnerId.isNullOrBlank() || isBlocked) {
                        emptyFlow()
                    } else {
                        repository.observeConversation(convId)
                            .map { conv ->
                                val ts = conv?.typingByUid?.get(partnerId)?.toDate()?.time
                                ts != null && (System.currentTimeMillis() - ts) <= TYPING_TTL_MS
                            }
                            .catch { err ->
                                if (err is CancellationException) throw err
                                emit(false)
                            }
                    }
                }.collect { typing ->
                    _uiState.update { it.copy(isPartnerTyping = typing) }
                }
        }
    }

    fun initializeChat(partner: UserProfile, myProfile: UserProfile? = null) {
        val previousPartnerId = activePartner.value?.userId
        val blockedNow = _blockedUserIds.value.contains(partner.userId)
        val blockedByMe = _myBlockedUserIds.value.contains(partner.userId)
        if (previousPartnerId != partner.userId) {
            markedReadMessageIds.clear()
            typingIdleJob?.cancel()
            lastTypingSentAtMs = 0L
            _uiState.value = ChatUiState(
                partnerProfile = partner,
                isBlocked = blockedNow,
                isBlockedByMe = blockedByMe
            )
            activePartner.value = partner

            partnerGalleryJob?.cancel()
            partnerGalleryJob = viewModelScope.launch {
                try {
                    repository.observeGalleryPhotos(partner.userId).collect { photos ->
                        if (activePartner.value?.userId == partner.userId) {
                            _uiState.update { it.copy(partnerGalleryPhotos = photos) }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }

            if (!blockedNow && myProfile != null) {
                viewModelScope.launch {
                    try {
                        repository.recordProfileVisit(partner.userId, myProfile)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {}
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    partnerProfile = partner,
                    isBlocked = blockedNow,
                    isBlockedByMe = blockedByMe
                )
            }
        }
    }

    fun clearActiveChat() {
        val prevConvId = _uiState.value.conversationId
        typingIdleJob?.cancel()
        partnerGalleryJob?.cancel()
        markedReadMessageIds.clear()
        activePartner.value = null
        _uiState.value = ChatUiState()
        if (!prevConvId.isNullOrBlank()) {
            viewModelScope.launch {
                try {
                    repository.setTypingStatus(prevConvId, false)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearReportSubmitted() {
        _uiState.update { it.copy(reportSubmitted = false) }
    }

    fun clearContentReportSubmitted() {
        _uiState.update { it.copy(contentReportSubmitted = false) }
    }

    fun reportFeatureNotImplemented(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
        val convId = _uiState.value.conversationId ?: return
        if (_uiState.value.isBlocked) return

        val now = System.currentTimeMillis()
        typingIdleJob?.cancel()
        if (text.isBlank()) {
            lastTypingSentAtMs = 0L
            viewModelScope.launch {
                try {
                    repository.setTypingStatus(convId, false)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }
        } else {
            if (now - lastTypingSentAtMs >= TYPING_THROTTLE_MS) {
                lastTypingSentAtMs = now
                viewModelScope.launch {
                    try {
                        repository.setTypingStatus(convId, true)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {}
                }
            }
            typingIdleJob = viewModelScope.launch {
                delay(TYPING_IDLE_CLEAR_MS)
                lastTypingSentAtMs = 0L
                try {
                    repository.setTypingStatus(convId, false)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }
        }
    }

    fun sendTextMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        val partnerId = _uiState.value.partnerProfile?.userId ?: return
        if (_uiState.value.isBlocked || _blockedUserIds.value.contains(partnerId)) {
            _uiState.update { it.copy(errorMessage = "Messaging is restricted because this user is blocked.") }
            return
        }
        val convId = _uiState.value.conversationId ?: return

        typingIdleJob?.cancel()
        lastTypingSentAtMs = 0L
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

    fun sendImageMessage(imageDataUri: String, caption: String = "") {
        val safeUri = imageDataUri.trim()
        if (safeUri.isBlank() || safeUri.length > 65000) {
            _uiState.update { it.copy(errorMessage = "Selected image is too large or invalid.") }
            return
        }
        val partnerId = _uiState.value.partnerProfile?.userId ?: return
        if (_uiState.value.isBlocked || _blockedUserIds.value.contains(partnerId)) {
            _uiState.update { it.copy(errorMessage = "Messaging is restricted because this user is blocked.") }
            return
        }
        val convId = _uiState.value.conversationId ?: return

        _uiState.update { it.copy(isSending = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                repository.sendMessage(
                    conversationId = convId,
                    receiverId = partnerId,
                    content = caption.trim().ifBlank { "📷 Photo" },
                    type = "IMAGE",
                    mediaUrl = safeUri
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to send photo.") }
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    fun getCurrentUserId(): String? = repository.getCurrentUserId()

    fun toggleMessageReaction(messageId: String, emoji: String) {
        val partnerId = _uiState.value.partnerProfile?.userId ?: return
        if (_uiState.value.isBlocked || _blockedUserIds.value.contains(partnerId)) return
        val convId = _uiState.value.conversationId ?: return
        viewModelScope.launch {
            try {
                repository.toggleChatMessageReaction(convId, messageId, emoji)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Could not add emoji reaction.") }
            }
        }
    }

    fun sendSticker(sticker: String) {
        val partnerId = _uiState.value.partnerProfile?.userId ?: return
        if (_uiState.value.isBlocked || _blockedUserIds.value.contains(partnerId)) {
            _uiState.update { it.copy(errorMessage = "Messaging is restricted because this user is blocked.") }
            return
        }
        val convId = _uiState.value.conversationId ?: return

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

    // --- Safety: Block & Report ---

    fun blockCurrentMember(onBlocked: () -> Unit = {}) {
        val partnerId = _uiState.value.partnerProfile?.userId ?: return
        viewModelScope.launch {
            try {
                repository.blockUser(partnerId)
                _blockedUserIds.value = _blockedUserIds.value + partnerId
                _myBlockedUserIds.value = _myBlockedUserIds.value + partnerId
                _uiState.update {
                    it.copy(
                        isBlocked = true,
                        isBlockedByMe = true,
                        isPartnerTyping = false,
                        messages = emptyList(),
                        errorMessage = null
                    )
                }
                onBlocked()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to block member.") }
            }
        }
    }

    fun unblockCurrentMember() {
        val partnerId = _uiState.value.partnerProfile?.userId ?: return
        viewModelScope.launch {
            try {
                repository.unblockUser(partnerId)
                _myBlockedUserIds.value = _myBlockedUserIds.value - partnerId
                _blockedUserIds.value = _blockedUserIds.value - partnerId
                _uiState.update {
                    it.copy(
                        isBlocked = _blockedUserIds.value.contains(partnerId),
                        isBlockedByMe = false,
                        errorMessage = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to unblock member.") }
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

    fun reportMessageContent(message: ChatMessage, reason: String, details: String) {
        val convId = _uiState.value.conversationId ?: message.conversationId
        if (convId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Conversation is not ready yet. Please try again.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingContentReport = true, errorMessage = null) }
            try {
                repository.reportChatContent(
                    conversationId = convId,
                    message = message,
                    reason = reason,
                    details = details
                )
                _uiState.update { it.copy(contentReportSubmitted = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Failed to report content for moderation.")
                }
            } finally {
                _uiState.update { it.copy(isSubmittingContentReport = false) }
            }
        }
    }

    companion object {
        private const val TYPING_TTL_MS = 12_000L
        private const val TYPING_THROTTLE_MS = 3_000L
        private const val TYPING_IDLE_CLEAR_MS = 4_000L
    }
}
