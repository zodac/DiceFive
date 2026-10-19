package net.zodac.dicefive.data.scores

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
import androidx.room.Transaction
import net.zodac.dicefive.game.TieBreak
import net.zodac.dicefive.game.TieBreakCriterion

/**
 * The Leaderboard's row order as an `ORDER BY` body: score, then [TieBreak.leaderboardOrder]'s criteria, each in the
 * direction the live ranking uses. Shared by every query that pages the Leaderboard - see [ScoreDao.pagedScores].
 */
internal val LEADERBOARD_ORDER: String = (
    listOf("score DESC") + TieBreak.leaderboardOrder.map { "${it.criterion.column()} ${if (it.lowerIsBetter) "ASC" else "DESC"}" }
    ).joinToString(", ")

/** The [ScoreEntry] column that stores this criterion. There is none for the tricolour count, which the Leaderboard leaves out. */
private fun TieBreakCriterion.column(): String = when (this) {
    TieBreakCriterion.FIVE_OF_A_KIND_COUNT -> "fiveOfAKindCount"
    TieBreakCriterion.ZEROED_CATEGORIES -> "zeroedCategoryCount"
    TieBreakCriterion.UPPER_SECTION_TOTAL -> "upperSectionTotal"
    TieBreakCriterion.CHANCE -> "chanceScore"
    TieBreakCriterion.THREE_OF_A_KIND -> "threeOfAKindScore"
    TieBreakCriterion.FOUR_OF_A_KIND -> "fourOfAKindScore"
    TieBreakCriterion.TRICOLOUR_SCORED_COUNT -> error("The Leaderboard has no tricolour column")
}

@Dao
interface ScoreDao {

    @Insert
    suspend fun insert(entry: ScoreEntry)

    /**
     * One finished game's rows, in a single transaction: all of them are recorded or none are. Each row's player is
     * shown on the Statistics screen again if they were dismissed from it - see [clearDismissal].
     */
    @Transaction
    suspend fun insertGame(entries: List<ScoreEntry>) {
        for (entry in entries) {
            insert(entry)
            clearDismissal(entry.playerName)
        }
    }

    /**
     * Ties on [ScoreEntry.score] are broken by the same house rule `GameOverScreen` applies within
     * a single game (see [net.zodac.dicefive.game.TieBreak]) - fewest 5x, most zeroed categories,
     * lowest upper section, Chance, 3x, then 4x - the order is built from
     * [TieBreak.leaderboardOrder], as [TieBreak.leaderboardComparator] is, and
     * `AndroidAppContainerTest` checks the column mapping against it. `ScoresScreen` uses the comparator to decide
     * which adjacent rows are a true tie (an equal `=` rank) versus one this ordering has already broken.
     */
    suspend fun pagedScores(limit: Int, offset: Int): List<ScoreEntry> = rawPagedScores(
        pageQuery("onLeaderboard = 1", limit, offset),
    )

    /**
     * [pagedScores] for one game mode's own Leaderboard card - the same ordering, [gameModeId] rows only, and only
     * games played under the mode's own rules ([ScoreEntry.playedUnmodified]), as its online leaderboard takes. For a
     * mode on the Combined table that's the same rows as [ScoreEntry.onLeaderboard]; for one that never counts there,
     * its card is the only place its scores are listed.
     */
    suspend fun pagedScoresForMode(gameModeId: String, limit: Int, offset: Int): List<ScoreEntry> = rawPagedScores(
        pageQuery("playedUnmodified = 1 AND gameModeId = ?", limit, offset, gameModeId),
    )

    /** Runs a [pageQuery]. An `@Query` needs a constant string, so the queries that use [LEADERBOARD_ORDER] are built in code. */
    @RawQuery
    suspend fun rawPagedScores(query: RoomRawQuery): List<ScoreEntry>

    /** How many rows the Leaderboard shows - see [ScoreEntry.onLeaderboard]. */
    @Query("SELECT COUNT(*) FROM scores WHERE onLeaderboard = 1")
    suspend fun count(): Int

    /** How many rows one game mode's Leaderboard card lists - see [pagedScoresForMode]. */
    @Query("SELECT COUNT(*) FROM scores WHERE playedUnmodified = 1 AND gameModeId = :gameModeId")
    suspend fun countForMode(gameModeId: String): Int

    /** One player's best score, by name - null if that name has never recorded one. Used for the
     * "New Personal Best" achievement, which is player 1's own best, not the leaderboard's overall
     * best. Leaderboard rows only - see [ScoreEntry.onLeaderboard]. */
    @Query("SELECT MAX(score) FROM scores WHERE playerName = :playerName COLLATE NOCASE AND onLeaderboard = 1")
    suspend fun bestScoreForPlayer(playerName: String): Int?

    /** Every score that has ever been recorded on the Leaderboard, once each - what the score-collection achievements count. */
    @Query("SELECT DISTINCT score FROM scores WHERE onLeaderboard = 1")
    suspend fun distinctScores(): List<Int>

    /** Every point the primary player alone has ever scored, added up - see
     * [ScoreEntry.isPrimaryPlayer]. Null when nothing has been recorded yet - SUM over no rows. */
    @Query("SELECT SUM(score) FROM scores WHERE isPrimaryPlayer = 1")
    suspend fun primaryPlayerTotalPoints(): Int?

    /** The [ScoreEntry.gameModeId] of every mode the primary player has a recorded game in, on the Leaderboard or not. */
    @Query("SELECT DISTINCT gameModeId FROM scores WHERE isPrimaryPlayer = 1")
    suspend fun primaryPlayerGameModeIds(): List<String>

