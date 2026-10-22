package net.zodac.dicefive.device

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.zodac.dicefive.BuildConfig
import net.zodac.dicefive.platform.ProgressFileResult
import net.zodac.dicefive.platform.ProgressFiles

/**
 * [ProgressFiles] through Android's own pickers (the Storage Access Framework): the player picks the place, and the app
 * needs no storage permission. One for the process, attached to each new MainActivity: a picker outlives the Activity
 * that opened it when the screen turns, and its answer goes to the next one's launcher (registered under the same key,
 * in the same order), which completes the call still waiting in the view model.
 */
internal object AndroidProgressFiles : ProgressFiles {

    /** Any bytes: a file of [net.zodac.dicefive.online.PROGRESS_FILE_EXTENSION] has no type of its own to Android. */
    private const val MIME_TYPE = "application/octet-stream"

    /** The most a progress file is read up to: anything larger isn't one. */
    private const val MAX_BYTES = 16 * 1024 * 1024

    override val key: ByteArray = BuildConfig.PROGRESS_FILE_KEY.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private var resolver: ContentResolver? = null
    private var save: ActivityResultLauncher<String>? = null
    private var open: ActivityResultLauncher<Array<String>>? = null

    /** The picker open now, answered by whichever Activity's launcher gets its result. */
    private var picking: CompletableDeferred<Uri?>? = null

    /** Registers [activity]'s pickers, which it must do before it starts. */
    fun attach(activity: ComponentActivity): ProgressFiles {
        resolver = activity.applicationContext.contentResolver
        val save = activity.registerForActivityResult(ActivityResultContracts.CreateDocument(MIME_TYPE)) { picking?.complete(it) }
        val open = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { picking?.complete(it) }
        this.save = save
        this.open = open
        activity.lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY && this.save === save) {
                    this.save = null
                    this.open = null
                }
            },
        )
        return this
    }

    override suspend fun save(fileName: String, bytes: ByteArray): ProgressFileResult = withPicked({ save?.launch(fileName) }) { resolver, uri ->
        // "wt": a file picked to be overwritten is cut to the new length, not left with the old one's tail.
        checkNotNull(resolver.openOutputStream(uri, "wt")) { "No stream for $uri" }.use { it.write(bytes) }
        ProgressFileResult.Done()
    }

    override suspend fun open(): ProgressFileResult = withPicked({ open?.launch(arrayOf("*/*")) }) { resolver, uri ->
        ProgressFileResult.Done(checkNotNull(resolver.openInputStream(uri)) { "No stream for $uri" }.use(::readUpToMax))
    }

    /**
     * Opens a picker with [launch] (null: no Activity to open it from), then [use]s the file picked, off the main thread.
     * Fails at once while another call's picker is still open: there's one place for an answer, and it is that call's.
     */
    private suspend fun withPicked(launch: () -> Unit?, use: (ContentResolver, Uri) -> ProgressFileResult): ProgressFileResult {
        val resolver = resolver ?: return ProgressFileResult.Failed
        if (picking != null) return ProgressFileResult.Failed
        val picked = CompletableDeferred<Uri?>()
        picking = picked
        try {
            if (runCatching(launch).getOrNull() == null) return ProgressFileResult.Failed
            val uri = picked.await() ?: return ProgressFileResult.Cancelled
            return withContext(Dispatchers.IO) { runCatching { use(resolver, uri) }.getOrDefault(ProgressFileResult.Failed) }
        } finally {
            if (picking === picked) picking = null
        }
    }

    private fun readUpToMax(input: InputStream): ByteArray {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return bytes.toByteArray()
            bytes.write(buffer, 0, read)
            check(bytes.size() <= MAX_BYTES) { "Larger than a progress file" }
        }
    }
}
