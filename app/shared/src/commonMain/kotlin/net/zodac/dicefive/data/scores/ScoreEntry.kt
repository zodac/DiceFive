package net.zodac.dicefive.data.scores

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import net.zodac.dicefive.game.TieBreakStats
import net.zodac.dicefive.model.GameMode

/**
 * One finished game's final score for a human player - AI scores are never recorded - with the
 * tie-break house-rule stats the Leaderboard orders equal scores by (see
 * [net.zodac.dicefive.game.TieBreak]). Every column but [won] is always set.
 */
@Entity(tableName = "scores")
data class ScoreEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playerName: String,
    val score: Int,
    val timestampEpochMillis: Long,
    /**
     * Whether this player had the top score (ties included) in this game. Null for a solo game -
     * there's nobody to beat - which [ScoreRepository.playerStatistics] counts as played, but not
     * toward wins, losses or a win streak.
     */
    val won: Boolean?,
    /**
     * Whether this row belongs to the primary player (`state.players[0]`, "You" - see
     * [net.zodac.dicefive.game.AchievementEngine]'s class doc), as opposed to another human seat
     * in a local pass-and-play game. Names can be renamed/reused across players, so this is
     * recorded directly rather than inferred from [playerName] - it's what
     * [ScoreDao.primaryPlayerTotalPoints] filters on for
     * [net.zodac.dicefive.model.Achievement.PROFESSIONAL_ROLLER], which is scoped to player 1
     * alone, unlike the score-collection achievements that (correctly) count every human's score.
     */
    val isPrimaryPlayer: Boolean,
    /** How many 5x this player scored in this game - see [net.zodac.dicefive.model.PlayerState.fiveOfAKindCount]. */
    val fiveOfAKindCount: Int,
    /**
     * The rest of the tie-break stats: how many categories scored zero, the upper-section subtotal
     * (excluding its bonus), and the Chance/3x/4x boxes. There is no `tricolourScoredCount` column -
     * see [net.zodac.dicefive.game.TieBreak]'s doc comment on why that one criterion is left out of
     * the cross-mode leaderboard entirely.
     */
    val zeroedCategoryCount: Int,
    val upperSectionTotal: Int,
    val chanceScore: Int,
    val threeOfAKindScore: Int,
    val fourOfAKindScore: Int,
    /**
     * Whether this game's mode puts its scores on the Leaderboard ([net.zodac.dicefive.model.GameMode.countsOnLeaderboard]).
     * A row that doesn't is still a game played - Statistics and career points count it - but the
     * Leaderboard, a personal best and the score-collection achievements leave it out. Every row
     * recorded before the column existed was a game that counted.
     */
    @ColumnInfo(defaultValue = "1")
    val onLeaderboard: Boolean = true,
    /**
     * The [GameMode.id] this game was played in, for the Leaderboard's per-mode cards and the mode
     * its long-press names. Every row recorded before the column existed was a Standard game as far
     * as can be told - the mode wasn't kept - so that is its default.
     */
    @ColumnInfo(defaultValue = "'standard'")
    val gameModeId: String = GameMode.STANDARD.id,
) {

    /** The mode [gameModeId] names, or null for an id this build doesn't know (a mode since removed). */
    val gameMode: GameMode? get() = GameMode.fromId(gameModeId)

    /** For [ScoreDao.pagedScores]'s ORDER BY to translate into the Leaderboard's own displayed
     * ranks/`=` ties - see [ScoresScreen][net.zodac.dicefive.ui.scores.ScoresScreen]. */
    fun toTieBreakStats(): TieBreakStats = TieBreakStats(
        score = score,
        fiveOfAKindCount = fiveOfAKindCount,
        zeroedCategoryCount = zeroedCategoryCount,
        tricolourScoredCount = null,
        upperSectionTotal = upperSectionTotal,
        chance = chanceScore,
        threeOfAKind = threeOfAKindScore,
        fourOfAKind = fourOfAKindScore,
    )
}
