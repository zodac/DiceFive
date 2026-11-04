package net.zodac.dicefive.ui.style

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import net.zodac.dicefive.data.settings.DefaultStyleIds
import net.zodac.dicefive.ui.theme.BarrelBackgroundBottom
import net.zodac.dicefive.ui.theme.BarrelBackgroundTop
import net.zodac.dicefive.ui.theme.FeltNavyBottom
import net.zodac.dicefive.ui.theme.FeltNavyGlow
import net.zodac.dicefive.ui.theme.FeltNavyTop

private const val FELT_LIGHT_HEIGHT = 0.08f
private const val FELT_LIGHT_REACH = 0.95f
private const val FELT_MIDDLE_STOP = 0.55f

/** Default [TableBackground]: the launcher icon's navy felt behind the scorecard, lit from above its middle. */
object MidnightFeltBackground : TableBackground {
    override val id: String = DefaultStyleIds.TABLE_BACKGROUND

    override val scoreAreaBrush: Brush = object : ShaderBrush() {
        override fun createShader(size: Size): Shader = RadialGradientShader(
            center = Offset(size.width / 2, size.height * FELT_LIGHT_HEIGHT),
            radius = maxOf(size.width, size.height) * FELT_LIGHT_REACH,
            colors = listOf(FeltNavyGlow, FeltNavyTop, FeltNavyBottom),
            colorStops = listOf(0f, FELT_MIDDLE_STOP, 1f),
        )
    }
}

/** The Classic background in Brown [TableBackground]: dark stained wood behind the scorecard. */
object BarrelTableBackground : TableBackground {
    override val id: String = "classic_brown"

    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(BarrelBackgroundTop, BarrelBackgroundBottom))
}
