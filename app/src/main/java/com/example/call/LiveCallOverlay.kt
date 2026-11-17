package com.example.call

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BurgundyDark
import com.example.ui.theme.BurgundyPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldLight
import com.example.ui.theme.MatteBlackBackground
import com.example.ui.theme.MatteBlackSurface
import com.example.ui.theme.MatteBlackSurfaceVariant
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.OutlineGold
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun LiveCallOverlay(
    callState: ActiveCallUiState,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit
) {
    if (callState.phase == CallPhase.IDLE) return

    Dialog(
        onDismissRequest = { /* Keep active call open until explicit hangup or decline */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            BurgundyDark,
                            Color(0xFF1F060D),
                            MatteBlackBackground,
                            MatteBlackBackground
                        )
                    )
                )
                .testTag("live_call_overlay")
        ) {
            // Full-screen Remote Video Stage if active video track is present
            if (callState.callType == "VIDEO" &&
                callState.eglContext != null &&
                callState.remoteVideoTrack != null
            ) {
                WebRtcVideoRenderer(
                    eglContext = callState.eglContext,
                    videoTrack = callState.remoteVideoTrack,
                    mirror = false,
                    isOverlay = false,
                    modifier = Modifier.fillMaxSize()
                )
                // Dark scrim top & bottom for clear readable controls
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.55f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.72f)
                                )
                            )
                        )
                )
            }

            // Local Picture-in-Picture Camera Preview
            if (callState.callType == "VIDEO" &&
                callState.isVideoEnabled &&
                callState.eglContext != null &&
                callState.localVideoTrack != null &&
                callState.phase != CallPhase.RINGING_INCOMING
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(top = 64.dp, end = 16.dp)
                        .width(108.dp)
                        .height(152.dp)
                        .border(2.dp, OutlineGold, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    color = MatteBlackSurface
                ) {
                    WebRtcVideoRenderer(
                        eglContext = callState.eglContext,
                        videoTrack = callState.localVideoTrack,
                        mirror = true,
                        isOverlay = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Main UI Chrome
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header with FREE badge & peer status
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = OnlineGreen.copy(alpha = 0.16f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, OnlineGreen.copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = if (callState.callType == "VIDEO") {
                                "FREE VIDEO CALL • OPEN TO ALL"
                            } else {
                                "FREE VOICE CALL • OPEN TO ALL"
                            },
                            color = OnlineGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = callState.peerName.ifBlank { "SOGO Member" },
                        color = TextWhite,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val subtitleText = if (callState.phase == CallPhase.CONNECTED && callState.durationSeconds > 0) {
                        "${callState.statusLabel} • ${formatCallDuration(callState.durationSeconds)}"
                    } else {
                        callState.statusLabel
                    }
                    Text(
                        text = subtitleText,
                        color = GoldLight,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }

                // Center Avatar Stage when remote video is not yet rendering or audio call
                if (callState.remoteVideoTrack == null || callState.callType != "VIDEO") {
                    PulsingPeerAvatar(
                        peerName = callState.peerName,
                        peerPhotoUrl = callState.peerPhotoUrl,
                        isRinging = callState.phase == CallPhase.RINGING_INCOMING || callState.phase == CallPhase.RINGING_OUTGOING
                    )
                } else {
                    Spacer(modifier = Modifier.height(1.dp))
                }

                // Bottom Controls
                if (callState.phase == CallPhase.RINGING_INCOMING) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 18.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onDecline,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(AlertRed)
                                    .testTag("decline_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "Decline Call",
                                    tint = TextWhite,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Decline", color = TextMuted, fontSize = 13.sp)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onAccept,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(OnlineGreen)
                                    .testTag("accept_call_button")
                            ) {
                                Icon(
                                    imageVector = if (callState.callType == "VIDEO") {
                                        Icons.Default.Videocam
                                    } else {
                                        Icons.Default.Call
                                    },
                                    contentDescription = "Accept Free Call",
                                    tint = MatteBlackBackground,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Answer", color = GoldLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onToggleMute,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(if (callState.isMuted) AlertRed else MatteBlackSurfaceVariant)
                                .testTag("call_mute_button")
                        ) {
                            Icon(
                                imageVector = if (callState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Toggle Microphone",
                                tint = TextWhite
                            )
                        }

                        IconButton(
                            onClick = onToggleSpeaker,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(if (callState.isSpeakerOn) GoldAccent else MatteBlackSurfaceVariant)
                                .testTag("call_speaker_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Toggle Speaker",
                                tint = if (callState.isSpeakerOn) MatteBlackBackground else TextWhite
                            )
                        }

                        if (callState.callType == "VIDEO") {
                            IconButton(
                                onClick = onToggleVideo,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(if (!callState.isVideoEnabled) AlertRed else MatteBlackSurfaceVariant)
                                    .testTag("call_video_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (callState.isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                    contentDescription = "Toggle Camera",
                                    tint = TextWhite
                                )
                            }

                            IconButton(
                                onClick = onSwitchCamera,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(MatteBlackSurfaceVariant)
                                    .testTag("call_switch_camera_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cameraswitch,
                                    contentDescription = "Switch Camera",
                                    tint = TextWhite
                                )
                            }
                        }

                        IconButton(
                            onClick = onEndCall,
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .background(AlertRed)
                                .testTag("end_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "End Call",
                                tint = TextWhite,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PulsingPeerAvatar(
    peerName: String,
    peerPhotoUrl: String?,
    isRinging: Boolean
) {
    val transition = rememberInfiniteTransition(label = "ringPulse")
    val pulse by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isRinging) 1.12f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(182.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(BurgundyPrimary.copy(alpha = 0.25f))
                .border(2.dp, OutlineGold.copy(alpha = 0.6f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(152.dp)
                .clip(CircleShape)
                .border(3.dp, GoldAccent, CircleShape)
        ) {
            if (!peerPhotoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = peerPhotoUrl,
                    contentDescription = peerName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BurgundyPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = peerName.take(1).ifBlank { "S" }.uppercase(),
                        color = GoldLight,
                        fontSize = 58.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun WebRtcVideoRenderer(
    eglContext: EglBase.Context,
    videoTrack: VideoTrack,
    mirror: Boolean,
    isOverlay: Boolean,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                init(eglContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                setMirror(mirror)
                setZOrderMediaOverlay(isOverlay)
                tag = null
            }
        },
        update = { view ->
            val previousTrack = view.tag as? VideoTrack
            if (previousTrack !== videoTrack) {
                runCatching { previousTrack?.removeSink(view) }
                runCatching { videoTrack.addSink(view) }
                view.tag = videoTrack
            }
        },
        onRelease = { view ->
            val previousTrack = view.tag as? VideoTrack
            runCatching { previousTrack?.removeSink(view) }
            view.tag = null
            runCatching { view.release() }
        }
    )
}

private fun formatCallDuration(totalSeconds: Int): String {
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return "%02d:%02d".format(mins, secs)
}
