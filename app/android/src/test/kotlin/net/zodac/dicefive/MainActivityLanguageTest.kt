package net.zodac.dicefive

import android.view.View
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The app is either wholly in a language it has a translation for, or wholly in English, left to right - Android's own
 * text (Material's labels, the system's copy menu) and the window's direction included, not just the app's words. See
 * stringsLanguageConfiguration and .claude/I18N.md.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLanguageTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    companion object {
        init {
            // The rule's own recomposer and test clock, not the Activity's capped one - see MainActivity.useCappedFrameClock.
            MainActivity.useCappedFrameClock = false
        }
    }

    private val configuration get() = compose.activity.resources.configuration

    /** A string from Android's own resources, as the Activity resolves it. */
    private val systemCopy get() = compose.activity.getString(android.R.string.copy)

    @Test
    @Config(qualifiers = "fa")
    fun `on a Persian phone the whole Activity is in English and left to right`() {
        assertEquals(Locale.forLanguageTag("en-GB"), configuration.locales[0])
        assertEquals(View.LAYOUT_DIRECTION_LTR, configuration.layoutDirection)
        assertEquals("Copy", systemCopy)
        assertEquals("en", Locale.getDefault().language)
    }

    @Test
    @Config(qualifiers = "ar")
    fun `on an Arabic phone the Activity stays Arabic and right to left`() {
        assertEquals("ar", configuration.locales[0].language)
        assertEquals(View.LAYOUT_DIRECTION_RTL, configuration.layoutDirection)
    }

    @Test
    @Config(qualifiers = "es")
    fun `on a Spanish phone the Activity stays Spanish`() {
        assertEquals("es", configuration.locales[0].language)
        assertEquals("Copiar", systemCopy)
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `on an American phone the Activity keeps its own English`() {
        assertEquals(Locale.US, configuration.locales[0])
    }
}
