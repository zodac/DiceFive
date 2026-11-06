package net.zodac.dicefive.app

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Whether a saved game is being brought back or drawn for the first time, kept for the process so an error nothing
 * caught can tell that game is what failed. That game then can't be played, and leaving it saved would fail the same way
 * each time, so the crash handler has it removed.
 */
object ResumeAttempt : ResumeAttempts()

/** The attempts under way, and any that ended in an error. */
open class ResumeAttempts {

    private val running = MutableStateFlow(0)

    /** Whether any [during] is under way, or ended in an error. */
    val active: Boolean get() = running.value > 0

    /** Runs [block] as an attempt. Only an error leaves it [active]: that error is the one the crash handler is about to see. */
    suspend fun <T> during(block: suspend () -> T): T {
        running.update { it + 1 }
        val result = try {
            block()
        } catch (e: CancellationException) {
            running.update { it - 1 }
            throw e
        }
        running.update { it - 1 }
        return result
    }
}
