# DiceFive: frame-cost benchmarks

How the game's animation cost is measured, what it costs today, the patterns that keep it down, and what's still
open. Read this
when a player reports a stutter, lag or slowdown, or before adding art that draws every frame (a
new dice, cup, mat or background style, or anything animated). It isn't needed for everyday UI
work.

The short version: **keep per-frame work out of what a toss or shake redraws.** A die face, cup or
tile that re-runs expensive drawing code on every frame costs the whole table its frame rate, and
the cup's shake and tip is where a player notices first.

---

## How to measure

None of this needs a device: Robolectric renders for real with native graphics (every unit test's - see
`app/android/src/test/resources/robolectric.properties`), and
`captureToImage()` forces a frame to be drawn. The benchmarks were throwaway tests in
`app/android/src/test/kotlin/net/zodac/dicefive/`, run with
`./gradlew :app:android:testDebugUnitTest --tests '*SomethingScratch*'`, printing to the JUnit XML's
`system-out` (`app/android/build/test-results/testDebugUnitTest/TEST-*.xml`). They aren't kept:
they take minutes and prove nothing on their own. Recreate them from the pieces below.

### The frame loop

```kotlin
@RunWith(AndroidJUnit4::class)
class SomethingScratch {
    @get:Rule val compose = createComposeRule()

    // One frame, split into its phases: coroutines/delays on the main looper, composition +
    // layout, then draw (plus the capture itself).
    private fun frame(): Triple<Double, Double, Double> {
        val a = System.nanoTime()
        ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)   // the game's delays run on this
        val b = System.nanoTime()
        compose.mainClock.advanceTimeByFrame()                    // compose + layout
        val c = System.nanoTime()
        compose.onRoot().captureToImage()                          // draw
        val d = System.nanoTime()
        return Triple((b - a) / 1e6, (c - b) / 1e6, (d - c) / 1e6)
    }
}
```

With `compose.mainClock.autoAdvance = false` set before `setContent`, each `frame()` is exactly one
frame. Change state between frames with
`compose.runOnIdle { x = y; Snapshot.sendApplyNotifications() }` - without the apply notification
the change isn't seen until much later, and without `runOnIdle` it can be missed entirely.

### Set-ups used

- **The whole game screen** - `GameViewModel()`, `setPlayerCount(1)`, `startGame()`, then
  `GameScreen(viewModel)` inside `LocalPlatformServices provides SilentPlatformServices` and
  `DiceFiveTheme`. Roll by `onNodeWithContentDescription("Dice cup", substring = true)
  .performSemanticsAction(SemanticsActions.OnClick)`; score with `viewModel.commitScore(...)` once
  `rollsRemaining == 0`. With `CUP_SHAKE_MILLIS` 420, the landing frame is the 27th after the tap.
- **Board plus tray, with any table art** - `GameBoard(state, rolling, ...)` over
  `DiceTray(dice, ..., rolling)` in a 411x780dp `Column`, under
  `LocalGameVisualTheme provides GameVisualTheme(background = ...)` (or `mat`, `diceCupStyle`);
  `state` from a view model after one `rollDice()`. Flip `rolling` to walk through idle (20
  frames), shake (26) and toss (40), per style from `TableBackgrounds.all`, `DiceMats.all`,
  `DiceCupStyles.all`.
- **Seven dice, one style at a time (the reference for dice)** - a `Row` of seven 54dp dice (`LocalDieIndex` 0-6), **only
  that row captured** (`onNodeWithTag(...).captureToImage()`), 80 frames at rest and 120 tumbling (`style.TossedDie`,
  `roll` stepped 0 to 3.9 and round), against a `_blank` style whose `Die` is an empty `Box`. Capturing the whole screen
  or the whole tray puts a 9-12ms floor under every style and buries the differences.
- **Tossed dice, one style at a time** - five `style.TossedDie(roll = r + i * 0.1f, finalTurns = 3,
  ring = listOf(1, 2, 6, 5), modifier = Modifier.size(48.dp))`, each under
  `LocalDieIndex provides i`, advancing `r` by 0.1 per frame, for every style in `DiceStyles.all`.
