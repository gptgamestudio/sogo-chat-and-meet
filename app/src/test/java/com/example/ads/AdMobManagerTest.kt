package com.example.ads

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdMobManagerTest {

    @Test
    fun stringsXml_containsConfiguredAdMobAppIdAndUnitIds() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals(
            AdMobConfig.PROD_APP_ID,
            context.getString(R.string.admob_app_id)
        )
        assertEquals(
            AdMobConfig.PROD_INTERSTITIAL_PRIMARY_UNIT_ID,
            context.getString(R.string.admob_interstitial_primary_unit_id)
        )
        assertEquals(
            AdMobConfig.PROD_INTERSTITIAL_SECONDARY_UNIT_ID,
            context.getString(R.string.admob_interstitial_secondary_unit_id)
        )
        assertEquals(
            AdMobConfig.PROD_REWARDED_UNIT_ID,
            context.getString(R.string.admob_rewarded_unit_id)
        )
        assertEquals("", context.getString(R.string.admob_banner_unit_id))
    }

    @Test
    fun resolveUnitIds_usesGoogleSampleTestIdsInDebugAndRealIdsInRelease() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val debugUnits = AdMobConfig.resolveUnitIds(context, isDebug = true)
        assertEquals(AdMobConfig.TEST_INTERSTITIAL_UNIT_ID, debugUnits.interstitialPrimaryId)
        assertEquals(AdMobConfig.TEST_INTERSTITIAL_UNIT_ID, debugUnits.interstitialSecondaryId)
        assertEquals(AdMobConfig.TEST_REWARDED_UNIT_ID, debugUnits.rewardedId)
        assertEquals(AdMobConfig.TEST_BANNER_UNIT_ID, debugUnits.bannerId)

        val releaseUnits = AdMobConfig.resolveUnitIds(context, isDebug = false)
        assertEquals("ca-app-pub-9786472984542330/7764767269", releaseUnits.interstitialPrimaryId)
        assertEquals("ca-app-pub-9786472984542330/2624296233", releaseUnits.interstitialSecondaryId)
        assertEquals("ca-app-pub-9786472984542330/7930474134", releaseUnits.rewardedId)
        assertEquals("", releaseUnits.bannerId)
    }

    @Test
    fun clampBannerOffsetY_constrainsVerticalDragWithinScreenBounds() {
        val maxDragUpPx = -1200f

        // Dragging below docked bottom stops at 0f
        assertEquals(0f, AdMobConfig.clampBannerOffsetY(150f, maxDragUpPx), 0.001f)
        // Dragging higher than screen top stops at maxDragUpPx
        assertEquals(-1200f, AdMobConfig.clampBannerOffsetY(-1800f, maxDragUpPx), 0.001f)
        // In-range vertical drag offset is preserved
        assertEquals(-450f, AdMobConfig.clampBannerOffsetY(-450f, maxDragUpPx), 0.001f)
        // Positive or zero container limits snap cleanly to 0f
        assertEquals(0f, AdMobConfig.clampBannerOffsetY(-100f, 10f), 0.001f)
        assertEquals(0f, AdMobConfig.clampBannerOffsetY(-100f, 0f), 0.001f)
    }

    @Test
    fun canShowInterstitial_enforcesWarmupAndCooldown() {
        val sessionStart = 1_000_000L

        // Within 20s warmup window -> must NOT show
        assertFalse(AdMobConfig.canShowInterstitial(sessionStart, 0L, sessionStart + 10_000L))
        assertFalse(AdMobConfig.canShowInterstitial(sessionStart, 0L, sessionStart + 19_999L))

        // After 20s warmup with no prior impression -> allowed
        val firstShowAt = sessionStart + 20_000L
        assertTrue(AdMobConfig.canShowInterstitial(sessionStart, 0L, firstShowAt))

        // Within 90s cooldown after previous impression -> blocked
        assertFalse(AdMobConfig.canShowInterstitial(sessionStart, firstShowAt, firstShowAt + 30_000L))
        assertFalse(AdMobConfig.canShowInterstitial(sessionStart, firstShowAt, firstShowAt + 89_999L))

        // At or after 90s cooldown -> allowed again
        assertTrue(AdMobConfig.canShowInterstitial(sessionStart, firstShowAt, firstShowAt + 90_000L))
    }

    @Test
    fun showInterstitialIfEligible_alwaysInvokesCallbackWhenNotLoadedOrNoActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var now = 1_000_000L
        val manager = AdMobManager(context, isDebug = true, clockMs = { now })

        now += 30_000L
        var callbackCount = 0
        manager.showInterstitialIfEligible(activity = null) {
            callbackCount++
        }

        assertEquals(1, callbackCount)
    }
}
