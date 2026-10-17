package net.zodac.dicefive.device

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.data.scores.AppDatabase
import net.zodac.dicefive.data.scores.CloudScores
import net.zodac.dicefive.data.scores.PlayerResult
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.scores.UnpostedScore
import net.zodac.dicefive.data.scores.buildAppDatabase
import net.zodac.dicefive.data.scores.recordScore
import net.zodac.dicefive.game.TieBreak
import net.zodac.dicefive.game.TieBreakStats
import net.zodac.dicefive.model.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real Android storage - Room's multiplatform build on the framework's SQLite, and the
 * multiplatform DataStore on its files - opened exactly as the app opens it, under Robolectric (which
 * runs real SQLite). The repositories' own logic is covered in :app:shared with fakes; this is what's
 * left: that the platform wiring actually opens, writes and reads back, and that the DAO's SQL runs.
 */
@RunWith(AndroidJUnit4::class)
class AndroidAppContainerTest {

    private val container = AndroidAppContainer.get(ApplicationProvider.getApplicationContext())

    @Before
    fun clearScores() = runTest {
        container.scoreRepository.resetLeaderboard()
    }

    @Test
    fun `the real storage opens - the perfect-play table loads - scores and settings are written and read back - and the DAO's SQL runs`() = runTest {
        // Read through Compose resources from the APK's assets, as on a device - null would mean Hard quietly falling back
        // to estimating in Standard.
        assertNotNull(container.standardPerfectPlayTable())

        val scores = container.scoreRepository
        // A recorded score is read back from the database.
        scores.recordScore("Tester", stats(score = 250), won = true, isPrimaryPlayer = true)
        assertEquals(1, scores.totalCount())
        assertEquals(250, scores.page(0).single().score)
        assertEquals(250, scores.primaryPlayerTotalPoints())
        assertEquals(1, scores.playerStatistics().single().gamesWon)

        // Equal scores are ordered by the tie-break - fewer 5x ranks higher.
        scores.resetLeaderboard()
        scores.recordScore("More", stats(score = 200, fiveOfAKindCount = 2), won = null, isPrimaryPlayer = false)
        scores.recordScore("Fewer", stats(score = 200, fiveOfAKindCount = 0), won = null, isPrimaryPlayer = false)
        assertEquals(listOf("Fewer", "More"), scores.page(0).map { it.playerName })

        // Statistics come from one query - grouped by player in name order - skipping a dismissed player.
        scores.resetLeaderboard()
        scores.recordScore("bob", stats(score = 120), won = false, isPrimaryPlayer = false)
        scores.recordScore("Alice", stats(score = 150), won = true, isPrimaryPlayer = true)
        scores.recordScore("Bob", stats(score = 90), won = true, isPrimaryPlayer = false)
        scores.recordScore("Alice", stats(score = 210), won = true, isPrimaryPlayer = true)
        scores.recordScore("Carol", stats(score = 80), won = null, isPrimaryPlayer = false)
        scores.dismissPlayerStatistics("Carol")
        val statistics = scores.playerStatistics()
        // Case-insensitive name order, and "Bob" and "bob" are one player, as on the New Game screen.
        assertEquals(listOf("alice", "bob"), statistics.map { it.playerName.lowercase() })
        val alice = statistics.first()
        assertEquals(2, alice.gamesPlayed)
        assertEquals(2, alice.gamesWon)
        assertEquals(2, alice.bestWinStreak)
        assertEquals(210, alice.maxScore)
        assertEquals(2, statistics.last().gamesPlayed)
        assertEquals(1, statistics.last().gamesLost)

        // Settings are written to and read back from their preferences file.
        container.settingsRepository.setPlayerName(slot = 1, name = "Tester")
        assertEquals("Tester", container.settingsRepository.playerNameFor(1).first())
    }

    @Test
    fun `the SQL leaderboard order matches TieBreak's leaderboard comparator on every criterion`() = runTest {
        val scores = container.scoreRepository
        // Every combination of a low and a high value in each of the seven criteria, so each one decides some pair.
        val all = (0 until 128).map { bits ->
            fun pick(bit: Int) = (bits shr bit) and 1
            TieBreakStats(
                score = 100 + pick(0),
                fiveOfAKindCount = pick(1),
                zeroedCategoryCount = pick(2),
                tricolourScoredCount = null,
                upperSectionTotal = pick(3),
                chance = pick(4),
                threeOfAKind = pick(5),
                fourOfAKind = pick(6),
            )
        }
        // Stored out of order, so the order read back is the query's.
        for (stats in all.reversed()) {
            scores.recordScore("P", stats, won = null, isPrimaryPlayer = false)
        }

        val fromSql = scores.page(0, pageSize = all.size).map {
            TieBreakStats(it.score, it.fiveOfAKindCount, it.zeroedCategoryCount, null, it.upperSectionTotal, it.chanceScore, it.threeOfAKindScore, it.fourOfAKindScore)
        }
        assertEquals(all.sortedWith(TieBreak.leaderboardComparator), fromSql)
    }

    @Test
    fun `a game's players are recorded in one write - each dismissed one shown again`() = runTest {
        val scores = container.scoreRepository
        scores.recordScore("Alice", stats(score = 100), won = null, isPrimaryPlayer = true)
        scores.recordScore("Bob", stats(score = 90), won = null, isPrimaryPlayer = false)
        scores.dismissPlayerStatistics("Alice")
        scores.dismissPlayerStatistics("Bob")

        scores.recordGame(
            listOf(
                PlayerResult("Alice", stats(score = 200), won = true, isPrimaryPlayer = true),
                PlayerResult("Bob", stats(score = 150), won = false, isPrimaryPlayer = false),
            ),
        )

        assertEquals(listOf(200, 150, 100, 90), scores.page(0).map { it.score })
        assertEquals(listOf("Alice" to 2, "Bob" to 2), scores.playerStatistics().map { it.playerName to it.gamesPlayed })
    }

