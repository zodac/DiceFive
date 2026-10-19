package net.zodac.dicefive.ui.game

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.game.TutorialStep
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.ui.common.LocalReduceMotion
import net.zodac.dicefive.ui.theme.GoldAccent

/**
 * The guided game's [step], for the board's pieces to find: which one the player is to use next (outlined in gold, the
 * others not taking a tap), [onTotalsOpened] for the totals button to report it was opened, and whether the player's
 * cup is [rolling]. Provided by the game
 * screen in the tutorial only - null in every real game, where nothing reads more than that.
 */
internal class TutorialGuide(val step: TutorialStep?, val pulse: () -> Float, val onTotalsOpened: () -> Unit, val rolling: Boolean = false) {
    /** Whether [category]'s box is the one to score in. */
    fun scores(category: ScoreCategory): Boolean = (step as? TutorialStep.Score)?.category == category

    /** Whether the die at [index] is one to hold now. */
    fun holds(index: Int): Boolean = (step as? TutorialStep.Hold)?.dice?.contains(index) == true

    val rolls: Boolean
        get() = step is TutorialStep.Roll

    val opensTotals: Boolean
        get() = step is TutorialStep.OpenTotals

    val undoes: Boolean
        get() = step is TutorialStep.Undo
}

internal val LocalTutorialGuide = staticCompositionLocalOf<TutorialGuide?> { null }

/** The outline's strength, from [PULSE_LOW] to full and back - steady at full under reduced motion. */
@Composable
internal fun rememberTutorialPulse(): () -> Float {
    if (LocalReduceMotion.current) return { 1f }
    val alpha = rememberInfiniteTransition(label = "tutorialPulse") // i18n: not translated - an animation label, not shown
        .animateFloat(PULSE_LOW, 1f, infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse), label = "tutorialPulseAlpha") // i18n: not translated - an animation label, not shown
    return { alpha.value }
}

/**
 * A gold outline round this, at [pulse]'s strength - read as it's drawn, so the pulse redraws without recomposing. It's
 * drawn just outside the bounds, clear of anything at their edge (the cup's roll count).
 */
internal fun Modifier.tutorialOutline(pulse: () -> Float, cornerRadius: Dp = OUTLINE_CORNER): Modifier = drawWithContent {
    drawContent()
    val stroke = OUTLINE_WIDTH.toPx()
    val outset = OUTLINE_GAP.toPx() + stroke / 2
    drawRoundRect(
        color = GoldAccent.copy(alpha = pulse()),
        topLeft = Offset(-outset, -outset),
        size = Size(size.width + outset * 2, size.height + outset * 2),
        cornerRadius = CornerRadius(cornerRadius.toPx() + outset),
        style = Stroke(width = stroke),
    )
}

private const val PULSE_LOW = 0.35f
private const val PULSE_MILLIS = 700
private val OUTLINE_WIDTH = 3.dp
private val OUTLINE_GAP = 2.dp
private val OUTLINE_CORNER = 12.dp
