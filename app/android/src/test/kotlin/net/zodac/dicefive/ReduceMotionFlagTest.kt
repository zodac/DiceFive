package net.zodac.dicefive

import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zodac.dicefive.device.AndroidPlatformServices
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** "Remove animations" (animation scale 0) is what turns reduced motion on. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReduceMotionFlagTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun setAnimationScale(scale: Float) {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @Test
    fun `an animation scale of zero is reduced motion`() = runBlocking {
        setAnimationScale(0f)
        assertTrue(AndroidPlatformServices(context).reduceMotion().first())
    }

    @Test
    fun `the normal animation scale is not`() = runBlocking {
        setAnimationScale(1f)
        assertFalse(AndroidPlatformServices(context).reduceMotion().first())
    }

    @Test
    fun `a slowed or sped up scale is not`() = runBlocking {
        setAnimationScale(0.5f)
        assertFalse(AndroidPlatformServices(context).reduceMotion().first())
    }
}