    @Test
    fun `a game whose rows can't all be written records none of them`() = runTest {
        val database = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext<Context>()).buildAppDatabase()
        try {
            val dao = database.scoreDao()
            // The second row reuses the first's id, so it can't be inserted.
            val row = ScoreEntry(
                id = 7,
                playerName = "Alice",
                score = 200,
                timestampEpochMillis = 0L,
                won = null,
                isPrimaryPlayer = true,
                fiveOfAKindCount = 0,
                zeroedCategoryCount = 0,
                upperSectionTotal = 0,
                chanceScore = 0,
                threeOfAKindScore = 0,
                fourOfAKindScore = 0,
            )
            val failure = runCatching { dao.insertGame(listOf(row, row.copy(playerName = "Bob"))) }.exceptionOrNull()
            assertTrue("$failure", failure is SQLiteConstraintException)
            assertEquals(0, dao.count())
        } finally {
            database.close()
        }
    }

    @Test
    fun `an online leaderboard is sent player 1's best unmodified score after a time - and a mode's card lists only unmodified games`() = runTest {
        val database = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext<Context>()).buildAppDatabase()
        try {
            val dao = database.scoreDao()
            fun row(score: Int, at: Long, primary: Boolean = true, unmodified: Boolean = true, mode: GameMode = GameMode.QUICKFIRE) = ScoreEntry(
                playerName = "Alice",
                score = score,
                timestampEpochMillis = at,
                won = null,
                isPrimaryPlayer = primary,
                fiveOfAKindCount = 0,
                zeroedCategoryCount = 0,
                upperSectionTotal = 0,
                chanceScore = 0,
                threeOfAKindScore = 0,
                fourOfAKindScore = 0,
                onLeaderboard = false,
                gameModeId = mode.id,
                playedUnmodified = unmodified,
            )
            dao.insertGame(
                listOf(
                    row(100, at = 10),
                    row(150, at = 20),
                    row(120, at = 30),
                    row(205, at = 40, primary = false),
                    row(206, at = 50, unmodified = false),
                    row(GameMode.QUICKFIRE.maxPossibleScore + 1, at = 60),
                    row(1_000, at = 70, mode = GameMode.STANDARD),
                ),
            )
            val scores = ScoreRepository(dao)
            assertEquals(UnpostedScore(150, 30), scores.bestUnpostedScore(GameMode.QUICKFIRE, Long.MIN_VALUE))
            assertEquals(UnpostedScore(120, 30), scores.bestUnpostedScore(GameMode.QUICKFIRE, 20))
            assertEquals(null, scores.bestUnpostedScore(GameMode.QUICKFIRE, 30))
            assertEquals(null, scores.bestUnpostedScore(GameMode.D20, Long.MIN_VALUE))

            // A mode's Leaderboard card lists the same games: every player's, but none played with a modifier on.
            assertEquals(listOf(211, 205, 150, 120, 100), scores.pageForMode(GameMode.QUICKFIRE, pageIndex = 0).map { it.score })
            assertEquals(5, scores.totalCountForMode(GameMode.QUICKFIRE))
        } finally {
            database.close()
        }
    }

    @Test
    fun `the cloud save's scores merge in by uuid - a reset drops older ones - and a later game undoes a dismissal`() = runTest {
        val database = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext<Context>()).buildAppDatabase()
        try {
            val dao = database.scoreDao()
            val scores = ScoreRepository(dao)
            scores.recordScore("Alice", stats(score = 100), won = null, isPrimaryPlayer = true, timestampEpochMillis = 10)
            scores.recordScore("Alice", stats(score = 120), won = null, isPrimaryPlayer = true, timestampEpochMillis = 30)
            scores.dismissPlayerStatistics("Carol", atEpochMillis = 40)
            scores.dismissPlayerStatistics("Dave", atEpochMillis = 40)
            val local = scores.cloudScores()
            assertTrue(local.rows.all { it.id == 0L })

            val tablet = listOf(
                local.rows.first { it.score == 120 },
                local.rows.first().copy(uuid = "tablet-old", score = 90, timestampEpochMillis = 15),
                local.rows.first().copy(uuid = "tablet-new", score = 300, timestampEpochMillis = 50, playerName = "carol"),
            )
            scores.mergeCloud(resetAtEpochMillis = 20, CloudScores(rows = tablet, dismissedAt = mapOf("Bob" to 60L, "Dave" to 35L)))
            // The game at 10 and the tablet's at 15 were reset; the shared one at 30 isn't doubled.
            assertEquals(listOf(300, 120), scores.page(0).map { it.score })
            // Carol played after her dismissal; Dave's later dismissal here is kept over the tablet's.
            assertEquals(mapOf("Bob" to 60L, "Dave" to 40L), scores.cloudScores().dismissedAt)
        } finally {
            database.close()
        }
    }

    private fun stats(score: Int, fiveOfAKindCount: Int = 0) = TieBreakStats(
        score = score,
        fiveOfAKindCount = fiveOfAKindCount,
        zeroedCategoryCount = 0,
        tricolourScoredCount = null,
        upperSectionTotal = 0,
        chance = 0,
        threeOfAKind = 0,
        fourOfAKind = 0,
    )
}
