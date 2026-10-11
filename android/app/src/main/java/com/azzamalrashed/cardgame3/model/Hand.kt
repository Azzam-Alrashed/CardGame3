package com.azzamalrashed.cardgame3.model

/**
 * What a hand is, for showing its name ("Pair of Kings"). Also works on the 1–3 cards turned over so far.
 * Mirrors `evaluateHand` in firebase/functions/src/engine/hands.ts; the server decides who wins.
 */
data class HandValue(
    val category: Category,
    /** Ranks of the paired, tripled or quad groups, strongest first. */
    val groups: List<Int>,
    /** Ranks of the leftover single cards, highest first. */
    val kickers: List<Int>,
    /** Strength of the best suit among the highest-ranked cards (♠ 4, ♥ 3, ♦ 2, ♣ 1): the last tiebreak. */
    val topSuit: Int,
) {
    /** Weakest first, so `ordinal` (and `compareTo`) is the hand's strength. */
    enum class Category { HighCard, OnePair, TwoPairs, ThreeOfAKind, FourOfAKind }

    /** Which step of the tiebreak separates two hands, in the server's order (`compareHands` in hands.ts). */
    enum class Decider { Category, Group, Kicker, Suit }

    fun decider(other: HandValue): Decider = when {
        category != other.category -> Decider.Category
        groups != other.groups -> Decider.Group
        kickers != other.kickers -> Decider.Kicker
        else -> Decider.Suit
    }

    /** Whether this hand beats the other. There is never a tie between hands from one deck. */
    fun beats(other: HandValue): Boolean = when (decider(other)) {
        Decider.Category -> category > other.category
        Decider.Group -> firstDifference(groups, other.groups)
        Decider.Kicker -> firstDifference(kickers, other.kickers)
        Decider.Suit -> topSuit > other.topSuit
    }

    val name: String
        get() = when (category) {
            Category.FourOfAKind -> "Four ${plural(groups[0])}"
            Category.ThreeOfAKind -> "Three ${plural(groups[0])}"
            Category.TwoPairs -> "Two pairs · ${short(groups[0])} & ${short(groups[1])}"
            Category.OnePair -> "Pair of ${plural(groups[0])}"
            Category.HighCard -> "${singular(kickers[0])} high"
        }

    companion object {
        val suitStrength = mapOf("S" to 4, "H" to 3, "D" to 2, "C" to 1)
        val suitSymbol = mapOf(4 to "♠", 3 to "♥", 2 to "♦", 1 to "♣")

        /** The value of 1 to 4 cards, or null for none (or too many). */
        fun of(cards: List<Card>): HandValue? {
            if (cards.size !in 1..4) return null
            val counts = cards.groupingBy { it.rank }.eachCount()
            // Bigger groups first, then higher rank.
            val byGroup = counts.entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })
            val groups = byGroup.filter { it.value > 1 }.map { it.key }
            val kickers = byGroup.filter { it.value == 1 }.map { it.key }
            val category = when (byGroup[0].value) {
                4 -> Category.FourOfAKind
                3 -> Category.ThreeOfAKind
                2 -> if (groups.size == 2) Category.TwoPairs else Category.OnePair
                else -> Category.HighCard
            }
            val topRank = cards.maxOf { it.rank }
            val topSuit = cards.filter { it.rank == topRank }.maxOf { suitStrength[it.suit] ?: 0 }
            return HandValue(category, groups, kickers, topSuit)
        }

        private fun firstDifference(a: List<Int>, b: List<Int>): Boolean =
            a.zip(b).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false

        private fun singular(rank: Int): String =
            mapOf(14 to "Ace", 13 to "King", 12 to "Queen", 11 to "Jack")[rank] ?: "$rank"

        private fun plural(rank: Int): String = when {
            rank == 6 -> "Sixes"
            rank > 10 -> singular(rank) + "s"
            else -> "${rank}s"
        }

        private fun short(rank: Int): String = mapOf(14 to "A", 13 to "K", 12 to "Q", 11 to "J")[rank] ?: "$rank"
    }
}

/** "King of hearts", for TalkBack. */
val Card.spokenName: String
    get() {
        val name = mapOf(14 to "Ace", 13 to "King", 12 to "Queen", 11 to "Jack")[rank] ?: "$rank"
        val suitName = mapOf("S" to "spades", "H" to "hearts", "D" to "diamonds", "C" to "clubs")[suit] ?: ""
        return "$name of $suitName"
    }
