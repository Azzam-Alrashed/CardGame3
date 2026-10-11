package com.azzamalrashed.cardgame3.model

import com.azzamalrashed.cardgame3.model.HandValue.Category
import com.azzamalrashed.cardgame3.model.HandValue.Decider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** HandValue against the server's `evaluateHand` / `compareHands` (firebase/functions/src/engine/hands.ts). */
class HandValueTest {

    // Names

    @Test fun namesFourOfAKind() {
        assertEquals("Four Queens", name("QS QH QD QC"))
        assertEquals("Four Aces", name("AS AH AD AC"))
        assertEquals("Four Kings", name("KS KH KD KC"))
        assertEquals("Four Jacks", name("JS JH JD JC"))
        assertEquals("Four 10s", name("10S 10H 10D 10C"))
        assertEquals("Four Sixes", name("6S 6H 6D 6C"))
        assertEquals("Four 2s", name("2S 2H 2D 2C"))
    }

    @Test fun namesThreeOfAKind() {
        assertEquals("Three 10s", name("10S 10H 10D 2C"))
        assertEquals("Three Aces", name("AS AH AD KS"))
        assertEquals("Three Sixes", name("6S AC 6H 6D"))
        assertEquals("Three 9s", name("9S 9H 9D AC"))
    }

    @Test fun namesTwoPairs() {
        assertEquals("Two pairs · K & 9", name("KS KH 9D 9C"))
        assertEquals("Two pairs · K & 9", name("9D KS 9C KH"))
        assertEquals("Two pairs · A & 10", name("10S AS 10H AD"))
        assertEquals("Two pairs · Q & J", name("JS QS JH QD"))
        assertEquals("Two pairs · 3 & 2", name("2S 3H 2D 3C"))
        assertEquals("Two pairs · 6 & 5", name("5S 6H 5D 6C"))
    }

    @Test fun namesOnePair() {
        assertEquals("Pair of Sixes", name("6S 6H AD KC"))
        assertEquals("Pair of Kings", name("KS KH 2D 3C"))
        assertEquals("Pair of Aces", name("2D AS 3C AH"))
        assertEquals("Pair of Queens", name("QS QH AD JC"))
        assertEquals("Pair of Jacks", name("JS JH 4D 3C"))
        assertEquals("Pair of 10s", name("10S 10H AD 3C"))
        assertEquals("Pair of 2s", name("2S 2H 5D 3C"))
    }

    @Test fun namesHighCard() {
        assertEquals("Ace high", name("AH KS QD JC"))
        assertEquals("Ace high", name("2C AH 9S 4D"))
        assertEquals("King high", name("KS 9H 4D 2C"))
        assertEquals("Queen high", name("QS 9H 4D 2C"))
        assertEquals("Jack high", name("JS 9H 4D 2C"))
        assertEquals("10 high", name("10S 9H 8D 7C"))
        assertEquals("6 high", name("6S 5H 4D 2C"))
    }

    // Partial hands, while the cards are turning over

    @Test fun oneCard() {
        val v = HandValue.of(hand("KH"))!!
        assertEquals(HandValue(Category.HighCard, emptyList(), listOf(13), 3), v)
        assertEquals("King high", v.name)
        assertEquals("6 high", name("6C"))
    }

    @Test fun twoCards() {
        assertEquals(HandValue(Category.OnePair, listOf(9), emptyList(), 4), HandValue.of(hand("9H 9S")))
        assertEquals("Pair of 9s", name("9H 9S"))
        assertEquals("Pair of Sixes", name("6D 6C"))
        assertEquals(HandValue(Category.HighCard, emptyList(), listOf(14, 3), 3), HandValue.of(hand("3C AH")))
        assertEquals("Ace high", name("3C AH"))
    }

    @Test fun threeCards() {
        assertEquals(HandValue(Category.ThreeOfAKind, listOf(7), emptyList(), 4), HandValue.of(hand("7D 7S 7H")))
        assertEquals("Three 7s", name("7D 7S 7H"))
        assertEquals(HandValue(Category.OnePair, listOf(12), listOf(4), 4), HandValue.of(hand("QS 4D QH")))
        assertEquals("Pair of Queens", name("QS 4D QH"))
        assertEquals(HandValue(Category.HighCard, emptyList(), listOf(9, 5, 2), 3), HandValue.of(hand("5S 9H 2D")))
        assertEquals("9 high", name("5S 9H 2D"))
    }

