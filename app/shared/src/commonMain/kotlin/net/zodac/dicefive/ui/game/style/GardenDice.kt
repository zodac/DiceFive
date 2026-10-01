package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import net.zodac.dicefive.ui.theme.GoldAccent

/**
 * The colours a garden die's vegetables are drawn in - each its own natural colours, or all of them
 * in one colour's shades on a recoloured die.
 */
class GardenColours(
    val squash: Shades,
    val carrot: Shades,
    val tomato: Shades,
    val artichoke: Shades,
    val eggplant: Shades,
    val onion: Shades,
    val leaf: Shades,
    val stalk: Shades,
) {
    companion object {
        val Natural = GardenColours(
            squash = Shades(Color(0xFFA8662A), Color(0xFFE2AA5E), Color(0xFFF8D9A0)),
            carrot = Shades(Color(0xFFB4470A), Color(0xFFF07A1A), Color(0xFFFFB46A)),
            tomato = Shades(Color(0xFF8A0E0A), Color(0xFFE0291C), Color(0xFFFF8A70)),
            artichoke = Shades(Color(0xFF3A5A28), Color(0xFF7FA35A), Color(0xFFC6DDA0)),
            eggplant = Shades(Color(0xFF240A30), Color(0xFF55216E), Color(0xFFB485D0)),
            onion = Shades(Color(0xFF7A4612), Color(0xFFC88A3E), Color(0xFFF2D29A)),
            leaf = Shades(Color(0xFF1F5A12), Color(0xFF3E9A26), Color(0xFF8ED36A)),
            stalk = Shades(Color(0xFF4A3A1A), Color(0xFF7A6A3A), Color(0xFFB8A86A)),
        )

        fun allIn(colour: Color): GardenColours = Shades.of(colour).let { GardenColours(it, it, it, it, it, it, it, it) }
    }
}

/**
 * Garden dice: each value a different crop, as many as the number - 1 butternut squash, 2 carrots,
 * 3 tomatoes, 4 artichokes, 5 eggplants, 6 onions - drawn as a seed packet's illustrations, each
 * piece turned and sized a little differently, on [face]. On a coloured roll the face takes the
 * roll's face colours and every vegetable its pip colour.
 */
