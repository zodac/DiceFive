package net.zodac.dicefive.online

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.data.InMemoryPreferences
import net.zodac.dicefive.data.achievements.AchievementsRepository
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.achievements.InMemoryAchievementStore
import net.zodac.dicefive.data.game.InProgressGameRepository
import net.zodac.dicefive.data.scores.CloudScores
import net.zodac.dicefive.data.scores.InMemoryScoreDao
import net.zodac.dicefive.data.scores.PlayerResult
import net.zodac.dicefive.data.scores.ScoreRepository
import net.zodac.dicefive.data.settings.SettingsRepository
import net.zodac.dicefive.game.LeaderboardTotals
import net.zodac.dicefive.game.TieBreakStats
import net.zodac.dicefive.model.AchievementCounter
import okio.Buffer
import okio.GzipSink
import okio.buffer
import okio.use

/** The service as `PlayGamesServices.writeSave` uses it: what it holds is merged with what's sent, unless it's empty or the same. */
private class SaveService(var bytes: ByteArray?, account: String = "sam") : GameServices by NoGameServices {
    val mutableState = MutableStateFlow<GameServicesState>(GameServicesState.SignedIn(OnlineAccount(account, null, account)))
    override val state: StateFlow<GameServicesState> = mutableState
    var commits = 0
    var lastMerge: ((ByteArray, ByteArray) -> ByteArray)? = null
    var takesWrites = true

    override suspend fun unlockedAchievementIds(): Set<String> = emptySet()

    override suspend fun readCloudSave(merge: (local: ByteArray, remote: ByteArray) -> ByteArray): CloudSaveRead {
        lastMerge = merge
        return bytes?.let { CloudSaveRead.Found(it) } ?: CloudSaveRead.Empty
    }

    override suspend fun writeCloudSave(data: ByteArray, merge: (local: ByteArray, remote: ByteArray) -> ByteArray): ByteArray? {
        if (!takesWrites) return null
        val current = bytes
        val final = if (current == null || current.contentEquals(data)) data else merge(data, current)
        if (current == null || !final.contentEquals(current)) {
            bytes = final
            commits++
        }
        return final
    }
}

/** A device's side of the save, counting what a sync reads and merges. */
private class SaveDevice(var save: CloudSave = CloudSave()) : CloudSaveStore {
    var snapshots = 0
    var merges = 0
    var wipes = 0

    /** Moves on with each change, as `LocalCloudSave`'s does. */
    var stamp = 0

    /** Runs once the snapshot has been read: a change made while the sync goes on. */
    var afterSnapshot: () -> Unit = {}

    override suspend fun snapshot(): CloudSave = save.also {
        snapshots++
        afterSnapshot()
    }
    override suspend fun changeStamp(): Any = stamp
    override suspend fun mergeSettings(save: CloudSave) = Unit
    override suspend fun mergeGame(save: CloudSave) = Unit
    override suspend fun mergeAchievements(save: CloudSave) = Unit
    override suspend fun mergeScores(save: CloudSave) {
        merges++
        this.save = CloudSaveMerge.merge(this.save, CloudSave(scoresResetAtEpochMillis = save.scoresResetAtEpochMillis, scores = save.scores)).copy(version = CloudSaveCodec.VERSION)
    }
    override suspend fun wipe() {
        wipes++
        save = CloudSave()
    }

    fun play(vararg labels: String) {
        save = CloudSaveMerge.merge(save, saveOf(*labels))
        stamp++
    }
}

private fun row(at: Long) = cloudRow("", at = at).copy(uuid = Uuid.random())

private fun saveOf(vararg labels: String) = CloudSave(scores = CloudScores(rows = labels.map { cloudRow(it, at = 10L + it.length) }))

/** Rows far less compressible than a game's, so a test reaches the service's limit quickly. */
private fun tooLargeForService(): CloudSave = CloudSave(
    scores = CloudScores(rows = (1..60_000).map { index -> row(index * 7_919L).copy(playerName = "${Uuid.random()}${Uuid.random()}") }),
)

/** A save, as bytes, that inflates past what may be read. */
private fun tooLargeSave(): ByteArray {
    val out = Buffer()
    GzipSink(out).buffer().use { it.writeUtf8("{\"version\":1,${" ".repeat((CloudSaveCodec.MAX_INFLATED_BYTES + 1).toInt())}\"scores\":{}}") }
    return out.readByteArray()
}

class CloudSaveSizeAndSkipTest {

    private val settings = OnlineSettingsRepository(InMemoryPreferences())

