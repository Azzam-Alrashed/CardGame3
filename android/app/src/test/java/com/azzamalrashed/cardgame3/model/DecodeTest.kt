package com.azzamalrashed.cardgame3.model

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Firestore documents as the Android SDK hands them over: maps and lists, whole numbers as Long (some written as
 * doubles come back as Double), explicit nulls, and server fields the app doesn't read (playerIds, botStyles,
 * createdAt, wakeAt, changedAt…). Shapes from `Room` (rooms.ts) and `PublicRound` (store.ts).
 */
class DecodeTest {

    // Rooms

    @Test fun lobbyRoom() {
        val room = Decode.room(lobbyDoc())
        assertEquals(
            Room(
                code = "KXRT",
                hostId = "uHost",
                status = Room.Status.Lobby,
                players = listOf(Room.Player("uHost", "Azzam"), Room.Player("uSara", "Sara"), Room.Player("ai_k3j9x2m1q", "Bot")),
                table = null,
                round = null,
                gameOver = null,
                away = null,
                aiPlayers = listOf("ai_k3j9x2m1q"),
                peeks = null,
                rematchCode = null,
                rematchBy = null,
            ),
            room,
        )
    }

    @Test fun playingRoomWithBettingRound() {
        val room = Decode.room(playingDoc())!!
        assertEquals(Room.Status.Playing, room.status)
        assertEquals(
            Table(
                seats = listOf(
                    Seat("uHost", 10000),
                    Seat("uSara", 9500),
                    Seat("uNoura", 12000),
                    Seat("uFaisal", 8500),
                    Seat("ai_k3j9x2m1q", 10000),
                ),
                dealerIndex = 1,
                roundNumber = 3,
            ),
            room.table,
        )
        assertEquals(
            PublicRound(
                roundNumber = 3,
                phase = PublicRound.Phase.Betting,
                dealerId = "uSara",
                turnId = "uFaisal",
                bets = mapOf("uNoura" to 1000),
                withdrawn = emptyList(),
                bossId = null,
                offers = emptyMap(),
                deals = emptyMap(),
                deadline = null,
                turnDeadline = 1_760_000_045_000L,
                nextRoundAt = null,
                revealEndsAt = null,
                result = null,
                revealedHands = emptyMap(),
            ),
            room.round,
        )
        assertEquals(listOf("uFaisal"), room.away)
        assertEquals(listOf("ai_k3j9x2m1q"), room.aiPlayers)
        assertEquals(mapOf("uHost" to 2, "uNoura" to 4), room.peeks)
        assertNull(room.gameOver)
        assertNull(room.rematchCode)
        assertNull(room.rematchBy)
    }

    @Test fun finishedRoundWithResultAndRevealedHands() {
        val round = Decode.room(playingDoc().also { it["round"] = finishedRound() })!!.round!!
        assertEquals(
            PublicRound(
                roundNumber = 4,
                phase = PublicRound.Phase.Finished,
                dealerId = "uNoura",
                turnId = null,
                bets = mapOf("uFaisal" to 2000, "uHost" to 1500, "uSara" to 1500),
                withdrawn = listOf("ai_k3j9x2m1q", "uNoura"),
                bossId = "uFaisal",
                offers = mapOf("uHost" to 500),
                deals = mapOf("uSara" to 1000),
                deadline = null,
                turnDeadline = null,
                nextRoundAt = 1_760_000_128_300L,
                revealEndsAt = 1_760_000_120_300L,
                result = RoundResult(
                    outcome = RoundResult.Outcome.Showdown,
                    winnerId = "uHost",
                    revealed = listOf("uHost", "uFaisal"),
                    deltas = mapOf("uHost" to 2000, "uFaisal" to -3000, "uSara" to 1000),
                ),
                revealedHands = mapOf(
                    "uHost" to listOf(Card(14, "S"), Card(14, "H"), Card(9, "D"), Card(9, "C")),
                    "uFaisal" to listOf(Card(13, "S"), Card(13, "H"), Card(12, "D"), Card(11, "C")),
                ),
            ),
            round,
        )
    }

