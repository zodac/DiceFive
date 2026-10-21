package net.zodac.dicefive.ui.settings

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.data.achievements.CloudAchievements
import net.zodac.dicefive.data.achievements.InMemoryAchievementStore
import net.zodac.dicefive.game.LeaderboardTotals
import net.zodac.dicefive.online.CloudSave
import net.zodac.dicefive.online.CloudSaveCodec
import net.zodac.dicefive.online.CloudSaveMerge
import net.zodac.dicefive.online.CloudSaveStore
import net.zodac.dicefive.online.ImportStep
import net.zodac.dicefive.online.ProgressFileCodec
import net.zodac.dicefive.online.ProgressTransfer
import net.zodac.dicefive.platform.ProgressFileResult
import net.zodac.dicefive.platform.ProgressFiles

/** This device's side of the save, in memory. */
private class MemoryCloudSave(var local: CloudSave = CloudSave()) : CloudSaveStore {
    override suspend fun snapshot() = local
    private fun mergeIn(save: CloudSave) {
        local = CloudSaveMerge.merge(local, save).copy(version = CloudSaveCodec.VERSION)
    }
    override suspend fun mergeSettings(save: CloudSave) = mergeIn(CloudSave(settings = save.settings))
    override suspend fun mergeGame(save: CloudSave) = mergeIn(CloudSave(inProgressGame = save.inProgressGame))
    override suspend fun mergeAchievements(save: CloudSave) = mergeIn(CloudSave(achievements = save.achievements))
    override suspend fun mergeScores(save: CloudSave) = mergeIn(CloudSave(scoresResetAtEpochMillis = save.scoresResetAtEpochMillis, scores = save.scores))
    override suspend fun wipe() = Unit
}

