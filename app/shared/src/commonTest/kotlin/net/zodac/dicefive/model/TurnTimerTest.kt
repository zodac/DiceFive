package net.zodac.dicefive.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TurnTimerTest {

    @Test
    fun `the fixed lengths are still saved under the names they always were`() {
        assertEquals("NONE", TurnTimer.NONE.name)
        assertEquals("SECONDS_30", TurnTimer.SECONDS_30.name)
        assertEquals("SECONDS_120", TurnTimer.SECONDS_120.name)
    }

    @Test
    fun `any length up to 999 seconds survives being saved and read back`() {
        for (seconds in listOf(5, 45, 999)) assertEquals(TurnTimer(seconds), TurnTimer.parse(TurnTimer(seconds).name))
        assertEquals(TurnTimer.NONE, TurnTimer.parse("NONE"))
    }

    @Test
    fun `a saved length out of range or unrecognised is not read`() {
        for (raw in listOf("SECONDS_0", "SECONDS_4", "SECONDS_1000", "SECONDS_x", "SECONDS_-5", "FIVE", "")) assertNull(TurnTimer.parse(raw), raw)
    }

    @Test
    fun `a length out of range cannot be made`() {
        assertFailsWith<IllegalArgumentException> { TurnTimer(4) }
        assertFailsWith<IllegalArgumentException> { TurnTimer(1000) }
    }

    @Test
    fun `only the three fixed lengths are presets`() {
        assertTrue(TurnTimer(60).isPreset)
        assertFalse(TurnTimer(61).isPreset)
        assertFalse(TurnTimer.NONE.isPreset)
    }
}
