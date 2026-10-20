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
        assertTrue("\"uuid\":\"0123456789abcdef0123456789abcdef\"" in text, text)
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
            {"version":2,"futureSection":{"x":1},
             "achievements":{"unlocked":{"FIVE_X":5,"FUTURE":6},"devices":{},"flags":{},"latest":{},"futureCounters":[1]},
             "scores":{"resetAt":0,"rows":[{"uuid":"0123456789abcdef0123456789abcdef","playerName":"Ann","score":200,"timestampEpochMillis":10,"won":null,
               "isPrimaryPlayer":true,"fiveOfAKindCount":0,"zeroedCategoryCount":0,"upperSectionTotal":0,"chanceScore":0,
               "threeOfAKindScore":0,"fourOfAKindScore":0,"onLeaderboard":true,"gameModeId":"STANDARD","playedUnmodified":true,
               "futureColumn":"x"}],"dismissed":{}},
             "settings":{},"game":{"value":null,"at":0}}
        """.trimIndent()
        val save = CloudSaveCodec.decode(gzip(newer))!!
        assertEquals(2, save.version)
        assertFalse(save.writableHere)
        assertEquals(mapOf("FIVE_X" to 5L, "FUTURE" to 6L), save.achievements.unlockedAt)
        assertEquals(listOf("0123456789abcdef0123456789abcdef"), save.scores.rows.map { it.uuid.toHexString() })
        // Merged with this app's own copy, it is still the newer format's.
        assertFalse(CloudSaveMerge.merge(CloudSave(), save).writableHere)
    }

    @Test
    fun `a newer app's save this app can't read past comes back empty - and never written over`() {
        val save = CloudSaveCodec.decode(gzip("{\"version\":3,\"scores\":{\"rows\":\"packed\"}}"))!!
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
    }
}