- **The Styles page** - `StylesScreen(StylesViewModel(savedStyles = flow))` where the flow's
  `SavedStyles` has `AchievementsState(unlockedAt = Achievement.entries.associateWith { 1L })`, so
  every style (secret ones too) is on the page; run with
  `@Config(qualifiers = "w411dp-h891dp-xxhdpi")` for a phone-sized screen. Scroll a row with its
  `SemanticsActions.ScrollToIndex` (one tile a frame) - `ScrollBy` does nothing on these rows.

- **Many more styles than exist (a stress catalog)** - to see how a page scales before the art is
  drawn. A throwaway file in `ui/game/style` with a function that repeats each `StyleFamily` N times,
  each copy renamed ("Marble 2" - the rows key on the name) and each colour's style wrapped in a
  delegating copy with its own id (`class Copied(override val id: String, base: DiceStyle) :
  DiceStyle by base`, one per art interface). Apply it in `StyleCatalog`'s constructor (make
  `families` a plain parameter and `val families = copies(families, n)`). Copies get their own tile,
  layer, label and place in the build order, but share the original's cached surfaces (marble, frost
  - `drawCachedSurface` keys on the pattern) and decoded mat textures, so they understate what that
  many genuinely new styles would cost the first time. Switch it off (N = 1) before running the
  suite - tests count the real catalog. Never commit it.
- **Opening a page, frame by frame** - `setContent { key(generation) { if (generation % 2 == 1)
  StylesScreen(...) } }` with `mainClock.autoAdvance = false`; bump `generation` to open or close the
  page, then time each of the next ~40 `advanceTimeByFrame()` + `waitForIdle()` pairs, and between
  openings advance a few seconds so the build-ahead finishes. The first opening is cold (~400ms);
  compare the later ones. Frame 1 is the page's first composition; the ones after show the
  build-ahead.

### Finding what the time is spent on

- **A sampling profiler in the test**: a daemon thread reading `mainThread.stackTrace` every
  0.2ms while a flag is set around the frames of interest, counting the innermost `net.zodac`
  frame (what app code is running) or every frame on the stack (which Compose phase:
  `performRecompose`, `measureAndLayout`, `applyChanges`, `layoutText`, `composeInitial`...).
  Samples with no `net.zodac` frame at all are Compose's own work (creating nodes, laying them
  out); for those, count the innermost frame whose class names `Text` or `Paragraph` - text layout
  hides there, under whatever composable asked for it. Keep the sampled state in top-level
  `@Volatile` vars, declared above the test class's annotations.
- **Which text re-lays out**: temporarily add `onTextLayout = { println("TL file:line " +
  it.layoutInput.text) }` to the `Text`s involved, and print markers around the frame in question.
  Callbacks for a frame's layouts arrive after it, so read the block following each marker.
- **Recording vs replaying**: capture the same frame twice. The second capture has nothing new to
  draw, so it's the harness's replay cost alone; if it's as slow as the first, the time isn't the
  app's.

### Reading the numbers

- **They're relative, not a phone's.** A desktop JVM is several times faster than a phone, and the
  user installs debug builds, which are slower again. What matters is how a frame compares with the
  frames either side of it and with other styles.
- **Warm up first.** The first time any code runs it's JIT-compiled: a roll's first landing measured
  ~36ms against ~5ms for the tenth. Take the minimum of several passes after a throwaway one, and
  report the cold number separately.
