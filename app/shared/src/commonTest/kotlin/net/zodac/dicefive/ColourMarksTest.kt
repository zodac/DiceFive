package net.zodac.dicefive

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import kotlin.test.Test
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.ui.game.COLOUR_MARK_TAG
import net.zodac.dicefive.ui.game.DiceTray
import net.zodac.dicefive.ui.style.LocalTricolourMarks
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** The Tricolour shapes setting: a mark on each coloured die while it's on, nothing at all while it's off, and no change to what's said. */
class ColourMarksTest : UiTest() {

    private val coloured = listOf(DieColour.RED, DieColour.YELLOW, DieColour.BLUE, DieColour.RED, DieColour.BLUE)
        .mapIndexed { index, colour -> Die(value = index + 1, colour = colour, isHeld = index == 0) }

    private fun show(dice: List<Die>, marks: Boolean) {
        compose.setContent {
            DiceFiveTheme {
                CompositionLocalProvider(LocalTricolourMarks provides marks) {
                    DiceTray(dice = dice, gameMode = GameMode.TRICOLOUR, enabled = true, showDice = true, rolling = false, onToggleHold = {}, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `every coloured die has a mark while the setting is on - held or not - and still says its colour`() = uiTest(TestScreen(360, 800)) {
        show(coloured, marks = true)
        compose.onAllNodesWithTag(COLOUR_MARK_TAG, useUnmergedTree = true).assertCountEquals(5)
        compose.onNodeWithContentDescription("Die 2, Yellow 2").assertExists()
    }

    @Test
    fun `no die has a mark while the setting is off`() = uiTest(TestScreen(360, 800)) {
        show(coloured, marks = false)
        compose.onAllNodesWithTag(COLOUR_MARK_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `a die with no colour has no mark even with the setting on`() = uiTest(TestScreen(360, 800)) {
        show(coloured.map { it.copy(colour = null) }, marks = true)
        compose.onAllNodesWithTag(COLOUR_MARK_TAG, useUnmergedTree = true).assertCountEquals(0)
    }
}
