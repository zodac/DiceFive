package net.zodac.dicefive.app

import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.zodac.dicefive.data.orNullOnFailure

/**
 * Counts the time the app is in front of the player, from each [resume] to its [pause], on a clock that changing the
 * phone's time can't move. Whole seconds go to [addSeconds] at each pause and every [saveEvery] while resumed, so an
 * ending the app gets no warning of costs at most that long; the part of a second left over waits for the next. Seconds
 * that can't be saved wait for the next save too.
 */
class PlayTimeTracker(
    private val addSeconds: suspend (Int) -> Unit,
    scope: CoroutineScope,
    private val clock: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    private val saveEvery: Duration = 5.minutes,
) {

    /** What's been counted and not saved: [unsaved] up to [resumedAt], and all the time since it while it isn't null. */
    private data class Count(val resumedAt: ComparableTimeMark? = null, val unsaved: Duration = Duration.ZERO)

    private val count = MutableStateFlow(Count())

    /** Each resume (true) or pause (false) and when it happened, handled in order by the one coroutine that saves. */
    private val changes = Channel<Pair<Boolean, ComparableTimeMark>>(Channel.UNLIMITED)

    /** The whole seconds counted that aren't saved yet, up to now. */
    val unsavedSeconds: Int
        get() = count.value.let { (resumedAt, unsaved) -> (unsaved + (resumedAt?.elapsedNow() ?: Duration.ZERO)).inWholeSeconds.toInt() }

    init {
        scope.launch {
            while (true) {
                val resumed = count.value.resumedAt != null
                // Paused, it waits for the next resume, and so wakes nothing.
                val change = if (resumed) withTimeoutOrNull(saveEvery) { changes.receive() } else changes.receive()
                val (resumes, at) = change ?: (true to clock.markNow())
                count.update { (since, unsaved) -> Count(at.takeIf { resumes }, unsaved + (since?.let { at - it } ?: Duration.ZERO)) }
                save()
            }
        }
    }

    /** Taken off before it's added on, so [unsavedSeconds] and the saved total together never count a second twice. */
    private suspend fun save() {
        val whole = count.value.unsaved.inWholeSeconds.toInt()
        if (whole <= 0) return
        count.update { it.copy(unsaved = it.unsaved - whole.seconds) }
        if (orNullOnFailure { addSeconds(whole) } == null) count.update { it.copy(unsaved = it.unsaved + whole.seconds) }
    }

    fun resume() {
        changes.trySend(true to clock.markNow())
    }

    fun pause() {
        changes.trySend(false to clock.markNow())
    }
}
