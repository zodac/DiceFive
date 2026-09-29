package net.zodac.dicefive

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.ui.game.DiceTray
import net.zodac.dicefive.ui.game.ScoreGrid
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What a screen reader gets from the game board's drawn art: each die named by its face and whether
 * it's held, with hold/release as its action, and each score box named with what it has or would
 * score, with scoring as its action - none of which the art itself says.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BoardSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun hasStateDescription(state: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

    @Test
    fun `each die says its face and whether it's held - and its action holds it`() {
        val toggled = mutableListOf<Int>()
        compose.setContent {
            DiceFiveTheme {
                DiceTray(
                    dice = listOf(Die(2), Die(5, isHeld = true), Die(3), Die(6), Die(1)),
                    gameMode = GameMode.STANDARD,
                    enabled = true,
                    showDice = true,
                    rolling = false,
                    onToggleHold = { toggled += it },
                    modifier = Modifier.width(500.dp),
                )
            }
        }

        compose.onNodeWithContentDescription("Die 2, 5").assert(hasStateDescription("Held"))
        compose.onNodeWithContentDescription("Die 4, 6").assert(hasStateDescription("Not held")).performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(3), toggled)
    }

    @Test
    fun `a score box says what it would score - and its action scores it`() {
        val scored = mutableListOf<ScoreCategory>()
        val player = PlayerState(name = "Tester", type = PlayerType.HUMAN)
        compose.setContent {
            DiceFiveTheme {
                ScoreGrid(
                    gameMode = GameMode.STANDARD,
                    player = player,
                    dice = listOf(Die(5), Die(5), Die(2), Die(3), Die(1)),
                    canScore = true,
                    showPreview = true,
                    available = ScoreCategory.entries.toSet(),
                    onScoreCategory = { scored += it },
                    modifier = Modifier.width(300.dp).height(380.dp),
                )
            }
        }

        compose.onNodeWithContentDescription("Fives").assert(hasStateDescription("Would score 10")).performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(ScoreCategory.FIVES), scored)
    }
}
