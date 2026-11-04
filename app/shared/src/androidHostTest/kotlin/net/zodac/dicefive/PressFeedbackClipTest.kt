package net.zodac.dicefive

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.launch
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.ui.achievements.AchievementsScreen
import net.zodac.dicefive.ui.common.ChoicePicker
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** A press on a rounded card stays inside its corners: a click on a card's `modifier` sits outside the card's own clip. */
class PressFeedbackClipTest : UiTest() {

    /** Paints the whole of whatever's pressed, unlike a ripple (which Robolectric doesn't draw), so a missing clip shows in its corners. */
    private object FillOnPress : IndicationNodeFactory {
        override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node(), DrawModifierNode {
            private var pressed = false

            override fun onAttach() {
                coroutineScope.launch {
                    interactionSource.interactions.collect {
                        pressed = it is PressInteraction.Press
                        invalidateDraw()
                    }
                }
            }

            override fun ContentDrawScope.draw() {
                drawContent()
                if (pressed) drawRect(Color.Magenta)
            }
        }

        override fun equals(other: Any?) = other === this

        override fun hashCode() = 0
    }

    private fun ImageBitmap.at(x: Float, y: Float) = toPixelMap()[x.toInt(), y.toInt()]

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            DiceFiveTheme { CompositionLocalProvider(LocalIndication provides FillOnPress, content = content) }
        }
    }

    private fun assertPressStaysInCorners(node: SemanticsNodeInteraction) {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val before = compose.onRoot().captureToImage()
        compose.mainClock.autoAdvance = false
        node.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(400)
        val pressed = compose.onRoot().captureToImage()

        assertEquals(before.at(bounds.left, bounds.top), pressed.at(bounds.left, bounds.top), "The press reached the corner")
        assertEquals(Color.Magenta, pressed.at(bounds.center.x, bounds.top + 2), "The press didn't show")
    }

    @Test
    fun `a picker field's press stays inside its rounded corners`() = uiTest(TestScreen(360, 800)) {
        show {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                ChoicePicker(title = "Thing", options = listOf("One"), selected = "One", onSelect = {}, label = { it }, description = { "About $it" })
            }
        }
        assertPressStaysInCorners(compose.onNodeWithText("One"))
    }

    @Test
    fun `an achievement row's press stays inside its rounded corners`() = uiTest(TestScreen(360, 800)) {
        val viewModel = achievementsViewModel()
        show { CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) { AchievementsScreen(viewModel = viewModel, onBack = {}) } }
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasText("The Journey Begins")).fetchSemanticsNodes().isNotEmpty() }
        // Only a row with a tip to show takes a press: the first one wholly on screen, as the list scrolls down to one.
        val hasTip = SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)
        val wholeRow = (0..ACHIEVEMENT_ROWS_SEARCHED).firstNotNullOf { index ->
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(index)
            compose.waitForIdle()
            compose.onAllNodes(hasTip).fetchSemanticsNodes().firstOrNull { it.boundsInRoot.height.toInt() == it.size.height }
        }
        assertPressStaysInCorners(compose.onNode(SemanticsMatcher("that row") { it.id == wholeRow.id }))
    }

    private companion object {
        const val ACHIEVEMENT_ROWS_SEARCHED = 120
    }
}
