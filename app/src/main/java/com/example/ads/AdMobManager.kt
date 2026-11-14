package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.example.BuildConfig
import com.example.R
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdUnitIds(
    val interstitialPrimaryId: String,
    val interstitialSecondaryId: String,
    val rewardedId: String
)

object AdMobConfig {
    // Official Google Mobile Ads sample test unit IDs (used automatically in DEBUG builds)
    const val TEST_INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_REWARDED_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    // Production SOGO CHAT AND MEET AdMob unit IDs (used automatically in RELEASE builds)
    const val PROD_APP_ID = "ca-app-pub-9786472984542330~4017093946"
    const val PROD_INTERSTITIAL_PRIMARY_UNIT_ID = "ca-app-pub-9786472984542330/7764767269"
    const val PROD_INTERSTITIAL_SECONDARY_UNIT_ID = "ca-app-pub-9786472984542330/2624296233"
    const val PROD_REWARDED_UNIT_ID = "ca-app-pub-9786472984542330/7930474134"

    const val MIN_SESSION_WARMUP_MS = 20_000L
    const val MIN_INTERSTITIAL_INTERVAL_MS = 90_000L

    fun resolveUnitIds(context: Context, isDebug: Boolean = BuildConfig.DEBUG): AdUnitIds {
        return if (isDebug) {
            AdUnitIds(
                interstitialPrimaryId = TEST_INTERSTITIAL_UNIT_ID,
                interstitialSecondaryId = TEST_INTERSTITIAL_UNIT_ID,
                rewardedId = TEST_REWARDED_UNIT_ID
            )
        } else {
            AdUnitIds(
                interstitialPrimaryId = context.getString(R.string.admob_interstitial_primary_unit_id),
                interstitialSecondaryId = context.getString(R.string.admob_interstitial_secondary_unit_id),
                rewardedId = context.getString(R.string.admob_rewarded_unit_id)
            )
        }
    }

    fun canShowInterstitial(
        sessionStartMs: Long,
        lastShownMs: Long,
        nowMs: Long
    ): Boolean {
        if (nowMs - sessionStartMs < MIN_SESSION_WARMUP_MS) return false
        if (lastShownMs > 0L && nowMs - lastShownMs < MIN_INTERSTITIAL_INTERVAL_MS) return false
        return true
    }
}

