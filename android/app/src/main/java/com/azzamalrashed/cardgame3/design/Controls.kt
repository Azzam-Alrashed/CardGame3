package com.azzamalrashed.cardgame3.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.azzamalrashed.cardgame3.CardGame3App
import kotlinx.coroutines.launch

/** SwiftUI's `.shadow(color:radius:y:)`. */
fun Modifier.softShadow(shape: Shape, color: Color, radius: Dp, y: Dp = 0.dp): Modifier =
    dropShadow(shape, Shadow(radius = radius, color = color, offset = DpOffset(0.dp, y)))

/** Shrinks a little while pressed, with a light tap, like the iOS button styles. */
@Composable
private fun pressFeedback(interaction: MutableInteractionSource, pressedScale: Float): Float {
    val pressed by interaction.collectIsPressedAsState()
    val haptics = rememberHaptics()
    LaunchedEffect(pressed) { if (pressed) haptics.play(Haptic.Light) }
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, swiftSpring(0.25, 0.5), label = "press")
    return scale
}

/**
 * Full-width pill button: solid black (primary) or black outline (secondary).
 * The label is shown with the button's text style and color (use [Text] inside).
 */
@Composable
fun PillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    label: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressFeedback(interaction, 0.96f)
    // Dims and brightens with the snappy spring when enabled changes, like SwiftUI's animated `.disabled`.
    val opacity by animateFloatAsState(if (enabled) 1f else 0.35f, swiftSpring(0.5, 0.15), label = "enabled")
    val foreground = if (primary) Color.White else Theme.ink
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = opacity
            }
            .background(if (primary) Theme.ink else Color.White.copy(alpha = 0.35f), CircleShape)
            .then(if (primary) Modifier else Modifier.border(2.dp, Theme.ink, CircleShape))
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides foreground,
            LocalTextStyle provides Theme.body(18f, FontWeight.Bold).copy(color = foreground),
        ) { label() }
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) = PillButton(onClick, modifier, primary, enabled) { Text(text) }

/**
 * A pill button that runs server work: shows a spinner and ignores taps until it finishes. Tells the
 * surrounding [BlocksWhileBusy] (if any) so it can block its other buttons meanwhile. The work runs in the
 * app's scope, like an iOS `Task`, so it finishes even if this screen goes away.
 */
@Composable
fun AsyncPillButton(
    action: suspend () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    label: @Composable () -> Unit,
) {
    var running by remember { mutableStateOf(false) }
    val group = LocalBusyGroup.current
    DisposableEffect(running, group) {
        if (running) group?.enter()
        onDispose { if (running) group?.leave() }
    }
    PillButton(
        onClick = {
            if (!running) {
                running = true
                CardGame3App.scope.launch {
                    try {
                        action()
                    } finally {
                        running = false
                    }
                }
            }
        },
        modifier = modifier,
        primary = primary,
        enabled = enabled,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.alpha(if (running) 0f else 1f)) { label() }
            if (running) Spinner()
        }
    }
}

@Composable
fun AsyncPillButton(
    text: String,
    action: suspend () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) = AsyncPillButton(action, modifier, primary, enabled) { Text(text) }

/** White circle icon button, like the "?" and leave buttons. Put an [androidx.compose.material3.Icon] or [Text] inside. */
@Composable
fun CircleButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressFeedback(interaction, 0.9f)
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.4f
            }
            .softShadow(CircleShape, Color.Black.copy(alpha = 0.08f), 10.dp, 4.dp)
            .background(Color.White, CircleShape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides Theme.ink,
            LocalTextStyle provides Theme.body(size.value * 0.4f, FontWeight.ExtraBold).copy(color = Theme.ink),
        ) {
            Box(Modifier.size(size * 0.45f), contentAlignment = Alignment.Center) { content() }
        }
    }
}

/** A small spinning arc in the current text color. */
@Composable
fun Spinner(modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    val spin = rememberInfiniteTransition(label = "spinner")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(800, easing = LinearEasing)), label = "angle")
    Canvas(modifier.size(20.dp).graphicsLayer { rotationZ = angle }) {
        val stroke = 3.dp.toPx()
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 0.7f * 360f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** How many [AsyncPillButton]s inside a [BlocksWhileBusy] are running. */
class BusyGroup {
    private var count by mutableIntStateOf(0)
    val busy: Boolean get() = count > 0
    fun enter() {
        count++
    }

    fun leave() {
        count--
    }
}

val LocalBusyGroup = compositionLocalOf<BusyGroup?> { null }

/** Blocks taps on everything inside while one of its [AsyncPillButton]s is running. */
@Composable
fun BlocksWhileBusy(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val group = remember { BusyGroup() }
    Box(modifier) {
        CompositionLocalProvider(LocalBusyGroup provides group) { content() }
        if (group.busy) {
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    },
            )
        }
    }
}

/** White pill text field. */
@Composable
fun PillField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = Theme.body(18f),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .softShadow(CircleShape, Color.Black.copy(alpha = 0.06f), 12.dp, 4.dp)
            .background(Color.White, CircleShape)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        textStyle = textStyle.copy(color = Theme.ink),
        singleLine = true,
        cursorBrush = SolidColor(Theme.ink),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { field ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = textStyle.copy(color = Color(0xFFC4C4C7)))
                field()
            }
        },
    )
}

/** The controls at the bottom of a screen. */
@Composable
fun BottomSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
