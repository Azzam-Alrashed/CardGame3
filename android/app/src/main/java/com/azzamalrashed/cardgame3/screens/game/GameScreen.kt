package com.azzamalrashed.cardgame3.screens.game

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azzamalrashed.cardgame3.CardGame3App
import com.azzamalrashed.cardgame3.R
import com.azzamalrashed.cardgame3.design.AdaptiveSplit
import com.azzamalrashed.cardgame3.design.AppIcons
import com.azzamalrashed.cardgame3.design.Choice
import com.azzamalrashed.cardgame3.design.ChoiceDialog
import com.azzamalrashed.cardgame3.design.CircleIconButton
import com.azzamalrashed.cardgame3.design.Haptic
import com.azzamalrashed.cardgame3.design.Haptics
import com.azzamalrashed.cardgame3.design.OnChange
import com.azzamalrashed.cardgame3.design.SoundToggle
import com.azzamalrashed.cardgame3.design.Theme
import com.azzamalrashed.cardgame3.design.formatted
import com.azzamalrashed.cardgame3.design.frameTime
import com.azzamalrashed.cardgame3.design.rememberHaptics
import com.azzamalrashed.cardgame3.design.swiftSpring
import com.azzamalrashed.cardgame3.model.DealTimeline
import com.azzamalrashed.cardgame3.model.PublicRound
import com.azzamalrashed.cardgame3.model.RevealClock
import com.azzamalrashed.cardgame3.model.RevealTimeline
import com.azzamalrashed.cardgame3.model.Room
import com.azzamalrashed.cardgame3.model.RoundResult
import com.azzamalrashed.cardgame3.screens.onboarding.HowToPlaySheet
import com.azzamalrashed.cardgame3.services.Backend
import com.azzamalrashed.cardgame3.services.Sound
import com.azzamalrashed.cardgame3.services.SoundPlayer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The table: everyone's status on top, the round's state in the middle, your cards and actions below. (iOS: GameView.swift) */
@Composable
fun GameScreen(backend: Backend) {
    val dealSpots = remember { DealSpots() }
    // The room can disappear (leaving, "Back home") a frame before the screen switches.
    val room = backend.room ?: return
    val over = room.gameOver
    if (over != null) {
        GameOverScreen(backend, room, over)
        return
    }
    val round = room.round ?: return
    CompositionLocalProvider(LocalDealSpots provides dealSpots) {
        Table(backend, room, round)
    }
}

@Composable
private fun Table(backend: Backend, room: Room, round: PublicRound) {
    val me = backend.uid ?: ""
    val clock = backend.dealClock
    val reveal = backend.revealClock
    val haptics = rememberHaptics()

    Box(Modifier.fillMaxSize()) {
        AdaptiveSplit(
            modifier = Modifier.safeDrawingPadding(),
            main = {
                Column(Modifier.fillMaxSize()) {
                    Header(backend, room, round, reveal)
                    PlayersStrip(backend, room, round, backend.uid, clock, backend.flipped.size, reveal)
                    // Results can be tall; they scroll when the screen is short (landscape).
                    CenteredScrolling(Modifier.weight(1f)) {
                        Banner(backend, room, round, backend.uid, reveal)
                    }
                }
            },
        ) {
            MyHand(
                backend = backend,
                cards = backend.myCards,
                inRound = room.isStillIn(me),
                ready = backend.myCardsRound?.let { it == round.roundNumber } ?: backend.myCards.isNotEmpty(),
                // A bot holding my seat looks at everything; after the round there's nothing to hide.
                allFaceUp = round.phase == PublicRound.Phase.Finished || room.isAway(me),
                clock = clock,
                modifier = Modifier.fillMaxWidth(),
            )
            // Draws its own background down past the bottom inset.
            ActionPanel(backend, room, round, Modifier.fillMaxWidth())
        }
        if (clock != null) DealLayer(clock, backend.uid, Modifier.fillMaxSize())
        WinConfetti(round, backend.uid, reveal)
    }

    OnChange(round.turnId) { _, turn ->
        if (turn != null && turn == backend.uid) {
            haptics.play(Haptic.Heavy)
            SoundPlayer.play(Sound.Turn)
        }
    }
    OnChange(round) { old, new ->
        for (sound in tableSounds(old, new, room, backend.uid, revealStaged = backend.revealClock != null)) SoundPlayer.play(sound)
    }
    OnChange(round.phase) { _, phase ->
        // A staged showdown buzzes at its verdict instead (playRevealSounds).
        val winner = round.result?.winnerId
        if (phase == PublicRound.Phase.Finished && backend.revealClock == null && winner != null) {
            haptics.play(if (winner == backend.uid) Haptic.Success else Haptic.Medium)
        }
    }
    LaunchedEffect(reveal?.start) {
        if (reveal != null) playRevealSounds(reveal, backend, haptics)
    }
}

