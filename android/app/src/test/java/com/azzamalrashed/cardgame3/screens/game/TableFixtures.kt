package com.azzamalrashed.cardgame3.screens.game

import com.azzamalrashed.cardgame3.model.Card
import com.azzamalrashed.cardgame3.model.PublicRound
import com.azzamalrashed.cardgame3.model.Room
import com.azzamalrashed.cardgame3.model.RoundResult
import com.azzamalrashed.cardgame3.model.Seat
import com.azzamalrashed.cardgame3.model.Table

/** Rooms and rounds for the table tests: players "a" to "d", "a" deals unless said otherwise. */
internal fun tableRound(
    number: Int = 1,
    phase: PublicRound.Phase = PublicRound.Phase.Betting,
    dealer: String = "a",
    turn: String? = null,
    bets: Map<String, Int> = emptyMap(),
    withdrawn: List<String> = emptyList(),
    boss: String? = null,
    offers: Map<String, Int> = emptyMap(),
    deals: Map<String, Int> = emptyMap(),
    result: RoundResult? = null,
    revealedHands: Map<String, List<Card>> = emptyMap(),
) = PublicRound(
    roundNumber = number,
    phase = phase,
    dealerId = dealer,
    turnId = turn,
    bets = bets,
    withdrawn = withdrawn,
    bossId = boss,
    offers = offers,
    deals = deals,
    deadline = null,
    turnDeadline = null,
    nextRoundAt = null,
    revealEndsAt = null,
    result = result,
    revealedHands = revealedHands,
)

internal fun tableRoom(
    round: PublicRound,
    players: List<String> = listOf("a", "b", "c", "d"),
    points: Map<String, Int> = emptyMap(),
    ai: List<String>? = null,
    away: List<String>? = null,
    peeks: Map<String, Int>? = null,
) = Room(
    code = "ABCD",
    hostId = players.first(),
    status = Room.Status.Playing,
    players = players.map { Room.Player(it, it.uppercase()) },
    table = Table(players.map { Seat(it, points[it] ?: 5_000) }, dealerIndex = 0, roundNumber = round.roundNumber),
    round = round,
    gameOver = null,
    away = away,
    aiPlayers = ai,
    peeks = peeks,
    rematchCode = null,
    rematchBy = null,
)

internal val tableHand = listOf(Card(14, "S"), Card(13, "H"), Card(12, "D"), Card(11, "C"))
