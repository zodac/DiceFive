package net.zodac.dicefive.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * How the app opens a page: the page in front fades out to the bare backdrop, its drifting dice still moving, the next
 * page is built behind it, and only once that has settled does it fade in. A page's first frames (its composition, its
 * art's first paint, its data arriving) are its costliest, and behind the cover they're never seen as a stutter. Only
 * one page is drawn at a time, never two cross-fading. Going back doesn't use it: the page it returns to is shown at once.
 *
 * Under reduced motion the cover is put up and taken down without fading, still hiding a page that isn't ready.
 */
@Stable
class PageCover internal constructor(private val scope: CoroutineScope) {
    private val alpha = Animatable(0f)

    /** True from the moment a change starts until the next page is in full view. A page's clocks wait for it. */
    var changing by mutableStateOf(false)
        private set

    /** True while the cover is up waiting on the next page, when a slow one earns a spinner. */
    internal var waiting by mutableStateOf(false)
        private set

    private var holds by mutableIntStateOf(0)

    internal val opacity: Float get() = alpha.value

    internal fun hold() = holds++

    internal fun release() = holds--

    /**
     * Fades the cover in, makes [change] (a navigation) under it, waits for the next page to settle, then fades it out.
     * Ignored while a change is already under way, so a second tap can't navigate twice. Run in the scope the cover was
     * made in, above the pages, so the page that asked for it leaving doesn't cut it short.
     *
     * The next page has settled once nothing holds the cover ([HoldPageCover]), [MIN_FRAMES] frames have passed and
     * [SETTLED_FRAMES] frames in a row came at this screen's own pace, or steadily: a page whose every frame is costly
     * (Styles builds its tiles a few at a time) is shown rather than waited out. The cover never waits longer than
     * [MAX_SETTLE_MILLIS] after the last hold is released, or [MAX_HOLD_MILLIS] in all.
     */
    fun change(reduceMotion: Boolean, change: () -> Unit) {
        if (changing) return
        changing = true
        scope.launch { changePage(reduceMotion, change) }
    }

    private suspend fun changePage(reduceMotion: Boolean, change: () -> Unit) {
        try {
            val pace = cover(reduceMotion)
            change()
            waiting = true
            settle(pace)
            waiting = false
            if (reduceMotion) alpha.snapTo(0f) else alpha.animateTo(0f, tween(FADE_IN_MILLIS, easing = LinearEasing))
        } finally {
            waiting = false
            changing = false
        }
    }

    /**
     * Puts the cover up and has it drawn. Returns the gap between frames on this screen when nothing much is changing,
     * the shortest seen meanwhile: a capped Animations level draws at 30 a second, a fast screen at 120.
     */
    private suspend fun cover(reduceMotion: Boolean): Long = coroutineScope {
        var shortest = Long.MAX_VALUE
        val sampler = launch {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                shortest = minOf(shortest, now - last)
                last = now
            }
        }
        if (reduceMotion) alpha.snapTo(1f) else alpha.animateTo(1f, tween(FADE_OUT_MILLIS, easing = LinearEasing))
        // Drawn opaque, and at least one gap measured, before anything costly starts.
        withFrameNanos { }
        withFrameNanos { }
        sampler.cancel()
        shortest
    }

    private suspend fun settle(pace: Long) {
        val onPace = maxOf(pace * 8 / 5, MIN_ON_PACE_NANOS)
        val start = withFrameNanos { it }
        var last = start
        var lastGap = 0L
        var frames = 0
        var settled = 0
        var releasedAt: Long? = null
        while (true) {
            val now = withFrameNanos { it }
            val gap = now - last
            last = now
            frames++
            if (now - start > MAX_HOLD_MILLIS * NANOS_PER_MILLI) return
            if (holds > 0) {
                releasedAt = null
                settled = 0
            } else {
                val released = releasedAt ?: now.also { releasedAt = it }
                val steady = lastGap > 0 && gap <= MAX_STEADY_NANOS && gap * 4 <= lastGap * 5 && gap * 5 >= lastGap * 4
                settled = if (gap <= onPace || steady) settled + 1 else 0
                if (frames >= MIN_FRAMES && settled >= SETTLED_FRAMES) return
                if (now - released > MAX_SETTLE_MILLIS * NANOS_PER_MILLI) return
            }
            lastGap = gap
        }
    }

    internal companion object {
        const val FADE_OUT_MILLIS = 150
        const val FADE_IN_MILLIS = 200

        // The next page's first composition, then its first frame drawn.
        const val MIN_FRAMES = 2
        const val SETTLED_FRAMES = 2
        const val MAX_SETTLE_MILLIS = 400L
        const val MAX_HOLD_MILLIS = 3000L

        // How long a page held up behind the cover can take before a spinner says it's still coming.
        const val SPINNER_DELAY_MILLIS = 500L

        // A frame up to this long counts as on pace even on a 120Hz screen, which misses a refresh now and then.
        private const val MIN_ON_PACE_NANOS = 20_000_000L

        // Frames slower than this are never "steady": a page that slow is shown at MAX_SETTLE_MILLIS instead.
        private const val MAX_STEADY_NANOS = 100_000_000L
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}

/** A [PageCover] for the life of the composition it's made in. */
@Composable
fun rememberPageCover(): PageCover {
    val scope = rememberCoroutineScope()
    return remember(scope) { PageCover(scope) }
}

/** The app's [PageCover], provided by the NavHost. Null in a copy of a page built out of sight (a warm-up), and in tests of one page. */
val LocalPageCover = staticCompositionLocalOf<PageCover?> { null }

/** Keeps the page cover up while this is composed: a page showing nothing yet but its title, its content still loading. */
@Composable
fun HoldPageCover() {
    val cover = LocalPageCover.current ?: return
    DisposableEffect(cover) {
        cover.hold()
        onDispose { cover.release() }
    }
}

/**
 * The cover itself, over the page and under the achievement banners: the page backdrop and its drifting dice, so the dice
 * carry on through the change, with a spinner once the next page has been slow to come. Silent to TalkBack, and taking
 * every touch while it's up, so nothing on the page going or coming is tapped by mistake.
 */
@Composable
internal fun PageCoverOverlay(cover: PageCover, modifier: Modifier = Modifier) {
    if (!cover.changing) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = cover.opacity }
            .clearAndSetSemantics { }
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
    ) {
        BrandBackdrop(driftingDice = true) {
            if (cover.waiting) {
                var slow by remember { mutableStateOf(false) }
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                LaunchedEffect(Unit) {
                    lifecycle.delayWhileResumed(PageCover.SPINNER_DELAY_MILLIS)
                    slow = true
                }
                // A merge boundary of its own, which the cover's clearing doesn't reach: cleared again here.
                if (slow) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).clearAndSetSemantics { })
            }
        }
    }
}
