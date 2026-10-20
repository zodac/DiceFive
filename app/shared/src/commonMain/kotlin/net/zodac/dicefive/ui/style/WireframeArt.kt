package net.zodac.dicefive.ui.style

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import net.zodac.dicefive.ui.theme.GoldAccent

private const val WIREFRAME_CORNER_PERCENT = 10

/** How many cells the mesh across a Wireframe die's face has, each way. */
private const val WIREFRAME_MESH_CELLS = 4

/** How many sides a Wireframe pip has - a low-poly circle. */
private const val WIREFRAME_PIP_SIDES = 8

// The editor's axis colours on the Wireframe mat's gizmo: X red, Y green.
private val WireframeAxisX = Color(0xFFFF5A5A)
private val WireframeAxisY = Color(0xFF5AFF7A)

/**
 * Wireframe dice: a model in a 3D editor, before it's textured. A dark [face] meshed into triangles in
 * [line], a vertex at every corner of the mesh, a glowing outline, and pips that are low-poly octagons,
 * lightly filled so they read at a glance. Static: nothing moves.
 */
class WireframeDiceStyle(
    override val id: String,
    private val face: Color,
    private val line: Color,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = line
    override val bodyColor: Color = face
    override val cornerPercent: Int = WIREFRAME_CORNER_PERCENT

    override fun recoloured(palette: DieColourPalette): DiceStyle = WireframeDiceStyle(id, palette.diceTop, palette.pip, palette.heldRing)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        Canvas(modifier = modifier.dieShadow(RoundedCornerShape(WIREFRAME_CORNER_PERCENT))) {
            drawWireframeFace(value)
            val corner = CornerRadius(size.minDimension * WIREFRAME_CORNER_PERCENT / 100f)
            if (held) drawRoundRect(heldRing, cornerRadius = corner, style = Stroke(width = 2.dp.toPx()))
        }
    }

    private fun DrawScope.drawWireframeFace(value: Int) {
        val corner = CornerRadius(size.minDimension * WIREFRAME_CORNER_PERCENT / 100f)
        drawRoundRect(face, cornerRadius = corner)

        // The mesh: a grid, each cell split corner to corner, inset so it stays inside the rounded outline.
        val inset = size.minDimension * 0.06f
        val cell = (size.minDimension - inset * 2) / WIREFRAME_MESH_CELLS
        val thin = 0.6.dp.toPx()
        for (i in 0..WIREFRAME_MESH_CELLS) {
            val at = inset + i * cell
            drawLine(line.copy(alpha = 0.22f), Offset(at, inset), Offset(at, size.height - inset), thin)
            drawLine(line.copy(alpha = 0.22f), Offset(inset, at), Offset(size.width - inset, at), thin)
        }
        for (row in 0 until WIREFRAME_MESH_CELLS) {
            for (column in 0 until WIREFRAME_MESH_CELLS) {
                drawLine(
                    line.copy(alpha = 0.12f),
                    Offset(inset + column * cell, inset + (row + 1) * cell),
                    Offset(inset + (column + 1) * cell, inset + row * cell),
                    thin,
                )
            }
        }
        val vertex = 0.9.dp.toPx()
        for (row in 0..WIREFRAME_MESH_CELLS) {
            for (column in 0..WIREFRAME_MESH_CELLS) {
                drawCircle(line.copy(alpha = 0.55f), vertex, Offset(inset + column * cell, inset + row * cell))
            }
        }

        // The outline, with a soft glow either side of it.
        for ((width, alpha) in listOf(4f to 0.12f, 2.5f to 0.25f)) {
            drawRoundRect(line.copy(alpha = alpha), cornerRadius = corner, style = Stroke(width = width.dp.toPx()))
        }
        drawRoundRect(lerp(line, Color.White, 0.2f), cornerRadius = corner, style = Stroke(width = 1.2.dp.toPx()))

        // The pips: octagons, each with a vertex at every point and one at its centre.
        val pipInset = size.minDimension * 0.12f
        val inner = Size(size.width - pipInset * 2, size.height - pipInset * 2)
        val radius = size.minDimension * 0.095f
        val outline = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
        for (spot in pipLayout(value)) {
            val centre = Offset(pipInset + spot.x * inner.width, pipInset + spot.y * inner.height)
            val points = List(WIREFRAME_PIP_SIDES) { side ->
                val angle = 2.0 * PI * side / WIREFRAME_PIP_SIDES + PI / WIREFRAME_PIP_SIDES
                centre + Offset((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat())
            }
            val octagon = polygonPath(points)
            drawPath(octagon, line.copy(alpha = 0.4f))
            drawPath(octagon, lerp(line, Color.White, 0.35f), style = outline)
            for (point in points) drawCircle(Color.White, vertex, point)
            drawCircle(Color.White, vertex, centre)
        }
    }
}

