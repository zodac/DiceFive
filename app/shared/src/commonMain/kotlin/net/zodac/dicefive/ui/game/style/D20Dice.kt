package net.zodac.dicefive.ui.game.style

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import net.zodac.dicefive.ui.theme.GoldAccent

/** A point or direction in the D20's own 3D space. */
internal data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = Vec3(x * k, y * k, z * k)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun normalised(): Vec3 = this * (1f / sqrt(this dot this))
}

/** One face of the icosahedron: its three corners (as indices into [D20.vertices]) and the number printed on it. */
internal class D20Face(val corners: List<Int>, val number: Int, val centre: Vec3, val normal: Vec3)

/**
 * A real icosahedron, numbered like a real D20: every number from 1 to 20 on one face each, and
 * every pair of opposite faces adding up to 21. Built once from its twelve vertices.
 */
internal object D20 {
    private const val PHI = 1.618034f

    val vertices: List<Vec3> = listOf(
        Vec3(0f, 1f, PHI), Vec3(0f, -1f, PHI), Vec3(0f, 1f, -PHI), Vec3(0f, -1f, -PHI),
        Vec3(1f, PHI, 0f), Vec3(-1f, PHI, 0f), Vec3(1f, -PHI, 0f), Vec3(-1f, -PHI, 0f),
        Vec3(PHI, 0f, 1f), Vec3(-PHI, 0f, 1f), Vec3(PHI, 0f, -1f), Vec3(-PHI, 0f, -1f),
    )

    /** How far a vertex is from the centre - what the drawing scales to fit. */
    val circumradius: Float = sqrt(vertices.first() dot vertices.first())

    val faces: List<D20Face> = buildFaces()

    private fun buildFaces(): List<D20Face> {
        // Every set of three vertices that are all an edge (length 2) apart is a face.
        fun adjacent(a: Int, b: Int) = (vertices[a] - vertices[b]).let { it dot it } in 3.9f..4.1f
        val corners = mutableListOf<List<Int>>()
        for (a in vertices.indices) for (b in a + 1 until vertices.size) for (c in b + 1 until vertices.size) {
            if (adjacent(a, b) && adjacent(b, c) && adjacent(a, c)) corners += listOf(a, b, c)
        }
        val centres = corners.map { (a, b, c) -> (vertices[a] + vertices[b] + vertices[c]) * (1f / 3f) }
        // Number each opposite pair n and 21 - n, so every pair sums to 21 as on a real D20. Which
        // of the pair takes the low number alternates from side to side of the die, so high and low
        // numbers sit mixed together as on real dice, rather than all the low ones clustering.
        val side = Vec3(1f, 0.3f, 0.2f)
        val numbers = IntArray(corners.size)
        var next = 1
        for (i in corners.indices) {
            if (numbers[i] != 0) continue
            val opposite = centres.indices.first { j -> (centres[j] + centres[i]).let { it dot it } < 0.01f }
            val lowHere = ((centres[i] dot side) > 0f) == (next % 2 == 1)
            numbers[if (lowHere) i else opposite] = next
            numbers[if (lowHere) opposite else i] = 21 - next
            next++
        }
        return corners.indices.map { i -> D20Face(corners[i], numbers[i], centres[i], centres[i].normalised()) }
    }

    fun face(number: Int): D20Face = faces.first { it.number == number }
}

/**
 * Which way the camera looks at the die: [right], [up] and [towards] are the camera's axes in the
 * die's own space.
 */
internal class D20View(val right: Vec3, val up: Vec3, val towards: Vec3) {
    fun cameraSpace(p: Vec3) = Vec3(p dot right, p dot up, p dot towards)

    /** This view spun [degrees] about an [axis] of the die's own - see [rotate]. */
    fun turned(axis: Vec3, degrees: Float) = D20View(rotate(right, axis, degrees), rotate(up, axis, degrees), rotate(towards, axis, degrees))

