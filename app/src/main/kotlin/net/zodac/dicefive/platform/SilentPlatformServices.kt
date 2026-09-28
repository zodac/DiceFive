package net.zodac.dicefive.platform

/** [PlatformServices] that does nothing at all - no sound, no vibration, no sensor, no messages. For
 * `@Preview`s, which have no device behind them. */
object SilentPlatformServices : PlatformServices {

    override fun createSoundPlayer(): SoundPlayer = object : SoundPlayer {
        override fun play(effect: SoundEffect, volume: Float) = Unit

        override fun release() = Unit
    }

    override fun createHapticsPlayer(): HapticsPlayer = object : HapticsPlayer {
        override fun play(effect: HapticEffect) = Unit
    }

    override fun createAccelerometer(): Accelerometer? = null

    override fun showTransientMessage(message: String) = Unit
}
