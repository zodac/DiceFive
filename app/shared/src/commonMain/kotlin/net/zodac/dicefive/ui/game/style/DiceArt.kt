package net.zodac.dicefive.ui.game.style

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.dicefive.ui.theme.GoldAccent

/**
 * Which physical die is being drawn - its position in the tray - so a style meant to look natural
 * (marble, frosted glass) can give every die its own pattern rather than stamping out identical
 * copies. 0 wherever there's only ever one die at a time, like the Styles screen's previews.
 */
val LocalDieIndex = compositionLocalOf { 0 }

/**
 * How long the die being drawn has been tumbling across the mat mid-toss, in milliseconds, or null
 * when it isn't. Only a style that [DiceStyle.tumblesItself] (the D20, a real solid) reads it,
 * turning through orientations as it goes - from the toss's own clock rather than one of its own,
 * so its ground shadow ([DiceStyle.shadowShape], handed the same time) turns with it.
 */
val LocalDieTumbleMillis = compositionLocalOf<Float?> { null }

/**
 * The player's "Simple dice roll animation" setting: when on, every die just flicks through faces
 * while rolling instead of tumbling in 3D. The game provides it; it's off everywhere else.
 */
val LocalSimpleDiceRoll = compositionLocalOf { false }

/**
 * Whether a die draws its own drop shadow. Off for dice lying on the mat, where the tray casts one
 * consistent ground shadow for every die instead, from a single light, following it as it's thrown
 * and tumbles - a die's own shadow can't follow it through a 3D tumble, so it would pop in only once
 * the die settled.
 */
val LocalDieCastsShadow = compositionLocalOf { true }

/** A die's usual drop shadow round [shape] - unless something else is casting it one ([LocalDieCastsShadow]). */
@Composable
internal fun Modifier.dieShadow(shape: Shape): Modifier =
    if (LocalDieCastsShadow.current) shadow(elevation = 4.dp, shape = shape, clip = false) else this

/** A seed for [value]'s face on the die at [LocalDieIndex]: different for every face of every die. */
@Composable
internal fun naturalPatternSeed(value: Int, styleSeed: Int = 0): Int = LocalDieIndex.current * 97 + value * 13 + styleSeed

/** How rounded a [StyledDie]'s corners are unless a style says otherwise, as a percentage of its size. */
internal const val STYLED_DIE_CORNER_PERCENT = 22

/** How a die shows its value. [CUSTOM] leaves it to the die's own pip painter. */
internal enum class PipShape { ROUND, SQUARE, NUMERAL, CUSTOM }

/**
 * The die every non-Classic [DiceStyle] is built from: a rounded square of [face], with [surface]
 * painted over it (a gloss, a vein, a highlight), a thin [edge] outline - swapped for a thicker
 * [heldRingColor] ring while held - and its value shown as [pipShape] in [pipColor]. The styles
 * differ only in these, so the held ring, shadow and pip layout behave the same across every one.
 */
@Composable
internal fun StyledDie(
    value: Int,
    held: Boolean,
    modifier: Modifier,
    face: Brush,
    edge: Color,
    pipColor: Color,
    cornerPercent: Int = STYLED_DIE_CORNER_PERCENT,
    edgeWidth: Dp = 1.dp,
    pipShape: PipShape = PipShape.ROUND,
    heldRingColor: Color = GoldAccent,
    customPips: DrawScope.(Int) -> Unit = {},
    numeral: (Int) -> String = { it.toString() },
    numeralFont: FontFamily? = null,
    numeralSize: Float = 0.62f,
    surface: DrawScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(cornerPercent)
    Box(
        modifier = modifier
            .dieShadow(shape)
            .clip(shape)
            .background(face)
            .drawBehind { surface() }
            .border(if (held) 2.dp else edgeWidth, if (held) heldRingColor else edge, shape),
        contentAlignment = Alignment.Center,
    ) {
        when (pipShape) {
            PipShape.ROUND -> PipFace(
                value = value,
                color = pipColor,
                modifier = Modifier.fillMaxSize().padding(6.dp),
                pipRadiusFraction = 0.11f,
            )
            PipShape.SQUARE -> Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                drawPipPositions(value) { centre ->
                    val half = size.minDimension * 0.1f
                    drawRect(pipColor, topLeft = Offset(centre.x - half, centre.y - half), size = Size(half * 2, half * 2))
                }
            }
            PipShape.CUSTOM -> Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) { customPips(value) }
            PipShape.NUMERAL -> BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // One size for every face of a style, as a fraction of the die's height - set small
                // enough by numeral systems with longer numerals that their widest one still fits.
                val fontSize = with(LocalDensity.current) { (maxHeight * numeralSize).toSp() }
                Text(
                    text = numeral(value),
                    color = pipColor,
                    fontSize = fontSize,
                    lineHeight = fontSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = numeralFont,
                )
            }
        }
    }
}

// How rounded a Casino die's corners are, as a percentage of its size - for drawing it and its shadow alike.
private const val CASINO_CORNER_PERCENT = 8

