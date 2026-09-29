package net.zodac.dicefive.model

/**
 * Which open category a turn is scored in when the turn timer runs out - a [GameMode] rule (see
 * [GameMode.timeoutPick]), applied by `ScoreCalculator.timeoutCategory`.
 */
enum class TimeoutPick {
    /** The first open category in scorecard order, whatever it scores - often a zero. */
    FIRST_OPEN,

    /** The open category the dice score least in, taking the first in scorecard order on a tie. */
    LOWEST_SCORE,
}