    @Test fun everyOutcomeAndPhaseReads() {
        for (outcome in RoundResult.Outcome.entries) {
            val doc = playingDoc().also {
                it["round"] = finishedRound().also { r ->
                    r["result"] = (r["result"] as Map<*, *>) + mapOf("outcome" to outcome.wire, "winnerId" to null)
                }
            }
            val result = Decode.room(doc)!!.round!!.result!!
            assertEquals(outcome, result.outcome)
            assertNull(result.winnerId)
        }
        assertEquals(listOf("redeal", "uncontested", "allDeals", "showdown"), RoundResult.Outcome.entries.map { it.wire })
        assertEquals(listOf("betting", "deals", "finished"), PublicRound.Phase.entries.map { it.wire })
        assertEquals(listOf("lobby", "playing", "finished"), Room.Status.entries.map { it.wire })
    }

    @Test fun dealsPhaseWithDeadline() {
        val doc = playingDoc().also {
            it["round"] = (it["round"] as Map<*, *>) + mapOf(
                "phase" to "deals", "turnId" to null, "bossId" to "uNoura", "deadline" to 1_760_000_300_000L,
                "turnDeadline" to null, "offers" to mapOf("uHost" to 500L, "uSara" to 1000.0),
            )
        }
        val round = Decode.room(doc)!!.round!!
        assertEquals(PublicRound.Phase.Deals, round.phase)
        assertEquals("uNoura", round.bossId)
        assertEquals(1_760_000_300_000L, round.deadline)
        assertNull(round.turnDeadline)
        assertEquals(mapOf("uHost" to 500, "uSara" to 1000), round.offers)
    }

    @Test fun finishedGame() {
        val room = Decode.room(finishedDoc())!!
        assertEquals(Room.Status.Finished, room.status)
        assertEquals(
            GameOver(winnerId = "uNoura", standings = listOf(Seat("uNoura", 30000), Seat("uHost", 15000), Seat("uSara", 0))),
            room.gameOver,
        )
        assertEquals("PQRS", room.rematchCode)
        assertEquals("uSara", room.rematchBy)
        assertNotNull(room.table)
        assertEquals(PublicRound.Phase.Finished, room.round!!.phase)
    }

    @Test fun explicitNullsReadAsMissing() {
        val doc = playingDoc().also {
            it["away"] = null
            it["aiPlayers"] = null
            it["peeks"] = null
            it["gameOver"] = null
            it["rematchCode"] = null
            it["rematchBy"] = null
            it["round"] = null
        }
        val room = Decode.room(doc)!!
        assertNull(room.away)
        assertNull(room.aiPlayers)
        assertNull(room.peeks)
        assertNull(room.gameOver)
        assertNull(room.rematchCode)
        assertNull(room.rematchBy)
        assertNull(room.round)
        assertNotNull(room.table)
    }

    @Test fun emptyPeeksAfterADeal() {
        assertEquals(emptyMap<String, Int>(), Decode.room(playingDoc().also { it["peeks"] = emptyMap<String, Any>() })!!.peeks)
    }

    @Test fun nothingReadsAsNoRoom() {
        assertNull(Decode.room(null))
        assertNull(Decode.room(emptyMap()))
    }

    @Test fun missingRequiredFieldReadsAsNoRoom() {
        for (key in listOf("code", "hostId", "status", "players")) {
            assertNull(key, Decode.room(playingDoc().also { it.remove(key) }))
        }
        for (key in listOf("seats", "dealerIndex", "roundNumber")) {
            assertNull("table.$key", Decode.room(playingDoc().also { it["table"] = (it["table"] as Map<*, *>) - key }))
        }
        val required = listOf("roundNumber", "phase", "dealerId", "bets", "withdrawn", "offers", "deals", "revealedHands")
        for (key in required) {
            assertNull("round.$key", Decode.room(playingDoc().also { it["round"] = (it["round"] as Map<*, *>) - key }))
        }
        for (key in listOf("outcome", "revealed", "deltas")) {
            val doc = playingDoc().also {
                it["round"] = finishedRound().also { r -> r["result"] = (r["result"] as Map<*, *>) - key }
            }
            assertNull("result.$key", Decode.room(doc))
        }
        for (key in listOf("winnerId", "standings")) {
            assertNull("gameOver.$key", Decode.room(finishedDoc().also { it["gameOver"] = (it["gameOver"] as Map<*, *>) - key }))
        }
        val nameless = playingDoc().also { it["players"] = listOf(mapOf("uid" to "uHost")) }
        assertNull("player.name", Decode.room(nameless))
    }

