package com.azzamalrashed.cardgame3.screens.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.azzamalrashed.cardgame3.model.DealClock
import com.azzamalrashed.cardgame3.model.PublicRound
import com.azzamalrashed.cardgame3.model.RevealClock
import com.azzamalrashed.cardgame3.model.Room
import com.azzamalrashed.cardgame3.services.Backend

/** Everyone at the table, in a row that scrolls to whoever is acting. (iOS: PlayersStrip.swift) */
@Composable
fun PlayersStrip(
    backend: Backend,
    room: Room,
    round: PublicRound,
    me: String?,
    /** The deal this phone is animating, if any. */
    clock: DealClock?,
    /** How many of my cards I've turned over (shown before the server has it). */
    myFlips: Int,
    /** The showdown this phone is staging, if any. */
    reveal: RevealClock?,
    modifier: Modifier = Modifier,
) {
}
