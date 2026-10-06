package net.zodac.dicefive.ui.game.style

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import net.zodac.dicefive.model.PlayerColour
import net.zodac.dicefive.ui.theme.color

// The frames round the player whose turn it is, in the row of tabs above the board (PlayerHeaderBar).
// Every one is drawn in that player's own colour - shades of it, never a colour of its own - so up to
// four players' frames all read as theirs. They're drawn behind the tab's name and score, in dp
// (see inDp), and stretch to any tab: a narrow one of four, a wide one of two. Nothing here moves,
// and a frame is only redrawn when its tab is, so there's nothing to cache.

/** The colour a frame's Styles tile shows it in: player 1's default. */
val ScoreFramePreviewColour: Color get() = PlayerColour.defaultFor(0).color

/** The tab's own corner radius, which a frame that follows its outline rounds to. */
private const val TAB_CORNER = 10f

/** A frame defined by one drawing function, in dp - see [inDp]. */
private class DrawnScoreFrame(
    override val id: String,
    private val draw: DrawScope.(w: Float, h: Float, colour: Color) -> Unit,
) : ScoreFrame {
    override fun DrawScope.drawFrame(color: Color) = inDp { w, h -> draw(w, h, color) }
}

/** Draws [block] with one unit to the dp, handing it the tab's width and height in dp. */
private inline fun DrawScope.inDp(crossinline block: DrawScope.(w: Float, h: Float) -> Unit) {
    val w = size.width / density
    val h = size.height / density
    val scale = density
    withTransform({ scale(scale, scale, Offset.Zero) }) { block(w, h) }
}

/** Draws [block] once for each corner: as authored, for the top left, then mirrored into the other three. */
private inline fun DrawScope.eachCorner(w: Float, h: Float, crossinline block: DrawScope.() -> Unit) {
    val centre = Offset(w / 2, h / 2)
    block()
    withTransform({ scale(-1f, 1f, centre) }) { block() }
    withTransform({ scale(1f, -1f, centre) }) { block() }
    withTransform({ scale(-1f, -1f, centre) }) { block() }
}

/** Draws [block] as authored and mirrored left to right, for art that's the same on both sides. */
private inline fun DrawScope.bothSides(w: Float, crossinline block: DrawScope.() -> Unit) {
    block()
    withTransform({ scale(-1f, 1f, Offset(w / 2, 0f)) }) { block() }
}

/** Draws [block] with its origin at [at], turned [degrees] clockwise. */
private inline fun DrawScope.placed(at: Offset, degrees: Float, crossinline block: DrawScope.() -> Unit) =
    withTransform({
        translate(at.x, at.y)
        rotate(degrees, Offset.Zero)
    }) { block() }

private fun Color.lighter(fraction: Float) = lerp(this, Color.White, fraction)
private fun Color.darker(fraction: Float) = lerp(this, Color.Black, fraction)

/** The tab's own rounded outline, [inset] in from its edges, its corners rounded to match. */
private fun DrawScope.drawTabOutline(w: Float, h: Float, colour: Color, width: Float, inset: Float = width / 2) {
    drawRoundRect(
        color = colour,
        topLeft = Offset(inset, inset),
        size = Size(w - 2 * inset, h - 2 * inset),
        cornerRadius = CornerRadius(max(TAB_CORNER - inset, 1f)),
        style = Stroke(width),
    )
}

/**
 * A filled stroke along [points] whose width follows [widthAt] (0 at the start to 1 at the end) - the
 * tapering strokes of the tribal frames, which a plain stroke can't draw.
 */
private fun taperedPath(points: List<Offset>, widthAt: (Float) -> Float): Path {
    val n = points.size
    val left = ArrayList<Offset>(n)
    val right = ArrayList<Offset>(n)
    for (i in 0 until n) {
        val d = points[min(i + 1, n - 1)] - points[max(i - 1, 0)]
        val length = d.getDistance().takeIf { it > 0f } ?: 1f
        val normal = Offset(-d.y / length, d.x / length) * (widthAt(i / (n - 1f)) / 2)
        left += points[i] + normal
        right += points[i] - normal
    }
    return polygonPath(left + right.asReversed())
}

