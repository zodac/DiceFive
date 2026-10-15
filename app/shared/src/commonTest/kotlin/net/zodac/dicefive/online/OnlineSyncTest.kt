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
import net.zodac.dicefive.game.LeaderboardTotals
import net.zodac.dicefive.model.Achievement
import net.zodac.dicefive.model.AchievementCounter

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
        if (account == null) GameServicesState.SignedOut else GameServicesState.SignedIn(OnlineAccount(account, null, account)),
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
}

class OnlineSyncTest {

    private val now = 1_000L

    private fun sync(services: SyncServices, store: SyncStore, settings: OnlineSettingsRepository, totals: LeaderboardTotals = LeaderboardTotals()) =
        OnlineSync(services, store, settings, { totals }, now = { now })

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
        services.mutableState.value = GameServicesState.SignedOut
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
}
