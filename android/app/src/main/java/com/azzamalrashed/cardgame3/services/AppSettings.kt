package com.azzamalrashed.cardgame3.services

import androidx.core.content.edit
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.azzamalrashed.cardgame3.model.HiddenNames

/** Small settings kept across launches (iOS keeps them in UserDefaults / @AppStorage, under the same names). */
object AppSettings {
    lateinit var prefs: SharedPreferences
        private set

    fun load(context: Context) {
        prefs = context.getSharedPreferences("cardgame3", Context.MODE_PRIVATE)
        seenOnboardingState = prefs.getBoolean("seenOnboarding", false)
        soundOnState = prefs.getBoolean("soundOn", true)
        HiddenNames.load(prefs)
    }

    private var seenOnboardingState by mutableStateOf(false)
    private var soundOnState by mutableStateOf(true)

    /** The first-launch intro has been seen (or skipped). "Replay the intro" sets it back. */
    var seenOnboarding: Boolean
        get() = seenOnboardingState
        set(value) {
            seenOnboardingState = value
            prefs.edit { putBoolean("seenOnboarding", value) }
        }

    /** Turned off with the speaker button; on by default. */
    var soundOn: Boolean
        get() = soundOnState
        set(value) {
            soundOnState = value
            prefs.edit { putBoolean("soundOn", value) }
        }
}
