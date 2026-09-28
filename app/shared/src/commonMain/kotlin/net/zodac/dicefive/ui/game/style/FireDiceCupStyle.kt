package net.zodac.dicefive.ui.game.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.zodac.dicefive.ui.theme.FireCupLitFace
import net.zodac.dicefive.ui.theme.FireCupMidFace
import net.zodac.dicefive.ui.theme.FireCupShadeFace
import net.zodac.dicefive.ui.theme.FireCupShadow
import net.zodac.dicefive.ui.theme.FlameOrange

/** A "fire" [DiceCupStyle]: [FacetedDiceCupStyle]'s shape in reds with orange edges. */
object FireDiceCupStyle : DiceCupStyle {
    override val id: String = "fire"

    @Composable
    override fun Cup(rolling: Boolean, tilted: Boolean, modifier: Modifier) {
        CupCanvas(rolling, tilted, modifier) {
            drawFacetedCup(
                shadeFace = FireCupShadeFace,
                litFace = FireCupLitFace,
                midFace = FireCupMidFace,
                edge = FlameOrange,
                mouth = FireCupShadow,
                shadow = FireCupShadow,
            )
        }
    }
}
