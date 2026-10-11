package com.azzamalrashed.cardgame3.screens.game

import com.azzamalrashed.cardgame3.model.Room
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMenuTest {
    private val room = Room(
        code = "QWKA",
        hostId = "me",
        status = Room.Status.Lobby,
        players = listOf(Room.Player("me", "Azzam"), Room.Player("sami", "Sami"), Room.Player("bot", "Bot Fahd 🤖")),
        table = null,
        round = null,
        gameOver = null,
        away = null,
        aiPlayers = listOf("bot"),
        peeks = null,
        rematchCode = null,
        rematchBy = null,
    )

    @Test
    fun onlyForOtherPeople() {
        assertTrue(hasPlayerMenu("sami", me = "me", room))
        assertFalse(hasPlayerMenu("me", me = "me", room))
        assertFalse(hasPlayerMenu("bot", me = "me", room))
    }

    @Test
    fun beforeSignInEveryPersonHasIt() {
        assertTrue(hasPlayerMenu("me", me = null, room))
        assertFalse(hasPlayerMenu("bot", me = null, room))
    }
}
