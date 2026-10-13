package net.zodac.dicefive.ui.game.style

import java.awt.Font
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The cut-down DiceFive Maths fonts (`scripts/subset-maths-fonts.sh`) hold every character the Maths
 * and Poker dice draw in them, and are named DiceFive Maths: the OFL keeps the MathJax originals'
 * Reserved Font Names for the unmodified fonts. JVM-only: it reads the font files in the source tree.
 */
class MathsFontCoverageTest {

    private val regular = font("dicefive_maths_regular.otf")
    private val italic = font("dicefive_maths_italic.otf")

    @Test
    fun `the regular font has every character the Maths and Poker dice draw in it`() {
        val drawn = MathsRegularCharacters + PokerRanks.joinToString("").toSet()

        assertEquals(emptyList(), drawn.filterNot(regular::canDisplay), "Missing from the font - add them to scripts/subset-maths-fonts.sh and re-run it")
    }

    @Test
    fun `the italic font has every character the Maths dice draw in it`() {
        assertEquals(emptyList(), MathsItalicCharacters.filterNot(italic::canDisplay), "Missing from the font - add them to scripts/subset-maths-fonts.sh and re-run it")
    }

    @Test
    fun `the fonts are named DiceFive Maths, not MathJax`() {
        assertEquals("DiceFive Maths", regular.family)
        assertEquals("DiceFive Maths Italic", italic.family)
        for (font in listOf(regular, italic)) {
            assertTrue("MathJax" !in font.fontName && "MathJax" !in font.psName, "${font.fontName} (${font.psName}) keeps a Reserved Font Name")
        }
    }

    private fun font(name: String): Font = Font.createFont(Font.TRUETYPE_FONT, File("src/commonMain/composeResources/font/$name"))
}
