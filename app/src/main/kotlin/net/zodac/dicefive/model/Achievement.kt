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
    THEMES("Themes"),
    DICE("Dice feats"),
    SCORING("Scoring"),
    WINNING("Winning"),
    MISFORTUNE("Misfortune"),
    MISCELLANEOUS("Miscellaneous"),
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
 * How much of a locked achievement is shown before it's earned, and how (if at all) it maps onto
 * a Google Play Games achievement in future.
 */
enum class AchievementVisibility {
    /** Title and description both visible from the start. Maps to a Play "revealed" achievement. */
    NORMAL,

    /**
     * Title visible, description hidden (shown as "???") until earned. Maps to a Play "hidden"
     * achievement, which Play itself blanks out the description of until revealed/unlocked.
     */
    HIDDEN,

    /**
     * Absent entirely - no entry, title, or description - until earned, and never counted
     * towards the unlocked/total tallies, earned or not. Local only: never synced to Play Games.
     * An easter egg, not a checklist item.
     */
    SECRET,
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
    /** True for the one achievement measured against every point ever scored, rather than a counter. */
    val isCareerPoints: Boolean = false,
    /**
     * Whether [COMPLETIONIST] waits on this one. False for [COMPLETIONIST] itself, and for
     * anything that cannot currently be earned at all - see [I_ROBOT].
     */
    val countsTowardCompletion: Boolean = true,
    /** See [AchievementVisibility]. Defaults to fully visible, which is the vast majority. */
    val visibility: AchievementVisibility = AchievementVisibility.NORMAL,
) {

    // ---- Milestones: simply playing the game -------------------------------------------------
    THE_JOURNEY_BEGINS(
        "journey_begins", "The Journey Begins", "Start your first game",
        AchievementCategory.MILESTONES,
    ),
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
    CONTINUED_GAME(
        "continued_game", "Let's Finish This", "Leave a game then resume it",
        AchievementCategory.MILESTONES,
    ),
    REPLAY_AFTER_LOSS(
        "replay_after_loss", "One More Time", "Start a new game immediately after losing one",
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
        "wins_25", "Hall Of Famer", "Win 25 games",
        AchievementCategory.MILESTONES, AchievementCounter.GAMES_WON, target = 25,
    ),
    DICE_10000(
        "dice_10000", "Well Rolled", "Roll 10,000 dice",
        AchievementCategory.MILESTONES, AchievementCounter.DICE_ROLLED, target = 10_000,
    ),
    STREAK_3(
        "streak_3", "On A Roll", "Win 3 games in a row",
        AchievementCategory.MILESTONES, AchievementCounter.WIN_STREAK, target = 3,
    ),
    STREAK_10(
        "streak_10", "Untouchable", "Win 10 games in a row",
        AchievementCategory.MILESTONES, AchievementCounter.WIN_STREAK, target = 10,
    ),
    PROFESSIONAL_ROLLER(
        "career_points_100k", "Professional Roller", "Score 100,000 points across all your games",
        AchievementCategory.MILESTONES, target = 100_000, isCareerPoints = true,
    ),

    // ---- Themes: playing dress-up with the table itself ---------------------------------------
    STYLE_DICE(
        "style_dice", "Fresh Set", "Start a game with a non-default dice style",
        AchievementCategory.THEMES,
    ),
    STYLE_CUP(
        "style_cup", "Shake It Up", "Start a game with a non-default dice cup style",
        AchievementCategory.THEMES,
    ),
    STYLE_BACKGROUND(
        "style_background", "Change Of Scenery", "Start a game with a non-default mat",
        AchievementCategory.THEMES,
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

    LOWER_150(
        "lower_150", "Lower Class", "Score 150 or more in the lower section",
        AchievementCategory.DICE,
    ),

    // The best full house there is - three 6s and two 5s specifically, never the joker rule's
    // five-of-a-kind bent into the box instead. Sits right above House Call, the more general
    // "any full house, first roll" feat.
    FULLER_HOUSE(
        "fuller_house", "Fuller House", "Score the best Full House (three 6s and two 5s)",
        AchievementCategory.DICE, visibility = AchievementVisibility.HIDDEN,
    ),

    // The three "straight out of the cup" feats, in ascending order of how unlikely they are on a
    // single throw of five dice: a full house is 300 of the 7776 outcomes, a large straight 240,
    // and five of a kind just 6.
    FIRST_ROLL_FULL_HOUSE(
        "first_roll_full_house", "House Call", "Roll a full house on the first roll of a turn",
        AchievementCategory.DICE, visibility = AchievementVisibility.HIDDEN,
    ),
    FIRST_ROLL_LARGE_STRAIGHT(
        "first_roll_large_straight", "Straight Away", "Roll a large straight on the first roll of a turn",
        AchievementCategory.DICE, visibility = AchievementVisibility.HIDDEN,
    ),
    FIRST_ROLL_5X(
        "5x_first_roll", "Straight Out Of The Cup", "Roll a 5x on the first roll of a turn",
        AchievementCategory.DICE, visibility = AchievementVisibility.HIDDEN,
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
        AchievementCategory.DICE, visibility = AchievementVisibility.HIDDEN,
    ),
    CHANCE_30(
        "chance_30", "Taking A Chance", "Score the maximum 30 in Chance",
        AchievementCategory.DICE, visibility = AchievementVisibility.HIDDEN,
    ),
    HAT_TRICK_5X(
        "5x_hat_trick", "Hat Trick", "Score three or more 5x in a single game",
        AchievementCategory.DICE,
    ),

    // ---- Dice feats continued: interaction quirks, not just what the dice show ----------------
    DEJA_VU(
        "deja_vu", "Déjà Vu", "Roll the exact same result twice in a row, without holding any dice in between",
        AchievementCategory.DICE, visibility = AchievementVisibility.HIDDEN,
    ),
    LOADED_DICE(
        "loaded_dice", "Are These Loaded Dice?", "After holding some dice, have the rest come up exactly the same on both re-rolls",
        AchievementCategory.DICE,
    ),
    TWICE_IN_A_LIFETIME(
        "5x_twice_in_a_row", "Twice In A Lifetime", "Score a 5x on two of your turns in a row",
        AchievementCategory.DICE,
    ),
    NATURAL_5X(
        "5x_natural", "Natural 5x", "Roll a 5x on the 2nd or 3rd roll without holding any dice",
        AchievementCategory.DICE,
    ),
    PRODUCT_PLACEMENT(
        "product_placement", "Product Placement", "Roll 2, 4, 5, 3, 6 - the exact dice on the main menu, in that order",
        AchievementCategory.DICE, visibility = AchievementVisibility.HIDDEN,
    ),
    I_CAN_COUNT(
        "i_can_count", "I Can Count!", "Roll 1, 2, 3, 4, 5 in that order on the first roll of a turn",
        AchievementCategory.DICE,
    ),
    POINTLESS_ROLL(
        "pointless_roll", "What Was The Point Of That?", "Hold all five dice, then roll anyway",
        AchievementCategory.DICE, visibility = AchievementVisibility.HIDDEN,
    ),
    CUNNING_STRATEGY(
        "cunning_strategy", "A Cunning Strategy", "Hold all five dice, then unhold every one of them",
        AchievementCategory.DICE,
    ),
    EXACT_CHANGE(
        "upper_exact_ladder", "Exact Change",
        "Score exactly 1 in Ones, 2 in Twos, and so on up to 6 in Sixes, all in the same game",
        AchievementCategory.DICE,
    ),

    // ---- Scoring: one ladder, so the rungs must stay adjacent and in order --------------------
    PERSONAL_BEST(
        "personal_best", "New Personal Best", "Beat your best score on the leaderboard",
        AchievementCategory.SCORING,
    ),
    NICE(
        "score_exactly_69", "Nice", "Finish a game on exactly 69",
        AchievementCategory.SCORING, visibility = AchievementVisibility.HIDDEN,
    ),
    TON(
        "score_exactly_100", "Ton!", "Finish a game on exactly 100",
        AchievementCategory.SCORING,
    ),
    SCORE_200(
        "score_200", "Solid Round", "Score 200 or more in a game",
        AchievementCategory.SCORING,
    ),
    // Harder than SOLID_ROUND despite the same threshold: any score from 200 up satisfies that one,
    // but only the single value 200 satisfies this - so it sits right after it, not before.
    DOUBLE_TON(
        "score_exactly_200", "Double Ton", "Finish a game on exactly 200",
        AchievementCategory.SCORING,
    ),
    SCORE_300(
        "score_300", "Sharpshooter", "Score 300 or more in a game",
        AchievementCategory.SCORING,
    ),
    TRIPLE_TON(
        "score_exactly_300", "Triple Ton", "Finish a game on exactly 300",
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
    // A hidden one-off above the ladder, not another rung of it: not shown, let alone attempted,
    // until it's already done. 1575 (PlayerState.MAX_POSSIBLE_SCORE) is the absolute ceiling the
    // rules allow, so this is excluded from COMPLETIONIST the same way I_ROBOT was while it
    // couldn't be earned - it's not that it's unearnable, it's that requiring every player to
    // stumble into a literally perfect game would make COMPLETIONIST itself absurd.
    CHEATER_CHEATER(
        "cheater_cheater", "Cheater, Cheater!", "Finish a game with the maximum possible score - 1575",
        AchievementCategory.SCORING, countsTowardCompletion = false, visibility = AchievementVisibility.SECRET,
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
    ZERO_TO_HERO(
        "zero_to_hero", "Zero To Hero", "Win a game after scoring zero at least three times",
        AchievementCategory.WINNING, visibility = AchievementVisibility.HIDDEN,
    ),

    BEAT_THREE_AI(
        "beat_three_ai", "Last Human Standing", "Win a four-player game against three CPU players",
        AchievementCategory.WINNING,
    ),

    I_ROBOT(
        "i_robot", "I, Robot", "Win a four-player game against three Hard CPU players",
        AchievementCategory.WINNING,
    ),
    NATURALLY_GIFTED(
        "naturally_gifted", "Naturally Gifted", "Win a game never rolling more than once in any turn",
        AchievementCategory.WINNING,
    ),

    // ---- Misfortune: going badly, on purpose or otherwise --------------------------------------
    SCRATCHED_5X(
        "5x_scratched", "Scratched", "Take a zero in the 5x box",
        AchievementCategory.MISFORTUNE,
    ),
    // Harder and more specific than Scratched: that one just needs a zero sitting in the 5x box
    // (from dice that never matched at all), this needs the dice to have genuinely been a 5x at
    // the moment a zero was committed anyway.
    WASTED_5X(
        "5x_wasted", "Wasted Fortune", "Roll a 5x but score a zero with it anyway",
        AchievementCategory.MISFORTUNE, visibility = AchievementVisibility.HIDDEN,
    ),
    DICE_HATE_ME(
        "dice_hate_me", "The Dice Hate Me", "Have a real scoring option after the 2nd roll, then leave yourself with none after the 3rd",
        AchievementCategory.MISFORTUNE,
    ),
    ALMOST_FAMOUS(
        "almost_famous", "Almost Famous", "Roll four of a kind on the first roll, then never turn it into a 5x",
        AchievementCategory.MISFORTUNE,
    ),
    SINGULARITY(
        "singularity", "Singularity", "Lose a game to a CPU player",
        AchievementCategory.MISFORTUNE,
    ),
    PIPPED_TO_THE_POST(
        "pipped_to_the_post", "Pipped To The Post", "Lose a game by a single point",
        AchievementCategory.MISFORTUNE,
    ),
    JAWS_OF_VICTORY(
        "jaws_of_victory", "Defeat From The Jaws Of Victory", "Lead going into the final round, then lose",
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
    WHY_DID_YOU_DO_THAT(
        "why_did_you_do_that", "Size Isn't Everything", "Score the small straight when the large straight was also available",
        AchievementCategory.MISFORTUNE, visibility = AchievementVisibility.HIDDEN,
    ),
    ALL_ZEROES(
        "zeroes_except_chance", "How Do You Play This Game?",
        "Score zero in every category except Chance",
        AchievementCategory.MISFORTUNE, visibility = AchievementVisibility.HIDDEN,
    ),
    EXTREME_LOW_ROLLS(
        "low_rolls_extreme", "Rock Bottom", "Finish a game on exactly 5 - the lowest score the rules allow",
        AchievementCategory.MISFORTUNE, visibility = AchievementVisibility.HIDDEN,
    ),

    // ---- Miscellaneous: interaction quirks that aren't about the dice or the scorecard --------
    COMMITMENT_ISSUES(
        "commitment_issues", "Commitment Issues",
        "Hold dice of one number, then change your mind and hold and score with another number",
        AchievementCategory.MISCELLANEOUS,
    ),
    DECISIONS_DECISIONS(
        "decisions_decisions", "Decisions, Decisions", "Hold and unhold the same die three times before rolling again",
        AchievementCategory.MISCELLANEOUS,
    ),
    TIME_TO_LET_IT_GO(
        "time_to_let_it_go", "Time To Let It Go", "Hold the same die through two rolls, then unhold it with none left to take",
        AchievementCategory.MISCELLANEOUS,
    ),
    TIME_WASTING(
        "time_wasting", "Time Wasting", "Hold then unhold each die in sequence",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    UNDO_DIFFERENT_CATEGORY(
        "undo_different_category", "I Didn't Mean That", "Undo a score and score a different category",
        AchievementCategory.MISCELLANEOUS,
    ),
    NOT_THOSE_DICE(
        "not_those_dice", "Not Those Dice!", "Tap the dice on the main menu",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    NO_MORE_ROLLS(
        "no_more_rolls", "No More Rolls", "Tap the dice cup three times after your last roll of a turn",
        AchievementCategory.MISCELLANEOUS,
    ),
    IMPATIENT(
        "impatient", "Impatient", "Finish a game never rolling more than once in any turn",
        AchievementCategory.MISCELLANEOUS,
    ),
    // Earned by having a human P2/P3/P4 named exactly "zodac" - the one name this checks for,
    // case-sensitively - never P1, who's always the human player at this device.
    BIG_FAN(
        "big_fan", "Big Fan", "Play a game with the creator",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
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
            // A band only ever gains scores, and career points only ever go up, so both climb
            // like any other running total.
            scoreBand != null || isCareerPoints -> ProgressStyle.CUMULATIVE
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
