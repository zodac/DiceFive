package net.zodac.dicefive.model

/**
 * How much of a name tab a typed name takes, in "characters' worth" of Latin width - the unit
 * [net.zodac.dicefive.ui.game.GameSetupState.maxPlayerNameLength] is in. A character is one, except that the full-width
 * scripts (Chinese, Japanese, Korean and emoji) take two, and combining marks, joiners and variation selectors none:
 * counting UTF-16 units would let twelve Chinese characters through to a tab that holds six, and cut an emoji or an accent in half.
 * A rough measure - not a font's - which is all a cap that picks "about this wide" needs.
 */
fun String.nameWidth(): Int {
    var width = 0
    forEachCodePoint { width += codePointWidth(it) }
    return width
}

/** The longest start of this name that is no wider than [maxWidth], never ending inside a character. */
fun String.takeNameWidth(maxWidth: Int): String {
    var width = 0
    var index = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        val size = if (codePoint > 0xFFFF) 2 else 1
        val next = width + codePointWidth(codePoint)
        if (next > maxWidth) break
        width = next
        index += size
    }
    return substring(0, index)
}

private inline fun String.forEachCodePoint(action: (Int) -> Unit) {
    var index = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        action(codePoint)
        index += if (codePoint > 0xFFFF) 2 else 1
    }
}

internal fun String.codePointAt(index: Int): Int {
    val high = this[index]
    if (high.isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) {
        return 0x10000 + ((high.code - 0xD800) shl 10) + (this[index + 1].code - 0xDC00)
    }
    return high.code
}

private fun codePointWidth(codePoint: Int): Int = when {
    ZERO_WIDTH.any { codePoint in it } -> 0
    WIDE.any { codePoint in it } -> 2
    else -> 1
}

private val ZERO_WIDTH = listOf(
    0x0300..0x036F, 0x0483..0x0489, 0x0591..0x05BD, 0x05BF..0x05BF, 0x05C1..0x05C2, 0x05C4..0x05C5, 0x05C7..0x05C7,
    0x0610..0x061A, 0x064B..0x065F, 0x0670..0x0670, 0x06D6..0x06DC, 0x06DF..0x06E4, 0x06E7..0x06E8, 0x06EA..0x06ED,
    0x0900..0x0902, 0x093A..0x093A, 0x093C..0x093C, 0x0941..0x0948, 0x094D..0x094D,
    0x0E31..0x0E31, 0x0E34..0x0E3A, 0x0E47..0x0E4E, 0x1AB0..0x1AFF, 0x1DC0..0x1DFF,
    0x200B..0x200F, 0x202A..0x202E, 0x2060..0x2064, 0x20D0..0x20FF, 0xFE00..0xFE0F, 0xFE20..0xFE2F,
)

private val WIDE = listOf(
    0x1100..0x115F, 0x2E80..0x303E, 0x3041..0xA4CF, 0xAC00..0xD7A3, 0xF900..0xFAFF, 0xFE30..0xFE6F, 0xFF00..0xFF60, 0xFFE0..0xFFE6,
    0x1F300..0x1FAFF, 0x20000..0x3FFFD,
)
