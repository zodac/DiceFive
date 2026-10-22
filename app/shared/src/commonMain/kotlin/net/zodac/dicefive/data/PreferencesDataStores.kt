package net.zodac.dicefive.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import okio.Path.Companion.toPath

/**
 * The app's five preferences files, kept apart so each can be cleared on its own - "Reset
 * achievements" must not take the settings or the remembered player names with it. Each platform
 * puts them in its own app-private directory and opens each exactly once per process (DataStore
 * refuses a second instance on the same file).
 */
enum class PreferencesFile(val fileName: String) {
    SETTINGS("settings.preferences_pb"),
    ACHIEVEMENTS("achievements.preferences_pb"),
    IN_PROGRESS_GAME("in_progress_game.preferences_pb"),
    ONLINE("online.preferences_pb"),
    PURCHASES("purchases.preferences_pb"),
}

/**
 * Opens the preferences file at [path] (a platform's directory plus a [PreferencesFile.fileName]). A file that can't
 * be read as preferences is replaced with an empty one, which resets only that file, rather than failing every read
 * of it on every launch.
 */
fun createPreferencesDataStore(path: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { path.toPath() },
    )
