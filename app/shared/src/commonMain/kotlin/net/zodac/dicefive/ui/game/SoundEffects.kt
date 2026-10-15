package net.zodac.dicefive.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.concurrent.Volatile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SoundEffect
import net.zodac.dicefive.platform.SoundPlayer
import net.zodac.dicefive.ui.common.delayWhileResumed

/** Quieter than the shake/roll clips - full volume read as too sharp for a click that fires on
 * every single tap rather than once per roll. */
private const val HOLD_VOLUME = 0.35f

/**
 * The dice sound effects - the cup being shaken, the dice landing once a roll resolves (and a golden egg's cluck), a die
 * being held or unheld, and the end-of-game celebration fanfare - played through the platform's
 * [SoundPlayer]. [release] tears the player down when nothing needs it any more - see
 * [rememberAppSoundEffects], which owns that lifecycle for the whole app.
 */
class SoundEffects(createPlayer: () -> SoundPlayer) {

    /** Built (and its clips decoded) by [load], or else on the first sound actually played, so a session with
     * sound off never pays for it - and one where it's switched on mid-game loads it then. */
    private val player by lazy(createPlayer)

    @Volatile
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

    /** Builds the player and starts decoding its clips ahead of the first sound. Safe off the main thread. */
    fun load() {
        player
        playerCreated = true
    }

    /** A roll's shake or landing clip - see [shakeSound] and [landingSounds]. */
    fun playRollSound(effect: SoundEffect) = playSound(effect, 1f)

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

/** How long after launch, with the app in front, the app's [SoundEffects] are loaded - once the menu has settled. */
private const val SOUND_LOAD_DELAY_MILLIS = 1000L

/** The app's one [SoundEffects] - see [rememberAppSoundEffects] - or null outside the app (a lone screen in a test). */
val LocalSoundEffects = staticCompositionLocalOf<SoundEffects?> { null }

/**
 * The [SoundEffects] every screen plays through: the app's one ([LocalSoundEffects]), so its clips are
 * decoded once rather than on each game's first roll and again for its fanfare. Outside the app, one of
 * its own, scoped to the current composition and released when it leaves.
 */
@Composable
fun rememberSoundEffects(): SoundEffects = LocalSoundEffects.current ?: rememberScopedSoundEffects()

/**
 * The app's one [SoundEffects], for [LocalSoundEffects]: loaded off the main thread a moment after
 * launch when [soundEnabled] says sound is on (otherwise on its first sound), held while the app is in the
 * background and released when the app leaves composition.
 */
@Composable
fun rememberAppSoundEffects(soundEnabled: Flow<Boolean>): SoundEffects {
    val soundEffects = rememberScopedSoundEffects()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(soundEffects) {
        lifecycle.delayWhileResumed(SOUND_LOAD_DELAY_MILLIS)
        if (soundEnabled.first()) withContext(Dispatchers.Default) { soundEffects.load() }
    }
    return soundEffects
}

/** A [SoundEffects] scoped to the current composition - loaded once and released when it leaves,
 * so a screen recomposing doesn't reload the clips or leak a player behind it. */
@Composable
private fun rememberScopedSoundEffects(): SoundEffects {
    val platform = LocalPlatformServices.current
    val soundEffects = remember(platform) { SoundEffects(platform::createSoundPlayer) }
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
