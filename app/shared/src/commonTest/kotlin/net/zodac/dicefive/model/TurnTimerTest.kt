package net.zodac.dicefive.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TurnTimerTest {

    @Test
    fun `a turn timer of 5 to 999 seconds saves and reads back - the three fixed lengths under the names they always had`() {
        assertEquals("NONE", TurnTimer.NONE.name)
        assertEquals("SECONDS_30", TurnTimer.SECONDS_30.name)
        assertEquals("SECONDS_120", TurnTimer.SECONDS_120.name)
        // Only those are presets.
        assertTrue(TurnTimer(60).isPreset)
        assertFalse(TurnTimer(61).isPreset)
        assertFalse(TurnTimer.NONE.isPreset)

        for (seconds in listOf(5, 45, 999)) assertEquals(TurnTimer(seconds), TurnTimer.parse(TurnTimer(seconds).name))
        assertEquals(TurnTimer.NONE, TurnTimer.parse("NONE"))

        // A length out of range can't be made - and a saved one out of range or unrecognised is not read.
        assertFailsWith<IllegalArgumentException> { TurnTimer(4) }
        assertFailsWith<IllegalArgumentException> { TurnTimer(1000) }
        for (raw in listOf("SECONDS_0", "SECONDS_4", "SECONDS_1000", "SECONDS_x", "SECONDS_-5", "FIVE", "")) assertNull(TurnTimer.parse(raw), raw)
    }
}
