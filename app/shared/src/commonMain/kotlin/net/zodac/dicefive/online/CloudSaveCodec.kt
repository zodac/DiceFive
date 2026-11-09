package net.zodac.dicefive.online

import kotlin.uuid.Uuid
import net.zodac.dicefive.data.JsonArray
import net.zodac.dicefive.data.JsonBoolean
import net.zodac.dicefive.data.JsonNull
import net.zodac.dicefive.data.JsonNumber
import net.zodac.dicefive.data.JsonObject
import net.zodac.dicefive.data.JsonString
import net.zodac.dicefive.data.JsonValue
import net.zodac.dicefive.data.Stamped
import net.zodac.dicefive.data.achievements.CloudAchievements
import net.zodac.dicefive.data.boolean
import net.zodac.dicefive.data.booleanOrNull
import net.zodac.dicefive.data.buildJsonObject
import net.zodac.dicefive.data.int
import net.zodac.dicefive.data.intOrNull
import net.zodac.dicefive.data.ints
import net.zodac.dicefive.data.jsonInts
import net.zodac.dicefive.data.jsonLongs
import net.zodac.dicefive.data.long
import net.zodac.dicefive.data.longOrNull
import net.zodac.dicefive.data.longs
import net.zodac.dicefive.data.objOrNull
import net.zodac.dicefive.data.parseJson
import net.zodac.dicefive.data.scores.CloudScores
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.data.settings.SettingValue
import net.zodac.dicefive.data.string
import net.zodac.dicefive.data.toJson
import okio.Buffer
import okio.GzipSink
import okio.GzipSource
import okio.buffer
import okio.use

/**
 * A [CloudSave] as the bytes the online service stores, in the one snapshot named [SNAPSHOT_NAME]: compact JSON,
 * gzipped.
 *
 * Every format, now and later, is gzipped JSON with a top-level `"version"`. Within a version, fields are only ever
 * added, and a reader skips the ones it doesn't know; a change an older app can't read past bumps [VERSION]. An app
 * reading a newer version takes what it can and never writes over it ([CloudSave.writableHere]).
 */
object CloudSaveCodec {

    /**
     * This app's format. Version 2 holds the scores as [encodeScores] packs them: an app on version 1 finds no `rows`, so
     * takes the rest of the save and, as it's a newer format, never writes over it. Version 1's `rows` are still read.
     */
    const val VERSION = 2

    /** The snapshot's name on the service. Permanent: another name would be another, empty, save. */
    const val SNAPSHOT_NAME = "dicefive-progress"

    /**
     * The most a save is read up to once inflated: more than a version 1 save the service's [MAX_SAVE_BYTES] can hold.
     * Anything larger is [DecodedSave.TooLarge].
     */
    internal const val MAX_INFLATED_BYTES = 32L * 1024 * 1024

    /** The most this app sends the service, which stores 3 MB at most: a larger save stays on the device. */
    internal const val MAX_SAVE_BYTES = 3_000_000

    private const val ROW_SEPARATOR = ';'
    private const val FIELD_SEPARATOR = ','
    private const val FLAG_PRIMARY_PLAYER = 1
    private const val FLAG_UNMODIFIED = 2
    private const val FLAG_LOST = 4
    private const val FLAG_WON = 8

    fun encode(save: CloudSave): ByteArray {
        val json = buildJsonObject {
            // Never below this app's: what's written is this app's format, whichever save it was merged from.
            put("version", maxOf(save.version, VERSION))
            put("achievements", encode(save.achievements))
            put("scores", encodeScores(save))
            put(
                "settings",
                JsonObject(save.settings.mapValues { (_, stamped) -> stamped(stamped) { encodeSetting(it) } }),
            )
            put("game", stamped(save.inProgressGame) { state -> state?.let(::JsonString) ?: JsonNull })
        }
        val gzipped = Buffer()
        GzipSink(gzipped).buffer().use { it.writeUtf8(json.toJson()) }
        return gzipped.readByteArray()
    }

    /** The save in [bytes], or null if this app can't read it, for whichever reason [read] gives. */
    fun decode(bytes: ByteArray): CloudSave? = (read(bytes) as? DecodedSave.Readable)?.save

    /**
     * What [bytes] are: a save, one too large to read here, or not a save at all. A newer version whose fields can't be
     * read comes back empty but for its [CloudSave.version], so it is still never written over.
     */
    fun read(bytes: ByteArray): DecodedSave = try {
        val text = GzipSource(Buffer().write(bytes)).buffer().use { source ->
            // A few kilobytes can inflate to gigabytes: one that would is never read into the app's memory.
            if (source.request(MAX_INFLATED_BYTES + 1)) null else source.readUtf8()
        }
        if (text == null) DecodedSave.TooLarge else readJson(text)
    } catch (_: Exception) {
        DecodedSave.Corrupt
    } catch (_: Throwable) {
        // Out of memory, or the stack: a save this device can't hold says nothing about whether it is one.
        DecodedSave.TooLarge
    }