    @Test fun badEnumReadsAsNoRoom() {
        assertNull(Decode.room(playingDoc().also { it["status"] = "paused" }))
        assertNull(Decode.room(playingDoc().also { it["round"] = (it["round"] as Map<*, *>) + ("phase" to "dealing") }))
        val badOutcome = playingDoc().also {
            it["round"] = finishedRound().also { r -> r["result"] = (r["result"] as Map<*, *>) + ("outcome" to "draw") }
        }
        assertNull(Decode.room(badOutcome))
    }

    @Test fun wrongTypeReadsAsNoRoom() {
        assertNull(Decode.room(playingDoc().also { it["code"] = 1234L }))
        assertNull(Decode.room(playingDoc().also { it["players"] = "uHost" }))
        assertNull(Decode.room(playingDoc().also { it["peeks"] = mapOf("uHost" to "two") }))
        val badCard = playingDoc().also {
            it["round"] = finishedRound().also { r ->
                r["revealedHands"] = mapOf("uHost" to listOf(mapOf("rank" to "A", "suit" to "S")))
            }
        }
        assertNull(Decode.room(badCard))
    }

    // Hands

    @Test fun hand() {
        val doc = mapOf(
            "cards" to listOf(card(14, "S"), mapOf("rank" to 13.0, "suit" to "H"), card(13, "D"), card(11, "C")),
            "round" to 4L,
        )
        assertEquals(listOf(Card(14, "S"), Card(13, "H"), Card(13, "D"), Card(11, "C")) to 4, Decode.hand(doc))
    }

    @Test fun handWithoutDocumentOrRound() {
        assertEquals(emptyList<Card>() to null, Decode.hand(null))
        assertEquals(emptyList<Card>() to null, Decode.hand(emptyMap()))
        assertEquals(listOf(Card(12, "D")) to null, Decode.hand(mapOf("cards" to listOf(card(12, "D")))))
        assertEquals(emptyList<Card>() to 2, Decode.hand(mapOf("round" to 2.0)))
    }

    @Test fun handSkipsUnreadableCards() {
        val doc = mapOf(
            "cards" to listOf(card(14, "S"), mapOf("rank" to "K", "suit" to "H"), mapOf("rank" to 12L), "AS", null),
            "round" to 0L,
        )
        assertEquals(listOf(Card(14, "S")) to 0, Decode.hand(doc))
    }

    // Documents

    private fun card(rank: Long, suit: String) = mapOf("rank" to rank, "suit" to suit)

    private fun lobbyDoc(): MutableMap<String, Any?> = mutableMapOf(
        "code" to "KXRT",
        "hostId" to "uHost",
        "status" to "lobby",
        "players" to listOf(
            mapOf("uid" to "uHost", "name" to "Azzam"),
            mapOf("uid" to "uSara", "name" to "Sara"),
            mapOf("uid" to "ai_k3j9x2m1q", "name" to "Bot"),
        ),
        "playerIds" to listOf("uHost", "uSara", "ai_k3j9x2m1q"),
        "table" to null,
        "createdAt" to Timestamp(1_760_000_000L, 0),
        "updatedAt" to 1_760_000_000_000L,
        "aiPlayers" to listOf("ai_k3j9x2m1q"),
        "botStyles" to mapOf("ai_k3j9x2m1q" to "balanced"),
    )

