package net.zodac.dicefive.ui.style

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.data.settings.DefaultStyleIds
import net.zodac.dicefive.ui.theme.TileIconColor
import net.zodac.dicefive.ui.theme.TileIndigoTop
import net.zodac.dicefive.ui.theme.TileScoredTop
import net.zodac.dicefive.ui.theme.TileSkyTop
import net.zodac.dicefive.ui.theme.TileTealBorder
import net.zodac.dicefive.ui.theme.TileTealBottom
import net.zodac.dicefive.ui.theme.TileTealTop

class ScoreBoxesTest {

    private val styles = ScoreBoxes.all

    @Test
    fun `the default is Classic Teal - the board as it was, teal with D20's sky blue and indigo halves`() {
        val classic = ScoreBoxes.default
        assertEquals(DefaultStyleIds.SCORE_BOX, classic.id)
        assertEquals(BoxPaint(TileTealTop, TileTealBottom, TileTealBorder, TileIconColor), classic.paint(BoxTone.OPEN))
        assertEquals(TileSkyTop, classic.paint(BoxTone.FIRST_HALF).top)
        assertEquals(TileIndigoTop, classic.paint(BoxTone.SECOND_HALF).top)
        assertEquals(TileScoredTop, classic.scored.top)
        assertEquals(null, classic.markFont)
    }

    @Test
    fun `every style gives D20's two halves different colours`() {
        for (style in styles) {
            val first = style.paint(BoxTone.FIRST_HALF)
            val second = style.paint(BoxTone.SECOND_HALF)
            assertTrue(apart(first, second) > MIN_DISTANCE, "${style.id}: halves $first and $second are too alike")
        }
    }

    @Test
    fun `a scored box looks spent beside an open one in every style - in either half`() {
        for (style in styles) {
            for (tone in BoxTone.entries) {
                assertTrue(apart(style.paint(tone), style.scored) > MIN_DISTANCE, "${style.id}: scored is too like $tone")
            }
        }
    }

    @Test
    fun `every mark is readable on its fill - at least 3 to 1 against both ends of the gradient`() {
        for (style in styles) {
            for (paint in BoxTone.entries.map(style::paint)) {
                for (fill in paint.fill) {
                    val ratio = contrast(paint.ink, fill)
                    assertTrue(ratio >= MIN_CONTRAST, "${style.id}: ink ${paint.ink} on $fill is $ratio to 1")
                }
            }
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = max(a.luminance(), b.luminance()) to min(a.luminance(), b.luminance())
        return (light + 0.05f) / (dark + 0.05f)
    }

    /** How different two fills look: by the fill, or by the rim where that carries the colour (Neon's tube). */
    private fun apart(a: BoxPaint, b: BoxPaint): Float = max(distance(a.top, b.top), distance(a.border, b.border))

    private fun distance(a: Color, b: Color): Float = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    private companion object {
        /** WCAG's for large text: the marks are bold and at least 16sp on a box. */
        const val MIN_CONTRAST = 3f
        const val MIN_DISTANCE = 0.15f
    }
}
