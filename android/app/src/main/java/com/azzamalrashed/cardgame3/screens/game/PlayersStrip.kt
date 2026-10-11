package com.azzamalrashed.cardgame3.screens.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azzamalrashed.cardgame3.R
import com.azzamalrashed.cardgame3.design.Blob
import com.azzamalrashed.cardgame3.design.CardBack
import com.azzamalrashed.cardgame3.design.FlipCard
import com.azzamalrashed.cardgame3.design.Mood
import com.azzamalrashed.cardgame3.design.Theme
import com.azzamalrashed.cardgame3.design.formatted
import com.azzamalrashed.cardgame3.design.frameTime
import com.azzamalrashed.cardgame3.design.periodicTime
import com.azzamalrashed.cardgame3.design.short
import com.azzamalrashed.cardgame3.design.swiftSpring
import com.azzamalrashed.cardgame3.model.Betting
import com.azzamalrashed.cardgame3.model.Card
import com.azzamalrashed.cardgame3.model.DealClock
import com.azzamalrashed.cardgame3.model.DealTimeline
import com.azzamalrashed.cardgame3.model.PublicRound
import com.azzamalrashed.cardgame3.model.RevealClock
import com.azzamalrashed.cardgame3.model.Room
import com.azzamalrashed.cardgame3.services.Backend
import kotlinx.coroutines.flow.first
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Everyone at the table, in a row that scrolls to whoever is acting. (iOS: PlayersStrip.swift) */
@Composable
fun PlayersStrip(
    backend: Backend,
    room: Room,
    round: PublicRound,
    me: String?,
    /** The deal this phone is animating, if any. */
    clock: DealClock?,
    /** How many of my cards I've turned over (shown before the server has it). */
    myFlips: Int,
    /** The showdown this phone is staging, if any. */
    reveal: RevealClock?,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    // Where each chip and the visible part of the strip are, to scroll to whoever is acting.
    val chips = remember { mutableStateMapOf<String, LayoutCoordinates>() }
    var viewport by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Not lazy: the deal needs every player's position, on screen or not.
    Row(
        modifier
            .fillMaxWidth()
            .dealSpot(DealSpot.Strip)
            .onPlaced { viewport = it }
            .horizontalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        for (player in room.players) {
            key(player.uid) {
                PlayerMenu(
                    player.uid, room, backend,
                    Modifier.onPlaced { if (chips[player.uid] !== it) chips[player.uid] = it },
                ) {
                    PlayerChip(room, round, player.uid, player.uid == me, clock, myFlips, reveal)
                }
            }
        }
    }

    // Keep whoever is acting in view on a crowded table (also when first shown).
    val focus = round.turnId ?: round.bossId
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    LaunchedEffect(focus) {
        val uid = focus ?: return@LaunchedEffect
        val (chip, view) = snapshotFlow { chips[uid] to viewport }
            .first { (chip, view) -> chip?.isAttached == true && view?.isAttached == true }
        if (chip == null || view == null) return@LaunchedEffect
        // How far the chip's center is from the strip's (px, left to right).
        val off = view.localPositionOf(chip, Offset.Zero).x + chip.size.width / 2f - view.size.width / 2f
        // Right to left, the strip scrolls the other way.
        val target = (scroll.value + if (rtl) -off else off).roundToInt().coerceIn(0, scroll.maxValue)
        scroll.animateScrollTo(target, swiftSpring(0.5))
    }
}

