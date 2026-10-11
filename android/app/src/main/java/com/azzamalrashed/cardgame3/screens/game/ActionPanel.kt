package com.azzamalrashed.cardgame3.screens.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azzamalrashed.cardgame3.R
import com.azzamalrashed.cardgame3.design.AppIcons
import com.azzamalrashed.cardgame3.design.AsyncPillButton
import com.azzamalrashed.cardgame3.design.BlocksWhileBusy
import com.azzamalrashed.cardgame3.design.Blob
import com.azzamalrashed.cardgame3.design.Haptic
import com.azzamalrashed.cardgame3.design.Mood
import com.azzamalrashed.cardgame3.design.OnChange
import com.azzamalrashed.cardgame3.design.PillButton
import com.azzamalrashed.cardgame3.design.Theme
import com.azzamalrashed.cardgame3.design.formatted
import com.azzamalrashed.cardgame3.design.periodicTime
import com.azzamalrashed.cardgame3.design.rememberHaptics
import com.azzamalrashed.cardgame3.design.softShadow
import com.azzamalrashed.cardgame3.design.swiftSpring
import com.azzamalrashed.cardgame3.model.Betting
import com.azzamalrashed.cardgame3.model.PublicRound
import com.azzamalrashed.cardgame3.model.Room
import com.azzamalrashed.cardgame3.services.Backend
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** What I can do now: bet, offer a deal, answer offers, reveal, skip, next round. (iOS: ActionPanel.swift) */
@Composable
fun ActionPanel(backend: Backend, room: Room, round: PublicRound, modifier: Modifier = Modifier) {
    val me = backend.uid ?: ""
    val mode = panelMode(room, round, me)
    val haptics = rememberHaptics()

    // The boss feels each new offer arrive.
    OnChange(round.offers) { old, new -> if (round.bossId == me && new.size > old.size) haptics.play(Haptic.Medium) }
    OnChange(mode == PanelMode.BotPlaying) { _, now -> if (now) haptics.play(Haptic.Warning) }

    // What each part showed last, so a part that's animating out keeps showing it (as SwiftUI's removed views do).
    val live = PanelState(room, round, me)
    val shown = remember { HashMap<PanelMode, PanelState>() }
    shown[mode] = live
    // The amount carries over from betting to offering, like iOS's one `amount`.
    val memory = remember { AmountMemory() }

    // The panel's backdrop runs down behind the gesture bar, to the bottom of the screen.
    val bottomInset = WindowInsets.safeDrawing.getBottom(LocalDensity.current)
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val corner = CornerRadius(32.dp.toPx())
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = 0f,
                            top = 0f,
                            right = size.width,
                            bottom = size.height + bottomInset,
                            topLeftCornerRadius = corner,
                            topRightCornerRadius = corner,
                        ),
                    )
                }
                drawPath(path, Color.White.copy(alpha = 0.25f))
            }
            .padding(20.dp),
    ) {
        BlocksWhileBusy(Modifier.fillMaxWidth()) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    // A bot taking the seat comes up from the bottom; everything else cross-fades.
                    val enter = if (targetState == PanelMode.BotPlaying) {
                        slideInVertically(panelMove) { it } + fadeIn(panelFade)
                    } else {
                        fadeIn(panelFade)
                    }
                    val exit = if (initialState == PanelMode.BotPlaying) {
                        slideOutVertically(panelMove) { it } + fadeOut(panelFade)
                    } else {
                        fadeOut(panelFade)
                    }
                    (enter togetherWith exit).using(SizeTransform(clip = false) { _, _ -> panelSize })
                },
                contentAlignment = Alignment.BottomCenter,
                label = "actions",
            ) { part ->
                val state = if (part == mode) live else shown[part] ?: live
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PanelContent(part, state, backend, memory)
                }
            }
        }
    }
}

/** The round as one part of the panel saw it. */
private data class PanelState(val room: Room, val round: PublicRound, val me: String) {
    val myPoints: Int get() = room.points(me)
}

/** The amount last shown in a picker. */
private class AmountMemory {
    var value = Betting.MIN_BET
}

// The panel's animation (iOS: `.animation(.spring(duration: 0.4, bounce: 0.3), value: round)`).
private val panelMove = swiftSpring(0.4, 0.3, IntOffset.VisibilityThreshold)
private val panelSize = swiftSpring(0.4, 0.3, IntSize.VisibilityThreshold)
private val panelFade = swiftSpring<Float>(0.4)

/** Which controls the panel shows. */
internal enum class PanelMode {
    /** A bot is playing my seat (I stepped away, or my turn ran out). */
    BotPlaying,

