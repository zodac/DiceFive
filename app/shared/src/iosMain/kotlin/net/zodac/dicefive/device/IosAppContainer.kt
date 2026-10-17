package net.zodac.dicefive.device

import kotlin.experimental.ExperimentalNativeApi
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.app.BuildInfo
import platform.Foundation.NSBundle

/**
 * The process-wide [AppContainer] on iOS - built on first use. One per process is also what
 * DataStore needs: it refuses a second instance on the same file. The iOS counterpart of Android's
 * `AndroidAppContainer`.
 */
internal object IosAppContainer {

    @OptIn(ExperimentalNativeApi::class)
    val instance: AppContainer by lazy {
        AppContainer.create(
            dataDir = appDataDirectory,
            databaseBuilder = iosAppDatabaseBuilder(),
            buildInfo = BuildInfo(
                versionName = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "?",
                isDebug = Platform.isDebugBinary,
            ),
        )
    }
}
