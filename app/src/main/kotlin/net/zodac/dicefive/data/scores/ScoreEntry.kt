package net.zodac.dicefive.data.scores

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One finished game's final score for a human player. AI scores are never recorded. */
@Entity(tableName = "scores")
data class ScoreEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playerName: String,
    val score: Int,
    val timestampEpochMillis: Long,
)
