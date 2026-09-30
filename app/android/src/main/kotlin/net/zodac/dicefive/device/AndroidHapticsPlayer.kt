package net.zodac.dicefive.device

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import net.zodac.dicefive.platform.HapticEffect
import net.zodac.dicefive.platform.HapticsPlayer

/** Slightly shorter than the hold/unhold sound clips, so the buzz resolves just before the click
 * finishes rather than lingering after it. */
private const val HOLD_HAPTIC_MILLIS = 150L

/** 20% shorter than GameScreen's own CUP_SHAKE_MILLIS (420ms) - full-length felt like it dragged
 * on past the point the shake had made its point. */
private const val SHAKE_HAPTIC_MILLIS = 336L

/** [VibrationEffect] amplitude is 1-255; shared by both the hold/unhold tick and the shake buzz. */
private const val HAPTIC_AMPLITUDE = 40

/** [HapticsPlayer] on the device's default [Vibrator], looked up once per instance. */
internal class AndroidHapticsPlayer(context: Context) : HapticsPlayer {

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    override fun cancel() {
        vibrator.cancel()
    }

    override fun play(effect: HapticEffect) {
        if (!vibrator.hasVibrator()) return
        val millis = when (effect) {
            HapticEffect.HOLD_TICK -> HOLD_HAPTIC_MILLIS
            HapticEffect.SHAKE_BUZZ -> SHAKE_HAPTIC_MILLIS
        }
        vibrator.vibrate(VibrationEffect.createOneShot(millis, HAPTIC_AMPLITUDE))
    }
}
