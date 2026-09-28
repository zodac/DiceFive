package net.zodac.dicefive.device

import android.content.Context
import net.zodac.dicefive.BuildConfig
import net.zodac.dicefive.data.achievements.AchievementsRepository
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.AppDatabase
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.platform.AppContainer
import net.zodac.dicefive.platform.BuildInfo

/** The process-wide [AppContainer] on Android - built on first use, from the application context. */
object AndroidAppContainer {

    @Volatile
    private var instance: AppContainer? = null

    fun get(context: Context): AppContainer = instance ?: synchronized(this) {
        instance ?: create(context.applicationContext).also { instance = it }
    }

    private fun create(appContext: Context) = AppContainer(
        scoreRepository = ScoreRepository(AppDatabase.getInstance(appContext).scoreDao()),
        settingsRepository = SettingsRepository(appContext),
        achievementsRepository = AchievementsRepository(appContext),
        inProgressGameRepository = InProgressGameRepository(appContext),
        buildInfo = BuildInfo(versionName = BuildConfig.VERSION_NAME, isDebug = BuildConfig.DEBUG),
    )
}
