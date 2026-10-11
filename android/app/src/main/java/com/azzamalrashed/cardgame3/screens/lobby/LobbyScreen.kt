package com.azzamalrashed.cardgame3.screens.lobby

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azzamalrashed.cardgame3.CardGame3App
import com.azzamalrashed.cardgame3.R
import com.azzamalrashed.cardgame3.design.AdaptiveSplit
import com.azzamalrashed.cardgame3.design.AsyncPillButton
import com.azzamalrashed.cardgame3.design.Blob
import com.azzamalrashed.cardgame3.design.BlocksWhileBusy
import com.azzamalrashed.cardgame3.design.BottomSheet
import com.azzamalrashed.cardgame3.design.Choice
import com.azzamalrashed.cardgame3.design.ChoiceDialog
import com.azzamalrashed.cardgame3.design.CircleIconButton
import com.azzamalrashed.cardgame3.design.Haptic
import com.azzamalrashed.cardgame3.design.Mood
import com.azzamalrashed.cardgame3.design.OnChange
import com.azzamalrashed.cardgame3.design.Theme
import com.azzamalrashed.cardgame3.design.formatted
import com.azzamalrashed.cardgame3.design.rememberHaptics
import com.azzamalrashed.cardgame3.design.swiftSpring
import com.azzamalrashed.cardgame3.model.Room
import com.azzamalrashed.cardgame3.model.TableSize
import com.azzamalrashed.cardgame3.screens.game.PlayerMenu
import com.azzamalrashed.cardgame3.services.Backend
import com.azzamalrashed.cardgame3.services.Sound
import com.azzamalrashed.cardgame3.services.SoundPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** The room before the game: its code, who's in, AI players, Start. (iOS: LobbyView.swift) */
@Composable
fun LobbyScreen(backend: Backend) {
    // The room can disappear (leaving) a frame before the screen switches.
    val room = backend.room ?: return
    // Player the host tapped ✕ on, waiting for "Remove" to be confirmed.
    var removing by remember { mutableStateOf<String?>(null) }
    val count = room.players.size
    val haptics = rememberHaptics()
    OnChange(count) { old, new ->
        haptics.play(Haptic.Light)
        if (new > old) SoundPlayer.play(Sound.Join)
    }

    AdaptiveSplit(
        modifier = Modifier.safeDrawingPadding(),
        main = {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                CodeHeader(room, Modifier.padding(top = 16.dp))
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    val columns = lobbyColumns(count, maxWidth.value)
                    val viewport = maxHeight
                    val scroll = rememberScrollState()
                    Box(
                        Modifier
                            .fillMaxSize()
                            // Like iOS's `.scrollBounceBehavior(.basedOnSize)`: no overscroll when everyone fits.
                            .verticalScroll(scroll, enabled = scroll.maxValue > 0),
                    ) {
                        key(room.code) {
                            BadgeGrid(
                                room = room,
                                backend = backend,
                                columns = columns,
                                onRemove = { removing = it },
                                // Centered in the space, instead of one long row on wide screens.
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = viewport),
                            )
                        }
                    }
                }
            }
        },
    ) {
        BlocksWhileBusy {
            BottomSheet {
                if (backend.isHost) {
                    AsyncPillButton(
                        text = stringResource(R.string.lobby_add_ai),
                        action = { backend.addAiPlayer() },
                        primary = false,
                        enabled = count < TableSize.MAX_PLAYERS,
                    )
                    val missing = missingPlayers(count)
                    val startLabel =
                        if (missing > 0) stringResource(R.string.lobby_need_more, missing.formatted()) else stringResource(R.string.lobby_start)
                    AsyncPillButton(action = { backend.startGame() }, enabled = count >= TableSize.MIN_PLAYERS) {
                        val snappy = swiftSpring<Float>(0.5, 0.15)
                        AnimatedContent(
                            targetState = startLabel,
                            transitionSpec = { fadeIn(snappy) togetherWith fadeOut(snappy) using SizeTransform(clip = false) },
                            contentAlignment = Alignment.Center,
                            label = "start label",
                        ) { Text(it) }
                    }
                } else {
                    Row(
                        Modifier
                            .padding(vertical = 18.dp)
                            .alpha(0.6f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.lobby_waiting), style = Theme.body(16f, FontWeight.SemiBold), color = Theme.ink)
                        WaitingDots(fontSize = 16f)
                    }
                }
                AsyncPillButton(
                    text = stringResource(R.string.lobby_leave),
                    action = { backend.leaveRoom() },
                    primary = false,
                )
            }
        }
    }

    removing?.let { uid ->
        ChoiceDialog(
            title = stringResource(R.string.lobby_remove_title, room.name(uid)),
            choices = listOf(
                Choice(stringResource(R.string.lobby_remove), destructive = true) {
                    CardGame3App.scope.launch { backend.removePlayer(uid) }
                },
            ),
            onDismiss = { removing = null },
        )
    }
}

