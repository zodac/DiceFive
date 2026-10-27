package net.zodac.dicefive

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.data.PreferencesFile
import net.zodac.dicefive.data.scores.AppDatabase
import net.zodac.dicefive.online.PlayGamesServices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.xmlpull.v1.XmlPullParser

/** The Play app: Google Play Games, and no progress files, signed in or not - nothing from a file can reach its boards. */
@RunWith(AndroidJUnit4::class)
class PlayFlavourTest {

    @Test
    fun `it is the Play app`() {
        assertEquals("net.zodac.dicefive", ApplicationProvider.getApplicationContext<Context>().packageName)
    }

    @Test
    fun `it has no progress files`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).create().get()
        assertNull(Distribution.progressFiles(activity))
    }

    @Test
    fun `backups and device transfers carry everything but Play Games' sign-in at launch - from Android 12`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(R.xml.data_extraction_rules, applicationManifestResource(context, "dataExtractionRules"))

        val playGames = "sharedpref:${PlayGamesServices.PREFERENCES_FILE}.xml"
        assertEquals(
            listOf("cloud-backup/exclude $playGames", "device-transfer/exclude $playGames"),
            rules(context, R.xml.data_extraction_rules),
        )
    }

    @Test
    fun `backups up to Android 11 carry only the settings - adb can edit and restore one there`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(R.xml.backup_rules, applicationManifestResource(context, "fullBackupContent"))

        val database = listOf("", "-journal", "-shm", "-wal").map { "database:${AppDatabase.FILE_NAME}$it" }
        val preferences = PreferencesFile.entries.filter { it != PreferencesFile.SETTINGS }.map { "file:datastore/${it.fileName}" }
        assertEquals(
            (listOf("sharedpref:${PlayGamesServices.PREFERENCES_FILE}.xml") + database + preferences).map { "full-backup-content/exclude $it" },
            rules(context, R.xml.backup_rules),
        )
    }

    /** Each include or exclude in the rules file [id], as "parent/tag domain:path". */
    private fun rules(context: Context, id: Int): List<String> {
        val parser = context.resources.getXml(id)
        val found = mutableListOf<String>()
        var parent = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "include" || parser.name == "exclude") {
                val domain = parser.getAttributeValue(null, "domain")
                val path = parser.getAttributeValue(null, "path")
                found += "$parent/${parser.name} $domain:$path"
            } else {
                parent = parser.name
            }
        }
        return found
    }
}
