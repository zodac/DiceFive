package net.zodac.dicefive.online

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import net.zodac.dicefive.data.Stamped
import net.zodac.dicefive.data.achievements.CloudAchievements
import net.zodac.dicefive.data.scores.CloudScores
import net.zodac.dicefive.data.settings.SettingValue
import okio.Buffer
import okio.GzipSink
import okio.GzipSource
import okio.buffer
import okio.use

/** What an app on format version 1 reads a save up to: past it, that app takes the save for corrupt and writes over it. */
private const val VERSION_1_MAX_INFLATED_BYTES = 16L * 1024 * 1024

class CloudSaveCodecTest {

    private fun gzip(text: String): ByteArray {
        val out = Buffer()
        GzipSink(out).buffer().use { it.writeUtf8(text) }
        return out.readByteArray()
    }

    private val full = CloudSave(
        achievements = CloudAchievements(
            unlockedAt = mapOf("FIVE_X" to 1_790_000_000_000L),
            deviceCounters = mapOf("phone" to mapOf("GAMES_PLAYED" to 12, "DICE_ROLLED" to 900)),
            flagCounters = mapOf("FIVE_X_NUMBERS" to 0b101),
            latestCounters = mapOf("WIN_STREAK" to Stamped(2, 1_790_000_000_500L)),
        ),
        scoresResetAtEpochMillis = 1_780_000_000_000L,
        scores = CloudScores(
            rows = listOf(cloudRow("aa", at = 1_790_000_000_000L).copy(won = true, gameModeId = "QUICKFIRE", playedUnmodified = false)),
            dismissedAt = mapOf("Bob \"the dice\"" to 1_790_000_000_100L),
        ),
        settings = mapOf(
            "dice_style_id" to Stamped(SettingValue.Text("ocean"), 7L),
            "player_count" to Stamped(SettingValue.Number(3), 8L),
            "extended_scores_modifier" to Stamped(SettingValue.Flag(true), 9L),
            "rolls_per_turn_modifier" to Stamped(null, 10L),
        ),
        inProgressGame = Stamped("{\"players\":[]}", 11L),
    )

    @Test
    fun `a save round-trips whole`() {
        assertEquals(full, CloudSaveCodec.decode(CloudSaveCodec.encode(full)))
        assertEquals(CloudSave(), CloudSaveCodec.decode(CloudSaveCodec.encode(CloudSave())))
    }

    @Test
    fun `it is gzipped`() {
        val bytes = CloudSaveCodec.encode(full)
        assertEquals(0x1f, bytes[0].toInt() and 0xff)
        assertEquals(0x8b, bytes[1].toInt() and 0xff)
    }

    @Test
    fun `bytes that aren't a save read as none`() {
        assertNull(CloudSaveCodec.decode(ByteArray(0)))
        assertNull(CloudSaveCodec.decode("not gzip".encodeToByteArray()))
        assertNull(CloudSaveCodec.decode(gzip("[1,2]")))
        assertNull(CloudSaveCodec.decode(gzip("{\"scores\":{}}")))
        // This version, but broken inside: corrupt, so it may be written over.
        assertNull(CloudSaveCodec.decode(gzip("{\"version\":1,\"scores\":{\"rows\":[{\"uuid\":5}]}}")))
        assertNull(CloudSaveCodec.decode(gzip(saveWithRow(uuid = "not-a-uuid", gameModeId = "standard", onLeaderboard = true))))
    }

    /** A format-1 save holding one row, as JSON. */
    private fun saveWithRow(uuid: String, gameModeId: String, onLeaderboard: Boolean) = """
        {"version":1,"achievements":{"unlocked":{},"devices":{},"flags":{},"latest":{}},
         "scores":{"resetAt":0,"rows":[{"uuid":"$uuid","playerName":"Ann","score":200,"timestampEpochMillis":10,"won":null,
           "isPrimaryPlayer":true,"fiveOfAKindCount":0,"zeroedCategoryCount":0,"upperSectionTotal":0,"chanceScore":0,
           "threeOfAKindScore":0,"fourOfAKindScore":0,"onLeaderboard":$onLeaderboard,"gameModeId":"$gameModeId","playedUnmodified":true}],
           "dismissed":{}},
         "settings":{},"game":{"value":null,"at":0}}
    """.trimIndent()

    @Test
    fun `a row's uuid is written as its 32 hex digits`() {
        val row = cloudRow("", at = 10).copy(uuid = Uuid.parseHex("0123456789abcdef0123456789abcdef"))
        val text = GzipSource(Buffer().write(CloudSaveCodec.encode(CloudSave(scores = CloudScores(rows = listOf(row)))))).buffer().use { it.readUtf8() }
        assertTrue("0123456789abcdef0123456789abcdef," in text, text)
    }

