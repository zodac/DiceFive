package net.zodac.dicefive.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How each box is named: by the game's own mark ([ScoreCategory.mark]), in words ([ScoreCategory.displayName]), or by its rule. */
class ScoreCategoryNameTest {

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

    @Test
    fun `every box has one way to be named - a mark, a name in words, or its rule's number or range`() {
        for (category in ScoreCategory.entries) {
            val namedByRule = category.rule is ScoreRule.Target || category.rule is ScoreRule.Wildcard ||
                (category.rule is ScoreRule.Numbered && category.section != ScoreSection.UPPER)
            val ways = listOf(category.mark != null, category.displayName != null, namedByRule).count { it }
            assertEquals(1, ways, "$category")
        }
    }

    @Test
    fun `only the colour boxes have Luck Of The Irish names`() {
        assertEquals(
            ScoreCategory.entries.filter { it.rule is ScoreRule.ColourSet },
            ScoreCategory.entries.filter { it.irishName != null },
        )
        assertTrue(ScoreCategory.entries.filter { it.irishName != null }.all { it.displayName != null })
    }
}
