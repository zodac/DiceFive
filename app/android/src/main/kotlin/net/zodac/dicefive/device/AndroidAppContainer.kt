package net.zodac.dicefive.device

import android.content.Context
import androidx.room.Room
import net.zodac.dicefive.BuildConfig
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.app.BuildInfo
import net.zodac.dicefive.data.PreferencesFile
import net.zodac.dicefive.data.achievements.AchievementsRepository
import net.zodac.dicefive.data.createPreferencesDataStore
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.AppDatabase
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.scores.buildAppDatabase
import net.zodac.dicefive.data.settings.SettingsRepository

/**
 * The process-wide [AppContainer] on Android - built on first use, from the application context.
 * One per process is also what DataStore needs: it refuses a second instance on the same file.
 */
object AndroidAppContainer {

    @Volatile
    private var instance: AppContainer? = null

    fun get(context: Context): AppContainer = instance ?: synchronized(this) {
        instance ?: create(context.applicationContext).also { instance = it }
    }

    private fun create(appContext: Context): AppContainer {
        // No driver set: on Android, Room opens it on the framework's own SQLite.
        val database = Room.databaseBuilder<AppDatabase>(
            context = appContext,
            name = appContext.getDatabasePath(AppDatabase.FILE_NAME).absolutePath,
        ).buildAppDatabase()
        // The directory the androidx preferencesDataStore delegate always used.
        fun dataStore(file: PreferencesFile) =
            createPreferencesDataStore(appContext.filesDir.resolve("datastore/${file.fileName}").absolutePath)

        return AppContainer(
            scoreRepository = ScoreRepository(database.scoreDao()),
            settingsRepository = SettingsRepository(dataStore(PreferencesFile.SETTINGS)),
            achievementsRepository = AchievementsRepository(dataStore(PreferencesFile.ACHIEVEMENTS)),
            inProgressGameRepository = InProgressGameRepository(dataStore(PreferencesFile.IN_PROGRESS_GAME)),
            buildInfo = BuildInfo(versionName = BuildConfig.VERSION_NAME, isDebug = BuildConfig.DEBUG),
        )
    }
}
