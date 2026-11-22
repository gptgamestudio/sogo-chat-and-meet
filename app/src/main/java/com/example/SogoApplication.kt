package com.example

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.Fetcher
import coil.request.Options
import com.example.ads.AdMobManager
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class SogoApplication : Application(), ImageLoaderFactory {

    val adMobManager: AdMobManager by lazy { AdMobManager(this) }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(DataUriFetcherFactory())
            }
            .crossfade(true)
            .build()
    }

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

    private class DataUriFetcherFactory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!data.scheme.equals("data", ignoreCase = true)) return null
            return Fetcher {
                val raw = data.toString()
                val commaIdx = raw.indexOf(',')
                require(commaIdx > 0) { "Malformed data URI" }
                val header = raw.substring(0, commaIdx)
                val payload = raw.substring(commaIdx + 1)
                val bytes = if (header.contains(";base64", ignoreCase = true)) {
                    Base64.decode(payload, Base64.DEFAULT)
                } else {
                    payload.toByteArray()
                }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: error("Unable to decode base64 profile image")
                DrawableResult(
                    drawable = bitmap.toDrawable(options.context.resources),
                    isSampled = false,
                    dataSource = DataSource.MEMORY
                )
            }
        }
    }

    companion object {
        private const val TAG = "SogoApplication"
    }
}
