package net.zodac.dicefive.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner

/** The tag of an [OffscreenWarmUp]'s box while it's drawing, for a test to see it - a screen reader finds nothing there. */
internal const val OFFSCREEN_WARM_UP_TAG = "offscreen_warm_up"

/** The pass an [OffscreenWarmUp] is drawing: set by its run, a pass at a time, and null between runs. */
internal class WarmUpPasses<P : Any> {

    var current: P? by mutableStateOf(null)

    /** Draws [pass] for [frames] frames - two is enough to compose and draw it - then returns, for the next. */
    suspend fun draw(pass: P, frames: Int = 2) {
        current = pass
        repeat(frames) { withFrameNanos { } }
    }
}

/**
 * Draws a screen's code once out of sight, so opening it isn't the first time that code runs: in a 1dp box clipped to
 * nothing and hidden from screen readers, under an opaque screen, laid out at [pageSize] as the real one would be.
 * [delayMillis] after the screen it's under settles - restarted whenever [key] changes - [run] steps through its passes
 * ([WarmUpPasses.draw]), and [content] draws whichever is current. Nothing is drawn between runs.
 */
@Composable
internal fun <P : Any> OffscreenWarmUp(
    key: Any?,
    delayMillis: Long,
    pageSize: DpSize,
    run: suspend WarmUpPasses<P>.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (P) -> Unit,
) {
    val passes = remember { WarmUpPasses<P>() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(key) {
        passes.current = null
        lifecycle.delayWhileResumed(delayMillis)
        passes.run()
        passes.current = null
    }
    val pass = passes.current ?: return
    Box(modifier = modifier.size(1.dp).clipToBounds().clearAndSetSemantics { testTag = OFFSCREEN_WARM_UP_TAG }) {
        Box(modifier = Modifier.requiredSize(pageSize)) {
            content(pass)
        }
    }
}
