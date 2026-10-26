package net.zodac.dicefive

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.dicefive.ui.common.FittedCountText
import net.zodac.dicefive.ui.common.percentOf
import net.zodac.dicefive.ui.theme.DiceFiveTheme

/** The count banner stays on one line: it shrinks, then drops its word, then becomes a percentage. */
class FittedCountTextTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun show(width: Int) {
        showcase.show {
            DiceFiveTheme {
                FittedCountText(
                    full = "12 of 120 earned",
                    short = "12 of 120",
                    percent = "10%",
                    style = TextStyle(fontSize = 20.sp),
                    modifier = Modifier.width(width.dp),
                )
            }
        }
    }

    private fun layout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.first()
    }

    @Test
    fun `the banner keeps its full text and size when it fits - shrinks when it doesn't - then drops the word - then shows a percentage - never on two lines`() = uiTest {
        show(width = 300)
        assertEquals(20f, layout("12 of 120 earned").layoutInput.style.fontSize.value, 0.01f)

        show(width = 120)
        val shrunk = layout("12 of 120 earned").layoutInput
        assert(shrunk.style.fontSize.value in 12f..19.5f) { "${shrunk.style.fontSize}" }
        assertEquals(1, shrunk.maxLines)

        show(width = 80)
        assertEquals(1, layout("12 of 120").lineCount)

        show(width = 30)
        assertEquals(12f, layout("10%").layoutInput.style.fontSize.value, 0.01f)
    }

    @Test
    fun `the percentage rounds down so only all of them says 100 - and nothing at all says 0`() {
        assertEquals(99, percentOf(199, 200))
        assertEquals(100, percentOf(5, 5))
        assertEquals(0, percentOf(0, 0))
    }
}