    private fun sync(service: SaveService, device: SaveDevice) =
        OnlineSync(service, InMemoryAchievementStore(AchievementsState()), settings, { LeaderboardTotals() }, { _, _ -> null }, device, now = { 1_000L })

    @Test
    fun `a new device never writes over a save too large for it to read - and says it couldn't sync`() = runTest {
        val tooLarge = tooLargeSave()
        val service = SaveService(tooLarge)
        val device = SaveDevice(saveOf("phone"))
        val sync = sync(service, device)
        sync.reconcile()
        assertTrue(service.bytes.contentEquals(tooLarge))
        assertEquals(0, service.commits)
        assertEquals(1, device.save.scores.rows.size)
        assertTrue(sync.unreachable.value)
        assertNull(settings.cloudSavedAt("sam").first())
    }

    @Test
    fun `a conflict with a save too large to read keeps that save`() = runTest {
        val tooLarge = tooLargeSave()
        val service = SaveService(CloudSaveCodec.encode(saveOf("tablet")))
        sync(service, SaveDevice(saveOf("phone"))).reconcile()
        val settled = service.lastMerge!!(CloudSaveCodec.encode(saveOf("phone")), tooLarge)
        assertTrue(settled.contentEquals(tooLarge))
    }

    @Test
    fun `a save growing too large between the read and the write isn't taken for saved`() = runTest {
        val tooLarge = tooLargeSave()
        val device = SaveDevice(saveOf("phone"))
        val service = object : GameServices by SaveService(CloudSaveCodec.encode(saveOf("tablet"))) {
            override suspend fun writeCloudSave(data: ByteArray, merge: (local: ByteArray, remote: ByteArray) -> ByteArray): ByteArray = merge(data, tooLarge)
        }
        val sync = OnlineSync(service, InMemoryAchievementStore(AchievementsState()), settings, { LeaderboardTotals() }, { _, _ -> null }, device, now = { 1_000L })
        sync.reconcile()
        assertTrue(sync.unreachable.value)
        assertNull(settings.cloudSavedAt("sam").first())
    }

    @Test
    fun `switching to an account whose save is too large to read wipes nothing here`() = runTest {
        settings.setSyncedAccount("ann")
        val device = SaveDevice(saveOf("phone"))
        val sync = sync(SaveService(tooLargeSave()), device)
        sync.reconcile()
        assertEquals(0, device.wipes)
        assertEquals(1, device.save.scores.rows.size)
        assertTrue(sync.unreachable.value)
    }

    @Test
    fun `a corrupt save is still replaced`() = runTest {
        val service = SaveService("not a save".encodeToByteArray())
        val sync = sync(service, SaveDevice(saveOf("phone")))
        sync.reconcile()
        assertEquals(1, service.commits)
        assertEquals(1, CloudSaveCodec.decode(service.bytes!!)?.scores?.rows?.size)
        assertFalse(sync.unreachable.value)
    }

    @Test
    fun `a save larger than the service takes stays on the device - and says it couldn't sync`() = runTest {
        val service = SaveService(null)
        val device = SaveDevice(tooLargeForService())
        assertTrue(CloudSaveCodec.encode(device.save).size > CloudSaveCodec.MAX_SAVE_BYTES)
        val sync = sync(service, device)
        sync.reconcile()
        assertEquals(0, service.commits)
        assertNull(service.bytes)
        assertEquals(60_000, device.save.scores.rows.size)
        assertTrue(sync.unreachable.value)
        assertNull(settings.cloudSavedAt("sam").first())
    }

    @Test
    fun `with nothing changed on either side a sync reads no rows - merges nothing - and writes nothing`() = runTest {
        val service = SaveService(null)
        val device = SaveDevice(saveOf("phone"))
        val sync = sync(service, device)
        sync.reconcile()
        assertEquals(1, service.commits)
        assertEquals(1, device.snapshots)

        sync.reconcile()
        sync.reconcile()
        assertEquals(1, service.commits)
        assertEquals(1, device.snapshots)
        assertEquals(0, device.merges)
        assertFalse(sync.unreachable.value)
        assertEquals(1_000L, settings.cloudSavedAt("sam").first())
    }

    @Test
    fun `a change on this device is written - merged with what the service holds - without reading the save first`() = runTest {
        val service = SaveService(null)
        val device = SaveDevice(saveOf("phone"))
        val sync = sync(service, device)
        sync.reconcile()

        device.play("game")
        sync.reconcile()
        assertEquals(0, device.merges)
        assertEquals(2, service.commits)
        assertEquals(setOf("phone", "game"), CloudSaveCodec.decode(service.bytes!!)?.scores?.rows?.map { it.uuidLabel }?.toSet())

        sync.reconcile()
        assertEquals(2, service.commits)
    }

