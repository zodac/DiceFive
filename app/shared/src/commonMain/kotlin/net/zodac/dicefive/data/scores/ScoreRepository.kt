package net.zodac.dicefive.data.scores

import net.zodac.dicefive.game.LeaderboardTotals
import net.zodac.dicefive.game.TieBreakStats
import net.zodac.dicefive.game.nowEpochMillis
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.playerNameKey

/** How many leaderboard rows make up one page. Each page is read whole, so a read takes longer the more rows it finds. */
const val SCORES_PAGE_SIZE = 1_000

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

/** What the Leaderboard screen reads: the Combined board and each game mode's board, a page at a time. */
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
     * One page of [gameMode]'s own Leaderboard board, in the same order as [page]: its games played with no modifier
     * on ([ScoreDao.pagedScoresForMode]). [page] has every mode's.
     */
    override suspend fun pageForMode(gameMode: GameMode, pageIndex: Int, pageSize: Int): List<ScoreEntry> =
        scoreDao.pagedScoresForMode(gameMode.id, limit = pageSize, offset = pageIndex * pageSize)

    override suspend fun totalCountForMode(gameMode: GameMode): Int = scoreDao.countForMode(gameMode.id)

    /**
     * Opens the database with a read, so it is migrated to this build's schema now: throws whatever the opening does (a
     * migration missing or failing, a schema this build doesn't know).
     */
    suspend fun open() {
        scoreDao.count()
    }

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

    /** Every distinct score on [gameMode]'s board: what a game in progress marks a new score against. */
    suspend fun distinctScores(gameMode: GameMode): Set<Int> = scoreDao.distinctScores(gameMode.id).toSet()

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
     * Per-player aggregate stats for the Statistics screen - see [PlayerStatistics]. SQLite adds up each player's games
     * per mode, and the win streaks are walked with them ([ScoreDao.playerRecords]); here they are added across modes.
     * Players come in [ScoreEntry.playerKey] order; names differing only by case are one player, shown by the spelling
     * of their latest game.
     */
    suspend fun playerStatistics(): List<PlayerStatistics> = scoreDao.playerRecords().map { record ->
        val modes = record.modes
        PlayerStatistics(
            playerName = record.playerName,
            firstPlayedEpochMillis = modes.minOf { it.firstPlayedEpochMillis },
            gamesPlayed = modes.sumOf { it.gamesPlayed },
            gamesWon = modes.sumOf { it.gamesWon },
            gamesLost = modes.sumOf { it.gamesLost },
            currentWinStreak = record.streaks.current,
            bestWinStreak = record.streaks.best,
            maxScore = modes.maxOf { it.maxScore },
            totalScore = modes.sumOf { it.totalScore },
            fiveOfAKindCount = modes.sumOf { it.fiveOfAKindCount },
            soloGames = modes.sumOf { it.soloGames },
            // In mode order; a mode this build doesn't know still counts above, but has no row of its own.
            modes = GameMode.entries.mapNotNull { mode ->
                modes.firstOrNull { it.gameModeId == mode.id }?.let { inMode ->
                    val modeStreaks = record.modeStreaks[mode.id] ?: WinStreaks()
                    ModeStatistics(
                        gameMode = mode,
                        gamesPlayed = inMode.gamesPlayed,
                        gamesWon = inMode.gamesWon,
                        gamesLost = inMode.gamesLost,
                        soloGames = inMode.soloGames,
                        maxScore = inMode.maxScore,
                        totalScore = inMode.totalScore,
                        fiveOfAKindCount = inMode.fiveOfAKindCount,
                        firstPlayedEpochMillis = inMode.firstPlayedEpochMillis,
                        currentWinStreak = modeStreaks.current,
                        bestWinStreak = modeStreaks.best,
                    )
                }
            },
        )
    }
}