@Composable
private fun PlayerChip(
    room: Room,
    round: PublicRound,
    uid: String,
    isMe: Boolean,
    clock: DealClock?,
    myFlips: Int,
    reveal: RevealClock?,
) {
    val out = !room.isStillIn(uid)
    // Frames while cards land here, bots look at theirs, and the showdown plays.
    val now = frameTime(listOfNotNull(clock?.peeksEnd, reveal?.end).maxOrNull(), fps = 30)
    // Points move at the showdown's verdict, not before.
    val settled = reveal?.let { now >= it.verdict } ?: true
    val name = when {
        isMe -> stringResource(R.string.table_you)
        room.isAway(uid) -> stringResource(R.string.table_away, room.name(uid))
        else -> room.name(uid)
    }
    Column(
        Modifier
            .width(72.dp)
            .alpha(if (out) 0.35f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Avatar(room, round, uid, isMe, clock, myFlips, reveal, now)
        Text(
            name,
            style = Theme.body(13f, FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = (13 * 0.7).sp, maxFontSize = 13.sp),
        )
        TableNumericText(
            if (out) stringResource(R.string.table_out) else room.points(uid, settled).formatted(),
            Theme.body(12f, FontWeight.SemiBold),
            Modifier.alpha(0.7f),
            duration = 0.8,
        )
        StatusTag(chipStatus(round, uid))
    }
}

@Composable
private fun Avatar(
    room: Room,
    round: PublicRound,
    uid: String,
    isMe: Boolean,
    clock: DealClock?,
    myFlips: Int,
    reveal: RevealClock?,
    now: Long,
) {
    val isTurn = round.turnId == uid
    val dealing = clock?.let { now < it.end } ?: false
    val verdict = round.phase == PublicRound.Phase.Finished && (reveal?.let { now >= it.verdict } ?: true)
    // Looking left means looking toward earlier seats, which sit on the right in a right-to-left layout.
    val mirror = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val look by animateOffsetAsState(
        if (dealing) chipLook(room, round, uid).let { Offset(it.x * mirror, it.y) } else Offset.Zero,
        tween(300, easing = FastOutSlowInEasing),
        label = "look",
    )
    val scale by animateFloatAsState(if (isTurn) 1.08f else 1f, swiftSpring(0.4, 0.4), label = "turn")
    val deadline = round.turnDeadline
    // A turn with a time limit shows its clock ring instead of the plain white border.
    val border by animateDpAsState(if (isTurn && deadline == null) 4.dp else 0.dp, swiftSpring(0.4, 0.4), label = "turn border")
    val density = LocalDensity.current

    Box(Modifier.size(56.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .dealSpot(if (round.dealerId == uid) DealSpot.Dealer else null)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            AnimatedVisibility(
                visible = isTurn,
                modifier = Modifier.matchParentSize(),
                enter = fadeIn(swiftSpring(0.4)),
                exit = fadeOut(swiftSpring(0.4)),
                label = "turn pulse",
            ) {
                TurnPulse(Modifier.fillMaxSize())
            }
            Blob(
                color = Theme.color(forSeat = room.seat(uid)),
                size = 56.dp,
                mood = chipMood(round, uid, verdict),
                look = look,
                hair = round.bossId == uid,
            )
            if (isTurn && deadline != null) {
                TurnClockRing(deadline, Modifier.matchParentSize())
            } else if (border > 0.dp) {
                Canvas(Modifier.matchParentSize()) {
                    val width = border.toPx()
                    drawCircle(Color.White, radius = size.minDimension / 2 - width / 2, style = Stroke(width))
                }
            }
        }
        val slide = with(density) { 14.dp.roundToPx() }
        AnimatedVisibility(
            visible = room.isStillIn(uid) && uid !in round.withdrawn,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 14.dp, y = (-2).dp),
            enter = slideInVertically(swiftSpring(0.4, 0.4)) { slide } + fadeIn(swiftSpring(0.4)),
            exit = slideOutVertically(swiftSpring(0.4)) { slide } + fadeOut(swiftSpring(0.4)),
            label = "mini cards",
        ) {
            MiniCards(
                landed = clock?.landedCount(uid, now) ?: DealTimeline.HAND_SIZE,
                peeked = chipPeeked(room, round, uid, isMe, clock, myFlips, now),
                faces = round.revealedHands[uid].orEmpty(),
                faceUp = chipFaceUp(round, uid, reveal, now),
                modifier = Modifier.dealSpot(DealSpot.Seat(uid)),
            )
        }
        if (room.isAway(uid) || room.isAI(uid)) {
            ChipBadge(
                stringResource(R.string.table_bot_badge),
                size = 26.dp,
                style = TextStyle(fontSize = with(density) { 16.dp.toSp() }),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-36).dp, y = 34.dp),
            )
        }
        if (round.dealerId == uid) {
            ChipBadge(
                stringResource(R.string.table_dealer_badge),
                size = 22.dp,
                style = Theme.body(12f, FontWeight.Black).copy(fontSize = with(density) { 12.dp.toSp() }),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp),
            )
        }
    }
}

