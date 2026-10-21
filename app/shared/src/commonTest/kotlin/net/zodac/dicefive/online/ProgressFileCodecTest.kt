package net.zodac.dicefive.online

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zodac.dicefive.data.Stamped
import net.zodac.dicefive.data.achievements.CloudAchievements
import net.zodac.dicefive.data.scores.CloudScores
import net.zodac.dicefive.data.settings.SettingValue

class ProgressFileCodecTest {

    private val key = ByteArray(32) { it.toByte() }
    private val nonce = ByteArray(16) { (0xa0 + it).toByte() }

    private val save = CloudSave(
        achievements = CloudAchievements(
            unlockedAt = mapOf("FIVE_X" to 1_790_000_000_000L),
            deviceCounters = mapOf("phone" to mapOf("GAMES_PLAYED" to 12)),
        ),
        scores = CloudScores(rows = listOf(cloudRow("aa", at = 1_790_000_000_000L))),
        settings = mapOf("dice_style_id" to Stamped(SettingValue.Text("ocean"), 7L)),
        inProgressGame = Stamped("{\"players\":[]}", 11L),
    )

    @Test
    fun `a save round-trips whole`() {
        assertEquals(save, ProgressFileCodec.decode(ProgressFileCodec.encode(save, key), key))
        assertEquals(CloudSave(), ProgressFileCodec.decode(ProgressFileCodec.encode(CloudSave(), key), key))
    }

    @Test
    fun `it starts with the app's name and its format`() {
        val bytes = ProgressFileCodec.encode(save, key, nonce)
        assertEquals("DiceFive", bytes.copyOfRange(0, 8).decodeToString())
        assertEquals(ProgressFileCodec.FORMAT, bytes[8])
        assertTrue(bytes.copyOfRange(9, 25).contentEquals(nonce))
    }

    @Test
    fun `the save inside can't be read without the key - it isn't the cloud save's gzip`() {
        val bytes = ProgressFileCodec.encode(save, key, nonce)
        val body = bytes.copyOfRange(25, bytes.size - 32)
        assertEquals(CloudSaveCodec.encode(save).size, body.size)
        assertFalse(body.contentEquals(CloudSaveCodec.encode(save)))
        assertNull(CloudSaveCodec.decode(body))
    }

    @Test
    fun `each export is sealed with a new nonce - so two of the same save differ`() {
        assertFalse(ProgressFileCodec.encode(save, key).contentEquals(ProgressFileCodec.encode(save, key)))
    }

    @Test
    fun `a file sealed with another key is no file at all`() {
        val other = ByteArray(32) { 7 }
        assertNull(ProgressFileCodec.decode(ProgressFileCodec.encode(save, key), other))
    }

    @Test
    fun `a file changed by one bit anywhere is no file at all`() {
        val bytes = ProgressFileCodec.encode(save, key, nonce)
        for (index in bytes.indices) {
            val changed = bytes.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertNull(ProgressFileCodec.decode(changed, key), "byte $index")
        }
    }

    @Test
    fun `a file cut short - or with bytes added - is no file at all`() {
        val bytes = ProgressFileCodec.encode(save, key, nonce)
        assertNull(ProgressFileCodec.decode(bytes.copyOf(bytes.size - 1), key))
        assertNull(ProgressFileCodec.decode(bytes + byteArrayOf(0), key))
        assertNull(ProgressFileCodec.decode(bytes.copyOf(40), key))
        assertNull(ProgressFileCodec.decode(ByteArray(0), key))
    }

    @Test
    fun `a format this app doesn't know is no file it can read`() {
        val bytes = ProgressFileCodec.encode(save, key, nonce).also { it[8] = (ProgressFileCodec.FORMAT + 1).toByte() }
        assertNull(ProgressFileCodec.decode(bytes, key))
    }

    @Test
    fun `a cloud save or any other file is not a progress file`() {
        assertNull(ProgressFileCodec.decode(CloudSaveCodec.encode(save), key))
        assertNull(ProgressFileCodec.decode("DiceFive, but not really a progress file at all".encodeToByteArray(), key))
    }
}
