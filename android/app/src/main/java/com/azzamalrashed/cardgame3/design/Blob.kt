package com.azzamalrashed.cardgame3.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/** A blob's face. */
enum class Mood { Happy, Wink, Surprised, Sleepy, Sad }

/**
 * A soft glowing ball with a little face. `look` is where the eyes look, each axis from -1 to 1.
 * The hair curl (the boss and the host) is drawn above the ball, outside its bounds.
 */
@Composable
fun Blob(
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    mood: Mood = Mood.Happy,
    look: Offset = Offset.Zero,
    hair: Boolean = false,
) {
    Canvas(
        modifier
            .size(size)
            .softShadow(CircleShape, color.copy(alpha = 0.45f), size * 0.12f, size * 0.06f),
    ) {
        drawBlob(color, mood, look, hair)
    }
}

/** Draws a blob filling this scope's size (square). */
fun DrawScope.drawBlob(color: Color, mood: Mood, look: Offset, hair: Boolean) {
    val s = size.minDimension
    val c = Offset(s / 2, s / 2)
    drawCircle(color, radius = s / 2, center = c)
    // Soft highlight top-left, slightly darker edge.
    drawCircle(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.6f),
            0.5f to Color.White.copy(alpha = 0f),
            1f to Color.Black.copy(alpha = 0.08f),
            center = Offset(s * 0.35f, s * 0.3f),
            radius = s * 0.7f,
        ),
        radius = s / 2,
        center = c,
    )

    // The face: two eyes above a mouth, centered a little below the middle.
    val ink = Theme.ink
    val eye = s * 0.17f
    val mouthHeight = when (mood) {
        Mood.Surprised -> s * 0.1f
        Mood.Sad -> s * 0.06f
        else -> s * 0.08f
    }
    val top = s / 2 + s * 0.02f - (eye + s * 0.06f + mouthHeight) / 2
    val eyeY = top + eye / 2
    val dx = s * 0.04f + eye / 2
    drawEye(Offset(s / 2 - dx, eyeY), eye, closed = mood == Mood.Sleepy, look)
    drawEye(Offset(s / 2 + dx, eyeY), eye, closed = mood == Mood.Wink || mood == Mood.Sleepy, look)

    val mouthTop = top + eye + s * 0.06f
    when (mood) {
        Mood.Surprised -> drawCircle(ink, radius = s * 0.05f, center = Offset(s / 2, mouthTop + s * 0.05f), style = Stroke(s * 0.035f))
        Mood.Sad -> drawSmile(Rect(s / 2 - s * 0.1f, mouthTop, s / 2 + s * 0.1f, mouthTop + s * 0.06f), s * 0.045f, flipped = true)
        else -> drawSmile(Rect(s / 2 - s * 0.11f, mouthTop, s / 2 + s * 0.11f, mouthTop + s * 0.08f), s * 0.045f)
    }

    if (hair) {
        // A curl that rises from the top-left and ends in a little loop.
        val w = s * 0.7f
        val h = s * 0.3f
        val x0 = s / 2 + s * 0.1f - w / 2
        val y0 = s / 2 - s * 0.42f - h / 2
        val path = Path().apply {
            moveTo(x0, y0 + h)
            quadraticTo(x0 + w * 0.2f, y0, x0 + w * 0.75f, y0)
            val r = h * 0.2f
            arcTo(Rect(Offset(x0 + w * 0.85f, y0 + h * 0.2f), r), 180f, 340f, forceMoveTo = false)
        }
        drawPath(path, ink, style = Stroke(width = s * 0.05f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawEye(center: Offset, d: Float, closed: Boolean, look: Offset) {
    if (closed) {
        drawSmile(Rect(center.x - d * 0.4f, center.y - d * 0.175f, center.x + d * 0.4f, center.y + d * 0.175f), d * 0.22f)
    } else {
        drawCircle(Color.White, radius = d / 2, center = center)
        drawCircle(Theme.ink, radius = d * 0.275f, center = center + Offset(look.x * d * 0.18f, look.y * d * 0.18f))
    }
}

/** A curve from the frame's top corners, dipping to its bottom; `flipped` turns it into a frown. */
private fun DrawScope.drawSmile(frame: Rect, lineWidth: Float, flipped: Boolean = false) {
    val path = Path().apply {
        if (flipped) {
            moveTo(frame.left, frame.bottom)
            quadraticTo(frame.center.x, frame.top - frame.height, frame.right, frame.bottom)
        } else {
            moveTo(frame.left, frame.top)
            quadraticTo(frame.center.x, frame.top + frame.height * 2, frame.right, frame.top)
        }
    }
    drawPath(path, Theme.ink, style = Stroke(width = lineWidth, cap = StrokeCap.Round))
}
