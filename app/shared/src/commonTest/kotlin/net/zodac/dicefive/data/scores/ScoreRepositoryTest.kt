package net.zodac.dicefive.data.scores

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.game.TieBreakStats
import net.zodac.dicefive.game.nowEpochMillis
import net.zodac.dicefive.model.GameMode

/**
 * Hand-written in-memory double for [ScoreDao] - lets [ScoreRepository]'s
 * pagination logic be verified without a Room/SQLite runtime, which would
 * otherwise need Robolectric or an on-device instrumented test (neither
 * available in this sandbox; see .claude/DESIGN.md's Verification section).
 * The DAO's own `@Query` SQL is deliberately simple (ORDER BY/LIMIT/OFFSET).
 */
private class FakeScoreDao : ScoreDao {
    private val entries = mutableListOf<ScoreEntry>()
    private val dismissed = mutableSetOf<String>()
    private var nextId = 1L

    override suspend fun insert(entry: ScoreEntry) {
        entries += entry.copy(id = nextId++)
    }

    // As the real queries do, only Leaderboard rows (ScoreEntry.onLeaderboard) are paged, counted,
    // best or collected; career points and Statistics count every row.
    private val leaderboard get() = entries.filter { it.onLeaderboard }

    override suspend fun pagedScores(limit: Int, offset: Int): List<ScoreEntry> =
        leaderboard.sortedByDescending { it.score }.drop(offset).take(limit)

    private fun forMode(gameModeId: String, includeOffBoard: Boolean) =
        entries.filter { (it.onLeaderboard || includeOffBoard) && it.gameModeId == gameModeId }

    override suspend fun pagedScoresForMode(gameModeId: String, includeOffBoard: Boolean, limit: Int, offset: Int): List<ScoreEntry> =
        forMode(gameModeId, includeOffBoard).sortedByDescending { it.score }.drop(offset).take(limit)

    override suspend fun count(): Int = leaderboard.size

    override suspend fun countForMode(gameModeId: String, includeOffBoard: Boolean): Int = forMode(gameModeId, includeOffBoard).size

    override suspend fun bestScoreForPlayer(playerName: String): Int? =
        leaderboard.filter { it.playerName == playerName }.maxOfOrNull { it.score }

    override suspend fun distinctScores(): List<Int> = leaderboard.map { it.score }.distinct()

    override suspend fun primaryPlayerTotalPoints(): Int? =
        entries.filter { it.isPrimaryPlayer }.takeIf { it.isNotEmpty() }?.sumOf { it.score }

    override suspend fun playerGames(): List<PlayerGame> =
        entries.filter { it.playerName !in dismissed }
            // SQLite's COLLATE NOCASE (for the names used here), then the exact name, then newest first.
            .sortedWith(compareBy<ScoreEntry>({ it.playerName.lowercase() }, { it.playerName }).thenByDescending { it.timestampEpochMillis })
            .map { PlayerGame(it.playerName, it.timestampEpochMillis, it.won, it.score, it.fiveOfAKindCount) }

    override suspend fun dismissPlayer(playerName: String) {
        dismissed += playerName
    }

    override suspend fun clearDismissal(playerName: String) {
        dismissed -= playerName
    }

    override suspend fun clearAllScores() {
        entries.clear()
    }

    override suspend fun clearAllDismissals() {
        dismissed.clear()
    }
}

/** Records a score for a test that doesn't care about its tie-break stats - all zero bar the score. */
private suspend fun ScoreRepository.record(
    playerName: String,
    score: Int,
    won: Boolean? = null,
    isPrimaryPlayer: Boolean = false,
    timestampEpochMillis: Long = nowEpochMillis(),
    fiveOfAKindCount: Int = 0,
    onLeaderboard: Boolean = true,
    gameMode: GameMode = GameMode.STANDARD,
) = recordScore(
    playerName = playerName,
    stats = TieBreakStats(
        score = score,
        fiveOfAKindCount = fiveOfAKindCount,
        zeroedCategoryCount = 0,
        tricolourScoredCount = null,
        upperSectionTotal = 0,
        chance = 0,
        threeOfAKind = 0,
        fourOfAKind = 0,
    ),
    won = won,
    isPrimaryPlayer = isPrimaryPlayer,
    onLeaderboard = onLeaderboard,
    gameMode = gameMode,
    timestampEpochMillis = timestampEpochMillis,
)

