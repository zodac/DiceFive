package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.ui.common.BrandBackdrop
import net.zodac.dicefive.ui.common.REDRAWS_ALONE_TAG
import net.zodac.dicefive.ui.game.DiceTray
import net.zodac.dicefive.ui.style.DiceMats
import net.zodac.dicefive.ui.style.GameVisualTheme
import net.zodac.dicefive.ui.style.LocalGameVisualTheme
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/**
 * The tray's mat and the backdrop's drifting dice are drawn in a layer of their own, covering all they always covered, so
 * their frames don't record what they sit behind again.
 */
class RedrawsAloneTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun redrawsAloneNode(): SemanticsNode = compose.onNodeWithTag(REDRAWS_ALONE_TAG, useUnmergedTree = true).fetchSemanticsNode()

    private fun Dp.px(): Int = with(compose.density) { roundToPx() }

    /** The one node redrawing alone: in a layer of its own, and with nothing for a screen reader to find. */
    private fun assertRedrawsAlone(expected: IntSize) {
        val node = redrawsAloneNode()
        assertEquals(expected, node.size)
        assertTrue(node.layoutInfo.getModifierInfo().any { it.extra != null }, "No layer of its own")
        assertEquals(setOf(SemanticsProperties.TestTag), node.config.map { it.key }.toSet())
    }

    @Test
    fun theTraysMatRedrawsAloneOverTheWholeTray() = uiTest {
        showcase.show {
            DiceFiveTheme {
                CompositionLocalProvider(LocalGameVisualTheme provides GameVisualTheme(mat = DiceMats.byId("starry_midnight"))) {
                    DiceTray(dice = List(5) { Die(it + 1) }, gameMode = GameMode.STANDARD, enabled = true, showDice = true, rolling = false, onToggleHold = {}, modifier = Modifier.width(BOARD_WIDTH))
                }
            }
        }
        assertEquals(BOARD_WIDTH.px(), redrawsAloneNode().size.width)
        assertRedrawsAlone(redrawsAloneNode().size)
    }

    @Test
    fun onlyDriftingBackdropDiceRedrawAlone() = uiTest {
        showcase.show {
            DiceFiveTheme {
                Box(Modifier.size(BOARD_WIDTH, BACKDROP_HEIGHT)) { BrandBackdrop(driftingDice = true) { Box(Modifier.fillMaxSize()) } }
            }
        }
        assertRedrawsAlone(IntSize(BOARD_WIDTH.px(), BACKDROP_HEIGHT.px()))

        showcase.show {
            DiceFiveTheme {
                Box(Modifier.size(BOARD_WIDTH, BACKDROP_HEIGHT)) { BrandBackdrop { Box(Modifier.fillMaxSize()) } }
            }
        }
        assertTrue(compose.onAllNodesWithTag(REDRAWS_ALONE_TAG, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    private companion object {
        val BOARD_WIDTH = 300.dp
        val BACKDROP_HEIGHT = 400.dp
    }
}
