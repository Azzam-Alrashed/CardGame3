package com.azzamalrashed.cardgame3.screens.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azzamalrashed.cardgame3.R
import com.azzamalrashed.cardgame3.design.AsyncPillButton
import com.azzamalrashed.cardgame3.design.Blob
import com.azzamalrashed.cardgame3.design.BlocksWhileBusy
import com.azzamalrashed.cardgame3.design.Haptic
import com.azzamalrashed.cardgame3.design.Mood
import com.azzamalrashed.cardgame3.design.OnChange
import com.azzamalrashed.cardgame3.design.PillButton
import com.azzamalrashed.cardgame3.design.Theme
import com.azzamalrashed.cardgame3.design.formatted
import com.azzamalrashed.cardgame3.design.frameTicks
import com.azzamalrashed.cardgame3.design.rememberHaptics
import com.azzamalrashed.cardgame3.design.swiftSpring
import com.azzamalrashed.cardgame3.model.GameOver
import com.azzamalrashed.cardgame3.model.Room
import com.azzamalrashed.cardgame3.model.Seat
import com.azzamalrashed.cardgame3.model.ms
import com.azzamalrashed.cardgame3.services.Backend
import com.azzamalrashed.cardgame3.services.Sound
import com.azzamalrashed.cardgame3.services.SoundPlayer
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

// Ported from ios/CardGame3/Screens/Game/GameOverView.swift.

private val medals = listOf("🥇", "🥈", "🥉")

/** The winner, the standings, and "Play again". (iOS: GameOverView.swift) */
@Composable
fun GameOverScreen(backend: Backend, room: Room, over: GameOver) {
    val iWon = over.winnerId == backend.uid
    var shown by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    LaunchedEffect(Unit) {
        shown = true
        haptics.play(if (iWon) Haptic.Success else Haptic.Medium)
        SoundPlayer.play(Sound.Fanfare)
    }
    OnChange(room.rematchCode) { old, new -> if (old == null && new != null) haptics.play(Haptic.Medium) }
    val appear by animateFloatAsState(if (shown) 1f else 0f, swiftSpring(0.6, 0.45), label = "appear")
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = 4f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            // Scrolls only when it doesn't fit (a phone on its side, a big table).
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.graphicsLayer {
                        val scale = 0.2f + 0.8f * appear
                        scaleX = scale
                        scaleY = scale
                        translationY = bob.dp.toPx()
                    },
                ) {
                    Blob(Theme.color(room.seat(over.winnerId)), 150.dp, mood = Mood.Wink, hair = true)
                    Text(
                        "👑",
                        Modifier
                            .align(Alignment.TopCenter)
                            .rotate(-10f)
                            .offset(y = (-44).dp),
                        style = TextStyle(fontSize = 56.sp),
                    )
                }
                Text(
                    if (iWon) stringResource(R.string.results_you_win) else stringResource(R.string.results_they_win, room.name(over.winnerId)),
                    Modifier.graphicsLayer { alpha = appear.coerceIn(0f, 1f) },
                    style = Theme.wordmark(44f),
                    textAlign = TextAlign.Center,
                )
                Column(
                    Modifier
                        .widthIn(max = 520.dp)
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    over.standings.forEachIndexed { place, seat ->
                        key(seat.id) { Standing(place, seat, room, backend.uid, shown) }
                    }
                }
                Spacer(Modifier.weight(1f))
                BlocksWhileBusy(
                    Modifier
                        .widthIn(max = 520.dp)
                        .padding(24.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // "Play again" opens a new lobby; once someone has, everyone else joins theirs.
                        val rematch = when {
                            room.rematchCode == null -> stringResource(R.string.results_play_again)
                            room.rematchBy == null -> stringResource(R.string.results_join_rematch)
                            else -> stringResource(R.string.results_join_their_rematch, room.rematchBy)
                        }
                        AsyncPillButton(action = { backend.rematch() }) {
                            AnimatedContent(
                                targetState = rematch,
                                transitionSpec = {
                                    fadeIn(swiftSpring(0.5, 0.15)) togetherWith fadeOut(swiftSpring(0.5, 0.15)) using
                                        SizeTransform(clip = false) { _, _ -> swiftSpring(0.5, 0.15, IntSize.VisibilityThreshold) }
                                },
                                contentAlignment = Alignment.Center,
                                label = "rematch",
                            ) { label -> Text(label) }
                        }
                        PillButton(stringResource(R.string.results_back_home), onClick = { backend.goHome() }, primary = false)
                    }
                }
            }
        }
        if (iWon) Confetti(Modifier.fillMaxSize())
    }
}

