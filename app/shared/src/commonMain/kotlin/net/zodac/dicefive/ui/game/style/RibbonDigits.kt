package net.zodac.dicefive.ui.game.style

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/** One length of ribbon: its centreline [points] (a unit box, y down, curved through smoothly), and where along it (0-1) it [twists] over. */
private class RibbonLength(val twists: List<Float>, val points: List<Offset>)

private fun o(x: Float, y: Float) = Offset(x, y)

/**
 * The digits 1-6 as flowing ribbon: cursive, every bend a curve, curling in and out at the ends -
 * which are always left free, never lying across another part of the ribbon, since their swallowtail
 * cuts would cut through it too.
 */
private val RIBBON_DIGITS: Map<Int, List<RibbonLength>> = mapOf(
    1 to listOf(
        RibbonLength(listOf(0.55f), listOf(o(0.2f, 0.43f), o(0.32f, 0.3f), o(0.46f, 0.2f), o(0.57f, 0.13f), o(0.57f, 0.3f), o(0.54f, 0.55f),
            o(0.51f, 0.74f), o(0.55f, 0.86f), o(0.67f, 0.88f), o(0.8f, 0.81f))),
    ),
    2 to listOf(
        RibbonLength(listOf(0.42f), listOf(o(0.2f, 0.34f), o(0.29f, 0.2f), o(0.46f, 0.12f), o(0.63f, 0.15f), o(0.72f, 0.29f), o(0.65f, 0.47f),
            o(0.47f, 0.64f), o(0.3f, 0.78f), o(0.24f, 0.87f), o(0.36f, 0.88f), o(0.52f, 0.81f), o(0.68f, 0.86f), o(0.82f, 0.8f))),
    ),
    3 to listOf(
        RibbonLength(listOf(0.28f, 0.74f), listOf(o(0.2f, 0.25f), o(0.35f, 0.13f), o(0.55f, 0.11f), o(0.69f, 0.21f), o(0.65f, 0.36f), o(0.51f, 0.45f),
            o(0.42f, 0.48f), o(0.53f, 0.5f), o(0.68f, 0.57f), o(0.74f, 0.71f), o(0.65f, 0.85f), o(0.46f, 0.9f),
            o(0.29f, 0.85f), o(0.19f, 0.74f))),
    ),
    4 to listOf(
        RibbonLength(listOf(0.45f), listOf(o(0.56f, 0.11f), o(0.44f, 0.3f), o(0.32f, 0.48f), o(0.23f, 0.63f), o(0.4f, 0.65f), o(0.6f, 0.62f), o(0.83f, 0.58f))),
        RibbonLength(listOf(0.5f), listOf(o(0.69f, 0.28f), o(0.67f, 0.5f), o(0.64f, 0.7f), o(0.63f, 0.84f), o(0.69f, 0.91f), o(0.79f, 0.88f))),
    ),
    5 to listOf(
        RibbonLength(listOf(0.72f), listOf(o(0.36f, 0.13f), o(0.34f, 0.3f), o(0.33f, 0.45f), o(0.48f, 0.41f), o(0.65f, 0.47f), o(0.74f, 0.62f),
            o(0.69f, 0.79f), o(0.52f, 0.89f), o(0.33f, 0.86f), o(0.19f, 0.76f))),
        RibbonLength(emptyList(), listOf(o(0.48f, 0.15f), o(0.6f, 0.14f), o(0.7f, 0.11f), o(0.81f, 0.14f))),
    ),
    6 to listOf(
        RibbonLength(listOf(0.3f), listOf(o(0.75f, 0.16f), o(0.6f, 0.11f), o(0.44f, 0.17f), o(0.33f, 0.33f), o(0.28f, 0.55f), o(0.33f, 0.75f),
            o(0.5f, 0.88f), o(0.67f, 0.82f), o(0.73f, 0.65f), o(0.64f, 0.51f), o(0.5f, 0.49f), o(0.42f, 0.55f))),
    ),
)

// How wide the ribbon is, as a fraction of the die, and how long a twist takes along it.
private const val RIBBON_WIDTH = 0.12f
private const val RIBBON_TWIST_SPAN = 0.07f

// Light from the top left, as for the other dice.
private val RIBBON_LIGHT = Offset(-0.6f, -0.8f)

/**
 * [value] laid out in flowing ribbon of [colour], filling the die. The ribbon is built along its
 * centreline in short slices, each shaded by which way that bit of satin faces the light, narrowing
 * edge-on through each twist and showing its duller back after one, with a soft shadow under each
 * stretch and swallowtails cut into its ends. Hundreds of slices - paint it through [drawCachedSurface].
 */
