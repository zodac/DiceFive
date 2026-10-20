package net.zodac.dicefive.data.scores

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import net.zodac.dicefive.model.playerNameKey

/** Every migration, oldest first - what [buildAppDatabase] opens an older database with. */
internal val MIGRATIONS: Array<Migration> = arrayOf(Migration1To2)

/**
 * Version 2 matches names by [ScoreEntry.playerKey] instead of SQLite's ASCII-only `COLLATE NOCASE`. Every score gets
 * its name's key, worked out by [playerNameKey] as a new row's is. The dismissals are keyed by it too: dismissals of one
 * player under differently cased names become one, the latest.
 */
internal object Migration1To2 : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `scores` ADD COLUMN `playerKey` TEXT NOT NULL DEFAULT ''")
        val names = connection.prepare("SELECT DISTINCT `playerName` FROM `scores`").use { statement ->
            buildList { while (statement.step()) add(statement.getText(0)) }
        }
        connection.prepare("UPDATE `scores` SET `playerKey` = ? WHERE `playerName` = ?").use { statement ->
            for (name in names) {
                statement.bindText(1, playerNameKey(name))
                statement.bindText(2, name)
                statement.step()
                statement.reset()
            }
        }
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_scores_playerKey_onLeaderboard_score` ON `scores` (`playerKey`, `onLeaderboard`, `score`)",
        )

        val dismissals = connection.prepare("SELECT `playerName`, `dismissedAtEpochMillis` FROM `dismissed_player_stats`").use { statement ->
            buildList { while (statement.step()) add(DismissedPlayerStats(statement.getText(0), statement.getLong(1))) }
        }
        connection.execSQL("DROP TABLE `dismissed_player_stats`")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `dismissed_player_stats` (`playerName` TEXT NOT NULL, `dismissedAtEpochMillis` INTEGER NOT NULL, " +
                "`playerKey` TEXT NOT NULL, PRIMARY KEY(`playerKey`))",
        )
        connection.prepare("INSERT INTO `dismissed_player_stats` (`playerName`, `dismissedAtEpochMillis`, `playerKey`) VALUES (?, ?, ?)").use { statement ->
            for (dismissal in dismissals.groupBy { it.playerKey }.values.map { forPlayer -> forPlayer.maxBy { it.dismissedAtEpochMillis } }) {
                statement.bindText(1, dismissal.playerName)
                statement.bindLong(2, dismissal.dismissedAtEpochMillis)
                statement.bindText(3, dismissal.playerKey)
                statement.step()
                statement.reset()
            }
        }
    }
}
