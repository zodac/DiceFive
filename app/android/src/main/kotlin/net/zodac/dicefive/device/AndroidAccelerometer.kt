package net.zodac.dicefive.device

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import net.zodac.dicefive.platform.Accelerometer

/**
 * [Accelerometer] on the platform's [Sensor.TYPE_ACCELEROMETER], which already reports m/s² in the
 * right sign - turned from the device's natural axes to the screen's by the display's rotation.
 */
internal class AndroidAccelerometer private constructor(
    private val sensorManager: SensorManager,
    private val sensor: Sensor,
    private val displayManager: DisplayManager,
) : Accelerometer {

    private var listener: SensorEventListener? = null

    override fun start(samplesPerSecond: Int, onSample: (x: Float, y: Float, z: Float) -> Unit) {
        stop()
        val newListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                // Read per sample, so a rotation mid-listen is picked up at once.
                when (displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0) {
                    Surface.ROTATION_90 -> onSample(-y, x, z)
                    Surface.ROTATION_180 -> onSample(-x, -y, z)
                    Surface.ROTATION_270 -> onSample(y, -x, z)
                    else -> onSample(x, y, z)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listener = newListener
        register(newListener, samplesPerSecond)
    }

    // Re-registering the same listener is how Android changes a sensor's rate - there's no setter.
    override fun setSamplesPerSecond(samplesPerSecond: Int) {
        val current = listener ?: return
        sensorManager.unregisterListener(current)
        register(current, samplesPerSecond)
    }

    private fun register(listener: SensorEventListener, samplesPerSecond: Int) {
        sensorManager.registerListener(listener, sensor, MICROS_PER_SECOND / samplesPerSecond)
    }

    override fun stop() {
        listener?.let(sensorManager::unregisterListener)
        listener = null
    }

    companion object {
        private const val MICROS_PER_SECOND = 1_000_000

        /** Null on a device with no accelerometer. */
        fun create(context: Context): AndroidAccelerometer? {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return null
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            return AndroidAccelerometer(sensorManager, sensor, displayManager)
        }
    }
}
