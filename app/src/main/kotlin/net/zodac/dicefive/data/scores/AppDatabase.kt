package net.zodac.dicefive.data.scores

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ScoreEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scoreDao(): ScoreDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "dicefive.db")
                    .build()
                    .also { instance = it }
            }
    }
}
