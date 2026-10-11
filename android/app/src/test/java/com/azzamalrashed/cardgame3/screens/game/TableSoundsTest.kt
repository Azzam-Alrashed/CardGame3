package com.azzamalrashed.cardgame3.screens.game

import com.azzamalrashed.cardgame3.model.PublicRound
import com.azzamalrashed.cardgame3.model.PublicRound.Phase
import com.azzamalrashed.cardgame3.model.RevealTimeline
import com.azzamalrashed.cardgame3.model.RoundResult
import com.azzamalrashed.cardgame3.services.Sound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TableSoundsTest {
    private fun sounds(old: PublicRound, new: PublicRound, staged: Boolean = false) =
        tableSounds(old, new, tableRoom(new, points = mapOf("b" to 3_000)), me = "a", revealStaged = staged)

    @Test
    fun aNewRoundIsQuietHere() {
        assertEquals(emptyList<Sound>(), sounds(tableRound(1), tableRound(2, bets = mapOf("b" to 500))))
    }

    @Test
    fun betsSoundByAmountAndAllIn() {
        val before = tableRound()
        assertEquals(listOf(Sound.Chip), sounds(before, tableRound(bets = mapOf("c" to 500))))
        assertEquals(listOf(Sound.Chips), sounds(before, tableRound(bets = mapOf("c" to 1_500))))
        assertEquals(listOf(Sound.ChipsBig), sounds(before, tableRound(bets = mapOf("c" to 2_000))))
        // "b" has 3,000 points: betting them all is all in.
        assertEquals(listOf(Sound.AllIn), sounds(before, tableRound(bets = mapOf("b" to 3_000))))
        // Only bets that changed sound.
        assertEquals(listOf(Sound.Chips), sounds(tableRound(bets = mapOf("c" to 500)), tableRound(bets = mapOf("c" to 500, "d" to 1_000))))
    }

    @Test
    fun foldOfferAndBoss() {
        assertEquals(listOf(Sound.Fold), sounds(tableRound(), tableRound(withdrawn = listOf("b"))))
        val deals = tableRound(phase = Phase.Deals, boss = "a")
        assertEquals(listOf(Sound.Offer), sounds(deals, deals.copy(offers = mapOf("b" to 500))))
        assertEquals(listOf(Sound.Offer), sounds(deals.copy(offers = mapOf("b" to 500)), deals.copy(offers = mapOf("b" to 1_000))))
        assertEquals(listOf(Sound.Boss), sounds(tableRound(), deals))
    }

    @Test
    fun acceptedAndRejectedOffers() {
        val asked = tableRound(phase = Phase.Deals, boss = "a", offers = mapOf("b" to 500))
        assertEquals(listOf(Sound.Accept), sounds(asked, asked.copy(offers = emptyMap(), deals = mapOf("b" to 500))))
        assertEquals(listOf(Sound.Reject), sounds(asked, asked.copy(offers = emptyMap())))
    }

    @Test
    fun theVerdictSoundsUnlessTheShowdownIsStaged() {
        val result = RoundResult(RoundResult.Outcome.Uncontested, winnerId = "a", revealed = emptyList(), deltas = mapOf("a" to 500))
        val betting = tableRound(bets = mapOf("a" to 500))
        val finished = betting.copy(phase = Phase.Finished, result = result)
        assertEquals(listOf(Sound.Win), sounds(betting, finished))
        assertEquals(emptyList<Sound>(), sounds(betting, finished, staged = true))
    }

    @Test
    fun verdictSounds() {
        fun result(outcome: RoundResult.Outcome, winner: String?, mine: Int) =
            RoundResult(outcome, winner, emptyList(), mapOf("a" to mine))
        assertEquals(Sound.Fold, tableVerdictSound(result(RoundResult.Outcome.Redeal, null, 0), "a"))
        assertEquals(Sound.Win, tableVerdictSound(result(RoundResult.Outcome.Showdown, "a", 1_000), "a"))
        assertEquals(Sound.Lose, tableVerdictSound(result(RoundResult.Outcome.Showdown, "b", -500), "a"))
        assertEquals(Sound.Verdict, tableVerdictSound(result(RoundResult.Outcome.Showdown, "b", 0), "a"))
    }

    @Test
    fun revealCuesFollowTheShowdown() {
        val timeline = RevealTimeline(challengers = listOf("b", "c"), boss = "a", per = 1.0)
        val cues = revealSoundCues(timeline)
        // The drumroll, four flips per hand, the verdict.
        assertEquals(1 + 3 * 4 + 1, cues.size)
        assertEquals(cues.sortedBy { it.first }, cues)
        assertEquals(timeline.drumrollAt to RevealSoundCue.Drumroll, cues.first { it.second == RevealSoundCue.Drumroll })
        assertEquals(listOf(timeline.flipTime("a", 3) to RevealSoundCue.LastFlip), cues.filter { it.second == RevealSoundCue.LastFlip })
        assertEquals(timeline.verdictAt to RevealSoundCue.Verdict, cues.last())
        // Every challenger's cards turn over before the drumroll.
        assertTrue(cues.takeWhile { it.second != RevealSoundCue.Drumroll }.all { it.second == RevealSoundCue.Flip })
        assertEquals(8, cues.takeWhile { it.second != RevealSoundCue.Drumroll }.size)
    }
}