internal fun DrawScope.paintRibbonDigit(value: Int, colour: Color) {
    val side = size.minDimension
    val width = side * RIBBON_WIDTH
    val step = side * 0.008f
    val shadow = Offset(0.7.dp.toPx(), 1.1.dp.toPx())
    for (length in RIBBON_DIGITS.getValue(value.coerceIn(1, 6))) {
        val path = smoothPath(length.points.map { it * side })
        val measure = PathMeasure().apply { setPath(path, false) }
        val total = measure.length
        val count = (total / step).toInt().coerceAtLeast(2)
        val centres = ArrayList<Offset>(count + 1)
        val normals = ArrayList<Offset>(count + 1)
        val halfWidths = FloatArray(count + 1)
        val backs = BooleanArray(count + 1)
        for (i in 0..count) {
            val d = total * i / count
            val f = d / total
            centres += measure.getPosition(d)
            val t = measure.getTangent(d)
            normals += Offset(-t.y, t.x)
            // Through a twist the ribbon turns edge-on, narrowing nearly to nothing and back.
            val twist = length.twists.minOfOrNull { abs(f - it) } ?: 1f
            val u = (twist / RIBBON_TWIST_SPAN).coerceAtMost(1f)
            halfWidths[i] = width / 2f * (0.14f + 0.86f * (1f - cos(PI.toFloat() * u)) / 2f)
            backs[i] = length.twists.count { f > it } % 2 == 1
        }
        fun slice(i: Int, scale: Float, shift: Float = 0f): Path {
            val a = centres[i] + normals[i] * (halfWidths[i] * shift)
            val b = centres[i + 1] + normals[i + 1] * (halfWidths[i + 1] * shift)
            // A hair longer than its step, so neighbouring slices overlap rather than leave a seam.
            val reach = (b - a) * 0.35f
            val ha = halfWidths[i] * scale
            val hb = halfWidths[i + 1] * scale
            return polygonPath(listOf(a - reach + normals[i] * ha, b + reach + normals[i + 1] * hb, b + reach - normals[i + 1] * hb, a - reach - normals[i] * ha))
        }
        val deep = lerp(colour, Color.Black, 0.38f)
        val sheen = lerp(colour, Color.White, 0.6f)
        // In stretches, each with its shadow under it, so where the ribbon crosses itself the upper
        // stretch casts a shadow on the lower.
        val stretch = (count / 4).coerceAtLeast(1)
        var from = 0
        while (from < count) {
            val to = minOf(from + stretch, count)
            for (i in from until to) drawPath(slice(i, 1f).apply { translate(shadow) }, Color.Black.copy(alpha = 0.08f))
            // Each layer across the whole stretch before the next, so one slice's dark edge never
            // lands on its neighbour's satin.
            val bodies = (from until to).map { i ->
                val narrow = halfWidths[i] / (width / 2f)
                val n = normals[i]
                val lit = ((n.x * RIBBON_LIGHT.x + n.y * RIBBON_LIGHT.y) * 0.5f + 0.5f).coerceIn(0f, 1f)
                var body = lerp(lerp(colour, Color.Black, 0.18f), lerp(colour, Color.White, 0.22f), lit)
                if (backs[i]) body = lerp(body, deep, 0.25f)
                // Edge-on through a twist, it darkens.
                Triple(lerp(deep, body, 0.45f + 0.55f * narrow), narrow, lit)
            }
            for (i in from until to) drawPath(slice(i, 1f), deep)
            for (i in from until to) drawPath(slice(i, 0.78f), bodies[i - from].first)
            for (i in from until to) {
                val (_, narrow, lit) = bodies[i - from]
                if (!backs[i]) drawPath(slice(i, 0.22f, shift = -0.25f), sheen.copy(alpha = 0.45f * narrow * (0.4f + 0.6f * lit)))
            }
            from = to
        }
        // Swallowtails: a notch cut out of each free end.
        for ((at, back) in listOf(0 to 1, count to count - 1)) {
            val end = centres[at]
            val out = (end - centres[back]).let { it / it.getDistance() }
            val across = normals[at] * (halfWidths[at] * 1.05f)
            val notch = polygonPath(listOf(end + across + out * step, end - out * (width * 0.5f), end - across + out * step))
            drawPath(notch, Color.Black, blendMode = BlendMode.Clear)
        }
    }
}
