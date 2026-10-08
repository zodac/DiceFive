package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.TimeUnit
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.game.GameScreen
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper

/**
 * The cup takes no tap while a roll is in hand - shaking, or its dice still tumbling onto the mat - the same
 * wait scoring has. Needs the real screen: the settling window is tracked there, not in the view model.
 */
@RunWith(AndroidJUnit4::class)
class GameScreenCupGateTest {

    @get:Rule
    val compose = createComposeRule()

    private val showcase = Showcase(compose)

    /** A fresh solo game on screen. */
    private fun showSoloGame(): GameViewModel {
        // Off, or every waitForIdle (each assertion makes one) runs the clock on through every pending
        // delay - the whole shake and toss - in one go, and there's no "still settling" left to tap into.
        compose.mainClock.autoAdvance = false
        val viewModel = GameViewModel()
        viewModel.setPlayerCount(1)
        viewModel.setGameMode(GameMode.STANDARD)
        viewModel.startGame()
        showcase.show {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                DiceFiveTheme { GameScreen(viewModel = viewModel) }
            }
        }
        return viewModel
    }

    private fun cup() = compose.onNodeWithContentDescription("Dice cup", substring = true)

    // Through the node's own Roll action, which calls onCupTap whether or not the node says it's enabled -
    // so this checks the tap's own gate, not just the semantics.
    private fun tapCup() {
        cup().performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun GameViewModel.rollsRemaining() = game.value!!.rollsRemaining

    /** Moves both clocks on by [millis]: the compose one (the shake, the settle wait) and the paused main looper. */
    private fun advance(millis: Long) {
        repeat((millis / STEP_MILLIS).toInt()) {
            ShadowLooper.idleMainLooper(STEP_MILLIS, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(STEP_MILLIS)
            compose.waitForIdle()
        }
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            advance(STEP_MILLIS)
        }
        error("Timed out waiting for $what")
    }

    @Test
    fun theCupTakesNoTapWhileTheDiceSettleAndSaysHowManyRollsAreLeftInTheRightForm() {
        // The count is a plural from the string resources: "rolls" for 3 and 2, "roll" for 1.
        val viewModel = showSoloGame()
        compose.onNodeWithContentDescription("Dice cup, 3 rolls left").assertExists()
        assertEquals("Roll", cup().fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        tapCup()
        waitFor("the first roll to land") { viewModel.game.value!!.phase == TurnPhase.ROLLED }
        assertEquals(2, viewModel.rollsRemaining())
        compose.onNodeWithContentDescription("Dice cup, 2 rolls left").assertExists()

        // Straight after landing the dice are still tumbling: the cup says it's disabled, and a tap does nothing - even
        // given longer than a shake (420ms) to land, while still inside the toss (900ms).
        cup().assertIsNotEnabled()
        tapCup()
        advance(600)
        assertEquals(2, viewModel.rollsRemaining())

        // Once they've settled, the cup rolls again.
        waitFor("the dice to settle") { runCatching { cup().assertIsEnabled() }.isSuccess }
        tapCup()
        waitFor("the second roll to land") { viewModel.rollsRemaining() == 1 }
        compose.onNodeWithContentDescription("Dice cup, 1 roll left").assertExists()
    }

    @Test
    fun aSecondTapDuringTheShakeDoesNotRollTwice() {
        val shaking = showSoloGame()
        tapCup()
        advance(STEP_MILLIS)
        cup().assertIsNotEnabled()
        tapCup()
        waitFor("the roll to land") { shaking.game.value!!.phase == TurnPhase.ROLLED }
        advance(1_500)
        assertEquals(2, shaking.rollsRemaining())
    }

    private companion object {
        const val STEP_MILLIS = 50L
    }
}
