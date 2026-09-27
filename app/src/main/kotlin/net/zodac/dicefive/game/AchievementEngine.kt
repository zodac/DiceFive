package net.zodac.dicefive.game

import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCategory
import net.zodac.dicefive.model.AchievementCounter
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ProgressStyle
import net.zodac.dicefive.model.ScoreCategory

/**
 * The parts of a finished game that aren't recoverable from its final [GameState], collected live
 * by `GameViewModel` while the game is played.
 */
data class GameAchievementContext(
    /** Player 1's own best leaderboard score (by name) *before* this game's rows were inserted, or
     * null if they hadn't recorded one yet - see `ScoreRepository.bestScoreForPlayer`. */
    val previousBestScore: Int? = null,
    /** Whether a human went into their final turn behind every other player. */
    val trailedIntoFinalRound: Boolean = false,
    /** Whether a human went into their final turn AHEAD of every other player - [JAWS_OF_VICTORY]'s
     * precondition, the mirror image of [trailedIntoFinalRound]. */
    val ledIntoFinalRound: Boolean = false,
    /** How many dice player 1 actually re-rolled across the game. Nobody else's rolls count -
     * see [AchievementEngine]'s class doc. */
    val diceRolledByPlayerOne: Int = 0,
    /**
     * Whether player 1 rolled more than once on at least one of their own turns this game -
     * [Achievement.IMPATIENT]/[Achievement.NATURALLY_GIFTED] need this false, i.e. every one of
     * their turns was a single roll.
     */
    val playerOneTookExtraRoll: Boolean = false,
    /**
     * What the leaderboard said *before* this game's rows were inserted. Read pre-insert for the
     * same reason [previousBestScore] is, and because the engine adds this game's own human totals
     * itself - that way one read gives it both the before and after state of everything measured
     * against the board.
     */
    val previousLeaderboard: LeaderboardTotals = LeaderboardTotals(),
)

/**
 * What's already decided the moment a game begins, before a single die is rolled - today, just
 * which table-art style is in play, but the natural home for anything else `GameViewModel` can
 * only answer by reading outside the [GameState] itself (settings, persisted history, ...) at
 * `startGame`/`resumeGame` time. See [AchievementEngine.evaluateAtGameStart].
 */
data class GameStartContext(
    /**
     * Whether any of the four independently swappable table-art styles (dice, dice cup, mat,
     * background) in effect for this game is something other than that category's shipped default
     * (`ui.game.style`'s per-category `default`) - a single boolean, not a style id, so this pure
     * engine never has to import the UI-layer style catalog just to compare a string.
     * [Achievement.FRESH_COAT_OF_PAINT]'s trigger.
     */
    val playedNonDefaultStyle: Boolean = false,
    /** Whether this is a two-player game where P2 specifically - never P1, and never a 3P/4P
     * game's P2 - is a human named exactly "zodac" (case-sensitive) - [Achievement.BIG_FAN]'s
     * trigger. */
    val hasHumanPlayerNamedZodac: Boolean = false,
    /** [net.zodac.dicefive.model.isLuckOfTheIrish] - [Achievement.LUCK_OF_THE_IRISH]'s trigger. */
    val hasIrishPlayerOneInTricolour: Boolean = false,
    /** Whether this game was started with any setup option changed from the app's own default -
     * the turn timer (`turnTimer != TurnTimer.NONE`) or the game mode (anything but
     * [GameMode.default]); extend this as later setup options gain their own default worth
     * deviating from - [Achievement.I_DID_IT_MY_WAY]'s trigger. */
    val customizedGameSettings: Boolean = false,
    /** The rules this game is played under - [Achievement.NON_STANDARD_MODE]'s trigger. */
    val gameMode: GameMode = GameMode.default,
)

/**
 * The parts of the leaderboard that achievements are scored against, for the ones measured by what
 * has actually been recorded rather than by a stored counter: the score bands, and career points.
 *
 * The two fields are deliberately scoped differently - see [AchievementEngine]'s class doc:
 * [distinctScores] is every human's score, device-wide, but [totalPoints] is player 1's alone.
 */
