package net.zodac.dicefive.data.scores

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ScoreEntry::class, DismissedPlayerStats::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scoreDao(): ScoreDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * Adds [ScoreEntry.won], backing per-player win/loss/streak stats on the Statistics
         * screen. Existing rows come back null - unknown - which is handled the same as a solo
         * game: counted as played, not counted toward wins, losses or a streak.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scores ADD COLUMN won INTEGER")
            }
        }

        /**
         * Adds [DismissedPlayerStats], letting a player's card be removed from the Statistics
         * screen without touching their `scores` rows - the Leaderboard keeps their full history.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS dismissed_player_stats (playerName TEXT NOT NULL PRIMARY KEY)")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "dicefive.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
