package net.zodac.dicefive

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The launcher icon's layers, drawn at [PIXELS_PER_UNIT] pixels for each unit of their 108-unit canvas. */
@RunWith(AndroidJUnit4::class)
class LauncherIconTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun render(layer: Int): Bitmap {
        val size = CANVAS_UNITS * PIXELS_PER_UNIT
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val drawable = checkNotNull(ContextCompat.getDrawable(context, layer))
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    @Test
    fun `the wrapper has all three layers`() {
        val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) as AdaptiveIconDrawable
        assertNotNull(icon.background)
        assertNotNull(icon.foreground)
        assertNotNull(icon.monochrome)
    }

    @Test
    fun `the background is opaque to every edge of the canvas, where launchers move it about`() {
        val background = render(R.drawable.ic_launcher_background)
        val pixels = IntArray(background.width * background.height)
        background.getPixels(pixels, 0, background.width, 0, 0, background.width, background.height)
        assertEquals(0, pixels.count { Color.alpha(it) != OPAQUE })
    }

    @Test
    fun `the mat's gold ring shows against the blue felt and mat`() {
        val background = render(R.drawable.ic_launcher_background)
        // Left of the cup, the ring crosses this column at about 79 units down; everything else there is blue.
        val column = 25 * PIXELS_PER_UNIT
        val gold = (75 * PIXELS_PER_UNIT..83 * PIXELS_PER_UNIT).count {
            val pixel = background.getPixel(column, it)
            Color.red(pixel) > Color.blue(pixel)
        }
        assertTrue("Gold pixels: $gold", gold > 0)
    }

    @Test
    fun `the cup's band ends on its sides, without sticking out past them`() {
        val foreground = render(R.drawable.ic_launcher_foreground)
        // The cup's left side is at 37 inner units and the band's end at 78.5, inside the icon's 0.8 zoom about 54.
        val justOutsideCup = ((54 + (36.5 - 54) * 0.8) * PIXELS_PER_UNIT).toInt()
        val atBandHeight = ((54 + (78.5 - 54) * 0.8) * PIXELS_PER_UNIT).toInt()
        assertEquals(0, Color.alpha(foreground.getPixel(justOutsideCup, atBandHeight)))
    }

    private companion object {
        const val CANVAS_UNITS = 108
        const val PIXELS_PER_UNIT = 10
        const val OPAQUE = 255
    }
}