    companion object {
        /**
         * Looking straight at [front], turned so one of its corners points up - the classic D20
         * view, a triangle pointing up in a hexagon.
         */
        fun facing(front: D20Face): D20View {
            val towards = front.normal
            val up = (D20.vertices[front.corners.first()] - front.centre).normalised()
            return D20View(up cross towards, up, towards)
        }
    }
}

internal val D20_VIEWS: Map<Int, D20View> = (1..6).associateWith { D20View.facing(D20.face(it)) }

/** [v] rotated [degrees] about the unit [axis] (Rodrigues' rotation formula). */
private fun rotate(v: Vec3, axis: Vec3, degrees: Float): Vec3 {
    val radians = degrees * PI.toFloat() / 180f
    val c = cos(radians)
    val s = sin(radians)
    return v * c + (axis cross v) * s + axis * ((axis dot v) * (1f - c))
}

// How strongly every number but the roll shows - there to make it a D20, not to be read.
private const val D20_OTHER_NUMBER_ALPHA = 0.4f

// Mid-roll, the die tumbles through a full turn this often about each of two axes; at rest it lies
// on its rolled face, twisted up to this far either way so no two landings look alike.
private const val D20_TUMBLE_MILLIS = 420
private const val D20_REST_TWIST_DEGREES = 30f

// Light from above, left and in front, in camera space.
private val D20_LIGHT = Vec3(-0.4f, 0.6f, 1f).normalised()

/**
 * The die's outline from [view] - the convex hull of its corners, a hexagon - in camera space.
 */
private fun silhouette(view: D20View): List<Offset> {
    val points = D20.vertices.map { view.cameraSpace(it) }.map { Offset(it.x, -it.y) }.sortedWith(compareBy({ it.x }, { it.y }))
    fun cross(o: Offset, a: Offset, b: Offset) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    val lower = mutableListOf<Offset>()
    for (p in points) {
        while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0f) lower.removeAt(lower.size - 1)
        lower += p
    }
    val upper = mutableListOf<Offset>()
    for (p in points.asReversed()) {
        while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0f) upper.removeAt(upper.size - 1)
        upper += p
    }
    return lower.dropLast(1) + upper.dropLast(1)
}

/**
 * A D20: a real icosahedron with all twenty numbers on its twenty faces, drawn in 3D with the ten
 * faces towards you shaded and their numbers foreshortened as they turn away. Only its front face
 * is ever the roll, though, and a roll only ever lands on 1-6 - it's still a six-sided die as far as
 * the rules are concerned; it just looks like a D20. The rolled number is printed larger and at full
 * strength, the rest faded back, so the roll reads at a glance. 6 and 9 are underlined, as on real dice.
 */
