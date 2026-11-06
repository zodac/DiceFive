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
import net.zodac.dicefive.ui.statistics.untilPlayTimeChanges
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** The time played, in hours and minutes on screen and in words to a screen reader, on Statistics with or without players. */
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
        assertLine(1234 * 3600 + 5 * 60, "Time played: 1,234h 5m", "Time played: 1234 hours and 5 minutes")
    }

    @Test
    fun `seconds change each second for the first minute - then the line changes on each whole minute`() {
        assertEquals(1.seconds, untilPlayTimeChanges(0))
        assertEquals(1.seconds, untilPlayTimeChanges(59))
        assertEquals(60.seconds, untilPlayTimeChanges(60))
        assertEquals(1.seconds, untilPlayTimeChanges(119))
        assertEquals(35.seconds, untilPlayTimeChanges(3625))
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
