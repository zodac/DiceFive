package net.zodac.dicefive

import androidx.compose.runtime.CompositionLocalProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.dicefive.platform.HapticEffect
import net.zodac.dicefive.platform.HapticsPlayer
import net.zodac.dicefive.platform.LocalPlatformServices
import net.zodac.dicefive.platform.PlatformServices
import net.zodac.dicefive.platform.SilentPlatformServices
import net.zodac.dicefive.platform.SoundEffect
import net.zodac.dicefive.platform.SoundPlayer
import net.zodac.dicefive.ui.game.DiceHaptics
import net.zodac.dicefive.ui.game.SoundEffects
import net.zodac.dicefive.ui.game.rememberAppHaptics
import net.zodac.dicefive.ui.game.rememberAppSoundEffects

/** The app's one sound and one vibration follow the Settings switches themselves - no screen has to pass them in. */
class AppFeedbackSettingsTest : UiTest() {

    private val heard = mutableListOf<SoundEffect>()
    private val felt = mutableListOf<HapticEffect>()

    private val recordingServices = object : PlatformServices by SilentPlatformServices {
        override fun createSoundPlayer(): SoundPlayer = object : SoundPlayer {
            override fun play(effect: SoundEffect, volume: Float) {
                heard += effect
            }

            override fun release() = Unit
        }

        override fun createHapticsPlayer(): HapticsPlayer = object : HapticsPlayer {
            override fun play(effect: HapticEffect) {
                felt += effect
            }
        }
    }

    private val sound = MutableStateFlow(false)
    private val vibration = MutableStateFlow(false)
    private lateinit var effects: SoundEffects
    private lateinit var haptics: DiceHaptics

    @Test
    fun `sound and vibration are silent while the switches are off - and follow them on and off again`() = uiTest {
        compose.setContent {
            CompositionLocalProvider(LocalPlatformServices provides recordingServices) {
                effects = rememberAppSoundEffects(sound)
                haptics = rememberAppHaptics(vibration)
            }
        }
        compose.waitForIdle()
        effects.playHold()
        haptics.playHoldTick()
        assertEquals(emptyList<SoundEffect>(), heard)
        assertEquals(emptyList<HapticEffect>(), felt)

        sound.value = true
        vibration.value = true
        compose.waitForIdle()
        effects.playHold()
        haptics.playHoldTick()
        assertEquals(listOf(SoundEffect.HOLD), heard)
        assertEquals(listOf(HapticEffect.HOLD_TICK), felt)

        sound.value = false
        vibration.value = false
        compose.waitForIdle()
        effects.playUnhold()
        haptics.playShakeBuzz()
        assertEquals(listOf(SoundEffect.HOLD), heard)
        assertEquals(listOf(HapticEffect.HOLD_TICK), felt)
    }
}
