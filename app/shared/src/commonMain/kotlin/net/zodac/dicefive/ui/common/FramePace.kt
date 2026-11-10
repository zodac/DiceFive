package net.zodac.dicefive.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import net.zodac.dicefive.data.settings.AnimationLevel
import net.zodac.dicefive.data.settings.MotionGroup
import net.zodac.dicefive.data.settings.MotionSettings

/**
 * One [MotionGroup]'s frames: at most [framesPerSecond] a second handed on to what animates in it ([run]), or every
 * frame the window draws where null. One is shared by everything in its group, so the group steps together.
 *
 * The window itself runs at the fastest group's rate ([MotionSettings.maxFramesPerSecond]); a slower group skips whole
 * refreshes of it. A frame is handed on if it's at least 90% of a paced frame's length after the last one handed on
 * (slack for vsync jitter) - or is that same frame, so every animation waiting on one frame gets it, not just the
 * first. Only touched on the main thread, where frames are delivered.
 */
class FramePace(val framesPerSecond: Int?) {

    private val minGapNanos = framesPerSecond?.let { 1_000_000_000L / it * 9 / 10 } ?: 0L

    private var lastFrameNanos: Long? = null

    /** Whether the frame at [frameTimeNanos] is one to hand on, noting it as the last if it is. */
    internal fun admits(frameTimeNanos: Long): Boolean {
        val last = lastFrameNanos
        if (minGapNanos != 0L && last != null && frameTimeNanos != last && frameTimeNanos - last < minGapNanos) return false
        lastFrameNanos = frameTimeNanos
        return true
    }

    /**
     * Runs [block] with its frames at this pace: every animation it runs (an `Animatable`'s `animateTo`, a
     * `withFrameNanos` or `withInfiniteAnimationFrameNanos` loop) waits for this pace's frames, not the window's.
     */
    suspend fun <R> run(block: suspend CoroutineScope.() -> R): R {
        val base = currentCoroutineContext()[MonotonicFrameClock]
        return if (minGapNanos == 0L || base == null) coroutineScope(block) else withContext(PacedFrameClock(base, this), block)
    }

    companion object {
        /** Every frame the window draws. */
        val Unpaced = FramePace(null)
    }
}

/** [base]'s frames, as [pace] admits them. */
private class PacedFrameClock(private val base: MonotonicFrameClock, private val pace: FramePace) : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
        while (true) {
            var handedOn = false
            var result: R? = null
            base.withFrameNanos { frameTimeNanos ->
                if (pace.admits(frameTimeNanos)) {
                    result = onFrame(frameTimeNanos)
                    handedOn = true
                }
            }
            // R may itself be nullable, so whether the frame was handed on is tracked separately from the result.
            @Suppress("UNCHECKED_CAST")
            if (handedOn) return result as R
        }
    }
}

/** A [FramePace] for each [MotionGroup] of [motion], at its rate. */
class FramePaces(motion: MotionSettings) {
    private val paces = MotionGroup.entries.associateWith { FramePace(motion.rate(it).framesPerSecond) }

    /** [group]'s pace. */
    fun of(group: MotionGroup): FramePace = paces.getValue(group)

    val menus: FramePace get() = of(MotionGroup.MENUS)
    val scoreGlow: FramePace get() = of(MotionGroup.SCORE_GLOW)
    val cupShake: FramePace get() = of(MotionGroup.CUP_SHAKE)
    val diceRoll: FramePace get() = of(MotionGroup.DICE_ROLL)
}

/** Each group's [FramePace], from [LocalMotionSettings]. Provided once at the root, beside it. */
val LocalFramePaces = staticCompositionLocalOf { FramePaces(AnimationLevel.default.motion) }

/**
 * The [FramePace] of whatever animates here, for [PacedEffect] and [rememberPacedLoop]: the Menus & pages
 * group's everywhere (provided at the root), a group's own where it's provided ([GroupMotion]), the Dice roll's round the dice, and the Cup shake's round the cup.
 */
val LocalFramePace = compositionLocalOf { FramePace.Unpaced }

/**
 * A [LaunchedEffect] whose animations run at [LocalFramePace] - for whatever it animates (a cup's lid, a banner's
 * slide) to keep its group's rate. Restarted when [keys] change, as a [LaunchedEffect] is.
 */
@Composable
@NonRestartableComposable
fun PacedEffect(vararg keys: Any?, block: suspend CoroutineScope.() -> Unit) {
    val pace = LocalFramePace.current
    LaunchedEffect(keys = keys) { pace.run(block) }
}

/**
 * What an `InfiniteTransition`'s `animateFloat` would give, [initialValue] to [targetValue] and round again by
 * [animationSpec], at [pace]'s frames. Like that, it asks for a frame only while it's composed, and it waits on the
 * window's infinite-animation policy (`ScreenIdle`). Read it in the draw phase.
 */
@Composable
fun rememberPacedLoop(
    initialValue: Float,
    targetValue: Float,
    animationSpec: InfiniteRepeatableSpec<Float>,
    pace: FramePace = LocalFramePace.current,
): State<Float> {
    val animatable = remember { Animatable(initialValue) }
    LaunchedEffect(animatable, pace) {
        pace.run {
            animatable.snapTo(initialValue)
            animatable.animateTo(targetValue, animationSpec)
        }
    }
    return animatable.asState()
}
