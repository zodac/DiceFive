package net.zodac.dicefive.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/** The inline markers [parseInlineMarkup] understands, longest first so `**` wins over `*`. */
private enum class Marker(val token: String, val style: SpanStyle) {
    BOLD("**", SpanStyle(fontWeight = FontWeight.Bold)),
    UNDERLINE("__", SpanStyle(textDecoration = TextDecoration.Underline)),
    ITALIC_STAR("*", SpanStyle(fontStyle = FontStyle.Italic)),
    ITALIC_UNDERSCORE("_", SpanStyle(fontStyle = FontStyle.Italic)),
}

private sealed interface Token {
    data class Plain(val text: String) : Token
    data class Delimiter(val marker: Marker) : Token
    data class Code(val text: String) : Token
}

/**
 * A small Markdown-like subset for hand-written body text (the rules pages): `**bold**`,
 * `*italic*` or `_italic_`, `__underlined__` and `` `monospace` ``. Deliberately not full Markdown -
 * just enough to highlight a word or two without pulling in a Markdown library.
 *
 * - A marker with no partner (a stray `*`, say) is shown as-is rather than styling the rest of the
 *   paragraph, so a typo is visible but harmless.
 * - A backslash shows the next character literally: `\*` is an asterisk, `\\` a backslash.
 * - Text between backticks is shown literally, markers and all; [codeStyle] styles it.
 */
fun parseInlineMarkup(
    text: String,
    codeStyle: SpanStyle = SpanStyle(fontFamily = FontFamily.Monospace),
): AnnotatedString {
    val tokens = tokenise(text)

    // Pair each marker kind's occurrences in order (1st with 2nd, 3rd with 4th...); an odd one out
    // at the end has no partner and stays literal.
    val paired = BooleanArray(tokens.size)
    Marker.entries.forEach { marker ->
        val positions = tokens.indices.filter { (tokens[it] as? Token.Delimiter)?.marker == marker }
        positions.take(positions.size - positions.size % 2).forEach { paired[it] = true }
    }

    return buildAnnotatedString {
        val openedAt = mutableMapOf<Marker, Int>()
        tokens.forEachIndexed { index, token ->
            when (token) {
                is Token.Plain -> append(token.text)
                is Token.Code -> {
                    val start = length
                    append(token.text)
                    addStyle(codeStyle, start, length)
                }
                is Token.Delimiter -> when {
                    !paired[index] -> append(token.marker.token)
                    token.marker in openedAt -> addStyle(token.marker.style, openedAt.remove(token.marker)!!, length)
                    else -> openedAt[token.marker] = length
                }
            }
        }
    }
}

private fun tokenise(text: String): List<Token> {
    val tokens = mutableListOf<Token>()
    val plain = StringBuilder()
    fun flushPlain() {
        if (plain.isNotEmpty()) {
            tokens += Token.Plain(plain.toString())
            plain.clear()
        }
    }

    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '\\' && i + 1 < text.length) {
            plain.append(text[i + 1])
            i += 2
            continue
        }
        if (c == '`') {
            val close = text.indexOf('`', startIndex = i + 1)
            if (close > i + 1) {
                flushPlain()
                tokens += Token.Code(text.substring(i + 1, close))
                i = close + 1
                continue
            }
        }
        val marker = Marker.entries.firstOrNull { text.startsWith(it.token, startIndex = i) }
        if (marker != null) {
            flushPlain()
            tokens += Token.Delimiter(marker)
            i += marker.token.length
            continue
        }
        plain.append(c)
        i++
    }
    flushPlain()
    return tokens
}
