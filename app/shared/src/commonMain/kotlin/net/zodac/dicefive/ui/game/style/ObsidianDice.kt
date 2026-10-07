package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.dicefive.ui.theme.GoldAccent

// A lava pip's molten opening, as a fraction of the pip area, and how far off its pip's spot it can burst through.
// Vents are laid out VENT_SPREAD times wider than a plain die's pips, so each one - its glow, cracks and spatter - reads on its own.
private const val VENT_RADIUS_FRACTION = 0.085f
private const val VENT_SHOVE = 0.025f
// Where the vents are laid out: StyledDie's usual pip area.
private const val VENT_SPREAD = 1.18f
private val PIP_AREA_INSET = 6.dp

/**
 * The colours of molten rock, from the dark crust that skins it to the [core] at its hottest: [deep]
 * where it's cooling, [glow] for its body and the light it throws, [core] where it's brightest.
 */
class Lava(val crust: Color, val deep: Color, val glow: Color, val core: Color)

/**
 * Black volcanic glass, polished: a deep, glossy face with the curved ripples obsidian breaks along,
 * a sharp sheen across it - and every pip a vent where [lava] bursts through, glowing out of a
 * crusted opening, with fine cracks of light running out from it into the glass and a few spatters
 * thrown clear. Every vent and crack is different on every face of every die. On a coloured roll
 * the glass takes the roll's face colours and the lava its pip colour.
 */
class ObsidianDiceStyle(
    override val id: String,
    private val glass: Color,
    private val glassDark: Color,
    private val lava: Lava,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = lava.glow

    override fun recoloured(palette: DieColourPalette): DiceStyle {
        val pip = palette.pip
        return ObsidianDiceStyle(
            id,
            palette.diceTop,
            palette.diceBottom,
            Lava(lerp(pip, Color.Black, 0.75f), lerp(pip, Color.Black, 0.35f), pip, lerp(pip, Color.White, 0.7f)),
            palette.heldRing,
        )
    }

    override val cornerPercent: Int = STYLED_DIE_CORNER_PERCENT

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        // Seeded by the colour too, so the Lava and Blue dice burst through in different places and shapes.
        val pattern = naturalPatternSeed(value, styleSeed = id.hashCode())
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.linearGradient(listOf(glass, glassDark)),
            edge = lerp(glass, Color.White, 0.15f),
            pipColor = lava.glow,
            pipShape = PipShape.CUSTOM,
            heldRingColor = heldRing,
            // Laid out over the whole face, inset to the usual pip area: a vent pushed towards the edge
            // still has room for its glow, rather than being cut off square at the pip area's edge.
            pipPadding = 0.dp,
            customPips = {
                drawCachedSurface(Vents(value, lava, glass, pattern)) {
                    val pad = PIP_AREA_INSET.toPx()
                    inset(pad, pad) { drawVents(value, pattern) }
                }
            },
        ) {
            drawCachedSurface(GlassFace(glass, glassDark, pattern)) { paintObsidian(glass, glassDark, pattern) }
        }
    }

    private data class Vents(val value: Int, val lava: Lava, val glass: Color, val pattern: Int)

    private data class GlassFace(val glass: Color, val dark: Color, val pattern: Int)

    private fun DrawScope.drawVents(value: Int, pattern: Int) {
        val radius = size.minDimension * VENT_RADIUS_FRACTION
        val random = Random(pattern * 5 + 1)
        val shove = size.minDimension * VENT_SHOVE
        // The lava's forced its own way out: each vent pushed a little off its pip's spot, and its own size.
        drawPipPositions(value, spread = VENT_SPREAD) { centre ->
            val at = centre + Offset((random.nextFloat() - 0.5f) * 2f * shove, (random.nextFloat() - 0.5f) * 2f * shove)
            drawVent(at, radius * (0.85f + random.nextFloat() * 0.3f), lava, random.nextInt())
        }
    }
}

