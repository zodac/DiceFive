package net.zodac.dicefive.game

import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.ScoreCategory

/**
 * How one kind of CPU plays its turn: what it holds, what a hand is worth to it and where it scores. [AiTurnPlayer]
 * does what every CPU does the same (the unlucky dice, picking out a hand, applying holds) and asks the strategy
 * [cpuStrategyFor] picks for everything else. [perfectPlay] is Standard's perfect-play table, which only Hard reads.
 */
internal interface CpuStrategy {

    /** Which of the just-rolled dice to hold before the next roll, in a mode where every die scores. Every die means "stop rolling". */
    fun chooseHolds(state: GameState, player: PlayerState, perfectPlay: StandardPerfectPlayTable?): Set<Int>

    /**
     * [chooseHolds] where only held dice score ([GameMode.scoresHeldDiceOnly]): every die still means "stop rolling",
     * and any other answer holds no more than there are slots.
     */
    fun chooseHoldsForHand(state: GameState, player: PlayerState, perfectPlay: StandardPerfectPlayTable?): Set<Int> =
        chooseHolds(state, player, perfectPlay)

    /** What a whole hand is worth to this CPU, for picking which held dice to score with ([AiTurnPlayer.chooseHand]). */
    fun handValuer(player: PlayerState, perfectPlay: StandardPerfectPlayTable?): (List<Die>) -> Double =
        { hand -> bestRawScore(player, hand).toDouble() }

    /** The box [hand] goes in, of the [available] ones. */
    fun chooseCategory(
        state: GameState,
        player: PlayerState,
        hand: List<Die>,
        available: List<ScoreCategory>,
        perfectPlay: StandardPerfectPlayTable?,
    ): ScoreCategory
}

/** The strategy [player] plays [mode] by: a card of targets by [HitListPlay] at every difficulty, any other by its difficulty's. */
internal fun cpuStrategyFor(player: PlayerState, mode: GameMode): CpuStrategy = when {
    mode.hasHitList -> HitListPlay
    else -> when (player.difficulty) {
        Difficulty.EASY -> EasyCpu
        Difficulty.MEDIUM -> MediumCpu
        Difficulty.HARD -> HardCpu
    }
}

/** The most [hand] scores in any box open to it. */
internal fun bestRawScore(player: PlayerState, hand: List<Die>): Int =
    ScoreCalculator.availableCategories(player, hand).maxOf { ScoreCalculator.scoreFor(player, it, hand) }

/** Whether [dice] score above zero in any box open to them. */
internal fun hasPossibleScore(player: PlayerState, dice: List<Die>): Boolean =
    ScoreCalculator.availableCategories(player, dice).any { ScoreCalculator.scoreFor(player, it, dice) > 0 }

/**
 * Easy: never holds individual dice, but stops rerolling (all of them, together) as soon as any open category would
 * score above zero, then greedily scores the highest-value one. If nothing scores by the last roll it's forced to take a
 * zero somewhere, same as a human would be. No lookahead at all.
 */
internal object EasyCpu : CpuStrategy {

    /**
     * It's all of the dice or none. Once at least one open category would score above zero, there's nothing left to
     * decide, so it holds everything (the "stop rolling" signal) rather than spend a roll it doesn't need; while nothing
     * scores, it holds nothing and lets every die reroll.
     */
    override fun chooseHolds(state: GameState, player: PlayerState, perfectPlay: StandardPerfectPlayTable?): Set<Int> =
        if (hasPossibleScore(player, state.dice)) state.dice.indices.toSet() else emptySet()

    /** Judges the hand it would hold now ([AiTurnPlayer.chooseHand]), stopping as soon as that scores at all. */
    override fun chooseHoldsForHand(state: GameState, player: PlayerState, perfectPlay: StandardPerfectPlayTable?): Set<Int> {
        val hand = AiTurnPlayer.chooseHand(state, perfectPlay).sorted().map { state.dice[it] }
        return if (hasPossibleScore(player, hand)) state.dice.indices.toSet() else emptySet()
    }

    override fun chooseCategory(
        state: GameState,
        player: PlayerState,
        hand: List<Die>,
        available: List<ScoreCategory>,
        perfectPlay: StandardPerfectPlayTable?,
    ): ScoreCategory = available.maxBy { ScoreCalculator.scoreFor(player, it, hand) }
}
