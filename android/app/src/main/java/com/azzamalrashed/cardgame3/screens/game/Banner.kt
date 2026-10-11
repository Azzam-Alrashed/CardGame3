package com.azzamalrashed.cardgame3.screens.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azzamalrashed.cardgame3.CardGame3App
import com.azzamalrashed.cardgame3.R
import com.azzamalrashed.cardgame3.design.FlipCard
import com.azzamalrashed.cardgame3.design.Haptic
import com.azzamalrashed.cardgame3.design.OnChange
import com.azzamalrashed.cardgame3.design.Theme
import com.azzamalrashed.cardgame3.design.clockText
import com.azzamalrashed.cardgame3.design.formatted
import com.azzamalrashed.cardgame3.design.frameTime
import com.azzamalrashed.cardgame3.design.rememberHaptics
import com.azzamalrashed.cardgame3.design.swiftSpring
import com.azzamalrashed.cardgame3.model.HandValue
import com.azzamalrashed.cardgame3.model.PublicRound
import com.azzamalrashed.cardgame3.model.RevealClock
import com.azzamalrashed.cardgame3.model.RevealTimeline
import com.azzamalrashed.cardgame3.model.Room
import com.azzamalrashed.cardgame3.model.RoundResult
import com.azzamalrashed.cardgame3.services.Backend
import com.azzamalrashed.cardgame3.services.Sound
import com.azzamalrashed.cardgame3.services.SoundPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Ported from ios/CardGame3/Screens/Game/Banner.swift.

/** The banner's spring whenever the round changes (iOS: `.spring(duration: 0.45, bounce: 0.35)`). */
private fun <T> roundSpring(threshold: T? = null) = swiftSpring(0.45, 0.35, threshold)

/** The showdown's verdict (`.spring(duration: 0.5, bounce: 0.4)`). */
private fun <T> verdictSpring(threshold: T? = null) = swiftSpring(0.5, 0.4, threshold)

/** "Best so far" moving from row to row (`.spring(duration: 0.35, bounce: 0.4)`). */
private fun <T> leaderSpring(threshold: T? = null) = swiftSpring(0.35, 0.4, threshold)

/** `.snappy`. */
private fun <T> snappy(threshold: T? = null) = swiftSpring(0.5, 0.15, threshold)

/** U+FE0E: the plain (text) look of ♥ and ♦ in the callout, never the emoji one. */
private const val TEXT_STYLE = "\uFE0E"

/** Big headline for what is happening right now, and the staged showdown. (iOS: Banner.swift) */
@Composable
fun Banner(backend: Backend, room: Room, round: PublicRound, me: String?, reveal: RevealClock?, modifier: Modifier = Modifier) {
    // What each phase last showed: the phase on its way out keeps its look while it fades, as in SwiftUI.
    val lastShown = remember { HashMap<PublicRound.Phase, Pair<PublicRound, RevealClock?>>() }
    lastShown[round.phase] = round to reveal
    AnimatedContent(
        targetState = round.phase,
        modifier = modifier,
        transitionSpec = {
            (scaleIn(roundSpring(), initialScale = 0.85f) + fadeIn(roundSpring())) togetherWith
                (scaleOut(roundSpring(), targetScale = 0.85f) + fadeOut(roundSpring())) using
                SizeTransform(clip = false) { _, _ -> roundSpring(IntSize.VisibilityThreshold) }
        },
        contentAlignment = Alignment.Center,
        label = "banner",
    ) { phase ->
        val (shown, shownReveal) = if (phase == round.phase) round to reveal else lastShown[phase] ?: (round to reveal)
        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (phase) {
                PublicRound.Phase.Betting -> Betting(room, shown, me)
                PublicRound.Phase.Deals -> Deals(backend, room, shown, me)
                PublicRound.Phase.Finished -> ResultSummary(backend, room, shown, me, shownReveal)
            }
        }
    }
}

