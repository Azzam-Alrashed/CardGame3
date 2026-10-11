package com.azzamalrashed.cardgame3.screens.game

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.azzamalrashed.cardgame3.design.Haptic
import com.azzamalrashed.cardgame3.design.drawCardBack
import com.azzamalrashed.cardgame3.design.frameTicks
import com.azzamalrashed.cardgame3.design.rememberHaptics
import com.azzamalrashed.cardgame3.design.reduceMotion
import com.azzamalrashed.cardgame3.model.DealClock
import com.azzamalrashed.cardgame3.model.DealTimeline
import com.azzamalrashed.cardgame3.model.ms
import com.azzamalrashed.cardgame3.services.Sound
import com.azzamalrashed.cardgame3.services.SoundPlayer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The dealer shuffling, then cards flying face down to every player, one at a time, with sound. Drawn over the
 * whole table; the small cards and my hand show each card once it has landed. (iOS: DealLayer.swift)
 */
@Composable
fun DealLayer(clock: DealClock, me: String?, modifier: Modifier = Modifier) {
    val spots = LocalDealSpots.current
    val flat = reduceMotion()
    val haptics = rememberHaptics()
    // The deal that is over, so no card can be left in mid-air.
    var finished by remember { mutableStateOf<Long?>(null) }
    // Where this layer sits on screen: the spots are in root coordinates.
    val layer = remember { LayerOrigin() }

    LaunchedEffect(clock.start) {
        playCuesOnTime(dealSoundCues(clock.timeline, me), elapsed = { (System.currentTimeMillis() - clock.start) / 1000.0 }) { cue ->
            when (cue) {
                DealSoundCue.Shuffle -> SoundPlayer.play(Sound.Shuffle)
                DealSoundCue.Deal -> SoundPlayer.play(Sound.Deal, volume = 0.7f)
                DealSoundCue.Land -> {
                    SoundPlayer.play(Sound.Land, volume = 0.6f)
                    haptics.play(Haptic.Light)
                }
            }
        }
        delay(max(0L, clock.end - System.currentTimeMillis()) + 50)
        finished = clock.start
    }

    Box(
        modifier
            .onGloballyPositioned { layer.origin = it.positionInRoot() }
            .clearAndSetSemantics {},
    ) {
        if (!flat && finished != clock.start) {
            val ticks = frameTicks(clock.end)
            Canvas(Modifier.fillMaxSize()) {
                ticks.value // Redraw every frame until the deal ends.
                drawDeal(clock, me, spots, layer.origin, System.currentTimeMillis())
            }
        }
    }
}

private class LayerOrigin {
    var origin = Offset.Zero
}

// Drawing

/** The size flying cards start at, as they leave the deck. */
private val DECK_CARD_WIDTH = 16.dp

private fun DrawScope.drawDeal(clock: DealClock, me: String?, spots: DealSpots, origin: Offset, now: Long) {
    val t = (now - clock.start) / 1000.0

    // A spot's frame in this layer's coordinates.
    fun rect(spot: DealSpot): Rect? = spots.frames[spot]?.translate(-origin)

    val strip = rect(DealSpot.Strip)
    val edge = 12.dp.toPx()

    // Players scrolled out of view: their cards go to the strip's edge.
    fun clampedToStrip(point: Offset): Offset =
        if (strip == null) point else Offset(min(max(point.x, strip.left + edge), strip.right - edge), point.y)

    // The deck: on the dealer's blob, kept inside the visible strip if they're scrolled out of view.
    val deck = rect(DealSpot.Dealer)?.center?.let(::clampedToStrip) ?: return

    if (t < DealTimeline.SHUFFLE) drawShuffle(t, deck)

    for (flight in clock.timeline.flights(t)) {
        val slot = if (flight.uid == me) rect(DealSpot.Hand(flight.card)) else null
        val card = if (slot != null) {
            // The slot is the card before its tilt. The card tilts about its bottom center, which moves its own
            // center a little sideways and down: land there, so the card doesn't jump as it appears.
            val angle = Math.toRadians(TableLayout.handAngle(flight.card).toDouble())
            val half = TableLayout.handCardWidth.toPx() * 1.4f / 2
            val landing = slot.center + Offset((half * sin(angle)).toFloat(), (half * (1 - cos(angle))).toFloat())
            flyingCard(
                flight.progress, deck, landing,
                TableLayout.handCardWidth.toPx(), TableLayout.handAngle(flight.card),
                DECK_CARD_WIDTH.toPx(), 90.dp.toPx(),
            )
        } else {
            val seat = rect(DealSpot.Seat(flight.uid)) ?: continue
            // Fanned like MiniCards: about 7 dp apart.
            val x = seat.center.x + (flight.card - 1.5f) * (TableLayout.miniWidth - 5.dp).toPx()
            flyingCard(
                flight.progress, deck, clampedToStrip(Offset(x, seat.center.y)),
                TableLayout.miniWidth.toPx(), TableLayout.miniAngle(flight.card),
                DECK_CARD_WIDTH.toPx(), 90.dp.toPx(),
            )
        }
        drawCard(card.center, card.width, card.angle, card.scaleX, card.scaleY)
    }
}

