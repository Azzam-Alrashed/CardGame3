package com.azzamalrashed.cardgame3.screens.onboarding

import androidx.compose.runtime.Composable

/**
 * The full house rules, opened from the "?" on the home screen and at the table. `onReplayIntro` offers
 * "Replay the intro" (home screen only). (iOS: HowToPlayView.swift)
 */
@Composable
fun HowToPlaySheet(onDismiss: () -> Unit, onReplayIntro: (() -> Unit)? = null) {
}

/** The five hands from best to worst, with a small example of each. */
@Composable
fun HandRanking() {
}
