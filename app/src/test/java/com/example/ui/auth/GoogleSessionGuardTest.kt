package com.example.ui.auth

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.os.CancellationSignal
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateCredentialRequest
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PrepareGetCredentialResponse
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.ClearCredentialUnknownException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.OwnerDashboardAggregator
import com.example.data.repository.SogoRepository
import com.example.ui.theme.SogoChatTheme
import com.example.SessionGateContent
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GoogleSessionGuardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var fbApp: FirebaseApp
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private class MutableAuthSessionSource(
        var uid: String? = null,
        var displayName: String? = null,
        var photoUrl: String? = null,
        var email: String? = null,
        var emailVerified: Boolean = false,
        var isGoogleSignIn: Boolean = false
    ) : AuthSessionSource {
        private var callback: ((String) -> Unit)? = null

        override fun currentSnapshotData(): Triple<String?, String?, String?> =
            Triple(uid, displayName, photoUrl)

        override fun currentProviderSnapshot(): AuthProviderSnapshot = AuthProviderSnapshot(
            userId = uid,
            displayName = displayName,
            photoUrl = photoUrl,
            email = email,
            emailVerified = emailVerified,
            isGoogleSignIn = isGoogleSignIn
        )

        override fun registerListeners(onChanged: (source: String) -> Unit): () -> Unit {
            callback = onChanged
            return { callback = null }
        }

        fun emitChange(source: String = "firebase_auth_state_listener") {
            callback?.invoke(source)
        }
    }

    private class FakeClearCredentialManager(
        private val shouldFailClear: Boolean = false
    ) : CredentialManager {
        var clearCalls = 0

        override fun getCredentialAsync(
            context: Context,
            request: GetCredentialRequest,
            cancellationSignal: CancellationSignal?,
            executor: Executor,
            callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>
        ) = Unit

        override fun getCredentialAsync(
            context: Context,
            pendingGetCredentialHandle: PrepareGetCredentialResponse.PendingGetCredentialHandle,
            cancellationSignal: CancellationSignal?,
            executor: Executor,
            callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>
        ) = Unit

        override fun prepareGetCredentialAsync(
            request: GetCredentialRequest,
            cancellationSignal: CancellationSignal?,
            executor: Executor,
            callback: CredentialManagerCallback<PrepareGetCredentialResponse, GetCredentialException>
        ) = Unit

        override fun createCredentialAsync(
            context: Context,
            request: CreateCredentialRequest,
            cancellationSignal: CancellationSignal?,
            executor: Executor,
            callback: CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException>
        ) = Unit

        override fun clearCredentialStateAsync(
            request: ClearCredentialStateRequest,
            cancellationSignal: CancellationSignal?,
            executor: Executor,
            callback: CredentialManagerCallback<Void?, ClearCredentialException>
        ) {
            clearCalls++
            if (shouldFailClear) {
                callback.onError(ClearCredentialUnknownException("simulated clear failure"))
            } else {
                callback.onResult(null)
            }
        }

        override fun createSettingsPendingIntent(): PendingIntent {
            throw UnsupportedOperationException("Not needed in unit test")
        }
    }

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        val context = app as Context
        fbApp = FirebaseApp.getApps(context).firstOrNull { it.name == "google_session_guard_test_app" }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setProjectId("sogo-guard-test")
                    .setApplicationId("1:1234567890:android:abcdef123456")
                    .setApiKey("AIzaSyTestOnlyPlaceholderKey1234567890")
                    .build(),
                "google_session_guard_test_app"
            )
        firebaseAuth = FirebaseAuth.getInstance(fbApp)
        firestore = FirebaseFirestore.getInstance(fbApp)
    }

    @Test
    fun hasGoogleAuthProvider_acceptsExactGoogleProviderIdAndRejectsMissingOrMalformed() {
        assertTrue(hasGoogleAuthProvider(listOf("firebase", GoogleAuthProvider.PROVIDER_ID)))
        assertTrue(hasGoogleAuthProvider(listOf("google.com")))

        assertFalse(hasGoogleAuthProvider(null))
        assertFalse(hasGoogleAuthProvider(emptyList()))
        assertFalse(hasGoogleAuthProvider(listOf(null)))
        assertFalse(hasGoogleAuthProvider(listOf("")))
        assertFalse(hasGoogleAuthProvider(listOf("   ")))
        assertFalse(hasGoogleAuthProvider(listOf("firebase")))
        assertFalse(hasGoogleAuthProvider(listOf("password")))
        assertFalse(hasGoogleAuthProvider(listOf("anonymous")))
        assertFalse(hasGoogleAuthProvider(listOf("phone")))
        assertFalse(hasGoogleAuthProvider(listOf(" google.com ")))
        assertFalse(hasGoogleAuthProvider(listOf("GOOGLE.COM")))
        assertFalse(hasGoogleAuthProvider(listOf("google")))
        assertFalse(isGoogleAuthenticatedUser(null))
    }

    @Test
    fun resolveAppSessionDestination_routesGoogleNonGoogleAndMissingProviderSafely() {
        val validGoogle = AuthSessionSnapshot(
            userId = "uid_google_1",
            email = "member@gmail.com",
            emailVerified = true,
            isGoogleSignIn = true,
            sessionNonce = 1
        )
        assertTrue(validGoogle.isGoogleAuthenticated)
        assertFalse(validGoogle.isUnsupportedNonGoogleSession)
        assertEquals(AppSessionDestination.MEMBER_APP_FLOW, resolveAppSessionDestination(validGoogle))

        val nonGoogleCached = AuthSessionSnapshot(
            userId = "uid_legacy_password",
            email = "legacy@example.com",
            emailVerified = true,
            isGoogleSignIn = false,
            sessionNonce = 1
        )
        assertTrue(nonGoogleCached.isAuthenticated)
        assertFalse(nonGoogleCached.isGoogleAuthenticated)
        assertTrue(nonGoogleCached.isUnsupportedNonGoogleSession)
        assertEquals(
            AppSessionDestination.UNSUPPORTED_NON_GOOGLE_SESSION,
            resolveAppSessionDestination(nonGoogleCached)
        )

        val blankUidWithGoogleFlag = AuthSessionSnapshot(
            userId = "   ",
            isGoogleSignIn = true
        )
        assertFalse(blankUidWithGoogleFlag.isAuthenticated)
        assertFalse(blankUidWithGoogleFlag.isGoogleAuthenticated)
        assertEquals(AppSessionDestination.AUTH_SIGN_IN, resolveAppSessionDestination(blankUidWithGoogleFlag))

        // Default AuthSessionSource implementation omits provider info -> never treated as Google
        val missingProviderSource = object : AuthSessionSource {
            override fun currentSnapshotData(): Triple<String?, String?, String?> =
                Triple("uid_missing_provider", "Unknown", null)

            override fun registerListeners(onChanged: (source: String) -> Unit): () -> Unit = {}
        }
        val listener = FirebaseAuthListener(missingProviderSource)
        assertTrue(listener.state.value.isAuthenticated)
        assertFalse(listener.state.value.isGoogleSignIn)
        assertTrue(listener.state.value.isUnsupportedNonGoogleSession)
        assertEquals(
            AppSessionDestination.UNSUPPORTED_NON_GOOGLE_SESSION,
            resolveAppSessionDestination(listener.state.value)
        )
    }

    @Test
    fun sessionGateContent_validGoogleSession_entersMemberFlowWithoutRecoveryScreen() {
        val googleSession = AuthSessionSnapshot(
            userId = "uid_google_member",
            displayName = "Leann",
            email = "leann@gmail.com",
            emailVerified = true,
            isGoogleSignIn = true,
            sessionNonce = 1
        )
        var enteredUid: String? = null
        var enteredNonce = -1

        composeTestRule.setContent {
            SogoChatTheme {
                SessionGateContent(
                    authSession = googleSession,
                    onAuthSuccess = {},
                    onNonGoogleSessionSignOut = {},
                    onPostLoginSignOut = {},
                    authScreenContent = {
                        Text("AUTH_SCREEN_TEST", modifier = Modifier.testTag("test_auth_screen"))
                    },
                    memberFlowContent = { uid, nonce, displayName, _, _ ->
                        enteredUid = uid
                        enteredNonce = nonce
                        Text("MEMBER_FLOW:$displayName", modifier = Modifier.testTag("test_member_flow"))
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        assertEquals("uid_google_member", enteredUid)
        assertEquals(1, enteredNonce)
        composeTestRule.onNodeWithTag("test_member_flow").assertIsDisplayed()
        composeTestRule.onNodeWithText("MEMBER_FLOW:Leann").assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithTag("unsupported_session_recovery_screen").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithTag("test_auth_screen").fetchSemanticsNodes().size)
    }

    @Test
    fun sessionGateContent_nonGoogleCachedSession_blocksMemberFlowAndProvidesSignOutRecoveryOnly() {
        val source = MutableAuthSessionSource(
            uid = "uid_legacy_pw",
            displayName = "Legacy User",
            email = "legacy@example.com",
            emailVerified = true,
            isGoogleSignIn = false
        )
        val listener = FirebaseAuthListener(source)
        var memberFlowEntered = false

        composeTestRule.setContent {
            val session by listener.state.collectAsState()
            SogoChatTheme {
                SessionGateContent(
                    authSession = session,
                    onAuthSuccess = {
                        listener.syncNow("google_sign_in_callback")
                    },
                    onNonGoogleSessionSignOut = { onDone ->
                        source.uid = null
                        source.displayName = null
                        source.email = null
                        source.emailVerified = false
                        source.isGoogleSignIn = false
                        listener.markSignedOut("unsupported_non_google_sign_out")
                        onDone()
                    },
                    onPostLoginSignOut = {
                        listener.markSignedOut("user_sign_out")
                    },
                    authScreenContent = { onAuthSuccess ->
                        Button(
                            onClick = {
                                source.uid = "uid_google_fresh"
                                source.displayName = "Google User"
                                source.email = "googleuser@gmail.com"
                                source.emailVerified = true
                                source.isGoogleSignIn = true
                                onAuthSuccess()
                            },
                            modifier = Modifier.testTag("test_google_sign_in_button")
                        ) {
                            Text("Sign In with Google")
                        }
                    },
                    memberFlowContent = { uid, _, _, _, _ ->
                        memberFlowEntered = true
                        Text("MEMBER_FLOW:$uid", modifier = Modifier.testTag("test_member_flow"))
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Non-Google session must never enter member flow or show a Retry dashboard loop
        assertFalse(memberFlowEntered)
        composeTestRule.onNodeWithTag("unsupported_session_recovery_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unsupported_session_recovery_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Google Sign-In Required").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unsupported_session_sign_out_button").assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithText("Retry", ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithTag("test_member_flow").fetchSemanticsNodes().size)

        // Tap sign-out recovery CTA -> clears invalid session and returns to normal Google Sign-In flow
        composeTestRule.onNodeWithTag("unsupported_session_sign_out_button").performClick()
        composeTestRule.waitForIdle()

        assertFalse(memberFlowEntered)
        assertNull(listener.state.value.userId)
        assertFalse(listener.state.value.isGoogleSignIn)
        composeTestRule.onNodeWithTag("test_google_sign_in_button").assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithTag("unsupported_session_recovery_screen").fetchSemanticsNodes().size)

        // Completing Google Sign-In afterwards increments nonce and restores member access cleanly
        composeTestRule.onNodeWithTag("test_google_sign_in_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue(memberFlowEntered)
        assertTrue(listener.state.value.isGoogleAuthenticated)
        assertEquals(2, listener.state.value.sessionNonce)
        composeTestRule.onNodeWithTag("test_member_flow").assertIsDisplayed()
        composeTestRule.onNodeWithText("MEMBER_FLOW:uid_google_fresh").assertIsDisplayed()
    }

    @Test
    fun signOutHelper_clearsCredentialStateAndInvokesCompletionEvenWhenCredentialClearThrows() = runTest {
        val failingCredentialManager = FakeClearCredentialManager(shouldFailClear = true)
        var completed = false

        signOut(
            credentialManager = failingCredentialManager,
            scope = this,
            onSignOutComplete = { completed = true },
            auth = firebaseAuth
        )
        advanceUntilIdle()

        assertEquals(1, failingCredentialManager.clearCalls)
        assertTrue(completed)
        assertNull(firebaseAuth.currentUser)
    }

    @Test
    fun sogoRepository_andOwnerClaims_preserveGoogleOnlyAuthorization() {
        firebaseAuth.signOut()
        val repo = SogoRepository(firestore, firebaseAuth)
        var requireError: IllegalStateException? = null
        try {
            repo.requireUserId()
        } catch (e: IllegalStateException) {
            requireError = e
        }
        assertEquals(
            "User must be signed in with Google before accessing SOGO CHAT AND MEET.",
            requireError?.message
        )
        assertFalse(repo.isOwnerCandidateSignedIn())

        val validOwner = OwnerDashboardAggregator.evaluateOwnerClaims(
            email = "gptgamestudioph@gmail.com",
            emailVerified = true,
            claims = mapOf("owner" to true, "admin" to true),
            isGoogleProvider = true
        )
        assertTrue(validOwner.isAuthorized)

        val nonGoogleOwner = OwnerDashboardAggregator.evaluateOwnerClaims(
            email = "gptgamestudioph@gmail.com",
            emailVerified = true,
            claims = mapOf("owner" to true, "admin" to true),
            isGoogleProvider = false
        )
        assertFalse(nonGoogleOwner.isAuthorized)
    }
}
