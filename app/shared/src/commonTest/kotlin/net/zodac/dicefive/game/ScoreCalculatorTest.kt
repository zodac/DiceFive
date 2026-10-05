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
    fun `a first five of a kind is not a joker situation`() {
        val dice = diceOf(4, 4, 4, 4, 4)

        assertFalse(ScoreCalculator.awardsFiveOfAKindBonus(freshPlayer, dice))
        assertEquals(GameMode.STANDARD.categories.filter { freshPlayer.isOpen(it) }, ScoreCalculator.availableCategories(freshPlayer, dice))
    }

    @Test
    fun `a second five of a kind is forced into the matching open upper box`() {
        val player = freshPlayer.copy(scorecard = freshPlayer.scorecard + (ScoreCategory.FIVE_OF_A_KIND to listOf(50)))
        val dice = diceOf(4, 4, 4, 4, 4)

        assertEquals(listOf(ScoreCategory.FOURS), ScoreCalculator.availableCategories(player, dice))
        assertTrue(ScoreCalculator.awardsFiveOfAKindBonus(player, dice))
        assertEquals(20, ScoreCalculator.scoreFor(player, ScoreCategory.FOURS, dice))
    }

    @Test
    fun `a second five of a kind offers a choice among lower boxes once the matching upper box is used`() {
        val scorecard = freshPlayer.scorecard + oneScoreEach(
            mapOf(
                ScoreCategory.FIVE_OF_A_KIND to 50,
                ScoreCategory.FOURS to 16,
            ),
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
        val scorecard = freshPlayer.scorecard + oneScoreEach(
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
        val player = freshPlayer.copy(scorecard = freshPlayer.scorecard + (ScoreCategory.FIVE_OF_A_KIND to listOf(0)))
        val dice = diceOf(2, 2, 2, 2, 2)

        assertFalse(ScoreCalculator.awardsFiveOfAKindBonus(player, dice))
        // Not forced into TWOS - all still-open categories remain available.
        assertTrue(ScoreCalculator.availableCategories(player, dice).size > 1)
    }

    // ---- The joker rule in Tricolour -----------------------------------------------------------

    private val tricolourPlayer = PlayerState(name = "Player 1", type = PlayerType.HUMAN, gameMode = GameMode.TRICOLOUR)

    private fun tricolourJokerPlayer(vararg filled: Pair<ScoreCategory, Int>) =
        tricolourPlayer.copy(scorecard = tricolourPlayer.scorecard + (ScoreCategory.FIVE_OF_A_KIND to listOf(50)) + oneScoreEach(filled.toMap()))

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

    // ---- Where a timed-out turn is scored -----------------------------------------------------

    private val quickfirePlayer = PlayerState(name = "Player 1", type = PlayerType.HUMAN, gameMode = GameMode.QUICKFIRE)

    @Test
    fun `a Standard timeout scores the first open category - whatever it's worth`() {
        val player = freshPlayer.copy(scorecard = freshPlayer.scorecard + (ScoreCategory.ONES to listOf(3)))

        assertEquals(ScoreCategory.TWOS, ScoreCalculator.timeoutCategory(player, diceOf(2, 2, 3, 4, 6)))
    }

    @Test
    fun `a Quickfire timeout scores the open category worth the least`() {
        val player = quickfirePlayer.copy(scorecard = quickfirePlayer.scorecard + (ScoreCategory.ONES to listOf(3)))
        // Twos 4, Threes 3, Fours 4, Fives 0 - the first open box would be Twos, the lowest is Fives.
        val dice = diceOf(2, 2, 3, 4, 6)

        assertEquals(ScoreCategory.FIVES, ScoreCalculator.timeoutCategory(player, dice))
    }

    @Test
    fun `a Quickfire timeout takes the first category in scorecard order when several tie for lowest`() {
        // Ones 4, Twos 2, then Threes/Fours/Fives/Sixes and more all 0 - Threes is the first of them.
        assertEquals(ScoreCategory.THREES, ScoreCalculator.timeoutCategory(quickfirePlayer, diceOf(1, 1, 1, 1, 2)))

        // A non-zero tie too: only Twos and Fours open, both worth 4 - Twos comes first.
        val twosAndFoursOpen = quickfirePlayer.copy(
            scorecard = quickfirePlayer.scorecard + GameMode.QUICKFIRE.categories
                .filter { it != ScoreCategory.TWOS && it != ScoreCategory.FOURS }
                .associateWith { listOf(0) },
        )
        assertEquals(ScoreCategory.TWOS, ScoreCalculator.timeoutCategory(twosAndFoursOpen, diceOf(2, 2, 4, 1, 1)))
    }

    @Test
    fun `a Quickfire timeout still obeys the joker rule's forced box`() {
        val player = quickfirePlayer.copy(scorecard = quickfirePlayer.scorecard + (ScoreCategory.FIVE_OF_A_KIND to listOf(50)))

        // A repeat 5x of 4s must go in Fours, even though other open boxes would score less.
        assertEquals(ScoreCategory.FOURS, ScoreCalculator.timeoutCategory(player, diceOf(4, 4, 4, 4, 4)))
    }

    // ---- Third Wind: three slots a box -----------------------------------------------------------

    private val thirdWindPlayer = PlayerState(name = "Player 1", type = PlayerType.HUMAN, gameMode = GameMode.THIRD_WIND)

    private fun thirdWind(vararg boxes: Pair<ScoreCategory, List<Int>>) = thirdWindPlayer.copy(scorecard = thirdWindPlayer.scorecard + boxes)

    @Test
    fun `in Third Wind a box can be scored until all three of its slots are used`() {
        val dice = diceOf(1, 2, 3, 4, 6)
        assertTrue(ScoreCategory.CHANCE in ScoreCalculator.availableCategories(thirdWind(ScoreCategory.CHANCE to listOf(20, 12)), dice))
        assertFalse(ScoreCategory.CHANCE in ScoreCalculator.availableCategories(thirdWind(ScoreCategory.CHANCE to listOf(20, 12, 9)), dice))
    }

    @Test
    fun `in Third Wind a 5x with a 5x slot still open scores 50 there - no bonus and no joker`() {
        val player = thirdWind(ScoreCategory.FIVE_OF_A_KIND to listOf(50, 50))
        val dice = diceOf(4, 4, 4, 4, 4)

        assertFalse(ScoreCalculator.awardsFiveOfAKindBonus(player, dice))
        assertEquals(50, ScoreCalculator.scoreFor(player, ScoreCategory.FIVE_OF_A_KIND, dice))
        // Not forced into Fours - every box is open to it.
        assertEquals(GameMode.THIRD_WIND.categories, ScoreCalculator.availableCategories(player, dice))
        // And a Full House takes it for what the dice show - nothing, as in any other turn.
        assertEquals(0, ScoreCalculator.scoreFor(player, ScoreCategory.FULL_HOUSE, dice))
    }

    @Test
    fun `in Third Wind the joker and bonus start once all three 5x slots are used with a 50 among them`() {
        val player = thirdWind(ScoreCategory.FIVE_OF_A_KIND to listOf(0, 50, 0))
        val dice = diceOf(4, 4, 4, 4, 4)

        assertTrue(ScoreCalculator.awardsFiveOfAKindBonus(player, dice))
        assertEquals(listOf(ScoreCategory.FOURS), ScoreCalculator.availableCategories(player, dice))
    }

    @Test
    fun `in Third Wind the joker stays with a Fours box partly used - its open slot is still forced`() {
        val player = thirdWind(ScoreCategory.FIVE_OF_A_KIND to listOf(50, 50, 50), ScoreCategory.FOURS to listOf(16, 12))
        assertEquals(listOf(ScoreCategory.FOURS), ScoreCalculator.availableCategories(player, diceOf(4, 4, 4, 4, 4)))
    }

    @Test
    fun `in Third Wind three zeroed 5x slots never start the joker`() {
        val player = thirdWind(ScoreCategory.FIVE_OF_A_KIND to listOf(0, 0, 0))
        assertFalse(ScoreCalculator.awardsFiveOfAKindBonus(player, diceOf(2, 2, 2, 2, 2)))
    }
}
