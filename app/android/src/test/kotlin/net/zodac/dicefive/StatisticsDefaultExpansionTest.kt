package net.zodac.dicefive

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.data.scores.PlayerModeTotals
import net.zodac.dicefive.data.scores.PlayerRecord
import net.zodac.dicefive.data.scores.ScoreDao
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.ui.statistics.StatisticsScreen
import net.zodac.dicefive.ui.statistics.StatisticsViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme
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
        val records = (1..count).map { PlayerRecord("Player$it", listOf(totals("player$it")), emptyList()) }
        val dao = object : ScoreDao by RowsDao(emptyList()) {
            override suspend fun playerRecords() = records
        }
        val viewModel = StatisticsViewModel(ScoreRepository(dao))
        showcase.show { DiceFiveTheme { StatisticsScreen(viewModel = viewModel, onBack = {}) } }
        compose.waitForIdle()
    }

    private fun totals(key: String) = PlayerModeTotals(
        playerKey = key, gameModeId = GameMode.STANDARD.id, gamesPlayed = 2, gamesWon = 1, gamesLost = 1, soloGames = 0,
        maxScore = 200, totalScore = 300, fiveOfAKindCount = 0, firstPlayedEpochMillis = 1_000L,
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

        compose.onNodeWithContentDescription("Player1. Best score 200.").assertExists()
        compose.onNodeWithContentDescription("Player2. Best score 200.").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Overview").assertCountEquals(2)
    }

    @Test
    fun `a lone player's card is open even where it doesn't fit`() {
        showPlayers(1)
        compose.onAllNodesWithText("Overview").assertCountEquals(1)
    }
}
