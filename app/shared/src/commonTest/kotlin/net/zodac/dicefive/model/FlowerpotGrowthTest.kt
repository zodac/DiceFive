package net.zodac.dicefive.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlowerpotGrowthTest {

    private fun GameMode.stages(): List<Int> = (0..maxRollsPerGame).map { flowerpotGrowthStage(it) }

    @Test
    fun `in Standard the plant grows every 10 rolls and blooms on the 39th - its last`() {
        assertEquals(List(10) { 0 } + List(10) { 1 } + List(10) { 2 } + List(9) { 3 } + listOf(FLOWERPOT_FULL_BLOOM), GameMode.STANDARD.stages())
    }

    @Test
    fun `in Tricolour it grows at the same rolls but only blooms on its 51st - its last`() {
        assertEquals(List(10) { 0 } + List(10) { 1 } + List(10) { 2 } + List(21) { 3 } + listOf(FLOWERPOT_FULL_BLOOM), GameMode.TRICOLOUR.stages())
    }

    @Test
    fun `in Quickfire it only reaches the seedling - its 13 rolls never bring it into bloom`() {
        assertEquals(List(10) { 0 } + List(4) { 1 }, GameMode.QUICKFIRE.stages())
    }

    @Test
    fun `it blooms on every roll of the game - but never on fewer than 39`() {
        for (mode in GameMode.entries) {
            assertEquals(maxOf(SUNFLOWER_MIN_ROLLS, mode.maxRollsPerGame), mode.rollsToBloom, mode.id)
        }
    }

    @Test
    fun `a player has grown a sunflower only once their plant is in full bloom`() {
        val player = PlayerState(name = "Player 1", type = PlayerType.HUMAN)

        assertFalse(player.copy(rollCount = 38).hasGrownSunflower)
        assertTrue(player.copy(rollCount = 39).hasGrownSunflower)
        assertFalse(player.copy(gameMode = GameMode.TRICOLOUR, rollCount = 50).hasGrownSunflower)
        assertTrue(player.copy(gameMode = GameMode.TRICOLOUR, rollCount = 51).hasGrownSunflower)
    }
}