class ScoreRepositoryTest {

    @Test
    fun `the Leaderboard - every score recorded - highest first - paged - by mode - with each name's best - until it's reset`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.record("Alice", 150)
        repository.record("Bob", 300)
        repository.record("Carol", 220)
        repository.record("Alice", 120)
        assertEquals(4, repository.totalCount())
        assertEquals(listOf("Bob", "Carol", "Alice", "Alice"), repository.page(pageIndex = 0, pageSize = 100).map { it.playerName })

        // bestScoreForPlayer is scoped to that name - not the whole leaderboard.
        assertEquals(150, repository.bestScoreForPlayer("Alice"))
        assertEquals(300, repository.bestScoreForPlayer("Bob"))
        assertEquals(null, repository.bestScoreForPlayer("Dave"))

        repository.resetLeaderboard()
        assertEquals(0, repository.totalCount())

        // A page respects its size and offset.
        repeat(5) { repository.record("Player$it", it * 10) }
        val firstPage = repository.page(pageIndex = 0, pageSize = 2)
        val secondPage = repository.page(pageIndex = 1, pageSize = 2)
        assertEquals(2, firstPage.size)
        assertEquals(2, secondPage.size)
        assertTrue(firstPage.none { entry -> entry.score in secondPage.map { it.score } })

