package net.zodac.dicefive.ui.game

import kotlin.test.Test
import kotlin.test.assertEquals

/** [ShakeDetector]'s beat counting, fed made-up accelerometer samples (m/s², gravity along z). */
class ShakeDetectorTest {

    /** A phone fed made-up samples: the shakes it counted, and each time it asked for a faster or slower sensor rate. */
    private class Phone {
        var shakes = 0
        val rateChanges = mutableListOf<Boolean>()
        private val detector = ShakeDetector(onActiveChanged = { rateChanges += it }) { shakes++ }
        private var now = 0L

        /** Lying still for long enough that the gravity filter has settled on z. */
        fun settled() = apply { repeat(100) { sample(x = 0f) } }

        fun sample(x: Float, stepMillis: Long = 10) {
            now += stepMillis
            detector.onSample(x, 0f, GRAVITY, now)
        }

        /** One swing well past the threshold and back to rest - a single rising edge. */
        fun beat(direction: Float, stepMillis: Long = 10) {
            sample(x = direction * SWING, stepMillis = stepMillis)
            sample(x = 0f)
        }

        fun shake() {
            beat(1f)
            beat(-1f)
            beat(1f)
        }
    }

    @Test
    fun `three direction changes inside the window are one shake - not beats spread wider - one long swing - or a repeat in the cooldown`() {
        val phone = Phone().settled()
        phone.shake()
        assertEquals(1, phone.shakes)
        // A shake drops the sensor rate straight back down.
        assertEquals(listOf(true, false), phone.rateChanges)

        val spread = Phone().settled()
        spread.beat(1f)
        spread.beat(-1f, stepMillis = 400)
        spread.beat(1f, stepMillis = 400)
        assertEquals(0, spread.shakes)

        val oneSwing = Phone().settled()
        repeat(20) { oneSwing.sample(x = SWING) }
        assertEquals(0, oneSwing.shakes)

        // A second shake straight after the first is ignored during the cooldown.
        val twice = Phone().settled()
        repeat(2) { twice.shake() }
        assertEquals(1, twice.shakes)
    }

    @Test
    fun `a still phone is read at the idle rate - motion steps it up - and it drops back once the phone has been still a while`() {
        // Not woken by its first reading.
        val phone = Phone().settled()
        assertEquals(emptyList(), phone.rateChanges)

        phone.sample(x = WAKE_SWING)
        assertEquals(listOf(true), phone.rateChanges)
        repeat(50) { phone.sample(x = 0f) } // half a second still: stays up
        assertEquals(listOf(true), phone.rateChanges)
        repeat(60) { phone.sample(x = 0f) } // past the hold: back down
        assertEquals(listOf(true, false), phone.rateChanges)
    }

    private companion object {
        const val GRAVITY = 9.81f
        const val SWING = 30f
        // Past the wake threshold, well short of a shake's.
        const val WAKE_SWING = 6f
    }
}
