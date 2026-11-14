package com.example

import android.app.Application
import android.util.Log
import com.example.ads.AdMobManager
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class SogoApplication : Application() {

    val adMobManager: AdMobManager by lazy { AdMobManager(this) }

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Google Mobile Ads SDK asynchronously on a background thread
        adMobManager.initialize()

        // 2. Ensure FirebaseApp is initialized before installing App Check provider factory
        val app = FirebaseApp.initializeApp(this) ?: FirebaseApp.getApps(this).firstOrNull()
        if (app == null) {
            Log.e(TAG, "FirebaseApp failed to initialize; skipping App Check setup")
            return
        }

        // 3. Install App Check provider factory before any Firebase service (Firestore, Auth, Storage) is accessed
        try {
            val appCheck = FirebaseAppCheck.getInstance()
            if (BuildConfig.DEBUG) {
                // Debug provider factory is scoped strictly to debugImplementation
                val debugClass = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                val getInstanceMethod = debugClass.getMethod("getInstance")
                val factory = getInstanceMethod.invoke(null) as AppCheckProviderFactory
                appCheck.installAppCheckProviderFactory(factory)
                Log.d(TAG, "Firebase App Check initialized with DebugAppCheckProviderFactory")
            } else {
                // Play Integrity provider factory for official production release builds
                appCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Firebase App Check initialization skipped or failed (${e::class.java.simpleName})")
        }
    }

    companion object {
        private const val TAG = "SogoApplication"
    }
}
