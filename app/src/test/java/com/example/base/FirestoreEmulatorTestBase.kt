package com.example.base

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
abstract class FirestoreEmulatorTestBase {

  protected lateinit var firestore: FirebaseFirestore
  protected lateinit var auth: FirebaseAuth
  protected lateinit var databaseId: String

  @Before
  open fun setUpFirebase() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    databaseId = context.getString(com.example.R.string.firestore_database_id)

    val existingApp = FirebaseApp.getApps(context).firstOrNull { it.name == TEST_APP_NAME }
    val app = if (existingApp == null) {
      val newApp = FirebaseApp.initializeApp(
        context,
        FirebaseOptions.Builder()
          .setApplicationId("com.sogo.chatandmeet")
          .setProjectId(PROJECT_ID)
          .setApiKey("fake-api-key-for-emulator")
          .build(),
        TEST_APP_NAME
      )
      val newFirestore = FirebaseFirestore.getInstance(newApp, databaseId)
      newFirestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
      newFirestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
        .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
        .build()

      val newAuth = FirebaseAuth.getInstance(newApp)
      newAuth.useEmulator(EMULATOR_HOST, AUTH_PORT)
      newApp
    } else {
      existingApp
    }

    firestore = FirebaseFirestore.getInstance(app, databaseId)
    auth = FirebaseAuth.getInstance(app)
    clearFirestoreData()
  }

  @After
  open fun tearDownFirebase() {
    auth.signOut()
    clearFirestoreData()
  }

  private fun clearFirestoreData() {
    try {
      val url = java.net.URI("http://$EMULATOR_HOST:$FIRESTORE_PORT/emulator/v1/projects/$PROJECT_ID/databases/$databaseId/documents").toURL()
      val conn = url.openConnection() as java.net.HttpURLConnection
      conn.requestMethod = "DELETE"
      conn.connectTimeout = 2000
      conn.readTimeout = 2000
      conn.responseCode
      conn.disconnect()
    } catch (_: Exception) {}
  }

  protected suspend fun signInTestUser(email: String): String = withContext(Dispatchers.IO) {
    withTimeout(AUTH_TIMEOUT_MS) {
      val unsignedGoogleJwt = buildEmulatorGoogleIdToken(email)
      val credential = GoogleAuthProvider.getCredential(unsignedGoogleJwt, null)
      val result = auth.signInWithCredential(credential).await()
      val user = checkNotNull(result.user) { "Google IdP emulator user auth failed" }
      val tokenResult = user.getIdToken(false).await()
      val firebaseClaim = tokenResult.claims["firebase"] as? Map<*, *>
      val signInProvider = firebaseClaim?.get("sign_in_provider") as? String ?: tokenResult.signInProvider
      check(signInProvider == GoogleAuthProvider.PROVIDER_ID) {
        "Expected Auth Emulator token sign_in_provider='google.com', got '$signInProvider'"
      }
      check(user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }) {
        "Expected google.com in FirebaseUser.providerData"
      }
      user.uid
    }
  }

  private fun buildEmulatorGoogleIdToken(email: String): String {
    val normalized = email.trim().lowercase()
    val sub = "google_" + normalized.replace(Regex("[^a-z0-9]"), "_")
    val header = JSONObject(mapOf("alg" to "none", "typ" to "JWT")).toString()
    val payload = JSONObject(
      mapOf(
        "sub" to sub,
        "email" to normalized,
        "email_verified" to true,
        "name" to normalized.substringBefore("@"),
        "iss" to "https://accounts.google.com",
        "aud" to PROJECT_ID
      )
    ).toString()
    val flags = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    val encodedHeader = Base64.encodeToString(header.toByteArray(Charsets.UTF_8), flags)
    val encodedPayload = Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), flags)
    return "$encodedHeader.$encodedPayload."
  }

  protected fun Throwable.unwrapFirestoreException(): FirebaseFirestoreException? {
    var current: Throwable? = this
    while (current != null) {
      if (current is FirebaseFirestoreException) return current
      current = current.cause
    }
    return null
  }

  private companion object {
    const val TEST_APP_NAME = "sogo-emulator-test"
    const val EMULATOR_HOST = "127.0.0.1"
    const val FIRESTORE_PORT = 8085
    const val AUTH_PORT = 9099
    const val PROJECT_ID = "demo-no-project"
    const val AUTH_TIMEOUT_MS = 5000L
  }
}