class GardenDiceStyle(
    override val id: String,
    private val face: Color,
    private val faceShade: Color,
    private val soil: Boolean,
    private val crops: GardenColours = GardenColours.Natural,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = face

    override fun recoloured(palette: DieColourPalette): DiceStyle =
        GardenDiceStyle(id, palette.diceTop, palette.diceBottom, soil = false, crops = GardenColours.allIn(palette.pip), heldRing = palette.heldRing)

    override val cornerPercent: Int = STYLED_DIE_CORNER_PERCENT

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val pattern = naturalPatternSeed(value)
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.linearGradient(listOf(face, faceShade)),
            edge = faceShade,
            pipColor = crops.tomato.base,
            pipShape = PipShape.CUSTOM,
            pipPadding = 5.dp,
            heldRingColor = heldRing,
            customPips = { drawCachedSurface(Crops(value, crops, pattern)) { drawCrops(value, pattern) } },
        ) {
            drawCachedSurface(GardenFace(face, faceShade, soil, pattern)) { paintGardenFace(pattern) }
        }
    }

    private data class Crops(val value: Int, val crops: GardenColours, val pattern: Int)

    private data class GardenFace(val face: Color, val shade: Color, val soil: Boolean, val pattern: Int)

    /** A linen seed packet's weave, or tilled soil's crumbs. */
    private fun DrawScope.paintGardenFace(pattern: Int) {
        val random = Random(pattern * 19 + 2)
        val m = size.minDimension
        if (soil) {
            repeat(140) {
                val at = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
                val tone = if (random.nextBoolean()) lerp(face, Color.White, 0.25f) else lerp(faceShade, Color.Black, 0.35f)
                drawCircle(tone.copy(alpha = 0.6f), m * (0.006f + random.nextFloat() * 0.014f), at)
            }
        } else {
            val thread = lerp(faceShade, Color.Black, 0.1f).copy(alpha = 0.18f)
            var k = 0f
            while (k < m) {
                drawLine(thread, Offset(0f, k), Offset(size.width, k), strokeWidth = m * 0.006f)
                drawLine(thread, Offset(k, 0f), Offset(k, size.height), strokeWidth = m * 0.006f)
                k += m * 0.035f
            }
        }
        drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.15f), Color.Transparent), Offset(size.width * 0.3f, size.height * 0.25f), m * 0.7f))
    }

    private fun DrawScope.drawCrops(value: Int, pattern: Int) {
        val random = Random(pattern * 23 + 9)
        val m = size.minDimension
        fun jitter(base: Float) = base + (random.nextFloat() - 0.5f) * 24f
        when (value) {
            1 -> drawSquash(Offset(size.width / 2f, size.height / 2f), m * 0.42f, jitter(-25f))
            2 -> {
                drawCarrot(Offset(size.width * 0.3f, size.height * 0.36f), m * 0.3f, jitter(40f))
                drawCarrot(Offset(size.width * 0.7f, size.height * 0.64f), m * 0.3f, jitter(40f))
            }
            3 -> drawPipPositions(3) { drawTomato(it, m * 0.15f, jitter(0f)) }
            4 -> drawPipPositions(4) { drawArtichoke(it, m * 0.2f, jitter(0f)) }
            5 -> drawPipPositions(5) { drawEggplant(it, m * 0.18f, jitter(30f)) }
            else -> drawPipPositions(6) { drawOnion(it, m * 0.13f, jitter(0f)) }
        }
    }

    /** A soft shadow of [shape] under the vegetable at [centre], [radius] across, turned [degrees]. */
    private fun DrawScope.dropShadow(centre: Offset, radius: Float, degrees: Float, shape: Path) =
        inUnit(centre + Offset(radius * 0.08f, radius * 0.12f), radius, degrees) { drawPath(shape, Color.Black.copy(alpha = 0.2f)) }

    /** A butternut squash: a slender neck swelling to a round bulb, faint ribs, a woody stalk. */
    private fun DrawScope.drawSquash(centre: Offset, radius: Float, degrees: Float) {
        val c = crops.squash
        dropShadow(centre, radius, degrees, SquashBody)
        inUnit(centre, radius, degrees) {
            drawPath(SquashBody, Brush.horizontalGradient(listOf(c.deep, c.light, c.base, c.deep), startX = -0.6f, endX = 0.6f))
            clipPath(SquashBody) {
                for (x in listOf(-0.3f, 0f, 0.3f)) {
                    drawPath(Path().apply { moveTo(x * 0.5f, -0.85f); quadraticTo(x * 1.3f, 0.2f, x * 0.8f, 1f) }, c.deep.copy(alpha = 0.25f), style = Stroke(0.03f))
                }
                drawOval(Color.White.copy(alpha = 0.3f), topLeft = Offset(-0.38f, 0.05f), size = Size(0.18f, 0.5f))
            }
            drawPath(SquashBody, c.deep, style = Stroke(0.045f))
            drawCircle(c.deep.copy(alpha = 0.6f), 0.06f, Offset(0f, 0.86f))
            drawLine(crops.stalk.deep, Offset(0f, -0.9f), Offset(0.05f, -1.06f), strokeWidth = 0.16f, cap = StrokeCap.Round)
            drawLine(crops.stalk.base, Offset(0f, -0.9f), Offset(0.04f, -1.03f), strokeWidth = 0.08f, cap = StrokeCap.Round)
        }
    }

    /** A carrot: an orange taproot ringed with fine grooves, and a tuft of feathery leaves. */
    private fun DrawScope.drawCarrot(centre: Offset, radius: Float, degrees: Float) {
        val c = crops.carrot
        dropShadow(centre, radius, degrees, CarrotRoot)
        inUnit(centre, radius, degrees) {
            // The leaves: three fronds of little leaflets fanned out of its top.
            for (k in -1..1) {
                val a = -PI.toFloat() / 2f + k * 0.38f
                val tip = Offset(cos(a), sin(a)) * 0.62f + Offset(0f, -0.48f)
                drawLine(crops.leaf.deep, Offset(0f, -0.5f), tip, strokeWidth = 0.06f, cap = StrokeCap.Round)
                for (j in 1..3) {
                    val at = Offset(0f, -0.5f) + (tip - Offset(0f, -0.5f)) * (j / 3.3f)
                    drawCircle(crops.leaf.base, 0.1f, at + Offset(0.06f, 0f))
                    drawCircle(crops.leaf.light, 0.07f, at - Offset(0.06f, 0f))
                }
            }
            drawPath(CarrotRoot, Brush.horizontalGradient(listOf(c.deep, c.light, c.base, c.deep), startX = -0.28f, endX = 0.28f))
            clipPath(CarrotRoot) {
                for (y in listOf(-0.25f, 0.05f, 0.3f, 0.55f, 0.75f)) {
                    drawLine(c.deep.copy(alpha = 0.45f), Offset(-0.3f, y), Offset(0.05f, y + 0.04f), strokeWidth = 0.03f, cap = StrokeCap.Round)
                }
            }
            drawPath(CarrotRoot, c.deep, style = Stroke(0.04f))
        }
    }

    /** A tomato from above: a glossy red ball, slightly lobed, with its green star of sepals. */
    private fun DrawScope.drawTomato(centre: Offset, radius: Float, degrees: Float) {
        val c = crops.tomato
        dropShadow(centre, radius, degrees, TomatoBody)
        inUnit(centre, radius, degrees) {
            drawPath(TomatoBody, Brush.radialGradient(listOf(c.light, c.base, c.deep), Offset(-0.35f, -0.35f), 1.4f))
            drawPath(TomatoBody, c.deep, style = Stroke(0.05f))
            drawOval(Color.White.copy(alpha = 0.55f), topLeft = Offset(-0.6f, -0.55f), size = Size(0.3f, 0.2f))
            // The calyx: a five-pointed star of thin sepals, and the stalk's stub.
            val star = Path()
            for (i in 0 until 10) {
                val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
                val r = if (i % 2 == 0) 0.5f else 0.12f
                val p = Offset(cos(a), sin(a)) * r
                if (i == 0) star.moveTo(p.x, p.y) else star.lineTo(p.x, p.y)
            }
            star.close()
            drawPath(star, crops.leaf.base)
            drawPath(star, crops.leaf.deep, style = Stroke(0.035f))
            drawCircle(crops.leaf.deep, 0.1f, Offset.Zero)
        }
    }

    /**
     * A globe artichoke: a thick cut stalk, and over it rows of broad pointed scales - each with a
     * darker edge, a pale vein and a purple tip - staggered like roof tiles and closing to a point at
     * the top, with the two outermost splayed out at its base.
     */
    private fun DrawScope.drawArtichoke(centre: Offset, radius: Float, degrees: Float) {
        val c = crops.artichoke
        val tip = lerp(c.base, crops.eggplant.base, 0.6f)
        dropShadow(centre, radius, degrees, ArtichokeOutline)
        inUnit(centre, radius, degrees) {
            // The stalk, cut square at its foot.
            drawLine(c.deep, Offset(0f, 0.55f), Offset(0.06f, 1.05f), strokeWidth = 0.3f, cap = StrokeCap.Butt)
            drawLine(c.base, Offset(-0.03f, 0.55f), Offset(0.03f, 1.05f), strokeWidth = 0.14f, cap = StrokeCap.Butt)
            drawOval(c.light, topLeft = Offset(-0.1f, 0.99f), size = Size(0.32f, 0.12f))
            drawPath(ArtichokeOutline, c.deep)
            // Top down, so each lower, outer row overlaps the one inside it: a few broad, fleshy
            // scales, not many small ones - so it reads as an artichoke, not a pine cone.
            val scales = listOf(
                Triple(0f, -0.62f, 0.3f),
                Triple(-0.24f, -0.4f, 0.36f), Triple(0.24f, -0.4f, 0.36f),
                Triple(-0.48f, -0.04f, 0.38f), Triple(0f, -0.08f, 0.42f), Triple(0.48f, -0.04f, 0.38f),
                Triple(-0.26f, 0.28f, 0.44f), Triple(0.26f, 0.28f, 0.44f),
            )
            for ((x, y, k) in scales) drawScale(x, y, k, 0f, c, tip)
            // The outermost pair, splayed out at the base.
            drawScale(-0.5f, 0.45f, 0.4f, -58f, c, tip)
            drawScale(0.5f, 0.45f, 0.4f, 58f, c, tip)
        }
    }

    /** One artichoke scale [k] across, its base at ([x], [y]), leaning [lean] degrees from upright. */
    private fun DrawScope.drawScale(x: Float, y: Float, k: Float, lean: Float, c: Shades, tip: Color) {
        withTransform({ rotate(lean, Offset(x, y + k * 0.4f)) }) {
            // Broad and round-shouldered, with only a small point at its tip.
            val scale = Path().apply {
                moveTo(x - k * 1.05f, y + k * 0.3f)
                cubicTo(x - k * 1.1f, y - k * 0.55f, x - k * 0.45f, y - k * 0.95f, x, y - k * 1.05f)
                cubicTo(x + k * 0.45f, y - k * 0.95f, x + k * 1.1f, y - k * 0.55f, x + k * 1.05f, y + k * 0.3f)
                quadraticTo(x, y + k * 0.7f, x - k * 1.05f, y + k * 0.3f)
                close()
            }
            drawPath(scale, Brush.verticalGradient(listOf(tip, lerp(c.base, tip, 0.25f), c.base, c.light), startY = y - k * 1.05f, endY = y + k * 0.55f))
            // Its thick, fleshy body: a lighter swell in the middle.
            drawOval(c.light.copy(alpha = 0.45f), topLeft = Offset(x - k * 0.45f, y - k * 0.5f), size = Size(k * 0.9f, k * 0.8f))
            drawPath(scale, lerp(c.deep, tip, 0.35f), style = Stroke(k * 0.13f))
        }
    }

    /** An eggplant: a glossy, plump purple teardrop with its green cap and stalk. */
    private fun DrawScope.drawEggplant(centre: Offset, radius: Float, degrees: Float) {
        val c = crops.eggplant
        dropShadow(centre, radius, degrees, EggplantBody)
        inUnit(centre, radius, degrees) {
            drawPath(EggplantBody, Brush.radialGradient(listOf(c.light, c.base, c.deep), Offset(-0.2f, 0.1f), 1.1f))
            drawPath(EggplantBody, c.deep, style = Stroke(0.04f))
            drawPath(Path().apply { moveTo(-0.22f, -0.15f); quadraticTo(-0.3f, 0.35f, -0.1f, 0.7f) }, Color.White.copy(alpha = 0.5f), style = Stroke(0.07f, cap = StrokeCap.Round))
            // The cap: pointed sepals clasping its top, and the stalk.
            val cap = Path().apply {
                moveTo(-0.32f, -0.42f)
                lineTo(-0.2f, -0.18f)
                lineTo(-0.08f, -0.36f)
                lineTo(0.05f, -0.14f)
                lineTo(0.14f, -0.36f)
                lineTo(0.3f, -0.2f)
                lineTo(0.3f, -0.48f)
                quadraticTo(0f, -0.72f, -0.32f, -0.42f)
                close()
            }
            drawPath(cap, crops.leaf.base)
            drawPath(cap, crops.leaf.deep, style = Stroke(0.035f))
            drawLine(crops.leaf.deep, Offset(0f, -0.6f), Offset(0.06f, -0.95f), strokeWidth = 0.12f, cap = StrokeCap.Round)
        }
    }

    /** An onion: a round, papery golden bulb narrowing to a twist at its top, with roots below. */
    private fun DrawScope.drawOnion(centre: Offset, radius: Float, degrees: Float) {
        val c = crops.onion
        dropShadow(centre, radius, degrees, OnionBulb)
        inUnit(centre, radius, degrees) {
            for (x in listOf(-0.2f, -0.07f, 0.07f, 0.2f)) {
                drawLine(crops.stalk.light, Offset(x * 0.5f, 0.82f), Offset(x, 1.02f), strokeWidth = 0.04f, cap = StrokeCap.Round)
            }
            drawPath(OnionBulb, Brush.radialGradient(listOf(c.light, c.base, c.deep), Offset(-0.25f, -0.1f), 1.1f))
            clipPath(OnionBulb) {
                for (x in listOf(-0.5f, -0.2f, 0.1f, 0.4f)) {
                    drawPath(Path().apply { moveTo(0f, -0.9f); quadraticTo(x * 2f, 0f, x * 0.4f, 0.85f) }, c.deep.copy(alpha = 0.35f), style = Stroke(0.03f))
                }
            }
            drawPath(OnionBulb, c.deep, style = Stroke(0.045f))
        }
    }
}