    @Test
    fun `another device's write is read and merged in`() = runTest {
        val service = SaveService(null)
        val device = SaveDevice(saveOf("phone"))
        val sync = sync(service, device)
        sync.reconcile()

        service.bytes = CloudSaveCodec.encode(CloudSaveMerge.merge(CloudSaveCodec.decode(service.bytes!!)!!, saveOf("tablet")))
        sync.reconcile()
        assertEquals(1, device.merges)
        assertEquals(setOf("phone", "tablet"), device.save.scores.rows.map { it.uuidLabel }.toSet())
    }

    @Test
    fun `a change made while the save is written is written by the next sync`() = runTest {
        val service = SaveService(null)
        val device = SaveDevice(saveOf("phone"))
        device.afterSnapshot = {
            device.afterSnapshot = {}
            device.play("game")
        }
        val sync = sync(service, device)
        sync.reconcile()
        assertEquals(setOf("phone"), CloudSaveCodec.decode(service.bytes!!)?.scores?.rows?.map { it.uuidLabel }?.toSet())

        sync.reconcile()
        assertEquals(setOf("phone", "game"), CloudSaveCodec.decode(service.bytes!!)?.scores?.rows?.map { it.uuidLabel }?.toSet())
    }

    @Test
    fun `an account's save is read again after another account has been on this device`() = runTest {
        val annSave = CloudSaveCodec.encode(saveOf("ann's"))
        val service = SaveService(annSave)
        val device = SaveDevice()
        val sync = sync(service, device)
        sync.reconcile()
        val ann = service.bytes!!

        service.mutableState.value = GameServicesState.SignedIn(OnlineAccount("bob", null, "bob"))
        service.bytes = CloudSaveCodec.encode(saveOf("bob's"))
        sync.reconcile()
        assertEquals(setOf("bob's"), device.save.scores.rows.map { it.uuidLabel }.toSet())

        service.mutableState.value = GameServicesState.SignedIn(OnlineAccount("sam", null, "sam"))
        service.bytes = ann
        sync.reconcile()
        assertEquals(setOf("ann's"), device.save.scores.rows.map { it.uuidLabel }.toSet())
    }

    @Test
    fun `a wipe for another account means an account's save is read again - though it hasn't changed`() = runTest {
        val service = SaveService(CloudSaveCodec.encode(saveOf("sam's")))
        val device = SaveDevice()
        val sync = sync(service, device)
        sync.reconcile()
        val sams = service.bytes!!

        // Bob's sync wipes this device, then gets no further than failing to write.
        service.mutableState.value = GameServicesState.SignedIn(OnlineAccount("bob", null, "bob"))
        service.bytes = null
        service.takesWrites = false
        sync.reconcile()
        assertEquals(1, device.wipes)

        service.mutableState.value = GameServicesState.SignedIn(OnlineAccount("sam", null, "sam"))
        service.bytes = sams
        service.takesWrites = true
        sync.reconcile()
        assertEquals(setOf("sam's"), device.save.scores.rows.map { it.uuidLabel }.toSet())
    }

    @Test
    fun `the real store's stamp stands still until something the save holds changes - and reads no rows`() = runTest {
        val scores = ScoreRepository(InMemoryScoreDao())
        val online = OnlineSettingsRepository(InMemoryPreferences())
        val achievements = AchievementsRepository(InMemoryPreferences())
        val store = LocalCloudSave(
            achievements,
            scores,
            SettingsRepository(InMemoryPreferences()),
            InProgressGameRepository(InMemoryPreferences()),
            online,
        )
        val first = store.changeStamp()
        assertEquals(first, store.changeStamp())

        // Time played alone is nothing to write a save for: it goes with the next save that has something else to send.
        achievements.addPlaySeconds(60)
        assertEquals(first, store.changeStamp())
        assertEquals(60, store.snapshot().achievements.deviceCounters.values.single()[AchievementCounter.PLAY_SECONDS.id])

        scores.recordGame(listOf(PlayerResult("Sam", TieBreakStats(score = 200, fiveOfAKindCount = 0, zeroedCategoryCount = 0, tricolourScoredCount = null, upperSectionTotal = 0, chance = 0, threeOfAKind = 0, fourOfAKind = 0), won = null, isPrimaryPlayer = true)))
        val afterGame = store.changeStamp()
        assertNotEquals(first, afterGame)

        online.setScoresResetAt(5L)
        assertNotEquals(afterGame, store.changeStamp())
    }
}