class D20DiceStyle(
    override val id: String,
    private val light: Color,
    private val dark: Color,
    private val numberColour: Color,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = light
    override val tumblesItself: Boolean = true

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val dieIndex = LocalDieIndex.current
        val tumbling = LocalDieTumbling.current
        val rest = D20_VIEWS[value] ?: D20_VIEWS.getValue(1)
        val view = if (tumbling) {
            // Only composed mid-roll, so a die at rest asks for no frames at all.
            val spin by rememberInfiniteTransition(label = "d20Tumble").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(D20_TUMBLE_MILLIS, easing = LinearEasing)),
                label = "d20Spin",
            )
            // Two different axes per die, at different speeds, so it rolls through every kind of
            // position rather than spinning like a coin - and no two dice tumble alike.
            val axes = remember(dieIndex) {
                val random = Random(dieIndex * 53 + 7)
                fun axis() = Vec3(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f).normalised()
                axis() to axis()
            }
            // From one fixed starting view, not the face the tray happens to be flickering through
            // this tick - so the spin is continuous, only snapping to the roll once it lands.
            D20_VIEWS.getValue(1).turned(axes.first, spin).turned(axes.second, spin * 1.7f)
        } else {
            // Lying on its rolled face, twisted by an amount of its own for this die and this roll.
            val twist = (Random(dieIndex * 31 + value).nextFloat() * 2f - 1f) * D20_REST_TWIST_DEGREES
            rest.turned(rest.towards, twist)
        }
        val outline = silhouette(view)
        val shape = GenericShape { size, _ ->
            val scale = size.minDimension / 2f / D20.circumradius
            outline.forEachIndexed { i, p ->
                val x = size.width / 2 + p.x * scale
                val y = size.height / 2 + p.y * scale
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        val measurer = rememberTextMeasurer()
        val style = TextStyle(color = numberColour, fontSize = 40.sp, fontWeight = FontWeight.Bold)

        Canvas(modifier = modifier.dieShadow(shape)) {
            val scale = size.minDimension / 2f / D20.circumradius
            val centre = Offset(size.width / 2, size.height / 2)
            fun toScreen(p: Vec3) = centre + Offset(p.x * scale, -p.y * scale)

            for (face in D20.faces) {
                val normal = view.cameraSpace(face.normal)
                if (normal.z <= 0f) continue
                val isRoll = !tumbling && face.number == value
                val corners = face.corners.map { toScreen(view.cameraSpace(D20.vertices[it])) }
                val facet = Path().apply {
                    moveTo(corners[0].x, corners[0].y)
                    lineTo(corners[1].x, corners[1].y)
                    lineTo(corners[2].x, corners[2].y)
                    close()
                }
                val lit = (normal dot D20_LIGHT).coerceIn(0f, 1f)
                drawPath(facet, lerp(dark, light, 0.35f + 0.65f * lit))
                drawPath(facet, dark.copy(alpha = 0.6f), style = Stroke(width = 0.8.dp.toPx()))

                // The number, printed flat on its face: laid out along the face's own "up" (camera
                // up, pressed flat onto the face, so every number is as upright as it can be) and
                // squashed by however far the face has turned away.
                val decoration = if (face.number == 6 || face.number == 9) TextDecoration.Underline else null
                // Only the rolled number at full strength; every other face's is faded back, so the
                // roll reads at a glance even at game size, tumbled across the mat.
                val colour = if (isRoll) numberColour else numberColour.copy(alpha = D20_OTHER_NUMBER_ALPHA)
                val text = measurer.measure(face.number.toString(), style.copy(color = colour, textDecoration = decoration))
                val faceUp = (Vec3(0f, 1f, 0f) - normal * normal.y).normalised()
                val faceRight = faceUp cross normal
                // Camera units per text pixel: a number stands about 0.7 units tall on its face - the
                // rolled one a little taller.
                val height = when {
                    isRoll -> 0.8f
                    face.number >= 10 -> 0.62f
                    else -> 0.7f
                }
                val perPixel = height / text.size.height * scale
                val middle = toScreen(view.cameraSpace(face.centre))
                val matrix = Matrix().apply {
                    values[Matrix.ScaleX] = faceRight.x * perPixel
                    values[Matrix.SkewY] = -faceRight.y * perPixel
                    values[Matrix.SkewX] = -faceUp.x * perPixel
                    values[Matrix.ScaleY] = faceUp.y * perPixel
                    values[Matrix.TranslateX] = middle.x
                    values[Matrix.TranslateY] = middle.y
                }
                withTransform({ transform(matrix) }) {
                    drawText(text, topLeft = Offset(-text.size.width / 2f, -text.size.height / 2f))
                }
            }

            val ring = Path().apply {
                outline.forEachIndexed { i, p ->
                    val at = centre + p * scale
                    if (i == 0) moveTo(at.x, at.y) else lineTo(at.x, at.y)
                }
                close()
            }
            drawPath(ring, if (held) heldRing else dark, style = Stroke(width = if (held) 2.dp.toPx() else 1.dp.toPx()))
        }
    }
}
