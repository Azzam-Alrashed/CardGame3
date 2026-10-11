package com.azzamalrashed.cardgame3.screens.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.azzamalrashed.cardgame3.R
import com.azzamalrashed.cardgame3.design.PillButton
import com.azzamalrashed.cardgame3.design.PlayingCard
import com.azzamalrashed.cardgame3.design.Theme
import kotlinx.coroutines.launch

/** Where "Contact us" writes to. */
private const val CONTACT_URL = "mailto:azzam.rar@gmail.com?subject=Muda"

/** The house rules: each section's title and its lines. */
private val sections: List<Pair<Int, Int>> = listOf(
    R.string.onboarding_rules_setup to R.array.onboarding_rules_setup_lines,
    R.string.onboarding_rules_betting to R.array.onboarding_rules_betting_lines,
    R.string.onboarding_rules_deals to R.array.onboarding_rules_deals_lines,
    R.string.onboarding_rules_reveal to R.array.onboarding_rules_reveal_lines,
    R.string.onboarding_rules_automatic to R.array.onboarding_rules_automatic_lines,
    R.string.onboarding_rules_between to R.array.onboarding_rules_between_lines,
    R.string.onboarding_rules_end to R.array.onboarding_rules_end_lines,
    R.string.onboarding_rules_nicely to R.array.onboarding_rules_nicely_lines,
)

/**
 * The full house rules, opened from the "?" on the home screen and at the table. `onReplayIntro` offers
 * "Replay the intro" (home screen only). (iOS: HowToPlayView.swift)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToPlaySheet(onDismiss: () -> Unit, onReplayIntro: (() -> Unit)? = null) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Slides the sheet away, then tells the caller (iOS's `dismiss()`).
    val dismiss: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Full height, but below the status bar, like an iOS sheet.
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        sheetState = sheetState,
        containerColor = Theme.sunny,
        contentColor = Theme.ink,
        dragHandle = null,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal) },
    ) {
        CompositionLocalProvider(
            LocalContentColor provides Theme.ink,
            LocalTextStyle provides Theme.body(17f).copy(color = Theme.ink),
        ) {
            val scroll = rememberScrollState()
            var largeTitleHeight by remember { mutableIntStateOf(0) }
            // The large title scrolls away under the bar, which then shows the title instead (iOS navigation title).
            val titleInBar by remember { derivedStateOf { largeTitleHeight > 0 && scroll.value >= largeTitleHeight } }
            val barTitleOpacity by animateFloatAsState(if (titleInBar) 1f else 0f, tween(200), label = "bar title")

            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(56.dp)) {
                    Text(
                        stringResource(R.string.how_to_play),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .alpha(barTitleOpacity)
                            .then(if (titleInBar) Modifier else Modifier.clearAndSetSemantics {}),
                        style = Theme.body(17f, FontWeight.SemiBold),
                    )
                    PlainButton(
                        text = stringResource(R.string.onboarding_rules_done),
                        onClick = dismiss,
                        style = Theme.body(17f, FontWeight.Bold),
                        padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                    )
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scroll),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                    ) {
                        Text(
                            stringResource(R.string.how_to_play),
                            modifier = Modifier
                                .onSizeChanged { largeTitleHeight = it.height }
                                .semantics { heading() },
                            style = Theme.body(34f, FontWeight.Bold),
                        )
                        Column(
                            Modifier.padding(vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            RulesCard(stringResource(R.string.onboarding_rules_hands)) { HandRanking() }
                            for ((title, lines) in sections) RulesSection(title, lines)
                            if (onReplayIntro != null) {
                                PillButton(
                                    text = stringResource(R.string.onboarding_rules_replay_intro),
                                    onClick = {
                                        dismiss()
                                        onReplayIntro()
                                    },
                                    modifier = Modifier.padding(top = 8.dp),
                                    primary = false,
                                )
                            }
                            PillButton(
                                text = stringResource(R.string.onboarding_rules_contact),
                                onClick = { contactUs(context) },
                                primary = false,
                            )
                        }
                    }
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
                }
            }
        }
    }
}

@Composable
private fun RulesSection(@StringRes title: Int, @ArrayRes lines: Int) {
    RulesCard(stringResource(title)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (line in stringArrayResource(lines)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(BULLET, modifier = Modifier.alignByBaseline(), style = Theme.body(16f, FontWeight.Black))
                    Text(line, modifier = Modifier.alignByBaseline(), style = Theme.body(16f, FontWeight.Medium))
                }
            }
        }
    }
}

/** A white card with a title. */
@Composable
private fun RulesCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, modifier = Modifier.semantics { heading() }, style = Theme.body(20f, FontWeight.ExtraBold))
        content()
    }
}

private const val BULLET = "•"

/** Opens the mail app (iOS's `Link` to a mailto: URL). Nothing happens if there's no mail app. */
private fun contactUs(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_SENDTO, CONTACT_URL.toUri()))
    } catch (e: ActivityNotFoundException) {
        // No app handles e-mail on this phone.
    }
}

/** The five hands, best to worst, each with an example. */
private val handRows: List<Pair<Int, List<Pair<Int, String>>>> = listOf(
    R.string.onboarding_hand_four to listOf(14 to "S", 14 to "H", 14 to "D", 14 to "C"),
    R.string.onboarding_hand_three to listOf(13 to "S", 13 to "H", 13 to "C", 12 to "D"),
    R.string.onboarding_hand_two_pairs to listOf(12 to "S", 12 to "H", 11 to "D", 11 to "C"),
    R.string.onboarding_hand_one_pair to listOf(14 to "H", 14 to "C", 13 to "S", 11 to "D"),
    R.string.onboarding_hand_high_card to listOf(14 to "S", 13 to "D", 12 to "C", 11 to "H"),
)

/** The five hands from best to worst, with a small example of each. */
@Composable
fun HandRanking() {
    Column(
        Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        handRows.forEachIndexed { i, (name, cards) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", modifier = Modifier.width(18.dp), style = Theme.body(15f, FontWeight.Black), textAlign = TextAlign.Center)
                // At least 8 dp (plus the spacing) between the name and the cards.
                Text(stringResource(name), modifier = Modifier.weight(1f).padding(end = 20.dp), style = Theme.body(16f, FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
                    for ((rank, suit) in cards) PlayingCard(rank, suit, width = 30.dp)
                }
            }
        }
        Text(
            stringResource(R.string.onboarding_hand_no_ties),
            modifier = Modifier.padding(top = 4.dp).alpha(0.7f),
            style = Theme.body(14f, FontWeight.Medium),
        )
    }
}
