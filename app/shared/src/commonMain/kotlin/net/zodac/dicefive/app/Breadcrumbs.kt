package net.zodac.dicefive.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * The last [MAX] things done in the app, oldest first - the pages opened and the moves made - kept for the process so
 * a crash report can say what led up to it. Developer text, never translated, and never anything a player typed: no
 * names, only ids and numbers.
 */
object Breadcrumbs {

    const val MAX = 12

    private val trail = MutableStateFlow<List<String>>(emptyList())

    /** What was done, oldest first. */
    val recent: List<String> get() = trail.value

    fun record(action: String) {
        trail.update { (it + action).takeLast(MAX) }
    }
}
