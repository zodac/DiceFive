package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.dicefive.ui.theme.GoldAccent

/** How a stone is cut, seen from above. */
enum class GemCut {
    /** A round brilliant: the classic diamond. */
    ROUND,

    /** A brilliant stretched into an oval, as rubies, sapphires and topaz often are. */
    OVAL,

    /** A brilliant on a rounded square. */
    CUSHION,

    /** Step-cut: a rectangle with its corners clipped, in concentric bands - the emerald's own cut. */
    EMERALD,
}

/**
 * A gemstone: [cut] the way it's cut, and its colours - [deep] for its shadowed facets and its
 * outline, [base] its body colour, [light] its lit facets. [fire] adds a few facets in spectral
 * colours, as a diamond's dispersion does.
 */
class Gem(
    val cut: GemCut,
    val deep: Color,
    val base: Color,
    val light: Color,
    val fire: Boolean = false,
)

/**
 * The gem each value's pips are, 1 to 6: an imperial topaz, a ruby, an emerald, a sapphire, an
 * amethyst and a diamond - each in its usual cut.
 */
val GemsByValue: List<Gem> = listOf(
    Gem(GemCut.OVAL, deep = Color(0xFF7A3005), base = Color(0xFFDB7410), light = Color(0xFFFFC870)),
    Gem(GemCut.CUSHION, deep = Color(0xFF3F000D), base = Color(0xFFC0102C), light = Color(0xFFFF6E80)),
    Gem(GemCut.EMERALD, deep = Color(0xFF00351D), base = Color(0xFF0C8546), light = Color(0xFF74E3A6)),
    Gem(GemCut.OVAL, deep = Color(0xFF05154A), base = Color(0xFF1A42B4), light = Color(0xFF80AAFF)),
    Gem(GemCut.CUSHION, deep = Color(0xFF290845), base = Color(0xFF7738BC), light = Color(0xFFD6ABFF)),
    Gem(GemCut.ROUND, deep = Color(0xFF3A4654), base = Color(0xFFB9C7D6), light = Color(0xFFF2F7FC), fire = true),
)

// A gem's radius, as a fraction of the pip area.
private const val GEM_RADIUS_FRACTION = 0.14f

/**
 * Gem dice: a standard die face whose pips are cut gemstones, a different stone for every
 * value - see [GemsByValue]. Each stone is a little different from every other of its kind, the way
 * natural stones are. On a coloured roll every gem is cut from the roll's pip colour instead, so the
 * colour still reads.
 */
class GemDiceStyle(
    override val id: String,
    private val light: Color,
    private val dark: Color,
    private val gems: List<Gem> = GemsByValue,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    init {
        require(gems.size == 6) { "A gem die needs a gem for each of its six values" }
    }

    override val swatch: Color = light

    override fun recoloured(palette: DieColourPalette): DiceStyle {
        val pip = palette.pip
        val gem = Gem(GemCut.ROUND, deep = lerp(pip, Color.Black, 0.6f), base = pip, light = lerp(pip, Color.White, 0.55f))
        return GemDiceStyle(id, palette.diceTop, palette.diceBottom, List(6) { gem }, palette.heldRing)
    }

    override val cornerPercent: Int = STYLED_DIE_CORNER_PERCENT

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val gem = gems[value - 1]
        // Every face of every die cut differently - they're meant to be natural stones.
        val seed = naturalPatternSeed(value)
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.linearGradient(listOf(light, dark)),
            edge = dark,
            pipColor = gem.base,
            pipShape = PipShape.CUSTOM,
            heldRingColor = heldRing,
            // Both painted once per face and size, then stamped: a toss redraws two faces of every die each frame.
            customPips = { drawCachedSurface(GemFace(value, gem, dark, seed)) { drawGems(value, gem, dark, seed) } },
        ) {
            drawCachedSurface(GemFaceSurface(light, dark, seed)) { paintGemFace(light, dark, seed) }
        }
    }
}

private data class GemFace(val value: Int, val gem: Gem, val socket: Color, val seed: Int)

private fun DrawScope.drawGems(value: Int, gem: Gem, socket: Color, seed: Int) {
    val radius = size.minDimension * GEM_RADIUS_FRACTION
    val random = Random(seed)
    drawPipPositions(value) { centre -> drawGem(centre, radius, gem, random.nextInt(), socket = socket) }
}

private data class GemFaceSurface(val light: Color, val dark: Color, val seed: Int)

/**
 * A gem die's face, so the stones sit on a surface rather than a flat colour: soft clouding where the
 * material is a touch lighter or darker, a broad sheen across it from the top left, the edges falling
 * a little into shadow, and a fine grain.
 */