/** Sharp-cornered, glossy casino dice with flush white pips. */
class CasinoDiceStyle(override val id: String, private val light: Color, private val dark: Color) : DiceStyle, Swatched {
    override val swatch: Color = light

    override fun shadowShape(value: Int, dieIndex: Int, tumbleMillis: Float?): Shape = RoundedCornerShape(CASINO_CORNER_PERCENT)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) = StyledDie(
        value = value,
        held = held,
        modifier = modifier,
        face = Brush.linearGradient(listOf(light, dark)),
        edge = dark,
        pipColor = Color.White,
        cornerPercent = CASINO_CORNER_PERCENT,
    ) {
        // A glossy band across the top, fading out by halfway down.
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent), endY = size.height * 0.45f),
            size = Size(size.width, size.height * 0.45f),
        )
    }
}

// How rounded a Frosted die's corners are, as a percentage of its size - for drawing it and its shadow alike.
private const val FROSTED_CORNER_PERCENT = 6

/**
 * Frosted-glass dice with square corners: a see-through milky tint the mat shows faintly through,
 * frost that's thicker in some patches than others with a few fine crystalline scratches, a soft
 * glow round the edges where the glass is thickest, and a diffuse highlight - with tinted, slightly
 * see-through pips. The frost is different on every face of every die (see [LocalDieIndex]), the
 * way real frosting never repeats.
 */
class FrostedDiceStyle(
    override val id: String,
    private val light: Color,
    private val dark: Color,
    private val pip: Color,
) : DiceStyle, Swatched {
    override val swatch: Color = dark

    override fun shadowShape(value: Int, dieIndex: Int, tumbleMillis: Float?): Shape = RoundedCornerShape(FROSTED_CORNER_PERCENT)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val seed = naturalPatternSeed(value)
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.linearGradient(listOf(light.copy(alpha = 0.82f), dark.copy(alpha = 0.72f))),
            edge = Color.White.copy(alpha = 0.55f),
            pipColor = pip.copy(alpha = 0.85f),
            cornerPercent = FROSTED_CORNER_PERCENT,
        ) {
            drawFrost(seed)
        }
    }
}

/**
 * Frost for one face: uneven patches where it's built up thicker, a fine grain whose density
 * varies across the face, a few thin crystalline scratches, then the diffuse highlight and the
 * brighter thick-glass edge. All of it from [seed], so the face looks the same every time it's drawn.
 */
private fun DrawScope.drawFrost(seed: Int) {
    val random = Random(seed)
    val w = size.width
    val h = size.height
    // Patches of heavier frost, each a soft bloom of its own size and strength.
    val patches = List(2 + random.nextInt(3)) {
        Triple(Offset(random.nextFloat() * w, random.nextFloat() * h), size.minDimension * (0.2f + random.nextFloat() * 0.35f), 0.12f + random.nextFloat() * 0.2f)
    }
    for ((centre, radius, strength) in patches) {
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = strength), Color.Transparent), center = centre, radius = radius), radius, centre)
    }
    // Grain: denser inside the patches than between them.
    val speck = 2.dp.toPx()
    repeat((w * h / (speck * speck) * 1.4f).toInt()) {
        val at = Offset(random.nextFloat() * w, random.nextFloat() * h)
        val inPatch = patches.any { (centre, radius, _) -> (at - centre).getDistance() < radius }
        if (!inPatch && random.nextFloat() < 0.55f) return@repeat
        drawCircle(
            Color.White.copy(alpha = (if (inPatch) 0.12f else 0.05f) + random.nextFloat() * 0.12f),
            radius = speck * (0.15f + random.nextFloat() * 0.25f),
            center = at,
        )
    }
    // A few fine scratches, each a short straight line at its own angle.
    repeat(2 + random.nextInt(3)) {
        val from = Offset(random.nextFloat() * w, random.nextFloat() * h)
        val angle = random.nextFloat() * 2f * PI.toFloat()
        val length = size.minDimension * (0.15f + random.nextFloat() * 0.3f)
        drawLine(
            Color.White.copy(alpha = 0.18f + random.nextFloat() * 0.15f),
            from,
            from + Offset(cos(angle) * length, sin(angle) * length),
            strokeWidth = 0.6.dp.toPx(),
        )
    }
    // A soft, diffuse highlight rather than a sharp reflection - frosted glass scatters it.
    val glint = Offset(w * (0.2f + random.nextFloat() * 0.2f), h * (0.2f + random.nextFloat() * 0.15f))
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent), center = glint, radius = size.minDimension * 0.6f),
        radius = size.minDimension * 0.6f,
        center = glint,
    )
    // The thicker glass at the edges glows a little brighter.
    val inset = 1.5.dp.toPx()
    drawRect(
        Color.White.copy(alpha = 0.3f),
        topLeft = Offset(inset, inset),
        size = Size(w - inset * 2, h - inset * 2),
        style = Stroke(width = 2.dp.toPx()),
    )
}

// How rounded a Marble die's corners are, as a percentage of its size - for drawing it and its shadow alike.
private const val MARBLE_CORNER_PERCENT = 18

