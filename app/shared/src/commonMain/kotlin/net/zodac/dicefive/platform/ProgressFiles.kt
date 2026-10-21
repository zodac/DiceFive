package net.zodac.dicefive.platform

/**
 * Where the player keeps a progress file ([net.zodac.dicefive.online.ProgressFileCodec]): the platform's own file
 * picker, for a build that has progress files at all ([PlatformServices.progressFiles]). It only moves the bytes; what
 * goes in them, and what is done with them, is shared.
 */
interface ProgressFiles {

    /** The secret this build seals and opens its files with: the release's own, which is never in the source. */
    val key: ByteArray

    /** Asks the player where to save [bytes], suggesting [fileName], and writes them there. */
    suspend fun save(fileName: String, bytes: ByteArray): ProgressFileResult

    /** Asks the player for a file and reads it whole. */
    suspend fun open(): ProgressFileResult
}

/** How far a [ProgressFiles] call got. */
sealed interface ProgressFileResult {

    /** Saved, or opened: [bytes] are what was read (empty for a save). */
    class Done(val bytes: ByteArray = ByteArray(0)) : ProgressFileResult

    /** The player closed the picker without choosing. */
    data object Cancelled : ProgressFileResult

    /** The file couldn't be written or read. */
    data object Failed : ProgressFileResult
}