    @Test fun noCardsOrTooManyIsNull() {
        assertNull(HandValue.of(emptyList()))
        assertNull(HandValue.of(hand("AS KS QS JS 10S")))
        assertNull(HandValue.of(hand("AS AH AD AC KS")))
    }

    @Test fun categoryOrderMatchesServerEnum() {
        // HandCategory in hands.ts: HighCard = 0 … FourOfAKind = 4.
        assertEquals(
            listOf("HighCard", "OnePair", "TwoPairs", "ThreeOfAKind", "FourOfAKind"),
            Category.entries.map { it.name },
        )
        assertEquals(listOf(0, 1, 2, 3, 4), Category.entries.map { it.ordinal })
    }

    @Test fun suitStrengthMatchesServer() {
        // suitStrength in cards.ts: ♠ > ♥ > ♦ > ♣.
        assertEquals(mapOf("S" to 4, "H" to 3, "D" to 2, "C" to 1), HandValue.suitStrength)
        assertEquals(mapOf(4 to "♠\uFE0E", 3 to "♥\uFE0E", 2 to "♦\uFE0E", 1 to "♣\uFE0E"), HandValue.suitSymbol)
    }

    // Against the server

    /** `evaluateHand` output from the real hands.ts (run with node): category, groups, kickers, topSuit. */
    private val serverValues = listOf(
        "2S 2H 2D 2C" to HandValue(Category.FourOfAKind, listOf(2), emptyList(), 4),
        "AS AH AD KS" to HandValue(Category.ThreeOfAKind, listOf(14), listOf(13), 4),
        "AS AH AD 2C" to HandValue(Category.ThreeOfAKind, listOf(14), listOf(2), 4),
        "KS KH QD QC" to HandValue(Category.TwoPairs, listOf(13, 12), emptyList(), 4),
        "3S 3H 2D 2C" to HandValue(Category.TwoPairs, listOf(3, 2), emptyList(), 4),
        "AC AD KH QS" to HandValue(Category.OnePair, listOf(14), listOf(13, 12), 2),
        "2S 2H 5D 3C" to HandValue(Category.OnePair, listOf(2), listOf(5, 3), 2),
        "AH KS QD JC" to HandValue(Category.HighCard, emptyList(), listOf(14, 13, 12, 11), 3),
        "QS QH QD QC" to HandValue(Category.FourOfAKind, listOf(12), emptyList(), 4),
        "KS KH KD 2C" to HandValue(Category.ThreeOfAKind, listOf(13), listOf(2), 4),
        "KS KH 4D 3C" to HandValue(Category.OnePair, listOf(13), listOf(4, 3), 4),
        "QS QH AD JC" to HandValue(Category.OnePair, listOf(12), listOf(14, 11), 2),
        "AS AH 9D 9C" to HandValue(Category.TwoPairs, listOf(14, 9), emptyList(), 4),
        "KS KH 9S 9H" to HandValue(Category.TwoPairs, listOf(13, 9), emptyList(), 4),
        "KD KC 8S 8H" to HandValue(Category.TwoPairs, listOf(13, 8), emptyList(), 2),
        "QS QH QD AC" to HandValue(Category.ThreeOfAKind, listOf(12), listOf(14), 1),
        "10S 10H AD 3C" to HandValue(Category.OnePair, listOf(10), listOf(14, 3), 2),
        "10D 10C KS QH" to HandValue(Category.OnePair, listOf(10), listOf(13, 12), 4),
        "10S 10H AD 5C" to HandValue(Category.OnePair, listOf(10), listOf(14, 5), 2),
        "10D 10C AS 4H" to HandValue(Category.OnePair, listOf(10), listOf(14, 4), 4),
        "AS KH QD 9C" to HandValue(Category.HighCard, emptyList(), listOf(14, 13, 12, 9), 4),
        "AH KS QS 8D" to HandValue(Category.HighCard, emptyList(), listOf(14, 13, 12, 8), 3),
        "AS KH 5D 4C" to HandValue(Category.HighCard, emptyList(), listOf(14, 13, 5, 4), 4),
        "AH KS 5S 3D" to HandValue(Category.HighCard, emptyList(), listOf(14, 13, 5, 3), 3),
        "9S 9H 8D 2C" to HandValue(Category.OnePair, listOf(9), listOf(8, 2), 4),
        "9D 9C 7S 6H" to HandValue(Category.OnePair, listOf(9), listOf(7, 6), 2),
        "AS KC QC JC" to HandValue(Category.HighCard, emptyList(), listOf(14, 13, 12, 11), 4),
        "AH KS QS JS" to HandValue(Category.HighCard, emptyList(), listOf(14, 13, 12, 11), 3),
        "KS KD 9H 9C" to HandValue(Category.TwoPairs, listOf(13, 9), emptyList(), 4),
        "KH KC 9S 9D" to HandValue(Category.TwoPairs, listOf(13, 9), emptyList(), 3),
        "QS QC 7H 5D" to HandValue(Category.OnePair, listOf(12), listOf(7, 5), 4),
        "QH QD 7S 5S" to HandValue(Category.OnePair, listOf(12), listOf(7, 5), 3),
        "8S 8H AC 3D" to HandValue(Category.OnePair, listOf(8), listOf(14, 3), 1),
        "8D 8C AD 3S" to HandValue(Category.OnePair, listOf(8), listOf(14, 3), 2),
        "JD 10C 9C 8C" to HandValue(Category.HighCard, emptyList(), listOf(11, 10, 9, 8), 2),
        "JC 10S 9S 8S" to HandValue(Category.HighCard, emptyList(), listOf(11, 10, 9, 8), 1),
        "10D 10C 5H 5C" to HandValue(Category.TwoPairs, listOf(10, 5), emptyList(), 2),
        "10S 10H 5S 5D" to HandValue(Category.TwoPairs, listOf(10, 5), emptyList(), 4),
    )

