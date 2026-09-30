package net.zodac.dicefive.ui.common

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundDelayTest {

    private class Owner : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
        fun moveTo(state: Lifecycle.State) {
            registry.currentState = state
        }
    }

    @Test
    fun `it finishes after its time while the app is in front`() = runTest {
        val owner = Owner().apply { moveTo(Lifecycle.State.RESUMED) }
        var done = false
        launch {
            owner.lifecycle.delayWhileResumed(1_000)
            done = true
        }

        advanceTimeBy(999)
        runCurrent()
        assertFalse(done)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(done)
    }

    @Test
    fun `it does not run down while the app is in the background`() = runTest {
        val owner = Owner().apply { moveTo(Lifecycle.State.CREATED) }
        var done = false
        launch {
            owner.lifecycle.delayWhileResumed(1_000)
            done = true
        }

        advanceTimeBy(60_000)
        runCurrent()
        assertFalse(done)

        owner.moveTo(Lifecycle.State.RESUMED)
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(done)
    }

    @Test
    fun `going to the background part-way starts it again on return`() = runTest {
        val owner = Owner().apply { moveTo(Lifecycle.State.RESUMED) }
        var done = false
        launch {
            owner.lifecycle.delayWhileResumed(1_000)
            done = true
        }

        advanceTimeBy(600)
        runCurrent()
        owner.moveTo(Lifecycle.State.STARTED)
        advanceTimeBy(30_000)
        runCurrent()
        assertFalse(done)

        owner.moveTo(Lifecycle.State.RESUMED)
        advanceTimeBy(999)
        runCurrent()
        assertFalse(done)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(done)
    }
}
