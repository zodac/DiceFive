package net.zodac.dicefive.platform

import androidx.compose.ui.window.ComposeUIViewController
import kotlin.experimental.ExperimentalNativeApi
import net.zodac.dicefive.data.PreferencesFile
import net.zodac.dicefive.data.achievements.AchievementsRepository
import net.zodac.dicefive.data.createIosAppDatabase
import net.zodac.dicefive.data.createIosPreferencesDataStore
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.ui.DiceFiveApp
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

/** The process-wide [AppContainer] on iOS - one per process, as DataStore requires. */
@OptIn(ExperimentalNativeApi::class)
private val iosAppContainer: AppContainer by lazy {
    AppContainer(
        scoreRepository = ScoreRepository(createIosAppDatabase().scoreDao()),
        settingsRepository = SettingsRepository(createIosPreferencesDataStore(PreferencesFile.SETTINGS)),
        achievementsRepository = AchievementsRepository(createIosPreferencesDataStore(PreferencesFile.ACHIEVEMENTS)),
        inProgressGameRepository = InProgressGameRepository(createIosPreferencesDataStore(PreferencesFile.IN_PROGRESS_GAME)),
        buildInfo = BuildInfo(
            versionName = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "?",
            isDebug = Platform.isDebugBinary,
        ),
    )
}

/**
 * The whole app as a UIKit view controller - what the (still to be written) Xcode project's Swift
 * entry point hosts, e.g. from SwiftUI:
 *
 * ```swift
 * struct ContentView: UIViewControllerRepresentable {
 *     func makeUIViewController(context: Context) -> UIViewController { MainViewControllerKt.MainViewController() }
 *     func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
 * }
 * ```
 */
@Suppress("FunctionName") // Named like the view controller it returns, as Kotlin/Native iOS entry points conventionally are.
fun MainViewController(): UIViewController {
    val platform = IosPlatformServices()
    return ComposeUIViewController {
        TransientMessageHost(platform) {
            DiceFiveApp(container = iosAppContainer, platform = platform)
        }
    }
}
