package net.zodac.dicefive

import android.content.Context
import android.media.SoundPool
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.dicefive.device.AndroidSoundPlayer
import net.zodac.dicefive.platform.SoundEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowSoundPool

/**
 * A [SoundPool] whose every clip finishes decoding the moment it's loaded - the platform's worst case. Like the platform,
 * it tells the load-complete listener only if one is registered by then, and drops the news otherwise.
 */
@Implements(SoundPool::class)
class InstantlyLoadingSoundPool : ShadowSoundPool() {
    @Implementation
    override fun load(context: Context?, resId: Int, priority: Int): Int =
        super.load(context, resId, priority).also { notifyResourceLoaded(resId, true) }
}

/** [AndroidSoundPlayer] on a pool that decodes faster than the player is built. */
@RunWith(AndroidJUnit4::class)
@Config(shadows = [InstantlyLoadingSoundPool::class])
class AndroidSoundPlayerTest {

    private val player = AndroidSoundPlayer(ApplicationProvider.getApplicationContext())

    private val pool: ShadowSoundPool = shadowOf(player.pool)

    @Test
    fun `a clip decoded while the player is still being built plays at once`() {
        player.play(SoundEffect.HOLD, 0.5f)
        player.play(SoundEffect.CELEBRATION, 1f)
        assertTrue("hold didn't play", pool.wasResourcePlayed(R.raw.hold))
        assertTrue("celebration didn't play", pool.wasResourcePlayed(R.raw.celebration))
        assertEquals(0.5f, pool.getResourcePlaybacks(R.raw.hold).single().leftVolume)
    }
}
