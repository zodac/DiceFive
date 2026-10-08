package net.zodac.dicefive.model

/** The most code points a name keeps, whatever it is made of: a hard bound on what is stored and drawn. */
const val MAX_PLAYER_NAME_CODE_POINTS = 64

/**
 * The most combining marks (and joiners, variation selectors) that stay on one character. Four is more than
 * any real script stacks on a letter (Hebrew with points and a cantillation mark, Arabic with a shadda, a vowel
 * and a small letter), and far less than the dozens "glitch text" piles up to run over the lines around it.
 */
const val MAX_MARKS_PER_CHARACTER = 4

/** A flag's regional subdivision (England, Scotland, Wales) is a base emoji and seven tag characters, so tags get their own, longer run. */
private const val MAX_TAGS_PER_CHARACTER = 8

private const val ZERO_WIDTH_NON_JOINER = 0x200C
private const val ZERO_WIDTH_JOINER = 0x200D
private const val LEFT_TO_RIGHT_MARK = 0x200E
private const val RIGHT_TO_LEFT_MARK = 0x200F

/**
 * [raw] as a name that is safe to draw, store and compare, whatever was typed or pasted. Nothing is censored and no
 * script is excluded: only what can break the layout or hide in it is removed.
 *
 * - Control characters go, and every kind of whitespace (a pasted newline or tab, a no-break or ideographic space)
 *   becomes one plain space, a run of them just one.
 * - Invisible and direction-changing characters go: zero-width spaces, the bidirectional overrides and isolates
 *   (which can reorder the text around the name), soft hyphens, byte-order marks, object-replacement and
 *   replacement characters, private use, non-characters, unpaired surrogates, and the blank Hangul fillers that
 *   let a name look empty. Zero-width joiners and non-joiners and the left/right marks stay, since emoji sequences
 *   and Persian, Indic and mixed-direction names need them.
 * - No more than [MAX_MARKS_PER_CHARACTER] combining marks stay on a character, and none on nothing (the start of
 *   the name, or after a space), so stacked "glitch" text can't grow tall or wide.
 * - At most [MAX_PLAYER_NAME_CODE_POINTS] code points are kept.
 * - The result is in canonical composed form (NFC), so the same name typed on two keyboards is the same string.
 *
 * Idempotent, and it does not trim: a trailing space has to survive while a second word is being typed.
 */
fun sanitizePlayerName(raw: String): String {
    val kept = StringBuilder(raw.length)
    var codePoints = 0
    var afterBase = false
    var marks = 0
    var tags = 0
    var index = 0
    while (index < raw.length && codePoints < MAX_PLAYER_NAME_CODE_POINTS) {
        val codePoint = raw.codePointAt(index)
        index += if (codePoint > 0xFFFF) 2 else 1
        when (val kind = classify(codePoint)) {
            CodeKind.DROP -> Unit
            CodeKind.SPACE -> if (kept.isEmpty() || kept.last() != ' ') {
                kept.append(' ')
                codePoints++
                afterBase = false
            }
            CodeKind.MARK, CodeKind.TAG -> if (afterBase) {
                if (kind == CodeKind.MARK) marks++ else tags++
                if (marks <= MAX_MARKS_PER_CHARACTER && tags <= MAX_TAGS_PER_CHARACTER) {
                    kept.appendScalar(codePoint)
                    codePoints++
                }
            }
            CodeKind.BASE -> {
                kept.appendScalar(codePoint)
                codePoints++
                afterBase = true
                marks = 0
                tags = 0
            }
        }
    }
    return normalizeNfc(kept.toString())
}

/**
 * What two names are compared by: sanitised, trimmed and lower-cased, so "BOB", "bob " and a name typed with a
 * decomposed accent all match the same player. Names are sanitised on the way in, so this only matters for ones
 * saved by an older version.
 */
fun playerNameKey(name: String): String = sanitizePlayerName(name).trim().lowercase()

/** Common code has no `appendCodePoint`: a character above the first plane goes in as its surrogate pair. */
private fun StringBuilder.appendScalar(codePoint: Int) {
    if (codePoint <= 0xFFFF) {
        append(codePoint.toChar())
    } else {
        val offset = codePoint - 0x10000
        append((0xD800 + (offset shr 10)).toChar())
        append((0xDC00 + (offset and 0x3FF)).toChar())
    }
}

private enum class CodeKind { BASE, MARK, TAG, SPACE, DROP }

private fun classify(codePoint: Int): CodeKind = when {
    codePoint in 0xD800..0xDFFF -> CodeKind.DROP // an unpaired surrogate
    codePoint in 0xE0020..0xE007F || codePoint == 0xE0001 -> CodeKind.TAG
    codePoint in 0xE0100..0xE01EF -> CodeKind.MARK // supplementary variation selectors
    codePoint in INVISIBLE -> CodeKind.DROP
    codePoint == ZERO_WIDTH_JOINER || codePoint == ZERO_WIDTH_NON_JOINER || codePoint == LEFT_TO_RIGHT_MARK || codePoint == RIGHT_TO_LEFT_MARK ->
        CodeKind.MARK
    codePoint > 0xFFFF -> if (codePoint in SUPPLEMENTARY_MARKS) CodeKind.MARK else if (codePoint >= 0xF0000) CodeKind.DROP else CodeKind.BASE
    else -> classifyBasic(codePoint.toChar())
}

private fun classifyBasic(char: Char): CodeKind = when {
    char.isWhitespace() -> CodeKind.SPACE
    else -> when (char.category) {
        CharCategory.SPACE_SEPARATOR, CharCategory.LINE_SEPARATOR, CharCategory.PARAGRAPH_SEPARATOR -> CodeKind.SPACE
        CharCategory.CONTROL, CharCategory.FORMAT, CharCategory.PRIVATE_USE, CharCategory.SURROGATE -> CodeKind.DROP
        CharCategory.NON_SPACING_MARK, CharCategory.ENCLOSING_MARK, CharCategory.COMBINING_SPACING_MARK -> CodeKind.MARK
        else -> CodeKind.BASE
    }
}

/** Characters that draw nothing, or that rearrange or break the text around them - none of which a name needs. */
private val INVISIBLE = listOf(
    0x00AD..0x00AD, // soft hyphen
    0x061C..0x061C, // Arabic letter mark
    0x115F..0x1160, 0x3164..0x3164, 0xFFA0..0xFFA0, // Hangul fillers
    0x180E..0x180E, // Mongolian vowel separator
    0x200B..0x200B, // zero-width space
    0x202A..0x202E, // the bidirectional embeddings and overrides
    0x2060..0x206F, // word joiner, invisible operators, the bidirectional isolates, and the deprecated shaping controls
    0xFDD0..0xFDEF, // non-characters
    0xFEFF..0xFEFF, // byte-order mark
    0xFFF0..0xFFFF, // specials: annotation anchors, the object and plain replacement characters, non-characters
)

/** The combining marks above the first plane that "glitch text" generators use, as ranges. */
private val SUPPLEMENTARY_MARKS = listOf(
    0x1D165..0x1D169, 0x1D16D..0x1D172, 0x1D17B..0x1D182, 0x1D185..0x1D18B, 0x1D1AA..0x1D1AD, 0x1D242..0x1D244,
    0x1E8D0..0x1E8D6, 0x1E944..0x1E94A,
)

private operator fun List<IntRange>.contains(codePoint: Int): Boolean = any { codePoint in it }

/** [text] in Unicode's canonical composed form (NFC), by each platform's own tables. */
internal expect fun normalizeNfc(text: String): String
