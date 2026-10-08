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
import net.zodac.dicefive.oneScoreEach

private fun diceOf(vararg values: Int): List<Die> = values.map { Die(value = it) }

class ScoreCalculatorTest {

    private val freshPlayer = PlayerState(name = "Player 1", type = PlayerType.HUMAN)

    @Test
    fun `the joker rule - a repeat 5x is forced into its upper box - then the lower section - then anything - and a timeout takes the first open box`() {
        val fours = diceOf(4, 4, 4, 4, 4)
        // A first 5x is not a joker situation.
        assertFalse(ScoreCalculator.awardsFiveOfAKindBonus(freshPlayer, fours))
        assertEquals(GameMode.STANDARD.categories.filter { freshPlayer.isOpen(it) }, ScoreCalculator.availableCategories(freshPlayer, fours))

        // A second is forced into the matching open upper box.
        val banked = freshPlayer.copy(scorecard = freshPlayer.scorecard + (ScoreCategory.FIVE_OF_A_KIND to listOf(50)))
        assertEquals(listOf(ScoreCategory.FOURS), ScoreCalculator.availableCategories(banked, fours))
        assertTrue(ScoreCalculator.awardsFiveOfAKindBonus(banked, fours))
        assertEquals(20, ScoreCalculator.scoreFor(banked, ScoreCategory.FOURS, fours))

        // Once that's used, a choice among the lower boxes - other open upper boxes are not a legal choice. The bonus is
        // unconditional once a repeat 5x is rolled: it doesn't matter which open (lower) box the player then picks.
        val foursUsed = freshPlayer.copy(scorecard = freshPlayer.scorecard + oneScoreEach(mapOf(ScoreCategory.FIVE_OF_A_KIND to 50, ScoreCategory.FOURS to 16)))
        val lower = ScoreCalculator.availableCategories(foursUsed, fours)
        assertTrue(ScoreCategory.SMALL_STRAIGHT in lower)
        assertFalse(ScoreCategory.TWOS in lower)
        assertEquals(30, ScoreCalculator.scoreFor(foursUsed, ScoreCategory.SMALL_STRAIGHT, fours))
        assertTrue(ScoreCalculator.awardsFiveOfAKindBonus(foursUsed, fours))

        // Once every matching upper and lower box is filled, any remaining open box may be zeroed - a legal, if wasteful, choice.
        val allLowerUsed = freshPlayer.copy(
            scorecard = freshPlayer.scorecard + oneScoreEach(
                mapOf(
                    ScoreCategory.FIVE_OF_A_KIND to 50,
                    ScoreCategory.FOURS to 16,
                    ScoreCategory.THREE_OF_A_KIND to 20,
                    ScoreCategory.FOUR_OF_A_KIND to 20,
                    ScoreCategory.FULL_HOUSE to 25,
                    ScoreCategory.SMALL_STRAIGHT to 30,
                    ScoreCategory.LARGE_STRAIGHT to 40,
                    ScoreCategory.CHANCE to 20,
                ),
            ),
        )
        assertTrue(ScoreCategory.TWOS in ScoreCalculator.availableCategories(allLowerUsed, fours))
        assertEquals(0, ScoreCalculator.scoreFor(allLowerUsed, ScoreCategory.TWOS, fours))
        assertTrue(ScoreCalculator.awardsFiveOfAKindBonus(allLowerUsed, fours))

        // A 5x scored as zero does not unlock the joker rule: not forced into Twos - all still-open categories remain.
        val scratched = freshPlayer.copy(scorecard = freshPlayer.scorecard + (ScoreCategory.FIVE_OF_A_KIND to listOf(0)))
        assertFalse(ScoreCalculator.awardsFiveOfAKindBonus(scratched, diceOf(2, 2, 2, 2, 2)))
        assertTrue(ScoreCalculator.availableCategories(scratched, diceOf(2, 2, 2, 2, 2)).size > 1)

        // A Standard timeout scores the first open category - whatever it's worth.
        assertEquals(ScoreCategory.TWOS, ScoreCalculator.timeoutCategory(freshPlayer.copy(scorecard = freshPlayer.scorecard + (ScoreCategory.ONES to listOf(3))), diceOf(2, 2, 3, 4, 6)))
    }

    // ---- The joker rule in Tricolour -----------------------------------------------------------

    private val tricolourPlayer = PlayerState(name = "Player 1", type = PlayerType.HUMAN, gameMode = GameMode.TRICOLOUR)

    private fun tricolourJokerPlayer(vararg filled: Pair<ScoreCategory, Int>) =
        tricolourPlayer.copy(scorecard = tricolourPlayer.scorecard + (ScoreCategory.FIVE_OF_A_KIND to listOf(50)) + oneScoreEach(filled.toMap()))

