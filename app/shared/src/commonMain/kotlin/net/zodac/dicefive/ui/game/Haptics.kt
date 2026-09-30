package net.zodac.dicefive.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.zodac.dicefive.platform.HapticEffect
import net.zodac.dicefive.platform.HapticsPlayer
import net.zodac.dicefive.platform.LocalPlatformServices

/** Short vibrations fired alongside [SoundEffects] in [GameScreen]: one tick for a die being held
 * or unheld (the same tick for both directions, since the sounds already carry that distinction),
 * and a longer buzz spanning the cup shake. There's no release step to manage the way
 * [SoundEffects] needs one. */
class DiceHaptics(createPlayer: () -> HapticsPlayer) {

    /** Built on the first vibration actually fired, so a session with vibration off never looks up the vibrator. */
    private val player by lazy(createPlayer)
    private var playerCreated = false

    /** Set from the Settings screen's "Vibration" switch - see [SoundEffects.enabled] for why
     * this is a plain var rather than gating at the call site. */
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

/** A [DiceHaptics] scoped to the current composition. */
@Composable
fun rememberDiceHaptics(): DiceHaptics {
    val platform = LocalPlatformServices.current
    val haptics = remember { DiceHaptics(platform::createHapticsPlayer) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) haptics.cancel() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return haptics
}
