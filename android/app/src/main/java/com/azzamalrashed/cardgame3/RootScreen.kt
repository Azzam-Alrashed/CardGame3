package com.azzamalrashed.cardgame3

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.azzamalrashed.cardgame3.design.Choice
import com.azzamalrashed.cardgame3.design.MessageDialog
import com.azzamalrashed.cardgame3.design.Theme
import com.azzamalrashed.cardgame3.design.swiftSpring
import com.azzamalrashed.cardgame3.model.Room
import com.azzamalrashed.cardgame3.screens.game.GameScreen
import com.azzamalrashed.cardgame3.screens.home.HomeScreen
import com.azzamalrashed.cardgame3.screens.lobby.LobbyScreen
import com.azzamalrashed.cardgame3.screens.onboarding.OnboardingScreen
import com.azzamalrashed.cardgame3.services.AppSettings
import com.azzamalrashed.cardgame3.services.Backend
import com.azzamalrashed.cardgame3.services.SoundPlayer

private enum class Screen { Onboarding, Home, Lobby, Game }

/** Shows the right screen for where the player is: intro, home, lobby, or game. (iOS: RootView.swift) */
@Composable
fun RootScreen(backend: Backend) {
    val status = backend.room?.status
    val screen = when (status) {
        null -> if (AppSettings.seenOnboarding) Screen.Home else Screen.Onboarding
        Room.Status.Lobby -> Screen.Lobby
        Room.Status.Playing, Room.Status.Finished -> Screen.Game
    }
    // Each screen gets its own bold color.
    val color by animateColorAsState(
        when (status) {
            null -> Theme.lime
            Room.Status.Lobby -> Theme.sunny
            else -> Theme.grape
        },
        tween(350),
        label = "screen color",
    )
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        SoundPlayer.preload(context.applicationContext)
        backend.signIn()
    }
    CompositionLocalProvider(
        LocalContentColor provides Theme.ink,
        LocalTextStyle provides Theme.body(17f).copy(color = Theme.ink),
    ) {
        Box(Modifier.fillMaxSize().background(color)) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    (fadeIn(swiftSpring(0.45)) + scaleIn(swiftSpring(0.45), initialScale = 0.96f)) togetherWith
                        (fadeOut(swiftSpring(0.45)) + scaleOut(swiftSpring(0.45), targetScale = 1.04f))
                },
                label = "screen",
            ) { target ->
                when (target) {
                    Screen.Onboarding -> OnboardingScreen(backend) { AppSettings.seenOnboarding = true }
                    Screen.Home -> HomeScreen(backend)
                    Screen.Lobby -> LobbyScreen(backend)
                    Screen.Game -> GameScreen(backend)
                }
            }
        }
        backend.errorMessage?.let { message ->
            MessageDialog(
                title = stringResource(R.string.error_title),
                message = message,
                choices = listOf(Choice(stringResource(R.string.ok)) {}),
                onDismiss = { backend.errorMessage = null },
            )
        }
    }
}
