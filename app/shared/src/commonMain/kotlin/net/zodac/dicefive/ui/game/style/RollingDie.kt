package net.zodac.dicefive.ui.game.style

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// How far through the toss the die reaches the far wall and bounces; the rest is rolling back.
private const val WALL_AT = 0.45f

/**
 * Where a tossed die is, part-way through its toss - relative to where it will come to rest, in dp.
 *
 * [dx]/[dy] are its offset from its resting spot, [yawDegrees] how far it's spun from its resting
 * angle, and [roll] how many quarter-turns it has tumbled through, forwards up the tray and back
 * again after the bounce - see [TossedCube].
 */
class TossPose(val dx: Float, val dy: Float, val yawDegrees: Float, val roll: Float)

/**
 * One die's toss, in dp measured down from the far wall - the top of the area dice land in: thrown
 * from [startY], just past the near edge and out of sight, flying up its column into the wall, bouncing and rolling back to
 * rest at [restY] on [result]. The whole throw is [toss] from 0 to 1; each die comes in a touch later
 * than the others ([seed]), drifts a little sideways between its column's invisible walls
 * ([sideRoom] dp either way), and spins before settling.
 *
 * It tumbles about one axis as it travels, forwards up the tray and back after the bounce, through
 * [ring] - the real faces round that axis. It starts on [startFace] (the face it was showing when
 * picked up off the mat, if it was on the mat at all) and finishes on [result], so it never jumps from one face to
 * another: [finalTurns], the whole quarter-turns it ends up rolled through, is picked to make both
 * true, and the tumble is paced to its distance travelled so it comes to rest squarely on a face.
 * For a style that draws more than the face it rolled ([DiceStyle.topFace]), [restTop] is the face
 * it has to finish with on top - the one before [result] in the ring - so it lands looking exactly as
 * it then rests; that takes priority over [startFace], which is thrown in out of sight anyway.
 */
class TossPath(
    seed: Int,
    result: Int,
    startFace: Int?,
    restTop: Int? = null,
    private val startY: Float,
    private val restY: Float,
    dieSize: Float,
    sideRoom: Float,
) {
    private val random = Random(seed)
    private val delay = random.nextFloat() * 0.08f
    private val drift = (random.nextFloat() * 2f - 1f) * sideRoom
    private val driftWaves = 2f + random.nextFloat()
    private val spin = (random.nextFloat() * 2f - 1f) * 120f
    private val travel = ((startY - restY) / dieSize).coerceAtLeast(0.01f)

    val ring: List<Int>
    val finalTurns: Int

    init {
        // The ring runs result, neighbour, opposite, neighbour's opposite; the face it starts on sits
        // (finalTurns) places back round it, so pick the neighbour and the turns to put startFace there.
        // The ring's last face is the one on top at rest, one turn back from the result.
        val closest = maxOf(1, travel.roundToInt())
        val neighbours = (1..6).filter { it != result && it != 7 - result }
        val neighbour = when {
            restTop != null && restTop in neighbours -> 7 - restTop
            startFace != null && startFace in neighbours -> 7 - startFace
            else -> neighbours.random(random)
        }
        ring = listOf(result, neighbour, 7 - result, 7 - neighbour)
        // It starts on the ring's face finalTurns places back from the result - or, if the face it
        // was picked up on isn't round this ring at all, anywhere.
        val startIndex = startFace?.let { ring.indexOf(it) }?.takeIf { it >= 0 }
        finalTurns = if (startIndex == null) {
            closest
        } else {
            (1..8).filter { (it + startIndex) % 4 == 0 }.minBy { abs(it - closest) }
        }
    }

    fun pose(toss: Float): TossPose {
        val t = ((toss - delay) / (1f - delay)).coerceIn(0f, 1f)
        // Up to the wall, slowing a little as it goes; then back from the wall, slowing to a stop.
        val y = if (t < WALL_AT) {
            val w = t / WALL_AT
            startY * (1f - w).pow(1.6f)
        } else {
            val w = (t - WALL_AT) / (1f - WALL_AT)
            restY * (1f - (1f - w).pow(3))
        }
        // Bouncing between the column's walls, dying away to nothing as it settles.
        val dx = drift * sin(t * PI.toFloat() * driftWaves) * (1f - t).pow(2)
        val roll = (startY - y) / (startY - restY).coerceAtLeast(0.01f) * finalTurns
        return TossPose(dx = dx, dy = y - restY, yawDegrees = spin * (1f - t).pow(2), roll = roll)
    }
}

