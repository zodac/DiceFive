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
    /** Every score the service took, by leaderboard key; while [takesScores] is false it takes none. */
    val posted = mutableListOf<Pair<String, Long>>()
    var takesScores = true
    override suspend fun submitScore(leaderboardKey: String, score: Long): Boolean =
        takesScores.also { if (it) posted += leaderboardKey to score }
}

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
    ) = OnlineSync(services, store, settings, { totals }, scores::best, now = { now })

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
}
