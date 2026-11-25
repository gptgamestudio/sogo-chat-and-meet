package com.example.ui.auth

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Trusted provider/account metadata derived strictly from Firebase Authentication.
 */
data class AuthProviderSnapshot(
    val userId: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val isGoogleSignIn: Boolean = false
)

/**
 * Immutable snapshot of the active Firebase Authentication session used to drive root navigation
 * cleanly from Google Sign-In to Profile Setup / Main Dashboard.
 */
data class AuthSessionSnapshot(
    val userId: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val isGoogleSignIn: Boolean = false,
    val sessionNonce: Int = 0,
    val lastTransitionSource: String = "init",
    val updatedAtMs: Long = 0L
) {
    val isAuthenticated: Boolean get() = !userId.isNullOrBlank()
}

/**
 * Lightweight source abstraction allowing unit tests to exercise listener transitions without a
 * live Firebase backend while production binds directly to [FirebaseAuth.AuthStateListener] and
 * [FirebaseAuth.IdTokenListener].
 */
interface AuthSessionSource {
    fun currentSnapshotData(): Triple<String?, String?, String?>
    fun currentProviderSnapshot(): AuthProviderSnapshot = currentSnapshotData().let { (uid, name, photo) ->
        AuthProviderSnapshot(
            userId = uid,
            displayName = name,
            photoUrl = photo
        )
    }
    fun registerListeners(onChanged: (source: String) -> Unit): () -> Unit
}

class FirebaseAuthSessionSource(
    private val auth: FirebaseAuth = Firebase.auth
) : AuthSessionSource {

    override fun currentSnapshotData(): Triple<String?, String?, String?> {
        val user = auth.currentUser
        return Triple(user?.uid, user?.displayName, user?.photoUrl?.toString())
    }

    override fun currentProviderSnapshot(): AuthProviderSnapshot {
        val user = auth.currentUser
        val hasGoogleProvider = user?.providerData?.any {
            it.providerId == GoogleAuthProvider.PROVIDER_ID
        } == true
        return AuthProviderSnapshot(
            userId = user?.uid,
            displayName = user?.displayName,
            photoUrl = user?.photoUrl?.toString(),
            email = user?.email,
            emailVerified = user?.isEmailVerified == true,
            isGoogleSignIn = hasGoogleProvider
        )
    }

    override fun registerListeners(onChanged: (source: String) -> Unit): () -> Unit {
        val authStateListener = FirebaseAuth.AuthStateListener {
            onChanged("firebase_auth_state_listener")
        }
        val idTokenListener = FirebaseAuth.IdTokenListener {
            onChanged("firebase_id_token_listener")
        }
        auth.addAuthStateListener(authStateListener)
        auth.addIdTokenListener(idTokenListener)
        return {
            auth.removeAuthStateListener(authStateListener)
            auth.removeIdTokenListener(idTokenListener)
        }
    }
}

/**
 * Helper that observes Firebase Auth state and ID token updates, prevents stale Compose
 * [androidx.lifecycle.ViewModel] reuse across re-sign-in via [AuthSessionSnapshot.sessionNonce],
 * and logs non-PII diagnostic breadcrumbs for post-sign-in navigation troubleshooting.
 */
class FirebaseAuthListener(
    private val source: AuthSessionSource,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    constructor(
        auth: FirebaseAuth = Firebase.auth,
        clockMs: () -> Long = { System.currentTimeMillis() }
    ) : this(FirebaseAuthSessionSource(auth), clockMs)

    private val lock = Any()
    private var unregisterHandle: (() -> Unit)? = null
    private var isStarted = false

    private val _state = MutableStateFlow(createInitialSnapshot())
    val state: StateFlow<AuthSessionSnapshot> = _state.asStateFlow()

    private fun createInitialSnapshot(): AuthSessionSnapshot {
        val provider = source.currentProviderSnapshot()
        val uid = provider.userId?.takeIf { it.isNotBlank() }
        val initialNonce = if (uid != null) 1 else 0
        return AuthSessionSnapshot(
            userId = uid,
            displayName = if (uid != null) provider.displayName else null,
            photoUrl = if (uid != null) provider.photoUrl else null,
            email = if (uid != null) provider.email else null,
            emailVerified = uid != null && provider.emailVerified,
            isGoogleSignIn = uid != null && provider.isGoogleSignIn,
            sessionNonce = initialNonce,
            lastTransitionSource = "initial_check",
            updatedAtMs = clockMs()
        )
    }

    fun start() {
        synchronized(lock) {
            if (isStarted) return
            isStarted = true
            // Sync before registering listeners in case auth changed during composition setup
            syncLocked("listener_start")
            unregisterHandle = source.registerListeners { eventSource ->
                syncNow(eventSource)
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            if (!isStarted) return
            isStarted = false
            unregisterHandle?.invoke()
            unregisterHandle = null
            Log.d(TAG, "Auth listeners detached")
        }
    }

    fun syncNow(triggerSource: String = "manual_sync") {
        synchronized(lock) {
            syncLocked(triggerSource)
        }
    }

    fun markSignedOut(triggerSource: String = "explicit_sign_out") {
        synchronized(lock) {
            val prev = _state.value
            if (prev.userId == null) return
            _state.value = AuthSessionSnapshot(
                userId = null,
                displayName = null,
                photoUrl = null,
                email = null,
                emailVerified = false,
                isGoogleSignIn = false,
                sessionNonce = prev.sessionNonce,
                lastTransitionSource = triggerSource,
                updatedAtMs = clockMs()
            )
            Log.d(TAG, "Session cleared ($triggerSource), previousNonce=${prev.sessionNonce}")
        }
    }

    private fun syncLocked(triggerSource: String) {
        val provider = source.currentProviderSnapshot()
        val nextUid = provider.userId?.takeIf { it.isNotBlank() }
        val prev = _state.value

        val uidChanged = nextUid != prev.userId
        val nextEmailVerified = nextUid != null && provider.emailVerified
        val nextIsGoogle = nextUid != null && provider.isGoogleSignIn
        val nextEmail = if (nextUid != null) provider.email else null
        val metadataChanged = nextUid != null && (
            provider.displayName != prev.displayName ||
                provider.photoUrl != prev.photoUrl ||
                nextEmail != prev.email ||
                nextEmailVerified != prev.emailVerified ||
                nextIsGoogle != prev.isGoogleSignIn
            )

        if (!uidChanged && !metadataChanged) {
            return
        }

        val nextNonce = if (uidChanged && nextUid != null) {
            prev.sessionNonce + 1
        } else {
            prev.sessionNonce
        }

        _state.value = AuthSessionSnapshot(
            userId = nextUid,
            displayName = if (nextUid != null) provider.displayName else null,
            photoUrl = if (nextUid != null) provider.photoUrl else null,
            email = nextEmail,
            emailVerified = nextEmailVerified,
            isGoogleSignIn = nextIsGoogle,
            sessionNonce = nextNonce,
            lastTransitionSource = triggerSource,
            updatedAtMs = clockMs()
        )

        Log.d(
            TAG,
            "Auth transition [$triggerSource]: authenticated=${nextUid != null}, " +
                "uidChanged=$uidChanged, sessionNonce=$nextNonce, google=$nextIsGoogle, emailVerified=$nextEmailVerified"
        )
    }

    companion object {
        private const val TAG = "FirebaseAuthListener"
    }
}
