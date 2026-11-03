package net.zodac.dicefive.ui

import androidx.room.RoomRawQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import net.zodac.dicefive.ui.scores.LeaderboardChoice
import net.zodac.dicefive.ui.scores.ScoresViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/** Just the reads the Leaderboard makes, over rows held in memory. */
private class ListScoreDao(private val rows: List<ScoreEntry>) : ScoreDao {
    var modeReads = 0

    /** How long a read of a board's page at each offset takes - none unless set. */
    var pageDelays: Map<Int, Long> = emptyMap()

    private fun ordered(modeId: String?) =
        rows.filter { it.playedUnmodified && (modeId == null || it.gameModeId == modeId) }.sortedByDescending { it.score }

    override suspend fun insert(entry: ScoreEntry) = Unit
    override suspend fun rawPagedScores(query: RoomRawQuery): List<ScoreEntry> = error("Only the real DAO runs the SQL")
    override suspend fun pagedScores(limit: Int, offset: Int): List<ScoreEntry> = ordered(null).drop(offset).take(limit)
    override suspend fun pagedScoresForMode(gameModeId: String, limit: Int, offset: Int): List<ScoreEntry> {
        modeReads++
        pageDelays[offset]?.let { delay(it) }
        return ordered(gameModeId).drop(offset).take(limit)
    }
    override suspend fun count() = ordered(null).size
    override suspend fun countForMode(gameModeId: String) = ordered(gameModeId).size
    override suspend fun bestScoreForPlayer(playerKey: String): Int? = null
    override suspend fun distinctScores(gameModeId: String): List<Int> = emptyList()
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

    private val standard = LeaderboardChoice.Mode(GameMode.STANDARD)
    private fun mode(mode: GameMode) = LeaderboardChoice.Mode(mode)
    private fun ScoresViewModel.board(choice: LeaderboardChoice) = uiState.value.boards.getValue(choice)

    @Test
    fun `it opens on Standard's board, a board is read when it is first picked and kept, and each board pages on its own`() = runTest(testDispatcher) {
        val dao = ListScoreDao(listOf(entry(1, 200, GameMode.STANDARD)))
        val viewModel = ScoresViewModel(ScoreRepository(dao))
        advanceUntilIdle()
        assertEquals(standard, viewModel.uiState.value.choice)
        assertEquals(setOf<LeaderboardChoice>(standard), viewModel.uiState.value.boards.keys)
        assertEquals(1, dao.modeReads)
        viewModel.selectChoice(mode(GameMode.TRICOLOUR))
        advanceUntilIdle()
        assertEquals(mode(GameMode.TRICOLOUR), viewModel.uiState.value.choice)
        assertEquals(2, dao.modeReads)
        // Switching back and forth doesn't read a board again.
        viewModel.selectChoice(standard)
        viewModel.selectChoice(mode(GameMode.TRICOLOUR))
        advanceUntilIdle()
        assertEquals(2, dao.modeReads)

        val tricolour = (1..SCORES_PAGE_SIZE + 5).map { entry(it.toLong(), it, GameMode.TRICOLOUR) }
        val paging = ScoresViewModel(ScoreRepository(ListScoreDao(tricolour + entry(1000, 50, GameMode.STANDARD))))
        advanceUntilIdle()
        paging.selectChoice(mode(GameMode.TRICOLOUR))
        advanceUntilIdle()
        val before = paging.board(mode(GameMode.TRICOLOUR))
        assertEquals(2, before.totalPages)
        assertEquals(SCORES_PAGE_SIZE, before.entries.size)
        assertFalse(before.hasPreviousPage)
        paging.nextPage()
        advanceUntilIdle()
        val after = paging.board(mode(GameMode.TRICOLOUR))
        assertEquals(1, after.pageIndex)
        assertEquals(5, after.entries.size)
        assertTrue(after.hasPreviousPage)
        // Standard's board didn't move.
        assertEquals(0, paging.board(standard).pageIndex)
        paging.previousPage()
        advanceUntilIdle()
        assertEquals(0, paging.board(mode(GameMode.TRICOLOUR)).pageIndex)
    }