/**
 * Obsidian's polished face: conchoidal ripples - the shell-like curves it fractures in - as faint
 * concentric arcs round a point off the face, a soft sheen and one sharp streak of reflected light.
 */
private fun DrawScope.paintObsidian(glass: Color, dark: Color, pattern: Int) {
    val random = Random(pattern * 13 + 29)
    val w = size.width
    val h = size.height
    val m = size.minDimension
    val origin = Offset(if (random.nextBoolean()) -w * 0.2f else w * 1.2f, h * (random.nextFloat() * 1.2f - 0.1f))
    val light = lerp(glass, Color.White, 0.35f)
    for (ring in 0 until 7) {
        val r = m * (0.35f + ring * 0.17f + random.nextFloat() * 0.05f)
        drawCircle(light.copy(alpha = 0.07f + random.nextFloat() * 0.06f), r, origin, style = Stroke(width = m * (0.01f + random.nextFloat() * 0.025f)))
        drawCircle(dark.copy(alpha = 0.5f), r + m * 0.02f, origin, style = Stroke(width = m * 0.012f))
    }
    drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent), Offset(w * 0.3f, h * 0.22f), m * 0.6f))
    // The sharp streak of a window's reflection, glass-smooth.
    val streak = Path().apply {
        moveTo(w * 0.12f, h * 0.36f)
        quadraticTo(w * 0.22f, h * 0.12f, w * 0.48f, h * 0.08f)
        quadraticTo(w * 0.26f, h * 0.17f, w * 0.12f, h * 0.36f)
        close()
    }
    drawPath(streak, Color.White.copy(alpha = 0.22f))
}

/**
 * One vent of [lava] at [centre], [radius] across: its glow spilling into the glass round it, cracks
 * of light running out from it, the crusted, jagged opening, molten rock welling in it - brightest
 * at its heart - and a few drops spattered clear. [seed] makes each one its own.
 */
