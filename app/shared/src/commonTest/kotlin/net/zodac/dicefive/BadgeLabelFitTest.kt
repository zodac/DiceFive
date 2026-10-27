package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.game.GameScreen
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** The screen this class's tests are drawn on. */
private val SCREEN = TestScreen(360, 800, 3f)

/**
 * A tile's text mark stays on one line at any font size: D20's wildcards ("1-10", "11-20") are drawn at their own size
 * where they fit, and shrink rather than wrap at a large font.
 */
class BadgeLabelFitTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun showD20(fontScale: Float) {
        val viewModel = gameViewModel()
        viewModel.setupForm.setPlayerCount(1)
        viewModel.setupForm.setGameMode(GameMode.D20)
        viewModel.startGame()
        showcase.show {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    DiceFiveTheme { GameScreen(viewModel = viewModel) }
                }
            }
        }
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val node = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    /** One line, no wider than a compact tile less the label's inset each side. */
    private fun assertOneLine(layout: TextLayoutResult, label: String) {
        assertEquals(1, layout.lineCount, label)
        // The whole mark on that line, not one broken at its hyphen with the rest cut off.
        assertEquals(label.length, layout.getLineEnd(0), label)
        val room = layout.layoutInput.density.run { (40.dp - 3.dp * 2).toPx() }
        val width = layout.getLineRight(0) - layout.getLineLeft(0)
        assertTrue(width <= room, "$label $width > $room")
    }

    private val TextLayoutResult.fontSize: TextUnit
        get() = layoutInput.style.fontSize

    @Test
    fun `the wildcards keep their own size at the default font`() = uiTest(SCREEN) {
        showD20(fontScale = 1f)
        for (label in listOf("1-10", "11-20")) {
            val layout = layoutOf(label)
            assertOneLine(layout, label)
            // A compact tile's 16sp, at the wildcard's 0.62.
            assertEquals(16f * 0.62f, layout.fontSize.value, 0.01f, label)
        }
    }

    @Test
    fun `the wildcards shrink onto one line at a 200 percent font`() = uiTest(SCREEN) {
        showD20(fontScale = 2f)
        for (label in listOf("1-10", "11-20")) {
            val layout = layoutOf(label)
            assertOneLine(layout, label)
            assertTrue(layout.fontSize < (16f * 0.62f).sp && layout.fontSize >= 6.sp, "$label ${layout.fontSize}")
        }
    }
}