/** A picker that answers [saveResult] to a save, and [opened] to an open - each once [answer] is completed. */
private class FakeProgressFiles(
    var saveResult: ProgressFileResult = ProgressFileResult.Done(),
    var opened: ProgressFileResult = ProgressFileResult.Cancelled,
) : ProgressFiles {
    override val key = ByteArray(32) { 9 }
    val answer = CompletableDeferred(Unit)
    val saved = mutableListOf<Pair<String, ByteArray>>()
    var pickers = 0
    override suspend fun save(fileName: String, bytes: ByteArray): ProgressFileResult {
        pickers++
        answer.await()
        return saveResult.also { if (it is ProgressFileResult.Done) saved += fileName to bytes }
    }
    override suspend fun open(): ProgressFileResult {
        pickers++
        answer.await()
        return opened
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressFilesViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** 2026-10-21, at noon UTC. */
    private val today = 1_792_584_000_000L

    private val save = MemoryCloudSave(CloudSave(achievements = CloudAchievements(unlockedAt = mapOf("FIVE_X" to 5L))))

    private fun TestScope.viewModel() = settingsViewModel(
        now = { today },
        progressTransfer = ProgressTransfer(save, InMemoryAchievementStore(), { LeaderboardTotals() }, now = { today }, background = StandardTestDispatcher(testScheduler)),
    )

    /** How [action] ended, as the card shows it once it has finished: nothing, or the one outcome. */
    private fun TestScope.messages(viewModel: SettingsViewModel, action: () -> Unit): List<ProgressOutcome> {
        action()
        advanceUntilIdle()
        return listOfNotNull((viewModel.progressFilesStatus.value as? ProgressFilesStatus.Finished)?.outcome)
    }

    @Test
    fun `an export saves the sealed progress under today's name - then says so`() = runTest {
        val files = FakeProgressFiles()
        val viewModel = viewModel()
        assertEquals(listOf(ProgressOutcome.EXPORTED), messages(viewModel) { viewModel.exportProgress(files) })
        val (name, bytes) = files.saved.single()
        // The player's own day: noon UTC is the 21st from UTC-12 to UTC+11, and the 22nd from UTC+12.
        assertTrue(name in setOf("DiceFive-2026-10-21.dicefive", "DiceFive-2026-10-22.dicefive"), name)
        assertEquals(save.local, ProgressFileCodec.decode(bytes, files.key))
    }

    @Test
    fun `an export that couldn't be written says so - and one cancelled says nothing`() = runTest {
        val viewModel = viewModel()
        assertEquals(
            listOf(ProgressOutcome.EXPORT_FAILED),
            messages(viewModel) { viewModel.exportProgress(FakeProgressFiles(saveResult = ProgressFileResult.Failed)) },
        )
        assertEquals(emptyList(), messages(viewModel) { viewModel.exportProgress(FakeProgressFiles(saveResult = ProgressFileResult.Cancelled)) })
    }

    @Test
    fun `an import merges the file in - and says how it went`() = runTest {
        val files = FakeProgressFiles()
        val other = CloudSave(achievements = CloudAchievements(unlockedAt = mapOf("CENTURION" to 7L)))
        files.opened = ProgressFileResult.Done(ProgressFileCodec.encode(other, files.key))
        val viewModel = viewModel()
        assertEquals(listOf(ProgressOutcome.IMPORTED), messages(viewModel) { viewModel.importProgress(files) })
        assertEquals(mapOf("FIVE_X" to 5L, "CENTURION" to 7L), save.local.achievements.unlockedAt)

        files.opened = ProgressFileResult.Done("not a progress file".encodeToByteArray())
        assertEquals(listOf(ProgressOutcome.NOT_A_PROGRESS_FILE), messages(viewModel) { viewModel.importProgress(files) })
        files.opened = ProgressFileResult.Done(ProgressFileCodec.encode(other.copy(version = CloudSaveCodec.VERSION + 1), files.key))
        assertEquals(listOf(ProgressOutcome.FROM_NEWER_APP), messages(viewModel) { viewModel.importProgress(files) })
        files.opened = ProgressFileResult.Failed
        assertEquals(listOf(ProgressOutcome.IMPORT_FAILED), messages(viewModel) { viewModel.importProgress(files) })
        files.opened = ProgressFileResult.Cancelled
        assertEquals(emptyList(), messages(viewModel) { viewModel.importProgress(files) })
    }

    @Test
    fun `an import shows each step as it starts - then how it ended - until the next starts`() = runTest {
        val files = FakeProgressFiles()
        files.opened = ProgressFileResult.Done(ProgressFileCodec.encode(CloudSave(), files.key))
        val viewModel = viewModel()
        val shown = mutableListOf<ProgressFilesStatus?>()
        val watching = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.progressFilesStatus.toList(shown) }
        viewModel.importProgress(files)
        advanceUntilIdle()
        assertEquals(
            listOf(null) + ImportStep.entries.map(ProgressFilesStatus::Importing) + ProgressFilesStatus.Finished(ProgressOutcome.IMPORTED),
            shown,
        )
        // A picker closed unchosen clears the last outcome rather than leaving it under a new attempt.
        files.opened = ProgressFileResult.Cancelled
        viewModel.importProgress(files)
        advanceUntilIdle()
        assertEquals(null, viewModel.progressFilesStatus.value)
        watching.cancel()
    }

    @Test
    fun `a second tap while a picker is open starts nothing`() = runTest {
        val files = FakeProgressFiles()
        val waiting = FakeProgressFiles()
        val viewModel = viewModel()
        val gate = CompletableDeferred<Unit>()
        val slow = object : ProgressFiles by waiting {
            override suspend fun save(fileName: String, bytes: ByteArray): ProgressFileResult {
                gate.await()
                return waiting.save(fileName, bytes)
            }
        }
        val messages = messages(viewModel) {
            viewModel.exportProgress(slow)
            viewModel.importProgress(files)
            viewModel.exportProgress(files)
        }
        assertTrue(messages.isEmpty())
        assertEquals(0, files.pickers)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, waiting.saved.size)
    }
}
