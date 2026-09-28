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

val Primary = Color(0xFFF9BC48)
val OnPrimary = Color(0xFF412D00)
val PrimaryContainer = Color(0xFF5E4200)
val OnPrimaryContainer = Color(0xFFFFDEAD)
val Secondary = Color(0xFFB6C6F2)
val OnSecondary = Color(0xFF1B3053)
val SecondaryContainer = Color(0xFF34466B)
val OnSecondaryContainer = Color(0xFFD9E2FF)
val Tertiary = Color(0xFF94D78F)
val OnTertiary = Color(0xFF003908)
val TertiaryContainer = Color(0xFF0A5315)
val OnTertiaryContainer = Color(0xFFB0F3AA)
val Background = Color(0xFF11131A)
val OnBackground = Color(0xFFE0E2EC)
val Surface = Color(0xFF11131A)
val OnSurface = Color(0xFFE0E2EC)
val SurfaceVariant = Color(0xFF414656)
val OnSurfaceVariant = Color(0xFFC1C6D9)
val SurfaceDim = Color(0xFF11131A)
val SurfaceBright = Color(0xFF373940)
val SurfaceContainerLowest = Color(0xFF0B0E16)
val SurfaceContainerLow = Color(0xFF191B22)
val SurfaceContainer = Color(0xFF1D1F26)
val SurfaceContainerHigh = Color(0xFF282A31)
val SurfaceContainerHighest = Color(0xFF32353C)
val Outline = Color(0xFF8B90A2)
val OutlineVariant = Color(0xFF414656)
val InverseSurface = Color(0xFFE0E2EC)
val InverseOnSurface = Color(0xFF2E3037)
val InversePrimary = Color(0xFF7C5800)

// M3's baseline error palette, used as-is: there's no reason to brand "something went wrong".
val Error = Color(0xFFF2B8B5)
val OnError = Color(0xFF601410)
val ErrorContainer = Color(0xFF8C1D18)
val OnErrorContainer = Color(0xFFF9DEDC)

// Game-table palette: the tavern felt/parchment look of the in-game board, kept separate from
// the Material roles above. Referenced by the default GameVisualTheme implementations in
// ui.game.style, which is the layer to swap out for alternate dice/cup/background art. These are
// deliberately NOT colour roles - the board is a rendered object, not app chrome, and it looks
// the same whichever theme the app is in.
val FeltNavyTop = Color(0xFF1C3D66)
val FeltNavyBottom = Color(0xFF0B1E3A)
// A muted slate blue, not a saturated one: it was 0xFF3574C4..0xFF123765 until Tricolour's blue die
// (TricolourBlueDiceTop/Bottom) turned up, and the two were nearly the same colour - a blue die sat
// on the default mat like a hole in it. Still the table's navy family, just greyer and darker, so
// every die colour (ivory, fire, red/yellow/blue) now stands off it.
val TrayBlueTop = Color(0xFF3E5670)
val TrayBlueBottom = Color(0xFF16222F)

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

val CupRimGold = Color(0xFFC79A4B)
val CupShadow = Color(0xFF06101F)

// FacetedDiceCupStyle, the default cup: the green score tiles' own colours, one per visible face
// (shadowed left, lit centre, mid-tone right), so the cup reads as part of the same board, edged in
// the gold the board uses for a good pick and a held die.
val FacetedCupShadeFace = TileTealBottom
val FacetedCupLitFace = TileTealBorder
val FacetedCupMidFace = TileTealTop
val FacetedCupEdge = GoldAccent

// BarrelDiceCupStyle: brown wooden staves bound with dark iron hoops.
val BarrelWoodDark = Color(0xFF3E2412)
val BarrelWood = Color(0xFF6B4226)
val BarrelWoodLight = Color(0xFF8A5A32)
val BarrelRim = Color(0xFFA87447)
val BarrelInterior = Color(0xFF1A0F08)
val BarrelIron = Color(0xFF34343A)
val BarrelIronSheen = Color(0xFF74747C)

// Fire theme: a red-and-orange skin for the die, dice cup and table mat - see FireDiceStyle,
// FireDiceCupStyle and FireTableBackground. Same "not a colour role" rule as the rest of this block.
val FireDiceTop = Color(0xFFFF6B4A)
val FireDiceBottom = Color(0xFFA6180C)
val FireDicePipColor = Color(0xFFFFB347)

// The fire cup's faces: shadowed left, lit centre, mid-tone right - the same lighting as the
// faceted default's teal faces.
val FireCupShadeFace = Color(0xFF4A0D08)
val FireCupLitFace = Color(0xFFD9473A)
val FireCupMidFace = Color(0xFFB3261B)
val FireCupShadow = Color(0xFF1A0503)

val FlameOrange = Color(0xFFFF8A1E)

val FireBackgroundTop = Color(0xFF7A130D)
val FireBackgroundBottom = Color(0xFF3D0805)
val FireTrayTop = Color(0xFFB2231A)
val FireTrayBottom = Color(0xFF6E120A)

