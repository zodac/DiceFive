package net.zodac.dicefive.online

import net.zodac.dicefive.data.Stamped
import net.zodac.dicefive.data.achievements.CloudAchievements
import net.zodac.dicefive.data.newerOf
import net.zodac.dicefive.data.scores.CloudScores
import net.zodac.dicefive.data.settings.SettingValue

/**
 * Everything one account keeps in the cloud: the whole of a device's synced progress, or several devices' merged
 * ([CloudSaveMerge]). Only the Animations level and the Styles page fit - about what a device can draw and its screen -
 * are each device's own and never in it. Stored as [CloudSaveCodec] writes it.
 */
data class CloudSave(
    /** The format it was written in: an app reads a newer one's fields that it knows, but never writes over it. */
    val version: Int = CloudSaveCodec.VERSION,
    val achievements: CloudAchievements = CloudAchievements(),
    /** When the leaderboard was last reset, on any device (0: never): every score and dismissal from before is gone. */
    val scoresResetAtEpochMillis: Long = 0,
    val scores: CloudScores = CloudScores(),
    /** The settings - the New Game setup, the Styles picks, sound, vibration, the leave-game confirmation - by preference key name. */
    val settings: Map<String, Stamped<SettingValue?>> = emptyMap(),
    /** The game in progress (`GameStateJson`), or null once it finished or was abandoned. */
    val inProgressGame: Stamped<String?> = Stamped(null, 0),
) {
    /** Whether this app may write over it: not over a newer app's format, whose fields it would drop. */
    val writableHere: Boolean get() = version <= CloudSaveCodec.VERSION
}

/**
 * Combines two [CloudSave]s - two devices', or a device's and the cloud's - without asking which to keep: the data are
 * facts, so nothing needs throwing away. Either order gives the same result, and merging one in twice changes nothing.
 *
 * - Achievement unlocks are a union, each at its earliest time.
 * - A [net.zodac.dicefive.model.CounterMerge.SUM] counter keeps each device's share and adds them up; a share only
 *   grows, so the larger of two copies is the newer. Flags are a union; a streak comes from the latest game.
 * - Scores are a union by [net.zodac.dicefive.data.scores.ScoreEntry.uuid], less any from before the latest leaderboard
 *   reset. A dismissed Statistics card stays dismissed until a later game under its name, played anywhere.
 * - Each setting, and the game in progress, is whichever was set last; one set before stamps existed beats one never set.
 */
object CloudSaveMerge {

    fun merge(a: CloudSave, b: CloudSave): CloudSave {
        val resetAt = maxOf(a.scoresResetAtEpochMillis, b.scoresResetAtEpochMillis)
        val rows = (a.scores.rows + b.scores.rows)
            .filter { it.timestampEpochMillis > resetAt }
            .distinctBy { it.uuid }
            .sortedWith(compareBy({ it.timestampEpochMillis }, { it.uuid }))
        val lastPlayed = HashMap<String, Long>()
        for (row in rows) {
            val name = row.playerName.lowercase()
            lastPlayed[name] = maxOf(lastPlayed[name] ?: Long.MIN_VALUE, row.timestampEpochMillis)
        }
        val dismissedAt = unionBy(a.scores.dismissedAt, b.scores.dismissedAt, ::maxOf)
            .filter { (name, at) -> at > resetAt && (lastPlayed[name.lowercase()] ?: Long.MIN_VALUE) <= at }
        return CloudSave(
            // The newer format's: a merge with a newer app's save is still one this app mustn't write.
            version = maxOf(a.version, b.version),
            achievements = merge(a.achievements, b.achievements),
            scoresResetAtEpochMillis = resetAt,
            scores = CloudScores(rows, dismissedAt),
            settings = unionBy(a.settings, b.settings) { x, y -> newerOf(x, y) { it.toString() } },
            inProgressGame = newerOf(a.inProgressGame, b.inProgressGame) { it.orEmpty() },
        )
    }

    fun merge(a: CloudAchievements, b: CloudAchievements): CloudAchievements = CloudAchievements(
        unlockedAt = unionBy(a.unlockedAt, b.unlockedAt, ::minOf),
        deviceCounters = unionBy(a.deviceCounters, b.deviceCounters) { x, y -> unionBy(x, y, ::maxOf) },
        flagCounters = unionBy(a.flagCounters, b.flagCounters, Int::or),
        latestCounters = unionBy(a.latestCounters, b.latestCounters) { x, y -> newerOf(x, y) { it.toString().padStart(11, '0') } },
    )

    /** Every key of [a] and [b], with [combine] of the two values where both have one. */
    private fun <K, V> unionBy(a: Map<K, V>, b: Map<K, V>, combine: (V, V) -> V): Map<K, V> {
        val result = LinkedHashMap(a)
        for ((key, value) in b) result[key] = result[key]?.let { combine(it, value) } ?: value
        return result
    }
}
