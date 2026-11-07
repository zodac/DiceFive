package net.zodac.dicefive.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

/**
 * Whether anyone has touched the screen lately, and the window's [InfiniteAnimationPolicy]: from [IDLE_MILLIS] after the
 * last [wake], every infinite animation (an `InfiniteTransition`, a `withInfiniteAnimationFrameNanos` loop) waits before its
 * next frame, so it freezes as it is and an untouched screen asks for no frames for it. What the game does isn't frozen: a
 * finite animation doesn't ask this policy, and a loop the game needs runs under a [hold] ([HoldScreenAwake]).
 */
class ScreenIdle : InfiniteAnimationPolicy {

    private companion object {
        const val IDLE_MILLIS = 60_000L
    }

    /** Read in composition, so only what depends on it recomposes when it changes. */
    var idle by mutableStateOf(false)
        private set

    private var holds = 0
    private val frozen = MutableStateFlow(false)
    private val wakes = MutableStateFlow(0L)

    /** Someone's there: what's frozen moves on, and the wait starts over. Main thread only. */
    fun wake() {
        wakes.value += 1
        markIdle(false)
    }

    /** Sets [idle] once [IDLE_MILLIS] of [sleep] has passed since the last [wake]. Runs until cancelled. */
    suspend fun runTimer(sleep: suspend (Long) -> Unit) {
        wakes.collectLatest {
            sleep(IDLE_MILLIS)
            markIdle(true)
        }
    }

    /** Lets infinite animations run, idle or not, until the matching [release]. Main thread only. */
    fun hold() {
        holds++
        updateFrozen()
    }

    fun release() {
        holds--
        updateFrozen()
    }

    private fun markIdle(value: Boolean) {
        idle = value
        updateFrozen()
    }

    private fun updateFrozen() {
        frozen.value = idle && holds == 0
    }

    override suspend fun <R> onInfiniteOperation(block: suspend () -> R): R {
        frozen.first { !it }
        return block()
    }
}

/** The app's one [ScreenIdle]; null, so never idle, where nothing provides it. Provided once at the root. */
internal val LocalScreenIdle = staticCompositionLocalOf<ScreenIdle?> { null }

/** Wakes [screen] on every touch of this or anything in it, taking nothing from what's touched. */
internal fun Modifier.wakesScreen(screen: ScreenIdle): Modifier = pointerInput(screen) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            screen.wake()
        }
    }
}

/** While composed, nothing is frozen ([ScreenIdle.hold]): for a loop the game needs, such as a CPU's cup shaking. */
@Composable
internal fun HoldScreenAwake() {
    val screen = LocalScreenIdle.current ?: return
    DisposableEffect(screen) {
        screen.hold()
        onDispose { screen.release() }
    }
}
