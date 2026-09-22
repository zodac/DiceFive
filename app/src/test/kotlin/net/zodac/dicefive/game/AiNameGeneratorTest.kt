package net.zodac.dicefive.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import net.zodac.dicefive.ui.game.GameSetupState
import org.junit.Test

class AiNameGeneratorTest {

    @Test
    fun `generateNames returns the requested count with no duplicates`() {
        val names = AiNameGenerator.generateNames(3, random = Random(1))

        assertEquals(3, names.size)
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `generateNames returns an empty list for zero players`() {
        assertEquals(emptyList<String>(), AiNameGenerator.generateNames(0))
    }

    @Test
    fun `every generated name fits the player name length cap`() {
        // The whole pool, not a sample: one over-long name added later would otherwise only show
        // up as an ellipsised tab in a real 4-player game.
        val names = AiNameGenerator.generateNames(AiNameGenerator.poolSize)

        val tooLong = names.filter { it.length > GameSetupState.MAX_PLAYER_NAME_LENGTH }
        assertEquals("AI names longer than ${GameSetupState.MAX_PLAYER_NAME_LENGTH} characters", emptyList<String>(), tooLong)
    }
}
