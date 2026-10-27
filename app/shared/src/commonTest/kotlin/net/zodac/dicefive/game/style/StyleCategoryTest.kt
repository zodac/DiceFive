package net.zodac.dicefive.game.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.settings.DefaultStyleIds
import net.zodac.dicefive.data.settings.SavedStyles
import net.zodac.dicefive.model.Achievement

class StyleCategoryTest {

    private fun picks(dice: String = DefaultStyleIds.DICE, achievements: AchievementsState = AchievementsState(), ownsAllStyles: Boolean = false) =
        SavedStyles(dice, DefaultStyleIds.DICE_CUP, DefaultStyleIds.TABLE_BACKGROUND, DefaultStyleIds.DICE_MAT, achievements, scoreFrameId = DefaultStyleIds.SCORE_FRAME, ownsAllStyles = ownsAllStyles)

    @Test
    fun `the categories are the registries in the Styles screen's order - each reading its own pick`() {
        assertEquals(listOf(DiceStyleRegistry, DiceCupStyleRegistry, DiceMatRegistry, TableBackgroundRegistry, ScoreFrameRegistry), StyleCategory.entries.map { it.registry })
        val picks = SavedStyles("dice", "cup", "background", "mat", AchievementsState(), scoreFrameId = "frame")
        assertEquals(listOf("dice", "cup", "mat", "background", "frame"), StyleCategory.entries.map { it.pickIn(picks) })
    }

    @Test
    fun `a table drawn in anything but the defaults - a locked or unknown pick drawing its default`() {
        assertFalse(picks().hasNonDefaultStyle)
        assertFalse(picks(dice = "no_such_style").hasNonDefaultStyle)
        // Leather opens at 5 achievements: picked but locked, it's drawn as the default.
        assertFalse(picks(dice = "leather_tan").hasNonDefaultStyle)
        val five = AchievementsState(Achievement.entries.take(5).associateWith { 0L })
        assertTrue(picks(dice = "leather_tan", achievements = five).hasNonDefaultStyle)
        assertTrue(picks(dice = "leather_tan", ownsAllStyles = true).hasNonDefaultStyle)
    }
}
