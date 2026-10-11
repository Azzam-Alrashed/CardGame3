package com.azzamalrashed.cardgame3.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import com.azzamalrashed.cardgame3.design.EaseInOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.azzamalrashed.cardgame3.R
import com.azzamalrashed.cardgame3.design.AdaptiveSplit
import com.azzamalrashed.cardgame3.design.Blob
import com.azzamalrashed.cardgame3.design.BottomSheet
import com.azzamalrashed.cardgame3.design.Mood
import com.azzamalrashed.cardgame3.design.OnChange
import com.azzamalrashed.cardgame3.design.PillButton
import com.azzamalrashed.cardgame3.design.PillField
import com.azzamalrashed.cardgame3.design.PlayingCard
import com.azzamalrashed.cardgame3.design.SpeechBubble
import com.azzamalrashed.cardgame3.design.Theme
import com.azzamalrashed.cardgame3.design.swiftSpring
import com.azzamalrashed.cardgame3.screens.home.bobOffset
import com.azzamalrashed.cardgame3.screens.home.hasPlayerName
import com.azzamalrashed.cardgame3.screens.home.position
import com.azzamalrashed.cardgame3.services.Backend
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Each page's screen color. */
private val pageColors = listOf(Theme.lime, Theme.sunny, Theme.hotPink, Theme.grape, Theme.lime)

/** First-launch intro: the game in four swipeable pages, then the player's name. (iOS: OnboardingView.swift) */
@Composable
fun OnboardingScreen(backend: Backend, onFinish: () -> Unit) {
    val pager = rememberPagerState { pageColors.size }
    val lastPage = pageColors.size - 1
    // The page being shown or headed to: Next changes it at once, a swipe once it has gone far enough.
    val page = pager.targetPage
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val background by animateColorAsState(pageColors[page], tween(350, easing = EaseInOut), label = "page color")
    // The name field takes focus once its page has settled (see NamePage); leaving it puts the keyboard away.
    OnChange(page) { _, new -> if (new != lastPage) focusManager.clearFocus() }
    val nameFocused = page == lastPage && !pager.isScrollInProgress
    val next: () -> Unit = { scope.launch { pager.animateScrollToPage(pager.targetPage + 1) } }

    Box(Modifier.fillMaxSize().background(background)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { index ->
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                when (index) {
                    0 -> WelcomePage { NextControls(pageColors.size, page, next) }
                    1 -> BetPage { NextControls(pageColors.size, page, next) }
                    2 -> DealPage { NextControls(pageColors.size, page, next) }
                    3 -> RevealPage { NextControls(pageColors.size, page, next) }
                    else -> NamePage(backend, nameFocused, onFinish)
                }
            }
        }
        AnimatedVisibility(
            visible = page < lastPage,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PlainButton(
                text = stringResource(R.string.onboarding_skip),
                onClick = onFinish,
                style = Theme.body(16f, FontWeight.Bold),
                padding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

// Pages

@Composable
private fun WelcomePage(controls: @Composable ColumnScope.() -> Unit) {
    IntroPage(
        title = stringResource(R.string.wordmark),
        titleStyle = Theme.wordmark(64f),
        text = stringResource(R.string.onboarding_welcome_text),
        art = {
            val w = maxWidth
            val h = maxHeight
            val s = min(w, h)
            val r = s * 0.36f
            val pointsSize = with(LocalDensity.current) { (s * 0.16f).toSp() }
            Column(Modifier.position(w / 2, h / 2), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.onboarding_welcome_points), style = Theme.wordmark(0f).copy(fontSize = pointsSize))
                Text(
                    stringResource(R.string.onboarding_welcome_points_each),
                    modifier = Modifier.alpha(0.7f),
                    style = Theme.body(15f, FontWeight.Medium),
                )
            }
            val moods = listOf(Mood.Happy, Mood.Wink, Mood.Surprised, Mood.Happy, Mood.Sleepy, Mood.Wink)
            for (i in 0 until 6) {
                val angle = i / 6.0 * 2 * PI - PI / 2
                val dx = cos(angle).toFloat()
                val dy = sin(angle).toFloat()
                Blob(
                    color = Theme.color(forSeat = i),
                    size = r * 0.55f,
                    modifier = Modifier.position(w / 2 + r * dx, h / 2 + r * dy),
                    mood = moods[i],
                    look = Offset(-dx, -dy),
                    hair = i == 3,
                )
            }
            SpeechBubble(stringResource(R.string.onboarding_welcome_bubble), Modifier.position(w / 2 + r * 0.75f, h / 2 - r * 1.1f))
        },
        controls = controls,
    )
}

@Composable
private fun BetPage(controls: @Composable ColumnScope.() -> Unit) {
    IntroPage(
        title = stringResource(R.string.onboarding_bet_title),
        text = stringResource(R.string.onboarding_bet_text),
        art = {
            val w = maxWidth
            val h = maxHeight
            val cardW = min(w * 0.22f, h * 0.3f)
            FannedHand(listOf(13 to "S", 13 to "H", 12 to "D", 14 to "C"), cardW, Modifier.position(w / 2, h * 0.6f))
            Blob(Theme.blobColors[1], cardW * 0.9f, Modifier.position(w * 0.16f, h * 0.3f), Mood.Happy, Offset(1f, 0.5f))
            SpeechBubble(stringResource(R.string.onboarding_bet_bubble), Modifier.position(w * 0.3f, h * 0.1f))
            Blob(Theme.blobColors[6], cardW * 0.75f, Modifier.position(w * 0.85f, h * 0.28f), Mood.Sleepy)
            SpeechBubble(stringResource(R.string.onboarding_bet_out_bubble), Modifier.position(w * 0.76f, h * 0.08f))
        },
        controls = controls,
    )
}

@Composable
private fun DealPage(controls: @Composable ColumnScope.() -> Unit) {
    IntroPage(
        title = stringResource(R.string.onboarding_deal_title),
        text = stringResource(R.string.onboarding_deal_text),
        art = {
            val w = maxWidth
            val h = maxHeight
            val s = min(w, h)
            Blob(Theme.blobColors[3], s * 0.42f, Modifier.position(w * 0.66f, h * 0.58f), Mood.Wink, Offset(-0.6f, 0.3f), hair = true)
            Text(
                stringResource(R.string.onboarding_deal_boss),
                modifier = Modifier
                    .position(w * 0.66f, h * 0.58f + s * 0.26f)
                    .background(Theme.ink, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = Color.White,
                style = Theme.body(14f, FontWeight.Black).copy(letterSpacing = 2.sp),
            )
            Blob(Theme.blobColors[2], s * 0.26f, Modifier.position(w * 0.22f, h * 0.62f), Mood.Surprised, Offset(1f, -0.3f))
            SpeechBubble(stringResource(R.string.onboarding_deal_offer_bubble), Modifier.position(w * 0.3f, h * 0.3f))
            SpeechBubble(stringResource(R.string.onboarding_deal_accept_bubble), Modifier.position(w * 0.78f, h * 0.14f))
        },
        controls = controls,
    )
}

@Composable
private fun RevealPage(controls: @Composable ColumnScope.() -> Unit) {
    IntroPage(
        title = stringResource(R.string.onboarding_reveal_title),
        text = stringResource(R.string.onboarding_reveal_text),
        art = {
            Box(Modifier.align(Alignment.Center).padding(horizontal = 24.dp)) { HandRanking() }
        },
        controls = controls,
    )
}

@Composable
private fun NamePage(backend: Backend, focused: Boolean, onFinish: () -> Unit) {
    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(focused) { if (focused) focus.requestFocus() }
    val hasName = hasPlayerName(backend.playerName)
    IntroPage(
        title = stringResource(R.string.onboarding_name_title),
        text = stringResource(R.string.onboarding_name_text),
        art = {
            val w = maxWidth
            val h = maxHeight
            val s = min(w, h)
            Blob(Theme.blobColors[4], s * 0.5f, Modifier.position(w / 2, h * 0.58f), Mood.Happy, Offset(0f, 1f))
            SpeechBubble(
                if (hasName) stringResource(R.string.onboarding_name_hi, backend.playerName) else stringResource(R.string.onboarding_name_who),
                Modifier.position(w / 2 + s * 0.12f, h * 0.58f - s * 0.38f),
            )
        },
    ) {
        PillField(
            value = backend.playerName,
            onValueChange = { backend.playerName = it },
            placeholder = stringResource(R.string.onboarding_name_placeholder),
            modifier = Modifier.focusRequester(focus),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                focusManager.clearFocus()
                if (hasName) onFinish()
            }),
        )
        PillButton(stringResource(R.string.onboarding_lets_play), onClick = onFinish, enabled = hasName)
    }
}

