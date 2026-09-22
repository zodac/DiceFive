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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import net.zodac.dicefive.ui.theme.DicePipColor
import net.zodac.dicefive.ui.theme.GoldAccent
import net.zodac.dicefive.ui.theme.IvoryDiceBottom
import net.zodac.dicefive.ui.theme.IvoryDiceTop

/** Default [DiceStyle]: a rounded ivory die with a soft bevel, matching the reference art. */
object IvoryDiceStyle : DiceStyle {
    override val id: String = "ivory"

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) {
        val shape = RoundedCornerShape(22)
        Box(
            modifier = modifier
                .shadow(elevation = 4.dp, shape = shape, clip = false)
                .clip(shape)
                .background(Brush.linearGradient(listOf(IvoryDiceTop, IvoryDiceBottom)))
                .then(
                    if (held) {
                        Modifier.border(2.dp, GoldAccent, shape)
                    } else {
                        Modifier.border(1.dp, IvoryDiceBottom.copy(alpha = 0.6f), shape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            PipFace(
                value = value,
                color = DicePipColor,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                pipRadiusFraction = 0.11f,
            )
        }
    }
}
