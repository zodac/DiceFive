package net.zodac.dicefive.device

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import net.zodac.dicefive.R
import net.zodac.dicefive.platform.Accelerometer
import net.zodac.dicefive.platform.HapticsPlayer
import net.zodac.dicefive.platform.LicenceReports
import net.zodac.dicefive.platform.PlatformServices
import net.zodac.dicefive.platform.SoundPlayer
import net.zodac.dicefive.ui.settings.LicenseReport
import net.zodac.dicefive.ui.settings.SelectionClearer

/**
 * [PlatformServices] for Android. Holds the application context, never an Activity's: sound
 * players and sensors can outlive any single Activity instance across a configuration change.
 */
class AndroidPlatformServices(context: Context) : PlatformServices {

    private val appContext = context.applicationContext

    override fun createSoundPlayer(): SoundPlayer = AndroidSoundPlayer(appContext)

    override fun createHapticsPlayer(): HapticsPlayer = AndroidHapticsPlayer(appContext)

    override fun createAccelerometer(): Accelerometer? = AndroidAccelerometer.create(appContext)

    /**
     * True while the system's animation scale is 0 - what "Remove animations" (Settings > Accessibility)
     * and the developer option of the same name set. Watched, so flipping it with the app open
     * takes effect without a restart.
     */
    override fun reduceMotion(): Flow<Boolean> = callbackFlow {
        val resolver = appContext.contentResolver
        fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(read())
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        trySend(read())
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.distinctUntilChanged()

    override fun showTransientMessage(message: String) {
        Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
    }

    /** Both generated into res/raw by the build - see the "Open-source licenses" section of app/android/build.gradle.kts. */
    override suspend fun loadLicenceReports(): LicenceReports = withContext(Dispatchers.IO) {
        LicenceReports(librariesJson = readRaw(R.raw.aboutlibraries), noticesJson = readRaw(R.raw.third_party_notices))
    }

    override fun createSelectionClearer(): SelectionClearer = TextViewSelectionClearer()

    @Composable
    override fun LicenceDocument(report: LicenseReport, modifier: Modifier) = TextViewLicenceDocument(report, modifier)

    private fun readRaw(@RawRes id: Int): String = appContext.resources.openRawResource(id).bufferedReader().use { it.readText() }
}