// Built on first use, not when the file loads: a Path needs the platform's graphics.
private val SquashBody: Path by lazy {
    smoothPath(
        listOf(
            Offset(0f, -0.92f), Offset(0.24f, -0.82f), Offset(0.27f, -0.4f), Offset(0.42f, -0.05f), Offset(0.6f, 0.4f),
            Offset(0.45f, 0.85f), Offset(0f, 1f), Offset(-0.45f, 0.85f), Offset(-0.6f, 0.4f), Offset(-0.42f, -0.05f),
            Offset(-0.27f, -0.4f), Offset(-0.24f, -0.82f),
        ),
        closed = true,
    )
}

// Built on first use, not when the file loads: a Path needs the platform's graphics.
private val CarrotRoot: Path by lazy {
    Path().apply {
        moveTo(-0.26f, -0.5f)
        quadraticTo(0f, -0.6f, 0.26f, -0.5f)
        cubicTo(0.24f, 0.1f, 0.1f, 0.7f, 0.02f, 1f)
        cubicTo(-0.04f, 1.02f, -0.06f, 0.95f, -0.08f, 0.85f)
        cubicTo(-0.16f, 0.4f, -0.26f, 0f, -0.26f, -0.5f)
        close()
    }
}

// Built on first use, not when the file loads: a Path needs the platform's graphics.
private val TomatoBody: Path by lazy {
    smoothPath(List(10) { i ->
        val a = i * 2f * PI.toFloat() / 10
        val r = if (i % 2 == 0) 0.97f else 0.92f
        Offset(cos(a) * r, sin(a) * r * 0.94f)
    }, closed = true)
}

