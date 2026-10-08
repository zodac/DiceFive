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

/**
 * Text that used to be shrunk below 12sp to fit one line now wraps instead, and text that fits is left
 * alone. This pins which size and line limit are chosen - not how it looks, which is for a device.
 */
@RunWith(AndroidJUnit4::class)
class ShrinkThenWrapTextTest {

    @get:Rule
    val compose = createComposeRule()

    private val showcase = Showcase(compose)

    private fun show(text: String, width: Int, wrappedMaxLines: Int = 2) {
        showcase.show {
            DiceFiveTheme {
                ShrinkThenWrapText(
                    text = text,
                    style = TextStyle(fontSize = 16.sp),
                    modifier = Modifier.width(width.dp),
                    wrappedMaxLines = wrappedMaxLines,
                )
            }
        }
    }

    private fun layoutInput(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.first()
    }

    @Test
    fun `a text that fits stays on one line at its own size - one too wide even at the smallest wraps at the floor on as many lines as allowed`() {
        show("Sound effects", width = 200)
        val fits = layoutInput("Sound effects").layoutInput
        assertEquals(16f, fits.style.fontSize.value, 0.01f)
        assertEquals(1, fits.maxLines)

        // Rather than shrinking further.
        val text = "Where We're Going, We Don't Need Rules"
        show(text, width = 30)
        val wrapped = layoutInput(text).layoutInput
        assertEquals(12f, wrapped.style.fontSize.value, 0.01f)
        assertEquals(2, wrapped.maxLines)

        show(text, width = 30, wrappedMaxLines = 3)
        assertEquals(3, layoutInput(text).layoutInput.maxLines)
    }
}
