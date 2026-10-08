package net.zodac.dicefive.ui.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shake window is timed to the shake sound and buzz (about 400ms and 336ms), so reduced motion only
 * shortens it when neither can play.
 */
class CupShakeTest {

    @Test
    fun `the shake window is shortened only under reduced motion with neither sound nor vibration - and never to nothing`() {
        for (reduceMotion in listOf(false, true)) for (sound in listOf(true, false)) for (vibration in listOf(true, false)) {
            val expected = if (reduceMotion && !sound && !vibration) REDUCED_MOTION_CUP_SHAKE_MILLIS else CUP_SHAKE_MILLIS
            assertEquals(expected, cupShakeMillis(reduceMotion = reduceMotion, soundEnabled = sound, vibrationEnabled = vibration), "reduce motion $reduceMotion, sound $sound, vibration $vibration")
        }
        assertTrue(REDUCED_MOTION_CUP_SHAKE_MILLIS in 1 until CUP_SHAKE_MILLIS)
    }
}