/** "Tap to copy the code", the code itself, and "6 / 13 players" with the Invite button. */
@Composable
private fun CodeHeader(room: Room, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    val snappy = swiftSpring<Float>(0.5, 0.15)
    val scale by animateFloatAsState(if (copied) 1.06f else 1f, snappy, label = "copied scale")
    val clipLabel = stringResource(R.string.lobby_clip_label)
    val codePress = remember { MutableInteractionSource() }
    val codeAlpha = pressedAlpha(codePress)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AnimatedContent(
            targetState = copied,
            transitionSpec = { fadeIn(snappy) togetherWith fadeOut(snappy) using SizeTransform(clip = false) },
            contentAlignment = Alignment.Center,
            label = "copy hint",
        ) { now ->
            Text(
                stringResource(if (now) R.string.lobby_copied else R.string.lobby_tap_to_copy),
                style = Theme.body(15f, FontWeight.Medium),
                color = Theme.ink.copy(alpha = 0.6f),
            )
        }
        Text(
            room.code,
            style = Theme.wordmark(72f).copy(letterSpacing = 6.sp),
            color = Theme.ink,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = codeAlpha
                }
                .clickable(codePress, indication = null, role = Role.Button) {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText(clipLabel, room.code))
                    // iOS: `.sensoryFeedback(.success, trigger: copied)` when it turns true.
                    if (!copied) haptics.play(Haptic.Success)
                    copied = true
                    scope.launch {
                        delay(1500)
                        copied = false
                    }
                },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.lobby_player_count, room.players.size.formatted(), TableSize.MAX_PLAYERS.formatted()),
                style = Theme.body(15f, FontWeight.Medium),
                color = Theme.ink.copy(alpha = 0.6f),
            )
            InviteButton(room.code)
        }
    }
}

/** White capsule that opens the share sheet with an invitation to this room. */
@Composable
private fun InviteButton(code: String) {
    val context = LocalContext.current
    val message = stringResource(R.string.lobby_invite_message, code)
    val press = remember { MutableInteractionSource() }
    val alpha = pressedAlpha(press)
    val iconSize = with(LocalDensity.current) { 16.sp.toDp() }
    Row(
        Modifier
            .graphicsLayer { this.alpha = alpha }
            .background(Color.White, CircleShape)
            .clickable(press, indication = null, role = Role.Button) {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, message)
                context.startActivity(Intent.createChooser(send, null))
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(iconSize), tint = Theme.ink)
        Text(stringResource(R.string.lobby_invite), style = Theme.body(14f, FontWeight.Bold), color = Theme.ink)
    }
}

/** Dims while pressed, like an iOS plain or borderless button. */
@Composable
private fun pressedAlpha(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 0.5f else 1f, tween(if (pressed) 0 else 200), label = "pressed")
    return alpha
}

