package net.zodac.dicefive.ui.game

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Slightly shorter than the hold/unhold sound clips, so the buzz resolves just before the click
 * finishes rather than lingering after it. */
private const val HOLD_HAPTIC_MILLIS = 150L

/** 20% shorter than [GameScreen]'s own CUP_SHAKE_MILLIS (420ms) - full-length felt like it dragged
 * on past the point the shake had made its point. */
private const val SHAKE_HAPTIC_MILLIS = 336L

/** [VibrationEffect] amplitude is 1-255; shared by both the hold/unhold tick and the shake buzz. */
private const val HAPTIC_AMPLITUDE = 40

/** Short vibrations fired alongside [SoundEffects] in [GameScreen]: one tick for a die being held
 * or unheld (the same tick for both directions, since the sounds already carry that distinction),
 * and a longer buzz spanning the cup shake. [Vibrator] is looked up once per instance; there's no
 * pool or release step to manage the way [SoundEffects] needs one. */
class DiceHaptics(context: Context) {

    /** Set from the Settings screen's "Vibration" switch - see [SoundEffects.enabled] for why
     * this is a plain var rather than gating at the call site. */
    var enabled: Boolean = true

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    fun playHoldTick() {
        if (!enabled || !vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(HOLD_HAPTIC_MILLIS, HAPTIC_AMPLITUDE))
    }

    fun playShakeBuzz() {
        if (!enabled || !vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(SHAKE_HAPTIC_MILLIS, HAPTIC_AMPLITUDE))
    }
}

/** A [DiceHaptics] scoped to the current composition - the application context, not the (possibly
 * Activity) [LocalContext.current], matching [rememberSoundEffects] for the same reason: it can
 * outlive any single Activity instance across a configuration change. */
@Composable
fun rememberDiceHaptics(): DiceHaptics {
    val context = LocalContext.current.applicationContext
    return remember { DiceHaptics(context) }
}
