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
    fun `a filled 5x box counts as one 5x and each bonus chip as another - an open or zeroed one was never a 5x`() {
        assertEquals(1, player(fiveOfAKindBox = 50).fiveOfAKindCount)
        assertEquals(4, player(fiveOfAKindBox = 50, bonusChips = 3).fiveOfAKindCount)
        assertEquals(0, player(fiveOfAKindBox = null).fiveOfAKindCount)
        assertEquals(0, player(fiveOfAKindBox = 0).fiveOfAKindCount)
    }

    // ---- Third Wind: every box scored three times ---------------------------------------------

    private fun thirdWind(vararg boxes: Pair<ScoreCategory, List<Int>>) =
        PlayerState(name = "P", type = PlayerType.HUMAN, gameMode = GameMode.THIRD_WIND).let { it.copy(scorecard = it.scorecard + boxes) }

    /** Every upper box's three slots at three of its number - 189 in all, Third Wind's bonus threshold exactly. */
    private val upperAtPar = PlayerState.UPPER_CATEGORIES.mapIndexed { index, category -> category to List(3) { 3 * (index + 1) } }

    @Test
    fun `a Third Wind box is worth all three slots and open until they're used - 189 upper earns 105 - every 50 in 5x is a 5x`() {
        val player = thirdWind(*upperAtPar.toTypedArray(), ScoreCategory.CHANCE to listOf(20, 12))
        assertEquals(189, player.upperSectionTotal)
        assertEquals(105, player.upperSectionBonus)
        assertEquals(32, player.lowerSectionTotal)
        assertEquals(189 + 105 + 32, player.totalScore)
        // One short of 189 earns no bonus.
        val short = thirdWind(*upperAtPar.toTypedArray(), ScoreCategory.ONES to listOf(3, 3, 2))
        assertEquals(188, short.upperSectionTotal)
        assertEquals(0, short.upperSectionBonus)

        // A box stays open until all three slots are scored - and the game until all 39 are.
        val twoScored = thirdWind(ScoreCategory.CHANCE to listOf(20, 12))
        assertTrue(twoScored.isOpen(ScoreCategory.CHANCE))
        assertEquals(2, twoScored.turnsTaken)
        assertEquals(37, twoScored.turnsLeft)
        val full = thirdWind(ScoreCategory.CHANCE to listOf(20, 12, 9))
        assertFalse(full.isOpen(ScoreCategory.CHANCE))
        assertFalse(full.isScorecardComplete)

        // The joker needs all three 5x slots used.
        assertEquals(2, thirdWind(ScoreCategory.FIVE_OF_A_KIND to listOf(50, 50)).fiveOfAKindCount)
        assertFalse(thirdWind(ScoreCategory.FIVE_OF_A_KIND to listOf(50, 50)).fiveOfAKindJokerActive)
        assertTrue(thirdWind(ScoreCategory.FIVE_OF_A_KIND to listOf(0, 50, 0)).fiveOfAKindJokerActive)
        assertFalse(thirdWind(ScoreCategory.FIVE_OF_A_KIND to listOf(0, 0, 0)).fiveOfAKindJokerActive)
    }

    // ---- Extended Scores: Two Pair, Evens and Odds ----------------------------------------------

    private fun extended(mode: GameMode = GameMode.STANDARD) =
        PlayerState(name = "P", type = PlayerType.HUMAN, gameMode = mode, extendedScores = true)

    @Test
    fun `Extended Scores adds its own section after the mode's boxes - a turn and three rolls for each`() {
        // On the card only when asked for.
        assertEquals(GameMode.STANDARD.categories + listOf(ScoreCategory.TWO_PAIR, ScoreCategory.EVENS, ScoreCategory.ODDS), extended().categories)
        assertEquals(extended().categories.toSet(), extended().scorecard.keys)
        assertEquals(GameMode.STANDARD.categories, PlayerState(name = "P", type = PlayerType.HUMAN).categories)
        assertEquals(GameMode.TRICOLOUR.categories + ScoreCategory.EXTENDED, extended(GameMode.TRICOLOUR).categories)

        // A game lasts a turn per box - three more than the mode's own.
        assertEquals(16, extended().turnsPerGame)
        assertEquals(20, extended(GameMode.TRICOLOUR).turnsPerGame)
        assertEquals(48, extended(GameMode.THIRD_WIND).turnsPerGame)
        assertEquals(13, PlayerState(name = "P", type = PlayerType.HUMAN).turnsPerGame)
        assertEquals(16, extended().turnsLeft)

        // In the total, but in neither the upper nor lower section.
        val player = extended().let { it.copy(scorecard = it.scorecard + mapOf(ScoreCategory.TWO_PAIR to listOf(22), ScoreCategory.EVENS to listOf(12), ScoreCategory.CHANCE to listOf(20))) }
        assertEquals(34, player.extendedSectionTotal)
        assertEquals(20, player.lowerSectionTotal)
        assertEquals(0, player.upperSectionTotal)
        assertEquals(54, player.totalScore)
        assertEquals(3, player.turnsTaken)

        // The plant's last roll stretches over the extra turns.
        assertEquals(39, PlayerState(name = "P", type = PlayerType.HUMAN).maxRollsPerGame)
        assertEquals(48, extended().maxRollsPerGame)
        assertEquals(60, extended(GameMode.TRICOLOUR).maxRollsPerGame)
        assertEquals(144, extended(GameMode.THIRD_WIND).maxRollsPerGame)
        // Quickfire's six turns, and Extended Scores' three: the boxes switched off don't stretch it.
        assertEquals(27, extended(GameMode.QUICKFIRE).copy(disabledCategories = GameMode.QUICKFIRE.categories.take(7).toSet()).maxRollsPerGame)
    }
}
