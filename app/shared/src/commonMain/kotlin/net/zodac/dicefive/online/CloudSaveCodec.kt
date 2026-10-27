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

    /** This app's format. */
    const val VERSION = 1

    /** The snapshot's name on the service. Permanent: another name would be another, empty, save. */
    const val SNAPSHOT_NAME = "dicefive-progress"

    /** The most a save is read up to once inflated: anything larger isn't one, and [decode] reads it as corrupt. */
    internal const val MAX_INFLATED_BYTES = 16L * 1024 * 1024

    fun encode(save: CloudSave): ByteArray {
        val json = buildJsonObject {
            put("version", save.version)
            put("achievements", encode(save.achievements))
            put(
                "scores",
                buildJsonObject {
                    put("resetAt", save.scoresResetAtEpochMillis)
                    put("rows", JsonArray(save.scores.rows.map(::encodeRow)))
                    put("dismissed", jsonLongs(save.scores.dismissedAt))
                },
            )
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

    /**
     * The save in [bytes], or null if they aren't one this app can read at all - corrupt, or not gzipped JSON. A newer
     * version whose fields can't be read comes back empty but for its [CloudSave.version], so it is still never written
     * over.
     */
    fun decode(bytes: ByteArray): CloudSave? {
        val root = runCatching {
            val text = GzipSource(Buffer().write(bytes)).buffer().use { source ->
                // A few kilobytes can inflate to gigabytes: a save that would is no save, rather than the app's memory.
                check(!source.request(MAX_INFLATED_BYTES + 1)) { "Larger than a save" }
                source.readUtf8()
            }
            parseJson(text) as JsonObject
        }.getOrNull() ?: return null
        val version = runCatching { root.intOrNull("version") }.getOrNull() ?: return null
        return runCatching { decodeBody(root, version) }.getOrElse { if (version > VERSION) CloudSave(version = version) else null }
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
                rows = ((scores["rows"] as? JsonArray)?.items ?: emptyList()).map { decodeRow(it as JsonObject) },
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

    // One object per game, with ScoreEntry's own field names; the uuid as its 32 hex digits.
    private fun encodeRow(row: ScoreEntry): JsonObject = buildJsonObject {
        put("uuid", row.uuid.toHexString())
        put("playerName", row.playerName)
        put("score", row.score)
        put("timestampEpochMillis", row.timestampEpochMillis)
        put("won", row.won?.let(::JsonBoolean) ?: JsonNull)
        put("isPrimaryPlayer", row.isPrimaryPlayer)
        put("fiveOfAKindCount", row.fiveOfAKindCount)
        put("zeroedCategoryCount", row.zeroedCategoryCount)
        put("upperSectionTotal", row.upperSectionTotal)
        put("chanceScore", row.chanceScore)
        put("threeOfAKindScore", row.threeOfAKindScore)
        put("fourOfAKindScore", row.fourOfAKindScore)
        // Derived from the mode and playedUnmodified on reading, but still written: format 1's readers require it.
        put("onLeaderboard", row.onLeaderboard)
        put("gameModeId", row.gameModeId)
        put("playedUnmodified", row.playedUnmodified)
    }

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
