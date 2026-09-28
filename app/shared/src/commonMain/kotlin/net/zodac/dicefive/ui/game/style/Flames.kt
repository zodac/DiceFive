package net.zodac.dicefive.ui.game.style

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Draws a single stylized flame silhouette - a pointed tip, a wavy waist on each side, a rounded
 * base - filling the box described by [topLeft]/[size]. Shared by the fire theme's dice cup (a
 * couple of licks up the body) so both pieces of "fire" art use the same brushstroke.
 */
fun DrawScope.drawFlame(topLeft: Offset, size: Size, color: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(topLeft.x + w * 0.5f, topLeft.y)
        quadraticTo(
            topLeft.x + w * 0.95f, topLeft.y + h * 0.35f,
            topLeft.x + w * 0.68f, topLeft.y + h * 0.55f,
        )
        quadraticTo(
            topLeft.x + w * 1.05f, topLeft.y + h * 0.85f,
            topLeft.x + w * 0.56f, topLeft.y + h,
        )
        lineTo(topLeft.x + w * 0.44f, topLeft.y + h)
        quadraticTo(
            topLeft.x - w * 0.05f, topLeft.y + h * 0.85f,
            topLeft.x + w * 0.32f, topLeft.y + h * 0.55f,
        )
        quadraticTo(
            topLeft.x + w * 0.05f, topLeft.y + h * 0.35f,
            topLeft.x + w * 0.5f, topLeft.y,
        )
        close()
    }
    drawPath(path, color = color)
}

/**
 * A two-tone flame: [outerColor] for the full lick, [innerColor] for a smaller hot core nested near
 * the base - the same silhouette scaled down and re-anchored, rather than a second hand-authored
 * shape.
 */
fun DrawScope.drawFlame(topLeft: Offset, size: Size, outerColor: Color, innerColor: Color) {
    drawFlame(topLeft, size, outerColor)
    val innerSize = Size(size.width * 0.55f, size.height * 0.6f)
    val innerTopLeft = Offset(
        topLeft.x + (size.width - innerSize.width) / 2f,
        topLeft.y + size.height - innerSize.height,
    )
    drawFlame(innerTopLeft, innerSize, innerColor)
}
