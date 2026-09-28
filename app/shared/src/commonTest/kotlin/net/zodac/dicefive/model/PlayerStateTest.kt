package net.zodac.dicefive.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerStateTest {

    private fun player(fiveOfAKindBox: Int?, bonusChips: Int = 0) = PlayerState(
        name = "P",
        type = PlayerType.HUMAN,
        scorecard = GameMode.STANDARD.categories.associateWith { null } + (ScoreCategory.FIVE_OF_A_KIND to fiveOfAKindBox),
        fiveOfAKindBonusCount = bonusChips,
    )

    @Test
    fun `a filled 5x box counts as one 5x - and each bonus chip as another`() {
        assertEquals(1, player(fiveOfAKindBox = 50).fiveOfAKindCount)
        assertEquals(4, player(fiveOfAKindBox = 50, bonusChips = 3).fiveOfAKindCount)
    }

    @Test
    fun `an open or zeroed 5x box was never a 5x`() {
        assertEquals(0, player(fiveOfAKindBox = null).fiveOfAKindCount)
        assertEquals(0, player(fiveOfAKindBox = 0).fiveOfAKindCount)
    }
}
