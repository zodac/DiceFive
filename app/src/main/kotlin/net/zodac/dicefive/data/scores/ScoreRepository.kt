package net.zodac.dicefive.data.scores

import net.zodac.dicefive.game.LeaderboardTotals

/** How many leaderboard rows make up one page. Was 100; halved so a page is a shorter scroll. */
const val SCORES_PAGE_SIZE = 50

class ScoreRepository(private val scoreDao: ScoreDao) {

    suspend fun recordScore(
        playerName: String,
        score: Int,
        won: Boolean? = null,
        timestampEpochMillis: Long = System.currentTimeMillis(),
    ) {
        scoreDao.insert(ScoreEntry(playerName = playerName, score = score, timestampEpochMillis = timestampEpochMillis, won = won))
    }

    suspend fun page(pageIndex: Int, pageSize: Int = SCORES_PAGE_SIZE): List<ScoreEntry> =
        scoreDao.pagedScores(limit = pageSize, offset = pageIndex * pageSize)

    suspend fun totalCount(): Int = scoreDao.count()

    /**
     * Wipes every recorded score. The Leaderboard and Statistics screens are both just different
     * views over this same table, so there's no way to clear one without the other.
     */
    suspend fun clearAll() = scoreDao.clearAll()

    /**
     * The highest score on the leaderboard, or null if there isn't one yet. Read *before* a
     * finished game's own rows are inserted, or "beat your best score" would be comparing the new
     * score against itself - see `GameViewModel.finishGame`.
     */
    suspend fun bestScore(): Int? = scoreDao.bestScore()

    /**
     * Every distinct score on the leaderboard. The score-collection achievements are measured
     * against this rather than a stored counter, so they are retroactive and always agree with
     * what the Leaderboard screen actually shows.
     */
    suspend fun distinctScores(): Set<Int> = scoreDao.distinctScores().toSet()

    /** Career points: every point ever recorded, added up. Zero when the board is empty. */
    suspend fun totalPoints(): Int = scoreDao.totalPoints() ?: 0

    /** Both leaderboard-derived figures in one go, for the achievements measured against the board. */
    suspend fun leaderboardTotals(): LeaderboardTotals =
        LeaderboardTotals(distinctScores = distinctScores(), totalPoints = totalPoints())

    /** Per-player aggregate stats for the Statistics screen - see [PlayerStatistics]. */
    suspend fun playerStatistics(): List<PlayerStatistics> =
        scoreDao.playerSummaries().map { summary ->
            val streaks = winStreaks(scoreDao.outcomesForPlayer(summary.playerName))
            PlayerStatistics(
                playerName = summary.playerName,
                firstPlayedEpochMillis = summary.firstPlayedEpochMillis,
                gamesPlayed = summary.gamesPlayed,
                gamesWon = summary.gamesWon,
                gamesLost = summary.gamesLost,
                currentWinStreak = streaks.current,
                bestWinStreak = streaks.best,
                maxScore = summary.maxScore,
            )
        }

    private data class WinStreaks(val current: Int, val best: Int)

    /**
     * Walks [outcomesMostRecentFirst] once to find both streaks. A solo game (recorded with a
     * null outcome - nobody to beat) neither extends nor breaks a run, the same rule the
     * device-wide WIN_STREAK achievement counter already follows; a loss ends one.
     *
     * The *current* streak is only "locked in" by the first loss encountered - after that, later
     * (i.e. older) wins are still counted toward [WinStreaks.best] but can no longer be part of
     * the current one.
     */
    private fun winStreaks(outcomesMostRecentFirst: List<Boolean?>): WinStreaks {
        var running = 0
        var best = 0
        var current = 0
        var currentLocked = false
        for (won in outcomesMostRecentFirst) {
            when (won) {
                true -> {
                    running++
                    if (running > best) best = running
                    if (!currentLocked) current = running
                }
                false -> {
                    running = 0
                    currentLocked = true
                }
                null -> Unit
            }
        }
        return WinStreaks(current = current, best = best)
    }
}
