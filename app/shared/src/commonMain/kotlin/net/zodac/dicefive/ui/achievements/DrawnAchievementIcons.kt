package net.zodac.dicefive.ui.achievements

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.ui.style.pipLayout
import net.zodac.dicefive.ui.theme.CrystalTrophyCyan
import net.zodac.dicefive.ui.theme.CrystalTrophyIce
import net.zodac.dicefive.ui.theme.CrystalTrophyViolet
import net.zodac.dicefive.ui.theme.CrystalTrophyWhite
import net.zodac.dicefive.ui.theme.GooglyEyePupil
import net.zodac.dicefive.ui.theme.GooglyEyeRim
import net.zodac.dicefive.ui.theme.GooglyEyeWhite
import net.zodac.dicefive.ui.theme.TrafficLightHousing
import net.zodac.dicefive.ui.theme.TricolourBlueSwatch
import net.zodac.dicefive.ui.theme.TricolourRedSwatch
import net.zodac.dicefive.ui.theme.TricolourYellowSwatch

// Achievement icons drawn for this app where Material Symbols has nothing that fits. Each is one colour, as a
// Symbol is - the row or banner tints it like any other - with any detail inside (a pip) cut out of the shape
// rather than drawn in another colour; the few in fixed colours are together at the end. Written as SVG path
// data on Material's 24 x 24 grid; shown at 22dp, so nothing finer than about a unit survives.

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

/** A closed polygon through [points], as path data. */
private fun polygon(vararg points: Pair<Float, Float>): String =
    points.joinToString(separator = "L", prefix = "M", postfix = "z") { (x, y) -> "$x,$y" }

/**
 * The 5x box's own mark (Sora Bold, as the scorecard's tile draws it) cut out of a fourteen-point burst. For 5x!, the
 * first one scored. The numeral is Sora's outline with its overlapping contours merged, so even-odd cuts it cleanly.
 */
internal val FIVE_X_BURST_ICON: ImageVector by lazy {
    icon("FiveXBurst") {
        fill(
            "M12,0.8L14.05,3.03L16.86,1.91L17.74,4.81L20.76,5.02L20.29,8.01L22.92,9.51L21.2,12L22.92,14.49L20.29,15.99L20.76,18.98L17.74,19.19L16.86,22.09L14.05,20.97L12,23.2L9.95,20.97L7.14,22.09L6.26,19.19L3.24,18.98L3.71,15.99L1.08,14.49L2.8,12L1.08,9.51L3.71,8.01L3.24,5.02L6.26,4.81L7.14,1.91L9.95,3.03z" +
                "M8.95 15.82Q8.11 15.82 7.51 15.49Q6.9 15.17 6.57 14.62Q6.25 14.07 6.25 13.4H7.78Q7.78 13.73 7.93 13.98Q8.07 14.22 8.35 14.36Q8.62 14.5 8.96 14.5Q9.35 14.5 9.62 14.34Q9.88 14.18 10.03 13.91Q10.17 13.63 10.17 13.29Q10.17 12.93 10.03 12.65Q9.89 12.38 9.62 12.21Q9.36 12.05 8.98 12.05Q8.63 12.05 8.35 12.2Q8.07 12.34 7.94 12.56H6.45L6.81 8.58H11.09V9.94H8.01L7.88 11.33Q8.02 11.23 8.17 11.13Q8.58 10.9 9.24 10.9Q10.02 10.9 10.56 11.22Q11.11 11.53 11.4 12.06Q11.7 12.58 11.7 13.23V13.35Q11.7 14 11.38 14.56Q11.06 15.12 10.45 15.47Q9.83 15.82 8.95 15.82Z" +
                "M12.14 15.63 13.69 12.95 12.2 10.34H13.87L14.82 12.14H14.98L15.88 10.34H17.45L16.05 12.91L17.75 15.63H16.07L14.93 13.71H14.78L13.7 15.63Z",
            evenOdd = true,
        )
    }
}

/**
 * A number line: three ticks along a rule, with 1, 2 and 3 (Sora Bold) over them - counting as it's first taught. For
 * I Can Count!, 1-2-3-4-5 rolled in order.
 */
