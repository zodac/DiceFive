package net.zodac.dicefive.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerStateTest {

    @Test
    fun `a perfect game totals exactly MAX_POSSIBLE_SCORE`() {
        val perfectGame = PlayerState(
            name = "Perfect",
            type = PlayerType.HUMAN,
            scorecard = mapOf(
                ScoreCategory.ONES to 5,
                ScoreCategory.TWOS to 10,
                ScoreCategory.THREES to 15,
                ScoreCategory.FOURS to 20,
                ScoreCategory.FIVES to 25,
                ScoreCategory.SIXES to 30,
                ScoreCategory.THREE_OF_A_KIND to 30,
                ScoreCategory.FOUR_OF_A_KIND to 30,
                ScoreCategory.FULL_HOUSE to 25,
                ScoreCategory.SMALL_STRAIGHT to 30,
                ScoreCategory.LARGE_STRAIGHT to 40,
                ScoreCategory.FIVE_OF_A_KIND to 50,
                ScoreCategory.CHANCE to 30,
            ),
            // The 5x box itself is the first of the 13 turns; every other turn is also a 5x.
            fiveOfAKindBonusCount = 12,
        )

        assertEquals(PlayerState.MAX_POSSIBLE_SCORE, perfectGame.totalScore)
    }
}
