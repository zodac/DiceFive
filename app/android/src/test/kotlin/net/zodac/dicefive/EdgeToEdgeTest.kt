package net.zodac.dicefive

import android.content.Context
import android.graphics.Color
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app draws edge to edge without the APIs Android 15 deprecated, which Play reports on the release dashboard: the
 * window theme holds the bars' colours, contrast, and the cutout mode, and no code sets them.
 */
@RunWith(AndroidJUnit4::class)
class EdgeToEdgeTest {

    @Test
    // The attributes the theme has to set for the versions below Android 15, deprecated from it.
    @Suppress("DEPRECATION")
    fun `the window theme has transparent bars and no scrim, drawing into every cutout`() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_DiceFive)
        val attributes = intArrayOf(
            android.R.attr.statusBarColor,
            android.R.attr.navigationBarColor,
            android.R.attr.enforceStatusBarContrast,
            android.R.attr.enforceNavigationBarContrast,
            android.R.attr.windowLayoutInDisplayCutoutMode,
            android.R.attr.windowLightStatusBar,
            android.R.attr.windowLightNavigationBar,
        ).also { it.sort() }
        val theme = context.obtainStyledAttributes(attributes)
        fun index(attribute: Int) = attributes.indexOf(attribute)
        try {
            assertEquals(Color.TRANSPARENT, theme.getColor(index(android.R.attr.statusBarColor), Color.RED))
            assertEquals(Color.TRANSPARENT, theme.getColor(index(android.R.attr.navigationBarColor), Color.RED))
            assertFalse(theme.getBoolean(index(android.R.attr.enforceStatusBarContrast), true))
            assertFalse(theme.getBoolean(index(android.R.attr.enforceNavigationBarContrast), true))
            assertEquals(LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS, theme.getInt(index(android.R.attr.windowLayoutInDisplayCutoutMode), -1))
            // Unset in the dark parent, which is dark bars: light icons on a phone in light mode too.
            assertFalse(theme.getBoolean(index(android.R.attr.windowLightStatusBar), false))
            assertFalse(theme.getBoolean(index(android.R.attr.windowLightNavigationBar), false))
        } finally {
            theme.recycle()
        }
    }

    @Test
    fun `older Android versions' themes keep to the short edges of a cutout and turn the scrim off`() {
        assertTrue("shortEdges" in themeText("values-v28"))
        assertTrue("shortEdges" in themeText("values-v29"))
        assertTrue("always" in themeText("values-v30"))
        listOf("values-v29", "values-v30").forEach { directory ->
            assertTrue(directory, """name="android:enforceStatusBarContrast">false""" in themeText(directory))
            assertTrue(directory, """name="android:enforceNavigationBarContrast">false""" in themeText(directory))
        }
    }

    @Test
    fun `no app code uses the deprecated edge-to-edge calls`() {
        val roots = listOf("src/main/kotlin", "src/play/kotlin", "src/github/kotlin", "../shared/src/commonMain/kotlin", "../shared/src/androidMain/kotlin")
        roots.forEach { assertTrue("Run from app/android: $it", File(it).isDirectory) }
        val offenders = roots.flatMap { root -> File(root).walkTopDown().filter { it.isFile && it.extension == "kt" } }
            .filter { file -> DEPRECATED_CALLS.any { it in file.readText() } }
            .map { it.path }
        assertEquals(emptyList<String>(), offenders)
    }

    private fun themeText(directory: String): String = File("src/main/res/$directory/themes.xml").readText()

    private companion object {
        /** `WindowManager.LayoutParams`'s value for the theme's `always`. */
        const val LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS = 3
        val DEPRECATED_CALLS = listOf("enableEdgeToEdge", "EdgeToEdge.enable", "statusBarColor", "navigationBarColor", "LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES")
    }
}
