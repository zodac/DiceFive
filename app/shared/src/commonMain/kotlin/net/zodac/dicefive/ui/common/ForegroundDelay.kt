package net.zodac.dicefive.ui.common

import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [kotlinx.coroutines.delay] that only runs while this lifecycle is resumed - the app is in front. A plain
 * `delay` keeps counting with the app in the background (the process stays alive, and coroutines with it), so
 * a banner would clear itself, or a roll land, while nobody was looking. This waits for the app to come
 * back first, and if it goes away part-way through starts the delay again on return: a fresh [millis]
 * rather than the remainder, which is what a hold or a shake wants.
 *
 * Read from the lifecycle's own state flow, not composition state: a backgrounded app draws no frames,
 * so nothing would recompose to notice.
 */
suspend fun Lifecycle.delayWhileResumed(millis: Long) {
    val resumed = currentStateFlow.map { it.isAtLeast(Lifecycle.State.RESUMED) }.distinctUntilChanged()
    while (true) {
        resumed.first { it }
        if (withTimeoutOrNull(millis) { resumed.first { !it } } == null) return
    }
}
