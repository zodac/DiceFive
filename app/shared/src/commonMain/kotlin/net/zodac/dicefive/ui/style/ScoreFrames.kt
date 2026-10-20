package net.zodac.dicefive.ui.style

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import net.zodac.dicefive.data.settings.DefaultStyleIds
import net.zodac.dicefive.model.PlayerColour
import net.zodac.dicefive.ui.theme.color

// The frames round the player whose turn it is, in the row of tabs above the board (PlayerHeaderBar).
// Every one is drawn in that player's own colour - shades of it, never a colour of its own - so up to
// four players' frames all read as theirs. They're drawn behind the tab's name and score, in dp
// (see inDp), and stretch to any tab: a narrow one of four, a wide one of two. Nothing here moves.
// A frame is redrawn whenever its tab is, which is every frame while a score counts up, so one made of
// hundreds of leaves, links or stars is painted once instead (see paintedFrame).

/** The colour a frame's Styles tile shows it in: player 1's default. */
val ScoreFramePreviewColour: Color get() = PlayerColour.defaultFor(0).color

/** The tab's own corner radius, which a frame that follows its outline rounds to. */
private const val TAB_CORNER = 10f

/**
 * A frame defined by one drawing function, in dp - see [inDp]. A [painted] one is drawn once per colour and tab size and
 * then stamped (see [paintedFrame]): for one with so many parts that drawing it afresh on every redraw would cost more
 * than the rest of the tab.
 */
private class DrawnScoreFrame(
    override val id: String,
    private val painted: Boolean = false,
    private val draw: DrawScope.(w: Float, h: Float, colour: Color) -> Unit,
) : ScoreFrame {
    override fun DrawScope.drawFrame(color: Color) {
        if (painted) paintedFrame(id, color, draw) else inDp { w, h -> draw(w, h, color) }
    }
}

/** How far, in dp, past the tab a painted frame may draw: a glow or an ornament can spill into the gap beside it. */
private const val FRAME_SPILL = 8f

private data class FrameSurface(val id: String, val colour: Color, val width: Int, val height: Int)

// Only ever drawn on the main thread, so never shared between threads. A game uses one frame in one colour at
// one tab size; the Styles page shows a few more, at the size of its tiles.
private val frameImages = LinkedHashMap<FrameSurface, ImageBitmap>()
private const val FRAME_CACHE_SIZE = 24