private fun DrawScope.paintGemFace(light: Color, dark: Color, seed: Int) {
    val random = Random(seed * 31 + 7)
    val w = size.width
    val h = size.height
    repeat(5) {
        val at = Offset(random.nextFloat() * w, random.nextFloat() * h)
        val r = size.minDimension * (0.25f + random.nextFloat() * 0.3f)
        val tint = if (random.nextBoolean()) light else dark
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.35f), Color.Transparent), at, r), r, at)
    }
    drawRect(
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0f)),
            start = Offset(0f, h * 0.15f),
            end = Offset(w * 0.85f, h),
        ),
    )
    drawRect(Brush.radialGradient(listOf(Color.Transparent, dark.copy(alpha = 0.35f)), Offset(w / 2f, h / 2f), size.minDimension * 0.75f))
    repeat((w * h / (size.minDimension * size.minDimension) * 220).toInt()) {
        drawCircle(
            (if (random.nextBoolean()) Color.White else Color.Black).copy(alpha = 0.035f),
            radius = size.minDimension * 0.006f,
            center = Offset(random.nextFloat() * w, random.nextFloat() * h),
        )
    }
}

/** One facet: its corners, and how far out from the table it lies - 0 for the table itself. */
private class Facet(val points: List<Offset>, val ring: Int)

/**
 * A cut stone seen from above, [radius] across, at [centre] - drawn as artwork rather than a
 * photograph: every facet a flat fill in one of a few tones of [gem]'s colour (lit towards the top
 * left, shadowed away from it, and broken up the way a gem's facets are), inked in its deep colour
 * with a bolder outline, a crisp white highlight and a single sparkle - inlaid in a socket cut into a
 * surface of [socket]'s colour when there is one, or resting with a soft shadow. [seed] decides everything that
 * makes this stone its own: its angle, its slightly uneven edge, which facets catch the light, and
 * where it sparkles.
 */
internal fun DrawScope.drawGem(centre: Offset, radius: Float, gem: Gem, seed: Int, socket: Color? = null) {
    val random = Random(seed)
    val rotation = (random.nextFloat() - 0.5f) * if (gem.cut == GemCut.ROUND) 0.8f else 0.75f
    val stretch = 1f + (random.nextFloat() - 0.5f) * 0.1f
    val outline = gemOutline(gem.cut, centre, radius, rotation, stretch, random)

    if (socket != null) {
        // Inlaid: a shallow socket cut into the face round the stone, in shadow at its top left
        // where its wall blocks the light, fading out towards the bottom right - no rim of its own.
        val cut = outline.map { centre + (it - centre) * 1.12f }
        drawPath(
            polygon(cut),
            Brush.linearGradient(
                listOf(lerp(socket, Color.Black, 0.45f).copy(alpha = 0.55f), Color.Transparent),
                start = centre - Offset(radius, radius),
                end = centre + Offset(radius * 0.6f, radius * 0.6f),
            ),
        )
    } else {
        // A soft shadow under it.
        drawPath(polygon(outline.map { it + Offset(radius * 0.07f, radius * 0.11f) }), Color.Black.copy(alpha = 0.22f))
    }
    drawPath(polygon(outline), gem.base)

    val facets = if (gem.cut == GemCut.EMERALD) stepFacets(outline, centre) else brilliantFacets(outline, centre)
    val tones = listOf(gem.deep, lerp(gem.deep, gem.base, 0.55f), gem.base, lerp(gem.base, gem.light, 0.55f), gem.light)
    // The light comes from the top left; a facet facing it is lit, one facing away shadowed.
    val lightAngle = (PI * 1.25).toFloat()
    for (facet in facets) {
        val mid = facet.points.fold(Offset.Zero) { sum, p -> sum + p } / facet.points.size.toFloat()
        val toward = cos(atan2(mid - centre) - lightAngle)
        val level = if (facet.ring == 0) {
            3
        } else {
            (2f + 1.6f * toward + (random.nextFloat() - 0.5f) * 2.2f - facet.ring * 0.25f).roundToInt().coerceIn(0, tones.size - 1)
        }
        val fill = if (gem.fire && facet.ring > 0 && random.nextFloat() < 0.18f) lerp(tones[level], Fire[random.nextInt(Fire.size)], 0.45f) else tones[level]
        drawPath(polygon(facet.points), fill)
    }
    // The ink: thin lines between facets, a bolder line round the stone.
    val ink = lerp(gem.deep, Color.Black, 0.25f)
    for (facet in facets) drawPath(polygon(facet.points), ink.copy(alpha = 0.45f), style = Stroke(width = radius * 0.035f))
    drawPath(polygon(outline), ink, style = Stroke(width = radius * 0.09f))
    // A crisp highlight across the top-left of the table, and one sparkle.
    val table = facets.first().points
    val highlight = listOf(table[table.size - 1], table[0], (table[0] + centre) / 2f, (table[table.size - 1] + centre) / 2f)
    drawPath(polygon(highlight), Color.White.copy(alpha = 0.75f))
    val sparkleAt = centre + Offset(radius * (0.25f + random.nextFloat() * 0.2f), -radius * (0.35f + random.nextFloat() * 0.2f))
    drawSparkle(sparkleAt, radius * (0.24f + random.nextFloat() * 0.08f))
}

// A diamond's fire: flecks of the spectrum split out of white light.
private val Fire = listOf(Color(0xFFFF5A5A), Color(0xFFFFB347), Color(0xFFFFF27A), Color(0xFF6CFF9C), Color(0xFF6AB8FF), Color(0xFFB98CFF))

