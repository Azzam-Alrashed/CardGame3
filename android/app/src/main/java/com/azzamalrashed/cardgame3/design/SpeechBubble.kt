package com.azzamalrashed.cardgame3.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Black speech bubble, like "Hello~", tilted a little, with its tail at the bottom right. */
@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Box(modifier.rotate(-8f)) {
        Text(
            text,
            style = Theme.body(20f, FontWeight.ExtraBold).copy(color = Color.White),
            modifier = Modifier
                .background(Theme.ink, CircleShape)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
        Canvas(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-18).dp, y = 9.dp)
                .size(14.dp, 12.dp),
        ) {
            val p = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2, size.height)
                close()
            }
            drawPath(p, Theme.ink)
        }
    }
}
