package com.azzamalrashed.cardgame3.screens.game

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize

/** Places the deal starts from and lands on, reported by the views that own them. */
sealed interface DealSpot {
    /** The dealer's blob, where the deck sits. */
    data object Dealer : DealSpot

    /** The small cards in front of a player. */
    data class Seat(val uid: String) : DealSpot

    /** One of my cards, by position. */
    data class Hand(val index: Int) : DealSpot

    /** The visible part of the players strip (players scrolled out of view get cards at its edge). */
    data object Strip : DealSpot
}

/**
 * Where the deal starts and lands, in root coordinates (px): written as those views lay out (and scroll), read by
 * `DealLayer` on every frame. Plain (not Compose state) on purpose: the layer redraws every frame anyway.
 */
class DealSpots {
    val frames = mutableMapOf<DealSpot, Rect>()
}

/** The table's [DealSpots], provided by `GameScreen`. */
val LocalDealSpots = staticCompositionLocalOf { DealSpots() }

/** Marks this view as a place the deal starts from or lands on (nothing for null). */
fun Modifier.dealSpot(spot: DealSpot?): Modifier = if (spot == null) this else composed {
    val spots = LocalDealSpots.current
    onGloballyPositioned {
        // The true center (through any rotation or scale above), with the view's own unrotated size.
        val size = it.size.toSize()
        val center = it.localToRoot(Offset(size.width / 2, size.height / 2))
        spots.frames[spot] = Rect(center - Offset(size.width / 2, size.height / 2), size)
    }
}

/** Shared sizes: the deal lands each card exactly where these views draw it. */
object TableLayout {
    /** Width of the small cards in front of each player (`MiniCards`). */
    val miniWidth = 12.dp

    /** Width of my cards (`MyHand`). */
    val handCardWidth = 84.dp

    /** How much each of my cards leans in the fan (degrees); the flying cards land at the same angle. */
    fun handAngle(index: Int): Float = index * 6f - 9f

    /** How each small card leans in front of a player (degrees), before it's looked at. */
    fun miniAngle(index: Int): Float = index * 8f - 12f
}
