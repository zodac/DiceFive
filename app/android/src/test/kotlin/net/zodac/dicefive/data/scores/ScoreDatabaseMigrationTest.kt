package net.zodac.dicefive.data.scores

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An older scores database opens on today's schema through its migrations, as the app opens it, under Robolectric's real
 * SQLite. Each older database is built from its exported schema in app/shared/schemas/, and Room checks every table and
 * index against today's once the migrations have run.
 */
@RunWith(AndroidJUnit4::class)
class ScoreDatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = context.getDatabasePath("migration-test.db")

    /** A database as [version] of the schema left it - its tables, indices and Room's record of it - holding what [fill] adds. */
    private fun createAt(version: Int, fill: SQLiteDatabase.() -> Unit) {
        val schema = JSONObject(File("../shared/schemas/net.zodac.dicefive.data.scores.AppDatabase/$version.json").readText()).getJSONObject("database")
        file.parentFile?.mkdirs()
        SQLiteDatabase.deleteDatabase(file)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                database.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (j in 0 until indices.length()) database.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) database.execSQL(setup.getString(i))
            database.version = version
            database.fill()
        }
    }

    private fun SQLiteDatabase.score(playerName: String, score: Int, atEpochMillis: Long) = execSQL(
        "INSERT INTO scores (playerName, score, timestampEpochMillis, won, isPrimaryPlayer, fiveOfAKindCount, zeroedCategoryCount, " +
            "upperSectionTotal, chanceScore, threeOfAKindScore, fourOfAKindScore, onLeaderboard, gameModeId, playedUnmodified, uuid) " +
            "VALUES (?, ?, ?, NULL, 1, 0, 0, 0, 0, 0, 0, 1, 'standard', 1, ?)",
        arrayOf<Any>(playerName, score, atEpochMillis, "uuid-$atEpochMillis"),
    )

    @Test
    fun `version 1 opens as version 2 - each score keyed by its name in any alphabet - one dismissal a player - nothing lost`() = runTest {
        createAt(1) {
            score("Ángel", 150, 1_000)
            score("ángel", 220, 2_000)
            score("Bob", 90, 3_000)
            // One player dismissed twice under two spellings, which version 1's ASCII-only NOCASE kept apart.
            execSQL("INSERT INTO dismissed_player_stats VALUES (?, ?)", arrayOf<Any>("ÁNGEL", 2_500L))
            execSQL("INSERT INTO dismissed_player_stats VALUES (?, ?)", arrayOf<Any>("ángel", 1_500L))
        }
        val database = Room.databaseBuilder<AppDatabase>(context, file.absolutePath).buildAppDatabase()
        try {
            val dao = database.scoreDao()
            assertEquals(mapOf("Ángel" to "ángel", "ángel" to "ángel", "Bob" to "bob"), dao.allScores().associate { it.playerName to it.playerKey })
            assertEquals(220, dao.bestScoreForPlayer("ángel"))
            // The later dismissal is kept, under the spelling it was made with, and still hides both spellings' games.
            assertEquals(listOf(DismissedPlayerStats("ÁNGEL", 2_500L)), dao.allDismissals())
            assertEquals(listOf("Bob"), dao.playerGames().map { it.playerName })
        } finally {
            database.close()
            SQLiteDatabase.deleteDatabase(file)
        }
    }
}
