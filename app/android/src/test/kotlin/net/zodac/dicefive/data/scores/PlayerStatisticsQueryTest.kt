package net.zodac.dicefive.data.scores

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.model.GameMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Statistics as SQLite adds it up ([ScoreDao.playerRecords]) gives exactly what walking every recorded game one by one
 * gives, under Robolectric's real SQLite: over a long, varied history of solo games, wins, losses, ties, every mode, a
 * mode since removed, modifiers, names differing only by case in more than one alphabet, and a dismissed player.
 */
@RunWith(AndroidJUnit4::class)
class PlayerStatisticsQueryTest {

    private val database = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext<Context>()).buildAppDatabase()
    private val dao = database.scoreDao()

    @After
    fun close() = database.close()

    private val names = listOf("You", "you", "Bob", "Ángel", "ÁNGEL", "ángel", "Zoë", "Priya", "Dismissed")
    private val modeIds = GameMode.entries.map { it.id } + "retired_mode"

    /** [games] seeded games of one to four humans each, a minute or more apart. */
    private suspend fun record(games: Int) {
        val random = Random(7)
        var at = 1_790_000_000_000L
        repeat(games) {
            at += random.nextLong(60_000, 3_600_000)
            val players = names.shuffled(random).take(random.nextInt(1, 5)).distinctBy { it.lowercase() }
            // Scores close together, so ties at the top come up.
            val scores = players.map { random.nextInt(150, 160) }
            val top = scores.max()
            val modeId = modeIds[random.nextInt(modeIds.size)]
            val unmodified = random.nextInt(4) != 0
            dao.insertGame(
                players.mapIndexed { index, name ->
                    ScoreEntry(
                        playerName = name,
                        score = scores[index],
                        timestampEpochMillis = at,
                        won = if (players.size == 1) null else scores[index] == top,
                        isPrimaryPlayer = index == 0,
                        fiveOfAKindCount = random.nextInt(3),
                        zeroedCategoryCount = 0,
                        upperSectionTotal = 0,
                        chanceScore = 0,
                        threeOfAKindScore = 0,
                        fourOfAKindScore = 0,
                        gameModeId = modeId,
                        playedUnmodified = unmodified,
                    )
                },
            )
        }
        dao.dismissPlayer("Dismissed", at + 1)
    }

    /** Statistics worked out by walking every game, most recent first, as they were before SQLite added them up. */
    private suspend fun walkedStatistics(): List<PlayerStatistics> {
        val dismissed = dao.allDismissals().map { it.playerKey }.toSet()
        return dao.allScores().filter { it.playerKey !in dismissed }
            .sortedWith(compareBy<ScoreEntry> { it.playerKey }.thenByDescending { it.timestampEpochMillis })
            .groupBy { it.playerKey }.values.map { games ->
                var running = 0
                var best = 0
                var current = 0
                var locked = false
                for (won in games.mapNotNull { it.won }) {
                    if (won) {
                        running++
                        best = maxOf(best, running)
                        if (!locked) current = running
                    } else {
                        running = 0
                        locked = true
                    }
                }
                PlayerStatistics(
                    playerName = games.first().playerName,
                    firstPlayedEpochMillis = games.minOf { it.timestampEpochMillis },
                    gamesPlayed = games.size,
                    gamesWon = games.count { it.won == true },
                    gamesLost = games.count { it.won == false },
                    currentWinStreak = current,
                    bestWinStreak = best,
                    maxScore = games.maxOf { it.score },
                    totalScore = games.sumOf { it.score },
                    fiveOfAKindCount = games.sumOf { it.fiveOfAKindCount },
                    soloGames = games.count { it.won == null },
                    modes = GameMode.entries.mapNotNull { mode ->
                        games.filter { it.gameModeId == mode.id }.takeIf { it.isNotEmpty() }?.let { inMode ->
                            ModeStatistics(
                                gameMode = mode,
                                gamesPlayed = inMode.size,
                                gamesWon = inMode.count { it.won == true },
                                gamesLost = inMode.count { it.won == false },
                                soloGames = inMode.count { it.won == null },
                                maxScore = inMode.maxOf { it.score },
                            )
                        }
                    },
                )
            }
    }

    @Test
    fun `SQLite's sums give the same Statistics as walking every game`() = runTest {
        record(games = 600)
        val expected = walkedStatistics()
        // The history is varied enough to test something: one card per player whatever the casing, the dismissed one
        // left out, current and broken streaks, solo games, and games in a mode since removed.
        assertEquals(listOf("bob", "priya", "you", "zoë", "ángel"), expected.map { it.playerName.lowercase() })
        assertTrue(expected.any { it.currentWinStreak > 0 } && expected.any { it.bestWinStreak > it.currentWinStreak && it.bestWinStreak > 1 })
        assertTrue(expected.all { it.soloGames > 0 && it.gamesLost > 0 })
        assertTrue(expected.all { player -> player.modes.sumOf { it.gamesPlayed } < player.gamesPlayed })
        assertEquals(expected, ScoreRepository(dao).playerStatistics())
    }
}
