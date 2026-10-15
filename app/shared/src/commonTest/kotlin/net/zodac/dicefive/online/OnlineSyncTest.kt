package net.zodac.dicefive.online

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.data.achievements.AchievementStore
import net.zodac.dicefive.data.achievements.AchievementsState
import net.zodac.dicefive.data.achievements.CloudAchievements
import net.zodac.dicefive.data.scores.CloudScores
import net.zodac.dicefive.data.scores.UnpostedScore
import net.zodac.dicefive.game.LeaderboardTotals
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter
import net.zodac.dicefive.model.GameMode

private class SyncPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

private class SyncStore(initial: AchievementsState) : AchievementStore {
    val mutableState = MutableStateFlow(initial)
    override val state: Flow<AchievementsState> = mutableState
    override suspend fun current(): AchievementsState = mutableState.value
    override suspend fun record(unlockedAt: Map<Achievement, Long>, counters: Map<AchievementCounter, Int>) {
        val before = mutableState.value
        mutableState.value = AchievementsState(before.unlockedAt + unlockedAt.filterKeys { it !in before.unlockedAt }, before.counters + counters)
    }
    override suspend fun resetAll() {
        mutableState.value = AchievementsState()
    }
    override suspend fun forceLock(achievement: Achievement) = Unit
}

/** Records every call; [remote] is what it says is unlocked, null if it can't be asked. */
private class SyncServices(account: String?, var remote: Set<String>? = emptySet()) : GameServices by NoGameServices {
    val mutableState = MutableStateFlow<GameServicesState>(
        if (account == null) GameServicesState.SignedOut() else GameServicesState.SignedIn(OnlineAccount(account, null, account)),
    )
    override val state: StateFlow<GameServicesState> = mutableState
    val unlocked = mutableListOf<String>()
    val progress = mutableMapOf<String, Int>()
    var asked = 0
    override fun mirrors(achievementId: String) = true
    override suspend fun unlockedAchievementIds(): Set<String>? = remote.also { asked++ }
    override suspend fun unlock(achievementId: String) {
        unlocked += achievementId
    }
    override suspend fun setProgress(achievementId: String, steps: Int) {
        progress[achievementId] = steps
    }
    /** Every score the service took, by leaderboard key; while [takesScores] is false it takes none. */
    val posted = mutableListOf<Pair<String, Long>>()
    var takesScores = true
    override suspend fun submitScore(leaderboardKey: String, score: Long): Boolean =
        takesScores.also {
            seen("submitScore")
            if (it) posted += leaderboardKey to score
        }

    /** The sync's progress, read at each call made of the service: what the Settings card would show then. */
    var syncProgress: (() -> SyncProgress?)? = null
    val progressSeen = mutableListOf<Pair<String, SyncProgress?>>()
    private fun seen(call: String) {
        syncProgress?.let { progressSeen += call to it() }
    }

    /** The account's cloud save as the service would answer a read of it. */
    var cloud: CloudSaveRead = CloudSaveRead.Empty
    var cloudReads = 0
    val cloudWrites = mutableListOf<ByteArray>()
    /** Another device's save, committed after this one's read and before its write. */
    var writtenMeanwhile: ByteArray? = null
    var merge: ((ByteArray, ByteArray) -> ByteArray)? = null
    override suspend fun readCloudSave(merge: (local: ByteArray, remote: ByteArray) -> ByteArray): CloudSaveRead {
        cloudReads++
        seen("readCloudSave")
        this.merge = merge
        return cloud
    }
    override suspend fun writeCloudSave(data: ByteArray, merge: (local: ByteArray, remote: ByteArray) -> ByteArray): ByteArray? {
        seen("writeCloudSave")
        if (cloud == CloudSaveRead.Failed) return null
        val written = writtenMeanwhile?.let { merge(data, it) } ?: data
        cloudWrites += written
        cloud = CloudSaveRead.Found(written)
        return written
    }
    /** The save last written, decoded. */
    val saved: CloudSave? get() = cloudWrites.lastOrNull()?.let(CloudSaveCodec::decode)
}

