# DiceFive: frame cost

The patterns that keep frames cheap, what the art costs today, and what's still open. Read this for a reported
stutter, or before adding art that draws every frame. **How to measure** is the `benchmark` skill.

The short version: **keep per-frame work out of what a toss or shake redraws.** The cup's shake and tip is where a
player notices first.

## Patterns that keep frames cheap

Each of these fixed a reported stutter. New work must keep them.

- **Paint costly, unchanging surfaces once.**
  - `drawCachedSurface(key)` (`Patterns.kt`) paints once per key and size into an `ImageBitmap`, then stamps it. It's
    an LRU of 128, ~90KB each at die size. The key captures everything except size. Marble and Frosted cost 10x more
    when repainted every frame.
  - A tossed die's faces are recorded once into `GraphicsLayer`s (`FaceLayers`, `RollingDie.kt`); only shading and
    perspective change per frame.
- **Paint very heavy art off the frame.** The Treasure Chest pile settles and paints on a background thread
  (`rememberHoardArt`), with a stand-in until it's ready. Painting it on the frame hitched ~160ms. Draw tilted discs as
  rotated ovals.
- **Hold art still where nobody sees it move.**
  - A tumbling face is frozen (`LocalArtFrozen`), except the landing face in its last quarter-turn.
  - Off-screen Styles tiles get `LocalReduceMotion`.
  - Ambient loops obey the Animations levels (`UI.md`).
- **Animate opacity or tint, not geometry.** Neon paints its tube once and pulses the alpha. RGB paints once in white
  and tints (`ColorFilter.tint(.., SrcIn)`). A tinted image per lamp is slower than a small gradient.
- **Don't rebuild nodes on a state change.**
  - One `Moving(...)` call site takes either move's arguments, so landing doesn't rebuild every die.
  - A turn's first dice are composed off the mat during the shake.
  - The cup starts pouring `CUP_POUR_LEAD_MILLIS` (64ms) before the dice land.
- **Don't re-lay out unchanged text.**
  - Score cells keep their number laid out and swap visibility with "-" (`CategoryCell`, `SlotScore`, `ShownScore`
    compares by value).
  - `FontFitCache` keeps each label's chosen size.
  - Styles tile names share one `TextMeasurer` (`rememberTileLabelMeasurer`).
  - `D20DiceStyle` shares one measurer, with no colour in the measured style.
  - `formatList`, `isRightToLeft` and the number formats are cached per language.
- **Read changing state where it's used.**
  - The Rules screen reads the current page only where it's shown (`derivedStateOf`), and remembers its tab modifiers.
  - The turn timer's seconds are read only by its badge (`RunningTurnTimer`): a tick recomposes the badge, not the
    board.
  - Settings, achievements and the saved-game flag emit only when their own value changes (`distinctUntilChanged`).
- **Load ahead of the first use.** The sound clips load a second after launch, off the main thread, not on a game's
  first roll.
- **The Styles page.**
  - Each tile's preview is drawn in its own offscreen layer.
  - Rows are plain `Row`s built deterministically: the tiles on screen at open, then one every `TILE_BUILD_FRAMES`
    frames, nearest the pick first, with unbuilt runs as one `PlaceholderRun` `Canvas`. A `LazyLayoutCacheWindow` built
    nothing ahead on a device.
  - `StylesWarmUp` pre-draws on the menu's idle frames (`UI_SCREENS.md`).
- **The Rules screen.**
  - A tab snaps to its page (`scrollToPage`) without building the pages between.
  - A page builds its first screenful, then the rest over the following frames (`rememberShownBlocks`).
  - Tab rows never re-centre (`KeepTabInView`).

## Current costs

Desktop JVM, warm, in ms. These are relative only: compare within a run.

- **Dice, seven at a time** (row harness, median of three runs, p95):
  - At rest, no style is over 10: Tally ~9, Glitter ~8.6, RGB ~8, Classic ~6.5, blank 3-4.
  - Tumbling, every style is over 10: Classic ~12, the heaviest ~16, blank ~5.
  - Composition and layout are under 1ms; the rest is drawing 14 face layers under perspective. That's probably
    overstated versus a device's textured quads (unmeasured).
  - Bestagon is cheaper: it spins one flat face.
- **Cups, backgrounds, mats** (board plus tray): 2-4 a frame in idle, shake and toss for every one, within ~20% of each
  other. A cup alone is 0.8-1.7.
  - A patterned mat's or background's first frame is 17-55. Stone stamps three textured flags to stay there.
  - The Stone, Meadow and Circuit backgrounds are painted once and sit within ~3.5ms of the plain Classic one (whole
    screen, captured: 25-26ms against 22), level with Pinstripe and Starry. Neon is ~29ms, level with
    Honeycomb and under Glitter and Glitch (32-34ms).
  - Treasure Chest, Urn, Volcano and Picnic Basket are within a few ms of Classic Gold.
  - A cup alone, 110x96dp (median of three runs, p95): the Fishbowl is ~5.8 in idle, ~6.1 shaking, ~6.2 pouring, against
    Cauldron ~5.2, Top Hat ~4.5 and Classic Gold ~4.2 in idle, and Treasure Chest ~6.3 shaking and ~8.4 pouring.
    Measured with a painted stone bed; it now draws three rocks live and its fish as a turning solid (unmeasured).
  - A background alone, 411x560dp (p95): Ocean Waves is the dearest, ~28-34 against Glitch ~25-32, Glitter ~23-25 and
    Neon ~21. Its seven moving gradient bands are ~5 of that: without them it measured ~22, level with Glitch. Ocean
    Lagoon is painted once and sits with Neon; the Ocean mats sit with Classic and Stone.
