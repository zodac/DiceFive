package net.zodac.dicefive.device

import android.content.Context
import java.io.File
import net.zodac.dicefive.data.game.SavedGameDiscard

/**
 * A [SavedGameDiscard] as an empty file, so a process on its way down can ask without opening the saved game's store,
 * and a process of the app's own carries it out at its next start. In the no-backup folder: a backup restored later
 * mustn't remove the game saved by then.
 */
internal class SavedGameDiscardFile(context: Context) : SavedGameDiscard {

    private val file = File(context.noBackupFilesDir, FILE_NAME)

    fun request() {
        file.createNewFile()
    }

    override fun isRequested(): Boolean = file.exists()

    override fun done() {
        file.delete()
    }

    private companion object {
        const val FILE_NAME = "discard_saved_game"
    }
}