/** This device's side of the cloud save, in memory: merged in by the same rules the real stores follow. */
private class SyncCloudSave(var local: CloudSave = CloudSave()) : CloudSaveStore {
    var wipes = 0
    override suspend fun snapshot(): CloudSave = local
    /** Each part merged in alone, in the order it was asked for. */
    val merged = mutableListOf<String>()
    private fun mergeIn(part: String, save: CloudSave) {
        merged += part
        local = CloudSaveMerge.merge(local, save).copy(version = CloudSaveCodec.VERSION)
    }
    override suspend fun mergeSettings(save: CloudSave) = mergeIn("settings", CloudSave(settings = save.settings))
    override suspend fun mergeGame(save: CloudSave) = mergeIn("game", CloudSave(inProgressGame = save.inProgressGame))
    override suspend fun mergeAchievements(save: CloudSave) = mergeIn("achievements", CloudSave(achievements = save.achievements))
    override suspend fun mergeScores(save: CloudSave) =
        mergeIn("scores", CloudSave(scoresResetAtEpochMillis = save.scoresResetAtEpochMillis, scores = save.scores))
    override suspend fun wipe() {
        wipes++
        local = CloudSave(achievements = local.achievements)
    }
}

private fun found(save: CloudSave) = CloudSaveRead.Found(CloudSaveCodec.encode(save))

/** Player 1's postable games, as (mode, score, played at); the filtering by rules and ceiling is `ScoreDao`'s, tested against Room. */
private class SyncScores(vararg games: Triple<GameMode, Int, Long>) {
    val games = games.toMutableList()
    fun best(mode: GameMode, after: Long): UnpostedScore? =
        games.filter { it.first == mode && it.third > after }.takeIf { it.isNotEmpty() }?.let { played ->
            UnpostedScore(played.maxOf { it.second }, played.maxOf { it.third })
        }
}

class OnlineSyncTest {

    private val now = 1_000L

    private fun sync(
        services: SyncServices,
        store: SyncStore,
        settings: OnlineSettingsRepository,
        totals: LeaderboardTotals = LeaderboardTotals(),
        scores: SyncScores = SyncScores(),
        cloudSave: CloudSaveStore? = null,
    ) = OnlineSync(services, store, settings, { totals }, scores::best, cloudSave, now = { now })

    private fun state(vararg unlocked: Achievement, counters: Map<AchievementCounter, Int> = emptyMap()) =
        AchievementsState(unlocked.associateWith { 5L }, counters)

    @Test
    fun `signed out - nothing is asked or sent - and the app keeps its own banners`() = runTest {
        val services = SyncServices(account = null)
        val sync = sync(services, SyncStore(state(Achievement.FIVE_X)), OnlineSettingsRepository(SyncPreferences()))
        assertEquals(0, sync.reconcile())
        assertEquals(0, services.asked)
        assertTrue(services.unlocked.isEmpty())
        assertFalse(sync.announcesUnlock(Achievement.FIVE_X))
    }

    @Test
    fun `unlocks the service lacks are sent - and only those`() = runTest {
        val services = SyncServices("ann", remote = setOf(Achievement.FIVE_X.id))
        val sync = sync(services, SyncStore(state(Achievement.FIVE_X, Achievement.BONUS_ROUND)), OnlineSettingsRepository(SyncPreferences()))
        sync.reconcile()
        assertEquals(listOf(Achievement.BONUS_ROUND.id), services.unlocked)
        // From now on the service shows its own pop-up for a first unlock there.
        assertTrue(sync.announcesUnlock(Achievement.CENTURION))
        assertFalse(sync.announcesUnlock(Achievement.BONUS_ROUND))
    }

    @Test
    fun `once signed out - the app announces its own unlocks again - before any reconcile`() = runTest {
        val services = SyncServices("ann")
        val sync = sync(services, SyncStore(state()), OnlineSettingsRepository(SyncPreferences()))
        sync.reconcile()
        assertTrue(sync.announcesUnlock(Achievement.NAT_20))
        services.mutableState.value = GameServicesState.SignedOut()
        assertFalse(sync.announcesUnlock(Achievement.NAT_20))
    }

