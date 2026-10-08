package net.zodac.dicefive.ui.game.style

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.zodac.dicefive.ui.common.LocalReduceMotion

/** A treasure chest's colours: its wood, the gold of its fittings, its dark inside and its velvet lining. */
class ChestPalette(
    val woodLight: Color,
    val woodDark: Color,
    val seam: Color,
    val goldLight: Color,
    val gold: Color,
    val goldDark: Color,
    val interior: Color,
    val lining: Color,
)

// The chest, in grid units (SQUAT, 76 x 66), as a solid in its own space: x across it (its right
// end positive), y up from the table, z forward from its middle. It's drawn turned CHEST_YAW so its
// right end shows as well as its front, and seen from a little above like every other cup - a
// point's distance towards the viewer moves it CUP_VIEW_SQUASH of that further down the canvas.
private const val CHEST_WIDTH = 46f
private const val CHEST_DEPTH = 24f
private const val PLINTH_HEIGHT = 3f
private const val PLINTH_OVERHANG = 1.2f
private const val BODY_HEIGHT = 20f
private const val WALL = 1.3f
// The lid: straight sides this tall, then a half-round top across the chest's depth.
private const val LID_SIDE = 4f
// The lid's height as a fraction of a half-round over its straight sides: a touch lower, so it isn't
// too tall on a chest drawn this large.
private const val LID_RISE = 0.9f
private const val LID_ARC_STEPS = 18
// Where the middle of the chest's footprint is drawn.
private const val CHEST_CENTRE_X = 38f
private const val CHEST_BASE_Y = 53f
private const val CHEST_YAW_DEGREES = 28f
// The whole chest drawn this much larger than its grid, about the middle of its footprint.
private const val CHEST_SCALE = 1.2f
private const val VIEW_ELEVATION_DEGREES = 22f
// How far the lid swings back on its hinge when open - past upright, so it rests leaning back.
private const val LID_OPEN_DEGREES = 105f
// The metal straps round lid and body, this far in from each end, and this wide.
private const val STRAP_INSET = 7f
private const val STRAP_WIDTH = 3f
private const val CORNER_WIDTH = 3.5f

// The shake: one loop of this long carries every part of it, each a whole number of cycles per loop
// so it repeats seamlessly, and every part a smooth wave - no jolts. It rocks from side to side four
// times a second, drifting a little as it goes, lifting smoothly at each end of the rock with the lid
// lifting on its hinge in time.
private const val SHAKE_LOOP_MILLIS = 1000
private const val SHAKE_ROCK_DEGREES = 8f
private const val SHAKE_SKITTER = 1f
private const val SHAKE_HOP = 1.4f
private const val SHAKE_LID_RATTLE_DEGREES = 6f
private const val SHAKE_FADE_MILLIS = 120

private const val LID_CLOSE_MILLIS = 110
private const val FLOURISH_MILLIS = 1100

/**
 * A treasure chest, instead of a cup: shaken, it rocks from side to side, lifting at each end of the
 * rock with its lid lifting on its hinge; poured, it doesn't tip over but throws its lid open
 * with a burst of gold - light, rays, sparkles and a few coins flung out - like a game's bonus chest,
 * then sits open, brimming with treasure, until the next shake shuts it.
 */
class TreasureChestDiceCupStyle(override val id: String, private val palette: ChestPalette) : DiceCupStyle, Swatched {
    override val shape: CupShape = CupShape.SQUAT
    override val swatch: Color = palette.gold

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        val reduceMotion = LocalReduceMotion.current
        // Unlike a cup, a chest that first appears with the roll already poured - a game being continued
        // - stays shut until the next shake, rather than appearing open with its treasure still to load.
        // The next roll then opens it as usual. Under reduced motion a cup is never told it's shaking, so the
        // next roll is seen by the chest being stood back up for it instead (see DiceCupPanel).
        var heldShut by remember { mutableStateOf(tilted && !rolling) }
        if (rolling || !tilted) heldShut = false
        val open = tilted && !rolling && !heldShut
        // So it always starts shut.
        val lid = remember { Animatable(0f) }
        val flourish = remember { Animatable(1f) }
        // What spills out this time: a fresh handful every time the lid opens.
        var spills by remember { mutableStateOf(emptyList<Spill>()) }
        LaunchedEffect(open) {
            // Under reduced motion the lid is simply shut or open, with no swing between, and no burst of gold.
            if (!open) {
                if (reduceMotion) lid.snapTo(0f) else lid.animateTo(0f, tween(LID_CLOSE_MILLIS))
            } else if (lid.value < 1f) {
                spills = spillHandful(Random.nextInt())
                if (reduceMotion) {
                    lid.snapTo(1f)
                } else {
                    launch { flourish.snapTo(0f); flourish.animateTo(1f, tween(FLOURISH_MILLIS, easing = LinearEasing)) }
                    // Underdamped, so the lid flies back and bounces on its hinge.
                    lid.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow))
                }
            }
        }
        // The hoard is simulated and painted in the background from the first shake (or straight away if
        // the chest is already open), so it's normally ready by the time the lid lifts - and never for
        // a chest that's only ever shown closed and still, like a Styles preview that hasn't been picked.
        var canvasSize by remember { mutableStateOf(IntSize.Zero) }
        var hoardWanted by remember { mutableStateOf(false) }
        // Under reduced motion there's no shake to start it on, so it starts with the chest on a table being played
        // at (LocalCupActivity), well before a roll lands - else the lid could open on an empty chest, the treasure
        // popping in after.
        val atTable = LocalCupActivity.current != null
        if (rolling || tilted || (reduceMotion && atTable)) hoardWanted = true
        val hoard by rememberHoardArt(id, palette, if (hoardWanted) canvasSize else IntSize.Zero)
        val shakeWeight by animateFloatAsState(if (rolling) 1f else 0f, tween(SHAKE_FADE_MILLIS), label = "chestShakeFade") // i18n: not translated - an animation label, not shown
        // The shake's clock only exists while it counts, so a still chest asks for no frames.
        val shake: State<Float>? = if (rolling || shakeWeight > 0f) rememberShakeLoop() else null

        Canvas(
            modifier = modifier.onSizeChanged { canvasSize = it }.graphicsLayer {
                // Read here, so the shake only moves the layer.
                val t = (shake?.value ?: 0f) * 2f * PI.toFloat()
                val weight = shakeWeight
                rotationZ = weight * SHAKE_ROCK_DEGREES * sin(4f * t)
                translationX = weight * size.width * SHAKE_SKITTER * sin(2f * t + 1f) / CupShape.SQUAT.gridWidth
                translationY = -weight * size.height * SHAKE_HOP * shakeLift(t) / CupShape.SQUAT.gridHeight
                transformOrigin = TransformOrigin(0.5f, 0.9f)
            },
        ) {
            val scope = CupDrawScope(this, shape, CupPose(0f, 0f))
            val t = (shake?.value ?: 0f) * 2f * PI.toFloat()
            val rattle = shakeWeight * SHAKE_LID_RATTLE_DEGREES * shakeLift(t)
            scaledAbout(CHEST_SCALE, chestPivot(size)) {
                scope.drawChest(id, palette, hoard, lid.value * LID_OPEN_DEGREES + rattle, openness = lid.value.coerceIn(0f, 1f), flourish = flourish.value, spills = if (open) spills else emptyList())
            }
        }
    }
}

/** Where the chest is enlarged about ([CHEST_SCALE]) on a canvas of [size]: the middle of its footprint. */
private fun chestPivot(size: Size): Offset =
    Offset(size.width * CHEST_CENTRE_X / CupShape.SQUAT.gridWidth, size.height * CHEST_BASE_Y / CupShape.SQUAT.gridHeight)

/** How far the chest is lifted at [t] (radians round the shake's loop), 0 to 1: up smoothly at each end of its rock, down as it swings through the middle. */
private fun shakeLift(t: Float): Float = (1f - cos(8f * t)) / 2f

@Composable
private fun rememberShakeLoop(): State<Float> = rememberInfiniteTransition(label = "chestShake").animateFloat( // i18n: not translated - an animation label, not shown
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(SHAKE_LOOP_MILLIS, easing = LinearEasing), RepeatMode.Restart),
    label = "chestShakeLoop", // i18n: not translated - an animation label, not shown
)

private val YawSin = sin(CHEST_YAW_DEGREES * PI.toFloat() / 180f)
private val YawCos = cos(CHEST_YAW_DEGREES * PI.toFloat() / 180f)
private val ElevationSin = sin(VIEW_ELEVATION_DEGREES * PI.toFloat() / 180f)
private val ElevationCos = cos(VIEW_ELEVATION_DEGREES * PI.toFloat() / 180f)

