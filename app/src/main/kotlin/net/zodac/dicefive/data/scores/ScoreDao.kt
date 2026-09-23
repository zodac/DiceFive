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

    /** Null when nothing has been recorded yet - MAX over no rows. */
    @Query("SELECT MAX(score) FROM scores")
    suspend fun bestScore(): Int?

    /** Every score that has ever been recorded, once each - what the score-collection achievements count. */
    @Query("SELECT DISTINCT score FROM scores")
    suspend fun distinctScores(): List<Int>

    /** Every point ever scored, added up. Null when nothing has been recorded yet - SUM over no rows. */
    @Query("SELECT SUM(score) FROM scores")
    suspend fun totalPoints(): Int?

    /** One row per distinct player name that has ever recorded a score, alphabetical. */
    @Query(
        """
        SELECT playerName,
               MIN(timestampEpochMillis) AS firstPlayedEpochMillis,
               COUNT(*) AS gamesPlayed,
               SUM(CASE WHEN won = 1 THEN 1 ELSE 0 END) AS gamesWon,
               SUM(CASE WHEN won = 0 THEN 1 ELSE 0 END) AS gamesLost,
               MAX(score) AS maxScore
        FROM scores
        GROUP BY playerName
        ORDER BY playerName COLLATE NOCASE ASC
        """,
    )
    suspend fun playerSummaries(): List<PlayerScoreSummary>

    /** One player's win/loss history, most recent game first - walked to find their current streak. */
    @Query("SELECT won FROM scores WHERE playerName = :playerName ORDER BY timestampEpochMillis DESC")
    suspend fun outcomesForPlayer(playerName: String): List<Boolean?>

    /** Wipes every recorded score - the Leaderboard and Statistics screens share this one table. */
    @Query("DELETE FROM scores")
    suspend fun clearAll()
}
