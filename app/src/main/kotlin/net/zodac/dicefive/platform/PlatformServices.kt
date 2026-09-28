package net.zodac.dicefive.platform

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What the shared game and UI need from the device underneath them - sound, vibration, the
 * accelerometer and short transient messages - and nothing else. Each platform implements it once
 * (on Android, `AndroidPlatformServices` in the app module), and the root composable provides it
 * through [LocalPlatformServices]. Everything that *can* be written once, including the logic that
 * decides when to play a sound or what counts as a shake, stays out of here - an implementation
 * only moves bytes to and from the hardware.
 */
interface PlatformServices {

    /** A fresh player with every [SoundEffect] loaded - the caller [SoundPlayer.release]s it. */
    fun createSoundPlayer(): SoundPlayer

    fun createHapticsPlayer(): HapticsPlayer

    /** Null on hardware with no accelerometer - shake-to-roll then simply never fires. */
    fun createAccelerometer(): Accelerometer?

    /** A short message that shows for a moment and needs no response (an Android toast). */
    fun showTransientMessage(message: String)
}

val LocalPlatformServices = staticCompositionLocalOf<PlatformServices> {
    error("No PlatformServices provided - the root composable provides one for the platform it runs on")
}

/** Every sound the game makes. Each platform maps these to its own copy of the clips. */
enum class SoundEffect {
    CUP_SHAKE,
    MAT_LANDING,
    HOLD,
    UNHOLD,
    CELEBRATION,
}

interface SoundPlayer {

    /** Plays [effect] at [volume] (0-1). A clip still loading plays as soon as it's ready. */
    fun play(effect: SoundEffect, volume: Float)

    fun release()
}

/** Every vibration the game makes - named for what it accompanies, so each platform can pick the
 * closest thing its own haptics offer rather than all of them sharing one duration/amplitude. */
enum class HapticEffect {
    /** A die being held or unheld. */
    HOLD_TICK,

    /** The dice cup being shaken. */
    SHAKE_BUZZ,
}

interface HapticsPlayer {
    fun play(effect: HapticEffect)
}

/** Raw accelerometer readings, gravity included, in m/s² along the device's own x/y/z axes. */
interface Accelerometer {

    fun start(onSample: (x: Float, y: Float, z: Float) -> Unit)

    fun stop()
}
