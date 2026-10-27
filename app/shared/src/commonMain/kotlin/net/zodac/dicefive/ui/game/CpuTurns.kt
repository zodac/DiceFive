package net.zodac.dicefive.ui.game

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.zodac.dicefive.game.AiTurnPlayer
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.game.StandardPerfectPlayTable
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerType

/**
 * Plays the CPUs' turns on the table, paced for the screen as a person's would be: the cup's shake, the dice landing,
 * a beat before reaching for them, the holds a die at a time, and a pause before scoring. The decisions are
 * [AiTurnPlayer]'s, worked out on [dispatcher] while those waits run. What it needs of the game is its [Host]'s.
 */
internal class CpuTurns(private val scope: CoroutineScope, private val dispatcher: CoroutineDispatcher, private val host: Host) {

    /** What a CPU's turn needs of the game it's played in. */
    interface Host {
        /** The waits a turn keeps for the screen's animations. */
        val pacing: GamePacing

        /** Waits [millis], paused while the game is off screen. */
        suspend fun pause(millis: Long)

        /** Rolls the dice for [state]'s CPU as every roll is rolled, puts the roll on the table, and returns it. */
        fun roll(state: GameState): GameState

        /** Puts [state] on the table, with nothing to undo. */
        fun publish(state: GameState)

        /** The perfect-play table [state]'s CPU plays by, or null for one that estimates. */
        suspend fun perfectPlayFor(state: GameState): StandardPerfectPlayTable?
    }

    /** Whether a CPU's cup is shaking for its roll - see [startIfDue]. */
    private val _rolling = MutableStateFlow(false)
    val rolling: StateFlow<Boolean> = _rolling.asStateFlow()

    private var job: Job? = null

    /** Stops the turn under way, if any, wherever it is: an undo took it back, or the turn timer ran out on it. */
    fun cancel() {
        job?.cancel()
        job = null
        _rolling.value = false
    }

    /**
     * Flips each of [indices]' holds in turn, left to right, [AI_HOLD_STEP_MS] apart, publishing every
     * one - a CPU reaching for its dice one by one. Returns the state with all of them flipped.
     */
    private suspend fun toggleHoldsOneByOne(state: GameState, indices: List<Int>): GameState {
        var current = state
        for ((step, index) in indices.withIndex()) {
            if (step > 0) host.pause(AI_HOLD_STEP_MS)
            current = GameEngine.toggleHold(current, index)
            host.publish(current)
        }
        return current
    }

    /**
     * The CPU's hold changes: every release, then every new hold. One die at a time while the dice are animated
     * ([toggleHoldsOneByOne]), as a hand would; with them not animated ([GamePacing.diceAnimated] off) that stepping is just
     * motion, so every change lands at once, published together. Returns the state with all of them made.
     */
    private suspend fun changeHolds(state: GameState, toRelease: List<Int>, toHold: List<Int>): GameState {
        if (!host.pacing.diceAnimated) {
            val current = (toRelease + toHold).fold(state, GameEngine::toggleHold)
            if (current !== state) host.publish(current)
            return current
        }
        var current = toggleHoldsOneByOne(state, toRelease)
        if (toRelease.isNotEmpty() && toHold.isNotEmpty()) host.pause(AI_RELEASE_TO_HOLD_GAP_MS)
        current = toggleHoldsOneByOne(current, toHold)
        return current
    }

