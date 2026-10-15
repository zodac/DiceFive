package net.zodac.dicefive.data.scores

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
 * Version 1 is the schema the app was first released with; version 2 adds [ScoreEntry.uuid] and
 * [DismissedPlayerStats.dismissedAtEpochMillis] for the cloud save ([MIGRATION_1_2]). Every schema change needs a
 * version bump and a migration; the exported schemas in app/shared/schemas/ are what a migration test
 * compares against.
 */
@Database(
    entities = [ScoreEntry::class, DismissedPlayerStats::class],
    version = 2,
    exportSchema = true,
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
 * Finishes a platform's builder the same way everywhere. No destructive fallback of any kind: a
 * missing migration must fail loudly in testing, never quietly wipe a player's scores.
 */
fun RoomDatabase.Builder<AppDatabase>.buildAppDatabase(): AppDatabase = this
    .addMigrations(MIGRATION_1_2)
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()

/**
 * Version 1 to 2: every existing score gets a random [ScoreEntry.uuid], and every existing dismissal is dated to the
 * migration - each one is newer than its name's last game, or that game would have cleared it.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `scores` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        connection.execSQL("UPDATE `scores` SET `uuid` = lower(hex(randomblob(16)))")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_scores_uuid` ON `scores` (`uuid`)")
        connection.execSQL("ALTER TABLE `dismissed_player_stats` ADD COLUMN `dismissedAtEpochMillis` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("UPDATE `dismissed_player_stats` SET `dismissedAtEpochMillis` = CAST(strftime('%s', 'now') AS INTEGER) * 1000")
    }
}
