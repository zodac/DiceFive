package net.zodac.dicefive.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/**
 * A small circular badge showing 1-2 digits, drawn as seven-segment "LCD" glyphs rather than a
 * real font - used for both the Small/Large Straight run-length badge and the 5x bonus-count
 * badge, so the two look like one shared design instead of a hand-drawn digit next to a system
 * font one (which is what happened before this existed: the straight badges were flattened vector
 * assets with their own blocky digit shapes, and the 5x badge was a plain Compose `Text` in
 * the ambient font - visibly different typefaces side by side).
 */
@Composable
fun SegmentBadge(count: Int, color: Color, modifier: Modifier = Modifier) {
    val digits = count.coerceIn(0, 99).toString().map { it - '0' }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF0F211D))
            .border(1.dp, color.copy(alpha = 0.6f), CircleShape),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Proportions (a digit cell a third of the badge's width, three-fifths of its height)
            // match the original hand-authored single-digit vector badges this replaces. A second
            // digit is scaled down a bit and the pair packed side by side, rather than recomputed
            // from scratch - values here only ever run 0-13 or so (game-length bounded), never
            // more than two digits.
            val cellHeight = size.height * 0.58f
            val cellWidth = cellHeight / SEGMENT_DIGIT_ASPECT
            val scale = if (digits.size == 1) 1f else 0.8f
            val digitWidth = cellWidth * scale
            val digitHeight = cellHeight * scale
            val gap = digitWidth * 0.25f
            val totalWidth = digitWidth * digits.size + gap * (digits.size - 1)
            val startX = (size.width - totalWidth) / 2f
            val startY = (size.height - digitHeight) / 2f
            digits.forEachIndexed { index, digit ->
                drawSegmentDigit(
                    digit = digit,
                    origin = Offset(startX + index * (digitWidth + gap), startY),
                    width = digitWidth,
                    height = digitHeight,
                    color = color,
                )
            }
        }
    }
}

/** Height/width ratio of one digit cell, from the original badges' 8x14 (of a 24-wide viewport) proportions. */
private const val SEGMENT_DIGIT_ASPECT = 14f / 8f

/** The seven segments of a classic LCD/calculator digit: A top, B top-right, C bottom-right,
 * D bottom, E bottom-left, F top-left, G middle. */
private enum class Segment { A, B, C, D, E, F, G }

private val DIGIT_SEGMENTS: Map<Int, Set<Segment>> = mapOf(
    0 to setOf(Segment.A, Segment.B, Segment.C, Segment.D, Segment.E, Segment.F),
    1 to setOf(Segment.B, Segment.C),
    2 to setOf(Segment.A, Segment.B, Segment.G, Segment.E, Segment.D),
    3 to setOf(Segment.A, Segment.B, Segment.G, Segment.C, Segment.D),
    4 to setOf(Segment.F, Segment.G, Segment.B, Segment.C),
    5 to setOf(Segment.A, Segment.F, Segment.G, Segment.C, Segment.D),
    6 to setOf(Segment.A, Segment.F, Segment.G, Segment.E, Segment.C, Segment.D),
    7 to setOf(Segment.A, Segment.B, Segment.C),
    8 to setOf(Segment.A, Segment.B, Segment.C, Segment.D, Segment.E, Segment.F, Segment.G),
    9 to setOf(Segment.A, Segment.B, Segment.C, Segment.D, Segment.F, Segment.G),
)

private fun DrawScope.drawSegmentDigit(
    digit: Int,
    origin: Offset,
    width: Float,
    height: Float,
    color: Color,
) {
    val lit = DIGIT_SEGMENTS.getValue(digit)
    fun segment(left: Float, top: Float, right: Float, bottom: Float) {
        drawRect(
            color = color,
            topLeft = Offset(origin.x + left * width, origin.y + top * height),
            size = Size((right - left) * width, (bottom - top) * height),
        )
    }
    // Fractions of the digit's own cell (not the badge) - the same seven rectangles the original
    // ic_run_badge_4/5 vector assets used, normalized from their 8x14-of-24 absolute coordinates.
    if (Segment.A in lit) segment(0f, 0f, 1f, 0.1429f)
    if (Segment.F in lit) segment(0f, 0.1429f, 0.25f, 0.4286f)
    if (Segment.B in lit) segment(0.75f, 0.1429f, 1f, 0.4286f)
    if (Segment.G in lit) segment(0f, 0.4286f, 1f, 0.5714f)
    if (Segment.E in lit) segment(0f, 0.5714f, 0.25f, 0.8571f)
    if (Segment.C in lit) segment(0.75f, 0.5714f, 1f, 0.8571f)
    if (Segment.D in lit) segment(0f, 0.8571f, 1f, 1f)
}
