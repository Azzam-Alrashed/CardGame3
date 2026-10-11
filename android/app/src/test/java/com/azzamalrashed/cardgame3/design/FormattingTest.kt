package com.azzamalrashed.cardgame3.design

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Against ios/CardGame3/Design/Formatting.swift and SwiftUI's `Int.formatted()`, run with `swift` in en_US. */
class FormattingTest {

    @Test fun shortAmounts() {
        assertEquals("500", short(500, Locale.US))
        assertEquals("1.5K", short(1500, Locale.US))
        assertEquals("2K", short(2000, Locale.US))
        assertEquals("12K", short(12000, Locale.US))
        assertEquals("3.5K", short(3500, Locale.US))
    }

    /** `short(_:)` from Formatting.swift, with the locale pinned to en_US, run with `swift`. */
    @Test fun shortMatchesSwift() {
        val swift = listOf(
            0 to "0",
            500 to "500",
            999 to "999",
            1000 to "1K",
            1049 to "1K",
            1050 to "1K",
            1150 to "1.2K",
            1250 to "1.2K",
            1500 to "1.5K",
            1949 to "1.9K",
            1950 to "2K",
            1999 to "2K",
            2000 to "2K",
            3500 to "3.5K",
            9950 to "10K",
            9999 to "10K",
            12000 to "12K",
            12345 to "12.3K",
            99999 to "100K",
            100000 to "100K",
            1234567 to "1,234.6K",
            -500 to "-500",
            -1500 to "-1500",
        )
        for ((n, expected) in swift) assertEquals("short($n)", expected, short(n, Locale.US))
    }

    /** `Int.formatted()` in en_US, run with `swift`. */
    @Test fun formattedMatchesSwift() {
        val swift = listOf(
            0 to "0",
            5 to "5",
            500 to "500",
            5000 to "5,000",
            12500 to "12,500",
            1234567 to "1,234,567",
            -5000 to "-5,000",
            -12500 to "-12,500",
        )
        for ((n, expected) in swift) assertEquals("$n", expected, n.formatted(Locale.US))
    }

    /** `String(format: "%d:%02d", left / 60, left % 60)` in Banner.swift. */
    @Test fun clockText() {
        assertEquals("1:32", clockText(92))
        assertEquals("0:05", clockText(5))
        assertEquals("0:00", clockText(0))
        assertEquals("0:45", clockText(45))
        assertEquals("1:00", clockText(60))
        assertEquals("10:00", clockText(600))
        assertEquals("59:59", clockText(3599))
    }
}
