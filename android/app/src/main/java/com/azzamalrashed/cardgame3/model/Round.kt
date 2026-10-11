package com.azzamalrashed.cardgame3.model

/** A playing card; rank 11-14 = J, Q, K, A. Suits: S, H, D, C. */
data class Card(val rank: Int, val suit: String)

/** Everything players may see about the current round. Mirrors `PublicRound` in firebase/functions/src/store.ts. */
data class PublicRound(
    val roundNumber: Int,
    val phase: Phase,
    val dealerId: String,
    val turnId: String?,
    val bets: Map<String, Int>,
    val withdrawn: List<String>,
    val bossId: String?,
    val offers: Map<String, Int>,
    val deals: Map<String, Int>,
    /** Epoch milliseconds when the deals timer runs out. */
    val deadline: Long?,
    /** Epoch milliseconds when the person whose betting turn it is runs out of time (then a bot plays). */
    val turnDeadline: Long?,
    /** Epoch milliseconds when the server deals the next round. */
    val nextRoundAt: Long?,
    /** Epoch milliseconds when every phone has finished staging the showdown; nobody can deal before. */
    val revealEndsAt: Long?,
    val result: RoundResult?,
    val revealedHands: Map<String, List<Card>>,
) {
    enum class Phase(val wire: String) { Betting("betting"), Deals("deals"), Finished("finished") }

    val highestBet: Int get() = bets.values.maxOrNull() ?: 0

    /** What is left of the boss's bet for new deals. */
    val dealRoom: Int
        get() {
            val boss = bossId ?: return 0
            val bossBet = bets[boss] ?: return 0
            return bossBet - deals.values.sum()
        }
}

data class RoundResult(
    val outcome: Outcome,
    val winnerId: String?,
    val revealed: List<String>,
    val deltas: Map<String, Int>,
) {
    enum class Outcome(val wire: String) {
        Redeal("redeal"), Uncontested("uncontested"), AllDeals("allDeals"), Showdown("showdown")
    }
}

data class GameOver(val winnerId: String, val standings: List<Seat>)
