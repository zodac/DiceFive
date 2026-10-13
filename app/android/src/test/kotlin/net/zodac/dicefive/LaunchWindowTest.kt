package net.zodac.dicefive

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.ui.theme.Surface
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The window shown before the app's first frame is drawn. */
@RunWith(AndroidJUnit4::class)
class LaunchWindowTest {

    @Test
    fun `the window is the theme's surface colour, so the launch screen hands over without a change of colour`() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_DiceFive)
        val attributes = context.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
        val background = try {
            attributes.getDrawable(0)
        } finally {
            attributes.recycle()
        }
        assertEquals(Surface.toArgb(), (background as ColorDrawable).color)
    }
}
