package net.zodac.dicefive.ui.achievements

import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.compose.ui.graphics.Color
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.ui.icons.Symbols

class AchievementIconColourTest {

    @Test
    fun `only the Easter Eggs and the few icons about a colour ignore the row's tint`() {
        val tint = Color.Magenta
        val fixed = Achievement.entries.filter { it.iconTintOrUnspecified(tint) != tint }.toSet()
        assertEquals(
            setOf(
                Achievement.EVERYTHING_EVERYWHERE_ALL_AT_ONCE,
                Achievement.TRICOLOURFUL,
                Achievement.TRICOLOUR_ME_IMPRESSED,
                Achievement.COMPLETIONIST,
                Achievement.ARE_YOU_A_DEVELOPER,
                Achievement.BIG_FAN,
                Achievement.LUCK_OF_THE_IRISH,
                Achievement.SHAKEN_NOT_TAPPED,
                Achievement.THE_MAGICIANS_SECRET,
                Achievement.NOT_THOSE_DICE,
                Achievement.ROLLING_THE_MIDNIGHT_DICE,
                Achievement.GREENFINGERS,
                Achievement.EGGCELLENT_DISCOVERY,
                Achievement.THE_SOLUTION,
            ),
            fixed,
        )
    }

    @Test
    fun `glyphs that point a way flip right to left, and no others`() {
        val all = mapOf(
            "ArrowBack" to Symbols.ArrowBack, "ArrowForward" to Symbols.ArrowForward, "Help" to Symbols.Help,
            "HelpOutline" to Symbols.HelpOutline, "KeyboardArrowLeft" to Symbols.KeyboardArrowLeft,
            "KeyboardArrowRight" to Symbols.KeyboardArrowRight, "Logout" to Symbols.Logout, "OpenInNew" to Symbols.OpenInNew,
            "Sort" to Symbols.Sort, "TrendingDown" to Symbols.TrendingDown, "TrendingUp" to Symbols.TrendingUp,
            "Undo" to Symbols.Undo, "VolumeUp" to Symbols.VolumeUp,
            // A sample of the rest, which stay as drawn.
            "ArrowUpward" to Symbols.ArrowUpward, "Close" to Symbols.Close, "QuestionMark" to Symbols.QuestionMark,
            "Star" to Symbols.Star, "AutoAwesome" to Symbols.AutoAwesome,
        )
        val mirrored = all.filterValues { it.autoMirror }.keys
        assertEquals(
            setOf(
                "ArrowBack", "ArrowForward", "Help", "HelpOutline", "KeyboardArrowLeft", "KeyboardArrowRight", "Logout",
                "OpenInNew", "Sort", "TrendingDown", "TrendingUp", "Undo", "VolumeUp",
            ),
            mirrored,
        )
    }

    @Test
    fun `a Symbol is 24dp whichever grid its path data is on`() {
        // AutoAwesome is still on Symbols' old 24 grid; the rest on the 960 one.
        for (glyph in listOf(Symbols.AutoAwesome, Symbols.Star)) {
            assertEquals(24f, glyph.defaultWidth.value)
            assertEquals(24f, glyph.defaultHeight.value)
        }
        assertEquals(24f, Symbols.AutoAwesome.viewportWidth)
        assertEquals(960f, Symbols.Star.viewportWidth)
    }
}