data class LeaderboardTotals(
    val distinctScores: Set<Int> = emptySet(),
    val totalPoints: Int = 0,
) {
    /**
     * The same totals with a finished game folded in - what the board will say once it's saved.
     * [allHumanScores] (every human at the table) feeds [distinctScores]; [primaryPlayerScore]
     * (player 1 alone) feeds [totalPoints] - passed separately, not derived from one list, because
     * conflating them is exactly the bug this split guards against (see git history: career points
     * used to count every human at the table, not just the one whose achievement it is).
     */
    fun plusGame(allHumanScores: List<Int>, primaryPlayerScore: Int) = LeaderboardTotals(
        distinctScores = distinctScores + allHumanScores,
        totalPoints = totalPoints + primaryPlayerScore,
    )
}

/** One "getting closer" banner: [achievement] moved from [previous] to [current] out of its
 * target - both are carried (not just [current]) so the banner can animate the count climbing
 * rather than just snapping to the new value. */
data class AchievementProgress(val achievement: Achievement, val previous: Int, val current: Int)

/** What a single evaluation changed. Empty on every count when nothing happened. */
data class AchievementUpdate(
    val newlyUnlocked: List<Achievement> = emptyList(),
    val counters: Map<AchievementCounter, Int> = emptyMap(),
    val progressed: List<AchievementProgress> = emptyList(),
    val unlockedAtMillis: Long = 0L,
) {
    val isEmpty: Boolean get() = newlyUnlocked.isEmpty() && counters.isEmpty() && progressed.isEmpty()

    fun unlockedAt(): Map<Achievement, Long> = newlyUnlocked.associateWith { unlockedAtMillis }
}

/**
 * Decides which achievements a finished game earned. Pure: no Android APIs, no persistence, no
 * clock of its own - `GameViewModel` reads the stored state, calls this, and writes the result
 * back, exactly as it does with [GameEngine] for the game itself.
 *
 * Two rules run through all of it:
 *  - **Player 1 only, not any human.** `state.players[0]` - the human player at this device, always
 *    HUMAN - is the only seat whose turns and scorecard earn achievements; every other seat,
 *    human or AI, is only ever the opposition. The one exception is the score-collection ledger at
 *    the tail of [AchievementCategory.COLLECTION] ([LeaderboardTotals.distinctScores]), which stays
 *    measured against the leaderboard as a whole (every human's score, not just player 1's), since
 *    that's what the Leaderboard screen itself shows. Career points
 *    ([LeaderboardTotals.totalPoints], [Achievement.PROFESSIONAL_ROLLER]) are NOT part of that
 *    exception, despite living on the same [LeaderboardTotals] - its own description says "your
 *    games", so it follows the player-1-only rule like everything else.
 *  - **Superuser mode still earns.** Hand-setting dice used to disqualify a game outright, which
 *    made the debug cheat useless for testing the very thing it was best placed to test. It also
 *    protected nobody: superuser mode is gated on `BuildConfig.DEBUG`, so a release build cannot
 *    reach it at all.
 */
object AchievementEngine {

    private const val UPPER_CLASS_THRESHOLD = 84

    /** Out of a 235-point maximum across the lower boxes, so a high bar without being the ceiling. */
    private const val LOWER_CLASS_THRESHOLD = 150
    private const val NICE_SCORE = 69
    private const val TON_SCORE = 100
    private const val DOUBLE_TON_SCORE = 200
    private const val TRIPLE_TON_SCORE = 300
    private const val LANDSLIDE_MARGIN = 100
    private const val PHOTO_FINISH_MARGIN = 1
    private const val PIPPED_MARGIN = 1
    private const val ZEROES_FOR_HERO = 3
    private const val COLD_DICE_SCORE = 100
    private const val LOW_ROLLS_SCORE = 20

    /**
     * The lowest total the rules permit. Legal, if perverse: five 1s taken as Chance scores 5,
     * and every other box can be zeroed deliberately.
     */
    private const val LOWEST_POSSIBLE_SCORE = 5