    private fun readJson(text: String): DecodedSave {
        val root = parseJson(text) as JsonObject
        val version = root.intOrNull("version") ?: return DecodedSave.Corrupt
        return try {
            DecodedSave.Readable(decodeBody(root, version))
        } catch (_: Exception) {
            if (version > VERSION) DecodedSave.Readable(CloudSave(version = version)) else DecodedSave.Corrupt
        }
    }

    private fun decodeBody(root: JsonObject, version: Int): CloudSave {
        val achievements = root.section("achievements")
        val scores = root.section("scores")
        return CloudSave(
            version = version,
            achievements = CloudAchievements(
                unlockedAt = achievements.section("unlocked").longs(),
                deviceCounters = achievements.section("devices").fields.mapValues { (_, counters) -> (counters as JsonObject).ints() },
                flagCounters = achievements.section("flags").ints(),
                latestCounters = achievements.section("latest").fields.mapValues { (_, value) -> decodeStamped(value) { (it as JsonNumber).toInt() } },
            ),
            scoresResetAtEpochMillis = scores.longOrNull("resetAt") ?: 0,
            scores = CloudScores(
                rows = decodeRows(scores),
                dismissedAt = scores.section("dismissed").longs(),
            ),
            settings = root.section("settings").fields.mapValues { (_, value) -> decodeStamped(value, ::decodeSetting) },
            inProgressGame = root.objOrNull("game")?.let { game -> decodeStamped(game) { (it as? JsonString)?.value } }
                ?: Stamped(null, 0),
        )
    }

    private fun encode(achievements: CloudAchievements): JsonObject = buildJsonObject {
        put("unlocked", jsonLongs(achievements.unlockedAt))
        put("devices", JsonObject(achievements.deviceCounters.mapValues { (_, counters) -> jsonInts(counters) }))
        put("flags", jsonInts(achievements.flagCounters))
        put("latest", JsonObject(achievements.latestCounters.mapValues { (_, stamped) -> stamped(stamped) { JsonNumber(it) } }))
    }

    /**
     * The scores, in far less than an object per row: `names` and `modes` hold each player name and mode id once, and
     * `table` is one string of rows, split by [ROW_SEPARATOR], each row's fields by [FIELD_SEPARATOR]: its uuid as 32 hex
     * digits, its name's and mode's places in those lists, score, time since the row before, flags ([FLAG_PRIMARY_PLAYER]
     * and the rest), then 5x count, zeroed categories, upper section, Chance, 3x, 4x, and dice rolled. Fields are only added
     * at a row's end, and a row without one (written before it was added) reads it as 0. A row's [ScoreEntry.playerKey] and [ScoreEntry.onLeaderboard] are worked out again on reading.
     */
    private fun encodeScores(save: CloudSave): JsonObject {
        val rows = save.scores.rows
        val names = rows.mapTo(LinkedHashSet()) { it.playerName }.withIndex().associate { (index, name) -> name to index }
        val modes = rows.mapTo(LinkedHashSet()) { it.gameModeId }.withIndex().associate { (index, mode) -> mode to index }
        val table = StringBuilder()
        var previousTime = 0L
        for (row in rows) {
            if (table.isNotEmpty()) table.append(ROW_SEPARATOR)
            val flags = (if (row.isPrimaryPlayer) FLAG_PRIMARY_PLAYER else 0) or
                (if (row.playedUnmodified) FLAG_UNMODIFIED else 0) or
                when (row.won) {
                    null -> 0
                    true -> FLAG_WON
                    false -> FLAG_LOST
                }
            listOf(
                row.uuid.toHexString(), names.getValue(row.playerName), modes.getValue(row.gameModeId), row.score, row.timestampEpochMillis - previousTime, flags,
                row.fiveOfAKindCount, row.zeroedCategoryCount, row.upperSectionTotal, row.chanceScore, row.threeOfAKindScore, row.fourOfAKindScore,
                row.diceRolled,
            ).joinTo(table, FIELD_SEPARATOR.toString())
            previousTime = row.timestampEpochMillis
        }
        return buildJsonObject {
            put("resetAt", save.scoresResetAtEpochMillis)
            put("names", JsonArray(names.keys.map(::JsonString)))
            put("modes", JsonArray(modes.keys.map(::JsonString)))
            put("table", table.toString())
            put("dismissed", jsonLongs(save.scores.dismissedAt))
        }
    }

