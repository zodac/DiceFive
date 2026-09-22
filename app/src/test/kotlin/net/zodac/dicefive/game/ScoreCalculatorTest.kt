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
    fun `a first five of a kind is not a joker situation`() {
        val dice = diceOf(4, 4, 4, 4, 4)

        assertFalse(ScoreCalculator.awardsFiveOfAKindBonus(freshPlayer, dice))
        assertEquals(ScoreCategory.entries.filter { freshPlayer.scorecard[it] == null }, ScoreCalculator.availableCategories(freshPlayer, dice))
    }

    @Test
    fun `a second five of a kind is forced into the matching open upper box`() {
        val player = freshPlayer.copy(scorecard = freshPlayer.scorecard + (ScoreCategory.FIVE_OF_A_KIND to 50))
        val dice = diceOf(4, 4, 4, 4, 4)

        assertEquals(listOf(ScoreCategory.FOURS), ScoreCalculator.availableCategories(player, dice))
        assertTrue(ScoreCalculator.awardsFiveOfAKindBonus(player, dice))
        assertEquals(20, ScoreCalculator.scoreFor(player, ScoreCategory.FOURS, dice))
    }

    @Test
    fun `a second five of a kind offers a choice among lower boxes once the matching upper box is used`() {
        val scorecard = freshPlayer.scorecard + mapOf(
            ScoreCategory.FIVE_OF_A_KIND to 50,
            ScoreCategory.FOURS to 16,
        )
        val player = freshPlayer.copy(scorecard = scorecard)
        val dice = diceOf(4, 4, 4, 4, 4)

        val available = ScoreCalculator.availableCategories(player, dice)

        // Restricted to the lower section - other open upper boxes are not a legal choice here.
        assertTrue(ScoreCategory.SMALL_STRAIGHT in available)
        assertFalse(ScoreCategory.TWOS in available)
        assertEquals(30, ScoreCalculator.scoreFor(player, ScoreCategory.SMALL_STRAIGHT, dice))
        // The bonus is unconditional once a repeat 5x is rolled - it doesn't matter which
        // open (lower) box the player then picks.
        assertTrue(ScoreCalculator.awardsFiveOfAKindBonus(player, dice))
    }

    @Test
    fun `once every matching upper and lower box is filled, any remaining open box may be zeroed`() {
        val scorecard = freshPlayer.scorecard + mapOf(
            ScoreCategory.FIVE_OF_A_KIND to 50,
            ScoreCategory.FOURS to 16,
            ScoreCategory.THREE_OF_A_KIND to 20,
            ScoreCategory.FOUR_OF_A_KIND to 20,
            ScoreCategory.FULL_HOUSE to 25,
            ScoreCategory.SMALL_STRAIGHT to 30,
            ScoreCategory.LARGE_STRAIGHT to 40,
            ScoreCategory.CHANCE to 20,
        )
        val player = freshPlayer.copy(scorecard = scorecard)
        val dice = diceOf(4, 4, 4, 4, 4)

        val available = ScoreCalculator.availableCategories(player, dice)

        // Every lower box (and the matching upper box) is filled, so any other open upper box is
        // now a legal - if wasteful - choice, per the "score zero in any remaining open box" rule.
        assertTrue(ScoreCategory.TWOS in available)
        assertEquals(0, ScoreCalculator.scoreFor(player, ScoreCategory.TWOS, dice))
        assertTrue(ScoreCalculator.awardsFiveOfAKindBonus(player, dice))
    }

    @Test
    fun `a five of a kind scored as zero does not unlock the joker rule`() {
        val player = freshPlayer.copy(scorecard = freshPlayer.scorecard + (ScoreCategory.FIVE_OF_A_KIND to 0))
        val dice = diceOf(2, 2, 2, 2, 2)

        assertFalse(ScoreCalculator.awardsFiveOfAKindBonus(player, dice))
        // Not forced into TWOS - all still-open categories remain available.
        assertTrue(ScoreCalculator.availableCategories(player, dice).size > 1)
    }
}
