package net.zodac.dicefive.ui.style

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp as lerpFloat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.dicefive_d20
import net.zodac.dicefive.ui.game.DICE_TOSS_MILLIS
import net.zodac.dicefive.ui.theme.GoldAccent
import org.jetbrains.compose.resources.Font

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

internal val D20_VIEWS: Map<Int, D20View> = (1..20).associateWith { D20View.facing(D20.face(it)) }

/** [v] rotated [degrees] about the unit [axis] (Rodrigues' rotation formula). */
private fun rotate(v: Vec3, axis: Vec3, degrees: Float): Vec3 {
    val radians = degrees * PI.toFloat() / 180f
    val c = cos(radians)
    val s = sin(radians)
    return v * c + (axis cross v) * s + axis * ((axis dot v) * (1f - c))
}

// How strongly every number but the roll shows - there to make it a D20, not to be read.
private const val D20_OTHER_NUMBER_ALPHA = 0.4f

// Room for all twenty numbers' layouts (the 6 and 9 are underlined, so differ), so none is ever measured twice.
private const val D20_TEXT_CACHE = 32

// Mid-roll, the die starts out tumbling through a full turn this often about each of two axes,
// slowing to a stop as the toss ends; at rest it lies on its rolled face, twisted up to this far
// either way so no two landings look alike.
private const val D20_TUMBLE_MILLIS = 420
private const val D20_REST_TWIST_DEGREES = 30f

// The rolled number fades up between these fractions of the toss.
private const val D20_EMPHASIS_START = 0.35f
private const val D20_EMPHASIS_END = 0.8f

// How tall a number's digits stand on their face, in camera units: every other face's, and the rolled one's - about as
// large as a facet holds, a two-digit number being wider than it is tall and the facet narrower than it is high.
private const val ONE_DIGIT_HEIGHT = 0.425f
private const val TWO_DIGIT_HEIGHT = 0.375f
private const val ROLL_ONE_DIGIT_HEIGHT = 0.64f
private const val ROLL_TWO_DIGIT_HEIGHT = 0.575f

/** How tall DiceFive D20's digits are, baseline to top, as a fraction of the font size: numbers are sized by their ink, not their line. */
internal const val D20_DIGIT_HEIGHT_EM = 0.73f

// Light from above, left and in front, in camera space.
private val D20_LIGHT = Vec3(-0.4f, 0.6f, 1f).normalised()

/**
 * The die's outline from [view] - the convex hull of its corners, a hexagon - in camera space.
 */
private fun silhouette(view: D20View): List<Offset> =
    convexHull(D20.vertices.map { view.cameraSpace(it) }.map { Offset(it.x, -it.y) })

/**
 * How much the rolled number stands out, 0-1: it's known before the toss starts, so it fades up as
 * the tumble slows ([tumbleMillis] into the toss) rather than only once the die has stopped. Full at
 * rest (null).
 */
private fun rollEmphasis(tumbleMillis: Float?): Float {
    if (tumbleMillis == null) return 1f
    return ((tumbleMillis / DICE_TOSS_MILLIS - D20_EMPHASIS_START) / (D20_EMPHASIS_END - D20_EMPHASIS_START)).coerceIn(0f, 1f)
}

/**
 * How a D20 that rolled [value] lies at rest: on that face, twisted by an amount of its own for this
 * die ([dieIndex]) and this roll, so no two landings look alike.
 */
private fun restingView(value: Int, dieIndex: Int): D20View {
    val rest = D20_VIEWS[value] ?: D20_VIEWS.getValue(1)
    val twist = (Random(dieIndex * 31 + value).nextFloat() * 2f - 1f) * D20_REST_TWIST_DEGREES
    return rest.turned(rest.towards, twist)
}

/**
 * How a D20 lies [tumbleMillis] into a tumble - turning about two axes of its own per die, at
 * different speeds, so it rolls through every kind of position rather than spinning like a coin, and
 * no two dice tumble alike - or, when null, how it lies at rest ([restingView]).
 */
