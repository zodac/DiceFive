package net.zodac.dicefive.online

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.game.LeaderboardTotals
import net.zodac.dicefive.game.nowEpochMillis

/**
 * This device's progress to and from a progress file ([ProgressFileCodec]), for a build with no online service. The
 * file holds what a cloud save holds ([CloudSaveStore.snapshot]), and an import is the cloud save's merge from another
 * source: every part merged in ([CloudSaveStore.merge]), then whatever the merged totals reach unlocked quietly. Nothing
 * here is picked or thrown away, and importing one file twice changes nothing the second time.
 */
class ProgressTransfer(
    private val store: CloudSaveStore,
    private val achievements: AchievementStore,
    private val leaderboardTotals: suspend () -> LeaderboardTotals,
    private val now: () -> Long = ::nowEpochMillis,
    /** Where a file is sealed and opened, off the main thread. */
    private val background: CoroutineDispatcher = Dispatchers.Default,
) {

    /** Everything a cloud save would hold, sealed with [key]. */
    suspend fun export(key: ByteArray): ByteArray {
        val save = store.snapshot()
        return withContext(background) { ProgressFileCodec.encode(save, key) }
    }

    /**
     * Merges in the progress file [bytes], sealed with [key], telling [onStep] each [ImportStep] as it starts. A newer
     * app's file is refused whole, not merged in part: once this app is updated, it imports in full.
     */
    suspend fun import(bytes: ByteArray, key: ByteArray, onStep: (ImportStep) -> Unit = {}): ProgressImport {
        onStep(ImportStep.OPENING)
        val save = withContext(background) { ProgressFileCodec.decode(bytes, key) } ?: return ProgressImport.NOT_A_PROGRESS_FILE
        if (!save.writableHere) return ProgressImport.FROM_NEWER_APP
        store.merge(save) { merge -> onStep(ImportStep.entries.single { it.merge == merge }) }
        // Last, as the score bands and career points count the merged scores.
        achievements.unlockReachedTargets(leaderboardTotals(), now())
        return ProgressImport.IMPORTED
    }
}

/**
 * The parts of a [ProgressTransfer.import], in the order it takes them, as the Settings card names them: the file
 * opened, then each [merge] step, as a cloud save's sync takes them ([SyncStep.merge]).
 */
enum class ImportStep(val merge: MergeStep? = null) {
    /** The file checked and opened: [ProgressFileCodec.decode]. */
    OPENING,
    SETTINGS(MergeStep.SETTINGS),
    GAME(MergeStep.GAME),
    ACHIEVEMENTS(MergeStep.ACHIEVEMENTS),

    /** The scores merged in, then any achievement the merged totals reach. */
    SCORES(MergeStep.SCORES),
}

/** How a [ProgressTransfer.import] ended. */
enum class ProgressImport {
    IMPORTED,

    /** Not a progress file this build can open: changed, cut short, another kind of file, or sealed by another build. */
    NOT_A_PROGRESS_FILE,

    /** Saved by a newer version of the app: this one would lose what it can't read. */
    FROM_NEWER_APP,
}
