package com.example.ads

import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldLight
import com.example.ui.theme.MatteBlackSurface
import com.example.ui.theme.MatteBlackSurfaceVariant
import com.example.ui.theme.TextMuted
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import kotlin.math.roundToInt

/**
 * Compact AdMob banner anchored at the very bottom of the screen by default and freely
 * draggable up and down via touch within safe screen bounds.
 * Automatically hides while the on-screen keyboard (IME) is open.
 */
@Composable
fun MovableBottomAdBanner(
    bannerUnitId: String,
    defaultBottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    if (bannerUnitId.isBlank()) return

    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    if (imeBottomPx > 0) return

    val lifecycleOwner = LocalLifecycleOwner.current
    var adViewRef by remember(bannerUnitId) { mutableStateOf<AdView?>(null) }

    DisposableEffect(lifecycleOwner, adViewRef) {
        val adView = adViewRef
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> adView?.resume()
                Lifecycle.Event.ON_PAUSE -> adView?.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(bannerUnitId) {
        onDispose {
            adViewRef?.destroy()
            adViewRef = null
        }
    }

    var offsetY by remember { mutableFloatStateOf(0f) }
    var bannerHeightPx by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val containerHeightPx = constraints.maxHeight.toFloat()
        val maxDragUpPx = -((containerHeightPx - bannerHeightPx).coerceAtLeast(0f))

        LaunchedEffect(maxDragUpPx) {
            offsetY = AdMobConfig.clampBannerOffsetY(offsetY, maxDragUpPx)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp + defaultBottomPadding),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 336.dp)
                    .fillMaxWidth()
                    .offset { IntOffset(0, offsetY.roundToInt()) }
                    .onSizeChanged { bannerHeightPx = it.height.toFloat() }
                    .pointerInput(maxDragUpPx) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            offsetY = AdMobConfig.clampBannerOffsetY(
                                rawOffsetY = offsetY + dragAmount,
                                maxDragUpPx = maxDragUpPx
                            )
                        }
                    }
                    .testTag("movable_ad_banner"),
                shape = RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = 10.dp,
                    bottomEnd = 10.dp
                ),
                color = MatteBlackSurface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.45f)),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Slim draggable header strip to move banner up/down via touch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .background(MatteBlackSurfaceVariant.copy(alpha = 0.85f))
                            .padding(horizontal = 10.dp)
                            .semantics {
                                contentDescription = "Move ad banner up or down"
                            }
                            .testTag("ad_banner_drag_handle"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "AD",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .background(GoldAccent.copy(alpha = 0.8f))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "Drag ad banner up or down",
                                tint = GoldAccent,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        if (offsetY < -12f) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { offsetY = 0f }
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                    .testTag("ad_banner_dock_bottom_button"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Down",
                                    color = GoldLight,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Dock ad banner back to bottom",
                                    tint = GoldLight,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "Move ↕",
                                color = TextMuted,
                                fontSize = 9.sp
                            )
                        }
                    }

                    // Compact 320x50 AdMob Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .padding(bottom = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            modifier = Modifier
                                .widthIn(max = 320.dp)
                                .height(50.dp),
                            factory = { ctx ->
                                try {
                                    AdView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.WRAP_CONTENT,
                                            ViewGroup.LayoutParams.WRAP_CONTENT
                                        )
                                        setAdSize(AdSize.BANNER)
                                        adUnitId = bannerUnitId
                                        loadAd(AdRequest.Builder().build())
                                    }.also { created ->
                                        adViewRef = created
                                    }
                                } catch (_: Throwable) {
                                    android.widget.FrameLayout(ctx)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
