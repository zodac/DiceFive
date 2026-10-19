package net.zodac.dicefive.ui.settings

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.dicefive.data.achievements.InMemoryAchievementStore
import net.zodac.dicefive.model.Achievement

@OptIn(ExperimentalCoroutinesApi::class)
class VersionTapTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Whether taps on the version number at each of [times] earn Are You A Developer?. */
    private fun earnedBy(vararg times: Long): Boolean {
        val store = InMemoryAchievementStore()
        val viewModel = settingsViewModel(achievements = store)
        var earned = false
        runTest {
            times.forEach { viewModel.onVersionTapped(it) }
            advanceUntilIdle()
            earned = store.current().isUnlocked(Achievement.ARE_YOU_A_DEVELOPER)
        }
        return earned
    }

    @Test
    fun `seven quick taps on the version number - not six - earn Are You A Developer`() {
        assertTrue(earnedBy(*LongArray(7) { it * 300L }))
        assertFalse(earnedBy(*LongArray(6) { it * 300L }))
    }

    @Test
    fun `a slow tap starts the count again`() {
        val gap = SettingsViewModel.VERSION_TAP_GAP_MILLIS
        // Six quick, a pause, then one: one in a row, not seven.
        assertFalse(earnedBy(*(LongArray(6) { it * 300L } + (5 * 300L + gap + 1))))
        // The pause just within the gap still counts.
        assertTrue(earnedBy(*(LongArray(6) { it * 300L } + (5 * 300L + gap))))
        // Seven more after a pause earn it, counted from the pause.
        assertTrue(earnedBy(*(LongArray(3) { it * 300L } + LongArray(7) { 10_000L + it * 300L })))
    }

    /** What the version taps at each of [times] message, in order (see [SettingsViewModel.versionTapMessages]), with the achievement [earned] or not. */
    private fun messagesFor(earned: Boolean, vararg times: Long): List<Int> {
        val store = InMemoryAchievementStore()
        val viewModel = settingsViewModel(achievements = store)
        val messages = mutableListOf<Int>()
        runTest {
            if (earned) store.record(mapOf(Achievement.ARE_YOU_A_DEVELOPER to 1L), emptyMap())
            val job = launch { viewModel.versionTapMessages.collect { messages += it } }
            times.forEach { viewModel.onVersionTapped(it) }
            advanceUntilIdle()
            job.cancel()
        }
        return messages
    }

    @Test
    fun `repeated taps before the achievement count down the steps left`() {
        assertEquals(emptyList(), messagesFor(false, 0L))
        assertEquals(listOf(5), messagesFor(false, 0L, 300L))
        // The seventh tap earns it, and its banner is the message.
        assertEquals(listOf(5, 4, 3, 2, 1), messagesFor(false, *LongArray(7) { it * 300L }))
    }

    @Test
    fun `repeated taps after the achievement say so every time`() {
        assertEquals(emptyList(), messagesFor(true, 0L))
        assertEquals(listOf(0, 0, 0), messagesFor(true, 0L, 300L, 600L, 900L).take(3))
        assertEquals(List(6) { 0 }, messagesFor(true, *LongArray(7) { it * 300L }))
    }

    @Test
    fun `a slow tap restarts the messages`() {
        val gap = SettingsViewModel.VERSION_TAP_GAP_MILLIS
        assertEquals(listOf(5), messagesFor(false, 0L, 300L, 300L + gap + 1))
        assertEquals(listOf(5, 5), messagesFor(false, 0L, 300L, 10_000L, 10_300L))
    }
}