internal val NUMBER_LINE_ICON: ImageVector by lazy {
    icon("NumberLine") {
        line("M2,18.5H22", 1.6f)
        line("M5,16.4V20.6M12,16.4V20.6M19,16.4V20.6", 1.6f)
        fill("M4.91 13.7V8.54H3.66V7.3H6.34V13.7Z")
        fill(
            "M9.83 13.7V12.42Q9.83 12.02 9.96 11.73Q10.09 11.43 10.38 11.19Q10.67 10.96 11.14 10.76L12.15 10.32Q12.53 10.16 12.71 9.92Q12.9 9.68 12.9 9.32Q12.9 8.94 12.67 8.7Q12.44 8.47 12 8.47Q11.55 8.47 11.32 8.71Q11.09 8.96 11.09 9.38H9.74Q9.74 8.79 10 8.32Q10.25 7.84 10.75 7.57Q11.26 7.3 12 7.3Q12.74 7.3 13.23 7.56Q13.73 7.83 13.99 8.27Q14.25 8.71 14.25 9.27V9.37Q14.25 10.08 13.86 10.55Q13.47 11.01 12.65 11.37L11.66 11.8Q11.4 11.91 11.29 12.03Q11.19 12.15 11.19 12.36V12.5H14.26V13.7Z",
        )
        fill(
            "M19 13.7Q18.27 13.7 17.74 13.43Q17.21 13.16 16.92 12.7Q16.64 12.24 16.64 11.66H17.99Q17.99 12.05 18.26 12.29Q18.52 12.54 19 12.54Q19.32 12.54 19.54 12.42Q19.76 12.3 19.88 12.09Q20 11.88 20 11.62Q20 11.2 19.74 10.94Q19.48 10.68 19 10.68Q18.74 10.68 18.55 10.75Q18.36 10.83 18.22 10.94L17.59 9.87L19.48 8.64L19.4 8.5H16.9V7.3H21.1V8.6L19.37 9.7Q19.87 9.74 20.26 9.92Q20.79 10.16 21.07 10.59Q21.36 11.03 21.36 11.6V11.7Q21.36 12.27 21.07 12.73Q20.79 13.18 20.26 13.44Q19.73 13.7 19 13.7Z",
        )
    }
}

/**
 * A kettlebell with 100 (Sora Bold) cut out of its bell. For Ton!, a game finished on exactly 100. The handle is its own
 * shape, so where it meets the bell doesn't cut a hole.
 */
internal val KETTLEBELL_100_ICON: ImageVector by lazy {
    icon("Kettlebell100") {
        fill(roundRect(6.6f, 1.8f, 10.8f, 9.4f, 3.6f) + roundRect(9f, 4.2f, 6f, 5.4f, 1.6f), evenOdd = true)
        fill(
            circle(12f, 15.2f, 7.6f) +
                "M7.6 17.64V14.36H6.8V13.57H8.51V17.64Z" +
                "M11.14 17.75Q10.65 17.75 10.3 17.59Q9.96 17.43 9.74 17.16Q9.53 16.89 9.43 16.53Q9.32 16.17 9.32 15.76V15.45Q9.32 14.89 9.52 14.44Q9.71 13.98 10.11 13.72Q10.52 13.45 11.14 13.45Q11.75 13.45 12.15 13.72Q12.55 13.98 12.75 14.44Q12.94 14.89 12.94 15.45V15.76Q12.94 16.17 12.84 16.53Q12.74 16.89 12.52 17.16Q12.31 17.43 11.96 17.59Q11.62 17.75 11.14 17.75ZM11.14 16.91Q11.59 16.91 11.81 16.59Q12.03 16.28 12.03 15.61Q12.03 14.91 11.8 14.6Q11.58 14.29 11.14 14.29Q10.68 14.29 10.46 14.6Q10.24 14.91 10.24 15.6Q10.24 16.27 10.46 16.59Q10.68 16.91 11.14 16.91Z" +
                "M15.39 17.75Q14.91 17.75 14.56 17.59Q14.22 17.43 14 17.16Q13.79 16.89 13.68 16.53Q13.58 16.17 13.58 15.76V15.45Q13.58 14.89 13.77 14.44Q13.97 13.98 14.37 13.72Q14.77 13.45 15.39 13.45Q16.01 13.45 16.41 13.72Q16.81 13.98 17 14.44Q17.2 14.89 17.2 15.45V15.76Q17.2 16.17 17.1 16.53Q16.99 16.89 16.78 17.16Q16.56 17.43 16.22 17.59Q15.88 17.75 15.39 17.75ZM15.39 16.91Q15.84 16.91 16.06 16.59Q16.28 16.28 16.28 15.61Q16.28 14.91 16.06 14.6Q15.83 14.29 15.39 14.29Q14.94 14.29 14.72 14.6Q14.49 14.91 14.49 15.6Q14.49 16.27 14.71 16.59Q14.93 16.91 15.39 16.91Z",
            evenOdd = true,
        )
    }
}