    private const val MAX_CHANCE = 30
    private const val MAX_SIXES = 30
    private const val FULL_TABLE_SIZE = 4

    /** Every box [Achievement.TRICOLOUR_ALL_COLOURS] needs a non-zero score in. */
    private val TRICOLOUR_SET = listOf(
        ScoreCategory.REDS,
        ScoreCategory.YELLOWS,
        ScoreCategory.BLUES,
        ScoreCategory.COLOURED_HOUSE,
    )

    /** How finely a [ProgressStyle.CUMULATIVE] achievement announces itself: quarter by quarter. */
    private const val PROGRESS_MILESTONES = 4

    /**
     * Everything a finished [state] earned, given what was already stored in [before].
     *
     * [now] is passed in rather than read from the clock so the caller owns the timestamp and
     * tests stay deterministic.
     */
    fun evaluate(
        state: GameState,
        context: GameAchievementContext,
        before: AchievementsState,
        now: Long,
    ): AchievementUpdate {
        if (!state.isGameOver) return AchievementUpdate()

        // Player 1 only - see the class doc's first rule. The score-collection ledger is the one
        // exception: it's measured against the leaderboard as a whole, which already carries every
        // human's score - career points are NOT part of that exception, see plusGame below.
        if (state.players.firstOrNull()?.type != PlayerType.HUMAN) return AchievementUpdate()
        val humans = listOf(state.players.first())
        val allHumans = state.players.filter { it.type == PlayerType.HUMAN }

        val leaderboardBefore = context.previousLeaderboard
        val leaderboardAfter = leaderboardBefore.plusGame(allHumans.map { it.totalScore }, humans.first().totalScore)

        val counters = countersAfter(state, humans, context, before)
        val earned = earnedBy(state, humans, context, counters, leaderboardAfter)
        return update(earned, counters, before, now, leaderboardBefore, leaderboardAfter)
    }

    /**
     * What a game **in progress** has already earned - called after every scored category, so
     * "Six Appeal" lands the moment the 30 goes in the box rather than waiting for the results
     * screen. Only judges what a later turn cannot undo (see [earnedDuringPlay]); counters and
     * progress banners are left entirely to [evaluate], keeping the running totals a
     * once-per-finished-game affair that an undo can't inflate.
     */
    fun evaluateInProgress(
        state: GameState,
        before: AchievementsState,
        now: Long,
    ): AchievementUpdate {
        if (state.isGameOver) return AchievementUpdate()

        // Player 1 only - see the class doc's first rule.
        if (state.players.firstOrNull()?.type != PlayerType.HUMAN) return AchievementUpdate()
        val humans = listOf(state.players.first())

        return update(earnedDuringPlay(state.players, humans), emptyMap(), before, now)
    }

    /**
     * Achievements earned at a specific moment rather than off the back of a scorecard - today,
     * the three feats rolled straight out of the cup. Counters are untouched.
     */
    fun unlockNow(achievements: Set<Achievement>, before: AchievementsState, now: Long): AchievementUpdate =
        update(achievements, before.counters, before, now)

    /**
     * Everything [context] already earns before a single die is rolled. Add a new game-start
     * achievement here - and a field on [GameStartContext] for whatever `GameViewModel` needs to
     * answer it - rather than deciding it ad hoc at the call site: this is the one place "what
     * counts as earned" is decided, same as [evaluate]/[evaluateInProgress] for the rest of a game.
     * Counters are untouched, same as [unlockNow] - nothing here is a running total.
     */
    fun evaluateAtGameStart(context: GameStartContext, before: AchievementsState, now: Long): AchievementUpdate {
        val earned = buildSet {
            // Unconditional: every game start satisfies it, but `update()` only ever reports it
            // as newly unlocked once, which is exactly "the very first time" means here.
            add(Achievement.THE_JOURNEY_BEGINS)
            if (context.playedNonDefaultStyle) add(Achievement.FRESH_COAT_OF_PAINT)
            if (context.hasHumanPlayerNamedZodac) add(Achievement.BIG_FAN)
            if (context.hasIrishPlayerOneInTricolour) add(Achievement.LUCK_OF_THE_IRISH)
            if (context.customizedGameSettings) add(Achievement.I_DID_IT_MY_WAY)
            if (context.gameMode != GameMode.STANDARD) add(Achievement.NON_STANDARD_MODE)
        }
        return update(earned, before.counters, before, now)
    }

