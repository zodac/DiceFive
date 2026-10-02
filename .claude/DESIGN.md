# DiceFive: Yahtzee game — design & phased plan

Status legend: `[ ]` not started · `[~]` in progress · `[x]` done

## Context

The repo started as a skeleton: a single `GameScreen` showing "DiceFive" and
"Rolls remaining: 3", a `GameState` with no scoring/turn logic, and no
navigation, persistence, or other screens. This document tracks the full v1
build: a menu-driven Android app implementing local (no netplay) Yahtzee for
1-4 players (human and AI), with score history, a settings screen (profile, gameplay and reset
options, plus a version + GitHub link footer), and a stub achievements screen.

Update this file's checkboxes as work lands, so the build can be resumed or
revisited across sessions without re-deriving the plan.

The interface itself is documented separately in `.claude/UI.md` — the
Material 3 colour system, the shared chrome in `ui/common/`, and the layout
decisions behind it. Read that before changing anything visual.

## Decisions (locked in)

- **Persistence**: Room DB for score history, Jetpack DataStore
  (Preferences) for settings and remembered human player names.
- **Navigation**: Navigation Compose (`NavHost`), with a nested "play" graph
  sharing one scoped `GameViewModel` across the setup and in-game screens.
- **AI difficulty**: the setup-screen selector is live (Easy/Medium/Hard per AI slot). `AiTurnPlayer`
  strategy per tier - EASY: never holds individual dice, but stops rerolling (all together) once
  any open category scores above zero, then scores the highest-value open category. MEDIUM: holds
  dice by rule of thumb (a forming straight, else the largest matching
  group) between rolls; ties in category choice toward an upper box "on pace" for the 63-point
  bonus. It also doesn't always spend every roll: a fixed set of "good enough" shapes - Full House,
  Large Straight, a Small Straight once Large Straight is no longer open, or three-plus dice on a
  4/5/6 with that upper box still open - gets banked immediately instead of gambled on a reroll.
  HARD: exhaustively evaluates all 32 hold/reroll subsets each roll via exact expected value
  (every possible outcome of the freed dice, weighted equally) through every reroll left in the
  turn (the next one only, with Tricolour's coloured dice), and both its holds and its category
  choice value a box by how far its score beats what a whole turn chasing that box averages, plus
  its share of the upper bonus (so a rare category like Full House can beat a nominally
  higher-scoring but easy-to-satisfy-later one like Chance, and three low dice are worth chasing
  for 5x) - see Phase 23.
- **Leaderboard screen**: one global leaderboard (not split by player or game
  type), sorted score-descending, paginated 50/page (originally 100; halved
  alongside a compact row style, so a page is a shorter scroll). No date
  column — date is shown via a long-press tooltip.
- **Statistics screen**: one card per distinct human player *name* (a rename starts a new "user",
  same as the leaderboard). Win/loss and the current win streak are only tracked for multiplayer
  games — a solo game has nobody to beat, so it's recorded with a null outcome that counts toward
  games played but neither wins, losses, nor breaks a streak, mirroring the existing
  `WIN_STREAK`/`GAMES_WON` achievement counters.
- **GitHub link**: shown with the app version, in a footer at the bottom of
  the Settings screen (there is no separate About screen); `https://github.com/zodac/DiceFive`.
- **Table art styles**: `ui/game/style/DiceStyle`/`DiceCupStyle`/`TableBackground` are
  independently swappable and independently persisted (`SettingsRepository.diceStyleId`/
  `diceCupStyleId`/`tableBackgroundId`, plain string ids, defaulted and resolved through each
  category's own catalog object in `ui/game/style/StyleCatalog.kt` - `DiceStyles`/`DiceCupStyles`/
  `TableBackgrounds`/`DiceMats`, each a `StyleCatalog` with a `byId` lookup falling back to that
  category's `default`). Each catalog groups its art into `StyleFamily`s - a style (shape or
  pattern) with one or more `StyleColour`s - and every colour is still its own `TableArt` with its
  own saved id, so grouping never changed what's persisted. The full list of styles and colours is
  `StyleCatalog.kt` itself; the originals are dice Classic (Ivory, Red, Oak - the original Red, id `fire`, was later removed; a saved `fire` dice pick now falls back to the default, and Classic gained Tricolour's red/yellow/blue ahead of Oak), cups Faceted (Green,
  Red) and Barrel (with the casino shaker since moved ahead of them as the default), mats Classic (Blue, Red) and Barrel, backgrounds Classic (Navy, Red, Brown). A new
  colour of an existing shape goes in that family, not a new one, and every family has 1-3 colours
  (`StyleCatalogTest` checks). The later styles are built from shared parts rather than one file
  each: `StyledDie` (`DiceArt.kt` - corner, surface finish, pip shape; `ShapedDice.kt` for the dice that
  aren't a flat rounded square, like the 3D cube and the misprint; `LcdDice.kt` for the 7-segment LCD
  dice; `D20Dice.kt` for the D20 - a real, projected icosahedron numbered 1-20 with opposite faces
  summing to 21, turned so the roll (only ever 1-6) faces you, the rest of its numbers faded back; mid-roll
  (`LocalDieTumbleMillis`, the toss's elapsed time, set by the tray - driven by the toss's clock, not
  one of its own, so its shadow can turn with it) it tumbles continuously about two axes instead, and at rest it
  gets a small per-die, per-roll twist so no two landings look alike; `D20Test` pins the numbering;
  `GooglyDice.kt` for the googly-eyed die - a plain ivory die whose every pip is a loose pupil in a
  clear socket well over twice its radius, thrown about by the die's movement: a style with a
  `DiceStyle.pupilTravel` gets a `DieMotion` per die column from the tray (handed down as
  `LocalDieMotion`, kept across holding so the eyes don't jump), which the tray tells where the die
  is on the mat whenever it's drawn there and which, only while the die or its pupils are moving,
  moves the pupils every frame - flung against the die's acceleration in its own turned frame, sliding
  down each face as it tips over mid-tumble (the tray passes the roll's tip), left
  behind by part of its spin, slowed by friction and bounced off the rim - then stops asking for
  frames; `DieMotionTest` pins the physics). On the main menu only, the logo's googly dice (and no
  other style, cup or screen) follow the phone itself: `AppLogo`'s `pupilsFollowDevice`, which the
  menu alone sets, keeps a `DieMotion` per logo die and feeds it the accelerometer's pull
  (`DieMotion.feel`, in g on screen - tilt and shake alike) while the menu is resumed. A pupil held
  against its rim by that steady pull rests there instead of bouncing, or it would jitter forever and
  never let the frames stop; with no device pull the physics is untouched. The round-cup kit
  (`RoundCups.kt`, same raised view as the rest), `PatternedDiceMat` (`PatternedMats.kt`) and the
  patterned backgrounds (`PatternedBackgrounds.kt`, drawn through
  `TableBackground.drawScoreAreaDecoration`), with `Patterns.kt` holding painters more than one of
  them uses (stars, countertop marble, gingham checks - the random ones always from a fixed seed so
  they don't shimmer). Mats of natural materials that Canvas strokes can't make convincing (the
  Marble mat, the Wood mat's Hardwood) are `TexturedDiceMat`s (`TexturedMats.kt`): a pre-rendered
  texture in `composeResources/drawable/`, cropped to fill the tray. The textures aren't photographs -
  `scripts/art/generate_*_mat.py` (numpy/scipy/pillow) generate them from a fixed seed, each script's
  docstring says how to re-export, and each is in `app/licensing/asset-sources.json` as the app's own
  work. Earlier stroke-drawn marble mats read as lightning; see the scripts for what replaced them. Natural-looking dice (Marble, Frosted) also seed by `LocalDieIndex`, the
  die's position in the tray, so no two dice - or two faces of one die - share a pattern. Their palettes
  sit beside their entries in `StyleCatalog.kt` rather than in `Color.kt` - see UI.md.
  The Maths dice (`MathsDice.kt`; White, Black, Green - a secret style, hidden until The Solution is earned) show a formula per face - x⁰, ln e², ⌊π⌋, 2², √25,
  3! - in the MathJax TeX fonts (`composeResources/font/mathjax_*.otf`, OFL, unmodified), the
  Computer Modern faces Wikipedia sets formulas in. Each run is laid out once at a fixed size and
  scaled to the die, placed from the glyph metrics copied from the font into `MathsDice.kt`
  (superscripts at TeX's 0.7 scale and 0.413em rise); the root's bar is drawn as a rule, since the
  font's √ has none. A new formula needs its glyphs' metrics added there (fontTools' `BoundsPen`).
  The Gems dice (`GemDice.kt`; Ivory, Black) cut each value's pips as a different stone - topaz,
  ruby, emerald, sapphire, amethyst, diamond - in its usual cut (oval, cushion, step-cut, round
  brilliant). They're drawn as artwork, not photographs (the maintainer's call after a realistic
  pass looked "too real"): flat facet fills in five tones, ink lines, a crisp highlight, no metal
  setting, each stone inlaid in a shadowed socket on a softly clouded face. Every stone differs
  (angle, uneven edge, which facets catch the light), seeded by `naturalPatternSeed` like Marble;
  faces and gems are painted once through `drawCachedSurface`.
  The Treasure Chest cup (`TreasureChestCup.kt`) is the one cup not drawn in `CupCanvas`: it's a
  solid in its own 3D space, turned `CHEST_YAW_DEGREES` so its right end shows, projected with the
  cups' usual `CUP_VIEW_SQUASH`. It doesn't tip - shaken, it rocks side to side four times a second,
  lifting smoothly at each end with its lid lifting in time (its own shake, in the layer; every part
  a smooth wave - the first version's jolting hops were too aggressive); poured, the lid flies open on
  a bouncing spring with a gold burst (`FLOURISH_MILLIS`; skipped under reduced motion), and it rests
  open on its hoard. Its one special rule: a chest that first appears with the roll already poured (a
  game being continued) stays shut until the next shake, which then opens it as usual - appearing open
  meant showing it before its painted treasure was ready, empty then popping full (the maintainer's
  call). The spill only shows once the lid has really opened, not when it lifts in the shake.
  The lid's arch is 90% of a half-round's height (`LID_RISE` - at full height it looked too tall once
  the chest was enlarged). The lid has thickness (lining, lip edge, inner end face) and is visibly hinged on
  the right end (hinge leaves, knuckle, and a stay once open).
  The hoard is settled, not placed (`settlePile`): the chest is full to just under the rim, a flat
  bed of coins covers that, then coins are dropped one at a time onto whatever is highest beneath
  them, tipped gently to the slope they land on and rolling downhill while they'd rest on a slope
  steeper than `REPOSE` - so every coin rests on something, and the heap takes a real heap's shape.
  Coins placed by a formula looked as if they floated, however the gaps were patched; don't go back.
  It's seeded, so the same pile every time. Its traps: a coin's tilt and its roll check must look at
  the surface just *outside* its footprint (inside, the last coin there has already raised it - the
  roll never triggered and the coins climbed into a tower), and the roll check uses where the coin
  would actually rest (the highest point under it), not the ground under its centre. A realistic
  slope across the chest's depth tops the heap out ~9 above the rim, which `DROPPED_COINS` is set to.
  Its shadow is its own footprint (the plinth's outline projected on the table, cast right and back,
  softened in a few faint layers), painted with the body - not the generic oval the round cups use.
  For frame cost the body and the settled pile (with the pearls and loose gems) are each painted once
  into an image per size; only the lid, the side hinge, the goblet, the crown, the highest six coins
  and the spill are drawn live. The pile is simulated and painted on a background thread
  (`rememberHoardArt`, started at the chest's first shake, or at once if it's already open - never
  for a chest only shown closed and still, like an unpicked Styles preview) and kept for the session - done on the frame it first showed, resuming a game with the chest open hitched badly
  (~160ms of the first frame on the desktop JVM). Until it's ready, or if painting ever fails, a plain gold fill stands in.
  The treasure (painted pile or stand-in) is only drawn once the lid has swung back past 45 degrees and is
  drawn behind it: the goblet, crown and top coins stand higher than the rim, so with the lid rattling in
  the shake or only starting to open they poked up through it. Until then the gap shows the dark inside. Each
  coin is drawn as rotated ovals (the ellipse its tilted face projects to, worked out once from two
  projected directions), not projected polygons - several times cheaper to paint. Every opening
  spills a fresh random handful (`spillHandful`): 2-4 coins and 1-2 sapphires, each thrown from its
  own spot on the pile at its own moment, arc and spin, landing over the front rim or on the table
  inside fixed bounds and clear of each other - no bounce. They vanish the moment the chest starts to
  close for the next roll. With no burst (reduced motion) they're simply at rest. Checking for gaps: colour the inside of the chest bright green
  for a render and count the green pixels.
  Seven more dice, each drawn as artwork, every costly face painted once through `drawCachedSurface`
  and seeded per face and die (`naturalPatternSeed`) so no two are alike; illustrated pieces are
  authored in a unit space and placed with `inUnit` (`ArtShapes.kt`), and a recoloured die draws
  every piece in shades of the roll's pip colour (`Shades.of`). Cake (`CakeDice.kt`; Vanilla,
  Chocolate, Pink): a frosted top with a ring of piped rosettes following the die's rounded outline
  and strawberries for pips - on Chocolate (a milk chocolate, the darker one hid the berries) each
  sits on a dollop of cream so it stands out. Meadow (`MeadowDice.kt`): a grassy field with a river shaped like the
  number, every river rising at one edge of the face and running out at another (`RIVERS` - the 4's
  branch and the 6's loop both reach an edge too). Poker (`PokerDice.kt`): a playing card per face,
  Ace for 1, the suits cycling spades, hearts, clubs, diamonds (only four suits for six faces), ranks
  in the bundled MathJax serif, pips laid out as a card's, the lower ones upside down. Obsidian
  (`ObsidianDice.kt`; Lava, Blue): polished black glass with conchoidal ripples, every pip a crusted
  vent of glowing lava with a few short cracks and spatters - each pushed a little off its pip's spot
  (`VENT_SHOVE`), its own size, stretched and ragged, as if the lava forced its own way out - and
  seeded by colour too, so Lava and Blue differ. No vent has a clean rim: its glow soaks out into
  the glass in fading rings, a few rivulets leak out of it and fade, and its crust is only a few
  dark-red flakes of cooling skin (a black crust ring read as too clean an edge, black flakes as dirt). The vents are laid out over the whole face, inset to
  the usual pip area, so a pushed one's glow isn't cut off square at the pip area's edge. Mahjong (`MahjongDice.kt`; Pinzu, Manzu,
  Sozu - one suit per colour): ivory tile faces with a jade back at the edge; Manzu's numerals and 萬
  come from the system's CJK font (like the Japanese numerals); the 1 of bamboo is a bird. Tally
  (`TallyDice.kt`): four ways of counting in fives, each written by hand on its own surface - the
  Western five-bar gate in chalk on slate, the Chinese 正 in brush on rice paper, the Latin American
  square-and-diagonal in pencil on a notebook, and the foresters' dot tally in pen on a field book.
  Garden (`GardenDice.kt`; Soil - a Linen colour was dropped): 1 butternut squash, 2 carrots, 3
  tomatoes, 4 artichokes (whole - a cut heart doesn't read at dice size - with a few broad, fleshy,
  purple-tipped scales; many small pointed ones read as a pine cone), 5 eggplants, 6 onions. A top-level `Path` must be
  `by lazy`: built when its file's class loads, it broke every plain-JVM test that touched
  `DiceStyles` (a `Path` needs the platform's graphics).
  Stone (`StoneDice.kt`; Granite, Slate, Sandstone, Limestone, Basalt, then White and Black Marble -
  the Marble dice, once a style of their own at 60 achievements, merged in, their ids unchanged so
  saved picks still resolve, and given the same carved pips - `drawCarvedPips`, shared): one class
  for the five, each stone's own
  texture painted once per face (`StoneKind`) - granite's packed crystals (fine and many; coarse
  ones read as terrazzo), slate's cleavage layers, sandstone's wavy bedding and grit, limestone's
  mottling, pores and the odd fossil, basalt's gas holes - with chiselled pips: slightly irregular
  hollows, shadowed at the top left, lit at the bottom-right lip, their floors matte (a bright floor
  read as a metal disc).
  Containers is one cup style of three shapes: the wooden Barrel (once a style of its own; its
  unlock count is the family's, so nobody loses it), the Oil Drum and the Shipping container. The
  Oil Drum (`OilDrumDiceCupStyle`, in `RoundCups.kt`) is a steel drum from a photo the maintainer
  supplied: a glossy enamelled cylinder (a hard highlight left of centre, a softer one right), two
  rolling hoops standing proud a third of the way down and up, rolled rims at its top and foot, and a
  closed lid - a dished top in its chime - with a bright metal bung and a small painted one. The
  Shipping container (`ShippingContainerCup.kt`) is long and low, so it's a 3D box on the squat grid,
  laid along its width and turned 40 degrees - mirrored, its door end on the left - so its doors show
  as well as its long side (at 24 the end was a sliver - a container's only about 2.4m across to 6m
  long): corrugated side, steel frame with cast corner blocks, doors with locking bars, handles and
  hinges, and a little rust and scuffing. The drum shakes and tips like any cup; the container shakes
  but never tips (`CupCanvas(tips = false)` - the maintainer's call). Poured, its doors swing out on their hinges on an
  underdamped spring (`DOOR_OPEN_DEGREES`), showing its dark inside and plank floor; they shut as the
  next shake starts, are already open for a continued game, and open without the bounce under reduced
  motion. The box, inside included, is painted once; only the doors are live.
  The Urn cup (`UrnCup.kt`; Terracotta, Bronze) is a turned profile (a Catmull-Rom curve through
  `UrnProfile` - a smoothstep between points left kinks at each one) in `CupCanvas`, so it shakes and
  tips like any cup; its Greek key and rays are wrapped round the body (`onBody`), foreshortening
  towards its edges.
  The Volcano cup (`VolcanoCup.kt`) draws its own `Canvas` like the chest and doesn't tip. Its foot
  never moves: shaken, only its top half rumbles from side to side (gentle smooth waves, a few puffs
  of smoke), and the eruption's jolt is the same - the cached cone is stamped in bands
  (`SWAY_BANDS`), each shifted by how far up it is (`swayWeight`, fading to nothing `SWAY_DEPTH`
  down), and everything live on it (crater, lava, burst) takes the same sway, so there's no seam.
  Poured, it erupts - a flash, a tapering jet of lava, glowing bombs with streaks arcing over and
  falling back below the rim, a plume of ash - while lava wells over the front of the rim and runs
  down the slopes as ribbons that widen as they go and end in rounded tongues, stopping short of the
  foot. It rests glowing until the next shake cools the lava away (`COOL_MILLIS`). A volcano
  composed already poured (a continued game) shows its lava at rest, no eruption. The cone is a
  mountain, not a lathe: its radius swells and hollows with angle and height (`radiusAt`), its rim is
  ragged, its rock broken up by patches, a few soft ledges, cracks, scree and ash on its upper slopes -
  but no channels down it (they read as lava's paths before there was any lava) - and a ring of rocks
  of every size is heaped round its foot. All of that is painted once; only the crater, the five
  flows and the burst are live.
  The Picnic Basket cup (`PicnicBasketCup.kt`) is a turned 3D box like the chest (both drawn 20%
  larger than their grid, about their footprint's middle - `BASKET_SCALE`, `CHEST_SCALE` - their
  cached images painted at that scale and stamped with it undone, `unscaledAbout`), its top split
  across the middle into two lids, each hinged along its own short end (leather straps wrapping
  from lid onto wall, visible on the right end). Shaken, it rocks with its lids rattling; poured,
  the lids lift at the split and swing out in opposite directions on an underdamped spring, resting
  at `FLAP_OPEN_DEGREES` (105 - at 122 the left lid's lining turned square to the viewer and looked
  bigger than the closed lid, though its edges project to the same lengths), and 1 to 3 apples
  tumble out onto the table (a fresh random handful each time, only while open). The handle is an
  upright U on leather brackets standing its legs out past the lids' edges, so the lids open and
  shut under it. Inside, a picnic packed on gingham (a water bottle, grapes, an orange, a sandwich,
  cheese, apples, a baguette), drawn back to front, filling it to the rim but low enough for the
  lids to shut over it; a corner of the cloth always hangs over the front wall. Unlike the chest, a
  basket composed already poured appears open at rest - its contents are drawn live and cheaply, so
  there's nothing to wait for. A lid is only drawn behind the contents once it's swung past upright;
  a lid rattling low in the shake is over them. The woven body and shadow are painted once.
  The Styles screen (`ui/styles/`) is the picker: one tile per family, showing the picked colour
  (or the family's first), colour dots along the bottom when it has more than one, and a long
  press popping up a scrollable row of previews, one per colour (no colour names on screen - the
  name belongs to the style; `StyleColour.name` is only read out by screen readers). Picking a cup
  plays its whole roll on its tile (`CupPickShake`): shaken for `CUP_SHAKE_MILLIS`, tipped over as if
  pouring (a chest throws its lid open), left lying for 1.5s, then stood back up; not under reduced
  motion. Each tile's cup is keyed on its style id, so a tile switching colours starts the new one
  fresh - without that, picking the Sunflower grew it in from the bare pot's plant, and the Rabbit hat
  kept the plain hat's hidden rabbit (only its ears showing). `GameScreen` reads the ids and builds the active
  `GameVisualTheme` from them on every recomposition. Shipped skins: Ivory/Midnight Felt/Blue Felt
  (the defaults - the default cup is now the casino shaker, below) and a second, fully independent "fire" skin for the cup, background and mat (the dice had one too, since removed) - the faceted cup in reds with orange edges (`FireDiceCupStyle`), a red
  felt background (`FireTableBackground`) and a plain red tray (`FireDiceMat`). The cup and mat
  used to carry painted flames (a flame up the cup, a band of flame tongues along the tray's
  bottom); both were removed at the maintainer's request. A third, "barrel" skin: honey-oak dice
  with off-white pips (`BarrelDiceStyle`), the wooden barrel cup (`BarrelDiceCupStyle`), a tray of
  brown planks with seams drawn across it (`BarrelDiceMat`) and a dark stained-wood background
  (`BarrelTableBackground`). The five held-dice slots belong to the mat: every `DiceMat` supplies
  its own `slotSocketBrush` (darker than the mat) and `slotSocketBorder` (lighter than both), so a
  new mat can't fall back to another mat's slots. Picking one skin for one category doesn't imply the others - a fire
  die can sit in a barrel cup on a midnight felt mat.
  Cups: every `DiceCupStyle` draws inside `CupCanvas` (`CupRotation.kt`), which owns the shared
  shake/pour rotation and pivot, and authors its art on a 58 x 84 grid (the in-game cup's dp size).
  Every cup is drawn side-on from about 22 degrees above: anything round the cup's axis (mouth,
  base, hoops) is an ellipse squashed by the shared `CUP_VIEW_SQUASH`, and the open mouth shows a
  lit band of the far inner wall above a shadowed interior. Drawing the mouth open but the base
  flat mixes two viewpoints and reads wrong - keep new cups on the same angle.
  Proportions: a cup declares a `CupShape` - `TALL` (58 x 84) or `MEDIUM` (66 x 76: the Barrel and
  Takeaway) or `SQUAT` (76 x 66: the Top Hat, Cauldron, Flowerpot, Tankard and Beaker) - which is both its canvas size in dp in the game's 104dp cup slot and the grid its art
  is drawn on; the round-cup helpers read the grid from `CupDrawScope`, so they work on either.
  Liquid: `CupCanvas` hands every cup a `CupPose` - its rotation plus a `liquidRotation` that
  trails it on an underdamped spring. Every cup with liquid in it (Glass, Beaker, Takeaway,
  Cauldron) moves it with the one shared physics, `liquidIn` in `RoundCups.kt`; each only picks
  where its liquid rests. The surface is a level plane cutting the cup, so tipping *shears* it
  rather than rotating it: every edge point stays against the wall but rides up it on the low side
  and down on the high side by the tilt's slope times its distance across, the slope following
  `CupPose.surfaceTilt` so the liquid rushes to the low side, overshoots and washes back. If that
  ever lifts the low side over the lip, the level drops so it just reaches it - it brims, never
  spills. Glass cups draw all of it clipped to the glass (`drawLiquidInGlass`); solid ones only
  what shows through the opening. The Cauldron's brew rests deep enough that tipping all the way over
  only brings it to the lip, leaving little showing when standing (the maintainer's choice); the
  Takeaway's coffee rests high enough to see, and brims at the lip when tipped. The Flowerpot's plant bends back towards
  upright as the pot tips (`PLANT_UPRIGHT_PULL` of the tilt, each point along the stem taking its
  share by height, so it curves), and follows the pot on the liquid's spring
  (`CupPose.liquidRotation`, plus `PLANT_WOBBLE` of the slosh), so it wobbles through a shake and
  overshoots a pour. Cups without liquid ignore the pose.
  Two cups are animated, both on `rememberAmbientCycle` (an infinite transition read only in the
  draw, so it repaints rather than recomposes): the Cauldron's bubbles that swell and pop, heaving
  froth and glowing puffs rising off the brew, and the Takeaway's three thin, distinct steam lines
  (`drawSteamLines`). Both start on the liquid's own surface (so a tipped cup steams from wherever
  its liquid has pooled), rise straight up on screen (`screenwards` turns only the rise against the
  cup's rotation - bubble highlights use it too, to stay towards the light), and are clipped out
  wherever they'd sit over the cup's solid body rather than its opening. The Cauldron also carries a
  few fixed drips of old brew down its front, just to look used. It stops
  once a turn's rolls are spent: `DiceCupPanel` provides `LocalCupAnimated = false` while the cup
  is dimmed, and `rememberAmbientCycle` then leaves the infinite transition out of composition altogether,
  so a spent cup requests no frames, and draws a calm brew - no bubbles, froth or steam - until the
  next turn starts it again from the beginning.
  Easter egg: the Top Hat (drawn as deep as its opening is wide) hides a rabbit. Left tipped over
  after a roll with nothing happening for a random 5-10s, a rabbit pokes its head and paws out for
  5s, twitches an ear and ducks back in - once per quiet spell. Seeing it (on a human's turn)
  unlocks the secret achievement The Magician's Secret (`MAGICIANS_SECRET`), through
  `LocalOnRabbitSeen`, which `GameScreen` points at `GameViewModel.onRabbitSeen`; its icon is the
  same tipped hat and rabbit, rebuilt as a fixed-colour vector (`rememberMagicianIcon`). "Nothing happening" is `LocalCupActivity`, which
  `DiceCupPanel` sets to the current dice, so holding or releasing one restarts the wait (as do a
  roll or a score, through the cup's own `rolling`/`tilted`).
  The always-out rabbit (`RabbitSlide`) comes to rest against the low end of a tipped hat rather than
  bouncing off it: at 60fps each bounce stayed just over `RABBIT_AT_REST_SPEED`, so the hat's frame
  clock never stopped while it lay tipped. The rabbit is clipped only below the opening's centre line
  (so nothing shows down inside the hat), not to the opening's width above it: slid to one end and
  leaning, its ear tip used to be cut off square at the opening's edge.
  Easter egg: the Flowerpot's plant grows with the current player's rolls (`PlayerState.rollCount`,
  counted by `GameEngine.rollDice` and saved with the game) - bare soil, the seedling, a stalk with
  a bud, green petals pushing out of the bud, then a yellow sunflower (`flowerpotGrowthStage`). The
  first three come at 10, 20 and 30 rolls in every mode; the bloom at `rollsToBloom` - every roll of
  the game (`GameMode.maxRollsPerGame`, declared per mode like `maxPossibleScore`) but never fewer
  than 39. So it only blooms for a player who uses every roll of every turn: 39 in Standard, 51 in
  Tricolour, and never in Quickfire (13 rolls, so it stops at the seedling). `GameBoard` hands the
  cup a `FlowerpotGrowth` (stage plus whose plant it is) through `LocalFlowerpotGrowth`: the same
  player's plant grows into its next stage over `PLANT_GROW_MILLIS`, a different player's replaces
  it outright. The bloom stands well above the pot, into the room over the cup on the board (the
  spent-cup dimming layer is inflated past the cup's box so it isn't cut off). The bloom arrives on
  the game's last roll, just as the cup is spent, so a Flowerpot whose sunflower has grown keeps its
  colour instead of greying and, `BLOOM_STAND_UP_MILLIS` after pouring, stands back up to show it off
  (`DiceCupStyle.showsOffWhenSpent`, for any player whose pot blooms) - it still can't be rolled; the
  held-stage pots (`fixedStage`) grey like any other; outside a game - a
  Styles tile, the menu logo, where the local's default (bare soil, no grower) applies - a tall
  plant is shrunk to reach no higher than `PLANT_TOP_OUTSIDE_GAME`. Player 1 bringing it into bloom
  unlocks the secret Greenfingers (`GREENFINGERS`), judged by `AchievementEngine`'s in-progress
  rules straight after the roll (the last roll lands before the last box is filled). It unlocks four secret Flowerpot colours: the same pot held at each stage of the plant
  (seedling, bud, opening, sunflower), which never grow and never show off when spent - only the default, empty pot does. Its icon is the same sunflower
  (`rememberSunflowerIcon`), in the plant's shared colours from `ui/theme/Color.kt`.
  The default cup is the casino shaker (`CasinoDiceCupStyle`, Gold - Gold, Black, Green), shown on the Styles screen as
  "Classic" (every category's default style is called Classic - `StyleCatalogTest` checks) and
  listed first; its ids keep the `casino_` prefix so saved picks survive the default
  moving again. Gold (`casino_gold`) leads so a new player's first cup matches the launcher icon,
  which draws the same cup as a silhouette: `CupRimGold` body, `DicePipColor` mouth, lighter gold
  rim, and a `FeltNavyBottom` band (the one casino cup whose band isn't its rim colour). It took
  Burgundy's place, since a style has at most three colours; Black was the default before it. `FacetedDiceCupStyle` (a six-sided prism in the green score-tile colours with gold
  edges, the default before it) and `FireDiceCupStyle` share `drawFacetedCup`; `BarrelDiceCupStyle` is a brown wooden barrel with
  iron hoops. The original "leather" cup (steep taper, flared foot) was dropped for its shape;
  `SettingsRepository.diceCupStyleId` reads a saved `"leather"` or `"casino_burgundy"` (both dropped)
  back as today's default (`"casino_gold"`), since that's what's drawn for them, so neither counts as
  a non-default pick for `STYLE_CUP`.
- **Game modes** (`model/GameMode.kt`, was `GameType`): `STANDARD` (the official rules, formerly
  `CLASSIC`), `TRICOLOUR` (see Phase 14) and `QUICKFIRE` (see Phase 20). **Every rule that can
  differ between modes is a field on the mode**, even where the modes agree today: dice count, rolls
  per turn, die faces, die colours, the scorecard's categories (which is also the number of turns),
  the upper-bonus threshold/amount, the 5x bonus chip, the max possible score, a fixed turn
  timer that overrides the setup pick (`turnTimerSeconds`), where a timed-out turn is scored
  (`timeoutPick`), and whether a human's first roll is tapped for them (`autoRollAtTurnStart`). `.claude/GAME_MODES.md` has the
  checklist for adding a mode. The engine, AI, achievements,
  persistence and board all read the rules from there - a new mode should be a new entry plus any
  genuinely new scoring rule, and nothing else learning it exists. A category's own scoring rule is
  fixed and mode-independent (`ScoreCategory` carries its `section`, `fixedScore`, `jokerFreeFill`
  and `matchingColour`); a mode just chooses which categories are on its card.
- **Achievements**: 97 of them, **player 1 only** (`state.players[0]`, "You" on the setup
  screen) rather than any human at the table - the one exception is the ledger (the score-band and
  career-points achievements at the tail of `AchievementCategory.COLLECTION`), which stays measured
  against the leaderboard as a whole, i.e. every human who has played on this device, not just
  player 1 - see the "Player 1 only" phase entry below for why, and for the Google Play Games
  question this raises. Career points (`PROFESSIONAL_ROLLER`) are the exception to that exception:
  they sum only rows with `ScoreEntry.isPrimaryPlayer` (set from the seat, never inferred from the
  name). Score-threshold achievements (Solid Round, Sharpshooter, High Roller, Dice Deity) are
  judged only once a game finishes, never mid-game off a total an abandoned game would throw away.
  Local only for now but shaped so each maps onto a Google Play Games achievement later (see
  Phase 13). The seven in `AchievementCategory.EASTER_EGGS` are secret
  (`AchievementVisibility.SECRET`): kept out of the list - and its unlocked/total counts - until
  earned, and out of Completionist. Every `MISCELLANEOUS` achievement is `HIDDEN` (title shown,
  description "???" until earned), and vice versa - `AchievementEngineTest` enforces both pairings.
  Table-art style ones (`STYLE_DICE`/`STYLE_CUP`/`STYLE_BACKGROUND`) are judged at game START, not
  the end - the player picked the style before a die was ever rolled, so there's no reason to make
  them finish playing to hear about it. This is its own evaluation entry point,
  `AchievementEngine.evaluateAtGameStart(context: GameStartContext, ...)`, alongside `evaluate`/
  `evaluateInProgress`/`unlockNow` - added when the first game-start achievement showed the ad hoc
  `unlockAchievements(buildSet {...})` GameViewModel had been doing inline wasn't going to scale to
  a second one: **the engine, not the call site, decides what counts as earned**, everywhere else
  in this file, and game-start achievements are no exception. `GameStartContext` carries a plain
  boolean per style category rather than a style id, so `game/`'s pure engine never has to import
  the UI-layer style catalog just to compare a string; `GameViewModel.checkGameStartAchievements`
  (called from `startGame`/`resumeGame`) is only responsible for the async `SettingsRepository`
  read that builds that context and for persisting/announcing whatever the engine decides. A future
  game-start achievement is a new field on `GameStartContext` and a line in `evaluateAtGameStart` -
  never a new ad hoc check at a `GameViewModel` call site. `WASTED_5X` ("Roll a 5x but score a zero
  with it anyway") is checked at the moment of `commitScore`, not from the finished scorecard: a
  filled-in `FIVE_OF_A_KIND` box already answers "was it ever a real 5x", but not "were the dice a
  5x right when THIS zero went in" - that needs the dice and the chosen category together, which
  only `GameViewModel` sees before the commit changes anything. `DOUBLE_TON`/`TRIPLE_TON` (exactly
  200/300) sit right after `SCORE_200`/`SCORE_300` on the Scoring ladder, not before - despite the
  same threshold, hitting it exactly is harder than clearing it by any margin. `EXACT_CHANGE` needs
  every upper box scored with precisely its own pip count (1 in Ones, ..., 6 in Sixes) in one game.
  A second, much larger batch (25 more) covers interaction patterns a finished scorecard can't
  reconstruct at all - rolls, holds and commits watched turn-by-turn - so `GameViewModel` grew a
  large block of per-turn tracking state (reset in `resetPerTurnTracking`, called from `rollDice`
  whenever `rollsRemaining == FULL_ROLLS_REMAINING`) alongside the existing per-game fields.
  Highlights, and the traps found building them:
  - **`COMMITMENT_ISSUES` is "hold N of one number (1-4, not a 5x), let it go, then hold M of a
    different number (1-4) and score it"** - not specifically a pair, despite the name (a single
    die or a four of a kind both count, as long as every held die shares that one value and nothing
    else is held alongside it). Its state machine has to survive releasing a held group ONE TAP AT
    A TIME, since the real UI can only unhold one die per tap (each is its own tap target): "hold a
    group, then let it go" passes through intermediate states that aren't themselves an exact group
    of the SAME size - a first version treated any such intermediate state as "not holding an exact
    group anymore" and cleared the tracked value right there, before the last tap ever arrived, so
    the achievement could never fire from real play (a test written to release the group the same
    way a finger would caught this immediately). The fix tracks the group's own die indices
    (`pendingCommitmentGroupIndices`) and only clears the pending value when the held set stops
    being a SUBSET of that group - shrinking down one die at a time doesn't count as "different",
    only holding something outside the original group does.
    A held group of all 5 dice sharing a value is a real 5x, not indecision, and must never become
    the baseline the next group is compared against - even once it's *released* back down through
    4, 3, 2, 1 held, which looks identical, held-set-wise, to shrinking a plain 4-group. A
    `commitmentGroupTainted` flag set the instant the held set ever reaches all 5 (and cleared only
    once the held set returns to genuinely empty, or a fresh, unrelated group starts) is what tells
    the empty-handed moment not to credit that release.
  - **`TWICE_IN_A_LIFETIME`/streak-style per-player state is keyed by player index, not "any
    human"**, because "two turns in a row" means one player's own consecutive turns, which in a
    2+ player game are never chronologically adjacent (opponents go in between) - only that
    player's own map entry is ever written or read, so an intervening AI or other human's turn
    can't accidentally reset or fake the streak.
  - **`IMPATIENT`/`NATURALLY_GIFTED` track a `Set<Int>` of player indices that used an extra roll**,
    not a boolean - a human index absent from that set means every one of THEIR turns was a single
    roll, checked at game end against `state.players` by index rather than by name (names aren't
    unique, human vs AI matters, and the set is built purely from `GameViewModel.rollDice` noticing
    `rollsRemaining < FULL_ROLLS_REMAINING`).
  - **`WHY_DID_YOU_DO_THAT`, `ALMOST_FAMOUS`, `DICE_HATE_ME` and `COMMITMENT_ISSUES` all fire from
    `checkPreCommitAchievements`, called before `GameEngine.commitScore` changes anything** - same
    reasoning as `WASTED_5X`: the dice and the chosen category have to be read together, at the
    exact moment of commit, before the scorecard and turn move on.
  - **The dice cup stays clickable even with zero rolls left** (`DiceCupPanel`'s `enabled` was
    `canRoll`, now unconditional) purely so `NO_MORE_ROLLS` has a tap to count - `onCupTap` itself
    (in `GameScreen`) decides whether a tap rolls the dice or just increments the counter, as two
    separate `if`s rather than an `if`/`else if`: chaining them as `else if` made Kotlin infer the
    whole lambda's type as the join of `Job` (from `launch`) and `Unit`, i.e. `Any` - a type the
    `() -> Unit` callback slot doesn't accept.
  - **`Achievement.TIME_WASTING` is tracked in every build, debug or release** - only the actual
    superuser-mode effect (`GameViewModel.trackSuperuserSequence`'s `_superuserModeActive.value =
    true` and its toast) stays behind a debug build (`BuildInfo.isDebug` - `BuildConfig.DEBUG` on
    Android - passed in as `GameViewModel`'s `isDebugBuild`), so the achievement rewards performing
    the hidden hold sequence itself, whether or not this build lets it do anything.
  - **`NOT_THOSE_DICE` (tapping the menu's own logo dice - an Easter Egg now, so secret and outside
    Completionist, having started out in Miscellaneous as a hidden achievement; its icon is the
    logo's own fan of default dice, `rememberDiceFanIcon`) needed a `MenuViewModel`** purely to hold
    the one-line achievement unlock `MenuScreen` otherwise has no repository to reach - `AppLogo`
    gained an `onDiceTap` callback wrapping just the dice `Row`, not the wordmark below it. The
    tap also rolls the fan (`logoRollPose`, pinned by `LogoRollTest`): each die hops, spins a whole
    turn and flicks through other faces, one just after another, landing back on its own face at
    its own tilt (2-4-5-3-6 - the order "Product Placement" also relies on). A tap mid-roll is
    ignored; the achievement unlock is unchanged.
  - **The Achievements screen has its own, unrelated superuser mode**, entirely
    debug-build-gated (`AchievementsViewModel`'s `isDebugBuild`) - unlike the in-game one, this isn't a
    discoverable easter egg tied to an achievement, just a tester's shortcut, so nothing about it
    runs at all in a release build. Tapping the unlocked-count banner
    `AchievementsViewModel.SUPERUSER_TAP_TARGET` times arms it; a long press on any row then
    (`AchievementsScreen`'s hand-rolled hold-to-repeat gesture, the same "launch a ticking
    coroutine on down, cancel it on up" shape `DiceTray`'s die-cycling uses) toggles a plain
    achievement locked/unlocked on the first 500ms tick, or - for one with a progress bar - adds 1
    per tick (a real progress banner pops, same as earning it for real) until either it reaches its
    target or the press has been held 10s, at which point it jumps straight to a full unlock -
    `PROFESSIONAL_ROLLER`'s target is 100,000, and nobody's holding a row that long one tick at a
    time. The bump is a purely in-memory overlay (`AchievementsViewModel.superuserProgressOverride`)
    added on top of the real stored progress only for this display/decision, never persisted - the
    real `AchievementCounter`s are shared across several achievements (`GAMES_PLAYED` backs
    `GAMES_10`/`50`/`100`) and the leaderboard-backed ones (`PROFESSIONAL_ROLLER`, the score-band
    ledger) are read from real recorded scores, so actually writing a fake counter or leaderboard
    row would corrupt real state rather than just performing a test unlock. Locking an already
    unlocked achievement back up needed a new `AchievementStore.forceLock`, since the only existing
    lock-direction operation was `resetAll` - the Settings screen's all-or-nothing wipe. The banner
    itself also carries a bulk version, a plain `combinedClickable` long press (nothing repeating
    needed here, so no reason to reach for the row's hold-to-repeat gesture): unlocks every
    achievement still locked in one go, and the *next* long press - once nothing is left locked -
    relocks every one of them instead, decided fresh from what's actually locked each time rather
    than a remembered flag.
  - Not covered by an automated test: `CONTINUED_GAME` ("leave a game, come back to finish it") and
    the real, end-to-end "picked a non-default style" path for `STYLE_DICE`/`STYLE_CUP`/
    `STYLE_BACKGROUND` - specifically the `SettingsRepository` read that feeds `GameStartContext`.
    Both need `SettingsRepository`/`InProgressGameRepository` - concrete DataStore-backed classes,
    not interfaces, so neither can be faked on the plain-JVM test setup the way
    `AchievementStore`/`ScoreDao` are. What tests do cover on that setup: `checkGameStartAchievements`
    returning early (no unlock, no crash) when there's no settings repository at all, and - fully,
    since it needs no repository - `AchievementEngine.evaluateAtGameStart`'s own decision logic
    once `GameStartContext`'s booleans are already known (`AchievementEngineTest`).
- **The trademarked name is banned from the application entirely** - source,
  comments, identifiers, filenames and anything a player can see. See the
  rule in `CLAUDE.md`. The term is **"5x"** in user-facing text (what the
  scorecard tile has always shown) and **`FIVE_OF_A_KIND`/`fiveOfAKind`** in
  code. These `.claude/*.md` files are the one exception, because explaining
  what the game is requires the word; `README.md` is public and is not.

## New Gradle dependencies

(The original Phase 0 list. Since Phase 18 the app is Kotlin Multiplatform - see that phase and
`.claude/IOS_SUPPORT.md` for the current dependency set.)

- `androidx.navigation:navigation-compose`
- Room: `androidx.room:room-runtime`, `androidx.room:room-ktx`, plus
  `androidx.room:room-compiler` via the **KSP** plugin
  (`com.google.devtools.ksp`, version matched to the Kotlin version in
  `gradle/libs.versions.toml`).
- `androidx.datastore:datastore-preferences`
- No kotlinx.serialization of the app's own — settings are individual
  Preference keys, not serialized JSON. (It does arrive transitively, via
  `com.mikepenz:aboutlibraries-core` - see Phase 17.)
- `com.mikepenz:aboutlibraries-core` plus the
  `com.mikepenz.aboutlibraries.plugin.android` Gradle plugin - the open-source
  licenses report (Phase 17).

## Package layout (target shape)

(Since Phase 18 these packages live in `app/shared/src/commonMain/kotlin/` - the Android app keeps
only `MainActivity`, the `device/` platform services and the licence `TextView`. See `README.md`.)

```
net.zodac.dicefive/
  MainActivity.kt                      — hosts NavHost inside DiceFiveTheme (dark only)
  data/
    settings/
      SettingsRepository.kt            — DataStore-backed: remembered player names (slots 1-4)
    scores/
      ScoreEntry.kt (Room @Entity)     — id, playerName, score, timestampEpochMillis, won (nullable;
                                          null for a solo game or a pre-migration row), isPrimaryPlayer
                                          (false for pre-migration rows), fiveOfAKindCount (nullable;
                                          null for a pre-migration row)
      ScoreDao.kt                      — pagedScores(limit, offset), count(), bestScore(), insert(),
                                          playerGames() (every game, player then newest first)
      AppDatabase.kt                   — Room database (schema v5), singleton via Application; MIGRATION_1_2
                                          adds `won`, MIGRATION_3_4 adds `isPrimaryPlayer`, MIGRATION_4_5
                                          adds `fiveOfAKindCount`. A new column means bumping the version
                                          and adding a migration here
      ScoreRepository.kt               — wraps DAO, exposes page loads and playerStatistics() (adds the
                                          per-player current/best win streaks, walked separately from
                                          the SQL aggregate)
    achievements/
      AchievementsState.kt             — unlock timestamps + counters, as read back
      AchievementsRepository.kt        — own DataStore file, so a reset can't touch settings
      AchievementEvents.kt             — process-wide SharedFlow feeding the banner host
      AchievementStore.kt              — the interface GameViewModel depends on, so the path is testable
  model/
    Die.kt                             — a die's value, colour (coloured modes only) and held state
    ScoreCategory.kt                   — every category any mode can use: section, fixed score, joker free-fill, colour
    GameMode.kt                        — STANDARD, TRICOLOUR, QUICKFIRE: every per-mode rule (see "Game modes" above)
    TurnTimer.kt                       — the setup screen's turn timer choices (NONE, 30s, 60s, 120s)
    TimeoutPick.kt                     — FIRST_OPEN, LOWEST_SCORE: where a mode scores a timed-out turn
    DieColour.kt                       — RED, YELLOW, BLUE (Tricolour's die colours)
    PlayerType.kt                      — HUMAN, AI
    Difficulty.kt                      — EASY, MEDIUM, HARD (picked per CPU seat on the setup screen)
    PlayerConfig.kt                    — setup-time: slot, type, name, difficulty
    PlayerState.kt                     — in-game: name, type, difficulty, scorecard (Map<ScoreCategory, Int?>), fiveOfAKindBonusCount
    GameState.kt                       — gameMode, turnTimer, players: List<PlayerState>, currentPlayerIndex,
                                          dice: List<Die>, rollsRemaining, phase (AWAITING_ROLL / ROLLED), isGameOver;
                                          turnSeconds and awaitsAutoRoll resolve the mode's turn rules
  game/
    DiceScoring.kt                     — pure functions: score(category, dice), isFiveOfAKind etc.
    ScoreCalculator.kt                 — resolves a category pick against current scorecard incl. upper bonus (63+ => +35)
                                          and Yahtzee joker rule (extra Yahtzee => +100 bonus, forced placement rules)
    GameEngine.kt                      — pure reducer-style functions: rollDice, toggleHold, commitScore, advanceTurn
    AiTurnPlayer.kt                    — Easy/Medium/Hard hold and category choices, from the player's own mode
    AiNameGenerator.kt                 — static themed name pool, random pick without duplicates per game
    AchievementEngine.kt               — pure: what a game has earned so far (mid-game) and at the end
  navigation/
    Screen.kt                          — sealed route constants (menu, play/setup, play/game, scores, achievements, styles, settings)
    DiceFiveNavHost.kt                 — NavHost wiring, "play" nested graph shares GameViewModel via getBackStackEntry
  ui/
    menu/MenuScreen.kt                 — Play / Achievements / Styles / Leaderboard / Statistics / Rules / Settings buttons
    setup/
      GameSetupScreen.kt               — player count 1-4, per-slot human/AI + name field, game mode radio, turn timer
    game/
      GameScreen.kt                    — the in-game screen: turn timer badge, player tabs, board, the cup tap
                                          (onCupTap - finger, phone shake, or Quickfire's auto-roll), game over
      GameViewModel.kt                 — owns setup config AND live GameState; every roll lands through
                                          performRoll; drives AI auto-play via viewModelScope coroutine with short
                                          delays between steps; the turn timer; achievement tracking
      GameBoard.kt / ScoreGrid.kt      — the felt board: the scorecard grid (laid out from the mode's categories)
      DiceCupPanel.kt / DiceTray.kt    — the cup (and its rolls-left badge) and the dice on the mat
    scores/
      ScoresScreen.kt                  — paginated table (50/page), long-press row shows date tooltip
      ScoresViewModel.kt               — talks to ScoreRepository, tracks current page
    statistics/
      StatisticsScreen.kt              — one card per distinct human player name: max score, games
                                          played/won/lost, current/best win streak, first-played date
                                          + time; a drawn scrollbar hints at cards below the fold
      StatisticsViewModel.kt           — talks to ScoreRepository.playerStatistics()
    achievements/                       — AchievementsScreen (one themed list, easiest-first per
                                          theme; unlocked ones highlighted in place rather than
                                          split out + "Hide unlocked"), AchievementsViewModel,
                                          AchievementBannerHost (the overlay above the whole
                                          NavHost - see .claude/UI.md)
    styles/StylesScreen.kt             — preview tiles (dice / dice cup / mat & background),
                                          one horizontally-scrolling row per category
    settings/
      SettingsScreen.kt                — switches card and a reset card; footer shows
                                          app version + GitHub link via UriHandler
      SettingsViewModel.kt             — reads/writes SettingsRepository
    theme/                             — Theme.kt (one dark M3 colour scheme, no dynamic colour) + Color.kt
                                          (tonal-palette roles, plus the separate game-table palette). No
                                          light scheme and no theme setting. No Type.kt: the M3
                                          type scale is used as-is rather than overridden.
    common/                            — chrome shared by the menu and every non-game page: BrandBackdrop,
                                          ScreenScaffold (top app bar + back), PageColumn, AppLogo
```

## Screen flow (Navigation Compose)

```
NavHost(start = "menu")
  "menu"                    -> MenuScreen
  navigation(route = "play", start = "play/setup") {
    "play/setup"            -> GameSetupScreen (GameViewModel scoped to "play" graph)
    "play/game"             -> GameScreen (same scoped GameViewModel)
  }
  "scores"                  -> ScoresScreen
  "statistics"              -> StatisticsScreen
  "achievements"            -> AchievementsScreen
  "styles"                  -> StylesScreen
  "settings"                -> SettingsScreen
```

Sharing one `GameViewModel` across `play/setup` and `play/game` (scoped to
the parent "play" nav graph entry) avoids serializing player lists through
nav arguments or introducing a singleton holder.

## Game setup rules

- Player count stepper/selector: 1-4.
- Player 1 is always Human, locked (no type toggle) — 1P mode forces Human.
- Players 2-4: toggle Human/AI.
  - Human: text field for name, default `"Player N"`; prefilled from
    `SettingsRepository` if previously entered for that slot, saved back on
    change.
  - AI: no name field (generated at Start Game); a disabled difficulty
    selector (Easy/Medium/Hard) defaulting to Medium, greyed out.
- Game mode: radio group, one row per `GameMode` with its one-line description. Remembered
  between games (`SettingsRepository.gameMode`, stored by `GameMode.id`).
- Turn timer: segmented row, remembered between games. Disabled while the picked mode sets its own
  timer (`GameMode.turnTimerSeconds` - Quickfire's 10s); the pick is kept for when another mode is
  chosen, and the game itself starts with `TurnTimer.NONE`.
- "Start Game": builds initial `GameState` — generates AI names via
  `AiNameGenerator` (no duplicates within the game) at this point — and
  flips `GameViewModel` phase from CONFIGURING to PLAYING, navigating from
  `play/setup` to `play/game`.

## Yahtzee turn engine rules

The numbers here are Standard's - dice count, rolls per turn and the rest come from the game's
`GameMode` (see "Game modes" under Decisions, and `.claude/GAME_MODES.md`).

- Start of turn: 5 dice, 3 rolls remaining, all unheld. In a mode with
  `autoRollAtTurnStart` (Quickfire), a human's turn starts with `GameScreen` tapping the cup for
  them - the same tap a finger makes.
- Roll: rolls all non-held dice, decrements `rollsRemaining`.
- Hold: toggles a die's `isHeld` — only after ≥1 roll this turn. Still allowed once the last
  roll is spent (it has no effect then, but disabling the dice looked broken - see `GameScreen`'s
  `canHold`).
- Every roll, human or AI, lands through `GameViewModel.performRoll`, after the cup shakes for
  `CUP_SHAKE_MILLIS`: a human's via the cup tap (`onCupTap` - finger, phone shake or auto-roll)
  and `rollDice`, an AI's via its own turn loop.
- Score: player may pick any open `ScoreCategory` at any point once ≥1 die
  has been rolled this turn (not only after 3 rolls). `ScoreCalculator`
  computes the value including upper-section 63+ bonus (+35) and the
  Yahtzee joker rule (second-or-later Yahtzee always adds +100; if the
  Yahtzee box is already filled, score must go in the matching upper box if
  open, else any open lower box via joker free-fill, else zeroed into any
  open box).
- After scoring: advance to next player, reset dice/rolls/phase. If the new
  current player is AI, `GameViewModel` drives `AiTurnPlayer` through
  its rolls (up to the mode's `rollsPerTurn`, stopping early once it holds every die) and then a
  score, with brief coroutine delays so it's visibly animated, not instant.
- Turn timer: when one is set (the Turn Timer setting, or the mode's own `turnTimerSeconds`),
  running out rolls if needed and scores `ScoreCalculator.timeoutCategory` - the first open box, or
  in Quickfire the lowest-scoring one.
- Game ends when every player's scorecard is full; `GameViewModel` persists
  each **human** player's final total to `ScoreRepository` (one row per
  human player; AI scores are not saved) and sets `isGameOver = true` so
  `GameScreen` shows a results summary.

`DiceScoring`/`GameEngine` are pure functions with no Android
dependencies — most unit tests live here.

- **Maximum possible score, per mode: 1575 Standard and Quickfire, 2120 Tricolour** (`GameMode.maxPossibleScore`,
  with each derivation as a doc comment on its entry, locked by `GameModeTest` playing the perfect
  game through the real `GameEngine`) — every upper box maxed plus the 63+ bonus, every other box
  maxed, and every turn after the 5x box also landing a 5x for its +100 bonus chip (12 of them in
  Standard, 16 in Tricolour, whose three colour boxes take five 6s all of one colour and whose
  Coloured House takes the joker free-fill). Every screen that shows a score (Leaderboard,
  Statistics' max score) pads it to the digit width of the highest of these
  (`GameMode.HIGHEST_POSSIBLE_SCORE`, 4 digits) so the column stays a fixed width
  regardless of how many digits a given score has; current-game score displays (`PlayerHeaderBar`,
  `GameOverScreen`) don't need this — the player-name column next to them is already capped at 10
  characters, so there's nothing there for a short score to let grow sideways.

## Scores screen

- `ScoreDao`: `ORDER BY score DESC LIMIT :limit OFFSET :offset`, plus a
  `COUNT(*)` query for total pages.
- `ScoresViewModel`: current page index (0-based), `SCORES_PAGE_SIZE` rows
  per page (50), next/prev availability.
- `ScoresScreen`: scrollable rank/name/5x/score table in a compact row style;
  the 5x column is how many 5x that game scored (`PlayerState.fiveOfAKindCount` - the 5x box when
  it holds 50, plus one per bonus chip - the same definition the achievements' `SCORED_5X` counter
  uses);
  long-press a row shows a popup/tooltip with the formatted date from
  `timestampEpochMillis`. The next/prev controls only render when there is
  more than one page, but their height is always reserved, so the table ends
  in the same place either way.

## Settings & theme

- The app has a single, dark theme. There used to be a Light/Dark/System
  setting (`SettingsRepository.theme`); it was removed along with the light
  colour scheme. `DiceFiveTheme` takes no arguments, `MainActivity` pins the
  system bars to dark icons-on-dark (`SystemBarStyle.dark`) so a phone in
  light mode doesn't draw dark status bar icons over the page, and the XML
  window theme is `Theme.Material.NoActionBar`. A `theme` key left in an old
  install's DataStore is simply never read.
- `SettingsScreen`: a switches card and a reset card (two error-tinted `ListItem` rows styled like the
  switch rows, each a button that opens its confirmation; no filled red buttons). A quiet
  footer below it shows the app version (`BuildInfo.versionName` - from `BuildConfig`
  on Android)
  and a "View on GitHub" link to `https://github.com/zodac/DiceFive`,
  opened via Compose's `UriHandler` - this used to be its own `AboutScreen`,
  folded in here so the menu has one less destination. Below that, an
  "Licences" link opens `LicensesDialog` (Phase 17).

## Sound & haptics

- **Source clips live in `app/android/src/main/rawAudioSource/*.ogg`, not `res/raw/`.** The
  `normalizeOggAudio` task (`NormalizeOggAudioTask` in `app/android/build.gradle.kts`) peak-normalises
  every clip to -0.8 dBFS with **ffmpeg** and registers the output as a generated res dir, so
  `R.raw.<name>` still works. To add or replace a sound, drop the `.ogg` in `rawAudioSource/`.
  Don't hand-adjust gain and don't create `res/raw/`. The build fails without `ffmpeg` on PATH
  (`sandbox/Dockerfile` installs it locally; the Release workflow's "Install ffmpeg" step does
  so on CI).
- Clips: `cup_shake`, `mat_landing`, `hold`, `unhold` (played at 0.35 volume), `celebration`
  (Game Over when any human seat wins a game of two or more players, alongside an all-gold
  fireworks animation; a solo game doesn't celebrate, as there's nobody to beat).
- `SoundEffects` (shared) plays through the platform's `SoundPlayer`. On Android that's
  `AndroidSoundPlayer` (SoundPool, `app/.../device/`), which queues a play request made before
  that sample finishes decoding and plays it from `setOnLoadCompleteListener`. Without this,
  `celebration` is silently dropped because it fires on the first frame after the pool is created.
- `DiceHaptics` (shared) plays through the platform's `HapticsPlayer` - on Android
  (`AndroidHapticsPlayer`), a 150ms tick on hold/unhold and a buzz for the length of the cup shake. The
  Settings switches "Sound effects" and "Vibration" (in `SettingsRepository`) gate sound and
  haptics separately. `SoundEffects.enabled` is a mutable flag rather than a reason to skip
  loading, since the setting can change mid-session.
- Dice roll animation (`ScatterArea` in `DiceTray.kt`; paths in `ui/game/style/RollingDie.kt`):
  as the cup starts shaking, each unheld die is swept off the mat past the tray's near edge, out
  of sight for the rest of the shake - it's in the cup (`PickUpPath`, a slide); a turn's
  first dice aren't on the mat yet, so `GameScreen` keeps them hidden until the roll lands. When it
  lands - with the `mat_landing` sound - they're thrown back on together from there (`TossPath`) - up each die's own column into the
  far wall (the top of the
  scatter area), bouncing and rolling back to rest on their results, drifting a little between
  invisible column walls and spinning before they settle. Flat-faced styles tumble as a cube of
  their own faces (`TossedCube`), paced to the distance travelled, through the real faces round one
  axis; a toss starts on the very face the die was picked up showing and ends on its result, with the
  quarter-turns counted to make both true, so no face ever jumps. The D20 (`DiceStyle.tumblesItself`)
  turns itself instead. A style whose every face is already a drawn solid can't be rolled as cards -
  each would carry its own painted top and side, showing extra faces - so the hooks for one remain:
  it would override `DiceStyle.TossedDie` and tumble as one real projected solid, and its `topFace`
  makes `TossPath` finish with the face its still art shows on top, so it lands exactly as it then
  rests. (The Cube dice, the one style that used them, were removed at the maintainer's request; a
  saved pick of one now draws the default dice.) `TossPathTest` pins the ring rules. **No flash of the result:** `RollTracker` counts shake-starts and landings
  in the composition they happen in, and the pick-up/toss animations are keyed on those counts, so a
  toss is already under way in the frame the new dice arrive - they never appear at rest first.
  **Scoring waits for the dice:** `GameScreen` uses the same tracker to hold `diceSettling` true for
  `DICE_TOSS_MILLIS` after each landing, and `GameBoard` shows no highlights and takes no score
  taps while a roll is in hand (shaking or settling). **The cup waits too:** `onCupTap` ignores a
  tap (finger, phone shake or Quickfire's auto-roll) for the same window, so the next roll can't
  start until this one's dice are at rest, and the cup reads as disabled to TalkBack meanwhile
  (`CupPanelState.rollInHand`; `GameScreenCupGateTest`). Quickfire's auto-roll is keyed on it as
  well, so a turn that starts mid-settle rolls once the dice land. The CPU already waited the same
  `diceTossMillis` after each roll in its own loop. The Settings switch "Simple dice roll"
  (`SettingsRepository.simpleDiceRoll`, off by default) turns all of this back to dice
  flicking through faces in place while rolling, scoring straight away; `GameScreen` provides it as
  `LocalSimpleDiceRoll`.
  Shadows: dice on the mat don't draw their own drop shadow (`LocalDieCastsShadow` off - every style
  goes through `dieShadow`); the tray casts one ground shadow per die instead (`GroundShadow`), from a
  single fixed light above and left of the tray (`LIGHT_X`/`LIGHT_Y`), falling away from it by a share
  of the die's distance from it and spreading as a tumbling cube lifts over an edge. It follows the
  die through the pick-up, the throw and rest alike, so it never pops in on landing. Held dice, off the
  mat in their slots, keep their own. Each shadow is the die's own outline, from
  `DiceStyle.shadowShape` - the style's corner rounding (every style shares its corner constant
  between the die and its shadow), and the D20's exact twisted
  silhouette, at rest (`restingView`) and mid-tumble alike - the same `view` the die is drawn
  in, frame by frame. Coloured dice use the bevelled die's rounded square.

## Achievements

- `AchievementsScreen`: the full catalogue, locked first. See Phase 13 for
  the rules, storage and banner behaviour.

## Tests

Pure logic and view models are in `app/shared/src/commonTest` (run on the JVM, and compiled for
iOS); anything that needs Android or a rendered screen is in `app/android/src/test` (Robolectric).

- Rules: `DiceScoringTest` (every category), `ScoreCalculatorTest` (bonuses, the joker rule, where
  a timeout scores), `GameEngineTest` (roll/hold/score/turn advance/game-over), `GameModeTest`
  (each mode's rules, and its declared ceiling against a perfect game), `TieBreakTest`,
  `AiTurnPlayerTest`.
- Achievements: `AchievementEngineTest` (the rules), `GameAchievementsWiringTest` (unlocks through
  a real `GameViewModel` game, with scripted dice).
- View models and persistence: `GameViewModelTest`, `MenuViewModelTest`, `GameStateJsonTest`,
  `InProgressGameRepositoryTest`, `ScoreRepositoryTest`, `JsonTest`.
- Screens (Robolectric): `MainActivitySmokeTest` (the whole app launches), `GameScreenAutoRollTest`
  (Quickfire's automatic roll with the real screen and view model), `BoardSemanticsTest`,
  `GameBoardScoringGateTest`, `DiceTrayGestureTest`, `SettingsRowsTest`, `LicensesDialogTest`.
- `.claude/GAME_MODES.md`'s Tests section has the helpers (scripted dice, virtual time, the screen
  harness) and the traps.

## Verification

- `./gradlew test` — all unit tests pass.
- `./gradlew assembleDebug` — builds with new dependencies (Room/KSP,
  Navigation, DataStore).
- `./gradlew compileDebugAndroidTestKotlin` — androidTest sources compile
  against the current screens/ViewModels.
- **No emulator available in this sandbox**: no `emulator` package, no AVD
  system images, no connected device, and `/dev/kvm` alone isn't enough to
  stand one up without a multi-GB download outside this pre-baked image.
  So there is no manual/screenshot smoke test per phase — verification is
  compile + full unit test coverage of the pure logic (`game`/`model`
  packages) plus careful reading of each screen's Compose code. If the user
  runs this on a real device/emulator later and finds a UI issue, fix it
  then.
- **Robolectric is available for UI interaction tests** (since Phase 17): JVM
  unit tests can render a real screen and drive it with touches -
  `createComposeRule` (the `junit4.v2` one; the old one is deprecated, which
  fails the build) plus Espresso for any platform `View`s, with
  `@Config(sdk = [35])` and `@GraphicsMode(NATIVE)` (see `LicensesDialogTest`).
  Good for reproducing crashes and checking touch handling; it does not
  simulate everything a device does (e.g. the platform's long-press text
  selection), so a real-device check still matters for those.
  `MainActivitySmokeTest` launches the whole app this way (Phase 18).
- **iOS compiles here too** (since Phase 18): `./gradlew testDebugUnitTest` also compiles
  `:app:shared` (main and tests) for both iOS targets, so a JVM/Android API in common code fails the
  build. Linking an iOS app and running its tests needs macOS.

## Ad hoc debug build versioning

When handing the user a `DiceFive-debug.apk` directly (e.g. attached in chat, to sideload over
whatever debug build they already have installed), two separate things determine whether that
install-over-existing succeeds:

- **Signing certificate** — must match exactly, or Android refuses the install outright
  ("conflicts with an existing package") regardless of version numbers. All debug builds are
  signed with the local Gradle-managed `~/.android/debug.keystore`, which is created once and
  reused for every build afterwards - so this holds automatically *unless* that keystore file is
  deleted/regenerated between builds (e.g. a fresh container/sandbox). Sanity-check with
  `keytool -list -keystore ~/.android/debug.keystore -storepass android` and compare the SHA-256
  fingerprint to the previous build if there's any doubt.
- **`versionCode`** — must be >= whatever's currently installed, or Android refuses the install as
  a downgrade. The release scheme (`major*10000 + minor*100 + patch`, from the root `VERSION`
  file) is usually *unchanged* between two ad hoc debug builds in the same chat session, since
  bumping `VERSION` is part of the release flow, not something to do just to hand over a debug
  build. Reusing that scheme for debug builds would give repeated builds the *same* versionCode,
  which is a same-version reinstall (works today, but is one accidental `VERSION` edit away from
  becoming a refused downgrade).

  Fix: `app/android/build.gradle.kts` gives the `debug` build type its own `versionCode`/`versionName` via
  `androidComponents { onVariants(selector().withBuildType("debug")) { ... } }`, derived from
  wall-clock minutes-since-epoch instead of the `VERSION` file. Every fresh `assembleDebug` this
  way gets a strictly-increasing versionCode independent of whether `VERSION` changed, so handing
  over a new debug APK always installs cleanly over the last one. This only touches the `debug`
  variant - `assembleRelease` (what CI/the release workflow uses) still gets the clean
  `VERSION`-derived versionCode/versionName untouched.

  **Rule of thumb**: never hand-edit `VERSION` just to make an ad hoc debug build installable -
  that file is the release pipeline's source of truth (see Phase 12) and bumping it outside a
  real release desyncs it from the patch-bump automation. The debug versionCode scheme above
  already handles it.

---

## Phases

### Phase 0 — Build setup
- [x] Add Navigation Compose, Room (+ KSP), DataStore dependencies to
      `gradle/libs.versions.toml`, `app/android/build.gradle.kts`,
      `settings.gradle.kts` / root `build.gradle.kts`.
- [x] Confirm `./gradlew assembleDebug` still builds with no app changes yet
      (dependency wiring only). `./gradlew testDebugUnitTest` also green.

### Phase 1 — Domain models & pure game logic
- [x] `model/GameType.kt`, `model/PlayerType.kt`, `model/Difficulty.kt`,
      `model/PlayerConfig.kt`, `model/PlayerState.kt`, `model/TurnPhase.kt`.
      Renamed `ScoreCategory.FIVE_OF_A_KIND` → `YAHTZEE`.
- [x] Rewrite `model/GameState.kt` for multi-player/turn/phase shape.
- [x] `game/YahtzeeScoring.kt` — per-category scoring functions. (Since renamed `DiceScoring.kt`,
      with `YAHTZEE` → `FIVE_OF_A_KIND` and `GameType` → `GameMode` - see Phases 13 and 14.)
- [x] `game/ScoreCalculator.kt` — upper bonus (via `PlayerState`) + Yahtzee
      joker rule (available categories + scoring + bonus-chip eligibility).
- [x] `game/GameEngine.kt` — newGame/rollDice/toggleHold/commitScore
      reducers (commitScore calls advanceTurn internally).
- [x] `game/AiTurnPlayer.kt` — basic auto-play strategy + pure `playTurn`
      helper for tests.
- [x] `game/AiNameGenerator.kt` — themed name pool (12 names).
- [x] Unit tests: `YahtzeeScoringTest` (9), `ScoreCalculatorTest` (4, joker
      rule branches), `GameEngineTest` (10), `AiTurnPlayerTest` (3),
      `AiNameGeneratorTest` (2). All 30 tests pass; `assembleDebug` green.
- [x] Update `GameStateTest` for new shape.

### Phase 2 — Navigation scaffold & Menu
- [x] `navigation/Screen.kt` route constants.
- [x] `navigation/DiceFiveNavHost.kt` with all destinations wired: menu,
      nested "play" graph (play/setup -> play/game), scores, achievements,
      settings, about. Setup/scores/achievements/settings/about are thin
      stub screens for now (each notes which later phase fills it in).
- [x] `ui/menu/MenuScreen.kt` (Play/Scores/Achievements/Settings/About),
      "DiceFive" as the header text.
- [x] `MainActivity.kt` hosts `DiceFiveNavHost` instead of `GameScreen`
      directly.
- [x] `MainActivityTest` needed no change - menu header text is still
      "DiceFive" so `onNodeWithText("DiceFive")` keeps passing; confirmed
      `compileDebugAndroidTestKotlin` + `assembleDebug` + unit tests green.
      (The "play" nested graph doesn't yet share a scoped `GameViewModel`
      across setup/game - that lands in Phase 3 when setup actually has
      state worth sharing.)

### Phase 3 — Game setup screen
- [x] `GameViewModel`: `GameSetupState`/`PlayerSetupSlot` (player count 1-4,
      per-slot type/name/difficulty, game type), plus a nullable
      `game: StateFlow<GameState?>` populated by `startGame()`. Slot 1 is
      hardcoded Human (setPlayerType rejects slot 1).
- [x] `ui/setup/GameSetupScreen.kt`: player count stepper, per-slot
      Human/AI toggle (slot 1 fixed) + name field or disabled difficulty
      row, Classic/Extended (disabled) radio group, Start Game button.
- [x] `DiceFiveNavHost` now scopes one `GameViewModel` to the "play" nav
      graph entry (`navController.getBackStackEntry(Screen.PLAY_GRAPH)`),
      shared by both `play/setup` and `play/game` — Start Game populates
      `game`, then navigates; `GameScreen` reads the same instance's `game`
      state. `GameScreen` itself is still a minimal placeholder (full UI in
      Phase 4).
- [x] `assembleDebug`, `compileDebugAndroidTestKotlin`, and
      `testDebugUnitTest` all green.

### Phase 4 — Game screen & turn flow
- [x] `ui/game/DiceRow.kt` (tappable hold toggles), `ui/game/ScorecardView.kt`
      (category x player grid; current human player's open, available
      boxes are tappable score-preview buttons, others show a dash).
- [x] `GameScreen.kt`: current player banner, dice tray, roll button
      ("Roll (n left)"), scorecard grid, game-over ranked results summary
      with a Back to Menu button.
- [x] `GameViewModel`: `rollDice()`/`toggleHold()`/`commitScore()` call
      `GameEngine`, gated to a no-op unless it's the human's turn.
- [x] AI auto-play: `maybeStartAiTurn()` launches a `viewModelScope`
      coroutine (600ms/step) that rolls 3x then scores via `AiTurnPlayer`
      (since Phase 20 its rolls shake for the tap's `CUP_SHAKE_MILLIS`),
      re-checking itself afterward so back-to-back AI players chain
      automatically.
- [ ] Game-over → persist human scores: **deferred to Phase 5** (no
      `ScoreRepository` yet) - `GameScreen` already renders the ranked
      results screen once `isGameOver`.
- [x] New tests: `GameViewModelTest` (4 tests, incl. AI auto-play via
      `kotlinx-coroutines-test`'s `StandardTestDispatcher` +
      `advanceUntilIdle`). `assembleDebug`, `compileDebugAndroidTestKotlin`,
      `testDebugUnitTest` all green.

### Phase 5 — Persistence
- [x] `data/settings/Theme.kt`, `data/settings/SettingsRepository.kt`
      (DataStore: `theme` + per-slot `playerNameFor(slot)`/`setPlayerName`).
- [x] `data/scores/ScoreEntry.kt`, `ScoreDao.kt`, `AppDatabase.kt`,
      `ScoreRepository.kt` (Room).
- [x] `GameViewModel` now takes `scoreRepository`/`settingsRepository` as
      **nullable constructor params** (not `AndroidViewModel`) so it stays
      constructible/testable on a plain JVM with no `Context`; a
      `GameViewModel.factory(context)` (via `viewModelFactory { initializer {...} }`)
      builds the real ones and is passed at both `play/setup` and
      `play/game` call sites in `DiceFiveNavHost` (`LocalContext.current`).
      `startGame()` persists human slot names; `setGameState()` persists
      each human player's final score exactly once, on the not-over ->
      over transition.
- [x] Unit tests: `ScoreRepositoryTest` (3 tests) against a hand-written
      `FakeScoreDao` rather than a real Room/SQLite instance - no
      Robolectric/emulator available in this sandbox (see Verification).
      Room's own `@Query` SQL (ORDER BY/LIMIT/OFFSET/COUNT) is simple and
      low-risk; worth double-checking on a real device later.
- [x] `assembleDebug`, `compileDebugAndroidTestKotlin`, `testDebugUnitTest`
      all green (37 unit tests total).

### Phase 6 — Scores screen
- [x] `ui/scores/ScoresViewModel.kt`: `ScoresUiState` (entries/pageIndex/
      totalCount/isLoading + derived totalPages/hasNext/hasPrevious),
      nullable `ScoreRepository` ctor param + `factory(context)` (same
      pattern as `GameViewModel`).
- [x] `ui/scores/ScoresScreen.kt`: rank/name/score `LazyColumn` (100/page),
      Previous/Next + "Page X of Y" controls, empty state. Each row is
      wrapped in a Material3 `TooltipBox` (`PlainTooltip`, built-in
      long-press-to-show on touch) showing the game's date - the date
      column itself stays hidden per the clarified requirement. Date
      formatted with `java.time` (available unshimmed at minSdk 26).
- [x] Wired into `DiceFiveNavHost` via `ScoresViewModel.factory(LocalContext.current)`.
- [x] `assembleDebug`, `compileDebugAndroidTestKotlin`, `testDebugUnitTest`
      all green (37 unit tests, unchanged - no new pure-logic surface here
      beyond what Phase 5's `ScoreRepositoryTest` already covers).

### Phase 7 — Settings screen
- [x] `ui/settings/SettingsViewModel.kt`: `theme: StateFlow<Theme>` (default
      SYSTEM), `setTheme()`; same nullable-repository + `factory(context)`
      pattern.
- [x] `ui/settings/SettingsScreen.kt`: Light/Dark/System radio group.
      (Later removed: the app is dark only - see "Settings & theme".)
- [x] `MainActivity` now builds a `SettingsRepository` directly (not via a
      ViewModel - it's a simple top-level read, and Activity recreation
      re-reads DataStore fresh anyway) and resolves `darkTheme` for
      `DiceFiveTheme`: LIGHT->false, DARK->true, SYSTEM->`isSystemInDarkTheme()`.
- [x] `assembleDebug`, `compileDebugAndroidTestKotlin`, `testDebugUnitTest`
      all green (37 unit tests, unchanged).

### Phase 8 — Achievements screen (stub)
- [x] `ui/achievements/AchievementsScreen.kt` placeholder screen wired into
      nav - already built and wired in Phase 2 (it never needed a
      ViewModel or later-phase rework), so there's nothing further to do
      here. No trigger/persistence infrastructure, per the task's own
      "TBC later".

### Phase 9 — About screen
- [x] `ui/about/AboutScreen.kt`: app name, `BuildConfig.VERSION_NAME`, and a
      clickable "View on GitHub" text opening
      `https://github.com/zodac/DiceFive` via `LocalUriHandler`. Enabled
      `buildFeatures.buildConfig = true` in `app/android/build.gradle.kts` for the
      version string.
- [x] `assembleDebug`, `compileDebugAndroidTestKotlin`, `testDebugUnitTest`
      all green (37 unit tests, unchanged).

### Phase 10 — Final verification
- [x] `./gradlew clean assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin lint`
      all green (37 unit tests, 0 failures).
- [x] Ran `./gradlew lint`: fixed the one finding introduced by this work
      (`ModifierParameter` in `GameScreen.kt` - `modifier` wasn't the first
      optional param; reordered ahead of `onBackToMenu`). The remaining ~22
      warnings (GradleDependency/NewerVersionAvailable/AGP-version nags,
      plus a few pre-existing launcher-icon/target-SDK findings from the
      original skeleton) are pre-existing or out of scope - not touched.
- [x] Manual smoke pass via `run` skill: **not possible** - no
      emulator/AVD/device in this sandbox (see Verification above);
      substituted with full unit test coverage of the pure logic and a
      careful reading of every screen.
- [x] Updated `README.md`'s Structure section to match the final package
      layout, and pointed it at this file for the full design/phase log.

### Phase 11 — Post-release review follow-ups
- [x] **Back confirmation during a game**: `GameScreen` installs a
      `BackHandler` (this also fixes a latent gap - previously an actual
      back-press/gesture would default to popping just one nav entry,
      landing back on the setup form instead of Menu). While a game is in
      progress and the new Settings toggle "Confirm before leaving a game
      in progress" (`SettingsRepository.confirmBeforeLeavingGame`, default
      on) is enabled, back shows an AlertDialog ("Leave"/"Cancel") before
      exiting to Menu; once the game is over, back always goes straight to
      Menu with no prompt.
- [x] **Continue / New Game**: added `data/game/GameStateJson.kt` (hand-rolled
      `org.json` (de)serialization of `GameState` - `testImplementation(libs.org.json)`
      added since Android's org.json is stubbed on the unit-test classpath)
      and `data/game/InProgressGameRepository.kt` (DataStore-backed, at most
      one saved game, cleared on finish). `GameViewModel.applyGameState()`
      now autosaves after every state change (human, AI, or undo) and clears
      the save when a game finishes, so a game survives navigating away or
      even process death. `MenuScreen`'s Play button shows a
      Continue/New Game dialog when `InProgressGameRepository.hasInProgressGame`
      is true (later replaced by a split New Game | Continue button with no
      dialog - see `UI.md`'s component conventions); `Screen.PLAY_SETUP_ROUTE` gained an optional `resume` nav arg
      - `DiceFiveNavHost` uses it to call `GameViewModel.resumeGame()` behind
      a small loading spinner before landing on `play/game`, falling back to
      the normal setup form if there was nothing to resume after all.
      Fixed a latent bug found while wiring this up: AI turns previously
      mutated `_game` directly rather than through the shared
      apply-state path, so if an AI player's move happened to be the one
      that completed the game, human players' final scores were never
      persisted to the leaderboard. Routing AI moves through the same
      `applyGameState()` fixed this as a side effect.
- [x] **Undo**: `GameViewModel` tracks a single pre-action snapshot
      (`undoSnapshot`/`canUndo`) captured before every human roll/hold/score.
      `undo()` restores it, cancels any AI turn job it would have triggered
      (closes a race where a pending AI coroutine could otherwise clobber
      the reverted state ~600ms later), and clears the snapshot (single-use,
      no redo). Any AI action clears the snapshot outright - only the most
      recent *human* move is ever undoable. An "Undo" button sits next to
      Roll on `GameScreen`, enabled only while `canUndo` is true; there's no
      Undo on the game-over screen (undoing a finished game would also need
      to retract an already-persisted score - out of scope here).
      The button is shown only in a solo game (one player): with others it reached back into their
      finished turn, or was cleared by the AI's move almost at once.
- [x] New/updated tests: `GameStateJsonTest` (3, round-trips including nulls
      and a finished game), `GameViewModelTest` (+5: undo of a roll, undo of
      a scored category, undo unavailable before any move and after an AI
      move, `resumeGame()` with no repository configured). Found and fixed a
      real bug during this work: `canUndo` was originally a
      `_undoSnapshot.map {}.stateIn(...)` derived flow, which doesn't update
      synchronously (only after the coroutine dispatcher actually runs the
      collector) - a plain `_canUndo` `MutableStateFlow` updated in lockstep
      with the snapshot, matching how `_game`/`_setup` already work in this
      class, both fixed the test failures and is simpler.
- [x] `assembleDebug`, `compileDebugAndroidTestKotlin`, `testDebugUnitTest`
      (45 unit tests), and `lint` all green; no new lint findings beyond the
      pre-existing/version-nag set from Phase 10.

### Phase 12 — Release pipeline
- [x] Root `VERSION` file (currently `0.0.1`) is the single source of truth
      for the app version. `app/android/build.gradle.kts` reads it at configure
      time: `versionName` = the file's contents, `versionCode` =
      `major*10_000 + minor*100 + patch` (deterministic, reproducible
      locally and in CI, no extra state to track).
- [x] Release signing: `app/android/build.gradle.kts` builds a `release`
      `signingConfig` from four env vars (`ANDROID_RELEASE_KEYSTORE_PATH`,
      `_KEYSTORE_PASSWORD`, `_KEY_ALIAS`, `_KEY_PASSWORD`). If any are
      unset, the `release` build type simply gets no signing config
      (`./gradlew assembleRelease` still works locally, producing an
      unsigned APK) - CI supplies all four from repository secrets.
      Generated a real release keystore (`keytool`, RSA 2048, 10000-day
      validity, alias `dicefive-release`) - **not committed**; handed to
      the user out-of-band (base64 + passwords) with instructions to add
      as repo secrets (`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
      `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`). `.gitignore` gained
      `*.jks`/`*.keystore`/`keystore.properties` as a defensive backstop.
      Verified end-to-end locally: `assembleRelease` with these env vars
      set produces an APK whose signing cert SHA-256 matches the generated
      keystore's, and `versionCode`/`versionName` land correctly (verified
      via `aapt dump badging`).
- [x] `.github/workflows/release.yml`: triggers on every push to `main`.
      Steps: checkout, JDK 21 (matches the sandbox's pin), Android SDK
      (explicit `sdkmanager` call installing `platforms;android-35` /
      `build-tools;35.0.0` against the SDK already on the `ubuntu-latest`
      runner - see the CI-failure fix note below), Gradle
      caching (`gradle/actions/setup-gradle@v4`), run `testDebugUnitTest`
      as a release gate (a broken build never gets published), decode the
      keystore secret to `$RUNNER_TEMP` (outside the checkout, never
      persisted), `assembleRelease`, then `softprops/action-gh-release@v2`
      creates/updates the GitHub Release tagged `v<VERSION>` with the APK
      attached. Re-pushing without bumping `VERSION` updates that same
      release (idempotent) rather than failing on a duplicate tag - only
      bump `VERSION` when a new release entry is actually wanted.
      `concurrency: group: release` prevents two overlapping runs from
      racing on the same release.
- [x] Scope note: no lint step in this workflow (kept the pipeline focused
      on what "release" needs: correctness gate + build + publish - lint
      is a code-quality check already exercised in Phase 10, not a release
      gate the user asked for).
- [x] **Secrets scope**: recommended repository (not organization) secrets
      - this keystore signs only this one app's identity, no other repo
        should ever need it.
- [x] **CI failure fix (first real run)**: `android-actions/setup-android@v3`
      failed on the "Set up Android SDK" step with `Failed to find package
      'tools'` - that action always tries to install the legacy monolithic
      "tools" SDK package, which Google removed from the repository years
      ago (superseded by cmdline-tools), so it now fails on every fresh
      install. GitHub's `ubuntu-latest` runners already ship a full Android
      SDK pre-installed with licenses accepted (confirmed by the failure
      log itself - the broken action found and ran `sdkmanager` from
      `/usr/local/lib/android/sdk/...`, the runner image's standard
      location), so the action added no value anyway. Replaced both the
      "Set up Android SDK" and "Install required SDK packages" steps with
      one step that calls `$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager`
      directly to accept licenses and install just the two packages this
      project pins.
- [x] **Pre-1.0 releases**: a "Read version" step derives `stable` (major
      version `>= 1`) alongside the version string. While `stable != true`:
      the keystore-decode/`assembleRelease`/APK-rename steps are skipped
      entirely (`if: steps.version.outputs.stable == 'true'`) - no signing,
      no build, just the version bump - and the GitHub Release is created
      with `prerelease: true` and no file attached (`files:` sourced from
      the skipped rename step's now-empty output, which
      `softprops/action-gh-release` treats as "no assets"). Unit tests
      still run regardless of stability, since correctness gating shouldn't
      depend on version number. Once `VERSION` reaches `1.0.0`, the full
      build+sign+attach path resumes and releases stop being marked
      pre-release.
- [x] **Automatic patch-version bump**: `scripts/set-version.sh <version>`
      is the one designated place that writes the app version everywhere
      it's referenced - today that's just the `VERSION` file (Gradle
      already single-sources from it, and no doc hardcodes a "live"
      version number), but it's the extension point if that ever changes.
      `scripts/bump-patch-version.sh` reads the current version and calls
      `set-version.sh` with its patch component incremented
      (`0.1.0 -> 0.1.1`) - it never touches major/minor; those are only
      ever changed by hand (edit `VERSION` directly, or run
      `set-version.sh` with an exact value) to start a new minor/major
      line. The release workflow's last step runs `bump-patch-version.sh`
      after every successful release (re-syncing with `origin/main` first
      in case it moved mid-run) and commits+pushes the bump with the
      default `GITHUB_TOKEN`, which GitHub does not use to re-trigger
      workflows on push - so this can't create an infinite loop. Verified
      both scripts locally (patch bump, manual set to a new minor line,
      resumed patch-bumping from there, and invalid-input rejection) in an
      isolated temp directory before wiring them into CI.
- [x] **Output APK naming**: `app/android/build.gradle.kts` sets
      `android.base.archivesName = "DiceFive"`, so output filenames are
      `DiceFive-debug.apk` / `DiceFive-release.apk` (or
      `DiceFive-release-unsigned.apk` when built locally with no signing
      env vars set) instead of AGP's `app-*.apk` default. Updated the
      release workflow's "Rename APK for release" step to copy from the
      new `DiceFive-release.apk` path - it was still referencing the old
      `app-release.apk`, which would have broken that step on the next
      full (>=1.0.0) release. Verified both `assembleDebug` and
      `assembleRelease` locally produce the expected filenames.

- [x] **Release descriptions and commit format**: the GitHub release's description is no longer
      GitHub's auto-generated notes (`generate_release_notes`), but `RELEASE_NOTES.md` - the
      maintainer's own summary, written before pushing, skipped if empty - followed by a
      "## Changes" section from `scripts/release-changelog.sh`: every commit since the previous
      release, grouped by the category in its subject line. That script takes the newest `v*` tag
      reachable from HEAD other than this release's own (so a re-run of an already-tagged release
      lists the same changes), drops merges and the workflow's own bump commits (authored by
      `github-actions[bot]`), sorts categories alphabetically ignoring case (grouped under the
      first spelling seen), keeps commits oldest-first within one, and lists subjects that don't
      follow the format under a final "Other". Checkout now uses `fetch-depth: 0` for the history
      and tags this needs.
      The subject format - `[Category] Short description`, anything after the first line
      free-form - is enforced locally by `.githooks/commit-msg` (enabled per clone by
      `scripts/install-git-hooks.sh`, which sets `core.hooksPath`; git never runs a repository's
      hooks on its own). Merge, revert and fixup!/squash!/amend! messages git writes itself are let
      through. Nothing enforces it server-side: a commit made without the hook installed shows up
      under "Other".
      The bump step empties `RELEASE_NOTES.md` along with bumping `VERSION`, and its commit is now
      `[Release] Bump version to X [skip ci]` - but it only empties the notes if they still match
      what this release shipped with (a copy is kept in `$RUNNER_TEMP`), so notes for the *next*
      release pushed while a run was going aren't wiped. Claude never edits `RELEASE_NOTES.md`
      without the user's say-so - see `CLAUDE.md`, and the guard hook in `.claude/settings.json`: a
      file edit aimed at it asks first (a hook's "ask" holds even in bypass-permissions mode), and
      a shell command is judged by what it did - the hook snapshots the file before every command
      and, if it changed (edited, created, deleted), puts it back and tells Claude, unless git left
      it matching HEAD (a pull/checkout/reset). The first version asked whenever a command merely
      *mentioned* the name, which prompted for commit messages, doc edits and scratch repos - and
      still missed a `sed` over a glob. `.claude/hooks/tests/run-hook-tests.sh` covers it against a
      throwaway repo, and `sandbox/setup.sh` runs it before every session.
      Follow-up: the "## Changes" heading now names the previous release ("## Changes since X.Y.Z",
      or plain "## Changes" with no previous tag), and each commit is listed as an 8-character short
      hash hyperlinked to its GitHub commit page (`<repo>/commit/<hash>`, the repo URL taken from the
      `origin` remote) instead of a bare hash in parentheses. Both changes live in
      `scripts/release-changelog.sh`, which now prints the heading itself; the workflow's "Compose
      release description" step no longer prints "## Changes" separately.
- [x] **Dependency updates**: `.github/scripts/update_dependency_versions.sh` bumps everything
      pinned - the Gradle catalog/wrapper/plugins (majors included), compileSdk (incl. minor SDK
      releases), the JDK (owned by the Gradle toolchain, `gradle/gradle-daemon-jvm.properties`),
      the sandbox image's pins, the actionlint image (`.github/scripts/lint_workflows.sh`) and the
      workflows' actions. Anything metadata can't vet (a JDK major, a compileSdk, any Gradle bump) is
      proven by a build; a bump that breaks it is found, taken back and stepped down to the newest
      version that builds. targetSdk is never moved by it (a device test decision).
      `.github/workflows/update-dependencies.yml` runs it on the 2nd of each month (and on dispatch),
      re-runs the release gates plus actionlint, and only if all pass commits
      `[Dependencies] Update dependency versions` to main, then starts `release.yml` with
      `gh workflow run` - releasing the next patch version. It uses only `GITHUB_TOKEN`, which may not
      edit workflow files and whose pushes trigger no workflows (hence the explicit dispatch, the one
      event it may trigger). So no version the update moves is pinned in a workflow: both workflows
      read the JDK from the toolchain file and the SDK packages from `app/android/build.gradle.kts`
      (`.github/scripts/android_sdk_packages.sh`) at run time. The actions' own `uses:` versions are
      the exception - the monthly run passes `--no-workflow-edits` and only lists newer ones in its
      summary; running the script locally applies them. The commit is authored as "DiceFive
      dependency updater", not `github-actions[bot]`, which `scripts/release-changelog.sh` drops from
      release notes.
- [x] **Deprecation gates**: deprecated API use fails compilation (`-Xwarning-level=DEPRECATION:error`
      in `app/android/build.gradle.kts`), any build-script warning fails the build
      (`org.gradle.kotlin.dsl.allWarningsAsErrors` in `gradle.properties`), and lint treats
      `Deprecated` / `ObsoleteSdkInt` as errors. `release.yml` compiles the instrumented tests and
      runs lint so all three are enforced in CI. The build uses AGP 9's new DSL and built-in Kotlin
      (no `kotlin.android` plugin); APK renaming goes through `VariantOutputImpl`, an internal AGP
      class, as there is no public API for it.

- [x] **R8 shrinking/minification/obfuscation for release.** The placeholder
      `app/proguard-rules.pro` Android Studio generates by default was renamed to
      `app/android/r8-rules.pro` (the project only ever runs R8, not classic ProGuard - the old
      filename was misleading) and `app/android/build.gradle.kts`'s `release` build type turned on
      `isMinifyEnabled` and `isShrinkResources` (both were `false`). Verified against a
      same-commit A/B: unminified `assembleRelease` produced a 14.19 MB unsigned APK: shrinking
      alone (code + resources, no renaming) brought that to 1.91 MB, and allowing R8 to rename
      classes/methods/fields on top of that (removing an initial `-dontobfuscate` rule) took it
      to 1.96 MB - most of the win is shrinking unused Compose/AndroidX classes and unused
      resource variants, not renaming, but renaming's own ~2.4% was worth keeping. `r8-rules.pro`
      also sets `-allowaccessmodification` so R8 can merge/inline more freely. No `-keep` rules
      were needed - the app built and its existing test suite stayed green with no missing-class
      or reflection failures. See `PUBLISHING.md` for archiving `mapping.txt` so Play Console can
      still deobfuscate crash traces once this ships.

### Phase 13 — Achievements
- [x] **Scope**: 51 achievements (50 + the later-added secret `CHEATER_CHEATER`),
      replacing the Phase 8 placeholder screen. (Since then: Cheater Cheater was dropped in
      `c4537af`, the secret ones became the Easter Eggs category, and there are 95 in all.)
      Local only for now, but every piece is shaped for a later Google Play
      Games migration: `Achievement.id` is a stable snake_case external key
      (**never change one** — it is the storage key and will be the Play
      achievement key; rename `title`/`description` instead), and the
      incremental ones carry a `counter` + `target` that map onto Play's
      incremental type. `ProgressStyle.STREAK` ones deliberately do *not*
      map — Play's `setSteps` can't go backwards — so they become plain
      unlock-only achievements there and keep their progress bar locally.
- [x] `model/Achievement.kt` — the catalogue, plus `AchievementCounter`
      (the five device-wide running totals) and `ProgressStyle`. Counters
      are shared, not per-achievement: "finish 10 / 50 / 100 games" is one
      stored number, not three.
- [x] `data/achievements/` — `AchievementsRepository` on its **own**
      DataStore file (`achievements`), so "Reset achievements" is a
      `clear()` that can't take the theme or the remembered player names
      with it; `AchievementsState` (unlock timestamps + counters); and
      `AchievementEvents`, a process-wide `SharedFlow` the banner host
      listens on.
- [x] `game/AchievementEngine.kt` — pure, no Android/persistence/clock, in
      the same style as `GameEngine`: `GameViewModel` reads the stored
      state, calls `evaluate`, writes the result back. `unlockNow` handles
      the achievements earned mid-turn (a full house, large straight or 5x
      rolled straight out of the cup).
- [x] **Rules that needed deciding** (all documented on the engine):
      - *Per device, not per player*: any human at the table satisfies an
        achievement; AI results only ever count as the opposition. A
        consequence worth knowing: in an **all-human** game the device
        always "wins", so win streaks keep climbing — that's what a
        device-scoped streak means.
      - *Solo games* count for games played, feats and score thresholds,
        but neither extend nor break a win streak (nobody to beat).
      - *A tie at the top counts as a win* for the human — nobody beat them.
      - *Cheating disqualifies the whole game*: if superuser mode was
        activated, nothing at all is counted, not even games played.
      - *Undo can't inflate anything*: end-of-game achievements are derived
        from the final scorecard, and the only live counter (dice rolled)
        is only ever incremented by a human roll, which is itself not
        undoable.
      - *`PERSONAL_BEST` reads the leaderboard before this game's own rows
        are inserted* — hence `ScoreDao.bestScore()` and the reordering of
        `GameViewModel.finishGame`, which now sequences read → persist →
        evaluate in one coroutine instead of firing persistence and
        forgetting about it.
      - *`I_ROBOT` is not currently earnable* (AI difficulty is deferred and
        every AI plays the same strategy), so it is excluded from
        `COMPLETIONIST`'s requirements via `countsTowardCompletion = false`.
        Flip that back when difficulty lands.
      - *`EXTREME_LOW_ROLLS` (finish on exactly 5) is* earnable: five 1s
        taken as Chance is legal play, and holding 1s makes it grindable.
        It stays a `COMPLETIONIST` requirement.
- [x] **When achievements are evaluated**: three moments, not one.
      `AchievementEngine.evaluateInProgress` runs after **every scored
      category** (and at game start, for "Full Table"), so a maxed Sixes box
      or a second Yahtzee lands the instant it happens rather than on the
      results screen. It only judges what a later turn cannot take away — a
      filled box, a banked bonus, a running total past a threshold (a total
      only ever grows), which is why the score-threshold wording is "Score
      200 or more in a game", not "Finish a game with". `evaluate` runs at
      game over for everything else (a complete card, a final score, a
      result, every counter). `unlockNow` covers the moment-in-time ones -
      a full house, a large straight or a 5x on the first of a turn's three
      rolls, which is the only thing a scorecard can't show after the fact.
      Counters stay **end-of-game only** so an undo can't inflate them, and
      the in-progress pass is skipped once the game is over so it can't race
      the final one into double-popping the same banner. A `Mutex` in
      `GameViewModel` serialises all three paths' read-decide-write cycles
      for the same reason.
- [x] **Progress banners**: `STREAK` achievements announce every single step
      (a streak is fragile and slow to build); `CUMULATIVE` ones only at
      quarter marks, or a 10,000-dice total would pop a banner every game.
      Neither fires for something already unlocked, or for one being
      unlocked in the same breath — the unlock banner says it all.
- [x] **Ordering**: `Achievement`'s own declaration order *is* the display
      order — grouped by `AchievementCategory`, and easiest-first within a
      category so a ladder's rungs stay adjacent and ascending
      ("Sharpshooter" → "High Roller" → "Dice Deity"). This replaced an
      alphabetical locked list, which scattered every ladder. Streaks are
      their own category rather than part of Winning, being the only ones
      that can fall back to zero. A new achievement therefore goes where it
      belongs in that reading order, not on the end;
      `AchievementEngineTest` fails the build if a category ends up split
      across the list. Nothing reads the ordinal and storage is keyed by
      `id`, so reordering never disturbs stored unlocks.
- [x] `ui/achievements/` — `AchievementsViewModel` (locked grouped by theme,
      unlocked newest-first and flat, "Hide unlocked" toggle), the rewritten
      `AchievementsScreen`, and `AchievementBannerHost` (see `.claude/UI.md`
      for why it sits above the `NavHost` and how the stack behaves).
- [x] Settings gained a "Reset achievements" row behind a `DiceFiveDialog`
      confirmation.
- [x] **Isolating the side effects**: `finishGame` wraps the leaderboard
      read and write in `runCatching`. They used to be sequential and
      unguarded, so a throw from either silently took the achievement
      evaluation down with it — the kind of failure that leaves no trace at
      all. `AchievementStore` was extracted from `AchievementsRepository`
      for the same reason: the path from a finished game to a stored unlock
      now has JVM tests (`GameAchievementsWiringTest`), where before only
      the pure engine did and the wiring was unverified.
- [x] Tests: `AchievementEngineTest` (25, covering win/loss/tie, streak
      reset, margins, per-device feats, cheat disqualification, both
      progress-banner policies, the Completionist cascade and id
      uniqueness). `ScoreRepositoryTest`'s `FakeScoreDao` gained
      `bestScore()`. 81 unit tests total, all green; `assembleDebug`,
      `compileDebugAndroidTestKotlin` and `lint` also green, with no new
      lint findings (the three in `DiceTray.kt` are pre-existing).
      Later grown to 95 tests total with the mid-game evaluation and the
      `GameAchievementsWiringTest` suite.
- [x] **"5x", not the trademark**: `Achievement`'s titles, descriptions and
      ids were renamed off the word ("5x!", `5x_first`, `SCRATCHED_5X`,
      `AchievementCounter.SCORED_5X`), matching what the scorecard tile has
      always shown. A test asserts no title, description or category label
      contains it, so it can't creep back in. The ids changed, which resets
      those unlocks on any device that already had them - acceptable
      pre-release, and the reason the "never change an id" rule starts
      properly from here.
- [x] **Renamed the word out of the source entirely** - landed as its own
      commit *ahead of* this phase, so it could ship without waiting for
      achievements: `ScoreCategory.YAHTZEE`
      → `FIVE_OF_A_KIND` (its original Phase 1 name, reverted),
      `YahtzeeScoring.kt` → `DiceScoring.kt`, `YahtzeeCupPanel.kt` →
      `DiceCupPanel.kt`, `isYahtzee` → `isFiveOfAKind`,
      `PlayerState.yahtzeeBonusCount` → `fiveOfAKindBonusCount`,
      `awardsYahtzeeBonus` → `awardsFiveOfAKindBonus`, plus every comment.
      `README.md` too - it is public, so it is not covered by the
      reference-documentation exception.
      **Known cost, accepted:** `GameStateJson` writes scorecard keys and
      the bonus count by their enum/property names, so any game that was
      in progress across this upgrade no longer resumes.
      `InProgressGameRepository.load` already swallows a decode failure and
      falls back to the setup form, so it degrades to "Continue does
      nothing" rather than crashing.
- [x] **The ledger** (the tail of `AchievementCategory.COLLECTION`): six achievements
      for having recorded *every* score in a 50-point band on the
      leaderboard - 5-50 (46 scores, since the band is inclusive and 5 is the
      lowest total the rules allow), then 51-100, 101-150, 151-200, 201-250,
      251-300 (50 each). Tally → Bookkeeper → Registrar → Auditor →
      Archivist → Historian, then Completionist after them: they are the
      longest haul in the game, so the whole group sits at the very end of
      the list. "Practice Makes Perfect" and "Full Table" moved the other
      way, out of Collection and into Milestones, where the rest of the
      "shape of a game you played" achievements already live.
      These are the one group **not** backed by a stored counter: progress is
      derived from the scores table via `ScoreDao.distinctScores()`, so they
      are retroactive (scores already on the leaderboard count the moment the
      next game finishes) and the progress bar can never disagree with what
      the Scores screen shows. `GameViewModel.finishGame` reads the distinct
      scores *before* inserting, for the same reason it reads the best score
      there, and `AchievementEngine` adds the finished game's own human
      totals itself - one read gives it both the before and after state of a
      band, which is what the quarter-mark progress banner needs.
      "Professional Roller" (100,000 career points) is measured the same
      way, off `SUM(score)`, which is why both it and the bands now travel
      together in a `LeaderboardTotals` carrier rather than as loose
      parameters.
      Two consequences worth knowing: a reset re-locks them but the
      underlying scores remain, so they re-earn on the next qualifying game
      (the same is already true of `PERSONAL_BEST`); and they count toward
      `COMPLETIONIST`, which makes that achievement a ~296-game commitment -
      flip `countsTowardCompletion` on the six if that is not wanted.
- [x] **Player 1 only, not any human**: achievements used to be per-device -
      any human at the table satisfied one. Now only `state.players[0]` -
      "You" on the setup screen, always `HUMAN` - earns anything at all;
      another human seat is just an opponent, same as AI. This is
      `AchievementEngine`'s first rule now, replacing the old "per device"
      one, and it touches three layers:
      - `AchievementEngine.evaluate`/`evaluateInProgress` bail out entirely
        unless `players[0]` is human, then build their `humans` list from
        just that one player - almost every existing check (`anyHuman`,
        `bestHumanScore`, `humanWon`, `winningMargin`) needed no further
        change, since they already just walked whatever list they were
        given.
      - `GameAchievementContext.extraRollPlayerIndices` (a `Set<Int>`) and
        `diceRolledByHumans` shrank to `playerOneTookExtraRoll: Boolean` and
        `diceRolledByPlayerOne`, since only one player's index was ever
        going to matter again. `GameViewModel` now guards every mid-turn
        achievement check (`rollDice`/`toggleHold`/`commitScore`/
        `tapCupWithNoRollsLeft`/`trackFinalRoundPosition`) with an
        `isPlayerOneTurn` (`currentPlayerIndex == 0`) check - the underlying
        game actions themselves stay ungated, so a second human seat still
        plays normally, it just earns nothing. `TIME_WASTING` is the one
        partial exception: the hidden hold sequence and superuser-mode
        activation itself still work "any player's turn" (that cheat's own
        design), only the achievement unlock is now player-1-gated.
        `previousTurnFiveOfAKindByPlayer` (keyed by player index, for
        `TWICE_IN_A_LIFETIME`) collapsed to a plain
        `playerOnePreviousTurnWasFiveOfAKind: Boolean` for the same reason.
      - **`SINGULARITY` needed an explicit fix, not just a narrower `humans`
        list**: it's the one Misfortune achievement whose description names
        an opponent type ("Lose a game to an AI"), but its condition was
        just `!humanWon`. Under the old "any human" rule that was safe -
        in an all-human game *someone* human is always at the top, so
        `!humanWon` could only be true when an AI actually won. Restricting
        `humans` to player 1 alone breaks that: player 1 losing to a
        *second human* now also makes `humanWon` false. Fixed by requiring
        an AI to actually hold `topScore` (`aiWon`) before awarding it.
        `PIPPED_TO_THE_POST`/`JAWS_OF_VICTORY` needed no such fix - their
        own descriptions never named an opponent type, so "player 1 simply
        lost" was already the right reading.
      - **`PERSONAL_BEST` needed a data-layer change**: "beat your best
        score" only makes sense per-name once a second human at the table
        stops counting as "you". `ScoreDao.bestScore()`/
        `ScoreRepository.bestScore()` (a global `MAX(score)` across every
        recorded human, its one and only caller) were replaced with
        `bestScoreForPlayer(playerName)` (`WHERE playerName = :playerName`),
        and `GameViewModel.finishGame` now reads player 1's own name before
        looking it up. Same rule as `PlayerStatistics` already followed:
        renaming player 1 between games starts a fresh "personal best"
        under the new name.
      - **The one exception, confirmed rather than changed**: the ledger
        (`TALLY`...`HISTORIAN`, `PROFESSIONAL_ROLLER`) already measured
        itself against `LeaderboardTotals`, built from *every* human's
        score via `ScoreRepository.leaderboardTotals()` and
        `GameViewModel.persistHumanScores` - both untouched. `evaluate` now
        computes that from a separate `allHumans` list, kept apart from the
        player-1-only `humans` used everywhere else.
- [ ] **Google Play Games**: not started. The mapping is designed for, not
      built — no Play Games SDK dependency, no sign-in, no server-side
      achievement definitions.
      **Decided, to apply once sync is actually wired up**:
      - The ledger keeps reading the whole device's leaderboard (`ScoreRepository.leaderboardTotals()`,
        every human's recorded score, not just player 1's) - unchanged from today's behaviour, and
        matching how the Leaderboard/Statistics screens already work. What changes is only *who* the
        unlock is reported to: even though a second human's games can contribute to `TALLY`/
        `PROFESSIONAL_ROLLER`/etc. reaching their target, the resulting unlock still pops on
        whichever Google account is signed in as player 1 - the same account every other achievement
        already reports to, since only one account is ever signed in per device. Nothing about the
        engine changes for this; it only matters for whatever thin syncing layer eventually calls
        the Play Games SDK off `AchievementUpdate.newlyUnlocked`.
      - Secret achievements (`AchievementVisibility.SECRET` - the Easter Eggs category) are **not**
        registered as Google Play Games achievements at all, hidden or otherwise - they stay a
        local-only surprise. Whatever mapping table eventually pairs `Achievement.id` with a Play
        Games achievement id should simply omit every `SECRET` entry, and the sync call that
        reports `newlyUnlocked` achievements needs to skip them too.

### Phase 14 — Game modes: Standard and Tricolour
- [x] **`GameType` → `GameMode`**, `CLASSIC` → `STANDARD`, placeholder `EXTENDED` → `TRICOLOUR`. The
      mode is modelled as the one home for every per-mode rule (see "Game modes" under Decisions),
      carried on `GameState` *and* on each `PlayerState` - a scorecard's totals depend on its mode
      (which boxes exist, the upper bonus, the chip value), so a player's totals never need the game
      around them. A scorecard only ever holds its own mode's categories; `ScoreCalculator`,
      `isScorecardComplete`, the section totals, `GameStateJson` and "How Do You Play This Game?"
      all iterate `gameMode.categories`, never `ScoreCategory.entries` (which now means "every
      category any mode could use"). Things that are the same in both modes today but were
      hard-coded - `GameEngine`'s 5 dice / 3 rolls, `GameViewModel`'s `FULL_ROLLS_REMAINING`
      family and 5-slot hold-cycle array, `AiTurnPlayer`'s `DICE_COUNT`, `PlayerState`'s bonus
      constants and `MAX_POSSIBLE_SCORE` - now read the mode.
- [x] **Tricolour rules** (beyond the official rules): every die rolls a colour (red/yellow/blue,
      equally likely) alongside its number - `Die.colour`, null in Standard, and `GameEngine.rollDice`
      only draws a colour in a coloured mode, so Standard's dice come out of a seeded `Random` exactly
      as before. Four new boxes in a new `ScoreSection.COLOUR` (kept out of `LOWER`, so the lower
      total and "Lower Class" mean the same in both modes): Reds/Yellows/Blues (40, all five dice
      that colour) and Coloured House (25, three of one colour and two of another). Coloured House
      is a joker free-fill like Full House (`ScoreCategory.jokerFreeFill`); the single-colour boxes
      are open to a repeat 5x under joker step 2 but score by the dice's real colours. Scores go on
      the one shared leaderboard like any other.
- [x] **Superuser cycling** in a coloured mode runs 1..6 within a colour then moves to the next
      colour's 1 (red → yellow → blue → red); Standard still wraps 6 → 1.
- [x] **Board**: the grid is laid out from the mode's categories (`scoreGridRows`), compact tiles
      beyond six rows (Tricolour's colour boxes are two extra rows), the 5x tile's top level with the
      grid's first row in both modes (`firstRowTileInset`), dice drawn in their own colour
      instead of the dice style (`ColouredDie`), and the default mat went from saturated blue to a
      muted slate so the blue die doesn't vanish on it - see `.claude/UI.md`'s "The scorecard grid
      and game modes" (including the side-column layout that was tried and reverted).
- [x] **AI**: Hard's exact expectation now enumerates *unordered* outcomes with multinomial weights
      (`forEachOutcome`), and memoises by held faces - five dice with 18 faces is 1.9M ordered rolls
      but 26,334 distinct ones, so it stays exact in Tricolour at a similar cost to Standard's old
      7,776. The per-category baseline is per mode, computed lazily. Medium banks any colour box
      the dice already fill and holds four of one colour to chase the fifth (after a forming
      straight, before a number group). Easy needed nothing new.
- [x] **Achievements**: new `AchievementCategory.GAME_MODES` (after Winning, before Misfortune):
      `NON_STANDARD_MODE` "Where We're Going, We Don't Need Rules" (game start, any
      non-Standard mode, via `GameStartContext.gameMode`; its icon, `ic_time_machine_car`, is an
      original wedge-car silhouette trailing fire - a nod to the film, not its car), `TRICOLOUR_WIN` "Tricolourful" (game end;
      needs an opponent, like every other win), `TRICOLOUR_ALL_COLOURS` "Tricolour Me Impressed"
      (non-zero in all four colour boxes; judged in `earnedDuringPlay`, so it lands the moment the
      fourth one is committed). `CHEATER_CHEATER` now checks the game's own mode's maximum and no
      longer names 1575 (it was later dropped - see Phase 13). Picking a mode other than Standard also counts as customising the game for
      "I Did It My Way", and a non-default *dice* style no longer counts for "Fresh Coat Of Paint"
      in a mode that doesn't show it. "Déjà Vu" and "Are These Loaded Dice?" compare number *and*
      colour, since that's what "the same result" means when dice have colours.
- [x] **Persistence**: `GameMode.id` is the stable storage key (never rename one). Saved games write
      `gameMode` plus each die's `colour`; a save from before this reads `gameType: "CLASSIC"` as
      Standard. The setup form remembers the last mode (`SettingsRepository.gameMode`).
- [x] Tests: `GameModeTest` (every mode's perfect game through the engine equals its declared max,
      ids, card contents), plus Tricolour cases in `DiceScoringTest`, `ScoreCalculatorTest`,
      `GameEngineTest`, `AiTurnPlayerTest`, `AchievementEngineTest`, `GameStateJsonTest` and
      `GameViewModelTest`. `PlayerStateTest`'s one test is superseded by `GameModeTest`.
- [ ] **Not yet seen on a device**: the compact 8-row grid, the striped Coloured House tile, the
      coloured dice and the new mat colour are compile-and-read verified only (no emulator in the
      sandbox).

### Phase 15 — Tie-break house rules
- [x] **Not in the official rules**: the real rulebook is silent on a tie at the top - it's simply
      recorded as a shared win, and DiceFive did the same until this phase (see Phase 13's "a tie at
      the top counts as a win"). This is a deliberate house rule layered on top, the same status as a
      casual group's own sudden-death round - not a restoration of anything official.
- [x] **The rule, in priority order** - whoever matched a score with more handicaps (less luck, more
      empty boxes) ranks above the other(s): fewest 5x, then most zeroed categories, then fewest of
      the four Tricolour-mode colour boxes actually scored (Tricolour only), then lowest upper
      section (excluding its 35 bonus), then lowest Chance, then lowest 3x, then lowest 4x. The first
      criterion that differs decides it; if every one of them also matches, it's a true tie and stays
      a shared rank - same as an untouched game of the official rules would have called it.
- [x] `game/TieBreak.kt` (new, pure, no Android/persistence deps): `TieBreakCriterion` (the seven
      criteria above, each with its own `reasonText` for the Game Over screen's caption),
      `TieBreakStats` (one player/row's inputs to the rule - every field but `score` nullable),
      `PlayerState.toTieBreakStats()` (always fully populated except `tricolourScoredCount`, null in
      Standard mode - no colour boxes to count), and two comparators:
      - `liveGameComparator` - a criterion both sides share as null (only possible for the Tricolour
        one, in a Standard game) is skipped, falling through to the next. Used by `GameOverScreen`,
        where a just-finished game's own players always have every other field populated.
      - `leaderboardComparator` - a *missing* stat (a row recorded before this phase shipped) counts
        as the worst possible value for that criterion rather than being skipped, so an unknown can
        never quietly tie with someone else's real, hard-won handicap - the same "unknown means
        excluded, not guessed at" call already made for `ScoreEntry.fiveOfAKindCount`. It also drops
        the Tricolour criterion entirely: the leaderboard mixes Standard and Tricolour scores on one
        list, and a Standard row has no colour boxes to compare a Tricolour one against - null there
        would otherwise always sink a Standard row under a Tricolour one on the same score, for the
        wrong reason.
      `decidingCriterion(a, b, forLeaderboard)` returns whichever criterion told two same-scored
      rows apart (or null for a true tie) - what a Game Over row's caption names. `TieBreak.rank`
      and its `RankedPlayer` (`player`, `originalIndex`, `rank`, `tieBreakReason`) live here too,
      not in a screen - the one ranking a just-finished game's players, shared by `GameOverScreen`
      (which renders it as-is, sorted) and the `TIE_BREAK` achievement (which looks up
      `originalIndex == 0` for player 1, since sorted position isn't seat identity). `originalIndex`
      exists because ranking pairs by list index rather than `associateWith`-ing `PlayerState` to
      its stats - two players can legitimately have identical `PlayerState` values (a true tie down
      to the last box), which a `Map` would silently collapse into one entry.
- [x] **`GameOverScreen`**: player-ranking now comes from `TieBreak.rank` - assigns a rank that only
      advances past however many players are a true tie (same pattern the previous "runners-up can
      tie too" fix used, now generalised across the whole table including the top spot, and shown
      with the same "=2" prefix `ScoresScreen` uses for a true tie). `winners` is now just "everyone
      at rank 1" - almost always one player, since a raw-score tie is now nearly always broken by
      some criterion below it. `WinnerCard`/`RunnerUpRow` gained an optional `tieBreakReason`:
      whenever a player's raw score matches the very next player down the list but the house rule
      still told them apart, their row gets a small caption ("Won on fewer 5x", "Won on more zeroed
      categories", ...) crediting the deciding criterion.
- [x] **Leaderboard**: `ScoreEntry` gained five nullable columns - `zeroedCategoryCount`,
      `upperSectionTotal`, `chanceScore`, `threeOfAKindScore`, `fourOfAKindScore` (no
      `tricolourScoredCount` column - see `leaderboardComparator`'s doc comment on why that
      criterion doesn't belong on the shared board at all) - via `MIGRATION_5_6` (schema v6),
      populated from `GameViewModel.persistHumanScores` off the same `PlayerState.toTieBreakStats()`
      the Game Over screen itself just used. `ScoreDao.pagedScores`'s `ORDER BY` now breaks a score
      tie with `COALESCE(column, sentinel)` per criterion (a large number for "lower is better", -1
      for "higher is better" `zeroedCategoryCount`) - the SQL-level mirror of
      `leaderboardComparator`'s "missing counts as worst" rule, so a page's row order and
      `ScoresScreen`'s own client-side tie/rank logic agree. **This is unrelated to
      `ScoreEntry.won`** - the achievements/statistics "a tie at the top counts as a win" rule from
      Phase 13 is untouched; the house rule only changes *ranking/display*, never who counts as
      having won for achievement or streak purposes.
- [x] **`ScoresScreen`**: `rankEntries` assigns each page's rows a rank via `leaderboardComparator`,
      the same "only advances past a true tie" logic as `GameOverScreen`; a row prefixes its rank
      with "=" only when it is a true tie with a neighbour (every criterion also matches), never for
      a raw-score tie the house rule went on to break. No reason caption on the Leaderboard itself -
      unlike the Game Over screen, a compact 50-row table has no room for one, and behind-the-scenes
      ranking was all that was asked for. A tie split across a page boundary isn't caught - each
      page only ever compares against its own fetched rows, same limitation the table's pagination
      already had for anything else.
- [x] **`Achievement.TIE_BREAK`** ("Tie Break", Winning category, visible - not Miscellaneous,
      despite the original ask, since that category is exclusively the *hidden*-achievement bucket
      (`AchievementEngineTest` enforces it both ways) and this is a normal, chaseable win condition
      like its neighbours): unlocked when player 1 holds `TieBreak.rank`'s rank 1 *and* their
      `tieBreakReason` is non-null - a plain, unbroken top score leaves that null, same as a solo
      win with nobody to tie. Deliberately separate from `FIRST_WIN`'s existing "a raw-score tie
      counts as a win" rule (Phase 13), which stays untouched - `TIE_BREAK` only fires when the
      house rule was actually the reason player 1, not the other tied player, holds the win. Icon:
      `Icons.Filled.Gavel` in `AchievementIcons.kt` - the ruling that settled an otherwise-equal
      score (an earlier pick, two arrows meeting head-on, didn't read clearly enough and was
      swapped out).
- [x] Tests: `TieBreakTest` (12 - each criterion's priority, null/skip vs null/worst semantics for
      the two comparators, the Tricolour-only criterion's exclusion from the leaderboard comparator,
      and `PlayerState.toTieBreakStats()`'s field extraction off a real scorecard). Four new
      `AchievementEngineTest` cases for `TIE_BREAK` (wins it, loses the tie-break so doesn't, a true
      unbroken tie doesn't, and a solo game can't). `./gradlew testDebugUnitTest` and `assembleDebug`
      both green.
- [ ] **Not yet seen on a device**: the Game Over screen's tie-break captions and "=" rank prefix,
      the Leaderboard's "=" rank prefix, and the Tie Break achievement's banner/icon are
      compile-and-read verified only (no emulator in the sandbox).
- [x] **For later**: this phase's rule list is the source for the player-facing rules section's own
      tie-break explanation - see Phase 16, which added that section.

### Phase 16 — Player-facing rules
- [x] A "Rules" entry on the main menu (`MenuScreen`, last in the destination button stack - since
      moved above Settings, which now closes it - same `MenuDestinationButton` style as every other
      entry) opens `ui/common/RulesDialog.kt`: a modal (since Phase 22, a real screen instead),
      not a nav destination - it never needs to be deep-linked to or survive process death, so a
      plain `remember { mutableStateOf(false) }` boolean in `MenuScreen` (the same pattern the
      existing resume-game confirmation already uses) is simpler than a new `Screen`/`composable`
      route.
- [x] **A new kind of modal**: `DiceFiveDialog` (the app's one existing dialog shape) is built on
      M3's `AlertDialog`, whose icon/title/text/buttons slots have no room for a pager and page
      indicator. `RulesDialog` instead wraps a raw `Dialog(properties = DialogProperties
      (usePlatformDefaultWidth = false))` around a `Surface` sized to ~92%/82% of the screen (capped
      at `CONTENT_MAX_WIDTH`), reusing `DiceFiveDialog`'s own container colour/shape/elevation
      (`surfaceContainerHigh`, `shapes.extraLarge`, `tonalElevation = 6.dp`) so the two dialog styles
      still read as the same app asking, despite one being a custom layout. First use of
      `HorizontalPager` (`androidx.compose.foundation.pager`) anywhere in this app - a plain
      `Column`/`Row` page-indicator (dots, larger for the current page) and previous/next
      `IconButton`s sit either side of it, since there was no existing convention to match.
- [x] **Six pages** (seven since Phase 20 added "Mode: Quickfire"), each its own swipe: How to Play (5 dice, 3 rolls, score into an open category),
      Upper Section (per-number totals, the 63/35 bonus), Lower Section (Full House/Small
      Straight/Large Straight's fixed values, Three/Four of a Kind and Chance scoring every die),
      5x and the Joker Rule (the 50-point box, the 100-point bonus chip, and the joker rule's
      three-step placement priority from `ScoreCalculator`), Tie Breaks (Phase 15's criterion list,
      in order), and Tricolour Mode (the colour boxes and dice, everything else unchanged). Content
      lives in a private `RULES_PAGES` list in `RulesDialog.kt` - kept in step with the actual rules
      engine (`ScoreCategory`'s fixed values, `ScoreCalculator`'s joker rule, `game/TieBreak.kt`'s
      criterion order) rather than copied out once and left to drift.
- [x] `./gradlew testDebugUnitTest` and `assembleDebug` both green - no new pure-logic surface here
      (this is presentation only), so no new unit tests.
- [ ] **Not yet seen on a device**: the pager, its page indicator/nav arrows, and the dialog's sizing
      against `CONTENT_MAX_WIDTH` on a real screen are compile-and-read verified only (no emulator in
      the sandbox).

### Phase 17 — Open-source licenses
- [x] **Why**: nearly every dependency is Apache-2.0, whose §4(a) requires giving recipients a copy of
      the license; the build strips AndroidX's own bundled copies (a size optimisation), and nothing
      replaced them - `PUBLISHING.md` tracked this as a real gap. The OFL on the bundled Sora font and
      the BSD-3-Clause on DataStore's embedded protobuf likewise require their text and copyright
      notice to ship. An in-app page covers every way the app is distributed (the GitHub release
      APKs today, Play later) at once, which a hosted web page linked from each would not.
- [x] **Nothing hand-listed**: the AboutLibraries Android plugin walks each variant's runtime
      dependency graph at build time into a generated `res/raw/aboutlibraries.json`
      (`includePlatform = false` - BOMs ship nothing). `LicensesDialog` (`ui/settings/`, opened from
      the Settings footer) reads it through `aboutlibraries-core` and shows it grouped by license,
      most-used first, each license's full text folded away behind "Show license text" - ninety-odd
      libraries share Apache-2.0 and it only needs to appear once. Same raised `Surface` shape as
      `RulesDialog` (Phase 16). `parseLicenseReport` is the pure part, unit-tested in
      `LicenseReportTest`.
- [x] **Copyleft guard**: strict mode `FAIL` with an allowlist of permissive licenses (Apache-2.0,
      MIT, BSD-2/3-Clause) - any other license, copyleft or unrecognised, fails every build of the
      variant (assemble, unit tests and lint alike), naming the license and every library that brought
      it in, transitive ones included. OFL-1.1 is allowed only for `sora` via `allowedLicensesMap`.
      Verified by temporarily adding `org.mariadb.jdbc:mariadb-java-client` (LGPL-2.1): the build
      failed on it and on the `jna` it pulls in transitively.
- [x] **Offline and deterministic**: `offlineMode = true`, so the plugin never fetches license text
      from GitHub/SPDX. The price is that it then knows each license's name but not its text, so the
      allowed licenses' SPDX texts are committed under `app/licensing/licenses/` - see the
      README there. `VerifyLicenseReportTask` (`verifyLicenseReport<Variant>`, wired before
      `generate<Variant>Resources`) fails the build if any shipped license lacks its text, if any
      library declares no license, or if a library under a notice-requiring license (BSD, MIT, ISC,
      OFL) lacks a `Copyright ...` line - the plugin never collects copyright lines, so those come
      from a `libraries/<name>.json` override.
- [x] **NOTICE files** (Apache-2.0 §4(d)): AGP's default packaging drops every `META-INF/NOTICE*`,
      so `CollectThirdPartyNoticesTask` pulls them out of the variant's runtime Java resources into a
      generated `res/raw/third_party_notices.json`, and the dialog shows a "Notices" section when it's
      non-empty. None of today's shipped dependencies has one (`concurrent-futures-ktx` does, but the
      app ships only `concurrent-futures`), so the section is currently hidden.
- [x] **Assets the build can't see** - fonts, sounds, artwork - aren't dependencies, so nothing can
      discover their license. `app/licensing/asset-sources.json` records every bundled asset file
      (any source set's `res/` bar `values*/`, `rawAudioSource/`, `assets/`) with a description,
      source, copyright line and license - the app's own artwork included
      (`LicenseRef-DiceFive-AllRightsReserved` - see `LICENSE`; source = the Claude Code session and commit that created it, confirmed from git history). A
      third-party asset also names its `libraries/` entry, which must agree on license and copyright.
      `verifyAssetSources` (before every variant's `generate<Variant>Resources`) fails the build on an
      unlisted asset, an incomplete or inconsistent entry, or a stale one - unaccounted-for is treated
      as unlicensed. Sora is a modified (static weight-700) instance - the OFL allows that, and Sora
      declares no Reserved Font Name, so it keeps its name.
- [x] **The five `.ogg` clips** had no recorded source at first, and the build was deliberately left
      failing until the maintainer found them - all modified Freesound recordings: `celebration`
      (remix of 588198 + 695731), `cup_shake` (185986) and `mat_landing` (596051) are CC0;
      `hold`/`unhold` are cut from 140147 ("Mantel Clock Ticking.wav" by Tewkesound), CC-BY 4.0. Each
      source has its own `libraries/` entry (`freesound-<id>`); an asset lists every source it's made
      from under `"libraries"`. CC-BY 4.0 §3(a) needs the creator, a link to the source and the
      license, and a note that it was modified - all in `freesound-140147`'s description, which the
      dialog shows. CC0-1.0 and CC-BY-4.0 are allowed only for those entries (`allowedLicensesMap` -
      verified: any other entry claiming CC0 fails strict mode).
- [x] **Dialog follow-ups**: the Settings link and dialog title are "Licences" (British spelling,
      matching the rest of the UI's "colour"; code identifiers keep `license`, matching the library's
      API and SPDX). The whole list is **one** platform `TextView` in a platform
      `ScrollView` (`LicenceDocument` - since Phase 18 `TextViewLicenceDocument`, in `app/android/.../device/`), its headings, rows,
      dividers and "Show / Hide licence text" toggles all spans in one `SpannableStringBuilder`
      (`buildLicenceDocument`) - because only within a single TextView can a selection be dragged
      across rows. `setTextIsSelectable` gives the system's own long-press behaviour - smart
      selection of a whole URL and the Copy / Share / Select all toolbar - and TalkBack sees its
      links. A tap on a link opens it, on a toggle flips it (`LinkTextView`'s `GestureDetector` - a
      bare `OnGestureListener`, since a double-tap listener would swallow a quick second tap), and a
      tap anywhere in the dialog drops a selection (`clearSelectionsOnTap` on the Initial pass ->
      `SelectionClearer`) without that same tap also following a link. A long press on a
      link shows the platform context menu instead of selecting - the URL as its title, then Copy link /
      Copy text, as a browser does (`LinkTextView`'s `OnLongClickListener` runs before the TextView's
      own long-press selection and stops it by returning true; anywhere off a link it returns false
      and selection proceeds). A Toast confirms a copy below Android 13, which confirms copies itself. Selection handles and
      highlight are tinted `primary` (handles API 29+). The `AndroidView` needs `clipToBounds()`: a
      platform `ScrollView` draws its content offset and relies on its parent to clip it, which
      Compose's interop host doesn't - without it, scrolled text drew up over the dialog's title
      (except mid-overscroll, whose stretch effect draws through a clipped layer). Pinned by a pixel
      test in `LicensesDialogTest` (Robolectric's `captureToImage` works with `GraphicsMode.NATIVE`,
      which also makes it a way to *see* a screen here - save the bitmap and view it). **Why not Compose**: tried first, and it
      failed on device - (1) `LinkAnnotation` opens on every press inside a `SelectionContainer`,
      long press included; (2) a custom long-press link menu raced the selection gesture, which can't
      be pre-empted (for touch, `awaitSelectionGestures` ignores a consumed down, and
      `awaitLongPressOrCancellation` only notices consumption of *later* events, which a still finger
      doesn't produce), so selection, its magnifier and the menu all started at once - that crashed;
      (3) clearing focus didn't drop a `SelectionContainer` selection inside the dialog; (4) a
      selection can't span separate text elements (rows). `LicensesDialogTest` covers link tap, link
      long press, the toggles, a selection spanning rows and tap-to-clear on the real view under
      **Robolectric** (added as a test dependency for this - the sandbox has no emulator); Robolectric
      doesn't simulate the platform's long-press selection itself, so that part is device-only.
      A licence's
      heading names what uses it by kind ("Used by 4 sounds", "Used by 1 library and 1 font"): an
      asset's `libraries/` entry carries `"tag"` (`font`/`sound`/`image` - `ComponentKind`), which
      `verifyAssetSources` requires; anything the plugin discovers is a library.
- [ ] **Not yet seen on a device** (no emulator in the sandbox). Release-build check done at the
      APK level only: both generated JSONs survive R8 resource shrinking (referenced directly via
      `R.raw`), but the parse under R8 hasn't been exercised on a device.

### Phase 18 — Kotlin Multiplatform (iOS groundwork)
- [x] **Why**: keep the option of an iOS version without rewriting the game in Swift. The plan,
      the target layout and what's left are in `.claude/IOS_SUPPORT.md`; this is the log.
- [x] **Seams first, in the single module** (`4241b5c`): `org.json` → `kotlinx.serialization`,
      `java.time` → `kotlinx-datetime` (both later replaced - see the APK size item below),
      `System.currentTimeMillis` → `nowEpochMillis()`; sound,
      haptics, the accelerometer and toasts behind `platform/PlatformServices` (Android side in
      `app/.../device/`); ViewModel factories take an `AppContainer`, not a `Context`;
      `BuildConfig` → `BuildInfo`. `ShakeDetector` became pure logic with its own tests.
- [x] **`:app:shared`** (`97476d6`): a KMP library (AGP 9's `com.android.kotlin.multiplatform.library`
      - `com.android.application` can't host KMP, hence the separate module) with Android, `iosArm64`
      and `iosSimulatorArm64` targets. The iOS targets compile on Linux, so every build checks
      common code for platform leaks. It answers to `testDebugUnitTest` (JVM tests + iOS compiles),
      so CI and the update script needed no changes. Tests are `kotlin.test` in `commonTest`; Kotlin/
      Native forbids commas and parentheses in their backticked names.
- [x] **Persistence** (`745d9bb`): Room and DataStore multiplatform. **The schema was collapsed
      into a single initial version 1**, with no migrations and no destructive fallback - pre-release,
      nothing to keep. Every stat column but `won` (null for a solo game) is `NOT NULL`: the nullable
      "recorded before this column existed" columns, the leaderboard's treat-missing-as-worst rule and
      the "-" for an unknown 5x count all went with the old rows. Old saved games from before game
      modes/turn timers are no longer read either. Schemas are exported to `app/shared/schemas/`.
      `AndroidAppContainerTest` opens the real Android storage under Robolectric.
- [x] **UI**: every screen moved to Compose Multiplatform unchanged apart from resources
      (`Res.drawable.*`, `Res.font.sora`), `BackHandler` (now `ui/common/BackHandler.kt`, on the
      navigation-event API) and the licences split (common dialog and parsing; the platform supplies
      the JSON and the document view - Android keeps its `TextView`). `verifyAssetSources` covers
      `shared/.../composeResources/`. `MainActivitySmokeTest` drives the real app under Robolectric.
- [x] **iOS side written, compile-checked only**: `IosPlatformServices` (AVAudioPlayer, UIKit
      haptics, CoreMotion, a snackbar for transient messages), storage in Application Support, and
      `MainViewController()` for the Xcode project to host.
- [x] **Release APK size**: 1.93MB before the port, 2.03MB after it. kotlinx.serialization (~49KB,
      for the saved game and licence notices) became `data/Json.kt`, a small strict JSON
      reader/writer with its own tests; kotlinx-datetime (~37KB, for one timestamp pattern) became an
      `expect fun formatTimestamp` on each platform's own formatter; JetBrains' bundled
      `META-INF/.../LICENSE.txt` copies are excluded like AndroidX's. Now 1.95MB - the rest is Compose
      Multiplatform's resources runtime and the platform seams.
- [x] **Lint coverage and clean-ups**: lint covers `app/shared` again (it had silently stopped at
      the module split); the warnings that surfaced are fixed - modifier parameter order, the shake
      effect renamed `ShakeDetectorEffect`, and `DiceTray`/`AchievementsScreen`'s press handling made
      one `awaitEachGesture` each. That last one fixed a real bug: two separate pointer scopes dropped
      a second quick tap - `DiceTrayGestureTest` pins it, and fails on the old code.
- [ ] **Not yet seen on a device** - neither the Android build since the move (Robolectric only)
      nor iOS at all (needs macOS - `IOS_SUPPORT.md` Phase 5).

### Phase 19 — Performance review
- [x] **Idle animations** (`be041a3`): looping clocks (score-tile glow, cup shake, turn-timer flash)
      are composed only while they show something, and the tile glow is applied in a graphics layer -
      they had kept the board recomposing every frame for the whole game. See `UI.md`'s Motion section.
- [x] **Saves conflated** (`5368910`): `InProgressGameRepository` queues saves/clears in its own
      process-lifetime scope and writes only the latest waiting one.
- [x] **Lifecycle-aware collection** (`875d33a`), **no default-style flash on the board**
      (`ac7e6d9`, `GameViewModel.tableSettings`), **style lookups computed once** (`3334dce`).
- [x] **Baseline Profile, hand-written** (`4054fe6`): `app/android/src/main/baseline-prof.txt`
      marks the whole `net.zodac.dicefive` package hot with wildcards (AGP expands them, R8 carries
      them into the release `baseline.prof`). The AndroidX/Compose libraries already ship their own.
- [ ] **Generate a real Baseline Profile on a device** (generated on the sandbox emulator, 2026-10-02;
      the phone before/after in step 2 and the wildcard decision in step 3 are still open). `:app:baselineprofile`
      holds the Macrobenchmark journey (`BaselineProfileGenerator`, steps in `Journeys.kt`: a game
      played and then resumed, Styles, Achievements, Leaderboard, Statistics, Rules, Settings with its
      Licences and About dialogs), `StartupBenchmark` and `StylesBenchmark` (frame times opening
      Styles and swiping its rows), each compared with no compilation against the profile. Needs a
      phone on adb, so it is run by hand:
      1. `./gradlew :app:android:generateBaselineProfile` - writes
         `app/android/src/release/generated/baselineProfiles/baseline-prof.txt`, which is *merged* with
         the hand-written `src/main/baseline-prof.txt`, not replacing it.
      2. `./gradlew :app:baselineprofile:connectedBenchmarkReleaseAndroidTest` for the before/after.
      3. Compare with and without the hand-written wildcard file (it marks all app code hot, so with it
         present the generated profile adds little but startup ordering); if the generated one is as
         good, delete the wildcard file so unvisited code stops being compiled ahead of time.
      `sandbox/emulator.sh start` boots an emulator inside the sandbox, so the journey can be run and
      debugged there without a phone (see `.claude/EMULATOR.md`); generating works on it, benchmarking does
      not mean anything on it.
      While checking the journey itself, cut it short: add
      `-Pandroid.testInstrumentationRunnerArguments.journeyLaps=1` (profile laps) or `...benchmarkIterations=1`
      to the Gradle command, so a failing step stops the run quickly.
      The first full run (15 laps, ~20 minutes) wrote 31k rules (743 app classes); the release build's
      profile went from 13.7k to 17.8k rules with it, and its startup profile now orders the dex. Full
      runs found three races a one-lap run hadn't: a back press or tap sent while a dialog is still
      closing goes to the dying dialog (leaving the app) or is dropped under its fading dim layer, so
      dialogs are left through `closeDialog` and Continue is retried.
      The startup profile is `StartupProfileGenerator`'s alone (cold start to the menu); the journey
      had been marked as startup too, which made `startup-prof.txt` the whole profile and left R8
      nothing to put first. It still holds the style art: `StylesWarmUp` starts on the menu's first
      idle frames, before the profile is captured - which is what a launch really runs. 22k of 31.6k
      rules; the primary dex went from 2.94MB (everything) to 2.17MB (startup code first).
      Regenerate after large UI changes. Selectors are the visible labels and screen-reader
      descriptions, so a relabelled button fails the run (`await` in `Journeys.kt`) rather than
      quietly thinning the profile.
- [x] **Install size**: the release build installed at 6.4MB (15.9MB once its profile is compiled),
      most of it a second, extracted copy of the dex - with `minSdk` 26 the dex is compressed in the
      APK by default. Release builds now store it uncompressed
      (`variant.packaging.dex.useLegacyPackaging`; not debug, whose unminified dex would take its APK
      from 22MB to 70MB), so it runs from the APK: 4.5MB installed (14.0MB compiled), the APK file
      2.5MB -> 4.4MB (Play compresses downloads itself). Also dropped `kotlin/**.kotlin_builtins` and `DebugProbesKt.bin` (13KB).
      Measured and not taken: R8 `-repackageclasses` (identical dex - AGP already does it), and
      dropping the wildcard profile (-1.2MB compiled; waits on step 3 above). What's left is mostly
      library code: Compose 1.6MB of the 3.2MB dex, the app's own 0.7MB.
- [ ] **Benchmark battery use of the remaining always-on animations.** The menu's (and every reading
      page's) drifting watermark dice are now capped at about 30 redraws a second (`DriftState.onFrame`:
      the drift is slow, and the dice still move by the whole time since their last move, so their speed
      is unchanged). Still at the display's full rate (up to 120fps): the Starry background/mat twinkle
      (`TwinkleClock`), the coffee-steam/cauldron cups (`rememberAmbientCycle`, only for players who
      pick them) and the highlighted tile's glow. Whether capping those too is worth it should be measured, not assumed - e.g. a Macrobenchmark
      `FrameTimingMetric`/`PowerMetric` run, or Android Studio's Power Profiler, on a real 120Hz phone.
      Dice rolls, cup shakes and hold/unhold stay at full rate regardless. The logo pupils' sensor is read
      20 times a second (`PULL_SAMPLES_PER_SECOND`, smoothing adjusted to match), not 50.
- [ ] **Not fixed: AboutLibraries resolves configurations while Gradle plans the build** (the
      "`debugCompileClasspath` was resolved during configuration time" CI warning). It's the plugin's
      own task inputs (`BaseAboutLibrariesTask`), on its latest version (15.2.0); nothing in our
      script triggers it, and the classpaths are resolved during the build anyway. Revisit on a
      plugin upgrade.

### Phase 20 — Game mode: Quickfire
- [x] **Rules** (beyond the official rules): Standard's dice, card and scoring, but `rollsPerTurn = 1`
      (no rerolls) and a fixed 10-second turn timer. Same 1575 ceiling as Standard. Engine, AI,
      board and persistence needed no change for the one roll - they already read it from the mode.
- [x] **The timer** is a new `GameMode` field, `turnTimerSeconds` (null for every other mode):
      `GameState.turnSeconds` resolves "the mode's timer, else the setup pick", and
      `GameViewModel.syncTurnTimer` counts down from it.
- [x] **Timeouts score the lowest box**: a timeout rolls if needed as it always has, then scores the
      open category the dice are worth *least* in, the first in scorecard order winning a tie.
      Other modes still take the first open category. This is `GameMode.timeoutPick`
      (`TimeoutPick.LOWEST_SCORE` vs the default `FIRST_OPEN`), applied by
      `ScoreCalculator.timeoutCategory`, and still limited to the joker rule's
      `availableCategories`.
- [x] **Automatic roll** (`GameMode.autoRollAtTurnStart`): a human's Quickfire turn starts with the
      cup tapped for them. `GameScreen` calls its own `onCupTap` when `GameState.awaitsAutoRoll`
      goes true, so the shake, sound and roll are exactly a manual tap's. There's no separate
      view-model roll; one was built with its own 600ms shake and replaced on review, so auto-rolls
      and real rolls stay the same thing. AI turns are unchanged: their loop already starts with a
      roll. `GameViewModel.rollDice` now ignores a roll with no rolls left, which an Undo during
      the shake could otherwise trigger (it threw from `GameEngine.rollDice`).
- [x] **Every roll shares one path, in every mode**: a CPU's roll now shakes for the same
      `CUP_SHAKE_MILLIS` (420ms, was 600) as a tap, and lands through the same
      `GameViewModel.performRoll` as a human's `rollDice`. The CPU still plays in the view model,
      not through the screen's tap: routing it through the screen would stall CPU turns whenever
      the screen isn't showing (and time them out, with a timer), and make the AI loop wait on the
      UI. The CPU's 600ms pause before scoring (`AI_STEP_DELAY_MS`) is unchanged.
- [x] **New Game screen**: Quickfire appears in the Game Mode radio group from `GameMode.entries`.
      The Turn Timer row is disabled while it's picked (`SegmentedChoiceRow` gained `enabled`); the
      player's pick is kept and saved, and the game starts with `TurnTimer.NONE`.
- [x] **Rules page**: "Mode: Quickfire", last in `RULES_PAGES`.
- [x] **Achievements** (Game Modes, after Tricolour's): `QUICKFIRE_WIN` "Quick On The Draw" (win,
      multiplayer) and `QUICKFIRE_BEAT_THE_CLOCK` "Beat The Clock" (finish without player 1's timer
      ever running out - first a `playerOneTimedOut` flag, now `GameAchievementContext
      .playerOneTimeouts == 0`, see Phase 21). Impatient, Naturally Gifted and Almost Famous are
      guarded so a one-roll mode can't give them away; six reroll-based ones simply can't be earned
      in it. The full list is in `.claude/GAME_MODES.md`.
- [x] **`.claude/GAME_MODES.md`**: how to add a mode (including ones unlike these), where each mode
      field is read, the turn-flow rules learned here, and the per-mode achievement audit.
- [x] Tests: `GameScreenAutoRollTest` (Robolectric: Quickfire rolls without a tap, Standard waits, a
      human/CPU game goes round), `GameModeTest`, `ScoreCalculatorTest` (timeout pick: lowest, tie
      order, joker-forced box, Standard unchanged), `AchievementEngineTest`, `GameViewModelTest`,
      `GameAchievementsWiringTest`.
- [ ] **Not yet seen on a device**: the disabled Turn Timer row, the 10s countdown and the automatic
      roll.

### Phase 21 — Luck Of The Draw
- [x] `LUCK_OF_THE_DRAW` "Luck Of The Draw" (Miscellaneous, so hidden like the rest of that category):
      win a game against at least one opponent having scored 3 or fewer categories yourself, with
      the turn timer scoring the rest. Not tied to a mode: any game with a timer (the Turn Timer
      setting, or Quickfire's own) can earn it, measured against player 1's own card size (13 boxes,
      or 17 in Tricolour, so 10 or 14 timeouts). `GameAchievementContext.playerOneTimeouts` counts
      player 1's timed-out turns, replacing the old `playerOneTimedOut` flag (Beat The Clock now
      checks it's 0). A timed-out score can't be undone, so the count never has to go back down.
      Like every per-game counter, a resumed game starts it from zero, which can only make this
      harder to earn, never easier.
- [x] Tests: `AchievementEngineTest` (threshold, win needed, every mode against its own card) and a
      `GameAchievementsWiringTest` game played through the view model with every player-1 turn
      timed out.

### Phase 22 — Rules as a screen
- [x] **Why**: Rules was the one menu destination that opened a modal rather than a screen, and the
      reason for a modal (Phase 16: nothing needs to deep-link to it) mattered less than what it
      cost - a box at ~92%/82% of the screen for the app's longest reading, and a row of dots that
      grows with every game mode (seven pages meant six swipes to reach the last, with nothing to
      say which page was which).
- [x] `ui/common/RulesDialog.kt` became `ui/rules/RulesScreen.kt`, a `Screen.RULES` destination in
      `DiceFiveNavHost` inside the shared `ScreenScaffold` (backdrop, "Rules" app bar, back arrow,
      content fade-in), so it opens and closes like every other menu entry; `MenuScreen`'s
      `showRulesDialog` flag gave way to an `onRules` callback.
- [x] **Tabs, not dots**: a `PrimaryScrollableTabRow` (transparent over the backdrop, no edge
      padding so it lines up with the page text) above the same `HorizontalPager` - swipe or tap a
      tab to jump straight to a page, and TalkBack gets real tabs. Each `RulesPage` gained a
      `tabLabel` ("Upper Section", "5x & Joker", "Quickfire"...) shorter than its page heading, so
      more than a couple of tabs fit on a phone. The previous/next arrows went with the dots.
- [x] **Showing there's more**: seen on a device, the first three tabs ended flush with the edge, so
      the row looked complete. `fadeOffscreenEdges` fades whichever end has tabs beyond it (48dp,
      deepening with the distance left to scroll, so it eases away rather than popping off at the
      end) - but on its own that failed too: with a middle tab selected and centred, the next label
      can start just past the edge, so the fade sat over empty space and hinted at nothing. So each
      end with more beyond it also gets a `TabScrollChevron` (gold, 48dp, over the fade, eased by
      the same distance-left strength), which is there whatever the labels' widths. Under a chevron
      the tabs are hidden outright (`TAB_EDGE_CLEAR`, 40dp - the glyph ends 36dp in), then fade
      back in over 24dp, so the glyph is never drawn over a label: a plain gradient left text
      half-visible behind it. A tap scrolls
      the row 60% of its width - tabs, not the page. It's kept out of TalkBack, since the tabs are
      already stops with their own "x of 7". The TalkBack equivalent: stock `Tab`s are only `Role.Tab` in a selectable group, so
      nothing said how many there were - the row now carries `CollectionInfo` and each tab its
      `CollectionItemInfo`, so it's announced as "Tab, 1 of 7". `RulesScreenAccessibilityTest`
      (Robolectric) pins the count, each tab's position, and that an off-screen tab scrolls into view
      and opens its page, that the chevrons add no TalkBack stops, and that tapping the end chevron
      scrolls the tabs without changing page.
- [x] **A "1 of 7" footer** (`PageCountFooter`) alongside the chevrons: they say there's more, it
      says where you are and how much. Kept small and given priority: a `labelMedium` gold pill
      (`primary` behind `onPrimary`, the filled button's pair), pinned to the bottom and drawn *over* the pages rather than taking a
      row of its own, so longer text scrolls behind it. Each page is padded at the bottom by the
      pill's measured height (so a large font still clears it) plus 8dp, so its last line can always
      scroll above it. A plain clipped background, not a `Surface`, which would swallow touches and
      stop a scroll that starts on the pill. TalkBack hears "Page 1 of 7", and it's a polite live
      region so a swipe to another page is announced - the pager says nothing on its own. Pinned in
      `RulesScreenAccessibilityTest`, including that a long page's last line scrolls clear of it
      (checked to fail without the padding).
- [ ] **Known limit**: TalkBack scrolling a focused line into view only brings it inside the scroll
      area, which runs on under the pill, so a line near the bottom can sit partly behind it. The
      pill is ~60dp wide and centred, so most of the line stays visible. The fix would be a
      `BringIntoViewSpec` that knows about the pill - not done.
- [x] `assembleDebug`, `testDebugUnitTest`, `compileDebugAndroidTestKotlin` and `lint` green.
- [ ] **Not yet seen on a device**: the footer's live-region announcement, the chevrons, and the "1 of 7" announcement under real TalkBack.

### Phase 23 — Hard CPU review
- [x] **Why**: seen in play, Hard threw away low sets (4-6-1-1-1 kept the 4 and 6; 2-2-2-6-5 kept
      the 6 and 5), rarely chased 5x, and almost never earned the upper bonus. Three causes: it only
      looked one reroll ahead even with two left; it valued a hand by raw points, so loose high dice
      beat three low ones; and its category choice (score over baseline) and its holds (raw points)
      disagreed about what a hand was worth.
- [x] **Whole-turn search**: `HandValues` now plans through every reroll left - a hold's value is
      the average over every outcome of the best hold of the hand after it, down to the last roll -
      with each hand and held set valued once per decision. Standard: 252 hands, ~1-30ms a decision
      on the JVM. Tricolour (18 faces, 26,334 hands) stays one reroll ahead (`maxLookahead`), at
      about its old cost; two ahead took ~0.1-0.8s a decision, too long for the pause after a roll.
- [x] **One valuation** (`HardValuation`) for holds and category choice: the score (with any 5x
      bonus chip) less the box's baseline, plus `(score - 3 x face) x 35/63` in the upper section
      while the bonus is still open and reachable. The baseline changed from a box's average on one
      random roll to its average when a whole turn chases it (Large Straight ~1 -> 10.4, Chance 17.5
      -> 23.3, 5x 0.02 -> 2.3): with single-roll baselines every straight looked like ~39 points'
      profit and Hard chased them from a lone 4. Every box scores on numbers or on colours, never
      both, so each baseline is solved over six number faces or three colour faces - cheap even for
      Tricolour.
- [x] **Measured** (`AiTurnPlayer.playTurn`, solo, seeded): Standard Hard 217.9 -> 238.9 over 400
      games (Medium 190.5), upper bonus in 2% -> 27% of games; Tricolour 324 -> 350 over 40 (noisy);
      Quickfire unchanged (125, no rerolls to plan). A 5x "future bonus chips" term was tried at two
      strengths and moved the average within the noise (+0.7, +1.9 over 1,000 games), so it was left
      out. `AiTurnPlayerTest` pins the 2-2-2-6-5 hold and a seeded 200-game average >= 230 (old Hard
      averaged 219 on those games, new 239).
- [ ] **Step 2 - a perfect-play table (not built; measured only)**: the value of every start-of-turn
      state (filled boxes, upper subtotal capped at 63, 5x box scored or not) under optimal play,
      looked up by the whole-turn search as each finished hand's future. Measured with a throwaway
      prototype on the game's own scoring code:
      - Standard and Quickfire: 536,448 reachable states each - 2.15 MB as float32, 1.07 MB as
        float16 (the debug APK is ~22 MB).
      - Tricolour: 8,583,168 states (34 MB / 17 MB), and each state's turn is the 26,334-hand
        search above - out of reach exactly; it would need an approximation of its own.
      - Generation: a throwaway array-based solver (precomputed roll transitions; its scoring checked
        against `ScoreCalculator`, joker rule included, on 3,000 random cases) solved all of
        Standard in 14s on one core of the sandbox. (A HashMap-based first try took ~9ms a state, ~80
        min.) Its turn search took 0.05-0.08ms, against 1-30ms for `HandValues`' HashMaps.
      - Strength: it gives perfect solo play an expected 254.5 (Verhoeff's published figure for these
        rules - 13 boxes, 35 at 63, 100 per extra 5x, joker - is 254.6); played over 4,000 seeded
        games it averaged 253.4 +/- 0.9, against Hard's 238.9 on the same games.
      - **Hybrid (store the most common X% of states, estimate the rest)**: a missing state's value
        can't be worked out on the fly - it depends on every state after it, which is the whole
        solve - so a miss falls back to Hard's own measure, as an estimate of the rest of the game
        (each open box's baseline, plus the upper-bonus term, plus the average gap to the true value
        for that many open boxes). States ranked by visits over 20,000 perfect games (60,814
        distinct states came up - 11% of them); 4,000 seeded games each:

        | Stored | States | Avg score | Lookup hits | Size (sparse, 6 B/entry) |
        |---|---|---|---|---|
        | 0% | 0 | 238.9 | 0% | 0 |
        | 1% | 5,364 | 238.9 | 23% | 0.03 MB |
        | 5% | 26,822 | 238.4 | 32% | 0.16 MB |
        | 10% | 53,644 | 237.7 | 37% | 0.32 MB |
        | 20% | 107,289 | 244.9 | 77% | 0.64 MB |
        | 35% | 187,756 | 248.3 | 84% | 1.13 MB |
        | 50% | 268,224 | 249.6 | 89% | 1.61 MB |
        | 100% | 536,448 | 253.4 | 100% | 1.07 MB dense, float16 |

        Below 20% it gains nothing: a decision compares the states after each candidate box, most
        of them off the common path, so exact values for a few sit beside estimates for the rest.
        From ~35% up a sparse table (keys and values) is as big as the whole table stored densely,
        and still weaker. And every stored value still needs the full solve, so a partial table saves
        no generation - for Tricolour, whose problem is generation (and 17 MB), it doesn't help.
      - Costs: a generator, the table as a bundled resource for `commonMain`, a test that fails
        when the rules no longer match the table, and one table per mode.

