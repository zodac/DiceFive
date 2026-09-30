package net.zodac.dicefive

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.ui.common.ShrinkThenWrapText
import net.zodac.dicefive.ui.theme.DiceFiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Text that used to be shrunk below 12sp to fit one line now wraps instead, and text that fits is left
 * alone. Robolectric's text engine gives every character one pixel whatever its size and never wraps,
 * so this can pin which size and line limit are chosen - not how it looks; the shrinking steps and the
 * real widths are for a device.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ShrinkThenWrapTextTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(text: String, width: Int, wrappedMaxLines: Int = 2) {
        compose.setContent {
            DiceFiveTheme {
                ShrinkThenWrapText(
                    text = text,
                    style = TextStyle(fontSize = 16.sp),
                    modifier = Modifier.width(width.dp),
                    wrappedMaxLines = wrappedMaxLines,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun layoutInput(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.first()
    }

    @Test
    fun `a text that fits stays on one line at its own size`() {
        show("Sound effects", width = 200)

        val input = layoutInput("Sound effects").layoutInput
        assertEquals(16f, input.style.fontSize.value, 0.01f)
        assertEquals(1, input.maxLines)
    }

    @Test
    fun `a text too wide even at the smallest size wraps at the floor rather than shrinking further`() {
        val text = "Where We're Going, We Don't Need Rules"
        show(text, width = 30)

        val input = layoutInput(text).layoutInput
        assertEquals(12f, input.style.fontSize.value, 0.01f)
        assertEquals(2, input.maxLines)
    }

    @Test
    fun `a wrapped text gets as many lines as it is allowed`() {
        val text = "Where We're Going, We Don't Need Rules"
        show(text, width = 30, wrappedMaxLines = 3)

        assertEquals(3, layoutInput(text).layoutInput.maxLines)
    }
}