/** [draw] in [colour], painted once for this id, colour and size (with room all round for [FRAME_SPILL]) and then just stamped. */
private fun DrawScope.paintedFrame(id: String, colour: Color, draw: DrawScope.(w: Float, h: Float, colour: Color) -> Unit) {
    val width = ceil(size.width).toInt()
    val height = ceil(size.height).toInt()
    if (width <= 0 || height <= 0) return
    val spill = ceil(FRAME_SPILL * density)
    val key = FrameSurface(id, colour, width, height)
    val image = frameImages.remove(key) ?: ImageBitmap(width + 2 * spill.toInt(), height + 2 * spill.toInt()).also { image ->
        val unit = density
        val wDp = size.width / unit
        val hDp = size.height / unit
        CanvasDrawScope().draw(this, layoutDirection, Canvas(image), Size(image.width.toFloat(), image.height.toFloat())) {
            withTransform({
                translate(spill, spill)
                scale(unit, unit, Offset.Zero)
            }) { draw(wDp, hDp, colour) }
        }
    }
    // Re-added on every use, so the map runs least recently used first.
    frameImages[key] = image
    if (frameImages.size > FRAME_CACHE_SIZE) frameImages.remove(frameImages.keys.first())
    drawImage(image, Offset(-spill, -spill))
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
val ClassicScoreFrame: ScoreFrame = DrawnScoreFrame(DefaultStyleIds.SCORE_FRAME) { w, h, colour ->
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

private val RoseScoreFrame: ScoreFrame = DrawnScoreFrame("floral_rose") { w, h, colour ->
    drawTabOutline(w, h, colour.copy(alpha = 0.7f), width = 1.1f)
    val leafColour = colour.darker(0.45f)
    val rose: DrawScope.() -> Unit = {
        placed(Offset(5.5f, 5.5f), 0f) {
            placed(Offset(4.5f, -5f), -6f) { drawLeaf(leafColour, 10f, 3f) }
            placed(Offset(-5f, 4.5f), 96f) { drawLeaf(leafColour, 10f, 3f) }
            drawRose(colour, 5.8f)
        }
    }
    val bud: DrawScope.() -> Unit = {
        placed(Offset(w - 4f, 4f), 0f) {
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

private val DaisyScoreFrame: ScoreFrame = DrawnScoreFrame("floral_daisy") { w, h, colour ->
    drawTabOutline(w, h, colour.copy(alpha = 0.5f), width = 1f)
    eachCorner(w, h) {
        placed(Offset(9.5f, 1.5f), 0f) { drawLeaf(colour.darker(0.45f), 5.5f, 1.7f, vein = false) }
        placed(Offset(1.5f, 9.5f), 90f) { drawLeaf(colour.darker(0.45f), 5.5f, 1.7f, vein = false) }
        placed(Offset(5f, 5f), 0f) { drawDaisy(colour, 5.5f, turn = 10f) }
    }
}

private val VineScoreFrame: ScoreFrame = DrawnScoreFrame("floral_vine") { w, h, colour ->
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

private val DecoFanScoreFrame: ScoreFrame = DrawnScoreFrame("art_deco_fan") { w, h, colour ->
    drawDecoWash(w, h, colour)
    drawTabOutline(w, h, colour, width = 1.2f)
    drawTabOutline(w, h, colour.copy(alpha = 0.7f), width = 0.6f, inset = 3f)
    eachCorner(w, h) {
        // A quarter sunburst fanning out of the inner outline's corner.
        val hub = Offset(3f, 3f)
        val radius = 7.5f
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

private val DecoSteppedScoreFrame: ScoreFrame = DrawnScoreFrame("art_deco_stepped") { w, h, colour ->
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
        val radius = 4.8f
        val hub = Offset(1.4f + radius, 1.4f + radius)
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
        val top = sampled(24) { t -> Offset(1.6f + t * (w * 0.4f - 1.6f), 1.6f + 3f * (t - t * t) * 0.4f + (1 - t) * 1.6f) }
        drawPath(taperedPath(top) { t -> 3f * (1 - t).pow(0.7f) + 0.2f }, colour)
        val side = sampled(24) { t -> Offset(1.6f + 3f * (t - t * t) * 0.4f + (1 - t) * 1.6f, 1.6f + t * (h * 0.4f - 1.6f)) }
        drawPath(taperedPath(side) { t -> 3f * (1 - t).pow(0.7f) + 0.2f }, colour)
        val hook = sampled(20) { t ->
            val angle = -PI.toFloat() * 0.25f + t * PI.toFloat() * 0.9f
            Offset(4f, 4f) + Offset(t * 4.5f, t * 4.5f) + Offset(cos(angle), sin(angle)) * (2f * t)
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
    val knot = 5.8f
    // A double line between the knots, round all four sides.
    for (inset in listOf(1.1f, 2.6f)) {
        drawLine(colour, Offset(knot + 4f, inset), Offset(w - knot - 4f, inset), strokeWidth = 0.8f)
        drawLine(colour, Offset(knot + 4f, h - inset), Offset(w - knot - 4f, h - inset), strokeWidth = 0.8f)
        drawLine(colour, Offset(inset, knot + 4f), Offset(inset, h - knot - 4f), strokeWidth = 0.8f)
        drawLine(colour, Offset(w - inset, knot + 4f), Offset(w - inset, h - knot - 4f), strokeWidth = 0.8f)
    }
    eachCorner(w, h) {
        placed(Offset(knot, knot), 0f) { drawTrefoil(colour, 1.6f, -45f, 1f) }
    }
    drawIntoCanvas { it.restore() }
}

// ---------------------------------------------------------------------------------------------
// Wreath.

/**
 * One half of a wreath round the whole tab: a branch of leaves from the bottom centre, along the bottom, up the left side
 * and along the top to the top centre, a leaf every [spacing], alternating sides. Mirrored, the two halves close the ring.
 *
 * Beside the name, which can reach nearly to the tab's sides, the stem squeezes out to the edge and the leaves pointing in
 * shrink, while those pointing out (into the gap between tabs) hardly do. Everywhere else the leaves are full size, and
 * along the top edge, above the name, they have room to spare.
 */
private fun DrawScope.drawWreathBranch(w: Float, h: Float, colour: Color, leafLength: Float, leafWidth: Float, spacing: Float, berries: Boolean) {
    val inset = 3f
    val corner = TAB_CORNER - inset
    val nameBottom = WREATH_NAME_BOTTOM * h
    /** How far into the name's rows [y] is down a side, from 0 (below them) to 1 (beside the name). */
    fun squeeze(y: Float) = ((nameBottom - y) / WREATH_SQUEEZE_RAMP).coerceIn(0f, 1f)
    val stem = ArrayList<Offset>()
    val squeezes = ArrayList<Float>()
    fun add(at: Offset, squeezed: Float = 0f) { stem += at; squeezes += squeezed }
    // Bottom edge from the centre to the corner, the corner's arc, then up the side to the top corner's arc and along the top.
    var x = w / 2
    while (x > inset + corner) { add(Offset(x, h - inset)); x -= 1f }
    for (i in 0..12) {
        val angle = PI.toFloat() / 2 + i / 12f * PI.toFloat() / 2
        add(Offset(inset + corner + cos(angle) * corner, h - inset - corner + sin(angle) * corner))
    }
    var y = h - inset - corner - 1f
    while (y > inset + corner) {
        val squeezed = squeeze(y)
        add(Offset(inset - WREATH_SQUEEZE_OUT * squeezed, y), squeezed)
        y -= 1f
    }
    for (i in 0..12) {
        val angle = PI.toFloat() + i / 12f * PI.toFloat() / 2
        add(Offset(inset + corner + cos(angle) * corner, inset + corner + sin(angle) * corner))
    }
    // Stops a leaf's length short of the centre, so the two halves' last leaves just meet.
    var tx = inset + corner + 1f
    while (tx < w / 2 - leafLength * 0.6f) { add(Offset(tx, inset)); tx += 1f }

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
        val squeezed = squeezes[i]
        val scale = if (outward) 1f - WREATH_OUT_SHRINK * squeezed else 1f - WREATH_IN_SHRINK * squeezed
        placed(stem[i], heading + if (outward) 38f else -38f) { drawLeaf(shade, leafLength * scale, leafWidth * scale) }
        if (berries && !outward && i % 3 == 0 && squeezed == 0f) {
            drawCircle(colour.lighter(0.35f), 1.1f, stem[i] + Offset(0.8f, -0.8f))
        }
        outward = !outward
    }
    // The tip: one last leaf pointing on along the top.
    placed(stem.last(), 0f) { drawLeaf(colour, leafLength, leafWidth) }
}

/** How far down the tab, as a fraction of its height, the name's rows end: the leaves beside them are squeezed. */
private const val WREATH_NAME_BOTTOM = 0.36f

/** How many dp below the name's rows the squeeze eases off over. */
private const val WREATH_SQUEEZE_RAMP = 6f

/** How far, in dp, the stem moves out to the edge beside the name. */
private const val WREATH_SQUEEZE_OUT = 1f

/** How much of its size a leaf pointing in, and one pointing out, loses beside the name. */
private const val WREATH_IN_SHRINK = 0.65f
private const val WREATH_OUT_SHRINK = 0.1f

private val LaurelScoreFrame: ScoreFrame = DrawnScoreFrame("wreath_laurel", painted = true) { w, h, colour ->
    bothSides(w) { drawWreathBranch(w, h, colour, leafLength = 5f, leafWidth = 2.3f, spacing = 3.2f, berries = false) }
}

private val OliveScoreFrame: ScoreFrame = DrawnScoreFrame("wreath_olive", painted = true) { w, h, colour ->
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

private val NeonScoreFrame: ScoreFrame = DrawnScoreFrame("neon_single") { w, h, colour ->
    drawNeonTube(w, h, colour, inset = 1.5f, core = 1f)
}

private val NeonDoubleScoreFrame: ScoreFrame = DrawnScoreFrame("neon_double") { w, h, colour ->
    drawNeonTube(w, h, colour, inset = 1.2f, core = 0.8f)
    drawNeonTube(w, h, colour, inset = 3.6f, core = 0.6f)
}

// ---------------------------------------------------------------------------------------------
// Pixel.

private val PixelScoreFrame: ScoreFrame = DrawnScoreFrame("pixel_retro") { w, h, colour ->
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
// Starry, Nautical and Marquee: things strung along the tab's edge.

/** A spot on the tab's outline: where it is, which way (in degrees clockwise from along +x) the outline runs there, and whether it's a corner's middle. */
private class OutlineSpot(val at: Offset, val degrees: Float, val corner: Boolean)

/**
 * Spots all the way round the tab's outline, [inset] in and about [spacing] apart: each edge's are evenly spread from one
 * corner's arc to the next, and the middle of every rounded corner has one of its own, so a corner is always marked.
 * Clockwise from the top left.
 */
private fun outlineSpots(w: Float, h: Float, inset: Float, spacing: Float): List<OutlineSpot> {
    val r = max(TAB_CORNER - inset, 1f)
    val left = inset + r
    val right = w - inset - r
    val top = inset + r
    val bottom = h - inset - r
    val diagonal = r * 0.7071f
    val spots = ArrayList<OutlineSpot>()
    fun edge(from: Offset, to: Offset, degrees: Float) {
        val steps = max(round((to - from).getDistance() / spacing).toInt(), 1)
        for (i in 0..steps) spots += OutlineSpot(from + (to - from) * (i / steps.toFloat()), degrees, corner = false)
    }
    edge(Offset(left, inset), Offset(right, inset), 0f)
    spots += OutlineSpot(Offset(right + diagonal, top - diagonal), 45f, corner = true)
    edge(Offset(w - inset, top), Offset(w - inset, bottom), 90f)
    spots += OutlineSpot(Offset(right + diagonal, bottom + diagonal), 135f, corner = true)
    edge(Offset(right, h - inset), Offset(left, h - inset), 180f)
    spots += OutlineSpot(Offset(left - diagonal, bottom + diagonal), 225f, corner = true)
    edge(Offset(inset, bottom), Offset(inset, top), 270f)
    spots += OutlineSpot(Offset(left - diagonal, top - diagonal), 315f, corner = true)
    return spots
}

/** The tab's outline, [inset] in, as one closed path to stroke or measure. */
private fun outlinePath(w: Float, h: Float, inset: Float): Path = Path().apply {
    addRoundRect(RoundRect(Rect(inset, inset, w - inset, h - inset), CornerRadius(max(TAB_CORNER - inset, 1f))))
}

/** A five-pointed star [radius] out from [centre], one point straight up. */
private fun starPath(centre: Offset, radius: Float, inner: Float = 0.45f): Path = Path().apply {
    for (i in 0 until 10) {
        val angle = -PI.toFloat() / 2 + i * PI.toFloat() / 5
        val reach = if (i % 2 == 0) radius else radius * inner
        val x = centre.x + cos(angle) * reach
        val y = centre.y + sin(angle) * reach + radius * 0.07f
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** How big a star is, in dp, beside the one before it on the edge: big and small in turn, with a corner's the biggest. */
private fun starRadius(spot: OutlineSpot, index: Int, big: Float, small: Float, corner: Float) = when {
    spot.corner -> corner
    index % 2 == 0 -> big
    else -> small
}

private val StarsScoreFrame: ScoreFrame = DrawnScoreFrame("starry_stars", painted = true) { w, h, colour ->
    val spots = outlineSpots(w, h, inset = 2.8f, spacing = 6.4f)
    spots.forEachIndexed { index, spot ->
        val radius = starRadius(spot, index, big = 2.0f, small = 1.35f, corner = 3.0f)
        drawPath(starPath(spot.at, radius), if (radius > 1.8f) colour.lighter(0.3f) else colour)
    }
    // Faint specks of distant stars between them.
    for (i in 1 until spots.size) {
        val mid = (spots[i].at + spots[i - 1].at) / 2f
        if ((spots[i].at - spots[i - 1].at).getDistance() < 8f) drawCircle(colour.copy(alpha = 0.55f), 0.45f, mid)
    }
}

private val SparklesScoreFrame: ScoreFrame = DrawnScoreFrame("starry_sparkles", painted = true) { w, h, colour ->
    val spots = outlineSpots(w, h, inset = 2.8f, spacing = 6.4f)
    val star = Path()
    spots.forEachIndexed { index, spot ->
        val radius = starRadius(spot, index, big = 2.6f, small = 1.8f, corner = 3.6f)
        drawSoftSpot(colour.copy(alpha = if (index % 2 == 0 || spot.corner) 0.34f else 0.2f), spot.at, radius * 1.9f)
        drawPath(star.fourPointStar(spot.at, radius), colour.lighter(if (index % 2 == 0 || spot.corner) 0.55f else 0.25f))
    }
}

private val ConstellationScoreFrame: ScoreFrame = DrawnScoreFrame("starry_constellation", painted = true) { w, h, colour ->
    val spots = outlineSpots(w, h, inset = 2.9f, spacing = 7.6f)
    val random = kotlin.random.Random(23)
    // Each star knocked a little off the line, so the border reads as a constellation, not a row.
    val nodes = spots.map { spot ->
        val turn = spot.degrees * PI.toFloat() / 180f
        val along = (random.nextFloat() - 0.5f) * 2.4f
        val across = (random.nextFloat() - 0.5f) * 1.8f
        spot.at + Offset(cos(turn) * along - sin(turn) * across, sin(turn) * along + cos(turn) * across)
    }
    for (i in nodes.indices) {
        drawLine(colour.copy(alpha = 0.5f), nodes[i], nodes[(i + 1) % nodes.size], strokeWidth = 0.55f)
    }
    nodes.forEachIndexed { index, at ->
        val radius = if (spots[index].corner) 2.6f else 1.1f + random.nextFloat() * 1.0f
        drawSoftSpot(colour.copy(alpha = 0.28f), at, radius * 2.1f)
        drawPath(starPath(at, radius), colour.lighter(if (radius > 1.7f) 0.5f else 0.2f))
    }
}

private val RopeScoreFrame: ScoreFrame = DrawnScoreFrame("nautical_rope", painted = true) { w, h, colour ->
    val inset = 2.3f
    val path = outlinePath(w, h, inset)
    val measure = PathMeasure().apply { setPath(path, false) }
    // The rope's body, then its strands twisting across it every couple of dp, each lit on one side.
    drawPath(path, colour.darker(0.45f), style = Stroke(3.2f))
    var distance = 0f
    while (distance < measure.length) {
        val at = measure.getPosition(distance)
        val along = measure.getTangent(distance)
        val across = Offset(-along.y, along.x)
        drawLine(colour.darker(0.15f), at - across * 1.35f - along * 0.85f, at + across * 1.35f + along * 0.85f, strokeWidth = 1.05f, cap = StrokeCap.Round)
        drawLine(colour.lighter(0.3f).copy(alpha = 0.85f), at - across * 1.0f - along * 0.7f, at + across * 0.2f + along * 0.2f, strokeWidth = 0.5f, cap = StrokeCap.Round)
        distance += 1.9f
    }
}

private val ChainScoreFrame: ScoreFrame = DrawnScoreFrame("nautical_chain", painted = true) { w, h, colour ->
    val inset = 2.6f
    val path = outlinePath(w, h, inset)
    val measure = PathMeasure().apply { setPath(path, false) }
    val spacing = 3.7f
    val links = max(round(measure.length / spacing).toInt(), 1)
    for (i in 0 until links) {
        val distance = measure.length * i / links
        val at = measure.getPosition(distance)
        val along = measure.getTangent(distance)
        val degrees = atan2(along.y, along.x) * 180f / PI.toFloat()
        if (i % 2 == 0) {
            // A link seen flat: an oval ring.
            placed(at, degrees) {
                drawRoundRect(colour.darker(0.5f), Offset(-2.5f, -1.6f), Size(5f, 3.2f), CornerRadius(1.6f), style = Stroke(1.5f))
                drawRoundRect(colour.lighter(0.15f), Offset(-2.4f, -1.5f), Size(4.8f, 3.0f), CornerRadius(1.5f), style = Stroke(0.85f))
            }
        } else {
            // The next one turned side-on: a short bar.
            placed(at, degrees) {
                drawRoundRect(colour.darker(0.2f), Offset(-1.5f, -0.75f), Size(3f, 1.5f), CornerRadius(0.75f))
                drawLine(colour.lighter(0.4f).copy(alpha = 0.8f), Offset(-1.0f, -0.3f), Offset(1.0f, -0.3f), strokeWidth = 0.4f, cap = StrokeCap.Round)
            }
        }
    }
}

private val BulbsScoreFrame: ScoreFrame = DrawnScoreFrame("marquee_bulbs", painted = true) { w, h, colour ->
    val inset = 3.2f
    drawPath(outlinePath(w, h, inset), colour.darker(0.45f), style = Stroke(0.8f))
    outlineSpots(w, h, inset, spacing = 6.2f).forEachIndexed { index, spot ->
        // Every other bulb lit bright, as a marquee's chase is caught mid-run.
        val lit = index % 2 == 0 || spot.corner
        val radius = if (spot.corner) 2.0f else 1.55f
        drawSoftSpot(colour.copy(alpha = if (lit) 0.34f else 0.14f), spot.at, radius * 2.3f)
        drawCircle(if (lit) colour.lighter(0.5f) else colour.darker(0.2f), radius, spot.at)
        if (lit) drawCircle(Color.White.copy(alpha = 0.8f), radius * 0.3f, spot.at + Offset(-radius * 0.3f, -radius * 0.3f))
    }
}

private val DiamondsScoreFrame: ScoreFrame = DrawnScoreFrame("marquee_diamonds", painted = true) { w, h, colour ->
    val inset = 3.3f
    val rail = outlinePath(w, h, inset)
    drawPath(rail, colour.darker(0.5f), style = Stroke(0.7f))
    outlineSpots(w, h, inset, spacing = 6.8f).forEachIndexed { index, spot ->
        if (index % 2 == 0 || spot.corner) {
            val half = if (spot.corner) 2.7f else 2.0f
            val diamond = polygonPath(listOf(spot.at + Offset(0f, -half), spot.at + Offset(half * 0.8f, 0f), spot.at + Offset(0f, half), spot.at + Offset(-half * 0.8f, 0f)))
            drawPath(diamond, colour.lighter(0.35f))
            drawPath(diamond, colour.darker(0.35f), style = Stroke(0.5f))
            drawLine(Color.White.copy(alpha = 0.7f), spot.at + Offset(0f, -half * 0.6f), spot.at + Offset(half * 0.35f, -half * 0.1f), strokeWidth = 0.4f, cap = StrokeCap.Round)
        } else {
            drawCircle(colour.lighter(0.15f), 0.8f, spot.at)
        }
    }
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
    val stars = StarsScoreFrame
    val sparkles = SparklesScoreFrame
    val constellation = ConstellationScoreFrame
    val rope = RopeScoreFrame
    val chain = ChainScoreFrame
    val bulbs = BulbsScoreFrame
    val diamonds = DiamondsScoreFrame
}
