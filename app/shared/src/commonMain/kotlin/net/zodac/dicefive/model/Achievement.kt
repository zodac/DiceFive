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
 * The longest an [Achievement.title] may be. The unlock banner shows a title on ONE line, shrunk to no
 * smaller than 12sp and never wrapped (every banner is the same size), so a title has to fit its
 * column at that size: 38 characters is the longest there is ("Where We're Going, We Don't Need
 * Rules"), which the banner's text column holds on a 360dp phone. Narrower screens ellipsise it
 * first. A longer title is a project-rule breach - shorten it; see CLAUDE.md. `AchievementTextTest`
 * enforces this.
 */
const val MAX_ACHIEVEMENT_TITLE_LENGTH = 38

/**
 * What an achievement is *about*, and the order those themes are shown in. Grouping the list this
 * way is what lets one ladder's rungs sit together - "Sharpshooter" then "High Roller" then "Dice
 * Deity" - instead of being scattered across an alphabetical list.
 *
 * [EASTER_EGGS] is last on purpose, and every achievement in it has [AchievementVisibility.SECRET]:
 * [AchievementsViewModel][net.zodac.dicefive.ui.achievements.AchievementsViewModel] already
 * filters a locked secret achievement out of the list entirely, so as long as this category holds
 * nothing else, that filtering is *also* what keeps its own section header from ever appearing
 * until something in it has actually been earned - no separate "is this section empty" check
 * needed in the screen itself.
 */
enum class AchievementCategory(val label: String) {
    MILESTONES("Milestones"),
    DICE("Dice feats"),
    SCORING("Scoring"),
    WINNING("Winning"),
    GAME_MODES("Game Modes"),
    MISFORTUNE("Misfortune"),
    MISCELLANEOUS("Miscellaneous"),
    COLLECTION("Collection"),
    EASTER_EGGS("Easter Eggs"),
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
 *
 * [AchievementCategory.MISCELLANEOUS] is exclusive with [AchievementVisibility.HIDDEN]: every
 * achievement with that visibility lives in that category, and everything in that category has
 * that visibility - `AchievementEngineTest` enforces both directions. A new hidden achievement
 * goes straight into Miscellaneous rather than its subject's usual category.
 *
 * [AchievementCategory.EASTER_EGGS] is the same pairing with [AchievementVisibility.SECRET], and
 * goes at the very end of this enum (after [COMPLETIONIST]) rather than filed under its subject's
 * usual category - see [AchievementCategory.EASTER_EGGS]'s own doc for why that placement is
 * load-bearing, not just tidiness. `AchievementEngineTest` enforces this exclusivity too.
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
     * anything that cannot currently be earned at all - see [NATURAL_INTELLIGENCE].
     */
    val countsTowardCompletion: Boolean = true,
    /** See [AchievementVisibility]. Defaults to fully visible, which is the vast majority. */
    val visibility: AchievementVisibility = AchievementVisibility.NORMAL,
    /**
     * Overrides [AchievementEngine][net.zodac.dicefive.game.AchievementEngine]'s default "announce
     * every quarter of [target]" progress-banner cadence with a fixed step size instead - a banner
     * fires every time the running total crosses a multiple of this many, regardless of how far
     * that is from [target]. `null` (the default) keeps the quarter-based cadence. For a target as
     * large as [DICE_10000]/[PROFESSIONAL_ROLLER]'s, quartering it would mean a banner only once
     * every several dozen (or several hundred) games.
     */
    val progressStepSize: Int? = null,
) {

    // ---- Milestones: simply playing the game -------------------------------------------------
    THE_JOURNEY_BEGINS(
        "journey_begins", "The Journey Begins", "Start your first game",
        AchievementCategory.MILESTONES,
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
        AchievementCategory.MILESTONES, AchievementCounter.DICE_ROLLED, target = 10_000, progressStepSize = 1_000,
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
        AchievementCategory.MILESTONES, target = 100_000, isCareerPoints = true, progressStepSize = 1_000,
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
        "lower_150", "Lower Class", "Score 150 or more in the lower section (excluding 5x scores)",
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
    HAT_TRICK_5X(
        "5x_hat_trick", "Hat Trick", "Score three or more 5x in a single game",
        AchievementCategory.DICE,
    ),

    // ---- Dice feats continued: interaction quirks, not just what the dice show ----------------
    TWICE_IN_A_LIFETIME(
        "5x_twice_in_a_row", "Twice In A Lifetime", "Score a 5x on two of your turns in a row",
        AchievementCategory.DICE,
    ),
    NATURAL_5X(
        "5x_natural", "Natural 5x", "Roll a 5x on the 2nd or 3rd roll without holding any dice",
        AchievementCategory.DICE,
    ),
    I_CAN_COUNT(
        "i_can_count", "I Can Count!", "Roll 1, 2, 3, 4, 5 in that order on the first roll of a turn",
        AchievementCategory.DICE,
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
    TON(
        "score_exactly_100", "Ton!", "Finish a game on exactly 100",
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
        AchievementCategory.WINNING,
    ),
    WIN_BY_100(
        "win_by_100", "Landslide", "Win by 100 points or more",
        AchievementCategory.WINNING,
    ),
    WIN_BY_5(
        "win_by_5", "Photo Finish", "Win by a single point",
        AchievementCategory.WINNING,
    ),
    TIE_BREAK(
        "tie_break", "Tie Break", "Win a game on a tie-break, after matching another player's score exactly",
        AchievementCategory.WINNING,
    ),
    COMEBACK(
        "comeback", "Comeback Kid", "Win after trailing at the start of the final round",
        AchievementCategory.WINNING,
    ),
    BEAT_THREE_AI(
        "beat_three_ai", "Last Human Standing", "Win a four-player game against three CPU players",
        AchievementCategory.WINNING,
    ),

    NATURAL_INTELLIGENCE(
        "natural_intelligence", "Natural Intelligence", "Win a four-player game against three Hard CPU players",
        AchievementCategory.WINNING,
    ),
    NATURALLY_GIFTED(
        "naturally_gifted", "Naturally Gifted", "Win a game never rolling more than once in any turn",
        AchievementCategory.WINNING,
    ),

    // ---- Game modes: playing beyond the Standard rules ------------------------------------------
    NON_STANDARD_MODE(
        "game_mode_non_standard", "Where We're Going, We Don't Need Rules", "Start a non-Standard game mode",
        AchievementCategory.GAME_MODES,
    ),
    TRICOLOUR_WIN(
        "tricolour_win", "Tricolourful", "Win a game of 'Tricolour' mode",
        AchievementCategory.GAME_MODES,
    ),
    // Judged mid-game, the moment the fourth of the four boxes goes in with a non-zero score - not
    // held back for the results screen.
    TRICOLOUR_ALL_COLOURS(
        "tricolour_all_colours", "Tricolour Me Impressed",
        "Score all 'Tricolour' mode scores (red, yellow, blue, coloured house) in one game",
        AchievementCategory.GAME_MODES,
    ),
    QUICKFIRE_WIN(
        "quickfire_win", "Quick On The Draw", "Win a game of 'Quickfire' mode",
        AchievementCategory.GAME_MODES,
    ),
    // Judged on the finished game's total. Not with extra rolls or Extended Scores' three boxes, which
    // make a total that high far easier.
    QUICKFIRE_SCORE(
        "quickfire_score_150", "Six Of The Best", "Score 150 or more in a game of 'Quickfire' mode",
        AchievementCategory.GAME_MODES,
    ),
    STUD_WIN(
        "stud_win", "Hold 'Em", "Win a game of 'Stud' mode",
        AchievementCategory.GAME_MODES,
    ),
    // Judged as the dice land, like the other roll feats - held dice count, so it's five held and the
    // last two rolled to match them, or any other way to get there.
    STUD_LUCKY_SEVEN(
        "stud_lucky_seven", "Lucky Seven", "Have all seven dice show the same number in 'Stud' mode",
        AchievementCategory.GAME_MODES,
    ),
    THIRD_WIND_WIN(
        "third_wind_win", "Gone With The Wind", "Win a game of 'Third Wind' mode",
        AchievementCategory.GAME_MODES,
    ),
    // Third Wind's own Spotless, which can't be earned there - every one of the 39 slots, no zeroes.
    THIRD_WIND_NO_ZEROES(
        "third_wind_no_zeroes", "Third Time's The Charm",
        "Fill all three slots of every category without a zero in 'Third Wind' mode",
        AchievementCategory.GAME_MODES,
    ),
    HIT_LIST_WIN(
        "hit_list_win", "Contract Fulfilled", "Win a game of 'Hit List' mode",
        AchievementCategory.GAME_MODES,
    ),
    // Judged mid-game, the moment the exact hit goes in its target - only a target with no any places counts.
    HIT_LIST_RIGHT_ON_TARGET(
        "hit_list_right_on_target", "Right On Target",
        "Score an exact hit on a target with all five numbers named in 'Hit List' mode",
        AchievementCategory.GAME_MODES,
    ),

    // ---- Misfortune: going badly, on purpose or otherwise --------------------------------------
    SCRATCHED_5X(
        "5x_scratched", "Scratched", "Take a zero in the 5x box",
        AchievementCategory.MISFORTUNE,
    ),
    DICE_HATE_ME(
        "dice_hate_me", "The Dice Hate Me", "Have a scoring option after the 2nd roll, then leave yourself with none after the 3rd",
        AchievementCategory.MISFORTUNE,
    ),
    ALMOST_FAMOUS(
        "almost_famous", "Almost Famous", "Hold a first-roll 4x all the way to the last roll, but never land the 5x",
        AchievementCategory.MISFORTUNE,
    ),
    I_ROBOT(
        "i_robot", "I, Robot", "Lose a game to a CPU player",
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
    // Judged at the commit: the last box on the card is an upper one, a single die in it would have earned the bonus, and all
    // the rolls went on a zero. Not with Unlucky Dice, which rigs the dice against you.
    PROBABILITY_NEVER_HEARD_OF_HER(
        "probability_never_heard_of_her", "Probability? Never Heard of Her",
        "Enter your final turn needing a single die to score your bonus, but fail",
        AchievementCategory.MISFORTUNE,
    ),
    // The turn timer forcing a category on you, not a bad roll - a different flavor of misfortune
    // than everything above it, so it sits last in the category rather than being slotted by rank.
    OUT_OF_TIME(
        "out_of_time", "Out Of Time", "Fail to score within the time limit",
        AchievementCategory.MISFORTUNE,
    ),

    // ---- Miscellaneous: every hidden achievement, whatever it's about --------------------------
    // This category is exclusive in both directions - see AchievementEngineTest - so it isn't
    // themed by subject the way the others are; entries below keep their original relative order
    // from whichever theme they moved out of, grouped by that origin for a paper trail.
    I_DID_IT_MY_WAY(
        "i_did_it_my_way", "I Did It My Way", "Customise a game before starting",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    COMMITMENT_ISSUES(
        "commitment_issues", "Commitment Issues",
        "Hold dice of one number, then change your mind and hold and score with another number",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    DECISIONS_DECISIONS(
        "decisions_decisions", "Decisions, Decisions", "Hold and unhold the same die three times before rolling again",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    TIME_TO_LET_IT_GO(
        "time_to_let_it_go", "Time To Let It Go", "Hold the same die after the 1st and 2nd roll, then score without using it",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    TIME_WASTING(
        "time_wasting", "Time Wasting", "Hold then unhold each die in sequence",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    UNDO_DIFFERENT_CATEGORY(
        "undo_different_category", "I Didn't Mean That", "Undo a score and score a different category",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    NO_MORE_ROLLS(
        "no_more_rolls", "No More Rolls", "Tap the dice cup three times after your last roll of a turn",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    IMPATIENT(
        "impatient", "Impatient", "Finish a game never rolling more than once in any turn",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    // Any game with the Turn Timer modifier on. Needs an opponent, like every win.
    LUCK_OF_THE_DRAW(
        "luck_of_the_draw", "Luck Of The Draw",
        "Win a game scoring 3 or fewer categories yourself - the turn timer did the rest",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),

    // Moved from Themes.
    FRESH_COAT_OF_PAINT(
        "fresh_coat_of_paint", "Fresh Coat Of Paint", "Play a game with any item using a non-default style",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),

    // Moved from Dice feats.
    // The worst full house there is - three 1s and two 2s specifically, never the joker rule's
    // five-of-a-kind bent into the box instead. Sits right above its opposite number, Fuller House.
    EMPTY_HOUSE(
        "empty_house", "Empty House", "Score the worst Full House (three 1s and two 2s)",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    // The best full house there is - three 6s and two 5s specifically, never the joker rule's
    // five-of-a-kind bent into the box instead. Sits right above House Call, the more general
    // "any full house, first roll" feat.
    FULLER_HOUSE(
        "fuller_house", "Fuller House", "Score the best Full House (three 6s and two 5s)",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    // The three "straight out of the cup" feats, in ascending order of how unlikely they are on a
    // single throw of five dice: a full house is 300 of the 7776 outcomes, a large straight 240,
    // and five of a kind just 6.
    FIRST_ROLL_FULL_HOUSE(
        "first_roll_full_house", "House Call", "Roll a full house on the first roll of a turn",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    FIRST_ROLL_LARGE_STRAIGHT(
        "first_roll_large_straight", "Straight Away", "Roll a large straight on the first roll of a turn",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    FIRST_ROLL_5X(
        "5x_first_roll", "Five on the Fly", "Roll a 5x on the first roll of a turn",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    SIXES_30(
        "sixes_30", "Six Appeal", "Score the maximum 30 in Sixes",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    CHANCE_30(
        "chance_30", "Taking A Chance", "Score the maximum 30 in Chance",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    DEJA_VU(
        "deja_vu", "Déjà Vu", "Roll the exact same result twice in a row, without holding any dice in between",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    // Sits right after Déjà Vu, its complement: this is the *some dice held* case of the same
    // "the dice landed the same twice" idea, where Déjà Vu is specifically the *nothing held* one.
    LOADED_DICE(
        "loaded_dice", "Are These Loaded Dice?", "After holding some dice, have the rest come up exactly the same on both re-rolls",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    PRODUCT_PLACEMENT(
        "product_placement", "Product Placement", "Roll/hold 2, 4, 5, 3, 6 - the exact dice on the main menu, in that order",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    POINTLESS_ROLL(
        "pointless_roll", "What Was The Point Of That?", "Hold all five dice, then roll anyway",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),

    // Moved from Scoring.
    NICE(
        "score_exactly_69", "Nice", "Finish a game on exactly 69",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),

    // Moved from Winning.
    ZERO_TO_HERO(
        "zero_to_hero", "Zero To Hero", "Win a game after scoring zero at least three times",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),

    // Moved from Misfortune.
    // Harder and more specific than Scratched: that one just needs a zero sitting in the 5x box
    // (from dice that never matched at all), this needs the dice to have genuinely been a 5x at
    // the moment a zero was committed anyway.
    WASTED_5X(
        "5x_wasted", "Wasted Fortune", "Roll a 5x but score a zero with it anyway",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    WHY_DID_YOU_DO_THAT(
        "why_did_you_do_that", "Size Isn't Everything", "Score the small straight when the large straight was also available",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    ALL_ZEROES(
        "zeroes_except_chance", "How Do You Play This Game?",
        "Score zero in every category except Chance",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    EXTREME_LOW_ROLLS(
        "low_rolls_extreme", "Rock Bottom", "Finish a game on exactly 5 - the lowest score the rules allow",
        AchievementCategory.MISCELLANEOUS, visibility = AchievementVisibility.HIDDEN,
    ),
    WHO_MADE_THIS(
        "who_made_this", "Who Made This?", "Open the About page",
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

    // ---- Easter Eggs: not shown, let alone attempted, until already done ----------------------
    // See AchievementCategory.EASTER_EGGS's doc for why this category exists (rather than filing
    // these under each one's subject-matter category, the way Big Fan used to sit in
    // Miscellaneous) - it's what keeps the "Easter Eggs" section header itself from ever
    // appearing while empty.
    //
    // Unlike every other category, these run alphabetically by title rather than easiest-first -
    // there's no ladder among easter eggs to keep in order. AchievementEngineTest holds them to it.
    //
    // Excluded from COMPLETIONIST the same way NATURAL_INTELLIGENCE was while it couldn't be
    // earned - naming yourself after the creator, or a human P2/P3/P4 named exactly "zodac"
    // (case-sensitive, never P1, who's always the human player at this device), isn't something
    // every player could reasonably be expected to stumble into on the way to 100%.
    BIG_FAN(
        "big_fan", "Big Fan", "Play a 1v1 game against the creator",
        AchievementCategory.EASTER_EGGS, countsTowardCompletion = false, visibility = AchievementVisibility.SECRET,
    ),
    // Excluded from COMPLETIONIST like the rest of this category - nothing hints that the
    // Flowerpot grows a plant, let alone that using every roll of every turn brings it into bloom.
    // Never in a mode without 3 rolls a turn, where it can't bloom at all.
    GREENFINGERS(
        "greenfingers", "Greenfingers", "Grow a Sunflower",
        AchievementCategory.EASTER_EGGS, countsTowardCompletion = false, visibility = AchievementVisibility.SECRET,
    ),
    // Excluded from COMPLETIONIST the same way Big Fan is - naming yourself after a country to
    // re-skin a game mode's dice isn't something every player could reasonably be expected to
    // stumble into on the way to 100%.
    LUCK_OF_THE_IRISH(
        "luck_of_the_irish", "Luck of the Irish", "Play a game of Tricolour as Ireland/Éire",
        AchievementCategory.EASTER_EGGS, countsTowardCompletion = false, visibility = AchievementVisibility.SECRET,
    ),
    // Excluded from COMPLETIONIST like the rest of this category - nothing hints that the menu's logo
    // dice can be tapped, let alone that they roll. Its id is unchanged from when it sat in
    // Miscellaneous as a hidden achievement, so an earlier unlock still counts.
    NOT_THOSE_DICE(
        "not_those_dice", "Not Those Dice!", "Tap the dice on the main menu",
        AchievementCategory.EASTER_EGGS, countsTowardCompletion = false, visibility = AchievementVisibility.SECRET,
    ),
    // Excluded from COMPLETIONIST the same way Big Fan/Luck of the Irish are - shaking the phone to
    // roll isn't something every player could reasonably be expected to stumble into on the way to
    // 100%, since nothing in the UI hints it's possible.
    SHAKEN_NOT_TAPPED(
        "shaken_not_tapped", "Shaken, Not Tapped", "Shake your phone to roll the dice",
        AchievementCategory.EASTER_EGGS, countsTowardCompletion = false, visibility = AchievementVisibility.SECRET,
    ),
    // Excluded from COMPLETIONIST like the rest of this category - it needs the Top Hat cup picked
    // and the table left alone mid-turn, which nothing in the UI hints at.
    MAGICIANS_SECRET(
        "magicians_secret", "The Magician's Secret", "Find where Luna is hiding",
        AchievementCategory.EASTER_EGGS, countsTowardCompletion = false, visibility = AchievementVisibility.SECRET,
    ),
    // Excluded from COMPLETIONIST like the rest of this category - nothing hints that a player
    // name means anything, let alone this one. Standard mode only (not a re-skinned or shortened
    // card) and solo only, so it can't be had by beating anybody, and with no modifiers on.
    THE_SOLUTION(
        "the_solution", "The Solution", "Play a solo standard game, no modifiers, as Phil Woodward and score exactly 255 points",
        AchievementCategory.EASTER_EGGS, countsTowardCompletion = false, visibility = AchievementVisibility.SECRET,
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
