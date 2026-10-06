package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.dicefive.ui.theme.GoldAccent

// A strawberry's size, as a fraction of the pip area, and how far apart they sit (see drawPipPositions).
private const val STRAWBERRY_RADIUS_FRACTION = 0.14f
private const val STRAWBERRY_SPREAD = 1.07f

// The piped border: how many rosettes run along each side, and how far in from the edge they sit, as
// fractions of the face.
private const val ROSETTES_PER_SIDE = 7
private const val ROSETTE_INSET = 0.075f

private val StrawberryRed = Shades(Color(0xFF8E0716), Color(0xFFDD1F2F), Color(0xFFFF7A82))
private val LeafGreen = Shades(Color(0xFF1F5A12), Color(0xFF3E9A26), Color(0xFF8ED36A))
private val SeedGold = Color(0xFFFFE08A)

/**
 * A cake, seen from above: its top smoothed with [frosting] (falling to [shade] where it's thinner),
 * a ring of piped rosettes round the edge, and fresh strawberries for pips. On a coloured roll the
 * frosting takes the roll's face colours and every strawberry its pip colour.
 */
class CakeDiceStyle(
    override val id: String,
    private val frosting: Color,
    private val shade: Color,
    private val berry: Shades = StrawberryRed,
    private val leaf: Shades = LeafGreen,
    private val seed: Color = SeedGold,
    private val heldRing: Color = GoldAccent,
    private val dollop: Color? = null,
) : DiceStyle, Swatched {
    override val swatch: Color = frosting

    override fun recoloured(palette: DieColourPalette): DiceStyle {
        val pip = Shades.of(palette.pip)
        return CakeDiceStyle(id, palette.diceTop, palette.diceBottom, pip, pip, pip.light, palette.heldRing, dollop?.let { lerp(palette.diceTop, Color.White, 0.6f) })
    }

    override val cornerPercent: Int = STYLED_DIE_CORNER_PERCENT

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val pattern = naturalPatternSeed(value)
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.linearGradient(listOf(frosting, shade)),
            edge = shade,
            pipColor = berry.base,
            pipShape = PipShape.CUSTOM,
            heldRingColor = heldRing,
            customPips = { drawCachedSurface(CakeBerries(value, berry, leaf, seed, dollop, pattern)) { drawBerries(value, pattern) } },
        ) {
            drawCachedSurface(CakeTop(frosting, shade, pattern)) { paintFrosting(frosting, shade, pattern) }
        }
    }

    private data class CakeBerries(val value: Int, val berry: Shades, val leaf: Shades, val seed: Color, val dollop: Color?, val pattern: Int)

    private data class CakeTop(val frosting: Color, val shade: Color, val pattern: Int)

    private fun DrawScope.drawBerries(value: Int, pattern: Int) {
        // Six berries are packed closer, so a touch smaller.
        val radius = size.minDimension * STRAWBERRY_RADIUS_FRACTION * if (value == 6) 0.88f else 1f
        val random = Random(pattern)
        val cream = dollop
        if (cream != null) {
            // The cream first, under every berry: a swirled dollop a little wider than the berry.
            drawPipPositions(value, STRAWBERRY_SPREAD) { centre -> drawRosette(centre + Offset(0f, radius * 0.25f), radius * 1.15f, cream, lerp(cream, Color.Black, 0.18f), 30f) }
        }
        drawPipPositions(value, STRAWBERRY_SPREAD) { centre -> drawStrawberry(centre, radius, (random.nextFloat() - 0.5f) * 50f, berry, leaf, seed, random.nextInt()) }
    }
}

/**
 * The frosted top: palette-knife swirls a shade lighter and darker, a soft sheen, then the piped
 * rosettes round the edge, each a little swirl with its own shadow.
 */