    @Test
    fun `in Tricolour a repeat 5x is still forced upper first - fills Coloured House at 25 - and scores a colour box by its real colours`() {
        assertEquals(listOf(ScoreCategory.TWOS), ScoreCalculator.availableCategories(tricolourJokerPlayer(), List(5) { Die(value = 2, colour = DieColour.YELLOW) }))

        // Five 4s in three different colours - no coloured house on the dice themselves.
        val foursUsed = tricolourJokerPlayer(ScoreCategory.FOURS to 20)
        val mixedFours = listOf(DieColour.RED, DieColour.RED, DieColour.YELLOW, DieColour.BLUE, DieColour.BLUE).map { Die(value = 4, colour = it) }
        assertTrue(ScoreCategory.COLOURED_HOUSE in ScoreCalculator.availableCategories(foursUsed, mixedFours))
        assertEquals(25, ScoreCalculator.scoreFor(foursUsed, ScoreCategory.COLOURED_HOUSE, mixedFours))
        assertEquals(100, ScoreCalculator.fiveOfAKindBonusFor(foursUsed, mixedFours))

        val sixesUsed = tricolourJokerPlayer(ScoreCategory.SIXES to 30)
        val mixed = List(5) { Die(value = 6, colour = if (it == 0) DieColour.BLUE else DieColour.RED) }
        assertTrue(ScoreCategory.REDS in ScoreCalculator.availableCategories(sixesUsed, mixed))
        assertEquals(0, ScoreCalculator.scoreFor(sixesUsed, ScoreCategory.REDS, mixed))
        assertEquals(40, ScoreCalculator.scoreFor(sixesUsed, ScoreCategory.REDS, List(5) { Die(value = 6, colour = DieColour.RED) }))

        // A Standard player is never offered a colour box.
        val available = ScoreCalculator.availableCategories(freshPlayer, List(5) { Die(value = 3, colour = DieColour.RED) })
        assertTrue(available.none { it in listOf(ScoreCategory.REDS, ScoreCategory.YELLOWS, ScoreCategory.BLUES, ScoreCategory.COLOURED_HOUSE) })
    }

    // ---- Third Wind: three slots a box -----------------------------------------------------------

    private val thirdWindPlayer = PlayerState(name = "Player 1", type = PlayerType.HUMAN, gameMode = GameMode.THIRD_WIND)

    private fun thirdWind(vararg boxes: Pair<ScoreCategory, List<Int>>) = thirdWindPlayer.copy(scorecard = thirdWindPlayer.scorecard + boxes)

    @Test
    fun `in Third Wind a box takes three scores - and the joker starts only once all three 5x slots are used with a 50 among them`() {
        val dice = diceOf(1, 2, 3, 4, 6)
        assertTrue(ScoreCategory.CHANCE in ScoreCalculator.availableCategories(thirdWind(ScoreCategory.CHANCE to listOf(20, 12)), dice))
        assertFalse(ScoreCategory.CHANCE in ScoreCalculator.availableCategories(thirdWind(ScoreCategory.CHANCE to listOf(20, 12, 9)), dice))

        // A 5x with a 5x slot still open scores 50 there - no bonus and no joker: every box is open to it, and a Full House
        // takes it for what the dice show - nothing, as in any other turn.
        val fours = diceOf(4, 4, 4, 4, 4)
        val slotOpen = thirdWind(ScoreCategory.FIVE_OF_A_KIND to listOf(50, 50))
        assertFalse(ScoreCalculator.awardsFiveOfAKindBonus(slotOpen, fours))
        assertEquals(50, ScoreCalculator.scoreFor(slotOpen, ScoreCategory.FIVE_OF_A_KIND, fours))
        assertEquals(GameMode.THIRD_WIND.categories, ScoreCalculator.availableCategories(slotOpen, fours))
        assertEquals(0, ScoreCalculator.scoreFor(slotOpen, ScoreCategory.FULL_HOUSE, fours))

        val joker = thirdWind(ScoreCategory.FIVE_OF_A_KIND to listOf(0, 50, 0))
        assertTrue(ScoreCalculator.awardsFiveOfAKindBonus(joker, fours))
        assertEquals(listOf(ScoreCategory.FOURS), ScoreCalculator.availableCategories(joker, fours))
        // With a Fours box partly used, its open slot is still forced.
        assertEquals(listOf(ScoreCategory.FOURS), ScoreCalculator.availableCategories(thirdWind(ScoreCategory.FIVE_OF_A_KIND to listOf(50, 50, 50), ScoreCategory.FOURS to listOf(16, 12)), fours))
        // Three zeroed 5x slots never start it.
        assertFalse(ScoreCalculator.awardsFiveOfAKindBonus(thirdWind(ScoreCategory.FIVE_OF_A_KIND to listOf(0, 0, 0)), diceOf(2, 2, 2, 2, 2)))
    }
}