    @Test
    fun `unlocks only the service has are restored here - as of now - with no counters touched`() = runTest {
        val services = SyncServices("ann", remote = setOf(Achievement.FIVE_X.id, Achievement.CENTURION.id))
        val store = SyncStore(state(Achievement.FIVE_X, counters = mapOf(AchievementCounter.GAMES_PLAYED to 7)))
        assertEquals(1, sync(services, store, OnlineSettingsRepository(SyncPreferences())).reconcile())
        assertEquals(mapOf(Achievement.FIVE_X to 5L, Achievement.CENTURION to now), store.mutableState.value.unlockedAt)
        assertEquals(mapOf(AchievementCounter.GAMES_PLAYED to 7), store.mutableState.value.counters)
        assertTrue(services.unlocked.isEmpty())
    }

    @Test
    fun `climbing totals send their progress - streaks and untouched ones don't`() = runTest {
        val services = SyncServices("ann")
        val store = SyncStore(state(counters = mapOf(AchievementCounter.GAMES_PLAYED to 7, AchievementCounter.WIN_STREAK to 2)))
        sync(services, store, OnlineSettingsRepository(SyncPreferences()), LeaderboardTotals(totalPoints = 1_234)).reconcile()
        assertEquals(7, services.progress[Achievement.GETTING_COMFORTABLE.id])
        assertEquals(7, services.progress[Achievement.CENTURION.id])
        assertEquals(1_234, services.progress[Achievement.PROFESSIONAL_ROLLER.id])
        assertFalse(Achievement.UNTOUCHABLE.id in services.progress)
        assertFalse(Achievement.WELL_ROLLED.id in services.progress)
    }

    @Test
    fun `the first account merges with the device - and so does the same one again`() = runTest {
        val settings = OnlineSettingsRepository(SyncPreferences())
        val store = SyncStore(state(Achievement.FIVE_X))
        val ann = SyncServices("ann", remote = setOf(Achievement.CENTURION.id))
        sync(ann, store, settings).reconcile()
        assertEquals("ann", settings.syncedAccount.first())
        assertEquals(listOf(Achievement.FIVE_X.id), ann.unlocked)
        assertEquals(setOf(Achievement.FIVE_X, Achievement.CENTURION), store.mutableState.value.unlockedAt.keys)

        // Earned signed out, then the same account again: sent up, nothing wiped.
        store.record(mapOf(Achievement.BONUS_ROUND to 9L), emptyMap())
        val again = SyncServices("ann", remote = setOf(Achievement.FIVE_X.id, Achievement.CENTURION.id))
        sync(again, store, settings).reconcile()
        assertEquals(listOf(Achievement.BONUS_ROUND.id), again.unlocked)
        assertEquals(setOf(Achievement.FIVE_X, Achievement.CENTURION, Achievement.BONUS_ROUND), store.mutableState.value.unlockedAt.keys)
    }

    @Test
    fun `a different account replaces the device's achievements and progress with its own - sending it nothing of the last one's`() = runTest {
        val settings = OnlineSettingsRepository(SyncPreferences())
        val store = SyncStore(state(Achievement.FIVE_X, counters = mapOf(AchievementCounter.GAMES_PLAYED to 7)))
        sync(SyncServices("ann", remote = setOf(Achievement.FIVE_X.id)), store, settings).reconcile()

        val bob = SyncServices("bob", remote = setOf(Achievement.CENTURION.id))
        assertEquals(1, sync(bob, store, settings).reconcile())
        assertEquals(setOf(Achievement.CENTURION), store.mutableState.value.unlockedAt.keys)
        assertEquals(emptyMap(), store.mutableState.value.counters)
        assertTrue(bob.unlocked.isEmpty() && bob.progress.isEmpty())
        assertEquals("bob", settings.syncedAccount.first())
    }

