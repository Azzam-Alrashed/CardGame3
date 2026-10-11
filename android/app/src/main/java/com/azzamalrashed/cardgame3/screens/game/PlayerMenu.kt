package com.azzamalrashed.cardgame3.screens.game

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.azzamalrashed.cardgame3.model.Room
import com.azzamalrashed.cardgame3.services.Backend

/**
 * Long-press a player to report their name or hide it on this phone. Does nothing for yourself and for AI
 * players. Wraps `content`. (iOS: PlayerMenu.swift)
 */
@Composable
fun PlayerMenu(uid: String, room: Room, backend: Backend, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier) { content() }
}
