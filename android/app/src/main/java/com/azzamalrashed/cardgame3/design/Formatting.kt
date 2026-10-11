package com.azzamalrashed.cardgame3.design

import java.text.NumberFormat
import java.util.Locale

/** 5000 → "5,000", in the phone's language (SwiftUI's `Int.formatted()`). */
fun Int.formatted(locale: Locale = Locale.getDefault()): String = NumberFormat.getIntegerInstance(locale).format(this)

/** 500 → "500", 1500 → "1.5K", 12000 → "12K". */
fun short(n: Int, locale: Locale = Locale.getDefault()): String {
    if (n < 1000) return "$n"
    val format = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
    }
    return format.format(n / 1000.0) + "K"
}

/** "1:32": minutes and seconds left on a timer. */
fun clockText(secondsLeft: Int): String = "%d:%02d".format(Locale.ROOT, secondsLeft / 60, secondsLeft % 60)
