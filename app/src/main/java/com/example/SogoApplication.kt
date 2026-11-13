package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class SogoApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Ensure FirebaseApp is initialized before installing App Check provider factory
        FirebaseApp.initializeApp(this)

        // 2. Install App Check provider factory before any Firebase service (Firestore, Auth, Storage) is accessed
        val appCheck = FirebaseAppCheck.getInstance()

        if (BuildConfig.DEBUG) {
            // Debug provider factory for local development and emulator testing.
            // Loaded via reflection so firebase-appcheck-debug is scoped strictly to debugImplementation
            // and completely absent/excluded from release builds.
            try {
                val debugClass = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                val getInstanceMethod = debugClass.getMethod("getInstance")
                val factory = getInstanceMethod.invoke(null) as AppCheckProviderFactory
                appCheck.installAppCheckProviderFactory(factory)
                Log.d(TAG, "Firebase App Check initialized with DebugAppCheckProviderFactory")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize DebugAppCheckProviderFactory in debug build", e)
                throw IllegalStateException("Failed to initialize Firebase App Check with DebugAppCheckProviderFactory in debug build", e)
            }
        } else {
            // Play Integrity provider factory for official production release builds
            appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "Firebase App Check initialized with PlayIntegrityAppCheckProviderFactory")
        }
    }

    companion object {
        private const val TAG = "SogoApplication"
    }
}
