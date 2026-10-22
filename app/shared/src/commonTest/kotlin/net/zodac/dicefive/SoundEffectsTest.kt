@file:OptIn(ExperimentalAtomicApi::class)

package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.PlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.platform.SoundEffect
import net.zodac.dicefive.platform.SoundPlayer
import net.zodac.dicefive.ui.game.LocalSoundEffects
import net.zodac.dicefive.ui.game.SoundEffects
import net.zodac.dicefive.ui.game.rememberAppSoundEffects
import net.zodac.dicefive.ui.game.rememberSoundEffects

/** Silent, counting the sound players it builds and the sounds played on them. */
private class CountingPlatform : PlatformServices by SilentPlatformServices {
    val playersBuilt = AtomicInt(0)
    val played = mutableListOf<SoundEffect>()
    val released = AtomicInt(0)

    override fun createSoundPlayer(): SoundPlayer {
        playersBuilt.incrementAndFetch()
        return object : SoundPlayer {
            override fun play(effect: SoundEffect, volume: Float) {
                played += effect
            }

            override fun release() {
                released.incrementAndFetch()
            }
        }
    }
}

/** The app's one sound player: when it's built, who shares it, and when it goes. */
class SoundEffectsTest : UiTest() {

    /** Whether the app is composed - set false to take it away. */
    private val appShown = mutableStateOf(true)

    /** The app's [SoundEffects] with sound [on], and what two screens under it are given. */
    private fun showApp(platform: CountingPlatform, on: Boolean, setting: MutableStateFlow<Boolean> = MutableStateFlow(on)): List<SoundEffects> {
        val seen = mutableListOf<SoundEffects>()
        compose.setContent {
            if (appShown.value) CompositionLocalProvider(LocalPlatformServices provides platform) {
                val app = rememberAppSoundEffects(setting)
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
    fun `with sound on the app's player is built once a moment after launch - shared by every screen - and played without building another`() = uiTest {
        val platform = CountingPlatform()
        val (app, first, second) = showApp(platform, on = true)
        assertSame(app, first)
        assertSame(app, second)

        compose.mainClock.advanceTimeBy(2_000L)
        compose.waitUntil(timeoutMillis = 5_000L) { platform.playersBuilt.load() == 1 }

        first.playRollSound(SoundEffect.CUP_SHAKE)
        second.playCelebration()
        assertEquals(1, platform.playersBuilt.load())
        assertEquals(listOf(SoundEffect.CUP_SHAKE, SoundEffect.CELEBRATION), platform.played)

        // Released with the app, once.
        appShown.value = false
        compose.waitForIdle()
        assertEquals(1, platform.released.load())
    }

    @Test
    fun `with sound off nothing is built or played until it is switched on`() = uiTest {
        val platform = CountingPlatform()
        val setting = MutableStateFlow(false)
        val (app) = showApp(platform, on = false, setting = setting)
        compose.mainClock.advanceTimeBy(2_000L)
        compose.waitForIdle()
        assertEquals(0, platform.playersBuilt.load())

        app.playHold()
        assertEquals(0, platform.playersBuilt.load())
        assertEquals(emptyList<SoundEffect>(), platform.played)

        setting.value = true
        compose.waitForIdle()
        app.playHold()
        assertEquals(1, platform.playersBuilt.load())
        assertEquals(listOf(SoundEffect.HOLD), platform.played)
    }

    @Test
    fun `a screen outside the app has a player of its own`() = uiTest {
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
