package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.flowOf
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.PlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.platform.SoundEffect
import net.zodac.dicefive.platform.SoundPlayer
import net.zodac.dicefive.ui.game.LocalSoundEffects
import net.zodac.dicefive.ui.game.SoundEffects
import net.zodac.dicefive.ui.game.rememberAppSoundEffects
import net.zodac.dicefive.ui.game.rememberSoundEffects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Silent, counting the sound players it builds and the sounds played on them. */
private class CountingPlatform : PlatformServices by SilentPlatformServices {
    val playersBuilt = AtomicInteger()
    val played = mutableListOf<SoundEffect>()
    val released = AtomicInteger()

    override fun createSoundPlayer(): SoundPlayer {
        playersBuilt.incrementAndGet()
        return object : SoundPlayer {
            override fun play(effect: SoundEffect, volume: Float) {
                played += effect
            }

            override fun release() {
                released.incrementAndGet()
            }
        }
    }
}

/** The app's one sound player: when it's built, who shares it, and when it goes. */
@RunWith(AndroidJUnit4::class)
class SoundEffectsTest {

    @get:Rule
    val compose = createComposeRule()

    /** Whether the app is composed - set false to take it away. */
    private val appShown = mutableStateOf(true)

    /** The app's [SoundEffects] with sound [on], and what two screens under it are given. */
    private fun showApp(platform: CountingPlatform, on: Boolean): List<SoundEffects> {
        val seen = mutableListOf<SoundEffects>()
        compose.setContent {
            if (appShown.value) CompositionLocalProvider(LocalPlatformServices provides platform) {
                val app = rememberAppSoundEffects(flowOf(on))
                CompositionLocalProvider(LocalSoundEffects provides app) {
                    seen += app
                    seen += rememberSoundEffects()
                    seen += rememberSoundEffects()
                }
            }
        }
        compose.waitForIdle()
        return seen.take(3)
    }

    @Test
    fun `with sound on the app's player is built once a moment after launch - shared by every screen - and played without building another`() {
        val platform = CountingPlatform()
        val (app, first, second) = showApp(platform, on = true)
        assertSame(app, first)
        assertSame(app, second)

        compose.mainClock.advanceTimeBy(2_000L)
        compose.waitUntil(timeoutMillis = 5_000L) { platform.playersBuilt.get() == 1 }

        first.playShake()
        second.playCelebration()
        assertEquals(1, platform.playersBuilt.get())
        assertEquals(listOf(SoundEffect.CUP_SHAKE, SoundEffect.CELEBRATION), platform.played)

        // Released with the app, once.
        appShown.value = false
        compose.waitForIdle()
        assertEquals(1, platform.released.get())
    }

    @Test
    fun `with sound off nothing is built until a sound plays`() {
        val platform = CountingPlatform()
        val (app) = showApp(platform, on = false)
        compose.mainClock.advanceTimeBy(2_000L)
        compose.waitForIdle()
        assertEquals(0, platform.playersBuilt.get())

        app.playHold()
        assertEquals(1, platform.playersBuilt.get())
        assertEquals(listOf(SoundEffect.HOLD), platform.played)
    }

    @Test
    fun `a screen outside the app has a player of its own`() {
        val platform = CountingPlatform()
        val seen = mutableListOf<SoundEffects>()
        compose.setContent {
            CompositionLocalProvider(LocalPlatformServices provides platform) {
                seen += rememberSoundEffects()
                seen += rememberSoundEffects()
            }
        }
        compose.waitForIdle()
        assertNotSame(seen[0], seen[1])
    }
}
