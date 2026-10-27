package net.zodac.dicefive.game

import kotlin.random.Random
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory

/**
 * How a CPU plays its turn: what every CPU does the same way here, and the rest by the [CpuStrategy] for its
 * [Difficulty] - [EasyCpu], [MediumCpu] or [HardCpu].
 *
 * Every rule comes from the player's own [GameMode] - its dice, faces and colours, and which
 * categories are on the card. In a mode with coloured dice, Medium's rules of thumb also chase a colour set. A card of
 * targets ([GameMode.hasHitList]) is played by [HitListPlay] instead, at every difficulty.
 */
object AiTurnPlayer {

    /**
     * Which dice indices an AI would hold before its next reroll, given the current (just-rolled) dice.
     * [perfectPlay] is Standard's perfect-play table, which Hard plays by in Standard when it's given
     * (see [StandardPerfectPlayTable]); without it, or in another mode, Hard estimates.
     *
     * Every die means "stop rolling": the turn is scored as it stands. In a mode where only held dice
     * score ([GameMode.scoresHeldDiceOnly]) that's the only way to ask for all of them, since there
     * are fewer hold slots than dice - every other answer there holds no more than the slots allow,
     * and the hand to score is then picked by [chooseHand].
     */
    fun chooseHolds(state: GameState, perfectPlay: StandardPerfectPlayTable? = null): Set<Int> {
        val player = requireNotNull(state.currentPlayer) { "No current player" }
        if (state.dice.any { it.isUnlucky }) return withoutUnluckyDice(state) { reduced -> chooseHolds(reduced, perfectPlay) }
        val strategy = cpuStrategyFor(player, state.gameMode)
        return if (state.gameMode.scoresHeldDiceOnly) strategy.chooseHoldsForHand(state, player, perfectPlay) else strategy.chooseHolds(state, player, perfectPlay)
    }

    /**
     * Which dice an AI would score with, in a mode where only held dice score - the
     * [GameMode.scoringDiceCount] of them it holds once it's done rolling: the hand its strategy values most
     * ([CpuStrategy.handValuer]) - Easy and Medium the one that scores the most anywhere open, Hard the one its
     * valuation rates highest. On a tie, the hand that changes fewer of the dice already held.
     */
    fun chooseHand(state: GameState, perfectPlay: StandardPerfectPlayTable? = null): Set<Int> {
        val player = requireNotNull(state.currentPlayer) { "No current player" }
        if (state.dice.any { it.isUnlucky }) return withoutUnluckyDice(state) { reduced -> chooseHand(reduced, perfectPlay) }
        val dice = state.dice
        val valueOf = cpuStrategyFor(player, state.gameMode).handValuer(player, perfectPlay)
        var best = emptySet<Int>()
        var bestValue = Double.NEGATIVE_INFINITY
        var bestChanges = Int.MAX_VALUE
        forEachCombination(dice.size, minOf(state.gameMode.scoringDiceCount, dice.size)) { indices ->
            val value = valueOf(indices.map { dice[it] })
            val changes = dice.indices.count { dice[it].isHeld != (it in indices) }
            if (value > bestValue || (value == bestValue && changes < bestChanges)) {
                best = indices.toSet()
                bestValue = value
                bestChanges = changes
            }
        }
        return best
    }

    /**
     * Runs [choose] on [state] as it would be with its unlucky dice ([Die.isUnlucky]) taken off the table - they can't
     * be held and don't score, so they aren't the AI's to weigh - and maps the indices it answers with back to
     * [state]'s own dice. An answer that keeps every remaining die (the "stop rolling" signal) becomes every die.
     *
     * Hard plans over whole hands of every die ([DiceSpace]), which a hand with dice missing isn't, so it plays
     * the turn as Medium does: its rules of thumb on the dice that are left. It doesn't anticipate a die being
     * locked on a coming roll either.
     */
    private fun withoutUnluckyDice(state: GameState, choose: (GameState) -> Set<Int>): Set<Int> {
        val player = requireNotNull(state.currentPlayer) { "No current player" }
        val usable = state.dice.indices.filter { !state.dice[it].isUnlucky }
        val playedAs = if (player.difficulty == Difficulty.HARD) player.copy(difficulty = Difficulty.MEDIUM) else player
        val reduced = state.copy(
            dice = usable.map { state.dice[it] },
            players = state.players.mapIndexed { index, each -> if (index == state.currentPlayerIndex) playedAs else each },
        )
        val chosen = choose(reduced)
        if (chosen.size == reduced.dice.size && reduced.dice.isNotEmpty()) return state.dice.indices.toSet()
        return chosen.map { usable[it] }.toSet()
    }