/** A badge on screen: everyone in the room, plus anyone who just left, until they've faded out. */
private class ShownBadge(val uid: String, room: Room, val visible: MutableTransitionState<Boolean>) {
    /** The latest room this player was in (what a leaving badge keeps showing). */
    var room by mutableStateOf(room)
}

/**
 * Where each badge sits, sliding when it moves (iOS animates the grid with a spring when the players change).
 * Plain maps on purpose: written and read while placing.
 */
private class BadgeMotion {
    /** Last place (px, in the grid) of each badge still in the room; a leaving badge fades out there. */
    val lastPlace = mutableMapOf<String, IntOffset>()
    private val slides = mutableMapOf<String, Animatable<IntOffset, AnimationVector2D>>()
    private val targets = mutableMapOf<String, IntOffset>()

    /** Where to draw `uid` this frame, starting a slide when its spot changed. A new badge starts in place. */
    fun place(uid: String, target: IntOffset, scope: CoroutineScope, spec: AnimationSpec<IntOffset>): IntOffset {
        val slide = slides.getOrPut(uid) { Animatable(target, IntOffset.VectorConverter) }
        val previous = targets.put(uid, target)
        if (previous != null && previous != target) scope.launch { slide.animateTo(target, spec) }
        return slide.value
    }

    fun forget(uid: String) {
        lastPlace.remove(uid)
        slides.remove(uid)
        targets.remove(uid)
    }
}

/**
 * The players' badges in a grid of fixed 100 dp columns, centered in the space it gets (iOS: a LazyVGrid in a
 * ScrollView). Badges pop in and out with a bouncy spring and slide to their new spots.
 */
