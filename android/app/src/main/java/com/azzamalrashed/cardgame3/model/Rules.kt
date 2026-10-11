package com.azzamalrashed.cardgame3.model

/** Rules shared with the backend. */
object TableSize {
    const val MIN_PLAYERS = 4
    const val MAX_PLAYERS = 13
}

object Betting {
    const val STEP = 500
    const val MIN_BET = 500

    /** Length of a betting turn before a bot takes the seat. */
    const val TURN_SECONDS = 45.0
}
