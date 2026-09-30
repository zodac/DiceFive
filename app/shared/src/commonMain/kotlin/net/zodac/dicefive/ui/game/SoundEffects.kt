package net.zodac.dicefive.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SoundEffect
import net.zodac.dicefive.platform.SoundPlayer

/** Quieter than the shake/roll clips - full volume read as too sharp for a click that fires on
 * every single tap rather than once per roll. */
private const val HOLD_VOLUME = 0.35f

/**
 * The dice sound effects - the cup being shaken, the dice landing once a roll resolves, a die
 * being held or unheld, and the end-of-game celebration fanfare - played through the platform's
 * [SoundPlayer]. [release] tears the player down when nothing needs it any more - see
 * [rememberSoundEffects], which owns that lifecycle for [GameScreen] and [GameOverScreen].
 */
class SoundEffects(createPlayer: () -> SoundPlayer) {

    /** Built (and its clips decoded) on the first sound actually played, so a session with sound
     * off never pays for it - and one where it's switched on mid-game loads it then. */
    private val player by lazy(createPlayer)
    private var playerCreated = false

    /** Set from the Settings screen's "Sound effects" switch - every play call below returns
     * before touching the player while this is false, since the setting can flip mid-session
     * without recreating this instance. */
    var enabled: Boolean = true

    private fun playSound(effect: SoundEffect, volume: Float) {
        if (!enabled) return
        playerCreated = true
        player.play(effect, volume)
    }

    fun playShake() = playSound(SoundEffect.CUP_SHAKE, 1f)

    fun playRoll() = playSound(SoundEffect.MAT_LANDING, 1f)

    fun playHold() = playSound(SoundEffect.HOLD, HOLD_VOLUME)

    fun playUnhold() = playSound(SoundEffect.UNHOLD, HOLD_VOLUME)

    fun playCelebration() = playSound(SoundEffect.CELEBRATION, 1f)

    /** Holds a clip still playing (the fanfare, say) while the app is in the background; [resume] carries on. */
    fun pause() {
        if (playerCreated) player.pause()
    }

    fun resume() {
        if (playerCreated) player.resume()
    }

    fun release() {
        if (playerCreated) player.release()
    }
}

/** A [SoundEffects] scoped to the current composition - loaded once and released when it leaves,
 * so a screen recomposing doesn't reload the clips or leak a player behind it. */
@Composable
fun rememberSoundEffects(): SoundEffects {
    val platform = LocalPlatformServices.current
    val soundEffects = remember { SoundEffects(platform::createSoundPlayer) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // Nothing sounds in the background: a clip under way is held, and carries on when the app is back.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> soundEffects.pause()
                Lifecycle.Event.ON_RESUME -> soundEffects.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            soundEffects.release()
        }
    }
    return soundEffects
}
