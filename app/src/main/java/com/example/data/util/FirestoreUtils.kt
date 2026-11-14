package com.example.data.util

import android.util.Log
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

private fun sanitizeFirestorePath(path: String?): String {
    if (path.isNullOrBlank()) return "unknown"
    return path.split('/')
        .mapIndexed { index, segment -> if (index % 2 == 1) "<redacted>" else segment }
        .joinToString("/")
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val isSignedIn = Firebase.auth.currentUser != null
    val firestoreCode = (rootError as? FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
    val sanitizedPath = sanitizeFirestorePath(path)

    val errorInfoJson = JSONObject().apply {
        put("code", firestoreCode)
        put("operationType", operationType.value)
        put("path", sanitizedPath)
        put("authenticated", isSignedIn)
        if (BuildConfig.DEBUG) {
            put("error", rootError.message ?: rootError::class.java.simpleName)
        }
    }

    val jsonString = errorInfoJson.toString()
    if (BuildConfig.DEBUG) {
        Log.e("FirestoreError", "Firestore Error: $jsonString")
    } else {
        Log.w("FirestoreError", "Firestore operation failed: op=${operationType.value}, code=$firestoreCode, path=$sanitizedPath")
    }
    return jsonString
}
