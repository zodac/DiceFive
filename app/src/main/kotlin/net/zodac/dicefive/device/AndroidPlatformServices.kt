package net.zodac.dicefive.device

import android.content.Context
import android.widget.Toast
import net.zodac.dicefive.platform.Accelerometer
import net.zodac.dicefive.platform.HapticsPlayer
import net.zodac.dicefive.platform.PlatformServices
import net.zodac.dicefive.platform.SoundPlayer

/**
 * [PlatformServices] for Android. Holds the application context, never an Activity's: sound
 * players and sensors can outlive any single Activity instance across a configuration change.
 */
class AndroidPlatformServices(context: Context) : PlatformServices {

    private val appContext = context.applicationContext

    override fun createSoundPlayer(): SoundPlayer = AndroidSoundPlayer(appContext)

    override fun createHapticsPlayer(): HapticsPlayer = AndroidHapticsPlayer(appContext)

    override fun createAccelerometer(): Accelerometer? = AndroidAccelerometer.create(appContext)

    override fun showTransientMessage(message: String) {
        Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
    }
}