    // Betting
    YourTurn,
    BetPlaced,
    SatOut,
    TurnComing,

    // Deals
    Boss,
    DealLocked,
    Offer,
    NoRoom,
    Watching,

    /** The showdown and the wait for the next round. */
    AfterRound,
}

internal fun panelMode(room: Room, round: PublicRound, me: String): PanelMode {
    if (room.isAway(me) && room.isStillIn(me)) return PanelMode.BotPlaying
    return when (round.phase) {
        PublicRound.Phase.Betting -> when {
            round.turnId == me -> PanelMode.YourTurn
            round.bets[me] != null -> PanelMode.BetPlaced
            me in round.withdrawn -> PanelMode.SatOut
            else -> PanelMode.TurnComing
        }
        PublicRound.Phase.Deals -> when {
            round.bossId == me -> PanelMode.Boss
            round.deals[me] != null -> PanelMode.DealLocked
            round.bets[me] != null -> if (round.dealRoom >= Betting.STEP) PanelMode.Offer else PanelMode.NoRoom
            else -> PanelMode.Watching
        }
        PublicRound.Phase.Finished -> PanelMode.AfterRound
    }
}

/** The numbers behind the amount pickers and the next-round countdown. */
internal object ActionMath {
    /** Smallest bet that beats the highest one: the next step of 500 above it (it may be an odd all-in amount). */
    fun minBet(highestBet: Int): Int = max(Betting.MIN_BET, (highestBet / Betting.STEP + 1) * Betting.STEP)

    /** Down to a whole step of 500, but never below one step. */
    fun roundDown(n: Int): Int = max(Betting.STEP, n / Betting.STEP * Betting.STEP)

    /** One step less, not below the range. */
    fun less(amount: Int, range: IntRange): Int = max(range.first, amount - Betting.STEP)

    /** One step more, not above the range. */
    fun more(amount: Int, range: IntRange): Int = min(range.last, amount + Betting.STEP)

    /** Whole seconds left until `at` (rounded up), never below zero. */
    fun secondsLeft(at: Long, now: Long): Int = max(0, ceil((at - now) / 1000.0).toInt())
}

@Composable
private fun PanelContent(mode: PanelMode, state: PanelState, backend: Backend, memory: AmountMemory) {
    val round = state.round
    val me = state.me
    when (mode) {
        PanelMode.BotPlaying -> BotPlaying(backend)
        PanelMode.YourTurn -> YourTurn(state, backend, memory)
        PanelMode.BetPlaced -> Waiting(
            stringResource(R.string.actions_in_with, (round.bets[me] ?: 0).formatted()),
            Icons.Filled.HourglassTop,
        )
        PanelMode.SatOut -> Waiting(stringResource(R.string.actions_sat_out), Icons.Filled.Bedtime)
        PanelMode.TurnComing -> Waiting(stringResource(R.string.actions_turn_coming), Icons.Filled.Visibility)
        PanelMode.Boss -> Boss(state, backend)
        PanelMode.DealLocked -> Waiting(
            stringResource(
                R.string.actions_deal_locked,
                (round.deals[me] ?: 0).formatted(),
                state.room.name(round.bossId ?: ""),
            ),
            Icons.Filled.Lock,
        )
        PanelMode.Offer -> Offer(state, backend, memory)
        PanelMode.NoRoom -> Waiting(stringResource(R.string.actions_no_room), Icons.Filled.Bolt)
        PanelMode.Watching -> Waiting(stringResource(R.string.actions_watching), Icons.Filled.Visibility)
        PanelMode.AfterRound -> AfterRound(round, backend)
    }
}

// Away

@Composable
private fun BotPlaying(backend: Backend) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.actions_bot_emoji), style = TextStyle(fontSize = 28.sp))
        Text(stringResource(R.string.actions_bot_playing), style = Theme.body(17f, FontWeight.Bold))
    }
    AsyncPillButton(stringResource(R.string.actions_take_seat_back), { backend.takeSeatBack() })
}

// After the round

/**
 * While the showdown plays: Skip (on this phone). Then the next round, which nobody can deal before everyone's
 * reveal has played.
 */
@Composable
private fun AfterRound(round: PublicRound, backend: Backend) {
    val now = periodicTime(250)
    val reveal = backend.revealClock
    if (reveal != null && now < reveal.verdict) {
        PillButton(stringResource(R.string.actions_skip), { backend.skipReveal() }, primary = false)
    } else {
        AsyncPillButton(
            action = { backend.nextRound() },
            enabled = now >= (round.revealEndsAt ?: 0L),
        ) { NextRoundLabel(round.nextRoundAt, now) }
    }
}