/** [count] + 1 points along [at], from 0 to 1. */
private inline fun sampled(count: Int, at: (Float) -> Offset): List<Offset> = List(count + 1) { at(it / count.toFloat()) }

/** A leaf [length] long and [width] wide, from its stalk at the origin out along +x. */
private fun leafPath(length: Float, width: Float): Path = Path().apply {
    moveTo(0f, 0f)
    quadraticTo(length * 0.45f, -width, length, 0f)
    quadraticTo(length * 0.45f, width, 0f, 0f)
    close()
}

// ---------------------------------------------------------------------------------------------
// Classic: the plain ring the active tab has always had.

/** The plain coloured ring round the tab - what the active player's tab had before there were frames. */
val ClassicScoreFrame: ScoreFrame = DrawnScoreFrame("classic") { w, h, colour ->
    drawTabOutline(w, h, colour.copy(alpha = 0.85f), width = 1.5f)
}

// ---------------------------------------------------------------------------------------------
// Floral.

/** A rose seen from above, [radius] across from its centre at the origin: petals round a spiralled heart. */
private fun DrawScope.drawRose(colour: Color, radius: Float) {
    val outer = colour.darker(0.35f)
    for (i in 0 until 5) {
        val angle = i * 2 * PI.toFloat() / 5 - PI.toFloat() / 2
        drawCircle(outer, radius * 0.5f, Offset(cos(angle), sin(angle)) * (radius * 0.48f))
    }
    for (i in 0 until 5) {
        val angle = (i + 0.5f) * 2 * PI.toFloat() / 5 - PI.toFloat() / 2
        drawCircle(colour, radius * 0.4f, Offset(cos(angle), sin(angle)) * (radius * 0.32f))
    }
    drawCircle(colour.lighter(0.25f), radius * 0.42f, Offset.Zero)
    val spiral = sampled(24) { t ->
        val angle = t * 3.2f * PI.toFloat()
        Offset(cos(angle), sin(angle)) * (radius * 0.36f * t)
    }
    drawPath(smoothPath(spiral), colour.darker(0.45f), style = Stroke(0.6f, cap = StrokeCap.Round))
}

/** A leaf with its midrib, in [colour], from its stalk at the origin out along +x. */
private fun DrawScope.drawLeaf(colour: Color, length: Float, width: Float, vein: Boolean = true) {
    drawPath(leafPath(length, width), colour)
    if (vein) drawLine(colour.darker(0.4f), Offset(length * 0.1f, 0f), Offset(length * 0.8f, 0f), strokeWidth = 0.4f)
}

private val RoseScoreFrame: ScoreFrame = DrawnScoreFrame("rose") { w, h, colour ->
    drawTabOutline(w, h, colour.copy(alpha = 0.7f), width = 1.1f)
    val leafColour = colour.darker(0.45f)
    val rose: DrawScope.() -> Unit = {
        placed(Offset(7f, 7f), 0f) {
            placed(Offset(5f, -5.5f), -6f) { drawLeaf(leafColour, 11f, 3.2f) }
            placed(Offset(-5.5f, 5f), 96f) { drawLeaf(leafColour, 11f, 3.2f) }
            drawRose(colour, 7.5f)
        }
    }
    val bud: DrawScope.() -> Unit = {
        placed(Offset(w - 5f, 5f), 0f) {
            placed(Offset(-1f, 1f), 165f) { drawLeaf(leafColour, 7f, 2.2f, vein = false) }
            placed(Offset(-1f, 1f), 105f) { drawLeaf(leafColour, 7f, 2.2f, vein = false) }
            drawCircle(colour.darker(0.35f), 3.4f, Offset.Zero)
            drawCircle(colour, 2.3f, Offset(-0.4f, 0.4f))
        }
    }
    rose()
    bud()
    withTransform({ scale(-1f, -1f, Offset(w / 2, h / 2)) }) {
        rose()
        bud()
    }
}