    @Test fun valuesMatchServerEvaluateHand() {
        for ((cards, expected) in serverValues) assertEquals(cards, expected, HandValue.of(hand(cards)))
    }

    /**
     * `Math.sign(compareHands(a, b))` from the real hands.ts (run with node), and the step that decides it.
     * Run both ways round, the server said the opposite every time.
     */
    private val serverComparisons = listOf(
        Triple("2S 2H 2D 2C", "AS AH AD KS", 1) to Decider.Category,
        Triple("AS AH AD 2C", "KS KH QD QC", 1) to Decider.Category,
        Triple("3S 3H 2D 2C", "AC AD KH QS", 1) to Decider.Category,
        Triple("2S 2H 5D 3C", "AH KS QD JC", 1) to Decider.Category,
        Triple("QS QH QD QC", "KS KH KD 2C", 1) to Decider.Category,
        Triple("KS KH 4D 3C", "QS QH AD JC", 1) to Decider.Group,
        Triple("AS AH 9D 9C", "KS KH QD QC", 1) to Decider.Group,
        Triple("KS KH 9S 9H", "KD KC 8S 8H", 1) to Decider.Group,
        Triple("KS KH KD 2C", "QS QH QD AC", 1) to Decider.Group,
        Triple("10S 10H AD 3C", "10D 10C KS QH", 1) to Decider.Kicker,
        Triple("10S 10H AD 5C", "10D 10C AS 4H", 1) to Decider.Kicker,
        Triple("AS KH QD 9C", "AH KS QS 8D", 1) to Decider.Kicker,
        Triple("AS KH 5D 4C", "AH KS 5S 3D", 1) to Decider.Kicker,
        Triple("9S 9H 8D 2C", "9D 9C 7S 6H", 1) to Decider.Kicker,
        // Same ranks: the best suit among the top-ranked cards wins, nothing else.
        Triple("AS KC QC JC", "AH KS QS JS", 1) to Decider.Suit,
        Triple("KS KD 9H 9C", "KH KC 9S 9D", 1) to Decider.Suit,
        Triple("QS QC 7H 5D", "QH QD 7S 5S", 1) to Decider.Suit,
        Triple("8S 8H AC 3D", "8D 8C AD 3S", -1) to Decider.Suit,
        Triple("JD 10C 9C 8C", "JC 10S 9S 8S", 1) to Decider.Suit,
        Triple("10D 10C 5H 5C", "10S 10H 5S 5D", -1) to Decider.Suit,
    )