@Composable
private fun Betting(room: Room, round: PublicRound, me: String?) {
    // A new turn pushes in from the trailing edge.
    AnimatedContent(
        targetState = round.turnId,
        transitionSpec = {
            (slideIntoContainer(SlideDirection.Start, roundSpring(IntOffset.VisibilityThreshold)) + fadeIn(roundSpring())) togetherWith
                (slideOutOfContainer(SlideDirection.Start, roundSpring(IntOffset.VisibilityThreshold)) + fadeOut(roundSpring())) using
                SizeTransform(clip = false) { _, _ -> roundSpring(IntSize.VisibilityThreshold) }
        },
        contentAlignment = Alignment.Center,
        label = "turn",
    ) { turn ->
        FitText(
            if (turn == me) stringResource(R.string.results_your_turn) else stringResource(R.string.results_their_turn, room.name(turn ?: "")),
            Theme.wordmark(36f),
            minScale = 0.5f,
            textAlign = TextAlign.Center,
        )
    }
    FadingText(
        if (round.highestBet > 0) {
            stringResource(R.string.results_highest_bet, round.highestBet.formatted())
        } else {
            stringResource(R.string.results_no_bets)
        },
        Theme.body(16f, FontWeight.Medium),
        alpha = 0.7f,
    )
}

@Composable
private fun Deals(backend: Backend, room: Room, round: PublicRound, me: String?) {
    Text(stringResource(R.string.results_deal_time), style = Theme.wordmark(36f), textAlign = TextAlign.Center)
    round.deadline?.let { deadline ->
        Countdown(deadline, onZero = { CardGame3App.scope.launch { backend.timerRanOut() } })
    }
    val left = round.dealRoom.formatted()
    FadingText(
        if (round.bossId == me) {
            stringResource(R.string.results_you_are_boss, left)
        } else {
            stringResource(R.string.results_they_are_boss, room.name(round.bossId ?: ""), left)
        },
        Theme.body(15f, FontWeight.Medium),
        alpha = 0.7f,
    )
}

/** "1:32" in a pill that ticks every second and turns pink near the end; calls `onZero` once when time runs out. */
@Composable
fun Countdown(deadline: Long, modifier: Modifier = Modifier, size: Float = 44f, onZero: () -> Unit = {}) {
    var left by remember { mutableIntStateOf(CountdownClock.secondsLeft(deadline, System.currentTimeMillis())) }
    LaunchedEffect(deadline) {
        while (true) {
            val now = System.currentTimeMillis()
            left = CountdownClock.secondsLeft(deadline, now)
            if (left == 0) break
            delay(CountdownClock.untilNextSecond(deadline, now))
        }
    }
    val hurry = left <= 10
    val haptics = rememberHaptics()
    OnChange(left) { _, new ->
        if (new in 1..5) {
            haptics.play(Haptic.Light)
            SoundPlayer.play(Sound.Tick)
        }
    }
    // A little heartbeat each second near the end.
    val beat = remember { Animatable(1f) }
    OnChange(if (hurry) left else -1) { _, _ ->
        beat.animateTo(1.12f, swiftSpring(0.12))
        beat.animateTo(1f, swiftSpring(0.3))
    }
    // Once, also when the time had already run out when this was shown.
    val currentOnZero by rememberUpdatedState(onZero)
    var fired by remember { mutableStateOf(false) }
    LaunchedEffect(left == 0) {
        if (left == 0 && !fired) {
            fired = true
            currentOnZero()
        }
    }
    val pill by animateColorAsState(if (hurry) Theme.hotPink else Color.White, snappy(), label = "countdown pill")
    val ink by animateColorAsState(if (hurry) Color.White else Theme.ink, snappy(), label = "countdown text")
    val text = clockText(left)
    Box(
        modifier
            .graphicsLayer {
                scaleX = beat.value
                scaleY = beat.value
            }
            .background(pill, CircleShape)
            .padding(horizontal = (size / 2).dp, vertical = (size / 7).dp)
            .clearAndSetSemantics { contentDescription = text },
    ) {
        RollingText(text, with(Theme) { Theme.wordmark(size).monospacedDigits() }, ink, countsDown = true)
    }
}

/** The countdown's clock arithmetic. */
internal object CountdownClock {
    /** Whole seconds left, rounded up (so "0:00" shows only once time is up). */
    fun secondsLeft(deadline: Long, now: Long): Int = if (deadline <= now) 0 else ((deadline - now + 999) / 1000).toInt()

    /** Milliseconds until the seconds left next go down. */
    fun untilNextSecond(deadline: Long, now: Long): Long {
        val rest = (deadline - now) % 1000
        return if (rest <= 0) 1000 else rest
    }
}

private enum class Stage { Showdown, BossTurn, Verdict }

