package net.zodac.dicefive.model

/**
 * A device-wide running total that one or more [Achievement]s are measured against. Shared rather
 * than per-achievement so "finish 10 / 50 / 100 games" is one stored number, not three.
 */
enum class AchievementCounter {
    GAMES_PLAYED,
    GAMES_WON,
    WIN_STREAK,
    SCORED_5X,
    DICE_ROLLED,
}

/**
 * What an achievement is *about*, and the order those themes are shown in. Grouping the list this
 * way is what lets one ladder's rungs sit together - "Sharpshooter" then "High Roller" then "Dice
 * Deity" - instead of being scattered across an alphabetical list.
 */
enum class AchievementCategory(val label: String) {
    MILESTONES("Milestones"),
    DICE("Dice feats"),
    SCORING("Scoring"),
    WINNING("Winning"),
    STREAKS("Streaks"),
    MISFORTUNE("Misfortune"),
    COLLECTION("Collection"),
}

/** How a locked achievement shows progress, and how eagerly progress is worth announcing. */
enum class ProgressStyle {
    /** One-shot: there is nothing to count, you either did it or you didn't. */
    NONE,

    /** Counts up and never falls. Announced only at quarter marks, or a banner per roll. */
    CUMULATIVE,

    /** Can collapse back to zero at any time, so every step forward is worth announcing. */
    STREAK,
}

/**
 * Every achievement the app tracks. Achievements are **per device, not per player**: any human
 * player at this device contributes, and AI results never do (they only ever count as opponents).
 *
 * [id] is the stable external key - it is what's written to storage and what a Google Play Games
 * achievement will be mapped to later, so **never change one**; rename [title]/[description]
 * freely instead. [counter]/[target] describe incremental achievements, which map onto Play's
 * incremental type; [ProgressStyle.STREAK] ones do not (Play's `setSteps` can't go backwards) and
 * become plain unlock-only achievements there, keeping their progress bar locally.
 *
 * **Declaration order is display order.** Entries are grouped by [category] and, within a
 * category, run from easiest to hardest, so a ladder's rungs stay adjacent and in sequence -
 * "Sharpshooter", "High Roller", "Dice Deity". Nothing reads the ordinal, so a new achievement
 * goes wherever it belongs in that reading order rather than on the end; `AchievementEngineTest`
 * fails the build if a category ends up split across the list. Ids are keyed by [id], not
 * position, so reordering is safe for already-stored unlocks.
 */
