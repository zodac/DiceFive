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
}
