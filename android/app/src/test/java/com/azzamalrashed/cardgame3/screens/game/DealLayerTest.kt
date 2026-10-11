package com.azzamalrashed.cardgame3.screens.game

import androidx.compose.ui.geometry.Offset
import com.azzamalrashed.cardgame3.model.DealTimeline
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DealLayerTest {
    private val from = Offset(100f, 200f)
    private val to = Offset(300f, 600f)

    private fun fly(progress: Double, to: Offset = this.to) =
        flyingCard(progress, from, to, toWidth = 84f, toAngle = -9f, startWidth = 16f, maxLift = 90f)

    @Test
    fun leavesTheDeckSpinning() {
        val card = fly(0.0)
        assertEquals(from, card.center)
        assertEquals(16f, card.width, 0.001f)
        assertEquals(300f, card.angle, 0.001f)
        assertEquals(1f, card.scaleX, 0.001f)
        assertEquals(1f, card.scaleY, 0.001f)
    }

    @Test
    fun landsInPlaceSquashed() {
        val card = fly(1.0)
        assertEquals(to.x, card.center.x, 0.01f)
        assertEquals(to.y, card.center.y, 0.01f)
        assertEquals(84f, card.width, 0.001f)
        assertEquals(-9f, card.angle, 0.001f)
        // Squashes over the last 15% of the flight.
        assertEquals(0.88f, card.scaleY, 0.001f)
        assertEquals(1.12f, card.scaleX, 0.001f)
        assertEquals(1f, fly(0.85).scaleY, 0.001f)
    }

    @Test
    fun arcsAboveBothEnds() {
        // Level flight: the arc rises, by at most 90 px.
        val far = Offset(2_000f, 200f)
        val top = (1..99).minOf { fly(it / 100.0, far).center.y }
        assertTrue(top < 200f)
        assertTrue(top >= 200f - 90f)
        // Up close the lift is a third of the distance or so.
        val near = Offset(140f, 200f)
        assertTrue((1..99).minOf { fly(it / 100.0, near).center.y } >= 200f - 40f * 0.35f)
    }

    @Test
    fun dealSounds() {
        val small = DealTimeline.of(listOf("a", "b", "c", "d"), "a")
        val cues = dealSoundCues(small, me = "c")
        assertEquals(0.0 to DealSoundCue.Shuffle, cues.first())
        assertEquals(cues.sortedBy { it.first }, cues)
        assertEquals(16, cues.count { it.second == DealSoundCue.Deal })
        val lands = cues.filter { it.second == DealSoundCue.Land }.map { it.first }
        assertEquals((0 until 4).map { small.departure("c", it)!! + DealTimeline.FLIGHT }, lands)

        // A full table deals fast: a swish for every other card.
        val big = DealTimeline.of((1..13).map { "p$it" }, "p1")
        assertEquals(26, dealSoundCues(big, me = null).count { it.second == DealSoundCue.Deal })
        assertEquals(0, dealSoundCues(big, me = null).count { it.second == DealSoundCue.Land })
    }

    @Test
    fun cuesLongPastAreSkipped() = runBlocking {
        val played = mutableListOf<String>()
        val cues = listOf(0.0 to "long ago", 9.95 to "just now", 10.0 to "now")
        playCuesOnTime(cues, elapsed = { 10.0 }) { played += it }
        assertEquals(listOf("just now", "now"), played)
    }
}
