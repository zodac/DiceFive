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
    /**
     * Whether this player had the top score (ties included) in this game. Null for a solo game -
     * there's nobody to beat - and for a row recorded before this column existed; both are
     * treated alike by [ScoreRepository.playerStatistics]: counted as played, but not counted
     * toward wins, losses or a win streak.
     */
    val won: Boolean? = null,
)
