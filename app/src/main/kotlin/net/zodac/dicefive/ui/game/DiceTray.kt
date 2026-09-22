package net.zodac.dicefive.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.ui.game.style.DiceStyle
import net.zodac.dicefive.ui.game.style.LocalGameVisualTheme
import net.zodac.dicefive.ui.theme.SlotSocketBorder
import net.zodac.dicefive.ui.theme.SlotSocketBottom
import net.zodac.dicefive.ui.theme.SlotSocketTop

private data class ScatterSlot(val xFraction: Float, val yFraction: Float, val rotationDegrees: Float)

/** Fixed, hand-tuned "just tumbled out of the cup" positions, keyed by die index so a die never
 * jumps to a different spot just because a neighbour was held or released. */
private val SCATTER_SLOTS = listOf(
    ScatterSlot(0.0f, 0.6f, -18f),
    ScatterSlot(0.28f, 0.0f, 11f),
    ScatterSlot(0.55f, 0.5f, -9f),
    ScatterSlot(0.78f, 0.05f, 17f),
    ScatterSlot(1.0f, 0.58f, -23f),
)

private val SCATTERED_DIE_SIZE = 44.dp
private val MAX_SLOT_DIE_SIZE = 52.dp
private const val SCRAMBLE_INTERVAL_MILLIS = 90L

/**
 * The dice area: five fixed "held" slots up top (each always represents the same die index), and
 * - once the dice have been rolled this turn, or while [rolling] is shaking the cup - any not-held
 * dice scattered below as if just poured from the cup. While [rolling], scattered (unheld) dice
 * flicker through random faces to read as "still tumbling" before settling on the real roll.
 * Tapping a held die in its slot releases it back into the scatter; tapping a scattered die holds
 * it. Uses [LocalGameVisualTheme] for both the die art and the background.
 */
@Composable
fun DiceTray(
    dice: List<Die>,
    enabled: Boolean,
    showDice: Boolean,
    rolling: Boolean,
    onToggleHold: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visualTheme = LocalGameVisualTheme.current

    var scrambleTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(rolling) {
        while (rolling) {
            delay(SCRAMBLE_INTERVAL_MILLIS)
            scrambleTick++
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(visualTheme.background.diceTrayBrush)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            dice.forEachIndexed { index, die ->
                DiceSlot(
                    die = die,
                    show = showDice,
                    enabled = enabled,
                    diceStyle = visualTheme.diceStyle,
                    onClick = { onToggleHold(index) },
                    modifier = Modifier.weight(1f).aspectRatio(1f).sizeIn(maxWidth = MAX_SLOT_DIE_SIZE, maxHeight = MAX_SLOT_DIE_SIZE),
                )
            }
        }

        if (showDice) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(96.dp)) {
                val freeWidth = maxWidth - SCATTERED_DIE_SIZE
                val freeHeight = maxHeight - SCATTERED_DIE_SIZE
                dice.forEachIndexed { index, die ->
                    if (!die.isHeld) {
                        val scatter = SCATTER_SLOTS[index % SCATTER_SLOTS.size]
                        // Reads scrambleTick so each tick's recomposition seeds a fresh face -
                        // deliberately not remember()'d, since a cached value wouldn't flicker.
                        val displayValue = if (rolling) Random(scrambleTick * 31 + index).nextInt(1, 7) else die.value
                        visualTheme.diceStyle.Die(
                            value = displayValue,
                            held = false,
                            modifier = Modifier
                                .size(SCATTERED_DIE_SIZE)
                                .offset(x = freeWidth * scatter.xFraction, y = freeHeight * scatter.yFraction)
                                .graphicsLayer { rotationZ = scatter.rotationDegrees }
                                .then(if (enabled) Modifier.clickable { onToggleHold(index) } else Modifier),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiceSlot(
    die: Die,
    show: Boolean,
    enabled: Boolean,
    diceStyle: DiceStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(SlotSocketTop, SlotSocketBottom)))
            .border(1.dp, SlotSocketBorder, shape),
    ) {
        if (show && die.isHeld) {
            diceStyle.Die(
                value = die.value,
                held = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(3.dp)
                    .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
            )
        }
    }
}
