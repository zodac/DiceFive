package net.zodac.dicefive

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.data.scores.PlayerModeTotals
import net.zodac.dicefive.data.scores.PlayerRecord
import net.zodac.dicefive.data.scores.ScoreDao
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.ui.statistics.StatisticsScreen
import net.zodac.dicefive.ui.statistics.StatisticsViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import net.zodac.dicefive.data.scores.PlayerStatistics
import net.zodac.dicefive.ui.statistics.StatisticsSort
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Statistics cards open when they all fit on screen, else only the first does; opening one never closes another. An
 * opened card is the one showing the Overview / By mode switch.
 */
@RunWith(AndroidJUnit4::class)
class StatisticsDefaultExpansionTest {

    @get:Rule
    val compose = createComposeRule()

    private val showcase = Showcase(compose)

    private fun showPlayers(count: Int) {
        val records = (1..count).map { PlayerRecord("Player$it", listOf(totals("player$it", maxScore = 100 * it)), emptyList()) }
        val dao = object : ScoreDao by RowsDao(emptyList()) {
            override suspend fun playerRecords() = records
        }
        val viewModel = StatisticsViewModel(ScoreRepository(dao), SettingsRepository(InMemoryPreferences()))
        showcase.show { DiceFiveTheme { StatisticsScreen(viewModel = viewModel, onBack = {}) } }
        compose.waitForIdle()
    }

    private fun totals(key: String, maxScore: Int) = PlayerModeTotals(
        playerKey = key, gameModeId = GameMode.STANDARD.id, gamesPlayed = 2, gamesWon = 1, gamesLost = 1, soloGames = 0,
        maxScore = maxScore, totalScore = 300, fiveOfAKindCount = 0, firstPlayedEpochMillis = 1_000L,
    )

    @Test
    @Config(qualifiers = "w400dp-h3000dp")
    fun `every card opens when they all fit on screen`() {
        showPlayers(3)
        compose.onAllNodesWithText("Overview").assertCountEquals(3)
    }

    @Test
    fun `only the first card opens when they don't all fit - and opening another leaves it open`() {
        showPlayers(4)
        compose.onAllNodesWithText("Overview").assertCountEquals(1)

        compose.onNodeWithContentDescription("Player1. Best score 100.").assertExists()
        compose.onNodeWithContentDescription("Player2. Best score 200.").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Overview").assertCountEquals(2)
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp")
    fun `in landscape the top card of each column opens`() {
        showPlayers(5)
        compose.onAllNodesWithText("Overview").assertCountEquals(2)
    }

    @Test
    fun `the sort menu names its options - marks the one in use and reverses it when tapped again - which reorders the cards`() {
        showPlayers(3)
        compose.onNodeWithContentDescription("Sort players").performClick()
        compose.waitForIdle()
        listOf("Name", "First played", "Highest score", "Highest average", "Most games").forEach {
            compose.onNodeWithContentDescription(it).assertExists()
        }
        var config = compose.onNodeWithContentDescription("Name").fetchSemanticsNode().config
        assertEquals("Selected, ascending", config[SemanticsProperties.StateDescription])
        assertEquals("Reverse order", config[SemanticsActions.OnClick].label)

        compose.onNodeWithContentDescription("Name").performClick()
        compose.onNodeWithContentDescription("Sort players").performClick()
        compose.waitForIdle()
        config = compose.onNodeWithContentDescription("Name").fetchSemanticsNode().config
        assertEquals("Selected, descending", config[SemanticsProperties.StateDescription])

        // Another option starts in its own direction: highest first.
        compose.onNodeWithContentDescription("Most games").performClick()
        compose.onNodeWithContentDescription("Sort players").performClick()
        compose.waitForIdle()
        config = compose.onNodeWithContentDescription("Most games").fetchSemanticsNode().config
        assertEquals("Selected, descending", config[SemanticsProperties.StateDescription])
    }

    @Test
    fun `sorting orders by the option in either direction - ties by name`() {
        fun player(name: String, played: Int, total: Int, max: Int, first: Long) = PlayerStatistics(
            playerName = name, firstPlayedEpochMillis = first, gamesPlayed = played, gamesWon = 0, gamesLost = 0, currentWinStreak = 0,
            bestWinStreak = 0, maxScore = max, totalScore = total, fiveOfAKindCount = 0, soloGames = 0,
        )
        val players = listOf(
            player("bob", played = 4, total = 400, max = 150, first = 30),
            player("Ann", played = 4, total = 800, max = 300, first = 20),
            player("Cleo", played = 2, total = 400, max = 300, first = 10),
        )
        fun names(sort: StatisticsSort, ascending: Boolean) = sort.order(players, ascending).map { it.playerName }
        assertEquals(listOf("Ann", "bob", "Cleo"), names(StatisticsSort.NAME, true))
        assertEquals(listOf("Cleo", "bob", "Ann"), names(StatisticsSort.NAME, false))
        assertEquals(listOf("Cleo", "Ann", "bob"), names(StatisticsSort.FIRST_PLAYED, true))
        assertEquals(listOf("Ann", "Cleo", "bob"), names(StatisticsSort.HIGHEST_SCORE, false))
        assertEquals(listOf("Ann", "Cleo", "bob"), names(StatisticsSort.HIGHEST_AVERAGE, false))
        assertEquals(listOf("Ann", "bob", "Cleo"), names(StatisticsSort.MOST_GAMES, false))
        assertEquals(listOf("Cleo", "Ann", "bob"), names(StatisticsSort.MOST_GAMES, true))
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `a new order opens its top card and closes the rest - whatever was open before`() {
        showPlayers(4)
        fun state(name: String, best: Int) = compose.onNodeWithContentDescription("$name. Best score $best.")
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertEquals("Expanded", state("Player1", 100))
        assertEquals("Collapsed", state("Player4", 400))

        compose.onNodeWithContentDescription("Sort players").performClick()
        compose.onNodeWithContentDescription("Highest score").performClick()
        compose.waitForIdle()
        // Player4 now leads, so it opens; Player1, now last, closes.
        assertEquals("Expanded", state("Player4", 400))
        assertEquals("Collapsed", state("Player1", 100))
        compose.onAllNodesWithText("Overview").assertCountEquals(1)
    }

    @Test
    fun `a lone player's card is open even where it doesn't fit`() {
        showPlayers(1)
        compose.onAllNodesWithText("Overview").assertCountEquals(1)
    }
}
