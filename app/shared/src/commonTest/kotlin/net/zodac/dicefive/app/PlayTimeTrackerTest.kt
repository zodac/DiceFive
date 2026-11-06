package net.zodac.dicefive.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource

/** Only the time between a resume and its pause counts, in whole seconds, and a save that fails loses none of it. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayTimeTrackerTest {

    private val saved = mutableListOf<Int>()
    private var failing = false

    private fun TestScope.tracker() = PlayTimeTracker(
        addSeconds = {
            check(!failing) { "Disk full" }
            saved += it
        },
        scope = backgroundScope,
        clock = testTimeSource,
    ).also { runCurrent() }

    private fun TestScope.play(tracker: PlayTimeTracker, seconds: Long, millis: Long = 0) {
        tracker.resume()
        runCurrent()
        advanceTimeBy(seconds.seconds + millis.milliseconds)
        tracker.pause()
        runCurrent()
    }

    @Test
    fun `time counts from a resume to its pause - and none of the time in the background does`() = runTest {
        val tracker = tracker()
        play(tracker, seconds = 30)
        assertEquals(listOf(30), saved)

        advanceTimeBy(10.minutes)
        runCurrent()
        assertEquals(listOf(30), saved, "paused, nothing counts and nothing is saved")

        play(tracker, seconds = 5)
        assertEquals(listOf(30, 5), saved)
    }

    @Test
    fun `it saves every five minutes while resumed - so an ending with no warning costs at most that`() = runTest {
        val tracker = tracker()
        tracker.resume()
        runCurrent()
        advanceTimeBy(10.minutes + 1.seconds)
        runCurrent()
        assertEquals(listOf(300, 300), saved)

        tracker.pause()
        runCurrent()
        assertEquals(listOf(300, 300, 1), saved)
    }

    @Test
    fun `parts of a second wait for the next session rather than being lost or rounded up`() = runTest {
        val tracker = tracker()
        play(tracker, seconds = 0, millis = 600)
        assertEquals(emptyList(), saved)

        play(tracker, seconds = 0, millis = 600)
        assertEquals(listOf(1), saved)
    }

    @Test
    fun `seconds that can't be saved wait for the next save`() = runTest {
        val tracker = tracker()
        failing = true
        play(tracker, seconds = 20)
        assertEquals(emptyList(), saved)

        failing = false
        play(tracker, seconds = 1)
        assertEquals(listOf(20, 1), saved)
    }

    @Test
    fun `what isn't saved yet is on hand for a live count - and a save moves it across without counting it twice`() = runTest {
        var total = 0
        val tracker = PlayTimeTracker(addSeconds = { total += it }, scope = backgroundScope, clock = testTimeSource)
        runCurrent()
        assertEquals(0, tracker.unsavedSeconds)

        tracker.resume()
        runCurrent()
        advanceTimeBy(42.seconds)
        assertEquals(42, total + tracker.unsavedSeconds)

        advanceTimeBy(300.seconds)
        runCurrent()
        assertEquals(300, total)
        assertEquals(342, total + tracker.unsavedSeconds)

        tracker.pause()
        runCurrent()
        assertEquals(342, total)
        advanceTimeBy(30.seconds)
        assertEquals(342, total + tracker.unsavedSeconds, "nothing counts while paused")
    }
}