private fun DrawScope.paintFrosting(frosting: Color, shade: Color, pattern: Int) {
    val random = Random(pattern * 17 + 3)
    val w = size.width
    val h = size.height
    val m = size.minDimension
    // Swirls: soft arcs where the knife dragged the frosting.
    repeat(9) {
        val centre = Offset(random.nextFloat() * w, random.nextFloat() * h)
        val r = m * (0.12f + random.nextFloat() * 0.25f)
        val start = random.nextFloat() * 360f
        val tone = if (random.nextBoolean()) lerp(frosting, Color.White, 0.6f) else shade
        drawArc(
            tone.copy(alpha = 0.45f),
            startAngle = start,
            sweepAngle = 70f + random.nextFloat() * 120f,
            useCenter = false,
            topLeft = centre - Offset(r, r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = Stroke(width = m * (0.02f + random.nextFloat() * 0.02f), cap = StrokeCap.Round),
        )
    }
    drawRect(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent), Offset(w * 0.3f, h * 0.25f), m * 0.7f),
    )
    // The piped border: rosettes round the edge, the corners rounded off like the die's.
    val inset = m * ROSETTE_INSET
    val rosette = m * 0.062f
    val stops = roundedRectStops(inset, size.minDimension * STYLED_DIE_CORNER_PERCENT / 100f - inset * 0.6f, ROSETTES_PER_SIDE * 4)
    for (at in stops) drawRosette(at, rosette, frosting, shade, random.nextFloat() * 360f)
}

/**
 * [count] points evenly spaced round a rectangle [inset] in from this scope's edges, with corners
 * rounded to [corner] - so a border of rosettes follows the die's own rounded outline.
 */
private fun DrawScope.roundedRectStops(inset: Float, corner: Float, count: Int): List<Offset> {
    val w = size.width - inset * 2
    val h = size.height - inset * 2
    val r = corner.coerceIn(0f, minOf(w, h) / 2f)
    val straightW = w - 2 * r
    val straightH = h - 2 * r
    val arc = PI.toFloat() / 2f * r
    val perimeter = 2 * straightW + 2 * straightH + 4 * arc
    // Walks the outline clockwise from the top-left corner's end, piece by piece.
    fun pointAt(distance: Float): Offset {
        var d = distance % perimeter
        val pieces = listOf(straightW, arc, straightH, arc, straightW, arc, straightH, arc)
        for ((i, length) in pieces.withIndex()) {
            if (d <= length) {
                val t = if (length == 0f) 0f else d / length
                return when (i) {
                    0 -> Offset(inset + r + t * straightW, inset)
                    2 -> Offset(inset + w, inset + r + t * straightH)
                    4 -> Offset(inset + w - r - t * straightW, inset + h)
                    6 -> Offset(inset, inset + h - r - t * straightH)
                    else -> {
                        val corners = listOf(Offset(inset + w - r, inset + r), Offset(inset + w - r, inset + h - r), Offset(inset + r, inset + h - r), Offset(inset + r, inset + r))
                        val k = i / 2
                        val angle = -PI.toFloat() / 2f + k * PI.toFloat() / 2f + t * PI.toFloat() / 2f
                        corners[k] + Offset(cos(angle) * r, sin(angle) * r)
                    }
                }
            }
            d -= length
        }
        return Offset(inset + r, inset)
    }
    return List(count) { pointAt(perimeter * it / count) }
}

/** One piped rosette of frosting at [at], [radius] across: a swirl with a shadow below and right. */
private fun DrawScope.drawRosette(at: Offset, radius: Float, frosting: Color, shade: Color, turn: Float) {
    drawCircle(Color.Black.copy(alpha = 0.12f), radius * 1.05f, at + Offset(radius * 0.18f, radius * 0.25f))
    drawCircle(
        Brush.radialGradient(listOf(lerp(frosting, Color.White, 0.5f), frosting, lerp(frosting, shade, 0.8f)), at - Offset(radius * 0.35f, radius * 0.35f), radius * 1.4f),
        radius,
        at,
    )
    // The swirl: a spiral of a darker line, from the edge in to the peak.
    val spiral = Path()
    val turnRadians = turn * PI.toFloat() / 180f
    for (step in 0..18) {
        val t = step / 18f
        val angle = turnRadians + t * 3.4f * PI.toFloat()
        val r = radius * 0.85f * (1f - t)
        val p = at + Offset(cos(angle) * r, sin(angle) * r)
        if (step == 0) spiral.moveTo(p.x, p.y) else spiral.lineTo(p.x, p.y)
    }
    drawPath(spiral, shade.copy(alpha = 0.7f), style = Stroke(width = radius * 0.16f, cap = StrokeCap.Round))
    drawCircle(Color.White.copy(alpha = 0.7f), radius * 0.18f, at - Offset(radius * 0.3f, radius * 0.35f))
}

