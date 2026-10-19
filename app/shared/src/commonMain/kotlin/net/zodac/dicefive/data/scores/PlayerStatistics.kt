package net.zodac.dicefive.data.scores

import net.zodac.dicefive.model.GameMode

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
    /** The same player's games split by mode, in [GameMode.entries] order; a mode never played is absent. */
    val modes: List<ModeStatistics> = emptyList(),
) {

    /** [totalScore] over [gamesPlayed], rounded half up to a whole number; 0 for a player with no games. */
    val averageScore: Int
        get() = if (gamesPlayed == 0) 0 else ((totalScore.toLong() * 2 + gamesPlayed) / (gamesPlayed * 2L)).toInt()

    /** See [winRatePercent]. */
    val winRatePercent: Int?
        get() = winRatePercent(gamesWon, gamesLost)
}

/** One player's games in one [gameMode]: the Statistics card's By mode row. */
data class ModeStatistics(
    val gameMode: GameMode,
    /** Every game in the mode, solo ones included. */
    val gamesPlayed: Int,
    val gamesWon: Int,
    val gamesLost: Int,
    val soloGames: Int,
    val maxScore: Int,
) {

    /** See [winRatePercent]. */
    val winRatePercent: Int?
        get() = winRatePercent(gamesWon, gamesLost)
}

/**
 * The share of the games with someone to beat that were won, as a whole percentage rounded half up. Solo games aren't
 * in it. Null when every game was solo, so there's no rate to give.
 */
internal fun winRatePercent(won: Int, lost: Int): Int? {
    val decided = won + lost
    return if (decided == 0) null else ((won * 200L + decided) / (decided * 2L)).toInt()
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
    val gameModeId: String,
)
