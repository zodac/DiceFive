package net.zodac.dicefive.game

import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.ScoreCategory

private fun diceOf(vararg values: Int): List<Die> = values.map { Die(value = it) }

class DiceScoringTest {

    private fun score(category: ScoreCategory, vararg values: Int) = DiceScoring.score(category, diceOf(*values))

    @Test
    fun `each Standard box scores only the hand it asks for`() {
        // The upper section sums only matching dice.
        assertEquals(9, score(ScoreCategory.THREES, 3, 3, 5, 3, 6))
        assertEquals(5, score(ScoreCategory.FIVES, 3, 3, 5, 3, 6))
        assertEquals(0, score(ScoreCategory.TWOS, 3, 3, 5, 3, 6))
        // 3x sums all dice with at least three matching; 4x needs four.
        assertEquals(20, score(ScoreCategory.THREE_OF_A_KIND, 4, 4, 4, 2, 6))
        assertEquals(0, score(ScoreCategory.THREE_OF_A_KIND, 4, 4, 3, 2, 6))
        assertEquals(22, score(ScoreCategory.FOUR_OF_A_KIND, 5, 5, 5, 5, 2))
        assertEquals(0, score(ScoreCategory.FOUR_OF_A_KIND, 5, 5, 5, 2, 2))
        // Full House is exactly a three and two split.
        assertEquals(25, score(ScoreCategory.FULL_HOUSE, 2, 2, 5, 5, 5))
        assertEquals(0, score(ScoreCategory.FULL_HOUSE, 6, 6, 6, 6, 6))
        assertEquals(0, score(ScoreCategory.FULL_HOUSE, 2, 2, 2, 2, 5))
        // A small straight is four sequential distinct values, a large one five.
        assertEquals(30, score(ScoreCategory.SMALL_STRAIGHT, 1, 2, 3, 4, 4))
        assertEquals(0, score(ScoreCategory.SMALL_STRAIGHT, 1, 2, 3, 6, 6))
        assertEquals(40, score(ScoreCategory.LARGE_STRAIGHT, 2, 3, 4, 5, 6))
        assertEquals(0, score(ScoreCategory.LARGE_STRAIGHT, 1, 2, 3, 4, 4))
        // 5x needs all five dice matching.
        assertEquals(50, score(ScoreCategory.FIVE_OF_A_KIND, 3, 3, 3, 3, 3))
        assertEquals(0, score(ScoreCategory.FIVE_OF_A_KIND, 3, 3, 3, 3, 4))
        assertEquals(true, DiceScoring.isFiveOfAKind(diceOf(3, 3, 3, 3, 3)))
        assertEquals(false, DiceScoring.isFiveOfAKind(diceOf(3, 3, 3, 3, 4)))
        // Chance sums every die.
        assertEquals(15, score(ScoreCategory.CHANCE, 1, 2, 3, 4, 5))

        // A part-held hand of matching dice is no 5x - but still scores what it can.
        assertEquals(false, DiceScoring.isFiveOfAKind(diceOf(6, 6, 6)))
        assertEquals(0, score(ScoreCategory.FIVE_OF_A_KIND, 6, 6, 6))
        assertEquals(18, score(ScoreCategory.SIXES, 6, 6, 6))
        assertEquals(18, score(ScoreCategory.THREE_OF_A_KIND, 6, 6, 6))
        assertEquals(0, score(ScoreCategory.FULL_HOUSE, 6, 6, 6))
    }

    // ---- Tricolour's colour boxes ------------------------------------------------------------

    private fun colouredDice(vararg dice: Pair<Int, DieColour>): List<Die> = dice.map { (value, colour) -> Die(value = value, colour = colour) }

    @Test
    fun `a colour box scores 40 for five dice of its colour - and Coloured House is three of one colour and two of another`() {
        val allRed = colouredDice(1 to DieColour.RED, 3 to DieColour.RED, 4 to DieColour.RED, 6 to DieColour.RED, 2 to DieColour.RED)
        val fourRed = colouredDice(1 to DieColour.RED, 3 to DieColour.RED, 4 to DieColour.RED, 6 to DieColour.RED, 2 to DieColour.BLUE)
        // Whatever the numbers.
        assertEquals(40, DiceScoring.score(ScoreCategory.REDS, allRed))
        assertEquals(0, DiceScoring.score(ScoreCategory.YELLOWS, allRed))
        assertEquals(0, DiceScoring.score(ScoreCategory.BLUES, allRed))
        assertEquals(0, DiceScoring.score(ScoreCategory.REDS, fourRed))

        val house = colouredDice(1 to DieColour.RED, 2 to DieColour.RED, 3 to DieColour.RED, 4 to DieColour.BLUE, 5 to DieColour.BLUE)
        assertEquals(25, DiceScoring.score(ScoreCategory.COLOURED_HOUSE, house))
        assertEquals(0, DiceScoring.score(ScoreCategory.COLOURED_HOUSE, colouredDice(1 to DieColour.RED, 2 to DieColour.RED, 3 to DieColour.RED, 4 to DieColour.RED, 5 to DieColour.RED)))
        assertEquals(0, DiceScoring.score(ScoreCategory.COLOURED_HOUSE, colouredDice(1 to DieColour.RED, 2 to DieColour.RED, 3 to DieColour.RED, 4 to DieColour.BLUE, 5 to DieColour.YELLOW)))
        // Colour and number are independent: this is no Full House by number.
        assertEquals(0, DiceScoring.score(ScoreCategory.FULL_HOUSE, house))

        // Colourless dice never score a colour box.
        assertEquals(0, score(ScoreCategory.COLOURED_HOUSE, 2, 2, 2, 5, 5))
        assertEquals(0, score(ScoreCategory.REDS, 2, 2, 2, 5, 5))
    }

    @Test
    fun `Extended Scores - Two Pair scores the four dice of two different pairs - Evens and Odds only their own dice`() {
        assertEquals(22, score(ScoreCategory.TWO_PAIR, 6, 6, 5, 5, 1))
        assertEquals(6, score(ScoreCategory.TWO_PAIR, 2, 1, 1, 2, 4))
        // 4+4+2+2 - the third 4 is a full house's, not part of the two pair.
        assertEquals(12, score(ScoreCategory.TWO_PAIR, 4, 4, 2, 2, 4))
        assertEquals(12, score(ScoreCategory.TWO_PAIR, 4, 2, 4, 4, 2))
        // Two different numbers.
        assertEquals(0, score(ScoreCategory.TWO_PAIR, 3, 3, 3, 3, 5))
        assertEquals(0, score(ScoreCategory.TWO_PAIR, 6, 6, 6, 6, 6))
        assertEquals(0, score(ScoreCategory.TWO_PAIR, 6, 6, 5, 3, 1))
        assertEquals(0, score(ScoreCategory.TWO_PAIR, 1, 2, 3, 4, 5))

        assertEquals(10, score(ScoreCategory.EVENS, 6, 4, 3, 3, 1))
        assertEquals(7, score(ScoreCategory.ODDS, 6, 4, 3, 3, 1))
        assertEquals(0, score(ScoreCategory.EVENS, 1, 3, 5, 5, 1))
        assertEquals(0, score(ScoreCategory.ODDS, 2, 4, 6, 6, 2))
        // Together they are the whole roll - and a part-held Stud hand scores what it holds.
        assertEquals(30, score(ScoreCategory.EVENS, 6, 6, 6, 6, 6))
        assertEquals(6, score(ScoreCategory.EVENS, 6))
        assertEquals(0, score(ScoreCategory.TWO_PAIR, 6, 6, 5))
    }
}
