package net.zodac.dicefive.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import net.zodac.dicefive.platform.HapticEffect
import net.zodac.dicefive.platform.HapticsPlayer
import kotlinx.coroutines.flow.Flow
import net.zodac.dicefive.platform.LocalPlatformServices

/** Short vibrations fired alongside [SoundEffects] in [GameScreen]: one tick for a die being held
 * or unheld (the same tick for both directions, since the sounds already carry that distinction),
 * and a longer buzz spanning the cup shake. There's no release step to manage the way
 * [SoundEffects] needs one. */
class DiceHaptics(createPlayer: () -> HapticsPlayer) {

    /** Built on the first vibration actually fired, so a session with vibration off never looks up the vibrator. */
    private val player by lazy(createPlayer)
    private var playerCreated = false

    /** Follows the Settings screen's "Vibration" switch, set once by [rememberAppHaptics] - see [SoundEffects.enabled]
     * for why this is a plain var rather than gating at the call site. */
    var enabled: Boolean = true

    fun playHoldTick() {
        if (enabled) play(HapticEffect.HOLD_TICK)
    }

    fun playShakeBuzz() {
        if (enabled) play(HapticEffect.SHAKE_BUZZ)
    }

    private fun play(effect: HapticEffect) {
        playerCreated = true
        player.play(effect)
    }

    /** Stops a buzz under way - the app has gone to the background. */
    fun cancel() {
        if (playerCreated) player.cancel()
    }
}

/** The app's one [DiceHaptics] - see [rememberAppHaptics] - or null outside the app (a lone screen in a test). */
val LocalDiceHaptics = staticCompositionLocalOf<DiceHaptics?> { null }

/** The [DiceHaptics] every screen vibrates through: the app's one ([LocalDiceHaptics]), or else one of its own. */
@Composable
fun rememberDiceHaptics(): DiceHaptics = LocalDiceHaptics.current ?: rememberScopedDiceHaptics()

/** The app's one [DiceHaptics], for [LocalDiceHaptics]. It follows [vibrationEnabled] from then on, so no screen sets it. */
@Composable
fun rememberAppHaptics(vibrationEnabled: Flow<Boolean>): DiceHaptics {
    val haptics = rememberScopedDiceHaptics()
    // Still until the setting has been read.
    val enabled by vibrationEnabled.collectAsState(initial = false)
    haptics.enabled = enabled
    return haptics
}

/** A [DiceHaptics] scoped to the current composition. */
@Composable
private fun rememberScopedDiceHaptics(): DiceHaptics {
    val platform = LocalPlatformServices.current
    val haptics = remember { DiceHaptics(platform::createHapticsPlayer) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { haptics.cancel() }
    return haptics
}
