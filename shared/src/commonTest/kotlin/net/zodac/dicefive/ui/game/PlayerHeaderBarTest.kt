package net.zodac.dicefive.ui.game

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerHeaderBarTest {

    @Test
    fun `an everyday score rises in one second`() {
        assertEquals(1000, scoreRiseMillis(1))
        assertEquals(1000, scoreRiseMillis(25))
    }

    @Test
    fun `a bigger score takes longer - up to two seconds`() {
        assertEquals(1200, scoreRiseMillis(30))
        assertEquals(2000, scoreRiseMillis(50))
    }

    @Test
    fun `past two seconds the score counts faster instead of taking longer`() {
        assertEquals(2000, scoreRiseMillis(100))
        assertEquals(2000, scoreRiseMillis(135))
    }
}
