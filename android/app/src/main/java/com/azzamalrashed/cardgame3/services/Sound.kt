package com.azzamalrashed.cardgame3.services

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.annotation.RawRes
import com.azzamalrashed.cardgame3.R
import kotlin.random.Random

/** Every sound in the app: the iOS set (ios/CardGame3/Resources/Assets.xcassets/Sounds), in res/raw. */
enum class Sound(
    /** One is picked at random each time, so repeated sounds don't feel mechanical. */
    val assets: List<Int>,
    /** Every file is normalized to the same peak, so loudness is set here. */
    val volume: Float,
    /** Slight random pitch for sounds that repeat a lot; music stays in tune. */
    val varies: Boolean = true,
) {
    Shuffle(listOf(R.raw.sfx_shuffle), 0.75f),
    Deal(listOf(R.raw.sfx_deal_1, R.raw.sfx_deal_2, R.raw.sfx_deal_3, R.raw.sfx_deal_4), 0.45f),
    Land(listOf(R.raw.sfx_land_1, R.raw.sfx_land_2), 0.7f),
    Flip(listOf(R.raw.sfx_flip_1, R.raw.sfx_flip_2), 0.8f),
    Fold(listOf(R.raw.sfx_fold_1, R.raw.sfx_fold_2), 0.6f),
    Chip(listOf(R.raw.sfx_chip_1, R.raw.sfx_chip_2, R.raw.sfx_chip_3), 0.7f),
    Chips(listOf(R.raw.sfx_chips_1, R.raw.sfx_chips_2, R.raw.sfx_chips_3), 0.8f),
    ChipsBig(listOf(R.raw.sfx_chips_big), 0.9f),
    AllIn(listOf(R.raw.sfx_all_in), 1f),
    Offer(listOf(R.raw.sfx_offer), 0.8f),
    Accept(listOf(R.raw.sfx_accept), 0.8f),
    Reject(listOf(R.raw.sfx_reject), 0.7f),
    Turn(listOf(R.raw.sfx_turn), 0.8f),
    Tick(listOf(R.raw.sfx_tick), 0.45f),
    Match(listOf(R.raw.sfx_match), 0.9f, varies = false),
    Join(listOf(R.raw.sfx_join), 0.6f),
    Toggle(listOf(R.raw.sfx_toggle), 0.6f),
    Boss(listOf(R.raw.sfx_boss), 0.8f, varies = false),
    Win(listOf(R.raw.sfx_win), 0.9f, varies = false),
    Lose(listOf(R.raw.sfx_lose), 0.8f, varies = false),
    Verdict(listOf(R.raw.sfx_verdict), 0.8f, varies = false),
    Quads(listOf(R.raw.sfx_quads), 1f, varies = false),
    Fanfare(listOf(R.raw.sfx_fanfare), 0.9f, varies = false),

    /** Darbuka: the drumroll before the boss reveals, and a deep center stroke. */
    Roll(listOf(R.raw.sfx_roll), 0.9f, varies = false),
    Doum(listOf(R.raw.sfx_doum), 1f),
}

/**
 * Plays sounds over whatever else is playing (no audio focus is taken, so music keeps going), and stays quiet
 * when the phone is on silent or vibrate, like iOS's ambient sounds and the silent switch.
 */
object SoundPlayer {
    private var pool: SoundPool? = null
    private var audio: AudioManager? = null
    /** Raw resource → loaded SoundPool id. */
    private val ids = mutableMapOf<Int, Int>()
    /** Streams still playing per sound, so a long one (the drumroll) can be stopped. */
    private val streams = mutableMapOf<Sound, MutableList<Int>>()

    /** Loads every file once so the first play of each sound isn't late. */
    fun preload(context: Context) {
        if (pool != null) return
        audio = context.getSystemService(AudioManager::class.java)
        val soundPool = SoundPool.Builder()
            .setMaxStreams(16)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        for (res in Sound.entries.flatMap { it.assets }) ids[res] = soundPool.load(context, res, 1)
        pool = soundPool
    }

    /** Silent and vibrate mode both mean quiet. */
    private val phoneIsSilent: Boolean get() = audio?.ringerMode?.let { it != AudioManager.RINGER_MODE_NORMAL } ?: false

    /** `rate` above 1 plays higher and faster (used for rising "match" pops). */
    fun play(sound: Sound, rate: Float = 1f, volume: Float = 1f) {
        val pool = pool ?: return
        if (!AppSettings.soundOn || phoneIsSilent) return
        @RawRes val asset = sound.assets.random()
        val id = ids[asset] ?: return
        val v = (sound.volume * volume).coerceIn(0f, 1f)
        val r = (rate * if (sound.varies) Random.nextDouble(0.94, 1.06).toFloat() else 1f).coerceIn(0.5f, 2f)
        val stream = pool.play(id, v, v, 1, 0, r)
        if (stream != 0) streams.getOrPut(sound) { mutableListOf() }.apply {
            add(stream)
            if (size > 8) removeAt(0)
        }
    }

    /** Stops a long sound early (the drumroll, when a reveal is skipped). */
    fun stop(sound: Sound) {
        val pool = pool ?: return
        streams.remove(sound)?.forEach { pool.stop(it) }
    }
}
