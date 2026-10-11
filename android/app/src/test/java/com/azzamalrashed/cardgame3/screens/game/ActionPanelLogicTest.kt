package com.azzamalrashed.cardgame3.screens.game

import com.azzamalrashed.cardgame3.model.PublicRound
import com.azzamalrashed.cardgame3.model.Room
import com.azzamalrashed.cardgame3.model.Seat
import com.azzamalrashed.cardgame3.model.Table
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionPanelLogicTest {
    // Bet math

    @Test
    fun minBetIsTheNextStepAboveTheHighestBet() {
        assertEquals(500, ActionMath.minBet(0))
        assertEquals(1000, ActionMath.minBet(500))
        assertEquals(2000, ActionMath.minBet(1500))
        // An odd all-in amount: the next whole step above it.
        assertEquals(2000, ActionMath.minBet(1700))
        assertEquals(500, ActionMath.minBet(250))
    }

    @Test
    fun roundDownKeepsWholeStepsAndAtLeastOne() {
        assertEquals(2000, ActionMath.roundDown(2000))
        assertEquals(1500, ActionMath.roundDown(1999))
        assertEquals(500, ActionMath.roundDown(500))
        assertEquals(500, ActionMath.roundDown(300))
        assertEquals(500, ActionMath.roundDown(0))
    }

    @Test
    fun stepsStayInsideTheRange() {
        val range = 1000..3000
        assertEquals(1500, ActionMath.less(2000, range))
        assertEquals(1000, ActionMath.less(1000, range))
        assertEquals(2500, ActionMath.more(2000, range))
        assertEquals(3000, ActionMath.more(3000, range))
        // The room for deals shrank under the amount (as on iOS, it isn't clamped): − steps down, + snaps to the top.
        assertEquals(3500, ActionMath.less(4000, range))
        assertEquals(3000, ActionMath.more(4000, range))
    }

    @Test
    fun secondsLeftRoundsUpAndStopsAtZero() {
        assertEquals(6, ActionMath.secondsLeft(at = 10_000, now = 4_500))
        assertEquals(1, ActionMath.secondsLeft(at = 10_000, now = 9_999))
        assertEquals(0, ActionMath.secondsLeft(at = 10_000, now = 10_000))
        assertEquals(0, ActionMath.secondsLeft(at = 10_000, now = 12_000))
    }

    // Which controls show

    private fun round(
        phase: PublicRound.Phase,
        turnId: String? = null,
        bets: Map<String, Int> = emptyMap(),
        withdrawn: List<String> = emptyList(),
        bossId: String? = null,
        deals: Map<String, Int> = emptyMap(),
    ) = PublicRound(
        roundNumber = 1,
        phase = phase,
        dealerId = "a",
        turnId = turnId,
        bets = bets,
        withdrawn = withdrawn,
        bossId = bossId,
        offers = emptyMap(),
        deals = deals,
        deadline = null,
        turnDeadline = null,
        nextRoundAt = null,
        revealEndsAt = null,
        result = null,
        revealedHands = emptyMap(),
    )

    private fun room(away: List<String>? = null, seats: List<String> = listOf("a", "b", "me")) = Room(
        code = "ABCD",
        hostId = "a",
        status = Room.Status.Playing,
        players = listOf("a", "b", "me").map { Room.Player(it, it) },
        table = Table(seats.map { Seat(it, 5000) }, dealerIndex = 0, roundNumber = 1),
        round = null,
        gameOver = null,
        away = away,
        aiPlayers = null,
        peeks = null,
        rematchCode = null,
        rematchBy = null,
    )

    @Test
    fun aBotHoldingMySeatComesFirst() {
        val betting = round(PublicRound.Phase.Betting, turnId = "me")
        assertEquals(PanelMode.BotPlaying, panelMode(room(away = listOf("me")), betting, "me"))
        // Knocked out and away: nothing to take back.
        assertEquals(PanelMode.YourTurn, panelMode(room(away = listOf("me"), seats = listOf("a", "b")), betting, "me"))
    }

    @Test
    fun betting() {
        val room = room()
        assertEquals(PanelMode.YourTurn, panelMode(room, round(PublicRound.Phase.Betting, turnId = "me"), "me"))
        assertEquals(
            PanelMode.BetPlaced,
            panelMode(room, round(PublicRound.Phase.Betting, turnId = "b", bets = mapOf("me" to 1000)), "me"),
        )
        assertEquals(
            PanelMode.SatOut,
            panelMode(room, round(PublicRound.Phase.Betting, turnId = "b", withdrawn = listOf("me")), "me"),
        )
        assertEquals(PanelMode.TurnComing, panelMode(room, round(PublicRound.Phase.Betting, turnId = "b"), "me"))
    }

    @Test
    fun deals() {
        val room = room()
        val bets = mapOf("a" to 2000, "me" to 1000)
        assertEquals(PanelMode.Boss, panelMode(room, round(PublicRound.Phase.Deals, bets = bets, bossId = "me"), "me"))
        assertEquals(
            PanelMode.DealLocked,
            panelMode(room, round(PublicRound.Phase.Deals, bets = bets, bossId = "a", deals = mapOf("me" to 500)), "me"),
        )
        assertEquals(PanelMode.Offer, panelMode(room, round(PublicRound.Phase.Deals, bets = bets, bossId = "a"), "me"))
        // The boss's 2,000 is all promised to others.
        assertEquals(
            PanelMode.NoRoom,
            panelMode(
                room,
                round(PublicRound.Phase.Deals, bets = bets + ("b" to 1000), bossId = "a", deals = mapOf("b" to 1800)),
                "me",
            ),
        )
        assertEquals(
            PanelMode.Watching,
            panelMode(room, round(PublicRound.Phase.Deals, bets = mapOf("a" to 2000), bossId = "a", withdrawn = listOf("me")), "me"),
        )
    }

    @Test
    fun afterTheRound() {
        assertEquals(PanelMode.AfterRound, panelMode(room(), round(PublicRound.Phase.Finished), "me"))
    }
}
