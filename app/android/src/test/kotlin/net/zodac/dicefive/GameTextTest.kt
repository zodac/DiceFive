package net.zodac.dicefive

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.game.GameEngine
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerConfig
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.ui.game.CpuPlayerIcon
import net.zodac.dicefive.ui.game.DiceTray
import net.zodac.dicefive.ui.game.HitListTotalsButton
import net.zodac.dicefive.ui.game.PlayerHeaderBar
import net.zodac.dicefive.ui.game.ReadOnlyScoreboard
import net.zodac.dicefive.ui.game.ScoreGrid
import net.zodac.dicefive.ui.game.ScorecardReviewScreen
import net.zodac.dicefive.ui.game.TotalsButton
import net.zodac.dicefive.ui.game.TurnTimerBadge
import net.zodac.dicefive.ui.game.style.LocalIrishTricolour
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The words on the game screen that the other game tests don't reach: box and die names, action names, and what's drawn as text. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class GameTextTest {

    @get:Rule
    val compose = createComposeRule()

    private fun actionLabel(description: String) =
        compose.onNodeWithContentDescription(description).fetchSemanticsNode().config[SemanticsActions.OnClick].label

    private fun showGrid(mode: GameMode, player: PlayerState, irish: Boolean = false, showPreview: Boolean = false, dice: List<Die> = listOf(Die(5), Die(5), Die(2), Die(3), Die(1))) {
        compose.setContent {
            DiceFiveTheme {
                CompositionLocalProvider(LocalIrishTricolour provides irish) {
                    ScoreGrid(
                        categories = mode.categories,
                        player = player,
                        dice = dice,
                        canScore = true,
                        showPreview = showPreview,
                        available = ScoreCategoryAll,
                        onScoreCategory = {},
                        modifier = Modifier.width(300.dp).height(380.dp),
                    )
                }
            }
        }
    }

    private val ScoreCategoryAll = ScoreCategory.entries.toSet()

    @Test
    fun `an empty box says it is open - and its tap is called Score`() {
        showGrid(GameMode.STANDARD, PlayerState(name = "Tester", type = PlayerType.HUMAN))

        compose.onNodeWithContentDescription("Fives").fetchSemanticsNode().config[SemanticsProperties.StateDescription].let { assertEquals("Open", it) }
        assertEquals("Score", actionLabel("Fives"))
    }

    @Test
    fun `a box switched off says Off`() {
        val player = PlayerState(name = "Tester", type = PlayerType.HUMAN, gameMode = GameMode.QUICKFIRE)
        showGrid(GameMode.QUICKFIRE, player.copy(disabledCategories = setOf(ScoreCategory.FIVES)))

        compose.onNodeWithText("Off", useUnmergedTree = true).assertExists()
    }

    private fun showTricolourBoard(irish: Boolean) {
        val player = PlayerState(name = "Tester", type = PlayerType.HUMAN, gameMode = GameMode.TRICOLOUR)
        compose.setContent {
            DiceFiveTheme {
                CompositionLocalProvider(LocalIrishTricolour provides irish) { ReadOnlyScoreboard(player = player, modifier = Modifier.width(400.dp)) }
            }
        }
    }

    @Test
    fun `Tricolour's colour boxes are named by their colour`() {
        showTricolourBoard(irish = false)

        listOf("Reds", "Yellows", "Blues", "Coloured House").forEach { compose.onNodeWithContentDescription(it).assertExists() }
    }

    @Test
    fun `Luck of the Irish names them in the flag's colours`() {
        showTricolourBoard(irish = true)

        listOf("Greens", "Whites", "Oranges", "Coloured House").forEach { compose.onNodeWithContentDescription(it).assertExists() }
    }

    @Test
    fun `a coloured die is named by its colour - and the tray's actions are called Hold and Release`() {
        compose.setContent {
            DiceFiveTheme {
                DiceTray(
                    dice = listOf(Die(2, colour = DieColour.RED), Die(5, isHeld = true, colour = DieColour.BLUE), Die(3), Die(6), Die(1)),
                    gameMode = GameMode.TRICOLOUR,
                    enabled = true,
                    showDice = true,
                    rolling = false,
                    onToggleHold = {},
                    modifier = Modifier.width(500.dp),
                )
            }
        }

        assertEquals("Hold", actionLabel("Die 1, Red 2"))
        assertEquals("Release", actionLabel("Die 2, Blue 5"))
        compose.onNodeWithContentDescription("Die 3, 3").assertExists()
    }

    @Test
    fun `a player's tab is called View scorecard - and says whose turn it is`() {
        val players = listOf("P1", "P2").map { PlayerState(name = it, type = PlayerType.HUMAN) }
        compose.setContent {
            DiceFiveTheme { PlayerHeaderBar(players = players, currentPlayerIndex = 0, viewedPlayerIndex = null, enabled = true, onPlayerTap = {}, modifier = Modifier.width(400.dp)) }
        }

        val tab = compose.onNode(hasContentDescription("P1").or(androidx.compose.ui.test.hasText("P1"))).fetchSemanticsNode().config
        assertEquals("View scorecard", tab[SemanticsActions.OnClick].label)
    }

    @Test
    fun `the totals button's tap is called Show totals - for both card shapes`() {
        compose.setContent { DiceFiveTheme { TotalsButton(upperTotal = 70, upperBonus = 0, lowerTotal = 140) } }
        assertEquals("Show totals", actionLabel("Totals"))
    }

    @Test
    fun `the Hit List totals button says its targets and alibi`() {
        compose.setContent { DiceFiveTheme { HitListTotalsButton(targetsTotal = 60, alibiTotal = 20) } }

        assertEquals("Targets 60, alibi 20", compose.onNodeWithContentDescription("Totals").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
    }

    @Test
    fun `a computer player's icon is called CPU`() {
        compose.setContent { DiceFiveTheme { CpuPlayerIcon(size = 24.dp) } }

        compose.onNodeWithContentDescription("CPU").assertExists()
    }

    @Test
    fun `the scorecard review page is titled Scorecards`() {
        val state = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Tester")), GameMode.STANDARD)
        compose.setContent { DiceFiveTheme { ScorecardReviewScreen(state = state, onBack = {}) } }

        compose.onNodeWithText("Scorecards").assertExists()
    }
}
