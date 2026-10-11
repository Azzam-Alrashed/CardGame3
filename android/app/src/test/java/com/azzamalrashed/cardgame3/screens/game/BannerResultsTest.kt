package com.azzamalrashed.cardgame3.screens.game

import com.azzamalrashed.cardgame3.model.Card
import com.azzamalrashed.cardgame3.model.PublicRound
import com.azzamalrashed.cardgame3.model.RevealTimeline
import com.azzamalrashed.cardgame3.model.RoundResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The banner's result rules (iOS `ResultSummary`) and the countdown's clock. */
class BannerResultsTest {
    private fun cards(vararg s: String): List<Card> = s.map { Card(rankOf(it.dropLast(1)), it.takeLast(1)) }

    private fun rankOf(r: String): Int = when (r) {
        "A" -> 14
        "K" -> 13
        "Q" -> 12
        "J" -> 11
        else -> r.toInt()
    }

    private fun round(
        result: RoundResult?,
        bossId: String? = "boss",
        bets: Map<String, Int> = emptyMap(),
        deals: Map<String, Int> = emptyMap(),
        hands: Map<String, List<Card>> = emptyMap(),
    ) = PublicRound(
        roundNumber = 3,
        phase = PublicRound.Phase.Finished,
        dealerId = "boss",
        turnId = null,
        bets = bets,
        withdrawn = emptyList(),
        bossId = bossId,
        offers = emptyMap(),
        deals = deals,
        deadline = null,
        turnDeadline = null,
        nextRoundAt = null,
        revealEndsAt = null,
        result = result,
        revealedHands = hands,
    )

    private fun showdown(winner: String, revealed: List<String>, deltas: Map<String, Int> = emptyMap()) =
        RoundResult(RoundResult.Outcome.Showdown, winner, revealed, deltas)

    // Kings with a 9 kicker beat kings with an 8 kicker.
    private val kingsNine = cards("KS", "KH", "9S", "5D")
    private val kingsEight = cards("KD", "KC", "8S", "4H")

    // The same ranks: the ace of spades beats the ace of hearts.
    private val aceSpades = cards("AS", "10H", "7D", "3C")
    private val aceHearts = cards("AH", "10S", "7C", "3D")

    private val pairOfTwos = cards("2S", "2H", "6D", "3S")

    // Callout

    @Test
    fun bossWinningClearlyHasNoCallout() {
        val r = round(showdown("boss", listOf("boss", "a")), hands = mapOf("boss" to kingsNine, "a" to pairOfTwos))
        assertNull(Showdown.callout(r))
    }

    @Test
    fun challengerBeatingTheBossClearly() {
        val r = round(showdown("a", listOf("boss", "a")), hands = mapOf("boss" to pairOfTwos, "a" to kingsNine))
        assertEquals(Showdown.Callout(bossFalls = true, close = null), Showdown.callout(r))
    }

    @Test
    fun bossWinningOnTheKicker() {
        val r = round(showdown("boss", listOf("boss", "a")), hands = mapOf("boss" to kingsNine, "a" to kingsEight))
        assertEquals(Showdown.Callout(bossFalls = false, close = Showdown.Close.Kicker), Showdown.callout(r))
    }

    @Test
    fun challengerWinningOnTheSuit() {
        val r = round(showdown("a", listOf("boss", "a")), hands = mapOf("boss" to aceHearts, "a" to aceSpades))
        assertEquals(Showdown.Callout(bossFalls = true, close = Showdown.Close.Suit("♠\uFE0E", "♥\uFE0E")), Showdown.callout(r))
    }

    @Test
    fun calloutComparesWithTheStrongestRival() {
        // The runner-up is the kings with an 8, not the twos: a kicker win.
        val r = round(
            showdown("boss", listOf("boss", "a", "b")),
            hands = mapOf("boss" to kingsNine, "a" to pairOfTwos, "b" to kingsEight),
        )
        assertEquals(Showdown.Close.Kicker, Showdown.callout(r)?.close)
    }

    @Test
    fun noCalloutWithoutAShowdownOrARival() {
        val uncontested = RoundResult(RoundResult.Outcome.Uncontested, "a", emptyList(), mapOf("a" to 500))
        assertNull(Showdown.callout(round(uncontested, bossId = null)))
        assertNull(Showdown.callout(round(showdown("a", listOf("a")), hands = mapOf("a" to kingsNine))))
        assertNull(Showdown.callout(round(null)))
    }

