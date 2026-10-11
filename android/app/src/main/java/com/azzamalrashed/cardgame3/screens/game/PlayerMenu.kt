package com.azzamalrashed.cardgame3.screens.game

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azzamalrashed.cardgame3.CardGame3App
import com.azzamalrashed.cardgame3.R
import com.azzamalrashed.cardgame3.design.Choice
import com.azzamalrashed.cardgame3.design.ChoiceDialog
import com.azzamalrashed.cardgame3.design.Haptic
import com.azzamalrashed.cardgame3.design.MessageDialog
import com.azzamalrashed.cardgame3.design.Theme
import com.azzamalrashed.cardgame3.design.rememberHaptics
import com.azzamalrashed.cardgame3.model.HiddenNames
import com.azzamalrashed.cardgame3.model.Room
import com.azzamalrashed.cardgame3.services.Backend
import com.azzamalrashed.cardgame3.services.ReportReason
import kotlinx.coroutines.launch

/**
 * Long-press a player to report their name or hide it on this phone. Does nothing for yourself and for AI
 * players. Wraps `content`. (iOS: PlayerMenu.swift)
 */
@Composable
fun PlayerMenu(uid: String, room: Room, backend: Backend, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (!hasPlayerMenu(uid, me = backend.uid, room)) {
        Box(modifier) { content() }
    } else {
        var open by remember { mutableStateOf(false) }
        var reporting by remember { mutableStateOf(false) }
        var thanked by remember { mutableStateOf(false) }
        val hidden = HiddenNames.contains(uid)
        val haptics = rememberHaptics()

        Box(
            modifier
                .pointerInput(uid) {
                    onLongPress {
                        haptics.play(Haptic.Heavy)
                        open = true
                    }
                }
                // One TalkBack stop per player ("double-tap and hold" opens the menu), like VoiceOver's actions.
                .semantics(mergeDescendants = true) {
                    onLongClick {
                        open = true
                        true
                    }
                },
        ) {
            content()
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                shape = RoundedCornerShape(14.dp),
                containerColor = Color.White,
            ) {
                MenuItem(stringResource(R.string.player_menu_report), Icons.Filled.Feedback) {
                    open = false
                    reporting = true
                }
                MenuItem(
                    stringResource(if (hidden) R.string.player_menu_show else R.string.player_menu_hide),
                    if (hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                ) {
                    open = false
                    HiddenNames.set(uid, !hidden)
                }
            }
        }

        if (reporting) {
            ChoiceDialog(
                title = stringResource(R.string.player_menu_report_title, room.name(uid)),
                message = stringResource(R.string.player_menu_report_message),
                choices = ReportReason.entries.map { reason ->
                    Choice(stringResource(reason.label)) {
                        CardGame3App.scope.launch { thanked = backend.report(uid, reason) }
                    }
                },
                onDismiss = { reporting = false },
            )
        }

        if (thanked) {
            val hideTheirs = Choice(stringResource(R.string.player_menu_hide_theirs)) { HiddenNames.set(uid, true) }
            val ok = Choice(stringResource(R.string.ok)) {}
            MessageDialog(
                title = stringResource(R.string.player_menu_thanks_title),
                message = stringResource(R.string.player_menu_thanks_message),
                choices = if (hidden) listOf(ok) else listOf(hideTheirs, ok),
                onDismiss = { thanked = false },
            )
        }
    }
}

/** The menu is for other people only: not for yourself, and not for AI players. */
internal fun hasPlayerMenu(uid: String, me: String?, room: Room): Boolean = uid != me && !room.isAI(uid)

/** One row of the menu: its label, then its icon (as in an iOS context menu). */
@Composable
private fun MenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = Theme.body(17f, FontWeight.Normal), color = Theme.ink) },
        trailingIcon = { Icon(icon, contentDescription = null, tint = Theme.ink) },
        onClick = onClick,
    )
}

/**
 * Calls `action` when this is held down (iOS's context menu). Taps are left alone: nothing is consumed unless the
 * long press fires, a press a child already took (the lobby's ✕ button) is ignored, and moving off or scrolling
 * cancels it.
 */
private suspend fun PointerInputScope.onLongPress(action: () -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = true)
        val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            waitForUpOrCancellation()
            true
        }
        if (endedEarly == null) {
            action()
            // The rest of this touch belongs to the menu: nothing underneath sees a tap or a drag.
            do {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}