@Composable
private fun BadgeGrid(room: Room, backend: Backend, columns: Int, onRemove: (String) -> Unit, modifier: Modifier = Modifier) {
    val players = room.players
    val shown = remember { mutableStateListOf(*players.map { ShownBadge(it.uid, room, MutableTransitionState(true)) }.toTypedArray()) }
    val motion = remember { BadgeMotion() }
    val scope = rememberCoroutineScope()
    val seats = remember(players) { players.withIndex().associate { (index, player) -> player.uid to index } }
    val bouncy = swiftSpring<Float>(0.5, 0.45)
    val slide = swiftSpring(0.5, 0.45, IntOffset(1, 1))

    // Newcomers pop in; leavers stay put (shrinking and fading) while everyone else moves up.
    LaunchedEffect(room) {
        val byUid = shown.associateBy { it.uid }
        val next = mergeShown(shown.map { it.uid }, players.map { it.uid }).map { uid ->
            val badge = byUid[uid] ?: ShownBadge(uid, room, MutableTransitionState(false))
            if (uid in seats) {
                badge.room = room
                badge.visible.targetState = true
            } else {
                badge.visible.targetState = false
            }
            badge
        }
        if (next != shown.toList()) {
            shown.clear()
            shown.addAll(next)
        }
    }

    Layout(
        content = {
            for (badge in shown) {
                key(badge.uid) {
                    val here = badge.uid in seats
                    val shownRoom = if (here) room else badge.room
                    val uid = badge.uid
                    AnimatedVisibility(
                        visibleState = badge.visible,
                        modifier = Modifier.layoutId(uid),
                        enter = scaleIn(bouncy, initialScale = 0.3f) + fadeIn(bouncy),
                        exit = scaleOut(bouncy, targetScale = 0.3f) + fadeOut(bouncy),
                    ) {
                        PlayerMenu(uid, shownRoom, backend) {
                            PlayerBadge(
                                name = shownRoom.name(uid),
                                color = Theme.color(forSeat = shownRoom.seat(uid)),
                                isHost = uid == shownRoom.hostId,
                                isYou = uid == backend.uid,
                                isAI = shownRoom.isAI(uid),
                                onRemove = if (here && backend.isHost && uid != room.hostId) ({ onRemove(uid) }) else null,
                            )
                        }
                    }
                    if (!badge.visible.targetState) {
                        LaunchedEffect(badge) {
                            snapshotFlow { badge.visible.isIdle && !badge.visible.currentState }.first { it }
                            shown.remove(badge)
                            motion.forget(uid)
                        }
                    }
                }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val ids = measurables.map { it.layoutId as String }
        val placeables = measurables.map { it.measure(Constraints(maxWidth = 100.dp.roundToPx())) }
        // Badges of players in the room, in seat order; the others are on their way out.
        val inRoom = ids.indices.filter { ids[it] in seats }.sortedBy { seats.getValue(ids[it]) }
        val plan = planBadgeGrid(
            sizes = inRoom.map { IntSize(placeables[it].width, placeables[it].height) },
            columns = columns,
            cell = 100.dp.roundToPx(),
            spacing = 12.dp.roundToPx(),
            rowSpacing = 18.dp.roundToPx(),
            padding = 24.dp.roundToPx(),
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else null,
            minHeight = constraints.minHeight,
            rtl = layoutDirection == LayoutDirection.Rtl,
        )
        layout(plan.width, plan.height.coerceAtMost(constraints.maxHeight)) {
            inRoom.forEachIndexed { slot, child -> motion.lastPlace[ids[child]] = plan.positions[slot] }
            ids.forEachIndexed { child, uid ->
                val target = motion.lastPlace[uid] ?: return@forEachIndexed
                placeables[child].place(motion.place(uid, target, scope, slide))
            }
        }
    }
}

@Composable
private fun PlayerBadge(name: String, color: Color, isHost: Boolean, isYou: Boolean, isAI: Boolean, onRemove: (() -> Unit)?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BadgeBlob(name, color, isHost, isYou, onRemove)
        Text(
            name,
            style = Theme.body(15f, FontWeight.Bold),
            color = Theme.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // iOS: `.minimumScaleFactor(0.7)`.
            autoSize = TextAutoSize.StepBased(minFontSize = (15 * 0.7).sp, maxFontSize = 15.sp, stepSize = 0.5.sp),
        )
        badgeTag(isHost = isHost, isYou = isYou, isAI = isAI)?.let { tag ->
            Text(
                stringResource(tag.label),
                style = Theme.body(12f, FontWeight.Bold),
                color = if (isHost) Color.White else Theme.ink,
                maxLines = 1,
                modifier = Modifier
                    .background(if (isHost) Theme.ink else Color.White, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/** The blob, with the host's ✕ on its top corner for removing the player. */
@Composable
private fun BadgeBlob(name: String, color: Color, isHost: Boolean, isYou: Boolean, onRemove: (() -> Unit)?) {
    Box {
        Blob(color, 72.dp, mood = if (isYou) Mood.Wink else Mood.Happy, hair = isHost)
        AnimatedVisibility(
            visible = onRemove != null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp),
            enter = fadeIn(swiftSpring(0.5, 0.45)),
            exit = fadeOut(swiftSpring(0.5, 0.45)),
        ) {
            CircleIconButton(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.lobby_remove_player, name),
                onClick = { onRemove?.invoke() },
                size = 26.dp,
            )
        }
    }
}

/** The ellipsis after "Waiting for the host to start", its dots lighting up in turn (iOS's variable color effect). */
@Composable
private fun WaitingDots(fontSize: Float) {
    val cycle = rememberInfiniteTransition(label = "waiting dots")
    val phase = cycle.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "phase")
    val em = with(LocalDensity.current) { fontSize.sp.toDp() }
    Canvas(Modifier.size(width = em, height = em * 0.22f)) {
        val d = size.height
        val step = (size.width - d) / 2
        for (index in 0 until 3) {
            drawCircle(
                Theme.ink.copy(alpha = ellipsisDotAlpha(phase.value, index)),
                radius = d / 2,
                center = Offset(d / 2 + index * step, d / 2),
            )
        }
    }
}

// Pure layout rules (unit tested in LobbyLayoutTest).

/** Only as many columns as there are players (at most 5), so the badges stay centered. `width` in dp. */
internal fun lobbyColumns(count: Int, width: Float): Int {
    val fit = ((width - 48 + 12) / (100 + 12)).toInt()
    return max(1, min(min(count, fit), 5))
}

/** How many more players the table needs before it can start (0 or less: ready). */
internal fun missingPlayers(count: Int): Int = TableSize.MIN_PLAYERS - count

/** The little label under a badge. */
internal enum class BadgeTag(@param:StringRes val label: Int) {
    AI(R.string.lobby_tag_ai),
    Host(R.string.lobby_tag_host),
    HostYou(R.string.lobby_tag_host_you),
    You(R.string.lobby_tag_you),
}

internal fun badgeTag(isHost: Boolean, isYou: Boolean, isAI: Boolean): BadgeTag? = when {
    !(isHost || isYou || isAI) -> null
    isAI -> BadgeTag.AI
    isHost -> if (isYou) BadgeTag.HostYou else BadgeTag.Host
    else -> BadgeTag.You
}

/**
 * The badges to keep on screen, in order: everyone in `current` (seat order), with those only in `shown` (leaving)
 * kept just after the badge they followed.
 */
internal fun mergeShown(shown: List<String>, current: List<String>): List<String> {
    val result = current.toMutableList()
    shown.forEachIndexed { index, uid ->
        if (uid !in current) {
            val before = shown.subList(0, index).lastOrNull { it in result }
            result.add(if (before == null) 0 else result.indexOf(before) + 1, uid)
        }
    }
    return result
}

/** Size of the grid and where each badge goes (top-left, px), in the order of `sizes`. */
internal data class BadgeGridPlan(val width: Int, val height: Int, val positions: List<IntOffset>)

/**
 * Lays out badges of `sizes` in `columns` fixed `cell`-wide columns, each badge centered in its cell and row, the
 * whole grid (plus `padding`) centered in `maxWidth` × at least `minHeight`. The first column is on the right in
 * right-to-left layouts, like SwiftUI's grids.
 */
internal fun planBadgeGrid(
    sizes: List<IntSize>,
    columns: Int,
    cell: Int,
    spacing: Int,
    rowSpacing: Int,
    padding: Int,
    maxWidth: Int?,
    minHeight: Int,
    rtl: Boolean,
): BadgeGridPlan {
    val rows = sizes.indices.chunked(columns)
    val rowHeights = rows.map { row -> row.maxOf { sizes[it].height } }
    val gridWidth = columns * cell + (columns - 1) * spacing
    val gridHeight = rowHeights.sum() + max(0, rows.size - 1) * rowSpacing
    val width = maxWidth ?: (gridWidth + 2 * padding)
    val height = max(minHeight, gridHeight + 2 * padding)
    val left = (width - gridWidth) / 2
    var top = (height - gridHeight) / 2
    val positions = MutableList(sizes.size) { IntOffset.Zero }
    rows.forEachIndexed { r, row ->
        row.forEachIndexed { column, i ->
            val size = sizes[i]
            val x = left + column * (cell + spacing) + (cell - size.width) / 2
            positions[i] = IntOffset(if (rtl) width - x - size.width else x, top + (rowHeights[r] - size.height) / 2)
        }
        top += rowHeights[r] + rowSpacing
    }
    return BadgeGridPlan(width, height, positions)
}

/**
 * How lit dot `index` (of 3) of the waiting ellipsis is at `phase` (0 to 1 through a cycle): one dot at a time,
 * in turn, the others dimmed.
 */
internal fun ellipsisDotAlpha(phase: Float, index: Int): Float {
    val dim = 0.3f
    // Distance in dots from this dot's peak, wrapping around the three.
    val offset = ((phase * 3 - index - 0.5f) % 3 + 3) % 3
    val distance = min(offset, 3 - offset)
    val lit = (1 - distance).coerceIn(0f, 1f)
    return dim + (1 - dim) * lit
}