    /**
     * The primary player's best score in [gameModeId] from the games played after [afterEpochMillis], and when the newest
     * of those was played - what an online leaderboard is sent next. Only games played under the mode's own rules
     * ([ScoreEntry.playedUnmodified]), and none over [maxScore], the most the mode allows. Null when there are none.
     */
    @Query(
        """
        SELECT MAX(score) AS score, MAX(timestampEpochMillis) AS playedEpochMillis
        FROM scores
        WHERE isPrimaryPlayer = 1 AND playedUnmodified = 1 AND gameModeId = :gameModeId
            AND timestampEpochMillis > :afterEpochMillis AND score <= :maxScore
        GROUP BY gameModeId
        """,
    )
    suspend fun bestUnpostedScore(gameModeId: String, afterEpochMillis: Long, maxScore: Int): UnpostedScore?

    /**
     * Every recorded game, grouped by player name alphabetically and most recent first within each
     * player - excluding anyone dismissed from the Statistics screen (see [DismissedPlayerStats]).
     * Their scores are still counted on the Leaderboard; this query backs Statistics only, and counts
     * every game, on the Leaderboard or not (see [ScoreEntry.onLeaderboard]). Names are
     * compared ignoring case, as the New Game screen does, so "bob" and "Bob" are one player.
     */
    @Query(
        """
        SELECT playerName, timestampEpochMillis, won, score, fiveOfAKindCount, gameModeId
        FROM scores
        WHERE playerName COLLATE NOCASE NOT IN (SELECT playerName FROM dismissed_player_stats)
        ORDER BY playerName COLLATE NOCASE ASC, timestampEpochMillis DESC
        """,
    )
    suspend fun playerGames(): List<PlayerGame>

    /** Hides [playerName] from the Statistics screen, as of [atEpochMillis] - see [DismissedPlayerStats]. */
    @Query("INSERT OR REPLACE INTO dismissed_player_stats (playerName, dismissedAtEpochMillis) VALUES (:playerName, :atEpochMillis)")
    suspend fun dismissPlayer(playerName: String, atEpochMillis: Long)

    /** Un-hides [playerName] from the Statistics screen, if they were dismissed. */
    @Query("DELETE FROM dismissed_player_stats WHERE playerName = :playerName COLLATE NOCASE")
    suspend fun clearDismissal(playerName: String)

    /** Wipes every recorded score - the Leaderboard and Statistics screens share this one table. */
    @Query("DELETE FROM scores")
    suspend fun clearAllScores()

    /** Wipes every per-player Statistics dismissal, so `clearAllScores` starts from a clean slate. */
    @Query("DELETE FROM dismissed_player_stats")
    suspend fun clearAllDismissals()

    /** Every recorded row, for the cloud save. */
    @Query("SELECT * FROM scores")
    suspend fun allScores(): List<ScoreEntry>

    /** Every Statistics dismissal, for the cloud save. */
    @Query("SELECT * FROM dismissed_player_stats")
    suspend fun allDismissals(): List<DismissedPlayerStats>

    /** Adds the rows whose [ScoreEntry.uuid] this device doesn't have yet; one it has is left as it is. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(entries: List<ScoreEntry>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDismissals(dismissals: List<DismissedPlayerStats>)

    /** Drops every row from a game played at or before [epochMillis] - a Reset leaderboard made then, on any device. */
    @Query("DELETE FROM scores WHERE timestampEpochMillis <= :epochMillis")
    suspend fun deleteScoresThrough(epochMillis: Long)

    @Query("DELETE FROM dismissed_player_stats WHERE dismissedAtEpochMillis <= :epochMillis")
    suspend fun deleteDismissalsThrough(epochMillis: Long)

    /** Drops each dismissal that a later game under the same name (ignoring case) has undone, wherever it was played. */
    @Query(
        """
        DELETE FROM dismissed_player_stats WHERE EXISTS (
            SELECT 1 FROM scores
            WHERE scores.playerName = dismissed_player_stats.playerName COLLATE NOCASE
                AND scores.timestampEpochMillis > dismissed_player_stats.dismissedAtEpochMillis
        )
        """,
    )
    suspend fun deleteUndoneDismissals()

    /**
     * Merges the cloud save's scores into this device's, in one transaction: drops what a reset at [resetAtEpochMillis]
     * cleared (0: none), adds the [entries] this device lacks, keeps the later of each name's dismissals, then drops any
     * a later game has undone - the rules [net.zodac.dicefive.online.CloudSaveMerge] merges two saves by.
     */
    @Transaction
    suspend fun mergeCloud(resetAtEpochMillis: Long, entries: List<ScoreEntry>, dismissals: List<DismissedPlayerStats>) {
        if (resetAtEpochMillis > 0) {
            deleteScoresThrough(resetAtEpochMillis)
            deleteDismissalsThrough(resetAtEpochMillis)
        }
        insertMissing(entries.filter { it.timestampEpochMillis > resetAtEpochMillis }.map { it.copy(id = 0) })
        val local = allDismissals().associate { it.playerName to it.dismissedAtEpochMillis }
        putDismissals(
            dismissals.filter { it.dismissedAtEpochMillis > resetAtEpochMillis && it.dismissedAtEpochMillis > (local[it.playerName] ?: Long.MIN_VALUE) },
        )
        deleteUndoneDismissals()
    }
}

/** A page of Leaderboard rows matching [where] in [LEADERBOARD_ORDER], with [gameModeId] bound to its one `?` if it has one. */
private fun pageQuery(where: String, limit: Int, offset: Int, gameModeId: String? = null) = RoomRawQuery(
    "SELECT * FROM scores WHERE $where ORDER BY $LEADERBOARD_ORDER LIMIT ? OFFSET ?",
) { statement ->
    var index = 1
    if (gameModeId != null) statement.bindText(index++, gameModeId)
    statement.bindLong(index++, limit.toLong())
    statement.bindLong(index, offset.toLong())
}
