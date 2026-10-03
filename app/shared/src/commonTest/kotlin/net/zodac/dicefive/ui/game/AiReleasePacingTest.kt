package net.zodac.dicefive.ui.game

import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory

/** Dice that land on an exact script of [values] in order, cycling once it runs out. */
private class ScriptedRolls(private val values: List<Int>) : Random() {
    private var index = 0

    override fun nextBits(bitCount: Int): Int = 0
    override fun nextInt(from: Int, until: Int): Int = values[index++ % values.size]
}

/**
 * A CPU's hold changes are paced like a hand's: one die at a time, and a die it lets go of stays on the
 * mat long enough to be seen there before its next roll sweeps it up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiReleasePacingTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a die the CPU releases rests on the mat before the next shake starts`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(
            aiDispatcher = testDispatcher,
            random = ScriptedRolls(
                listOf(
                    1, 1, 1, 1, 1, // The human's roll.
                    6, 6, 1, 3, 2, // The CPU's first: Medium holds the pair of 6s...
                    3, 4, 5, //       ...and its second makes 3-4-5-6, so it keeps one 6 and lets the other go.
                ),
            ),
        )
        viewModel.setPlayerCount(2)
        viewModel.setPlayerType(2, PlayerType.AI)
        viewModel.startGame()

        var releasedAt: Long? = null
        var nextShakeAt: Long? = null
        backgroundScope.launch(testDispatcher) {
            viewModel.game.collect { state ->
                val wasHeldPair = state?.currentPlayerIndex == 1 && state.dice[0].isHeld && !state.dice[1].isHeld &&
                    state.dice.count { it.isHeld } == 4
                if (wasHeldPair && releasedAt == null) releasedAt = testScheduler.currentTime
            }
        }
        backgroundScope.launch(testDispatcher) {
            viewModel.aiRolling.collect { rolling ->
                if (rolling && releasedAt != null && nextShakeAt == null) nextShakeAt = testScheduler.currentTime
            }
        }

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.CHANCE)
        advanceTimeBy(30_000)
        runCurrent()

        val released = assertNotNull(releasedAt, "the CPU never let go of its second 6")
        val shake = assertNotNull(nextShakeAt, "the CPU never rolled again after letting go")
        assertTrue(shake - released >= 500, "the next shake started ${shake - released}ms after the release")
    }

    @Test
    fun `the CPU releases and holds dice one at a time with a longer beat between the two`() = runTest(testDispatcher) {
        val viewModel = GameViewModel(aiDispatcher = testDispatcher, random = ScriptedRolls(PAIR_THEN_STRAIGHT))
        viewModel.setPlayerCount(2)
        viewModel.setPlayerType(2, PlayerType.AI)
        viewModel.startGame()

        // Every change to a die's hold during the CPU's turn: when, which die, and which way.
        val changes = mutableListOf<Triple<Long, Int, Boolean>>()
        backgroundScope.launch(testDispatcher) {
            var previous: List<Boolean>? = null
            viewModel.game.collect { state ->
                val cpuTurn = state != null && state.currentPlayerIndex == 1 && state.players[1].scorecard.values.all { it == null }
                val held = state?.dice?.map { it.isHeld }
                if (cpuTurn && previous != null && held != null) {
                    held.indices.filter { held[it] != previous!![it] }.forEach { changes += Triple(testScheduler.currentTime, it, held[it]) }
                }
                previous = if (cpuTurn) held else null
            }
        }

        viewModel.rollDice()
        viewModel.commitScore(ScoreCategory.CHANCE)
        advanceTimeBy(30_000)
        runCurrent()

        val firstHold = changes.first().first
        val release = changes.single { !it.third }.first
        assertEquals(
            listOf(
                // First roll, 6-6-1-3-2: the pair of 6s, one die after the other.
                Triple(firstHold, 0, true), Triple(firstHold + 50, 1, true),
                // Second, 6-6-3-4-5: let one 6 go, a longer beat, then 3, 4 and 5 one by one.
                Triple(release, 1, false), Triple(release + 125, 2, true), Triple(release + 175, 3, true), Triple(release + 225, 4, true),
            ),
            changes,
        )
    }

    private companion object {
        val PAIR_THEN_STRAIGHT = listOf(1, 1, 1, 1, 1, 6, 6, 1, 3, 2, 3, 4, 5)
    }
}
