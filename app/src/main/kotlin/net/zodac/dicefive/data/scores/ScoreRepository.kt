package net.zodac.dicefive.data.scores

import net.zodac.dicefive.game.LeaderboardTotals

/** How many leaderboard rows make up one page. Was 100; halved so a page is a shorter scroll. */
const val SCORES_PAGE_SIZE = 50

class ScoreRepository(private val scoreDao: ScoreDao) {

    suspend fun recordScore(
        playerName: String,
        score: Int,
        won: Boolean? = null,
        isPrimaryPlayer: Boolean = false,
        fiveOfAKindCount: Int? = null,
        zeroedCategoryCount: Int? = null,
        upperSectionTotal: Int? = null,
        chanceScore: Int? = null,
        threeOfAKindScore: Int? = null,
        fourOfAKindScore: Int? = null,
        timestampEpochMillis: Long = System.currentTimeMillis(),
    ) {
        scoreDao.insert(
            ScoreEntry(
                playerName = playerName,
                score = score,
                timestampEpochMillis = timestampEpochMillis,
                won = won,
                isPrimaryPlayer = isPrimaryPlayer,
                fiveOfAKindCount = fiveOfAKindCount,
                zeroedCategoryCount = zeroedCategoryCount,
                upperSectionTotal = upperSectionTotal,
                chanceScore = chanceScore,
                threeOfAKindScore = threeOfAKindScore,
                fourOfAKindScore = fourOfAKindScore,
            ),
        )
        // A dismissed player who plays again clearly cares about their stats once more.
        scoreDao.clearDismissal(playerName)
    }

    suspend fun page(pageIndex: Int, pageSize: Int = SCORES_PAGE_SIZE): List<ScoreEntry> =
        scoreDao.pagedScores(limit = pageSize, offset = pageIndex * pageSize)

    suspend fun totalCount(): Int = scoreDao.count()

    /**
     * Wipes every recorded score, which necessarily empties the Statistics screen too - it's
     * computed from these same rows (see [playerStatistics]) - so nothing is left to dismiss either.
     */
    suspend fun resetLeaderboard() {
        scoreDao.clearAllScores()
        scoreDao.clearAllDismissals()
    }

    /**
     * Hides [playerName] from the Statistics screen without touching their recorded scores - the
     * Leaderboard keeps their full history. Playing another game under the same name un-hides them
     * again, in [recordScore].
     */
    suspend fun dismissPlayerStatistics(playerName: String) = scoreDao.dismissPlayer(playerName)

    /**
     * [playerName]'s own highest score on the leaderboard, or null if they haven't recorded one.
     * Read *before* a finished game's own rows are inserted, or "beat your best score" would be
     * comparing the new score against itself - see `GameViewModel.finishGame`. Scoped to the name
     * specifically because only player 1 earns "New Personal Best": someone else's high score on
     * the same device must not count as beating *your* best.
     */
    suspend fun bestScoreForPlayer(playerName: String): Int? = scoreDao.bestScoreForPlayer(playerName)

    /**
     * Every distinct score on the leaderboard. The score-collection achievements are measured
     * against this rather than a stored counter, so they are retroactive and always agree with
     * what the Leaderboard screen actually shows.
     */
    suspend fun distinctScores(): Set<Int> = scoreDao.distinctScores().toSet()

    /** Career points: every point the primary player alone has ever recorded, added up - see
     * [ScoreEntry.isPrimaryPlayer]. Zero when the board is empty. */
    suspend fun primaryPlayerTotalPoints(): Int = scoreDao.primaryPlayerTotalPoints() ?: 0

    /** Both leaderboard-derived figures in one go, for the achievements measured against the board. */
    suspend fun leaderboardTotals(): LeaderboardTotals =
        LeaderboardTotals(distinctScores = distinctScores(), totalPoints = primaryPlayerTotalPoints())

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