    /**
     * Starts a job that plays out every AI turn in a row from here (not just one) - e.g. with a
     * human followed by three AI, this single job carries players 2, 3 and 4 through their whole
     * turns before handing back to the human. Looping in place rather than recursively re-launching
     * itself matters: `job` isn't reassigned - and so isn't `isActive` - until this whole
     * coroutine returns, so a self re-launch from its own tail would see itself as still active and
     * bail out, silently dropping every AI turn after the first.
     */
    fun startIfDue(state: GameState) {
        if (state.isGameOver) return
        if (state.currentPlayer?.type != PlayerType.AI) return
        if (job?.isActive == true) return

        job = scope.launch {
            var current = state
            while (!current.isGameOver && current.currentPlayer?.type == PlayerType.AI) {
                val perfectPlay = host.perfectPlayFor(current)
                while (current.rollsRemaining > 0) {
                    // The delay doubles as the cup's shake animation window - the same CUP_SHAKE_MILLIS
                    // a human's tap shakes for, whatever the difficulty, and the roll itself goes
                    // through the same performRoll. aiRolling is true for the shake's whole span
                    // and false the moment the roll (never the hold decision below - see that
                    // comment) is published.
                    _rolling.value = true
                    try {
                        host.pause(host.pacing.cupShakeMillis)
                        current = host.roll(current)
                    } finally {
                        _rolling.value = false
                    }
                    // Nothing left to decide on the turn's last roll - there's no further reroll to
                    // hold dice FOR.
                    val rolled = current
                    val holdsChoice = if (rolled.rollsRemaining > 0) async(dispatcher) { AiTurnPlayer.chooseHolds(rolled, perfectPlay) } else null

                    // The roll is published but the dice are still tossing onto the mat: let them settle
                    // before holding, rerolling or scoring, as a human has to. The hold choice above is
                    // already being worked out meanwhile, so the toss covers up to its own length of thinking.
                    host.pause(host.pacing.diceTossMillis)
                    if (holdsChoice == null) break

                    // Deliberately outside the block above: choosing what to hold happens once
                    // the cup has already stopped shaking and this roll's dice are already on
                    // screen, never while the shake itself is still playing. Hard's hold choice
                    // is an exhaustive search over every 32 hold/reroll subsets, each averaged
                    // over every possible reroll outcome - expensive enough that computing it
                    // during the shake (as this used to) stretched the shake's own on-screen
                    // duration out past Easy/Medium's, whose choices are next to free. Now every
                    // difficulty's shake is the same fixed length, and "thinking" is just a
                    // static pause with nothing animating - however long it takes, only the pause
                    // before the dice's held state updates changes, not any animation. It runs during
                    // the toss, so the pause is only whatever it takes beyond the toss itself.
                    val holds = holdsChoice.await()
                    // Done rolling: stop before the holds are applied, as showing every die held just before scoring
                    // is noise. "x$rollsRemaining" on the cup still shows the rolls left - see AiTurnPlayer.stopsRolling.
                    if (AiTurnPlayer.stopsRolling(current, holds)) break

                    // A human's beat to take in the settled dice before reaching for them - with the
                    // choice worked out during the toss, the holds would otherwise land the instant the
                    // dice stop. Scoring gets the same beat from AI_STEP_DELAY_MS below.
                    host.pause(AI_REACTION_DELAY_MS)
                    // One die at a time, as a hand would: every release first, then every new hold, each
                    // published as it happens - all at once, a swap of held dice read as a jump cut.
                    val toRelease = current.dice.indices.filter { current.dice[it].isHeld && it !in holds }
                    val toHold = current.dice.indices.filter { !current.dice[it].isHeld && it in holds }
                    val releasedAny = toRelease.isNotEmpty()
                    current = changeHolds(current, toRelease, toHold)

                    // A beat with the cup settled and the result visible before the next roll's
                    // shake starts - without it, back-to-back rolls (routine for Easy, which never
                    // holds anything and so never gets to skip a roll) read as one continuous blur
                    // rather than distinct rolls. Only between rolls: the very first roll and the
                    // score are already paced by the shake above and AI_STEP_DELAY_MS below. A die just
                    // let go of gets longer: it drops from its slot onto the mat, and the shake sweeps
                    // every loose die off the mat - so after only ROLL_GAP_MS it would be gone again
                    // almost as soon as it landed, reading as a die vanishing rather than being released.
                    // With the dice not animated, a released die is simply back on the mat: nothing to watch land.
                    host.pause(if (releasedAny && host.pacing.diceAnimated) RELEASE_GAP_MS else ROLL_GAP_MS)
                }
                // Where only held dice score, the CPU picks out its hand now it's done rolling - held as a
                // human would, so the hand it scores is the one in its slots.
                if (current.gameMode.scoresHeldDiceOnly) {
                    val toHold = current
                    val handChoice = async(dispatcher) { AiTurnPlayer.chooseHand(toHold, perfectPlay) }
                    host.pause(AI_REACTION_DELAY_MS)
                    val hand = handChoice.await()
                    current = changeHolds(
                        current,
                        toRelease = current.dice.indices.filter { current.dice[it].isHeld && it !in hand },
                        toHold = current.dice.indices.filter { !current.dice[it].isHeld && it in hand },
                    )
                }
                // Also off the main thread: Hard's category choice compares against
                // HardCpu.CATEGORY_BASELINES, a `by lazy` average-over-every-outcome computed once per
                // process on whichever call touches it first - same cost/rationale as the hold
                // choice above. Worked out during the pause before scoring, not after it.
                val toScore = current
                val categoryChoice = async(dispatcher) { AiTurnPlayer.chooseCategory(toScore, perfectPlay) }
                host.pause(AI_STEP_DELAY_MS)
                val category = categoryChoice.await()
                current = GameEngine.commitScore(current, category)
                host.publish(current)
            }
        }
    }

    private companion object {
        /** The AI's pause, dice settled, before it scores. Its rolls shake for CUP_SHAKE_MILLIS, same as a tap's. */
        private const val AI_STEP_DELAY_MS = 250L

        /** The AI's pause, dice settled, before it holds any of them for its next roll. */
        private const val AI_REACTION_DELAY_MS = 200L

        /** Between one die the AI holds (or releases) and the next, when it changes several. */
        private const val AI_HOLD_STEP_MS = 50L

        /** Between the last die the AI releases and the first it then holds in their place. */
        private const val AI_RELEASE_TO_HOLD_GAP_MS = 125L

        /** Pause between one roll settling and the next one's shake starting, within the same AI turn. */
        private const val ROLL_GAP_MS = 100L

        /** [ROLL_GAP_MS] when the AI has just let go of a die: long enough to see it land on the mat before the shake sweeps it up. */
        private const val RELEASE_GAP_MS = 500L
    }
}
