package net.zodac.dicefive.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import okio.IOException
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
 * of it on every launch. One that can't be read or written at all is [ResilientDataStore]'s.
 */
fun createPreferencesDataStore(path: String): DataStore<Preferences> =
    ResilientDataStore(
        PreferenceDataStoreFactory.createWithPath(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { path.toPath() },
        ),
    )

/**
 * [store], with its I/O failures kept from whoever asked: a write that can't be made (a full disk) is skipped, leaving
 * the file as it was, and a read that can't be made is empty preferences. Every write is made from a coroutine with
 * nothing to catch it, where a throw would take the app down.
 *
 * A read that fails is tried again [RETRY_DELAYS_MILLIS].size times, after each of those waits in turn (1s, 3s, 5s), and
 * then no more: a collector kept for the process sees the file if it can be read within those, rather than empty
 * preferences for good. Empty preferences are given at the first failure, so nothing waits on the retries; a retry that
 * reads the file gives what it read.
 */
internal class ResilientDataStore(private val store: DataStore<Preferences>) : DataStore<Preferences> {

    override val data: Flow<Preferences> = store.data.retryWhen { cause, attempt ->
        if (cause !is IOException) return@retryWhen false
        if (attempt == 0L) emit(emptyPreferences())
        val wait = RETRY_DELAYS_MILLIS.getOrNull(attempt.toInt()) ?: return@retryWhen false
        delay(wait)
        true
    }.catch { if (it !is IOException) throw it }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        try {
            store.updateData(transform)
        } catch (_: IOException) {
            data.first()
        }

    private companion object {
        val RETRY_DELAYS_MILLIS = listOf(1_000L, 3_000L, 5_000L)
    }
}