/** Two half-decks riffling into each other above the dealer. */
private fun DrawScope.drawShuffle(t: Double, deck: Offset) {
    val split = (sin(t / DealTimeline.SHUFFLE * PI) * 9).toFloat().dp.toPx()
    val riffle = (sin(t * 40) * 1.5).toFloat().dp.toPx()
    val width = DECK_CARD_WIDTH.toPx()
    drawCard(deck + Offset(-split, riffle), width, -8f, 1f, 1f)
    drawCard(deck + Offset(split, -riffle), width, 8f, 1f, 1f)
}

/** A card back centered on `center`, `width` px wide, turned `angle` degrees, then squashed. */
private fun DrawScope.drawCard(center: Offset, width: Float, angle: Float, scaleX: Float, scaleY: Float) {
    val height = width * 1.4f
    withTransform({
        translate(center.x, center.y)
        rotate(angle, pivot = Offset.Zero)
        scale(scaleX, scaleY, pivot = Offset.Zero)
        translate(-width / 2, -height / 2)
    }) {
        drawCardShadow(width)
        drawCardBack(width.toDp())
    }
}

private val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.Black.copy(alpha = 0.18f).toArgb()
}

/** Blur filters by radius (in half pixels), so growing cards don't make new ones every frame. */
private val shadowBlurs = HashMap<Int, BlurMaskFilter>()

/**
 * The shadow every card casts (like `CardBack`'s), under a card drawn at the origin. Blurred shadows need
 * Android 9 on a hardware canvas; older phones draw the cards without one.
 */
private fun DrawScope.drawCardShadow(width: Float) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val widthDp = width.toDp().value
    val radius = min(10f, widthDp * 0.11f).dp.toPx()
    val y = min(6f, widthDp * 0.07f).dp.toPx()
    val corner = width * 0.14f
    val halfPixels = (radius * 2).roundToInt()
    shadowPaint.maskFilter = if (halfPixels > 0) {
        shadowBlurs.getOrPut(halfPixels) { BlurMaskFilter(halfPixels / 2f, BlurMaskFilter.Blur.NORMAL) }
    } else {
        null
    }
    drawContext.canvas.nativeCanvas.drawRoundRect(0f, y, width, width * 1.4f + y, corner, corner, shadowPaint)
}

// Flight

/** Where a flying card is drawn: its center, width (px), angle (degrees) and squash. */
internal data class FlyingCard(val center: Offset, val width: Float, val angle: Float, val scaleX: Float, val scaleY: Float)

/**
 * One card on its way: an arc from the deck, spinning, growing to its landing size, squashing as it lands.
 * `progress` is how far along its flight it is (0–1); sizes and points are in px.
 */
internal fun flyingCard(
    progress: Double,
    from: Offset,
    to: Offset,
    toWidth: Float,
    toAngle: Float,
    startWidth: Float,
    maxLift: Float,
): FlyingCard {
    val p = 1 - (1 - progress).pow(2.2) // fast out of the hand, settling at the end
    val lift = min(maxLift.toDouble(), hypot((to.x - from.x).toDouble(), (to.y - from.y).toDouble()) * 0.35)
    val controlX = (from.x + to.x) / 2.0
    val controlY = min(from.y, to.y) - lift
    val x = (1 - p).pow(2) * from.x + 2 * (1 - p) * p * controlX + p * p * to.x
    val y = (1 - p).pow(2) * from.y + 2 * (1 - p) * p * controlY + p * p * to.y
    val width = startWidth + (toWidth - startWidth) * p
    val squash = if (progress > 0.85) 1 - (progress - 0.85) * 0.8 else 1.0
    return FlyingCard(
        center = Offset(x.toFloat(), y.toFloat()),
        width = width.toFloat(),
        angle = ((1 - p) * 300 + toAngle * p).toFloat(),
        scaleX = (2 - squash).toFloat(),
        scaleY = squash.toFloat(),
    )
}

// Sound

internal enum class DealSoundCue { Shuffle, Deal, Land }

/**
 * The shuffle, a swish for each card, and a soft landing (with a tap) for each of mine: when each plays, in
 * seconds since the deal started, in order.
 */
internal fun dealSoundCues(timeline: DealTimeline, me: String?): List<Pair<Double, DealSoundCue>> {
    val cues = mutableListOf(0.0 to DealSoundCue.Shuffle)
    // On a big table cards leave every few hundredths of a second: a swish for every other one is plenty.
    val every = if (timeline.interval < 0.05) 2 else 1
    for ((seat, uid) in timeline.order.withIndex()) {
        for (card in 0 until DealTimeline.HAND_SIZE) {
            val leaves = timeline.departure(uid, card) ?: continue
            if ((card * timeline.order.size + seat) % every == 0) cues += leaves to DealSoundCue.Deal
            if (uid == me) cues += (leaves + DealTimeline.FLIGHT) to DealSoundCue.Land
        }
    }
    return cues.sortedBy { it.first }
}

/**
 * Plays each cue at its time (seconds, as `elapsed` counts them). Cues already more than 0.1 s past are
 * skipped: when the deal or showdown started a while ago, stay quiet rather than play a burst.
 */
internal suspend fun <T> playCuesOnTime(cues: List<Pair<Double, T>>, elapsed: () -> Double, play: (T) -> Unit) {
    for ((at, cue) in cues.sortedBy { it.first }) {
        val wait = at - elapsed()
        if (wait < -0.1) continue
        if (wait > 0) delay(ms(wait))
        currentCoroutineContext().ensureActive()
        play(cue)
    }
}
