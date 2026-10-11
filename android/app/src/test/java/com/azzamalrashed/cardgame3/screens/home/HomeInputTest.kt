package com.azzamalrashed.cardgame3.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeInputTest {
    @Test
    fun roomCodeIsUppercased() {
        assertEquals("ABCD", roomCodeInput("abcd"))
        assertEquals("QWER", roomCodeInput("QwEr"))
    }

    @Test
    fun roomCodeKeepsOnlyLetters() {
        assertEquals("AB", roomCodeInput("a1 b-"))
        assertEquals("", roomCodeInput("1234"))
        assertEquals("", roomCodeInput(""))
    }

    @Test
    fun roomCodeStopsAtFourLetters() {
        assertEquals("ABCD", roomCodeInput("abcdef"))
        assertEquals("ABCD", roomCodeInput("a1b2c3d4e5"))
        assertEquals(ROOM_CODE_LENGTH, roomCodeInput("zzzzzzzz").length)
    }

    @Test
    fun roomCodeIsUppercasedWithoutTheLocale() {
        // Not the Turkish dotted İ, whatever the phone's language (Swift's `uppercased()` ignores the locale too).
        assertEquals("QUIZ", roomCodeInput("quiz"))
    }

    @Test
    fun nameNeedsMoreThanSpaces() {
        assertFalse(hasPlayerName(""))
        assertFalse(hasPlayerName("   "))
        assertFalse(hasPlayerName(" \t "))
        assertFalse(hasPlayerName(" ")) // no-break space
    }

    @Test
    fun nameWithLettersCounts() {
        assertTrue(hasPlayerName("Sami"))
        assertTrue(hasPlayerName("  Sami  "))
        assertTrue(hasPlayerName("سامي"))
    }
}
