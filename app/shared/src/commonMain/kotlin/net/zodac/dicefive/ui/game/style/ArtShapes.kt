package net.zodac.dicefive.ui.game.style

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp

// Small drawing helpers shared by the illustrated dice (cake, garden, mahjong, poker...): art is
// authored in a unit space round its own centre and placed with [inUnit].

/**
 * Draws [block] in a unit space: (0, 0) at [centre], 1 unit [radius] pixels, turned [degrees]
 * clockwise - so a piece of art is authored once, at its own scale, and placed anywhere.
 */
internal inline fun DrawScope.inUnit(centre: Offset, radius: Float, degrees: Float = 0f, crossinline block: DrawScope.() -> Unit) =
    withTransform({
        translate(centre.x, centre.y)
        if (degrees != 0f) rotate(degrees, Offset.Zero)
        scale(radius, radius, Offset.Zero)
    }) { block() }

/** A closed polygon through [points]. */
internal fun polygonPath(points: List<Offset>): Path = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (p in points.drop(1)) lineTo(p.x, p.y)
    close()
}

/**
 * A smooth curve through every one of [points] (a Catmull-Rom spline, as cubics), closed back to the
 * first when [closed].
 */
internal fun smoothPath(points: List<Offset>, closed: Boolean = false): Path = Path().apply {
    val n = points.size
    fun at(i: Int): Offset = if (closed) points[(i + n) % n] else points[i.coerceIn(0, n - 1)]
    moveTo(points[0].x, points[0].y)
    val segments = if (closed) n else n - 1
    for (i in 0 until segments) {
        val p0 = at(i - 1)
        val p1 = at(i)
        val p2 = at(i + 1)
        val p3 = at(i + 2)
        val c1 = p1 + (p2 - p0) / 6f
        val c2 = p2 - (p3 - p1) / 6f
        cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    if (closed) close()
}

/**
 * Three shades of one colour - [deep] for shadow and outline, [base], [light] for the lit side -
 * which the illustrated dice draw each object in, so a recoloured die ([DiceStyle.recoloured]) can
 * draw every object in shades of its one pip colour instead.
 */
class Shades(val deep: Color, val base: Color, val light: Color) {
    companion object {
        /** Shades of [colour] alone, for a recoloured die. */
        fun of(colour: Color): Shades = Shades(lerp(colour, Color.Black, 0.5f), colour, lerp(colour, Color.White, 0.45f))
    }
}

/**
 * Draws [block] [scale] times as large about [pivot] - how a 3D cup drawn on its grid is enlarged
 * as a whole, strokes and all.
 */
internal inline fun DrawScope.scaledAbout(scale: Float, pivot: Offset, crossinline block: DrawScope.() -> Unit) =
    withTransform({ scale(scale, scale, pivot) }) { block() }

/**
 * Draws [block] with a [scaledAbout] of [scale] about [pivot] undone - for stamping an image that
 * was already painted at that scale (see [drawCachedSurface]), so it isn't enlarged twice.
 */
internal inline fun DrawScope.unscaledAbout(scale: Float, pivot: Offset, crossinline block: DrawScope.() -> Unit) =
    withTransform({ scale(1f / scale, 1f / scale, pivot) }) { block() }
