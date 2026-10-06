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
    fun `recordScore inserts an entry reflected in totalCount`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())

        repository.record("Alice", 250)
        repository.record("Bob", 180)

        assertEquals(2, repository.totalCount())
    }

    @Test
    fun `page returns scores ordered highest first`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.record("Alice", 150)
        repository.record("Bob", 300)
        repository.record("Carol", 220)

        val page = repository.page(pageIndex = 0, pageSize = 100)

        assertEquals(listOf("Bob", "Carol", "Alice"), page.map { it.playerName })
    }

    @Test
    fun `page respects page size and offset`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repeat(5) { repository.record("Player$it", it * 10) }

        val firstPage = repository.page(pageIndex = 0, pageSize = 2)
        val secondPage = repository.page(pageIndex = 1, pageSize = 2)

        assertEquals(2, firstPage.size)
        assertEquals(2, secondPage.size)
        assertTrue(firstPage.none { entry -> entry.score in secondPage.map { it.score } })
    }

    @Test
    fun `a mode's page and count hold only that mode's scores`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.record("Alice", 150, gameMode = GameMode.STANDARD)
        repository.record("Bob", 300, gameMode = GameMode.TRICOLOUR)
        repository.record("Carol", 220, gameMode = GameMode.TRICOLOUR)
        repository.record("Dave", 400, gameMode = GameMode.TRICOLOUR, onLeaderboard = false)

        assertEquals(listOf("Bob", "Carol"), repository.pageForMode(GameMode.TRICOLOUR, pageIndex = 0).map { it.playerName })
        assertEquals(2, repository.totalCountForMode(GameMode.TRICOLOUR))
        assertEquals(listOf("Alice"), repository.pageForMode(GameMode.STANDARD, pageIndex = 0).map { it.playerName })
        assertEquals(0, repository.totalCountForMode(GameMode.STUD))
        // The combined board still has them all, each saying which mode it was.
        assertEquals(listOf(GameMode.TRICOLOUR, GameMode.TRICOLOUR, GameMode.STANDARD), repository.page(0).map { it.gameMode })
    }

    @Test
    fun `playerStatistics groups by player name with games played - max score and first played`() = runTest {
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
    }

    @Test
    fun `playerStatistics totals scores and 5x and counts solo games`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.record("Alice", 100, won = true, fiveOfAKindCount = 1, timestampEpochMillis = 1_000L)
        repository.record("Alice", 201, won = null, fiveOfAKindCount = 2, timestampEpochMillis = 2_000L)
        repository.record("Alice", 100, won = null, timestampEpochMillis = 3_000L)

        val alice = repository.playerStatistics().single { it.playerName == "Alice" }

        assertEquals(401, alice.totalScore)
        assertEquals(3, alice.fiveOfAKindCount)
        assertEquals(2, alice.soloGames)
        // 401 / 3 = 133.67, rounded to the nearest whole number.
        assertEquals(134, alice.averageScore)
    }

    @Test
    fun `average score rounds a half up`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.record("Alice", 100, timestampEpochMillis = 1_000L)
        repository.record("Alice", 101, timestampEpochMillis = 2_000L)

        assertEquals(101, repository.playerStatistics().single().averageScore)
    }

    @Test
    fun `playerStatistics counts wins and losses from recorded outcomes`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.record("Alice", 300, won = true, timestampEpochMillis = 1_000L)
        repository.record("Alice", 150, won = false, timestampEpochMillis = 2_000L)
        repository.record("Alice", 200, won = null, timestampEpochMillis = 3_000L)

        val alice = repository.playerStatistics().single { it.playerName == "Alice" }

        assertEquals(3, alice.gamesPlayed)
        assertEquals(1, alice.gamesWon)
        assertEquals(1, alice.gamesLost)
    }

    @Test
    fun `current win streak counts consecutive wins back from the most recent game`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.record("Alice", 100, won = false, timestampEpochMillis = 1_000L)
        repository.record("Alice", 200, won = true, timestampEpochMillis = 2_000L)
        repository.record("Alice", 300, won = true, timestampEpochMillis = 3_000L)

        val alice = repository.playerStatistics().single { it.playerName == "Alice" }

        assertEquals(2, alice.currentWinStreak)
    }

    @Test
    fun `current win streak treats a solo - null outcome - game as neutral - not a break`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.record("Alice", 100, won = true, timestampEpochMillis = 1_000L)
        repository.record("Alice", 200, won = null, timestampEpochMillis = 2_000L)
        repository.record("Alice", 300, won = true, timestampEpochMillis = 3_000L)

        val alice = repository.playerStatistics().single { it.playerName == "Alice" }

        assertEquals(2, alice.currentWinStreak)
    }

    @Test
    fun `best win streak is the longest run of wins anywhere in the history - not just the current one`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        // Oldest to newest: win, win, win, loss, win - a 3-game run that's since been broken.
        repository.record("Alice", 100, won = true, timestampEpochMillis = 1_000L)
        repository.record("Alice", 100, won = true, timestampEpochMillis = 2_000L)
        repository.record("Alice", 100, won = true, timestampEpochMillis = 3_000L)
        repository.record("Alice", 100, won = false, timestampEpochMillis = 4_000L)
        repository.record("Alice", 100, won = true, timestampEpochMillis = 5_000L)

        val alice = repository.playerStatistics().single { it.playerName == "Alice" }

        assertEquals(1, alice.currentWinStreak)
        assertEquals(3, alice.bestWinStreak)
    }

    @Test
    fun `best win streak also ignores solo - null outcome - games`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.record("Alice", 100, won = true, timestampEpochMillis = 1_000L)
        repository.record("Alice", 100, won = null, timestampEpochMillis = 2_000L)
        repository.record("Alice", 100, won = true, timestampEpochMillis = 3_000L)

        val alice = repository.playerStatistics().single { it.playerName == "Alice" }

        assertEquals(2, alice.bestWinStreak)
    }

    @Test
    fun `bestScoreForPlayer is scoped to that name - not the whole leaderboard`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.record("Alice", 150)
        repository.record("Alice", 300)
        repository.record("Bob", 500)

        assertEquals(300, repository.bestScoreForPlayer("Alice"))
        assertEquals(500, repository.bestScoreForPlayer("Bob"))
        assertEquals(null, repository.bestScoreForPlayer("Carol"))
    }

    @Test
    fun `resetLeaderboard removes every recorded score`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.record("Alice", 150)
        repository.record("Bob", 300)

        repository.resetLeaderboard()

        assertEquals(0, repository.totalCount())
    }

    @Test
    fun `dismissPlayerStatistics hides a player from statistics without touching their scores`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.record("Alice", 150)
        repository.record("Bob", 300)

        repository.dismissPlayerStatistics("Alice")

        assertEquals(listOf("Bob"), repository.playerStatistics().map { it.playerName })
        assertEquals(2, repository.totalCount())
        assertEquals(150, repository.bestScoreForPlayer("Alice"))
    }

    @Test
    fun `recording a new score for a dismissed player un-hides their statistics`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.record("Alice", 150)
        repository.dismissPlayerStatistics("Alice")

        repository.record("Alice", 200)

        assertEquals(listOf("Alice"), repository.playerStatistics().map { it.playerName })
    }

    @Test
    fun `primaryPlayerTotalPoints only sums rows recorded as the primary player`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.record("Alice", 150, isPrimaryPlayer = true)
        // A second human seat in the same local game - must not count towards Alice's career points.
        repository.record("Bob", 300, isPrimaryPlayer = false)
        repository.record("Alice", 250, isPrimaryPlayer = true)

        assertEquals(400, repository.primaryPlayerTotalPoints())
    }

    @Test
    fun `primaryPlayerTotalPoints is zero - not null-crashing - when nothing has been recorded`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())

        assertEquals(0, repository.primaryPlayerTotalPoints())
    }

    @Test
    fun `a game off the Leaderboard still counts in Statistics and career points`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.record("Alice", 200, isPrimaryPlayer = true)
        repository.record("Alice", 750, isPrimaryPlayer = true, onLeaderboard = false)

        assertEquals(1, repository.totalCount())
        assertEquals(listOf(200), repository.page(0).map { it.score })
        assertEquals(200, repository.bestScoreForPlayer("Alice"))
        assertEquals(setOf(200), repository.distinctScores())

        val alice = repository.playerStatistics().single()
        assertEquals(2, alice.gamesPlayed)
        assertEquals(750, alice.maxScore)
        assertEquals(950, repository.primaryPlayerTotalPoints())
    }
}
