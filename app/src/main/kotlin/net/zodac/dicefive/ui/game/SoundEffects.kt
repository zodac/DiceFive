package net.zodac.dicefive.ui.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The two dice sound effects - the cup being shaken, and the dice landing once a roll resolves -
 * played through [SoundPool]: built for exactly this kind of sub-second one-shot clip, unlike
 * [android.media.MediaPlayer] which carries far more setup overhead than a clip this short needs.
 * Both clips are loaded once per instance; [release] tears the pool down when nothing needs it
 * any more - see [rememberSoundEffects], which owns that lifecycle for [GameScreen].
 *
 * PLACEHOLDER: there's no `res/raw/dice_shake.ogg` or `res/raw/dice_roll.ogg` to load yet, so
 * [shakeSoundId]/[rollSoundId] stay null and [playShake]/[playRoll] silently no-op. Once those
 * two files are dropped in, swap the `null`s below for `pool.load(context, R.raw.dice_shake, 1)`
 * and `pool.load(context, R.raw.dice_roll, 1)` respectively - that's the only code change needed.
 */
class SoundEffects(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val shakeSoundId: Int? = null
    private val rollSoundId: Int? = null

    fun playShake() {
        shakeSoundId?.let { pool.play(it, 1f, 1f, 0, 0, 1f) }
    }

    fun playRoll() {
        rollSoundId?.let { pool.play(it, 1f, 1f, 0, 0, 1f) }
    }

    fun release() {
        pool.release()
    }
}

/** A [SoundEffects] scoped to the current composition - loaded once and released when it leaves,
 * so a screen recomposing doesn't reload the clips or leak a pool behind it. The application
 * context, not the (possibly Activity) [LocalContext.current], since the pool can outlive any
 * single Activity instance across a configuration change. */
@Composable
fun rememberSoundEffects(): SoundEffects {
    val context = LocalContext.current.applicationContext
    val soundEffects = remember { SoundEffects(context) }
    DisposableEffect(Unit) {
        onDispose { soundEffects.release() }
    }
    return soundEffects
}
