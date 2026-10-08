package net.zodac.dicefive.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.dicefive.ui.game.GameSetupState

class AiNameGeneratorTest {

    @Test
    fun `CPU names come as many as asked without duplicates - and every one in each pool fits that player count's cap`() {
        val names = AiNameGenerator.generateNames(count = 3, playerCount = 4, random = Random(1))
        assertEquals(3, names.size)
        assertEquals(names.size, names.toSet().size)
        assertEquals(emptyList<String>(), AiNameGenerator.generateNames(count = 0, playerCount = 2))

        // The whole pool for each count, not a sample: one over-long name added later would otherwise only show up as an
        // ellipsised tab in a real game at that player count.
        for (playerCount in 2..4) {
            val cap = GameSetupState.maxAiNameLength(playerCount)
            val pool = AiNameGenerator.generateNames(count = AiNameGenerator.poolSize(playerCount), playerCount = playerCount)
            assertEquals(emptyList<String>(), pool.filter { it.length > cap }, "AI names longer than $cap characters at $playerCount players")
        }
    }
}
