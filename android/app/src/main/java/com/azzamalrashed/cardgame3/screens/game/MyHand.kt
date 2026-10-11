package com.azzamalrashed.cardgame3.screens.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.azzamalrashed.cardgame3.model.Card
import com.azzamalrashed.cardgame3.model.DealClock
import com.azzamalrashed.cardgame3.services.Backend

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
}
