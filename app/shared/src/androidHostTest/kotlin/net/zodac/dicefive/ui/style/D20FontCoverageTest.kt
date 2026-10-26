package net.zodac.dicefive.ui.style

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The cut-down DiceFive D20 font (`scripts/subset-d20-font.sh`) holds every digit the D20 draws, draws its 7 crossed
 * (Andika's `seven.Bar`) and is named DiceFive D20: the OFL keeps the Andika original's Reserved Font Names for the
 * unmodified font. JVM-only: it reads the font files in the source tree.
 */
class D20FontCoverageTest {

    private val d20 = font("src/commonMain/composeResources/font/dicefive_d20.ttf")
    private val andika = font("../licensing/sources/Andika-Bold.ttf")

    @Test
    fun `the font has every digit the D20 draws`() {
        assertEquals("", "0123456789".filterNot(d20::canDisplay), "Missing from the font - add them to scripts/subset-d20-font.sh and re-run it")
    }

    @Test
    fun `the font's 7 is crossed and its 1 has a foot`() {
        assertTrue(ink(d20, '7') > ink(andika, '7') * 1.1, "The 7 should carry Andika's crossbar")
        assertEquals(ink(andika, '1'), ink(d20, '1'), "The 1 should be Andika's own")
    }

    @Test
    fun `the font is named DiceFive D20, not Andika`() {
        assertEquals("DiceFive D20", d20.family)
        assertTrue("Andika" !in d20.fontName && "Andika" !in d20.psName, "${d20.fontName} (${d20.psName}) keeps a Reserved Font Name")
    }

    /** How many pixels [digit] fills when drawn large in [font]. */
    private fun ink(font: Font, digit: Char): Int {
        val image = BufferedImage(160, 160, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, 160, 160)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.font = font.deriveFont(120f)
        g.color = Color.BLACK
        g.drawString(digit.toString(), 30, 130)
        g.dispose()
        return (0 until 160).sumOf { y -> (0 until 160).count { x -> (image.getRGB(x, y) and 0xFF) < 128 } }
    }

    private fun font(path: String): Font = Font.createFont(Font.TRUETYPE_FONT, File(path))
}
