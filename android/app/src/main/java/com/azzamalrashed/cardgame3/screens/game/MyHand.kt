package com.azzamalrashed.cardgame3.screens.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.azzamalrashed.cardgame3.R
import com.azzamalrashed.cardgame3.design.CardBack
import com.azzamalrashed.cardgame3.design.FlipCard
import com.azzamalrashed.cardgame3.design.Haptic
import com.azzamalrashed.cardgame3.design.OnChange
import com.azzamalrashed.cardgame3.design.Theme
import com.azzamalrashed.cardgame3.design.frameTime
import com.azzamalrashed.cardgame3.design.reduceMotion
import com.azzamalrashed.cardgame3.design.rememberHaptics
import com.azzamalrashed.cardgame3.design.swiftSpring
import com.azzamalrashed.cardgame3.model.Card
import com.azzamalrashed.cardgame3.model.DealClock
import com.azzamalrashed.cardgame3.model.DealTimeline
import com.azzamalrashed.cardgame3.model.HandValue
import com.azzamalrashed.cardgame3.services.Backend
import com.azzamalrashed.cardgame3.services.Sound
import com.azzamalrashed.cardgame3.services.SoundPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * My cards. They're dealt face down and I turn them over by tapping (or swiping across them); the table sees how
 * many I've looked at. Betting without looking is allowed. (iOS: MyHand.swift)
 */
