package com.azzamalrashed.cardgame3.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

/** The deal and reveal timelines, against ios/CardGame3/Models/Choreography.swift and schedule.ts. */
class ChoreographyTest {
    private val eps = 1e-9

    // The deal

    @Test fun dealOrderStartsAfterTheDealerAndWraps() {
        val seats = listOf("a", "b", "c", "d", "e")
        assertEquals(listOf("b", "c", "d", "e", "a"), DealTimeline.of(seats, "a").order)
        assertEquals(listOf("d", "e", "a", "b", "c"), DealTimeline.of(seats, "c").order)
        assertEquals(listOf("a", "b", "c", "d", "e"), DealTimeline.of(seats, "e").order)
        // A dealer who isn't seated: from the first seat, as iOS's `firstIndex ?? -1`.
        assertEquals(listOf("a", "b", "c", "d", "e"), DealTimeline.of(seats, "zz").order)
    }

    @Test fun dealIntervalShrinksOnBigTables() {
        for (n in 1..13) {
            val seats = (1..n).map { "p$it" }
            assertEquals("$n seats", min(0.1, 1.4 / (n * 4)), DealTimeline.of(seats, "p1").interval, 0.0)
        }
        assertEquals(0.1, DealTimeline.of(listOf("a", "b", "c"), "a").interval, 0.0)
        assertEquals(0.0875, DealTimeline.of(listOf("a", "b", "c", "d"), "a").interval, eps)
        assertEquals(0.07, DealTimeline.of(listOf("a", "b", "c", "d", "e"), "a").interval, eps)
        assertEquals(1.4 / 52, DealTimeline.of((1..13).map { "p$it" }, "p1").interval, eps)
    }

    @Test fun dealTotal() {
        // shuffle + (cards - 1) × interval + flight
        assertEquals(0.6 + 15 * 0.0875 + 0.35, fourSeats.total, eps)
        assertEquals(2.2625, fourSeats.total, eps)
        assertEquals(2.28, DealTimeline.of(listOf("a", "b", "c", "d", "e"), "a").total, eps)
        assertEquals(0.95 + 51 * 1.4 / 52, DealTimeline.of((1..13).map { "p$it" }, "p1").total, eps)
    }

    @Test fun departure() {
        // Order c, d, a, b; 0.0875 s apart after the 0.6 s shuffle, four times around.
        assertEquals(0.6, fourSeats.departure("c", 0)!!, eps)
        assertEquals(0.6875, fourSeats.departure("d", 0)!!, eps)
        assertEquals(0.8625, fourSeats.departure("b", 0)!!, eps)
        assertEquals(0.95, fourSeats.departure("c", 1)!!, eps)
        assertEquals(1.475, fourSeats.departure("a", 2)!!, eps)
        assertEquals(1.9125, fourSeats.departure("b", 3)!!, eps)
        assertEquals(fourSeats.total, fourSeats.departure("b", 3)!! + DealTimeline.FLIGHT, eps)
        assertNull(fourSeats.departure("zz", 0))
    }

    @Test fun flightsAtSeveralTimes() {
        assertEquals(emptyList<DealTimeline.Flight>(), fourSeats.flights(0.0))
        assertEquals(emptyList<DealTimeline.Flight>(), fourSeats.flights(0.59))
        assertEquals(listOf(DealTimeline.Flight("c", 0, 0.0)), fourSeats.flights(0.6))

        val early = fourSeats.flights(0.7)
        assertEquals(listOf("c#0", "d#0"), early.map { it.id })
        assertEquals(0.1 / 0.35, early[0].progress, eps)
        assertEquals(0.0125 / 0.35, early[1].progress, eps)

        val middle = fourSeats.flights(1.0)
        // c's first card has landed; its second just left. Listed seat by seat, as on iOS.
        assertEquals(listOf("c#1", "d#0", "a#0", "b#0"), middle.map { it.id })
        assertEquals(listOf(0.05, 0.3125, 0.225, 0.1375).map { it / 0.35 }, middle.map { it.progress }, eps)

        assertEquals(listOf("c#3", "d#3", "a#3", "b#3"), fourSeats.flights(1.95).map { it.id })
        assertEquals(listOf("b#3"), fourSeats.flights(fourSeats.total - 0.01).map { it.id })
        assertEquals(emptyList<DealTimeline.Flight>(), fourSeats.flights(fourSeats.total + 0.01))
    }