    /** The rows of a save: [encodeScores]' `table`, or version 1's `rows`, an object for each. */
    private fun decodeRows(scores: JsonObject): List<ScoreEntry> {
        val table = (scores["table"] as? JsonString)?.value ?: return ((scores["rows"] as? JsonArray)?.items ?: emptyList()).map { decodeRow(it as JsonObject) }
        if (table.isEmpty()) return emptyList()
        val names = (scores["names"] as JsonArray).items.map { (it as JsonString).value }
        val modes = (scores["modes"] as JsonArray).items.map { (it as JsonString).value }
        var time = 0L
        return table.split(ROW_SEPARATOR).map { row ->
            val fields = row.split(FIELD_SEPARATOR)
            time += fields[4].toLong()
            val flags = fields[5].toInt()
            ScoreEntry(
                uuid = Uuid.parseHex(fields[0]),
                playerName = names[fields[1].toInt()],
                gameModeId = modes[fields[2].toInt()],
                score = fields[3].toInt(),
                timestampEpochMillis = time,
                isPrimaryPlayer = flags and FLAG_PRIMARY_PLAYER != 0,
                playedUnmodified = flags and FLAG_UNMODIFIED != 0,
                won = when {
                    flags and FLAG_WON != 0 -> true
                    flags and FLAG_LOST != 0 -> false
                    else -> null
                },
                fiveOfAKindCount = fields[6].toInt(),
                zeroedCategoryCount = fields[7].toInt(),
                upperSectionTotal = fields[8].toInt(),
                chanceScore = fields[9].toInt(),
                threeOfAKindScore = fields[10].toInt(),
                fourOfAKindScore = fields[11].toInt(),
                diceRolled = fields.getOrNull(12)?.toInt() ?: 0,
            )
        }
    }

    // Version 1's row: one object per game, with ScoreEntry's own field names; the uuid as its 32 hex digits.
    private fun decodeRow(row: JsonObject): ScoreEntry = ScoreEntry(
        uuid = Uuid.parseHex(row.string("uuid")),
        playerName = row.string("playerName"),
        score = row.int("score"),
        timestampEpochMillis = row.long("timestampEpochMillis"),
        won = row.booleanOrNull("won"),
        isPrimaryPlayer = row.boolean("isPrimaryPlayer"),
        fiveOfAKindCount = row.int("fiveOfAKindCount"),
        zeroedCategoryCount = row.int("zeroedCategoryCount"),
        upperSectionTotal = row.int("upperSectionTotal"),
        chanceScore = row.int("chanceScore"),
        threeOfAKindScore = row.int("threeOfAKindScore"),
        fourOfAKindScore = row.int("fourOfAKindScore"),
        gameModeId = row.string("gameModeId"),
        playedUnmodified = row.boolean("playedUnmodified"),
    )

    private fun encodeSetting(value: SettingValue?): JsonValue = when (value) {
        is SettingValue.Text -> JsonString(value.value)
        is SettingValue.Number -> JsonNumber(value.value)
        is SettingValue.Flag -> JsonBoolean(value.value)
        null -> JsonNull
    }

    private fun decodeSetting(value: JsonValue): SettingValue? = when (value) {
        is JsonString -> SettingValue.Text(value.value)
        is JsonNumber -> SettingValue.Number(value.toInt())
        is JsonBoolean -> SettingValue.Flag(value.value)
        else -> null
    }

    private fun <T> stamped(stamped: Stamped<T>, encode: (T) -> JsonValue): JsonObject = buildJsonObject {
        put("value", encode(stamped.value))
        put("at", stamped.atEpochMillis)
    }

    private fun <T> decodeStamped(json: JsonValue, decode: (JsonValue) -> T): Stamped<T> {
        val stamped = json as JsonObject
        return Stamped(decode(stamped["value"] ?: JsonNull), stamped.long("at"))
    }

    /** A missing section reads as an empty one. */
    private fun JsonObject.section(key: String): JsonObject = objOrNull(key) ?: JsonObject(emptyMap())
}

/** What a save's bytes turned out to be ([CloudSaveCodec.read]). */
sealed interface DecodedSave {

    data class Readable(val save: CloudSave) : DecodedSave

    /** Not a save, or a broken one: it holds nothing to keep, so it may be written over. */
    data object Corrupt : DecodedSave

    /** Too large to read here: it may well be someone's whole progress, so it is never written over. */
    data object TooLarge : DecodedSave
}
