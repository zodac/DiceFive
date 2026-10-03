package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.TimeUnit
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.game.GameScreen
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Quickfire's automatic roll is the game screen tapping its own cup - so it's only testable with the
 * real screen and view model together, not the view model alone.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class GameScreenAutoRollTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showGame(mode: GameMode, withCpu: Boolean = false): GameViewModel {
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(if (withCpu) 2 else 1)
        if (withCpu) viewModel.setPlayerType(2, PlayerType.AI)
        viewModel.setGameMode(mode)
        viewModel.startGame()
        compose.setContent {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                DiceFiveTheme { GameScreen(viewModel = viewModel) }
            }
        }
        return viewModel
    }

    private fun GameViewModel.phase() = game.value!!.phase

    /** Waits for [condition], moving the main looper's clock along as it goes - the view model's
     * coroutines (the AI turn, the turn timer) delay on the main looper, which Robolectric keeps
     * paused and the compose clock alone doesn't advance. */
    private fun waitFor(what: String, viewModel: GameViewModel, condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(50)
            compose.waitForIdle()
        }
        error("Timed out waiting for $what - state: ${viewModel.game.value}")
    }

    @Test
    fun quickfireRollsTheFirstTurnWithoutATap() {
        val viewModel = showGame(GameMode.QUICKFIRE)

        compose.waitUntil(timeoutMillis = 5_000) { viewModel.phase() == TurnPhase.ROLLED }

        assertEquals(0, viewModel.game.value!!.rollsRemaining)
    }

    @Test
    fun standardWaitsForTheCupToBeTapped() {
        val viewModel = showGame(GameMode.STANDARD)

        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()

        assertEquals(TurnPhase.AWAITING_ROLL, viewModel.phase())
    }

    @Test
    fun quickfireShowsItsRollAtOnceSoNothingWaitsForATossButStandardDoes() {
        val quickfire = showGame(GameMode.QUICKFIRE)
        compose.waitForIdle()
        // What the CPU loop and the roll achievements wait on before acting on a roll.
        assertEquals(0L, quickfire.diceTossMillis)
    }

    @Test
    fun standardWaitsForItsDiceToBeTossed() {
        val standard = showGame(GameMode.STANDARD)
        compose.waitForIdle()
        assertTrue(standard.diceTossMillis > 0L)
    }

    @Test
    fun quickfireRollsEachHumanTurnWhileTheCpuPlaysItsOwn() {
        val viewModel = showGame(GameMode.QUICKFIRE, withCpu = true)

        repeat(3) { turn ->
            waitFor("the human's turn $turn to roll", viewModel) {
                val state = viewModel.game.value!!
                state.currentPlayerIndex == 0 && state.phase == TurnPhase.ROLLED
            }
            val open = viewModel.game.value!!.players[0].scorecard.filterValues { it == null }.keys
            compose.runOnIdle { viewModel.commitScore(open.first { it != ScoreCategory.FIVE_OF_A_KIND }) }

            // The CPU takes its turn - one roll, one score - and play comes back round.
            waitFor("the CPU's turn $turn", viewModel) {
                val state = viewModel.game.value!!
                state.currentPlayerIndex == 0 && state.players[1].scorecard.values.count { it != null } == turn + 1
            }
        }

        val state = viewModel.game.value!!
        assertEquals(3, state.players[0].scorecard.values.count { it != null })
        assertEquals(3, state.players[1].scorecard.values.count { it != null })
    }
}