// A strawberry in its unit space: broad shoulders at the top, narrowing to a rounded point below.
// Built on first use, not when the file loads: a Path needs the platform's graphics.
private val StrawberryBody: Path by lazy {
    Path().apply {
        moveTo(0f, -0.62f)
        cubicTo(0.5f, -0.9f, 1.02f, -0.62f, 0.88f, -0.04f)
        cubicTo(0.74f, 0.5f, 0.3f, 0.98f, 0f, 1.02f)
        cubicTo(-0.3f, 0.98f, -0.74f, 0.5f, -0.88f, -0.04f)
        cubicTo(-1.02f, -0.62f, -0.5f, -0.9f, 0f, -0.62f)
        close()
    }
}

/**
 * A strawberry, [radius] across at [centre], turned [degrees]: a glossy red body freckled with seeds
 * in neat staggered rows, a star of green sepals and a stalk at its top, and a soft shadow under it.
 */
private fun DrawScope.drawStrawberry(centre: Offset, radius: Float, degrees: Float, berry: Shades, leaf: Shades, seed: Color, pattern: Int) {
    val random = Random(pattern)
    inUnit(centre + Offset(radius * 0.08f, radius * 0.14f), radius, degrees) { drawPath(StrawberryBody, Color.Black.copy(alpha = 0.22f)) }
    inUnit(centre, radius, degrees) {
        drawPath(StrawberryBody, Brush.radialGradient(listOf(berry.light, berry.base, berry.deep), Offset(-0.35f, -0.2f), 1.35f))
        clipPath(StrawberryBody) {
            // Seeds: each a tiny teardrop pit, in staggered rows.
            for (row in 0..5) {
                val y = -0.45f + row * 0.27f
                val offset = if (row % 2 == 0) 0f else 0.13f
                var x = -0.9f + offset
                while (x < 0.9f) {
                    val jitter = Offset((random.nextFloat() - 0.5f) * 0.06f, (random.nextFloat() - 0.5f) * 0.06f)
                    val at = Offset(x, y) + jitter
                    drawOval(berry.deep.copy(alpha = 0.55f), topLeft = at - Offset(0.055f, 0.075f), size = androidx.compose.ui.geometry.Size(0.11f, 0.15f))
                    drawOval(seed, topLeft = at - Offset(0.032f, 0.055f), size = androidx.compose.ui.geometry.Size(0.064f, 0.1f))
                    x += 0.26f
                }
            }
        }
        drawPath(StrawberryBody, berry.deep, style = Stroke(width = 0.07f))
        // A glossy highlight on its lit shoulder.
        drawOval(Color.White.copy(alpha = 0.45f), topLeft = Offset(-0.6f, -0.35f), size = androidx.compose.ui.geometry.Size(0.28f, 0.42f))
        // The sepals: five pointed leaves splayed out over the shoulders.
        for (k in 0 until 5) {
            val angle = -PI.toFloat() / 2f + (k - 2) * 0.62f + (random.nextFloat() - 0.5f) * 0.2f
            val dir = Offset(cos(angle), sin(angle))
            val side = Offset(-dir.y, dir.x)
            val base = Offset(0f, -0.62f)
            // Splayed downwards over the shoulders rather than pointing up.
            val tip = base + Offset(dir.x * 0.62f, -dir.y * 0.32f + 0.12f)
            val sepal = Path().apply {
                moveTo(base.x + side.x * 0.1f, base.y + side.y * 0.1f)
                quadraticTo((base.x + tip.x) / 2f + side.x * 0.16f, (base.y + tip.y) / 2f + side.y * 0.16f, tip.x, tip.y)
                quadraticTo((base.x + tip.x) / 2f - side.x * 0.16f, (base.y + tip.y) / 2f - side.y * 0.16f, base.x - side.x * 0.1f, base.y - side.y * 0.1f)
                close()
            }
            drawPath(sepal, leaf.base)
            drawPath(sepal, leaf.deep, style = Stroke(width = 0.05f))
        }
        drawCircle(leaf.base, 0.14f, Offset(0f, -0.64f))
        // The stalk.
        drawLine(leaf.deep, Offset(0f, -0.66f), Offset(0.08f, -0.98f), strokeWidth = 0.13f, cap = StrokeCap.Round)
        drawLine(leaf.light, Offset(-0.01f, -0.7f), Offset(0.06f, -0.94f), strokeWidth = 0.04f, cap = StrokeCap.Round)
    }
}
