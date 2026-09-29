package net.zodac.dicefive.ui.game

import kotlin.test.Test
import kotlin.test.assertEquals

/** [ShakeDetector]'s beat counting, fed made-up accelerometer samples (m/s², gravity along z). */
class ShakeDetectorTest {

    private var shakes = 0
    private val rateChanges = mutableListOf<Boolean>()
    private val detector = ShakeDetector(onActiveChanged = { rateChanges += it }) { shakes++ }
    private var now = 0L

    /** The phone lying still for long enough that the gravity filter has settled on z. */
    private fun settle() {
        repeat(100) { sample(x = 0f) }
    }

    private fun sample(x: Float, stepMillis: Long = 10) {
        now += stepMillis
        detector.onSample(x, 0f, GRAVITY, now)
    }

    /** One swing well past the threshold and back to rest - a single rising edge. */
    private fun beat(direction: Float, stepMillis: Long = 10) {
        sample(x = direction * SWING, stepMillis = stepMillis)
        sample(x = 0f)
    }

    @Test
    fun `three direction changes inside the window count as one shake`() {
        settle()
        beat(1f)
        beat(-1f)
        beat(1f)

        assertEquals(1, shakes)
    }

    @Test
    fun `beats spread wider than the window are not a shake`() {
        settle()
        beat(1f)
        beat(-1f, stepMillis = 400)
        beat(1f, stepMillis = 400)

        assertEquals(0, shakes)
    }

    @Test
    fun `one long swing in a single direction is only one beat`() {
        settle()
        repeat(20) { sample(x = SWING) }

        assertEquals(0, shakes)
    }

    @Test
    fun `a second shake straight after the first is ignored during the cooldown`() {
        settle()
        repeat(2) {
            beat(1f)
            beat(-1f)
            beat(1f)
        }

        assertEquals(1, shakes)
    }

    @Test
    fun `a still phone is read at the idle rate - not woken by its first reading`() {
        settle()

        assertEquals(emptyList(), rateChanges)
    }

    @Test
    fun `motion steps the rate up - and it drops back once the phone has been still a while`() {
        settle()
        sample(x = WAKE_SWING)
        assertEquals(listOf(true), rateChanges)

        repeat(50) { sample(x = 0f) } // half a second still: stays up
        assertEquals(listOf(true), rateChanges)
        repeat(60) { sample(x = 0f) } // past the hold: back down
        assertEquals(listOf(true, false), rateChanges)
    }

    @Test
    fun `a shake drops the rate straight back down`() {
        settle()
        beat(1f)
        beat(-1f)
        beat(1f)

        assertEquals(1, shakes)
        assertEquals(listOf(true, false), rateChanges)
    }

    private companion object {
        const val GRAVITY = 9.81f
        const val SWING = 30f
        // Past the wake threshold, well short of a shake's.
        const val WAKE_SWING = 6f
    }
}
