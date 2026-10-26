package net.zodac.dicefive.data.scores

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.model.GameMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every read [ScoreDao] makes while the app is in use - a Leaderboard page or count, a personal best, the figures the
 * achievements are measured against, an online leaderboard's unposted best - is answered from an index: SQLite's plan for
 * it neither reads the whole `scores` table nor sorts it. Statistics ([ScoreDao.playerRecords]) adds up every game by
 * design, so may read a whole index, but never the table and never with a sort. The cloud save ([ScoreDao.allScores])
 * reads every row by design, so isn't held to it.
 */
@RunWith(AndroidJUnit4::class)
class ScoreQueryPlanTest {

    /** Each statement Room runs, with its arguments. */
    private val statements = mutableListOf<Pair<String, List<Any?>>>()

    private val database = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext<Context>())
        .setQueryCallback({ sql, args -> synchronized(statements) { statements += sql to args } }) { it.run() }
        .buildAppDatabase()
    private val dao = database.scoreDao()

    @After
    fun close() = database.close()

    /**
     * Runs [read] and fails if any `SELECT` it made would scan or sort `scores`, naming the plan that would. With
     * [wholeIndex], reading every entry of an index that holds all the columns it needs is allowed.
     */
    private fun assertIndexed(name: String, wholeIndex: Boolean = false, read: suspend ScoreDao.() -> Unit) = runTest {
        dao.insertGame(
            listOf(
                row(GameMode.STANDARD, score = 200, primary = true),
                row(GameMode.STANDARD, score = 150, primary = false),
                row(GameMode.QUICKFIRE, score = 90, primary = true),
            ),
        )
        synchronized(statements) { statements.clear() }
        dao.read()
        // Room's own bookkeeping (its invalidation tracker) runs SELECTs too; only those reading scores are the DAO's.
        val selects = synchronized(statements) { statements.filter { (sql, _) -> sql.trimStart().startsWith("SELECT", ignoreCase = true) && SCORES in sql } }
        assertTrue("$name made no SELECT", selects.isNotEmpty())
        for ((sql, args) in selects) {
            val plan = database.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN $sql", args.toTypedArray()).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("detail"))) }
            }
            // A subquery SQLite runs as a co-routine is read back row by row ("SCAN <its name>"), but holds its own rows,
            // not the table's.
            val coroutines = plan.mapNotNull { it.removePrefix("CO-ROUTINE ").takeIf { name -> name != it } }.toSet()
            val scans = plan.filter {
                it.startsWith("SCAN") && it.removePrefix("SCAN ") !in coroutines &&
                    !(wholeIndex && it.startsWith("SCAN scores USING COVERING INDEX"))
            }
            if (scans.isNotEmpty() || plan.any { "TEMP B-TREE" in it }) fail("$name reads or sorts the whole table:\n$sql\n${plan.joinToString("\n")}")
        }
    }

    private companion object {
        val SCORES = Regex("""\bFROM `?scores`?\b""", RegexOption.IGNORE_CASE)
    }

    private fun row(mode: GameMode, score: Int, primary: Boolean) = ScoreEntry(
        playerName = "Alice",
        score = score,
        timestampEpochMillis = 10L,
        won = null,
        isPrimaryPlayer = primary,
        fiveOfAKindCount = 0,
        zeroedCategoryCount = 0,
        upperSectionTotal = 0,
        chanceScore = 0,
        threeOfAKindScore = 0,
        fourOfAKindScore = 0,
        gameModeId = mode.id,
    )

    @Test
    fun `a Combined Leaderboard page is read in order from an index`() = assertIndexed("pagedScores") { pagedScores(limit = 50, offset = 50) }

    @Test
    fun `a mode's Leaderboard page is read in order from an index`() =
        assertIndexed("pagedScoresForMode") { pagedScoresForMode(GameMode.STANDARD.id, limit = 50, offset = 50) }

    @Test
    fun `the Leaderboard's counts come from an index`() = assertIndexed("count, countForMode") {
        count()
        countForMode(GameMode.QUICKFIRE.id)
    }

    @Test
    fun `a player's best comes from an index`() = assertIndexed("bestScoreForPlayer") { bestScoreForPlayer("alice") }

    @Test
    fun `the achievements' leaderboard figures come from indices`() = assertIndexed("leaderboardTotals") { leaderboardTotals() }

    @Test
    fun `an online leaderboard's unposted best comes from an index`() =
        assertIndexed("bestUnpostedScore") { bestUnpostedScore(GameMode.STANDARD.id, afterEpochMillis = 0L, maxScore = 1_575) }

    @Test
    fun `Statistics adds up each player's games from an index - and reads their latest name and outcomes from another`() =
        assertIndexed("playerRecords", wholeIndex = true) { playerRecords() }

    @Test
    fun `Statistics makes three reads however many players there are`() = runTest {
        dao.insertGame((1..20).map { row(GameMode.STANDARD, score = 100 + it, primary = it == 1).copy(playerName = "Player $it", won = it == 1) })
        synchronized(statements) { statements.clear() }
        assertEquals(20, dao.playerRecords().size)
        assertEquals(3, synchronized(statements) { statements.count { (sql, _) -> sql.trimStart().startsWith("SELECT", ignoreCase = true) && SCORES in sql } })
    }
}
