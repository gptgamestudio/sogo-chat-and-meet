package com.example.call

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.model.CallSession
import com.example.data.model.UserProfile
import com.example.data.repository.SogoDataSource
import com.example.data.repository.SogoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.Collections
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class CallPhase {
    IDLE,
    RINGING_OUTGOING,
    RINGING_INCOMING,
    CONNECTING,
    CONNECTED
}

data class ActiveCallUiState(
    val phase: CallPhase = CallPhase.IDLE,
    val callSession: CallSession? = null,
    val callType: String = "VIDEO", // "VIDEO" or "AUDIO"
    val peerName: String = "",
    val peerPhotoUrl: String? = null,
    val isIncoming: Boolean = false,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val isVideoEnabled: Boolean = true,
    val durationSeconds: Int = 0,
    val statusLabel: String = "",
    val errorMessage: String? = null,
    val eglContext: EglBase.Context? = null,
    val localVideoTrack: VideoTrack? = null,
    val remoteVideoTrack: VideoTrack? = null
)

class CallViewModel(
    application: Application,
    private val repository: SogoDataSource
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, SogoRepository(application))

    private val _uiState = MutableStateFlow(ActiveCallUiState())
    val uiState: StateFlow<ActiveCallUiState> = _uiState.asStateFlow()

    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var iceObserverJob: Job? = null
    private var iceReconnectJob: Job? = null
    private var timerJob: Job? = null

    private val processedCandidateIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val pendingRemoteCandidates = Collections.synchronizedList(mutableListOf<IceCandidate>())
    private val dismissedCallIds = Collections.synchronizedSet(mutableSetOf<String>())

    @Volatile
    private var isRemoteDescriptionSet = false

    @Volatile
    private var isAnswerApplied = false

    @Volatile
    private var activeCallId: String? = null

    @Volatile
    private var activeParticipants: List<String> = emptyList()

    @Volatile
    private var blockedUserIds: Set<String> = emptySet()

    private var previousAudioMode: Int? = null
    private var previousSpeakerState: Boolean? = null

    init {
        if (repository.getCurrentUserId() != null) {
            observeBlockedUsers()
            observeSignalingSessions()
        }
    }

    private fun observeBlockedUsers() {
        viewModelScope.launch {
            try {
                repository.observeBlockedUserIds().collect { blocked ->
                    blockedUserIds = blocked
                    val currentSession = _uiState.value.callSession
                    if (currentSession != null) {
                        val otherUid = if (currentSession.callerId == repository.getCurrentUserId()) {
                            currentSession.calleeId
                        } else {
                            currentSession.callerId
                        }
                        if (blocked.contains(otherUid)) {
                            teardownWebRtcAndReset()
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private fun observeSignalingSessions() {
        viewModelScope.launch {
            try {
                repository.observeCallSessions().collect { sessions ->
                    handleSessionsSnapshot(sessions)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Call session listener stopped: ${e.message}")
            }
        }
    }

    private fun handleSessionsSnapshot(sessions: List<CallSession>) {
        val myUid = repository.getCurrentUserId() ?: return
        val currentCallId = activeCallId
        val currentPhase = _uiState.value.phase

        if (currentCallId != null) {
            val match = sessions.firstOrNull { it.callId == currentCallId }
            if (match == null || match.status == "ENDED" || match.status == "DECLINED") {
                val endNotice = if (match?.status == "DECLINED") {
                    "Call was declined."
                } else {
                    null
                }
                teardownWebRtcAndReset(errorNotice = endNotice)
                return
            }

            // Caller receives callee's SDP answer
            if (match.callerId == myUid &&
                match.status == "ANSWERED" &&
                !match.answerSdp.isNullOrBlank() &&
                !isAnswerApplied
            ) {
                isAnswerApplied = true
                applyRemoteAnswer(match)
            }
            return
        }

        // Only surface new incoming ringing calls when currently IDLE
        if (currentPhase == CallPhase.IDLE) {
            val nowMs = System.currentTimeMillis()
            val incoming = sessions
                .filter { session ->
                    session.calleeId == myUid &&
                        session.status == "RINGING" &&
                        session.callerId !in blockedUserIds &&
                        session.callId !in dismissedCallIds &&
                        session.offerSdp.isNotBlank() &&
                        (session.createdAt == null || (nowMs - session.createdAt.toDate().time) <= MAX_RING_AGE_MS)
                }
                .maxByOrNull { it.createdAt?.toDate()?.time ?: nowMs }

            if (incoming != null) {
                activeCallId = incoming.callId
                activeParticipants = incoming.participantUids
                _uiState.value = ActiveCallUiState(
                    phase = CallPhase.RINGING_INCOMING,
                    callSession = incoming,
                    callType = incoming.callType,
                    peerName = incoming.callerName,
                    peerPhotoUrl = incoming.callerPhotoUrl,
                    isIncoming = true,
                    isMuted = false,
                    isSpeakerOn = incoming.callType == "VIDEO",
                    isVideoEnabled = incoming.callType == "VIDEO",
                    statusLabel = if (incoming.callType == "VIDEO") {
                        "Incoming Free Video Call..."
                    } else {
                        "Incoming Free Voice Call..."
                    }
                )
            }
        }
    }

    fun startOutgoingCall(
        callee: UserProfile,
        callerProfile: UserProfile?,
        callType: String
    ) {
        val myUid = try {
            repository.requireUserId()
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.message ?: "Sign in with Google to call.") }
            return
        }
        if (callee.userId == myUid) return
        if (blockedUserIds.contains(callee.userId)) {
            _uiState.update { it.copy(errorMessage = "Calls are restricted with a blocked user.") }
            return
        }
        if (_uiState.value.phase != CallPhase.IDLE) return

        val normalizedType = if (callType == "AUDIO") "AUDIO" else "VIDEO"
        val callerName = callerProfile?.displayName?.takeIf { it.isNotBlank() } ?: "SOGO Member"
        val callerPhoto = callerProfile?.photoUrl
        val participants = listOf(myUid, callee.userId).sorted()

        activeParticipants = participants
        isRemoteDescriptionSet = false
        isAnswerApplied = false
        processedCandidateIds.clear()
        pendingRemoteCandidates.clear()

        _uiState.value = ActiveCallUiState(
            phase = CallPhase.RINGING_OUTGOING,
            callType = normalizedType,
            peerName = callee.displayName,
            peerPhotoUrl = callee.photoUrl,
            isIncoming = false,
            isMuted = false,
            isSpeakerOn = normalizedType == "VIDEO",
            isVideoEnabled = normalizedType == "VIDEO",
            statusLabel = "Calling ${callee.displayName} (Free)..."
        )

        viewModelScope.launch {
            try {
                configureAudioForCall(speakerOn = normalizedType == "VIDEO")
                initPeerConnection(enableVideo = normalizedType == "VIDEO")

                val pc = peerConnection ?: throw IllegalStateException("Could not initialize call engine.")
                val offer = createSdpOffer(pc, enableVideo = normalizedType == "VIDEO")
                setLocalSdp(pc, offer)

                val createdCallId = withContext(Dispatchers.IO) {
                    repository.createCallSession(
                        callee = callee,
                        callerName = callerName,
                        callerPhotoUrl = callerPhoto,
                        callType = normalizedType,
                        offerSdp = offer.description
                    )
                }

                // User may have cancelled while offer/doc creation was in flight
                if (_uiState.value.phase == CallPhase.IDLE) {
                    withContext(Dispatchers.IO) {
                        runCatching { repository.updateCallStatus(createdCallId, "ENDED") }
                    }
                    return@launch
                }

                activeCallId = createdCallId
                startObservingRemoteCandidates(createdCallId)
                _uiState.update {
                    it.copy(
                        statusLabel = "Ringing ${callee.displayName}..."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start free call", e)
                teardownWebRtcAndReset(
                    errorNotice = e.message ?: "Unable to start free call. Please try again."
                )
            }
        }
    }

    fun acceptIncomingCall() {
        val session = _uiState.value.callSession ?: return
        if (_uiState.value.phase != CallPhase.RINGING_INCOMING) return

        val callId = session.callId
        val enableVideo = session.callType == "VIDEO"
        activeCallId = callId
        activeParticipants = session.participantUids
        isRemoteDescriptionSet = false
        processedCandidateIds.clear()
        pendingRemoteCandidates.clear()

        _uiState.update {
            it.copy(
                phase = CallPhase.CONNECTING,
                isSpeakerOn = enableVideo,
                isVideoEnabled = enableVideo,
                statusLabel = "Connecting live call..."
            )
        }

        viewModelScope.launch {
            try {
                configureAudioForCall(speakerOn = enableVideo)
                initPeerConnection(enableVideo = enableVideo)

                val pc = peerConnection ?: throw IllegalStateException("Could not initialize call engine.")
                val remoteOffer = SessionDescription(SessionDescription.Type.OFFER, session.offerSdp)
                setRemoteSdp(pc, remoteOffer)
                isRemoteDescriptionSet = true
                drainPendingRemoteCandidates(pc)

                val answer = createSdpAnswer(pc)
                setLocalSdp(pc, answer)

                withContext(Dispatchers.IO) {
                    repository.answerCallSession(callId, answer.description)
                }
                startObservingRemoteCandidates(callId)
                startCallTimerIfNeeded()
                _uiState.update {
                    it.copy(
                        phase = CallPhase.CONNECTED,
                        statusLabel = "Live • Free P2P Call"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to answer incoming call", e)
                withContext(Dispatchers.IO) {
                    runCatching { repository.updateCallStatus(callId, "ENDED") }
                }
                teardownWebRtcAndReset(
                    errorNotice = e.message ?: "Could not connect the call."
                )
            }
        }
    }

    fun declineIncomingCall() {
        val callId = _uiState.value.callSession?.callId ?: activeCallId ?: return
        dismissedCallIds.add(callId)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.updateCallStatus(callId, "DECLINED") }
        }
        teardownWebRtcAndReset()
    }

    fun endCurrentCall() {
        val callId = activeCallId ?: _uiState.value.callSession?.callId
        if (callId != null) {
            dismissedCallIds.add(callId)
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { repository.updateCallStatus(callId, "ENDED") }
            }
        }
        teardownWebRtcAndReset()
    }

    fun toggleMute() {
        val newMuted = !_uiState.value.isMuted
        runCatching { localAudioTrack?.setEnabled(!newMuted) }
        _uiState.update { it.copy(isMuted = newMuted) }
    }

    fun toggleSpeaker() {
        val newSpeaker = !_uiState.value.isSpeakerOn
        runCatching {
            val am = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            @Suppress("DEPRECATION")
            am?.isSpeakerphoneOn = newSpeaker
        }
        _uiState.update { it.copy(isSpeakerOn = newSpeaker) }
    }

    fun toggleVideo() {
        if (_uiState.value.callType != "VIDEO") return
        val newEnabled = !_uiState.value.isVideoEnabled
        runCatching { localVideoTrack?.setEnabled(newEnabled) }
        _uiState.update { it.copy(isVideoEnabled = newEnabled) }
    }

    fun switchCamera() {
        runCatching { videoCapturer?.switchCamera(null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun reportPermissionDenied(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    private fun applyRemoteAnswer(session: CallSession) {
        val pc = peerConnection ?: return
        val answerText = session.answerSdp ?: return

        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        phase = CallPhase.CONNECTING,
                        callSession = session,
                        statusLabel = "Connecting media stream..."
                    )
                }
                val remoteAnswer = SessionDescription(SessionDescription.Type.ANSWER, answerText)
                setRemoteSdp(pc, remoteAnswer)
                isRemoteDescriptionSet = true
                drainPendingRemoteCandidates(pc)
                startCallTimerIfNeeded()
                _uiState.update {
                    it.copy(
                        phase = CallPhase.CONNECTED,
                        statusLabel = "Live • Free P2P Call"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error setting remote answer", e)
                endCurrentCall()
            }
        }
    }

    private fun startObservingRemoteCandidates(callId: String) {
        iceObserverJob?.cancel()
        val myUid = repository.getCurrentUserId()
        iceObserverJob = viewModelScope.launch {
            try {
                repository.observeIceCandidates(callId).collect { list ->
                    val pc = peerConnection ?: return@collect
                    list.filter { it.senderId != myUid }.forEach { record ->
                        if (processedCandidateIds.add(record.candidateId)) {
                            val ice = IceCandidate(record.sdpMid, record.sdpMLineIndex, record.candidate)
                            if (isRemoteDescriptionSet) {
                                runCatching { pc.addIceCandidate(ice) }
                            } else {
                                pendingRemoteCandidates.add(ice)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Candidate stream error: ${e.message}")
            }
        }
    }

    private fun drainPendingRemoteCandidates(pc: PeerConnection) {
        synchronized(pendingRemoteCandidates) {
            pendingRemoteCandidates.forEach { candidate ->
                runCatching { pc.addIceCandidate(candidate) }
            }
            pendingRemoteCandidates.clear()
        }
    }

    private fun startCallTimerIfNeeded() {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _uiState.update { it.copy(durationSeconds = it.durationSeconds + 1) }
            }
        }
    }

    private fun initPeerConnection(enableVideo: Boolean) {
        val appContext = getApplication<Application>().applicationContext

        val egl = EglBase.create()
        eglBase = egl

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        val encoderFactory = DefaultVideoEncoderFactory(egl.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(egl.eglBaseContext)
        val factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
        peerConnectionFactory = factory

        val iceServers = buildList {
            add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
            add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer())
            add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer())
            add(PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer())
            add(PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer())
            val turnUrl = runCatching { appContext.getString(R.string.webrtc_turn_url).trim() }.getOrDefault("")
            if (turnUrl.isNotBlank()) {
                val turnUser = runCatching { appContext.getString(R.string.webrtc_turn_username).trim() }.getOrDefault("")
                val turnCred = runCatching { appContext.getString(R.string.webrtc_turn_credential).trim() }.getOrDefault("")
                val builder = PeerConnection.IceServer.builder(turnUrl)
                if (turnUser.isNotBlank()) builder.setUsername(turnUser)
                if (turnCred.isNotBlank()) builder.setPassword(turnCred)
                add(builder.createIceServer())
            }
        }
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                if (newState == PeerConnection.IceConnectionState.CONNECTED ||
                    newState == PeerConnection.IceConnectionState.COMPLETED
                ) {
                    iceReconnectJob?.cancel()
                    iceReconnectJob = null
                    viewModelScope.launch {
                        startCallTimerIfNeeded()
                        _uiState.update {
                            if (it.phase != CallPhase.IDLE) {
                                it.copy(phase = CallPhase.CONNECTED, statusLabel = "Live • Free P2P Call")
                            } else it
                        }
                    }
                } else if (newState == PeerConnection.IceConnectionState.FAILED ||
                    newState == PeerConnection.IceConnectionState.DISCONNECTED
                ) {
                    viewModelScope.launch {
                        _uiState.update {
                            it.copy(statusLabel = "Reconnecting network...")
                        }
                    }
                    if (iceReconnectJob?.isActive != true) {
                        iceReconnectJob = viewModelScope.launch {
                            delay(ICE_RECONNECT_TIMEOUT_MS)
                            val callId = activeCallId
                            if (callId != null && _uiState.value.phase != CallPhase.IDLE) {
                                runCatching { repository.updateCallStatus(callId, "ENDED") }
                                teardownWebRtcAndReset("Call ended: peer network unreachable (strict NAT/Wi-Fi). Please try again.")
                            }
                        }
                    }
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) = Unit

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null) return
                val callId = activeCallId ?: return
                val participants = activeParticipants
                if (participants.size != 2) return
                viewModelScope.launch(Dispatchers.IO) {
                    repository.addIceCandidate(
                        callId = callId,
                        participantUids = participants,
                        sdpMid = candidate.sdpMid ?: "0",
                        sdpMLineIndex = candidate.sdpMLineIndex,
                        candidate = candidate.sdp
                    )
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit

            override fun onAddStream(stream: MediaStream?) {
                val track = stream?.videoTracks?.firstOrNull() ?: return
                viewModelScope.launch {
                    _uiState.update { it.copy(remoteVideoTrack = track) }
                }
            }

            override fun onRemoveStream(stream: MediaStream?) = Unit
            override fun onDataChannel(dataChannel: DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit

            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) {
                    viewModelScope.launch {
                        _uiState.update { it.copy(remoteVideoTrack = track) }
                    }
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) {
                    viewModelScope.launch {
                        _uiState.update { it.copy(remoteVideoTrack = track) }
                    }
                }
            }
        }

        val pc = factory.createPeerConnection(rtcConfig, observer)
            ?: throw IllegalStateException("PeerConnection creation failed.")
        peerConnection = pc

        val audioSrc = factory.createAudioSource(MediaConstraints())
        audioSource = audioSrc
        val audioTrack = factory.createAudioTrack("ARDAMSa0", audioSrc).apply { setEnabled(true) }
        localAudioTrack = audioTrack
        pc.addTrack(audioTrack, listOf("ARDAMS"))

        if (enableVideo) {
            val capturer = createCameraCapturer(appContext)
            videoCapturer = capturer
            if (capturer != null) {
                val helper = SurfaceTextureHelper.create("SogoCaptureThread", egl.eglBaseContext)
                surfaceTextureHelper = helper
                val vSource = factory.createVideoSource(capturer.isScreencast)
                videoSource = vSource
                capturer.initialize(helper, appContext, vSource.capturerObserver)
                runCatching { capturer.startCapture(640, 480, 24) }
                val vTrack = factory.createVideoTrack("ARDAMSv0", vSource).apply { setEnabled(true) }
                localVideoTrack = vTrack
                pc.addTrack(vTrack, listOf("ARDAMS"))
            }
        }

        _uiState.update {
            it.copy(
                eglContext = egl.eglBaseContext,
                localVideoTrack = localVideoTrack
            )
        }
    }

    private fun createCameraCapturer(context: Context): CameraVideoCapturer? {
        return runCatching {
            val enumerator: CameraEnumerator = if (Camera2Enumerator.isSupported(context)) {
                Camera2Enumerator(context)
            } else {
                Camera1Enumerator(false)
            }
            val deviceNames = enumerator.deviceNames
            val frontCamera = deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: deviceNames.firstOrNull()
            frontCamera?.let { enumerator.createCapturer(it, null) }
        }.getOrNull()
    }

    private fun configureAudioForCall(speakerOn: Boolean) {
        runCatching {
            val am = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null) {
                if (previousAudioMode == null) {
                    previousAudioMode = am.mode
                }
                if (previousSpeakerState == null) {
                    @Suppress("DEPRECATION")
                    previousSpeakerState = am.isSpeakerphoneOn
                }
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = speakerOn
            }
        }
    }

    private fun restoreAudioState() {
        runCatching {
            val am = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null) {
                previousAudioMode?.let { am.mode = it }
                previousSpeakerState?.let {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = it
                }
            }
        }
        previousAudioMode = null
        previousSpeakerState = null
    }

    private suspend fun createSdpOffer(
        pc: PeerConnection,
        enableVideo: Boolean
    ): SessionDescription = suspendCancellableCoroutine { cont ->
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (enableVideo) "true" else "false"))
        }
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp != null && cont.isActive) cont.resume(sdp)
                else if (cont.isActive) cont.resumeWithException(IllegalStateException("Null SDP offer"))
            }
            override fun onCreateFailure(error: String?) {
                if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "Create offer failed"))
            }
            override fun onSetSuccess() = Unit
            override fun onSetFailure(error: String?) = Unit
        }, constraints)
    }

    private suspend fun createSdpAnswer(pc: PeerConnection): SessionDescription =
        suspendCancellableCoroutine { cont ->
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
            pc.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp != null && cont.isActive) cont.resume(sdp)
                    else if (cont.isActive) cont.resumeWithException(IllegalStateException("Null SDP answer"))
                }
                override fun onCreateFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "Create answer failed"))
                }
                override fun onSetSuccess() = Unit
                override fun onSetFailure(error: String?) = Unit
            }, constraints)
        }

    private suspend fun setLocalSdp(pc: PeerConnection, sdp: SessionDescription) =
        suspendCancellableCoroutine<Unit> { cont ->
            pc.setLocalDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onSetFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "Set local SDP failed"))
                }
                override fun onCreateSuccess(p0: SessionDescription?) = Unit
                override fun onCreateFailure(p0: String?) = Unit
            }, sdp)
        }

    private suspend fun setRemoteSdp(pc: PeerConnection, sdp: SessionDescription) =
        suspendCancellableCoroutine<Unit> { cont ->
            pc.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onSetFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "Set remote SDP failed"))
                }
                override fun onCreateSuccess(p0: SessionDescription?) = Unit
                override fun onCreateFailure(p0: String?) = Unit
            }, sdp)
        }

    private fun teardownWebRtcAndReset(errorNotice: String? = null) {
        iceObserverJob?.cancel()
        iceObserverJob = null
        iceReconnectJob?.cancel()
        iceReconnectJob = null
        timerJob?.cancel()
        timerJob = null

        activeCallId = null
        activeParticipants = emptyList()
        isRemoteDescriptionSet = false
        isAnswerApplied = false
        processedCandidateIds.clear()
        pendingRemoteCandidates.clear()

        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        videoCapturer = null

        runCatching { localVideoTrack?.dispose() }
        localVideoTrack = null

        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null

        runCatching { videoSource?.dispose() }
        videoSource = null

        runCatching { audioSource?.dispose() }
        audioSource = null

        runCatching { surfaceTextureHelper?.dispose() }
        surfaceTextureHelper = null

        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        peerConnection = null

        runCatching { peerConnectionFactory?.dispose() }
        peerConnectionFactory = null

        runCatching { eglBase?.release() }
        eglBase = null

        restoreAudioState()

        _uiState.value = ActiveCallUiState(
            phase = CallPhase.IDLE,
            errorMessage = errorNotice
        )
    }

    override fun onCleared() {
        val remainingId = activeCallId
        if (remainingId != null) {
            runCatching {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    runCatching { repository.updateCallStatus(remainingId, "ENDED") }
                }
            }
        }
        teardownWebRtcAndReset()
        super.onCleared()
    }

    companion object {
        private const val TAG = "SogoWebRtcCall"
        private const val MAX_RING_AGE_MS = 5 * 60 * 1000L
        private const val ICE_RECONNECT_TIMEOUT_MS = 18_000L
    }
}
