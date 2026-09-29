package net.zodac.dicefive.ui.game.style

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// How far through the toss the die reaches the far wall and bounces; the rest is rolling back.
private const val WALL_AT = 0.45f

/**
 * Where a tossed die is, part-way through its toss - relative to where it will come to rest, in dp.
 *
 * [dx]/[dy] are its offset from its resting spot, [yawDegrees] how far it's spun from its resting
 * angle, and [roll] how many quarter-turns it has tumbled through, forwards up the tray and back
 * again after the bounce - see [TossedCube].
 */
class TossPose(val dx: Float, val dy: Float, val yawDegrees: Float, val roll: Float)

/**
 * One die's toss, in dp measured down from the far wall - the top of the area dice land in: thrown
 * from [startY], just past the near edge and out of sight, flying up its column into the wall, bouncing and rolling back to
 * rest at [restY] on [result]. The whole throw is [toss] from 0 to 1; each die comes in a touch later
 * than the others ([seed]), drifts a little sideways between its column's invisible walls
 * ([sideRoom] dp either way), and spins before settling.
 *
 * It tumbles about one axis as it travels, forwards up the tray and back after the bounce, through
 * [ring] - the real faces round that axis. It starts on [startFace] (the face it was showing when
 * picked up off the mat, if it was on the mat at all) and finishes on [result], so it never jumps from one face to
 * another: [finalTurns], the whole quarter-turns it ends up rolled through, is picked to make both
 * true, and the tumble is paced to its distance travelled so it comes to rest squarely on a face.
 * For a style that draws more than the face it rolled ([DiceStyle.topFace]), [restTop] is the face
 * it has to finish with on top - the one before [result] in the ring - so it lands looking exactly as
 * it then rests; that takes priority over [startFace], which is thrown in out of sight anyway.
 */
class TossPath(
    seed: Int,
    result: Int,
    startFace: Int?,
    restTop: Int? = null,
    private val startY: Float,
    private val restY: Float,
    dieSize: Float,
    sideRoom: Float,
) {
    private val random = Random(seed)
    private val delay = random.nextFloat() * 0.08f
    private val drift = (random.nextFloat() * 2f - 1f) * sideRoom
    private val driftWaves = 2f + random.nextFloat()
    private val spin = (random.nextFloat() * 2f - 1f) * 120f
    private val travel = ((startY - restY) / dieSize).coerceAtLeast(0.01f)

    val ring: List<Int>
    val finalTurns: Int

    init {
        // The ring runs result, neighbour, opposite, neighbour's opposite; the face it starts on sits
        // (finalTurns) places back round it, so pick the neighbour and the turns to put startFace there.
        // The ring's last face is the one on top at rest, one turn back from the result.
        val closest = maxOf(1, travel.roundToInt())
        val neighbours = (1..6).filter { it != result && it != 7 - result }
        val neighbour = when {
            restTop != null && restTop in neighbours -> 7 - restTop
            startFace != null && startFace in neighbours -> 7 - startFace
            else -> neighbours.random(random)
        }
        ring = listOf(result, neighbour, 7 - result, 7 - neighbour)
        // It starts on the ring's face finalTurns places back from the result - or, if the face it
        // was picked up on isn't round this ring at all, anywhere.
        val startIndex = startFace?.let { ring.indexOf(it) }?.takeIf { it >= 0 }
        finalTurns = if (startIndex == null) {
            closest
        } else {
            (1..8).filter { (it + startIndex) % 4 == 0 }.minBy { abs(it - closest) }
        }
    }

    fun pose(toss: Float): TossPose {
        val t = ((toss - delay) / (1f - delay)).coerceIn(0f, 1f)
        // Up to the wall, slowing a little as it goes; then back from the wall, slowing to a stop.
        val y = if (t < WALL_AT) {
            val w = t / WALL_AT
            startY * (1f - w).pow(1.6f)
        } else {
            val w = (t - WALL_AT) / (1f - WALL_AT)
            restY * (1f - (1f - w).pow(3))
        }
        // Bouncing between the column's walls, dying away to nothing as it settles.
        val dx = drift * sin(t * PI.toFloat() * driftWaves) * (1f - t).pow(2)
        val roll = (startY - y) / (startY - restY).coerceAtLeast(0.01f) * finalTurns
        return TossPose(dx = dx, dy = y - restY, yawDegrees = spin * (1f - t).pow(2), roll = roll)
    }
}

/**
 * A die being picked up for a roll: slid off the mat from where it rests ([restY]) to past its near
 * edge ([startY], out of sight), speeding up as it goes, as if swept into the cup. It slides rather
 * than rolls, so it still shows [value] should the throw that follows be caught starting from there.
 */
class PickUpPath(val value: Int, private val startY: Float, private val restY: Float) {
    fun pose(pickUp: Float): TossPose {
        val t = pickUp.coerceIn(0f, 1f)
        val y = restY + (startY - restY) * t * t
        return TossPose(dx = 0f, dy = y - restY, yawDegrees = 0f, roll = 0f)
    }
}

/**
 * The four faces round one of a real die's axes, in the order a roll brings them round, starting
 * from [face]: the face, a random neighbour, its opposite, and the neighbour's opposite - opposite
 * faces add up to 7.
 */
fun faceRing(face: Int, seed: Int): List<Int> {
    val neighbour = (1..6).filter { it != face && it != 7 - face }.random(Random(seed))
    return listOf(face, neighbour, 7 - face, 7 - neighbour)
}

/**
 * A die drawn as a solid cube, tumbled [roll] quarter-turns from its resting face ([finalTurns]
 * quarter-turns is resting): the face it's tipping off and the one tipping on, each drawn by [face]
 * in the die's own style, turned in 3D and pushed out to where it sits on the cube, the nearer on
 * top, each shaded as it turns away. [ring] is the faces round the axis it's rolling about - see
 * [faceRing] - so rolling forward and back again brings the same faces round, like a real die.
 */
@Composable
fun TossedCube(
    roll: Float,
    finalTurns: Int,
    ring: List<Int>,
    modifier: Modifier = Modifier,
    face: @Composable (value: Int, modifier: Modifier) -> Unit,
) {
    fun faceAt(turn: Int) = ring[(turn - finalTurns).mod(ring.size)]
    val base = floor(roll).toInt()
    val tipped = (roll - base) * 90f
    BoxWithConstraints(modifier = modifier) {
        val half = constraints.maxHeight / 2f
        // The face tipping away and the one coming up from the near side, farthest first.
        val faces = listOf(faceAt(base) to tipped, faceAt(base + 1) to tipped - 90f)
            .map { (value, degrees) -> Triple(value, degrees, cos(degrees * PI.toFloat() / 180f)) }
            .filter { (_, _, facing) -> facing > 0.02f }
            .sortedBy { (_, _, facing) -> facing }
        for ((value, degrees, facing) in faces) {
            val offset = half * sin(degrees * PI.toFloat() / 180f)
            face(
                value,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationX = degrees
                        translationY = -offset
                        cameraDistance = 12f * density
                        // Drawn off-screen so the shading below only darkens the face itself.
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(Color.Black.copy(alpha = (1f - facing) * 0.45f), blendMode = BlendMode.SrcAtop)
                    },
            )
        }
    }
}
