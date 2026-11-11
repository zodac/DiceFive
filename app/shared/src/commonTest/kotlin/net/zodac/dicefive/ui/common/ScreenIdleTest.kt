package net.zodac.dicefive.ui.common

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.MonotonicFrameClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.zodac.dicefive.data.settings.IdleTimeout

/** The screen goes idle the player's Pause when idle (a minute by default) after the last touch, counting only the time the app is in front. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenIdleTest {

    private class Owner : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
        fun moveTo(state: Lifecycle.State) {
            registry.currentState = state
        }
    }

    private fun TestScope.wait(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `it goes idle a minute after the start - and a wake ends that for another minute`() = runTest {
        val screen = ScreenIdle()
        backgroundScope.launch { screen.runTimer { delay(it) } }
        runCurrent()
        assertFalse(screen.idle)
        wait(59_999)
        assertFalse(screen.idle)
        wait(1)
        assertTrue(screen.idle)

        screen.wake()
        assertFalse(screen.idle)
        runCurrent()
        wait(59_999)
        assertFalse(screen.idle)
        wait(1)
        assertTrue(screen.idle)
    }

    @Test
    fun `each wake starts the minute again`() = runTest {
        val screen = ScreenIdle()
        backgroundScope.launch { screen.runTimer { delay(it) } }
        runCurrent()
        wait(59_000)
        screen.wake()
        runCurrent()
        wait(59_000)
        assertFalse(screen.idle)
        wait(1_000)
        assertTrue(screen.idle)
    }

    @Test
    fun `time in the background doesn't count towards the minute`() = runTest {
        val owner = Owner().apply { moveTo(Lifecycle.State.RESUMED) }
        val screen = ScreenIdle()
        backgroundScope.launch { screen.runTimer(owner.lifecycle::delayWhileResumed) }
        runCurrent()
        wait(30_000)
        owner.moveTo(Lifecycle.State.CREATED)
        wait(600_000)
        assertFalse(screen.idle)
        owner.moveTo(Lifecycle.State.RESUMED)
        wait(59_999)
        assertFalse(screen.idle)
        wait(1)
        assertTrue(screen.idle)
    }

    @Test
    fun `each wait goes idle after its own time - and a new one starts the wait over`() = runTest {
        val screen = ScreenIdle()
        backgroundScope.launch { screen.runTimer { delay(it) } }
        screen.setTimeout(IdleTimeout.SECONDS_30)
        runCurrent()
        wait(29_999)
        assertFalse(screen.idle)
        wait(1)
        assertTrue(screen.idle)

        screen.setTimeout(IdleTimeout.SECONDS_90)
        screen.wake()
        runCurrent()
        wait(89_999)
        assertFalse(screen.idle)
        wait(1)
        assertTrue(screen.idle)

        // Changed partway through a wait, the new wait counts from the change.
        screen.wake()
        runCurrent()
        wait(20_000)
        screen.setTimeout(IdleTimeout.SECONDS_30)
        runCurrent()
        wait(29_999)
        assertFalse(screen.idle)
        wait(1)
        assertTrue(screen.idle)
    }

    @Test
    fun `off - it never goes idle - and turned off on an idle screen it wakes`() = runTest {
        val screen = ScreenIdle()
        backgroundScope.launch { screen.runTimer { delay(it) } }
        val frames = loopUnder(screen)
        runCurrent()
        wait(60_016)
        assertTrue(screen.idle)

        screen.setTimeout(IdleTimeout.OFF)
        runCurrent()
        assertFalse(screen.idle)
        wait(16)
        val woken = frames()
        wait(600_000)
        assertFalse(screen.idle)
        assertTrue(frames() > woken, "Never frozen")

        // Back on, the wait starts from then.
        screen.setTimeout(IdleTimeout.SECONDS_30)
        runCurrent()
        wait(30_000)
        assertTrue(screen.idle)
    }

    /** A frame every 16ms of test time. */
    private class TestFrames : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            delay(16)
            return onFrame(0L)
        }
    }

    /** An infinite animation run under [screen], as the window's recomposer runs one: the frames it has had so far. */
    private fun TestScope.loopUnder(screen: ScreenIdle): () -> Int {
        var frames = 0
        backgroundScope.launch(screen + TestFrames()) {
            while (true) withInfiniteAnimationFrameNanos { frames++ }
        }
        return { frames }
    }

    @Test
    fun `an idle screen freezes an infinite animation - and a wake lets it move on`() = runTest {
        val screen = ScreenIdle()
        backgroundScope.launch { screen.runTimer { delay(it) } }
        val frames = loopUnder(screen)
        runCurrent()
        wait(1_000)
        assertTrue(frames() > 0, "Moving before the minute is up")

        wait(59_000)
        assertTrue(screen.idle)
        wait(16)
        val frozen = frames()
        wait(10_000)
        assertEquals(frozen, frames(), "Idle, it asks for no frames")

        screen.wake()
        runCurrent()
        wait(1_000)
        assertTrue(frames() > frozen, "Woken, it moves on")
    }

    @Test
    fun `a hold lets an infinite animation run on an idle screen - until it's released`() = runTest {
        val screen = ScreenIdle()
        backgroundScope.launch { screen.runTimer { delay(it) } }
        val frames = loopUnder(screen)
        runCurrent()
        wait(60_016)
        val frozen = frames()
        wait(1_000)
        assertEquals(frozen, frames())

        screen.hold()
        runCurrent()
        wait(1_000)
        val held = frames()
        assertTrue(held > frozen, "Held, it runs")

        screen.release()
        runCurrent()
        wait(16)
        val released = frames()
        wait(1_000)
        assertEquals(released, frames(), "Released, it's frozen again")
    }
}
