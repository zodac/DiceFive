package net.zodac.dicefive.data.scores

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    override suspend fun pagedScores(limit: Int, offset: Int): List<ScoreEntry> =
        entries.sortedByDescending { it.score }.drop(offset).take(limit)

    override suspend fun count(): Int = entries.size

    override suspend fun bestScoreForPlayer(playerName: String): Int? =
        entries.filter { it.playerName == playerName }.maxOfOrNull { it.score }

    override suspend fun distinctScores(): List<Int> = entries.map { it.score }.distinct()

    override suspend fun totalPoints(): Int? = entries.map { it.score }.sum().takeIf { entries.isNotEmpty() }

    override suspend fun playerSummaries(): List<PlayerScoreSummary> =
        entries.groupBy { it.playerName }
            .filterKeys { it !in dismissed }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { (name, rows) ->
                PlayerScoreSummary(
                    playerName = name,
                    firstPlayedEpochMillis = rows.minOf { it.timestampEpochMillis },
                    gamesPlayed = rows.size,
                    gamesWon = rows.count { it.won == true },
                    gamesLost = rows.count { it.won == false },
                    maxScore = rows.maxOf { it.score },
                )
            }

    override suspend fun outcomesForPlayer(playerName: String): List<Boolean?> =
        entries.filter { it.playerName == playerName }
            .sortedByDescending { it.timestampEpochMillis }
            .map { it.won }

    override suspend fun dismissPlayer(playerName: String) {
        dismissed += playerName
    }

    override suspend fun dismissAllPlayers() {
        dismissed += entries.map { it.playerName }
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

class ScoreRepositoryTest {

    @Test
    fun `recordScore inserts an entry reflected in totalCount`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())

        repository.recordScore("Alice", 250)
        repository.recordScore("Bob", 180)

        assertEquals(2, repository.totalCount())
    }

    @Test
    fun `page returns scores ordered highest first`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.recordScore("Alice", 150)
        repository.recordScore("Bob", 300)
        repository.recordScore("Carol", 220)

        val page = repository.page(pageIndex = 0, pageSize = 100)

        assertEquals(listOf("Bob", "Carol", "Alice"), page.map { it.playerName })
    }

    @Test
    fun `page respects page size and offset`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repeat(5) { repository.recordScore("Player$it", it * 10) }

        val firstPage = repository.page(pageIndex = 0, pageSize = 2)
        val secondPage = repository.page(pageIndex = 1, pageSize = 2)

        assertEquals(2, firstPage.size)
        assertEquals(2, secondPage.size)
        assertTrue(firstPage.none { entry -> entry.score in secondPage.map { it.score } })
    }

    @Test
    fun `playerStatistics groups by player name with games played, max score and first played`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.recordScore("Alice", 150, timestampEpochMillis = 1_000L)
        repository.recordScore("Alice", 300, timestampEpochMillis = 2_000L)
        repository.recordScore("Bob", 220, timestampEpochMillis = 1_500L)

        val stats = repository.playerStatistics()

        assertEquals(listOf("Alice", "Bob"), stats.map { it.playerName })
        val alice = stats.first { it.playerName == "Alice" }
        assertEquals(2, alice.gamesPlayed)
        assertEquals(300, alice.maxScore)
        assertEquals(1_000L, alice.firstPlayedEpochMillis)
    }

    @Test
    fun `playerStatistics counts wins and losses from recorded outcomes`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.recordScore("Alice", 300, won = true, timestampEpochMillis = 1_000L)
        repository.recordScore("Alice", 150, won = false, timestampEpochMillis = 2_000L)
        repository.recordScore("Alice", 200, won = null, timestampEpochMillis = 3_000L)

        val alice = repository.playerStatistics().single { it.playerName == "Alice" }

        assertEquals(3, alice.gamesPlayed)
        assertEquals(1, alice.gamesWon)
        assertEquals(1, alice.gamesLost)
    }

    @Test
    fun `current win streak counts consecutive wins back from the most recent game`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.recordScore("Alice", 100, won = false, timestampEpochMillis = 1_000L)
        repository.recordScore("Alice", 200, won = true, timestampEpochMillis = 2_000L)
        repository.recordScore("Alice", 300, won = true, timestampEpochMillis = 3_000L)

        val alice = repository.playerStatistics().single { it.playerName == "Alice" }

        assertEquals(2, alice.currentWinStreak)
    }

    @Test
    fun `current win streak treats a solo (null outcome) game as neutral, not a break`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.recordScore("Alice", 100, won = true, timestampEpochMillis = 1_000L)
        repository.recordScore("Alice", 200, won = null, timestampEpochMillis = 2_000L)
        repository.recordScore("Alice", 300, won = true, timestampEpochMillis = 3_000L)

        val alice = repository.playerStatistics().single { it.playerName == "Alice" }

        assertEquals(2, alice.currentWinStreak)
    }

    @Test
    fun `best win streak is the longest run of wins anywhere in the history, not just the current one`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        // Oldest to newest: win, win, win, loss, win - a 3-game run that's since been broken.
        repository.recordScore("Alice", 100, won = true, timestampEpochMillis = 1_000L)
        repository.recordScore("Alice", 100, won = true, timestampEpochMillis = 2_000L)
        repository.recordScore("Alice", 100, won = true, timestampEpochMillis = 3_000L)
        repository.recordScore("Alice", 100, won = false, timestampEpochMillis = 4_000L)
        repository.recordScore("Alice", 100, won = true, timestampEpochMillis = 5_000L)

        val alice = repository.playerStatistics().single { it.playerName == "Alice" }

        assertEquals(1, alice.currentWinStreak)
        assertEquals(3, alice.bestWinStreak)
    }

    @Test
    fun `best win streak also ignores solo (null outcome) games`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.recordScore("Alice", 100, won = true, timestampEpochMillis = 1_000L)
        repository.recordScore("Alice", 100, won = null, timestampEpochMillis = 2_000L)
        repository.recordScore("Alice", 100, won = true, timestampEpochMillis = 3_000L)

        val alice = repository.playerStatistics().single { it.playerName == "Alice" }

        assertEquals(2, alice.bestWinStreak)
    }

    @Test
    fun `bestScoreForPlayer is scoped to that name, not the whole leaderboard`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.recordScore("Alice", 150)
        repository.recordScore("Alice", 300)
        repository.recordScore("Bob", 500)

        assertEquals(300, repository.bestScoreForPlayer("Alice"))
        assertEquals(500, repository.bestScoreForPlayer("Bob"))
        assertEquals(null, repository.bestScoreForPlayer("Carol"))
    }

    @Test
    fun `resetLeaderboard removes every recorded score`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.recordScore("Alice", 150)
        repository.recordScore("Bob", 300)

        repository.resetLeaderboard()

        assertEquals(0, repository.totalCount())
    }

    @Test
    fun `resetStatistics hides every player without touching any recorded score`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.recordScore("Alice", 150)
        repository.recordScore("Bob", 300)

        repository.resetStatistics()

        assertEquals(emptyList<String>(), repository.playerStatistics().map { it.playerName })
        assertEquals(2, repository.totalCount())
        assertEquals(150, repository.bestScoreForPlayer("Alice"))
        assertEquals(300, repository.bestScoreForPlayer("Bob"))
    }

    @Test
    fun `recording a new score for a player after resetStatistics un-hides them`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.recordScore("Alice", 150)
        repository.resetStatistics()

        repository.recordScore("Alice", 200)

        assertEquals(listOf("Alice"), repository.playerStatistics().map { it.playerName })
    }

    @Test
    fun `dismissPlayerStatistics hides a player from statistics without touching their scores`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.recordScore("Alice", 150)
        repository.recordScore("Bob", 300)

        repository.dismissPlayerStatistics("Alice")

        assertEquals(listOf("Bob"), repository.playerStatistics().map { it.playerName })
        assertEquals(2, repository.totalCount())
        assertEquals(150, repository.bestScoreForPlayer("Alice"))
    }

    @Test
    fun `recording a new score for a dismissed player un-hides their statistics`() = runTest {
        val repository = ScoreRepository(FakeScoreDao())
        repository.recordScore("Alice", 150)
        repository.dismissPlayerStatistics("Alice")

        repository.recordScore("Alice", 200)

        assertEquals(listOf("Alice"), repository.playerStatistics().map { it.playerName })
    }
}
