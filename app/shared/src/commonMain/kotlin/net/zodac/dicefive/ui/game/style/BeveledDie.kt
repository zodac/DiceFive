package net.zodac.dicefive.ui.game.style

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.ui.theme.GoldAccent

/** How rounded a [BeveledDie]'s corners are, as a percentage of its size. */
internal const val BEVELED_DIE_CORNER_PERCENT = 22

/**
 * The rounded, soft-bevelled die shared by every [DiceStyle] so far - only the gradient and pip
 * colour change between skins. Pulled out once [FireDiceStyle] needed the exact same bevel/shadow
 * mechanics as [IvoryDiceStyle], so a third skin is just three new colours, not a new shape.
 */
@Composable
internal fun BeveledDie(
    value: Int,
    held: Boolean,
    topColor: Color,
    bottomColor: Color,
    pipColor: Color,
    modifier: Modifier,
    heldRingColor: Color = GoldAccent,
) {
    val shape = RoundedCornerShape(BEVELED_DIE_CORNER_PERCENT)
    Box(
        modifier = modifier
            .dieShadow(shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(topColor, bottomColor)))
            .then(
                if (held) {
                    Modifier.border(2.dp, heldRingColor, shape)
                } else {
                    Modifier.border(1.dp, bottomColor.copy(alpha = 0.6f), shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        PipFace(
            value = value,
            color = pipColor,
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            pipRadiusFraction = 0.11f,
        )
    }
}
