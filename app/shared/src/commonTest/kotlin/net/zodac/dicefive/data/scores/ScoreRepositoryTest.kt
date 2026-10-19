package net.zodac.dicefive.data.scores

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.game.TieBreakStats
import net.zodac.dicefive.game.nowEpochMillis
import net.zodac.dicefive.model.GameMode

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
    playedUnmodified: Boolean = true,
) = recordGame(
    listOf(
        PlayerResult(
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
        ),
    ),
    onLeaderboard = onLeaderboard,
    gameMode = gameMode,
    timestampEpochMillis = timestampEpochMillis,
    playedUnmodified = playedUnmodified,
)

private fun tieBreakStats(score: Int) = TieBreakStats(
    score = score,
    fiveOfAKindCount = 0,
    zeroedCategoryCount = 0,
    tricolourScoredCount = null,
    upperSectionTotal = 0,
    chance = 0,
    threeOfAKind = 0,
    fourOfAKind = 0,
)

class ScoreRepositoryTest {

    @Test
    fun `the Leaderboard - every score recorded - highest first - paged - by mode - with each name's best - until it's reset`() = runTest {
        val repository = ScoreRepository(InMemoryScoreDao())
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

        // A mode's page and count hold only that mode's scores - and only those played with no modifier on.
        val modes = ScoreRepository(InMemoryScoreDao())
        modes.record("Alice", 150, gameMode = GameMode.STANDARD)
        modes.record("Bob", 300, gameMode = GameMode.TRICOLOUR)
        modes.record("Carol", 220, gameMode = GameMode.TRICOLOUR)
        modes.record("Dave", 400, gameMode = GameMode.TRICOLOUR, onLeaderboard = false, playedUnmodified = false)
        // The same for a mode off the Combined board, whose every game is off it: its card lists only the unmodified ones.
        modes.record("Erin", 120, gameMode = GameMode.QUICKFIRE, onLeaderboard = false)
        modes.record("Finn", 180, gameMode = GameMode.QUICKFIRE, onLeaderboard = false, playedUnmodified = false)
        assertEquals(listOf("Erin"), modes.pageForMode(GameMode.QUICKFIRE, pageIndex = 0).map { it.playerName })
        assertEquals(1, modes.totalCountForMode(GameMode.QUICKFIRE))
        assertEquals(listOf("Bob", "Carol"), modes.pageForMode(GameMode.TRICOLOUR, pageIndex = 0).map { it.playerName })
        assertEquals(2, modes.totalCountForMode(GameMode.TRICOLOUR))
        assertEquals(listOf("Alice"), modes.pageForMode(GameMode.STANDARD, pageIndex = 0).map { it.playerName })
        assertEquals(0, modes.totalCountForMode(GameMode.STUD))
        // The combined board still has them all, each saying which mode it was.
        assertEquals(listOf(GameMode.TRICOLOUR, GameMode.TRICOLOUR, GameMode.STANDARD), modes.page(0).map { it.gameMode })
    }

    @Test
    fun `Statistics - each mode's games - wins - losses - solo games and best - in mode order - none for a mode never played`() = runTest {
        val repository = ScoreRepository(InMemoryScoreDao())
        repository.record("Alice", 120, won = true, gameMode = GameMode.TRICOLOUR, timestampEpochMillis = 1_000L)
        repository.record("Alice", 100, won = null, gameMode = GameMode.STANDARD, timestampEpochMillis = 2_000L)
        repository.record("Alice", 180, won = false, gameMode = GameMode.TRICOLOUR, timestampEpochMillis = 3_000L)
        repository.record("Alice", 90, won = null, gameMode = GameMode.TRICOLOUR, timestampEpochMillis = 4_000L)
        val alice = repository.playerStatistics().single()
        assertEquals(
            listOf(
                ModeStatistics(GameMode.STANDARD, gamesPlayed = 1, gamesWon = 0, gamesLost = 0, soloGames = 1, maxScore = 100),
                ModeStatistics(GameMode.TRICOLOUR, gamesPlayed = 3, gamesWon = 1, gamesLost = 1, soloGames = 1, maxScore = 180),
            ),
            alice.modes,
        )
        assertEquals(alice.gamesPlayed, alice.modes.sumOf { it.gamesPlayed })
        // Solo games aren't in a win rate; with nothing but solo games there's none.
        assertEquals(listOf(null, 50), alice.modes.map { it.winRatePercent })
    }