// Pieces

/** One intro page: art on top (or left in landscape), words and buttons below. */
@Composable
private fun IntroPage(
    title: String,
    text: String,
    titleStyle: TextStyle = Theme.wordmark(36f),
    art: @Composable BoxWithConstraintsScope.() -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
) {
    AdaptiveSplit(main = { BoxWithConstraints(Modifier.fillMaxSize().padding(top = 48.dp), content = art) }) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = titleStyle, textAlign = TextAlign.Center)
            Text(text, modifier = Modifier.alpha(0.75f), style = Theme.body(17f, FontWeight.Medium), textAlign = TextAlign.Center)
        }
        BottomSheet(content = controls)
    }
}

@Composable
private fun NextControls(count: Int, current: Int, onNext: () -> Unit) {
    PageDots(count, current)
    PillButton(stringResource(R.string.onboarding_next), onClick = onNext)
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until count) {
            val width by animateDpAsState(if (i == current) 22.dp else 8.dp, swiftSpring(0.3), label = "dot width")
            val opacity by animateFloatAsState(if (i == current) 1f else 0.25f, swiftSpring(0.3), label = "dot opacity")
            Box(Modifier.size(width, 8.dp).background(Theme.ink.copy(alpha = opacity), CircleShape))
        }
    }
}

/** A few cards fanned from their bottoms, bobbing gently. */
@Composable
private fun FannedHand(cards: List<Pair<Int, String>>, width: Dp, modifier: Modifier = Modifier) {
    val bob = bobOffset()
    Box(modifier.graphicsLayer { translationY = bob.value.dp.toPx() }) {
        cards.forEachIndexed { i, (rank, suit) ->
            PlayingCard(
                rank = rank,
                suit = suit,
                modifier = Modifier.graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    rotationZ = i * 10f - 15f
                    translationX = (i - 1.5f) * width.toPx() * 0.35f
                },
                width = width,
            )
        }
    }
}

/** A text-only button (iOS's plain `Button`): the text dims while pressed. Also used by How to play. */
@Composable
internal fun PlainButton(text: String, onClick: () -> Unit, style: TextStyle, padding: PaddingValues, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(padding),
    ) {
        Text(text, modifier = Modifier.alpha(if (pressed) 0.3f else 1f), style = style)
    }
}
