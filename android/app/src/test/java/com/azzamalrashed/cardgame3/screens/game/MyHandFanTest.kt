package com.azzamalrashed.cardgame3.screens.game

import com.azzamalrashed.cardgame3.design.Haptic
import com.azzamalrashed.cardgame3.model.HandValue.Category
import com.azzamalrashed.cardgame3.services.Sound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyHandFanTest {
    @Test
    fun outerCardsSitLowerAndALiftedCardRises() {
        assertEquals(9f, HandFan.yOffset(0, lifted = false), 0.001f)
        assertEquals(3f, HandFan.yOffset(1, lifted = false), 0.001f)
        assertEquals(3f, HandFan.yOffset(2, lifted = false), 0.001f)
        assertEquals(9f, HandFan.yOffset(3, lifted = false), 0.001f)
        assertEquals(-19f, HandFan.yOffset(1, lifted = true), 0.001f)
    }

    @Test
    fun wiggleStaysWithinTwoAndAHalfDegrees() {
        for (step in 0..200) {
            val t = step * 0.013
            for (i in 0..3) assertTrue(kotlin.math.abs(HandFan.wiggle(i, t)) <= 2.5f)
        }
        // Neighbors are out of step.
        assertTrue(HandFan.wiggle(0, 1.0) != HandFan.wiggle(1, 1.0))
    }

    @Test
    fun swipeTurnsOverTheRestInItsDirection() {
        assertEquals(listOf(0, 2, 3), HandFan.inSwipeOrder(listOf(0, 2, 3), rightward = true))
        assertEquals(listOf(3, 2, 0), HandFan.inSwipeOrder(listOf(0, 2, 3), rightward = false))
        assertEquals(emptyList<Int>(), HandFan.inSwipeOrder(emptyList(), rightward = true))
    }

    @Test
    fun onlyABetterHandOfAPairOrMoreIsCelebrated() {
        assertNull(HandFan.celebration(null, Category.HighCard))
        assertNull(HandFan.celebration(Category.HighCard, Category.HighCard))
        assertNull(HandFan.celebration(Category.OnePair, Category.OnePair))
        assertNull(HandFan.celebration(Category.TwoPairs, null))

        assertEquals(HandCelebration(Sound.Match, 1f, Haptic.Medium), HandFan.celebration(Category.HighCard, Category.OnePair))
        assertEquals(HandCelebration(Sound.Match, 1f, Haptic.Medium), HandFan.celebration(null, Category.OnePair))
        assertEquals(HandCelebration(Sound.Match, 1.12f, Haptic.Medium), HandFan.celebration(Category.OnePair, Category.TwoPairs))
        assertEquals(HandCelebration(Sound.Match, 1.26f, Haptic.Medium), HandFan.celebration(Category.OnePair, Category.ThreeOfAKind))
        assertEquals(HandCelebration(Sound.Quads, 1f, Haptic.Success), HandFan.celebration(Category.ThreeOfAKind, Category.FourOfAKind))
    }
}
