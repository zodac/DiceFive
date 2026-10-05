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
    fun `in Tricolour the stages spread evenly over its 51 rolls and it blooms only on the last`() {
        assertEquals(List(13) { 0 } + List(13) { 1 } + List(13) { 2 } + List(12) { 3 } + listOf(FLOWERPOT_FULL_BLOOM), GameMode.TRICOLOUR.stages())
    }

    @Test
    fun `in Quickfire it grows over its 13 rolls but never blooms - one roll a turn`() {
        assertFalse(GameMode.QUICKFIRE.growsSunflower)
        assertEquals(List(4) { 0 } + List(3) { 1 } + List(3) { 2 } + List(4) { 3 }, GameMode.QUICKFIRE.stages())
    }

    @Test
    fun `only a mode with 3 rolls a turn can bloom`() {
        for (mode in GameMode.entries) {
            assertEquals(mode.rollsPerTurn == 3, mode.growsSunflower, mode.id)
        }
    }

    @Test
    fun `for any game length the stages never go backwards and the bloom comes only on the last roll`() {
        for (canBloom in listOf(true, false)) {
            for (maxRolls in 1..500) {
                val stages = (0..maxRolls).map { flowerpotGrowthStage(it, maxRolls, canBloom) }
                assertEquals(stages.sorted(), stages, "over $maxRolls")
                assertEquals(if (canBloom) FLOWERPOT_FULL_BLOOM else FLOWERPOT_FULL_BLOOM - 1, stages.last(), "over $maxRolls")
                assertTrue(stages.dropLast(1).all { it < FLOWERPOT_FULL_BLOOM }, "over $maxRolls bloomed early")
            }
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

    @Test
    fun `a roll modifier keeps the plant from blooming - however many rolls`() {
        val player = PlayerState(name = "Player 1", type = PlayerType.HUMAN, rollsModified = true)

        assertEquals(3, player.copy(rollCount = 39).flowerpotStage)
        assertFalse(player.copy(rollCount = 400).hasGrownSunflower)
    }
}
