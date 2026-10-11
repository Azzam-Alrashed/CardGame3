package com.azzamalrashed.cardgame3.design

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** The iOS feedback styles the app uses (`sensoryFeedback`, `UIImpactFeedbackGenerator`). */
enum class Haptic { Light, Medium, Heavy, Success, Warning, Selection }

/** Plays haptics through the view, so they follow the phone's touch-feedback setting. */
class Haptics(private val view: View) {
    fun play(haptic: Haptic) {
        val constant = when (haptic) {
            Haptic.Light -> HapticFeedbackConstants.KEYBOARD_TAP
            Haptic.Medium -> HapticFeedbackConstants.CONTEXT_CLICK
            Haptic.Heavy -> HapticFeedbackConstants.LONG_PRESS
            Haptic.Success ->
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
            Haptic.Warning ->
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
            Haptic.Selection ->
                if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
