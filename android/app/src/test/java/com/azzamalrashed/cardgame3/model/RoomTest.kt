package com.azzamalrashed.cardgame3.model

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Room and PublicRound helpers, against ios/CardGame3/Models/Room.swift and Round.swift. */
class RoomTest {
    private val hidden = listOf("uNoura", "zz")

    @After fun showNamesAgain() {
        for (uid in hidden) HiddenNames.set(uid, false)
    }

    // Points

    @Test fun pointsIncludeTheFinishedRoundWhenSettled() {
        assertEquals(12_000, room.points("uHost"))
        assertEquals(12_000, room.points("uHost", settled = true))
        assertEquals(6_500, room.points("uFaisal"))
        assertEquals(10_500, room.points("uSara"))
        // No change this round.
        assertEquals(12_000, room.points("uNoura"))
    }

    @Test fun pointsLeaveTheResultOutWhileRevealing() {
        assertEquals(10_000, room.points("uHost", settled = false))
        assertEquals(8_500, room.points("uFaisal", settled = false))
        assertEquals(9_500, room.points("uSara", settled = false))
    }

    @Test fun pointsOfSomeoneNotAtTheTable() {
        assertEquals(0, room.points("uOmar"))
        assertEquals(0, room.points("zz"))
        assertEquals(2_000, room.copy(table = null).points("uHost"))
        assertEquals(0, room.copy(table = null).points("uHost", settled = false))
    }

    @Test fun pointsWithoutAResult() {
        val betting = room.copy(round = room.round!!.copy(result = null))
        assertEquals(10_000, betting.points("uHost"))
        assertEquals(10_000, betting.points("uHost", settled = false))
        assertEquals(10_000, room.copy(round = null).points("uHost"))
    }

    // Names and seats

    @Test fun names() {
        assertEquals("Azzam", room.name("uHost"))
        assertEquals("Noura", room.name("uNoura"))
        assertEquals("Omar", room.name("uOmar"))
        assertEquals("?", room.name("zz"))
    }

    @Test fun hiddenNamesShowTheSeat() {
        HiddenNames.set("uNoura", true)
        assertTrue(HiddenNames.contains("uNoura"))
        assertEquals("Player 3", room.name("uNoura"))
        assertEquals("Azzam", room.name("uHost"))
        // Someone not in the room: seat 0, as iOS's `firstIndex ?? 0`.
        HiddenNames.set("zz", true)
        assertEquals("Player 1", room.name("zz"))

        HiddenNames.set("uNoura", false)
        assertFalse(HiddenNames.contains("uNoura"))
        assertEquals("Noura", room.name("uNoura"))
    }

    @Test fun seats() {
        assertEquals(0, room.seat("uHost"))
        assertEquals(2, room.seat("uNoura"))
        assertEquals(5, room.seat("uOmar"))
        assertEquals(0, room.seat("zz"))
    }

    // Who's playing

    @Test fun aiAndAway() {
        assertTrue(room.isAI("ai_k3j9x2m1q"))
        assertFalse(room.isAI("uHost"))
        assertFalse(room.isAI("uFaisal"))
        assertTrue(room.isAway("uFaisal"))
        assertFalse(room.isAway("uHost"))
        assertFalse(room.isAway("ai_k3j9x2m1q"))

        val people = room.copy(aiPlayers = null, away = null)
        assertFalse(people.isAI("ai_k3j9x2m1q"))
        assertFalse(people.isAway("uFaisal"))
    }

    @Test fun stillIn() {
        assertTrue(room.isStillIn("uHost"))
        assertTrue(room.isStillIn("ai_k3j9x2m1q"))
        // Knocked out: still listed in players, no longer seated.
        assertFalse(room.isStillIn("uOmar"))
        assertFalse(room.isStillIn("zz"))
        assertFalse(room.copy(table = null).isStillIn("uHost"))
    }

    // The round

    @Test fun highestBet() {
        assertEquals(2_000, round.highestBet)
        assertEquals(0, round.copy(bets = emptyMap()).highestBet)
        assertEquals(500, round.copy(bets = mapOf("uSara" to 500)).highestBet)
    }

    @Test fun dealRoom() {
        // The boss bet 2000; 1000 of it already went to deals.
        assertEquals(1_000, round.dealRoom)
        assertEquals(2_000, round.copy(deals = emptyMap()).dealRoom)
        assertEquals(500, round.copy(deals = mapOf("uSara" to 1_000, "uHost" to 500)).dealRoom)
        assertEquals(0, round.copy(bossId = null).dealRoom)
        assertEquals(0, round.copy(bossId = "uNoura").dealRoom)
    }

    // Fixtures

    private val round = PublicRound(
        roundNumber = 4,
        phase = PublicRound.Phase.Finished,
        dealerId = "uNoura",
        turnId = null,
        bets = mapOf("uFaisal" to 2_000, "uHost" to 1_500, "uSara" to 1_500),
        withdrawn = listOf("ai_k3j9x2m1q", "uNoura"),
        bossId = "uFaisal",
        offers = emptyMap(),
        deals = mapOf("uSara" to 1_000),
        deadline = null,
        turnDeadline = null,
        nextRoundAt = 1_760_000_128_300L,
        revealEndsAt = 1_760_000_120_300L,
        result = RoundResult(
            outcome = RoundResult.Outcome.Showdown,
            winnerId = "uHost",
            revealed = listOf("uHost", "uFaisal"),
            deltas = mapOf("uHost" to 2_000, "uFaisal" to -2_000, "uSara" to 1_000),
        ),
        revealedHands = emptyMap(),
    )

    private val room = Room(
        code = "KXRT",
        hostId = "uHost",
        status = Room.Status.Playing,
        players = listOf(
            Room.Player("uHost", "Azzam"),
            Room.Player("uSara", "Sara"),
            Room.Player("uNoura", "Noura"),
            Room.Player("uFaisal", "Faisal"),
            Room.Player("ai_k3j9x2m1q", "Bot"),
            Room.Player("uOmar", "Omar"),
        ),
        table = Table(
            seats = listOf(
                Seat("uHost", 10_000),
                Seat("uSara", 9_500),
                Seat("uNoura", 12_000),
                Seat("uFaisal", 8_500),
                Seat("ai_k3j9x2m1q", 10_000),
            ),
            dealerIndex = 2,
            roundNumber = 4,
        ),
        round = round,
        gameOver = null,
        away = listOf("uFaisal"),
        aiPlayers = listOf("ai_k3j9x2m1q"),
        peeks = mapOf("uHost" to 4),
        rematchCode = null,
        rematchBy = null,
    )
}
