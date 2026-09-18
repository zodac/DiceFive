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
    private var nextId = 1L

    override suspend fun insert(entry: ScoreEntry) {
        entries += entry.copy(id = nextId++)
    }

    override suspend fun pagedScores(limit: Int, offset: Int): List<ScoreEntry> =
        entries.sortedByDescending { it.score }.drop(offset).take(limit)

    override suspend fun count(): Int = entries.size
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
}