    @Test fun everyCardFliesOnceForwards() {
        for (n in listOf(4, 7, 13)) {
            val timeline = DealTimeline.of((1..n).map { "p$it" }, "p2")
            val last = mutableMapOf<String, Double>()
            var t = 0.0
            while (t < timeline.total + 0.1) {
                for (f in timeline.flights(t)) {
                    assertTrue(f.progress >= 0 && f.progress < 1)
                    assertTrue("${f.id} goes forwards", f.progress > (last[f.id] ?: -1.0))
                    last[f.id] = f.progress
                }
                t += 0.001
            }
            assertEquals(n * 4, last.size)
        }
    }

    @Test fun dealClockLanded() {
        val start = 1_760_000_000_000L
        val clock = DealClock(fourSeats, start, 3)
        // c's first card leaves at 0.6 s and lands 0.35 s later.
        assertFalse(clock.landed("c", 0, start + 949))
        assertTrue(clock.landed("c", 0, start + 951))
        // b's last card lands as the deal ends, at 2.2625 s.
        assertFalse(clock.landed("b", 3, start + 2262))
        assertTrue(clock.landed("b", 3, start + 2263))
        // Not dealt in: nothing to wait for.
        assertTrue(clock.landed("zz", 0, start))
    }

    @Test fun dealClockLandedCount() {
        val start = 1_760_000_000_000L
        val clock = DealClock(fourSeats, start, 3)
        for (uid in listOf("a", "b", "c", "d")) assertEquals(0, clock.landedCount(uid, start))
        assertEquals(1, clock.landedCount("c", start + 1000))
        assertEquals(0, clock.landedCount("b", start + 1000))
        assertEquals(2, clock.landedCount("c", start + 1500))
        assertEquals(2, clock.landedCount("d", start + 1500))
        assertEquals(2, clock.landedCount("a", start + 1500))
        assertEquals(1, clock.landedCount("b", start + 1500))
        for (uid in listOf("a", "b", "c", "d")) assertEquals(4, clock.landedCount(uid, start + 3000))
        assertEquals(4, clock.landedCount("zz", start))
    }

    @Test fun dealClockEnds() {
        val start = 1_760_000_000_000L
        val clock = DealClock(DealTimeline.of(listOf("a", "b", "c", "d", "e"), "a"), start, 3)
        assertEquals(start + 2280, clock.end)
        assertEquals(start + 2280 + 3400, clock.peeksEnd)
    }

    @Test fun msRounds() {
        assertEquals(0L, ms(0.0))
        assertEquals(350L, ms(0.35))
        assertEquals(3400L, ms(DealTimeline.BOT_PEEKS_WINDOW))
        assertEquals(5300L, ms(5.3))
        assertEquals(1000L, ms(1.0004))
        assertEquals(1001L, ms(1.0006))
    }

    // Bots' tells, against the Swift implementation

    /**
     * `DealTimeline.stableHash` from Choreography.swift, run with `swift` on these strings. Includes the empty
     * string (the FNV offset basis) and non-ASCII names, which must hash their UTF-8 bytes.
     */
    @Test fun stableHashMatchesSwift() {
        assertEquals(14695981039346656037uL, DealTimeline.stableHash(""))
        assertEquals(12638187200555641996uL, DealTimeline.stableHash("a"))
        assertEquals(13083307274007423292uL, DealTimeline.stableHash("ai_k3j9x2m1q:0"))
        assertEquals(8646085615524315761uL, DealTimeline.stableHash("kq3ZbV9xTnPc1mWsE7hYr2LdA8u1:7"))
        assertEquals(12564272660165677116uL, DealTimeline.stableHash("عزام:3"))
        assertEquals(16593530840701217915uL, DealTimeline.stableHash("🃏:12"))
    }