    @Test fun beatsMatchesServerCompareHands() {
        for ((hands, decider) in serverComparisons) {
            val (a, b, sign) = hands
            val va = HandValue.of(hand(a))!!
            val vb = HandValue.of(hand(b))!!
            assertEquals("$a vs $b", sign > 0, va.beats(vb))
            assertEquals("$b vs $a", sign < 0, vb.beats(va))
            assertEquals("$a vs $b", decider, va.decider(vb))
            assertEquals("$b vs $a", decider, vb.decider(va))
        }
    }

    /** Real deals for every table size: every pair of hands compares as the server's `compareHands` would. */
    @Test fun beatsMatchesServerOnRandomDeals() {
        val random = Random(20261011)
        var bySuit = 0
        repeat(3000) { i ->
            val players = 4 + i % 10
            val deck = (14 downTo 15 - players).flatMap { rank -> listOf("S", "H", "D", "C").map { Card(rank, it) } }
            val hands = deck.shuffled(random).chunked(4)
            for (a in hands) for (b in hands) {
                if (a === b) continue
                val expected = serverCompare(a, b)
                assertNotEquals("never a tie: $a vs $b", 0, expected)
                val va = HandValue.of(a)!!
                val vb = HandValue.of(b)!!
                assertEquals("$a vs $b", expected > 0, va.beats(vb))
                if (va.decider(vb) == Decider.Suit) bySuit++
            }
        }
        assertTrue("the sweep reaches the suit tiebreak", bySuit > 100)
    }

    // Helpers

    private fun name(cards: String): String = HandValue.of(hand(cards))!!.name

    /** "AS 10H 2C" → cards. */
    private fun hand(cards: String): List<Card> = cards.split(" ").map { c ->
        val rank = when (val r = c.dropLast(1)) {
            "A" -> 14
            "K" -> 13
            "Q" -> 12
            "J" -> 11
            else -> r.toInt()
        }
        Card(rank, c.takeLast(1))
    }

    /** `evaluateHand` + `compareHands` from hands.ts, transcribed line by line. */
    private fun serverCompare(a: List<Card>, b: List<Card>): Int {
        class Value(val category: Int, val groups: List<Int>, val kickers: List<Int>, val topSuit: Int)

        fun suitStrength(suit: String) = 4 - listOf("S", "H", "D", "C").indexOf(suit)

        fun evaluate(hand: List<Card>): Value {
            val counts = linkedMapOf<Int, Int>()
            for (c in hand) counts[c.rank] = (counts[c.rank] ?: 0) + 1
            val byGroup = counts.entries.map { it.key to it.value }
                .sortedWith { x, y -> if (y.second != x.second) y.second - x.second else y.first - x.first }
            val groups = byGroup.filter { it.second > 1 }.map { it.first }
            val kickers = byGroup.filter { it.second == 1 }.map { it.first }
            val topCount = byGroup[0].second
            val category = when {
                topCount == 4 -> 4
                topCount == 3 -> 3
                groups.size == 2 -> 2
                groups.size == 1 -> 1
                else -> 0
            }
            val topRank = hand.maxOf { it.rank }
            val topSuit = hand.filter { it.rank == topRank }.maxOf { suitStrength(it.suit) }
            return Value(category, groups, kickers, topSuit)
        }

        val va = evaluate(a)
        val vb = evaluate(b)
        if (va.category != vb.category) return va.category - vb.category
        for (i in va.groups.indices) if (va.groups[i] != vb.groups[i]) return va.groups[i] - vb.groups[i]
        for (i in va.kickers.indices) if (va.kickers[i] != vb.kickers[i]) return va.kickers[i] - vb.kickers[i]
        return va.topSuit - vb.topSuit
    }
}
