package net.zodac.dicefive.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.oneScoreEach

class PlayerStateTest {

    private fun player(fiveOfAKindBox: Int?, bonusChips: Int = 0) = PlayerState(
        name = "P",
        type = PlayerType.HUMAN,
        scorecard = oneScoreEach(GameMode.STANDARD.categories.associateWith { null } + (ScoreCategory.FIVE_OF_A_KIND to fiveOfAKindBox)),
        fiveOfAKindBonusCount = bonusChips,
    )

    @Test
    fun `a filled 5x box counts as one 5x - and each bonus chip as another`() {
        assertEquals(1, player(fiveOfAKindBox = 50).fiveOfAKindCount)
        assertEquals(4, player(fiveOfAKindBox = 50, bonusChips = 3).fiveOfAKindCount)
    }

    @Test
    fun `an open or zeroed 5x box was never a 5x`() {
        assertEquals(0, player(fiveOfAKindBox = null).fiveOfAKindCount)
        assertEquals(0, player(fiveOfAKindBox = 0).fiveOfAKindCount)
    }

    // ---- Third Wind: every box scored three times ---------------------------------------------

    private fun thirdWind(vararg boxes: Pair<ScoreCategory, List<Int>>) =
        PlayerState(name = "P", type = PlayerType.HUMAN, gameMode = GameMode.THIRD_WIND).let { it.copy(scorecard = it.scorecard + boxes) }

    /** Every upper box's three slots at three of its number - 189 in all, Third Wind's bonus threshold exactly. */
    private val upperAtPar = PlayerState.UPPER_CATEGORIES.mapIndexed { index, category -> category to List(3) { 3 * (index + 1) } }

    @Test
    fun `a Third Wind box is worth all of its slots - and 189 in the upper section earns a 105 bonus`() {
        val player = thirdWind(*upperAtPar.toTypedArray(), ScoreCategory.CHANCE to listOf(20, 12))

        assertEquals(189, player.upperSectionTotal)
        assertEquals(105, player.upperSectionBonus)
        assertEquals(32, player.lowerSectionTotal)
        assertEquals(189 + 105 + 32, player.totalScore)
    }

    @Test
    fun `a Third Wind upper section one short of 189 earns no bonus`() {
        val player = thirdWind(*upperAtPar.toTypedArray(), ScoreCategory.ONES to listOf(3, 3, 2))

        assertEquals(188, player.upperSectionTotal)
        assertEquals(0, player.upperSectionBonus)
    }

    @Test
    fun `a Third Wind box stays open until all three slots are scored - and the game until all 39 are`() {
        val twoScored = thirdWind(ScoreCategory.CHANCE to listOf(20, 12))
        assertTrue(twoScored.isOpen(ScoreCategory.CHANCE))
        assertEquals(2, twoScored.turnsTaken)
        assertEquals(37, twoScored.turnsLeft)

        val full = thirdWind(ScoreCategory.CHANCE to listOf(20, 12, 9))
        assertFalse(full.isOpen(ScoreCategory.CHANCE))
        assertFalse(full.isScorecardComplete)
    }

    @Test
    fun `every 50 in Third Wind's 5x box counts as a 5x - and the joker needs all three slots used`() {
        assertEquals(2, thirdWind(ScoreCategory.FIVE_OF_A_KIND to listOf(50, 50)).fiveOfAKindCount)
        assertFalse(thirdWind(ScoreCategory.FIVE_OF_A_KIND to listOf(50, 50)).fiveOfAKindJokerActive)
        assertTrue(thirdWind(ScoreCategory.FIVE_OF_A_KIND to listOf(0, 50, 0)).fiveOfAKindJokerActive)
        assertFalse(thirdWind(ScoreCategory.FIVE_OF_A_KIND to listOf(0, 0, 0)).fiveOfAKindJokerActive)
    }
}
