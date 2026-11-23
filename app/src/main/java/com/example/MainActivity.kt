package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ads.MovableBottomAdBanner
import com.example.ads.findActivity
import com.example.call.CallPhase
import com.example.call.CallViewModel
import com.example.call.LiveCallOverlay
import com.example.data.model.FriendStatus
import com.example.data.model.UserProfile
import com.example.ui.admin.OwnerAdminScreen
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.FirebaseAuthListener
import com.example.ui.auth.signOut
import com.example.ui.chat.ChatViewModel
import com.example.ui.chat.ProfileAndChatScreen
import com.example.ui.dashboard.MainDashboardScreen
import com.example.ui.dashboard.MainDashboardViewModel
import com.example.ui.profile.MemberProfileSetupScreen
import com.example.ui.theme.MatteBlackBackground
import com.example.ui.theme.OffWhiteText
import com.example.ui.theme.PhilippineGold
import com.example.ui.theme.SecondaryMuted
import com.example.ui.theme.SogoChatTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SogoChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MatteBlackBackground
                ) {
                    AppContent()
                }
            }
        }
    }
}

@Composable
fun AppContent() {
    val authListener = remember { FirebaseAuthListener(Firebase.auth) }
    val authSession by authListener.state.collectAsState()

    DisposableEffect(authListener) {
        authListener.start()
        onDispose {
            authListener.stop()
        }
    }

    LaunchedEffect(authSession.userId, authSession.sessionNonce, authSession.lastTransitionSource) {
        Log.d(
            "MainActivityAuth",
            "Auth session state: authenticated=${authSession.isAuthenticated}, " +
                "sessionNonce=${authSession.sessionNonce}, source=${authSession.lastTransitionSource}"
        )
    }

    val activeUid = authSession.userId
    if (activeUid == null) {
        // Render login UI with Google Sign-In button
        AuthScreen(
            onAuthSuccess = {
                authListener.syncNow("google_sign_in_callback")
            }
        )
    } else {
        // Key by uid + session nonce so signing out or re-signing-in starts fresh observers
        key(activeUid, authSession.sessionNonce) {
            PostLoginFlow(
                currentUserId = activeUid,
                sessionNonce = authSession.sessionNonce,
                googleDisplayName = authSession.displayName.orEmpty(),
                googlePhotoUrl = authSession.photoUrl,
                onSignOut = {
                    authListener.markSignedOut("user_sign_out")
                }
            )
        }
    }
}

