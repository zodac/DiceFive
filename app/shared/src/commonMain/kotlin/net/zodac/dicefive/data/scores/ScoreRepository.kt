package net.zodac.dicefive.data.scores

import net.zodac.dicefive.game.LeaderboardTotals
import net.zodac.dicefive.game.TieBreakStats
import net.zodac.dicefive.game.nowEpochMillis

/** How many leaderboard rows make up one page. Was 100; halved so a page is a shorter scroll. */
const val SCORES_PAGE_SIZE = 50

class ScoreRepository(private val scoreDao: ScoreDao) {

    /**
     * Records one human player's finished game: their score and tie-break stats (see
     * [PlayerState.toTieBreakStats][net.zodac.dicefive.game.toTieBreakStats]), whether they won
     * (null for a solo game), and whether they were player 1.
     */
    suspend fun recordScore(
        playerName: String,
        stats: TieBreakStats,
        won: Boolean?,
        isPrimaryPlayer: Boolean,
        timestampEpochMillis: Long = nowEpochMillis(),
    ) {
        scoreDao.insert(
            ScoreEntry(
                playerName = playerName,
                score = stats.score,
                timestampEpochMillis = timestampEpochMillis,
                won = won,
                isPrimaryPlayer = isPrimaryPlayer,
                fiveOfAKindCount = stats.fiveOfAKindCount,
                zeroedCategoryCount = stats.zeroedCategoryCount,
                upperSectionTotal = stats.upperSectionTotal,
                chanceScore = stats.chance,
                threeOfAKindScore = stats.threeOfAKind,
                fourOfAKindScore = stats.fourOfAKind,
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

    /**
     * Per-player aggregate stats for the Statistics screen - see [PlayerStatistics]. One query for
     * every player's games, already in display order and most recent first within each player,
     * folded here: `groupBy` keeps that order, both across players and within each one's games.
     */
    suspend fun playerStatistics(): List<PlayerStatistics> =
        scoreDao.playerGames().groupBy { it.playerName.lowercase() }.values.map { variants ->
            // Names differing only by case are one player; show the spelling they used most recently.
            val games = variants.sortedByDescending { it.timestampEpochMillis }
            val playerName = games.first().playerName
            val streaks = winStreaks(games.map { it.won })
            PlayerStatistics(
                playerName = playerName,
                firstPlayedEpochMillis = games.minOf { it.timestampEpochMillis },
                gamesPlayed = games.size,
                gamesWon = games.count { it.won == true },
                gamesLost = games.count { it.won == false },
                currentWinStreak = streaks.current,
                bestWinStreak = streaks.best,
                maxScore = games.maxOf { it.score },
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
