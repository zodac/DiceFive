package net.zodac.dicefive.online

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.Stamped
import net.zodac.dicefive.data.achievements.AchievementsRepository
import net.zodac.dicefive.data.achievements.CloudAchievements
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.CloudScores
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingValue
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.LeaderboardTotals
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter

/** One device's stores, as the app has them, over memory: what an export reads and an import merges into. */
private class Device(scope: CoroutineScope, now: Long) {
    val achievements = AchievementsRepository(InMemoryPreferences(), now = { now })
    val scores = ScoreRepository(InMemoryScoreDao())
    private val online = OnlineSettingsRepository(InMemoryPreferences())
    val store = LocalCloudSave(
        achievements,
        scores,
        SettingsRepository(InMemoryPreferences(), now = { now }),
        InProgressGameRepository(InMemoryPreferences(), scope, now = { now }),
        online,
    )
    val transfer = ProgressTransfer(store, achievements, { LeaderboardTotals() }, now = { now })
}

class ProgressTransferTest {

    private val key = ByteArray(32) { 3 }
    private val now = 1_800_000_000_000L

    private fun TestScope.device() = Device(backgroundScope, now)

    private val phone = CloudSave(
        achievements = CloudAchievements(unlockedAt = mapOf(Achievement.FIVE_X.id to 1_790_000_000_000L)),
        scores = CloudScores(rows = listOf(cloudRow("aa", at = 1_790_000_000_000L, score = 250))),
        settings = mapOf("dice_style_id" to Stamped(SettingValue.Text("ocean"), 1_790_000_000_000L)),
    )

    @Test
    fun `an export imported on another device merges in as its cloud save would`() = runTest {
        val from = device().apply { store.merge(phone) }
        val to = device().apply {
            store.merge(CloudSave(scores = CloudScores(rows = listOf(cloudRow("bb", at = 1_790_000_000_500L)))))
        }
        val expected = CloudSaveMerge.merge(to.store.snapshot(), from.store.snapshot())

        assertEquals(ProgressImport.IMPORTED, to.transfer.import(from.transfer.export(key), key))
        val merged = to.store.snapshot()
        assertEquals(expected.achievements.unlockedAt, merged.achievements.unlockedAt)
        assertEquals(expected.scores.rows.map { it.uuid }.toSet(), merged.scores.rows.map { it.uuid }.toSet())
        assertEquals(Stamped(SettingValue.Text("ocean"), 1_790_000_000_000L), merged.settings["dice_style_id"])
    }

    @Test
    fun `an import takes its steps in the cloud save's order - and a file it can't open stops at the first`() = runTest {
        val bytes = device().apply { store.merge(phone) }.transfer.export(key)
        val steps = mutableListOf<ImportStep>()
        device().transfer.import(bytes, key) { steps += it }
        assertEquals(ImportStep.entries.toList(), steps)
        steps.clear()
        device().transfer.import(ByteArray(64), key) { steps += it }
        assertEquals(listOf(ImportStep.OPENING), steps)
    }

    @Test
    fun `importing one file twice changes nothing the second time`() = runTest {
        val bytes = device().apply { store.merge(phone) }.transfer.export(key)
        val to = device()
        to.transfer.import(bytes, key)
        val once = to.store.snapshot()
        assertEquals(ProgressImport.IMPORTED, to.transfer.import(bytes, key))
        assertEquals(once, to.store.snapshot())
    }

    @Test
    fun `an achievement the merged totals have reached is unlocked quietly`() = runTest {
        val to = device()
        to.achievements.record(emptyMap(), mapOf(AchievementCounter.GAMES_PLAYED to Achievement.CENTURION.target))
        to.transfer.import(device().apply { store.merge(phone) }.transfer.export(key), key)
        assertEquals(now, to.achievements.current().unlockedAt[Achievement.CENTURION])
    }

    @Test
    fun `a file that isn't one - or was changed - merges in nothing`() = runTest {
        val bytes = device().apply { store.merge(phone) }.transfer.export(key)
        val to = device()
        val before = to.store.snapshot()
        assertEquals(ProgressImport.NOT_A_PROGRESS_FILE, to.transfer.import(bytes.copyOf().also { it[30] = (it[30] + 1).toByte() }, key))
        assertEquals(ProgressImport.NOT_A_PROGRESS_FILE, to.transfer.import(bytes, ByteArray(32)))
        assertEquals(ProgressImport.NOT_A_PROGRESS_FILE, to.transfer.import(CloudSaveCodec.encode(phone), key))
        assertEquals(before, to.store.snapshot())
    }

    @Test
    fun `a newer app's file is refused whole`() = runTest {
        val newer = ProgressFileCodec.encode(phone.copy(version = CloudSaveCodec.VERSION + 1), key)
        val to = device()
        assertEquals(ProgressImport.FROM_NEWER_APP, to.transfer.import(newer, key))
        assertNull(to.achievements.current().unlockedAt[Achievement.FIVE_X])
    }
}