/** How far [x], [z] lies towards the viewer, once the chest is turned - for what's drawn over what. */
private fun towardViewer(x: Float, z: Float): Float = x * YawSin + z * YawCos

/** Where the point ([x], [y], [z]) of the chest is drawn, in grid units. */
private fun project(x: Float, y: Float, z: Float): Offset =
    Offset(CHEST_CENTRE_X + x * YawCos - z * YawSin, CHEST_BASE_Y - y + towardViewer(x, z) * CUP_VIEW_SQUASH)

/** How squarely a surface facing ([nx], [ny], [nz]) faces the viewer: above 0 it's seen, 1 head on. */
private fun facing(nx: Float, ny: Float, nz: Float): Float {
    val length = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
    return (nx * YawSin * ElevationCos + ny * ElevationSin + nz * YawCos * ElevationCos) / length
}

private fun CupDrawScope.point(x: Float, y: Float, z: Float): Offset = project(x, y, z).let { Offset(gx(it.x), gy(it.y)) }

private fun CupDrawScope.quad(vararg corners: Triple<Float, Float, Float>): Path = Path().apply {
    corners.forEachIndexed { i, (x, y, z) ->
        val p = point(x, y, z)
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

/** A rectangle on the chest's front face (z = depth / 2), from [x0] to [x1] and [y0] up to [y1]. */
private fun CupDrawScope.frontRect(x0: Float, x1: Float, y0: Float, y1: Float, z: Float = CHEST_DEPTH / 2f): Path =
    quad(Triple(x0, y0, z), Triple(x1, y0, z), Triple(x1, y1, z), Triple(x0, y1, z))

/** A rectangle on the chest's right end (x = width / 2), from [z0] to [z1] and [y0] up to [y1]. */
private fun CupDrawScope.sideRect(z0: Float, z1: Float, y0: Float, y1: Float, x: Float = CHEST_WIDTH / 2f): Path =
    quad(Triple(x, y0, z0), Triple(x, y0, z1), Triple(x, y1, z1), Triple(x, y1, z0))

// How thick the lid's wood is.
private const val LID_THICKNESS = 1.4f

/**
 * The lid's cross-section, closed, as (up from the hinge, forward from the hinge) - from the front
 * edge up its straight front side, over the half-round top, and down the back - [inset] in from its
 * outer surface (its inside surface, at the lid's thickness).
 */
private fun lidProfile(inset: Float): List<Pair<Float, Float>> = buildList {
    add(0f to CHEST_DEPTH - inset)
    val radius = CHEST_DEPTH / 2f
    for (i in 0..LID_ARC_STEPS) {
        val a = PI.toFloat() * i / LID_ARC_STEPS
        // The rise flattened by LID_RISE, so the arch is a little lower than a true half-round.
        add((LID_SIDE + (radius - inset) * sin(a)) * LID_RISE to radius + (radius - inset) * cos(a))
    }
    add(0f to inset)
}

private val LidOuter: List<Pair<Float, Float>> = lidProfile(0f)
private val LidInner: List<Pair<Float, Float>> = lidProfile(LID_THICKNESS)

/** One strip of the lid between two points of the profile, swung open: its edges (y, z), and which way it faces. */
private class LidStrip(val index: Int, val from: Pair<Float, Float>, val to: Pair<Float, Float>, val facing: Float) {
    val depth: Float = (from.second + to.second) / 2f
}

/** [profile] swung back [degrees] round the lid's hinge at the body's back top edge, as (y, z) points. */
private fun swung(profile: List<Pair<Float, Float>>, degrees: Float): List<Pair<Float, Float>> {
    val angle = degrees * PI.toFloat() / 180f
    val c = cos(angle)
    val s = sin(angle)
    return profile.map { (up, forward) -> (BODY_HEIGHT + up * c + forward * s) to (-CHEST_DEPTH / 2f + forward * c - up * s) }
}

private fun lidStrips(points: List<Pair<Float, Float>>): List<LidStrip> = points.zipWithNext().mapIndexed { i, (a, b) ->
    // The outward normal: the profile's direction turned a quarter-turn outwards.
    LidStrip(i, a, b, facing(0f, -(b.second - a.second), b.first - a.first))
}

/**
 * The chest (the style [id]) with its [hoard] (null until it's painted) and its lid swung back [lidDegrees], its treasure showing once it's
 * [openness] open, [flourish] (0 to 1, 1 once it's over) through the burst of gold that opening it
 * throws out, and the [spills] it threw out this time.
 */
private fun CupDrawScope.drawChest(
    id: String,
    palette: ChestPalette,
    hoard: HoardArt?,
    lidDegrees: Float,
    openness: Float,
    flourish: Float,
    spills: List<Spill>,
) {
    val treasureCentre = point(0f, BODY_HEIGHT + 9f, 0f)
    if (lidDegrees < 1f && openness <= 0f) {
        drawBodyImage(id, palette)
        drawLid(lidDegrees, palette)
        drawSideHinge(lidDegrees, palette)
        return
    }
    // Open: a lid swung well back is behind everything; one only just lifting is in front of the
    // treasure it's uncovering. Then the body in front of it all, and whatever has spilled out.
    val swungBack = lidDegrees > 45f
    if (swungBack) drawLid(lidDegrees, palette)
    if (flourish < 1f) drawFlourishBehind(treasureCentre, flourish)
    drawOpening(palette)
    // Only once the lid is open and swung back behind it: the goblet, the crown and the top coins are
    // heaped higher than the rim, so a lid just lifting - rattled in the shake, or only starting to
    // fly open - would have them poking up through it. Until then the gap shows the dark inside.
    if (swungBack && openness > 0f) drawHoard(hoard, palette)
    if (!swungBack) drawLid(lidDegrees, palette)
    drawBodyImage(id, palette)
    drawSideHinge(lidDegrees, palette)
    // Only while the chest is open: gone the moment it starts to close for the next roll (which spills
    // a fresh handful), and never there when the lid just lifts in the shake.
    if (openness > 0f) drawSpill(spills, palette, flourish)
    if (flourish < 1f) drawFlourishInFront(treasureCentre, flourish)
}

/**
 * The lid, swung back [degrees], as a solid with thickness: the inside of its far end and its velvet
 * lining where they show, its wood outside, the edge of its wood along its front lip, then its near
 * end over the lot.
 */
private fun CupDrawScope.drawLid(degrees: Float, palette: ChestPalette) {
    val outer = swung(LidOuter, degrees)
    val inner = swung(LidInner, degrees)
    val half = CHEST_WIDTH / 2f
    val innerHalf = half - LID_THICKNESS
    // The inside of the far end, facing back into the lid.
    if (facing(1f, 0f, 0f) > 0f) drawProfileFace(inner, -innerHalf, lerp(palette.lining, Color.Black, 0.45f))
    // The lining: an inside strip shows where its inward side faces the viewer. It falls into shadow
    // towards the hinge, where the treasure is piled up against it.
    val strips = lidStrips(inner)
    for (strip in strips.sortedBy { it.depth }) {
        if (strip.facing >= 0f) continue
        val (y0, z0) = strip.from
        val (y1, z1) = strip.to
        val light = -strip.facing
        val nearHinge = ((strip.index - strips.size * 0.55f) / (strips.size * 0.45f)).coerceIn(0f, 1f)
        drawPath(
            quad(Triple(-innerHalf, y0, z0), Triple(innerHalf, y0, z0), Triple(innerHalf, y1, z1), Triple(-innerHalf, y1, z1)),
            lerp(lerp(lerp(palette.lining, Color.Black, 0.55f), palette.lining, light), Color.Black, 0.6f * nearHinge),
        )
    }
    for (strip in lidStrips(outer).sortedBy { it.depth }) if (strip.facing > 0f) drawLidStrip(strip, palette)
    // The front lip's edge: the thickness of the wood between outside and lining, facing out of the
    // lid's open side - seen once the lid is lifted.
    val (oy, oz) = outer[0]
    val (iy, iz) = inner[0]
    val (ny, nz) = outer[1]
    val lip = quad(Triple(-half, oy, oz), Triple(half, oy, oz), Triple(half, iy, iz), Triple(-half, iy, iz))
    if (facing(0f, oy - ny, oz - nz) > 0f) {
        drawPath(lip, palette.woodDark)
        drawPath(lip, palette.goldDark, style = Stroke(width = gx(0.35f)))
    }
    drawLidEnd(outer, inner, palette)
}

/**
 * What visibly holds the lid on, on the chest's right end: a hinge at the back corner - one leaf on
 * the body, one on the lid's end, the round knuckle between them on the hinge line - and, once the
 * lid's up, the gold stay running from the body's side up to the lid's end that holds it there.
 */
private fun CupDrawScope.drawSideHinge(lidDegrees: Float, palette: ChestPalette) {
    val x = CHEST_WIDTH / 2f + 0.2f
    val back = -CHEST_DEPTH / 2f
    val top = BODY_HEIGHT
    val rim = Stroke(gx(0.35f))
    if (lidDegrees > 30f) {
        // The stay: from low on the body's side, up to a pin on the lid's end.
        val (lidY, lidZ) = swung(listOf((LID_SIDE + 2f) * LID_RISE to 7f), lidDegrees).first()
        val from = point(x + 0.1f, top - 7f, back + 6f)
        val to = point(x + 0.1f, lidY, lidZ)
        drawLine(palette.goldDark, from, to, strokeWidth = gx(1.3f), cap = StrokeCap.Round)
        drawLine(palette.goldLight, from, to, strokeWidth = gx(0.45f), cap = StrokeCap.Round)
        drawStud(from, palette)
        drawStud(to, palette)
    }
    val bodyLeaf = sideRect(back, back + 3.2f, top - 5f, top, x = x)
    drawPath(bodyLeaf, palette.gold)
    drawPath(bodyLeaf, palette.goldDark, style = rim)
    drawStud(point(x, top - 3.4f, back + 1.6f), palette)
    // The lid's leaf, in the lid's own (up, forward) terms, swung with it.
    val lidLeaf = swung(listOf(0f to 0f, 0f to 3.2f, 5f to 3.2f, 5f to 0f), lidDegrees)
    val leaf = quad(*lidLeaf.map { (y, z) -> Triple(x, y, z) }.toTypedArray())
    drawPath(leaf, palette.gold)
    drawPath(leaf, palette.goldDark, style = rim)
    val (studY, studZ) = swung(listOf(3.4f to 1.6f), lidDegrees).first()
    drawStud(point(x, studY, studZ), palette)
    // The knuckle, on the hinge line itself.
    val knuckle = point(x + 0.2f, top, back)
    drawCircle(palette.goldDark, radius = gx(1.5f), center = knuckle)
    drawCircle(palette.gold, radius = gx(1.1f), center = knuckle)
    drawCircle(palette.goldLight, radius = gx(0.45f), center = knuckle - Offset(gx(0.3f), gy(0.3f)))
}

/** The lid's cross-section [points] as a flat face at [x] across the chest, in [colour]. */
private fun CupDrawScope.drawProfileFace(points: List<Pair<Float, Float>>, x: Float, colour: Color) {
    val face = Path().apply {
        points.forEachIndexed { i, (y, z) ->
            val p = point(x, y, z)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    drawPath(face, colour)
}

/** One strip of the lid's outside: its wood, lit by which way it faces, with its gold fittings. */
private fun CupDrawScope.drawLidStrip(strip: LidStrip, palette: ChestPalette) {
    val half = CHEST_WIDTH / 2f
    val (y0, z0) = strip.from
    val (y1, z1) = strip.to
    fun band(x0: Float, x1: Float) = quad(Triple(x0, y0, z0), Triple(x1, y0, z0), Triple(x1, y1, z1), Triple(x0, y1, z1))
    val light = strip.facing
    drawPath(band(-half, half), lerp(palette.woodDark, palette.woodLight, 0.25f + 0.75f * light))
    // A seam between planks every few strips.
    if (strip.index % 4 == 2) {
        drawLine(palette.seam, point(-half, y0, z0), point(half, y0, z0), strokeWidth = gy(0.35f))
    }
    val gold = lerp(palette.goldDark, palette.goldLight, 0.2f + 0.8f * light)
    // Gold edging along each end, and the two straps over the top.
    for ((x0, x1) in listOf(-half to -half + 1.4f, half - 1.4f to half, -half + STRAP_INSET to -half + STRAP_INSET + STRAP_WIDTH, half - STRAP_INSET - STRAP_WIDTH to half - STRAP_INSET)) {
        drawPath(band(x0, x1), gold)
    }
    if (strip.index == 0) {
        // The front lip's gold rim, and the hasp that hangs down over the lock.
        val rimTop = y0 + (y1 - y0) * 0.35f
        val rimZ = z0 + (z1 - z0) * 0.35f
        drawPath(quad(Triple(-half, y0, z0), Triple(half, y0, z0), Triple(half, rimTop, rimZ), Triple(-half, rimTop, rimZ)), gold)
        drawPath(band(-2.2f, 2.2f), gold)
    }
    // Studs down the straps.
    if (strip.index % 3 == 1) {
        val y = (y0 + y1) / 2f
        val z = (z0 + z1) / 2f
        for (x in listOf(-half + STRAP_INSET + STRAP_WIDTH / 2f, half - STRAP_INSET - STRAP_WIDTH / 2f)) drawStud(point(x, y, z), palette)
    }
}

/**
 * The lid's near end: the arch of its [outer] profile in wood, an inner panel of darker wood, and
 * gold trim round its edge - its wood as thick as the gap to the [inner] profile.
 */
private fun CupDrawScope.drawLidEnd(outer: List<Pair<Float, Float>>, inner: List<Pair<Float, Float>>, palette: ChestPalette) {
    val x = CHEST_WIDTH / 2f
    drawProfileFace(outer, x, lerp(palette.woodDark, palette.woodLight, 0.45f))
    val centreY = inner.sumOf { it.first.toDouble() }.toFloat() / inner.size
    val centreZ = inner.sumOf { it.second.toDouble() }.toFloat() / inner.size
    val panel = inner.map { (y, z) -> (centreY + (y - centreY) * 0.8f) to (centreZ + (z - centreZ) * 0.8f) }
    drawProfileFace(panel, x, lerp(palette.woodDark, palette.woodLight, 0.25f))
    val edge = Path().apply {
        outer.forEachIndexed { i, (y, z) ->
            val p = point(x, y, z)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    drawPath(edge, palette.gold, style = Stroke(width = gx(1.4f)))
}

/** A small domed stud at [at]. */
private fun CupDrawScope.drawStud(at: Offset, palette: ChestPalette) {
    drawCircle(palette.goldDark, radius = gx(0.6f), center = at)
    drawCircle(palette.goldLight, radius = gx(0.25f), center = at - Offset(gx(0.15f), gy(0.15f)))
}

/** Which chest's body a painted image is of - one per style, at each size it's drawn. */
private data class ChestBodyImage(val chestId: String)

/**
 * The box (the style [id]), painted once at this size and stamped from then on: it never changes, and
 * the shake's lifting lid redraws the chest every frame.
 */
private fun CupDrawScope.drawBodyImage(id: String, palette: ChestPalette) {
    // Painted at the chest's scale, so it's stamped with that scale undone.
    val pivot = chestPivot(size)
    unscaledAbout(CHEST_SCALE, pivot) {
        drawCachedSurface(ChestBodyImage(id)) { scaledAbout(CHEST_SCALE, pivot) { CupDrawScope(this, CupShape.SQUAT, CupPose(0f, 0f)).drawBody(palette) } }
    }
}

/**
 * The box: its plinth, then its front and right end in planked wood, with gold rims top and bottom,
 * corner brackets, studded straps, a shield-shaped lock plate on the front and a ring handle on the
 * end. (The tops of its walls are drawn with the opening, before the treasure piled over them.)
 */
private fun CupDrawScope.drawBody(palette: ChestPalette) {
    val half = CHEST_WIDTH / 2f
    val front = CHEST_DEPTH / 2f
    val sideShade = 0.72f
    val ph = half + PLINTH_OVERHANG
    val pf = front + PLINTH_OVERHANG
    drawFootprintShadow(ph, pf)
    // The plinth: a slightly wider base, darker, with a gold edge along its top.
    drawPath(quad(Triple(-ph, PLINTH_HEIGHT, pf), Triple(ph, PLINTH_HEIGHT, pf), Triple(ph, PLINTH_HEIGHT, -pf), Triple(-ph, PLINTH_HEIGHT, -pf)), palette.woodDark)
    drawPath(frontRect(-ph, ph, 0f, PLINTH_HEIGHT, z = pf), lerp(palette.woodDark, Color.Black, 0.2f))
    drawPath(sideRect(-pf, pf, 0f, PLINTH_HEIGHT, x = ph), lerp(palette.woodDark, Color.Black, 0.4f))
    drawPath(frontRect(-ph, ph, PLINTH_HEIGHT - 0.7f, PLINTH_HEIGHT, z = pf), palette.gold)
    drawPath(sideRect(-pf, pf, PLINTH_HEIGHT - 0.7f, PLINTH_HEIGHT, x = ph), palette.goldDark)

    val bottom = PLINTH_HEIGHT
    val top = BODY_HEIGHT
    val frontTop = point(0f, top, front)
    val frontBottom = point(0f, bottom, front)
    drawPath(frontRect(-half, half, bottom, top), Brush.verticalGradient(listOf(palette.woodLight, palette.woodDark), startY = frontTop.y, endY = frontBottom.y))
    drawPath(sideRect(-front, front, bottom, top), Brush.verticalGradient(listOf(lerp(palette.woodDark, palette.woodLight, sideShade), palette.woodDark), startY = frontTop.y, endY = frontBottom.y))
    // Seams between three planks, front and end.
    for (i in 1..2) {
        val y = bottom + (top - bottom) * i / 3f
        drawLine(palette.seam, point(-half, y, front), point(half, y, front), strokeWidth = gy(0.4f))
        drawLine(palette.seam, point(half, y, front), point(half, y, -front), strokeWidth = gy(0.4f))
    }
    val frontGold = Brush.verticalGradient(listOf(palette.goldLight, palette.gold, palette.goldDark), startY = frontTop.y, endY = frontBottom.y)
    val sideGold = Brush.verticalGradient(listOf(palette.gold, palette.goldDark), startY = frontTop.y, endY = frontBottom.y)
    // Straps under the lid's, a strap round the end, and rims along the top and bottom.
    for (x in listOf(-half + STRAP_INSET, half - STRAP_INSET - STRAP_WIDTH)) drawPath(frontRect(x, x + STRAP_WIDTH, bottom, top), frontGold)
    drawPath(sideRect(-STRAP_WIDTH / 2f, STRAP_WIDTH / 2f, bottom, top), sideGold)
    drawPath(frontRect(-half, half, top - 1.4f, top), frontGold)
    drawPath(sideRect(-front, front, top - 1.4f, top), sideGold)
    drawPath(frontRect(-half, half, bottom, bottom + 1.2f), frontGold)
    drawPath(sideRect(-front, front, bottom, bottom + 1.2f), sideGold)
    // Corner brackets: each one folded round its corner, so it shows on both faces it covers.
    drawPath(frontRect(-half, -half + CORNER_WIDTH, bottom, top), frontGold)
    drawPath(frontRect(half - CORNER_WIDTH, half, bottom, top), frontGold)
    drawPath(sideRect(front - CORNER_WIDTH, front, bottom, top), sideGold)
    drawPath(sideRect(-front, -front + CORNER_WIDTH, bottom, top), sideGold)
    // Studs down the straps and brackets.
    var y = bottom + 3f
    while (y < top - 2f) {
        for (x in listOf(-half + CORNER_WIDTH / 2f, half - CORNER_WIDTH / 2f, -half + STRAP_INSET + STRAP_WIDTH / 2f, half - STRAP_INSET - STRAP_WIDTH / 2f)) {
            drawStud(point(x, y, front), palette)
        }
        drawStud(point(half, y, 0f), palette)
        y += 4f
    }
    drawLock(palette, top)
    drawHandle(palette, top)
}

/**
 * The chest's shadow on the table: its own footprint - the plinth's outline, [halfWidth] by
 * [halfDepth] - cast a little to the right and back, away from the light, and softened by laying it
 * down in a few faint, ever-larger copies. Painted with the body, so it costs nothing per frame.
 */
private fun CupDrawScope.drawFootprintShadow(halfWidth: Float, halfDepth: Float) {
    val shiftX = 3.5f
    val shiftZ = -2.5f
    for ((spread, alpha) in listOf(4.5f to 0.06f, 3f to 0.08f, 1.5f to 0.1f, 0f to 0.14f)) {
        val w = halfWidth + spread
        val d = halfDepth + spread
        drawPath(
            quad(Triple(-w + shiftX, 0f, d + shiftZ), Triple(w + shiftX, 0f, d + shiftZ), Triple(w + shiftX, 0f, -d + shiftZ), Triple(-w + shiftX, 0f, -d + shiftZ)),
            Color.Black.copy(alpha = alpha),
        )
    }
}

/** The lock: a shield-shaped gold plate under the lid's hasp, with a keyhole. */
private fun CupDrawScope.drawLock(palette: ChestPalette, top: Float) {
    val z = CHEST_DEPTH / 2f + 0.1f
    val plate = quad(
        Triple(-4f, top - 0.6f, z), Triple(4f, top - 0.6f, z), Triple(4f, top - 7f, z), Triple(0f, top - 10f, z), Triple(-4f, top - 7f, z),
    )
    val plateTop = point(0f, top, z)
    val plateBottom = point(0f, top - 10f, z)
    drawPath(plate, Brush.verticalGradient(listOf(palette.goldLight, palette.gold, palette.goldDark), startY = plateTop.y, endY = plateBottom.y))
    drawPath(plate, palette.goldDark, style = Stroke(gx(0.45f)))
    val keyhole = point(0f, top - 4.2f, z)
    drawCircle(palette.interior, radius = gx(1.1f), center = keyhole)
    drawPath(quad(Triple(-0.5f, top - 4.2f, z), Triple(0.5f, top - 4.2f, z), Triple(0.9f, top - 7f, z), Triple(-0.9f, top - 7f, z)), palette.interior)
}

/** A gold ring handle hanging from a plate on the chest's right end. */
private fun CupDrawScope.drawHandle(palette: ChestPalette, top: Float) {
    val x = CHEST_WIDTH / 2f + 0.15f
    drawPath(sideRect(-2.4f, 2.4f, top - 7.5f, top - 4f, x = x), palette.gold)
    drawPath(sideRect(-2.4f, 2.4f, top - 7.5f, top - 4f, x = x), palette.goldDark, style = Stroke(gx(0.35f)))
    val ring = Path()
    for (i in 0..24) {
        val a = i * 2f * PI.toFloat() / 24
        val p = point(x + 0.2f, top - 9.6f + 3.2f * sin(a), 3.2f * cos(a))
        if (i == 0) ring.moveTo(p.x, p.y) else ring.lineTo(p.x, p.y)
    }
    drawPath(ring, palette.goldDark, style = Stroke(gx(1.3f)))
    drawPath(ring, palette.goldLight, style = Stroke(gx(0.5f)))
}

/** The open top of the box: the tops of its walls round a dark inside. */
private fun CupDrawScope.drawOpening(palette: ChestPalette) {
    val half = CHEST_WIDTH / 2f
    val front = CHEST_DEPTH / 2f
    val top = BODY_HEIGHT
    // The tops of all four walls in one piece of wood, so they meet cleanly at the corners, then the
    // dark inside, edged in gold where the walls drop away.
    drawPath(quad(Triple(-half, top, front), Triple(half, top, front), Triple(half, top, -front), Triple(-half, top, -front)), palette.woodLight)
    val ih = half - WALL
    val inf = front - WALL
    val inside = quad(Triple(-ih, top, inf), Triple(ih, top, inf), Triple(ih, top, -inf), Triple(-ih, top, -inf))
    drawPath(inside, palette.interior)
    drawPath(inside, palette.goldDark, style = Stroke(gy(0.4f)))
    // The two hinges the lid swings on.
    for (x in listOf(-half + STRAP_INSET, half - STRAP_INSET - STRAP_WIDTH)) {
        val hinge = quad(Triple(x, top + 1.2f, -front - 0.4f), Triple(x + STRAP_WIDTH, top + 1.2f, -front - 0.4f), Triple(x + STRAP_WIDTH, top - 0.4f, -front - 0.4f), Triple(x, top - 0.4f, -front - 0.4f))
        drawPath(hinge, palette.gold)
        drawPath(hinge, palette.goldDark, style = Stroke(gx(0.35f)))
    }
}

// The treasure is settled rather than placed: the chest is full to just under its rim, a bed of coins
// lies across the top of that, and then coins are dropped one at a time onto whatever is highest
// beneath them, rolling downhill wherever they'd perch on a slope steeper than a pile can hold - so
// every coin rests on something, and the heap takes the shape a real one would.
private const val PILE_CELL = 0.5f
private const val COIN_THICKNESS = 0.35f
private const val DROPPED_COINS = 340
// The steepest slope a heap of coins holds (rise over run) before a coin slides off it.
private const val REPOSE = 0.55f
// How far below the rim the chest's contents come before the pile starts.
private const val FILL_BELOW_RIM = 0.4f

/** A coin come to rest: its centre, its size, how it's tipped (rise per unit across and per unit forward), and its own shade. */
private class SettledCoin(val x: Float, val y: Float, val z: Float, val radius: Float, val tiltX: Float, val tiltZ: Float, val shade: Float) {
    /** How far towards the viewer it lies - what's drawn over what. */
    val depth: Float = towardViewer(x, z) * ElevationCos + y * ElevationSin
}

/** The settled pile: its coins, back to front, and the height of its surface everywhere inside the chest. */
private class Pile(val coins: List<SettledCoin>, private val heights: FloatArray, private val columns: Int, private val rows: Int) {
    fun heightAt(x: Float, z: Float): Float {
        val c = ((x + PILE_HALF_WIDTH) / PILE_CELL).roundToInt().coerceIn(0, columns - 1)
        val r = ((z + PILE_HALF_DEPTH) / PILE_CELL).roundToInt().coerceIn(0, rows - 1)
        return heights[r * columns + c]
    }
}

private const val PILE_HALF_WIDTH = CHEST_WIDTH / 2f - WALL
private const val PILE_HALF_DEPTH = CHEST_DEPTH / 2f - WALL

/** The hoard, settled once - the same every time, since it's seeded. */
private val SettledPile: Pile by lazy { settlePile() }

private fun settlePile(): Pile {
    val columns = (2f * PILE_HALF_WIDTH / PILE_CELL).roundToInt() + 1
    val rows = (2f * PILE_HALF_DEPTH / PILE_CELL).roundToInt() + 1
    val heights = FloatArray(columns * rows) { BODY_HEIGHT - FILL_BELOW_RIM }
    fun at(x: Float, z: Float): Float {
        val c = ((x + PILE_HALF_WIDTH) / PILE_CELL).roundToInt().coerceIn(0, columns - 1)
        val r = ((z + PILE_HALF_DEPTH) / PILE_CELL).roundToInt().coerceIn(0, rows - 1)
        return heights[r * columns + c]
    }
    val random = Random(7)
    val coins = mutableListOf<SettledCoin>()

    /** The highest point anywhere under a coin of [radius] at [x], [z] - what it would come to rest on. */
    fun highestUnder(x: Float, z: Float, radius: Float): Float {
        var highest = -Float.MAX_VALUE
        val c0 = ((x - radius + PILE_HALF_WIDTH) / PILE_CELL).toInt().coerceIn(0, columns - 1)
        val c1 = ((x + radius + PILE_HALF_WIDTH) / PILE_CELL).toInt().coerceIn(0, columns - 1)
        val r0 = ((z - radius + PILE_HALF_DEPTH) / PILE_CELL).toInt().coerceIn(0, rows - 1)
        val r1 = ((z + radius + PILE_HALF_DEPTH) / PILE_CELL).toInt().coerceIn(0, rows - 1)
        for (r in r0..r1) for (c in c0..c1) {
            val dx = -PILE_HALF_WIDTH + c * PILE_CELL - x
            val dz = -PILE_HALF_DEPTH + r * PILE_CELL - z
            if (dx * dx + dz * dz <= radius * radius) highest = max(highest, heights[r * columns + c])
        }
        return highest
    }

    /**
     * Lays a coin of [radius] at [x], [z] on whatever is highest under it, tipped gently to match the
     * slope it lands on - measured just outside its footprint, so a coin's own raised edge never
     * steepens the slope the next one finds there - or lying [flat].
     */
    fun land(x: Float, z: Float, radius: Float, flat: Boolean = false) {
        val reach = radius + 1.2f
        val tiltX = if (flat) (random.nextFloat() - 0.5f) * 0.1f else ((at(x + reach, z) - at(x - reach, z)) / (2f * reach) * 0.7f).coerceIn(-0.4f, 0.4f)
        val tiltZ = if (flat) (random.nextFloat() - 0.5f) * 0.1f else ((at(x, z + reach) - at(x, z - reach)) / (2f * reach) * 0.7f).coerceIn(-0.4f, 0.4f)
        val c0 = ((x - radius + PILE_HALF_WIDTH) / PILE_CELL).toInt().coerceIn(0, columns - 1)
        val c1 = ((x + radius + PILE_HALF_WIDTH) / PILE_CELL).toInt().coerceIn(0, columns - 1)
        val r0 = ((z - radius + PILE_HALF_DEPTH) / PILE_CELL).toInt().coerceIn(0, rows - 1)
        val r1 = ((z + radius + PILE_HALF_DEPTH) / PILE_CELL).toInt().coerceIn(0, rows - 1)
        // It comes to rest touching the highest point under it, measured against its own tilt.
        var base = if (flat) BODY_HEIGHT - FILL_BELOW_RIM else -Float.MAX_VALUE
        if (!flat) for (r in r0..r1) for (c in c0..c1) {
            val dx = -PILE_HALF_WIDTH + c * PILE_CELL - x
            val dz = -PILE_HALF_DEPTH + r * PILE_CELL - z
            if (dx * dx + dz * dz <= radius * radius) base = max(base, heights[r * columns + c] - tiltX * dx - tiltZ * dz)
        }
        val y = base + COIN_THICKNESS
        for (r in r0..r1) for (c in c0..c1) {
            val dx = -PILE_HALF_WIDTH + c * PILE_CELL - x
            val dz = -PILE_HALF_DEPTH + r * PILE_CELL - z
            if (dx * dx + dz * dz <= radius * radius) {
                val i = r * columns + c
                heights[i] = max(heights[i], y + tiltX * dx + tiltZ * dz)
            }
        }
        coins += SettledCoin(x, y, z, radius, tiltX, tiltZ, random.nextFloat())
    }

    // The bed: the top layer of the coins filling the chest, across the whole of it.
    var bedZ = -PILE_HALF_DEPTH + 0.8f
    while (bedZ < PILE_HALF_DEPTH) {
        var bedX = -PILE_HALF_WIDTH + 0.6f + random.nextFloat()
        while (bedX < PILE_HALF_WIDTH) {
            land(bedX, bedZ + (random.nextFloat() - 0.5f) * 0.6f, 1.8f + random.nextFloat() * 0.5f, flat = true)
            bedX += 2.2f + random.nextFloat() * 0.6f
        }
        bedZ += 1.5f
    }
    // Then the heap: dropped mostly towards the middle, each rolling off any slope too steep to hold it.
    fun gaussian(): Float = sqrt(-2f * ln(random.nextFloat().coerceAtLeast(1e-6f))) * cos(2f * PI.toFloat() * random.nextFloat())
    val directions = List(8) { Offset(cos(it * PI.toFloat() / 4f), sin(it * PI.toFloat() / 4f)) }
    repeat(DROPPED_COINS) {
        val radius = 1.8f + random.nextFloat() * 0.6f
        val limitX = PILE_HALF_WIDTH - radius * 0.6f
        val limitZ = PILE_HALF_DEPTH - radius * 0.6f
        var x = (gaussian() * PILE_HALF_WIDTH * 0.45f).coerceIn(-limitX, limitX)
        var z = (gaussian() * PILE_HALF_DEPTH * 0.5f).coerceIn(-limitZ, limitZ)
        // Compared with the surface just beyond its own footprint, which the last coin here didn't raise.
        val reach = radius + 1.2f
        for (step in 0 until 60) {
            // Where it would rest, against the lowest ground just beyond it: too steep, and it rolls.
            val rest = highestUnder(x, z, radius)
            val lowest = directions.minBy { at(x + it.x * reach, z + it.y * reach) }
            if (rest - at(x + lowest.x * reach, z + lowest.y * reach) <= REPOSE * reach) break
            x = (x + lowest.x * 0.8f).coerceIn(-limitX, limitX)
            z = (z + lowest.y * 0.8f).coerceIn(-limitZ, limitZ)
        }
        land(x, z, radius)
    }
    return Pile(coins.sortedBy { it.depth }, heights, columns, rows)
}

/** One of the few pieces lying on the pile: [x], [z] across it, resting on its surface there. */
private sealed class Treasure(val x: Float, val z: Float) {
    class Goblet(x: Float, z: Float) : Treasure(x, z)

    class Crown(x: Float, z: Float) : Treasure(x, z)

    /** A loose cut stone: [gem], [size] across, cut by [seed]. */
    class Jewel(x: Float, z: Float, val gem: Gem, val size: Float, val seed: Int) : Treasure(x, z)

    /** A string of pearls lying across the pile from [x] to [toX]. */
    class Pearls(x: Float, z: Float, val toX: Float) : Treasure(x, z)
}

// Loose stones and the pearls, painted with the coins; the goblet and crown, drawn live on top.
private val PaintedTreasure: List<Treasure> = buildList {
    val random = Random(19)
    add(Treasure.Pearls(-9f, 4.5f, toX = 12f))
    for ((x, z, gem) in listOf(Triple(-16f, 2f, 1), Triple(-3f, -1f, 3), Triple(5f, 5f, 0), Triple(16f, 1f, 4), Triple(-7f, 6f, 5), Triple(2f, -5f, 2))) {
        add(Treasure.Jewel(x, z, GemsByValue[gem], 2.2f, random.nextInt()))
    }
}
private val LiveTreasure: List<Treasure> = listOf(Treasure.Goblet(-11f, -4f), Treasure.Crown(10f, -5f))

// The highest few coins are drawn live, over everything painted, so nothing cuts across them.
private const val LIVE_COINS = 6

/** The hoard as painted for one chest at one size: the settled [pile], and the [image] of the bulk of it. */
private class HoardArt(val pile: Pile, val image: ImageBitmap)

// Painted hoards, kept for the session by chest and size - only ever read and written on the main thread.
private val HoardArts = LinkedHashMap<Pair<String, IntSize>, HoardArt>()

/** For tests only: whether the chest [id]'s hoard has been painted (at any size), so a test can wait for the background painting. */
fun isHoardPaintedForTest(id: String): Boolean = HoardArts.keys.any { it.first == id }
private const val HOARD_ARTS_KEPT = 4

/**
 * The hoard for the chest [id] at [size]: simulated and painted on a background thread the first time
 * it's needed at that size - it's the heaviest thing the chest draws, too heavy for the frame it
 * appears on - then kept. Null until it's ready, and for a zero [size] (not wanted yet).
 */
@Composable
private fun rememberHoardArt(id: String, palette: ChestPalette, size: IntSize): State<HoardArt?> {
    val density = LocalDensity.current
    return produceState(HoardArts[id to size], id, size) {
        if (size.width <= 0 || size.height <= 0 || value != null) return@produceState
        // Should painting ever fail (no bitmap to paint into), the chest keeps its stand-in gold fill
        // rather than taking the game down with it.
        val art = withContext(Dispatchers.Default) {
            runCatching {
                val pile = SettledPile
                val image = ImageBitmap(size.width, size.height)
                CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(size.width.toFloat(), size.height.toFloat())) {
                    scaledAbout(CHEST_SCALE, chestPivot(this.size)) { CupDrawScope(this, CupShape.SQUAT, CupPose(0f, 0f)).drawPaintedHoard(pile, palette) }
                }
                HoardArt(pile, image)
            }.getOrNull()
        } ?: return@produceState
        HoardArts[id to size] = art
        if (HoardArts.size > HOARD_ARTS_KEPT) HoardArts.remove(HoardArts.keys.first())
        value = art
    }
}

/**
 * The treasure, heaped over the rim: the painted bulk of the settled pile - several hundred coins,
 * the pearls and the loose stones - stamped as one image, then the goblet, the crown and the highest
 * few coins drawn over it. Until the painting's ready, a plain heap of gold fills the chest, so it's
 * never seen empty.
 */
private fun CupDrawScope.drawHoard(hoard: HoardArt?, palette: ChestPalette) {
    if (hoard == null) {
        val half = CHEST_WIDTH / 2f - WALL
        val front = CHEST_DEPTH / 2f - WALL
        val top = BODY_HEIGHT
        drawPath(
            quad(Triple(-half, top, front), Triple(half, top, front), Triple(half, top, -front), Triple(-half, top, -front)),
            Brush.verticalGradient(listOf(palette.gold, palette.goldDark), startY = point(0f, top, -front).y, endY = point(0f, top, front).y),
        )
        return
    }
    // Painted at the chest's scale, so it's stamped with that scale undone.
    unscaledAbout(CHEST_SCALE, chestPivot(size)) { drawImage(hoard.image) }
    for (treasure in LiveTreasure) drawTreasure(treasure, hoard.pile, palette)
    for (coin in topCoins(hoard.pile)) drawSettledCoin(coin, hoard.pile, palette)
}

private fun topCoins(pile: Pile): List<SettledCoin> = pile.coins.sortedByDescending { it.y }.take(LIVE_COINS).sortedBy { it.depth }

private fun CupDrawScope.drawPaintedHoard(pile: Pile, palette: ChestPalette) {
    val top = topCoins(pile).toSet()
    // Everything in one back-to-front order: coins, stones and pearls by how far towards the viewer they lie.
    val treasures = PaintedTreasure.map { it to (towardViewer(it.x, it.z) * ElevationCos + (pile.heightAt(it.x, it.z) + 0.5f) * ElevationSin) }
    var next = 0
    val ordered = treasures.sortedBy { it.second }
    for (coin in pile.coins) {
        while (next < ordered.size && ordered[next].second < coin.depth) drawTreasure(ordered[next++].first, pile, palette)
        if (coin !in top) drawSettledCoin(coin, pile, palette)
    }
    while (next < ordered.size) drawTreasure(ordered[next++].first, pile, palette)
}

private fun CupDrawScope.drawTreasure(treasure: Treasure, pile: Pile, palette: ChestPalette) {
    val surface = pile.heightAt(treasure.x, treasure.z)
    when (treasure) {
        is Treasure.Goblet -> drawGoblet(point(treasure.x, surface, treasure.z), palette)
        is Treasure.Crown -> drawCrown(point(treasure.x, surface, treasure.z), palette)
        is Treasure.Jewel -> drawGem(point(treasure.x, surface + 0.4f, treasure.z), gx(treasure.size), treasure.gem, treasure.seed)
        is Treasure.Pearls -> drawPearls(treasure, pile)
    }
}

// Where the light on the treasure comes from: above, a little to the left and in front.
private val LightX = -0.38f
private val LightY = 0.85f
private val LightZ = 0.36f

/**
 * A settled coin as it lies: its edge (its thickness, showing below its face), its rim, its face, and
 * a glint - each the ellipse the coin's tilted face projects to, so it lies exactly on what it rests on. It's
 * lit by how its face turns to the light, and darker the further it sits down in a hollow of the pile.
 */
private fun CupDrawScope.drawSettledCoin(coin: SettledCoin, pile: Pile, palette: ChestPalette) {
    // The coin's face: its normal, and two directions across it.
    val nl = sqrt(coin.tiltX * coin.tiltX + 1f + coin.tiltZ * coin.tiltZ)
    val nx = -coin.tiltX / nl
    val ny = 1f / nl
    val nz = -coin.tiltZ / nl
    val ul = sqrt(1f + coin.tiltX * coin.tiltX)
    val ux = 1f / ul
    val uy = coin.tiltX / ul
    // v = n x u, across the face the other way.
    val vx = ny * 0f - nz * uy
    val vy = nz * ux - nx * 0f
    val vz = nx * uy - ny * ux
    // The face projects to an ellipse: work out its axes once from where the two directions across
    // the face land on screen, then draw every part of the coin as a rotated oval.
    val centre = point(coin.x, coin.y, coin.z)
    val alongU = point(coin.x + ux * coin.radius, coin.y + uy * coin.radius, coin.z) - centre
    val alongV = point(coin.x + vx * coin.radius, coin.y + vy * coin.radius, coin.z + vz * coin.radius) - centre
    val sxx = alongU.x * alongU.x + alongV.x * alongV.x
    val syy = alongU.y * alongU.y + alongV.y * alongV.y
    val sxy = alongU.x * alongU.y + alongV.x * alongV.y
    val spread = sqrt((sxx - syy) * (sxx - syy) + 4f * sxy * sxy)
    val major = sqrt(((sxx + syy + spread) / 2f).coerceAtLeast(0f))
    val minor = sqrt(((sxx + syy - spread) / 2f).coerceAtLeast(0f))
    val angle = 0.5f * atan2(2f * sxy, sxx - syy) * 180f / PI.toFloat()
    val edgeDrop = point(coin.x, coin.y - COIN_THICKNESS, coin.z) - centre
    val glintAt = centre - alongU * 0.28f - alongV * 0.22f
    fun oval(at: Offset, scale: Float, colour: Color) {
        rotate(angle, at) { drawOval(colour, at - Offset(major * scale, minor * scale), Size(major * scale * 2f, minor * scale * 2f)) }
    }
    val diffuse = (nx * LightX + ny * LightY + nz * LightZ).coerceIn(0f, 1f)
    // How far down in a hollow it lies, against the surface a little way round it.
    val around = listOf(-1f to 0f, 1f to 0f, 0f to -1f, 0f to 1f).map { (dx, dz) -> pile.heightAt(coin.x + dx * coin.radius * 1.6f, coin.z + dz * coin.radius * 1.6f) }.average().toFloat()
    val hollow = ((around - coin.y) / 2f).coerceIn(0f, 1f)
    val light = ((0.3f + 0.7f * diffuse) * (1f - 0.55f * hollow) * (0.8f + 0.2f * coin.shade)).coerceIn(0f, 1f)
    val edgeColour = lerp(lerp(palette.goldDark, palette.interior, 0.35f), palette.goldDark, light)
    oval(centre + edgeDrop, 1f, edgeColour)
    oval(centre, 1f, lerp(lerp(palette.goldDark, palette.interior, 0.2f), palette.gold, light))
    oval(centre, 0.82f, lerp(lerp(palette.goldDark, palette.gold, 0.4f), palette.goldLight, light))
    oval(glintAt, 0.28f, Color.White.copy(alpha = 0.4f * light))
}

/**
 * The soft shadow a coin lying at [face] casts on whatever it rests on, just below and to the right
 * of it - what makes it sit on the table rather than hover over it.
 */
private fun DrawScope.drawContactShadow(face: Rect, radius: Float, squash: Float) {
    val shadow = face.translate(radius * 0.18f, radius * squash * 0.55f).inflate(radius * 0.08f)
    drawOval(Color.Black.copy(alpha = 0.32f), shadow.topLeft, shadow.size)
}

/** Three shades of gold: a coin's rim, its face, and where its face catches the light. */
private class Gold(val light: Color, val mid: Color, val dark: Color)

private val ChestPalette.coinGold: Gold get() = Gold(goldLight, gold, goldDark)

/**
 * A coin lying [squash] tipped and turned [angle] degrees at [centre]: its rim, its face, and a glint
 * where it catches the light - with a contact shadow under it when it's [grounded] on something.
 */
private fun CupDrawScope.drawCoin(
    centre: Offset,
    radius: Float,
    squash: Float,
    angle: Float,
    shade: Float,
    gold: Gold,
    alpha: Float = 1f,
    grounded: Boolean = false,
) {
    val face = Rect(Offset(centre.x - radius, centre.y - radius * squash), Size(radius * 2f, radius * 2f * squash))
    val inner = Rect(face.left + radius * 0.16f, face.top + radius * 0.16f * squash, face.right - radius * 0.16f, face.bottom - radius * 0.16f * squash)
    if (grounded) drawContactShadow(face, radius, squash)
    rotate(angle, centre) {
        drawOval(lerp(gold.dark, gold.mid, shade * 0.5f).copy(alpha = alpha), face.topLeft, face.size)
        drawOval(lerp(gold.mid, gold.light, shade).copy(alpha = alpha), inner.topLeft, inner.size)
        drawOval(
            Color.White.copy(alpha = 0.45f * shade * alpha),
            Offset(inner.left + inner.width * 0.18f, inner.top + inner.height * 0.12f),
            Size(inner.width * 0.4f, inner.height * 0.3f),
        )
    }
}

/** A gold goblet standing in the pile, its foot at [base] and leaning a little, with a ruby on its bowl. */
private fun CupDrawScope.drawGoblet(base: Offset, palette: ChestPalette) {
    // Local helpers: the cup grid isn't reachable by implicit receiver inside rotate's own DrawScope.
    fun x(v: Float) = gx(v)
    fun y(v: Float) = gy(v)
    val gold = Brush.horizontalGradient(listOf(palette.goldLight, palette.gold, palette.goldDark), startX = base.x - x(4f), endX = base.x + x(4f))
    rotate(-10f, base) {
        drawOval(palette.goldDark, Offset(base.x - x(3f), base.y - y(1f)), Size(x(6f), y(2f)))
        drawRect(gold, Offset(base.x - x(0.7f), base.y - y(6f)), Size(x(1.4f), y(5.5f)))
        drawOval(gold, Offset(base.x - x(1.4f), base.y - y(4.6f)), Size(x(2.8f), y(1.4f)))
        val bowl = Path().apply {
            moveTo(base.x - x(4f), base.y - y(12.5f))
            cubicTo(base.x - x(4f), base.y - y(8f), base.x - x(2f), base.y - y(6f), base.x, base.y - y(6f))
            cubicTo(base.x + x(2f), base.y - y(6f), base.x + x(4f), base.y - y(8f), base.x + x(4f), base.y - y(12.5f))
            close()
        }
        drawPath(bowl, gold)
        drawOval(palette.goldLight, Offset(base.x - x(4f), base.y - y(13.4f)), Size(x(8f), y(1.8f)))
        drawOval(palette.interior, Offset(base.x - x(3.3f), base.y - y(13.1f)), Size(x(6.6f), y(1.2f)))
        drawGem(Offset(base.x, base.y - y(9.3f)), x(1.6f), GemsByValue[1], seed = 11)
    }
}

/** A gold crown sitting in the pile at [base], with a ball on each point and stones set round its band. */
private fun CupDrawScope.drawCrown(base: Offset, palette: ChestPalette) {
    // Local helpers: the cup grid isn't reachable by implicit receiver inside rotate's own DrawScope.
    fun x(v: Float) = gx(v)
    fun y(v: Float) = gy(v)
    val gold = Brush.horizontalGradient(listOf(palette.goldLight, palette.gold, palette.goldDark), startX = base.x - x(6f), endX = base.x + x(6f))
    rotate(8f, base) {
        val points = Path().apply {
            moveTo(base.x - x(6f), base.y - y(2.5f))
            lineTo(base.x - x(6.5f), base.y - y(9f))
            lineTo(base.x - x(3f), base.y - y(5f))
            lineTo(base.x, base.y - y(10f))
            lineTo(base.x + x(3f), base.y - y(5f))
            lineTo(base.x + x(6.5f), base.y - y(9f))
            lineTo(base.x + x(6f), base.y - y(2.5f))
            close()
        }
        drawPath(points, gold)
        drawPath(points, palette.goldDark, style = Stroke(x(0.3f)))
        for (tip in listOf(Offset(-6.5f, -9f), Offset(0f, -10f), Offset(6.5f, -9f))) {
            drawCircle(palette.goldLight, radius = x(0.9f), center = base + Offset(x(tip.x), y(tip.y)))
        }
        drawRect(gold, Offset(base.x - x(6f), base.y - y(3f)), Size(x(12f), y(3f)))
        drawRect(palette.goldDark, Offset(base.x - x(6f), base.y - y(3f)), Size(x(12f), y(3f)), style = Stroke(x(0.3f)))
        for ((i, dx) in listOf(-3.5f, 0f, 3.5f).withIndex()) {
            drawGem(base + Offset(x(dx), -y(1.5f)), x(1.3f), GemsByValue[listOf(3, 1, 2)[i]], seed = 20 + i)
        }
    }
}

/** A string of pearls lying across the pile, following its surface. */
private fun CupDrawScope.drawPearls(pearls: Treasure.Pearls, pile: Pile) {
    val count = 14
    for (i in 0..count) {
        val f = i / count.toFloat()
        val x = pearls.x + (pearls.toX - pearls.x) * f
        val z = pearls.z + 1.5f * sin(f * PI.toFloat())
        val at = point(x, pile.heightAt(x, z) + 0.5f, z)
        val r = gx(0.8f)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFE8E0D0), Color(0xFFB0A692)), at - Offset(r * 0.3f, r * 0.3f), r * 1.3f), r, at)
    }
}

/**
 * One piece thrown out of the chest as it opens: a coin, or a sapphire cut by [seed] if [isGem]. It
 * leaves the top of the pile at ([fromX], [fromZ]) when the flourish is [start] of the way through,
 * arcs [arc] high, spinning at [spin], and comes to rest at ([x], [y], [z]) - over the front rim, or
 * [onTable] - lying [squash] tipped and turned [angle] degrees.
 */
private class Spill(
    val isGem: Boolean,
    val seed: Int,
    val fromX: Float,
    val fromZ: Float,
    val x: Float,
    val y: Float,
    val z: Float,
    val onTable: Boolean,
    val squash: Float,
    val angle: Float,
    val start: Float,
    val arc: Float,
    val spin: Float,
)

// Where spilled pieces may land: hanging over the front rim along most of its width, or on the table in
// a band in front of the plinth - always within these, never on top of one another.
private const val SPILL_RIM_HALF_WIDTH = 18f
private const val SPILL_TABLE_HALF_WIDTH = 20f
private const val SPILL_TABLE_NEAR = CHEST_DEPTH / 2f + 2.5f
private const val SPILL_TABLE_FAR = CHEST_DEPTH / 2f + 7f
private const val SPILL_SPACING = 4f

/** A fresh handful to spill, chosen by [seed]: two to four coins and one or two sapphires, each thrown its own way. */
private fun spillHandful(seed: Int): List<Spill> {
    val random = Random(seed)
    val taken = mutableListOf<Offset>()
    fun clearOf(x: Float, z: Float) = taken.none { (it - Offset(x, z)).getDistance() < SPILL_SPACING }
    fun piece(isGem: Boolean): Spill? {
        val onTable = isGem || random.nextFloat() < 0.65f
        // A spot inside the landing bounds and clear of everything already spilled - or none, after a few tries.
        val spot = (0 until 12).firstNotNullOfOrNull {
            val x = if (onTable) (random.nextFloat() * 2f - 1f) * SPILL_TABLE_HALF_WIDTH else (random.nextFloat() * 2f - 1f) * SPILL_RIM_HALF_WIDTH
            val z = if (onTable) SPILL_TABLE_NEAR + random.nextFloat() * (SPILL_TABLE_FAR - SPILL_TABLE_NEAR) else CHEST_DEPTH / 2f + 0.4f
            Offset(x, z).takeIf { clearOf(x, z) }
        } ?: return null
        taken += spot
        return Spill(
            isGem = isGem,
            seed = random.nextInt(),
            fromX = (random.nextFloat() * 2f - 1f) * 10f,
            fromZ = -2f + random.nextFloat() * 6f,
            x = spot.x,
            y = if (onTable) (if (isGem) 0.6f else 0.2f) else BODY_HEIGHT + 0.3f,
            z = spot.y,
            onTable = onTable,
            squash = if (onTable) 0.38f + random.nextFloat() * 0.12f else 0.7f + random.nextFloat() * 0.1f,
            angle = random.nextFloat() * 50f - 25f,
            start = 0.06f + random.nextFloat() * 0.2f,
            arc = if (onTable) 7f + random.nextFloat() * 5f else 4f + random.nextFloat() * 2f,
            spin = 18f + random.nextFloat() * 16f,
        )
    }
    val coins = List(2 + random.nextInt(3)) { piece(isGem = false) }
    val gems = List(1 + random.nextInt(2)) { piece(isGem = true) }
    return (coins + gems).filterNotNull()
}

// How long each piece takes to fly out, as a share of the flourish.
private const val SPILL_FLIGHT = 0.45f

/**
 * Where a spilling piece is at [t] through the flourish: thrown up out of the top of the pile, arcing
 * over the front rim and down to where it rests - no bounce - or already there once it's landed (and
 * whenever there's no flourish, as with reduced motion). Null before it's thrown.
 */
private fun spillPosition(spill: Spill, t: Float): Triple<Float, Float, Float>? {
    val p = ((t - spill.start) / SPILL_FLIGHT).coerceAtMost(1f)
    if (p < 0f) return null
    val fromY = BODY_HEIGHT + 6f
    val arc = spill.arc * 4f * p * (1f - p)
    return Triple(spill.fromX + (spill.x - spill.fromX) * p, fromY + (spill.y - fromY) * p + arc, spill.fromZ + (spill.z - spill.fromZ) * p)
}

/** What's spilled out this time, flying out with the flourish and then lying where it landed. */
private fun CupDrawScope.drawSpill(spills: List<Spill>, palette: ChestPalette, flourish: Float) {
    // Furthest first, so nearer pieces land over them.
    for (spill in spills.sortedBy { towardViewer(it.x, it.z) }) {
        val (x, y, z) = spillPosition(spill, flourish) ?: continue
        val landed = flourish >= spill.start + SPILL_FLIGHT
        if (spill.isGem) {
            drawGem(point(x, y, z), gx(1.8f), GemsByValue[3], seed = spill.seed)
            continue
        }
        // Tumbling as it flies: edge-on twice a turn, then lying as it lands.
        val squash = if (landed) spill.squash else max(0.15f, abs(cos(flourish * spill.spin + spill.x)))
        drawCoin(point(x, y, z), gx(if (spill.onTable) 2.1f else 2.2f), squash, spill.angle, 0.8f, palette.coinGold, grounded = landed && spill.onTable)
    }
}

// The flourish's pieces, fixed so it's the same burst every time: sparkles as (angle in degrees from
// straight up, how far they fly, size), and coins as (sideways speed, upward speed, spin speed).
private val FlourishSparkles = listOf(
    Triple(-70f, 34f, 3.2f), Triple(-45f, 40f, 2.4f), Triple(-20f, 30f, 3.6f), Triple(0f, 42f, 2.8f), Triple(18f, 32f, 3.4f),
    Triple(40f, 38f, 2.6f), Triple(65f, 30f, 3f), Triple(-32f, 22f, 2f), Triple(30f, 24f, 2.2f), Triple(8f, 20f, 1.8f),
)
private val FlourishCoins = listOf(Triple(-26f, 42f, 11f), Triple(-9f, 52f, 14f), Triple(12f, 48f, 9f), Triple(28f, 38f, 12f))
private const val FLOURISH_RAYS = 12
private const val COIN_LAUNCH = 0.08f
private val FlourishGold = Color(0xFFFFD54F)
private val FlourishLight = Color(0xFFFFF4C2)

/** How bright the flourish is at [t]: up in a flash, then fading out. */
private fun flourishStrength(t: Float): Float = min(1f, t * 8f) * (1f - t * t)

/** The part of the burst behind the chest's front: a flash of light and a ring of turning rays. */
private fun CupDrawScope.drawFlourishBehind(centre: Offset, t: Float) {
    val strength = flourishStrength(t)
    val glowRadius = gx(14f + 34f * min(1f, t * 2.5f))
    drawCircle(
        Brush.radialGradient(listOf(FlourishLight.copy(alpha = strength), FlourishGold.copy(alpha = 0.75f * strength), Color.Transparent), centre, glowRadius),
        radius = glowRadius,
        center = centre,
    )
    val length = gx(22f + 36f * (1f - (1f - t) * (1f - t)))
    val turn = t * 40f * PI.toFloat() / 180f
    val halfWidth = 6f * PI.toFloat() / 180f
    val rays = Path()
    for (i in 0 until FLOURISH_RAYS) {
        val a = turn + i * 2f * PI.toFloat() / FLOURISH_RAYS
        rays.moveTo(centre.x, centre.y)
        rays.lineTo(centre.x + length * cos(a - halfWidth), centre.y + length * sin(a - halfWidth))
        rays.lineTo(centre.x + length * cos(a + halfWidth), centre.y + length * sin(a + halfWidth))
        rays.close()
    }
    drawPath(rays, Brush.radialGradient(listOf(FlourishLight.copy(alpha = strength), FlourishGold.copy(alpha = 0.7f * strength), Color.Transparent), centre, length))
}

/** The part of the burst in front: sparkles flying out and twinkling, and coins flung up and falling back. */
private fun CupDrawScope.drawFlourishInFront(centre: Offset, t: Float) {
    val fade = if (t < 0.7f) 1f else (1f - t) / 0.3f
    val out = 1f - (1f - t) * (1f - t) * (1f - t)
    FlourishSparkles.forEachIndexed { i, (angle, distance, sparkleSize) ->
        val a = (angle - 90f) * PI.toFloat() / 180f
        val at = centre + Offset(gx(distance * out * cos(a)), gy(distance * out * sin(a) + 10f * t * t))
        val twinkle = 0.6f + 0.4f * sin(t * 30f + i * 1.7f)
        drawSparkle(at, gx(sparkleSize * twinkle * (1f - 0.5f * t)), fade)
    }
    // The coins fly once the lid is up, not through it.
    if (t < COIN_LAUNCH) return
    for ((vx, vy, spinSpeed) in FlourishCoins) {
        val time = (t - COIN_LAUNCH) * 1.15f
        val at = centre + Offset(gx(vx * time), gy(-vy * time + 70f * time * time))
        // Spinning as it flies: seen edge-on twice a turn.
        val squash = max(0.18f, abs(cos(spinSpeed * t)))
        drawCoin(at, gx(2.6f), squash, 90f, 0.9f, FlourishCoinGold, alpha = fade)
    }
}

// The flung coins' gold, the same whatever chest they come from.
private val FlourishCoinGold = Gold(light = Color(0xFFFFE9A3), mid = Color(0xFFE0B03C), dark = Color(0xFF9C7219))

/** A four-pointed sparkle of [radius] at [centre]. */
private fun CupDrawScope.drawSparkle(centre: Offset, radius: Float, alpha: Float) {
    val waist = radius * 0.28f
    val star = Path().apply {
        moveTo(centre.x, centre.y - radius)
        lineTo(centre.x + waist, centre.y - waist)
        lineTo(centre.x + radius, centre.y)
        lineTo(centre.x + waist, centre.y + waist)
        lineTo(centre.x, centre.y + radius)
        lineTo(centre.x - waist, centre.y + waist)
        lineTo(centre.x - radius, centre.y)
        lineTo(centre.x - waist, centre.y - waist)
        close()
    }
    drawPath(star, FlourishLight.copy(alpha = alpha))
    drawCircle(Color.White.copy(alpha = alpha), radius = waist, center = centre)
}
