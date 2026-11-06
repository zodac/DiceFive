package net.zodac.dicefive.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

/** Only an error leaves an attempt standing for the crash handler to see: one that's finished or called off doesn't. */
class ResumeAttemptTest {

    @Test
    fun `an attempt is active while it runs and over once it returns`() = runTest {
        val attempts = ResumeAttempts()
        assertFalse(attempts.active)
        assertEquals("done", attempts.during {
            assertTrue(attempts.active)
            "done"
        })
        assertFalse(attempts.active)
    }

    @Test
    fun `an attempt called off is over - while another under way keeps it active`() = runTest {
        val attempts = ResumeAttempts()
        val warmUp = launch(start = CoroutineStart.UNDISPATCHED) { attempts.during { awaitCancellation() } }
        val resume = launch(start = CoroutineStart.UNDISPATCHED) { attempts.during { awaitCancellation() } }
        warmUp.cancelAndJoin()
        assertTrue(attempts.active)
        resume.cancelAndJoin()
        assertFalse(attempts.active)
    }

    @Test
    fun `an attempt that fails stays active for the crash handler`() = runTest {
        val attempts = ResumeAttempts()
        assertFailsWith<IllegalStateException> { attempts.during { error("Boom") } }
        assertTrue(attempts.active)
    }
}