/** Polished marble dice, veined across the face like a kitchen countertop - differently on every face of every die. */
class MarbleDiceStyle(
    override val id: String,
    private val light: Color,
    private val dark: Color,
    private val vein: Color,
    private val pip: Color,
    private val seed: Int,
) : DiceStyle, Swatched {
    override val swatch: Color = light

    override fun shadowShape(value: Int, dieIndex: Int, tumbleMillis: Float?): Shape = RoundedCornerShape(MARBLE_CORNER_PERCENT)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        // Every face of every die veined differently - it's meant to be natural stone.
        val faceSeed = naturalPatternSeed(value, styleSeed = seed)
        StyledDie(
            value = value,
            held = held,
            modifier = modifier,
            face = Brush.linearGradient(listOf(light, dark)),
            edge = dark,
            pipColor = pip,
            cornerPercent = MARBLE_CORNER_PERCENT,
        ) {
            drawMarble(faceSeed, vein)
        }
    }
}

// How rounded a Metal die's corners are, as a percentage of its size - for drawing it and its shadow alike.
private const val METAL_CORNER_PERCENT = 20

/**
 * Metal dice: a diagonal sheen of light and dark bands with dark, engraved pips. [heldRing] is
 * overridable because a gold die would swallow the usual gold held ring.
 */
class MetalDiceStyle(
    override val id: String,
    private val sheen: List<Color>,
    private val pip: Color,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = sheen[1]

    override fun shadowShape(value: Int, dieIndex: Int, tumbleMillis: Float?): Shape = RoundedCornerShape(METAL_CORNER_PERCENT)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) = StyledDie(
        value = value,
        held = held,
        modifier = modifier,
        face = Brush.linearGradient(sheen),
        edge = sheen.last(),
        pipColor = pip,
        cornerPercent = METAL_CORNER_PERCENT,
        heldRingColor = heldRing,
    )
}

// How rounded a Retro die's corners are, as a percentage of its size - for drawing it and its shadow alike.
private const val RETRO_CORNER_PERCENT = 6

/** Flat, square-cornered retro dice with square pips and a heavy outline, like an old handheld's screen. */
class RetroDiceStyle(
    override val id: String,
    private val face: Color,
    private val outline: Color,
    private val pip: Color,
    private val heldRing: Color = GoldAccent,
) : DiceStyle, Swatched {
    override val swatch: Color = face

    override fun shadowShape(value: Int, dieIndex: Int, tumbleMillis: Float?): Shape = RoundedCornerShape(RETRO_CORNER_PERCENT)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) = StyledDie(
        value = value,
        held = held,
        modifier = modifier,
        face = Brush.linearGradient(listOf(face, face)),
        edge = outline,
        pipColor = pip,
        cornerPercent = RETRO_CORNER_PERCENT,
        edgeWidth = 2.dp,
        pipShape = PipShape.SQUARE,
        heldRingColor = heldRing,
    )
}

/** How a [NumeralDiceStyle] writes a value: as a digit, a Roman numeral, or an Eastern Arabic digit. */
enum class NumeralSystem(val write: (Int) -> String, val size: Float, val font: FontFamily? = null) {
    DIGITS({ it.toString() }, size = 0.62f),
    // Smaller, so the widest numeral ("III") still fits - and every face at that same size.
    ROMAN({ listOf("I", "II", "III", "IV", "V", "VI")[it - 1] }, size = 0.4f, font = FontFamily.Serif),
    EASTERN_ARABIC({ listOf("\u0661", "\u0662", "\u0663", "\u0664", "\u0665", "\u0666")[it - 1] }, size = 0.62f),
}

/** Dice that show their value as a number instead of pips, written in [system]. */
class NumeralDiceStyle(
    override val id: String,
    private val light: Color,
    private val dark: Color,
    private val digit: Color,
    private val system: NumeralSystem = NumeralSystem.DIGITS,
) : DiceStyle, Swatched {
    override val swatch: Color = light

    override fun shadowShape(value: Int, dieIndex: Int, tumbleMillis: Float?): Shape = RoundedCornerShape(STYLED_DIE_CORNER_PERCENT)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) = StyledDie(
        value = value,
        held = held,
        modifier = modifier,
        face = Brush.linearGradient(listOf(light, dark)),
        edge = dark,
        pipColor = digit,
        pipShape = PipShape.NUMERAL,
        numeral = system.write,
        numeralFont = system.font,
        numeralSize = system.size,
    )
}

/** The corners of the smallest convex outline around [points], in order round it. */
internal fun convexHull(points: List<Offset>): List<Offset> {
    val sorted = points.sortedWith(compareBy({ it.x }, { it.y }))
    fun cross(o: Offset, a: Offset, b: Offset) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    fun half(ordered: List<Offset>): List<Offset> {
        val chain = mutableListOf<Offset>()
        for (p in ordered) {
            while (chain.size >= 2 && cross(chain[chain.size - 2], chain.last(), p) <= 0f) chain.removeAt(chain.size - 1)
            chain += p
        }
        return chain.dropLast(1)
    }
    return half(sorted) + half(sorted.asReversed())
}
