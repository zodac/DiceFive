package net.zodac.dicefive.ui.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.zodac.dicefive.game.TutorialScript
import net.zodac.dicefive.game.TutorialStep
import net.zodac.dicefive.model.GameState

/**
 * How far through [script]'s guided game the player is: the step they're on ([current]), the one the coach card shows
 * ([shown]) and its turn, and where back and forward go ([jump]). What each step lets the player do is
 * [GameViewModel]'s. A step shown later ([advance]) waits through [pause], so it keeps the game's own clock.
 */
internal class TutorialProgress(
    private val script: TutorialScript,
    private val scope: CoroutineScope,
    private val pause: suspend (Long) -> Unit,
) {
    /** How far through [script]'s steps the guided game is. */
    private var index = 0

    private val _shown = MutableStateFlow(script.steps.firstOrNull())

    /**
     * The step for the coach card and the board - null once the last one is done. After a roll it's the step after once
     * the dice have settled; what the player may do ([current]) moves on as the roll lands.
     */
    val shown: StateFlow<TutorialStep?> = _shown.asStateFlow()

    private val _turn = MutableStateFlow(1)

    /** Which of [script]'s turns [shown] is part of, from 1 - see [TutorialScript.turnOf]. */
    val turn: StateFlow<Int> = _turn.asStateFlow()

    /** The step the player is on: what they may do now. */
    val current: TutorialStep?
        get() = script.steps.getOrNull(index)

    /** Whether [jump] can go back: anywhere but the very start. */
    val canJumpBack: Boolean
        get() = index > 0

    /** Whether [jump] can go forward: before the last turn. */
    val canJumpForward: Boolean
        get() = script.turnStarts.any { it > index }

    /** The guided game as it began, for [jump] to replay from. */
    private var start: GameState? = null

    /** Showing the next step once a roll's dice have settled - see [shown]. */
    private var showJob: Job? = null

    /** Starts over from the first step, in the game [start] begins. */
    fun begin(start: GameState) {
        index = 0
        show(0)
        this.start = start
    }

    /** On to the next step, shown after [shownAfterMillis]. */
    fun advance(shownAfterMillis: Long = 0L) {
        index++
        val next = index
        showJob?.cancel()
        if (shownAfterMillis <= 0L) {
            show(next)
        } else {
            showJob = scope.launch {
                pause(shownAfterMillis)
                show(next)
            }
        }
    }

    /**
     * The coach card's back (-1) and forward (+1): to the start of the previous or next turn - or, going back from part
     * way through a turn, to the start of this one. Returns the game rebuilt as if every step before it had been played
     * ([TutorialScript.replay]), or null with no turn that way.
     */
    fun jump(direction: Int): GameState? {
        val start = start ?: return null
        val starts = script.turnStarts
        val current = starts.indexOfLast { it <= index }
        val target = when {
            direction < 0 && index > starts[current] -> starts[current]
            direction < 0 -> starts.getOrNull(current - 1) ?: return null
            else -> starts.getOrNull(current + 1) ?: return null
        }
        showJob?.cancel()
        index = target
        show(target)
        return script.replay(start, target)
    }

    private fun show(index: Int) {
        _shown.value = script.steps.getOrNull(index)
        _turn.value = script.turnOf(index).coerceAtMost(script.turns)
    }
}
