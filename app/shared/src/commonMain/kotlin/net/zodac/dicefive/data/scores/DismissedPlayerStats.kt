package net.zodac.dicefive.data.scores

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A player name whose card has been dismissed from the Statistics screen. This only tells
 * [ScoreDao.playerSummaries] to skip that name - the underlying `scores` rows are untouched, so
 * the Leaderboard screen still shows their full history. Playing another game under the same name
 * clears the dismissal automatically, in [ScoreRepository.recordScore].
 */
@Entity(tableName = "dismissed_player_stats")
data class DismissedPlayerStats(
    @PrimaryKey val playerName: String,
)
