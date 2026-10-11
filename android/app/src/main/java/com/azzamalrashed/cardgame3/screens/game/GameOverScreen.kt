package com.azzamalrashed.cardgame3.screens.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.azzamalrashed.cardgame3.model.GameOver
import com.azzamalrashed.cardgame3.model.Room
import com.azzamalrashed.cardgame3.services.Backend

/** The winner, the standings, and "Play again". (iOS: GameOverView.swift) */
@Composable
fun GameOverScreen(backend: Backend, room: Room, over: GameOver) {
}

/** Bits of colored paper falling from the top for a few seconds, for wins. */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
}
