package net.zodac.dicefive.device

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import net.zodac.dicefive.data.PreferencesFile
import net.zodac.dicefive.data.createPreferencesDataStore
import net.zodac.dicefive.data.scores.AppDatabase
import net.zodac.dicefive.data.scores.buildAppDatabase
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * Application Support, not Documents: the app's own data, which the Files app shouldn't show the
 * player. Created on first use - unlike Documents, iOS doesn't make it up front.
 */
@OptIn(ExperimentalForeignApi::class)
private val appDataDirectory: String by lazy {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    requireNotNull(url?.path) { "iOS gave no Application Support directory" }
}

/** The scores database on iOS - SQLite bundled with the app (Room's driver), as iOS has no framework one. */
internal fun createIosAppDatabase(): AppDatabase =
    Room.databaseBuilder<AppDatabase>(name = "$appDataDirectory/${AppDatabase.FILE_NAME}")
        .setDriver(BundledSQLiteDriver())
        .buildAppDatabase()

internal fun createIosPreferencesDataStore(file: PreferencesFile): DataStore<Preferences> =
    createPreferencesDataStore("$appDataDirectory/${file.fileName}")
