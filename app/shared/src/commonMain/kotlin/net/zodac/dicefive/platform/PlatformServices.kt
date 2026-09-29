package net.zodac.dicefive.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import net.zodac.dicefive.ui.settings.LicenseReport
import net.zodac.dicefive.ui.settings.SelectionClearer

/**
 * What the shared game and UI need from the device underneath them - sound, vibration, the
 * accelerometer, short transient messages and the Licences page's data and text view - and nothing
 * else. Each platform implements it once
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

    /** The build-generated open-source licence reports the Licences dialog lists - see [LicenceReports]. */
    suspend fun loadLicenceReports(): LicenceReports

    /** A fresh [SelectionClearer] for one Licences dialog, matched to what [LicenceDocument] draws. */
    fun createSelectionClearer(): SelectionClearer

    /**
     * The licence report as one selectable, scrollable document, with tappable links and "Show licence
     * text" toggles. Platform-specific because text selection is: see TextViewLicenceDocument in :app:android for
     * why Android's is a platform TextView rather than Compose text.
     */
    @Composable
    fun LicenceDocument(report: LicenseReport, modifier: Modifier)
}

/**
 * The two reports the Licences dialog is built from, as each platform's build generated them: the
 * AboutLibraries library list and the collected Apache-2.0 NOTICE files (see the "Open-source
 * licenses" section of app/android/build.gradle.kts).
 */
class LicenceReports(val librariesJson: String, val noticesJson: String) {
    companion object {
        /** Nothing to list - for a platform whose build doesn't generate the reports yet. */
        val EMPTY = LicenceReports(librariesJson = """{"libraries":[],"licenses":{}}""", noticesJson = "{}")
    }
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

/**
 * Raw accelerometer readings, gravity included, in m/s² along the screen's axes as it's currently
 * turned - x to its right, y to its top, z out of it towards the viewer - so a reading means the
 * same on screen whichever way round the app is displayed. At rest, the axis pointing up reads
 * +9.81 (Android's convention: the reaction to gravity, not gravity itself). Shake detection only
 * uses the size of the motion; the googly-eyed logo dice (see DieMotion.feel) need the direction.
 */
interface Accelerometer {

    fun start(onSample: (x: Float, y: Float, z: Float) -> Unit)

    fun stop()
}
