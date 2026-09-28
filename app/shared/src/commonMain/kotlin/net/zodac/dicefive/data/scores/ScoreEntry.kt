package net.zodac.dicefive.data.scores

import androidx.room.Entity
import androidx.room.PrimaryKey
import net.zodac.dicefive.game.TieBreakStats

/** One finished game's final score for a human player. AI scores are never recorded. */
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
    val won: Boolean? = null,
    /**
     * Whether this row belongs to the primary player (`state.players[0]`, "You" - see
     * [net.zodac.dicefive.game.AchievementEngine]'s class doc), as opposed to another human seat
     * in a local pass-and-play game. Names can be renamed/reused across players, so this is
     * recorded directly rather than inferred from [playerName] - it's what
     * [ScoreDao.primaryPlayerTotalPoints] filters on for
     * [net.zodac.dicefive.model.Achievement.PROFESSIONAL_ROLLER], which is scoped to player 1
     * alone, unlike the score-collection achievements that (correctly) count every human's score.
     */
    val isPrimaryPlayer: Boolean = false,
    /**
     * How many 5x this player scored in this game - see
     * [net.zodac.dicefive.model.PlayerState.fiveOfAKindCount]. Null means "not recorded" (the
     * Leaderboard shows "-"), not zero - a leftover of the pre-release schema, whose older rows
     * predated this column; every row recorded now has it.
     */
    val fiveOfAKindCount: Int? = null,
    /**
     * This player's tie-break house-rule stats (see [net.zodac.dicefive.game.TieBreak]) - how many
     * categories scored zero, the upper-section subtotal (excluding its bonus), and the Chance/3x/4x
     * boxes. Nullable for the same pre-release reason as [fiveOfAKindCount], with the same
     * "unknown means excluded, not guessed at" handling. There is no
     * `tricolourScoredCount` column - see [net.zodac.dicefive.game.TieBreak]'s doc comment on why
     * that one criterion is excluded from the cross-mode leaderboard entirely.
     */
    val zeroedCategoryCount: Int? = null,
    val upperSectionTotal: Int? = null,
    val chanceScore: Int? = null,
    val threeOfAKindScore: Int? = null,
    val fourOfAKindScore: Int? = null,
) {

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