/**
 * A ring: a band with a cut gem set on top of it. For Commitment Issues, holding one number, then going back on it. The
 * gem is its own shape, so where it sits on the band doesn't cut a hole.
 */
internal val WEDDING_RING_ICON: ImageVector by lazy {
    icon("WeddingRing") {
        fill(circle(12f, 15.6f, 6.9f) + circle(12f, 15.6f, 5.1f), evenOdd = true)
        fill(polygon(9.2f to 2.2f, 14.8f to 2.2f, 17f to 5f, 12f to 9.6f, 7f to 5f))
    }
}

// Material Symbols' trophy (emoji_events), on its own 960-unit grid, its y running from -960 at the top to 0.
private const val SYMBOLS_TROPHY =
    "M280-528v-152h-80v40q0 38 22 68.5t58 43.5Zm400 0q36-13 58-43.5t22-68.5v-40h-80v152ZM440-200v-124q-49-11-87.5-41.5T296-442q-75-9-125.5-65.5T120-640v-40q0-33 23.5-56.5T200-760h80q0-33 23.5-56.5T360-840h240q33 0 56.5 23.5T680-760h80q33 0 56.5 23.5T840-680v40q0 76-50.5 132.5T664-442q-18 46-56.5 76.5T520-324v124h120q17 0 28.5 11.5T680-160q0 17-11.5 28.5T640-120H320q-17 0-28.5-11.5T280-160q0-17 11.5-28.5T320-200h120Z"

/** An icon on Material Symbols' 960-unit grid: [build] draws with y from -960 (top) to 0, as Symbols' path data does. */
private fun symbolsGridIcon(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 960f, viewportHeight = 960f)
        .addGroup(translationY = 960f)
        .apply(build)
        .clearGroup()
        .build()

/**
 * Material Symbols' trophy with a jagged crack down the cup from its rim, cut out of it. For Defeat From The Jaws Of
 * Victory, the lead that broke in the final round.
 */
internal val CRACKED_TROPHY_ICON: ImageVector by lazy {
    symbolsGridIcon("CrackedTrophy") {
        val crack = "M467,-840L520,-760L430,-650L540,-530L455,-420L515,-420L600,-530L490,-650L580,-760L527,-840Z"
        fill(SYMBOLS_TROPHY + crack, evenOdd = true)
    }
}

// ---- Fixed colours --------------------------------------------------------------------------------------------------
// The few icons whose point is a colour of their own, shown untinted (Achievement.iconTintOrUnspecified gives them
// Color.Unspecified), like the Easter Eggs' in AchievementIcons.kt.

/**
 * A googly eye: a white eye with a dark rim, its black pupil slid down to the lower right with a glint in it. For
 * Everything Everywhere All At Once, the win with every modifier piled on.
 */
internal val GOOGLY_EYE_ICON: ImageVector by lazy {
    icon("GooglyEye") {
        addPath(addPathNodes(circle(12f, 12f, 10f)), fill = SolidColor(GooglyEyeWhite), stroke = SolidColor(GooglyEyeRim), strokeLineWidth = 1.1f)
        addPath(addPathNodes(circle(15.2f, 15.4f, 4.2f)), fill = SolidColor(GooglyEyePupil))
        addPath(addPathNodes(circle(16.6f, 13.6f, 1.1f)), fill = SolidColor(GooglyEyeWhite))
    }
}