private enum class Tag { None, BestSoFar, Delta }

/**
 * The round's result. A showdown is staged: everyone's cards start face down, the challengers turn theirs over one
 * at a time, then the boss, and only then come the crown, the points and the headline. Tapping skips to the verdict
 * on this phone.
 */
@Composable
private fun ResultSummary(backend: Backend, room: Room, round: PublicRound, me: String?, reveal: RevealClock?) {
    // Redrawn 30 times a second while the showdown plays, then still. Without one, everything shows at once.
    val now = frameTime(reveal?.end, fps = 30)
    val t = reveal?.elapsed(now) ?: Double.POSITIVE_INFINITY
    val result = Showdown.result(round)
    // The showdown's order and timing; also used (fully played) when this phone didn't stage it.
    val timeline = reveal?.timeline ?: RevealTimeline.of(round, room.players.map { it.uid })
    val verdict = t >= (timeline?.verdictAt ?: 0.0)
    val leader = Showdown.bestSoFar(timeline, round, t)
    val stage = when {
        verdict -> Stage.Verdict
        t >= (timeline?.drumrollAt ?: 0.0) -> Stage.BossTurn
        else -> Stage.Showdown
    }
    Column(
        Modifier
            .then(if (reveal != null) Modifier.pointerInput(backend) { detectTapGestures(onTap = { backend.skipReveal() }) } else Modifier)
            // Keeps rows readable on tablets and leaves room for the winner's crown and lift.
            .padding(horizontal = 6.dp)
            .widthIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Headline(stage, room, round, me, result)
        for (uid in timeline?.order ?: result.revealed) {
            key(uid) { RevealRow(uid, room, round, me, result, timeline, t, verdict, leading = uid == leader) }
        }
        val others = Showdown.others(result)
        AnimatedVisibility(
            visible = verdict && others.isNotEmpty(),
            enter = fadeIn(verdictSpring()) + expandVertically(verdictSpring(IntSize.VisibilityThreshold), clip = false),
            exit = ExitTransition.None,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((uid, amount) in others) {
                    key(uid) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (uid == me) stringResource(R.string.results_you) else room.name(uid),
                                Modifier.weight(1f),
                                style = Theme.body(14f, FontWeight.Bold),
                            )
                            RollingDelta(amount)
                        }
                    }
                }
            }
        }
    }
}

/** "Showdown!", then "Sami reveals…" (pushed up from the bottom), then the verdict. */
@Composable
private fun Headline(stage: Stage, room: Room, round: PublicRound, me: String?, result: RoundResult) {
    AnimatedContent(
        targetState = stage,
        transitionSpec = {
            // The verdict's parts bring themselves in (see Verdict).
            val enter = if (targetState == Stage.Verdict) {
                EnterTransition.None
            } else {
                slideInVertically(verdictSpring(IntOffset.VisibilityThreshold)) { it } + fadeIn(verdictSpring())
            }
            enter togetherWith (slideOutVertically(verdictSpring(IntOffset.VisibilityThreshold)) { -it } + fadeOut(verdictSpring())) using
                SizeTransform(clip = false) { _, _ -> verdictSpring(IntSize.VisibilityThreshold) }
        },
        contentAlignment = Alignment.TopCenter,
        label = "showdown headline",
    ) { shown ->
        when (shown) {
            Stage.Verdict -> Verdict(room, round, me, result)
            Stage.BossTurn -> Text(
                if (round.bossId == me) {
                    stringResource(R.string.results_you_reveal)
                } else {
                    stringResource(R.string.results_they_reveal, room.name(round.bossId ?: ""))
                },
                style = Theme.wordmark(32f),
                textAlign = TextAlign.Center,
            )
            Stage.Showdown -> Text(stringResource(R.string.results_showdown), style = Theme.wordmark(32f), textAlign = TextAlign.Center)
        }
    }
}