@Composable
fun MyHand(
    backend: Backend,
    cards: List<Card>,
    /** I'm dealt into this round (not knocked out). */
    inRound: Boolean,
    /** False until my cards for this round have arrived: the room and the cards update separately. */
    ready: Boolean,
    /** Everything shows face up: the round is over, or a bot holds my seat. */
    allFaceUp: Boolean,
    /** The deal this phone is animating; cards appear as they land. */
    clock: DealClock?,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    // The card lifted by a tap once it's face up, to read it more easily.
    var lifted by remember { mutableStateOf<Int?>(null) }
    // Cards turned over by a tap or a swipe: only those turn with a spring; the rest (the round ending, a bot
    // taking the seat) just show, as on iOS.
    val turnedByHand = remember { mutableStateSetOf<Int>() }
    var showHint by remember { mutableStateOf(false) }
    var hintAnimation by remember { mutableStateOf<AnimationSpec<Float>>(snap()) }

    val inputs = Inputs(cards, ready, allFaceUp, inRound)
    // The taps and the swipe (which keeps turning cards over after this composition) read the latest.
    val latest by rememberUpdatedState(inputs)

    fun isUp(index: Int): Boolean = latest.isUp(index, backend.flipped)

    /** The cards I can see, for the hand name. */
    fun seen(): HandValue? = latest.seen(backend.flipped)

    fun hideHint(animation: AnimationSpec<Float>) {
        hintAnimation = animation
        showHint = false
    }

    fun turnOver(index: Int) {
        val before = seen()?.category
        hideHint(swiftSpring(0.45, 0.25))
        turnedByHand += index
        backend.flip(listOf(index))
        SoundPlayer.play(Sound.Flip)
        haptics.play(Haptic.Light)
        HandFan.celebration(before, seen()?.category)?.let {
            SoundPlayer.play(it.sound, rate = it.rate)
            haptics.play(it.haptic)
        }
    }

    fun tap(index: Int) {
        if (isUp(index)) {
            lifted = if (lifted == index) null else index
        } else if (latest.ready) {
            turnOver(index)
        }
    }

    fun turnOverRest(rightward: Boolean) {
        if (!latest.ready) return
        val rest = HandFan.inSwipeOrder((0 until DealTimeline.HAND_SIZE).filter { !isUp(it) }, rightward)
        scope.launch {
            for (index in rest) {
                turnOver(index)
                delay(90)
            }
        }
    }

    OnChange(cards) { _, _ ->
        lifted = null
        turnedByHand.clear()
    }

    // New players may not know the cards can be turned over: after a moment, wiggle them and say so.
    LaunchedEffect(clock?.round ?: 0, backend.flipped.isEmpty(), allFaceUp, ready) {
        hideHint(snap())
        if (!latest.ready || !latest.inRound || latest.allFaceUp || backend.flipped.isNotEmpty()) return@LaunchedEffect
        val landed = clock?.end ?: System.currentTimeMillis()
        delay(max(0L, landed - System.currentTimeMillis()) + 2000)
        hintAnimation = swiftSpring(0.5)
        showHint = true
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .heightIn(min = TableLayout.handCardWidth * 1.4f + 38.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (inRound) {
            Fan(
                backend = backend,
                cards = cards,
                ready = ready,
                clock = clock,
                isUp = { inputs.isUp(it, backend.flipped) },
                lifted = lifted,
                showHint = showHint,
                hintAnimation = hintAnimation,
                turnedByHand = turnedByHand,
                onTap = ::tap,
                onSwipe = ::turnOverRest,
            )
        }
        Label(inputs.seen(backend.flipped), showHint, Modifier.heightIn(min = 28.dp))
    }
}

/** What the hand is drawn from. */
private data class Inputs(val cards: List<Card>, val ready: Boolean, val allFaceUp: Boolean, val inRound: Boolean) {
    fun isUp(index: Int, flipped: Set<Int>): Boolean = ready && (allFaceUp || index in flipped)

    /** The value of the cards I can see, for the hand name. */
    fun seen(flipped: Set<Int>): HandValue? = HandValue.of(cards.indices.filter { isUp(it, flipped) }.map { cards[it] })
}

/** A hand far in the future, for "keep animating" (`Long.MAX_VALUE` would overflow in `frameTicks`). */
private const val FOREVER = Long.MAX_VALUE / 2

/** The four cards, fanned out. */
@Composable
private fun Fan(
    backend: Backend,
    cards: List<Card>,
    ready: Boolean,
    clock: DealClock?,
    isUp: (Int) -> Boolean,
    lifted: Int?,
    showHint: Boolean,
    hintAnimation: AnimationSpec<Float>,
    turnedByHand: Set<Int>,
    onTap: (Int) -> Unit,
    onSwipe: (rightward: Boolean) -> Unit,
) {
    val flat = reduceMotion()
    // Frames while cards are landing, and while they wiggle for attention.
    val now = frameTime(if (showHint) FOREVER else clock?.end, fps = 30)
    val wiggle by animateFloatAsState(if (showHint) 1f else 0f, hintAnimation, label = "wiggle")
    val me = backend.uid ?: ""
    val turnOverLabel = stringResource(R.string.hand_turn_over)
    // The cards are things on the table: card 0 is on the left in every language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            Modifier
                .padding(bottom = 4.dp)
                .swipeAcross(onSwipe),
            horizontalArrangement = Arrangement.spacedBy(-HandFan.OVERLAP.dp),
        ) {
            for (i in 0 until DealTimeline.HAND_SIZE) {
                val landed = clock?.landed(me, i, now) ?: true
                val fadeIn by animateFloatAsState(if (landed) 1f else 0f, tween(250, easing = EaseIn), label = "land")
                val opacity = if (flat) fadeIn else if (landed) 1f else 0f
                val y by animateFloatAsState(HandFan.yOffset(i, lifted == i), swiftSpring(0.3, 0.5), label = "lift")
                val up = isUp(i)
                val card = Modifier
                    .zIndex(if (lifted == i) 1f else 0f)
                    .absoluteOffset { IntOffset(0, y.dp.roundToPx()) }
                    .dealSpot(DealSpot.Hand(i))
                    .graphicsLayer {
                        rotationZ = TableLayout.handAngle(i) + HandFan.wiggle(i, now / 1000.0) * wiggle
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        alpha = opacity
                    }
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        enabled = landed,
                        onClickLabel = if (up) null else turnOverLabel,
                        role = Role.Button,
                    ) { onTap(i) }
                if (ready && i < cards.size) {
                    FlipCard(
                        cards[i],
                        faceUp = up,
                        modifier = card,
                        width = TableLayout.handCardWidth,
                        animation = if (i in turnedByHand) swiftSpring(0.45, 0.25) else snap(),
                    )
                } else {
                    CardBack(card, TableLayout.handCardWidth)
                }
            }
        }
    }
}

/**
 * Swipe across the cards to turn over the rest, one after another: a sideways drag of more than 40 dp, which takes
 * over from the cards' taps once it has gone 24 dp (iOS's `DragGesture(minimumDistance: 24)`). A mostly vertical
 * drag is left alone, for a scrolling parent.
 */
private fun Modifier.swipeAcross(onSwipe: (rightward: Boolean) -> Unit): Modifier = pointerInput(Unit) {
    val claimAt = 24.dp.toPx()
    val swipeAt = 40.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var claimed = false
        var moved = Offset.Zero
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            moved = change.position - down.position
            if (!claimed && moved.getDistance() > claimAt) {
                if (abs(moved.x) < abs(moved.y)) break
                claimed = true
            }
            if (claimed) change.consume()
        }
        if (claimed && abs(moved.x) > swipeAt) onSwipe(moved.x > 0)
    }
}

