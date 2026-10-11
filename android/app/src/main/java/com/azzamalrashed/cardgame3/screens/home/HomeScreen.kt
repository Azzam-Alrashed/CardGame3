package com.azzamalrashed.cardgame3.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import com.azzamalrashed.cardgame3.design.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.azzamalrashed.cardgame3.CardGame3App
import com.azzamalrashed.cardgame3.R
import com.azzamalrashed.cardgame3.design.AdaptiveSplit
import com.azzamalrashed.cardgame3.design.AsyncPillButton
import com.azzamalrashed.cardgame3.design.BlocksWhileBusy
import com.azzamalrashed.cardgame3.design.Blob
import com.azzamalrashed.cardgame3.design.BottomSheet
import com.azzamalrashed.cardgame3.design.CircleIconButton
import com.azzamalrashed.cardgame3.design.Haptic
import com.azzamalrashed.cardgame3.design.LocalBusyGroup
import com.azzamalrashed.cardgame3.design.Mood
import com.azzamalrashed.cardgame3.design.PillField
import com.azzamalrashed.cardgame3.design.PlayingCard
import com.azzamalrashed.cardgame3.design.SoundToggle
import com.azzamalrashed.cardgame3.design.SpeechBubble
import com.azzamalrashed.cardgame3.design.Spinner
import com.azzamalrashed.cardgame3.design.Theme
import com.azzamalrashed.cardgame3.design.rememberHaptics
import com.azzamalrashed.cardgame3.design.swiftSpring
import com.azzamalrashed.cardgame3.screens.onboarding.HowToPlaySheet
import com.azzamalrashed.cardgame3.services.AppSettings
import com.azzamalrashed.cardgame3.services.Backend
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Name, create a room, join one by code, or go back to a game you stepped away from. (iOS: HomeView.swift) */
@Composable
fun HomeScreen(backend: Backend) {
    var code by remember { mutableStateOf("") }
    var showRules by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val ready = hasPlayerName(backend.playerName) && backend.uid != null
    val codeComplete = code.length == ROOM_CODE_LENGTH

    // The title and the controls come in when the screen appears.
    val appeared = remember { Animatable(0f, visibilityThreshold = 0.001f) }
    LaunchedEffect(Unit) {
        delay(100)
        appeared.animateTo(1f, swiftSpring(0.6, 0.35, 0.001f))
    }

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        AdaptiveSplit(
            main = {
                Hero(Modifier.pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } })
            },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .graphicsLayer {
                        val scale = 0.8f + 0.2f * appeared.value
                        scaleX = scale
                        scaleY = scale
                        alpha = appeared.value.coerceIn(0f, 1f)
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.wordmark), style = Theme.wordmark(72f))
                Text(
                    stringResource(R.string.home_tagline),
                    modifier = Modifier.alpha(0.7f),
                    style = Theme.body(16f, FontWeight.Medium),
                    textAlign = TextAlign.Center,
                )
            }

            BlocksWhileBusy(
                Modifier.graphicsLayer {
                    translationY = (1f - appeared.value) * 40.dp.toPx()
                    alpha = appeared.value.coerceIn(0f, 1f)
                },
            ) {
                BottomSheet {
                    backend.awayRoomCode?.let { away ->
                        AsyncPillButton(
                            text = stringResource(R.string.home_back_to_game, away),
                            action = { backend.returnToGame() },
                            enabled = backend.uid != null,
                        )
                    }
                    PillField(
                        value = backend.playerName,
                        onValueChange = { backend.playerName = it },
                        placeholder = stringResource(R.string.home_your_name),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    )

                    AsyncPillButton(
                        text = stringResource(R.string.home_create_room),
                        action = { backend.createRoom() },
                        enabled = ready,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PillField(
                            value = code,
                            onValueChange = { code = roomCodeInput(it) },
                            placeholder = stringResource(R.string.home_room_code),
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Go,
                            ),
                            keyboardActions = KeyboardActions(onGo = {
                                focusManager.clearFocus()
                                if (ready && code.length == ROOM_CODE_LENGTH) {
                                    val joining = code
                                    CardGame3App.scope.launch { backend.joinRoom(joining) }
                                }
                            }),
                        )
                        JoinButton(
                            primary = codeComplete,
                            enabled = ready && codeComplete,
                            action = { backend.joinRoom(code) },
                            modifier = Modifier.width(110.dp),
                        )
                    }
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SoundToggle()
            CircleIconButton(
                icon = Icons.Filled.QuestionMark,
                contentDescription = stringResource(R.string.how_to_play),
                onClick = { showRules = true },
            )
        }
    }

    if (showRules) {
        HowToPlaySheet(
            onDismiss = { showRules = false },
            onReplayIntro = { AppSettings.seenOnboarding = false },
        )
    }
}