- **Score frames** (one draw of the active tab's frame, 3x, desktop JVM, median ms): a plain ring 0.03-0.06; Neon,
  Pixel, Greek Key and the tribal and floral ones 0.1-0.5; Celtic Knot 0.62. Drawn live, the wreath was 0.61, Rope 1.08,
  Chain 0.90, and Starry Sparkles, Constellation and Marquee Bulbs 0.54-0.58. Those nine are `painted` and cost 0.013.
- **Live per frame**, for moving art:
  - glitter: 2 sparkles a die, 6 on its mat, 5 on its background, 2 on its cup;
  - neon strokes, and the Neon background's horizon glow (one gradient rect; its grid is painted once);
  - the Honeycomb bee (~40 shapes);
  - the Volcano's crater, 5 lava ribbons and eruption;
  - the Fishbowl's water, clownfish (body, 3 bands of up to 16 pieces, fins, eyes), 5 fronds, 3 rocks and 3 bubbles;
  - the Ocean Waves background's 7 crest lines and the gradient band under each;
  - the Basket's lids and food;
  - the Chest's lid, top coins and spill;
  - glitch bands only during a burst.
- **Game screen.** A landing frame is 3-6 (~17 for the first of a session). A reroll's tap frame is 3.5-8. Third Wind
  stacks add 0.15-0.4 a frame and ~2 to the tap. No score line recomposes on shake or toss frames.
- **Pages.** Styles opens at ~42-44 the first time after launch, then 16-24. A tile scrolling in costs ~0.6. Settings'
  first frame is ~26-30. A Rules tap frame is ~6-8.

A debug build on a phone is several times slower than any of this. Judge on a release build (R8 plus the Baseline
Profile).

## Still on the table

- **The tumble.** ~5-6ms with no face layers drawn; drawing them is the other 6-10. Untried: snapshot each face to an
  `ImageBitmap` (`GraphicsLayer.toImageBitmap`). The cost is that animated styles freeze through the 0.9s toss.
- **The ground shadow.** `GroundShadow` (`DiceTray.kt`) rebuilds its `Outline` and draws it four times each frame a die
  moves. Untried, and unmeasured on its own.
- **Dice switching mode** rebuilds each die's nodes twice a roll. Drawing the pick-up with the resting composable and
  switching to `Moving` once off the mat would hide the first rebuild in the shake.
- **Always-on animations' battery cost.** The menu drift is capped at ~30fps. Starry, steam, cauldron and the tile glow
  run at the display rate at High. Measure on a real 120Hz phone (Macrobenchmark `PowerMetric`, or Android Studio's
  Power Profiler) before capping.
- **Baseline Profile benchmarks on a phone.**
  - Measure the generated profile's before and after
    (`./gradlew :app:baselineprofile:connectedBenchmarkReleaseAndroidTest`).
  - Decide whether the hand-written wildcard `app/android/src/main/baseline-prof.txt` (all app code hot, ~1.2MB
    compiled) can go.
  - Emulator numbers mean nothing here.

## Tried and dropped

- **Reading the toss in layout and drawing, not composition** (2026-10). Built, verified and discarded.
  - **The idea.** A tossed die's pose was read in composition, so each moving die (`Moving`) and its `TossedCube`
    recomposed every frame. Reading the pose only where the die and its shadow are placed and drawn, with the cube
    rebuilt only as a face comes into or leaves view, would avoid that.
  - **What it changed.** Per toss (five dice), `Moving` went from ~275 recompositions to 5 and `TossedCube` from ~265 to
    ~30. `ScatterArea` itself already recomposed only once per die per toss.
  - **What it saved.** Toss frames' compose and layout went from p50 ~0.90ms to ~0.85ms, p95 ~1.83ms to ~1.73ms (whole
    game screen, two runs each). That's within run-to-run noise, and drawing, the bulk of a toss frame, was unchanged.
    Each recomposition removed cost microseconds.
  - **Why dropped.** No measurable gain, at the price of trickier code. A slower phone slows every part of the frame by
    a similar factor, so the share saved stays ~5% of compose and layout. A toss's cost is in drawing its face layers
    (see "The tumble" above).
  - **The trap it found.** Pose readers passed as references to local functions (`::x`) compare equal whatever state they
    capture, so Compose skipped the shadow and it kept the pick-up's pose: moving dice lost their shadows. The test suite
    passed; only a frame-by-frame pixel diff of the tray (every dice style, Tricolour, D20, Stud, Unlucky; ~32,000 frames)
    caught it. Pass lambdas, not references, to a skippable composable.