/**
 * A die being picked up for a roll: slid off the mat from where it rests ([restY]) to past its near
 * edge ([startY], out of sight), speeding up as it goes, as if swept into the cup. It slides rather
 * than rolls, so it still shows [value] should the throw that follows be caught starting from there.
 */
class PickUpPath(val value: Int, private val startY: Float, private val restY: Float) {
    fun pose(pickUp: Float): TossPose {
        val t = pickUp.coerceIn(0f, 1f)
        val y = restY + (startY - restY) * t * t
        return TossPose(dx = 0f, dy = y - restY, yawDegrees = 0f, roll = 0f)
    }
}

/**
 * The four faces round one of a real die's axes, in the order a roll brings them round, starting
 * from [face]: the face, a random neighbour, its opposite, and the neighbour's opposite - opposite
 * faces add up to 7.
 */
fun faceRing(face: Int, seed: Int): List<Int> {
    val neighbour = (1..6).filter { it != face && it != 7 - face }.random(Random(seed))
    return listOf(face, neighbour, 7 - face, 7 - neighbour)
}

// How far the eye is from a face lying flat on the mat, in face widths: far enough that the cube
// isn't warped, near enough that a face tipping up visibly widens towards you.
private const val CUBE_EYE_DISTANCE = 4f

/**
 * A die drawn as a solid cube, tumbled [roll] quarter-turns from its resting face ([finalTurns]
 * quarter-turns is resting): the face it's tipping off and the one tipping on, each drawn by [face]
 * in the die's own style, turned in 3D about the cube's centre and projected through one shared eye
 * ([cubeFaceProjection]) - so the two meet along their shared edge - the nearer on top, each shaded
 * as it turns away. Behind them is the die's solid [body]: the outline of everything in view, with
 * the die's own [cornerPercent] corners, so the rounded edge between the two faces is solid die rather than
 * a notch the mat shows through. [ring] is the faces round the axis it's rolling about - see
 * [faceRing] - so rolling forward and back again brings the same faces round, like a real die.
 */