/** A daisy, [radius] across from its centre at the origin. */
private fun DrawScope.drawDaisy(colour: Color, radius: Float, turn: Float = 0f) {
    val petal = colour.lighter(0.55f)
    val edge = colour.darker(0.2f)
    for (i in 0 until 9) {
        placed(Offset.Zero, turn + i * 40f) {
            drawOval(petal, Offset(radius * 0.25f, -radius * 0.2f), Size(radius * 0.8f, radius * 0.4f))
            drawOval(edge, Offset(radius * 0.25f, -radius * 0.2f), Size(radius * 0.8f, radius * 0.4f), style = Stroke(0.35f))
        }
    }
    drawCircle(colour.darker(0.15f), radius * 0.36f, Offset.Zero)
    drawCircle(colour.lighter(0.3f), radius * 0.16f, Offset(-radius * 0.08f, -radius * 0.08f))
}

private val DaisyScoreFrame: ScoreFrame = DrawnScoreFrame("daisy") { w, h, colour ->
    drawTabOutline(w, h, colour.copy(alpha = 0.5f), width = 1f)
    eachCorner(w, h) {
        placed(Offset(9.5f, 1.5f), 0f) { drawLeaf(colour.darker(0.45f), 5.5f, 1.7f, vein = false) }
        placed(Offset(1.5f, 9.5f), 90f) { drawLeaf(colour.darker(0.45f), 5.5f, 1.7f, vein = false) }
        placed(Offset(5f, 5f), 0f) { drawDaisy(colour, 5.5f, turn = 10f) }
    }
}

