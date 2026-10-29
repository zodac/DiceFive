package net.zodac.dicefive.data.scores

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlin.uuid.Uuid
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.playerNameKey

/** Every migration, oldest first - what [buildAppDatabase] opens an older database with. */
internal val MIGRATIONS: Array<Migration> = arrayOf(Migration1To2, Migration2To3)

/**
 * Version 2 rebuilds `scores`, as SQLite can't change a table's key or a column's type in place:
 *
 * - Names are matched by [ScoreEntry.playerKey] instead of SQLite's ASCII-only `COLLATE NOCASE`. Every score gets its
 *   name's key, worked out by [playerNameKey] as a new row's is.
 * - [ScoreEntry.uuid] is the primary key, stored as its 16 bytes; the device-local `id` is gone. A uuid that isn't 32 hex
 *   digits (none this app wrote) gets a new random one rather than failing the migration and losing the score.
 * - [ScoreEntry.onLeaderboard] is worked out again from the mode and `playedUnmodified`, as a new row's is.
 * - The indices that serve the Leaderboard's pages, a player's best, player 1's figures and Statistics are added.
 *
 * Rows keep the order they were recorded in, which is the order rows equal on every Leaderboard criterion are listed in.
 * The dismissals are keyed by [DismissedPlayerStats.playerKey] too: dismissals of one player under differently cased
 * names become one, the latest.
 */
internal object Migration1To2 : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        migrateScores(connection)
        migrateDismissals(connection)
    }

    private fun migrateScores(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `scores_new` (`uuid` BLOB NOT NULL, `playerName` TEXT NOT NULL, `score` INTEGER NOT NULL, " +
                "`timestampEpochMillis` INTEGER NOT NULL, `won` INTEGER, `isPrimaryPlayer` INTEGER NOT NULL, `fiveOfAKindCount` INTEGER NOT NULL, " +
                "`zeroedCategoryCount` INTEGER NOT NULL, `upperSectionTotal` INTEGER NOT NULL, `chanceScore` INTEGER NOT NULL, " +
                "`threeOfAKindScore` INTEGER NOT NULL, `fourOfAKindScore` INTEGER NOT NULL, `gameModeId` TEXT NOT NULL, " +
                "`playedUnmodified` INTEGER NOT NULL, `playerKey` TEXT NOT NULL, `onLeaderboard` INTEGER NOT NULL, PRIMARY KEY(`uuid`))",
        )
        val columns = "`playerName`, `score`, `timestampEpochMillis`, `won`, `isPrimaryPlayer`, `fiveOfAKindCount`, `zeroedCategoryCount`, " +
            "`upperSectionTotal`, `chanceScore`, `threeOfAKindScore`, `fourOfAKindScore`, `gameModeId`, `playedUnmodified`"
        // The uuids come across as text for now, and playerKey and onLeaderboard as placeholders; all three are set below.
        connection.execSQL(
            "INSERT INTO `scores_new` (`uuid`, $columns, `playerKey`, `onLeaderboard`) SELECT `uuid`, $columns, '', 0 FROM `scores` ORDER BY `id`",
        )

        val names = connection.prepare("SELECT DISTINCT `playerName` FROM `scores_new`").use { statement ->
            buildList { while (statement.step()) add(statement.getText(0)) }
        }
        connection.prepare("UPDATE `scores_new` SET `playerKey` = ? WHERE `playerName` = ?").use { statement ->
            for (name in names) {
                statement.bindText(1, playerNameKey(name))
                statement.bindText(2, name)
                statement.step()
                statement.reset()
            }
        }

        val uuids = connection.prepare("SELECT `rowid`, `uuid` FROM `scores_new`").use { statement ->
            buildList { while (statement.step()) add(statement.getLong(0) to statement.getText(1)) }
        }
        connection.prepare("UPDATE `scores_new` SET `uuid` = ? WHERE `rowid` = ?").use { statement ->
            for ((rowid, uuid) in uuids) {
                statement.bindBlob(1, (Uuid.parseHexOrNull(uuid) ?: Uuid.random()).toByteArray())
                statement.bindLong(2, rowid)
                statement.step()
                statement.reset()
            }
        }

        val leaderboardModes = GameMode.entries.filter { it.countsOnLeaderboard }.map { it.id }
        connection.prepare(
            "UPDATE `scores_new` SET `onLeaderboard` = (`playedUnmodified` = 1 AND `gameModeId` IN (${leaderboardModes.joinToString { "?" }}))",
        ).use { statement ->
            leaderboardModes.forEachIndexed { index, id -> statement.bindText(index + 1, id) }
            statement.step()
        }

        connection.execSQL("DROP TABLE `scores`")
        connection.execSQL("ALTER TABLE `scores_new` RENAME TO `scores`")
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_scores_leaderboard_order` ON `scores` (`onLeaderboard` ASC, `score` DESC, `fiveOfAKindCount` ASC, " +
                "`zeroedCategoryCount` DESC, `upperSectionTotal` ASC, `chanceScore` ASC, `threeOfAKindScore` ASC, `fourOfAKindScore` ASC)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_scores_mode_order` ON `scores` (`gameModeId` ASC, `playedUnmodified` ASC, `score` DESC, " +
                "`fiveOfAKindCount` ASC, `zeroedCategoryCount` DESC, `upperSectionTotal` ASC, `chanceScore` ASC, `threeOfAKindScore` ASC, " +
                "`fourOfAKindScore` ASC)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_scores_playerKey_onLeaderboard_score` ON `scores` (`playerKey`, `onLeaderboard`, `score`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_scores_primary_player` ON `scores` " +
                "(`isPrimaryPlayer`, `gameModeId`, `playedUnmodified`, `timestampEpochMillis`, `score`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_scores_player_mode_totals` ON `scores` " +
                "(`playerKey`, `gameModeId`, `won`, `score`, `fiveOfAKindCount`, `timestampEpochMillis`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_scores_player_history` ON `scores` (`playerKey` ASC, `timestampEpochMillis` DESC, `won` ASC)",
        )
    }

    private fun migrateDismissals(connection: SQLiteConnection) {
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

/**
 * Version 3 adds [ScoreEntry.playedUnmodified] to the two Statistics indices, so Statistics - which counts only games with
 * no modifier on - still reads every player's games from an index alone. No row changes.
 */
internal object Migration2To3 : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP INDEX IF EXISTS `index_scores_player_mode_totals`")
        connection.execSQL("DROP INDEX IF EXISTS `index_scores_player_history`")
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_scores_player_mode_totals` ON `scores` " +
                "(`playerKey`, `gameModeId`, `won`, `score`, `fiveOfAKindCount`, `timestampEpochMillis`, `playedUnmodified`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_scores_player_history` ON `scores` " +
                "(`playerKey` ASC, `timestampEpochMillis` DESC, `won` ASC, `playedUnmodified` ASC)",
        )
    }
}
