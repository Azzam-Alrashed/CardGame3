package com.azzamalrashed.cardgame3.services

import androidx.core.content.edit
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.azzamalrashed.cardgame3.R
import com.azzamalrashed.cardgame3.model.Card
import com.azzamalrashed.cardgame3.model.DealClock
import com.azzamalrashed.cardgame3.model.DealTimeline
import com.azzamalrashed.cardgame3.model.Decode
import com.azzamalrashed.cardgame3.model.PublicRound
import com.azzamalrashed.cardgame3.model.RevealClock
import com.azzamalrashed.cardgame3.model.RevealTimeline
import com.azzamalrashed.cardgame3.model.Room
import com.azzamalrashed.cardgame3.model.ms
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Talks to Firebase: signs in, calls the room functions, and keeps the current room live.
 * Port of ios/CardGame3/Services/Backend.swift. Its state is Compose state, so screens redraw when it changes.
 * Lives as long as the app (see `CardGame3App`), so rotating the phone keeps everything.
 */
class Backend(private val context: Context) {
    /** Runs moves the way iOS's unstructured `Task`s do: they finish even if the screen that started them goes away. */
    val scope = MainScope()

    var uid: String? by mutableStateOf(Firebase.auth.currentUser?.uid)
        private set
    var room: Room? by mutableStateOf(null)
        private set

    /** This player's 4 cards for the current round (readable only by them). */
    var myCards: List<Card> by mutableStateOf(emptyList())
        private set

    /** The round `myCards` were dealt for: the room and the cards arrive separately after a deal. */
    var myCardsRound: Int? by mutableStateOf(null)
        private set

    /**
     * When this phone saw the current round dealt, to animate the deal. Null when the round was already dealt as
     * we started listening (opening the app mid-round): nothing to animate.
     */
    var dealtAt: Seen? by mutableStateOf(null)
        private set

    /** Which of my cards (by position) I've turned over this round. Kept across launches. */
    var flipped: Set<Int> by mutableStateOf(emptySet())
        private set

    /** When this phone saw the current round's showdown, to stage the reveal. Null when it was over before we started listening. */
    var revealedAt: Seen? by mutableStateOf(null)
        private set

    var errorMessage: String? by mutableStateOf(null)

    /** A round number and when (epoch ms) this phone saw something happen to it. */
    data class Seen(val round: Int, val at: Long)

    private val prefs = AppSettings.prefs

    private var playerNameState by mutableStateOf(prefs.getString("playerName", "") ?: "")
    var playerName: String
        get() = playerNameState
        set(value) {
            playerNameState = value
            prefs.edit { putString("playerName", value) }
        }

    private var awayRoomCodeState by mutableStateOf(prefs.getString("awayRoomCode", null))

    /** Room code of a game this player stepped away from (shown on Home as "Back to game"). */
    var awayRoomCode: String?
        get() = awayRoomCodeState
        private set(value) {
            awayRoomCodeState = value
            prefs.edit { putString("awayRoomCode", value) }
        }

    /** Same region as the Cloud Functions (Doha). */
    private val functions = Firebase.functions(REGION)
    private var roomListener: ListenerRegistration? = null
    private var handListener: ListenerRegistration? = null

    /** True while this player is leaving a room on purpose (so losing access isn't news). */
    private var leaving = false

    /** Waits for flips to settle before telling the table, so flipping all four is one call. */
    private var peekJob: Job? = null

    /** Moves this phone sent that haven't shown up in the room yet (see [roundCall]). */
    private val landings = mutableListOf<Landing>()

    val isHost: Boolean get() = room != null && room?.hostId == uid

    // Sign-in