private val VineScoreFrame: ScoreFrame = DrawnScoreFrame("vine") { w, h, colour ->
    drawTabOutline(w, h, colour.copy(alpha = 0.35f), width = 0.8f)
    val stem = colour.darker(0.25f)
    val leaf = colour.darker(0.4f)
    // A wavy stem along the top and bottom edges, a leaf at every crest, between the corners' blossoms.
    for (bottom in listOf(false, true)) {
        withTransform({ if (bottom) scale(1f, -1f, Offset(w / 2, h / 2)) }) {
            val start = 9f
            val end = w - 9f
            val wave = 12f
            val waves = max(1, ((end - start) / wave).toInt())
            val step = (end - start) / waves
            val points = sampled(waves * 12) { t -> Offset(start + t * (end - start), 2.6f + 1.4f * sin(t * waves * 2 * PI.toFloat())) }
            drawPath(smoothPath(points), stem, style = Stroke(0.8f, cap = StrokeCap.Round))
            for (i in 0 until waves * 2) {
                val x = start + (i + 0.5f) * step / 2
                val up = i % 2 == 0
                placed(Offset(x, if (up) 1.6f else 3.6f), if (up) -35f else 35f) { drawLeaf(leaf, 4.2f, 1.6f, vein = false) }
            }
        }
    }
    eachCorner(w, h) {
        placed(Offset(4.5f, 4.5f), 0f) {
            for (i in 0 until 5) {
                val angle = i * 72f * PI.toFloat() / 180 - PI.toFloat() / 2
                drawCircle(colour.lighter(0.15f), 1.6f, Offset(cos(angle), sin(angle)) * 1.7f)
            }
            drawCircle(colour.darker(0.35f), 1f, Offset.Zero)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Art Deco.

/** A faint wash of the player's colour, strongest at the top, filling the tab's outline. */
private fun DrawScope.drawDecoWash(w: Float, h: Float, colour: Color) {
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(colour.copy(alpha = 0.16f), colour.copy(alpha = 0.03f)), startY = 0f, endY = h),
        size = Size(w, h),
        cornerRadius = CornerRadius(TAB_CORNER),
    )
}

private val DecoFanScoreFrame: ScoreFrame = DrawnScoreFrame("deco_fan") { w, h, colour ->
    drawDecoWash(w, h, colour)
    drawTabOutline(w, h, colour, width = 1.2f)
    drawTabOutline(w, h, colour.copy(alpha = 0.7f), width = 0.6f, inset = 3f)
    eachCorner(w, h) {
        // A quarter sunburst fanning out of the inner outline's corner.
        val hub = Offset(3f, 3f)
        val radius = 9.5f
        for (i in 0..4) {
            val angle = (i / 4f) * PI.toFloat() / 2
            drawLine(colour.copy(alpha = 0.85f), hub + Offset(cos(angle), sin(angle)) * 3.5f, hub + Offset(cos(angle), sin(angle)) * radius, strokeWidth = 0.6f)
        }
        drawArc(colour, 0f, 90f, useCenter = false, topLeft = hub - Offset(radius, radius), size = Size(radius * 2, radius * 2), style = Stroke(0.8f))
        drawArc(colour, 0f, 90f, useCenter = true, topLeft = hub - Offset(3.5f, 3.5f), size = Size(7f, 7f))
    }
    // A small diamond on the outline, top and bottom centre.
    for (y in listOf(0.6f, h - 0.6f)) {
        drawPath(polygonPath(listOf(Offset(w / 2, y - 2.4f), Offset(w / 2 + 2.4f, y), Offset(w / 2, y + 2.4f), Offset(w / 2 - 2.4f, y))), colour)
    }
}

/** The tab's outline with stepped corners, [inset] in, each corner cut back in two steps of [step]. */
private fun steppedOutline(w: Float, h: Float, inset: Float, step: Float): Path {
    val l = inset
    val t = inset
    val r = w - inset
    val b = h - inset
    return polygonPath(
        listOf(
            Offset(l + 2 * step, t), Offset(r - 2 * step, t), Offset(r - 2 * step, t + step), Offset(r - step, t + step),
            Offset(r - step, t + 2 * step), Offset(r, t + 2 * step), Offset(r, b - 2 * step), Offset(r - step, b - 2 * step),
            Offset(r - step, b - step), Offset(r - 2 * step, b - step), Offset(r - 2 * step, b), Offset(l + 2 * step, b),
            Offset(l + 2 * step, b - step), Offset(l + step, b - step), Offset(l + step, b - 2 * step), Offset(l, b - 2 * step),
            Offset(l, t + 2 * step), Offset(l + step, t + 2 * step), Offset(l + step, t + step), Offset(l + 2 * step, t + step),
        ),
    )
}

private val DecoSteppedScoreFrame: ScoreFrame = DrawnScoreFrame("deco_stepped") { w, h, colour ->
    val outer = steppedOutline(w, h, 0.6f, 3f)
    drawPath(outer, Brush.verticalGradient(listOf(colour.copy(alpha = 0.16f), colour.copy(alpha = 0.03f)), startY = 0f, endY = h))
    drawPath(outer, colour, style = Stroke(1.2f, join = StrokeJoin.Miter))
    drawPath(steppedOutline(w, h, 3.2f, 2.2f), colour.copy(alpha = 0.7f), style = Stroke(0.6f, join = StrokeJoin.Miter))
    // Three bars dropping from the middle of the top and bottom, longest in the centre.
    for (bottom in listOf(false, true)) {
        withTransform({ if (bottom) scale(1f, -1f, Offset(w / 2, h / 2)) }) {
            for ((dx, length) in listOf(-3f to 3f, 0f to 5f, 3f to 3f)) {
                drawLine(colour, Offset(w / 2 + dx, 0.6f), Offset(w / 2 + dx, 0.6f + length), strokeWidth = 1.2f)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tribal.

/** The swirl's spiral round [hub] out to [radius] - from its tight centre to its top, where the tail along the edge takes over. */
private fun koruSpiral(hub: Offset, radius: Float): List<Offset> {
    val end = -PI.toFloat() / 2
    val start = end - 2.3f * PI.toFloat()
    return sampled(48) { t ->
        val angle = start + t * (end - start)
        hub + Offset(cos(angle), sin(angle)) * (radius * (0.12f + 0.88f * t.pow(0.85f)))
    }
}

private val TribalSwirlScoreFrame: ScoreFrame = DrawnScoreFrame("tribal_swirl") { w, h, colour ->
    val ink = colour
    eachCorner(w, h) {
        val radius = 6.2f
        val hub = Offset(1.6f + radius, 1.6f + radius)
        val spiral = koruSpiral(hub, radius)
        // The spiral, thickening as it winds out, then a tail along the top edge to near the middle.
        val tailEnd = w * 0.44f
        val tail = sampled(24) { t -> Offset(hub.x + t * (tailEnd - hub.x), 1.6f + 0.8f * t * t) }
        val swirl = spiral + tail.drop(1)
        val turn = spiral.size / swirl.size.toFloat()
        drawPath(
            taperedPath(swirl) { t -> if (t < turn) 0.4f + 2.6f * (t / turn) else 3f * (1 - (t - turn) / (1 - turn)).pow(0.8f) },
            ink,
        )
        // A second tail down the side, forking off the spiral's outer turn.
        val sideEnd = h * 0.46f
        val side = sampled(20) { t -> Offset(1.6f + 0.7f * t * t, hub.y - 1f + t * (sideEnd - hub.y + 1f)) }
        drawPath(taperedPath(side) { t -> 2.6f * (1 - t).pow(0.9f) }, ink)
        // A thorn off each tail.
        drawPath(taperedPath(sampled(8) { t -> Offset(hub.x + 7f + t * 4f, 2.4f + t * t * 3f) }) { t -> 1.6f * (1 - t) }, ink)
        drawPath(taperedPath(sampled(8) { t -> Offset(2.4f + t * t * 3f, hub.y + 6f + t * 4f) }) { t -> 1.6f * (1 - t) }, ink)
    }
}

private val TribalFlameScoreFrame: ScoreFrame = DrawnScoreFrame("tribal_flame") { w, h, colour ->
    eachCorner(w, h) {
        // Three curved blades sweeping out of the corner: along the top, down the side, and a short
        // hooked one between, curling back on itself.
        val top = sampled(24) { t -> Offset(2f + t * (w * 0.4f - 2f), 2f + 3f * (t - t * t) * 0.6f + (1 - t) * 3f) }
        drawPath(taperedPath(top) { t -> 3.4f * (1 - t).pow(0.7f) + 0.2f }, colour)
        val side = sampled(24) { t -> Offset(2f + 3f * (t - t * t) * 0.6f + (1 - t) * 3f, 2f + t * (h * 0.4f - 2f)) }
        drawPath(taperedPath(side) { t -> 3.4f * (1 - t).pow(0.7f) + 0.2f }, colour)
        val hook = sampled(20) { t ->
            val angle = -PI.toFloat() * 0.25f + t * PI.toFloat() * 0.9f
            Offset(4f, 4f) + Offset(t * 6f, t * 6f) + Offset(cos(angle), sin(angle)) * (2.5f * t)
        }
        drawPath(taperedPath(hook) { t -> 2.6f * (1 - t) + 0.2f }, colour.darker(0.15f))
    }
}

// ---------------------------------------------------------------------------------------------
// Celtic.

/** One point along a trefoil knot's outline, for t from 0 to 2π - three lobes, three crossings. */
private fun trefoil(t: Float): Offset = Offset(sin(t) + 2 * sin(2 * t), cos(t) - 2 * cos(2 * t))

private const val TREFOIL_SAMPLES = 180

/**
 * Where along the trefoil each crossing is the strand on top: walking the knot, the crossings it
 * meets go over, under, over... - which is what makes it read as woven. Worked out once, from the
 * curve itself.
 */
private val trefoilOvers: List<Float> by lazy {
    val points = List(TREFOIL_SAMPLES) { trefoil(it * 2 * PI.toFloat() / TREFOIL_SAMPLES) }
    val crossings = mutableListOf<Float>()
    for (i in 0 until TREFOIL_SAMPLES) {
        for (j in i + 2 until TREFOIL_SAMPLES) {
            if (i == 0 && j == TREFOIL_SAMPLES - 1) continue
            val at = segmentsCross(points[i], points[(i + 1) % TREFOIL_SAMPLES], points[j], points[(j + 1) % TREFOIL_SAMPLES]) ?: continue
            crossings += (i + at.first) / TREFOIL_SAMPLES * 2 * PI.toFloat()
            crossings += (j + at.second) / TREFOIL_SAMPLES * 2 * PI.toFloat()
        }
    }
    crossings.sorted().filterIndexed { index, _ -> index % 2 == 0 }
}

/** Where along each of two segments they cross, as fractions of each - or null where they don't. */
private fun segmentsCross(a1: Offset, a2: Offset, b1: Offset, b2: Offset): Pair<Float, Float>? {
    val da = a2 - a1
    val db = b2 - b1
    val denominator = da.x * db.y - da.y * db.x
    if (abs(denominator) < 1e-6f) return null
    val s = ((b1.x - a1.x) * db.y - (b1.y - a1.y) * db.x) / denominator
    val u = ((b1.x - a1.x) * da.y - (b1.y - a1.y) * da.x) / denominator
    return if (s in 0f..1f && u in 0f..1f) s to u else null
}

/** The trefoil, [scale] units to each of its curve's, turned [degrees] - one corner's knot. */
private fun DrawScope.drawTrefoil(colour: Color, scale: Float, degrees: Float, strand: Float) {
    val angle = degrees * PI.toFloat() / 180
    fun point(t: Float): Offset {
        val p = trefoil(t) * scale
        return Offset(p.x * cos(angle) - p.y * sin(angle), p.x * sin(angle) + p.y * cos(angle))
    }
    val whole = smoothPath(List(TREFOIL_SAMPLES) { point(it * 2 * PI.toFloat() / TREFOIL_SAMPLES) }, closed = true)
    drawPath(whole, colour, style = Stroke(strand))
    // Over each crossing, cut a gap through the strand beneath, then lay the top strand back over it.
    for (over in trefoilOvers) {
        val piece = smoothPath(sampled(8) { t -> point(over - 0.35f + t * 0.7f) })
        drawPath(piece, Color.Black, style = Stroke(strand * 2.6f), blendMode = BlendMode.Clear)
        drawPath(piece, colour, style = Stroke(strand, cap = StrokeCap.Butt))
    }
}

private val CelticKnotScoreFrame: ScoreFrame = DrawnScoreFrame("celtic_knot") { w, h, colour ->
    // Its own layer, so cutting the knots' gaps clears only the frame, never what's behind the tab.
    drawIntoCanvas { it.saveLayer(Rect(-4f, -4f, w + 4f, h + 4f), Paint()) }
    val knot = 8f
    // A double line between the knots, round all four sides.
    for (inset in listOf(1.3f, 3.6f)) {
        drawLine(colour, Offset(knot + 4f, inset), Offset(w - knot - 4f, inset), strokeWidth = 0.8f)
        drawLine(colour, Offset(knot + 4f, h - inset), Offset(w - knot - 4f, h - inset), strokeWidth = 0.8f)
        drawLine(colour, Offset(inset, knot + 4f), Offset(inset, h - knot - 4f), strokeWidth = 0.8f)
        drawLine(colour, Offset(w - inset, knot + 4f), Offset(w - inset, h - knot - 4f), strokeWidth = 0.8f)
    }
    eachCorner(w, h) {
        placed(Offset(knot, knot), 0f) { drawTrefoil(colour, 2.15f, -45f, 1.2f) }
    }
    drawIntoCanvas { it.restore() }
}

// ---------------------------------------------------------------------------------------------
// Wreath.

/**
 * A branch of leaves up one side of the tab: from the bottom centre, along the bottom, round the
 * corner and up the left side to near the top, a leaf every [spacing], alternating sides.
 */
private fun DrawScope.drawWreathBranch(w: Float, h: Float, colour: Color, leafLength: Float, leafWidth: Float, spacing: Float, berries: Boolean) {
    val inset = 3f
    val corner = TAB_CORNER - inset
    // The stem's line, as points a little apart: bottom edge, corner arc, left side.
    val stem = buildList {
        val startX = w / 2 - 5f
        var x = startX
        while (x > inset + corner) { add(Offset(x, h - inset)); x -= 1f }
        for (i in 0..12) {
            val angle = PI.toFloat() / 2 + i / 12f * PI.toFloat() / 2
            add(Offset(inset + corner + cos(angle) * corner, h - inset - corner + sin(angle) * corner))
        }
        var y = h - inset - corner - 1f
        while (y > h * 0.14f) { add(Offset(inset, y)); y -= 1f }
    }
    drawPath(smoothPath(stem), colour.darker(0.3f), style = Stroke(0.8f, cap = StrokeCap.Round))
    var travelled = 0f
    var next = spacing * 0.6f
    var outward = true
    for (i in 1 until stem.size) {
        travelled += (stem[i] - stem[i - 1]).getDistance()
        if (travelled < next) continue
        next += spacing
        val along = stem[min(i + 1, stem.size - 1)] - stem[i - 1]
        val heading = atan2(along.y, along.x) * 180 / PI.toFloat()
        val shade = if (outward) colour else colour.darker(0.2f)
        placed(stem[i], heading + if (outward) 38f else -38f) { drawLeaf(shade, leafLength, leafWidth) }
        if (berries && !outward && i % 3 == 0) {
            drawCircle(colour.lighter(0.35f), 1.1f, stem[i] + Offset(0.8f, -0.8f))
        }
        outward = !outward
    }
    // The tip: one last leaf pointing on up the side.
    placed(stem.last(), -90f) { drawLeaf(colour, leafLength, leafWidth) }
}

private val LaurelScoreFrame: ScoreFrame = DrawnScoreFrame("laurel") { w, h, colour ->
    bothSides(w) { drawWreathBranch(w, h, colour, leafLength = 5f, leafWidth = 2.3f, spacing = 3.2f, berries = false) }
}

private val OliveScoreFrame: ScoreFrame = DrawnScoreFrame("olive") { w, h, colour ->
    bothSides(w) { drawWreathBranch(w, h, colour, leafLength = 6.5f, leafWidth = 1.6f, spacing = 3.6f, berries = true) }
}

// ---------------------------------------------------------------------------------------------
// Greek.

/** A band [height] tall along the tab's top edge, between two rails, with [unit] drawn in each of its repeats. */
private fun DrawScope.drawGreekBand(w: Float, colour: Color, height: Float, repeat: Float, unit: DrawScope.(x: Float) -> Unit) {
    val left = 1.2f
    val right = w - 1.2f
    val count = max(1, floor((right - left - 2f) / repeat).toInt())
    val start = (w - count * repeat) / 2
    drawLine(colour, Offset(left, 0.6f), Offset(right, 0.6f), strokeWidth = 0.8f)
    drawLine(colour, Offset(left, height + 0.6f), Offset(right, height + 0.6f), strokeWidth = 0.8f)
    for (i in 0 until count) unit(start + i * repeat)
}

/** The frame's sides and the ends of its bands: square, like the bands themselves. */
private fun DrawScope.drawGreekSides(w: Float, h: Float, colour: Color) {
    drawLine(colour, Offset(1.2f, 0.6f), Offset(1.2f, h - 0.6f), strokeWidth = 0.8f)
    drawLine(colour, Offset(w - 1.2f, 0.6f), Offset(w - 1.2f, h - 0.6f), strokeWidth = 0.8f)
}

private val GreekKeyScoreFrame: ScoreFrame = DrawnScoreFrame("greek_key") { w, h, colour ->
    drawGreekSides(w, h, colour)
    for (bottom in listOf(false, true)) {
        withTransform({ if (bottom) scale(1f, -1f, Offset(w / 2, h / 2)) }) {
            // One turn of the key in each repeat: up from the bottom rail, over, down, back and in.
            drawGreekBand(w, colour, height = 4.8f, repeat = 5f) { x ->
                val g = 1.2f
                val y0 = 0.6f
                drawPath(
                    Path().apply {
                        moveTo(x + 0.5f * g, y0 + 4 * g)
                        lineTo(x + 0.5f * g, y0 + g)
                        lineTo(x + 3.5f * g, y0 + g)
                        lineTo(x + 3.5f * g, y0 + 3 * g)
                        lineTo(x + 2f * g, y0 + 3 * g)
                        lineTo(x + 2f * g, y0 + 2 * g)
                    },
                    colour,
                    style = Stroke(0.7f, join = StrokeJoin.Miter),
                )
            }
        }
    }
}

private val GreekWavesScoreFrame: ScoreFrame = DrawnScoreFrame("greek_waves") { w, h, colour ->
    drawGreekSides(w, h, colour)
    for (bottom in listOf(false, true)) {
        withTransform({ if (bottom) scale(1f, -1f, Offset(w / 2, h / 2)) }) {
            val height = 4.8f
            // A wave in each repeat, rising off the bottom rail and curling over into itself.
            drawGreekBand(w, colour, height = height, repeat = 6f) { x ->
                val base = height + 0.6f
                val hub = Offset(x + 3.6f, 0.6f + height * 0.5f)
                val curl = 1.5f
                val path = Path().apply {
                    moveTo(x, base)
                    cubicTo(x + 1.6f, base, x + 1.6f, hub.y - curl, hub.x, hub.y - curl)
                    for (i in 1..16) {
                        val angle = -PI.toFloat() / 2 + i / 16f * 1.6f * PI.toFloat()
                        val r = curl * (1 - i / 16f * 0.6f)
                        lineTo(hub.x + cos(angle) * r, hub.y + sin(angle) * r)
                    }
                }
                drawPath(path, colour, style = Stroke(0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Neon.

/** A glowing tube along the tab's outline, [inset] in: soft light spreading round a bright core. */
private fun DrawScope.drawNeonTube(w: Float, h: Float, colour: Color, inset: Float, core: Float) {
    for ((width, alpha) in listOf(7f to 0.06f, 5f to 0.1f, 3.4f to 0.18f, 2.2f to 0.4f)) {
        drawTabOutline(w, h, colour.copy(alpha = alpha), width = width * core, inset = inset)
    }
    drawTabOutline(w, h, colour.lighter(0.6f), width = 1f * core, inset = inset)
}

private val NeonScoreFrame: ScoreFrame = DrawnScoreFrame("neon") { w, h, colour ->
    drawNeonTube(w, h, colour, inset = 1.5f, core = 1f)
}

private val NeonDoubleScoreFrame: ScoreFrame = DrawnScoreFrame("neon_double") { w, h, colour ->
    drawNeonTube(w, h, colour, inset = 1.2f, core = 0.8f)
    drawNeonTube(w, h, colour, inset = 4.4f, core = 0.6f)
}

// ---------------------------------------------------------------------------------------------
// Pixel.

private val PixelScoreFrame: ScoreFrame = DrawnScoreFrame("pixel") { w, h, colour ->
    val g = 2f
    val columns = floor(w / g).toInt()
    val rows = floor(h / g).toInt()
    // Centred on the tab, as whole pixels.
    val left = (w - columns * g) / 2
    val top = (h - rows * g) / 2
    // The outline's pixels: every edge, its corners stepped in two pixels at a time.
    val outline = buildList {
        for (c in 3 until columns - 3) { add(c to 0); add(c to rows - 1) }
        for (r in 3 until rows - 3) { add(0 to r); add(columns - 1 to r) }
        for ((c, r) in listOf(1 to 1, 1 to 2, 2 to 1)) {
            add(c to r); add(columns - 1 - c to r); add(c to rows - 1 - r); add(columns - 1 - c to rows - 1 - r)
        }
    }
    fun pixel(c: Int, r: Int, colour: Color, dx: Int = 0, dy: Int = 0) =
        drawRect(colour, Offset(left + (c + dx) * g, top + (r + dy) * g), Size(g, g))
    // A drop shadow a pixel down and right, inside the outline, then the outline, lit along its top and left.
    for ((c, r) in outline) if (c + 1 < columns - 1 && r + 1 < rows - 1) pixel(c, r, colour.darker(0.55f), 1, 1)
    for ((c, r) in outline) pixel(c, r, colour)
    for ((c, r) in outline) if (r == 0 || c == 0) pixel(c, r, colour.lighter(0.4f))
}

// ---------------------------------------------------------------------------------------------

/** Every frame by its id, for the catalog. */
internal object ScoreFrameArt {
    val classic = ClassicScoreFrame
    val rose = RoseScoreFrame
    val daisy = DaisyScoreFrame
    val vine = VineScoreFrame
    val decoFan = DecoFanScoreFrame
    val decoStepped = DecoSteppedScoreFrame
    val tribalSwirl = TribalSwirlScoreFrame
    val tribalFlame = TribalFlameScoreFrame
    val celticKnot = CelticKnotScoreFrame
    val laurel = LaurelScoreFrame
    val olive = OliveScoreFrame
    val greekKey = GreekKeyScoreFrame
    val greekWaves = GreekWavesScoreFrame
    val neon = NeonScoreFrame
    val neonDouble = NeonDoubleScoreFrame
    val pixel = PixelScoreFrame
}