        // A mode's page and count hold only that mode's scores - and only those on the Leaderboard.
        val modes = ScoreRepository(FakeScoreDao())
        modes.record("Alice", 150, gameMode = GameMode.STANDARD)
        modes.record("Bob", 300, gameMode = GameMode.TRICOLOUR)
        modes.record("Carol", 220, gameMode = GameMode.TRICOLOUR)
        modes.record("Dave", 400, gameMode = GameMode.TRICOLOUR, onLeaderboard = false)
        assertEquals(listOf("Bob", "Carol"), modes.pageForMode(GameMode.TRICOLOUR, pageIndex = 0).map { it.playerName })
        assertEquals(2, modes.totalCountForMode(GameMode.TRICOLOUR))
        assertEquals(listOf("Alice"), modes.pageForMode(GameMode.STANDARD, pageIndex = 0).map { it.playerName })
        assertEquals(0, modes.totalCountForMode(GameMode.STUD))
        // The combined board still has them all, each saying which mode it was.
        assertEquals(listOf(GameMode.TRICOLOUR, GameMode.TRICOLOUR, GameMode.STANDARD), modes.page(0).map { it.gameMode })
    }

    @Test
    fun `Statistics - each name's games - best - totals - average - wins and losses - and win streaks with solo games neutral`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.record("Alice", 150, timestampEpochMillis = 1_000L)
        repository.record("Alice", 300, timestampEpochMillis = 2_000L)
        repository.record("Bob", 220, timestampEpochMillis = 1_500L)
        val stats = repository.playerStatistics()
        assertEquals(listOf("Alice", "Bob"), stats.map { it.playerName })
        val alice = stats.first { it.playerName == "Alice" }
        assertEquals(2, alice.gamesPlayed)
        assertEquals(300, alice.maxScore)
        assertEquals(1_000L, alice.firstPlayedEpochMillis)

        // Totals of score and 5x, solo games counted, and the average rounded to the nearest whole number - a half up.
        val totals = ScoreRepository(FakeScoreDao())
        totals.record("Alice", 100, won = true, fiveOfAKindCount = 1, timestampEpochMillis = 1_000L)
        totals.record("Alice", 201, won = null, fiveOfAKindCount = 2, timestampEpochMillis = 2_000L)
        totals.record("Alice", 100, won = null, timestampEpochMillis = 3_000L)
        val totalled = totals.playerStatistics().single()
        assertEquals(401, totalled.totalScore)
        assertEquals(3, totalled.fiveOfAKindCount)
        assertEquals(2, totalled.soloGames)
        // 401 / 3 = 133.67.
        assertEquals(134, totalled.averageScore)
        val half = ScoreRepository(FakeScoreDao())
        half.record("Alice", 100, timestampEpochMillis = 1_000L)
        half.record("Alice", 101, timestampEpochMillis = 2_000L)
        assertEquals(101, half.playerStatistics().single().averageScore)

        // Wins and losses from the recorded outcomes - and the streaks, oldest to newest: a solo (null) game is neutral,
        // not a break; the best streak is the longest run anywhere in the history, not just the current one.
        suspend fun outcomes(vararg won: Boolean?): PlayerStatistics {
            val history = ScoreRepository(FakeScoreDao())
            won.forEachIndexed { index, outcome -> history.record("Alice", 100, won = outcome, timestampEpochMillis = 1_000L * (index + 1)) }
            return history.playerStatistics().single()
        }
        val mixed = outcomes(true, false, null)
        assertEquals(3, mixed.gamesPlayed)
        assertEquals(1, mixed.gamesWon)
        assertEquals(1, mixed.gamesLost)
        assertEquals(2, outcomes(false, true, true).currentWinStreak)
        assertEquals(2, outcomes(true, null, true).currentWinStreak)
        assertEquals(2, outcomes(true, null, true).bestWinStreak)
        val broken = outcomes(true, true, true, false, true)
        assertEquals(1, broken.currentWinStreak)
        assertEquals(3, broken.bestWinStreak)
    }

    @Test
    fun `dismissing a player hides their Statistics without touching their scores - until they record another`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.record("Alice", 150)
        repository.record("Bob", 300)

        repository.dismissPlayerStatistics("Alice")
        assertEquals(listOf("Bob"), repository.playerStatistics().map { it.playerName })
        assertEquals(2, repository.totalCount())
        assertEquals(150, repository.bestScoreForPlayer("Alice"))

        repository.record("Alice", 200)
        assertEquals(listOf("Alice", "Bob"), repository.playerStatistics().map { it.playerName })
    }

    @Test
    fun `career points sum only the primary player's rows - off the Leaderboard too - and are zero with nothing recorded`() = runTest {
        assertEquals(0, ScoreRepository(FakeScoreDao()).primaryPlayerTotalPoints())

        val repository = ScoreRepository(FakeScoreDao())
        repository.record("Alice", 150, isPrimaryPlayer = true)
        // A second human seat in the same local game - must not count towards Alice's career points.
        repository.record("Bob", 300, isPrimaryPlayer = false)
        repository.record("Alice", 250, isPrimaryPlayer = true)
        assertEquals(400, repository.primaryPlayerTotalPoints())

        // A game off the Leaderboard still counts in Statistics and career points.
        val offBoard = ScoreRepository(FakeScoreDao())
        offBoard.record("Alice", 200, isPrimaryPlayer = true)
        offBoard.record("Alice", 750, isPrimaryPlayer = true, onLeaderboard = false)
        assertEquals(1, offBoard.totalCount())
        assertEquals(listOf(200), offBoard.page(0).map { it.score })
        assertEquals(200, offBoard.bestScoreForPlayer("Alice"))
        assertEquals(setOf(200), offBoard.distinctScores())
        val alice = offBoard.playerStatistics().single()
        assertEquals(2, alice.gamesPlayed)
        assertEquals(750, alice.maxScore)
        assertEquals(950, offBoard.primaryPlayerTotalPoints())
    }
}
