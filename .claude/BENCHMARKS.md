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
  - Bestagon and Pyramid are cheaper: they spin one flat face.
- **Cups, backgrounds, mats** (board plus tray): 2-4 a frame in idle, shake and toss for every one, within ~20% of each
  other. A cup alone is 0.8-1.7.
  - A patterned mat's or background's first frame is 17-55. Stone stamps three textured flags to stay there.
  - Treasure Chest, Urn, Volcano and Picnic Basket are within a few ms of Classic Gold.
- **Live per frame**, for moving art:
  - glitter: 2 sparkles a die, 6 on its mat, 5 on its background, 2 on its cup;
  - neon strokes;
  - the Honeycomb bee (~40 shapes);
  - the Volcano's crater, 5 lava ribbons and eruption;
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
- **The tray's per-frame work.**
  - Each tossed die's position is read in composition (`toss.value`), so `ScatterArea` and `TossedCube` recompose with
    `offset(x, y)` every frame.
  - `GroundShadow` rebuilds its `Outline` and draws it four times.
  - Reading these in layout or draw (`Modifier.offset { }`, `graphicsLayer { }`) would cut most of it. That's a sizeable
    rework of `DiceTray.kt`/`RollingDie.kt`.
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
