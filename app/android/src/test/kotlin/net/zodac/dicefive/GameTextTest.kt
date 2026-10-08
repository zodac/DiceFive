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
import net.zodac.dicefive.ui.game.style.LocalIrishTricolour
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The words on the game screen that the other game tests don't reach: box and die names, action names, and what's drawn as text. */
@RunWith(AndroidJUnit4::class)
class GameTextTest {

    @get:Rule
    val compose = createComposeRule()

    private fun actionLabel(description: String) =
        compose.onNodeWithContentDescription(description).fetchSemanticsNode().config[SemanticsActions.OnClick].label

    private val showcase = Showcase(compose)

    private fun showGrid(mode: GameMode, player: PlayerState) = showcase.show {
        DiceFiveTheme {
            ScoreGrid(
                categories = mode.categories,
                player = player,
                dice = listOf(Die(5), Die(5), Die(2), Die(3), Die(1)),
                canScore = true,
                showPreview = false,
                available = ScoreCategory.entries.toSet(),
                onScoreCategory = {},
                modifier = Modifier.width(300.dp).height(380.dp),
            )
        }
    }

    private fun showTricolourBoard(irish: Boolean) {
        val player = PlayerState(name = "Tester", type = PlayerType.HUMAN, gameMode = GameMode.TRICOLOUR)
        showcase.show {
            DiceFiveTheme {
                CompositionLocalProvider(LocalIrishTricolour provides irish) { ReadOnlyScoreboard(player = player, modifier = Modifier.width(400.dp)) }
            }
        }
    }

    @Test
    fun `the scorecard's words - an open box and its Score tap, Off, the colour boxes, the totals and the review page's title`() {
        // An empty box says it is open - and its tap is called Score.
        showGrid(GameMode.STANDARD, PlayerState(name = "Tester", type = PlayerType.HUMAN))
        assertEquals("Open", compose.onNodeWithContentDescription("Fives").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        assertEquals("Score", actionLabel("Fives"))

        // A box switched off says Off.
        showGrid(GameMode.QUICKFIRE, PlayerState(name = "Tester", type = PlayerType.HUMAN, gameMode = GameMode.QUICKFIRE, disabledCategories = setOf(ScoreCategory.FIVES)))
        compose.onNodeWithText("Off", useUnmergedTree = true).assertExists()

        // Tricolour's colour boxes are named by their colour - and by Luck of the Irish in the flag's.
        showTricolourBoard(irish = false)
        listOf("Reds", "Yellows", "Blues", "Coloured House").forEach { compose.onNodeWithContentDescription(it).assertExists() }
        showTricolourBoard(irish = true)
        listOf("Greens", "Whites", "Oranges", "Coloured House").forEach { compose.onNodeWithContentDescription(it).assertExists() }

        // The totals button's tap is called Show totals; the Hit List one says its targets and alibi.
        showcase.show { DiceFiveTheme { TotalsButton(upperTotal = 70, upperBonus = 0, lowerTotal = 140) } }
        assertEquals("Show totals", actionLabel("Totals"))
        showcase.show { DiceFiveTheme { HitListTotalsButton(targetsTotal = 60, alibiTotal = 20) } }
        assertEquals("Targets 60, alibi 20", compose.onNodeWithContentDescription("Totals").fetchSemanticsNode().config[SemanticsProperties.StateDescription])

        // The scorecard review page is titled Scorecards.
        val state = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Tester")), GameMode.STANDARD)
        showcase.show { DiceFiveTheme { ScorecardReviewScreen(state = state, onBack = {}) } }
        compose.onNodeWithText("Scorecards").assertExists()
    }

    @Test
    fun `the table's words - a coloured die and Hold and Release, a player's tab and View scorecard, and the CPU icon`() {
        showcase.show {
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

        val players = listOf("P1", "P2").map { PlayerState(name = it, type = PlayerType.HUMAN) }
        showcase.show {
            DiceFiveTheme { PlayerHeaderBar(players = players, currentPlayerIndex = 0, viewedPlayerIndex = null, enabled = true, onPlayerTap = {}, modifier = Modifier.width(400.dp)) }
        }
        val tab = compose.onNode(hasContentDescription("P1").or(androidx.compose.ui.test.hasText("P1"))).fetchSemanticsNode().config
        assertEquals("View scorecard", tab[SemanticsActions.OnClick].label)

        showcase.show { DiceFiveTheme { CpuPlayerIcon(size = 24.dp) } }
        compose.onNodeWithContentDescription("CPU").assertExists()
    }
}
