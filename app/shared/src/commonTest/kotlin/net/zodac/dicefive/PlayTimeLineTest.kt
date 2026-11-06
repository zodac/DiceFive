package net.zodac.dicefive

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.ui.statistics.PlayTimeLine
import net.zodac.dicefive.ui.statistics.StatisticsScreen
import net.zodac.dicefive.ui.statistics.StatisticsViewModel
import net.zodac.dicefive.ui.statistics.playTimeNumbers
import net.zodac.dicefive.ui.statistics.untilPlayTimeChanges
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** The time played, in days, hours, and minutes on screen and in words to a screen reader, on Statistics with or without players. */
class PlayTimeLineTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun assertLine(seconds: Int, shown: String, spoken: String) {
        showcase.show { DiceFiveTheme { PlayTimeLine(seconds) } }
        compose.onNodeWithContentDescription(spoken).assertContentDescriptionEquals(spoken).assertIsDisplayed()
        compose.onNodeWithText(shown, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `it shows hours and minutes - says them in words - and leaves out a part that's zero`() = uiTest {
        assertLine(0, "Time played: 0s", "Time played: 0 seconds")
        assertLine(1, "Time played: 1s", "Time played: 1 second")
        assertLine(59, "Time played: 59s", "Time played: 59 seconds")
        assertLine(60, "Time played: 1m", "Time played: 1 minute")
        assertLine(34 * 60 + 59, "Time played: 34m", "Time played: 34 minutes")
        assertLine(3600, "Time played: 1h 0m", "Time played: 1 hour")
        assertLine(3600 + 60, "Time played: 1h 1m", "Time played: 1 hour and 1 minute")
        assertLine(12 * 3600 + 34 * 60, "Time played: 12h 34m", "Time played: 12 hours and 34 minutes")
        assertLine(23 * 3600 + 59 * 60 + 59, "Time played: 23h 59m", "Time played: 23 hours and 59 minutes")
        assertLine(24 * 3600, "Time played: 1d 0h 0m", "Time played: 1 day")
        assertLine(25 * 3600 + 59 * 60, "Time played: 1d 1h 59m", "Time played: 1 day, 1 hour, and 59 minutes")
        assertLine(24 * 3600 + 5 * 60, "Time played: 1d 0h 5m", "Time played: 1 day and 5 minutes")
        assertLine(3 * 86400 + 4 * 3600, "Time played: 3d 4h 0m", "Time played: 3 days and 4 hours")
        assertLine(3 * 86400 + 4 * 3600 + 12 * 60, "Time played: 3d 4h 12m", "Time played: 3 days, 4 hours, and 12 minutes")
        assertLine(1234 * 86400 + 5 * 3600 + 6 * 60, "Time played: 1,234d 5h 6m", "Time played: 1234 days, 5 hours, and 6 minutes")
    }

    @Test
    fun `the numbers are found whole - with their grouping - in any language - and the units aren't`() {
        fun numbers(shown: String) = playTimeNumbers(shown).map { shown.substring(it) }
        assertEquals(listOf("42"), numbers("42s"))
        assertEquals(listOf("1,234", "5", "6"), numbers("1,234d 5h 6m"))
        assertEquals(listOf("1.234", "5", "6"), numbers("1.234 d 5 h 6 min"))
        assertEquals(listOf("\u0663\u066C\u0664", "\u0664", "\u0661\u0662"), numbers("\u0663\u066C\u0664 \u064A \u0664 \u0633 \u0661\u0662 \u062F"))
    }

    @Test
    fun `seconds change each second for the first minute - then the line changes on each whole minute`() {
        assertEquals(1.seconds, untilPlayTimeChanges(0))
        assertEquals(1.seconds, untilPlayTimeChanges(59))
        assertEquals(60.seconds, untilPlayTimeChanges(60))
        assertEquals(1.seconds, untilPlayTimeChanges(119))
        assertEquals(35.seconds, untilPlayTimeChanges(3625))
        assertEquals(1.seconds, untilPlayTimeChanges(86399))
        assertEquals(60.seconds, untilPlayTimeChanges(86400))
        assertEquals(59.seconds, untilPlayTimeChanges(86401))
    }

    @Test
    fun `Statistics shows it before anyone has a card`() = uiTest {
        val viewModel = StatisticsViewModel(
            ScoreRepository(InMemoryScoreDao(emptyList())),
            SettingsRepository(InMemoryPreferences()),
            readPlaySeconds = { 90 * 60 },
        )
        showcase.show { DiceFiveTheme { StatisticsScreen(viewModel = viewModel, onBack = {}) } }
        compose.onNodeWithContentDescription("Time played: 1 hour and 30 minutes").assertIsDisplayed()
        compose.onNodeWithText("No statistics yet - play a game!").assertExists()
    }
}