    @Test
    fun `a different account that can't be asked wipes nothing`() = runTest {
        val settings = OnlineSettingsRepository(SyncPreferences())
        val store = SyncStore(state(Achievement.FIVE_X))
        sync(SyncServices("ann", remote = setOf(Achievement.FIVE_X.id)), store, settings).reconcile()
        sync(SyncServices("bob", remote = null), store, settings).reconcile()
        assertTrue(store.mutableState.value.isUnlocked(Achievement.FIVE_X))
        assertEquals("ann", settings.syncedAccount.first())
    }

    @Test
    fun `an unlock the service already has gets the app's own banner - the service shows none for it`() = runTest {
        val services = SyncServices("ann", remote = setOf(Achievement.NAT_20.id))
        val sync = sync(services, SyncStore(state()), OnlineSettingsRepository(SyncPreferences()))
        sync.reconcile()
        assertFalse(sync.announcesUnlock(Achievement.NAT_20))
        assertTrue(sync.announcesUnlock(Achievement.CRITICAL_SUCCESS))
    }

    @Test
    fun `once superuser mode has been used here - nothing is ever mirrored`() = runTest {
        val settings = OnlineSettingsRepository(SyncPreferences())
        settings.blockMirroring()
        val services = SyncServices("ann", remote = setOf(Achievement.CENTURION.id))
        val sync = sync(services, SyncStore(state(Achievement.FIVE_X)), settings)
        assertEquals(0, sync.reconcile())
        assertEquals(0, services.asked)
        assertTrue(services.unlocked.isEmpty() && services.progress.isEmpty())
        assertFalse(sync.announcesUnlock(Achievement.FIVE_X))
    }

    @Test
    fun `a service that can't be asked sends and restores nothing - and is tried again next time`() = runTest {
        val services = SyncServices("ann", remote = null)
        val store = SyncStore(state(Achievement.FIVE_X))
        val sync = sync(services, store, OnlineSettingsRepository(SyncPreferences()))
        assertEquals(0, sync.reconcile())
        assertTrue(services.unlocked.isEmpty())
        services.remote = emptySet()
        sync.reconcile()
        assertEquals(listOf(Achievement.FIVE_X.id), services.unlocked)
    }

    @Test
    fun `an account's first sync gets this device's best score in each mode played - and only those modes`() = runTest {
        val services = SyncServices("ann")
        val scores = SyncScores(
            Triple(GameMode.STANDARD, 180, 10L),
            Triple(GameMode.STANDARD, 240, 20L),
            Triple(GameMode.STANDARD, 200, 30L),
            Triple(GameMode.QUICKFIRE, 90, 15L),
        )
        sync(services, SyncStore(state()), OnlineSettingsRepository(SyncPreferences()), scores = scores).reconcile()
        assertEquals(listOf("standard" to 240L, "quickfire" to 90L), services.posted)
    }

    @Test
    fun `after that - only newer games are sent - each one's score even below the best`() = runTest {
        val services = SyncServices("ann")
        val scores = SyncScores(Triple(GameMode.STANDARD, 240, 20L))
        val sync = sync(services, SyncStore(state()), OnlineSettingsRepository(SyncPreferences()), scores = scores)
        sync.reconcile()
        sync.reconcile()
        assertEquals(listOf("standard" to 240L), services.posted)

        // The service keeps each player's best itself; a lower score still counts on today's and this week's boards.
        scores.games += Triple(GameMode.STANDARD, 150, 40L)
        sync.reconcile()
        assertEquals(listOf("standard" to 240L, "standard" to 150L), services.posted)
    }

    @Test
    fun `games played signed out are sent on signing in - the best of them`() = runTest {
        val settings = OnlineSettingsRepository(SyncPreferences())
        val scores = SyncScores(Triple(GameMode.D20, 300, 20L))
        val store = SyncStore(state())
        sync(SyncServices("ann"), store, settings, scores = scores).reconcile()

        scores.games += listOf(Triple(GameMode.D20, 310, 40L), Triple(GameMode.D20, 350, 50L), Triple(GameMode.D20, 320, 60L))
        val again = SyncServices("ann")
        sync(again, store, settings, scores = scores).reconcile()
        assertEquals(listOf("d20" to 350L), again.posted)
    }

