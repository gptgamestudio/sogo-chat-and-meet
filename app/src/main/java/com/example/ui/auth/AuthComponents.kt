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
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
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
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                (credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                 credential.type == TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL)
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                if (googleIdToken.isNotBlank()) {
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    Firebase.auth.signInWithCredential(authCredential).await()
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } else {
                onUnauthenticated()
            }
        } catch (e: Exception) {
            onUnauthenticated()
        }
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
                    onAuthError("Google ID token received was empty. Please check Google Sign-In setup.")
                    return@launch
                }
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                Log.d("Auth", "Firebase authentication succeeded for UID: ${authResult.user?.uid}")
                onAuthSuccess()
            } else {
                val type = (credential as? CustomCredential)?.type ?: credential::class.java.simpleName
                Log.e("Auth", "Unexpected credential type received: $type")
                onAuthError("Unexpected credential type received: $type")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            // When user selects an account in the Google account picker, but Google Play Services
            // aborts (e.g. SHA-1 missing in Firebase Console or Google provider disabled),
            // Play Services finishes with RESULT_CANCELED ("Activity is cancelled by the user").
            // Surface this explicitly rather than silently returning to the login screen.
            onAuthError(
                "Sign-In was cancelled or rejected by Google Play Services.\n\n" +
                "If you selected an account, verify the Firebase Console setup:\n" +
                "1. Google provider is enabled (Authentication → Sign-in method)\n" +
                "2. Debug SHA-1 fingerprint is registered under Your Apps:\n" +
                "   39:52:0D:71:BF:E3:07:7D:29:89:5C:A7:07:8B:5A:4E:BC:C0:98:0F"
            )
            onAuthCancelled()
        } catch (e: GetCredentialCustomException) {
            Log.e("Auth", "Google Sign-In custom error: type=${e.type}, message=${e.message}", e)
            onAuthError("Google Sign-In failed [${e.type}]: ${e.message ?: "Authentication service error. Verify SHA-1 in Firebase Console."}")
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            val detail = e.localizedMessage ?: e.message ?: e.cause?.message ?: e::class.java.simpleName
            onAuthError("Authentication error: $detail")
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
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Attempt silent auto-sign-in on initial launch
    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = { /* Keep showing login screen */ },
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

            // Google Sign-In Button
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { err ->
                            isLoading = false
                            errorMessage = err
                        },
                        scope = coroutineScope,
                        onAuthCancelled = {
                            isLoading = false
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
                    contentColor = TextWhite
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.horizontalGradient(listOf(GoldAccent, GoldLight))
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = GoldLight,
                        strokeWidth = 2.dp
                    )
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
