package net.zodac.dicefive.ui.achievements

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.ui.game.style.pipLayout

// Achievement icons drawn for this app where Material has nothing that fits. Each is one colour, as a
// Material glyph is - the row or banner tints it like any other - with any detail inside (a pip) cut
// out of the shape rather than drawn in another colour. Written as SVG path data on Material's 24 x 24
// grid; shown at 22dp, so nothing finer than about a unit survives.

private val INK = SolidColor(Color.Black)

/** A circle of [r] round ([cx], [cy]), as path data. */
private fun circle(cx: Float, cy: Float, r: Float): String = "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0z"

/** A [w] x [h] rectangle from ([x], [y]) with corners rounded to [r], as path data. */
private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
    "M${x + r},${y}h${w - 2 * r}a$r,$r 0 0,1 $r,${r}v${h - 2 * r}a$r,$r 0 0,1 ${-r},${r}h${-(w - 2 * r)}a$r,$r 0 0,1 ${-r},${-r}v${-(h - 2 * r)}a$r,$r 0 0,1 $r,${-r}z"

private fun icon(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply(build)
        .build()

private fun ImageVector.Builder.fill(pathData: String, evenOdd: Boolean = false) {
    addPath(addPathNodes(pathData), fill = INK, pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero)
}

/**
 * A die's face showing [value] pips, cut out of a rounded square - the six score-range collection
 * achievements count up 1 to 6 with them, one more pip for each range further up the scores.
 */
internal fun dieFaceIcon(value: Int): ImageVector = DIE_FACE_ICONS.getValue(value)

private val DIE_FACE_ICONS: Map<Int, ImageVector> by lazy {
    (1..6).associateWith { value ->
        icon("DieFace$value") {
            val face = StringBuilder(roundRect(2.5f, 2.5f, 19f, 19f, 4f))
            for (pip in pipLayout(value)) face.append(circle(2.5f + 2.6f + pip.x * 13.8f, 2.5f + 2.6f + pip.y * 13.8f, 1.85f))
            fill(face.toString(), evenOdd = true)
        }
    }
}

/**
 * All six die faces, 1 to 6, in two rows of three, each cut out of its own small rounded square - the set Full Set
 * collects, a 5x of each number.
 */
internal val DIE_FACE_SET_ICON: ImageVector by lazy {
    icon("DieFaceSet") {
        val die = 7.2f
        val gap = 1.0f
        val left = (24f - die * 3 - gap * 2) / 2
        val top = (24f - die * 2 - gap) / 2
        val faces = StringBuilder()
        for (value in 1..6) {
            val x = left + ((value - 1) % 3) * (die + gap)
            val y = top + ((value - 1) / 3) * (die + gap)
            faces.append(roundRect(x, y, die, die, 1.6f))
            for (pip in pipLayout(value)) faces.append(circle(x + 1.1f + pip.x * (die - 2.2f), y + 1.1f + pip.y * (die - 2.2f), 0.72f))
        }
        fill(faces.toString(), evenOdd = true)
    }
}

/**
 * A devil's head - a round face, two horns curling up off its brow, slanted eyes and a grin cut out of it. For Bad
 * Omen, every die cursed into chains at once.
 */
internal val DEVIL_HEAD_ICON: ImageVector by lazy {
    icon("DevilHead") {
        // The horns, each its own shape so the face doesn't cut a hole where they meet it.
        fill("M6.6,8.6Q4.2,6.2 4.2,2.2Q7.4,4.6 9.6,7.0Z")
        fill("M17.4,8.6Q19.8,6.2 19.8,2.2Q16.6,4.6 14.4,7.0Z")
        val eyes = polygon(8.0f to 11.6f, 11.0f to 13.0f, 8.6f to 14.4f) + polygon(16.0f to 11.6f, 13.0f to 13.0f, 15.4f to 14.4f)
        val grin = "M8.4,16.4Q12,20.6 15.6,16.4Q12,18.4 8.4,16.4Z"
        fill(circle(12f, 14f, 7.5f) + eyes + grin, evenOdd = true)
    }
}

/** A closed polygon through [points], as path data. */
private fun polygon(vararg points: Pair<Float, Float>): String =
    points.joinToString(separator = "L", prefix = "M", postfix = "z") { (x, y) -> "$x,$y" }

/**
 * A bullseye - two rings round a centre spot - with an arrow stuck in the middle of it, flown in from the top
 * right, its fletching clear of the rings. For Right On Target, Hit List's exact hit on a five-number target.
 */
internal val BULLSEYE_ARROW_ICON: ImageVector by lazy {
    icon("BullseyeArrow") {
        val cx = 10f
        val cy = 14f
        // Even-odd, outermost first: a ring, a gap, a ring, a gap, then the spot.
        fill(circle(cx, cy, 8.5f) + circle(cx, cy, 6.6f) + circle(cx, cy, 4.7f) + circle(cx, cy, 2.9f) + circle(cx, cy, 1.4f), evenOdd = true)
        // The arrow's line, out from the centre towards the top right.
        val dx = 0.7071f
        val dy = -0.7071f
        // Across the arrow, at right angles to it.
        val nx = 0.7071f
        val ny = 0.7071f
        fun at(along: Float, across: Float) = (cx + along * dx + across * nx) to (cy + along * dy + across * ny)
        val shaftHalf = 0.75f
        val tail = 13.5f
        val fletchStart = 10.5f
        fill(polygon(at(0f, shaftHalf), at(tail, shaftHalf), at(tail, -shaftHalf), at(0f, -shaftHalf)))
        // Two vanes, swept back from the shaft on either side.
        for (side in listOf(1f, -1f)) {
            fill(
                polygon(
                    at(fletchStart, side * shaftHalf),
                    at(tail, side * shaftHalf),
                    at(tail + 1.5f, side * 2.5f),
                    at(fletchStart + 1.5f, side * 2.5f),
                ),
            )
        }
    }
}

/** [pathData] as a round-ended line [width] wide - for the thin strokes of a numeral, which a filled outline would clog at this size. */
private fun ImageVector.Builder.line(pathData: String, width: Float) {
    addPath(addPathNodes(pathData), stroke = INK, strokeLineWidth = width, strokeLineCap = StrokeCap.Round)
}

/**
 * The upper bonus's 63 in a ring with a diagonal struck through both - the "no" sign. For Probability? Never Heard of
 * Her, the bonus that one die would have earned, missed. The slash runs through the middle of both numerals.
 */
internal val STRUCK_THROUGH_63_ICON: ImageVector by lazy {
    icon("StruckThrough63") {
        line(circle(12f, 12f, 10f), 1.8f)
        // A 6: a stem sweeping down the left to a round bowl.
        line("M10.6,8.2C8.2,8.5 6.8,10.5 6.8,13.5", 1.5f)
        line(circle(8.9f, 13.6f, 2.1f), 1.5f)
        // A 3: a flat top over a bowl.
        line("M13.2,8.3H16.9L15,11C16.7,11 17.5,12.1 17.5,13.4C17.5,14.8 16.5,15.9 15.2,15.9C14.3,15.9 13.7,15.6 13.2,15.1", 1.5f)
        line("M5.2,5.2L18.8,18.8", 1.8f)
    }
}

/**
 * A hexagon, the outline of a twenty-sided die seen face on, with a 20 inside it. For Nat 20, the win in D20 mode.
 * Everything is a line, so the white shows through the numerals.
 */
internal val D20_NAT_20_ICON: ImageVector by lazy {
    icon("D20Nat20") {
        line(polygon(12f to 1.6f, 21f to 6.8f, 21f to 17.2f, 12f to 22.4f, 3f to 17.2f, 3f to 6.8f), 1.6f)
        // A 2: a hooked top, a diagonal and a flat base. The pair spans 5.9 to 17.4 with its round ends, centred on the hexagon's 12.
        line("M6.6,9.7C6.6,8.5 7.5,7.9 8.6,7.9C9.8,7.9 10.6,8.5 10.6,9.6C10.6,10.6 10,11.2 9.1,12.2L6.6,15.9H10.9", 1.4f)
        // A 0: a tall oval.
        line("M15.3,7.9A2.1,3.9 0 1,0 15.3,15.9A2.1,3.9 0 1,0 15.3,7.9Z", 1.4f)
    }
}

/**
 * A googly eye: a ring round a loose pupil that has slid down to the lower left. For Everything Everywhere All At Once, the
 * win with every modifier piled on. The pupil is solid, so it reads at 22dp; the ring is a line, so the white shows.
 */
internal val GOOGLY_EYE_ICON: ImageVector by lazy {
    icon("GooglyEye") {
        line(circle(12f, 12f, 9.5f), 1.8f)
        fill(circle(9.2f, 14.2f, 4.2f))
    }
}
