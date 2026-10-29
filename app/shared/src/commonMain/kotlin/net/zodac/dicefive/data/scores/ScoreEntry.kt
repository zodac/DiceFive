package net.zodac.dicefive.data.scores

import androidx.room.Entity
import androidx.room.Index
import androidx.room.Index.Order.ASC
import androidx.room.Index.Order.DESC
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlin.uuid.Uuid
import net.zodac.dicefive.game.TieBreakStats
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.playerNameKey

/**
 * One finished game's final score for a human player - AI scores are never recorded - with the
 * tie-break house-rule stats the Leaderboard orders equal scores by (see
 * [net.zodac.dicefive.game.TieBreak]). Every column but [won] is always set, and [uuid] is the primary key.
 *
 * Every query [ScoreDao] runs while the app is in use is answered from an index, never by reading the whole table or
 * sorting it; `ScoreQueryPlanTest` holds each one to that.
 */
@Entity(
    tableName = "scores",
    indices = [
        // The Combined table's order: LEADERBOARD_ORDER under its filter, so a page is read in order, never sorted.
        Index(
            name = "index_scores_leaderboard_order",
            value = [
                "onLeaderboard", "score", "fiveOfAKindCount", "zeroedCategoryCount", "upperSectionTotal", "chanceScore", "threeOfAKindScore",
                "fourOfAKindScore",
            ],
            orders = [ASC, DESC, ASC, DESC, ASC, ASC, ASC, ASC],
        ),
        // Each mode's card: the same order under its own filter.
        Index(
            name = "index_scores_mode_order",
            value = [
                "gameModeId", "playedUnmodified", "score", "fiveOfAKindCount", "zeroedCategoryCount", "upperSectionTotal", "chanceScore",
                "threeOfAKindScore", "fourOfAKindScore",
            ],
            orders = [ASC, ASC, DESC, ASC, DESC, ASC, ASC, ASC, ASC],
        ),
        // A player's best, from the index alone.
        Index(value = ["playerKey", "onLeaderboard", "score"]),
        // Player 1's career points, modes played and unposted bests, from the index alone.
        Index(name = "index_scores_primary_player", value = ["isPrimaryPlayer", "gameModeId", "playedUnmodified", "timestampEpochMillis", "score"]),
        // Statistics: each player's games added up per mode, read in group order from the index alone.
        Index(
            name = "index_scores_player_mode_totals",
            value = ["playerKey", "gameModeId", "won", "score", "fiveOfAKindCount", "timestampEpochMillis", "playedUnmodified"],
        ),
        // Statistics: a player's games newest first - their latest name, and the outcomes the win streaks walk.
        Index(
            name = "index_scores_player_history",
            value = ["playerKey", "timestampEpochMillis", "won", "playedUnmodified"],
            orders = [ASC, DESC, ASC, ASC],
        ),
    ],
)
data class ScoreEntry(
    /**
     * This game's row everywhere it's copied - on this device, in the cloud save ([net.zodac.dicefive.online.CloudSave])
     * and on every device that merges it. Random, from the device that recorded it; stored as its 16 bytes.
     */
    @PrimaryKey val uuid: Uuid = Uuid.random(),
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
    /** The [GameMode.id] this game was played in, for the Leaderboard's per-mode cards and the mode its long-press names. */
    val gameModeId: String = GameMode.STANDARD.id,
    /**
     * Whether the game ran under its mode's own rules, with no modifier (not even the turn timer) -
     * [net.zodac.dicefive.model.GameState.hasModifiers] false. [onLeaderboard] can't say so for a mode that is always off the Combined
     * board (it's false whether or not a modifier was on), and an online leaderboard compares like with like, so
     * this is what decides whether the row may be submitted to one, and whether its mode's Leaderboard card lists it.
     * Only these rows count anywhere a player sees: Statistics, career points and the modes played leave the rest out too,
     * as a game with a modifier on counts for no achievement. The rest are kept, for the cloud save.
     */
    val playedUnmodified: Boolean = true,
) {

    /**
     * What every query matches [playerName] by: [playerNameKey], so names differing only by case - in any alphabet, which
     * SQLite's own `COLLATE NOCASE` can't fold - are one player. Derived from [playerName] (a copy with another name gets
     * that name's key), never copied between devices, and left out of equality. Settable only for Room to read it back.
     */
    var playerKey: String = playerNameKey(playerName)
        internal set

    /**
     * Whether this row is on the Combined Leaderboard: its mode counts there ([GameMode.countsOnLeaderboard]) and it was
     * [playedUnmodified], as [net.zodac.dicefive.model.GameState.countsOnLeaderboard] decides for a game. An unmodified row
     * that isn't (a mode off the board) is still a game played - Statistics and career points count it - but the
     * Leaderboard, a personal best and the score-collection achievements leave it out. Derived as [playerKey] is, so it can't disagree with the columns it
     * comes from; a mode this build doesn't know is off the board. Settable only for Room to read it back.
     */
    var onLeaderboard: Boolean = GameMode.fromId(gameModeId)?.countsOnLeaderboard == true && playedUnmodified
        internal set

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

/** How Room stores a [ScoreEntry.uuid]: its 16 bytes, half the size of its 32 hex digits. */
class UuidConverters {
    @TypeConverter
    fun toBytes(uuid: Uuid): ByteArray = uuid.toByteArray()

    @TypeConverter
    fun fromBytes(bytes: ByteArray): Uuid = Uuid.fromByteArray(bytes)
}