    /** Anonymous sign-in, as on iOS. */
    suspend fun signIn() {
        // A saved session from another backend (e.g. emulators vs live) can't refresh: start over.
        Firebase.auth.currentUser?.let { user ->
            try {
                user.getIdToken(true).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirebaseNetworkException) {
                // Being offline must not cost a player their seat.
            } catch (e: Exception) {
                // Any other failure means the session is no good here (expired, or from the other backend).
                Firebase.auth.signOut()
                uid = null
            }
        }
        if (uid == null) {
            attempt {
                val result = Firebase.auth.signInAnonymously().await()
                uid = result.user?.uid
            }
        }
        // Back to the table after the app was closed mid-game.
        if (room == null) prefs.getString("roomCode", null)?.let { listen(it) }
    }

    // Rooms

    suspend fun createRoom() = attempt {
        val code = callForCode("createRoom", mapOf("name" to playerName))
        listen(code)
    }

    suspend fun joinRoom(code: String) = attempt {
        val joined = callForCode("joinRoom", mapOf("code" to code, "name" to playerName))
        listen(joined)
    }

    suspend fun leaveRoom() {
        val code = room?.code ?: return
        leaving = true
        try {
            attempt {
                call("leaveRoom", mapOf("code" to code))
                stopListening()
            }
        } finally {
            leaving = false
        }
    }

    suspend fun addAiPlayer() {
        val code = room?.code ?: return
        attempt { call("addAiPlayer", mapOf("code" to code)) }
    }

    /** Host only, in the lobby: removes another player, person or AI. */
    suspend fun removePlayer(uid: String) {
        val code = room?.code ?: return
        attempt { call("removePlayer", mapOf("code" to code, "playerId" to uid)) }
    }

    /** After a game: opens a new lobby with the same AI players, or joins the one someone already opened. */
    suspend fun rematch() {
        val code = room?.code ?: return
        attempt {
            val newCode = callForCode("rematch", mapOf("code" to code))
            listen(newCode)
        }
    }

    suspend fun startGame() {
        val code = room?.code ?: return
        attempt { call("startGame", mapOf("code" to code)) }
    }

    /** Leaves the screen of a finished game (the server keeps the room as a record). */
    fun goHome() = stopListening()

    /** Reports another player's name for review. Returns whether the report was sent. */
    suspend fun report(uid: String, reason: ReportReason): Boolean {
        val code = room?.code ?: return false
        var sent = false
        attempt {
            call("reportPlayer", mapOf("code" to code, "playerId" to uid, "reason" to reason.wire))
            sent = true
        }
        return sent
    }

    // Away

    /** Step away from a game in progress: a bot plays for you until you come back. */
    suspend fun leaveGame() {
        val code = room?.code ?: return
        attempt {
            call("setAway", mapOf("code" to code, "away" to true))
            stopListening()
            awayRoomCode = code
        }
    }

    /** At the table while a bot plays your seat (your turn ran out): take it back. */
    suspend fun takeSeatBack() {
        val me = uid ?: ""
        roundCall("setAway", mapOf("away" to false), landed = { !it.isAway(me) })
    }

    /** Back from Home: take your seat back from the bot. */
    suspend fun returnToGame() {
        val code = awayRoomCode ?: return
        attempt {
            try {
                call("setAway", mapOf("code" to code, "away" to false))
                listen(code)
            } catch (e: Exception) {
                // The game ended or the room is gone: nothing to go back to.
                if (e !is CancellationException) awayRoomCode = null
                throw e
            }
            awayRoomCode = null
        }
    }

    // Playing

    suspend fun bet(amount: Int) {
        val me = uid ?: ""
        roundCall("bet", mapOf("amount" to amount), landed = landedWhen { it.bets[me] == amount })
    }

    suspend fun withdraw() {
        val me = uid ?: ""
        roundCall("withdraw", landed = landedWhen { me in it.withdrawn })
    }

    suspend fun makeOffer(amount: Int) {
        val me = uid ?: ""
        roundCall("makeOffer", mapOf("amount" to amount), landed = landedWhen { it.offers[me] == amount || it.deals[me] != null })
    }

