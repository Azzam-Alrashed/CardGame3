package com.azzamalrashed.cardgame3.design

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// Icons the Material set has no match for. The SF Symbols the iOS app uses map to Material icons like this:
//   speaker.wave.2.fill → VolumeUp, speaker.slash.fill → VolumeOff, questionmark → QuestionMark,
//   door.left.hand.open → MeetingRoom, xmark → Close, square.and.arrow.up → Share, minus/plus → Remove/Add,
//   hourglass → HourglassTop, moon.zzz.fill → Bedtime, eye.fill → Visibility, lock.fill → Lock,
//   bolt.fill → Bolt, exclamationmark.bubble → Feedback, eye/eye.slash → Visibility/VisibilityOff,
//   circle.hexagongrid.fill → [AppIcons.Points], crown.fill → [AppIcons.Crown].

object AppIcons {
    /** A cluster of seven round chips (SF Symbols' circle.hexagongrid.fill): the points badge. */
    val Points: ImageVector by lazy {
        ImageVector.Builder("Points", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                val r = 3.4f
                val centers = listOf(12f to 12f) + (0 until 6).map { i ->
                    val a = Math.toRadians(60.0 * i - 90).toFloat()
                    (12f + cos(a) * 7.6f) to (12f + sin(a) * 7.6f)
                }
                for ((x, y) in centers) {
                    moveTo(x - r, y)
                    arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x + r, y)
                    arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x - r, y)
                    close()
                }
            }
        }.build()
    }

    /** A three-pointed crown (SF Symbols' crown.fill). */
    val Crown: ImageVector by lazy {
        ImageVector.Builder("Crown", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 7.5f)
                lineTo(7.6f, 11.2f)
                lineTo(12f, 4.5f)
                lineTo(16.4f, 11.2f)
                lineTo(21f, 7.5f)
                lineTo(19.2f, 17f)
                lineTo(4.8f, 17f)
                close()
                moveTo(5f, 18.5f)
                lineTo(19f, 18.5f)
                lineTo(19f, 20.5f)
                lineTo(5f, 20.5f)
                close()
            }
        }.build()
    }
}

/** A [CircleButton] showing one icon at the iOS symbol size. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    enabled: Boolean = true,
) {
    CircleButton(onClick, contentDescription, modifier, size, enabled) {
        Icon(icon, contentDescription = null, modifier = Modifier.fillMaxSize(), tint = Theme.ink)
    }
}