/** The headline, the moment people argue about, and what the boss paid in deals. */
@Composable
private fun AnimatedVisibilityScope.Verdict(room: Room, round: PublicRound, me: String?, result: RoundResult) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val headline = if (result.outcome == RoundResult.Outcome.Redeal) {
            stringResource(R.string.results_redeal)
        } else {
            val amount = Showdown.winAmount(round).formatted()
            if (result.winnerId == me) {
                stringResource(R.string.results_you_win_amount, amount)
            } else {
                stringResource(R.string.results_they_win_amount, room.name(result.winnerId ?: ""), amount)
            }
        }
        Text(
            headline,
            Modifier.animateEnterExit(enter = scaleIn(verdictSpring(), initialScale = 0.6f) + fadeIn(verdictSpring()), exit = ExitTransition.None),
            style = Theme.wordmark(32f),
            textAlign = TextAlign.Center,
        )
        calloutText(round)?.let { callout ->
            Text(
                callout,
                Modifier
                    .animateEnterExit(enter = scaleIn(verdictSpring(), initialScale = 0.4f) + fadeIn(verdictSpring()), exit = ExitTransition.None)
                    .background(Theme.hotPink, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                style = Theme.body(15f, FontWeight.ExtraBold),
                color = Color.White,
            )
        }
        Showdown.dealsPaid(round)?.let { paid ->
            Text(
                stringResource(R.string.results_paid_in_deals, paid.formatted()),
                Modifier.animateEnterExit(enter = fadeIn(verdictSpring()), exit = ExitTransition.None),
                style = Theme.body(16f),
                color = Theme.ink.copy(alpha = 0.75f),
            )
        }
    }
}

/** "The boss falls!", "Won on the suit! ♠ beats ♥", or both. */
@Composable
private fun calloutText(round: PublicRound): String? {
    val callout = Showdown.callout(round) ?: return null
    val bossFalls = if (callout.bossFalls) stringResource(R.string.results_boss_falls) else null
    val close = when (val close = callout.close) {
        Showdown.Close.Kicker -> stringResource(R.string.results_won_on_kicker)
        is Showdown.Close.Suit -> stringResource(R.string.results_won_on_suit, close.winner + TEXT_STYLE, close.runnerUp + TEXT_STYLE)
        null -> null
    }
    return listOfNotNull(bossFalls, close).joinToString(" ")
}

/** One revealing player: name, the four cards turning over, then "Best so far" or their points. */
@Composable
private fun RevealRow(
    uid: String,
    room: Room,
    round: PublicRound,
    me: String?,
    result: RoundResult,
    timeline: RevealTimeline?,
    t: Double,
    verdict: Boolean,
    leading: Boolean,
) {
    val cards = round.revealedHands[uid].orEmpty()
    val shown = t >= (timeline?.shownTime(uid) ?: 0.0)
    val won = verdict && uid == result.winnerId
    val lost = verdict && uid != result.winnerId
    val fill by animateFloatAsState(if (won) 0.9f else 0.4f, verdictSpring(), label = "row fill")
    val scale by animateFloatAsState(if (won) 1.03f else 1f, verdictSpring(), label = "row scale")
    val opacity by animateFloatAsState(if (lost) 0.6f else 1f, verdictSpring(), label = "row opacity")
    val drop by animateFloatAsState(if (lost) 3f else 0f, verdictSpring(), label = "row drop")
    val crownDrop = with(LocalDensity.current) { 60.dp.roundToPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = opacity.coerceIn(0f, 1f)
                translationY = drop.dp.toPx()
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = fill.coerceIn(0f, 1f)), RoundedCornerShape(16.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(76.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                FitText(if (uid == me) stringResource(R.string.results_you) else room.name(uid), Theme.body(14f, FontWeight.Bold), minScale = 0.6f)
                val hand = when {
                    shown -> HandValue.of(cards)?.name ?: ""
                    uid == round.bossId -> stringResource(R.string.results_boss)
                    else -> " "
                }
                AnimatedContent(
                    targetState = hand,
                    transitionSpec = {
                        fadeIn(leaderSpring()) togetherWith fadeOut(leaderSpring()) using
                            SizeTransform(clip = false) { _, _ -> leaderSpring(IntSize.VisibilityThreshold) }
                    },
                    contentAlignment = Alignment.CenterStart,
                    label = "hand name",
                ) { name ->
                    FitText(name, Theme.body(11f), minScale = 0.6f, color = Theme.ink.copy(alpha = 0.7f))
                }
            }
            cards.forEachIndexed { i, card ->
                key(card) {
                    FlipCard(card, faceUp = t >= (timeline?.flipTime(uid, i) ?: 0.0), width = 34.dp, animation = swiftSpring(0.35, 0.3))
                }
            }
            Spacer(Modifier.weight(1f))
            AnimatedContent(
                targetState = when {
                    verdict -> Tag.Delta
                    leading -> Tag.BestSoFar
                    else -> Tag.None
                },
                transitionSpec = {
                    val verdictChange = targetState == Tag.Delta || initialState == Tag.Delta
                    val scaleSpec = if (verdictChange) verdictSpring<Float>() else leaderSpring()
                    val sizeSpec = if (verdictChange) verdictSpring(IntSize.VisibilityThreshold) else leaderSpring(IntSize.VisibilityThreshold)
                    (scaleIn(scaleSpec, initialScale = 0f) + fadeIn(scaleSpec)) togetherWith
                        (scaleOut(scaleSpec, targetScale = 0f) + fadeOut(scaleSpec)) using SizeTransform(clip = false) { _, _ -> sizeSpec }
                },
                contentAlignment = Alignment.CenterEnd,
                label = "row tag",
            ) { tag ->
                when (tag) {
                    Tag.Delta -> RollingDelta(result.deltas[uid] ?: 0)
                    Tag.BestSoFar -> Text(
                        stringResource(R.string.results_best_so_far),
                        Modifier
                            .background(Theme.ink, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        style = Theme.body(11f, FontWeight.ExtraBold),
                        color = Color.White,
                    )
                    Tag.None -> Unit
                }
            }
        }
        // The winner's crown drops in on the corner.
        AnimatedVisibility(
            visible = won,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-6).dp, y = (-12).dp),
            enter = slideInVertically(verdictSpring(IntOffset.VisibilityThreshold)) { -crownDrop } + fadeIn(verdictSpring()),
            exit = slideOutVertically(verdictSpring(IntOffset.VisibilityThreshold)) { -crownDrop } + fadeOut(verdictSpring()),
        ) {
            Text("👑", Modifier.rotate(-20f), style = TextStyle(fontSize = 22.sp))
        }
    }
}

