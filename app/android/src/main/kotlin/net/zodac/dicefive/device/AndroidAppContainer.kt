package net.zodac.dicefive.device

import android.content.Context
import androidx.room.Room
import net.zodac.dicefive.BuildConfig
import net.zodac.dicefive.app.AppContainer
import net.zodac.dicefive.app.BuildInfo
import net.zodac.dicefive.data.scores.AppDatabase

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

    private fun create(appContext: Context): AppContainer = AppContainer.create(
        // One file per PreferencesFile, in filesDir/datastore.
        dataDir = appContext.filesDir.resolve("datastore").absolutePath,
        // No driver set: on Android, Room opens it on the framework's own SQLite.
        databaseBuilder = Room.databaseBuilder<AppDatabase>(
            context = appContext,
            name = appContext.getDatabasePath(AppDatabase.FILE_NAME).absolutePath,
        ),
        buildInfo = BuildInfo(versionName = BuildConfig.VERSION_NAME, isDebug = BuildConfig.DEBUG),
    )
}
