package net.zodac.dicefive.game

import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.ScoreCategory

private fun diceOf(vararg values: Int): List<Die> = values.map { Die(value = it) }

class DiceScoringTest {

    @Test
    fun `upper section sums only matching dice`() {
        val dice = diceOf(3, 3, 5, 3, 6)

        assertEquals(9, DiceScoring.score(ScoreCategory.THREES, dice))
        assertEquals(5, DiceScoring.score(ScoreCategory.FIVES, dice))
        assertEquals(0, DiceScoring.score(ScoreCategory.TWOS, dice))
    }

    @Test
    fun `three of a kind sums all dice when at least three match`() {
        val dice = diceOf(4, 4, 4, 2, 6)

        assertEquals(20, DiceScoring.score(ScoreCategory.THREE_OF_A_KIND, dice))
    }

    @Test
    fun `three of a kind scores zero without a triple`() {
        val dice = diceOf(4, 4, 3, 2, 6)

        assertEquals(0, DiceScoring.score(ScoreCategory.THREE_OF_A_KIND, dice))
    }

    @Test
    fun `four of a kind requires four matching dice`() {
        val four = diceOf(5, 5, 5, 5, 2)
        val three = diceOf(5, 5, 5, 2, 2)

        assertEquals(22, DiceScoring.score(ScoreCategory.FOUR_OF_A_KIND, four))
        assertEquals(0, DiceScoring.score(ScoreCategory.FOUR_OF_A_KIND, three))
    }

    @Test
    fun `full house requires exactly a three and two split`() {
        val fullHouse = diceOf(2, 2, 5, 5, 5)
        val fiveOfAKind = diceOf(6, 6, 6, 6, 6)
        val fourAndOne = diceOf(2, 2, 2, 2, 5)

        assertEquals(25, DiceScoring.score(ScoreCategory.FULL_HOUSE, fullHouse))
        assertEquals(0, DiceScoring.score(ScoreCategory.FULL_HOUSE, fiveOfAKind))
        assertEquals(0, DiceScoring.score(ScoreCategory.FULL_HOUSE, fourAndOne))
    }

    @Test
    fun `small straight needs four sequential distinct values`() {
        val straight = diceOf(1, 2, 3, 4, 4)
        val notStraight = diceOf(1, 2, 3, 6, 6)

        assertEquals(30, DiceScoring.score(ScoreCategory.SMALL_STRAIGHT, straight))
        assertEquals(0, DiceScoring.score(ScoreCategory.SMALL_STRAIGHT, notStraight))
    }

    @Test
    fun `large straight needs five sequential distinct values`() {
        val straight = diceOf(2, 3, 4, 5, 6)
        val notStraight = diceOf(1, 2, 3, 4, 4)

        assertEquals(40, DiceScoring.score(ScoreCategory.LARGE_STRAIGHT, straight))
        assertEquals(0, DiceScoring.score(ScoreCategory.LARGE_STRAIGHT, notStraight))
    }

    @Test
    fun `five of a kind requires all five dice matching`() {
        val fiveOfAKind = diceOf(3, 3, 3, 3, 3)
        val notFiveOfAKind = diceOf(3, 3, 3, 3, 4)

        assertEquals(50, DiceScoring.score(ScoreCategory.FIVE_OF_A_KIND, fiveOfAKind))
        assertEquals(0, DiceScoring.score(ScoreCategory.FIVE_OF_A_KIND, notFiveOfAKind))
        assertEquals(true, DiceScoring.isFiveOfAKind(fiveOfAKind))
        assertEquals(false, DiceScoring.isFiveOfAKind(notFiveOfAKind))
    }

    @Test
    fun `a part-held hand of matching dice is no 5x - but still scores what it can`() {
        val threeSixes = diceOf(6, 6, 6)

        assertEquals(false, DiceScoring.isFiveOfAKind(threeSixes))
        assertEquals(0, DiceScoring.score(ScoreCategory.FIVE_OF_A_KIND, threeSixes))
        assertEquals(18, DiceScoring.score(ScoreCategory.SIXES, threeSixes))
        assertEquals(18, DiceScoring.score(ScoreCategory.THREE_OF_A_KIND, threeSixes))
        assertEquals(0, DiceScoring.score(ScoreCategory.FULL_HOUSE, threeSixes))
    }

    @Test
    fun `chance sums every die`() {
        val dice = diceOf(1, 2, 3, 4, 5)

        assertEquals(15, DiceScoring.score(ScoreCategory.CHANCE, dice))
    }

    // ---- Tricolour's colour boxes ------------------------------------------------------------

    private fun colouredDice(vararg dice: Pair<Int, DieColour>): List<Die> = dice.map { (value, colour) -> Die(value = value, colour = colour) }

