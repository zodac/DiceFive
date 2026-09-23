package net.zodac.dicefive.ui.game.style

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import net.zodac.dicefive.ui.theme.FireBackgroundBottom
import net.zodac.dicefive.ui.theme.FireBackgroundTop
import net.zodac.dicefive.ui.theme.FireTrayBottom
import net.zodac.dicefive.ui.theme.FireTrayTop
import net.zodac.dicefive.ui.theme.FireWaveBack
import net.zodac.dicefive.ui.theme.FlameOrange

private const val WAVE_SAMPLE_STEPS = 96

/**
 * A "fire" [TableBackground]: a red felt background and a red dice-tray mat trimmed with a band of
 * flame tongues rolling across its bottom third - the same shape language as a breaking water wave,
 * rendered in flame instead of surf.
 */
object FireTableBackground : TableBackground {
    override val id: String = "fire"

    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(FireBackgroundTop, FireBackgroundBottom))

    override val diceTrayBrush: Brush = Brush.verticalGradient(listOf(FireTrayTop, FireTrayBottom))

    @Composable
    override fun DiceTrayDecoration(modifier: Modifier) {
        Canvas(modifier = modifier) {
            // Both layers' resting base sits in the bottom third; the tongues themselves spike
            // well above that line, the same way real flame licks reach past the bed of coals
            // they rise from. Offsetting the front layer's tongues a half-cycle from the back
            // layer's is what makes this read as two overlapping bands rather than one flat one.
            drawFlameTongueBand(baselineFraction = 0.82f, amplitudeFraction = 0.30f, tongueCount = 5, sharpness = 3f, phase = 0f, color = FireWaveBack)
            drawFlameTongueBand(
                baselineFraction = 0.88f,
                amplitudeFraction = 0.22f,
                tongueCount = 5,
                sharpness = 3.5f,
                phase = 0.5f,
                color = FlameOrange,
            )
        }
    }
}

/**
 * One pointed tongue per cycle: zero lift at both edges of its span (so neighbouring tongues meet
 * at a smooth, flat-bottomed valley, not a hard seam), sharpening toward the tip as [sharpness]
 * rises - a plain sine hump reads as a soft dune, not flame.
 */
private fun flameTongueLift(u: Float, sharpness: Float): Float {
    val s = sin(u * PI.toFloat())
    return if (s > 0f) s.pow(sharpness) else 0f
}

/**
 * A single continuous band of flame tongues, filled down to the draw area's bottom edge - still one
 * connected shape, never gaps down to the background between tongues. [FireTableBackground] layers
 * two of these so the mat's fire trim has depth instead of reading as a flat stripe with a wavy edge.
 */
private fun DrawScope.drawFlameTongueBand(
    baselineFraction: Float,
    amplitudeFraction: Float,
    tongueCount: Int,
    sharpness: Float,
    phase: Float,
    color: Color,
) {
    val w = size.width
    val h = size.height
    val baseline = h * baselineFraction
    val amplitude = h * amplitudeFraction

    val path = Path().apply {
        moveTo(0f, h)
        for (step in 0..WAVE_SAMPLE_STEPS) {
            val t = step / WAVE_SAMPLE_STEPS.toFloat()
            val u = (t * tongueCount + phase) % 1f
            val y = baseline - flameTongueLift(u, sharpness) * amplitude
            lineTo(t * w, y)
        }
        lineTo(w, h)
        close()
    }
    drawPath(path, color = color)
}
