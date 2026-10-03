package net.zodac.dicefive.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.zodac.dicefive.ui.settings.ComposeLicenceDocument
import net.zodac.dicefive.ui.settings.LicenceScroll
import net.zodac.dicefive.ui.settings.LicenseReport
import net.zodac.dicefive.ui.settings.NoSelectionClearer
import net.zodac.dicefive.ui.settings.SelectionClearer

/** [PlatformServices] that does nothing at all - no sound, no vibration, no sensor, no messages, no
 * licences. For `@Preview`s, which have no device behind them. */
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

    override suspend fun loadLicenceReports(): LicenceReports = LicenceReports.EMPTY

    override fun createSelectionClearer(): SelectionClearer = NoSelectionClearer

    @Composable
    override fun LicenceDocument(report: LicenseReport, scroll: LicenceScroll, modifier: Modifier) =
        ComposeLicenceDocument(report, scroll, modifier)
}
