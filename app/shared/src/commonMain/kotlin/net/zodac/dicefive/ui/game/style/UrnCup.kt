package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * An urn's colours: [clay] its body ([light] where it's lit, [dark] at its edges), [paint] the
 * glaze its bands are painted in, and [interior] its dark inside.
 */
class UrnPalette(val clay: Color, val light: Color, val dark: Color, val paint: Color, val interior: Color)

// The urn's turned profile, top to bottom: (y, radius) on the tall cup grid - a flared lip, a
// narrow neck, broad shoulders, the belly tapering to a slim stem and a stepped foot.
private val UrnProfile = listOf(
    11f to 12f, 13f to 9.6f, 17f to 9.2f, 22f to 10.5f, 28f to 18f, 34f to 22f, 42f to 23f,
    50f to 21.5f, 58f to 17f, 65f to 11.5f, 69f to 7.5f, 71f to 7.2f,
)
private const val LIP_Y = 9f
private const val LIP_RADIUS = 13.5f
private const val FOOT_TOP_Y = 71.5f
private const val FOOT_BOTTOM_Y = 77f
private const val FOOT_RADIUS = 11.5f

// The painted bands: the Greek key round the shoulder, and the rays round the lower body.
private const val KEY_TOP = 29.5f
private const val KEY_BOTTOM = 35.5f
private const val RAYS_TOP = 54f
private const val RAYS_BOTTOM = 64f

/**
 * A classical urn: a turned clay body with a flared lip, two looped handles from neck to shoulder,
 * a Greek-key band painted round its shoulder and a ring of rays round its lower body - each wrapped
 * round the body, so they foreshorten towards its edges - on a stepped foot. It shakes and pours
 * like any other cup.
 */
