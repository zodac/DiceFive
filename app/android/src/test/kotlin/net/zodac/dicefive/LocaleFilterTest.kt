package net.zodac.dicefive

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The libraries' own text ships only in the app's languages (the build's localeFilters): in any other the app is
 * wholly English, so their other translations are left out of the APK.
 */
@RunWith(AndroidJUnit4::class)
class LocaleFilterTest {

    /** What TalkBack says for a switch that's on, from Compose's own resources, in [languageTag]. */
    private fun stateOn(languageTag: String): String {
        val configuration = Configuration().apply { setLocale(Locale.forLanguageTag(languageTag)) }
        return ApplicationProvider.getApplicationContext<Context>().createConfigurationContext(configuration)
            .getString(androidx.compose.ui.R.string.state_on)
    }

    @Test
    fun `the app's languages keep the libraries' translations`() {
        assertEquals("On", stateOn("en-GB"))
        assertEquals("Activado", stateOn("es"))
        assertEquals("مفعَّل", stateOn("ar"))
    }

    @Test
    fun `any other language has the libraries' English`() {
        assertEquals("On", stateOn("fr"))
        assertEquals("On", stateOn("fa"))
    }
}