@Composable
fun PostLoginFlow(
    currentUserId: String,
    sessionNonce: Int,
    googleDisplayName: String = "",
    googlePhotoUrl: String? = null,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val dashboardViewModel: MainDashboardViewModel = viewModel(
        key = "dashboard_${currentUserId}_$sessionNonce"
    )
    LaunchedEffect(currentUserId, sessionNonce) {
        dashboardViewModel.ensureSessionStarted(currentUserId)
    }
    val uiState by dashboardViewModel.uiState.collectAsState()

    val postLoginDestination = when {
        (!uiState.hasResolvedProfile && uiState.profileLoadError == null) ||
            (uiState.isLoading && uiState.currentUserProfile == null && uiState.profileLoadError == null) -> "loading_profile"
        uiState.profileLoadError != null && uiState.currentUserProfile == null -> "profile_load_error"
        uiState.currentUserProfile == null -> "member_profile_setup"
        else -> "main_dashboard"
    }

    LaunchedEffect(postLoginDestination, sessionNonce) {
        Log.d(
            "MainActivityAuth",
            "PostLoginFlow destination=$postLoginDestination (sessionNonce=$sessionNonce, " +
                "hasResolvedProfile=${uiState.hasResolvedProfile}, hasProfileError=${uiState.profileLoadError != null})"
        )
    }

    when (postLoginDestination) {
        // 1. Loading state while checking Firestore profile for this signed-in session
        "loading_profile" -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0E11)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = PhilippineGold,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("dashboard_loading_progress")
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "SOGO CHAT AND MEET",
                        color = PhilippineGold,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Validating account & opening dashboard...",
                        color = SecondaryMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 2. Profile lookup failed (e.g., transient network/permission hiccup) -> Let user retry without losing existing profile
        "profile_load_error" -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0E11))
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Unable to Open Member Dashboard",
                        color = PhilippineGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = uiState.profileLoadError ?: "Could not verify your SOGO profile right now.",
                        color = OffWhiteText,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                signOut(credentialManager, coroutineScope, onSignOut)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sign Out", color = OffWhiteText)
                        }
                        Button(
                            onClick = { dashboardViewModel.retrySession(currentUserId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PhilippineGold,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. Verified user has no profile in Firestore -> Display Member Profile Setup
        "member_profile_setup" -> {
            MemberProfileSetupScreen(
                initialProfile = null,
                defaultDisplayName = googleDisplayName,
                defaultPhotoUrl = googlePhotoUrl,
                isSaving = uiState.isSavingProfile,
                externalErrorMessage = uiState.errorMessage,
                onDismissError = { dashboardViewModel.clearError() },
                onSaveProfile = { name, bday, age, gen, country, city, photo, about, personality, interests ->
                    dashboardViewModel.createOrUpdateProfile(
                        displayName = name,
                        birthday = bday,
                        age = age,
                        gender = gen,
                        country = country,
                        city = city,
                        photoUrl = photo,
                        aboutMe = about,
                        personality = personality,
                        interests = interests
                    )
                },
                onSignOut = {
                    signOut(credentialManager, coroutineScope, onSignOut)
                }
            )
        }

        // 4. User profile exists -> Maintain exactly TWO main navigation destinations
        else -> {
            TwoDestinationNavigator(
                currentUserId = currentUserId,
                sessionNonce = sessionNonce,
                dashboardViewModel = dashboardViewModel,
                onSignOut = {
                    dashboardViewModel.setPresence(false, force = true)
                    signOut(credentialManager, coroutineScope, onSignOut)
                }
            )
        }
    }
}

@Composable
fun TwoDestinationNavigator(
    currentUserId: String,
    sessionNonce: Int,
    dashboardViewModel: MainDashboardViewModel,
    onSignOut: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // Lifecycle-based Real-time Online Status (active only once profile exists)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, currentUserId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    dashboardViewModel.setPresence(true)
                }
                Lifecycle.Event.ON_STOP -> {
                    dashboardViewModel.setPresence(false)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            dashboardViewModel.setPresence(false)
        }
    }

    LaunchedEffect(lifecycleOwner, currentUserId) {
        while (true) {
            kotlinx.coroutines.delay(90_000L)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                dashboardViewModel.setPresence(true, force = true)
            }
        }
    }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val adMobManager = remember(context) {
        (context.applicationContext as? SogoApplication)?.adMobManager
    }
    val adStatusMessage by (adMobManager?.adStatusMessage?.collectAsState()
        ?: remember { mutableStateOf(null) })
    val rewardCredits by (adMobManager?.rewardCredits?.collectAsState()
        ?: remember { mutableStateOf(0) })

    val dashboardState by dashboardViewModel.uiState.collectAsState()
    val callViewModel: CallViewModel = viewModel(key = "call_${currentUserId}_$sessionNonce")
    val callUiState by callViewModel.uiState.collectAsState()

    var pendingOutgoingCall by remember { mutableStateOf<Pair<UserProfile, String>?>(null) }
    var pendingIncomingAccept by remember { mutableStateOf(false) }
    var previousCallPhase by remember { mutableStateOf(CallPhase.IDLE) }

    LaunchedEffect(callUiState.phase) {
        val prev = previousCallPhase
        val curr = callUiState.phase
        previousCallPhase = curr
        val completedCallPhase = prev == CallPhase.CONNECTED ||
            prev == CallPhase.CONNECTING ||
            prev == CallPhase.RINGING_OUTGOING
        if (completedCallPhase && curr == CallPhase.IDLE) {
            adMobManager?.showInterstitialIfEligible(activity) {}
        }
    }

    LaunchedEffect(callUiState.errorMessage) {
        val msg = callUiState.errorMessage
        if (!msg.isNullOrBlank()) {
            dashboardViewModel.reportError(msg)
            callViewModel.clearError()
        }
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val hasMic = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val outgoing = pendingOutgoingCall
        val accepting = pendingIncomingAccept
        pendingOutgoingCall = null
        pendingIncomingAccept = false

        if (accepting) {
            val needCamera = callUiState.callType == "VIDEO"
            val hasCamera = !needCamera || grants[Manifest.permission.CAMERA] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (hasMic && hasCamera) {
                callViewModel.acceptIncomingCall()
            } else {
                val msg = if (needCamera) {
                    "Camera and microphone permissions are needed to answer a video call."
                } else {
                    "Microphone permission is needed to answer a voice call."
                }
                callViewModel.reportPermissionDenied(msg)
                callViewModel.declineIncomingCall()
            }
        } else if (outgoing != null) {
            val (callee, callType) = outgoing
            val needCamera = callType == "VIDEO"
            val hasCamera = !needCamera || grants[Manifest.permission.CAMERA] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (hasMic && hasCamera) {
                callViewModel.startOutgoingCall(
                    callee = callee,
                    callerProfile = dashboardState.currentUserProfile,
                    callType = callType
                )
            } else {
                val msg = if (needCamera) {
                    "Please grant Microphone & Camera permissions for free video calling."
                } else {
                    "Please grant Microphone permission for free voice calling."
                }
                dashboardViewModel.reportError(msg)
            }
        }
    }

    val launchOutgoingCallWithPermission: (UserProfile, String) -> Unit = { callee, callType ->
        val needed = if (callType == "VIDEO") {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            callViewModel.startOutgoingCall(
                callee = callee,
                callerProfile = dashboardState.currentUserProfile,
                callType = callType
            )
        } else {
            pendingIncomingAccept = false
            pendingOutgoingCall = callee to callType
            callPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    val acceptIncomingCallWithPermission: () -> Unit = {
        val needed = if (callUiState.callType == "VIDEO") {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            callViewModel.acceptIncomingCall()
        } else {
            pendingOutgoingCall = null
            pendingIncomingAccept = true
            callPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    // Navigation state between the exactly TWO main navigation destinations:
    // null -> SCREEN 1: Main Dashboard
    // non-null -> SCREEN 2: Personal Profile and Private Chat
    var selectedPartner by remember { mutableStateOf<UserProfile?>(null) }
    var showOwnerAdminDashboard by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (selectedPartner == null && showOwnerAdminDashboard) {
            OwnerAdminScreen(
                onBack = { showOwnerAdminDashboard = false }
            )
        } else if (selectedPartner == null) {
            // SCREEN 1 — MAIN DASHBOARD
            MainDashboardScreen(
                viewModel = dashboardViewModel,
                onSelectMember = { member ->
                    showOwnerAdminDashboard = false
                    selectedPartner = member
                },
                onVideoCallMember = { member ->
                    launchOutgoingCallWithPermission(member, "VIDEO")
                },
                coroutineScope = coroutineScope,
                onSignOut = {
                    showOwnerAdminDashboard = false
                    onSignOut()
                },
                adStatusMessage = adStatusMessage,
                rewardCredits = rewardCredits,
                onDismissAdStatus = { adMobManager?.clearStatusMessage() },
                onWatchRewardedAd = {
                    adMobManager?.showRewardedAd(activity)
                },
                onOpenOwnerAdmin = {
                    if (dashboardState.isOwnerCandidate) {
                        showOwnerAdminDashboard = true
                    }
                }
            )
        } else {
            // SCREEN 2 — PERSONAL PROFILE AND PRIVATE CHAT
            val partner = selectedPartner!!
            val partnerFriendStatus = dashboardState.friendStatusByUserId[partner.userId] ?: FriendStatus.NONE
            key(partner.userId) {
                val chatViewModel: ChatViewModel = viewModel(
                    key = "chat_${currentUserId}_${partner.userId}"
                )
                DisposableEffect(partner.userId) {
                    onDispose {
                        chatViewModel.clearActiveChat()
                    }
                }
                ProfileAndChatScreen(
                    partner = partner,
                    viewModel = chatViewModel,
                    myProfile = dashboardState.currentUserProfile,
                    friendStatus = partnerFriendStatus,
                    isFollowing = dashboardState.followingIds.contains(partner.userId),
                    isFavorited = dashboardState.favoriteIds.contains(partner.userId),
                    isLiked = dashboardState.profileLikesGivenIds.contains(partner.userId),
                    onFriendActionClick = {
                        when (partnerFriendStatus) {
                            FriendStatus.NONE -> dashboardViewModel.sendFriendRequest(partner.userId)
                            FriendStatus.REQUEST_RECEIVED -> dashboardViewModel.acceptFriendRequest(partner.userId)
                            FriendStatus.REQUEST_SENT,
                            FriendStatus.FRIENDS -> dashboardViewModel.removeFriendOrCancel(partner.userId)
                        }
                    },
                    onToggleFollow = { dashboardViewModel.toggleFollow(partner.userId) },
                    onToggleFavorite = { dashboardViewModel.toggleFavorite(partner.userId) },
                    onToggleLike = { dashboardViewModel.toggleProfileLike(partner) },
                    onSayHi = { dashboardViewModel.sendSayHi(partner) },
                    onStartAudioCall = { launchOutgoingCallWithPermission(partner, "AUDIO") },
                    onStartVideoCall = { launchOutgoingCallWithPermission(partner, "VIDEO") },
                    onBack = {
                        chatViewModel.clearActiveChat()
                        if (adMobManager != null) {
                            adMobManager.showInterstitialIfEligible(activity) {
                                selectedPartner = null
                            }
                        } else {
                            selectedPartner = null
                        }
                    }
                )
            }
        }

        if (adMobManager != null && callUiState.phase == CallPhase.IDLE) {
            MovableBottomAdBanner(
                bannerUnitId = adMobManager.bannerUnitId,
                defaultBottomPadding = if (selectedPartner == null && !showOwnerAdminDashboard) 76.dp else 0.dp
            )
        }

        if (callUiState.phase != CallPhase.IDLE) {
            LiveCallOverlay(
                callState = callUiState,
                onAccept = acceptIncomingCallWithPermission,
                onDecline = { callViewModel.declineIncomingCall() },
                onEndCall = { callViewModel.endCurrentCall() },
                onToggleMute = { callViewModel.toggleMute() },
                onToggleSpeaker = { callViewModel.toggleSpeaker() },
                onToggleVideo = { callViewModel.toggleVideo() },
                onSwitchCamera = { callViewModel.switchCamera() }
            )
        }
    }
}
