package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.rules.RulesScreen
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.robolectric.annotation.Config

/** A page's scrollbar thumb, on a real page: Rules, whose bar is in a margin narrower than a finger. */
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class RulesScrollbarThumbTest : UiTest() {

    private val scrolls = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private fun ComposeUiTest.place() = onNode(scrolls).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]

    private fun ComposeUiTest.showRules() {
        setContent { CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) { DiceFiveTheme { RulesScreen(onBack = {}) } } }
        waitForIdle()
        // The thumb's place comes from its drawing, which Robolectric does only for a capture.
        onRoot().captureToImage()
        assertTrue(place().maxValue() > 0f, "the page has to be too long to fit")
    }

    @Test
    fun `the thumb is pulled from over the page, past its margin, the length of the page`() = uiTest {
        showRules()
        val top = onNode(scrolls).fetchSemanticsNode().boundsInRoot.top
        onRoot().performTouchInput {
            // 8dp in from the 20dp margin, where only the thumb's handle reaches.
            down(Offset(width - 28.dp.toPx(), top + 5.dp.toPx()))
            moveBy(Offset(0f, height.toFloat()))
            up()
        }
        waitForIdle()
        assertEquals(place().maxValue(), place().value())
    }
}