// A brilliant's outline is traced as this many points: two for each of its eight main facets.
private const val BRILLIANT_POINTS = 16

/**
 * The outline of a stone of [cut], [radius] across at [centre], turned [rotation] radians and
 * stretched sideways by [stretch] - each corner nudged in or out a little by [random], so no two
 * stones' edges are quite the same.
 */
private fun gemOutline(cut: GemCut, centre: Offset, radius: Float, rotation: Float, stretch: Float, random: Random): List<Offset> {
    val points = when (cut) {
        GemCut.ROUND -> List(BRILLIANT_POINTS) { i -> polar(i * 2f * PI.toFloat() / BRILLIANT_POINTS, 1f, 1f) }
        GemCut.OVAL -> List(BRILLIANT_POINTS) { i -> polar(i * 2f * PI.toFloat() / BRILLIANT_POINTS, 0.8f, 1.08f) }
        GemCut.CUSHION -> List(BRILLIANT_POINTS) { i ->
            val angle = i * 2f * PI.toFloat() / BRILLIANT_POINTS
            // A superellipse: a square with well-rounded corners.
            val c = cos(angle)
            val s = sin(angle)
            val r = 1f / (abs(c).pow(3.2f) + abs(s).pow(3.2f)).pow(1f / 3.2f)
            Offset(c * r * 0.9f, s * r * 0.9f)
        }
        GemCut.EMERALD -> {
            val w = 0.78f
            val h = 1f
            val clip = 0.28f
            listOf(
                Offset(-w + clip, -h), Offset(w - clip, -h), Offset(w, -h + clip), Offset(w, h - clip),
                Offset(w - clip, h), Offset(-w + clip, h), Offset(-w, h - clip), Offset(-w, -h + clip),
            )
        }
    }
    val c = cos(rotation)
    val s = sin(rotation)
    return points.map { p ->
        val wobble = 1f + (random.nextFloat() - 0.5f) * 0.12f
        val x = p.x * stretch * wobble
        val y = p.y * wobble
        Offset(centre.x + radius * (x * c - y * s), centre.y + radius * (x * s + y * c))
    }
}

private fun polar(angle: Float, xRadius: Float, yRadius: Float) = Offset(cos(angle) * xRadius, sin(angle) * yRadius)

/**
 * A brilliant's crown, simplified, from the 16-point [outline]: the octagonal table, eight star
 * facets round it, eight kite-shaped bezel facets reaching the girdle, and eight girdle facets
 * between them.
 */
private fun brilliantFacets(outline: List<Offset>, centre: Offset): List<Facet> {
    fun scaled(p: Offset, k: Float) = centre + (p - centre) * k
    val main = List(8) { outline[it * 2] }
    val between = List(8) { outline[it * 2 + 1] }
    val table = main.map { scaled(it, 0.54f) }
    val star = between.map { scaled(it, 0.76f) }
    return buildList {
        add(Facet(table, ring = 0))
        for (k in 0 until 8) {
            val next = (k + 1) % 8
            val previous = (k + 7) % 8
            add(Facet(listOf(table[k], table[next], star[k]), ring = 1))
            add(Facet(listOf(table[k], star[previous], main[k], star[k]), ring = 2))
            add(Facet(listOf(star[k], main[k], between[k], main[next]), ring = 3))
        }
    }
}

/** A step cut's bands, from its 8-point [outline]: three rings of facets stepping in to a large flat table. */
private fun stepFacets(outline: List<Offset>, centre: Offset): List<Facet> {
    val scales = listOf(1f, 0.84f, 0.7f, 0.57f)
    val rings = scales.map { k -> outline.map { centre + (it - centre) * k } }
    return buildList {
        add(Facet(rings.last(), ring = 0))
        for (ring in 0 until scales.size - 1) {
            val outer = rings[ring]
            val inner = rings[ring + 1]
            for (k in outline.indices) {
                val next = (k + 1) % outline.size
                add(Facet(listOf(outer[k], outer[next], inner[next], inner[k]), ring = scales.size - 1 - ring))
            }
        }
    }
}

private fun atan2(v: Offset): Float = kotlin.math.atan2(v.y, v.x)

private fun polygon(points: List<Offset>): Path = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (p in points.drop(1)) lineTo(p.x, p.y)
    close()
}

/** A small four-pointed glint of white light. */
private fun DrawScope.drawSparkle(at: Offset, radius: Float) {
    val waist = radius * 0.22f
    val star = Path().apply {
        moveTo(at.x, at.y - radius)
        lineTo(at.x + waist, at.y - waist)
        lineTo(at.x + radius, at.y)
        lineTo(at.x + waist, at.y + waist)
        lineTo(at.x, at.y + radius)
        lineTo(at.x - waist, at.y + waist)
        lineTo(at.x - radius, at.y)
        lineTo(at.x - waist, at.y - waist)
        close()
    }
    drawPath(star, Color.White.copy(alpha = 0.9f))
    drawCircle(Color.White, radius = waist, center = at)
}