    @Test
    fun `a score the service didn't take is sent again next time`() = runTest {
        val services = SyncServices("ann").apply { takesScores = false }
        val scores = SyncScores(Triple(GameMode.HIT_LIST, 400, 20L))
        val sync = sync(services, SyncStore(state()), OnlineSettingsRepository(SyncPreferences()), scores = scores)
        sync.reconcile()
        services.takesScores = true
        sync.reconcile()
        assertEquals(listOf("hit_list" to 400L), services.posted)
    }

    @Test
    fun `each account gets this device's best once - another signing in gets them too`() = runTest {
        val settings = OnlineSettingsRepository(SyncPreferences())
        val scores = SyncScores(Triple(GameMode.TRICOLOUR, 500, 20L))
        val store = SyncStore(state())
        sync(SyncServices("ann"), store, settings, scores = scores).reconcile()
        val bob = SyncServices("bob")
        sync(bob, store, settings, scores = scores).reconcile()
        assertEquals(listOf("tricolour" to 500L), bob.posted)

        // Ann again: nothing new for her.
        val annAgain = SyncServices("ann")
        sync(annAgain, store, settings, scores = scores).reconcile()
        assertTrue(annAgain.posted.isEmpty())
    }

    @Test
    fun `no score is sent signed out - or once superuser mode has been used here`() = runTest {
        val scores = SyncScores(Triple(GameMode.STANDARD, 240, 20L))
        val signedOut = SyncServices(account = null)
        sync(signedOut, SyncStore(state()), OnlineSettingsRepository(SyncPreferences()), scores = scores).reconcile()
        assertTrue(signedOut.posted.isEmpty())

        val settings = OnlineSettingsRepository(SyncPreferences()).apply { blockMirroring() }
        val blocked = SyncServices("ann")
        sync(blocked, SyncStore(state()), settings, scores = scores).reconcile()
        assertTrue(blocked.posted.isEmpty())
    }

    private fun scores(vararg uuids: String) = CloudScores(rows = uuids.mapIndexed { index, uuid -> cloudRow(uuid, at = 10L + index) })

    @Test
    fun `the cloud save is read - merged in here - and the union written back - then Last saved is now`() = runTest {
        val settings = OnlineSettingsRepository(SyncPreferences())
        val services = SyncServices("ann").apply { cloud = found(CloudSave(scores = scores("tablet"))) }
        val local = SyncCloudSave(CloudSave(scores = scores("phone")))
        sync(services, SyncStore(state()), settings, cloudSave = local).reconcile()
        assertEquals(setOf("phone", "tablet"), local.local.scores.rows.map { it.uuid }.toSet())
        assertEquals(setOf("phone", "tablet"), services.saved?.scores?.rows?.map { it.uuid }?.toSet())
        assertEquals(now, settings.cloudSavedAt("ann").first())
    }

    @Test
    fun `a save that already holds everything here isn't written again - but counts as saved`() = runTest {
        val settings = OnlineSettingsRepository(SyncPreferences())
        val services = SyncServices("ann").apply { cloud = found(CloudSave(scores = scores("phone"))) }
        sync(services, SyncStore(state()), settings, cloudSave = SyncCloudSave(CloudSave(scores = scores("phone")))).reconcile()
        assertTrue(services.cloudWrites.isEmpty())
        assertEquals(now, settings.cloudSavedAt("ann").first())
    }

    @Test
    fun `an account with no save yet is given this device's`() = runTest {
        val services = SyncServices("ann").apply { cloud = CloudSaveRead.Empty }
        sync(services, SyncStore(state()), OnlineSettingsRepository(SyncPreferences()), cloudSave = SyncCloudSave(CloudSave(scores = scores("phone")))).reconcile()
        assertEquals(listOf("phone"), services.saved?.scores?.rows?.map { it.uuid })
    }

    @Test
    fun `a save that can't be read isn't written - nor marked saved - but achievements and scores still go up`() = runTest {
        val settings = OnlineSettingsRepository(SyncPreferences())
        val services = SyncServices("ann").apply { cloud = CloudSaveRead.Failed }
        sync(services, SyncStore(state(Achievement.FIVE_X)), settings, cloudSave = SyncCloudSave(CloudSave(scores = scores("phone")))).reconcile()
        assertTrue(services.cloudWrites.isEmpty())
        assertEquals(null, settings.cloudSavedAt("ann").first())
        assertEquals(listOf(Achievement.FIVE_X.id), services.unlocked)
    }

