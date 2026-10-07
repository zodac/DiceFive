package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

/**
 * One run of a digit's centreline: through [points] (in a unit box, y down), as a smooth curve when
 * [smooth], or as straight lines with sharp folds between them otherwise.
 */
internal class DigitRun(val smooth: Boolean, val points: List<Offset>)

/**
 * The digits 1-6 as single strokes - one continuous line each, the way a ribbon is laid or a neon
 * tube bent - every stroke a sequence of [DigitRun]s, each starting where the last ended. A digit
 * that needs to lift (none of these do) would take a second stroke.
 */
internal val STROKE_DIGITS: Map<Int, List<List<DigitRun>>> = mapOf(
    1 to listOf(
        listOf(
            DigitRun(false, listOf(Offset(0.36f, 0.3f), Offset(0.54f, 0.15f), Offset(0.54f, 0.86f))),
        ),
    ),
    2 to listOf(
        listOf(
            DigitRun(
                true,
                listOf(
                    Offset(0.3f, 0.32f), Offset(0.38f, 0.19f), Offset(0.52f, 0.14f), Offset(0.66f, 0.2f),
                    Offset(0.7f, 0.34f), Offset(0.62f, 0.5f), Offset(0.3f, 0.85f),
                ),
            ),
            DigitRun(false, listOf(Offset(0.3f, 0.85f), Offset(0.72f, 0.85f))),
        ),
    ),
    3 to listOf(
        listOf(
            DigitRun(
                true,
                listOf(
                    Offset(0.31f, 0.22f), Offset(0.45f, 0.14f), Offset(0.61f, 0.17f), Offset(0.67f, 0.3f),
                    Offset(0.6f, 0.43f), Offset(0.46f, 0.48f),
                ),
            ),
            DigitRun(
                true,
                listOf(
                    Offset(0.46f, 0.48f), Offset(0.63f, 0.53f), Offset(0.71f, 0.67f), Offset(0.64f, 0.81f),
                    Offset(0.48f, 0.87f), Offset(0.3f, 0.8f),
                ),
            ),
        ),
    ),
    4 to listOf(
        listOf(
            DigitRun(false, listOf(Offset(0.6f, 0.87f), Offset(0.6f, 0.14f), Offset(0.27f, 0.63f), Offset(0.76f, 0.63f))),
        ),
    ),
    5 to listOf(
        listOf(
            DigitRun(false, listOf(Offset(0.69f, 0.16f), Offset(0.37f, 0.16f), Offset(0.34f, 0.46f))),
            DigitRun(
                true,
                listOf(
                    Offset(0.34f, 0.46f), Offset(0.49f, 0.41f), Offset(0.65f, 0.46f), Offset(0.71f, 0.62f),
                    Offset(0.65f, 0.79f), Offset(0.49f, 0.87f), Offset(0.31f, 0.8f),
                ),
            ),
        ),
    ),
    6 to listOf(
        listOf(
            DigitRun(
                true,
                listOf(
                    Offset(0.66f, 0.17f), Offset(0.5f, 0.17f), Offset(0.37f, 0.3f), Offset(0.31f, 0.52f),
                    Offset(0.34f, 0.74f), Offset(0.49f, 0.87f), Offset(0.65f, 0.8f), Offset(0.7f, 0.64f),
                    Offset(0.62f, 0.5f), Offset(0.5f, 0.48f), Offset(0.41f, 0.53f),
                ),
            ),
        ),
    ),
)

/**
 * [value]'s strokes as paths, the unit box scaled to [side] and moved to [origin]. Each comes with
 * the direction its two ends point out of the stroke (unit vectors), for art that finishes them off -
 * a ribbon's cut tails.
 */
internal fun strokeDigitPaths(value: Int, origin: Offset, side: Float): List<StrokeDigitPath> =
    STROKE_DIGITS.getValue(value.coerceIn(1, 6)).map { runs ->
        val scaled = runs.map { run -> DigitRun(run.smooth, run.points.map { origin + it * side }) }
        // One continuous path, so the stroke folds (joins) where runs meet rather than overlapping two ends.
        val path = Path()
        path.moveTo(scaled[0].points[0].x, scaled[0].points[0].y)
        for (run in scaled) {
            if (run.smooth) appendSmooth(path, run.points) else for (p in run.points.drop(1)) path.lineTo(p.x, p.y)
        }
        val first = scaled.first().points
        val last = scaled.last().points
        StrokeDigitPath(
            path = path,
            start = first[0],
            startOut = (first[0] - first[1]).let { it / it.getDistance() },
            end = last.last(),
            endOut = (last.last() - last[last.size - 2]).let { it / it.getDistance() },
            runs = scaled,
        )
    }

/** Continues [path] from [points]' first (where it already is) through the rest, as [smoothPath] curves them. */
private fun appendSmooth(path: Path, points: List<Offset>) {
    val n = points.size
    fun at(i: Int): Offset = points[i.coerceIn(0, n - 1)]
    for (i in 0 until n - 1) {
        val c1 = at(i) + (at(i + 1) - at(i - 1)) / 6f
        val c2 = at(i + 1) - (at(i + 2) - at(i)) / 6f
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, at(i + 1).x, at(i + 1).y)
    }
}

/** One stroke of a digit, laid out - see [strokeDigitPaths]. */
internal class StrokeDigitPath(
    val path: Path,
    val start: Offset,
    val startOut: Offset,
    val end: Offset,
    val endOut: Offset,
    val runs: List<DigitRun>,
)

/**
 * Whether the art below should hold still: [rememberArtSeconds] stops updating, so nothing that reads it is
 * redrawn. For dice faces tumbling through a toss, where nobody can see them move - see [TossedCube].
 */
internal val LocalArtFrozen = compositionLocalOf { false }

// The last tick of any art clock, for a clock that starts later (a die's face made mid-roll) to start from.
private var latestArtSeconds = 0f

/**
 * The time in seconds, ticking every frame for as long as it's composed - for art that moves on its
 * own (a neon tube's pulse, glitter's sparkles). Read it inside a draw block, not during composition,
 * so a tick only redraws. Stands still at 0 under reduced motion (see [TwinkleClock]), and holds still
 * at the time it was made while [LocalArtFrozen] is true.
 */
@Composable
internal fun rememberArtSeconds(): State<Float> {
    val frozen = rememberUpdatedState(LocalArtFrozen.current)
    val seconds = remember { mutableFloatStateOf(latestArtSeconds) }
    TwinkleClock {
        latestArtSeconds = it
        if (!frozen.value) seconds.floatValue = it
    }
    return seconds
}
