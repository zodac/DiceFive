package net.zodac.dicefive.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.data.scores.SCORES_PAGE_SIZE
import net.zodac.dicefive.data.scores.ScoreDao
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.scores.PlayerGame
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.ui.scores.LEADERBOARD_MODES
import net.zodac.dicefive.ui.scores.LeaderboardView
import net.zodac.dicefive.ui.scores.ScoresViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/** Just the reads the Leaderboard makes, over rows held in memory. */
private class ListScoreDao(private val rows: List<ScoreEntry>) : ScoreDao {
    var modeReads = 0

    private fun ordered(modeId: String?, includeOffBoard: Boolean = false) =
        rows.filter { (it.onLeaderboard || includeOffBoard) && (modeId == null || it.gameModeId == modeId) }.sortedByDescending { it.score }

    override suspend fun insert(entry: ScoreEntry) = Unit
    override suspend fun pagedScores(limit: Int, offset: Int) = ordered(null).drop(offset).take(limit)
    override suspend fun pagedScoresForMode(gameModeId: String, includeOffBoard: Boolean, limit: Int, offset: Int): List<ScoreEntry> {
        modeReads++
        return ordered(gameModeId, includeOffBoard).drop(offset).take(limit)
    }
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

private fun entry(id: Long, score: Int, mode: GameMode) = ScoreEntry(
    id = id, playerName = "P$id", score = score, timestampEpochMillis = id, won = null, isPrimaryPlayer = false,
    fiveOfAKindCount = 0, zeroedCategoryCount = 0, upperSectionTotal = 0, chanceScore = 0,
    threeOfAKindScore = 0, fourOfAKindScore = 0, gameModeId = mode.id,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ScoresViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the game mode cards are read once that view is chosen - each paging on its own - with a card for a mode off the combined board`() = runTest(testDispatcher) {
        val dao = ListScoreDao(listOf(entry(1, 200, GameMode.STANDARD)))
        val viewModel = ScoresViewModel(ScoreRepository(dao))
        advanceUntilIdle()
        assertEquals(LeaderboardView.COMBINED, viewModel.uiState.value.view)
        assertEquals(0, dao.modeReads)
        viewModel.selectView(LeaderboardView.GAME_MODE)
        advanceUntilIdle()
        assertEquals(LeaderboardView.GAME_MODE, viewModel.uiState.value.view)
        assertEquals(LEADERBOARD_MODES.toSet(), viewModel.uiState.value.modeBoards.keys)
        // Switching back and forth doesn't read them again.
        viewModel.selectView(LeaderboardView.COMBINED)
        viewModel.selectView(LeaderboardView.GAME_MODE)
        advanceUntilIdle()
        assertEquals(LEADERBOARD_MODES.size, dao.modeReads)

        // Every card pages on its own.
        val tricolour = (1..SCORES_PAGE_SIZE + 5).map { entry(it.toLong(), it, GameMode.TRICOLOUR) }
        val paging = ScoresViewModel(ScoreRepository(ListScoreDao(tricolour + entry(1000, 50, GameMode.STANDARD))))
        paging.selectView(LeaderboardView.GAME_MODE)
        advanceUntilIdle()
        val before = paging.uiState.value.modeBoards.getValue(GameMode.TRICOLOUR)
        assertEquals(2, before.totalPages)
        assertEquals(SCORES_PAGE_SIZE, before.entries.size)
        assertFalse(before.hasPreviousPage)
        paging.nextModePage(GameMode.TRICOLOUR)
        advanceUntilIdle()
        val after = paging.uiState.value.modeBoards.getValue(GameMode.TRICOLOUR)
        assertEquals(1, after.pageIndex)
        assertEquals(5, after.entries.size)
        assertTrue(after.hasPreviousPage)
        // Standard's card didn't move, nor did the combined table.
        assertEquals(0, paging.uiState.value.modeBoards.getValue(GameMode.STANDARD).pageIndex)
        assertEquals(0, paging.uiState.value.pageIndex)
        paging.previousModePage(GameMode.TRICOLOUR)
        advanceUntilIdle()
        assertEquals(0, paging.uiState.value.modeBoards.getValue(GameMode.TRICOLOUR).pageIndex)

        // A mode that stays off the combined board still has a card.
        val quickfire = entry(1, 90, GameMode.QUICKFIRE).copy(onLeaderboard = false)
        val modifiers = entry(2, 300, GameMode.STANDARD).copy(onLeaderboard = false)
        val offBoard = ScoresViewModel(ScoreRepository(ListScoreDao(listOf(quickfire, modifiers, entry(3, 200, GameMode.STANDARD)))))
        offBoard.selectView(LeaderboardView.GAME_MODE)
        advanceUntilIdle()
        val state = offBoard.uiState.value
        // Not on the combined table, nor counted in it...
        assertEquals(listOf(3L), state.entries.map { it.id })
        assertEquals(1, state.totalCount)
        // ...but listed on its own card; a Standard game with modifiers (off-board in a mode that counts) is not.
        assertEquals(listOf(1L), state.modeBoards.getValue(GameMode.QUICKFIRE).entries.map { it.id })
        assertEquals(listOf(3L), state.modeBoards.getValue(GameMode.STANDARD).entries.map { it.id })
        assertEquals(0, state.modeBoards.getValue(GameMode.STUD).totalCount)
        assertTrue(state.modeBoardsLoaded)
    }
}
