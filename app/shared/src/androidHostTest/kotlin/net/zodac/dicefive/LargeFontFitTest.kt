package net.zodac.dicefive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.dicefive.app.LocalAppContainer
import net.zodac.dicefive.model.PlayerColour
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.common.ShrinkToLinesText
import net.zodac.dicefive.ui.common.WholeWordsText
import net.zodac.dicefive.ui.game.PlayerHeaderBar
import net.zodac.dicefive.ui.menu.PlayButton
import net.zodac.dicefive.ui.rules.RulesScreen
import net.zodac.dicefive.ui.styles.StylesScreen
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** The screen this class's tests are drawn on: the maintainer's phone. */
private val SCREEN = TestScreen(360, 800, 3f)

/**
 * Above a 100% font scale, text shrinks to stay whole, no smaller than at 100% bar a tab's long name: names, places, and
 * labels aren't broken mid-word or cut off, and Rules' pages stop above their pill. Pixel-identical at 100% isn't asserted here: there, each of
 * these draws through the same plain path as before, behind `isLargeFont`.
 *
 * Android only: Styles and Rules step whole pages, which the desktop test harness draws frame by frame.
 */
class LargeFontFitTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun show(scale: Float, content: @Composable () -> Unit) {
        showcase.show {
            CompositionLocalProvider(
                LocalAppContainer provides inMemoryAppContainer(),
                LocalPlatformServices provides SilentPlatformServices,
                LocalReduceMotion provides true,
            ) {
                DiceFiveTheme {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                        Box(Modifier.fillMaxSize()) { content() }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }

    private fun players(names: List<String>) = names.mapIndexed { i, name ->
        val player = PlayerState(name = name, type = PlayerType.HUMAN, colour = PlayerColour.entries[i])
        player.copy(scorecard = player.scorecard + (ScoreCategory.CHANCE to listOf(if (i == 0) 25 else 7)))
    }

    @Test
    fun `four long names stay whole on one line in their tabs - and each place is uncut - at every large font`() = uiTest(SCREEN) {
        for (scale in listOf(1.15f, 1.3f, 1.5f, 1.8f, 2f)) {
            val names = listOf("SamSamSa", "AdaAdaAd", "JoJoJoJo", "SamAdaJo")
            show(scale) { PlayerHeaderBar(players(names), currentPlayerIndex = 1, viewedPlayerIndex = null, enabled = true, onPlayerTap = {}) }
            for (name in names) {
                val layout = compose.onNodeWithText(name, useUnmergedTree = true).layout()
                assertEquals(1, layout.lineCount, "$name at $scale")
                assertFalse(layout.isLineEllipsized(0), "$name at $scale")
            }
            val place = compose.onAllNodes(hasText("=2nd"), useUnmergedTree = true)[0]
            assertEquals(1, place.layout().lineCount, "=2nd at $scale")
            assertFalse(place.layout().isLineEllipsized(0), "=2nd at $scale")
        }
    }

    @Test
    fun `a long name shrunk to fit shrinks its line height and letter spacing with it`() = uiTest(SCREEN) {
        lateinit var full: TextStyle
        val names = listOf("SamSamSa", "AdaAdaAd", "JoJoJoJo", "SamAdaJo")
        show(2f) {
            full = MaterialTheme.typography.labelMedium
            PlayerHeaderBar(players(names), currentPlayerIndex = 1, viewedPlayerIndex = null, enabled = true, onPlayerTap = {})
        }
        val shrunk = compose.onNodeWithText("SamSamSa", useUnmergedTree = true).layout().layoutInput.style
        val ratio = shrunk.fontSize.value / full.fontSize.value
        assertTrue(ratio < 1f, "it needed to shrink for this test to say anything")
        assertEquals(full.lineHeight.value * ratio, shrunk.lineHeight.value, 0.01f)
        assertEquals(full.letterSpacing.value * ratio, shrunk.letterSpacing.value, 0.01f)
    }

    @Test
    fun `a name too wide for its tab even at its smallest is cut short on one line - it never wraps`() = uiTest(SCREEN) {
        val names = listOf("WWWWWWWWWWWWWWWW", "Ada", "Jo", "Alex")
        for (scale in listOf(1.15f, 1.3f, 1.5f, 1.8f, 2f)) {
            show(scale) { PlayerHeaderBar(players(names), currentPlayerIndex = 1, viewedPlayerIndex = null, enabled = true, onPlayerTap = {}) }
            val layout = compose.onNodeWithText("WWWWWWWWWWWWWWWW", useUnmergedTree = true).layout()
            assertEquals(1, layout.lineCount, "at $scale")
            assertTrue(layout.isLineEllipsized(0), "at $scale")
        }
    }

    @Test
    fun `a short name keeps its full large size`() = uiTest(SCREEN) {
        show(2f) { PlayerHeaderBar(players(listOf("Sam", "Ada", "Jo", "Alex")), currentPlayerIndex = 1, viewedPlayerIndex = null, enabled = true, onPlayerTap = {}) }
        val style = compose.onNodeWithText("Sam", useUnmergedTree = true).layout().layoutInput.style
        // labelMedium, untouched.
        assertEquals(12f, style.fontSize.value)
    }

    @Test
    fun `the split Play button keeps Continue in one piece at every large font - however its row asks it to measure`() = uiTest(SCREEN) {
        for (scale in listOf(1.15f, 1.3f, 1.5f, 1.8f, 2f)) {
            show(scale) { PlayButton(hasInProgressGame = true, onContinue = {}, onNewGame = {}) }
            assertEquals(1, compose.onNodeWithText("Continue", useUnmergedTree = true).layout().lineCount, "at $scale")
        }
    }

    @Test
    fun `whole words shrink only as far as the longest needs - and wrap between words - never below their size at 100 percent`() = uiTest(SCREEN) {
        show(2f) {
            val style = MaterialTheme.typography.titleMedium
            Box(Modifier.width(120.dp)) { WholeWordsText(text = "Milestones Milestones", style = style) }
        }
        val fits = compose.onNodeWithText("Milestones Milestones", useUnmergedTree = true).layout()
        assertEquals(2, fits.lineCount)
        assertEquals("Milestones ", fits.layoutInput.text.text.substring(fits.getLineStart(0), fits.getLineEnd(0)))
        assertTrue(fits.layoutInput.style.fontSize.value in 8f..<16f)

        // Wider than even its size at 100% allows: it stops there, at 16sp of 2x, and doesn't go on shrinking.
        show(2f) {
            val style = MaterialTheme.typography.titleMedium
            Box(Modifier.width(10.dp)) { WholeWordsText(text = "Milestones", style = style) }
        }
        assertEquals(8f, compose.onNodeWithText("Milestones", useUnmergedTree = true).layout().layoutInput.style.fontSize.value)
    }

    @Test
    fun `a long description fits its two lines - in the same height as a short one`() = uiTest(SCREEN) {
        val long = "Roll a large straight on the first roll of a turn"
        var style = TextStyle.Default
        show(2f) {
            style = MaterialTheme.typography.bodySmall
            Box(Modifier.width(200.dp)) { ShrinkToLinesText(text = AnnotatedString(long), style = style, lines = 2) }
        }
        val layout = compose.onNodeWithText(long, useUnmergedTree = true).layout()
        assertFalse(layout.hasVisualOverflow)
        val longHeight = compose.onNodeWithText(long, useUnmergedTree = true).fetchSemanticsNode().parent?.size?.height
        show(2f) {
            Box(Modifier.width(200.dp)) { ShrinkToLinesText(text = AnnotatedString("Short"), style = style, lines = 2) }
        }
        assertEquals(longHeight, compose.onNodeWithText("Short", useUnmergedTree = true).fetchSemanticsNode().parent?.size?.height)
    }

    @Test
    fun `no style's name runs past its tile at the largest font`() = uiTest(SCREEN) {
        show(2f) { StylesScreen(viewModel = stylesViewModel(), onBack = {}) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        // Dice tiles (64dp) hold a name of up to their own width, at the 100% size if it needs to go that far.
        val classic = compose.onAllNodes(hasText("Classic"), useUnmergedTree = true)[0].fetchSemanticsNode().size.width
        assertTrue(classic <= with(compose.density) { 80.dp.roundToPx() }, "Classic is $classic wide")
    }

    @Test
    fun `at a large font Rules' words stop above the page pill`() = uiTest(SCREEN) {
        show(1.3f) { RulesScreen(onBack = {}) }
        val pill = compose.onNode(hasContentDescription(", page ", substring = true), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val words = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
            .filter { node -> node.config[SemanticsProperties.Text].any { it.text.length > 40 } }
        assertTrue(words.isNotEmpty())
        words.forEach { assertTrue(it.boundsInRoot.bottom <= pill.top + 1f, "${it.boundsInRoot.bottom} reaches behind the pill at ${pill.top}") }
    }
}
