package net.zodac.dicefive.ui.achievements

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
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
