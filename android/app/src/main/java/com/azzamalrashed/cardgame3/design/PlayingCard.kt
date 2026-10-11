package com.azzamalrashed.cardgame3.design

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azzamalrashed.cardgame3.model.Card
import com.azzamalrashed.cardgame3.model.spokenName
import kotlin.math.max
import kotlin.math.min

/** U+FE0E asks for the plain (text) look of ♥ and ♦, never the emoji one. */
private const val TEXT_STYLE = "︎"

fun suitSymbol(suit: String): String = (mapOf("S" to "♠", "H" to "♥", "D" to "♦", "C" to "♣")[suit] ?: "?") + TEXT_STYLE

fun rankText(rank: Int): String = when (rank) {
    14 -> "A"
    13 -> "K"
    12 -> "Q"
    11 -> "J"
    else -> "$rank"
}

/** The shadow every card casts, in proportion to its size. */
private fun Modifier.cardShadow(width: Dp): Modifier =
    softShadow(RoundedCornerShape(width * 0.14f), Color.Black.copy(alpha = 0.18f), min(10f, width.value * 0.11f).dp, min(6f, width.value * 0.07f).dp)

/** A white playing card face (card width; it's 1.4 times as tall). */
@Composable
fun PlayingCard(rank: Int, suit: String, modifier: Modifier = Modifier, width: Dp = 90.dp) {
    val ink = if (suit == "H" || suit == "D") Theme.hotPink else Theme.ink
    val shape = RoundedCornerShape(width * 0.14f)
    // Card text scales with the card, not with the system font size.
    val density = LocalDensity.current
    val rankSize = with(density) { (width * 0.26f).toSp() }
    val smallSuit = with(density) { (width * 0.2f).toSp() }
    val bigSuit = with(density) { (width * 0.5f).toSp() }
    Box(
        modifier
            .size(width, width * 1.4f)
            .cardShadow(width)
            .background(Color.White, shape)
            .border(2.5.dp, Theme.ink, shape),
    ) {
        Column(
            Modifier.padding(width * 0.09f),
            verticalArrangement = Arrangement.spacedBy((-2).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(rankText(rank), style = TextStyle(fontFamily = Theme.fontFamily, fontWeight = FontWeight.Black, fontSize = rankSize, color = ink, lineHeight = rankSize))
            Text(suitSymbol(suit), style = TextStyle(fontSize = smallSuit, color = ink, lineHeight = smallSuit))
        }
        Text(
            suitSymbol(suit),
            modifier = Modifier.align(Alignment.Center),
            style = TextStyle(fontSize = bigSuit, color = ink, lineHeight = bigSuit),
        )
    }
}

/** The back of a card: hot pink with a cream frame, and a pair of blob eyes on bigger cards. */
@Composable
fun CardBack(modifier: Modifier = Modifier, width: Dp = 90.dp) {
    Canvas(
        modifier
            .size(width, width * 1.4f)
            .cardShadow(width),
    ) { drawCardBack(width) }
}

/** Draws a card back at this scope's origin, `width` wide and 1.4 times as tall. */
fun DrawScope.drawCardBack(width: Dp) {
    val w = width.toPx()
    val h = w * 1.4f
    val corner = w * 0.14f
    val big = width >= 40.dp
    drawRoundRect(Theme.hotPink, size = Size(w, h), cornerRadius = CornerRadius(corner))
    // Cream frame, inset by a tenth of the width.
    val frameWidth = max(1.dp.toPx(), w * 0.04f)
    val inset = w * 0.1f + frameWidth / 2
    drawRoundRect(
        Theme.cream.copy(alpha = 0.9f),
        topLeft = Offset(inset, inset),
        size = Size(w - inset * 2, h - inset * 2),
        cornerRadius = CornerRadius(max(0f, corner * 0.6f - frameWidth / 2)),
        style = Stroke(frameWidth),
    )
    if (big) {
        val d = w * 0.2f
        val gap = w * 0.05f
        for (i in 0..1) {
            val cx = w / 2 + (i - 0.5f) * (d + gap)
            drawCircle(Color.White, radius = d / 2, center = Offset(cx, h / 2))
            drawCircle(Theme.ink, radius = w * 0.055f, center = Offset(cx, h / 2 + w * 0.02f))
        }
    }
    val border = (if (big) 2.5.dp else 1.dp).toPx()
    drawRoundRect(
        Theme.ink,
        topLeft = Offset(border / 2, border / 2),
        size = Size(w - border, h - border),
        cornerRadius = CornerRadius(corner - border / 2),
        style = Stroke(border),
    )
}

/**
 * A card that turns over, around its vertical axis, when `faceUp` changes, with `animation`.
 * With animations off in the system settings it cross-fades instead of turning.
 */
@Composable
fun FlipCard(
    card: Card,
    faceUp: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = 90.dp,
    animation: AnimationSpec<Float> = swiftSpring(0.45, 0.25),
) {
    val flat = reduceMotion()
    val angle by animateFloatAsState(if (faceUp) 0f else 180f, animation, label = "flip")
    val showFace = angle < 90f
    val description = if (faceUp) card.spokenName else "Face-down card"
    Box(
        modifier
            .semantics(mergeDescendants = true) { contentDescription = description }
            .graphicsLayer {
                if (!flat) {
                    rotationY = if (showFace) angle else angle - 180f
                    cameraDistance = 12f * density
                }
            },
    ) {
        if (flat) {
            PlayingCard(card.rank, card.suit, Modifier.alpha(1 - angle / 180f), width)
            CardBack(Modifier.alpha(angle / 180f), width)
        } else if (showFace) {
            PlayingCard(card.rank, card.suit, width = width)
        } else {
            CardBack(width = width)
        }
    }
}
