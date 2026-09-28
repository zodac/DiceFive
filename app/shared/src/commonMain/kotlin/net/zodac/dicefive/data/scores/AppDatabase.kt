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
 * Version 1 is the initial schema - the pre-release history was collapsed into it before the first
 * release. Every schema change from here on needs a version bump and a migration; the exported
 * schemas in app/shared/schemas/ are what a migration test compares against.
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

/**
 * Finishes a platform's builder the same way everywhere. No destructive fallback of any kind: a
 * missing migration must fail loudly in testing, never quietly wipe a player's scores.
 */
fun RoomDatabase.Builder<AppDatabase>.buildAppDatabase(): AppDatabase = this
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
