package net.zodac.dicefive.game

import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.ScoreCategory
import org.junit.Assert.assertEquals
import org.junit.Test

private fun diceOf(vararg values: Int): List<Die> = values.map { Die(value = it) }

class YahtzeeScoringTest {

    @Test
    fun `upper section sums only matching dice`() {
        val dice = diceOf(3, 3, 5, 3, 6)

        assertEquals(9, YahtzeeScoring.score(ScoreCategory.THREES, dice))
        assertEquals(5, YahtzeeScoring.score(ScoreCategory.FIVES, dice))
        assertEquals(0, YahtzeeScoring.score(ScoreCategory.TWOS, dice))
    }

    @Test
    fun `three of a kind sums all dice when at least three match`() {
        val dice = diceOf(4, 4, 4, 2, 6)

        assertEquals(20, YahtzeeScoring.score(ScoreCategory.THREE_OF_A_KIND, dice))
    }

    @Test
    fun `three of a kind scores zero without a triple`() {
        val dice = diceOf(4, 4, 3, 2, 6)

        assertEquals(0, YahtzeeScoring.score(ScoreCategory.THREE_OF_A_KIND, dice))
    }

    @Test
    fun `four of a kind requires four matching dice`() {
        val four = diceOf(5, 5, 5, 5, 2)
        val three = diceOf(5, 5, 5, 2, 2)

        assertEquals(22, YahtzeeScoring.score(ScoreCategory.FOUR_OF_A_KIND, four))
        assertEquals(0, YahtzeeScoring.score(ScoreCategory.FOUR_OF_A_KIND, three))
    }

    @Test
    fun `full house requires exactly a three and two split`() {
        val fullHouse = diceOf(2, 2, 5, 5, 5)
        val yahtzee = diceOf(6, 6, 6, 6, 6)
        val fourAndOne = diceOf(2, 2, 2, 2, 5)

        assertEquals(25, YahtzeeScoring.score(ScoreCategory.FULL_HOUSE, fullHouse))
        assertEquals(0, YahtzeeScoring.score(ScoreCategory.FULL_HOUSE, yahtzee))
        assertEquals(0, YahtzeeScoring.score(ScoreCategory.FULL_HOUSE, fourAndOne))
    }

    @Test
    fun `small straight needs four sequential distinct values`() {
        val straight = diceOf(1, 2, 3, 4, 4)
        val notStraight = diceOf(1, 2, 3, 6, 6)

        assertEquals(30, YahtzeeScoring.score(ScoreCategory.SMALL_STRAIGHT, straight))
        assertEquals(0, YahtzeeScoring.score(ScoreCategory.SMALL_STRAIGHT, notStraight))
    }

    @Test
    fun `large straight needs five sequential distinct values`() {
        val straight = diceOf(2, 3, 4, 5, 6)
        val notStraight = diceOf(1, 2, 3, 4, 4)

        assertEquals(40, YahtzeeScoring.score(ScoreCategory.LARGE_STRAIGHT, straight))
        assertEquals(0, YahtzeeScoring.score(ScoreCategory.LARGE_STRAIGHT, notStraight))
    }

    @Test
    fun `yahtzee requires all five dice matching`() {
        val yahtzee = diceOf(3, 3, 3, 3, 3)
        val notYahtzee = diceOf(3, 3, 3, 3, 4)

        assertEquals(50, YahtzeeScoring.score(ScoreCategory.YAHTZEE, yahtzee))
        assertEquals(0, YahtzeeScoring.score(ScoreCategory.YAHTZEE, notYahtzee))
        assertEquals(true, YahtzeeScoring.isYahtzee(yahtzee))
        assertEquals(false, YahtzeeScoring.isYahtzee(notYahtzee))
    }

    @Test
    fun `chance sums every die`() {
        val dice = diceOf(1, 2, 3, 4, 5)

        assertEquals(15, YahtzeeScoring.score(ScoreCategory.CHANCE, dice))
    }
}