    /**
     * How far along [achievement] is, for the progress bar on the achievements screen. Counter
     * achievements read their stored total; score-band ones are measured against the leaderboard
     * itself, which is why [distinctScores] has to be passed in rather than stored.
     */
    fun progressOf(
        achievement: Achievement,
        counters: Map<AchievementCounter, Int>,
        leaderboard: LeaderboardTotals,
    ): Int = when {
        achievement.scoreBand != null -> leaderboard.distinctScores.count { it in achievement.scoreBand }
        achievement.isCareerPoints -> leaderboard.totalPoints.coerceAtMost(achievement.target)
        achievement.counter != null -> (counters[achievement.counter] ?: 0).coerceAtMost(achievement.target)
        else -> 0
    }

    private fun countersAfter(
        state: GameState,
        humans: List<PlayerState>,
        context: GameAchievementContext,
        before: AchievementsState,
    ): Map<AchievementCounter, Int> {
        val multiplayer = state.players.size > 1
        val humanWon = humanWon(state, humans)
        val fiveOfAKindsThisGame = humans.sumOf { it.fiveOfAKindCount }

        return mapOf(
            AchievementCounter.GAMES_PLAYED to before.counter(AchievementCounter.GAMES_PLAYED) + 1,
            AchievementCounter.SCORED_5X to before.counter(AchievementCounter.SCORED_5X) + fiveOfAKindsThisGame,
            AchievementCounter.DICE_ROLLED to before.counter(AchievementCounter.DICE_ROLLED) + context.diceRolledByPlayerOne,
            AchievementCounter.GAMES_WON to before.counter(AchievementCounter.GAMES_WON) +
                if (multiplayer && humanWon) 1 else 0,
            // A solo game has nobody to beat, so it neither extends nor breaks a streak.
            AchievementCounter.WIN_STREAK to when {
                !multiplayer -> before.counter(AchievementCounter.WIN_STREAK)
                humanWon -> before.counter(AchievementCounter.WIN_STREAK) + 1
                else -> 0
            },
        )
    }

    /**
     * The achievements that can be judged from a scorecard that is still being filled in, because
     * nothing that happens later can take them away: a box already holds 30, or a bonus is
     * already banked.
     *
     * Deliberately NOT any score-total threshold, even one a running total could only ever grow
     * past ([SCORE_200][Achievement.SCORE_200] and friends): leaving a game in progress records
     * nothing on the leaderboard, so unlocking here off a total that's about to vanish would leave
     * an achievement earned with no matching score anywhere to show for it. Those wait for
     * [earnedBy] instead, same as everything else that needs an actual result - "Spotless" needs a
     * complete card, "Cold Dice" needs a score that can no longer climb, and anything about
     * winning needs a result.
     */
    private fun earnedDuringPlay(players: List<PlayerState>, humans: List<PlayerState>): Set<Achievement> {
        val earned = mutableSetOf<Achievement>()

        fun award(achievement: Achievement, condition: Boolean) {
            if (condition) earned += achievement
        }

        fun anyHuman(predicate: (PlayerState) -> Boolean) = humans.any(predicate)

        // Dice feats.
        award(Achievement.FIRST_5X, anyHuman { it.fiveOfAKindCount > 0 })
        award(Achievement.ENCORE_5X, anyHuman { it.fiveOfAKindBonusCount >= 1 })
        award(Achievement.HAT_TRICK_5X, anyHuman { it.fiveOfAKindBonusCount >= 2 })
        award(
            Achievement.BOTH_STRAIGHTS,
            anyHuman { it.scored(ScoreCategory.SMALL_STRAIGHT) && it.scored(ScoreCategory.LARGE_STRAIGHT) },
        )
        award(Achievement.CHANCE_30, anyHuman { it.scorecard[ScoreCategory.CHANCE] == MAX_CHANCE })
        award(Achievement.SIXES_30, anyHuman { it.scorecard[ScoreCategory.SIXES] == MAX_SIXES })
        award(Achievement.UPPER_BONUS, anyHuman { it.upperSectionBonus > 0 })
        award(Achievement.UPPER_84, anyHuman { it.upperSectionTotal >= UPPER_CLASS_THRESHOLD })
        award(Achievement.LOWER_150, anyHuman { it.lowerSectionTotal >= LOWER_CLASS_THRESHOLD })
        award(Achievement.SCRATCHED_5X, anyHuman { it.scorecard[ScoreCategory.FIVE_OF_A_KIND] == 0 })

        // Game modes. Only a Tricolour scorecard has these boxes at all, so no separate mode check.
        award(Achievement.TRICOLOUR_ALL_COLOURS, anyHuman { player -> TRICOLOUR_SET.all { player.scored(it) } })

        // Known the moment the table is set.
        award(Achievement.FULL_TABLE, players.size == FULL_TABLE_SIZE)

        return earned
    }