- **Capture cost scales with the screen.** An empty screen at 411x891dp xxhdpi costs ~10ms to
  capture, and a full Styles page ~43ms even with nothing to redraw (see "Recording vs
  replaying"). Compare like with like.
- **Canvas drawing only runs in NATIVE graphics mode.** Without it, a test that reads values inside
  a `Canvas { }` block sees nothing - test composition-side values (for example
  `rememberCupPose`) instead.

- **Percentiles, not averages.** Take p50/p90/p95/max over every frame; a mean or a best-of hides the frames a player
  sees. Judge a style by **p95**.
- **One full-catalogue run is noise** on a shared host (the same style can swing 7.5 to 14.9ms p95 between runs). Run
  three times and take each style's median, and compare only within a run. Pass the styles to measure in an
  environment variable and use `--rerun`, or Gradle serves the last results from cache.
- **Robolectric doesn't model the GPU.** Drawing cost on a device - the HWUI profile bars, or
  `adb shell dumpsys gfxinfo <package>` - is the measure for that; a device redraws the whole window whenever anything
  in it moves.

---

## Patterns that keep frames cheap

Each of these fixed a reported stutter. New work must keep them.

- **Paint costly, unchanging surfaces once**: `drawCachedSurface(key)` in `Patterns.kt` paints a pattern once per key
  and size into an `ImageBitmap` and stamps it after (an LRU of 128, ~90KB an image at die size). The key says
  everything the painter depends on except size. Marble and Frosted cost 10x more repainting every frame. A tossed die's
  faces are recorded once into their own `GraphicsLayer` (`FaceLayers` in `RollingDie.kt`) and only shaded and drawn
  under the perspective each frame.
- **Paint very heavy art off the frame**: the Treasure Chest's pile settles and paints on a background thread
  (`rememberHoardArt`), with a stand-in until it's ready - done on the frame, resuming a game with it open hitched
  ~160ms. Draw tilted discs as rotated ovals, not projected polygons.
- **Hold art still where nobody sees it move**: a tumbling face is frozen (`LocalArtFrozen`) except the landing face in
  its last quarter-turn; a Styles tile built but off screen gets `LocalReduceMotion`; decoration that loops while
  nothing happens obeys reduced motion and the animation levels (`UI.md`).
- **Animate opacity or tint, not geometry**: Neon paints its tube once and pulses the image's alpha; RGB paints its
  light once in white and tints it (`ColorFilter.tint(.., SrcIn)`); a tinted image per lamp is slower than a small
  gradient, so lenses and sockets stay plain circles.
- **Don't rebuild nodes on a state change**: one `Moving(...)` call site takes either move's arguments, so landing
  doesn't dispose and rebuild every die; a turn's first dice are composed off the mat during the shake; the cup starts
  pouring `CUP_POUR_LEAD_MILLIS` (64ms) before the dice land, so the tip is under way before the landing frame.
- **Don't re-lay out text that hasn't changed**: a score cell keeps its number laid out and only swaps its visibility
  with "-" (`CategoryCell`, `SlotScore`, `ShownScore` comparing by value); `FontFitCache` (`ShrinkThenWrapText.kt`)
  keeps each label's chosen size; the Styles tiles' names go through one shared `TextMeasurer`
  (`rememberTileLabelMeasurer`, `TileLabel`); `D20DiceStyle` holds one 32-layout measurer for all its dice with no colour
  in the measured style; `formatList`, `isRightToLeft` and the number formats are kept per language.
- **Read changing state where it's used**: the Rules screen reads the current page only where it's shown (through
  `derivedStateOf`) and remembers its tab modifiers, so a page change doesn't rebuild both tab rows.
- **The Styles page**: every tile's preview is drawn in its own offscreen layer (a still tile costs next to nothing,
  scrolling moves layers); rows are plain scrolling `Row`s built deterministically - the tiles on screen at open, then
  one every `TILE_BUILD_FRAMES` frames nearest the pick first, unbuilt runs as one `PlaceholderRun` `Canvas` - because a
  `LazyLayoutCacheWindow` built nothing ahead on a device. `StylesWarmUp` draws every tile, then each category's real
  card, then the whole screen, on the menu's idle frames, so the first open after a launch is warm.
- **The Rules screen**: a tab snaps to its page (`scrollToPage`) rather than building every page between; a page builds
  its first screenful at once and the rest over the next frames (`rememberShownBlocks`); tab rows never re-centre
  themselves (`KeepTabInView` moves one only when the selected tab is hidden), as a scrolling row swallows a tap made
  during its own scroll.

## Current costs

Desktop JVM, warm, ms - relative only; compare within a run.