/**
 * A traffic light, a charcoal housing with its three visors, lit in Tricolour's red, yellow and blue from the top. For
 * Tricolourful, the win in Tricolour mode.
 */
internal val TRICOLOUR_TRAFFIC_LIGHT_ICON: ImageVector by lazy {
    icon("TricolourTrafficLight") {
        val visors = listOf(3.6f, 9.4f, 15.2f).joinToString("") { top ->
            polygon(7f to top, 3.4f to top, 7f to top + 4.6f) + polygon(17f to top, 20.6f to top, 17f to top + 4.6f)
        }
        addPath(addPathNodes(roundRect(7f, 1.5f, 10f, 21f, 3f) + visors), fill = SolidColor(TrafficLightHousing))
        for ((y, colour) in listOf(6f to TricolourRedSwatch, 12f to TricolourYellowSwatch, 18f to TricolourBlueSwatch)) {
            addPath(addPathNodes(circle(12f, y, 2.4f)), fill = SolidColor(colour))
        }
    }
}

/**
 * Three dice in a triangle, one in each of Tricolour's colours - red showing three on top, yellow five and blue two
 * below - their pips cut out. For Tricolour Me Impressed, every colour box scored in one game, and Tricolour's Play
 * leaderboard icon (PlayConsoleLeaderboards.kt).
 */
val TRICOLOUR_DICE_ICON: ImageVector by lazy {
    icon("TricolourDice") {
        val size = 10.5f
        val dice = listOf(
            Triple(12f - size / 2, 1.25f, 3) to TricolourRedSwatch,
            Triple(1.25f, 12.25f, 5) to TricolourYellowSwatch,
            Triple(12.25f, 12.25f, 2) to TricolourBlueSwatch,
        )
        for ((place, colour) in dice) {
            val (x, y, value) = place
            addPath(addPathNodes(die(x, y, size, value)), fill = SolidColor(colour), pathFillType = PathFillType.EvenOdd)
        }
    }
}

/** A [size] square die from ([x], [y]) showing [value], its pips cut out - in the proportions of the die faces above. */
private fun die(x: Float, y: Float, size: Float, value: Int): String =
    roundRect(x, y, size, size, size * 0.21f) + pipLayout(value).joinToString("") { pip ->
        circle(x + size * (0.137f + pip.x * 0.726f), y + size * (0.137f + pip.y * 0.726f), size * 0.097f)
    }

/**
 * Material Symbols' trophy in crystal: white at the top left through ice and cyan to violet at the bottom right. For
 * Completionist, every other achievement unlocked.
 */
internal val CRYSTAL_TROPHY_ICON: ImageVector by lazy {
    symbolsGridIcon("CrystalTrophy") {
        val crystal = Brush.linearGradient(
            0f to CrystalTrophyWhite,
            0.35f to CrystalTrophyIce,
            0.65f to CrystalTrophyCyan,
            1f to CrystalTrophyViolet,
            start = Offset(120f, -840f),
            end = Offset(840f, -120f),
        )
        addPath(addPathNodes(SYMBOLS_TROPHY), fill = crystal)
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
 * A grey alien's head - a domed skull tapering to a narrow chin, with two long slanted eyes cut out. For Chance Encounter,
 * 25 or more in all three of Third Wind's Chance slots: a close encounter of the third kind.
 */
internal val ALIEN_HEAD_ICON: ImageVector by lazy {
    icon("AlienHead") {
        fill(
            "M12,1.6C6.6,1.6 3,5.6 3,10.4C3,15.4 8.4,22.4 12,22.4C15.6,22.4 21,15.4 21,10.4C21,5.6 17.4,1.6 12,1.6Z" +
                "M5.2,9.6C7.4,9.4 10,11.3 10.9,13.8C8.6,14.4 5.7,12.6 5.2,9.6Z" +
                "M18.8,9.6C16.6,9.4 14,11.3 13.1,13.8C15.4,14.4 18.3,12.6 18.8,9.6Z",
            evenOdd = true,
        )
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
