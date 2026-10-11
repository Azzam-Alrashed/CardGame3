package com.azzamalrashed.cardgame3.design

import android.text.TextUtils
import android.view.View
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import java.util.Locale

// Playful look: bold solid screen colors, heavy rounded headlines, black pill buttons,
// tilted playing cards, and soft gradient blob characters with faces. Values from ios/CardGame3/Design/Theme.swift.

object Theme {
    val ink = Color(0.07f, 0.07f, 0.08f)
    val cream = Color(0.97f, 0.95f, 0.93f)

    /** Bold screen backgrounds. */
    val lime = Color(0.49f, 0.89f, 0.10f)
    val sunny = Color(1.00f, 0.93f, 0.10f)
    val hotPink = Color(1.00f, 0.38f, 0.56f)
    val grape = Color(0.55f, 0.32f, 0.98f)

    /** iOS's system red, for destructive choices ("Remove"). */
    val destructive = Color(1.00f, 0.23f, 0.19f)

    /** Blob colors, also used to give each player their own avatar color. */
    val blobColors = listOf(
        Color(0.55f, 0.89f, 0.42f), // green
        Color(1.00f, 0.48f, 0.43f), // coral
        Color(0.44f, 0.70f, 0.97f), // blue
        Color(1.00f, 0.79f, 0.30f), // yellow
        Color(0.96f, 0.60f, 0.79f), // pink
        Color(1.00f, 0.62f, 0.35f), // orange
        Color(0.70f, 0.58f, 0.97f), // lilac
    )

    fun color(forSeat: Int): Color = blobColors[forSeat.mod(blobColors.size)]

    /**
     * iOS uses SF Rounded, which can't ship on Android. The platform font is used instead (see NOTES.md);
     * swap in a bundled rounded font here (e.g. Nunito in res/font) and every screen follows.
     */
    val fontFamily: FontFamily = FontFamily.Default

    /**
     * Like iOS's natural text: each paragraph reads in its own text's direction, so in an Arabic (right-to-left)
     * layout the English sentences still read left to right ("Paid 2,000 in deals", "Lucky Lulu wins 4,500!"),
     * while it lines up with the layout's leading edge (the right, in Arabic). Compose's Start would follow the
     * text's direction instead.
     */
    private val textDirection = TextDirection.Content

    private val leading: TextAlign
        get() = if (TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL) TextAlign.Right else TextAlign.Left

    fun wordmark(size: Float) = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.Black, fontSize = size.sp,
        textDirection = textDirection, textAlign = leading,
    )

    fun body(size: Float, weight: FontWeight = FontWeight.SemiBold) = TextStyle(
        fontFamily = fontFamily, fontWeight = weight, fontSize = size.sp,
        textDirection = textDirection, textAlign = leading,
    )

    /** Same width for every digit, for numbers that tick (SwiftUI's `.monospacedDigit()`). */
    fun TextStyle.monospacedDigits() = copy(fontFeatureSettings = "tnum")
}
