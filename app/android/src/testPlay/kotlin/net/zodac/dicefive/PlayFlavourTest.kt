package net.zodac.dicefive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The Play app: the one with Google Play Games. */
@RunWith(AndroidJUnit4::class)
class PlayFlavourTest {

    @Test
    fun `it is the Play app`() {
        assertEquals("net.zodac.dicefive", ApplicationProvider.getApplicationContext<Context>().packageName)
    }
}
