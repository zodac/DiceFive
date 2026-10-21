package net.zodac.dicefive

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

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
}
