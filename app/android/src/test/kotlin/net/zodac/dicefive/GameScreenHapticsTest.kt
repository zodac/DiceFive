package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.TimeUnit
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.platform.HapticEffect
import net.zodac.dicefive.platform.HapticsPlayer
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.PlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.game.GameScreen
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper

/** The phone buzzes for the player's own roll, and stays still for a CPU's - whose roll is still heard, but not felt. */
@RunWith(AndroidJUnit4::class)
class GameScreenHapticsTest {

    @get:Rule
    val compose = createComposeRule()

    private val showcase = Showcase(compose)

    private val felt = mutableListOf<HapticEffect>()

    private val recordingServices = object : PlatformServices by SilentPlatformServices {
        override fun createHapticsPlayer(): HapticsPlayer = object : HapticsPlayer {
            override fun play(effect: HapticEffect) {
                felt += effect
            }
        }
    }

    private fun show(viewModel: GameViewModel) {
        // Off, so each step moves the clock only as far as advance() says.
        compose.mainClock.autoAdvance = false
        viewModel.setGameMode(GameMode.STANDARD)
        viewModel.startGame()
        showcase.show {
            CompositionLocalProvider(LocalPlatformServices provides recordingServices) {
                DiceFiveTheme { GameScreen(viewModel = viewModel) }
            }
        }
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        repeat(600) {
            if (condition()) return
            ShadowLooper.idleMainLooper(STEP_MILLIS, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(STEP_MILLIS)
            compose.waitForIdle()
        }
        error("Timed out waiting for $what")
    }

    private fun tapCup() {
        compose.onNodeWithContentDescription("Dice cup", substring = true).performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test
    fun `the player's own roll buzzes the phone once`() {
        val viewModel = gameViewModel()
        viewModel.setPlayerCount(1)
        show(viewModel)
        tapCup()
        waitFor("the roll to land") { viewModel.game.value!!.phase == TurnPhase.ROLLED }
        assertEquals(listOf(HapticEffect.SHAKE_BUZZ), felt)
    }

    @Test
    fun `a CPU's roll does not buzz the phone - only the player's own did`() {
        val viewModel = gameViewModel()
        viewModel.setPlayerCount(2)
        viewModel.setPlayerType(2, PlayerType.AI)
        show(viewModel)
        tapCup()
        waitFor("the player's roll to land") { viewModel.game.value!!.phase == TurnPhase.ROLLED }
        viewModel.commitScore(ScoreCategory.CHANCE)

        // The CPU is up, and rolls: its dice land, and the phone has buzzed only for the first roll.
        waitFor("the CPU's roll to land") {
            val game = viewModel.game.value!!
            game.players[game.currentPlayerIndex].type == PlayerType.AI && game.phase == TurnPhase.ROLLED
        }
        assertEquals(listOf(HapticEffect.SHAKE_BUZZ), felt)
    }

    private companion object {
        const val STEP_MILLIS = 50L
    }
}
