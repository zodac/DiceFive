package net.zodac.dicefive

import androidx.compose.runtime.BroadcastFrameClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.device.CappedFrameClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** [CappedFrameClock], fed frames by hand at a screen's refresh rate. */
@OptIn(ExperimentalCoroutinesApi::class)
class CappedFrameClockTest {

    private val firstFrameNanos = 1_000_000_000L

    @After
    fun tearDown() {
        CappedFrameClock.maxFramesPerSecond = null
    }

    /** Sends eight frames at [refreshHz] to a clock capped at [maxFramesPerSecond] with two animations waiting on it, returning the frames each saw. */
    private fun framesSeen(maxFramesPerSecond: Int?, refreshHz: Int = 60): List<List<Long>> {
        val frameGapNanos = 1_000_000_000L / refreshHz
        CappedFrameClock.maxFramesPerSecond = maxFramesPerSecond
        val base = BroadcastFrameClock()
        val clock = CappedFrameClock(base)
        val seen = List(2) { mutableListOf<Long>() }
        runTest(UnconfinedTestDispatcher()) {
            seen.forEach { frames ->
                backgroundScope.launch { while (true) clock.withFrameNanos { frames += it } }
            }
            repeat(8) { frame ->
                base.sendFrame(firstFrameNanos + frame * frameGapNanos)
                runCurrent()
            }
        }
        return seen
    }

    /** The [count] frames at [refreshHz] from the first, taking every [step]th. */
    private fun frames(refreshHz: Int, step: Int, count: Int) = List(count) { firstFrameNanos + it * step * (1_000_000_000L / refreshHz) }

    @Test
    fun `every frame is handed on uncapped - and under a cap only every nth - to every animation waiting on it`() {
        framesSeen(maxFramesPerSecond = null, refreshHz = 120).forEach { assertEquals("uncapped at 120Hz", frames(120, step = 1, count = 8), it) }
        // Capped at 30: every other 60Hz frame, every fourth 120Hz one.
        framesSeen(maxFramesPerSecond = 30).forEach { assertEquals("30 at 60Hz", frames(60, step = 2, count = 4), it) }
        framesSeen(maxFramesPerSecond = 30, refreshHz = 120).forEach { assertEquals("30 at 120Hz", frames(120, step = 4, count = 2), it) }
        // Capped at 60: a 60Hz screen loses no frames and a 120Hz one every other.
        framesSeen(maxFramesPerSecond = 60).forEach { assertEquals("60 at 60Hz", frames(60, step = 1, count = 8), it) }
        framesSeen(maxFramesPerSecond = 60, refreshHz = 120).forEach { assertEquals("60 at 120Hz", frames(120, step = 2, count = 4), it) }
        // A 90Hz screen that can't drop to 60 gives 45 frames a second - every other.
        framesSeen(maxFramesPerSecond = 60, refreshHz = 90).forEach { assertEquals("60 at 90Hz", frames(90, step = 2, count = 4), it) }
    }
}
