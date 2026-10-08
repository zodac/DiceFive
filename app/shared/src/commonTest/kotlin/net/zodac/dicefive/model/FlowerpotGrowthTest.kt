package net.zodac.dicefive.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlowerpotGrowthTest {

    private fun GameMode.stages(): List<Int> = (0..maxRollsPerGame).map { flowerpotGrowthStage(it) }

    @Test
    fun `the plant's stages spread evenly over each mode's rolls - never back - blooming only on the last - and only with 3 rolls a turn`() {
        // In Standard it grows every 10 rolls and blooms on the 39th - its last.
        assertEquals(List(10) { 0 } + List(10) { 1 } + List(10) { 2 } + List(9) { 3 } + listOf(FLOWERPOT_FULL_BLOOM), GameMode.STANDARD.stages())
        assertEquals(List(13) { 0 } + List(13) { 1 } + List(13) { 2 } + List(12) { 3 } + listOf(FLOWERPOT_FULL_BLOOM), GameMode.TRICOLOUR.stages())
        assertEquals(List(5) { 0 } + List(4) { 1 } + List(5) { 2 } + List(4) { 3 } + listOf(FLOWERPOT_FULL_BLOOM), GameMode.QUICKFIRE.stages())
        for (mode in GameMode.entries) assertEquals(mode.rollsPerTurn == 3, mode.growsSunflower, mode.id)

        // For any game length the stages never go backwards and the bloom comes only on the last roll.
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
    fun `a player has grown a sunflower only in full bloom - which a roll modifier rules out and Extended Scores puts off to roll 48`() {
        val player = PlayerState(name = "Player 1", type = PlayerType.HUMAN)
        assertFalse(player.copy(rollCount = 38).hasGrownSunflower)
        assertTrue(player.copy(rollCount = 39).hasGrownSunflower)
        assertFalse(player.copy(gameMode = GameMode.TRICOLOUR, rollCount = 50).hasGrownSunflower)
        assertTrue(player.copy(gameMode = GameMode.TRICOLOUR, rollCount = 51).hasGrownSunflower)

        // However many rolls.
        val modified = player.copy(rollsModified = true)
        assertEquals(3, modified.copy(rollCount = 39).flowerpotStage)
        assertFalse(modified.copy(rollCount = 400).hasGrownSunflower)

        val extended = player.copy(extendedScores = true)
        assertFalse(extended.copy(rollCount = 39).hasGrownSunflower)
        assertFalse(extended.copy(rollCount = 47).hasGrownSunflower)
        assertTrue(extended.copy(rollCount = 48).hasGrownSunflower)
    }
}