- **Dice, seven at a time (row harness, median of three runs, p95)**: at rest, no style over 10 (highest Tally ~9,
  Glitter ~8.6, RGB ~8, Classic ivory ~6.5, the blank die 3-4). Tumbling, every style is over 10 - Classic ivory ~12,
  the heaviest ~16, the blank die ~5. Composition and layout are under 1ms of that; the rest is drawing 14 face layers
  under perspective, which on a device is a textured quad per layer, so probably overstated here (not measured on a
  device). Bestagon and Pyramid toss cheaper than a cube: they spin one flat face.
- **Cups, backgrounds and mats**: board plus tray, idle/shake/toss 2-4 a frame for every one, within ~20% of each other;
  a cup alone 0.8-1.7. A patterned mat or background's first frame (its pattern painted) is 17-55 (Stone uses three
  textured flags stamped across the floor to stay there); after that every one costs the same as a plain one. The
  Treasure Chest, Urn, Volcano and Picnic Basket are within a few ms of the Classic Gold cup shaking or opening.
- **Live per frame**, for art that moves: glitter 2 sparkles a die, 6 on its mat, 5 on its background, 2 on its cup; a
  neon tube's strokes; the Honeycomb mat's bee (~40 shapes); the Volcano's crater, five lava ribbons and its eruption;
  the Basket's lids and food; the Chest's lid, a few top coins and its spill; glitch bands only during a burst.
- **The game screen**: a roll's landing frame 3-6 (first of a session ~17); a reroll's tap frame 3.5-8. Third Wind's
  stacked scores add ~0.15-0.4 a frame for their extra text nodes and ~2 to the tap frame; no score line recomposes on
  a shake or toss frame.
- **Pages**: Styles opening frame ~42-44 for its first open after a launch, 16-24 after; a tile scrolling in ~0.6;
  Settings' first frame ~26-30; a Rules tap frame ~6-8 for the heaviest page.

A debug build on a phone is several times slower than all of this; a release build (R8, the Baseline Profile) is the
one to judge.

## Still on the table

- **The tumble's own cost.** Every style tumbles in ~5-6ms with its face layers not drawn at all; the other 6-10 is
  drawing them. Untried: snapshot each face to an `ImageBitmap` (`GraphicsLayer.toImageBitmap`, with the layer as a
  fallback until it's ready), at the price of a style's animation holding still through the 0.9s toss unless the
  snapshot is refreshed.
- **The tray's per-frame work**: each tossed die's position is read in composition (`toss.value`), so each column's
  `ScatterArea` and its `TossedCube` recompose every frame with `offset(x, y)` (a layout change), and `GroundShadow`
  re-creates its `Outline` and draws it four times per die. Reading it in layout/draw instead (`Modifier.offset { }`,
  `graphicsLayer { }`) would cut most of it - a sizeable rework of `DiceTray.kt` and `RollingDie.kt`.
- **Dice switching mode** still rebuilds each die's nodes twice a roll: resting to pick-up at the tap, and toss back to
  resting as they settle. Drawing the pick-up with the resting composable (it's a pure slide) and switching to `Moving`
  once the die is off the mat would move the first into the middle of the shake, where it's hidden.
- **Always-on animations' battery cost**: the menu's drifting dice are capped at ~30 redraws a second; the Starry
  twinkle, the steam/cauldron cups and the score tile's glow run at the display's rate (up to 120fps) at the High level.
  Whether capping those too is worth it should be measured on a real 120Hz phone (Macrobenchmark `PowerMetric`, or
  Android Studio's Power Profiler), not assumed.
- **Baseline Profile benchmarks on a phone**: the before/after of the generated profile
  (`./gradlew :app:baselineprofile:connectedBenchmarkReleaseAndroidTest`) and whether the hand-written wildcard profile
  (`app/android/src/main/baseline-prof.txt`, all app code marked hot) can go once the generated one is as good - it would
  stop unvisited code being compiled ahead of time (~1.2MB compiled). Needs a real phone; the emulator's numbers mean
  nothing.
