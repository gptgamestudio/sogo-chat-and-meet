package com.example.ui.auth

import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateCredentialRequest
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PrepareGetCredentialResponse
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AuthComponentsTest {

    private class FakeFailingCredentialManager(
        private val exceptionToReturn: GetCredentialException
    ) : CredentialManager {
        override fun getCredentialAsync(
            context: Context,
            request: GetCredentialRequest,
            cancellationSignal: CancellationSignal?,
            executor: Executor,
            callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>
        ) {
            callback.onError(exceptionToReturn)
        }

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
        ) = Unit

        override fun createSettingsPendingIntent(): PendingIntent {
            throw UnsupportedOperationException("Not needed in unit test")
        }
    }

    @Test
    fun findActivity_unwrapsContextWrappersAndReturnsNullForApplication() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        assertNull(appContext.findActivity())

        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        assertSame(activity, activity.findActivity())

        val wrapped = ContextWrapper(ContextWrapper(activity))
        assertSame(activity, wrapped.findActivity())
    }

    @Test
    fun defaultWebClientId_matchesGoogleServicesWebClient() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clientId = context.getString(R.string.default_web_client_id)
        assertEquals(
            "492343241434-6vth7qrvhbotitfouho1ssoocvt72efb.apps.googleusercontent.com",
            clientId
        )
    }

    @Test
    fun onGoogleSignInClicked_withNonActivityContext_surfacesError() = runTest {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val credentialManager = FakeFailingCredentialManager(
            GetCredentialCancellationException("unused")
        )
        var capturedError: String? = null
        var authSucceeded = false

        onGoogleSignInClicked(
            context = appContext,
            credentialManager = credentialManager,
            onAuthSuccess = { authSucceeded = true },
            onAuthError = { capturedError = it },
            scope = this
        )
        advanceUntilIdle()

        assertFalse(authSucceeded)
        assertNotNull(capturedError)
        assertTrue(capturedError!!.contains("Activity context is unavailable"))
    }

    @Test
    fun onGoogleSignInClicked_whenCancelled_surfacesCleanErrorWithoutSha1AndInvokesCancelled() = runTest {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val credentialManager = FakeFailingCredentialManager(
            GetCredentialCancellationException("Activity is cancelled by the user.")
        )
        var capturedError: String? = null
        var cancelledCalled = false
        var authSucceeded = false

        onGoogleSignInClicked(
            context = activity,
            credentialManager = credentialManager,
            onAuthSuccess = { authSucceeded = true },
            onAuthError = { capturedError = it },
            scope = this,
            onAuthCancelled = { cancelledCalled = true }
        )
        advanceUntilIdle()

        assertFalse(authSucceeded)
        assertTrue(cancelledCalled)
        assertNotNull(capturedError)
        assertTrue(capturedError!!.contains("cancelled", ignoreCase = true))
        assertFalse(capturedError!!.contains("SHA-1"))
        assertFalse(capturedError!!.contains("39:52:0D"))
    }

    @Test
    fun onGoogleSignInClicked_whenNoCredential_surfacesFriendlyMessage() = runTest {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val credentialManager = FakeFailingCredentialManager(
            NoCredentialException("No credentials available")
        )
        var capturedError: String? = null
        var authSucceeded = false

        onGoogleSignInClicked(
            context = activity,
            credentialManager = credentialManager,
            onAuthSuccess = { authSucceeded = true },
            onAuthError = { capturedError = it },
            scope = this
        )
        advanceUntilIdle()

        assertFalse(authSucceeded)
        assertNotNull(capturedError)
        assertTrue(capturedError!!.contains("No Google account is available"))
    }

    @Test
    fun onGoogleSignInClicked_whenCustomException_surfacesTypeWithoutRawDetails() = runTest {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val credentialManager = FakeFailingCredentialManager(
            GetCredentialCustomException("TEST_TYPE", "Developer error 10")
        )
        var capturedError: String? = null
        var authSucceeded = false

        onGoogleSignInClicked(
            context = activity,
            credentialManager = credentialManager,
            onAuthSuccess = { authSucceeded = true },
            onAuthError = { capturedError = it },
            scope = this
        )
        advanceUntilIdle()

        assertFalse(authSucceeded)
        assertNotNull(capturedError)
        assertTrue(capturedError!!.contains("TEST_TYPE"))
        assertTrue(capturedError!!.contains("could not be completed", ignoreCase = true))
        assertFalse(capturedError!!.contains("Developer error 10"))
    }

    @Test
    fun onGoogleSignInClicked_whenCancelledWithNonUserDetail_surfacesDetail() = runTest {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val credentialManager = FakeFailingCredentialManager(
            GetCredentialCancellationException("[28436] Caller configuration mismatch")
        )
        var capturedError: String? = null
        var cancelledCalled = false

        onGoogleSignInClicked(
            context = activity,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onAuthError = { capturedError = it },
            scope = this,
            onAuthCancelled = { cancelledCalled = true }
        )
        advanceUntilIdle()

        assertTrue(cancelledCalled)
        assertNotNull(capturedError)
        assertTrue(capturedError!!.contains("28436"))
        assertTrue(capturedError!!.contains("could not be completed", ignoreCase = true))
    }

    @Test
    fun onGoogleSignInClicked_whenGetCredentialException_surfacesType() = runTest {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val credentialManager = FakeFailingCredentialManager(
            GetCredentialInterruptedException("Play Services interrupted")
        )
        var capturedError: String? = null

        onGoogleSignInClicked(
            context = activity,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onAuthError = { capturedError = it },
            scope = this
        )
        advanceUntilIdle()

        assertNotNull(capturedError)
        assertTrue(capturedError!!.contains("unavailable", ignoreCase = true))
    }

    @Test
    fun onGoogleSignInClicked_whenBlankCancellation_invokesCancelledWithoutFalseConfirmedUserMessage() = runTest {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val credentialManager = FakeFailingCredentialManager(
            GetCredentialCancellationException("")
        )
        var capturedError: String? = null
        var cancelledCalled = false

        onGoogleSignInClicked(
            context = activity,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onAuthError = { capturedError = it },
            scope = this,
            onAuthCancelled = { cancelledCalled = true }
        )
        advanceUntilIdle()

        assertTrue(cancelledCalled)
        assertNotNull(capturedError)
        assertTrue(capturedError!!.contains("did not complete", ignoreCase = true))
        assertTrue(capturedError!!.contains("OAuth configuration", ignoreCase = true))
    }

    @Test
    fun mapGoogleAuthFailure_distinguishesConfirmedUserCancelFromBlankCancellation() {
        val confirmed = mapGoogleAuthFailure(
            GetCredentialCancellationException("Activity is cancelled by the user.")
        )
        assertTrue(confirmed.isConfirmedUserCancel)
        assertEquals(
            "Sign-In was cancelled. Please tap Sign In with Google to try again.",
            confirmed.userMessage
        )

        val ambiguous = mapGoogleAuthFailure(GetCredentialCancellationException(""))
        assertFalse(ambiguous.isConfirmedUserCancel)
        assertTrue(ambiguous.userMessage.contains("did not complete"))
    }

    @Test
    fun mapGoogleAuthFailure_googleIdTokenParsingException_avoidsConnectionMisattribution() {
        val failure = mapGoogleAuthFailure(GoogleIdTokenParsingException(IllegalArgumentException("bad jwt")))
        assertTrue(failure.userMessage.contains("invalid credential format", ignoreCase = true))
        assertFalse(failure.userMessage.contains("connection", ignoreCase = true))
        assertFalse(failure.logMessage.contains("bad jwt"))
    }

    @Test
    fun mapGoogleAuthFailure_firebaseAuthException_surfacesErrorCodeWithoutBlamingNetwork() {
        val disabledProvider = mapGoogleAuthFailure(
            FirebaseAuthException("ERROR_OPERATION_NOT_ALLOWED", "secret-internal-detail")
        )
        assertTrue(disabledProvider.userMessage.contains("ERROR_OPERATION_NOT_ALLOWED"))
        assertTrue(disabledProvider.userMessage.contains("not enabled", ignoreCase = true))
        assertFalse(disabledProvider.userMessage.contains("connection", ignoreCase = true))
        assertFalse(disabledProvider.userMessage.contains("secret-internal-detail"))
        assertFalse(disabledProvider.logMessage.contains("secret-internal-detail"))

        val invalidCred = mapGoogleAuthFailure(
            FirebaseAuthException("ERROR_INVALID_CREDENTIAL", "raw-token-detail")
        )
        assertTrue(invalidCred.userMessage.contains("ERROR_INVALID_CREDENTIAL"))
        assertTrue(invalidCred.userMessage.contains("OAuth/SHA", ignoreCase = true))
        assertFalse(invalidCred.logMessage.contains("raw-token-detail"))
    }

    @Test
    fun mapGoogleAuthFailure_firebaseNetworkException_reportsNetworkError() {
        val failure = mapGoogleAuthFailure(FirebaseNetworkException("network timeout"))
        assertTrue(failure.userMessage.contains("internet connection", ignoreCase = true))
    }

    @Test
    fun mapGoogleAuthFailure_unexpectedException_doesNotLeakArbitraryMessageOrBlameNetwork() {
        val failure = mapGoogleAuthFailure(IllegalStateException("idToken=eyJhbGciOiJSUzI1NiJ9"))
        assertTrue(failure.userMessage.contains("IllegalStateException"))
        assertFalse(failure.userMessage.contains("eyJhbGciOiJSUzI1NiJ9"))
        assertFalse(failure.logMessage.contains("eyJhbGciOiJSUzI1NiJ9"))
        assertFalse(failure.userMessage.contains("connection", ignoreCase = true))
    }

    @Test
    fun requestGoogleIdToken_translatesCredentialExceptionsConsistently() = runTest {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        val noCredCm = FakeFailingCredentialManager(NoCredentialException("none"))
        var noCredErr: String? = null
        try {
            requestGoogleIdToken(activity, noCredCm)
        } catch (e: IllegalStateException) {
            noCredErr = e.message
        }
        assertNotNull(noCredErr)
        assertTrue(noCredErr!!.contains("No Google account is available"))

        val blankCancelCm = FakeFailingCredentialManager(GetCredentialCancellationException(""))
        var blankCancelErr: String? = null
        try {
            requestGoogleIdToken(activity, blankCancelCm)
        } catch (e: IllegalStateException) {
            blankCancelErr = e.message
        }
        assertNotNull(blankCancelErr)
        assertTrue(blankCancelErr!!.contains("Google verification did not complete"))
    }
}
