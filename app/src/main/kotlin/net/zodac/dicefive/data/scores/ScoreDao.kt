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
}