    /** `DealTimeline.botPeeks` from Choreography.swift, run with `swift`: hash % 1600, then peeks at [peekTimes]. */
    private val swiftPeeks = listOf(
        PeekRow("ai_k3j9x2m1q", 0, 892, "0,0,0,1,3,4,4,4,4,4"),
        PeekRow("ai_k3j9x2m1q", 1, 303, "0,0,1,3,4,4,4,4,4,4"),
        PeekRow("ai_k3j9x2m1q", 7, 1481, "0,0,0,0,1,2,4,4,4,4"),
        PeekRow("ai_k3j9x2m1q", 42, 1318, "0,0,0,0,1,3,4,4,4,4"),
        PeekRow("ai_2f7c0", 0, 44, "0,1,2,4,4,4,4,4,4,4"),
        PeekRow("ai_2f7c0", 1, 1055, "0,0,0,1,2,3,4,4,4,4"),
        PeekRow("ai_2f7c0", 7, 633, "0,0,0,2,3,4,4,4,4,4"),
        PeekRow("ai_2f7c0", 42, 1494, "0,0,0,0,1,2,4,4,4,4"),
        PeekRow("kq3ZbV9xTnPc1mWsE7hYr2LdA8u1", 0, 1572, "0,0,0,0,1,2,3,4,4,4"),
        PeekRow("kq3ZbV9xTnPc1mWsE7hYr2LdA8u1", 1, 983, "0,0,0,1,2,4,4,4,4,4"),
        PeekRow("kq3ZbV9xTnPc1mWsE7hYr2LdA8u1", 7, 561, "0,0,1,2,3,4,4,4,4,4"),
        PeekRow("kq3ZbV9xTnPc1mWsE7hYr2LdA8u1", 42, 1310, "0,0,0,0,1,3,4,4,4,4"),
        PeekRow("عزام", 0, 149, "0,0,2,3,4,4,4,4,4,4"),
        PeekRow("عزام", 1, 738, "0,0,0,2,3,4,4,4,4,4"),
        PeekRow("عزام", 7, 1072, "0,0,0,1,2,3,4,4,4,4"),
        PeekRow("عزام", 42, 1025, "0,0,0,1,2,4,4,4,4,4"),
    )
    private val peekTimes = listOf(0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, Double.POSITIVE_INFINITY)

    private data class PeekRow(val uid: String, val round: Int, val mod: Int, val peeks: String)

    @Test fun botPeeksMatchSwift() {
        for (row in swiftPeeks) {
            assertEquals("$row", row.mod.toULong(), DealTimeline.stableHash("${row.uid}:${row.round}") % 1600u)
            val peeks = peekTimes.map { DealTimeline.botPeeks(row.uid, row.round, it) }
            assertEquals("$row", row.peeks, peeks.joinToString(","))
        }
    }

    @Test fun botPeeksWindow() {
        assertEquals(3.4, DealTimeline.BOT_PEEKS_WINDOW, 0.0)
        for (row in swiftPeeks) {
            assertEquals(0, DealTimeline.botPeeks(row.uid, row.round, -1.0))
            assertEquals(0, DealTimeline.botPeeks(row.uid, row.round, 0.39))
            // Everyone has looked at all four by the end of the window, and the count only goes up.
            assertEquals(4, DealTimeline.botPeeks(row.uid, row.round, DealTimeline.BOT_PEEKS_WINDOW))
            var previous = 0
            var t = 0.0
            while (t < 4) {
                val peeks = DealTimeline.botPeeks(row.uid, row.round, t)
                assertTrue(peeks >= previous)
                previous = peeks
                t += 0.01
            }
        }
    }

    // The reveal

    @Test fun revealChallengersInSeatOrderBossLast() {
        val timeline = RevealTimeline.of(showdown(boss = "c", revealed = listOf("e", "c", "a")), seatOrder)!!
        assertEquals(listOf("a", "e"), timeline.challengers)
        assertEquals("c", timeline.boss)
        assertEquals(listOf("a", "e", "c"), timeline.order)
        assertEquals(1.0, timeline.per, 0.0)
    }

    @Test fun revealOnlyForShowdowns() {
        for (outcome in RoundResult.Outcome.entries - RoundResult.Outcome.Showdown) {
            assertNull(outcome.name, RevealTimeline.of(showdown(boss = "c", revealed = listOf("c", "a"), outcome = outcome), seatOrder))
        }
        assertNull(RevealTimeline.of(showdown(boss = null, revealed = listOf("a", "b")), seatOrder))
        assertNull(RevealTimeline.of(showdown(boss = "c", revealed = listOf("c", "a")).copy(result = null), seatOrder))
    }

    @Test fun revealTimingTwoChallengers() {
        val t = RevealTimeline.of(showdown(boss = "c", revealed = listOf("e", "c", "a")), seatOrder)!!
        assertEquals(2.8, t.drumrollAt, eps)
        assertEquals(3.8, t.bossAt, eps)
        assertEquals(5.8, t.verdictAt, eps)
        assertEquals(7.3, t.total, eps)

        assertEquals(listOf(0.8, 0.92, 1.04, 1.16), (0..3).map { t.flipTime("a", it) }, eps)
        assertEquals(listOf(1.8, 1.92, 2.04, 2.16), (0..3).map { t.flipTime("e", it) }, eps)
        assertEquals(listOf(3.8, 4.15, 4.5, 5.3), (0..3).map { t.flipTime("c", it) }, eps)
        assertEquals(1.41, t.shownTime("a"), eps)
        assertEquals(2.41, t.shownTime("e"), eps)
        assertEquals(5.55, t.shownTime("c"), eps)
        // Someone not revealing: as the first challenger (iOS's `firstIndex ?? 0`).
        assertEquals(t.flipTime("a", 2), t.flipTime("zz", 2), 0.0)
    }

