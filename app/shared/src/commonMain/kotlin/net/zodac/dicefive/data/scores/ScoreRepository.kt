package net.zodac.dicefive.data.scores

import net.zodac.dicefive.game.LeaderboardTotals
import net.zodac.dicefive.game.TieBreakStats
import net.zodac.dicefive.game.nowEpochMillis
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.playerNameKey

/** How many leaderboard rows make up one page. Was 100; halved so a page is a shorter scroll. */
const val SCORES_PAGE_SIZE = 50

/**
 * One human player's part of a finished game, for [ScoreRepository.recordGame]: their score and tie-break stats (see
 * [PlayerState.toTieBreakStats][net.zodac.dicefive.game.toTieBreakStats]), whether they won (null for a solo game) and
 * whether they were player 1.
 */
data class PlayerResult(
    val playerName: String,
    val stats: TieBreakStats,
    val won: Boolean?,
    val isPrimaryPlayer: Boolean,
)

/** The scores part of the cloud save: every row, and when each dismissed Statistics card was dismissed, by name. */
data class CloudScores(val rows: List<ScoreEntry> = emptyList(), val dismissedAt: Map<String, Long> = emptyMap())

/** What the Leaderboard screen reads: the Combined table and each game mode's card, a page at a time. */
interface LeaderboardSource {
    suspend fun page(pageIndex: Int, pageSize: Int = SCORES_PAGE_SIZE): List<ScoreEntry>

    suspend fun totalCount(): Int

    suspend fun pageForMode(gameMode: GameMode, pageIndex: Int, pageSize: Int = SCORES_PAGE_SIZE): List<ScoreEntry>

    suspend fun totalCountForMode(gameMode: GameMode): Int
}

class ScoreRepository(private val scoreDao: ScoreDao) : LeaderboardSource {

    /**
     * Records one finished game's human players ([results]) in a single write: all of them or none. With each, which
     * [gameMode] it was played in, and whether it ran under that mode's own rules ([ScoreEntry.playedUnmodified]); the two
     * decide whether it is on the Leaderboard ([ScoreEntry.onLeaderboard]). A player dismissed from the Statistics
     * screen is shown there again.
     */
    suspend fun recordGame(
        results: List<PlayerResult>,
        gameMode: GameMode = GameMode.STANDARD,
        playedUnmodified: Boolean = true,
        timestampEpochMillis: Long = nowEpochMillis(),
    ) {
        if (results.isEmpty()) return
        scoreDao.insertGame(
            results.map { result ->
                ScoreEntry(
                    playerName = result.playerName,
                    score = result.stats.score,
                    timestampEpochMillis = timestampEpochMillis,
                    won = result.won,
                    isPrimaryPlayer = result.isPrimaryPlayer,
                    fiveOfAKindCount = result.stats.fiveOfAKindCount,
                    zeroedCategoryCount = result.stats.zeroedCategoryCount,
                    upperSectionTotal = result.stats.upperSectionTotal,
                    chanceScore = result.stats.chance,
                    threeOfAKindScore = result.stats.threeOfAKind,
                    fourOfAKindScore = result.stats.fourOfAKind,
                    gameModeId = gameMode.id,
                    playedUnmodified = playedUnmodified,
                )
            },
        )
    }

    override suspend fun page(pageIndex: Int, pageSize: Int): List<ScoreEntry> =
        scoreDao.pagedScores(limit = pageSize, offset = pageIndex * pageSize)

    override suspend fun totalCount(): Int = scoreDao.count()

    /**
     * One page of [gameMode]'s own Leaderboard card, in the same order as [page]: its games played with no modifier
     * on ([ScoreDao.pagedScoresForMode]). A mode that doesn't count on the Combined board
     * ([GameMode.countsOnLeaderboard]) is listed only here; its scores stay off [page], [totalCount] and everything
     * measured against the board (a personal best, the score achievements).
     */
    override suspend fun pageForMode(gameMode: GameMode, pageIndex: Int, pageSize: Int): List<ScoreEntry> =
        scoreDao.pagedScoresForMode(gameMode.id, limit = pageSize, offset = pageIndex * pageSize)

