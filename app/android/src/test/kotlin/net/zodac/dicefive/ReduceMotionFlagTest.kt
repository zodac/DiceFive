package net.zodac.dicefive

import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zodac.dicefive.device.AndroidPlatformServices
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** "Remove animations" (animation scale 0) is what turns reduced motion on. */
@RunWith(AndroidJUnit4::class)
class ReduceMotionFlagTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun setAnimationScale(scale: Float) {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @Test
    fun `only an animation scale of zero is reduced motion - not the normal one - nor one slowed or sped up`() = runBlocking {
        for ((scale, reduced) in listOf(0f to true, 1f to false, 0.5f to false, 2f to false)) {
            setAnimationScale(scale)
            assertEquals("scale $scale", reduced, AndroidPlatformServices(context).reduceMotion().first())
        }
    }
}
