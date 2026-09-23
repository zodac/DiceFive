package net.zodac.dicefive.data.scores

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ScoreEntry::class], version = 2, exportSchema = false)
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

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "dicefive.db")
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