    /** Calls [action] with every way of picking [size] of the indices `0 until [count]`, in ascending order. */
    private inline fun forEachCombination(count: Int, size: Int, action: (List<Int>) -> Unit) {
        val picked = IntArray(size) { it }
        if (size > count) return
        while (true) {
            action(picked.toList())
            var position = size - 1
            while (position >= 0 && picked[position] == count - size + position) position--
            if (position < 0) return
            picked[position]++
            for (next in position + 1 until size) picked[next] = picked[next - 1] + 1
        }
    }

    /**
     * Builds what Hard's decisions in [mode] (with the Extended Scores boxes, if [extendedScores]) need - every hand scored, and each box's baseline - ahead
     * of its first one. Kept for the process, so only the first call does anything: with Tricolour's
     * coloured dice that's most of a second on a laptop, so a game with a Hard CPU starts it in the
     * background rather than leave the CPU's first roll to wait on it. Nothing in a mode with targets, which Hard
     * plays by [HitListPlay].
     */
    fun prepareHard(mode: GameMode, extendedScores: Boolean = false) {
        if (mode.hasHitList) return
        HardCpu.prepare(mode, extendedScores)
    }

    /**
     * Applies a hold decision (e.g. from [chooseHolds]) to every die that isn't already in the right
     * state - every release first, so a hold slot let go of is free for a die held in its place.
     */
    fun applyHolds(state: GameState, holdIndices: Set<Int>): GameState {
        var current = state
        state.dice.forEachIndexed { index, die ->
            if (die.isHeld && index !in holdIndices) current = GameEngine.toggleHold(current, index)
        }
        state.dice.forEachIndexed { index, die ->
            if (!die.isHeld && index in holdIndices) current = GameEngine.toggleHold(current, index)
        }
        return current
    }

    /** The category an AI would choose for its current (fully-rolled) hand - [GameState.scoringDice] - [perfectPlay] as for [chooseHolds]. */
    fun chooseCategory(state: GameState, perfectPlay: StandardPerfectPlayTable? = null): ScoreCategory {
        val player = requireNotNull(state.currentPlayer) { "No current player" }
        val hand = state.scoringDice
        val available = ScoreCalculator.availableCategories(player, hand)
        check(available.isNotEmpty()) { "No available categories to score" }
        return cpuStrategyFor(player, state.gameMode).chooseCategory(state, player, hand, available, perfectPlay)
    }

    /**
     * Whether [state]'s CPU plays by Standard's perfect-play table: a Hard one, in a game on the card the table was
     * built for ([GameMode.playsByPerfectPlayTable]). Every other CPU is given none.
     */
    fun usesPerfectPlayTable(state: GameState): Boolean {
        val player = state.currentPlayer ?: return false
        return player.type == PlayerType.AI && player.difficulty == Difficulty.HARD && state.gameMode.playsByPerfectPlayTable(state.extendedScores)
    }

    /**
     * Whether a CPU choosing [holds] is done rolling: it keeps every die, so a roll would change nothing. It stops with its
     * rolls still left, as a person can, and [GameState.rollsRemaining] still says how many.
     */
    fun stopsRolling(state: GameState, holds: Set<Int>): Boolean = holds.size == state.dice.size

    /**
     * Pure end-to-end simulation of an AI's whole turn: roll, hold, roll, hold, roll, then - where only
     * held dice score - hold the hand, then score. Used by tests and as a reference for GameViewModel's
     * animated version.
     */
    fun playTurn(state: GameState, random: Random = Random.Default, perfectPlay: StandardPerfectPlayTable? = null): GameState {
        var current = state
        while (current.rollsRemaining > 0) {
            current = GameEngine.rollDice(current, random)
            if (current.rollsRemaining > 0) {
                val holds = chooseHolds(current, perfectPlay)
                if (stopsRolling(current, holds)) break
                current = applyHolds(current, holds)
            }
        }
        if (current.gameMode.scoresHeldDiceOnly) current = applyHolds(current, chooseHand(current, perfectPlay))
        return GameEngine.commitScore(current, chooseCategory(current, perfectPlay))
    }
}