    private fun earnedBy(
        state: GameState,
        humans: List<PlayerState>,
        context: GameAchievementContext,
        counters: Map<AchievementCounter, Int>,
        leaderboard: LeaderboardTotals,
    ): Set<Achievement> {
        val players = state.players
        val earned = earnedDuringPlay(players, humans).toMutableSet()
        val multiplayer = players.size > 1
        val humanWon = humanWon(state, humans)
        val bestHumanScore = humans.maxOf { it.totalScore }
        val margin = winningMargin(players, humans)
        val aiPlayers = players.filter { it.type == PlayerType.AI }

        fun award(achievement: Achievement, condition: Boolean) {
            if (condition) earned += achievement
        }

        fun anyHuman(predicate: (PlayerState) -> Boolean) = humans.any(predicate)

        // Counter-driven: earned the moment the running total reaches the target.
        for (achievement in Achievement.entries) {
            val counter = achievement.counter ?: continue
            award(achievement, (counters[counter] ?: 0) >= achievement.target)
        }

        award(Achievement.NO_ZEROES, anyHuman { player -> player.scorecard.values.none { it == 0 } })
        award(Achievement.PERSONAL_BEST, context.previousBestScore != null && bestHumanScore > context.previousBestScore)

        // Score thresholds - see earnedDuringPlay's doc comment for why these wait for the actual
        // result rather than firing off a total that's already passed the mark mid-game.
        // SCORE_200/SCORE_300 are strictly greater-than - see their doc comments in Achievement.kt
        // for why they're kept disjoint from DOUBLE_TON/TRIPLE_TON's exact thresholds.
        award(Achievement.SCORE_200, bestHumanScore > 200)
        award(Achievement.SCORE_300, bestHumanScore > 300)
        award(Achievement.SCORE_400, bestHumanScore >= 400)
        award(Achievement.SCORE_500, bestHumanScore >= 500)
        award(Achievement.CHEATER_CHEATER, bestHumanScore >= state.gameMode.maxPossibleScore)

        // Winning.
        award(Achievement.WIN_BY_100, multiplayer && humanWon && margin != null && margin >= LANDSLIDE_MARGIN)
        award(Achievement.WIN_BY_5, multiplayer && humanWon && margin == PHOTO_FINISH_MARGIN)
        award(Achievement.COMEBACK, multiplayer && humanWon && context.trailedIntoFinalRound)
        award(
            Achievement.ZERO_TO_HERO,
            multiplayer && humanWon && humans.any { it.totalScore == state.topScore && it.scorecard.values.count { v -> v == 0 } >= ZEROES_FOR_HERO },
        )
        award(Achievement.TRICOLOUR_WIN, multiplayer && humanWon && state.gameMode == GameMode.TRICOLOUR)
        award(Achievement.PIPPED_TO_THE_POST, multiplayer && !humanWon && state.topScore - bestHumanScore == PIPPED_MARGIN)
        award(Achievement.JAWS_OF_VICTORY, multiplayer && !humanWon && context.ledIntoFinalRound)
        award(
            Achievement.BEAT_THREE_AI,
            humanWon && players.size == FULL_TABLE_SIZE && aiPlayers.size == FULL_TABLE_SIZE - 1,
        )
        award(
            Achievement.I_ROBOT,
            humanWon && players.size == FULL_TABLE_SIZE && aiPlayers.size == FULL_TABLE_SIZE - 1 &&
                aiPlayers.all { it.difficulty == Difficulty.HARD },
        )

        // Misfortune that only a finished score can settle.
        award(Achievement.SCORE_UNDER_100, anyHuman { it.totalScore < COLD_DICE_SCORE })
        award(Achievement.LOW_ROLLS, anyHuman { it.totalScore < LOW_ROLLS_SCORE })
        // Chance is excluded because it cannot be zeroed - five dice always sum to at least 5.
        award(
            Achievement.ALL_ZEROES,
            anyHuman { player ->
                player.gameMode.categories.filter { it != ScoreCategory.CHANCE }.all { player.scorecard[it] == 0 }
            },
        )
        award(Achievement.EXTREME_LOW_ROLLS, anyHuman { it.totalScore == LOWEST_POSSIBLE_SCORE })
        // "Lose a game to a CPU player" specifically - not just any loss. Losing to another human seat is
        // still a loss (PIPPED_TO_THE_POST/JAWS_OF_VICTORY don't care who won), but with only
        // player 1 earning achievements now, `!humanWon` alone would also fire whenever another
        // human player at the table beat player 1, which isn't what this achievement means.
        val aiWon = players.any { it.type == PlayerType.AI && it.totalScore == state.topScore }
        award(Achievement.SINGULARITY, multiplayer && !humanWon && aiWon)

        award(Achievement.SOLO_GAME, players.size == 1)

        // Impatient/Naturally Gifted: player 1 took every one of their own turns on a single roll.
        val playerOneNeverRolledTwice = !context.playerOneTookExtraRoll
        award(Achievement.IMPATIENT, playerOneNeverRolledTwice)
        award(
            Achievement.NATURALLY_GIFTED,
            multiplayer && playerOneNeverRolledTwice && humans.first().totalScore == state.topScore,
        )

        // Exactly 69/100/200/300 - thresholds the running total can overshoot, so they can only be
        // judged now, unlike the 200-or-more/300-or-more rungs right next to them on the ladder.
        award(Achievement.NICE, anyHuman { it.totalScore == NICE_SCORE })
        award(Achievement.TON, anyHuman { it.totalScore == TON_SCORE })
        award(Achievement.DOUBLE_TON, anyHuman { it.totalScore == DOUBLE_TON_SCORE })
        award(Achievement.TRIPLE_TON, anyHuman { it.totalScore == TRIPLE_TON_SCORE })

        // The upper section filled with exactly the matching pip count in every box, in this one
        // game - can only be judged once every upper box is actually filled in.
        award(Achievement.EXACT_CHANGE, anyHuman { it.matchesExactUpperLadder() })

        // Score collection: every single score in the band has to have been recorded at least once.
        for (achievement in Achievement.entries) {
            val band = achievement.scoreBand ?: continue
            award(achievement, band.all { it in leaderboard.distinctScores })
        }
        award(Achievement.PROFESSIONAL_ROLLER, leaderboard.totalPoints >= Achievement.PROFESSIONAL_ROLLER.target)

        return earned
    }

