package net.zodac.dicefive.device

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import net.zodac.dicefive.platform.HapticEffect
import net.zodac.dicefive.platform.HapticsPlayer

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
        vibrator.vibrate(VibrationEffect.createOneShot(effect.millis, HAPTIC_AMPLITUDE))
    }
}
