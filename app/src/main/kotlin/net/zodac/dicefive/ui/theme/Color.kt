package net.zodac.dicefive.ui.theme

import androidx.compose.ui.graphics.Color

val DiceFiveGreen = Color(0xFF1B5E20)
val DiceFiveGreenLight = Color(0xFF4C8C4A)
val DiceFiveIvory = Color(0xFFFFFBF0)
val DiceFiveCharcoal = Color(0xFF1C1B1F)

// Game-table palette: the tavern felt/parchment look of the in-game board, kept separate from
// the app chrome colors above. Referenced by the default GameVisualTheme implementations in
// ui.game.style, which is the layer to swap out for alternate dice/cup/background art.
val FeltNavyTop = Color(0xFF1C3D66)
val FeltNavyBottom = Color(0xFF0B1E3A)
val TrayBlueTop = Color(0xFF3574C4)
val TrayBlueBottom = Color(0xFF123765)

val TileTealTop = Color(0xFF234B47)
val TileTealBottom = Color(0xFF122E2B)
val TileTealBorder = Color(0xFF3C6C66)
val TileIconColor = Color(0xFFE7F3EF)

val TileHighlightTop = Color(0xFF4A3B12)
val TileHighlightBottom = Color(0xFF241C08)

// A visibly "used up" look for a category that already has a committed score - flat and desaturated
// so it reads as spent at a glance, distinct from both the teal "open" tile and the gold "good pick" glow.
val TileScoredTop = Color(0xFF2E2E2E)
val TileScoredBottom = Color(0xFF181818)
val TileScoredBorder = Color(0xFF3F3F3F)

val GoldAccent = Color(0xFFFFCC55)
val GoldAccentDim = Color(0xFFB8862A)

val IvoryDiceTop = Color(0xFFFFFCF3)
val IvoryDiceBottom = Color(0xFFD9C9A3)
val DicePipColor = Color(0xFF2B2118)

val SlotSocketTop = Color(0xFF0D2549)
val SlotSocketBottom = Color(0xFF081833)
val SlotSocketBorder = Color(0xFF2A4A78)

val CupBodyTop = Color(0xFF35302B)
val CupBodyBottom = Color(0xFF14100D)
val CupRimGold = Color(0xFFC79A4B)
val CupShadow = Color(0xFF06101F)

/** Cycled by player-tab index; extend if more than 4 players are ever supported. */
val PlayerColors = listOf(
    Color(0xFF4FD6E8),
    Color(0xFF5CE38F),
    Color(0xFFA07BF0),
    Color(0xFFF2A93B),
)