    private fun playingDoc(): MutableMap<String, Any?> = mutableMapOf(
        "code" to "KXRT",
        "hostId" to "uHost",
        "status" to "playing",
        "players" to listOf(
            mapOf("uid" to "uHost", "name" to "Azzam"),
            mapOf("uid" to "uSara", "name" to "Sara"),
            mapOf("uid" to "uNoura", "name" to "Noura"),
            mapOf("uid" to "uFaisal", "name" to "Faisal"),
            mapOf("uid" to "ai_k3j9x2m1q", "name" to "Bot"),
        ),
        "playerIds" to listOf("uHost", "uSara", "uNoura", "uFaisal", "ai_k3j9x2m1q"),
        "table" to mapOf(
            "seats" to listOf(
                mapOf("id" to "uHost", "points" to 10_000L, "changedAt" to 0L),
                mapOf("id" to "uSara", "points" to 9_500.0, "changedAt" to 2L),
                mapOf("id" to "uNoura", "points" to 12_000L),
                mapOf("id" to "uFaisal", "points" to 8_500L, "changedAt" to 1L),
                mapOf("id" to "ai_k3j9x2m1q", "points" to 10_000L),
            ),
            "dealerIndex" to 1L,
            "roundNumber" to 3.0,
        ),
        "round" to mapOf(
            "roundNumber" to 3L,
            "phase" to "betting",
            "dealerId" to "uSara",
            "turnId" to "uFaisal",
            "bets" to mapOf("uNoura" to 1_000L),
            "withdrawn" to emptyList<String>(),
            "bossId" to null,
            "offers" to emptyMap<String, Any>(),
            "deals" to emptyMap<String, Any>(),
            "deadline" to null,
            "turnDeadline" to 1.760000045E12,
            "nextRoundAt" to null,
            "revealEndsAt" to null,
            "result" to null,
            "revealedHands" to emptyMap<String, Any>(),
        ),
        "createdAt" to Timestamp(1_760_000_000L, 0),
        "updatedAt" to 1_760_000_040_000L,
        "wakeAt" to 1_760_000_045_000L,
        "away" to listOf("uFaisal"),
        "aiPlayers" to listOf("ai_k3j9x2m1q"),
        "botStyles" to mapOf("ai_k3j9x2m1q" to "bold", "uFaisal" to "careful"),
        "peeks" to mapOf("uHost" to 2L, "uNoura" to 4L),
    )

    private fun finishedRound(): MutableMap<String, Any?> = mutableMapOf(
        "roundNumber" to 4L,
        "phase" to "finished",
        "dealerId" to "uNoura",
        "turnId" to null,
        "bets" to mapOf("uFaisal" to 2_000L, "uHost" to 1_500L, "uSara" to 1_500.0),
        "withdrawn" to listOf("ai_k3j9x2m1q", "uNoura"),
        "bossId" to "uFaisal",
        "offers" to mapOf("uHost" to 500L),
        "deals" to mapOf("uSara" to 1_000L),
        "deadline" to null,
        "turnDeadline" to null,
        "nextRoundAt" to 1_760_000_128_300L,
        "revealEndsAt" to 1.7600001203E12,
        "result" to mapOf(
            "outcome" to "showdown",
            "winnerId" to "uHost",
            "revealed" to listOf("uHost", "uFaisal"),
            "deltas" to mapOf("uHost" to 2_000L, "uFaisal" to -3_000L, "uSara" to 1_000L),
        ),
        "revealedHands" to mapOf(
            "uHost" to listOf(card(14, "S"), card(14, "H"), card(9, "D"), card(9, "C")),
            "uFaisal" to listOf(mapOf("rank" to 13.0, "suit" to "S"), card(13, "H"), card(12, "D"), card(11, "C")),
        ),
    )

    private fun finishedDoc(): MutableMap<String, Any?> = playingDoc().also {
        it["status"] = "finished"
        it["round"] = finishedRound()
        it["wakeAt"] = null
        it["gameOver"] = mapOf(
            "winnerId" to "uNoura",
            "standings" to listOf(
                mapOf("id" to "uNoura", "points" to 30_000L, "changedAt" to 9L),
                mapOf("id" to "uHost", "points" to 15_000L, "changedAt" to 8L),
                mapOf("id" to "uSara", "points" to 0L, "changedAt" to 7L),
            ),
        )
        it["rematchCode"] = "PQRS"
        it["rematchBy"] = "uSara"
    }
}