    // Winnings and deals

    @Test
    fun winAmountIsTheBossBet() {
        val r = round(showdown("a", listOf("boss", "a")), bets = mapOf("boss" to 2_000, "a" to 1_000))
        assertEquals(2_000, Showdown.winAmount(r))
    }

    @Test
    fun winAmountWithoutABossIsTheWinnersBetOrTheirGain() {
        val uncontested = RoundResult(RoundResult.Outcome.Uncontested, "a", emptyList(), mapOf("a" to 700))
        assertEquals(1_500, Showdown.winAmount(round(uncontested, bossId = null, bets = mapOf("a" to 1_500))))
        assertEquals(700, Showdown.winAmount(round(uncontested, bossId = null)))
    }

    @Test
    fun winAmountIsZeroWithoutAWinner() {
        val redeal = RoundResult(RoundResult.Outcome.Redeal, null, emptyList(), emptyMap())
        assertEquals(0, Showdown.winAmount(round(redeal, bets = mapOf("boss" to 2_000))))
    }

    @Test
    fun dealsPaidOnlyWhenTheBossWins() {
        val deals = mapOf("x" to 500, "y" to 1_000)
        assertEquals(1_500, Showdown.dealsPaid(round(showdown("boss", listOf("boss", "a")), deals = deals)))
        assertNull(Showdown.dealsPaid(round(showdown("a", listOf("boss", "a")), deals = deals)))
        assertNull(Showdown.dealsPaid(round(showdown("boss", listOf("boss", "a")))))
    }

    @Test
    fun othersAreThePlayersWhoDidNotRevealSortedById() {
        val result = showdown("a", listOf("boss", "a"), mapOf("boss" to -2_000, "a" to 2_000, "z" to -500, "c" to 500, "m" to 0))
        assertEquals(listOf("c" to 500, "m" to 0, "z" to -500), Showdown.others(result))
    }

    @Test
    fun missingResultReadsAsARedeal() {
        assertEquals(RoundResult.Outcome.Redeal, Showdown.result(round(null)).outcome)
    }

    // Best so far

    @Test
    fun bestSoFarNeedsTwoShownChallengers() {
        val hands = mapOf("a" to pairOfTwos, "b" to kingsNine, "c" to kingsEight, "boss" to aceSpades)
        val r = round(showdown("b", listOf("boss", "a", "b", "c")), hands = hands)
        val timeline = RevealTimeline.of(r, listOf("boss", "a", "b", "c"))!!
        // Only a's hand is showing.
        assertNull(Showdown.bestSoFar(timeline, r, timeline.shownTime("a")))
        // a and b: b's kings lead.
        assertEquals("b", Showdown.bestSoFar(timeline, r, timeline.shownTime("b")))
        // c's kings with an 8 don't beat b's.
        assertEquals("b", Showdown.bestSoFar(timeline, r, timeline.shownTime("c")))
        // Gone at the verdict, and without a timeline.
        assertNull(Showdown.bestSoFar(timeline, r, timeline.verdictAt))
        assertNull(Showdown.bestSoFar(null, r, 1.0))
    }

    // Countdown

    @Test
    fun secondsLeftRoundsUp() {
        assertEquals(6, CountdownClock.secondsLeft(deadline = 10_000, now = 4_700))
        assertEquals(5, CountdownClock.secondsLeft(deadline = 10_000, now = 5_000))
        assertEquals(1, CountdownClock.secondsLeft(deadline = 10_000, now = 9_999))
        assertEquals(0, CountdownClock.secondsLeft(deadline = 10_000, now = 10_000))
        assertEquals(0, CountdownClock.secondsLeft(deadline = 10_000, now = 12_000))
    }

    @Test
    fun ticksWhenTheSecondsGoDown() {
        assertEquals(300, CountdownClock.untilNextSecond(deadline = 10_000, now = 4_700))
        assertEquals(1_000, CountdownClock.untilNextSecond(deadline = 10_000, now = 5_000))
        val now = 4_700L
        val next = now + CountdownClock.untilNextSecond(10_000, now)
        assertEquals(CountdownClock.secondsLeft(10_000, now) - 1, CountdownClock.secondsLeft(10_000, next))
    }
}