    @Test
    fun `whether a row is on the Leaderboard comes from its mode and modifiers - not from the save`() {
        val uuid = "0123456789abcdef0123456789abcdef"
        assertEquals(listOf(false), CloudSaveCodec.decode(gzip(saveWithRow(uuid, "quickfire", onLeaderboard = true)))?.scores?.rows?.map { it.onLeaderboard })
        assertEquals(listOf(true), CloudSaveCodec.decode(gzip(saveWithRow(uuid, "standard", onLeaderboard = false)))?.scores?.rows?.map { it.onLeaderboard })
    }

    @Test
    fun `a newer app's save is read for what this app knows - and never written over`() {
        val newer = """
            {"version":3,"futureSection":{"x":1},
             "achievements":{"unlocked":{"FIVE_X":5,"FUTURE":6},"devices":{},"flags":{},"latest":{},"futureCounters":[1]},
             "scores":{"resetAt":0,"rows":[{"uuid":"0123456789abcdef0123456789abcdef","playerName":"Ann","score":200,"timestampEpochMillis":10,"won":null,
               "isPrimaryPlayer":true,"fiveOfAKindCount":0,"zeroedCategoryCount":0,"upperSectionTotal":0,"chanceScore":0,
               "threeOfAKindScore":0,"fourOfAKindScore":0,"onLeaderboard":true,"gameModeId":"STANDARD","playedUnmodified":true,
               "futureColumn":"x"}],"dismissed":{}},
             "settings":{},"game":{"value":null,"at":0}}
        """.trimIndent()
        val save = CloudSaveCodec.decode(gzip(newer))!!
        assertEquals(3, save.version)
        assertFalse(save.writableHere)
        assertEquals(mapOf("FIVE_X" to 5L, "FUTURE" to 6L), save.achievements.unlockedAt)
        assertEquals(listOf("0123456789abcdef0123456789abcdef"), save.scores.rows.map { it.uuid.toHexString() })
        // Merged with this app's own copy, it is still the newer format's.
        assertFalse(CloudSaveMerge.merge(CloudSave(), save).writableHere)
    }

    @Test
    fun `a newer app's save this app can't read past comes back empty - and never written over`() {
        val save = CloudSaveCodec.decode(gzip("{\"version\":3,\"scores\":{\"table\":\"packed\"}}"))!!
        assertEquals(CloudSave(version = 3), save)
        assertFalse(save.writableHere)
    }

    @Test
    fun `twenty thousand games fit in well under the service's limit`() {
        val rows = (1..20_000).map { index ->
            cloudRow(uuid = "", at = 1_790_000_000_000L + index * 431_017L, name = "Player ${index % 4}", score = 150 + index % 200)
                .copy(uuid = Uuid.random())
        }
        val bytes = CloudSaveCodec.encode(CloudSave(scores = CloudScores(rows = rows)))
        assertTrue(bytes.size < 1_500_000, "${bytes.size} bytes")
        // And are read back whole, well inside what a save may inflate to.
        assertEquals(20_000, CloudSaveCodec.decode(bytes)?.scores?.rows?.size)
        assertTrue(inflatedSize(bytes) < CloudSaveCodec.MAX_INFLATED_BYTES / 2, "${inflatedSize(bytes)} bytes inflated")
    }

    @Test
    fun `a save that inflates past the most a save may is read as none - not into memory`() {
        val padding = " ".repeat((CloudSaveCodec.MAX_INFLATED_BYTES + 1).toInt())
        val bomb = gzip("{\"version\":1,$padding\"scores\":{}}")
        assertTrue(bomb.size < 100_000, "${bomb.size} bytes gzipped")
        assertNull(CloudSaveCodec.decode(bomb))
    }

    @Test
    fun `every field of a row round-trips - and a name holding the table's own separators stays whole`() {
        val rows = listOf(
            cloudRow("", at = 1_790_000_000_000L, name = "Sam; \"the, dice\"", score = 1_575).copy(
                uuid = Uuid.random(), won = true, isPrimaryPlayer = true, fiveOfAKindCount = 3, zeroedCategoryCount = 1, upperSectionTotal = 84,
                chanceScore = 27, threeOfAKindScore = 24, fourOfAKindScore = 26, gameModeId = "tricolour", playedUnmodified = false,
            ),
            cloudRow("", at = 1_790_000_000_500L, name = "Ada", score = 90).copy(uuid = Uuid.random(), won = false, isPrimaryPlayer = false),
            // Earlier than the row before: a device's own rows aren't in time order until they're merged.
            cloudRow("", at = 1_780_000_000_000L, name = "Ada", score = 91).copy(uuid = Uuid.random(), won = null),
        )
        val read = CloudSaveCodec.decode(CloudSaveCodec.encode(CloudSave(scores = CloudScores(rows = rows))))!!
        assertEquals(rows, read.scores.rows)
        assertEquals(rows.map { it.playerKey }, read.scores.rows.map { it.playerKey })
        assertEquals(rows.map { it.onLeaderboard }, read.scores.rows.map { it.onLeaderboard })
    }