    suspend fun answerOffer(from: String, accept: Boolean) {
        roundCall("answerOffer", mapOf("from" to from, "accept" to accept), landed = landedWhen { it.offers[from] == null })
    }

    suspend fun reveal() = roundCall("reveal", landed = landedWhen { it.phase == PublicRound.Phase.Finished })

    private var timeUpSent: Int? = null

    /** Called by every phone when its countdown hits zero; the server checks the real deadline. */
    suspend fun timerRanOut() {
        val round = room?.round ?: return
        if (round.phase != PublicRound.Phase.Deals || timeUpSent == round.roundNumber) return
        timeUpSent = round.roundNumber
        roundCall("timeUp", quiet = true)
    }

    suspend fun nextRound() {
        val number = room?.round?.roundNumber ?: return
        roundCall("nextRound", mapOf("roundNumber" to number), landed = landedWhen { false })
    }

    // Looking at cards

    /** Turns over some of my cards. The table sees how many I've looked at (their small cards lift). */
    fun flip(cards: List<Int>) {
        val code = room?.code ?: return
        val round = room?.round?.roundNumber ?: return
        val before = flipped.size
        flipped = flipped + cards
        if (flipped.size <= before) return
        prefs.edit {
            putString("flippedRound", "$code:$round")
            putString("flippedCards", flipped.joinToString(","))
        }
        peekJob?.cancel()
        peekJob = scope.launch {
            delay(400)
            roundCall("peek", mapOf("roundNumber" to round, "count" to flipped.size), quiet = true)
        }
    }

    /** The deal this phone is animating for the current round, if any. */
    val dealClock: DealClock?
        get() {
            val round = room?.round ?: return null
            val dealt = dealtAt ?: return null
            if (dealt.round != round.roundNumber) return null
            val seats = room?.table?.seats?.map { it.id } ?: return null
            return DealClock(DealTimeline.of(seats, round.dealerId), dealt.at, dealt.round)
        }

    /** The showdown this phone is staging for the current round, if any. */
    val revealClock: RevealClock?
        get() {
            val room = room ?: return null
            val round = room.round ?: return null
            val seen = revealedAt ?: return null
            if (seen.round != round.roundNumber) return null
            val timeline = RevealTimeline.of(round, room.players.map { it.uid }) ?: return null
            return RevealClock(timeline, seen.at, seen.round)
        }

    /** Jumps this phone's reveal to the verdict. (The next round still waits for everyone else's.) */
    fun skipReveal() {
        val clock = revealClock ?: return
        val atVerdict = System.currentTimeMillis() - ms(clock.timeline.verdictAt)
        if (atVerdict < clock.start) revealedAt = Seen(clock.round, atVerdict)
    }

    /** Notes when a round is dealt or revealed (to animate them) and picks up which cards I'd turned over. */
    private fun roomChanged(old: Room?, new: Room?) {
        val round = new?.round ?: return
        val before = old?.round
        if (before != null && before.roundNumber == round.roundNumber &&
            before.phase != PublicRound.Phase.Finished && round.phase == PublicRound.Phase.Finished
        ) {
            revealedAt = Seen(round.roundNumber, System.currentTimeMillis())
        }
        if (round.roundNumber != old?.round?.roundNumber) {
            // The first snapshot after listening shows a round already in progress: nothing to animate.
            if (old != null) dealtAt = Seen(round.roundNumber, System.currentTimeMillis())
            val saved = if (prefs.getString("flippedRound", null) == "${new.code}:${round.roundNumber}") {
                prefs.getString("flippedCards", "").orEmpty().split(",").mapNotNull { it.toIntOrNull() }.toSet()
            } else {
                emptySet()
            }
            flipped = saved
        }
    }

