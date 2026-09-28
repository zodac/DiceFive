package net.zodac.dicefive.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import net.zodac.dicefive.platform.HapticEffect
import net.zodac.dicefive.platform.HapticsPlayer
import net.zodac.dicefive.platform.LocalPlatformServices

/** Short vibrations fired alongside [SoundEffects] in [GameScreen]: one tick for a die being held
 * or unheld (the same tick for both directions, since the sounds already carry that distinction),
 * and a longer buzz spanning the cup shake. There's no release step to manage the way
 * [SoundEffects] needs one. */
class DiceHaptics(private val player: HapticsPlayer) {

    /** Set from the Settings screen's "Vibration" switch - see [SoundEffects.enabled] for why
     * this is a plain var rather than gating at the call site. */
    var enabled: Boolean = true

    fun playHoldTick() {
        if (enabled) player.play(HapticEffect.HOLD_TICK)
    }

    fun playShakeBuzz() {
        if (enabled) player.play(HapticEffect.SHAKE_BUZZ)
    }
}

/** A [DiceHaptics] scoped to the current composition. */
@Composable
fun rememberDiceHaptics(): DiceHaptics {
    val platform = LocalPlatformServices.current
    return remember { DiceHaptics(platform.createHapticsPlayer()) }
}
