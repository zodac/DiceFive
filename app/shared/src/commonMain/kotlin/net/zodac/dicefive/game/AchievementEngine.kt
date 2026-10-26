package net.zodac.dicefive.game

import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.achievements.countsTowardStyleLocks
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
import net.zodac.dicefive.model.TurnTimer
import net.zodac.dicefive.model.hasGrownSunflower
import net.zodac.dicefive.model.isPhilWoodward

/**
 * The parts of a finished game that aren't recoverable from its final [GameState], collected live
 * by [AchievementTracker] while the game is played.
 */
data class GameAchievementContext(
    /** Player 1's own best leaderboard score (by name) *before* this game's rows were inserted, or
     * null if they hadn't recorded one yet - see `ScoreRepository.bestScoreForPlayer`. */
    val previousBestScore: Int? = null,
    /** Whether a human went into their final turn behind every other player. */
    val trailedIntoFinalRound: Boolean = false,
    /** Whether a human went into their final turn AHEAD of every other player - [DEFEAT_FROM_THE_JAWS_OF_VICTORY]'s
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
     * How many of player 1's own turns the turn timer ran out on (and so scored for them) this game.
     * A turn timed out before it was rolled scores a zero with no roll at all, so the achievements for
     * a low or zeroed card - and Impatient's single rolls - need this to be 0: letting the clock run
     * down would otherwise earn them for nothing.
     */
    val playerOneTimeouts: Int = 0,
    /**
     * The box of each zero the turn timer scored for player 1 this game, once per zero: the zeroes
     * [Achievement.ZERO_TO_HERO] and [Achievement.SCRATCHED] don't count, as player 1 didn't take them.
     */
    val playerOneTimedOutZeroes: List<ScoreCategory> = emptyList(),
    /**
     * What the leaderboard said *before* this game's rows were inserted. Read pre-insert for the
     * same reason [previousBestScore] is, and because the engine adds this game's own human totals
     * itself - that way one read gives it both the before and after state of everything measured
     * against the board.
     */
    val previousLeaderboard: LeaderboardTotals = LeaderboardTotals(),
    /** Each number (1-6) player 1 scored a 5x of this game, as the [AchievementCounter.SCORED_5X] counter counts one -
     * [Achievement.FULL_SET]'s. */
    val fiveOfAKindNumbers: Set<Int> = emptySet(),
    /** Whether the cup in play is the Flowerpot that grows its plant - [Achievement.GREENFINGERS] needs it. */
    val growingFlowerpotInPlay: Boolean = false,
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
     * (`ui.style`'s per-category `default`) - a single boolean, not a style id, so this pure
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
    /** The rules this game is played under - [Achievement.WHERE_WERE_GOING_WE_DONT_NEED_RULES]'s trigger. */
    val gameMode: GameMode = GameMode.default,
    /** The hour (0-23) on the phone's clock a new game started at, or null for a resumed one - [Achievement.ROLLING_THE_MIDNIGHT_DICE]'s trigger. */
    val startedAtLocalHour: Int? = null,
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
    /** Every [GameMode] player 1 has a recorded game in, on the Leaderboard or not - [Achievement.GRAND_TOUR]'s measure. */
    val modesPlayed: Set<GameMode> = emptySet(),
) {
    /**
     * The same totals with a finished game folded in - what the board will say once it's saved.
     * [allHumanScores] (every human at the table) feeds [distinctScores]; [primaryPlayerScore]
     * (player 1 alone) feeds [totalPoints] - passed separately, not derived from one list, because
     * conflating them is exactly the bug this split guards against (see git history: career points
     * used to count every human at the table, not just the one whose achievement it is). [gameMode]
     * joins [modesPlayed], as player 1 is always recorded. The scores join [distinctScores] only [onLeaderboard] - a
     * game in a mode off the board, or with a modifier, is recorded but never shown there.
     */
    fun plusGame(allHumanScores: List<Int>, primaryPlayerScore: Int, gameMode: GameMode, onLeaderboard: Boolean) = LeaderboardTotals(
        distinctScores = if (onLeaderboard) distinctScores + allHumanScores else distinctScores,
        totalPoints = totalPoints + primaryPlayerScore,
        modesPlayed = modesPlayed + gameMode,
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
    /** [AchievementsState.countedUnlocks] before this update - what a style's count lock is measured against. */
    val countedUnlocksBefore: Int = 0,
) {
    val isEmpty: Boolean get() = newlyUnlocked.isEmpty() && counters.isEmpty() && progressed.isEmpty()

    /** [AchievementsState.countedUnlocks] once this update is recorded. */
    val countedUnlocksAfter: Int get() = countedUnlocksBefore + newlyUnlocked.count { it.countsTowardStyleLocks }

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
    private const val THE_SOLUTION_SCORE = 255
    private const val LANDSLIDE_MARGIN = 100
    private const val PHOTO_FINISH_MARGIN = 1
    private const val PIPPED_MARGIN = 1
    private const val ZEROES_FOR_HERO = 3
    private const val ENCORE_5X_COUNT = 2
    private const val HAT_TRICK_5X_COUNT = 3
    private const val COLD_DICE_SCORE = 100
    private const val LOW_ROLLS_SCORE = 20

    /** The total [Achievement.HANDS_OFF] needs. */
    private const val HANDS_OFF_SCORE = 200

    /** The total [Achievement.SIX_OF_THE_BEST] needs - a perfect Quickfire game is 210. */
    private const val QUICKFIRE_SCORE_TARGET = 150

    /**
     * The lowest total the rules permit. Legal, if perverse: five 1s taken as Chance scores 5,
     * and every other box can be zeroed deliberately. (A turn timed out unrolled scores a zero even in
     * Chance - which is why the low-score achievements need a game without timeouts.)
     */
    private const val LOWEST_POSSIBLE_SCORE = 5

    /** The hours a new game has to start in for [Achievement.ROLLING_THE_MIDNIGHT_DICE]: midnight up to 6am. */
    private val MIDNIGHT_DICE_HOURS = 0 until 6

    private const val MAX_CHANCE = 30
    private const val MAX_SIXES = 30
    private const val FULL_TABLE_SIZE = 4

    /** Every box [Achievement.TRICOLOUR_ME_IMPRESSED] needs a non-zero score in. */
    private val TRICOLOUR_SET = listOf(
        ScoreCategory.REDS,
        ScoreCategory.YELLOWS,
        ScoreCategory.BLUES,
        ScoreCategory.COLOURED_HOUSE,
    )

    /** How finely a [ProgressStyle.CUMULATIVE] achievement announces itself by default: quarter by
     * quarter - overridden per-achievement by [Achievement.progressStepSize]. */
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
        val leaderboardAfter = leaderboardBefore.plusGame(allHumans.map { it.totalScore }, humans.first().totalScore, state.gameMode, state.countsOnLeaderboard)

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
        timedOutZeroes: List<ScoreCategory> = emptyList(),
        growingFlowerpotInPlay: Boolean = false,
    ): AchievementUpdate {
        if (state.isGameOver) return AchievementUpdate()

        // Player 1 only - see the class doc's first rule.
        if (state.players.firstOrNull()?.type != PlayerType.HUMAN) return AchievementUpdate()
        val humans = listOf(state.players.first())

        return update(earnedDuringPlay(state.players, humans, timedOutZeroes, growingFlowerpotInPlay), emptyMap(), before, now)
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
            if (context.gameMode != GameMode.STANDARD) add(Achievement.WHERE_WERE_GOING_WE_DONT_NEED_RULES)
            if (context.startedAtLocalHour in MIDNIGHT_DICE_HOURS) add(Achievement.ROLLING_THE_MIDNIGHT_DICE)
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
        achievement.isEveryMode -> leaderboard.modesPlayed.size.coerceAtMost(achievement.target)
        achievement.counter != null -> achievement.counter.progressOf(counters[achievement.counter] ?: 0).coerceAtMost(achievement.target)
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
        // The 5x that seven dice hand out (Stud) don't count toward Total 5x.
        val fiveOfAKindsThisGame = humans.filter { it.scoresRolledHand }.sumOf { it.fiveOfAKindCount }

        return mapOf(
            AchievementCounter.GAMES_PLAYED to before.counter(AchievementCounter.GAMES_PLAYED) + 1,
            AchievementCounter.SCORED_5X to before.counter(AchievementCounter.SCORED_5X) + fiveOfAKindsThisGame,
            AchievementCounter.DICE_ROLLED to before.counter(AchievementCounter.DICE_ROLLED) + context.diceRolledByPlayerOne,
            AchievementCounter.FIVE_X_NUMBERS to context.fiveOfAKindNumbers.fold(before.counter(AchievementCounter.FIVE_X_NUMBERS)) { flags, number ->
                flags or (1 shl (number - 1))
            },
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
     * past ([SOLID_ROUND][Achievement.SOLID_ROUND] and friends): leaving a game in progress records
     * nothing on the leaderboard, so unlocking here off a total that's about to vanish would leave
     * an achievement earned with no matching score anywhere to show for it. Those wait for
     * [earnedBy] instead, same as everything else that needs an actual result - "Spotless" needs a
     * complete card, "Cold Dice" needs a score that can no longer climb, and anything about
     * winning needs a result.
     */
    private fun earnedDuringPlay(
        players: List<PlayerState>,
        humans: List<PlayerState>,
        timedOutZeroes: List<ScoreCategory>,
        growingFlowerpotInPlay: Boolean,
    ): Set<Achievement> {
        val earned = mutableSetOf<Achievement>()

        fun award(achievement: Achievement, condition: Boolean) {
            if (condition) earned += achievement
        }

        fun anyHuman(predicate: (PlayerState) -> Boolean) = humans.any(predicate)

        // Dice feats. 5x are counted, not bonus chips: where the 5x box has more than one slot
        // (Third Wind), the second and third 5x can go in it with no chip.
        // Nor where the best five of more dice score (Stud): a 5x or a straight among seven dice is no feat.
        award(Achievement.FIVE_X, anyHuman { it.scoresRolledHand && it.fiveOfAKindCount > 0 })
        award(Achievement.ENCORE, anyHuman { it.scoresRolledHand && it.fiveOfAKindCount >= ENCORE_5X_COUNT })
        award(Achievement.HAT_TRICK, anyHuman { it.scoresRolledHand && it.fiveOfAKindCount >= HAT_TRICK_5X_COUNT })
        award(
            Achievement.STRAIGHT_TALKER,
            anyHuman { it.scoresRolledHand && it.scored(ScoreCategory.SMALL_STRAIGHT) && it.scored(ScoreCategory.LARGE_STRAIGHT) },
        )
        // A box "holds" a score if any one of its slots does, so these mean one turn's score.
        award(Achievement.TAKING_A_CHANCE, anyHuman { it.scoresRolledHand && it.hasScore(ScoreCategory.CHANCE, MAX_CHANCE) })
        award(Achievement.SIX_APPEAL, anyHuman { it.scoresRolledHand && it.hasScore(ScoreCategory.SIXES, MAX_SIXES) })
        award(
            Achievement.BONUS_ROUND,
            anyHuman { it.scoresRolledHand && !it.rollsModified && it.hasFullCard && it.upperSectionBonus > 0 },
        )
        // Section totals over a card scored three times over (Third Wind) would pass these with ease.
        award(Achievement.UPPER_CLASS, anyHuman { it.hasSingleScoreCard && it.upperSectionTotal >= UPPER_CLASS_THRESHOLD })
        award(Achievement.LOWER_CLASS, anyHuman { it.hasSingleScoreCard && it.lowerSectionTotalExcludingFiveOfAKind >= LOWER_CLASS_THRESHOLD })
        // Taken, not handed out by the turn timer: a zero there that the timer didn't score.
        award(
            Achievement.SCRATCHED,
            anyHuman { player ->
                player.scoresIn(ScoreCategory.FIVE_OF_A_KIND).count { it == 0 } > timedOutZeroes.count { it == ScoreCategory.FIVE_OF_A_KIND }
            },
        )

        // Game modes. Only a Tricolour scorecard has these boxes at all, so no separate mode check.
        award(Achievement.TRICOLOUR_ME_IMPRESSED, anyHuman { player -> TRICOLOUR_SET.all { player.scored(it) } })
        // Only a Hit List card has targets. A target's exact hit is the only score worth its exact points.
        award(
            Achievement.RIGHT_ON_TARGET,
            anyHuman { player ->
                player.hitList.any { (category, target) -> target.called.size == target.places.size && player.hasScore(category, target.exactPoints) }
            },
        )

        // Only a D20 card has half bonuses, and with extra rolls they come far more easily.
        award(
            Achievement.CRITICAL_SUCCESS,
            anyHuman { player ->
                val bonuses = player.gameMode.sectionBonuses
                !player.rollsModified && bonuses.isNotEmpty() && bonuses.all { player.hasSectionBonus(it) }
            },
        )

        // Known the moment the table is set.
        award(Achievement.FULL_TABLE, players.size == FULL_TABLE_SIZE)

        // Easter eggs. A roll can't be undone, so a bloom is final. A mode without 3 rolls a turn
        // can't earn it - see growsSunflower. Nor can a game on any cup but the Flowerpot that grows.
        award(Achievement.GREENFINGERS, growingFlowerpotInPlay && anyHuman { it.hasGrownSunflower })

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
        val earned = earnedDuringPlay(players, humans, context.playerOneTimedOutZeroes, context.growingFlowerpotInPlay).toMutableSet()
        // A turn timed out before it was rolled is a zero for nothing (see GameAchievementContext.playerOneTimeouts).
        val noTimeouts = context.playerOneTimeouts == 0
        val multiplayer = players.size > 1
        val primaryPlayerRank = TieBreak.rank(players).first { it.originalIndex == 0 }
        val humanWon = primaryPlayerRank.rank == 1
        val bestHumanScore = humans.maxOf { it.totalScore }
        val margin = winningMargin(players, humans)
        val aiPlayers = players.filter { it.type == PlayerType.AI }

        fun award(achievement: Achievement, condition: Boolean) {
            if (condition) earned += achievement
        }

        fun anyHuman(predicate: (PlayerState) -> Boolean) = humans.any(predicate)

        earned += earnedByProgress(counters, leaderboard)

        // A card scored three times over (Third Wind) has its own version of Spotless instead. Nor does one with
        // boxes switched off (Quickfire) - six turns without a zero is nothing like thirteen.
        // Nor where the best five of more dice score (Stud), whose cards are fuller than Standard's.
        val singleScoreCard = state.gameMode.scoresPerCategory == 1 && !state.rollModifiers.isActive && state.disabledCategories.isEmpty() &&
            !state.gameMode.scoresHeldDiceOnly
        val noZeroes = anyHuman { player -> player.allScores.none { it == 0 } }
        // Nor on a card of targets (Hit List), where a partial hit leaves a zero only now and then.
        award(Achievement.SPOTLESS, singleScoreCard && state.gameMode.hasStandardCard && noZeroes)
        award(Achievement.THIRD_TIMES_THE_CHARM, state.gameMode == GameMode.THIRD_WIND && noZeroes)
        // The best is off the Leaderboard, so a game that isn't on it has nothing to compare with.
        award(
            Achievement.NEW_PERSONAL_BEST,
            state.countsOnLeaderboard && context.previousBestScore != null && bestHumanScore > context.previousBestScore,
        )

        // Score thresholds - see earnedDuringPlay's doc comment for why these wait for the actual
        // result rather than firing off a total that's already passed the mark mid-game. Not on a
        // card scored three times over, whose totals clear every rung as a matter of course.
        // Nor with the Extended Scores modifier's three extra boxes, which add around 50 points to a card.
        // Nor on a card of targets (Hit List), whose totals are a different measure altogether.
        val standardCard = singleScoreCard && !state.extendedScores && state.gameMode.hasStandardCard
        award(Achievement.SOLID_ROUND, standardCard && bestHumanScore >= 200)
        award(Achievement.SHARPSHOOTER, standardCard && bestHumanScore >= 300)
        award(Achievement.HIGH_ROLLER, standardCard && bestHumanScore >= 400)
        award(Achievement.DICE_DEITY, standardCard && bestHumanScore >= 500)

        // Winning.
        // Judged directly off this game's own result, not off the GAMES_WON counter (unlike
        // HALL_OF_FAMER): a counter can already sit at or past its target from an earlier win, so
        // checking it here would re-flag FIRST_VICTORY as newly earned on a later loss if superuser
        // mode's forceLock ever relocked it without also rolling the counter back.
        award(Achievement.FIRST_VICTORY, multiplayer && humanWon)
        award(Achievement.LANDSLIDE, multiplayer && humanWon && margin != null && margin >= LANDSLIDE_MARGIN)
        award(Achievement.PHOTO_FINISH, multiplayer && humanWon && margin == PHOTO_FINISH_MARGIN)
        // Player 1 holds the top rank, but only because the house rule (TieBreak.kt) separated
        // them from another player who matched their raw score - a plain, unbroken top score
        // leaves tieBreakReason null, same as a solo win with nobody to tie.
        award(Achievement.TIE_BREAK, multiplayer && humanWon && primaryPlayerRank.tieBreakReason != null)
        award(Achievement.COMEBACK_KID, multiplayer && humanWon && context.trailedIntoFinalRound)
        award(
            Achievement.ZERO_TO_HERO,
            // Three zeroes over 39 turns (Third Wind), with Two Pair on the card, with dice locked by
            // Unlucky Dice, or on a card of targets (Hit List) that misses about half of them, is the usual run of
            // things, not a comeback.
            state.gameMode.scoresPerCategory == 1 && !state.extendedScores && state.unluckyDice == null && state.gameMode.hasStandardCard &&
                !state.gameMode.scoresHeldDiceOnly && multiplayer && humanWon &&
                humans.any { it.totalScore == state.topScore && it.allScores.count { v -> v == 0 } - context.playerOneTimedOutZeroes.size >= ZEROES_FOR_HERO },
        )
        award(Achievement.TRICOLOURFUL, multiplayer && humanWon && state.gameMode == GameMode.TRICOLOUR)
        award(Achievement.QUICK_ON_THE_DRAW, multiplayer && humanWon && state.gameMode == GameMode.QUICKFIRE)
        award(
            Achievement.SIX_OF_THE_BEST,
            state.gameMode == GameMode.QUICKFIRE && !state.rollModifiers.isActive && !state.extendedScores &&
                bestHumanScore >= QUICKFIRE_SCORE_TARGET,
        )
        award(Achievement.HOLD_EM, multiplayer && humanWon && state.gameMode == GameMode.STUD)
        award(Achievement.GONE_WITH_THE_WIND, multiplayer && humanWon && state.gameMode == GameMode.THIRD_WIND)
        award(Achievement.NAT_20, multiplayer && humanWon && state.gameMode == GameMode.D20)
        award(Achievement.CONTRACT_FULFILLED, multiplayer && humanWon && state.gameMode == GameMode.HIT_LIST)
        award(Achievement.PIPPED_TO_THE_POST, multiplayer && !humanWon && state.topScore - bestHumanScore == PIPPED_MARGIN)
        award(Achievement.DEFEAT_FROM_THE_JAWS_OF_VICTORY, multiplayer && !humanWon && context.ledIntoFinalRound)
        award(
            Achievement.LAST_HUMAN_STANDING,
            humanWon && players.size == FULL_TABLE_SIZE && aiPlayers.size == FULL_TABLE_SIZE - 1,
        )
        award(
            Achievement.HOW_DO_YOU_DO_FELLOW_CPUS,
            players.size == FULL_TABLE_SIZE && aiPlayers.size == FULL_TABLE_SIZE - 1 &&
                aiPlayers.all { it.difficulty == Difficulty.HARD } &&
                primaryPlayerRank.rank < TieBreak.rank(players).maxOf { it.rank },
        )

        // Misfortune that only a finished score can settle.
        // Not on a card with boxes switched off (Quickfire), where a total is low by construction, nor on a card of
        // targets (Hit List), where a total under 100 comes of an ordinary run of misses.
        // Nor with the turn timer having zeroed any of it.
        award(Achievement.COLD_DICE, noTimeouts && anyHuman { it.hasStandardTotals && it.totalScore < COLD_DICE_SCORE })
        award(Achievement.LOW_ROLLS, noTimeouts && anyHuman { it.hasStandardTotals && it.totalScore < LOW_ROLLS_SCORE })
        // Chance is excluded because it cannot be zeroed - five dice always sum to at least 5.
        award(
            Achievement.HOW_DO_YOU_PLAY_THIS_GAME,
            noTimeouts && anyHuman { player ->
                player.hasFullCard && player.categories.filter { it != ScoreCategory.CHANCE }.all { category -> player.scoresIn(category).all { it == 0 } }
            },
        )
        award(Achievement.ROCK_BOTTOM, noTimeouts && anyHuman { it.hasFullCard && it.totalScore == LOWEST_POSSIBLE_SCORE })
        // "Lose a game to a CPU player" specifically - not just any loss. Losing to another human seat is
        // still a loss (PIPPED_TO_THE_POST/DEFEAT_FROM_THE_JAWS_OF_VICTORY don't care who won), but with only
        // player 1 earning achievements now, `!humanWon` alone would also fire whenever another
        // human player at the table beat player 1, which isn't what this achievement means.
        val aiWon = players.any { it.type == PlayerType.AI && it.totalScore == state.topScore }
        award(Achievement.I_ROBOT, multiplayer && !humanWon && aiWon)

        award(Achievement.PRACTICE_MAKES_PERFECT, players.size == 1)
        // Player 1's name and the card are fixed for the whole game, so this only needs the result.
        // No modifier may be on (timer, roll modifiers, Extended Scores, Unlucky Dice): they change the score.
        award(
            Achievement.THE_SOLUTION,
            players.size == 1 && state.gameMode == GameMode.STANDARD && !state.hasModifiers && isPhilWoodward(players[0].name) &&
                players[0].totalScore == THE_SOLUTION_SCORE,
        )

        // Impatient/Naturally Gifted: player 1 took every one of their own turns on a single roll -
        // by choice, so never when a turn has only one roll (the Number of Rolls modifier), where it would be free.
        // Nor with a turn timed out, which takes no extra roll by never being rolled at all.
        val playerOneNeverRolledTwice = !context.playerOneTookExtraRoll && state.rollsPerTurn > 1 && noTimeouts
        award(Achievement.IMPATIENT, playerOneNeverRolledTwice)
        award(Achievement.NATURALLY_GIFTED, multiplayer && playerOneNeverRolledTwice && humanWon)
        // Every modifier at once, in a mode of its own: the timer, both roll modifiers, Extended Scores and Unlucky Dice.
        award(
            Achievement.EVERYTHING_EVERYWHERE_ALL_AT_ONCE,
            multiplayer && humanWon && state.gameMode != GameMode.STANDARD && state.turnTimer != TurnTimer.NONE &&
                state.rollModifiers.rollsPerTurn != null && state.rollModifiers.storedRolls && state.extendedScores && state.unluckyDice != null,
        )

        // Hands Off!: 200 or more without a die held all game - on Standard's own card and rules, nothing to make it easier.
        award(
            Achievement.HANDS_OFF,
            state.gameMode == GameMode.STANDARD && !state.hasModifiers && humans.first().diceHeld == 0 &&
                humans.first().totalScore >= HANDS_OFF_SCORE,
        )

        // Exactly 69/100 - thresholds the running total can overshoot, so they can only be judged
        // now, unlike the 200-or-more/300-or-more rungs further up the ladder.
        award(Achievement.NICE, anyHuman { it.totalScore == NICE_SCORE })
        award(Achievement.TON, anyHuman { it.totalScore == TON_SCORE })

        // The upper section filled with exactly the matching pip count in every box, in this one
        // game - can only be judged once every upper box is actually filled in.
        award(Achievement.EXACT_CHANGE, anyHuman { it.matchesExactUpperLadder() })

        return earned
    }

    /**
     * The achievements a running total has reached: a counter at its target, a score band whose every score is on the
     * Leaderboard, the career points and every mode played. Judged at a game's end, and again whenever the cloud save
     * adds another device's totals to this one's ([evaluateProgress]).
     */
    private fun earnedByProgress(counters: Map<AchievementCounter, Int>, leaderboard: LeaderboardTotals): Set<Achievement> = buildSet {
        for (achievement in Achievement.entries) {
            val counter = achievement.counter ?: continue
            if (counter.progressOf(counters[counter] ?: 0) >= achievement.target) add(achievement)
        }
        // Score collection: every single score in the band has to have been recorded at least once.
        for (achievement in Achievement.entries) {
            val band = achievement.scoreBand ?: continue
            if (band.all { it in leaderboard.distinctScores }) add(achievement)
        }
        if (leaderboard.totalPoints >= Achievement.PROFESSIONAL_ROLLER.target) add(Achievement.PROFESSIONAL_ROLLER)
        if (leaderboard.modesPlayed.containsAll(GameMode.entries)) add(Achievement.GRAND_TOUR)
    }

    /**
     * The achievements [before]'s counters and the [leaderboard] have reached, with no game played: for once the cloud
     * save has merged another device's totals in, which can carry one past a target that neither device reached alone.
     * Counters are untouched, and nothing is a progress step.
     */
    fun evaluateProgress(before: AchievementsState, leaderboard: LeaderboardTotals, now: Long): AchievementUpdate =
        update(earnedByProgress(before.counters, leaderboard), before.counters, before, now, leaderboard, leaderboard)
            .copy(counters = emptyMap(), progressed = emptyList())

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
            countedUnlocksBefore = before.countedUnlocks,
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
            achievement.scoreBand != null || achievement.isCareerPoints || achievement.isEveryMode -> {
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
            ProgressStyle.CUMULATIVE -> milestone(previous, achievement) != milestone(current, achievement)
            ProgressStyle.NONE -> false
        }
        return if (worthShowing) AchievementProgress(achievement, previous, current) else null
    }

    /** [Achievement.progressStepSize] if it has one, otherwise the default quarter-of-[Achievement
     * .target] cadence. */
    private fun milestone(value: Int, achievement: Achievement): Int =
        achievement.progressStepSize?.let { value / it } ?: (value * PROGRESS_MILESTONES / achievement.target)

    /** A tie at the top counts as a win for the human - nobody beat them. */
    // Rank, not raw score: a raw-score tie that the house rule (TieBreak.kt) then loses is not a
    // win, even though `totalScore == state.topScore` alone would say otherwise. `humans` is always
    // just player 1 (see the class doc's first rule), so their `originalIndex` is always 0.
    private fun humanWon(state: GameState, humans: List<PlayerState>): Boolean =
        humans.isNotEmpty() && TieBreak.rank(state.players).first { it.originalIndex == 0 }.rank == 1

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

    /** Whether any of [category]'s slots holds more than zero. */
    private fun PlayerState.scored(category: ScoreCategory): Boolean = scoresIn(category).any { it > 0 }

    /** Whether each box is scored only once, as in every mode but Third Wind, with the mode's own rolls -
     * the totals-based achievements assume both, since a card scored three times over or a turn with
     * extra rolls clears them as a matter of course. */
    private val PlayerState.hasSingleScoreCard: Boolean
        get() = gameMode.scoresPerCategory == 1 && !rollsModified && hasFullCard && scoresRolledHand

    /** Whether a hand is the dice rolled, not the best five of more (Stud). With seven dice to choose from, a 5x, a
     * straight, a full section or a 30 comes far too easily for the achievements that measure them. */
    private val PlayerState.scoresRolledHand: Boolean
        get() = !gameMode.scoresHeldDiceOnly

    /** Whether every box on the card can be scored - none switched off, as Quickfire does. With fewer turns and
     * boxes, the totals and the zeroes the achievements count are a different measure. */
    private val PlayerState.hasFullCard: Boolean
        get() = disabledCategories.isEmpty()

    /** Whether this card's total means what Standard's does: every box on it ([hasFullCard]), and boxes, not targets. */
    private val PlayerState.hasStandardTotals: Boolean
        get() = hasFullCard && gameMode.hasStandardCard

    /** ONES holds exactly 1, TWOS exactly 2, ... SIXES exactly 6 (in any one of its slots) -
     * [PlayerState.UPPER_CATEGORIES] is already declared in that order, so its index doubles as the
     * target value. */
    private fun PlayerState.matchesExactUpperLadder(): Boolean =
        PlayerState.UPPER_CATEGORIES.withIndex().all { (index, category) -> hasScore(category, index + 1) }
}