    @Test
    fun `Combined ranks every mode's games with no modifier on in one table`() = runTest(testDispatcher) {
        val rows = listOf(entry(1, 200, GameMode.STANDARD), entry(2, 900, GameMode.STUD), entry(3, 300, GameMode.STANDARD).copy(playedUnmodified = false))
        val viewModel = ScoresViewModel(ScoreRepository(ListScoreDao(rows)))
        advanceUntilIdle()
        viewModel.selectChoice(LeaderboardChoice.Combined)
        advanceUntilIdle()
        assertEquals(listOf(900, 200), viewModel.board(LeaderboardChoice.Combined).entries.map { it.score })
        assertEquals(2, viewModel.board(LeaderboardChoice.Combined).totalCount)
    }

    @Test
    fun `every mode has its own board, and a game with modifiers on is on none`() = runTest(testDispatcher) {
        val quickfire = entry(1, 90, GameMode.QUICKFIRE)
        val modifiers = entry(2, 300, GameMode.STANDARD).copy(playedUnmodified = false)
        val quickfireModifiers = entry(4, 120, GameMode.QUICKFIRE).copy(playedUnmodified = false)
        val viewModel = ScoresViewModel(ScoreRepository(ListScoreDao(listOf(quickfire, modifiers, entry(3, 200, GameMode.STANDARD), quickfireModifiers))))
        advanceUntilIdle()
        for (mode in LEADERBOARD_MODES) {
            viewModel.selectChoice(mode(mode))
            advanceUntilIdle()
        }
        assertEquals(listOf(1L), viewModel.board(mode(GameMode.QUICKFIRE)).entries.map { it.timestampEpochMillis })
        assertEquals(listOf(3L), viewModel.board(standard).entries.map { it.timestampEpochMillis })
        assertEquals(1, viewModel.board(standard).totalCount)
        assertEquals(0, viewModel.board(mode(GameMode.STUD)).totalCount)
    }

    @Test
    fun `the board last picked is opened first and each pick is handed on to be kept`() = runTest(testDispatcher) {
        val saved = MutableStateFlow<LeaderboardChoice?>(null)
        val kept = mutableListOf<LeaderboardChoice>()
        val viewModel = ScoresViewModel(ScoreRepository(ListScoreDao(listOf(entry(1, 200, GameMode.TRICOLOUR)))), saved) { kept += it }
        advanceUntilIdle()
        // Nothing until the saved pick has loaded.
        assertFalse(viewModel.uiState.value.isLoaded)
        saved.value = mode(GameMode.TRICOLOUR)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoaded)
        assertEquals(mode(GameMode.TRICOLOUR), viewModel.uiState.value.choice)
        // Restoring it isn't a pick.
        assertEquals(emptyList(), kept)
        viewModel.selectChoice(standard)
        advanceUntilIdle()
        assertEquals(listOf<LeaderboardChoice>(standard), kept)
        assertEquals(standard, viewModel.uiState.value.choice)
    }

    @Test
    fun `a stored board names itself by its mode's id or combined - and nothing stored or a mode no longer known is Standard's`() {
        for (choice in LeaderboardChoice.all) assertEquals(choice, LeaderboardChoice.parse(choice.stored))
        assertEquals(LEADERBOARD_MODES.map(::mode) + LeaderboardChoice.Combined, LeaderboardChoice.all)
        assertEquals(standard, LeaderboardChoice.parse(null))
        assertEquals(standard, LeaderboardChoice.parse("retired_mode"))
        assertEquals(LeaderboardChoice.Combined, LeaderboardChoice.parse("combined"))
        assertEquals(GameMode.TRICOLOUR.id, mode(GameMode.TRICOLOUR).stored)
    }

    @Test
    fun `a page asked for later is never overwritten by a slower one asked for before it`() = runTest(testDispatcher) {
        val dao = ListScoreDao((1..SCORES_PAGE_SIZE * 3).map { entry(it.toLong(), it, GameMode.STANDARD) })
        val viewModel = ScoresViewModel(ScoreRepository(dao))
        advanceUntilIdle()
        viewModel.nextPage()
        advanceUntilIdle()
        assertEquals(1, viewModel.board(standard).pageIndex)

        // Back to page 1, slowly - then on to page 3 before it arrives: page 3 is what stays.
        dao.pageDelays = mapOf(0 to 1_000L)
        viewModel.previousPage()
        viewModel.nextPage()
        advanceUntilIdle()
        assertEquals(2, viewModel.board(standard).pageIndex)
        assertEquals(SCORES_PAGE_SIZE, viewModel.board(standard).entries.size)
    }
}
