package com.azzamalrashed.cardgame3.screens.game

import androidx.compose.ui.geometry.Offset
import com.azzamalrashed.cardgame3.design.Mood
import com.azzamalrashed.cardgame3.model.DealClock
import com.azzamalrashed.cardgame3.model.DealTimeline
import com.azzamalrashed.cardgame3.model.PublicRound.Phase
import com.azzamalrashed.cardgame3.model.RevealClock
import com.azzamalrashed.cardgame3.model.RevealTimeline
import com.azzamalrashed.cardgame3.model.RoundResult
import com.azzamalrashed.cardgame3.model.ms
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayersStripTest {
    private val showdown = RoundResult(RoundResult.Outcome.Showdown, winnerId = "b", revealed = listOf("a", "b"), deltas = emptyMap())

    @Test
    fun moods() {
        val finished = tableRound(phase = Phase.Finished, boss = "a", withdrawn = listOf("c"), result = showdown)
        assertEquals(Mood.Wink, chipMood(finished, "b", verdict = true))
        assertEquals(Mood.Sad, chipMood(finished, "a", verdict = true))
        // Before the verdict nothing gives the result away.
        assertEquals(Mood.Happy, chipMood(finished, "b", verdict = false))
        assertEquals(Mood.Surprised, chipMood(finished, "a", verdict = false))
        assertEquals(Mood.Sleepy, chipMood(finished, "c", verdict = true))
        assertEquals(Mood.Happy, chipMood(finished, "d", verdict = true))
    }

    @Test
    fun everyoneLooksAtTheDealer() {
        val round = tableRound(dealer = "b")
        val room = tableRoom(round)
        assertEquals(Offset(0f, 1f), chipLook(room, round, "b"))
        assertEquals(Offset(1f, 0.2f), chipLook(room, round, "a"))
        assertEquals(Offset(-1f, 0.2f), chipLook(room, round, "d"))
    }

    @Test
    fun peeks() {
        val round = tableRound()
        val room = tableRoom(round, ai = listOf("d"), peeks = mapOf("b" to 2, "a" to 1))
        // People: what the table was told; me: what I've flipped, before the server has it.
        assertEquals(2, chipPeeked(room, round, "b", isMe = false, clock = null, myFlips = 0, now = 0))
        assertEquals(3, chipPeeked(room, round, "a", isMe = true, clock = null, myFlips = 3, now = 0))
        assertEquals(1, chipPeeked(room, round, "a", isMe = true, clock = null, myFlips = 0, now = 0))
        // Bots: long since looked when this phone didn't see the deal; nothing right as it ends.
        assertEquals(4, chipPeeked(room, round, "d", isMe = false, clock = null, myFlips = 0, now = 0))
        val clock = DealClock(DealTimeline.of(room.players.map { it.uid }, "a"), start = 1_000, round = 1)
        assertEquals(0, chipPeeked(room, round, "d", isMe = false, clock = clock, myFlips = 0, now = clock.end))
        assertEquals(4, chipPeeked(room, round, "d", isMe = false, clock = clock, myFlips = 0, now = clock.peeksEnd))
    }

    @Test
    fun cardsTurnOverWithTheShowdown() {
        val round = tableRound(phase = Phase.Finished, boss = "a", result = showdown, revealedHands = mapOf("a" to tableHand, "b" to tableHand))
        assertEquals(0, chipFaceUp(round, "c", reveal = null, now = 0))
        assertEquals(4, chipFaceUp(round, "b", reveal = null, now = 0))
        val timeline = RevealTimeline(challengers = listOf("b"), boss = "a", per = 1.0)
        val reveal = RevealClock(timeline, start = 10_000, round = 1)
        assertEquals(0, chipFaceUp(round, "b", reveal, now = 10_000))
        assertEquals(2, chipFaceUp(round, "b", reveal, now = 10_000 + ms(timeline.flipTime("b", 1)) + 1))
        assertEquals(0, chipFaceUp(round, "a", reveal, now = 10_000 + ms(timeline.drumrollAt)))
        assertEquals(4, chipFaceUp(round, "a", reveal, now = 10_000 + ms(timeline.verdictAt)))
    }

    @Test
    fun statusTags() {
        val round = tableRound(
            phase = Phase.Deals,
            boss = "a",
            bets = mapOf("a" to 3_500, "b" to 1_000, "c" to 500),
            offers = mapOf("c" to 500),
            deals = mapOf("b" to 1_000),
            withdrawn = listOf("d"),
        )
        assertEquals(ChipStatus(ChipStatus.Kind.Boss, 3_500), chipStatus(round, "a"))
        assertEquals(ChipStatus(ChipStatus.Kind.Deal, 1_000), chipStatus(round, "b"))
        assertEquals(ChipStatus(ChipStatus.Kind.Asks, 500), chipStatus(round, "c"))
        assertEquals(ChipStatus(ChipStatus.Kind.Out), chipStatus(round, "d"))
        assertEquals(ChipStatus(ChipStatus.Kind.Bet, 500), chipStatus(tableRound(bets = mapOf("c" to 500)), "c"))
        assertEquals(ChipStatus(ChipStatus.Kind.None), chipStatus(tableRound(), "c"))
    }
}