    @Test fun revealTimingManyChallengers() {
        val seats = (0..10).map { "p$it" }
        val t = RevealTimeline.of(showdown(boss = "p3", revealed = seats.reversed()), seats)!!
        assertEquals(seats - "p3", t.challengers)
        assertEquals(0.5, t.per, eps)
        assertEquals(5.8, t.drumrollAt, eps)
        assertEquals(10.3, t.total, eps)
        assertEquals(2.3, t.flipTime("p4", 0), eps)
        assertEquals(5.48, t.flipTime("p10", 3), eps)
        assertEquals(5.73, t.shownTime("p10"), eps)
        assertEquals(6.8, t.flipTime("p3", 0), eps)
    }

    @Test fun revealOnlyTheBoss() {
        val t = RevealTimeline.of(showdown(boss = "c", revealed = listOf("c")), seatOrder)!!
        assertEquals(emptyList<String>(), t.challengers)
        assertEquals(0.0, t.per, 0.0)
        assertEquals(5.3, t.total, eps)
    }

    /** `revealMs` in schedule.ts: the server waits exactly as long as phones stage the showdown. */
    @Test fun revealTotalMatchesServerRevealMs() {
        val seats = (0..12).map { "p$it" }
        val expected = listOf(5300, 6300, 7300, 8300, 9300, 10300, 10300, 10300, 10300, 10300, 10300, 10300, 10300)
        for (revealers in 1..13) {
            val revealed = seats.take(revealers).reversed()
            val serverMs = 5300 + min(1000 * (revealed.size - 1), 5000)
            assertEquals(expected[revealers - 1], serverMs)
            val t = RevealTimeline.of(showdown(boss = seats[revealers / 2], revealed = revealed), seats)!!
            assertEquals("$revealers revealing", serverMs.toLong(), ms(t.total))
            val clock = RevealClock(t, 1_760_000_000_000L, 5)
            assertEquals(1_760_000_000_000L + serverMs, clock.end)
            // Every challenger's hand is showing before the drumroll, and the boss's before the verdict.
            for (uid in t.challengers) assertTrue(t.shownTime(uid) <= t.drumrollAt)
            assertTrue(t.shownTime(t.boss) < t.verdictAt)
        }
    }

    @Test fun revealClock() {
        val start = 1_760_000_000_000L
        val clock = RevealClock(RevealTimeline.of(showdown(boss = "c", revealed = listOf("e", "c", "a")), seatOrder)!!, start, 5)
        assertEquals(0.0, clock.elapsed(start), 0.0)
        assertEquals(1.5, clock.elapsed(start + 1500), eps)
        assertEquals(start + 5800, clock.verdict)
        assertEquals(start + 7300, clock.end)
    }

    // Helpers

    /** Four seats, b deals: cards go to c, d, a, b. */
    private val fourSeats = DealTimeline.of(listOf("a", "b", "c", "d"), "b")
    private val seatOrder = listOf("a", "b", "c", "d", "e")

    private fun showdown(
        boss: String?,
        revealed: List<String>,
        outcome: RoundResult.Outcome = RoundResult.Outcome.Showdown,
    ) = PublicRound(
        roundNumber = 5,
        phase = PublicRound.Phase.Finished,
        dealerId = "a",
        turnId = null,
        bets = revealed.associateWith { 1000 },
        withdrawn = emptyList(),
        bossId = boss,
        offers = emptyMap(),
        deals = emptyMap(),
        deadline = null,
        turnDeadline = null,
        nextRoundAt = 1_760_000_020_000L,
        revealEndsAt = 1_760_000_012_000L,
        result = RoundResult(outcome, revealed.firstOrNull(), revealed, emptyMap()),
        revealedHands = emptyMap(),
    )

    private fun assertEquals(expected: List<Double>, actual: List<Double>, delta: Double) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertEquals("$expected vs $actual", e, a, delta) }
    }
}
