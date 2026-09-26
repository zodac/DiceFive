package net.zodac.dicefive.ui.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import net.zodac.dicefive.R

/**
 * The dice sound effects - the cup being shaken, the dice landing once a roll resolves, a die
 * being held or unheld, and the end-of-game celebration fanfare - played through [SoundPool]:
 * built for exactly this kind of one-shot clip, unlike [android.media.MediaPlayer] which carries
 * far more setup overhead. All clips are loaded once per instance; [release] tears the pool down
 * when nothing needs it any more - see [rememberSoundEffects], which owns that lifecycle for
 * [GameScreen] and [GameOverScreen].
 */
/** Quieter than the shake/roll clips - full volume read as too sharp for a click that fires on
 * every single tap rather than once per roll. */
private const val HOLD_VOLUME = 0.35f

class SoundEffects(context: Context) {

    /** Set from the Settings screen's "Sound effects" switch - every play call below silently
     * no-ops while this is false, rather than the pool never being loaded, since the setting can
     * flip mid-session without recreating this instance. */
    var enabled: Boolean = true

    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val shakeSoundId: Int? = pool.load(context, R.raw.cup_shake, 1)
    private val rollSoundId: Int? = pool.load(context, R.raw.mat_landing, 1)
    private val holdSoundId: Int? = pool.load(context, R.raw.hold, 1)
    private val unholdSoundId: Int? = pool.load(context, R.raw.unhold, 1)
    private val celebrationSoundId: Int? = pool.load(context, R.raw.celebration, 1)

    // pool.load() above only kicks off an async decode - it does NOT mean the clip is ready to
    // play yet, and pool.play() on a sample that isn't decoded yet is a silent no-op (no sound,
    // no exception). That race is invisible for the short SFX above, which never fire until well
    // after this pool was constructed (a roll needs a tap; a hold needs the dice on the felt) -
    // but celebration.ogg is played from GameOverScreen's very first frame, right as this whole
    // instance is created, so it used to lose the race and play nothing. This listener plays it
    // (or any sound requested too early) the instant its own decode actually finishes.
    private val loadedSoundIds = mutableSetOf<Int>()
    private val pendingVolumeById = mutableMapOf<Int, Float>()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status != 0) return@setOnLoadCompleteListener
            loadedSoundIds += sampleId
            pendingVolumeById.remove(sampleId)?.let { volume -> pool.play(sampleId, volume, volume, 0, 0, 1f) }
        }
    }

    private fun playSound(id: Int?, volume: Float) {
        if (!enabled || id == null) return
        if (id in loadedSoundIds) {
            pool.play(id, volume, volume, 0, 0, 1f)
        } else {
            pendingVolumeById[id] = volume
        }
    }

    fun playShake() = playSound(shakeSoundId, 1f)

    fun playRoll() = playSound(rollSoundId, 1f)

    fun playHold() = playSound(holdSoundId, HOLD_VOLUME)

    fun playUnhold() = playSound(unholdSoundId, HOLD_VOLUME)

    fun playCelebration() = playSound(celebrationSoundId, 1f)

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
