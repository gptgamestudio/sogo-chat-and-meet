package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ads.findActivity
import com.example.data.model.UserProfile
import com.example.ui.auth.AuthScreen
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
import com.google.firebase.auth.FirebaseAuth
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
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null) {
        // Render login UI with Google Sign-In button
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        val uid = currentUser!!.uid
        // Key by uid so signing out or switching accounts resets Compose UI state
        key(uid) {
            PostLoginFlow(
                currentUserId = uid,
                onSignOut = {
                    currentUser = null
                }
            )
        }
    }
}

@Composable
fun PostLoginFlow(
    currentUserId: String,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val dashboardViewModel: MainDashboardViewModel = viewModel(key = "dashboard_$currentUserId")
    val uiState by dashboardViewModel.uiState.collectAsState()

    when {
        // 1. Loading state while checking Firestore profile
        uiState.isLoading && uiState.currentUserProfile == null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0E11)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = PhilippineGold,
                        modifier = Modifier.size(44.dp)
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
                        text = "Connecting to secure member network...",
                        color = SecondaryMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 2. User has no profile in Firestore -> Display Member Profile Setup
        uiState.currentUserProfile == null -> {
            MemberProfileSetupScreen(
                initialProfile = null,
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

        // 3. User profile exists -> Maintain exactly TWO main navigation destinations
        else -> {
            TwoDestinationNavigator(
                currentUserId = currentUserId,
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

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val adMobManager = remember(context) {
        (context.applicationContext as? SogoApplication)?.adMobManager
    }
    val adStatusMessage by (adMobManager?.adStatusMessage?.collectAsState()
        ?: remember { mutableStateOf(null) })
    val rewardCredits by (adMobManager?.rewardCredits?.collectAsState()
        ?: remember { mutableStateOf(0) })

    // Navigation state between the exactly TWO main navigation destinations:
    // null -> SCREEN 1: Main Dashboard
    // non-null -> SCREEN 2: Personal Profile and Private Chat
    var selectedPartner by remember { mutableStateOf<UserProfile?>(null) }

    if (selectedPartner == null) {
        // SCREEN 1 — MAIN DASHBOARD
        MainDashboardScreen(
            viewModel = dashboardViewModel,
            onSelectMember = { member ->
                selectedPartner = member
            },
            coroutineScope = coroutineScope,
            onSignOut = onSignOut,
            adStatusMessage = adStatusMessage,
            rewardCredits = rewardCredits,
            onDismissAdStatus = { adMobManager?.clearStatusMessage() },
            onWatchRewardedAd = {
                adMobManager?.showRewardedAd(activity)
            }
        )
    } else {
        // SCREEN 2 — PERSONAL PROFILE AND PRIVATE CHAT
        val partner = selectedPartner!!
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
}
