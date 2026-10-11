package com.azzamalrashed.cardgame3.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azzamalrashed.cardgame3.R
import com.azzamalrashed.cardgame3.services.AppSettings
import com.azzamalrashed.cardgame3.services.Sound
import com.azzamalrashed.cardgame3.services.SoundPlayer

/** Speaker button that turns all sounds on or off. */
@Composable
fun SoundToggle(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val on = AppSettings.soundOn
    CircleIconButton(
        icon = if (on) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
        contentDescription = stringResource(if (on) R.string.sound_turn_off else R.string.sound_turn_on),
        onClick = {
            AppSettings.soundOn = !on
            SoundPlayer.play(Sound.Toggle)
        },
        modifier = modifier,
        size = size,
    )
}
