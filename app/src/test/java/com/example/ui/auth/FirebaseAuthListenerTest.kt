package com.example.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FirebaseAuthListenerTest {

    private class FakeAuthSessionSource(
        var uid: String? = null,
        var displayName: String? = null,
        var photoUrl: String? = null,
        var email: String? = null,
        var emailVerified: Boolean = false,
        var isGoogleSignIn: Boolean = false
    ) : AuthSessionSource {
        var registerCount = 0
        var unregisterCount = 0
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
            registerCount++
            callback = onChanged
            return {
                unregisterCount++
                callback = null
            }
        }

        fun emitChange(source: String = "firebase_auth_state_listener") {
            callback?.invoke(source)
        }
    }

    @Test
    fun startAndStop_areIdempotentAndTrackAuthSignInSignOutNonce() {
        val fakeSource = FakeAuthSessionSource(uid = null)
        var now = 1_000L
        val listener = FirebaseAuthListener(fakeSource) { now }

        assertFalse(listener.state.value.isAuthenticated)
        assertFalse(listener.state.value.emailVerified)
        assertFalse(listener.state.value.isGoogleSignIn)
        assertEquals(0, listener.state.value.sessionNonce)

        listener.start()
        listener.start() // Idempotent
        assertEquals(1, fakeSource.registerCount)

        // User completes Google Sign-In
        now = 2_000L
        fakeSource.uid = "uid_user_1"
        fakeSource.displayName = "Maria Santos"
        fakeSource.photoUrl = "https://example.com/maria.jpg"
        fakeSource.email = "maria@gmail.com"
        fakeSource.emailVerified = true
        fakeSource.isGoogleSignIn = true
        fakeSource.emitChange("firebase_auth_state_listener")

        val signedIn = listener.state.value
        assertTrue(signedIn.isAuthenticated)
        assertEquals("uid_user_1", signedIn.userId)
        assertEquals("Maria Santos", signedIn.displayName)
        assertEquals("https://example.com/maria.jpg", signedIn.photoUrl)
        assertEquals("maria@gmail.com", signedIn.email)
        assertTrue(signedIn.emailVerified)
        assertTrue(signedIn.isGoogleSignIn)
        assertEquals(1, signedIn.sessionNonce)

        // ID token refresh for same user updates provider metadata without incrementing sessionNonce
        now = 3_000L
        fakeSource.emitChange("firebase_id_token_listener")
        assertEquals(1, listener.state.value.sessionNonce)

        // Explicit sign-out clears active user and trusted provider flags while preserving nonce counter
        now = 4_000L
        fakeSource.uid = null
        fakeSource.displayName = null
        fakeSource.photoUrl = null
        fakeSource.email = null
        fakeSource.emailVerified = false
        fakeSource.isGoogleSignIn = false
        listener.markSignedOut("user_sign_out")
        assertFalse(listener.state.value.isAuthenticated)
        assertNull(listener.state.value.userId)
        assertNull(listener.state.value.email)
        assertFalse(listener.state.value.emailVerified)
        assertFalse(listener.state.value.isGoogleSignIn)
        assertEquals(1, listener.state.value.sessionNonce)

        // Re-signing in with the same Google account increments sessionNonce to reset stale ViewModels
        now = 5_000L
        fakeSource.uid = "uid_user_1"
        fakeSource.displayName = "Maria Santos"
        fakeSource.email = "maria@gmail.com"
        fakeSource.emailVerified = true
        fakeSource.isGoogleSignIn = true
        listener.syncNow("google_sign_in_callback")
        assertTrue(listener.state.value.isAuthenticated)
        assertTrue(listener.state.value.emailVerified)
        assertTrue(listener.state.value.isGoogleSignIn)
        assertEquals(2, listener.state.value.sessionNonce)

        listener.stop()
        listener.stop() // Idempotent
        assertEquals(1, fakeSource.unregisterCount)
    }

    @Test
    fun start_syncsSignedInUserEvenIfAuthCompletedBeforeEffectAttached() {
        val fakeSource = FakeAuthSessionSource(uid = null)
        val listener = FirebaseAuthListener(fakeSource)
        assertFalse(listener.state.value.isAuthenticated)

        // Sign-in happens right before DisposableEffect calls start()
        fakeSource.uid = "uid_early"
        fakeSource.displayName = "Juan"
        fakeSource.email = "juan@gmail.com"
        fakeSource.emailVerified = true
        fakeSource.isGoogleSignIn = true
        listener.start()

        assertTrue(listener.state.value.isAuthenticated)
        assertEquals("uid_early", listener.state.value.userId)
        assertEquals("juan@gmail.com", listener.state.value.email)
        assertTrue(listener.state.value.emailVerified)
        assertTrue(listener.state.value.isGoogleSignIn)
        assertEquals(1, listener.state.value.sessionNonce)
    }
}
