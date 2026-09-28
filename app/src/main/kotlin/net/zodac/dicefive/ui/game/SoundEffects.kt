package net.zodac.dicefive.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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
class SoundEffects(private val player: SoundPlayer) {

    /** Set from the Settings screen's "Sound effects" switch - every play call below silently
     * no-ops while this is false, rather than the player never being loaded, since the setting can
     * flip mid-session without recreating this instance. */
    var enabled: Boolean = true

    private fun playSound(effect: SoundEffect, volume: Float) {
        if (enabled) player.play(effect, volume)
    }

    fun playShake() = playSound(SoundEffect.CUP_SHAKE, 1f)

    fun playRoll() = playSound(SoundEffect.MAT_LANDING, 1f)

    fun playHold() = playSound(SoundEffect.HOLD, HOLD_VOLUME)

    fun playUnhold() = playSound(SoundEffect.UNHOLD, HOLD_VOLUME)

    fun playCelebration() = playSound(SoundEffect.CELEBRATION, 1f)

    fun release() {
        player.release()
    }
}

/** A [SoundEffects] scoped to the current composition - loaded once and released when it leaves,
 * so a screen recomposing doesn't reload the clips or leak a player behind it. */
@Composable
fun rememberSoundEffects(): SoundEffects {
    val platform = LocalPlatformServices.current
    val soundEffects = remember { SoundEffects(platform.createSoundPlayer()) }
    DisposableEffect(Unit) {
        onDispose { soundEffects.release() }
    }
    return soundEffects
}