    @Test
    fun `a colour box scores 40 only when all five dice are that colour - whatever the numbers`() {
        val allRed = colouredDice(1 to DieColour.RED, 3 to DieColour.RED, 4 to DieColour.RED, 6 to DieColour.RED, 2 to DieColour.RED)
        val fourRed = colouredDice(1 to DieColour.RED, 3 to DieColour.RED, 4 to DieColour.RED, 6 to DieColour.RED, 2 to DieColour.BLUE)

        assertEquals(40, DiceScoring.score(ScoreCategory.REDS, allRed))
        assertEquals(0, DiceScoring.score(ScoreCategory.YELLOWS, allRed))
        assertEquals(0, DiceScoring.score(ScoreCategory.BLUES, allRed))
        assertEquals(0, DiceScoring.score(ScoreCategory.REDS, fourRed))
    }

    @Test
    fun `coloured house is three of one colour and two of another`() {
        val house = colouredDice(1 to DieColour.RED, 2 to DieColour.RED, 3 to DieColour.RED, 4 to DieColour.BLUE, 5 to DieColour.BLUE)
        val fiveOfOneColour = colouredDice(1 to DieColour.RED, 2 to DieColour.RED, 3 to DieColour.RED, 4 to DieColour.RED, 5 to DieColour.RED)
        val threeColours = colouredDice(1 to DieColour.RED, 2 to DieColour.RED, 3 to DieColour.RED, 4 to DieColour.BLUE, 5 to DieColour.YELLOW)

        assertEquals(25, DiceScoring.score(ScoreCategory.COLOURED_HOUSE, house))
        assertEquals(0, DiceScoring.score(ScoreCategory.COLOURED_HOUSE, fiveOfOneColour))
        assertEquals(0, DiceScoring.score(ScoreCategory.COLOURED_HOUSE, threeColours))
        // Colour and number are independent: this is no Full House by number.
        assertEquals(0, DiceScoring.score(ScoreCategory.FULL_HOUSE, house))
    }

    @Test
    fun `colourless dice never score a colour box`() {
        val dice = diceOf(2, 2, 2, 5, 5)

        assertEquals(0, DiceScoring.score(ScoreCategory.COLOURED_HOUSE, dice))
        assertEquals(0, DiceScoring.score(ScoreCategory.REDS, dice))
    }

    @Test
    fun `two pair scores the four dice making the pairs`() {
        assertEquals(22, DiceScoring.score(ScoreCategory.TWO_PAIR, diceOf(6, 6, 5, 5, 1)))
        assertEquals(6, DiceScoring.score(ScoreCategory.TWO_PAIR, diceOf(2, 1, 1, 2, 4)))
    }

    @Test
    fun `two pair ignores a fifth die even when it matches a pair`() {
        // 4+4+2+2 - the third 4 is a full house's, not part of the two pair.
        assertEquals(12, DiceScoring.score(ScoreCategory.TWO_PAIR, diceOf(4, 4, 2, 2, 4)))
        assertEquals(12, DiceScoring.score(ScoreCategory.TWO_PAIR, diceOf(4, 2, 4, 4, 2)))
    }

    @Test
    fun `two pair needs two different numbers`() {
        assertEquals(0, DiceScoring.score(ScoreCategory.TWO_PAIR, diceOf(3, 3, 3, 3, 5)))
        assertEquals(0, DiceScoring.score(ScoreCategory.TWO_PAIR, diceOf(6, 6, 6, 6, 6)))
        assertEquals(0, DiceScoring.score(ScoreCategory.TWO_PAIR, diceOf(6, 6, 5, 3, 1)))
        assertEquals(0, DiceScoring.score(ScoreCategory.TWO_PAIR, diceOf(1, 2, 3, 4, 5)))
    }

    @Test
    fun `evens and odds each add up only their own dice`() {
        val dice = diceOf(6, 4, 3, 3, 1)

        assertEquals(10, DiceScoring.score(ScoreCategory.EVENS, dice))
        assertEquals(7, DiceScoring.score(ScoreCategory.ODDS, dice))
    }

    @Test
    fun `evens or odds is zero when no die is of that kind`() {
        assertEquals(0, DiceScoring.score(ScoreCategory.EVENS, diceOf(1, 3, 5, 5, 1)))
        assertEquals(0, DiceScoring.score(ScoreCategory.ODDS, diceOf(2, 4, 6, 6, 2)))
    }

    @Test
    fun `evens and odds together are the whole roll - a part-held Stud hand scores what it holds`() {
        assertEquals(30, DiceScoring.score(ScoreCategory.EVENS, diceOf(6, 6, 6, 6, 6)))
        assertEquals(6, DiceScoring.score(ScoreCategory.EVENS, diceOf(6)))
        assertEquals(0, DiceScoring.score(ScoreCategory.TWO_PAIR, diceOf(6, 6, 5)))
    }
}