private fun view(value: Int, dieIndex: Int, tumbleMillis: Float?): D20View {
    if (tumbleMillis == null) return restingView(value, dieIndex)
    val random = Random(dieIndex * 53 + 7)
    fun axis() = Vec3(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f).normalised()
    val first = axis()
    val second = axis()
    // The spin left to do eases to nothing as the toss ends, so the die settles onto its resting
    // view rather than snapping to it.
    val remaining = (1f - (tumbleMillis / DICE_TOSS_MILLIS).coerceIn(0f, 1f)).let { it * it }
    val spin = DICE_TOSS_MILLIS / D20_TUMBLE_MILLIS * 360f * remaining
    return restingView(value, dieIndex).turned(first, spin).turned(second, spin * 1.7f)
}

/** A shape tracing [outline] (in the die's own space, as [silhouette] gives it) at whatever size it's drawn. */
private fun silhouetteShape(outline: List<Offset>): Shape = GenericShape { size, _ ->
    val scale = size.minDimension / 2f / D20.circumradius
    outline.forEachIndexed { i, p ->
        val x = size.width / 2 + p.x * scale
        val y = size.height / 2 + p.y * scale
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/**
 * A D20: a real icosahedron with all twenty numbers on its twenty faces, drawn in 3D with the ten
 * faces towards you shaded and their numbers foreshortened as they turn away. Only its front face
 * is ever the roll, though. In every mode but D20 a roll only lands on 1-6, so it's a six-sided die
 * that looks like a D20; in D20 mode it lands on any of the twenty. The rolled number is printed as large as its facet holds and at full
 * strength, the rest faded back, so the roll reads at a glance. 6 and 9 are underlined, as on real dice, and the numbers are set in
 * Andika's digits (1 with a foot, 7 crossed) so none reads as another.
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
    override val lockedChainReach: Float = 0.6f

    override fun recoloured(palette: DieColourPalette): DiceStyle =
        D20DiceStyle(id, palette.diceTop, palette.diceBottom, palette.pip, palette.heldRing)

    // The measurer every die of this style draws its numbers with, rebuilt only if the font, density or direction change.
    private var measurer: TextMeasurer? = null
    private var measurerFor: Triple<FontFamily.Resolver, Density, LayoutDirection>? = null

    private fun sharedMeasurer(resolver: FontFamily.Resolver, density: Density, direction: LayoutDirection): TextMeasurer {
        val key = Triple(resolver, density, direction)
        measurer?.takeIf { measurerFor == key }?.let { return it }
        return TextMeasurer(resolver, density, direction, D20_TEXT_CACHE).also {
            measurer = it
            measurerFor = key
        }
    }

    /**
     * Its exact outline, twist and all at rest - and mid-tumble, the outline of the very orientation
     * it's being drawn in at [tumbleMillis], so the shadow turns with it.
     */
    override fun shadowShape(value: Int, dieIndex: Int, tumbleMillis: Float?): Shape =
        silhouetteShape(silhouette(view(value, dieIndex, tumbleMillis)))

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val dieIndex = LocalDieIndex.current
        val tumbleMillis = LocalDieTumbleMillis.current
        val view = view(value, dieIndex, tumbleMillis)
        val rollEmphasis = rollEmphasis(tumbleMillis)
        val outline = silhouette(view)
        val shape = silhouetteShape(outline)
        // Every number is measured once and kept, for every die of this style: the text measurer's default cache holds 8
        // layouts, and a tumbling die shows ten numbers a frame - so each die measured them all again every frame, and
        // each had its own to fill on the first roll, which was the lag.
        val measurer = sharedMeasurer(LocalFontFamilyResolver.current, LocalDensity.current, LocalLayoutDirection.current)
        // No colour in the style (it's applied as the number is drawn), so fading a number doesn't make it a new layout.
        // The numbers are set in DiceFive D20 (Andika's digits: a 1 with a foot, a 7 with a bar), so a 1 never reads as a 7.
        // Keyed on the font itself: where a font resource loads asynchronously, the first composition gets a stand-in.
        val font = Font(Res.font.dicefive_d20)
        val style = remember(font) { TextStyle(fontFamily = FontFamily(font), fontSize = 40.sp, fontWeight = FontWeight.Bold) }

        Canvas(modifier = modifier.dieShadow(shape)) {
            val scale = size.minDimension / 2f / D20.circumradius
            val digitPixels = style.fontSize.toPx() * D20_DIGIT_HEIGHT_EM
            val centre = Offset(size.width / 2, size.height / 2)
            fun toScreen(p: Vec3) = centre + Offset(p.x * scale, -p.y * scale)

            for (face in D20.faces) {
                val normal = view.cameraSpace(face.normal)
                if (normal.z <= 0f) continue
                val isRoll = face.number == value
                val emphasis = if (isRoll) rollEmphasis else 0f
                val corners = face.corners.map { toScreen(view.cameraSpace(D20.vertices[it])) }
                val facet = Path().apply {
                    moveTo(corners[0].x, corners[0].y)
                    lineTo(corners[1].x, corners[1].y)
                    lineTo(corners[2].x, corners[2].y)
                    close()
                }
                val lit = (normal dot D20_LIGHT).coerceIn(0f, 1f)
                // The rolled face is lit fully, so its number stands out against the brightest facet whatever the angle.
                drawPath(facet, lerp(dark, light, lerpFloat(0.35f + 0.65f * lit, 1f, emphasis)))
                drawPath(facet, dark.copy(alpha = 0.6f), style = Stroke(width = 0.8.dp.toPx()))

                // The number, printed flat on its face: laid out along the face's own "up" (camera
                // up, pressed flat onto the face, so every number is as upright as it can be) and
                // squashed by however far the face has turned away.
                val decoration = if (face.number == 6 || face.number == 9) TextDecoration.Underline else null
                // Only the rolled number at full strength; every other face's is faded back, so the
                // roll reads at a glance even at game size, tumbled across the mat.
                val colour = numberColour.copy(alpha = lerpFloat(D20_OTHER_NUMBER_ALPHA, 1f, emphasis))
                val text = measurer.measure(face.number.toString(), if (decoration != null) style.copy(textDecoration = decoration) else style)
                val faceUp = (Vec3(0f, 1f, 0f) - normal * normal.y).normalised()
                val faceRight = faceUp cross normal
                // Camera units per text pixel, from the digits' own height: the font's line is much taller than its
                // digits, and by how much varies from font to font.
                val height = lerpFloat(if (face.number >= 10) TWO_DIGIT_HEIGHT else ONE_DIGIT_HEIGHT, if (face.number >= 10) ROLL_TWO_DIGIT_HEIGHT else ROLL_ONE_DIGIT_HEIGHT, emphasis)
                val perPixel = height / digitPixels * scale
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
                    // Centred on the digits, not the line, whose space above them (room for accents) is far deeper than below.
                    drawText(text, color = colour, topLeft = Offset(-text.size.width / 2f, digitPixels / 2f - text.firstBaseline))
                }
            }

            val ring = Path().apply {
                outline.forEachIndexed { i, p ->
                    val at = centre + p * scale
                    if (i == 0) moveTo(at.x, at.y) else lineTo(at.x, at.y)
                }
                close()
            }
            drawPath(ring, if (held) heldRing else dark, style = dieEdgeStroke(held))
        }
    }
}

/** The black D20, its numbers white. */
val D20BlackDiceStyle: D20DiceStyle = D20DiceStyle("d20_black", Color(0xFF34343A), Color(0xFF0E0E11), Color.White)

/** The white D20 - the dice D20 mode is played with unless the player has picked another D20 style. */
val D20WhiteDiceStyle: D20DiceStyle = D20DiceStyle("d20_white", Color(0xFFFBFBF8), Color(0xFFB9BCC2), Color(0xFF1A1A1A))