    override suspend fun totalCountForMode(gameMode: GameMode): Int = scoreDao.countForMode(gameMode.id)

    /**
     * Wipes every recorded score, which necessarily empties the Statistics screen too - it's
     * computed from these same rows (see [playerStatistics]) - so nothing is left to dismiss either.
     */
    suspend fun resetLeaderboard() = scoreDao.clearAll()

    /**
     * Hides [playerName] from the Statistics screen without touching their recorded scores - the
     * Leaderboard keeps their full history. Playing another game under the same name un-hides them
     * again, in [recordScore].
     */
    suspend fun dismissPlayerStatistics(playerName: String, atEpochMillis: Long = nowEpochMillis()) =
        scoreDao.dismissPlayer(playerName, atEpochMillis)

    /** Every recorded row and dismissal, for the cloud save. */
    suspend fun cloudScores(): CloudScores = CloudScores(
        rows = scoreDao.allScores(),
        dismissedAt = scoreDao.allDismissals().associate { it.playerName to it.dismissedAtEpochMillis },
    )

    /** Merges [scores] into this device's, dropping everything a reset at [resetAtEpochMillis] cleared - see [ScoreDao.mergeCloud]. */
    suspend fun mergeCloud(resetAtEpochMillis: Long, scores: CloudScores) = scoreDao.mergeCloud(
        resetAtEpochMillis,
        scores.rows,
        scores.dismissedAt.map { (name, at) -> DismissedPlayerStats(name, at) },
    )

    /**
     * [playerName]'s own highest score on the leaderboard, or null if they haven't recorded one.
     * Read *before* a finished game's own rows are inserted, or "beat your best score" would be
     * comparing the new score against itself - see `GameViewModel.finishGame`. Scoped to the name
     * specifically because only player 1 earns "New Personal Best": someone else's high score on
     * the same device must not count as beating *your* best.
     */
    suspend fun bestScoreForPlayer(playerName: String): Int? = scoreDao.bestScoreForPlayer(playerNameKey(playerName))

    /**
     * Every distinct score on the leaderboard. The score-collection achievements are measured
     * against this rather than a stored counter, so they are retroactive and always agree with
     * what the Leaderboard screen actually shows.
     */
    suspend fun distinctScores(): Set<Int> = scoreDao.distinctScores().toSet()

    /** Career points: every point the primary player alone has ever recorded, added up - see
     * [ScoreEntry.isPrimaryPlayer]. Zero when the board is empty. */
    suspend fun primaryPlayerTotalPoints(): Int = scoreDao.primaryPlayerTotalPoints() ?: 0

    /**
     * The primary player's best score in [gameMode] from the games played after [afterEpochMillis] under its own rules,
     * for its online leaderboard - see [ScoreDao.bestUnpostedScore]. Null when there are none.
     */
    suspend fun bestUnpostedScore(gameMode: GameMode, afterEpochMillis: Long): UnpostedScore? =
        scoreDao.bestUnpostedScore(gameMode.id, afterEpochMillis, gameMode.maxPossibleScore)

    /** Every leaderboard-derived figure in one consistent read, for the achievements measured against the board - see [ScoreDao.leaderboardTotals]. */
    suspend fun leaderboardTotals(): LeaderboardTotals = scoreDao.leaderboardTotals()

    /**
     * Per-player aggregate stats for the Statistics screen - see [PlayerStatistics]. One query for
     * every player's games, already in display order and most recent first within each player,
     * folded here: `groupBy` keeps that order, both across players and within each one's games.
     */
    suspend fun playerStatistics(): List<PlayerStatistics> =
        scoreDao.playerGames().groupBy { it.playerKey }.values.map { games ->
            // Names differing only by case are one player; show the spelling they used most recently.
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
                totalScore = games.sumOf { it.score },
                fiveOfAKindCount = games.sumOf { it.fiveOfAKindCount },
                soloGames = games.count { it.won == null },
                modes = GameMode.entries.mapNotNull { mode ->
                    val inMode = games.filter { it.gameModeId == mode.id }
                    if (inMode.isEmpty()) {
                        null
                    } else {
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