private fun DrawScope.drawVent(centre: Offset, radius: Float, lava: Lava, seed: Int) {
    val random = Random(seed)
    // The light it throws into the glass.
    drawCircle(Brush.radialGradient(listOf(lava.glow.copy(alpha = 0.55f), lava.glow.copy(alpha = 0f)), centre, radius * 2.1f), radius * 2.1f, centre)
    // Cracks: forking lines of light running out, fading as they go.
    val cracks = 2 + random.nextInt(3)
    repeat(cracks) { k ->
        var angle = k * 2f * PI.toFloat() / cracks + (random.nextFloat() - 0.5f) * 0.8f
        var at = centre + Offset(cos(angle), sin(angle)) * radius * 0.8f
        val path = Path().apply { moveTo(at.x, at.y) }
        val steps = 2 + random.nextInt(2)
        repeat(steps) {
            angle += (random.nextFloat() - 0.5f) * 1.1f
            at += Offset(cos(angle), sin(angle)) * radius * (0.18f + random.nextFloat() * 0.18f)
            path.lineTo(at.x, at.y)
        }
        drawPath(path, lava.glow.copy(alpha = 0.35f), style = Stroke(width = radius * 0.18f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, lava.core.copy(alpha = 0.85f), style = Stroke(width = radius * 0.05f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    // The opening: a ragged, stretched tear - no two the same shape - with no clean rim: the lava
    // bleeds out into the glass round it.
    val points = 12
    val stretch = 0.72f + random.nextFloat() * 0.5f
    val turn = random.nextFloat() * PI.toFloat()
    val lobes = 2 + random.nextInt(3)
    val lobePhase = random.nextFloat() * 2f * PI.toFloat()
    val edge = List(points) { i ->
        val a = i * 2f * PI.toFloat() / points
        val r = radius * (1f + 0.22f * sin(lobes * a + lobePhase) + (random.nextFloat() - 0.5f) * 0.45f)
        val local = Offset(cos(a) * r * stretch, sin(a) * r / stretch)
        centre + Offset(local.x * cos(turn) - local.y * sin(turn), local.x * sin(turn) + local.y * cos(turn))
    }
    val molten = edge.map { centre + (it - centre) * (0.78f + random.nextFloat() * 0.1f) }
    // Leaks: tapering rivulets seeping out of the opening, fading into the glass as they go.
    repeat(2 + random.nextInt(3)) {
        val from = edge[random.nextInt(points)]
        var angle = kotlin.math.atan2(from.y - centre.y, from.x - centre.x) + (random.nextFloat() - 0.5f) * 0.7f
        var at = from + (centre - from) * 0.25f
        val length = 3 + random.nextInt(3)
        repeat(length) { step ->
            angle += (random.nextFloat() - 0.5f) * 0.8f
            val next = at + Offset(cos(angle), sin(angle)) * radius * (0.18f + random.nextFloat() * 0.14f)
            val fade = 1f - step / length.toFloat()
            drawLine(lava.glow.copy(alpha = 0.3f * fade), at, next, strokeWidth = radius * 0.42f * fade + radius * 0.08f, cap = StrokeCap.Round)
            drawLine(lava.glow.copy(alpha = 0.85f * fade), at, next, strokeWidth = radius * 0.22f * fade + radius * 0.04f, cap = StrokeCap.Round)
            drawLine(lava.core.copy(alpha = 0.7f * fade), at, next, strokeWidth = radius * 0.07f * fade, cap = StrokeCap.Round)
            at = next
        }
    }
    // Its edge, soft: the melt's own light soaking out into the glass in a few widening, fainter rings.
    val opening = smoothPath(edge, closed = true)
    for ((spread, alpha) in listOf(0.55f to 0.18f, 0.35f to 0.3f, 0.18f to 0.5f)) {
        drawPath(opening, lava.glow.copy(alpha = alpha), style = Stroke(width = radius * spread, join = StrokeJoin.Round))
    }
    drawPath(opening, lava.deep)
    drawPath(smoothPath(molten, closed = true), Brush.radialGradient(listOf(lava.core, lava.glow, lava.deep), centre - Offset(radius * 0.1f, radius * 0.15f), radius * 0.9f))
    // Crust: only broken flakes of it, cooling here and there along the edge - not a ring round it.
    repeat(2 + random.nextInt(2)) {
        // Just inside the edge, where the melt first cools.
        val at = centre + (edge[random.nextInt(points)] - centre) * 0.82f
        val r = radius * (0.16f + random.nextFloat() * 0.14f)
        val corners = 5
        val spin = random.nextFloat() * 6.28f
        drawPath(polygonPath(List(corners) { i -> at + Offset(cos(spin + i * 6.28f / corners), sin(spin + i * 6.28f / corners)) * (r * (0.6f + random.nextFloat() * 0.6f)) }), lerp(lava.crust, lava.deep, 0.45f).copy(alpha = 0.75f))
    }
    // Skin forming on the melt: a couple of dark wisps.
    repeat(2) {
        val a = random.nextFloat() * 2f * PI.toFloat()
        val from = centre + Offset(cos(a), sin(a)) * radius * 0.45f
        drawLine(lava.deep.copy(alpha = 0.7f), from, from + Offset(cos(a + 1.8f), sin(a + 1.8f)) * radius * 0.35f, strokeWidth = radius * 0.08f, cap = StrokeCap.Round)
    }
    // It's bursting through: a few glowing drops thrown out over the glass.
    repeat(random.nextInt(3)) {
        val a = random.nextFloat() * 2f * PI.toFloat()
        val at = centre + Offset(cos(a), sin(a)) * radius * (1.25f + random.nextFloat() * 0.5f)
        val r = radius * (0.08f + random.nextFloat() * 0.08f)
        drawCircle(lava.glow.copy(alpha = 0.5f), r * 2f, at)
        drawCircle(lava.core, r, at)
    }
}