/** A fanned hand of four Aces, with blob friends peeking around it. */
@Composable
private fun Hero(modifier: Modifier = Modifier) {
    // The cards fan out from a neat stack when the screen appears, one after another.
    val fan = remember { List(4) { Animatable(0f, visibilityThreshold = 0.001f) } }
    LaunchedEffect(Unit) {
        fan.forEachIndexed { i, card ->
            launch {
                delay(200L + i * 70L)
                card.animateTo(1f, swiftSpring(0.7, 0.4, 0.001f))
            }
        }
    }
    val bob = bobOffset()
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val w = maxWidth
        val h = maxHeight
        val cardW = min(w * 0.3f, h * 0.42f)
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = bob.value.dp.toPx() }) {
            Blob(Theme.blobColors[2], w * 0.34f, Modifier.position(w * 0.13f, h * 0.3f), Mood.Happy, Offset(1f, 0.5f), hair = true)
            Blob(Theme.blobColors[4], w * 0.3f, Modifier.position(w * 0.88f, h * 0.26f), Mood.Wink, Offset(-1f, 0.5f))
            // With the keyboard up on a small phone the hero can shrink to nothing.
            if (cardW >= 1.dp) {
                listOf("C", "D", "H", "S").forEachIndexed { i, suit ->
                    PlayingCard(
                        rank = 14,
                        suit = suit,
                        modifier = Modifier
                            .position(w * 0.5f, h * 0.52f)
                            .graphicsLayer {
                                val progress = fan[i].value
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                rotationZ = progress * ((i - 1) * 12f - 6f)
                                translationX = progress * (i * 0.2f - 0.3f) * cardW.toPx()
                            },
                        width = cardW,
                    )
                }
            }
            Blob(Theme.blobColors[3], w * 0.26f, Modifier.position(w * 0.22f, h * 0.86f), Mood.Surprised, Offset(0.5f, -1f))
            Blob(Theme.blobColors[1], w * 0.22f, Modifier.position(w * 0.8f, h * 0.84f), Mood.Happy, Offset(-1f, -1f))
            SpeechBubble(stringResource(R.string.home_deal_bubble), Modifier.position(w * 0.6f, h * 0.08f))
        }
    }
}

/**
 * The Join button: an async pill button that turns from outline to solid, animated (iOS `.snappy`), once the code
 * has all its letters. The shared `AsyncPillButton` switches style instantly, so this one draws itself; otherwise
 * it behaves the same (press feedback, spinner, blocks the surrounding `BlocksWhileBusy`).
 */
@Composable
private fun JoinButton(primary: Boolean, enabled: Boolean, action: suspend () -> Unit, modifier: Modifier = Modifier) {
    val background by animateColorAsState(
        if (primary) Theme.ink else Color.White.copy(alpha = 0.35f),
        swiftSpring(0.5, 0.15),
        label = "join background",
    )
    val foreground by animateColorAsState(if (primary) Color.White else Theme.ink, swiftSpring(0.5, 0.15), label = "join text")
    // Faded while disabled; the fade animates only along with the style change (iOS animates on `code.count == 4`).
    val opacity = remember { Animatable(if (enabled) 1f else 0.35f) }
    val styleShown = remember { mutableStateOf(primary) }
    LaunchedEffect(enabled, primary) {
        val target = if (enabled) 1f else 0.35f
        if (styleShown.value != primary) {
            styleShown.value = primary
            opacity.animateTo(target, swiftSpring(0.5, 0.15))
        } else {
            opacity.snapTo(target)
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = rememberHaptics()
    LaunchedEffect(pressed) { if (pressed) haptics.play(Haptic.Light) }
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, swiftSpring(0.25, 0.5), label = "join press")

    var running by remember { mutableStateOf(false) }
    val group = LocalBusyGroup.current
    DisposableEffect(running, group) {
        if (running) group?.enter()
        onDispose { if (running) group?.leave() }
    }

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = opacity.value
            }
            .background(background, CircleShape)
            // The solid look has an ink fill under the same ink outline, so the outline can stay.
            .border(2.dp, Theme.ink, CircleShape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
                if (!running) {
                    running = true
                    CardGame3App.scope.launch {
                        try {
                            action()
                        } finally {
                            running = false
                        }
                    }
                }
            }
            .padding(horizontal = 12.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.home_join),
            modifier = Modifier.alpha(if (running) 0f else 1f),
            color = foreground,
            style = Theme.body(18f, FontWeight.Bold),
        )
        if (running) Spinner(color = foreground)
    }
}

// Shared with the intro (screens/onboarding).

/** Room codes are 4 letters. */
internal const val ROOM_CODE_LENGTH = 4

/** What the room code field keeps of what was typed: letters only, in capitals, at most 4. */
internal fun roomCodeInput(typed: String): String = typed.uppercase().filter { it.isLetter() }.take(ROOM_CODE_LENGTH)

/** The name has something besides spaces (iOS trims `.whitespaces`: spaces and tabs, not newlines). */
internal fun hasPlayerName(name: String): Boolean = name.any { !(it == '\t' || Character.isSpaceChar(it)) }

/**
 * Centers this view on (`x`, `y`) of its parent, in physical (non-mirrored) coordinates: SwiftUI's `.position`.
 * Takes the whole parent; use it on children of a full-size `Box`.
 */
internal fun Modifier.position(x: Dp, y: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints(maxWidth = constraints.maxWidth))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx() - placeable.width / 2, y.roundToPx() - placeable.height / 2)
    }
}

/** The art's slow bob, in dp: from 4 down to -4 and back, 2 s each way (iOS `.easeInOut(duration: 2).repeatForever()`). */
@Composable
internal fun bobOffset(): State<Float> =
    rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = 4f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOut), RepeatMode.Reverse),
        label = "bob",
    )
