package net.zodac.dicefive.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory

private fun diceOf(vararg values: Int): List<Die> = values.map { Die(value = it) }

class ScoreCalculatorTest {

    private val freshPlayer = PlayerState(name = "Player 1", type = PlayerType.HUMAN)

    @Test
    fun `a first five of a kind is not a joker situation`() {
        val dice = diceOf(4, 4, 4, 4, 4)

        assertFalse(ScoreCalculator.awardsFiveOfAKindBonus(freshPlayer, dice))
        assertEquals(GameMode.STANDARD.categories.filter { freshPlayer.scorecard[it] == null }, ScoreCalculator.availableCategories(freshPlayer, dice))
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
    fun `once every matching upper and lower box is filled - any remaining open box may be zeroed`() {
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

    // ---- The joker rule in Tricolour -----------------------------------------------------------

    private val tricolourPlayer = PlayerState(name = "Player 1", type = PlayerType.HUMAN, gameMode = GameMode.TRICOLOUR)

    private fun tricolourJokerPlayer(vararg filled: Pair<ScoreCategory, Int>) =
        tricolourPlayer.copy(scorecard = tricolourPlayer.scorecard + (ScoreCategory.FIVE_OF_A_KIND to 50) + filled)

    @Test
    fun `a repeat 5x fills Coloured House at its full 25 whatever the colours show`() {
        val player = tricolourJokerPlayer(ScoreCategory.FOURS to 20)
        // Five 4s in three different colours - no coloured house on the dice themselves.
        val dice = listOf(DieColour.RED, DieColour.RED, DieColour.YELLOW, DieColour.BLUE, DieColour.BLUE)
            .map { Die(value = 4, colour = it) }

        assertTrue(ScoreCategory.COLOURED_HOUSE in ScoreCalculator.availableCategories(player, dice))
        assertEquals(25, ScoreCalculator.scoreFor(player, ScoreCategory.COLOURED_HOUSE, dice))
        assertEquals(100, ScoreCalculator.fiveOfAKindBonusFor(player, dice))
    }

    @Test
    fun `a repeat 5x can go in a colour box - but it scores by the dice's real colours`() {
        val player = tricolourJokerPlayer(ScoreCategory.SIXES to 30)
        val mixed = List(5) { Die(value = 6, colour = if (it == 0) DieColour.BLUE else DieColour.RED) }
        val allRed = List(5) { Die(value = 6, colour = DieColour.RED) }

        assertTrue(ScoreCategory.REDS in ScoreCalculator.availableCategories(player, mixed))
        assertEquals(0, ScoreCalculator.scoreFor(player, ScoreCategory.REDS, mixed))
        assertEquals(40, ScoreCalculator.scoreFor(player, ScoreCategory.REDS, allRed))
    }

    @Test
    fun `a repeat 5x in Tricolour is still forced into its open upper box first`() {
        val player = tricolourJokerPlayer()
        val dice = List(5) { Die(value = 2, colour = DieColour.YELLOW) }

        assertEquals(listOf(ScoreCategory.TWOS), ScoreCalculator.availableCategories(player, dice))
    }

    @Test
    fun `a Standard player is never offered a colour box`() {
        val dice = List(5) { Die(value = 3, colour = DieColour.RED) }

        val available = ScoreCalculator.availableCategories(freshPlayer, dice)

        assertTrue(available.none { it in listOf(ScoreCategory.REDS, ScoreCategory.YELLOWS, ScoreCategory.BLUES, ScoreCategory.COLOURED_HOUSE) })
    }
}
