package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.data.model.UserProfile
import com.example.ui.theme.BurgundyDark
import com.example.ui.theme.BurgundyLight
import com.example.ui.theme.BurgundyPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldLight
import com.example.ui.theme.MatteBlackBackground
import com.example.ui.theme.MatteBlackCard
import com.example.ui.theme.MatteBlackSurface
import com.example.ui.theme.MatteBlackSurfaceVariant
import com.example.ui.theme.OutlineGold
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Safely resolves the nearest Activity from the given Context, unwrapping any ContextWrappers.
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal data class GoogleAuthFailure(
    val userMessage: String,
    val logMessage: String,
    val isWarning: Boolean = false,
    val isConfirmedUserCancel: Boolean = false
)

internal fun mapGoogleAuthFailure(
    error: Throwable,
    forReauth: Boolean = false
): GoogleAuthFailure {
    val flowLabel = if (forReauth) "Google verification" else "Sign-In"
    val retryHint = if (forReauth) "Please try again." else "Please tap Sign In with Google to try again."

    return when (error) {
        is GetCredentialCancellationException -> {
            val rawDetail = error.errorMessage?.toString()?.trim()
                ?.takeUnless { it.isBlank() }
                ?: error.message?.trim().orEmpty()
            val confirmedUserCancel =
                rawDetail.contains("cancelled by the user", ignoreCase = true) ||
                rawDetail.contains("canceled by the user", ignoreCase = true) ||
                rawDetail.contains("user cancel", ignoreCase = true) ||
                rawDetail.contains("user_cancelled", ignoreCase = true) ||
                rawDetail.contains("dismissed by user", ignoreCase = true)

            when {
                confirmedUserCancel -> GoogleAuthFailure(
                    userMessage = "$flowLabel was cancelled. $retryHint",
                    logMessage = "Credential flow cancelled by user [type=${error.type}]",
                    isWarning = true,
                    isConfirmedUserCancel = true
                )
                rawDetail.isBlank() -> GoogleAuthFailure(
                    userMessage = "$flowLabel did not complete [${error.type}]. This may be caused by user dismissal or an app/OAuth configuration issue. $retryHint",
                    logMessage = "Credential flow aborted with empty detail [type=${error.type}, reauth=$forReauth]",
                    isWarning = true,
                    isConfirmedUserCancel = false
                )
                else -> GoogleAuthFailure(
                    userMessage = "$flowLabel could not be completed ($rawDetail). $retryHint",
                    logMessage = "Credential flow aborted with non-user detail [type=${error.type}, reauth=$forReauth]",
                    isWarning = true,
                    isConfirmedUserCancel = false
                )
            }
        }
        is NoCredentialException -> GoogleAuthFailure(
            userMessage = if (forReauth) {
                "No Google account is available on this device for verification. Add a Google account in Settings and try again."
            } else {
                "No Google account is available on this device. Add a Google account in Settings and try again."
            },
            logMessage = "No Google credentials available [type=${error.type}, reauth=$forReauth]",
            isWarning = true
        )
        is GetCredentialCustomException -> GoogleAuthFailure(
            userMessage = "Google $flowLabel could not be completed [${error.type}]. Please try again.",
            logMessage = "Google $flowLabel custom credential error [type=${error.type}]",
            isWarning = false
        )
        is GetCredentialException -> GoogleAuthFailure(
            userMessage = "Google $flowLabel unavailable [${error.type}]. Please verify Google Play Services and try again.",
            logMessage = "CredentialManager error [type=${error.type}, class=${error::class.java.simpleName}]",
            isWarning = false
        )
        is GoogleIdTokenParsingException -> GoogleAuthFailure(
            userMessage = "Google $flowLabel returned an invalid credential format. Please try again.",
            logMessage = "GoogleIdTokenCredential parsing failed [class=${error::class.java.simpleName}]",
            isWarning = false
        )
        is FirebaseAuthException -> {
            val code = error.errorCode
            val msg = when (code) {
                "ERROR_OPERATION_NOT_ALLOWED" ->
                    "Google Sign-In is not enabled in Firebase Authentication settings [$code]."
                "ERROR_INVALID_CREDENTIAL",
                "ERROR_INVALID_OAUTH_CLIENT_ID" ->
                    "Google credential was rejected by Firebase Authentication [$code]. Verify OAuth/SHA configuration."
                "ERROR_USER_DISABLED" ->
                    "This Google account has been disabled [$code]."
                "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
                    "An account already exists with a different sign-in method [$code]."
                "ERROR_TOO_MANY_REQUESTS" ->
                    "Too many authentication attempts [$code]. Please wait a moment and try again."
                else ->
                    "Firebase Authentication failed [$code]. Please try again."
            }
            GoogleAuthFailure(
                userMessage = msg,
                logMessage = "FirebaseAuthException [errorCode=$code, reauth=$forReauth]",
                isWarning = false
            )
        }
        is FirebaseNetworkException -> GoogleAuthFailure(
            userMessage = "Network error while contacting Google/Firebase. Please check your internet connection and try again.",
            logMessage = "FirebaseNetworkException during $flowLabel",
            isWarning = true
        )
        else -> GoogleAuthFailure(
            userMessage = "Google $flowLabel failed (${error::class.java.simpleName}). Please try again.",
            logMessage = "Unexpected $flowLabel failure [class=${error::class.java.simpleName}]",
            isWarning = false
        )
    }
}

