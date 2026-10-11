package com.azzamalrashed.cardgame3.model

// Reads the Firestore documents written by Cloud Functions into the models. Firestore hands back plain maps
// (numbers as Long or Double), so this is done by hand. Like iOS's Decodable, a document missing a required
// field reads as no room at all.

object Decode {
    /** The public room document (`rooms/{code}`), or null if it's missing or doesn't read. */
    fun room(data: Map<String, Any?>?): Room? {
        if (data == null) return null
        return try {
            Room(
                code = data.string("code"),
                hostId = data.string("hostId"),
                status = data.string("status").let { s -> Room.Status.entries.first { it.wire == s } },
                players = data.list("players").map { p ->
                    val m = p.asMap()
                    Room.Player(uid = m.string("uid"), name = m.string("name"))
                },
                table = data.mapOrNull("table")?.let(::table),
                round = data.mapOrNull("round")?.let(::round),
                gameOver = data.mapOrNull("gameOver")?.let { g ->
                    GameOver(winnerId = g.string("winnerId"), standings = g.list("standings").map { seat(it.asMap()) })
                },
                away = data.stringsOrNull("away"),
                aiPlayers = data.stringsOrNull("aiPlayers"),
                peeks = data.mapOrNull("peeks")?.let(::intMap),
                rematchCode = data["rematchCode"] as? String,
                rematchBy = data["rematchBy"] as? String,
            )
        } catch (e: RuntimeException) {
            null
        }
    }

    /** One player's hand document (`rooms/{code}/hands/{uid}`): their cards and the round they're for. */
    fun hand(data: Map<String, Any?>?): Pair<List<Card>, Int?> {
        val cards = (data?.get("cards") as? List<*>).orEmpty().mapNotNull { c ->
            val m = c as? Map<*, *> ?: return@mapNotNull null
            val rank = (m["rank"] as? Number)?.toInt() ?: return@mapNotNull null
            val suit = m["suit"] as? String ?: return@mapNotNull null
            Card(rank, suit)
        }
        return cards to (data?.get("round") as? Number)?.toInt()
    }

    private fun table(m: Map<String, Any?>) = Table(
        seats = m.list("seats").map { seat(it.asMap()) },
        dealerIndex = m.int("dealerIndex"),
        roundNumber = m.int("roundNumber"),
    )

    private fun seat(m: Map<String, Any?>) = Seat(id = m.string("id"), points = m.int("points"))

    private fun round(m: Map<String, Any?>) = PublicRound(
        roundNumber = m.int("roundNumber"),
        phase = m.string("phase").let { s -> PublicRound.Phase.entries.first { it.wire == s } },
        dealerId = m.string("dealerId"),
        turnId = m["turnId"] as? String,
        bets = intMap(m.map("bets")),
        withdrawn = m.list("withdrawn").map { it as String },
        bossId = m["bossId"] as? String,
        offers = intMap(m.map("offers")),
        deals = intMap(m.map("deals")),
        deadline = m.longOrNull("deadline"),
        turnDeadline = m.longOrNull("turnDeadline"),
        nextRoundAt = m.longOrNull("nextRoundAt"),
        revealEndsAt = m.longOrNull("revealEndsAt"),
        result = m.mapOrNull("result")?.let { r ->
            RoundResult(
                outcome = r.string("outcome").let { s -> RoundResult.Outcome.entries.first { it.wire == s } },
                winnerId = r["winnerId"] as? String,
                revealed = r.list("revealed").map { it as String },
                deltas = intMap(r.map("deltas")),
            )
        },
        revealedHands = m.map("revealedHands").mapValues { (_, cards) ->
            (cards as List<*>).map { c ->
                val card = c.asMap()
                Card(rank = card.int("rank"), suit = card.string("suit"))
            }
        },
    )

    private fun intMap(m: Map<String, Any?>): Map<String, Int> = m.mapValues { (_, v) -> (v as Number).toInt() }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asMap(): Map<String, Any?> = this as Map<String, Any?>

    private fun Map<String, Any?>.string(key: String): String = get(key) as String
    private fun Map<String, Any?>.int(key: String): Int = (get(key) as Number).toInt()
    private fun Map<String, Any?>.longOrNull(key: String): Long? = (get(key) as? Number)?.toLong()
    private fun Map<String, Any?>.list(key: String): List<Any?> = get(key) as List<Any?>
    private fun Map<String, Any?>.map(key: String): Map<String, Any?> = get(key).asMap()
    private fun Map<String, Any?>.mapOrNull(key: String): Map<String, Any?>? = get(key)?.asMap()
    private fun Map<String, Any?>.stringsOrNull(key: String): List<String>? =
        (get(key) as? List<*>)?.map { it as String }
}
