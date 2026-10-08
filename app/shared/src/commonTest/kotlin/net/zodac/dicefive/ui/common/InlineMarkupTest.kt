package net.zodac.dicefive.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineMarkupTest {

    private val bold = SpanStyle(fontWeight = FontWeight.Bold)
    private val italic = SpanStyle(fontStyle = FontStyle.Italic)
    private val underline = SpanStyle(textDecoration = TextDecoration.Underline)
    private val mono = SpanStyle(fontFamily = FontFamily.Monospace)

    private fun AnnotatedString.styles() = spanStyles.map { Triple(it.item, it.start, it.end) }.toSet()

    @Test
    fun `each marker applies its style and is removed - nesting and overlapping - and plain text passes through unstyled`() {
        val plain = parseInlineMarkup("Roll five dice - up to three times.")
        assertEquals("Roll five dice - up to three times.", plain.text)
        assertTrue(plain.spanStyles.isEmpty())

        val each = parseInlineMarkup("a **b** *c* _d_ __e__ `f`")
        assertEquals("a b c d e f", each.text)
        assertEquals(setOf(Triple(bold, 2, 3), Triple(italic, 4, 5), Triple(italic, 6, 7), Triple(underline, 8, 9), Triple(mono, 10, 11)), each.styles())

        val nested = parseInlineMarkup("***both*** and **bold _mixed_**")
        assertEquals("both and bold mixed", nested.text)
        assertEquals(setOf(Triple(bold, 0, 4), Triple(italic, 0, 4), Triple(bold, 9, 19), Triple(italic, 14, 19)), nested.styles())
    }

    @Test
    fun `a marker is literal when unpaired - escaped with a backslash - or in a code span that takes the given style`() {
        // Shown literally rather than styling the rest.
        val unpaired = parseInlineMarkup("5 * 4 is **20**")
        assertEquals("5 * 4 is 20", unpaired.text)
        assertEquals(setOf(Triple(bold, 9, 11)), unpaired.styles())
        assertEquals("a `b", parseInlineMarkup("a `b").text)

        val escaped = parseInlineMarkup("""\*not italic\* and \_ and \\""")
        assertEquals("""*not italic* and _ and \""", escaped.text)
        assertTrue(escaped.spanStyles.isEmpty())

        val codeStyle = SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
        val code = parseInlineMarkup("see `**Fours**`", codeStyle)
        assertEquals("see **Fours**", code.text)
        assertEquals(setOf(Triple(codeStyle, 4, 13)), code.styles())
    }
}