private fun logAuthFailure(prefix: String, failure: GoogleAuthFailure) {
    val line = "$prefix: ${failure.logMessage}"
    if (failure.isWarning) {
        Log.w("Auth", line)
    } else {
        Log.e("Auth", line)
    }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        Log.w("Auth", "Auto sign-in skipped: default_web_client_id resource unavailable")
        onUnauthenticated()
        return
    }

    val activity = context.findActivity()
    if (activity == null) {
        Log.w("Auth", "Auto sign-in skipped: Activity context unavailable")
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                (credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                 credential.type == TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL)
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                if (googleIdToken.isNotBlank()) {
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                    if (authResult.user != null || Firebase.auth.currentUser != null) {
                        onAuthSuccess()
                    } else {
                        Log.w("Auth", "Auto sign-in completed without an active Firebase user")
                        onUnauthenticated()
                    }
                } else {
                    Log.w("Auth", "Auto sign-in returned an empty ID token")
                    onUnauthenticated()
                }
            } else {
                Log.w("Auth", "Auto sign-in returned non-Google credential type")
                onUnauthenticated()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoCredentialException) {
            Log.d("Auth", "No auto sign-in credentials available")
            onUnauthenticated()
        } catch (e: GetCredentialCancellationException) {
            logAuthFailure("Auto sign-in aborted", mapGoogleAuthFailure(e))
            onUnauthenticated()
        } catch (e: GoogleIdTokenParsingException) {
            logAuthFailure("Auto sign-in token parse error", mapGoogleAuthFailure(e))
            onUnauthenticated()
        } catch (e: GetCredentialException) {
            logAuthFailure("Auto sign-in credential error", mapGoogleAuthFailure(e))
            onUnauthenticated()
        } catch (e: FirebaseAuthException) {
            logAuthFailure("Auto sign-in Firebase Auth error", mapGoogleAuthFailure(e))
            onUnauthenticated()
        } catch (e: FirebaseNetworkException) {
            logAuthFailure("Auto sign-in network error", mapGoogleAuthFailure(e))
            onUnauthenticated()
        } catch (e: Exception) {
            logAuthFailure("Auto sign-in unexpected error", mapGoogleAuthFailure(e))
            onUnauthenticated()
        }
    }
}