/** What the label under the cards says. */
private sealed interface HandLabel {
    data class Hand(val name: String, val category: HandValue.Category) : HandLabel
    data object Hint : HandLabel
}

private val labelScale = swiftSpring<Float>(0.45, 0.25)
private val labelFade = swiftSpring<Float>(0.45)
private val labelSize = swiftSpring(0.45, 0.25, IntSize.VisibilityThreshold)

/** The name of what I can see ("Pair of Kings"), or the nudge to look. */
@Composable
private fun Label(hand: HandValue?, showHint: Boolean, modifier: Modifier = Modifier) {
    val label = when {
        hand != null -> HandLabel.Hand(hand.name, hand.category)
        showHint -> HandLabel.Hint
        else -> null
    }
    // A pill that's going away keeps showing its last words (the live label is captured too, so the shown pill
    // recomposes when the name changes).
    val lastHand = remember { arrayOfNulls<HandLabel.Hand>(1) }
    if (label is HandLabel.Hand) lastHand[0] = label
    Box(modifier, contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = label?.let { it::class },
            transitionSpec = {
                (scaleIn(labelScale, initialScale = 0f) + fadeIn(labelFade))
                    .togetherWith(scaleOut(labelScale, targetScale = 0f) + fadeOut(labelFade))
                    .using(SizeTransform(clip = false))
            },
            contentAlignment = Alignment.Center,
            label = "hand label",
        ) { kind ->
            when (kind) {
                HandLabel.Hand::class -> ((label as? HandLabel.Hand) ?: lastHand[0])?.let { HandPill(it) }
                HandLabel.Hint::class -> Pill(stringResource(R.string.hand_hint))
                else -> Unit
            }
        }
    }
}

/** The hand's name, popping a little when it becomes a better kind of hand. */
@Composable
private fun HandPill(hand: HandLabel.Hand) {
    val pop = remember { Animatable(1f) }
    OnChange(hand.category) { _, _ ->
        withTimeoutOrNull(150) { pop.animateTo(1.25f, swiftSpring(0.15)) }
        pop.animateTo(1f, swiftSpring(0.35))
    }
    Pill(
        hand.name,
        Modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
        },
    )
}

@Composable
private fun Pill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Color.White.copy(alpha = 0.85f), CircleShape)
            .animateContentSize(labelSize)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(text, animationSpec = labelFade, label = "hand name") {
            Text(it, style = Theme.body(14f, FontWeight.ExtraBold), maxLines = 1)
        }
    }
}

/** What turning a card over earns: a sound at a pitch, and a buzz. */
internal data class HandCelebration(val sound: Sound, val rate: Float, val haptic: Haptic)

/** The fan's geometry and the small decisions behind turning cards over. */
internal object HandFan {
    /** How much neighboring cards overlap (dp). */
    const val OVERLAP = 24

    /** How far a card sits down (dp): the outer ones lower, for the fan's curve; a lifted card rises 22. */
    fun yOffset(index: Int, lifted: Boolean): Float = abs(index - 1.5f) * 6f - if (lifted) 22f else 0f

    /** A gentle side-to-side rock (degrees), each card a little out of step with the next. */
    fun wiggle(index: Int, seconds: Double): Float = (sin(seconds * 9 + index) * 2.5).toFloat()

    /** The face-down cards a swipe turns over, in the swipe's direction. */
    fun inSwipeOrder(faceDown: List<Int>, rightward: Boolean): List<Int> =
        if (rightward) faceDown.sorted() else faceDown.sortedDescending()

    /** A pair, then trips: each better hand pops a little higher. Four of a kind gets the fanfare. */
    fun celebration(before: HandValue.Category?, after: HandValue.Category?): HandCelebration? {
        if (after == null || after < HandValue.Category.OnePair || after <= (before ?: HandValue.Category.HighCard)) return null
        return when (after) {
            HandValue.Category.FourOfAKind -> HandCelebration(Sound.Quads, 1f, Haptic.Success)
            HandValue.Category.ThreeOfAKind -> HandCelebration(Sound.Match, 1.26f, Haptic.Medium)
            HandValue.Category.TwoPairs -> HandCelebration(Sound.Match, 1.12f, Haptic.Medium)
            else -> HandCelebration(Sound.Match, 1f, Haptic.Medium)
        }
    }
}