    @Test
    fun `another device's save written meanwhile is merged in here too`() = runTest {
        val services = SyncServices("ann").apply {
            cloud = CloudSaveRead.Empty
            writtenMeanwhile = CloudSaveCodec.encode(CloudSave(scores = scores("tablet")))
        }
        val local = SyncCloudSave(CloudSave(scores = scores("phone")))
        sync(services, SyncStore(state()), OnlineSettingsRepository(SyncPreferences()), cloudSave = local).reconcile()
        assertEquals(setOf("phone", "tablet"), local.local.scores.rows.map { it.uuid }.toSet())
    }

    @Test
    fun `a different account wipes this device's save data and restores its own`() = runTest {
        val settings = OnlineSettingsRepository(SyncPreferences())
        val local = SyncCloudSave(CloudSave(scores = scores("ann's game")))
        sync(SyncServices("ann"), SyncStore(state()), settings, cloudSave = local).reconcile()

        val bob = SyncServices("bob").apply { cloud = found(CloudSave(scores = scores("bob's game"))) }
        sync(bob, SyncStore(state()), settings, cloudSave = local).reconcile()
        assertEquals(1, local.wipes)
        assertEquals(listOf("bob's game"), local.local.scores.rows.map { it.uuid })
        // Bob's save gets nothing of Ann's.
        assertTrue(bob.cloudWrites.isEmpty())
    }

    @Test
    fun `a different account whose save can't be read wipes nothing - and isn't recorded as synced`() = runTest {
        val settings = OnlineSettingsRepository(SyncPreferences())
        val store = SyncStore(state(Achievement.FIVE_X))
        val local = SyncCloudSave(CloudSave(scores = scores("ann's game")))
        sync(SyncServices("ann", remote = setOf(Achievement.FIVE_X.id)), store, settings, cloudSave = local).reconcile()

        val bob = SyncServices("bob").apply { cloud = CloudSaveRead.Failed }
        assertEquals(0, sync(bob, store, settings, cloudSave = local).reconcile())
        assertEquals(0, local.wipes)
        assertTrue(store.mutableState.value.isUnlocked(Achievement.FIVE_X))
        assertEquals("ann", settings.syncedAccount.first())
        assertTrue(bob.posted.isEmpty() && bob.unlocked.isEmpty())
    }

    @Test
    fun `a newer app's save is merged in here - and never written over`() = runTest {
        val settings = OnlineSettingsRepository(SyncPreferences())
        val newer = CloudSave(version = CloudSaveCodec.VERSION + 1, scores = scores("tablet"))
        val services = SyncServices("ann").apply { cloud = found(newer) }
        val local = SyncCloudSave(CloudSave(scores = scores("phone")))
        sync(services, SyncStore(state()), settings, cloudSave = local).reconcile()
        assertEquals(setOf("phone", "tablet"), local.local.scores.rows.map { it.uuid }.toSet())
        assertTrue(services.cloudWrites.isEmpty())
        assertEquals(null, settings.cloudSavedAt("ann").first())
        // Settling a conflict keeps the newer app's copy whole, too.
        val newerBytes = CloudSaveCodec.encode(newer)
        assertTrue(services.merge!!(CloudSaveCodec.encode(CloudSave(scores = scores("phone"))), newerBytes).contentEquals(newerBytes))
    }

    @Test
    fun `two copies in conflict are settled as their union - and an unreadable one gives way`() = runTest {
        val services = SyncServices("ann")
        sync(services, SyncStore(state()), OnlineSettingsRepository(SyncPreferences()), cloudSave = SyncCloudSave()).reconcile()
        val merge = services.merge!!
        val phone = CloudSaveCodec.encode(CloudSave(scores = scores("phone")))
        val tablet = CloudSaveCodec.encode(CloudSave(scores = scores("tablet")))
        assertEquals(setOf("phone", "tablet"), CloudSaveCodec.decode(merge(phone, tablet))?.scores?.rows?.map { it.uuid }?.toSet())
        assertTrue(merge(phone, "garbage".encodeToByteArray()).contentEquals(phone))
        assertTrue(merge("garbage".encodeToByteArray(), tablet).contentEquals(tablet))
    }