// Tricolour mode: the dice's own red/yellow/blue, which replace the player's dice style in that mode
// (see ColouredDie), and the colour-box tiles on the scorecard (see CategoryIcon/CategoryTile). A
// fixed meaning, not a theme - same rule as the rest of this block. "Swatch" is the flat colour a
// colour box shows; "Stripe" is a deeper shade of it for the Coloured House tile's background, dark
// enough that the tile's white/gold house glyph still reads on top of it.
val TricolourRedDiceTop = Color(0xFFEF5350)
val TricolourRedDiceBottom = Color(0xFF9A1B1B)
val TricolourRedPipColor = Color(0xFFFFF4EF)
// The yellow die's face and its own held ring have swapped shades from every other die: its face
// is drawn in GoldAccent/GoldAccentDim (the colour every OTHER die's held ring uses) and its own
// held ring uses what used to be its face colour instead - see TricolourYellowHeldRing and
// DieColourPalette.heldRing. The original same-hue pairing made a held yellow die's ring vanish
// against its own face.
val TricolourYellowDiceTop = GoldAccent
val TricolourYellowDiceBottom = GoldAccentDim
val TricolourYellowPipColor = Color(0xFF3A2A05)
val TricolourYellowHeldRing = Color(0xFFFFE66B)
val TricolourBlueDiceTop = Color(0xFF5C9CEB)
val TricolourBlueDiceBottom = Color(0xFF173E8C)
val TricolourBluePipColor = Color(0xFFF1F6FF)

val TricolourRedSwatch = Color(0xFFE53935)
val TricolourYellowSwatch = Color(0xFFFDD835)
val TricolourBlueSwatch = Color(0xFF1E88E5)

val TricolourRedStripe = Color(0xFF9E2A22)
val TricolourYellowStripe = Color(0xFF8F7011)
val TricolourBlueStripe = Color(0xFF1F4F9A)

// Luck of the Irish: while active (see net.zodac.dicefive.model.isLuckOfTheIrish), Tricolour's
// red/yellow/blue render in these instead - RED -> green, YELLOW -> white, BLUE -> orange, the
// Irish flag's colours - via DieColour.palette(irish = true). Same fields, same meaning, as the
// TricolourRed/Yellow/Blue block above; White's own top/bottom/pip follow Yellow's old "bright
// body, dark pip, dark stripe" pattern rather than Red/Blue's "medium body, light pip" one, since
// it's the one colour here actually as light as the flag colour it's named for.
val IrishGreenDiceTop = Color(0xFF4CAF50)
val IrishGreenDiceBottom = Color(0xFF1B5E20)
val IrishGreenPipColor = Color(0xFFF1FFF3)
val IrishWhiteDiceTop = Color(0xFFFFFFFF)
val IrishWhiteDiceBottom = Color(0xFFD8D8D8)
val IrishWhitePipColor = Color(0xFF262626)
val IrishOrangeDiceTop = Color(0xFFFF8A50)
val IrishOrangeDiceBottom = Color(0xFFC1440E)
val IrishOrangePipColor = Color(0xFFFFF3E8)

val IrishGreenSwatch = Color(0xFF169B62)
val IrishWhiteSwatch = Color(0xFFFFFFFF)
val IrishOrangeSwatch = Color(0xFFFF883E)

// A proportional darkening of IrishGreenSwatch/IrishOrangeSwatch (same R:G:B ratio, not a
// separately-picked hue) - a from-scratch dark shade kept drifting away from the flag's own hue
// once darkened enough to hold the Coloured House tile's glyph.
val IrishGreenStripe = Color(0xFF127C4E)
val IrishOrangeStripe = Color(0xFFAD5C2A)
// The flag's white band, full brightness - CategoryIcon's HouseIcon already drops a dark shadow
// behind the glyph for exactly this "background too light to hold it" case (see its own doc), so
// this doesn't need darkening down to a readable grey the way the other two stripes do.
val IrishWhiteStripe = Color(0xFFFFFFFF)

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
// the brand's gold. Both are light tones, picked for contrast against the app's dark page
// background the same way Primary is.
val Silver = Color(0xFFC4CCD9)
val Bronze = Color(0xFFE0965A)

// Big Fan's heart icon (Achievement.iconTintOrUnspecified) - a fixed red for the same reason as
// the podium accents above: M3 has no role for "this heart is red", and it's one meaning, not a
// theme-able choice. Not TricolourRedSwatch - that one belongs to the Tricolour game mode and
// shouldn't couple an unrelated achievement's colour to a game mode's palette.
val AchievementHeartRed = Color(0xFFE53935)

// Shaken, Not Tapped's martini icon (Achievement.iconTintOrUnspecified) - same "fixed meaning,
// not a theme-able choice" reasoning as the heart/podium accents above: a martini has a glass, a
// drink and an olive, and those are three specific colours, not one ambient tint.
val MartiniGlassSwatch = Color(0xFFD8DEE0)
val MartiniLiquidSwatch = Color(0xFFD9B23C)
val MartiniOliveSwatch = Color(0xFF6E7A2E)
val MartiniOliveHighlightSwatch = Color(0xFF93A150)
val MartiniPickSwatch = Color(0xFFC9A66B)
