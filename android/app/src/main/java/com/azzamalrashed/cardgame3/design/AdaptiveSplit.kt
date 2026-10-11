package com.azzamalrashed.cardgame3.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Lays out a screen's two parts for any orientation.
 * Portrait: `main` on top, `side` below (as tall as its content, capped width on tablets).
 * Landscape: `main` on the left, `side` on the right, scrolling if it doesn't fit.
 * `side` is a column that sits at the bottom of the space it gets (iOS screens start it with a Spacer).
 */
@Composable
fun AdaptiveSplit(
    modifier: Modifier = Modifier,
    main: @Composable () -> Unit,
    side: @Composable ColumnScope.() -> Unit,
) {
    val maxSideWidth = 520.dp
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight
        if (width > height) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) { main() }
                Column(
                    Modifier
                        .width(min(maxSideWidth.value, width.value * 0.45f).dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = height),
                    verticalArrangement = Arrangement.Bottom,
                    content = side,
                )
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth()) { main() }
                Column(Modifier.widthIn(max = maxSideWidth).fillMaxWidth(), content = side)
            }
        }
    }
}
