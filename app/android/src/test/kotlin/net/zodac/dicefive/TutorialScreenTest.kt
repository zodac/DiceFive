package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.TimeUnit
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.TutorialMessage
import net.zodac.dicefive.game.TutorialScript
import net.zodac.dicefive.game.TutorialStep
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.game.GameScreen
import net.zodac.dicefive.ui.game.GameViewModel
import net.zodac.dicefive.ui.game.LeaveGameConfirmation
import net.zodac.dicefive.ui.game.LeaveGameConfirmationDialog
import net.zodac.dicefive.ui.game.LocalLeaveGameConfirmation
import net.zodac.dicefive.ui.rules.RulesScreen
import net.zodac.dicefive.ui.rules.resetRulesPageMemory
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * The guided game on screen: the coach card read out as each step arrives, only the step's control taking a tap, and
 * its own question on the way out.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class TutorialScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var viewModel: GameViewModel

    /** A tutorial on screen at its first step, with its own leave question (not the app's shared one). */
    private fun showTutorial() {
        compose.mainClock.autoAdvance = false
        viewModel = GameViewModel(
            ScoreRepository(RowsDao(emptyList())),
            SettingsRepository(InMemoryPreferences()),
            InProgressGameRepository(InMemoryPreferences()),
            InMemoryAchievementStore(),
            tutorial = TutorialScript.STANDARD,
        )
        viewModel.startTutorial("Player 1")
        val leave = LeaveGameConfirmation()
        compose.setContent {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices, LocalLeaveGameConfirmation provides leave) {
                DiceFiveTheme {
                    GameScreen(viewModel = viewModel)
                    LeaveGameConfirmationDialog(leave)
                }
            }
        }
        advance(1_000)
        viewModel.setForeground(true)
    }

    /** Moves both clocks on: the compose one and the paused main looper the view model's delays wait on. */
    private fun advance(millis: Long) {
        repeat((millis / STEP_MILLIS).toInt()) {
            ShadowLooper.idleMainLooper(STEP_MILLIS, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(STEP_MILLIS)
            compose.waitForIdle()
        }
    }

    /** Does what each step asks until [message]'s is showing, its dice settled. */
    private fun playTo(message: TutorialMessage) {
        repeat(MAX_STEPS) {
            val step = checkNotNull(viewModel.tutorialStep.value)
            if (step.message == message) {
                advance(SETTLE_MILLIS)
                return
            }
            when (step) {
                is TutorialStep.OpenTotals -> viewModel.tutorialTotalsOpened()
                is TutorialStep.Roll -> viewModel.shakeAndRoll()
                is TutorialStep.Hold -> step.dice.forEach(viewModel::toggleHold)
                is TutorialStep.Score -> viewModel.commitScore(step.category)
                is TutorialStep.Undo -> viewModel.undo()
            }
            advance(SETTLE_MILLIS)
        }
        error("Never reached $message")
    }

    private val hasClick = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)

    @Test
    fun `the card is read as one - its name then its words - as a polite live region - in place of the player tabs`() {
        showTutorial()
        val card = compose.onNode(hasText("Coach") and hasText("Roll the five dice", substring = true)).fetchSemanticsNode().config
        assertEquals(
            listOf("Coach", "Welcome! Roll the five dice up to 3 times a turn, then score them in one box of your scorecard. After 13 turns every box is filled, and their total is your score. Tap the cup to start."),
            card[SemanticsProperties.Text].map { it.text },
        )
        assertEquals(LiveRegionMode.Polite, card[SemanticsProperties.LiveRegion])
        // The turn: beside the name, but a node of its own, not read out again with every step.
        compose.onNodeWithText("Turn 1 of 13").assertExists()
        compose.onAllNodesWithText("Next").assertCountEquals(0)
        // No player tabs: the card has their place.
        compose.onAllNodesWithText("Player 1").assertCountEquals(0)
    }

    @Test
    fun `only the step's control takes a tap - the cup - the dice to hold - the box to score - the totals`() {
        showTutorial()
        // The welcome is the first roll's card: the cup takes a tap, and the totals don't open outside their own step.
        assertFalse(SemanticsProperties.Disabled in compose.onNodeWithContentDescription("Dice cup", substring = true).fetchSemanticsNode().config)
        assertTrue(SemanticsProperties.Disabled in compose.onNodeWithContentDescription("Totals").fetchSemanticsNode().config)

        // One tap on the cup rolls: holding the three 6s, only they can be held, and nothing scored.
        compose.onNodeWithContentDescription("Dice cup", substring = true).performClick()
        advance(SETTLE_MILLIS * 2)
        assertEquals(TutorialMessage.HOLD_SIXES, viewModel.tutorialStep.value?.message)
        val holdable = (1..5).filter { die -> SemanticsActions.OnClick in compose.onNode(hasContentDescription("Die $die,", substring = true)).fetchSemanticsNode().config }
        assertEquals(listOf(1, 3, 5), holdable)
        assertTrue(SemanticsProperties.Disabled in compose.onNodeWithContentDescription("Dice cup", substring = true).fetchSemanticsNode().config)
        compose.onAllNodes(hasContentDescription("Sixes") and hasClick).assertCountEquals(0)

        // Scoring the 5x: only its box.
        playTo(TutorialMessage.SCORE_FIVE_X)
        compose.onAllNodes(hasContentDescription("5x") and hasClick).assertCountEquals(1)
        compose.onAllNodes(hasContentDescription("Sixes") and hasClick).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("Chance") and hasClick).assertCountEquals(0)

        // Undo: shown all along in this solo game, but only taking a tap at the step that takes back the score in Fives.
        playTo(TutorialMessage.SCORE_FIVES_MISTAKE)
        assertTrue(SemanticsProperties.Disabled in compose.onNodeWithContentDescription("Undo").fetchSemanticsNode().config)
        viewModel.commitScore(net.zodac.dicefive.model.ScoreCategory.FIVES)
        advance(SETTLE_MILLIS)
        assertFalse(SemanticsProperties.Disabled in compose.onNodeWithContentDescription("Undo").fetchSemanticsNode().config)
        compose.onNodeWithContentDescription("Undo").performClick()
        advance(SETTLE_MILLIS)
        assertEquals(TutorialMessage.SCORE_FOUR_X, viewModel.tutorialStep.value?.message)
    }

    @Test
    fun `with the totals open - one tap on the cup rolls - and the totals close`() {
        showTutorial()
        playTo(TutorialMessage.OPEN_TOTALS)
        compose.onNodeWithContentDescription("Totals").performClick()
        advance(SETTLE_MILLIS)
        assertEquals(TutorialMessage.UPPER_PROGRESS, viewModel.tutorialStep.value?.message)
        compose.onNodeWithText("42 / 63").assertExists()

        compose.onNodeWithContentDescription("Dice cup", substring = true).performClick()
        advance(SETTLE_MILLIS * 2)
        assertEquals(TutorialMessage.HOLD_ONES, viewModel.tutorialStep.value?.message)
        compose.onAllNodesWithText("42 / 63").assertCountEquals(0)
    }

    @Test
    fun `the card's arrows go to the next and previous turns - named for TalkBack - back off at the very start`() {
        showTutorial()
        assertTrue(SemanticsProperties.Disabled in compose.onNodeWithContentDescription("Previous turn").fetchSemanticsNode().config)
        compose.onNodeWithContentDescription("Next turn").performClick()
        advance(SETTLE_MILLIS)
        compose.onNodeWithText("Turn 2 of 13").assertExists()
        assertFalse(SemanticsProperties.Disabled in compose.onNodeWithContentDescription("Previous turn").fetchSemanticsNode().config)
        compose.onNodeWithContentDescription("Previous turn").performClick()
        advance(SETTLE_MILLIS)
        compose.onNodeWithText("Turn 1 of 13").assertExists()
    }

    @Test
    fun `leaving asks first - the tutorial's own question - whatever the confirm setting`() {
        showTutorial()
        compose.onNodeWithContentDescription("Back").performClick()
        advance(STEP_MILLIS)
        compose.onNodeWithText("Leave the tutorial?").assertExists()
        compose.onNodeWithText("It isn't saved: you'll start again from the beginning next time.").assertExists()
    }

    @Test
    fun `How to Play starts it`() {
        var started = 0
        compose.setContent {
            resetRulesPageMemory()
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                DiceFiveTheme { RulesScreen(onBack = {}, onStartTutorial = { started++ }) }
            }
        }
        compose.onNodeWithText("How to Play DiceFive").assertExists()
        compose.onNodeWithText("Start Tutorial").performScrollTo().performClick()
        assertEquals(1, started)
        resetRulesPageMemory()
    }

    private companion object {
        const val STEP_MILLIS = 50L
        const val SETTLE_MILLIS = 1_500L
        const val MAX_STEPS = 100
    }
}