    @Test
    fun `once superuser mode has been used here - the cloud save is never read or written`() = runTest {
        val settings = OnlineSettingsRepository(SyncPreferences()).apply { blockMirroring() }
        val services = SyncServices("ann").apply { cloud = found(CloudSave(achievements = CloudAchievements(unlockedAt = mapOf("FIVE_X" to 1L)))) }
        sync(services, SyncStore(state()), settings, cloudSave = SyncCloudSave()).reconcile()
        assertEquals(0, services.cloudReads)
        assertTrue(services.cloudWrites.isEmpty())
    }

    @Test
    fun `an achievement the merged totals have reached is unlocked quietly - and sent to the service`() = runTest {
        val services = SyncServices("ann").apply { cloud = found(CloudSave(scores = scores("tablet"))) }
        val store = SyncStore(state(counters = mapOf(AchievementCounter.GAMES_PLAYED to Achievement.CENTURION.target)))
        sync(services, store, OnlineSettingsRepository(SyncPreferences()), cloudSave = SyncCloudSave()).reconcile()
        assertEquals(now, store.mutableState.value.unlockedAt[Achievement.CENTURION])
        assertTrue(Achievement.CENTURION.id in services.unlocked)
    }

    @Test
    fun `the service unreachable is reported - and cleared once a sync gets through or on signing out`() = runTest {
        val settings = OnlineSettingsRepository(SyncPreferences())
        val services = SyncServices("ann").apply { cloud = CloudSaveRead.Failed }
        val sync = sync(services, SyncStore(state()), settings, cloudSave = SyncCloudSave())
        sync.reconcile()
        assertTrue(sync.unreachable.value)
        services.cloud = CloudSaveRead.Empty
        sync.reconcile()
        assertFalse(sync.unreachable.value)
        services.remote = null
        sync.reconcile()
        assertTrue(sync.unreachable.value)
        services.mutableState.value = GameServicesState.SignedOut()
        sync.reconcile()
        assertFalse(sync.unreachable.value)
    }

    @Test
    fun `a save the service wouldn't take is reported as unreachable`() = runTest {
        val services = object : GameServices by SyncServices("ann") {
            override suspend fun readCloudSave(merge: (local: ByteArray, remote: ByteArray) -> ByteArray): CloudSaveRead = CloudSaveRead.Empty
            override suspend fun writeCloudSave(data: ByteArray, merge: (local: ByteArray, remote: ByteArray) -> ByteArray): ByteArray? = null
        }
        val sync = OnlineSync(services, SyncStore(state()), OnlineSettingsRepository(SyncPreferences()), { LeaderboardTotals() }, SyncScores()::best, SyncCloudSave(CloudSave(scores = scores("phone"))), now = { now })
        sync.reconcile()
        assertTrue(sync.unreachable.value)
    }

    @Test
    fun `a sync says which step it is on - of how many - and nothing once it ends`() = runTest {
        val services = SyncServices("ann").apply { cloud = found(CloudSave(scores = scores("tablet"))) }
        val scores = SyncScores(Triple(GameMode.STANDARD, 240, 20L))
        // This device has a game the save lacks, so there is something to upload.
        val sync = sync(services, SyncStore(state()), OnlineSettingsRepository(SyncPreferences()), scores = scores, cloudSave = SyncCloudSave(CloudSave(scores = scores("phone"))))
        services.syncProgress = { sync.progress.value }
        sync.reconcile(showProgress = true)
        assertEquals<List<Pair<String, SyncProgress?>>>(
            listOf(
                "readCloudSave" to SyncProgress(SyncStep.DOWNLOADING, 1, 7),
                "writeCloudSave" to SyncProgress(SyncStep.UPLOADING, 6, 7),
                "submitScore" to SyncProgress(SyncStep.LEADERBOARDS, 7, 7),
            ),
            services.progressSeen,
        )
        assertEquals(null, sync.progress.value)
    }

