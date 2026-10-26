package net.zodac.dicefive.ui

import androidx.room.RoomRawQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.data.scores.DismissedPlayerStats
import net.zodac.dicefive.data.scores.PlayerLatestName
import net.zodac.dicefive.data.scores.PlayerModeTotals
import net.zodac.dicefive.data.scores.PlayerOutcome
import net.zodac.dicefive.data.scores.SCORES_PAGE_SIZE
import net.zodac.dicefive.data.scores.ScoreDao
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.scores.UnpostedScore
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.ui.scores.LEADERBOARD_MODES
import net.zodac.dicefive.ui.scores.LeaderboardView
import net.zodac.dicefive.ui.scores.ScoresViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/** Just the reads the Leaderboard makes, over rows held in memory. */
private class ListScoreDao(private val rows: List<ScoreEntry>) : ScoreDao {
    var modeReads = 0

    /** How long a read of the Combined table's page at each offset takes - none unless set. */
    var pageDelays: Map<Int, Long> = emptyMap()

    private fun ordered(modeId: String?) =
        rows.filter { if (modeId == null) it.onLeaderboard else it.playedUnmodified && it.gameModeId == modeId }.sortedByDescending { it.score }

    override suspend fun insert(entry: ScoreEntry) = Unit
    override suspend fun rawPagedScores(query: RoomRawQuery): List<ScoreEntry> = error("Only the real DAO runs the SQL")
    override suspend fun pagedScores(limit: Int, offset: Int): List<ScoreEntry> {
        pageDelays[offset]?.let { delay(it) }
        return ordered(null).drop(offset).take(limit)
    }
    override suspend fun pagedScoresForMode(gameModeId: String, limit: Int, offset: Int): List<ScoreEntry> {
        modeReads++
        return ordered(gameModeId).drop(offset).take(limit)
    }
    override suspend fun count() = ordered(null).size
    override suspend fun countForMode(gameModeId: String) = ordered(gameModeId).size
    override suspend fun bestScoreForPlayer(playerKey: String): Int? = null
    override suspend fun distinctScores(): List<Int> = emptyList()
    override suspend fun primaryPlayerTotalPoints(): Int? = null
    override suspend fun primaryPlayerGameModeIds(): List<String> = emptyList()
    override suspend fun bestUnpostedScore(gameModeId: String, afterEpochMillis: Long, maxScore: Int): UnpostedScore? = null
    override suspend fun playerModeTotals(): List<PlayerModeTotals> = emptyList()
    override suspend fun latestPlayerNames(): List<PlayerLatestName> = emptyList()
    override suspend fun playerOutcomes(): List<PlayerOutcome> = emptyList()
    override suspend fun clearDismissal(playerKey: String) = Unit
    override suspend fun clearAllScores() = Unit
    override suspend fun clearAllDismissals() = Unit
    override suspend fun allScores(): List<ScoreEntry> = emptyList()
    override suspend fun allDismissals(): List<DismissedPlayerStats> = emptyList()
    override suspend fun insertMissing(entries: List<ScoreEntry>) = Unit
    override suspend fun putDismissals(dismissals: List<DismissedPlayerStats>) = Unit
    override suspend fun deleteScoresThrough(epochMillis: Long) = Unit
    override suspend fun deleteDismissalsThrough(epochMillis: Long) = Unit
    override suspend fun deleteUndoneDismissals() = Unit
}

private fun entry(id: Long, score: Int, mode: GameMode) = ScoreEntry(
    playerName = "P$id", score = score, timestampEpochMillis = id, won = null, isPrimaryPlayer = false,
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
        // All at once, so the view appears whole rather than card by card.
        assertTrue(viewModel.uiState.value.modeBoardsLoaded)
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
        assertEquals(0, paging.uiState.value.combined.pageIndex)
        paging.previousModePage(GameMode.TRICOLOUR)
        advanceUntilIdle()
        assertEquals(0, paging.uiState.value.modeBoards.getValue(GameMode.TRICOLOUR).pageIndex)

        // A mode that stays off the combined board still has a card.
        val quickfire = entry(1, 90, GameMode.QUICKFIRE)
        val modifiers = entry(2, 300, GameMode.STANDARD).copy(playedUnmodified = false)
        val quickfireModifiers = entry(4, 120, GameMode.QUICKFIRE).copy(playedUnmodified = false)
        val offBoard = ScoresViewModel(ScoreRepository(ListScoreDao(listOf(quickfire, modifiers, entry(3, 200, GameMode.STANDARD), quickfireModifiers))))
        offBoard.selectView(LeaderboardView.GAME_MODE)
        advanceUntilIdle()
        val state = offBoard.uiState.value
        // Not on the combined table, nor counted in it...
        assertEquals(listOf(3L), state.combined.entries.map { it.timestampEpochMillis })
        assertEquals(1, state.combined.totalCount)
        // ...but listed on its own card; a game with modifiers on is on no card, whichever its mode.
        assertEquals(listOf(1L), state.modeBoards.getValue(GameMode.QUICKFIRE).entries.map { it.timestampEpochMillis })
        assertEquals(listOf(3L), state.modeBoards.getValue(GameMode.STANDARD).entries.map { it.timestampEpochMillis })
        assertEquals(0, state.modeBoards.getValue(GameMode.STUD).totalCount)
        assertTrue(state.modeBoardsLoaded)
    }

    @Test
    fun `a page asked for later is never overwritten by a slower one asked for before it`() = runTest(testDispatcher) {
        val dao = ListScoreDao((1..SCORES_PAGE_SIZE * 3).map { entry(it.toLong(), it, GameMode.STANDARD) })
        val viewModel = ScoresViewModel(ScoreRepository(dao))
        advanceUntilIdle()
        viewModel.nextPage()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.combined.pageIndex)

        // Back to page 1, slowly - then on to page 3 before it arrives: page 3 is what stays.
        dao.pageDelays = mapOf(0 to 1_000L)
        viewModel.previousPage()
        viewModel.nextPage()
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.combined.pageIndex)
        assertEquals(SCORES_PAGE_SIZE, viewModel.uiState.value.combined.entries.size)
    }
}