    @Test
    fun `this format has no rows for an app on version 1 to fail on - which takes the rest and never writes over it`() {
        val text = inflated(CloudSaveCodec.encode(full))
        assertTrue(CloudSaveCodec.VERSION > 1)
        assertTrue("\"version\":${CloudSaveCodec.VERSION}" in text, text)
        assertFalse("\"rows\"" in text, text)
    }

    @Test
    fun `a save merged from an older one is written as this format's`() {
        val text = inflated(CloudSaveCodec.encode(full.copy(version = 1)))
        assertTrue("\"version\":${CloudSaveCodec.VERSION}" in text, text)
    }

    @Test
    fun `a hundred thousand games fit the service - and inflate to what an app on version 1 can still read`() {
        val rows = (1..100_000).map { index ->
            cloudRow("", at = 1_790_000_000_000L + index * 431_017L, name = "Player ${index % 4}", score = 90 + (index * 7919) % 330).copy(
                uuid = Uuid.random(), won = index % 3 == 0, fiveOfAKindCount = index % 3, zeroedCategoryCount = (index * 31) % 6,
                upperSectionTotal = 30 + (index * 17) % 70, chanceScore = 5 + (index * 13) % 25, threeOfAKindScore = (index * 11) % 25, fourOfAKindScore = (index * 3) % 25,
            )
        }
        val bytes = CloudSaveCodec.encode(CloudSave(scores = CloudScores(rows = rows)))
        assertTrue(bytes.size < CloudSaveCodec.MAX_SAVE_BYTES, "${bytes.size} bytes")
        assertTrue(inflatedSize(bytes) < VERSION_1_MAX_INFLATED_BYTES, "${inflatedSize(bytes)} bytes inflated")
        assertEquals(rows, CloudSaveCodec.decode(bytes)?.scores?.rows)
    }

    @Test
    fun `a version 1 save past the old limit is still read`() {
        val row = "{\"uuid\":\"%s\",\"playerName\":\"Ann\",\"score\":200,\"timestampEpochMillis\":10,\"won\":null,\"isPrimaryPlayer\":true,\"fiveOfAKindCount\":0," +
            "\"zeroedCategoryCount\":0,\"upperSectionTotal\":0,\"chanceScore\":0,\"threeOfAKindScore\":0,\"fourOfAKindScore\":0,\"onLeaderboard\":true," +
            "\"gameModeId\":\"standard\",\"playedUnmodified\":true}"
        val rows = (1..55_000).joinToString(",") { row.replace("%s", Uuid.random().toHexString()) }
        val text = "{\"version\":1,\"scores\":{\"resetAt\":0,\"rows\":[$rows],\"dismissed\":{}}}"
        assertTrue(text.length > VERSION_1_MAX_INFLATED_BYTES, "${text.length} characters")
        assertEquals(55_000, CloudSaveCodec.decode(gzip(text))?.scores?.rows?.size)
    }

    @Test
    fun `what bytes are - a save - too large to read - or corrupt`() {
        assertEquals(DecodedSave.Readable(full), CloudSaveCodec.read(CloudSaveCodec.encode(full)))
        assertEquals(DecodedSave.Corrupt, CloudSaveCodec.read("not gzip".encodeToByteArray()))
        assertEquals(DecodedSave.Corrupt, CloudSaveCodec.read(gzip("{\"version\":${CloudSaveCodec.VERSION},\"scores\":{\"table\":\"nonsense\",\"names\":[],\"modes\":[]}}")))
        val padding = " ".repeat((CloudSaveCodec.MAX_INFLATED_BYTES + 1).toInt())
        assertEquals(DecodedSave.TooLarge, CloudSaveCodec.read(gzip("{\"version\":1,$padding\"scores\":{}}")))
    }

    private fun inflated(bytes: ByteArray): String = GzipSource(Buffer().write(bytes)).buffer().use { it.readUtf8() }

    private fun inflatedSize(bytes: ByteArray): Long = GzipSource(Buffer().write(bytes)).buffer().use { it.readByteArray().size.toLong() }
}