enum class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val category: AchievementCategory,
    val counter: AchievementCounter? = null,
    val target: Int = 1,
    /**
     * For the "collect every score in this range" achievements: the band of final scores that
     * must each have been recorded on the leaderboard at least once. [target] is the number of
     * scores in the band, which `AchievementEngineTest` keeps in step.
     */
    val scoreBand: IntRange? = null,
    /**
     * Whether [COMPLETIONIST] waits on this one. False for [COMPLETIONIST] itself, and for
     * anything that cannot currently be earned at all - see [I_ROBOT].
     */
    val countsTowardCompletion: Boolean = true,
) {

    // ---- Milestones: simply playing the game -------------------------------------------------
    FIRST_GAME(
        "games_first", "First Game", "Finish your first game",
        AchievementCategory.MILESTONES, AchievementCounter.GAMES_PLAYED,
    ),
    SOLO_GAME(
        "solo_game", "Practice Makes Perfect", "Finish a solo game",
        AchievementCategory.MILESTONES,
    ),
    FULL_TABLE(
        "full_table", "Full Table", "Play a four-player game",
        AchievementCategory.MILESTONES,
    ),
    GAMES_10(
        "games_10", "Getting Comfortable", "Finish 10 games",
        AchievementCategory.MILESTONES, AchievementCounter.GAMES_PLAYED, target = 10,
    ),
    GAMES_50(
        "games_50", "Regular", "Finish 50 games",
        AchievementCategory.MILESTONES, AchievementCounter.GAMES_PLAYED, target = 50,
    ),
    GAMES_100(
        "games_100", "Centurion", "Finish 100 games",
        AchievementCategory.MILESTONES, AchievementCounter.GAMES_PLAYED, target = 100,
    ),
    WINS_25(
        "wins_25", "Hall of Famer", "Win 25 games",
        AchievementCategory.MILESTONES, AchievementCounter.GAMES_WON, target = 25,
    ),
    DICE_10000(
        "dice_10000", "Well Rolled", "Roll 10,000 dice",
        AchievementCategory.MILESTONES, AchievementCounter.DICE_ROLLED, target = 10_000,
    ),

    // ---- Dice feats: what the dice themselves did --------------------------------------------
    // Ordered by how hard each one is to actually pull off. The two 30s both need five 6s - one
    // of them deliberately wasted on the wrong box - which is why they sit so late.
    UPPER_BONUS(
        "upper_bonus", "Bonus Round", "Earn the 35-point upper section bonus",
        AchievementCategory.DICE,
    ),
    FIRST_5X(
        "5x_first", "5x!", "Score your first 5x",
        AchievementCategory.DICE,
    ),
    NO_ZEROES(
        "no_zeroes", "Spotless", "Finish a game without a zero on your scorecard",
        AchievementCategory.DICE,
    ),
    BOTH_STRAIGHTS(
        "straights_both", "Straight Talker", "Score both straights in the same game",
        AchievementCategory.DICE,
    ),
    UPPER_84(
        "upper_84", "Upper Class", "Score 84 or more in the upper section",
        AchievementCategory.DICE,
    ),

    // The three "straight out of the cup" feats, in ascending order of how unlikely they are on a
    // single throw of five dice: a full house is 300 of the 7776 outcomes, a large straight 240,
    // and five of a kind just 6.
    FIRST_ROLL_FULL_HOUSE(
        "first_roll_full_house", "House Call", "Roll a full house on the first roll of a turn",
        AchievementCategory.DICE,
    ),
    FIRST_ROLL_LARGE_STRAIGHT(
        "first_roll_large_straight", "Straight Away", "Roll a large straight on the first roll of a turn",
        AchievementCategory.DICE,
    ),
    FIRST_ROLL_5X(
        "5x_first_roll", "Straight Out of the Cup", "Roll a 5x on the first roll of a turn",
        AchievementCategory.DICE,
    ),

    ENCORE_5X(
        "5x_encore", "Encore", "Score a second 5x in a single game",
        AchievementCategory.DICE,
    ),
    TOTAL_5X_10(
        "5x_total_10", "Dice Whisperer", "Score 5x ten times in total",
        AchievementCategory.DICE, AchievementCounter.SCORED_5X, target = 10,
    ),
    SIXES_30(
        "sixes_30", "Six Appeal", "Score the maximum 30 in Sixes",
        AchievementCategory.DICE,
    ),
    CHANCE_30(
        "chance_30", "Taking a Chance", "Score the maximum 30 in Chance",
        AchievementCategory.DICE,
    ),
    HAT_TRICK_5X(
        "5x_hat_trick", "Hat Trick", "Score three or more 5x in a single game",
        AchievementCategory.DICE,
    ),

    // ---- Scoring: one ladder, so the rungs must stay adjacent and in order --------------------
    PERSONAL_BEST(
        "personal_best", "New Personal Best", "Beat your best score on the leaderboard",
        AchievementCategory.SCORING,
    ),
    SCORE_200(
        "score_200", "Solid Round", "Score 200 or more in a game",
        AchievementCategory.SCORING,
    ),
    SCORE_300(
        "score_300", "Sharpshooter", "Score 300 or more in a game",
        AchievementCategory.SCORING,
    ),
    SCORE_400(
        "score_400", "High Roller", "Score 400 or more in a game",
        AchievementCategory.SCORING,
    ),
    SCORE_500(
        "score_500", "Dice Deity", "Score 500 or more in a game",
        AchievementCategory.SCORING,
    ),

    // ---- Winning: beating whoever else was at the table ---------------------------------------
    FIRST_WIN(
        "win_first", "First Victory", "Win a game against at least one opponent",
        AchievementCategory.WINNING, AchievementCounter.GAMES_WON,
    ),
    WIN_BY_100(
        "win_by_100", "Landslide", "Win by 100 points or more",
        AchievementCategory.WINNING,
    ),
    WIN_BY_5(
        "win_by_5", "Photo Finish", "Win by 5 points or fewer",
        AchievementCategory.WINNING,
    ),
    COMEBACK(
        "comeback", "Comeback Kid", "Win after trailing at the start of the final round",
        AchievementCategory.WINNING,
    ),

    BEAT_THREE_AI(
        "beat_three_ai", "Last Human Standing", "Win a four-player game against three AI",
        AchievementCategory.WINNING,
    ),

    /**
     * Not currently earnable: the difficulty selector is deferred (see `.claude/UI.md`) and every
     * AI plays the same strategy whatever their stored [Difficulty], so no game can ever present
     * three genuinely Hard opponents. Listed so it's visible as something coming, last in its
     * category as the hardest thing there, and excluded from [COMPLETIONIST] so it doesn't make
     * that unobtainable in the meantime - flip `countsTowardCompletion` back on when AI difficulty
     * actually lands.
     */
    I_ROBOT(
        "i_robot", "I, Robot", "Win a four-player game against three Hard AI",
        AchievementCategory.WINNING, countsTowardCompletion = false,
    ),

    // ---- Streaks: their own theme, because they're the ones that can fall back to zero --------
    STREAK_3(
        "streak_3", "On a Roll", "Win 3 games in a row",
        AchievementCategory.STREAKS, AchievementCounter.WIN_STREAK, target = 3,
    ),
    STREAK_10(
        "streak_10", "Untouchable", "Win 10 games in a row",
        AchievementCategory.STREAKS, AchievementCounter.WIN_STREAK, target = 10,
    ),

    // ---- Misfortune: going badly, on purpose or otherwise --------------------------------------
    SCRATCHED_5X(
        "5x_scratched", "Scratched", "Take a zero in the 5x box",
        AchievementCategory.MISFORTUNE,
    ),
    SINGULARITY(
        "singularity", "Singularity", "Lose a game to an AI",
        AchievementCategory.MISFORTUNE,
    ),
    SCORE_UNDER_100(
        "score_under_100", "Cold Dice", "Finish a game with under 100 points",
        AchievementCategory.MISFORTUNE,
    ),
    LOW_ROLLS(
        "low_rolls", "Low Rolls", "Finish a game with under 20 points",
        AchievementCategory.MISFORTUNE,
    ),
    ALL_ZEROES(
        "zeroes_except_chance", "How Do You Play This Game?",
        "Score zero in every category except Chance",
        AchievementCategory.MISFORTUNE,
    ),
    EXTREME_LOW_ROLLS(
        "low_rolls_extreme", "Extreme Low Rolls", "Finish a game on exactly 5 - the lowest score the rules allow",
        AchievementCategory.MISFORTUNE,
    ),

    // ---- Collection: filling in every score there is, and the set of achievements itself ------
    // The six ledger achievements are the longest haul in the game, so they sit at the very end,
    // ascending by band, with Completionist after them.
    //
    // They are the one group not backed by a stored counter: progress is derived from the scores
    // table, so they are retroactive - scores already on the leaderboard count - and the progress
    // bar always agrees with what the Leaderboard screen shows. Only human scores are ever recorded
    // there, which is also the rule here.
    TALLY(
        "scores_5_50", "Tally", "Record every score from 5 to 50 on the leaderboard",
        AchievementCategory.COLLECTION, target = 46, scoreBand = 5..50,
    ),
    BOOKKEEPER(
        "scores_51_100", "Bookkeeper", "Record every score from 51 to 100 on the leaderboard",
        AchievementCategory.COLLECTION, target = 50, scoreBand = 51..100,
    ),
    REGISTRAR(
        "scores_101_150", "Registrar", "Record every score from 101 to 150 on the leaderboard",
        AchievementCategory.COLLECTION, target = 50, scoreBand = 101..150,
    ),
    AUDITOR(
        "scores_151_200", "Auditor", "Record every score from 151 to 200 on the leaderboard",
        AchievementCategory.COLLECTION, target = 50, scoreBand = 151..200,
    ),
    ARCHIVIST(
        "scores_201_250", "Archivist", "Record every score from 201 to 250 on the leaderboard",
        AchievementCategory.COLLECTION, target = 50, scoreBand = 201..250,
    ),
    HISTORIAN(
        "scores_251_300", "Historian", "Record every score from 251 to 300 on the leaderboard",
        AchievementCategory.COLLECTION, target = 50, scoreBand = 251..300,
    ),
    COMPLETIONIST(
        "completionist", "Completionist", "Unlock every other achievement",
        AchievementCategory.COLLECTION, countsTowardCompletion = false,
    ),
    ;

    val progressStyle: ProgressStyle
        get() = when {
            // A band only ever gains scores, so it climbs like any other running total.
            scoreBand != null -> ProgressStyle.CUMULATIVE
            // A target of 1 is a plain unlock that happens to be driven by a counter: a "0 of 1"
            // progress bar says nothing the locked state doesn't already.
            counter == null || target <= 1 -> ProgressStyle.NONE
            counter == AchievementCounter.WIN_STREAK -> ProgressStyle.STREAK
            else -> ProgressStyle.CUMULATIVE
        }

    val hasProgressBar: Boolean
        get() = progressStyle != ProgressStyle.NONE

    companion object {

        /** The achievements [COMPLETIONIST] waits on. */
        val COMPLETION_REQUIREMENTS: List<Achievement> = entries.filter { it.countsTowardCompletion }

        fun fromId(id: String): Achievement? = entries.firstOrNull { it.id == id }
    }
}
