package com.example.ui.auth

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Immutable snapshot of the active Firebase Authentication session used to drive root navigation
 * cleanly from Google Sign-In to Profile Setup / Main Dashboard.
 */
data class AuthSessionSnapshot(
    val userId: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
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
    fun registerListeners(onChanged: (source: String) -> Unit): () -> Unit
}

class FirebaseAuthSessionSource(
    private val auth: FirebaseAuth = Firebase.auth
) : AuthSessionSource {

    override fun currentSnapshotData(): Triple<String?, String?, String?> {
        val user = auth.currentUser
        return Triple(user?.uid, user?.displayName, user?.photoUrl?.toString())
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
        val (uid, displayName, photoUrl) = source.currentSnapshotData()
        val initialNonce = if (!uid.isNullOrBlank()) 1 else 0
        return AuthSessionSnapshot(
            userId = uid,
            displayName = displayName,
            photoUrl = photoUrl,
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
                sessionNonce = prev.sessionNonce,
                lastTransitionSource = triggerSource,
                updatedAtMs = clockMs()
            )
            Log.d(TAG, "Session cleared ($triggerSource), previousNonce=${prev.sessionNonce}")
        }
    }

    private fun syncLocked(triggerSource: String) {
        val (rawUid, displayName, photoUrl) = source.currentSnapshotData()
        val nextUid = rawUid?.takeIf { it.isNotBlank() }
        val prev = _state.value

        val uidChanged = nextUid != prev.userId
        val metadataChanged = nextUid != null &&
            (displayName != prev.displayName || photoUrl != prev.photoUrl)

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
            displayName = if (nextUid != null) displayName else null,
            photoUrl = if (nextUid != null) photoUrl else null,
            sessionNonce = nextNonce,
            lastTransitionSource = triggerSource,
            updatedAtMs = clockMs()
        )

        Log.d(
            TAG,
            "Auth transition [$triggerSource]: authenticated=${nextUid != null}, " +
                "uidChanged=$uidChanged, sessionNonce=$nextNonce, hasDisplayName=${!displayName.isNullOrBlank()}"
        )
    }

    companion object {
        private const val TAG = "FirebaseAuthListener"
    }
}
