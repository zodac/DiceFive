package net.zodac.dicefive.data.scores

/**
 * One player's aggregate stats across every game recorded for them - the Statistics screen's row
 * shape. "Player" here means a distinct `playerName`, not a stable identity: renaming a player
 * starts a new row, the same way it starts a new leaderboard entry.
 */
data class PlayerStatistics(
    val playerName: String,
    val firstPlayedEpochMillis: Long,
    val gamesPlayed: Int,
    val gamesWon: Int,
    val gamesLost: Int,
    val currentWinStreak: Int,
    val bestWinStreak: Int,
    val maxScore: Int,
    /** Every point scored across every recorded game, solo or multiplayer. */
    val totalScore: Int,
    /** How many 5x this player has scored, summed over every recorded game. */
    val fiveOfAKindCount: Int,
    /** Games with nobody to beat - the ones recorded with a null outcome, so counted in [gamesPlayed] only. */
    val soloGames: Int,
) {

    /** [totalScore] over [gamesPlayed], rounded half up to a whole number; 0 for a player with no games. */
    val averageScore: Int
        get() = if (gamesPlayed == 0) 0 else ((totalScore.toLong() * 2 + gamesPlayed) / (gamesPlayed * 2L)).toInt()
}

/**
 * [ScoreDao.playerGames]'s projection: one recorded game, just the columns the Statistics screen
 * needs. [ScoreRepository.playerStatistics] folds every player's games into their
 * [PlayerStatistics] in one pass - the win streaks depend on game order, which a `GROUP BY` can't
 * see, so this one query replaces a per-player summary plus a history query for each player.
 */
data class PlayerGame(
    val playerName: String,
    val timestampEpochMillis: Long,
    val won: Boolean?,
    val score: Int,
    val fiveOfAKindCount: Int,
)
