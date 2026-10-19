package net.zodac.dicefive.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.model.GameMode

class GameModeColourTest {

    @Test
    fun `Standard is the brand gold - every mode's colour is its own - and none is the red of a loss`() {
        assertEquals(Primary, GameMode.STANDARD.color)
        val colours = GameMode.entries.map { it.color }
        assertEquals(colours.size, colours.toSet().size)
        assertTrue(Error !in colours)
    }
}
