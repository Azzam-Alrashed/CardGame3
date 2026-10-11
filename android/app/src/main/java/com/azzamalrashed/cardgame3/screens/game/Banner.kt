package com.azzamalrashed.cardgame3.screens.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.azzamalrashed.cardgame3.model.PublicRound
import com.azzamalrashed.cardgame3.model.RevealClock
import com.azzamalrashed.cardgame3.model.Room
import com.azzamalrashed.cardgame3.services.Backend

/** Big headline for what is happening right now, and the staged showdown. (iOS: Banner.swift) */
@Composable
fun Banner(backend: Backend, room: Room, round: PublicRound, me: String?, reveal: RevealClock?, modifier: Modifier = Modifier) {
}

/** "1:32" in a pill that ticks every second and turns pink near the end; calls `onZero` once when time runs out. */
@Composable
fun Countdown(deadline: Long, modifier: Modifier = Modifier, size: Float = 44f, onZero: () -> Unit = {}) {
}