/** "Next round · 6": counts down to the server dealing it; tapping deals it now. */
@Composable
private fun NextRoundLabel(at: Long?, now: Long) {
    if (at == null) {
        Text(stringResource(R.string.actions_next_round))
    } else {
        val left = ActionMath.secondsLeft(at, now)
        RollingText(
            if (left > 0) stringResource(R.string.actions_next_round_in, left.formatted()) else stringResource(R.string.actions_dealing),
            countsDown = true,
            spec = swiftSpring(0.5, 0.15, IntOffset.VisibilityThreshold),
        )
    }
}

// Betting

@Composable
private fun YourTurn(state: PanelState, backend: Backend, memory: AmountMemory) {
    val round = state.round
    val myPoints = state.myPoints
    val minBet = ActionMath.minBet(round.highestBet)
    // Starts at the smallest bet when the turn comes, and again whenever the highest bet changes.
    var amount by remember(round.highestBet) { mutableIntStateOf(minBet) }
    SideEffect { memory.value = amount }
    round.turnDeadline?.let { Countdown(it, size = 20f) }
    if (minBet <= myPoints) {
        AmountPicker(amount, { amount = it }, minBet..ActionMath.roundDown(myPoints))
        AsyncPillButton(stringResource(R.string.actions_bet, amount.formatted()), { backend.bet(amount) })
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AsyncPillButton(
            stringResource(R.string.actions_all_in, myPoints.formatted()),
            { backend.bet(myPoints) },
            Modifier.weight(1f),
            primary = false,
        )
        AsyncPillButton(stringResource(R.string.actions_withdraw), { backend.withdraw() }, Modifier.weight(1f), primary = false)
    }
}

// Deals

@Composable
private fun Boss(state: PanelState, backend: Backend) {
    val round = state.round
    val offers = round.offers
    AnimatedVisibility(
        offers.isEmpty(),
        enter = fadeIn(panelFade) + expandVertically(panelSize, clip = false),
        exit = fadeOut(panelFade) + shrinkVertically(panelSize, clip = false),
    ) {
        Waiting(stringResource(R.string.actions_boss_waiting), AppIcons.Crown)
    }
    // Every offer seen while this was shown, with its last amount: a row stays until it has slid out.
    val present = remember { offers.keys.toSet() }
    val known = remember { mutableStateMapOf<String, Int>().apply { putAll(offers) } }
    OnChange(offers) { _, new -> known.putAll(new) }
    val start = if (LocalLayoutDirection.current == LayoutDirection.Ltr) -1 else 1
    for ((uid, last) in known.toList().sortedBy { it.first }) {
        key(uid) {
            // Rows there from the start come with the panel; later ones slide in from the leading edge.
            val visible = remember { MutableTransitionState(uid in present) }
            visible.targetState = uid in offers
            AnimatedVisibility(
                visible,
                enter = expandVertically(panelSize, clip = false) +
                    slideInHorizontally(panelMove) { it * start } + fadeIn(panelFade),
                exit = shrinkVertically(panelSize, clip = false) +
                    slideOutHorizontally(panelMove) { it * start } + fadeOut(panelFade),
            ) {
                OfferRow(uid, offers[uid] ?: last, state, backend)
            }
        }
    }
    AsyncPillButton(stringResource(R.string.actions_reveal), { backend.reveal() })
}

@Composable
private fun OfferRow(uid: String, offer: Int, state: PanelState, backend: Backend) {
    val room = state.room
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            PlayerMenu(uid, room, backend) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Blob(Theme.color(room.seat(uid)), 36.dp, mood = Mood.Surprised)
                    Column {
                        Text(
                            room.name(uid),
                            style = Theme.body(15f, FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        RollingText(
                            stringResource(R.string.actions_asks, offer.formatted()),
                            Modifier.alpha(0.7f),
                            style = Theme.body(14f, FontWeight.ExtraBold),
                            spec = panelMove,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        AsyncPillButton(
            stringResource(R.string.actions_no),
            { backend.answerOffer(uid, accept = false) },
            Modifier.width(70.dp),
            primary = false,
        )
        AsyncPillButton(
            stringResource(R.string.actions_deal),
            { backend.answerOffer(uid, accept = true) },
            Modifier.width(84.dp),
            enabled = offer <= state.round.dealRoom,
        )
    }
}

@Composable
private fun Offer(state: PanelState, backend: Backend, memory: AmountMemory) {
    val round = state.round
    val most = ActionMath.roundDown(round.dealRoom)
    // Starts from the amount last shown (the bet), within what's left for deals.
    var amount by remember { mutableIntStateOf(memory.value.coerceIn(Betting.STEP, most)) }
    SideEffect { memory.value = amount }
    AmountPicker(amount, { amount = it }, Betting.STEP..most)
    val label = if (round.offers[state.me] == null) R.string.actions_offer else R.string.actions_change_offer
    AsyncPillButton(stringResource(label, amount.formatted()), { backend.makeOffer(amount) })
}

// Pieces

/** An icon that pulses over a line of text: the panel while there's nothing to do. */
@Composable
private fun Waiting(text: String, icon: ImageVector) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val opacity by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1400
                1f at 0
                0.3f at 550
                1f at 1100
            },
        ),
        label = "pulse",
    )
    Column(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp).graphicsLayer { alpha = opacity }, tint = Theme.ink)
        Text(text, style = Theme.body(16f, FontWeight.SemiBold), textAlign = TextAlign.Center)
    }
}