// Built on first use, not when the file loads: a Path needs the platform's graphics.
private val ArtichokeOutline: Path by lazy {
    Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(-0.78f, -0.85f), Size(1.56f, 1.55f))) }
}

// Built on first use, not when the file loads: a Path needs the platform's graphics.
private val EggplantBody: Path by lazy {
    smoothPath(
        listOf(
            Offset(0f, -0.6f), Offset(0.28f, -0.42f), Offset(0.4f, 0.05f), Offset(0.48f, 0.55f), Offset(0.25f, 0.95f),
            Offset(-0.15f, 0.97f), Offset(-0.45f, 0.65f), Offset(-0.42f, 0.1f), Offset(-0.3f, -0.4f),
        ),
        closed = true,
    )
}

// Built on first use, not when the file loads: a Path needs the platform's graphics.
private val OnionBulb: Path by lazy {
    smoothPath(
        listOf(
            Offset(0f, -1f), Offset(0.12f, -0.75f), Offset(0.55f, -0.45f), Offset(0.85f, 0.1f), Offset(0.6f, 0.7f),
            Offset(0f, 0.86f), Offset(-0.6f, 0.7f), Offset(-0.85f, 0.1f), Offset(-0.55f, -0.45f), Offset(-0.12f, -0.75f),
        ),
        closed = true,
    )
}
