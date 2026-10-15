package net.zodac.dicefive.online

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** Play Games Sidekick's settings, as Play reads them from the manifest: where its tab sits, and its snooze. */
@RunWith(AndroidJUnit4::class)
class SidekickConfigTest {

    /** Each element of the config the manifest points Play at, as its tag and attributes. */
    private fun sidekickConfig(): List<Pair<String, Map<String, String>>> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        val resource = info.metaData.getInt("com.google.android.finsky.deku.OVERLAY_CONFIG")
        assertNotEquals("No Sidekick config in the manifest", 0, resource)
        val parser = context.resources.getXml(resource)
        val elements = mutableListOf<Pair<String, Map<String, String>>>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            elements += parser.name to (0 until parser.attributeCount).associate { parser.getAttributeName(it) to parser.getAttributeValue(it) }
        }
        return elements
    }

    @Test
    fun `the tab starts top-right in both orientations, clear of the totals button and the cup`() {
        val positions = sidekickConfig().filter { it.first == "default-entrypoint-position" }.associate { (_, attributes) ->
            attributes.getValue("orientation") to attributes.getValue("value")
        }
        assertEquals(mapOf("portrait" to "TOP_RIGHT", "landscape" to "TOP_RIGHT"), positions)
    }

    @Test
    fun `the tab is snoozed for the first five minutes`() {
        val snoozes = sidekickConfig().filter { it.first == "snooze" }.map { it.second.getValue("duration_seconds") }
        assertEquals(listOf("300"), snoozes)
    }

    @Test
    fun `the config is the one Play expects`() {
        assertEquals("deku-config", sidekickConfig().first().first)
    }
}
