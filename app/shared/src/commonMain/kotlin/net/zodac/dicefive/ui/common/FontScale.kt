// A desktop target, should one be added back (MULTIPLATFORM.md), has Compose declare Density's font-scale members
// (fontScale, TextUnit.toDp, Dp.toSp) on FontScalingLinear, which is @RestrictTo(LIBRARY_GROUP), so lint flags every call
// made through the public Density there. Shared code reaches them only through this file, so that needs no other change.
@file:Suppress("RestrictedApi")

package net.zodac.dicefive.ui.common

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** The system's font scale: [Density.fontScale]. */
val Density.textScale: Float
    get() = fontScale

/** Whether the player has chosen a font larger than the normal 100%. Every fit for a large font is gated on this, so 100% draws as if it weren't there. */
val Density.isLargeFont: Boolean
    get() = fontScale > 1f

/** The sp that draws at the size [size] (in sp) has at a 100% font scale: the floor for text shrunk to fit a large font, so it's never smaller than at 100%. */
fun Density.sizeAtNormalFont(size: TextUnit): TextUnit = (size.value / fontScale).sp

/** [size] in dp at this density and font scale. */
fun Density.spToDp(size: TextUnit): Dp = size.toDp()

/** [size] in sp at this density and font scale. */
fun Density.dpToSp(size: Dp): TextUnit = size.toSp()
