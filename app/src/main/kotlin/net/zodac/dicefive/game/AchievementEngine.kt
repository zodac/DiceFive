package net.zodac.dicefive.game

import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter
import net.zodac.dicefive.model.Difficulty
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
    /** The best leaderboard score *before* this game's rows were inserted, or null if there were none. */
    val previousBestScore: Int? = null,
    /** Whether a human went into their final turn behind every other player. */
    val trailedIntoFinalRound: Boolean = false,
    /** How many dice a human actually re-rolled across the game. AI rolls don't count. */
    val diceRolledByHumans: Int = 0,
    /**
     * What the leaderboard said *before* this game's rows were inserted. Read pre-insert for the
     * same reason [previousBestScore] is, and because the engine adds this game's own human totals
     * itself - that way one read gives it both the before and after state of everything measured
     * against the board.
     */
    val previousLeaderboard: LeaderboardTotals = LeaderboardTotals(),
)

/**
 * The parts of the leaderboard that achievements are scored against, for the ones measured by what
 * has actually been recorded rather than by a stored counter: the score bands, and career points.
 */
data class LeaderboardTotals(
    val distinctScores: Set<Int> = emptySet(),
    val totalPoints: Int = 0,
) {
    /** The same totals with [scores] added - what the board will say once a finished game is saved. */
    operator fun plus(scores: List<Int>) = LeaderboardTotals(
        distinctScores = distinctScores + scores,
        totalPoints = totalPoints + scores.sum(),
    )
}

/** One "getting closer" banner: [achievement] is now at [current] out of its target. */
data class AchievementProgress(val achievement: Achievement, val current: Int)

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
 *  - **Per device, not per player.** Every human at the table contributes; an achievement asking
 *    what "you" scored is satisfied if *any* human did. AI results never earn anything - they are
 *    only ever the opposition. In an all-human game the device therefore always wins, which is
 *    what device-scoped win streaks mean.
 *  - **Superuser mode still earns.** Hand-setting dice used to disqualify a game outright, which
 *    made the debug cheat useless for testing the very thing it was best placed to test. It also
 *    protected nobody: superuser mode is gated on `BuildConfig.DEBUG`, so a release build cannot
 *    reach it at all.
 */
object AchievementEngine {

    private const val UPPER_CLASS_THRESHOLD = 84

    /** Out of a 235-point maximum across the lower boxes, so a high bar without being the ceiling. */
    private const val LOWER_CLASS_THRESHOLD = 150
    private const val TON_SCORE = 100
    private const val LANDSLIDE_MARGIN = 100
    private const val PHOTO_FINISH_MARGIN = 5
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

        val humans = state.players.filter { it.type == PlayerType.HUMAN }
        if (humans.isEmpty()) return AchievementUpdate()

        val leaderboardBefore = context.previousLeaderboard
        val leaderboardAfter = leaderboardBefore + humans.map { it.totalScore }

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

        val humans = state.players.filter { it.type == PlayerType.HUMAN }
        if (humans.isEmpty()) return AchievementUpdate()

        return update(earnedDuringPlay(state.players, humans), emptyMap(), before, now)
    }

    /**
     * Achievements earned at a specific moment rather than off the back of a scorecard - today,
     * the three feats rolled straight out of the cup. Counters are untouched.
     */
    fun unlockNow(achievements: Set<Achievement>, before: AchievementsState, now: Long): AchievementUpdate =
        update(achievements, before.counters, before, now)

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
            AchievementCounter.DICE_ROLLED to before.counter(AchievementCounter.DICE_ROLLED) + context.diceRolledByHumans,
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
     * nothing that happens later can take them away: a box already holds 30, a bonus is already
     * banked, a running total has already passed a threshold (a total only ever grows).
     *
     * Everything else has to wait for the end - "Spotless" needs a complete card, "Cold Dice"
     * needs a score that can no longer climb, and anything about winning needs a result.
     */
    private fun earnedDuringPlay(players: List<PlayerState>, humans: List<PlayerState>): Set<Achievement> {
        val earned = mutableSetOf<Achievement>()

        fun award(achievement: Achievement, condition: Boolean) {
            if (condition) earned += achievement
        }

        fun anyHuman(predicate: (PlayerState) -> Boolean) = humans.any(predicate)

        val bestHumanScore = humans.maxOf { it.totalScore }

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

        // Score thresholds: a total only ever grows, so passing one mid-game is already final.
        award(Achievement.SCORE_200, bestHumanScore >= 200)
        award(Achievement.SCORE_300, bestHumanScore >= 300)
        award(Achievement.SCORE_400, bestHumanScore >= 400)
        award(Achievement.SCORE_500, bestHumanScore >= 500)

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

        // Winning.
        award(Achievement.WIN_BY_100, multiplayer && humanWon && margin != null && margin >= LANDSLIDE_MARGIN)
        award(Achievement.WIN_BY_5, multiplayer && humanWon && margin != null && margin <= PHOTO_FINISH_MARGIN)
        award(Achievement.COMEBACK, multiplayer && humanWon && context.trailedIntoFinalRound)
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
                ScoreCategory.entries.filter { it != ScoreCategory.CHANCE }.all { player.scorecard[it] == 0 }
            },
        )
        award(Achievement.EXTREME_LOW_ROLLS, anyHuman { it.totalScore == LOWEST_POSSIBLE_SCORE })
        award(Achievement.SINGULARITY, multiplayer && !humanWon)

        award(Achievement.SOLO_GAME, players.size == 1)

        // Exactly 100 - a threshold the running total can overshoot, so it can only be judged now.
        award(Achievement.TON, anyHuman { it.totalScore == TON_SCORE })

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
        if (!before.isUnlocked(Achievement.COMPLETIONIST) && unlockedAfter.containsAll(Achievement.COMPLETION_REQUIREMENTS)) {
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
        return if (worthShowing) AchievementProgress(achievement, current) else null
    }

    private fun milestone(value: Int, target: Int): Int = value * PROGRESS_MILESTONES / target

    /** A tie at the top counts as a win for the human - nobody beat them. */
    private fun humanWon(state: GameState, humans: List<PlayerState>): Boolean {
        val topScore = state.players.maxOf { it.totalScore }
        return humans.any { it.totalScore == topScore }
    }

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

    /** 5x actually rolled: the box itself, plus a bonus chip for every one after it. */
    private val PlayerState.fiveOfAKindCount: Int
        get() = (if (scorecard[ScoreCategory.FIVE_OF_A_KIND] == DiceScoring.FIVE_OF_A_KIND_SCORE) 1 else 0) + fiveOfAKindBonusCount
}
