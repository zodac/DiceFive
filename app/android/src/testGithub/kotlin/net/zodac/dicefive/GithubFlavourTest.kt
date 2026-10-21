package net.zodac.dicefive

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.online.NoGameServices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * The github APK: an app of its own, beside the Play one rather than in its place, with progress files and no Google
 * Play Games anywhere in it - no SDK, no manifest entries, no service.
 */
@RunWith(AndroidJUnit4::class)
class GithubFlavourTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `it is its own app - not the Play one`() {
        assertEquals("net.zodac.dicefive.github", context.packageName)
    }

    @Test
    fun `a debug build is DiceFive Debug on the launcher`() {
        val label = context.packageManager.getApplicationLabel(context.applicationInfo).toString()
        assertEquals(if (BuildConfig.DEBUG) "DiceFive Debug" else "DiceFive", label)
    }

    @Test
    @Config(qualifiers = "es")
    fun `a debug build is DiceFive Debug in Spanish too`() {
        assertEquals(if (BuildConfig.DEBUG) "DiceFive Debug" else "DiceFive", context.packageManager.getApplicationLabel(context.applicationInfo).toString())
    }

    @Test
    fun `there is no Play Games SDK in it`() {
        for (name in listOf("com.google.android.gms.games.PlayGames", "net.zodac.dicefive.online.PlayGamesServices")) {
            assertFalse(name, runCatching { Class.forName(name) }.isSuccess)
        }
    }

    @Test
    fun `its manifest has none of Play Games' or Sidekick's entries`() {
        val metaData = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        for (key in listOf("com.google.android.gms.games.APP_ID", "com.google.android.finsky.deku.OVERLAY_CONFIG")) {
            assertFalse(key, metaData?.containsKey(key) == true)
        }
        val providers = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS).providers.orEmpty()
        assertFalse(providers.any { it.name.contains("PlayGames") })
    }

    @Test
    fun `it has progress files - sealed with a 32-byte key - and no online service`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).create().get()
        assertSame(NoGameServices, Distribution.gameServices(activity))
        assertEquals(32, Distribution.progressFiles(activity).key.size)
    }
}
