package net.zodac.dicefive.game

import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun diceOf(vararg values: Int): List<Die> = values.map { Die(value = it) }

class ScoreCalculatorTest {

    private val freshPlayer = PlayerState(name = "Player 1", type = PlayerType.HUMAN)

    @Test
    fun `first yahtzee is not a joker situation`() {
        val dice = diceOf(4, 4, 4, 4, 4)

        assertFalse(ScoreCalculator.awardsYahtzeeBonus(freshPlayer, dice))
        assertEquals(ScoreCategory.entries.filter { freshPlayer.scorecard[it] == null }, ScoreCalculator.availableCategories(freshPlayer, dice))
    }

    @Test
    fun `second yahtzee is forced into the matching open upper box`() {
        val player = freshPlayer.copy(scorecard = freshPlayer.scorecard + (ScoreCategory.YAHTZEE to 50))
        val dice = diceOf(4, 4, 4, 4, 4)

        assertEquals(listOf(ScoreCategory.FOURS), ScoreCalculator.availableCategories(player, dice))
        assertTrue(ScoreCalculator.awardsYahtzeeBonus(player, dice))
        assertEquals(20, ScoreCalculator.scoreFor(player, ScoreCategory.FOURS, dice))
    }

    @Test
    fun `second yahtzee free-fills a straight box once the matching upper box is used`() {
        val scorecard = freshPlayer.scorecard + mapOf(
            ScoreCategory.YAHTZEE to 50,
            ScoreCategory.FOURS to 16,
        )
        val player = freshPlayer.copy(scorecard = scorecard)
        val dice = diceOf(4, 4, 4, 4, 4)

        val available = ScoreCalculator.availableCategories(player, dice)

        assertTrue(ScoreCategory.SMALL_STRAIGHT in available)
        assertEquals(30, ScoreCalculator.scoreFor(player, ScoreCategory.SMALL_STRAIGHT, dice))
        assertTrue(ScoreCalculator.awardsYahtzeeBonus(player, dice))
    }

    @Test
    fun `a yahtzee scored as zero does not unlock the joker rule`() {
        val player = freshPlayer.copy(scorecard = freshPlayer.scorecard + (ScoreCategory.YAHTZEE to 0))
        val dice = diceOf(2, 2, 2, 2, 2)

        assertFalse(ScoreCalculator.awardsYahtzeeBonus(player, dice))
        // Not forced into TWOS - all still-open categories remain available.
        assertTrue(ScoreCalculator.availableCategories(player, dice).size > 1)
    }
}
