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
    fun `plain text passes through unstyled`() {
        val result = parseInlineMarkup("Roll five dice - up to three times.")
        assertEquals("Roll five dice - up to three times.", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `each marker applies its style and is removed`() {
        val result = parseInlineMarkup("a **b** *c* _d_ __e__ `f`")
        assertEquals("a b c d e f", result.text)
        assertEquals(
            setOf(
                Triple(bold, 2, 3),
                Triple(italic, 4, 5),
                Triple(italic, 6, 7),
                Triple(underline, 8, 9),
                Triple(mono, 10, 11),
            ),
            result.styles(),
        )
    }

    @Test
    fun `markers can nest and overlap`() {
        val result = parseInlineMarkup("***both*** and **bold _mixed_**")
        assertEquals("both and bold mixed", result.text)
        assertEquals(
            setOf(Triple(bold, 0, 4), Triple(italic, 0, 4), Triple(bold, 9, 19), Triple(italic, 14, 19)),
            result.styles(),
        )
    }

    @Test
    fun `an unpaired marker is shown literally rather than styling the rest`() {
        val result = parseInlineMarkup("5 * 4 is **20**")
        assertEquals("5 * 4 is 20", result.text)
        assertEquals(setOf(Triple(bold, 9, 11)), result.styles())

        assertEquals("a `b", parseInlineMarkup("a `b").text)
    }

    @Test
    fun `backslash escapes a marker`() {
        val result = parseInlineMarkup("""\*not italic\* and \_ and \\""")
        assertEquals("""*not italic* and _ and \""", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `code spans keep markers literal and take the given style`() {
        val codeStyle = SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
        val result = parseInlineMarkup("see `**Fours**`", codeStyle)
        assertEquals("see **Fours**", result.text)
        assertEquals(setOf(Triple(codeStyle, 4, 13)), result.styles())
    }
}