    /**
     * Sends a move. With `landed`, returns as soon as the room shows the move rather than when the call does: the
     * server saves the move first, then spends about a second scheduling the bots' next wake-up before it answers.
     * A failed call still returns (and shows its error) as before.
     */
    private suspend fun roundCall(
        name: String,
        extra: Map<String, Any> = emptyMap(),
        quiet: Boolean = false,
        landed: ((Room) -> Boolean)? = null,
    ) {
        val room = room ?: return
        val data = extra + ("code" to room.code)
        // Already showing (e.g. the same offer again): only the call can say it went through.
        val landing = landed?.let { if (it(room)) null else Landing(it) }
        if (landing != null) landings += landing
        val call = scope.launch {
            try {
                call(name, data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!quiet) errorMessage = message(e)
            }
            landing?.finish()
            landings.removeAll { it.isDone }
        }
        if (landing != null) landing.await() else call.join()
    }

    /** The move shows in the room: `check` holds for the current round, or the table has moved past it. */
    private fun landedWhen(check: (PublicRound) -> Boolean): ((Room) -> Boolean)? {
        val number = room?.round?.roundNumber ?: return null
        return { room ->
            val round = room.round
            if (room.status != Room.Status.Playing || round == null || round.roundNumber != number) true else check(round)
        }
    }

    // Helpers

    private suspend fun call(name: String, data: Map<String, Any>): Any? =
        functions.getHttpsCallable(name).call(data).await().getData()

    private suspend fun callForCode(name: String, data: Map<String, Any>): String =
        (call(name, data) as? Map<*, *>)?.get("code") as? String ?: throw IllegalStateException(string(R.string.error_bad_response))

    private fun listen(code: String) {
        stopListening()
        prefs.edit { putString("roomCode", code) }
        val ref = Firebase.firestore.collection("rooms").document(code)
        roomListener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // No longer allowed to read this room: we left it, or the host removed us.
                if (!leaving && room?.status == Room.Status.Lobby) errorMessage = string(R.string.error_removed)
                stopListening()
                return@addSnapshotListener
            }
            // A missing document means the room was deleted.
            val new = Decode.room(snapshot?.data)
            roomChanged(room, new)
            room = new
            if (new != null) {
                landings.filter { it.landed(new) }.forEach { it.finish() }
                landings.removeAll { it.isDone }
            }
        }
        val me = uid ?: return
        handListener = ref.collection("hands").document(me).addSnapshotListener { snapshot, _ ->
            val (cards, round) = Decode.hand(snapshot?.data)
            myCards = cards
            myCardsRound = round
        }
    }

    private fun stopListening() {
        roomListener?.remove()
        roomListener = null
        handListener?.remove()
        handListener = null
        room = null
        myCards = emptyList()
        myCardsRound = null
        dealtAt = null
        revealedAt = null
        flipped = emptySet()
        peekJob?.cancel()
        landings.forEach { it.finish() }
        landings.clear()
        prefs.edit { remove("roomCode") }
    }

    /** Runs a backend call and turns any failure into a message the UI can show. */
    private suspend fun attempt(work: suspend () -> Unit) {
        errorMessage = null
        try {
            work()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = message(e)
        }
    }

    private fun message(e: Exception): String = e.localizedMessage ?: e.toString()

    private fun string(@StringRes id: Int) = context.getString(id)

    companion object {
        const val REGION = "me-central1"
    }
}

/**
 * A move this phone sent, waiting to show up in the room. Done when a room update shows it, or when the call
 * returns (it failed, or the update is slow).
 */
private class Landing(val landed: (Room) -> Boolean) {
    private val done = CompletableDeferred<Unit>()
    val isDone: Boolean get() = done.isCompleted
    suspend fun await() = done.await()
    fun finish() {
        done.complete(Unit)
    }
}

/** Why a player's name was reported (matches the server's `REPORT_REASONS`). */
enum class ReportReason(val wire: String, @StringRes val label: Int) {
    Offensive("offensive", R.string.report_offensive),
    Impersonation("impersonation", R.string.report_impersonation),
    Other("other", R.string.report_other),
}