/** A white circle with a symbol, pinned to a blob. Fixed size, like the blob it sits on. */
@Composable
private fun ChipBadge(text: String, size: Dp, style: TextStyle, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, maxLines = 1, softWrap = false)
    }
}

/** What a player has going in this round, under their name. */
internal data class ChipStatus(val kind: Kind, val amount: Int = 0) {
    enum class Kind { Boss, Deal, Asks, Bet, Out, None }
}

internal fun chipStatus(round: PublicRound, uid: String): ChipStatus {
    val deal = round.deals[uid]
    val offer = round.offers[uid]
    val bet = round.bets[uid]
    return when {
        round.bossId == uid -> ChipStatus(ChipStatus.Kind.Boss, bet ?: 0)
        deal != null -> ChipStatus(ChipStatus.Kind.Deal, deal)
        offer != null -> ChipStatus(ChipStatus.Kind.Asks, offer)
        bet != null -> ChipStatus(ChipStatus.Kind.Bet, bet)
        uid in round.withdrawn -> ChipStatus(ChipStatus.Kind.Out)
        else -> ChipStatus(ChipStatus.Kind.None)
    }
}

/** "Boss 3.5K", "Bet 2K", … popping in and out as it changes (amounts roll). */
@Composable
private fun StatusTag(status: ChipStatus) {
    AnimatedContent(
        targetState = status,
        contentKey = { it.kind },
        contentAlignment = Alignment.Center,
        transitionSpec = {
            (scaleIn(swiftSpring(0.4, 0.4), initialScale = 0f) + fadeIn(swiftSpring(0.4))) togetherWith
                (scaleOut(swiftSpring(0.4), targetScale = 0f) + fadeOut(swiftSpring(0.4))) using
                SizeTransform(clip = false)
        },
        label = "status",
    ) { s ->
        val amount = short(s.amount)
        when (s.kind) {
            ChipStatus.Kind.Boss -> Tag(stringResource(R.string.table_boss, TABLE_SLOT), amount, dark = true)
            ChipStatus.Kind.Deal -> Tag(stringResource(R.string.table_deal, TABLE_SLOT), amount)
            ChipStatus.Kind.Asks -> Tag(stringResource(R.string.table_asks, TABLE_SLOT), amount)
            ChipStatus.Kind.Bet -> Tag(stringResource(R.string.table_bet, TABLE_SLOT), amount)
            ChipStatus.Kind.Out -> Tag(stringResource(R.string.table_out), "", modifier = Modifier.alpha(0.6f))
            // Keeps the chips the same height whatever their status.
            ChipStatus.Kind.None -> Tag(" ", "", modifier = Modifier.alpha(0f).clearAndSetSemantics {})
        }
    }
}

