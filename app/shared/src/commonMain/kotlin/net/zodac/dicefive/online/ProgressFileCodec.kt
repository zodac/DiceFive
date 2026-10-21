package net.zodac.dicefive.online

import kotlin.uuid.Uuid
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

/**
 * A [CloudSave] as a file the player keeps ([PROGRESS_FILE_EXTENSION]): the cloud save's own bytes
 * ([CloudSaveCodec.encode]), sealed with the build's key so they can't be read, or changed and read back, without it.
 *
 * The file is [MAGIC], a [FORMAT] byte and a random [NONCE_BYTES]-byte nonce, then the save XORed with a keystream (each
 * 32 bytes the HMAC-SHA256 of the nonce and a block counter), then an HMAC-SHA256 of everything before it. The two HMACs
 * use keys derived from the build's own, so neither is the key itself. A file changed by even one bit, or sealed with
 * another key, reads as no file at all.
 *
 * Permanent once released: a [FORMAT] this app doesn't know is not a file it can read, so a new one is added beside it.
 */
object ProgressFileCodec {

    /** The envelope's format: how the save is sealed, not the save's own ([CloudSave.version]). */
    const val FORMAT: Byte = 1

    private val MAGIC = "DiceFive".encodeUtf8()
    private const val NONCE_BYTES = 16
    private const val MAC_BYTES = 32
    private val HEADER_BYTES = MAGIC.size + 1 + NONCE_BYTES

    fun encode(save: CloudSave, key: ByteArray, nonce: ByteArray = Uuid.random().toByteArray()): ByteArray {
        require(nonce.size == NONCE_BYTES) { "A nonce is $NONCE_BYTES bytes" }
        val header = Buffer().write(MAGIC).writeByte(FORMAT.toInt()).write(nonce).readByteString()
        val sealed = keystream(key, nonce, CloudSaveCodec.encode(save))
        val body = Buffer().write(header).write(sealed).readByteString()
        return Buffer().write(body).write(mac(key, body)).readByteArray()
    }

    /** The save in [bytes], or null if they aren't a progress file sealed with [key] - changed, cut short, or not one at all. */
    fun decode(bytes: ByteArray, key: ByteArray): CloudSave? {
        if (bytes.size < HEADER_BYTES + MAC_BYTES) return null
        val file = bytes.toByteString()
        if (!file.rangeEquals(0, MAGIC, 0, MAGIC.size) || file[MAGIC.size] != FORMAT) return null
        val body = file.substring(0, file.size - MAC_BYTES)
        if (!constantTimeEquals(mac(key, body), file.substring(file.size - MAC_BYTES))) return null
        val nonce = file.substring(MAGIC.size + 1, HEADER_BYTES).toByteArray()
        return CloudSaveCodec.decode(keystream(key, nonce, body.substring(HEADER_BYTES).toByteArray()))
    }

    /** [data] XORed with the keystream for [nonce]: sealing and opening are the same step. */
    private fun keystream(key: ByteArray, nonce: ByteArray, data: ByteArray): ByteArray {
        val streamKey = derive(key, "seal")
        val result = data.copyOf()
        var block = 0
        var offset = 0
        while (offset < result.size) {
            val stream = Buffer().write(nonce).writeInt(block++).readByteString().hmacSha256(streamKey)
            for (i in 0 until minOf(stream.size, result.size - offset)) {
                result[offset + i] = (result[offset + i].toInt() xor stream[i].toInt()).toByte()
            }
            offset += stream.size
        }
        return result
    }

    private fun mac(key: ByteArray, body: ByteString): ByteString = body.hmacSha256(derive(key, "check"))

    private fun derive(key: ByteArray, purpose: String): ByteString = "DiceFive progress file: $purpose".encodeUtf8().hmacSha256(key.toByteString())

    private fun constantTimeEquals(a: ByteString, b: ByteString): Boolean {
        if (a.size != b.size) return false
        var difference = 0
        for (i in 0 until a.size) difference = difference or (a[i].toInt() xor b[i].toInt())
        return difference == 0
    }
}

/** A progress file's extension, and so its kind to the player's file picker. */
const val PROGRESS_FILE_EXTENSION = "dicefive"