    /**
     * Turns a set of satisfied achievements into the delta to store and announce: drops the ones
     * already unlocked, cascades [Achievement.COMPLETIONIST], and works out which locked
     * achievements moved far enough to be worth a progress banner.
     */
    private fun update(
        earned: Set<Achievement>,
        counters: Map<AchievementCounter, Int>,
        before: AchievementsState,
        now: Long,
        leaderboardBefore: LeaderboardTotals = LeaderboardTotals(),
        leaderboardAfter: LeaderboardTotals = LeaderboardTotals(),
    ): AchievementUpdate {
        // Catalogue order, so a burst of banners always arrives in the same, grouped order.
        val newlyUnlocked = Achievement.entries
            .filter { it in earned && !before.isUnlocked(it) }
            .toMutableList()

        val unlockedAfter = before.unlockedAt.keys + newlyUnlocked
        // Completionist can also arrive here as a direct target of its own (e.g. superuser force-
        // unlocking it specifically) rather than only as a cascade from some other achievement - the
        // "in earned and not yet unlocked" filter above already added it in that case, so the
        // cascade check must not add it again and double the unlock event.
        if (Achievement.COMPLETIONIST !in newlyUnlocked &&
            !before.isUnlocked(Achievement.COMPLETIONIST) &&
            unlockedAfter.containsAll(Achievement.COMPLETION_REQUIREMENTS)
        ) {
            newlyUnlocked += Achievement.COMPLETIONIST
        }

        val progressed = Achievement.entries.mapNotNull { achievement ->
            progressEvent(achievement, counters, before, newlyUnlocked, leaderboardBefore, leaderboardAfter)
        }

        return AchievementUpdate(
            newlyUnlocked = newlyUnlocked,
            counters = counters,
            progressed = progressed,
            unlockedAtMillis = now,
        )
    }