class UrnDiceCupStyle(override val id: String, private val palette: UrnPalette) : DiceCupStyle, Swatched {
    override val swatch: Color = palette.clay

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        CupCanvas(rolling, tilted, modifier) {
            drawUrnShadow()
            drawFoot()
            val body = urnBody()
            drawPath(body, urnShading(23f))
            // Worked out here: the grid isn't reachable inside clipPath.
            val keyBand = band(KEY_TOP, KEY_BOTTOM)
            val key = greekKey()
            val raysBand = band(RAYS_TOP, RAYS_BOTTOM)
            val rays = rays()
            val lines = listOf(26.8f, 38f, 52f, 66f).map { y -> frontRing(y) }
            val lineWidth = gy(0.7f)
            clipPath(body) {
                drawPath(keyBand, palette.paint)
                drawPath(key, palette.clay, style = Stroke(width = lineWidth * 1.4f, cap = StrokeCap.Square, join = StrokeJoin.Miter))
                drawPath(rays, palette.paint)
                drawPath(raysBand, palette.paint, style = Stroke(width = lineWidth))
                for (line in lines) drawPath(line, palette.paint, style = Stroke(width = lineWidth))
            }
            // The body's shading again, over the paint, so the bands turn with the body.
            drawPath(body, Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = 0.35f),
                0.3f to Color.White.copy(alpha = 0.12f),
                0.6f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.4f),
                startX = gx(centreX - 23f),
                endX = gx(centreX + 23f),
            ))
            drawHandles()
            drawMouth()
        }
    }

    private fun CupDrawScope.urnShading(radius: Float): Brush = Brush.horizontalGradient(
        0f to palette.dark,
        0.3f to palette.light,
        0.58f to palette.clay,
        1f to palette.dark,
        startX = gx(centreX - radius),
        endX = gx(centreX + radius),
    )

    /** The radius of the body at [y], on a smooth curve through its profile's points. */
    private fun radiusAt(y: Float): Float {
        val i = UrnProfile.indexOfLast { it.first <= y }
        if (i < 0) return UrnProfile.first().second
        if (i >= UrnProfile.lastIndex) return UrnProfile.last().second
        fun r(k: Int) = UrnProfile[k.coerceIn(0, UrnProfile.lastIndex)].second
        val (y1, r1) = UrnProfile[i]
        val (y2, r2) = UrnProfile[i + 1]
        val t = (y - y1) / (y2 - y1)
        // Catmull-Rom, its tangents scaled to each span so uneven spacing doesn't kink it.
        val m1 = (r2 - r(i - 1)) / (y2 - UrnProfile[(i - 1).coerceAtLeast(0)].first) * (y2 - y1)
        val m2 = (r(i + 2) - r1) / (UrnProfile[(i + 2).coerceAtMost(UrnProfile.lastIndex)].first - y1) * (y2 - y1)
        val t2 = t * t
        val t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * r1 + (t3 - 2 * t2 + t) * m1 + (-2 * t3 + 3 * t2) * r2 + (t3 - t2) * m2
    }

    /** The body's outline: down its left side, round the front of its base, and back up its right. */
    private fun CupDrawScope.urnBody(): Path {
        val ys = UrnProfile.first().first.let { top -> (0..60).map { top + (UrnProfile.last().first - top) * it / 60f } }
        val left = ys.map { Offset(gx(centreX - radiusAt(it)), gy(it)) }
        val bottom = ys.last()
        val bottomRadius = radiusAt(bottom)
        return Path().apply {
            moveTo(left[0].x, left[0].y)
            for (p in left.drop(1)) lineTo(p.x, p.y)
            frontArcTo(this, bottomRadius, bottom)
            for (y in ys.reversed()) lineTo(gx(centreX + radiusAt(y)), gy(y))
            close()
        }
    }

    /** Adds the front half of the ring of [radius] at [y], left to right. */
    private fun CupDrawScope.frontArcTo(path: Path, radius: Float, y: Float) {
        for (step in 0..24) {
            val a = PI.toFloat() * step / 24f
            path.lineTo(gx(centreX - radius * cos(a)), gy(y + radius * CUP_VIEW_SQUASH * sin(a)))
        }
    }

    /** The front half of the ring round the body at [y]. */
    private fun CupDrawScope.frontRing(y: Float): Path = Path().apply {
        val r = radiusAt(y)
        moveTo(gx(centreX - r), gy(y))
        frontArcTo(this, r, y)
    }

    /** Where the point [angle] radians round the body from its front (negative to the left) at height [y] is drawn. */
    private fun CupDrawScope.onBody(rawAngle: Float, y: Float): Offset {
        // Anything painted round the back is pinned to the edge, where the body turns away.
        val angle = rawAngle.coerceIn(-PI.toFloat() / 2f, PI.toFloat() / 2f)
        val r = radiusAt(y)
        return Offset(gx(centreX + r * sin(angle)), gy(y + r * CUP_VIEW_SQUASH * cos(angle)))
    }

    /** The band round the body between [top] and [bottom], its front half. */
    private fun CupDrawScope.band(top: Float, bottom: Float): Path = Path().apply {
        val steps = 32
        for (i in 0..steps) {
            val p = onBody(-PI.toFloat() / 2f + PI.toFloat() * i / steps, top)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        for (i in steps downTo 0) {
            val p = onBody(-PI.toFloat() / 2f + PI.toFloat() * i / steps, bottom)
            lineTo(p.x, p.y)
        }
        close()
    }

    /**
     * The Greek key (a meander) wrapped round the shoulder band: one continuous line stepping round
     * and in, repeated all the way round - [KEY_UNITS] of them to the full turn.
     */
    private fun CupDrawScope.greekKey(): Path = Path().apply {
        val height = KEY_BOTTOM - KEY_TOP
        // One unit of the meander, as (fraction across the unit, fraction down the band).
        val unit = listOf(0f to 0.85f, 0f to 0.15f, 0.75f to 0.15f, 0.75f to 0.65f, 0.3f to 0.65f, 0.3f to 0.4f, 0.5f to 0.4f)
        val unitAngle = 2f * PI.toFloat() / KEY_UNITS
        fun at(k: Int, u: Float, v: Float) = onBody((k + u) * unitAngle, KEY_TOP + v * height)
        for (k in -KEY_UNITS / 4 - 1..KEY_UNITS / 4) {
            unit.forEachIndexed { i, (u, v) ->
                val p = at(k, u, v)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            // The base line running on to the next unit.
            val from = at(k, 0f, 0.85f)
            val to = at(k, 1f, 0.85f)
            moveTo(from.x, from.y)
            lineTo(to.x, to.y)
        }
    }

    /** The rays: tall painted triangles rising from the band's foot, round the lower body. */
    private fun CupDrawScope.rays(): Path = Path().apply {
        val unitAngle = 2f * PI.toFloat() / RAY_COUNT
        for (k in -RAY_COUNT / 4 - 1..RAY_COUNT / 4) {
            val a = k * unitAngle
            val left = onBody(a - unitAngle * 0.42f, RAYS_BOTTOM)
            val right = onBody(a + unitAngle * 0.42f, RAYS_BOTTOM)
            val tip = onBody(a, RAYS_TOP + 0.6f)
            moveTo(left.x, left.y)
            lineTo(tip.x, tip.y)
            lineTo(right.x, right.y)
            close()
        }
    }

    /** The stepped foot: a short disc wider than the stem, its top ring catching the light. */
    private fun CupDrawScope.drawFoot() {
        val foot = Path().apply {
            moveTo(gx(centreX - FOOT_RADIUS + 1f), gy(FOOT_TOP_Y))
            lineTo(gx(centreX - FOOT_RADIUS), gy(FOOT_BOTTOM_Y))
            frontArcTo(this, FOOT_RADIUS, FOOT_BOTTOM_Y)
            lineTo(gx(centreX + FOOT_RADIUS - 1f), gy(FOOT_TOP_Y))
            close()
        }
        drawPath(foot, urnShading(FOOT_RADIUS))
        val top = Rect(
            Offset(gx(centreX - FOOT_RADIUS + 1f), gy(FOOT_TOP_Y - (FOOT_RADIUS - 1f) * CUP_VIEW_SQUASH)),
            Size(gx(2f * (FOOT_RADIUS - 1f)), gy(2f * (FOOT_RADIUS - 1f) * CUP_VIEW_SQUASH)),
        )
        drawOval(lerp(palette.clay, palette.light, 0.5f), topLeft = top.topLeft, size = top.size)
        drawPath(Path().apply { moveTo(gx(centreX - FOOT_RADIUS), gy(FOOT_BOTTOM_Y - 1.5f)); frontArcTo(this, FOOT_RADIUS, FOOT_BOTTOM_Y - 1.5f) }, palette.paint, style = Stroke(gy(0.7f)))
    }

    /** Its shadow on the table, the shape of its round foot. */
    private fun CupDrawScope.drawUrnShadow() {
        val r = FOOT_RADIUS + 3f
        drawOval(Color.Black.copy(alpha = 0.4f), topLeft = Offset(gx(centreX - r), gy(FOOT_BOTTOM_Y + 1f - r * CUP_VIEW_SQUASH)), size = Size(gx(2f * r), gy(2f * r * CUP_VIEW_SQUASH)))
    }

    /**
     * The two handles: each a thick loop of clay leaving the neck, arching out past the shoulder and
     * coming down to meet it - joined at both ends with a little swelling, so they're visibly fixed on.
     */
    private fun CupDrawScope.drawHandles() {
        for (side in listOf(-1f, 1f)) {
            fun x(v: Float) = gx(centreX + side * v)
            val loop = Path().apply {
                moveTo(x(9.2f), gy(17f))
                cubicTo(x(17f), gy(12.5f), x(24f), gy(14f), x(23f), gy(20f))
                quadraticTo(x(22.5f), gy(25f), x(19f), gy(29f))
            }
            drawPath(loop, palette.dark, style = Stroke(width = gy(3.6f), cap = StrokeCap.Round))
            drawPath(loop, if (side < 0) palette.light else palette.clay, style = Stroke(width = gy(2f), cap = StrokeCap.Round))
            // Where it's luted on to the body.
            drawCircle(palette.dark, gy(1.7f), Offset(x(9.6f), gy(17f)))
            drawCircle(palette.dark, gy(1.9f), Offset(x(19f), gy(29f)))
            drawCircle(palette.clay, gy(1.1f), Offset(x(19f), gy(29f)))
        }
    }

    /** The flared lip and the open mouth inside it: the lit far wall, falling into the dark. */
    private fun CupDrawScope.drawMouth() {
        // The lip's flare, from the neck up to the rim.
        val flare = Path().apply {
            moveTo(gx(centreX - 9.6f), gy(13f))
            lineTo(gx(centreX - LIP_RADIUS), gy(LIP_Y))
            for (step in 0..24) {
                val a = PI.toFloat() * step / 24f
                lineTo(gx(centreX - LIP_RADIUS * cos(a)), gy(LIP_Y + LIP_RADIUS * CUP_VIEW_SQUASH * sin(a)))
            }
            lineTo(gx(centreX + 9.6f), gy(13f))
            for (step in 24 downTo 0) {
                val a = PI.toFloat() * step / 24f
                lineTo(gx(centreX - 9.6f * cos(a)), gy(13f + 9.6f * CUP_VIEW_SQUASH * sin(a)))
            }
            close()
        }
        drawPath(flare, urnShading(LIP_RADIUS))
        val mouth = Rect(Offset(gx(centreX - LIP_RADIUS), gy(LIP_Y - LIP_RADIUS * CUP_VIEW_SQUASH)), Size(gx(2f * LIP_RADIUS), gy(2f * LIP_RADIUS * CUP_VIEW_SQUASH)))
        val depth = gy(4f)
        clipPath(Path().apply { addOval(mouth) }) {
            drawOval(palette.clay, topLeft = mouth.topLeft, size = mouth.size)
            translate(top = depth) { drawOval(palette.interior, topLeft = mouth.topLeft, size = mouth.size) }
        }
        drawOval(palette.light, topLeft = mouth.topLeft, size = mouth.size, style = Stroke(width = gy(1.2f)))
        drawPath(Path().apply { moveTo(gx(centreX - LIP_RADIUS), gy(LIP_Y)); frontArcTo(this, LIP_RADIUS, LIP_Y) }, palette.paint, style = Stroke(gy(0.8f)))
    }
}

private const val KEY_UNITS = 14
private const val RAY_COUNT = 18