suspend fun requestGoogleIdToken(
    context: Context,
    credentialManager: CredentialManager
): String {
    val activity = context.findActivity()
        ?: throw IllegalStateException("Google verification failed: Activity context is unavailable.")
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        throw IllegalStateException("Google Sign-In configuration missing: default_web_client_id not found.")
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
    return try {
        val result = credentialManager.getCredential(activity, request)
        val credential = result.credential
        if (credential is CustomCredential &&
            (credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
             credential.type == TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL)
        ) {
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            if (idToken.isBlank()) {
                throw IllegalStateException("Google verification returned an empty ID token.")
            }
            idToken
        } else {
            throw IllegalStateException("Unexpected credential type received during verification.")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalStateException) {
        throw e
    } catch (e: Exception) {
        val failure = mapGoogleAuthFailure(e, forReauth = true)
        logAuthFailure("Google re-authentication failed", failure)
        throw IllegalStateException(failure.userMessage, e)
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val activity = context.findActivity()
    if (activity == null) {
        onAuthError("Google Sign-In failed: Activity context is unavailable.")
        return
    }

    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found in app resources.")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                (credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                 credential.type == TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL)
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                if (googleIdToken.isBlank()) {
                    Log.e("Auth", "Interactive sign-in received an empty Google ID token")
                    onAuthError("Google ID token received was empty. Please check Google Sign-In setup.")
                    return@launch
                }
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                if (authResult.user != null || Firebase.auth.currentUser != null) {
                    Log.d("Auth", "Firebase authentication succeeded")
                    onAuthSuccess()
                } else {
                    Log.e("Auth", "Firebase authentication completed without a currentUser")
                    onAuthError("Firebase Sign-In did not create an active session. Please try again.")
                }
            } else {
                val type = (credential as? CustomCredential)?.type ?: credential::class.java.simpleName
                Log.e("Auth", "Unexpected credential type received [class=${credential::class.java.simpleName}]")
                onAuthError("Unexpected credential type received: $type")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            val failure = mapGoogleAuthFailure(e)
            logAuthFailure("Interactive Google Sign-In aborted", failure)
            onAuthError(failure.userMessage)
            onAuthCancelled()
        } catch (e: NoCredentialException) {
            val failure = mapGoogleAuthFailure(e)
            logAuthFailure("Interactive Google Sign-In failed", failure)
            onAuthError(failure.userMessage)
        } catch (e: GetCredentialCustomException) {
            val failure = mapGoogleAuthFailure(e)
            logAuthFailure("Interactive Google Sign-In failed", failure)
            onAuthError(failure.userMessage)
        } catch (e: GetCredentialException) {
            val failure = mapGoogleAuthFailure(e)
            logAuthFailure("Interactive Google Sign-In failed", failure)
            onAuthError(failure.userMessage)
        } catch (e: GoogleIdTokenParsingException) {
            val failure = mapGoogleAuthFailure(e)
            logAuthFailure("Interactive Google Sign-In failed", failure)
            onAuthError(failure.userMessage)
        } catch (e: FirebaseAuthException) {
            val failure = mapGoogleAuthFailure(e)
            logAuthFailure("Interactive Google Sign-In failed", failure)
            onAuthError(failure.userMessage)
        } catch (e: FirebaseNetworkException) {
            val failure = mapGoogleAuthFailure(e)
            logAuthFailure("Interactive Google Sign-In failed", failure)
            onAuthError(failure.userMessage)
        } catch (e: Exception) {
            val failure = mapGoogleAuthFailure(e)
            logAuthFailure("Interactive Google Sign-In failed", failure)
            onAuthError(failure.userMessage)
        }
    }
}

fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {},
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isAutoChecking by remember { mutableStateOf(true) }
    var isManualSigningIn by remember { mutableStateOf(false) }
    var statusLabel by remember { mutableStateOf("Checking existing Google session...") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isValidating = isAutoChecking || isManualSigningIn

    // Attempt silent auto-sign-in on initial launch
    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                statusLabel = "Sign-In verified. Navigating to dashboard..."
                onAuthSuccess()
            },
            onUnauthenticated = {
                isAutoChecking = false
            },
            scope = coroutineScope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BurgundyDark,
                        Color(0xFF22050A),
                        Color(0xFF0F0E11),
                        Color(0xFF0F0E11)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Luxury Heart Logo Icon
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(BurgundyPrimary, BurgundyLight)
                        )
                    )
                    .border(2.dp, OutlineGold, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Sogo Chat Logo",
                    tint = GoldLight,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "SOGO CHAT & MEET",
                color = TextWhite,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Text(
                text = "PREMIUM FILIPINO PRIVATE DATING",
                color = GoldAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 18+ Privacy and Security Notice Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(OutlineGold, OutlineSubtle)))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Verified 18+",
                            tint = GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "18+ Adult Verified Community",
                            color = TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Real Filipino members, end-to-end user isolation, private audio/video calls, and zero fake accounts. All users must be 18 years of age or older.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (errorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2B0E13)),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(Color(0xFFFF5252), Color(0xFFE53935)))
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Authentication Notice",
                                color = Color(0xFFFF8A80),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMessage!!,
                            color = TextWhite,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            if (isValidating) {
                AuthValidatingCard(
                    statusLabel = statusLabel,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Google Sign-In Button
            Button(
                onClick = {
                    isAutoChecking = false
                    isManualSigningIn = true
                    statusLabel = "Validating Google Sign-In..."
                    errorMessage = null
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            // Keep validating spinner active until MainActivity transitions to dashboard
                            statusLabel = "Sign-In verified. Navigating to dashboard..."
                            onAuthSuccess()
                        },
                        onAuthError = { err ->
                            isManualSigningIn = false
                            errorMessage = err
                        },
                        scope = coroutineScope,
                        onAuthCancelled = {
                            isManualSigningIn = false
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("google_sign_in_button"),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BurgundyPrimary,
                    contentColor = TextWhite,
                    disabledContainerColor = BurgundyDark,
                    disabledContentColor = TextWhite
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = !isValidating).copy(
                    brush = Brush.horizontalGradient(listOf(GoldAccent, GoldLight))
                ),
                enabled = !isValidating
            ) {
                if (isValidating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = GoldLight,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Signing In...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GoldLight
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = GoldLight,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sign In with Google",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "By signing in, you confirm that you are at least 18 years old and agree to our Safety and Privacy terms.",
                color = TextMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun AuthValidatingCard(
    statusLabel: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("auth_validating_card"),
        colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(GoldAccent, OutlineSubtle))
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .testTag("auth_validating_progress"),
                color = GoldLight,
                strokeWidth = 2.5.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = statusLabel,
                color = TextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSetupDialog(
    initialDisplayName: String,
    onSaveProfile: (displayName: String, age: Int, gender: String, country: String, city: String, aboutMe: String, interests: List<String>) -> Unit
) {
    var displayName by remember { mutableStateOf(initialDisplayName.ifBlank { "Filipino Single" }) }
    var ageText by remember { mutableStateOf("24") }
    var gender by remember { mutableStateOf("Female") }
    var country by remember { mutableStateOf("Philippines") }
    var city by remember { mutableStateOf("Manila") }
    var aboutMe by remember { mutableStateOf("Friendly and down to earth. Looking for meaningful conversations and genuine connections.") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val allInterests = listOf(
        "Coffee & Kape", "Travel", "Beach", "Foodie", "Music & OPM", "Karaoke",
        "Movies", "Fitness", "Nightlife", "Gaming", "Art", "Cooking"
    )
    val selectedInterests = remember { mutableStateOf(setOf("Coffee & Kape", "Travel", "Music & OPM")) }

    Dialog(
        onDismissRequest = { /* Modal forces completion */ },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MatteBlackSurface,
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(OutlineGold, OutlineSubtle)))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Welcome to SOGO CHAT",
                    color = GoldLight,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Complete your 18+ dating profile",
                    color = TextMuted,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = OutlineSubtle
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = ageText,
                        onValueChange = { ageText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Age (18+)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = OutlineSubtle
                        ),
                        modifier = Modifier.weight(1f).testTag("profile_age_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("City (e.g. Manila)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = OutlineSubtle
                        ),
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Gender selector
                Text(
                    text = "Gender",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Female", "Male", "Non-binary").forEach { g ->
                        FilterChip(
                            selected = gender == g,
                            onClick = { gender = g },
                            label = { Text(g) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BurgundyPrimary,
                                selectedLabelColor = TextWhite,
                                containerColor = MatteBlackSurfaceVariant,
                                labelColor = TextMuted
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = aboutMe,
                    onValueChange = { aboutMe = it },
                    label = { Text("About Me") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = OutlineSubtle
                    ),
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Personality & Interests",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allInterests.forEach { interest ->
                        val isSelected = selectedInterests.value.contains(interest)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val current = selectedInterests.value.toMutableSet()
                                if (isSelected) current.remove(interest) else current.add(interest)
                                selectedInterests.value = current
                            },
                            label = { Text(interest, fontSize = 11.sp) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BurgundyPrimary,
                                selectedLabelColor = GoldLight,
                                containerColor = MatteBlackSurfaceVariant,
                                labelColor = TextMuted
                            )
                        )
                    }
                }

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorText!!,
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val ageNum = ageText.toIntOrNull() ?: 0
                        if (displayName.isBlank()) {
                            errorText = "Please enter your display name"
                            return@Button
                        }
                        if (ageNum < 18) {
                            errorText = "You must be at least 18 years old to join SOGO CHAT."
                            return@Button
                        }
                        errorText = null
                        onSaveProfile(
                            displayName.trim(),
                            ageNum,
                            gender,
                            country.trim(),
                            city.trim(),
                            aboutMe.trim(),
                            selectedInterests.value.toList()
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_profile_button"),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BurgundyPrimary)
                ) {
                    Text("Enter SOGO Chat & Meet", fontWeight = FontWeight.Bold, color = TextWhite)
                }
            }
        }
    }
}