    private fun progressEvent(
        achievement: Achievement,
        counters: Map<AchievementCounter, Int>,
        before: AchievementsState,
        newlyUnlocked: List<Achievement>,
        leaderboardBefore: LeaderboardTotals,
        leaderboardAfter: LeaderboardTotals,
    ): AchievementProgress? {
        if (!achievement.hasProgressBar) return null
        // Nothing to nudge someone towards once they've got there - the unlock banner says it all.
        if (before.isUnlocked(achievement) || achievement in newlyUnlocked) return null

        val previous: Int
        val current: Int
        when {
            achievement.scoreBand != null || achievement.isCareerPoints -> {
                previous = progressOf(achievement, counters, leaderboardBefore)
                current = progressOf(achievement, counters, leaderboardAfter)
            }

            achievement.counter != null -> {
                previous = before.progress(achievement)
                current = progressOf(achievement, counters, leaderboardAfter)
            }

            else -> return null
        }
        if (current <= previous) return null

        val worthShowing = when (achievement.progressStyle) {
            // A streak is fragile and slow to build, so every single step forward is news.
            ProgressStyle.STREAK -> true
            // A total that only climbs would otherwise pop a banner every single game.
            ProgressStyle.CUMULATIVE -> milestone(previous, achievement.target) != milestone(current, achievement.target)
            ProgressStyle.NONE -> false
        }
        return if (worthShowing) AchievementProgress(achievement, previous, current) else null
    }

    private fun milestone(value: Int, target: Int): Int = value * PROGRESS_MILESTONES / target

    /** A tie at the top counts as a win for the human - nobody beat them. */
    private fun humanWon(state: GameState, humans: List<PlayerState>): Boolean =
        humans.any { it.totalScore == state.topScore }

    /**
     * How far the leading human finished ahead of the next player along, or null in a solo game.
     * Negative if they lost, which is why every caller also checks they actually won.
     */
    private fun winningMargin(players: List<PlayerState>, humans: List<PlayerState>): Int? {
        val topHuman = humans.maxByOrNull { it.totalScore } ?: return null
        val others = players.toMutableList().apply { remove(topHuman) }
        val bestOther = others.maxOfOrNull { it.totalScore } ?: return null
        return topHuman.totalScore - bestOther
    }

    private fun PlayerState.scored(category: ScoreCategory): Boolean = (scorecard[category] ?: 0) > 0

    /** ONES holds exactly 1, TWOS exactly 2, ... SIXES exactly 6 - [PlayerState.UPPER_CATEGORIES]
     * is already declared in that order, so its index doubles as the target value. */
    private fun PlayerState.matchesExactUpperLadder(): Boolean =
        PlayerState.UPPER_CATEGORIES.withIndex().all { (index, category) -> scorecard[category] == index + 1 }
}