@Composable
fun TossedCube(
    roll: Float,
    finalTurns: Int,
    ring: List<Int>,
    body: Color,
    cornerPercent: Int,
    modifier: Modifier = Modifier,
    face: @Composable (value: Int, modifier: Modifier) -> Unit,
) {
    fun faceAt(turn: Int) = ring[(turn - finalTurns).mod(ring.size)]
    val base = floor(roll).toInt()
    val tipped = (roll - base) * 90f
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val side = constraints.maxWidth.toFloat()
        val dieWidth = maxWidth
        val dieHeight = maxHeight
        // The face tipping away and the one coming up from the near side, farthest first.
        val faces = listOf(faceAt(base) to tipped, faceAt(base + 1) to tipped - 90f)
            .map { (value, degrees) -> Triple(value, degrees, cos(degrees * PI.toFloat() / 180f)) }
            .filter { (_, _, facing) -> facing > 0.02f }
            .sortedBy { (_, _, facing) -> facing }
        val projections = faces.map { (_, degrees, _) -> cubeFaceProjection(side, degrees * PI.toFloat() / 180f) }
        // The die's silhouette: everything in view, with the die's own rounded corners. The solid
        // body fills it and every face is cut to it, so no face's painted corner pokes past the edge.
        val corners = listOf(Offset(0f, 0f), Offset(side, 0f), Offset(side, side), Offset(0f, side))
        val silhouette = roundedConvexPath(
            convexHull(projections.flatMap { projection -> corners.map { projection.map(it) } }),
            side * cornerPercent / 100f,
        )
        if (faces.size > 1) {
            Canvas(modifier = Modifier.size(dieWidth, dieHeight)) {
                // A shade darker than the faces: the edge rounds away from the light.
                drawPath(silhouette, lerp(body, Color.Black, 0.18f))
            }
        }
        for ((index, faceInView) in faces.withIndex()) {
            val (value, _, facing) = faceInView
            val projection = projections[index]
            // Drawn in a layer twice the die's size, so a face swung up past the die's own square
            // isn't clipped, and off-screen so the shading only darkens the face itself.
            Box(
                modifier = Modifier
                    .requiredSize(dieWidth * 2, dieHeight * 2)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        val inset = side / 2f
                        clipPath(Path().apply { addPath(silhouette, Offset(inset, inset)) }) {
                            withTransform({
                                translate(inset, inset)
                                transform(projection)
                                translate(-inset, -inset)
                            }) {
                                this@drawWithContent.drawContent()
                                drawRect(
                                    Color.Black.copy(alpha = (1f - facing) * 0.45f),
                                    topLeft = Offset(inset, inset),
                                    size = Size(side, side),
                                    blendMode = BlendMode.SrcAtop,
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                face(value, Modifier.size(dieWidth, dieHeight))
            }
        }
    }
}

/**
 * Where a cube face [side] pixels across lands on screen, as a perspective [Matrix] from its own
 * pixels: the face starts lying flat, facing you, as the top of a cube; the cube turns [radians]
 * about its left-right axis through its centre - the face tipping up and away - and the result is
 * seen from [CUBE_EYE_DISTANCE] face widths above that resting face, which it maps onto exactly.
 * Every face of the cube goes through the same eye, so neighbouring faces share their edge.
 */
internal fun cubeFaceProjection(side: Float, radians: Float): Matrix {
    val c = side / 2f
    val h = side / 2f
    val eye = side * CUBE_EYE_DISTANCE
    val sine = sin(radians)
    val cosine = cos(radians)
    // Depth scale: w = (eye + depth of the point) / eye, linear in the face's y.
    val a = -sine / eye
    val b = (eye + h - h * cosine) / eye
    val m = Matrix()
    m[0, 0] = 1f
    m[1, 0] = c * a
    m[3, 0] = c * b - c - a * c * c
    m[0, 1] = 0f
    m[1, 1] = cosine + c * a
    m[3, 1] = -c * cosine - h * sine + c * b - a * c * c
    m[0, 3] = 0f
    m[1, 3] = a
    m[3, 3] = b - a * c
    return m
}

/**
 * The convex polygon through [points] with every corner rounded to [radius] - the polygon shrunk by
 * [radius] and grown back by a disc of it, so each corner is a true circular arc however short the
 * edges beside it, matching a [RoundedCornerShape] exactly for a square. Too thin to shrink, it's
 * left sharp.
 */
private fun roundedConvexPath(points: List<Offset>, radius: Float): Path {
    val inner = insetConvex(points, radius)
    val path = Path()
    if (inner.isEmpty()) {
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        path.close()
        return path
    }
    val n = inner.size
    val centre = inner.reduce { sum, p -> sum + p } / n.toFloat()
    // The outward normal's angle, in degrees, of the edge from inner[i] to inner[i + 1].
    fun normalDegrees(i: Int): Float {
        val edge = inner[(i + 1) % n] - inner[i]
        var normal = Offset(edge.y, -edge.x)
        val middle = (inner[i] + inner[(i + 1) % n]) / 2f
        if ((middle - centre).x * normal.x + (middle - centre).y * normal.y < 0f) normal = -normal
        return atan2(normal.y, normal.x) * 180f / PI.toFloat()
    }
    for (i in 0 until n) {
        val start = normalDegrees((i + n - 1) % n)
        var sweep = normalDegrees(i) - start
        while (sweep > 180f) sweep -= 360f
        while (sweep < -180f) sweep += 360f
        val corner = inner[i]
        path.arcTo(Rect(corner, radius), start, sweep, forceMoveTo = i == 0)
    }
    path.close()
    return path
}

/** The convex polygon [points] with every edge moved [by] inwards - empty if nothing is left. */
private fun insetConvex(points: List<Offset>, by: Float): List<Offset> {
    val n = points.size
    // Winding: positive area is one way round, negative the other; inwards is to that side.
    val area = (0 until n).sumOf { i -> (points[i].x * points[(i + 1) % n].y - points[(i + 1) % n].x * points[i].y).toDouble() }
    val sign = if (area > 0) 1f else -1f
    var polygon = points
    for (i in 0 until n) {
        val a = points[i]
        val b = points[(i + 1) % n]
        val edge = b - a
        val length = edge.getDistance()
        if (length == 0f) continue
        val inward = Offset(-edge.y, edge.x) / length * sign
        val origin = a + inward * by
        // Keeps what's on the inward side of the moved edge (Sutherland-Hodgman, one edge).
        fun side(p: Offset) = (p - origin).x * inward.x + (p - origin).y * inward.y
        val kept = mutableListOf<Offset>()
        for (j in polygon.indices) {
            val p = polygon[j]
            val q = polygon[(j + 1) % polygon.size]
            val sp = side(p)
            val sq = side(q)
            if (sp >= 0f) kept += p
            if ((sp >= 0f) != (sq >= 0f)) kept += p + (q - p) * (sp / (sp - sq))
        }
        polygon = kept
        if (polygon.size < 3) return emptyList()
    }
    return polygon
}
