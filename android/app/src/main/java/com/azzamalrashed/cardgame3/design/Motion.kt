package com.azzamalrashed.cardgame3.design

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.pow

/**
 * SwiftUI's `.spring(duration:bounce:)` as a Compose spring: the same settle time and overshoot.
 * `.smooth` is (0.5, 0), `.snappy` (0.5, 0.15), `.bouncy` (0.5, 0.3).
 */
fun <T> swiftSpring(duration: Double = 0.5, bounce: Double = 0.0, visibilityThreshold: T? = null): SpringSpec<T> {
    val stiffness = (2 * PI / duration).pow(2)
    val damping = if (bounce >= 0) 1 - bounce else 1 / (1 + bounce)
    return spring(dampingRatio = damping.toFloat(), stiffness = stiffness.toFloat(), visibilityThreshold = visibilityThreshold)
}

/** SwiftUI's `.easeInOut` curve (Material's FastOutSlowIn is a different one). Default duration 0.35 s. */
val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/**
 * The wall-clock time (epoch ms), redrawn every frame (or `fps` times a second) until `until`, then still.
 * Like SwiftUI's `TimelineView(FramesUntil(end:))`: a view that reads it animates while something is moving and
 * goes quiet when it's done. Always returns the current time, so a later redraw for another reason is up to date.
 */
@Composable
fun frameTime(until: Long?, fps: Int = 60): Long {
    frameTicks(until, fps).value
    return System.currentTimeMillis()
}

/**
 * Ticks (epoch ms) until `until` plus a last frame a little after it, so the final state is always drawn.
 * Read it inside a draw block (Canvas, `drawBehind`, `graphicsLayer`) to redraw without recomposing.
 */
@Composable
fun frameTicks(until: Long?, fps: Int = 60): State<Long> {
    val tick = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(until, fps) {
        // A last frame a little after the end; `Long.MAX_VALUE` (forever) mustn't overflow.
        val last = (until ?: 0L).let { if (it > Long.MAX_VALUE - 100) Long.MAX_VALUE else it + 100 }
        while (true) {
            if (fps >= 60) withFrameMillis { } else delay(1000L / fps)
            tick.longValue = System.currentTimeMillis()
            if (tick.longValue >= last) break
        }
    }
    return tick
}

/** Ticks every `periodMs` for as long as the view is shown (SwiftUI's `TimelineView(.periodic(by:))`). */
@Composable
fun periodicTime(periodMs: Long): Long {
    val tick = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs - System.currentTimeMillis() % periodMs)
            tick.longValue = System.currentTimeMillis()
        }
    }
    tick.longValue
    return System.currentTimeMillis()
}

/** Animations are off in the system settings (Android's closest thing to iOS's Reduce Motion). */
@Composable
fun reduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Runs `action(old, new)` when `value` changes (SwiftUI's `.onChange(of:)`). With `initial`, also once with
 * `(value, value)` when first shown.
 */
@Composable
fun <T> OnChange(value: T, initial: Boolean = false, action: suspend (old: T, new: T) -> Unit) {
    val last = remember { Last(value, first = true) }
    LaunchedEffect(value) {
        val old = last.value
        val first = last.first
        last.value = value
        last.first = false
        if (first) {
            if (initial) action(value, value)
        } else if (old != value) {
            action(old, value)
        }
    }
}

private class Last<T>(var value: T, var first: Boolean)
