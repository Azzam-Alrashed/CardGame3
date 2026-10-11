package com.azzamalrashed.cardgame3.model

/** Mirrors the public room document written by Cloud Functions (`rooms/{code}`). */
data class Room(
    val code: String,
    val hostId: String,
    val status: Status,
    /** Everyone who joined, in seat order (knocked-out players stay listed). */
    val players: List<Player>,
    val table: Table?,
    val round: PublicRound?,
    val gameOver: GameOver?,
    /** Players who stepped away; a bot plays for them. */
    val away: List<String>?,
    /** AI players added by the host; always played by a bot. */
    val aiPlayers: List<String>?,
    /** How many of their cards each player has looked at this round (a tell everyone can see). */
    val peeks: Map<String, Int>?,
    /** After the game: the new lobby someone opened to play again, and who opened it. */
    val rematchCode: String?,
    val rematchBy: String?,
) {
    enum class Status(val wire: String) { Lobby("lobby"), Playing("playing"), Finished("finished") }

    data class Player(val uid: String, val name: String)

    /** A player's name, or "Player 3" if it is hidden on this phone (see [HiddenNames]). */
    fun name(uid: String): String {
        if (HiddenNames.contains(uid)) return "Player ${seat(uid) + 1}"
        return players.firstOrNull { it.uid == uid }?.name ?: "?"
    }

    /** Seat index in the joined order, used for each player's blob color. */
    fun seat(uid: String): Int = players.indexOfFirst { it.uid == uid }.coerceAtLeast(0)

    /**
     * Points including the result of a just-finished round (the table updates at the next deal).
     * `settled = false` leaves that result out while its showdown is still being revealed.
     */
    fun points(uid: String, settled: Boolean = true): Int {
        val base = table?.seats?.firstOrNull { it.id == uid }?.points ?: 0
        return base + if (settled) round?.result?.deltas?.get(uid) ?: 0 else 0
    }

    fun isAI(uid: String): Boolean = aiPlayers?.contains(uid) ?: false

    fun isAway(uid: String): Boolean = away?.contains(uid) ?: false

    fun isStillIn(uid: String): Boolean = table?.seats?.any { it.id == uid } ?: false
}

data class Seat(val id: String, val points: Int)

/** Players still in the game, plus whose deal it is. */
data class Table(val seats: List<Seat>, val dealerIndex: Int, val roundNumber: Int)