/** "+1,500" / "-500" / "0" in a pill, counting up from zero when it appears. */
@Composable
private fun RollingDelta(amount: Int) {
    val value = remember { Animatable(0f) }
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(amount) {
        if (!appeared) {
            appeared = true
            delay(500)
            value.animateTo(amount.toFloat(), swiftSpring(0.8, visibilityThreshold = 0.5f))
        } else {
            value.animateTo(amount.toFloat(), swiftSpring(0.5, visibilityThreshold = 0.5f))
        }
    }
    val shown = value.value.roundToInt()
    Text(
        if (shown > 0) stringResource(R.string.results_gain, shown.formatted()) else shown.formatted(),
        Modifier
            .background(if (amount > 0) Theme.ink else Color.White, CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = with(Theme) { Theme.body(14f, FontWeight.ExtraBold).monospacedDigits() },
        color = when {
            amount > 0 -> Color.White
            amount < 0 -> Theme.hotPink
            else -> Theme.ink
        },
    )
}

/** One line of text that shrinks to fit, down to `minScale` of its size (`.lineLimit(1).minimumScaleFactor`). */
@Composable
private fun FitText(
    text: String,
    style: TextStyle,
    minScale: Float,
    modifier: Modifier = Modifier,
    color: Color = Theme.ink,
    textAlign: TextAlign = TextAlign.Start,
) {
    BasicText(
        text,
        modifier,
        style = style.copy(color = color, textAlign = textAlign),
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = style.fontSize * minScale, maxFontSize = style.fontSize),
    )
}

/** Centered text that cross-fades when it changes, with the round's spring. */
@Composable
private fun FadingText(text: String, style: TextStyle, alpha: Float, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            fadeIn(roundSpring()) togetherWith fadeOut(roundSpring()) using
                SizeTransform(clip = false) { _, _ -> roundSpring(IntSize.VisibilityThreshold) }
        },
        contentAlignment = Alignment.Center,
        label = "banner text",
    ) { shown ->
        Text(shown, style = style, color = Theme.ink.copy(alpha = alpha), textAlign = TextAlign.Center)
    }
}

/**
 * A number whose changed characters roll in and out (SwiftUI's `.contentTransition(.numericText())`). With
 * `countsDown`, new digits drop in from above.
 */
