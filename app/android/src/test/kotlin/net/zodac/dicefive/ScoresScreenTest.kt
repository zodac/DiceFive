package net.zodac.dicefive

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import net.zodac.dicefive.data.scores.PlayerGame
import net.zodac.dicefive.data.scores.SCORES_PAGE_SIZE
import net.zodac.dicefive.data.scores.ScoreDao
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.ui.scores.ScoresScreen
import net.zodac.dicefive.ui.scores.ScoresViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Rows held in memory, ordered by score, for a Leaderboard to read. */
internal class RowsDao(private val rows: List<ScoreEntry>) : ScoreDao {
    private fun ordered(modeId: String?, includeOffBoard: Boolean = false) =
        rows.filter { (it.onLeaderboard || includeOffBoard) && (modeId == null || it.gameModeId == modeId) }.sortedByDescending { it.score }

    override suspend fun insert(entry: ScoreEntry) = Unit
    override suspend fun pagedScores(limit: Int, offset: Int) = ordered(null).drop(offset).take(limit)
    override suspend fun pagedScoresForMode(gameModeId: String, includeOffBoard: Boolean, limit: Int, offset: Int) =
        ordered(gameModeId, includeOffBoard).drop(offset).take(limit)
    override suspend fun count() = ordered(null).size
    override suspend fun countForMode(gameModeId: String, includeOffBoard: Boolean) = ordered(gameModeId, includeOffBoard).size
    override suspend fun bestScoreForPlayer(playerName: String): Int? = null
    override suspend fun distinctScores(): List<Int> = emptyList()
    override suspend fun primaryPlayerTotalPoints(): Int? = null
    override suspend fun playerGames(): List<PlayerGame> = emptyList()
    override suspend fun dismissPlayer(playerName: String) = Unit
    override suspend fun clearDismissal(playerName: String) = Unit
    override suspend fun clearAllScores() = Unit
    override suspend fun clearAllDismissals() = Unit
}

internal fun rowOf(id: Long, score: Int, mode: GameMode) = ScoreEntry(
    id = id, playerName = "Player$id", score = score, timestampEpochMillis = 1_000L * id, won = null, isPrimaryPlayer = false,
    fiveOfAKindCount = 0, zeroedCategoryCount = 0, upperSectionTotal = 0, chanceScore = 0,
    threeOfAKindScore = 0, fourOfAKindScore = 0, gameModeId = mode.id,
)

/**
 * The Leaderboard's Combined / Game Mode switch: what each view shows, and what a screen reader hears for a
 * row (the mode and date a long press shows). How the cards look is for a render; this pins the structure.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ScoresScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(rows: List<ScoreEntry>) {
        val viewModel = ScoresViewModel(ScoreRepository(RowsDao(rows)))
        compose.setContent { DiceFiveTheme { ScoresScreen(viewModel = viewModel, onBack = {}) } }
        compose.waitForIdle()
    }

    private val someRows = listOf(rowOf(1, 300, GameMode.STANDARD), rowOf(2, 250, GameMode.TRICOLOUR), rowOf(3, 200, GameMode.STANDARD))

    @Test
    fun opensOnTheCombinedTableWithTheSwitchAtTheTop() {
        show(someRows)

        compose.onNodeWithText("Combined").assertIsSelected()
        compose.onNodeWithText("Game Mode").assertExists()
        compose.onNodeWithText("Player1").assertExists()
        // The mode names only exist as cards in the other view.
        compose.onAllNodesWithText("Stud").assertCountEquals(0)
    }

    @Test
    fun aCombinedRowSpeaksItsModeAndDate() {
        show(someRows)

        val spoken = compose.onNodeWithText("Player2").fetchSemanticsNode().config
        val detail = spoken.getOrNull(SemanticsProperties.StateDescription)
        assertTrue(detail.orEmpty(), detail!!.endsWith(", Tricolour"))
    }

    /** The mode's name as `strings.xml` has it: `Res` is internal to :app:shared, so the file is read from disk. */
    private fun modeName(mode: GameMode): String = Regex("""<string name="mode_${mode.id}">([^<]*)</string>""")
        .find(File("../shared/src/commonMain/composeResources/values/strings.xml").readText())!!.groupValues[1]

    @Test
    fun gameModeShowsACardOnlyForModesWithScores() {
        // Quickfire never counts on the combined table, yet gets its card; Stud has no score, so no card.
        show(someRows + rowOf(4, 90, GameMode.QUICKFIRE).copy(onLeaderboard = false))

        compose.onNodeWithText("Game Mode").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Standard").assertExists()
        compose.onNodeWithText("Tricolour").assertExists()
        compose.onNodeWithText(modeName(GameMode.QUICKFIRE)).assertExists()
        compose.onAllNodesWithText(modeName(GameMode.STUD)).assertCountEquals(0)
        // On its own card a row's detail is the date alone - the mode is the card's title.
        val detail = compose.onNodeWithText("Player2").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        assertTrue(detail.orEmpty(), GameMode.entries.none { detail!!.contains(modeName(it)) })
    }

    @Test
    fun aLongPressOnARowArmsItsTooltip() {
        show(someRows)

        compose.onNodeWithText("Player1").performTouchInput { longClick() }
        compose.waitForIdle()

        // The first row of the combined table: its date and mode, in the tooltip.
        compose.onAllNodesWithText("Standard", substring = true).assertCountEquals(1)
    }

    @Test
    fun aCardsPageControlsAreAtTheEndOfItsOwnScroll() {
        show((1..SCORES_PAGE_SIZE + 5).map { rowOf(it.toLong(), it, GameMode.STANDARD) })

        compose.onNodeWithText("Game Mode").performClick()
        compose.waitForIdle()

        // Standard's card is the only one with a list, and its controls aren't in view until it scrolls to its end.
        compose.onAllNodesWithText("Page 1 of 2").assertCountEquals(0)
        compose.onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(SCORES_PAGE_SIZE)
        compose.onNodeWithText("Page 1 of 2").assertExists()

        compose.onNodeWithText("Next").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Page 2 of 2").assertExists()
    }
}