    @Test
    fun `with no cloud save - a sync has only its own four steps`() = runTest {
        val services = SyncServices("ann")
        val sync = sync(services, SyncStore(state()), OnlineSettingsRepository(SyncPreferences()), scores = SyncScores(Triple(GameMode.STANDARD, 240, 20L)))
        services.syncProgress = { sync.progress.value }
        sync.reconcile(showProgress = true)
        assertEquals<List<Pair<String, SyncProgress?>>>(listOf("submitScore" to SyncProgress(SyncStep.LEADERBOARDS, 4, 4)), services.progressSeen)
    }

    @Test
    fun `signed out - no sync is shown`() = runTest {
        val sync = sync(SyncServices(account = null), SyncStore(state()), OnlineSettingsRepository(SyncPreferences()), cloudSave = SyncCloudSave())
        sync.reconcile()
        assertEquals(null, sync.progress.value)
    }

    @Test
    fun `a quiet sync - not a sign-in's - shows no progress at all`() = runTest {
        val services = SyncServices("ann").apply { cloud = found(CloudSave(scores = scores("tablet"))) }
        val sync = sync(services, SyncStore(state()), OnlineSettingsRepository(SyncPreferences()), scores = SyncScores(Triple(GameMode.STANDARD, 240, 20L)), cloudSave = SyncCloudSave())
        services.syncProgress = { sync.progress.value }
        sync.reconcile()
        assertTrue(services.progressSeen.all { it.second == null })
    }

    @Test
    fun `the save is merged settings first - then the game in progress - achievements - and scores`() = runTest {
        val local = SyncCloudSave()
        val services = SyncServices("ann").apply { cloud = found(CloudSave(scores = scores("tablet"))) }
        sync(services, SyncStore(state()), OnlineSettingsRepository(SyncPreferences()), cloudSave = local).reconcile()
        assertEquals(listOf("settings", "game", "achievements", "scores"), local.merged)
    }

    @Test
    fun `another account's wipe comes before any of its save is merged in`() = runTest {
        val settings = OnlineSettingsRepository(SyncPreferences())
        val local = SyncCloudSave(CloudSave(scores = scores("ann's game")))
        sync(SyncServices("ann"), SyncStore(state()), settings, cloudSave = local).reconcile()
        local.merged.clear()
        val bob = SyncServices("bob").apply { cloud = found(CloudSave(scores = scores("bob's game"))) }
        sync(bob, SyncStore(state()), settings, cloudSave = SyncCloudSaveOrder(local)).reconcile()
        assertEquals(listOf("wipe", "settings", "game", "achievements", "scores"), local.merged)
    }

    @Test
    fun `a sign-in's sync marks its account synced when it ends - even when it couldn't get through - a quiet one doesn't`() = runTest {
        val services = SyncServices("ann", remote = null)
        val sync = sync(services, SyncStore(state()), OnlineSettingsRepository(SyncPreferences()), cloudSave = SyncCloudSave())
        sync.reconcile()
        assertEquals(null, sync.signInSynced.value)
        sync.reconcile(showProgress = true)
        assertEquals("ann", sync.signInSynced.value)
        sync.forgetSignInSync()
        assertEquals(null, sync.signInSynced.value)
    }

    @Test
    fun `a sign-in's sync on an install that used superuser mode still counts as done`() = runTest {
        val settings = OnlineSettingsRepository(SyncPreferences()).apply { blockMirroring() }
        val sync = sync(SyncServices("ann"), SyncStore(state()), settings, cloudSave = SyncCloudSave())
        sync.reconcile(showProgress = true)
        assertEquals("ann", sync.signInSynced.value)
    }
}

/** [inner], noting its wipe in [SyncCloudSave.merged] too, so its place in the order shows. */
private class SyncCloudSaveOrder(private val inner: SyncCloudSave) : CloudSaveStore by inner {
    override suspend fun wipe() {
        inner.merged += "wipe"
        inner.wipe()
    }
}