    @Test
    fun `a win rate is the games against someone that were won - a whole percentage rounded half up`() {
        assertEquals(null, winRatePercent(won = 0, lost = 0))
        assertEquals(0, winRatePercent(won = 0, lost = 3))
        assertEquals(100, winRatePercent(won = 2, lost = 0))
        // 1 / 3 = 33.3%, 2 / 3 = 66.7%, 1 / 8 = 12.5%.
        assertEquals(33, winRatePercent(won = 1, lost = 2))
        assertEquals(67, winRatePercent(won = 2, lost = 1))
        assertEquals(13, winRatePercent(won = 1, lost = 7))
        // A player's: 97 won and 81 lost is 54.49%, their 36 solo games left out.
        val player = PlayerStatistics(
            playerName = "Ann",
            firstPlayedEpochMillis = 0L,
            gamesPlayed = 214,
            gamesWon = 97,
            gamesLost = 81,
            currentWinStreak = 0,
            bestWinStreak = 0,
            maxScore = 0,
            totalScore = 0,
            fiveOfAKindCount = 0,
            soloGames = 36,
        )
        assertEquals(54, player.winRatePercent)
    }

    @Test
    fun `Statistics - each name's games - best - totals - average - wins and losses - and win streaks with solo games neutral`() = runTest {
        val repository = ScoreRepository(InMemoryScoreDao())
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
        val totals = ScoreRepository(InMemoryScoreDao())
        totals.record("Alice", 100, won = true, fiveOfAKindCount = 1, timestampEpochMillis = 1_000L)
        totals.record("Alice", 201, won = null, fiveOfAKindCount = 2, timestampEpochMillis = 2_000L)
        totals.record("Alice", 100, won = null, timestampEpochMillis = 3_000L)
        val totalled = totals.playerStatistics().single()
        assertEquals(401, totalled.totalScore)
        assertEquals(3, totalled.fiveOfAKindCount)
        assertEquals(2, totalled.soloGames)
        // 401 / 3 = 133.67.
        assertEquals(134, totalled.averageScore)
        val half = ScoreRepository(InMemoryScoreDao())
        half.record("Alice", 100, timestampEpochMillis = 1_000L)
        half.record("Alice", 101, timestampEpochMillis = 2_000L)
        assertEquals(101, half.playerStatistics().single().averageScore)

        // Wins and losses from the recorded outcomes - and the streaks, oldest to newest: a solo (null) game is neutral,
        // not a break; the best streak is the longest run anywhere in the history, not just the current one.
        suspend fun outcomes(vararg won: Boolean?): PlayerStatistics {
            val history = ScoreRepository(InMemoryScoreDao())
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
    fun `a game records every human player's row - each with the game's mode - time - and whether it counts on the board`() = runTest {
        val dao = InMemoryScoreDao()
        val repository = ScoreRepository(dao)
        repository.recordGame(
            listOf(
                PlayerResult("Alice", tieBreakStats(score = 200), won = true, isPrimaryPlayer = true),
                PlayerResult("Bob", tieBreakStats(score = 150), won = false, isPrimaryPlayer = false),
            ),
            onLeaderboard = false,
            gameMode = GameMode.THIRD_WIND,
            playedUnmodified = false,
            timestampEpochMillis = 1_000L,
        )
        val rows = dao.rows()
        assertEquals(listOf("Alice" to 200, "Bob" to 150), rows.map { it.playerName to it.score })
        assertEquals(listOf(true, false), rows.map { it.won })
        assertEquals(listOf(true, false), rows.map { it.isPrimaryPlayer })
        assertTrue(rows.all { it.timestampEpochMillis == 1_000L && !it.onLeaderboard && !it.playedUnmodified })
        // Off the Combined board, as the game's mode says, and off its own card, as a modifier was on.
        assertEquals(0, repository.totalCount())
        assertEquals(0, repository.totalCountForMode(GameMode.THIRD_WIND))

        // A game with no human players records nothing.
        repository.recordGame(emptyList())
        assertEquals(2, dao.rows().size)
    }

    @Test
    fun `dismissing a player hides their Statistics without touching their scores - until they record another`() = runTest {
        val repository = ScoreRepository(InMemoryScoreDao())
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
        assertEquals(0, ScoreRepository(InMemoryScoreDao()).primaryPlayerTotalPoints())

        val repository = ScoreRepository(InMemoryScoreDao())
        repository.record("Alice", 150, isPrimaryPlayer = true)
        // A second human seat in the same local game - must not count towards Alice's career points.
        repository.record("Bob", 300, isPrimaryPlayer = false)
        repository.record("Alice", 250, isPrimaryPlayer = true)
        assertEquals(400, repository.primaryPlayerTotalPoints())

        // A game off the Leaderboard still counts in Statistics and career points.
        val offBoard = ScoreRepository(InMemoryScoreDao())
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