class AdMobManager(
    context: Context,
    private val isDebug: Boolean = BuildConfig.DEBUG,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val unitIds: AdUnitIds = AdMobConfig.resolveUnitIds(appContext, isDebug)

    private val sessionStartMs: Long = clockMs()
    private var lastInterstitialShownMs: Long = 0L
    private var preferPrimaryInterstitial: Boolean = true
    private var isInitialized: Boolean = false

    private var primaryInterstitial: InterstitialAd? = null
    private var secondaryInterstitial: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    private var isLoadingPrimaryInterstitial = false
    private var isLoadingSecondaryInterstitial = false
    private var isLoadingRewarded = false

    private val _isInterstitialReady = MutableStateFlow(false)
    val isInterstitialReady: StateFlow<Boolean> = _isInterstitialReady.asStateFlow()

    private val _isRewardedReady = MutableStateFlow(false)
    val isRewardedReady: StateFlow<Boolean> = _isRewardedReady.asStateFlow()

    private val _rewardCredits = MutableStateFlow(0)
    val rewardCredits: StateFlow<Int> = _rewardCredits.asStateFlow()

    private val _adStatusMessage = MutableStateFlow<String?>(null)
    val adStatusMessage: StateFlow<String?> = _adStatusMessage.asStateFlow()

    fun initialize() {
        if (isInitialized) return
        isInitialized = true
        ioScope.launch {
            try {
                MobileAds.initialize(appContext) {
                    scope.launch {
                        preloadAll()
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "MobileAds initialization skipped (${t::class.java.simpleName})")
            }
        }
    }

    fun preloadAll() {
        loadPrimaryInterstitial()
        loadSecondaryInterstitial()
        loadRewardedAd()
    }

    fun clearStatusMessage() {
        _adStatusMessage.value = null
    }

    private fun updateInterstitialReadyFlag() {
        _isInterstitialReady.value = primaryInterstitial != null || secondaryInterstitial != null
    }

    fun loadPrimaryInterstitial() {
        if (primaryInterstitial != null || isLoadingPrimaryInterstitial) return
        isLoadingPrimaryInterstitial = true
        try {
            InterstitialAd.load(
                appContext,
                unitIds.interstitialPrimaryId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        isLoadingPrimaryInterstitial = false
                        primaryInterstitial = ad
                        updateInterstitialReadyFlag()
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isLoadingPrimaryInterstitial = false
                        primaryInterstitial = null
                        updateInterstitialReadyFlag()
                        Log.d(TAG, "Primary interstitial failed to load: code=${error.code}")
                    }
                }
            )
        } catch (t: Throwable) {
            isLoadingPrimaryInterstitial = false
            Log.w(TAG, "Primary interstitial load error (${t::class.java.simpleName})")
        }
    }

    fun loadSecondaryInterstitial() {
        if (secondaryInterstitial != null || isLoadingSecondaryInterstitial) return
        isLoadingSecondaryInterstitial = true
        try {
            InterstitialAd.load(
                appContext,
                unitIds.interstitialSecondaryId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        isLoadingSecondaryInterstitial = false
                        secondaryInterstitial = ad
                        updateInterstitialReadyFlag()
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isLoadingSecondaryInterstitial = false
                        secondaryInterstitial = null
                        updateInterstitialReadyFlag()
                        Log.d(TAG, "Secondary interstitial failed to load: code=${error.code}")
                    }
                }
            )
        } catch (t: Throwable) {
            isLoadingSecondaryInterstitial = false
            Log.w(TAG, "Secondary interstitial load error (${t::class.java.simpleName})")
        }
    }

    fun loadRewardedAd() {
        if (rewardedAd != null || isLoadingRewarded) return
        isLoadingRewarded = true
        try {
            RewardedAd.load(
                appContext,
                unitIds.rewardedId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        isLoadingRewarded = false
                        rewardedAd = ad
                        _isRewardedReady.value = true
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isLoadingRewarded = false
                        rewardedAd = null
                        _isRewardedReady.value = false
                        Log.d(TAG, "Rewarded ad failed to load: code=${error.code}")
                    }
                }
            )
        } catch (t: Throwable) {
            isLoadingRewarded = false
            Log.w(TAG, "Rewarded ad load error (${t::class.java.simpleName})")
        }
    }

    /**
     * Shows a frequency-capped interstitial ad at natural screen transitions (never during auth,
     * onboarding, or safety actions). Always invokes [onCompleted] so navigation is never blocked.
     */
    fun showInterstitialIfEligible(activity: Activity?, onCompleted: () -> Unit) {
        val now = clockMs()
        if (activity == null || !AdMobConfig.canShowInterstitial(sessionStartMs, lastInterstitialShownMs, now)) {
            onCompleted()
            return
        }

        val usePrimary = when {
            preferPrimaryInterstitial && primaryInterstitial != null -> true
            !preferPrimaryInterstitial && secondaryInterstitial != null -> false
            primaryInterstitial != null -> true
            secondaryInterstitial != null -> false
            else -> {
                preloadAll()
                onCompleted()
                return
            }
        }

        val selectedAd = if (usePrimary) primaryInterstitial else secondaryInterstitial
        if (selectedAd == null) {
            preloadAll()
            onCompleted()
            return
        }

        if (usePrimary) {
            primaryInterstitial = null
        } else {
            secondaryInterstitial = null
        }
        updateInterstitialReadyFlag()
        preferPrimaryInterstitial = !usePrimary
        lastInterstitialShownMs = now

        selectedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                if (usePrimary) loadPrimaryInterstitial() else loadSecondaryInterstitial()
                onCompleted()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                if (usePrimary) loadPrimaryInterstitial() else loadSecondaryInterstitial()
                onCompleted()
            }
        }
        selectedAd.show(activity)
    }

    /**
     * Shows the user-initiated Rewarded Ad ("Support SOGO CHAT") and grants the configured reward.
     */
    fun showRewardedAd(activity: Activity?, onRewardEarned: (Int) -> Unit = {}) {
        val ad = rewardedAd
        if (activity == null || ad == null) {
            loadRewardedAd()
            _adStatusMessage.value = "Ad is still preparing. Please try again in a few seconds."
            return
        }

        rewardedAd = null
        _isRewardedReady.value = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadRewardedAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                _adStatusMessage.value = "Could not display rewarded ad right now."
                loadRewardedAd()
            }
        }

        ad.show(activity) { rewardItem ->
            val earned = rewardItem.amount.coerceAtLeast(1)
            _rewardCredits.value += earned
            _adStatusMessage.value = "Thank you for supporting SOGO CHAT! +$earned ${rewardItem.type} earned."
            onRewardEarned(earned)
        }
    }

    companion object {
        private const val TAG = "AdMobManager"
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
