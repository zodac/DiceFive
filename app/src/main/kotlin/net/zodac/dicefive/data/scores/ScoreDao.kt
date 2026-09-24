package net.zodac.dicefive.data.scores

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ScoreDao {

    @Insert
    suspend fun insert(entry: ScoreEntry)

    @Query("SELECT * FROM scores ORDER BY score DESC LIMIT :limit OFFSET :offset")
    suspend fun pagedScores(limit: Int, offset: Int): List<ScoreEntry>

    @Query("SELECT COUNT(*) FROM scores")
    suspend fun count(): Int

    /** One player's best score, by name - null if that name has never recorded one. Used for the
     * "New Personal Best" achievement, which is player 1's own best, not the leaderboard's overall
     * best. */
    @Query("SELECT MAX(score) FROM scores WHERE playerName = :playerName")
    suspend fun bestScoreForPlayer(playerName: String): Int?

    /** Every score that has ever been recorded, once each - what the score-collection achievements count. */
    @Query("SELECT DISTINCT score FROM scores")
    suspend fun distinctScores(): List<Int>

    /** Every point ever scored, added up. Null when nothing has been recorded yet - SUM over no rows. */
    @Query("SELECT SUM(score) FROM scores")
    suspend fun totalPoints(): Int?

    /**
     * One row per distinct player name that has ever recorded a score, alphabetical - excluding
     * anyone dismissed from the Statistics screen (see [DismissedPlayerStats]). Their scores are
     * still counted on the Leaderboard; this query backs Statistics only.
     */
    @Query(
        """
        SELECT playerName,
               MIN(timestampEpochMillis) AS firstPlayedEpochMillis,
               COUNT(*) AS gamesPlayed,
               SUM(CASE WHEN won = 1 THEN 1 ELSE 0 END) AS gamesWon,
               SUM(CASE WHEN won = 0 THEN 1 ELSE 0 END) AS gamesLost,
               MAX(score) AS maxScore
        FROM scores
        WHERE playerName NOT IN (SELECT playerName FROM dismissed_player_stats)
        GROUP BY playerName
        ORDER BY playerName COLLATE NOCASE ASC
        """,
    )
    suspend fun playerSummaries(): List<PlayerScoreSummary>

    /** One player's win/loss history, most recent game first - walked to find their current streak. */
    @Query("SELECT won FROM scores WHERE playerName = :playerName ORDER BY timestampEpochMillis DESC")
    suspend fun outcomesForPlayer(playerName: String): List<Boolean?>

    /** Hides [playerName] from the Statistics screen - see [DismissedPlayerStats]. */
    @Query("INSERT OR REPLACE INTO dismissed_player_stats (playerName) VALUES (:playerName)")
    suspend fun dismissPlayer(playerName: String)

    /**
     * Hides every player currently on the Leaderboard from the Statistics screen in one go - the
     * bulk version of [dismissPlayer], backing "reset statistics" without touching `scores` itself,
     * so the Leaderboard keeps every recorded row.
     */
    @Query("INSERT OR REPLACE INTO dismissed_player_stats (playerName) SELECT DISTINCT playerName FROM scores")
    suspend fun dismissAllPlayers()

    /** Un-hides [playerName] from the Statistics screen, if they were dismissed. */
    @Query("DELETE FROM dismissed_player_stats WHERE playerName = :playerName")
    suspend fun clearDismissal(playerName: String)

    /** Wipes every recorded score - the Leaderboard and Statistics screens share this one table. */
    @Query("DELETE FROM scores")
    suspend fun clearAllScores()

    /** Wipes every per-player Statistics dismissal, so `clearAllScores` starts from a clean slate. */
    @Query("DELETE FROM dismissed_player_stats")
    suspend fun clearAllDismissals()
}