/**
 * The middle of the table: its content centered between at least 8 dp above and below, scrolling when it's
 * too tall (iOS: Spacers around a ViewThatFits).
 */
@Composable
private fun CenteredScrolling(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@Composable
private fun Header(backend: Backend, room: Room, round: PublicRound, reveal: RevealClock?) {
    var confirmLeave by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = Icons.Filled.MeetingRoom,
            contentDescription = stringResource(R.string.table_leave),
            onClick = { confirmLeave = true },
            size = 40.dp,
        )
        TableTemplateText(
            stringResource(R.string.table_round, TABLE_SLOT),
            round.roundNumber.formatted(),
            Theme.body(17f, FontWeight.ExtraBold),
            // .snappy
            duration = 0.5,
            bounce = 0.15,
        )
        Spacer(Modifier.weight(1f))
        SoundToggle(size = 40.dp)
        CircleIconButton(
            icon = Icons.Filled.QuestionMark,
            contentDescription = stringResource(R.string.how_to_play),
            onClick = { showRules = true },
            size = 40.dp,
        )
        backend.uid?.let { PointsPill(room, it, reveal) }
    }
    if (confirmLeave) {
        ChoiceDialog(
            title = stringResource(R.string.table_leave_title),
            message = stringResource(R.string.table_leave_message),
            choices = listOf(
                Choice(stringResource(R.string.table_leave_choice)) { CardGame3App.scope.launch { backend.leaveGame() } },
            ),
            onDismiss = { confirmLeave = false },
        )
    }
    if (showRules) HowToPlaySheet(onDismiss = { showRules = false })
}

/** My points. They move at the showdown's verdict, not before (no spoilers), counting to the new total. */
@Composable
private fun PointsPill(room: Room, uid: String, reveal: RevealClock?) {
    val now = frameTime(reveal?.verdict, fps = 4)
    val points = room.points(uid, settled = reveal?.let { now >= it.verdict } ?: true)
    val shown by animateFloatAsState(points.toFloat(), swiftSpring(0.8), label = "points")
    Row(
        Modifier
            .background(Color.White, CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Points, contentDescription = null, modifier = Modifier.size(20.dp), tint = Theme.ink)
        Text(
            shown.roundToInt().formatted(),
            style = with(Theme) { Theme.body(17f, FontWeight.ExtraBold).monospacedDigits() },
            maxLines = 1,
        )
    }
}

/** Confetti for the round's winner, once the showdown has played out. */
@Composable
private fun WinConfetti(round: PublicRound, me: String?, reveal: RevealClock?) {
    val winner = round.result?.winnerId
    if (round.phase != PublicRound.Phase.Finished || winner == null || winner != me) return
    key(round.roundNumber) {
        val now = frameTime(reveal?.verdict, fps = 10)
        if (reveal == null || now >= reveal.verdict) Confetti(Modifier.fillMaxSize())
    }
}

// Sounds

