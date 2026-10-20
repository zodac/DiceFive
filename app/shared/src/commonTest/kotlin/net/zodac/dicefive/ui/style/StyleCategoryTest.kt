package net.zodac.dicefive.ui.style

import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.settings.DefaultStyleIds
import net.zodac.dicefive.data.settings.SavedStyles

class StyleCategoryTest {

    @Test
    fun `each category's default is the style the settings fall back to - its saved id`() {
        assertEquals(DefaultStyleIds.DICE, StyleCategory.DICE.catalog.default.id)
        assertEquals(DefaultStyleIds.DICE_CUP, StyleCategory.DICE_CUP.catalog.default.id)
        assertEquals(DefaultStyleIds.DICE_MAT, StyleCategory.MAT.catalog.default.id)
        assertEquals(DefaultStyleIds.TABLE_BACKGROUND, StyleCategory.BACKGROUND.catalog.default.id)
        assertEquals(DefaultStyleIds.SCORE_FRAME, StyleCategory.FRAME.catalog.default.id)
    }

    @Test
    fun `the categories are the catalogs in the Styles screen's order - each reading its own pick`() {
        assertEquals(listOf(DiceStyles, DiceCupStyles, DiceMats, TableBackgrounds, ScoreFrames), StyleCategory.entries.map { it.catalog })
        assertEquals(StyleCatalogs, StyleCategory.entries.map { it.catalog })
        val picks = SavedStyles("dice", "cup", "background", "mat", AchievementsState(), scoreFrameId = "frame")
        assertEquals(listOf("dice", "cup", "mat", "background", "frame"), StyleCategory.entries.map { it.pickIn(picks) })
    }
}
