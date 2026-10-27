package net.zodac.dicefive

import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * A turn, a resize or a uiMode change is laid out again in the same [MainActivity], keeping its sound clips and any sync
 * under way; a change of language rebuilds it, for `attachBaseContext` to apply the strings' language again.
 */
@RunWith(RobolectricTestRunner::class)
class ConfigurationChangeTest {

    private fun changed(change: Configuration.() -> Unit): Pair<MainActivity, MainActivity> {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val before = controller.get()
        val config = Configuration(before.resources.configuration).apply(change)
        controller.configurationChange(config)
        return before to controller.get()
    }

    @Test
    fun `a turn, a resize and a uiMode change keep the Activity`() {
        changed { orientation = Configuration.ORIENTATION_LANDSCAPE }.let { (before, after) -> assertSame(before, after) }
        changed { screenWidthDp += 200; smallestScreenWidthDp += 200 }.let { (before, after) -> assertSame(before, after) }
        changed { uiMode = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_NO }.let { (before, after) -> assertSame(before, after) }
    }

    @Test
    fun `a change of language rebuilds the Activity`() {
        val (before, after) = changed { setLocales(LocaleList(Locale.forLanguageTag("es"))) }
        assertNotSame(before, after)
    }
}
