package net.zodac.dicefive.device

import kotlin.experimental.ExperimentalNativeApi
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.app.BuildInfo
import net.zodac.dicefive.data.PreferencesFile
import net.zodac.dicefive.data.achievements.AchievementsRepository
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import platform.Foundation.NSBundle

/**
 * The process-wide [AppContainer] on iOS - built on first use. One per process is also what
 * DataStore needs: it refuses a second instance on the same file. The iOS counterpart of Android's
 * `AndroidAppContainer`.
 */
internal object IosAppContainer {

    @OptIn(ExperimentalNativeApi::class)
    val instance: AppContainer by lazy {
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
}