/** What everyone at the table hears when the round changes: chips, folds, offers, the boss, the result. */
internal fun tableSounds(old: PublicRound, new: PublicRound, room: Room, me: String?, revealStaged: Boolean): List<Sound> {
    // A new deal sounds from DealLayer.
    if (old.roundNumber != new.roundNumber) return emptyList()
    val sounds = mutableListOf<Sound>()
    for ((uid, amount) in new.bets) {
        if (old.bets[uid] == amount) continue
        val points = room.table?.seats?.firstOrNull { it.id == uid }?.points ?: 0
        sounds += when {
            amount >= points -> Sound.AllIn
            amount >= 2_000 -> Sound.ChipsBig
            amount >= 1_000 -> Sound.Chips
            else -> Sound.Chip
        }
    }
    if (new.withdrawn.size > old.withdrawn.size) sounds += Sound.Fold
    if (new.offers.any { (uid, amount) -> old.offers[uid] != amount }) sounds += Sound.Offer
    if (new.deals.size > old.deals.size) {
        sounds += Sound.Accept
    } else if (new.phase == PublicRound.Phase.Deals && old.offers.keys.any { new.offers[it] == null }) {
        sounds += Sound.Reject
    }
    if (old.phase == PublicRound.Phase.Betting && new.phase == PublicRound.Phase.Deals) sounds += Sound.Boss
    // A staged showdown plays its own sounds (playRevealSounds).
    val result = new.result
    if (old.phase != PublicRound.Phase.Finished && new.phase == PublicRound.Phase.Finished && result != null && !revealStaged) {
        sounds += tableVerdictSound(result, me)
    }
    return sounds
}

internal fun tableVerdictSound(result: RoundResult, me: String?): Sound {
    if (result.outcome == RoundResult.Outcome.Redeal) return Sound.Fold
    return when {
        result.winnerId == (me ?: "") -> Sound.Win
        (result.deltas[me ?: ""] ?: 0) < 0 -> Sound.Lose
        else -> Sound.Verdict
    }
}

internal enum class RevealSoundCue { Drumroll, Flip, LastFlip, Verdict }

/**
 * The showdown's sounds, in time with the cards: a snap for each card turned over, a darbuka roll before the
 * boss, a deep doum on the boss's last card, then the verdict. Seconds since the showdown started, in order.
 */
internal fun revealSoundCues(timeline: RevealTimeline): List<Pair<Double, RevealSoundCue>> {
    val cues = mutableListOf(timeline.drumrollAt to RevealSoundCue.Drumroll)
    for (uid in timeline.order) {
        for (card in 0 until DealTimeline.HAND_SIZE) {
            val last = uid == timeline.boss && card == DealTimeline.HAND_SIZE - 1
            cues += timeline.flipTime(uid, card) to if (last) RevealSoundCue.LastFlip else RevealSoundCue.Flip
        }
    }
    cues += timeline.verdictAt to RevealSoundCue.Verdict
    return cues.sortedBy { it.first }
}

private suspend fun playRevealSounds(reveal: RevealClock, backend: Backend, haptics: Haptics) {
    // Restarted by a skip: cut the drumroll short.
    SoundPlayer.stop(Sound.Roll)
    playCuesOnTime(revealSoundCues(reveal.timeline), elapsed = { reveal.elapsed(System.currentTimeMillis()) }) { cue ->
        when (cue) {
            RevealSoundCue.Drumroll -> SoundPlayer.play(Sound.Roll)
            RevealSoundCue.Flip -> SoundPlayer.play(Sound.Flip, volume = 0.7f)
            RevealSoundCue.LastFlip -> {
                SoundPlayer.play(Sound.Flip, volume = 1f)
                SoundPlayer.play(Sound.Doum)
            }
            RevealSoundCue.Verdict -> {
                SoundPlayer.stop(Sound.Roll)
                val result = backend.room?.round?.result ?: return@playCuesOnTime
                SoundPlayer.play(tableVerdictSound(result, backend.uid))
                haptics.play(if (result.winnerId == backend.uid) Haptic.Heavy else Haptic.Medium)
            }
        }
    }
}
