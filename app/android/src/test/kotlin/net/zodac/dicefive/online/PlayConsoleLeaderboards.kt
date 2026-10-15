package net.zodac.dicefive.online

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.ui.game.style.pipLayout
import net.zodac.dicefive.ui.theme.OnPrimaryContainer
import net.zodac.dicefive.ui.theme.TricolourBlueSwatch
import net.zodac.dicefive.ui.theme.TricolourRedSwatch
import net.zodac.dicefive.ui.theme.TricolourYellowSwatch

// Each mode's Play leaderboard icon, for Play Console only - the game never shows them. Drawn like the achievements'
// icons: in their gold on their tile, bar Tricolour's dice, in the mode's own three colours. Written as SVG path data on
// Material's 24 x 24 grid, with any detail (a pip) cut out of its shape.

private val GOLD = OnPrimaryContainer

/** A circle of [r] round ([cx], [cy]), as path data. */
private fun circle(cx: Float, cy: Float, r: Float): String = "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0z"

/** A [w] x [h] rectangle from ([x], [y]) with corners rounded to [r], as path data. */
private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
    "M${x + r},${y}h${w - 2 * r}a$r,$r 0 0,1 $r,${r}v${h - 2 * r}a$r,$r 0 0,1 ${-r},${r}h${-(w - 2 * r)}a$r,$r 0 0,1 ${-r},${-r}v${-(h - 2 * r)}a$r,$r 0 0,1 $r,${-r}z"

private fun polygon(vararg points: Pair<Float, Float>): String =
    points.joinToString(separator = "L", prefix = "M", postfix = "z") { (x, y) -> "${x},${y}" }

/** A [size] square die from ([x], [y]) showing [value], its pips cut out - in the proportions of the achievements' die faces. */
private fun die(x: Float, y: Float, size: Float, value: Int): String =
    roundRect(x, y, size, size, size * 0.21f) + pipLayout(value).joinToString("") { pip ->
        circle(x + size * (0.137f + pip.x * 0.726f), y + size * (0.137f + pip.y * 0.726f), size * 0.097f)
    }

private fun icon(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply(build)
        .build()

private fun ImageVector.Builder.fill(pathData: String, colour: Color = GOLD) {
    addPath(addPathNodes(pathData), fill = SolidColor(colour), pathFillType = PathFillType.EvenOdd)
}

private fun ImageVector.Builder.line(pathData: String, width: Float) {
    addPath(addPathNodes(pathData), stroke = SolidColor(GOLD), strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
}

/** The mode's leaderboard icon: what sets its game apart, drawn round a die. */
internal val GameMode.leaderboardIcon: ImageVector
    get() = when (this) {
        GameMode.STANDARD -> STANDARD_ICON
        GameMode.TRICOLOUR -> TRICOLOUR_ICON
        GameMode.THIRD_WIND -> THIRD_WIND_ICON
        GameMode.STUD -> STUD_ICON
        GameMode.D20 -> D20_ICON
        GameMode.HIT_LIST -> HIT_LIST_ICON
        GameMode.QUICKFIRE -> QUICKFIRE_ICON
    }

/** One die showing five, for the five-dice game as it comes. */
private val STANDARD_ICON: ImageVector by lazy {
    icon("Standard") { fill(die(2.5f, 2.5f, 19f, 5)) }
}

/** Three dice in a triangle, one in each of the mode's colours. */
private val TRICOLOUR_ICON: ImageVector by lazy {
    icon("Tricolour") {
        val size = 10.5f
        fill(die(12f - size / 2, 1.25f, size, 3), TricolourRedSwatch)
        fill(die(1.25f, 12.25f, size, 5), TricolourYellowSwatch)
        fill(die(12.25f, 12.25f, size, 2), TricolourBlueSwatch)
    }
}

/** A die showing three, blown along by three gusts - the wind's curl on the outer two. */
private val THIRD_WIND_ICON: ImageVector by lazy {
    icon("ThirdWind") {
        fill(die(9.5f, 5.5f, 13f, 3))
        line("M1.5,9H5.6A1.7,1.7 0 1,0 3.9,7.3", 1.5f)
        line("M1,12H7.2", 1.5f)
        line("M1.5,15H5.6A1.7,1.7 0 1,1 3.9,16.7", 1.5f)
    }
}

/** Seven small dice, in rows of two, three and two: the seven rolled, of which five are held. */
private val STUD_ICON: ImageVector by lazy {
    icon("Stud") {
        val size = 6.2f
        val gap = 1f
        val rowTop = listOf(12f - size * 1.5f - gap, 12f - size / 2, 12f + size / 2 + gap)
        // No six: its pips run together at this size.
        val values = listOf(listOf(4, 2), listOf(3, 5, 1), listOf(2, 4))
        for ((row, rowValues) in values.withIndex()) {
            val left = 12f - (rowValues.size * size + (rowValues.size - 1) * gap) / 2
            for ((column, value) in rowValues.withIndex()) fill(die(left + column * (size + gap), rowTop[row], size, value))
        }
    }
}

/** A twenty-sided die seen face on: a hexagon with its facets drawn in, round the triangle facing out. */
private val D20_ICON: ImageVector by lazy {
    icon("D20") {
        val top = 12f to 1.6f
        val upperRight = 21f to 6.8f
        val lowerRight = 21f to 17.2f
        val bottom = 12f to 22.4f
        val lowerLeft = 3f to 17.2f
        val upperLeft = 3f to 6.8f
        val a = 12f to 7.6f
        val b = 17f to 16.2f
        val c = 7f to 16.2f
        line(polygon(top, upperRight, lowerRight, bottom, lowerLeft, upperLeft), 1.5f)
        line(polygon(a, b, c), 1.5f)
        for ((from, to) in listOf(top to a, upperLeft to a, upperRight to a, upperLeft to c, lowerLeft to c, bottom to c, upperRight to b, lowerRight to b, bottom to b)) {
            line("M${from.first},${from.second}L${to.first},${to.second}", 1.1f)
        }
    }
}

/** A die showing one in a sight's crosshairs: the targets called. */
private val HIT_LIST_ICON: ImageVector by lazy {
    icon("HitList") {
        line(circle(12f, 12f, 9.3f), 1.6f)
        line("M12,1V6.2M12,17.8V23M1,12H6.2M17.8,12H23", 1.6f)
        fill(die(8.25f, 8.25f, 7.5f, 1))
    }
}

/** A die struck by lightning, the bolt cut out of it: the shortest game. */
private val QUICKFIRE_ICON: ImageVector by lazy {
    icon("Quickfire") {
        fill(roundRect(2.5f, 2.5f, 19f, 19f, 19f * 0.21f) + polygon(14f to 4.8f, 6.6f to 13.4f, 11.2f to 13.4f, 9.6f to 19.4f, 17.4f to 10.4f, 12.8f to 10.4f))
    }
}
