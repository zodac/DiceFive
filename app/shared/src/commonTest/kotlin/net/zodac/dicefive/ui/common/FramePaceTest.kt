package net.zodac.dicefive.ui.common

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.withFrameNanos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/** A group's frames are its own rate's, skipping whole refreshes of the window's - and every frame where it's uncapped. */
class FramePaceTest {

    /** A window drawing at [hz], handing each caller the next refresh. */
    private class Window(hz: Int) : MonotonicFrameClock {
        private val step = 1_000_000_000L / hz
        var now = 0L
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            now += step
            return onFrame(now)
        }
    }

    /** The refreshes (1 the first) of a [hz] window that four frames at [pace] land on. */
    private fun refreshesSeen(pace: FramePace, hz: Int): List<Long> {
        val window = Window(hz)
        val seen = mutableListOf<Long>()
        runTest {
            withContext(window) { pace.run { repeat(4) { seen += withFrameNanos { it } / (1_000_000_000L / hz) } } }
        }
        return seen
    }

    @Test
    fun `a paced group skips whole refreshes - every fourth at 30 on 120Hz - and an uncapped one takes them all`() {
        assertEquals(listOf(1L, 5L, 9L, 13L), refreshesSeen(FramePace(30), hz = 120))
        assertEquals(listOf(1L, 3L, 5L, 7L), refreshesSeen(FramePace(60), hz = 120))
        assertEquals(listOf(1L, 2L, 3L, 4L), refreshesSeen(FramePace(60), hz = 60))
        assertEquals(listOf(1L, 2L, 3L, 4L), refreshesSeen(FramePace.Unpaced, hz = 120))
    }

    @Test
    fun `every animation waiting on a frame the pace hands on gets it - not just the first`() {
        val pace = FramePace(30)
        assertTrue(pace.admits(100L))
        assertTrue(pace.admits(100L), "the same frame, for the next animation waiting on it")
        assertEquals(false, pace.admits(100L + 8_333_333L))
    }
}
