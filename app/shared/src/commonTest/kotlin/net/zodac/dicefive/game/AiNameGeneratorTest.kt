package net.zodac.dicefive.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.dicefive.ui.game.GameSetupState

class AiNameGeneratorTest {

    @Test
    fun `generateNames returns the requested count with no duplicates`() {
        val names = AiNameGenerator.generateNames(count = 3, playerCount = 4, random = Random(1))

        assertEquals(3, names.size)
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `generateNames returns an empty list for zero players`() {
        assertEquals(emptyList<String>(), AiNameGenerator.generateNames(count = 0, playerCount = 2))
    }

    @Test
    fun `every 2-player name fits maxAiNameLength for 2 players`() = assertPoolFitsCap(playerCount = 2)

    @Test
    fun `every 3-player name fits maxAiNameLength for 3 players`() = assertPoolFitsCap(playerCount = 3)

    @Test
    fun `every 4-player name fits maxAiNameLength for 4 players`() = assertPoolFitsCap(playerCount = 4)

    // The whole pool for that count, not a sample: one over-long name added later would otherwise
    // only show up as an ellipsised tab in a real game at that player count.
    private fun assertPoolFitsCap(playerCount: Int) {
        val cap = GameSetupState.maxAiNameLength(playerCount)
        val poolSize = AiNameGenerator.poolSize(playerCount)
        val names = AiNameGenerator.generateNames(count = poolSize, playerCount = playerCount)

        val tooLong = names.filter { it.length > cap }
        assertEquals(emptyList<String>(), tooLong, "AI names longer than $cap characters at $playerCount players")
    }
}
