package net.zodac.dicefive.data.scores

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ScoreDao {

    @Insert
    suspend fun insert(entry: ScoreEntry)

    /**
     * Ties on [ScoreEntry.score] are broken by the same house rule `GameOverScreen` applies within
     * a single game (see [net.zodac.dicefive.game.TieBreak]) - fewest 5x, most zeroed categories,
     * lowest upper section, Chance, 3x, then 4x. Keep this in
     * step with [TieBreak.leaderboardComparator][net.zodac.dicefive.game.TieBreak.leaderboardComparator],
     * which `ScoresScreen` uses to decide which adjacent rows are a true tie (an equal `=` rank)
     * versus one this ordering has already broken.
     */
    @Query(
        """
        SELECT * FROM scores
        ORDER BY
            score DESC,
            fiveOfAKindCount ASC,
            zeroedCategoryCount DESC,
            upperSectionTotal ASC,
            chanceScore ASC,
            threeOfAKindScore ASC,
            fourOfAKindScore ASC
        LIMIT :limit OFFSET :offset
        """,
    )
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

    /** Every point the primary player alone has ever scored, added up - see
     * [ScoreEntry.isPrimaryPlayer]. Null when nothing has been recorded yet - SUM over no rows. */
    @Query("SELECT SUM(score) FROM scores WHERE isPrimaryPlayer = 1")
    suspend fun primaryPlayerTotalPoints(): Int?

    /**
     * Every recorded game, grouped by player name alphabetically and most recent first within each
     * player - excluding anyone dismissed from the Statistics screen (see [DismissedPlayerStats]).
     * Their scores are still counted on the Leaderboard; this query backs Statistics only. The exact
     * name breaks ties between names equal but for case, so each name's games stay together.
     */
    @Query(
        """
        SELECT playerName, timestampEpochMillis, won, score
        FROM scores
        WHERE playerName NOT IN (SELECT playerName FROM dismissed_player_stats)
        ORDER BY playerName COLLATE NOCASE ASC, playerName ASC, timestampEpochMillis DESC
        """,
    )
    suspend fun playerGames(): List<PlayerGame>

    /** Hides [playerName] from the Statistics screen - see [DismissedPlayerStats]. */
    @Query("INSERT OR REPLACE INTO dismissed_player_stats (playerName) VALUES (:playerName)")
    suspend fun dismissPlayer(playerName: String)

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
