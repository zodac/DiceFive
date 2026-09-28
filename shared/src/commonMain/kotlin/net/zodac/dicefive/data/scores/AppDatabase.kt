package net.zodac.dicefive.data.scores

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * The scores database - the Leaderboard and Statistics screens' shared table, plus Statistics'
 * dismissals. Room's multiplatform build: each platform opens it with its own
 * [RoomDatabase.Builder] (on Android, the framework's SQLite; on iOS, the bundled driver) and
 * finishes it with [buildAppDatabase].
 *
 * The schema restarted at version 1 when the app moved to Kotlin Multiplatform, before its first
 * release, and the pre-release migrations went with it. From here on, every schema change needs a
 * version bump and a migration again - the exported schemas in shared/schemas/ are what a
 * migration test compares against.
 */
@Database(entities = [ScoreEntry::class, DismissedPlayerStats::class], version = 1, exportSchema = true)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scoreDao(): ScoreDao

    companion object {
        /** The database's file name - the same on every platform, each in its own app-private directory. */
        const val FILE_NAME = "dicefive.db"
    }
}

/** Room's compiler generates the `actual` for each target. */
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** Finishes a platform's builder the same way everywhere. */
fun RoomDatabase.Builder<AppDatabase>.buildAppDatabase(): AppDatabase = this
    // A database written by a newer build (an older APK installed over it, or a pre-release install
    // from before the schema restarted at version 1) can't be migrated down - starting empty beats
    // failing to open at all.
    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