@Composable
private fun Tag(template: String, value: String, modifier: Modifier = Modifier, dark: Boolean = false) {
    val foreground = if (dark) Color.White else Theme.ink
    Box(
        modifier
            .background(if (dark) Theme.ink else Color.White, CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        // Shrinks a little so "Boss 3.5K" fits the chip.
        TableTemplateText(
            template, value,
            Theme.body(11f, FontWeight.ExtraBold),
            Modifier.shrinkToFit(0.7f),
            color = foreground,
            duration = 0.4,
            bounce = 0.4,
        )
    }
}

/**
 * Four small face-down cards in front of a player. The ones they've looked at stand up a little: a tell
 * everyone can read ("he bet 2,000 without even looking!"). Card 0 is on the left, where the deal lands it.
 */
@Composable
private fun MiniCards(landed: Int, peeked: Int, faces: List<Card>, faceUp: Int, modifier: Modifier = Modifier) {
    val width = TableLayout.miniWidth
    // 5 dp apart, overlapping.
    val step = 5.dp
    val description = if (peeked == 0) {
        stringResource(R.string.table_not_looked)
    } else {
        stringResource(R.string.table_looked, peeked.formatted())
    }
    Box(
        modifier
            .size(width + step * (DealTimeline.HAND_SIZE - 1), width * 1.4f)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        for (i in 0 until DealTimeline.HAND_SIZE) {
            // Unseen cards lie flat on the table; a card that's been looked at stands up.
            val standing = i < peeked || i < faceUp
            val stand by animateFloatAsState(
                if (standing) 1f else 0f,
                if (i < faceUp) swiftSpring(0.35, 0.3) else swiftSpring(0.3, 0.55),
                label = "stand",
            )
            Box(
                Modifier
                    .absoluteOffset(x = step * i)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        scaleY = 0.6f + 0.4f * stand
                        rotationZ = TableLayout.miniAngle(i) - 8f * stand
                        translationY = -7.dp.toPx() * stand
                        alpha = if (i < landed) 1f else 0f
                    },
            ) {
                if (i < faces.size) {
                    FlipCard(faces[i], faceUp = i < faceUp, width = width, animation = swiftSpring(0.35, 0.3))
                } else {
                    CardBack(width = width)
                }
            }
        }
    }
}

/** The turn's time left, as a ring that empties clockwise and turns pink for the last 10 seconds. */
@Composable
private fun TurnClockRing(deadline: Long, modifier: Modifier = Modifier) {
    val now = periodicTime(100)
    val left = max(0.0, (deadline - now) / 1000.0)
    val fraction = min(1.0, left / Betting.TURN_SECONDS).toFloat()
    val color = if (left <= 10) Theme.hotPink else Color.White
    Canvas(modifier.padding(2.dp)) {
        drawArc(
            color,
            startAngle = -90f,
            sweepAngle = 360f * fraction,
            useCenter = false,
            style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/** A white ring that keeps growing out of the blob whose turn it is. */
@Composable
private fun TurnPulse(modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "turn pulse")
    val on = pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = EaseOut)), label = "ring")
    Canvas(
        modifier.graphicsLayer {
            val s = 1f + 0.45f * on.value
            scaleX = s
            scaleY = s
            alpha = 0.9f * (1f - on.value)
        },
    ) {
        val width = 3.dp.toPx()
        drawCircle(Color.White, radius = size.minDimension / 2, style = Stroke(width))
    }
}

// What each chip shows

/** Once the result is in (after the showdown plays): the winner winks, the other revealed hands are sad. */
internal fun chipMood(round: PublicRound, uid: String, verdict: Boolean): Mood = when {
    verdict && round.result?.winnerId == uid -> Mood.Wink
    verdict && round.result?.revealed?.contains(uid) == true -> Mood.Sad
    uid in round.withdrawn -> Mood.Sleepy
    round.bossId == uid -> Mood.Surprised
    else -> Mood.Happy
}

/** While the cards are dealt, everyone watches the dealer (who looks down at the deck). Left to right. */
internal fun chipLook(room: Room, round: PublicRound, uid: String): Offset {
    if (round.dealerId == uid) return Offset(0f, 1f)
    return Offset(if (room.seat(round.dealerId) < room.seat(uid)) -1f else 1f, 0.2f)
}

