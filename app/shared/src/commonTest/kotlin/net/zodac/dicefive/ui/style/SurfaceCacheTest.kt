package net.zodac.dicefive.ui.style

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import net.zodac.dicefive.UiTest

/**
 * A painted surface is stamped from the cache on later draws, until the cache's pixel memory is over its budget (the
 * least recently drawn goes first) or the platform releases it, when it's painted again.
 */
class SurfaceCacheTest : UiTest() {

    /** How many surfaces drawing [key] at [side] x [side] painted: 1 if it wasn't cached, 0 if it was stamped. */
    private fun paintsDrawing(key: String, side: Int): Int {
        val before = surfacesPaintedForTest()
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(1, 1)), Size(side.toFloat(), side.toFloat())) {
            drawCachedSurface("SurfaceCacheTest:$key") { drawRect(Color.Red) }
        }
        return surfacesPaintedForTest() - before
    }

    @Test
    fun `a surface is stamped once painted, and painted again once released`() = uiTest {
        releaseCachedSurfaces()
        assertEquals(1, paintsDrawing("die", 100))
        assertEquals(0, paintsDrawing("die", 100))
        releaseCachedSurfaces()
        assertEquals(1, paintsDrawing("die", 100))
    }

    @Test
    fun `surfaces over the pixel budget let go of the least recently drawn`() = uiTest {
        releaseCachedSurfaces()
        // 36MB, then 16MB: over the 48MB budget together, so the older goes.
        assertEquals(1, paintsDrawing("large", 3000))
        assertEquals(1, paintsDrawing("medium", 2000))
        assertEquals(0, paintsDrawing("medium", 2000))
        assertEquals(1, paintsDrawing("large", 3000))
        releaseCachedSurfaces()
    }

    /** What drawing the stars for [seed] at [seconds] on a [side] x [side] surface comes to. */
    private fun starPixels(seed: Int, seconds: Float, side: Int): List<Int> {
        val image = ImageBitmap(side, side)
        // Dense enough for each star to have a solid middle, not only soft edges.
        CanvasDrawScope().draw(Density(4f), LayoutDirection.Ltr, Canvas(image), Size(side.toFloat(), side.toFloat())) {
            drawStars(seed = seed, color = Color.White, seconds = seconds)
        }
        return IntArray(side * side).also { image.readPixels(it) }.toList()
    }

    /** How many star fields doing [draw] built. */
    private fun starFieldsBuiltBy(draw: () -> Unit): Int {
        val before = starFieldsBuiltForTest()
        draw()
        return starFieldsBuiltForTest() - before
    }

    @Test
    fun `stars are built once for a surface, and a twinkle only changes how bright they are`() = uiTest {
        releaseCachedSurfaces()
        var first = emptyList<Int>()
        assertEquals(1, starFieldsBuiltBy { first = starPixels(seed = 9, seconds = 0f, side = 400) })
        var later = emptyList<Int>()
        assertEquals(0, starFieldsBuiltBy { later = starPixels(seed = 9, seconds = 3f, side = 400) })
        assertNotEquals(first, later)
        // In the same places, whichever beat each star is on: even at its dimmest, a star's solid middle still shows.
        val middles = first.indices.filter { first[it] ushr 24 >= 128 }
        assertTrue(middles.isNotEmpty())
        assertTrue(middles.all { later[it] != 0 })

        // Another size or seed is a field of its own.
        assertEquals(1, starFieldsBuiltBy { starPixels(seed = 9, seconds = 0f, side = 200) })
        assertEquals(1, starFieldsBuiltBy { starPixels(seed = 5, seconds = 0f, side = 400) })
    }

    @Test
    fun `stars built again once released are the same stars`() = uiTest {
        releaseCachedSurfaces()
        val first = starPixels(seed = 9, seconds = 1f, side = 200)
        releaseCachedSurfaces()
        var again = emptyList<Int>()
        assertEquals(1, starFieldsBuiltBy { again = starPixels(seed = 9, seconds = 1f, side = 200) })
        assertEquals(first, again)
    }
}