@Composable
private fun RollingText(text: String, style: TextStyle, color: Color, countsDown: Boolean) {
    // Numbers read left to right in every language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row {
            text.forEachIndexed { i, char ->
                // Counted from the right, so "10:00" → "9:59" keeps the seconds in place.
                key(text.length - i) {
                    AnimatedContent(
                        targetState = char,
                        transitionSpec = {
                            val from = if (countsDown) -1 else 1
                            (slideInVertically(snappy(IntOffset.VisibilityThreshold)) { from * it / 2 } + fadeIn(snappy())) togetherWith
                                (slideOutVertically(snappy(IntOffset.VisibilityThreshold)) { -from * it / 2 } + fadeOut(snappy())) using
                                SizeTransform(clip = false) { _, _ -> snappy(IntSize.VisibilityThreshold) }
                        },
                        contentAlignment = Alignment.Center,
                        label = "digit",
                    ) { shown ->
                        Text(shown.toString(), style = style, color = color)
                    }
                }
            }
        }
    }
}

/** The result's rules, as iOS's `ResultSummary` works them out. */
internal object Showdown {
    /** The round's result; a redeal nobody revealed in, if it has none. */
    fun result(round: PublicRound): RoundResult =
        round.result ?: RoundResult(RoundResult.Outcome.Redeal, winnerId = null, revealed = emptyList(), deltas = emptyMap())

    /** The strongest challenger shown so far at `t`, while there's more than one to compare. */
    fun bestSoFar(timeline: RevealTimeline?, round: PublicRound, t: Double): String? {
        if (timeline == null || t >= timeline.verdictAt) return null
        val hands = timeline.challengers
            .filter { t >= timeline.shownTime(it) }
            .mapNotNull { uid -> HandValue.of(round.revealedHands[uid].orEmpty())?.let { uid to it } }
        if (hands.size <= 1) return null
        return strongest(hands) { it.second }?.first
    }

    /** What the winner won before paying deals: the boss's bet (or the lone entrant's bet). */
    fun winAmount(round: PublicRound): Int {
        val result = result(round)
        val winner = result.winnerId ?: return 0
        round.bossId?.let { boss -> return round.bets[boss] ?: 0 }
        return round.bets[winner] ?: result.deltas[winner] ?: 0
    }

    /** Deals the winning boss paid out, if any. */
    fun dealsPaid(round: PublicRound): Int? {
        val winner = result(round).winnerId ?: return null
        if (winner != round.bossId) return null
        val total = round.deals.values.sum()
        return if (total > 0) total else null
    }

    /** The moments people argue about: the boss losing, and a win on the kicker or the suit. */
    fun callout(round: PublicRound): Callout? {
        val result = result(round)
        if (result.outcome != RoundResult.Outcome.Showdown) return null
        val winner = result.winnerId ?: return null
        val best = HandValue.of(round.revealedHands[winner].orEmpty()) ?: return null
        val rivals = result.revealed.filter { it != winner }.mapNotNull { HandValue.of(round.revealedHands[it].orEmpty()) }
        val runnerUp = strongest(rivals) { it } ?: return null
        val close = when (best.decider(runnerUp)) {
            HandValue.Decider.Kicker -> Close.Kicker
            HandValue.Decider.Suit ->
                Close.Suit(HandValue.suitSymbol[best.topSuit] ?: "", HandValue.suitSymbol[runnerUp.topSuit] ?: "")
            else -> null
        }
        val bossFalls = winner != round.bossId
        return if (bossFalls || close != null) Callout(bossFalls, close) else null
    }

    /** Points won or lost by players who didn't reveal (folded, or took a deal), by uid. */
    fun others(result: RoundResult): List<Pair<String, Int>> =
        result.deltas.filterKeys { it !in result.revealed }.toList().sortedBy { it.first }

    /** The strongest hand; the first of equals, like Swift's `max(by:)`. */
    private fun <T> strongest(items: List<T>, hand: (T) -> HandValue): T? =
        items.fold(null as T?) { best, item -> if (best == null || hand(item).beats(hand(best))) item else best }

    data class Callout(val bossFalls: Boolean, val close: Close?)

    /** How close the winner's hand was to the runner-up's. */
    sealed interface Close {
        data object Kicker : Close

        /** Won on the top card's suit: the two suit symbols. */
        data class Suit(val winner: String, val runnerUp: String) : Close
    }
}