/** − amount + in steps of 500. */
@Composable
private fun AmountPicker(amount: Int, onChange: (Int) -> Unit, range: IntRange) {
    val haptics = rememberHaptics()
    OnChange(amount) { _, _ -> haptics.play(Haptic.Selection) }
    val current by rememberUpdatedState(amount)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(Icons.Filled.Remove, stringResource(R.string.actions_less), enabled = amount > range.first) {
            onChange(ActionMath.less(current, range))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            RollingText(
                amount.formatted(),
                style = with(Theme) { body(34f, FontWeight.Black).monospacedDigits() },
                spec = swiftSpring(0.5, 0.15, IntOffset.VisibilityThreshold),
            )
        }
        StepButton(Icons.Filled.Add, stringResource(R.string.actions_more), enabled = amount < range.last) {
            onChange(ActionMath.more(current, range))
        }
    }
}

/** How long a step button is held before it repeats, and how often it repeats then. */
private const val REPEAT_AFTER_MS = 500L
private const val REPEAT_EVERY_MS = 100L

/** A white circle button (like `CircleButton`, 56 wide) that keeps stepping while it's held. */
@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onStep: () -> Unit) {
    val size = 56.dp
    var pressed by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    OnChange(pressed) { _, down -> if (down) haptics.play(Haptic.Light) }
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, swiftSpring(0.25, 0.5), label = "press")
    // Dims at the end of the range with the picker's snappy animation.
    val opacity by animateFloatAsState(if (enabled) 1f else 0.4f, swiftSpring(0.5, 0.15), label = "enabled")
    val canStep by rememberUpdatedState(enabled)
    val step by rememberUpdatedState(onStep)
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = opacity.coerceIn(0f, 1f)
            }
            .softShadow(CircleShape, Color.Black.copy(alpha = 0.08f), 10.dp, 4.dp)
            .background(Color.White, CircleShape)
            .semantics {
                role = Role.Button
                contentDescription = description
                if (!enabled) disabled()
                onClick {
                    if (canStep) step()
                    canStep
                }
            }
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown()
                        if (!canStep) return@awaitEachGesture
                        pressed = true
                        var repeated = false
                        val repeater = launch {
                            delay(REPEAT_AFTER_MS)
                            while (canStep) {
                                repeated = true
                                step()
                                delay(REPEAT_EVERY_MS)
                            }
                        }
                        val up = waitForUpOrCancellation()
                        repeater.cancel()
                        pressed = false
                        if (up != null && !repeated) {
                            up.consume()
                            if (canStep) step()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.45f), tint = Theme.ink)
    }
}

/**
 * Text whose changed characters roll into place (SwiftUI's `.contentTransition(.numericText())`): new ones come up
 * from below, or down from above when `countsDown`. Characters keep their place counted from the end, so
 * "1,500" → "2,000" rolls the digits that changed.
 */
@Composable
private fun RollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    countsDown: Boolean = false,
    spec: FiniteAnimationSpec<IntOffset>,
) {
    val from = if (countsDown) -1 else 1
    // One character per cell, so they're laid out left to right in every language (English text and numbers), and
    // TalkBack reads the whole text rather than each character.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }) {
            text.forEachIndexed { index, char ->
                key(text.length - index) {
                    AnimatedContent(
                        targetState = char,
                        transitionSpec = {
                            (slideInVertically(spec) { it / 2 * from } + fadeIn(panelFade))
                                .togetherWith(slideOutVertically(spec) { -it / 2 * from } + fadeOut(panelFade))
                                .using(SizeTransform(clip = false))
                        },
                        label = "character",
                    ) { shown -> Text(shown.toString(), style = style) }
                }
            }
        }
    }
}