/** One place in the final standings, springing in after the ones above it. */
@Composable
private fun Standing(place: Int, seat: Seat, room: Room, me: String?, shown: Boolean) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(shown) {
        if (shown) {
            delay(400L + place * 80L)
            progress.animateTo(1f, swiftSpring(0.5, 0.35))
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = (30 * (1 - progress.value)).dp.toPx()
                alpha = progress.value.coerceIn(0f, 1f)
            }
            .background(Color.White.copy(alpha = if (place == 0) 0.9f else 0.4f), CircleShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (place < medals.size) medals[place] else stringResource(R.string.results_place, place + 1),
            Modifier.width(32.dp),
            style = Theme.body(if (place < medals.size) 22f else 17f, FontWeight.ExtraBold),
            textAlign = TextAlign.Center,
        )
        Blob(Theme.color(room.seat(seat.id)), 28.dp, mood = if (place == 0) Mood.Wink else Mood.Happy)
        Text(
            if (seat.id == me) stringResource(R.string.results_you) else room.name(seat.id),
            Modifier.weight(1f),
            style = Theme.body(17f, FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(seat.points.formatted(), style = with(Theme) { Theme.body(17f, FontWeight.ExtraBold).monospacedDigits() })
    }
}

/** One bit of paper: where it starts, when it falls and how it tumbles (sizes in dp). */
private class ConfettiPiece(random: Random) {
    val x = random.nextDouble()
    val delay = random.nextDouble(0.0, 0.8)
    val speed = random.nextDouble(280.0, 480.0)
    val sway = random.nextDouble(1.5, 4.0)
    val spin = random.nextDouble(-6.0, 6.0)
    val width = random.nextDouble(7.0, 12.0).toFloat()
    val height = random.nextDouble(10.0, 18.0).toFloat()
    val color = (Theme.blobColors + Color.White + Theme.hotPink).random(random)
}

/** Seconds until every piece has fallen past the bottom of a view `height` dp tall. */
private fun confettiSeconds(pieces: List<ConfettiPiece>, height: Float): Double =
    pieces.maxOf { it.delay + (height + 60) / it.speed }

/** Bits of colored paper falling from the top for a few seconds, for wins. */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val pieces = remember { List(90) { ConfettiPiece(Random.Default) } }
    val start = remember { System.currentTimeMillis() }
    val density = LocalDensity.current
    var height by remember { mutableFloatStateOf(0f) }
    // Four seconds, as on iOS, or until the last piece has left a taller screen (so none stop mid-air).
    val ticks = frameTicks(start + ms(max(4.0, confettiSeconds(pieces, height))))
    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { height = with(density) { it.height.toDp().value } },
    ) {
        val t = (ticks.value - start) / 1000.0
        val sway = 24.dp.toPx()
        val corner = CornerRadius(2.dp.toPx())
        for (p in pieces) {
            val life = t - p.delay
            if (life <= 0) continue
            val y = (-30 + life * p.speed).toFloat().dp.toPx()
            if (y >= size.height + 30.dp.toPx()) continue
            val x = p.x.toFloat() * size.width + sin(life * p.sway).toFloat() * sway
            translate(x, y) {
                rotate(Math.toDegrees(life * p.spin).toFloat(), pivot = Offset.Zero) {
                    // Squash horizontally as it tumbles.
                    scale(cos(life * p.sway * 2).toFloat(), 1f, pivot = Offset.Zero) {
                        val w = p.width.dp.toPx()
                        val h = p.height.dp.toPx()
                        drawRoundRect(p.color, topLeft = Offset(-w / 2, -h / 2), size = Size(w, h), cornerRadius = corner)
                    }
                }
            }
        }
    }
}
