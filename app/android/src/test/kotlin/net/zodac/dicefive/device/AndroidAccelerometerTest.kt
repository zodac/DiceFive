package net.zodac.dicefive.device

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.view.Surface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.SensorBuilder
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowDisplay

/**
 * The accelerometer reports in the screen's axes, not the device's: each display rotation turns the device's natural
 * axes the way Android's own remapping does, read again for every sample. Listening stops when asked, and a device
 * with no accelerometer has none.
 */
@RunWith(AndroidJUnit4::class)
class AndroidAccelerometerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /** A reading of (1, 2, 3) on the device's natural axes, at [rotation], as the screen's axes. */
    private fun AndroidAccelerometer.readAt(rotation: Int, samples: MutableList<List<Float>>): List<Float> {
        shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        shadowOf(sensorManager).sendSensorEventToListeners(SensorEventBuilder.newBuilder(sensor, floatArrayOf(1f, 2f, 3f)).build())
        return samples.removeAt(0)
    }

    @Test
    fun `each rotation turns the device's axes into the screen's - read afresh for every sample - and stopping stops the samples`() {
        shadowOf(sensorManager).addSensor(SensorBuilder.newBuilder().setType(Sensor.TYPE_ACCELEROMETER).build())
        val accelerometer = requireNotNull(AndroidAccelerometer.create(context))
        val samples = mutableListOf<List<Float>>()
        accelerometer.start(samplesPerSecond = 50) { x, y, z -> samples += listOf(x, y, z) }

        assertEquals(listOf(1f, 2f, 3f), accelerometer.readAt(Surface.ROTATION_0, samples))
        assertEquals(listOf(-2f, 1f, 3f), accelerometer.readAt(Surface.ROTATION_90, samples))
        assertEquals(listOf(-1f, -2f, 3f), accelerometer.readAt(Surface.ROTATION_180, samples))
        assertEquals(listOf(2f, -1f, 3f), accelerometer.readAt(Surface.ROTATION_270, samples))

        // A new rate is a new registration, still delivering.
        accelerometer.setSamplesPerSecond(10)
        assertEquals(listOf(1f, 2f, 3f), accelerometer.readAt(Surface.ROTATION_0, samples))

        accelerometer.stop()
        assertTrue(shadowOf(sensorManager).listeners.isEmpty())
    }

    @Test
    fun `a device with no accelerometer has none`() {
        assertNull(AndroidAccelerometer.create(context))
    }
}
