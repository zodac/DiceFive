package net.zodac.dicefive.data.scores

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
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
@Database(
    entities = [ScoreEntry::class, DismissedPlayerStats::class],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        // 2: ScoreEntry.onLeaderboard, every existing row on it.
        AutoMigration(from = 1, to = 2),
        // 3: ScoreEntry.gameModeId, every existing row a Standard game.
        AutoMigration(from = 2, to = 3),
        // 4 changes data, not the schema: see MIGRATION_3_4.
        // 5: ScoreEntry.playedUnmodified, null for every existing row (not known).
        AutoMigration(from = 4, to = 5),
    ],
)
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
 * 4: Stud left the Leaderboard ([net.zodac.dicefive.model.GameMode.countsOnLeaderboard]), so the games already
 * recorded in it come off too. The schema is unchanged, which is why this one is written by hand.
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("UPDATE scores SET onLeaderboard = 0 WHERE gameModeId = 'stud'")
    }
}

/**
 * Finishes a platform's builder the same way everywhere. No destructive fallback of any kind: a
 * missing migration must fail loudly in testing, never quietly wipe a player's scores.
 */
fun RoomDatabase.Builder<AppDatabase>.buildAppDatabase(): AppDatabase = this
    .addMigrations(MIGRATION_3_4)
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
