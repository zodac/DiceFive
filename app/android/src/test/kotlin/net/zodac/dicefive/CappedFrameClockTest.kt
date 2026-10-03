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

/** [CappedFrameClock], fed 60Hz frames by hand. */
@OptIn(ExperimentalCoroutinesApi::class)
class CappedFrameClockTest {

    private val frameGapNanos = 16_666_667L
    private val firstFrameNanos = 1_000_000_000L

    @After
    fun tearDown() {
        CappedFrameClock.capped = false
    }

    /** Sends eight 60Hz frames to a clock with two animations waiting on it, returning the frames each saw. */
    private fun framesSeen(capped: Boolean): List<List<Long>> {
        CappedFrameClock.capped = capped
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

    @Test
    fun `uncapped, every frame is handed on`() {
        val expected = List(8) { firstFrameNanos + it * frameGapNanos }
        framesSeen(capped = false).forEach { assertEquals(expected, it) }
    }

    @Test
    fun `capped, every other 60Hz frame is handed on - 30fps - and to every animation waiting on it`() {
        val expected = List(4) { firstFrameNanos + it * 2 * frameGapNanos }
        framesSeen(capped = true).forEach { assertEquals(expected, it) }
    }
}
