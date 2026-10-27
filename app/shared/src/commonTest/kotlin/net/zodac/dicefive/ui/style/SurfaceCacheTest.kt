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
}
