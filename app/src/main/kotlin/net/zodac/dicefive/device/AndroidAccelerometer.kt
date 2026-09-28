package net.zodac.dicefive.device

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import net.zodac.dicefive.platform.Accelerometer

/** [Accelerometer] on the platform's [Sensor.TYPE_ACCELEROMETER], which already reports m/s². */
internal class AndroidAccelerometer private constructor(
    private val sensorManager: SensorManager,
    private val sensor: Sensor,
) : Accelerometer {

    private var listener: SensorEventListener? = null

    override fun start(onSample: (x: Float, y: Float, z: Float) -> Unit) {
        stop()
        val newListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = onSample(event.values[0], event.values[1], event.values[2])

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listener = newListener
        sensorManager.registerListener(newListener, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun stop() {
        listener?.let(sensorManager::unregisterListener)
        listener = null
    }

    companion object {
        /** Null on a device with no accelerometer. */
        fun create(context: Context): AndroidAccelerometer? {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return null
            return AndroidAccelerometer(sensorManager, sensor)
        }
    }
}
