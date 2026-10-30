// Compose's desktop target declares Density's font-scale members (fontScale, TextUnit.toDp, Dp.toSp) on
// FontScalingLinear, which is @RestrictTo(LIBRARY_GROUP), so lint flags every call made through the public Density
// there. Shared code reaches them only through this file.
@file:Suppress("RestrictedApi")

package net.zodac.dicefive.ui.common

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/** The system's font scale: [Density.fontScale]. */
val Density.textScale: Float
    get() = fontScale

/** [size] in dp at this density and font scale. */
fun Density.spToDp(size: TextUnit): Dp = size.toDp()

/** [size] in sp at this density and font scale. */
fun Density.dpToSp(size: Dp): TextUnit = size.toSp()
