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
)

/**
 * [ScoreDao.playerSummaries]'s projection - everything a single `GROUP BY playerName` query can
 * answer. The win streak isn't part of it: it depends on game order, not just counts, so
 * [ScoreRepository.playerStatistics] walks each player's history separately to fill it in.
 */
data class PlayerScoreSummary(
    val playerName: String,
    val firstPlayedEpochMillis: Long,
    val gamesPlayed: Int,
    val gamesWon: Int,
    val gamesLost: Int,
    val maxScore: Int,
)
