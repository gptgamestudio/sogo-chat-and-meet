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
import androidx.credentials.exceptions.NoCredentialException
import androidx.test.core.app.ApplicationProvider
import com.example.R
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
}
