package com.azzamalrashed.cardgame3

import android.app.Application
import com.azzamalrashed.cardgame3.services.AppSettings
import com.azzamalrashed.cardgame3.services.Backend
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import com.google.firebase.functions.functions
import kotlinx.coroutines.MainScope

/** Starts Firebase and holds the one [Backend], which outlives activities (rotation keeps the game state). */
class CardGame3App : Application() {
    lateinit var backend: Backend
        private set

    override fun onCreate() {
        super.onCreate()
        AppSettings.load(this)
        if (BuildConfig.USE_EMULATORS) useEmulators()
        backend = Backend(this)
    }

    /**
     * Debug builds made with `-Pcardgame3.emulators` use the local emulators instead of live Firebase, like the
     * iOS "CardGame3 (Emulators)" scheme. 10.0.2.2 is the host machine, seen from the Android emulator.
     */
    private fun useEmulators() {
        val host = "10.0.2.2"
        Firebase.auth.useEmulator(host, 9099)
        Firebase.functions(Backend.REGION).useEmulator(host, 5001)
        Firebase.firestore.useEmulator(host, 8080)
        Firebase.firestore.firestoreSettings = firestoreSettings { setLocalCacheSettings(memoryCacheSettings {}) }
    }

    companion object {
        /** For work that must finish even if the screen that started it goes away (like iOS `Task`s). */
        val scope = MainScope()
    }
}
