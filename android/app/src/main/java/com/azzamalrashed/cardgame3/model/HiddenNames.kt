package com.azzamalrashed.cardgame3.model

import androidx.core.content.edit
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Names this player hid on this phone (they show as "Player 3" everywhere). Kept across launches. */
object HiddenNames {
    private const val KEY = "hiddenNames"
    private var prefs: SharedPreferences? = null

    var ids: Set<String> by mutableStateOf(emptySet())
        private set

    fun load(prefs: SharedPreferences) {
        this.prefs = prefs
        ids = prefs.getStringSet(KEY, emptySet()).orEmpty().toSet()
    }

    fun contains(uid: String): Boolean = uid in ids

    fun set(uid: String, hidden: Boolean) {
        ids = if (hidden) ids + uid else ids - uid
        prefs?.edit { putStringSet(KEY, ids) }
    }
}
