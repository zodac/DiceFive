package net.zodac.dicefive.ui.common

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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

    /** A [Lifecycle.delayWhileResumed] of a second started on [owner]: whether it has finished yet. */
    private fun TestScope.delayed(owner: Owner): () -> Boolean {
        var done = false
        launch {
            owner.lifecycle.delayWhileResumed(1_000)
            done = true
        }
        return { done }
    }

    /** Lets [millis] of test time pass. */
    private fun TestScope.wait(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `it finishes after its time in front - doesn't run down in the background - and starts again on return part-way`() = runTest {
        val front = Owner().apply { moveTo(Lifecycle.State.RESUMED) }
        val frontDone = delayed(front)
        wait(999)
        assertFalse(frontDone())
        wait(1)
        assertTrue(frontDone())

        val background = Owner().apply { moveTo(Lifecycle.State.CREATED) }
        val backgroundDone = delayed(background)
        wait(60_000)
        assertFalse(backgroundDone())
        background.moveTo(Lifecycle.State.RESUMED)
        wait(1_000)
        assertTrue(backgroundDone())

        val partWay = Owner().apply { moveTo(Lifecycle.State.RESUMED) }
        val partWayDone = delayed(partWay)
        wait(600)
        partWay.moveTo(Lifecycle.State.STARTED)
        wait(30_000)
        assertFalse(partWayDone())
        partWay.moveTo(Lifecycle.State.RESUMED)
        wait(999)
        assertFalse(partWayDone())
        wait(1)
        assertTrue(partWayDone())
    }
}
