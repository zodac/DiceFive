package net.zodac.dicefive.ui.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug builds only: the crash superuser mode on Settings > About, to see the crash page an error nothing caught opens.
 * [TAP_TARGET] taps on the author's name turn it on; then each of the page's links crashes the app its own way, instead
 * of opening.
 */
internal object DebugCrashes {

    /** How many taps on the author's name turn the mode on. */
    const val TAP_TARGET = 5

    /** The toast that says it's on. */
    const val ACTIVATED_MESSAGE = "Crash mode activated! Each link now crashes the app" // i18n: not translated - a debug-build developer toast

    /** Where [Kind.BACKGROUND]'s coroutine runs: off the main thread, with nothing to catch what it throws. */
    var backgroundScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    /** A way to crash, one per link on the page, in the page's order. */
    enum class Kind {
        /** Thrown on the main thread, by the tap itself. */
        MAIN_THREAD,

        /** Thrown in a coroutine off the main thread. */
        BACKGROUND,

        /** Thrown while the page is being drawn. */
        COMPOSITION,
    }

    /** What [kind] throws. */
    fun error(kind: Kind): DeliberateCrash = when (kind) {
        Kind.MAIN_THREAD -> DeliberateCrash("A tap on the main thread") // i18n: not translated - a developer's exception
        Kind.BACKGROUND -> DeliberateCrash("A coroutine off the main thread") // i18n: not translated - a developer's exception
        Kind.COMPOSITION -> DeliberateCrash("The About page, as it was drawn") // i18n: not translated - a developer's exception
    }

    /** Throws [Kind.BACKGROUND]'s error in [backgroundScope]. */
    fun crashInBackground() {
        backgroundScope.launch { throw error(Kind.BACKGROUND) }
    }
}

/** What [DebugCrashes] throws, on purpose, in a debug build. */
internal class DeliberateCrash(where: String) : IllegalStateException("Debug build's crash mode: $where") // i18n: not translated - a developer's exception
