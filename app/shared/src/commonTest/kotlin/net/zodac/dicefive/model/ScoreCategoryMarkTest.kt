package net.zodac.dicefive.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** The boxes named by the game's own mark rather than in words - see [ScoreCategory.mark]. */
class ScoreCategoryMarkTest {

    @Test
    fun `3x - 4x - 5x and Alibi are named by their marks - every other box in words`() {
        assertEquals(
            mapOf(
                ScoreCategory.THREE_OF_A_KIND to "3x",
                ScoreCategory.FOUR_OF_A_KIND to "4x",
                ScoreCategory.FIVE_OF_A_KIND to "5x",
                ScoreCategory.ALIBI to "Alibi",
            ),
            ScoreCategory.entries.mapNotNull { category -> category.mark?.let { category to it } }.toMap(),
        )
    }
}
