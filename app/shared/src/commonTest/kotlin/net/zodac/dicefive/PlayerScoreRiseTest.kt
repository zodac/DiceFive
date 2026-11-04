package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.model.PlayerColour
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.ui.game.PlayerHeaderBar
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** A tab's score counts up to a new total, whether or not the new-score pill comes or goes as it does. */
class PlayerScoreRiseTest : UiTest() {

    private fun scoring(total: Int) = PlayerState(name = "Sam", type = PlayerType.HUMAN, colour = PlayerColour.CYAN)
        .let { it.copy(scorecard = it.scorecard + (ScoreCategory.CHANCE to listOf(total))) }

    /** The score the tab shows, by [FRAMES] of the rise from 100 to 200, with the pill as [before] and then as [after]. */
    private fun rise(before: Set<Int>, after: Set<Int>) = uiTest(TestScreen(360, 800)) {
        var player by mutableStateOf(scoring(100))
        var marked by mutableStateOf(before)
        compose.setContent {
            DiceFiveTheme {
                Box(Modifier.width(344.dp)) {
                    PlayerHeaderBar(listOf(player), currentPlayerIndex = 0, viewedPlayerIndex = null, enabled = true, onPlayerTap = {}, newScoreSeats = marked)
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        player = scoring(200)
        marked = after
        val shown = (1..FRAMES).map {
            compose.mainClock.advanceTimeBy(FRAME_STEP_MILLIS)
            compose.onAllNodes(SemanticsMatcher("has text") { node -> node.config.getOrNull(SemanticsProperties.Text) != null }, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty() }
                .firstNotNullOf { it.text.toIntOrNull() }
        }
        assertEquals(100, shown.first())
        assertTrue(shown.drop(1).dropLast(1).all { it in 101..199 }, "the score didn't count up: $shown")
        assertEquals(shown.sorted(), shown, "the score didn't only rise: $shown")
    }

    @Test
    fun `the score counts up with no pill`() = rise(before = emptySet(), after = emptySet())

    @Test
    fun `the score counts up under a pill`() = rise(before = setOf(0), after = setOf(0))

    @Test
    fun `the score counts up as the pill appears`() = rise(before = emptySet(), after = setOf(0))

    @Test
    fun `the score counts up as the pill goes`() = rise(before = setOf(0), after = emptySet())

    private companion object {
        const val FRAMES = 8
        const val FRAME_STEP_MILLIS = 100L
    }
}
