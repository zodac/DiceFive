package net.zodac.dicefive.data.scores

import androidx.room.Entity
import androidx.room.PrimaryKey
import net.zodac.dicefive.model.playerNameKey

/**
 * A player name whose card has been dismissed from the Statistics screen. This only tells
 * [ScoreDao.playerGames] to skip that name - the underlying `scores` rows are untouched, so
 * the Leaderboard screen still shows their full history. Playing another game under the same name
 * clears the dismissal automatically, in [ScoreRepository.recordScore].
 */
@Entity(tableName = "dismissed_player_stats")
data class DismissedPlayerStats(
    val playerName: String,
    /**
     * When the card was dismissed. A game under the name recorded after this shows the card again, on whichever device
     * it was played - the cloud save drops a dismissal older than the name's newest game.
     */
    val dismissedAtEpochMillis: Long,
) {
    /** The name's [ScoreEntry.playerKey]: one dismissal per player, however the name was cased. Derived as that is. */
    @PrimaryKey
    var playerKey: String = playerNameKey(playerName)
        internal set
}
