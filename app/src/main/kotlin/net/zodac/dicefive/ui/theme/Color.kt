package net.zodac.dicefive.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------------------------
// Material 3 colour roles.
//
// These are tonal-palette values, not hand-picked hexes: each palette holds one hue at a fixed
// chroma across the standard M3 tones (0-100, where the number is CIELAB lightness), and every
// role below is one tone from one palette, following M3's own role->tone mapping. That's what
// makes the contrast pairs (primary/onPrimary, surface/onSurface, ...) reliable rather than
// eyeballed, and it's what any Material Theme Builder export would give you.
//
// Generated from three brand seeds:
//   primary   #FFC14D - the gold the game board already uses for "this is the thing to press"
//   secondary #1E4B86 - the felt blue of the table
//   tertiary  #1B5E20 - DiceFive's original brand green
// Neutrals are the same blue held at very low chroma, so greys read slightly cool rather than
// dead, which is what keeps the app feeling like one piece with the board.
//
// To re-generate after a seed change, see the tonal-palette maths in M3's spec (or Material
// Theme Builder) - do not nudge individual values by hand, or the contrast guarantees go with it.
// ---------------------------------------------------------------------------------------------

val LightPrimary = Color(0xFF7C5800)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFFFDEAD)
val LightOnPrimaryContainer = Color(0xFF261A00)
val LightSecondary = Color(0xFF4D5E85)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFD9E2FF)
val LightOnSecondaryContainer = Color(0xFF001C3B)
val LightTertiary = Color(0xFF2A6B2C)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFB0F3AA)
val LightOnTertiaryContainer = Color(0xFF062100)
val LightBackground = Color(0xFFF8F9FF)
val LightOnBackground = Color(0xFF191B22)
val LightSurface = Color(0xFFF8F9FF)
val LightOnSurface = Color(0xFF191B22)
val LightSurfaceVariant = Color(0xFFDDE2F5)
val LightOnSurfaceVariant = Color(0xFF414656)
val LightSurfaceDim = Color(0xFFD7DAE3)
val LightSurfaceBright = Color(0xFFF8F9FF)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF1F3FD)
val LightSurfaceContainer = Color(0xFFEBEEF7)
val LightSurfaceContainerHigh = Color(0xFFE5E8F1)
val LightSurfaceContainerHighest = Color(0xFFE0E2EC)
val LightOutline = Color(0xFF717788)
val LightOutlineVariant = Color(0xFFC1C6D9)
val LightInverseSurface = Color(0xFF2E3037)
val LightInverseOnSurface = Color(0xFFEEF0FA)
val LightInversePrimary = Color(0xFFF9BC48)

val DarkPrimary = Color(0xFFF9BC48)
val DarkOnPrimary = Color(0xFF412D00)
val DarkPrimaryContainer = Color(0xFF5E4200)
val DarkOnPrimaryContainer = Color(0xFFFFDEAD)
val DarkSecondary = Color(0xFFB6C6F2)
val DarkOnSecondary = Color(0xFF1B3053)
val DarkSecondaryContainer = Color(0xFF34466B)
val DarkOnSecondaryContainer = Color(0xFFD9E2FF)
val DarkTertiary = Color(0xFF94D78F)
val DarkOnTertiary = Color(0xFF003908)
val DarkTertiaryContainer = Color(0xFF0A5315)
val DarkOnTertiaryContainer = Color(0xFFB0F3AA)
val DarkBackground = Color(0xFF11131A)
val DarkOnBackground = Color(0xFFE0E2EC)
val DarkSurface = Color(0xFF11131A)
val DarkOnSurface = Color(0xFFE0E2EC)
val DarkSurfaceVariant = Color(0xFF414656)
val DarkOnSurfaceVariant = Color(0xFFC1C6D9)
val DarkSurfaceDim = Color(0xFF11131A)
val DarkSurfaceBright = Color(0xFF373940)
val DarkSurfaceContainerLowest = Color(0xFF0B0E16)
val DarkSurfaceContainerLow = Color(0xFF191B22)
val DarkSurfaceContainer = Color(0xFF1D1F26)
val DarkSurfaceContainerHigh = Color(0xFF282A31)
val DarkSurfaceContainerHighest = Color(0xFF32353C)
val DarkOutline = Color(0xFF8B90A2)
val DarkOutlineVariant = Color(0xFF414656)
val DarkInverseSurface = Color(0xFFE0E2EC)
val DarkInverseOnSurface = Color(0xFF2E3037)
val DarkInversePrimary = Color(0xFF7C5800)