/** How many cards this player has looked at. Bots (AI and away players) look on their own schedule. */
internal fun chipPeeked(room: Room, round: PublicRound, uid: String, isMe: Boolean, clock: DealClock?, myFlips: Int, now: Long): Int {
    val told = room.peeks?.get(uid) ?: 0
    if (room.isAI(uid) || room.isAway(uid)) {
        val sinceDeal = clock?.let { (now - it.end) / 1000.0 } ?: Double.POSITIVE_INFINITY
        return max(told, DealTimeline.botPeeks(uid, round.roundNumber, sinceDeal))
    }
    return if (isMe) max(told, myFlips) else told
}

/** How many of this player's cards have turned over in the showdown. */
internal fun chipFaceUp(round: PublicRound, uid: String, reveal: RevealClock?, now: Long): Int {
    if (round.revealedHands[uid] == null) return 0
    if (reveal == null) return DealTimeline.HAND_SIZE
    val t = reveal.elapsed(now)
    return (0 until DealTimeline.HAND_SIZE).count { t >= reveal.timeline.flipTime(uid, it) }
}

// Rolling numbers (SwiftUI's `.contentTransition(.numericText())`), shared with the header

/** Stands for the number in a string resource, so the text around it stays still while it rolls. */
internal const val TABLE_SLOT = "\u0001"

/**
 * `template` (a string resource formatted with [TABLE_SLOT]) with `value` in place of the slot: the words stay
 * put and only the characters of the value that change roll.
 */
@Composable
internal fun TableTemplateText(
    template: String,
    value: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    duration: Double = 0.5,
    bounce: Double = 0.0,
) {
    val at = template.indexOf(TABLE_SLOT)
    val prefix = if (at < 0) template else template.substring(0, at)
    val suffix = if (at < 0) "" else template.substring(at + TABLE_SLOT.length)
    val full = prefix + (if (at < 0) "" else value) + suffix
    // The pieces of an English sentence, so left to right in every layout (iOS shows it as one LTR string).
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier.clearAndSetSemantics { contentDescription = full }) {
            if (prefix.isNotEmpty()) Text(prefix, style = style, color = color, maxLines = 1, softWrap = false)
            if (at >= 0) TableNumericText(value, style, color = color, duration = duration, bounce = bounce)
            if (suffix.isNotEmpty()) Text(suffix, style = style, color = color, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * Text whose characters roll up when they change, the rest staying still. Characters line up from the end, so
 * a number's digits keep their places when it grows.
 */
@Composable
internal fun TableNumericText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    duration: Double = 0.5,
    bounce: Double = 0.0,
) {
    // Digits always run left to right, Arabic ones included: the row mustn't mirror in a right-to-left layout.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier.clearAndSetSemantics { contentDescription = text }) {
            text.forEachIndexed { i, c ->
                key(text.length - i) {
                    AnimatedContent(
                        targetState = c,
                        transitionSpec = {
                            (slideInVertically(swiftSpring(duration, bounce)) { it / 2 } + fadeIn(swiftSpring(duration))) togetherWith
                                (slideOutVertically(swiftSpring(duration, bounce)) { -it / 2 } + fadeOut(swiftSpring(duration))) using
                                SizeTransform(clip = false)
                        },
                        label = "character",
                    ) { ch ->
                        Text(ch.toString(), style = style, color = color, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

/** Scales the content down (to at most `minScale`) when it's wider than there's room for, like SwiftUI's `minimumScaleFactor`. */
private fun Modifier.shrinkToFit(minScale: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
    val scale = if (constraints.hasBoundedWidth && placeable.width > constraints.maxWidth) {
        max(minScale, constraints.maxWidth / placeable.width.toFloat())
    } else {
        1f
    }
    val width = (placeable.width * scale).roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth)
    val height = (placeable.height * scale).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
    val x = ((width - placeable.width * scale) / 2).roundToInt()
    layout(width, height) {
        placeable.placeWithLayer(x, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}
