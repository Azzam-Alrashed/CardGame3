package com.azzamalrashed.cardgame3.model

import kotlin.math.max
import kotlin.math.min

// When each part of the deal happens. Every view that draws a piece of it (the flying cards, the small cards in
// front of each player, your hand) reads the same timeline, so they move together. Timeline times are seconds
// since this phone saw the round dealt (see `Backend.dealtAt`); clock dates are epoch milliseconds.
// Ported from ios/CardGame3/Models/Choreography.swift: keep the numbers in step.

/** The deal: the dealer shuffles, then gives one card at a time to each player, four times around. */
data class DealTimeline(
    /** Players in the order they get cards: starting after the dealer, going right. */
    val order: List<String>,
    /** Time between two cards leaving the dealer: shorter on big tables, so a deal takes about 2 s. */
    val interval: Double,
) {
    val total: Double get() = SHUFFLE + (order.size * HAND_SIZE - 1) * interval + FLIGHT

    /** When card `index` (0–3) of `uid`'s hand leaves the dealer, or null if they aren't dealt in. */
    fun departure(uid: String, card: Int): Double? {
        val seat = order.indexOf(uid)
        if (seat < 0) return null
        return SHUFFLE + (card * order.size + seat) * interval
    }

    /** Cards in the air at `t`: who each is for, which of their cards, and how far along (0–1). */
    fun flights(t: Double): List<Flight> {
        if (t < SHUFFLE) return emptyList()
        val flights = mutableListOf<Flight>()
        for ((seat, uid) in order.withIndex()) {
            for (card in 0 until HAND_SIZE) {
                val leaves = SHUFFLE + (card * order.size + seat) * interval
                val progress = (t - leaves) / FLIGHT
                if (progress >= 0 && progress < 1) flights += Flight(uid, card, progress)
            }
        }
        return flights
    }

    data class Flight(val uid: String, val card: Int, val progress: Double) {
        val id: String get() = "$uid#$card"
    }

    companion object {
        const val SHUFFLE = 0.6
        const val FLIGHT = 0.35
        const val HAND_SIZE = 4

        fun of(seats: List<String>, dealerId: String): DealTimeline {
            val first = seats.indexOf(dealerId) + 1
            val order = seats.indices.map { seats[(first + it) % seats.size] }
            return DealTimeline(order, min(0.1, 1.4 / max(1, seats.size * HAND_SIZE)))
        }

        // Bots' tells

        /**
         * AI and away players look at their cards too: soon after the deal, one card at a time.
         * Seeded by player and round, so every phone (iPhones too) shows the same thing. `t` is infinite when
         * this phone didn't see the deal: they've long since looked.
         */
        fun botPeeks(uid: String, round: Int, sinceDealEnd: Double): Int {
            val start = 0.4 + (stableHash("$uid:$round") % 1600u).toDouble() / 1000
            if (sinceDealEnd < start) return 0
            if (sinceDealEnd >= start + BOT_PEEKS_WINDOW) return HAND_SIZE
            return min(HAND_SIZE, 1 + ((sinceDealEnd - start) / 0.35).toInt())
        }

        /** Every bot has looked at all its cards this long after the deal ends. */
        const val BOT_PEEKS_WINDOW = 0.4 + 1.6 + 0.35 * 4

        /** FNV-1a over UTF-8: the same on every phone and every launch, and the same as the iOS app's. */
        fun stableHash(s: String): ULong =
            s.encodeToByteArray().fold(14_695_981_039_346_656_037uL) { h, b -> (h xor b.toUByte().toULong()) * 1_099_511_628_211uL }
    }
}

/** A deal this phone is showing: its timeline and when it started (epoch ms). */
data class DealClock(val timeline: DealTimeline, val start: Long, val round: Int) {
    val end: Long get() = start + ms(timeline.total)

    /** When the last bot has finished looking at its cards. */
    val peeksEnd: Long get() = end + ms(DealTimeline.BOT_PEEKS_WINDOW)

    /** Whether card `index` of `uid`'s hand has reached them by `at` (epoch ms). */
    fun landed(uid: String, card: Int, at: Long): Boolean {
        val leaves = timeline.departure(uid, card) ?: return true
        return (at - start) / 1000.0 >= leaves + DealTimeline.FLIGHT
    }

    fun landedCount(uid: String, at: Long): Int = (0 until DealTimeline.HAND_SIZE).count { landed(uid, it, at) }
}

// The reveal

/**
 * A showdown, staged: the challengers turn their cards over one at a time (in seat order), a drumroll, then the
 * boss card by card, then the verdict. The server waits for it before anyone can deal the next round: `revealMs`
 * in firebase/functions/src/schedule.ts mirrors `total`; keep them in step.
 */
data class RevealTimeline(
    /** Revealing players other than the boss, in seat order. (The server's `revealed` has no useful order.) */
    val challengers: List<String>,
    val boss: String,
    /** Time each challenger takes: a second, less when many reveal. */
    val per: Double,
) {
    /** Everyone revealing, in the order they turn their cards over. */
    val order: List<String> get() = challengers + boss
    val drumrollAt: Double get() = INTRO + challengers.size * per
    val bossAt: Double get() = drumrollAt + DRUMROLL
    val verdictAt: Double get() = bossAt + BOSS_TURN
    val total: Double get() = verdictAt + VERDICT

    /** When card `index` of `uid`'s hand turns over. */
    fun flipTime(uid: String, card: Int): Double {
        if (uid == boss) return bossAt + BOSS_FLIPS[card]
        val turn = challengers.indexOf(uid).coerceAtLeast(0)
        return INTRO + (turn + card * 0.12) * per
    }

    /** When a whole hand is showing, and its name appears. */
    fun shownTime(uid: String): Double = flipTime(uid, DealTimeline.HAND_SIZE - 1) + 0.25

    companion object {
        const val INTRO = 0.8
        const val DRUMROLL = 1.0
        const val BOSS_TURN = 2.0
        const val VERDICT = 1.5

        /** When each of the boss's cards turns over, from the start of their turn: the last one slowest. */
        val BOSS_FLIPS = listOf(0.0, 0.35, 0.7, 1.5)

        /** All the challengers together never take longer than this. */
        const val CHALLENGERS_AT_MOST = 5.0

        /** The staging of a finished round, or null if it wasn't a showdown. */
        fun of(round: PublicRound, seatOrder: List<String>): RevealTimeline? {
            val result = round.result ?: return null
            val boss = round.bossId ?: return null
            if (result.outcome != RoundResult.Outcome.Showdown) return null
            val seat = { uid: String -> seatOrder.indexOf(uid).coerceAtLeast(0) }
            val challengers = result.revealed.filter { it != boss }.sortedBy(seat)
            val per = if (challengers.isEmpty()) 0.0 else min(1.0, CHALLENGERS_AT_MOST / challengers.size)
            return RevealTimeline(challengers, boss, per)
        }
    }
}

/** A showdown this phone is staging, and when it started (epoch ms). */
data class RevealClock(val timeline: RevealTimeline, val start: Long, val round: Int) {
    /** Seconds since the showdown started. */
    fun elapsed(at: Long): Double = (at - start) / 1000.0
    val verdict: Long get() = start + ms(timeline.verdictAt)
    val end: Long get() = start + ms(timeline.total)
}

/** Seconds to whole milliseconds. */
fun ms(seconds: Double): Long = Math.round(seconds * 1000)