/**
 * A Wireframe mat: an editor's viewport. A fine grid with a bolder line every fourth, a vertex at each
 * bold crossing, crop brackets in the corners, and an X/Y axis gizmo at the bottom left. Painted once.
 */
class WireframeDiceMat(id: String, palette: MatPalette) : PatternedDiceMat(id, palette) {
    override fun DrawScope.drawPattern() = drawCachedSurface("mat:$id") {
        val cell = 8.dp.toPx()
        val thin = 0.6.dp.toPx()
        val columns = (size.width / cell).toInt()
        val rows = (size.height / cell).toInt()
        // The grid is centred, so it's even at both edges.
        val left = (size.width - columns * cell) / 2
        val top = (size.height - rows * cell) / 2
        for (column in 0..columns) {
            val x = left + column * cell
            val bold = column % 4 == 0
            drawLine(palette.detail.copy(alpha = if (bold) 0.28f else 0.1f), Offset(x, 0f), Offset(x, size.height), if (bold) thin * 1.4f else thin)
        }
        for (row in 0..rows) {
            val y = top + row * cell
            val bold = row % 4 == 0
            drawLine(palette.detail.copy(alpha = if (bold) 0.28f else 0.1f), Offset(0f, y), Offset(size.width, y), if (bold) thin * 1.4f else thin)
        }
        for (column in 0..columns step 4) {
            for (row in 0..rows step 4) drawCircle(palette.detail.copy(alpha = 0.6f), 1.2.dp.toPx(), Offset(left + column * cell, top + row * cell))
        }

        // Crop brackets in each corner.
        val inset = 7.dp.toPx()
        val arm = 14.dp.toPx()
        val bracket = 1.6.dp.toPx()
        val ink = lerp(palette.detail, Color.White, 0.2f).copy(alpha = 0.85f)
        for ((x, dx) in listOf(inset to 1f, size.width - inset to -1f)) {
            for ((y, dy) in listOf(inset to 1f, size.height - inset to -1f)) {
                drawLine(ink, Offset(x, y), Offset(x + arm * dx, y), bracket, StrokeCap.Round)
                drawLine(ink, Offset(x, y), Offset(x, y + arm * dy), bracket, StrokeCap.Round)
            }
        }

        // The axis gizmo, just inside the bottom-left bracket.
        val origin = Offset(inset + 9.dp.toPx(), size.height - inset - 9.dp.toPx())
        val length = 16.dp.toPx()
        val head = 4.dp.toPx()
        val shaft = 1.6.dp.toPx()
        drawLine(WireframeAxisX, origin, origin + Offset(length, 0f), shaft, StrokeCap.Round)
        drawPath(arrowHead(origin + Offset(length + head, 0f), Offset(-head, -head * 0.6f), Offset(-head, head * 0.6f)), WireframeAxisX)
        drawLine(WireframeAxisY, origin, origin + Offset(0f, -length), shaft, StrokeCap.Round)
        drawPath(arrowHead(origin + Offset(0f, -length - head), Offset(-head * 0.6f, head), Offset(head * 0.6f, head)), WireframeAxisY)
        drawCircle(Color.White, 1.6.dp.toPx(), origin)
    }

    private fun arrowHead(tip: Offset, back1: Offset, back2: Offset) = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x + back1.x, tip.y + back1.y)
        lineTo(tip.x + back2.x, tip.y + back2.y)
        close()
    }
}
