package net.zodac.dicefive

import net.zodac.dicefive.device.preferredRefreshRate
import org.junit.Assert.assertEquals
import org.junit.Test

/** The refresh rate a capped "Animations" level asks the screen for - see [preferredRefreshRate]. */
class RefreshRateTest {

    @Test
    fun `uncapped, no preference - the system's own choice`() {
        assertEquals(0f, preferredRefreshRate(listOf(60f, 120f), maxFramesPerSecond = null))
    }

    @Test
    fun `capped, a screen that can drop to 60Hz is asked to - at a 60 or a 30 cap`() {
        assertEquals(60f, preferredRefreshRate(listOf(120f, 60f, 90f), maxFramesPerSecond = 60))
        assertEquals(60f, preferredRefreshRate(listOf(120f, 60f, 90f), maxFramesPerSecond = 30))
        assertEquals(59.94f, preferredRefreshRate(listOf(59.94f, 120f), maxFramesPerSecond = 60))
    }

    @Test
    fun `capped, never below 60Hz - the lowest rate from 60 up, or none`() {
        assertEquals(60f, preferredRefreshRate(listOf(30f, 48f, 60f, 120f), maxFramesPerSecond = 30))
        assertEquals(90f, preferredRefreshRate(listOf(90f, 120f), maxFramesPerSecond = 60))
        assertEquals(0f, preferredRefreshRate(listOf(30f, 48f), maxFramesPerSecond = 30))
    }
}