// M3's baseline error palette, used as-is: there's no reason to brand "something went wrong".
val LightError = Color(0xFFB3261E)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFF9DEDC)
val LightOnErrorContainer = Color(0xFF410E0B)
val DarkError = Color(0xFFF2B8B5)
val DarkOnError = Color(0xFF601410)
val DarkErrorContainer = Color(0xFF8C1D18)
val DarkOnErrorContainer = Color(0xFFF9DEDC)

// Game-table palette: the tavern felt/parchment look of the in-game board, kept separate from
// the Material roles above. Referenced by the default GameVisualTheme implementations in
// ui.game.style, which is the layer to swap out for alternate dice/cup/background art. These are
// deliberately NOT colour roles - the board is a rendered object, not app chrome, and it looks
// the same whichever theme the app is in.
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
// Lighter than both the socket's own fill and TrayBlueTop/Bottom (the mat it sits on), so the rim
// reads as a crisp highlight against the mat rather than just a slightly-different shade of the
// same blue - matching how CategoryTile's border pops against its felt background.
val SlotSocketBorder = Color(0xFF6FA3E0)

val CupBodyTop = Color(0xFF35302B)
val CupBodyBottom = Color(0xFF14100D)
val CupRimGold = Color(0xFFC79A4B)
val CupShadow = Color(0xFF06101F)

// Fire theme: a red-and-orange skin for the die, dice cup and table mat - see FireDiceStyle,
// FireDiceCupStyle and FireTableBackground. Same "not a colour role" rule as the rest of this block.
val FireDiceTop = Color(0xFFFF6B4A)
val FireDiceBottom = Color(0xFFA6180C)
val FireDicePipColor = Color(0xFFFFB347)

val FireCupBodyTop = Color(0xFFB3261B)
val FireCupBodyBottom = Color(0xFF4A0D08)
val FireCupRim = Color(0xFF2B0705)
val FireCupShadow = Color(0xFF1A0503)

val FlameOrange = Color(0xFFFF8A1E)
val FlameOrangeLight = Color(0xFFFFC24D)

val FireBackgroundTop = Color(0xFF7A130D)
val FireBackgroundBottom = Color(0xFF3D0805)
val FireTrayTop = Color(0xFFB2231A)
val FireTrayBottom = Color(0xFF6E120A)
val FireWaveBack = Color(0xFFD2410F)

/** Cycled by player-tab index; extend if more than 4 players are ever supported. */
val PlayerColors = listOf(
    Color(0xFF4FD6E8),
    Color(0xFF5CE38F),
    Color(0xFFA07BF0),
    Color(0xFFF2A93B),
)

// Leaderboard podium accents for 2nd/3rd place - a fixed silver/bronze pairing, same "not a
// colour role" reasoning as the rest of this file: M3 has no role for "silver" or "bronze", and
// there's exactly one meaning for each, not a theme-able choice. 1st place reuses `primary`
// itself (see ScoresScreen) rather than adding a third fixed value here, since that's already
// the brand's gold. Each has a light/dark pair, like Primary does, so the accent keeps enough
// contrast against the page background in both themes rather than being one fixed mid-tone that
// only really works in one of them.
val LightSilver = Color(0xFF5B6472)
val DarkSilver = Color(0xFFC4CCD9)
val LightBronze = Color(0xFF8B4A1F)
val DarkBronze = Color(0xFFE0965A)
