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
    fun `without reduced motion the window is always the full length`() {
        for (sound in listOf(true, false)) {
            for (vibration in listOf(true, false)) {
                assertEquals(CUP_SHAKE_MILLIS, cupShakeMillis(reduceMotion = false, soundEnabled = sound, vibrationEnabled = vibration))
            }
        }
    }

    @Test
    fun `reduced motion keeps the full window while sound is on`() {
        assertEquals(CUP_SHAKE_MILLIS, cupShakeMillis(reduceMotion = true, soundEnabled = true, vibrationEnabled = false))
    }

    @Test
    fun `reduced motion keeps the full window while vibration is on`() {
        assertEquals(CUP_SHAKE_MILLIS, cupShakeMillis(reduceMotion = true, soundEnabled = false, vibrationEnabled = true))
    }

    @Test
    fun `reduced motion with no sound or vibration shortens the window`() {
        assertEquals(REDUCED_MOTION_CUP_SHAKE_MILLIS, cupShakeMillis(reduceMotion = true, soundEnabled = false, vibrationEnabled = false))
    }

    @Test
    fun `the shortened window is shorter than the full one but not zero`() {
        assertTrue(REDUCED_MOTION_CUP_SHAKE_MILLIS in 1 until CUP_SHAKE_MILLIS)
    }
}
