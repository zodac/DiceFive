package net.zodac.dicefive.ui.styles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.game.style.StyleCategory

/**
 * How a category page's tiles are sized to its screen at a larger font or display size: full size where they fit,
 * smaller where that fits them (down to a cup's 64dp from 88dp, and never a tile under 48dp), and full size again,
 * scrolling, where even that doesn't.
 */
class FittedTileScaleTest {

    // A 360dp phone at three pixels to a dp: its page 336dp wide inside the margins, names 16dp a line.
    private val density = 3f
    private val width = (336 * density).toInt()
    private val label = 16 * density

    // Twenty cups, five lines of four at full size: 12 + 5 x (88 + 4 + 16) + 4 x 12 + 12 = 612dp.
    private fun scale(heightDp: Int, category: StyleCategory = StyleCategory.DICE_CUP, count: Int = 20) =
        fittedTileScale(category, count, width, (heightDp * density).toInt(), density, label)

    @Test
    fun `tiles that fit are full size`() {
        assertEquals(1f, scale(612))
        assertEquals(1f, scale(700))
    }

    @Test
    fun `tiles a little too tall are drawn just small enough to fit - smaller for less room - never below the floor`() {
        val roomier = scale(580)
        val tighter = scale(540)
        assertTrue(roomier < 1f && tighter < roomier && tighter >= MIN_TILE_FIT_SCALE, "580dp: $roomier, 540dp: $tighter")
        // Just small enough: a dp more room never needs a smaller scale.
        for (height in 500..612) assertTrue(scale(height + 1) >= scale(height) || scale(height) == 1f, "at $height")
    }

    @Test
    fun `tiles that wouldn't fit even at the floor are full size - the page scrolls instead`() {
        assertEquals(1f, scale(300))
    }

    @Test
    fun `no tile is drawn under the 48dp touch target - a mat stops short of the cups' floor`() {
        assertEquals(64f / 88f, StyleCategory.DICE_CUP.minTileFitScale())
        val mat = StyleCategory.MAT.minTileFitScale()
        assertEquals(48f / 65f, mat, 0.0001f)
        for (category in StyleCategory.entries) {
            val size = category.previewSize
            assertTrue(minOf(size.width, size.height).value * category.minTileFitScale() >= 48f - 0.01f, "$category")
        }
        // Forty mats on a page with room for them only at the cups' floor: full size, scrolling.
        val needsCupFloor = (1..2000).first { scale(it, StyleCategory.MAT, 40) != 1f || scale(it + 1, StyleCategory.MAT, 40) == 1f }
        assertTrue(scale(needsCupFloor, StyleCategory.MAT, 40).let { it == 1f || it >= mat })
    }

    @Test
    fun `no tiles, or no room measured yet, are full size`() {
        assertEquals(1f, fittedTileScale(StyleCategory.DICE_CUP, 0, width, 1000, density, label))
        assertEquals(1f, fittedTileScale(StyleCategory.DICE_CUP, 20, width, 0, density, label))
    }
}
