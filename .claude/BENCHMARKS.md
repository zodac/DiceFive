# DiceFive: frame-cost benchmarks

How the game's animation cost was measured, what the numbers were, and what was fixed. Read this
when a player reports a stutter, lag or slowdown, or before adding art that draws every frame (a
new dice, cup, mat or background style, or anything animated). It isn't needed for everyday UI
work.

The short version: **keep per-frame work out of what a toss or shake redraws.** A die face, cup or
tile that re-runs expensive drawing code on every frame costs the whole table its frame rate, and
the cup's shake and tip is where a player notices first.

---

## How to measure

None of this needs a device: Robolectric renders for real with `@GraphicsMode(NATIVE)`, and
`captureToImage()` forces a frame to be drawn. The benchmarks were throwaway tests in
`app/android/src/test/kotlin/net/zodac/dicefive/`, run with
`./gradlew :app:android:testDebugUnitTest --tests '*SomethingScratch*'`, printing to the JUnit XML's
`system-out` (`app/android/build/test-results/testDebugUnitTest/TEST-*.xml`). They weren't kept:
they take minutes and prove nothing on their own. Recreate from the pieces below.

### The frame loop

```kotlin
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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

---

## Results

### 1. The cup's tip hitched as the dice landed (fixed)

Reported as "a small lag as the cup begins to tip, worst on the water cups, glass most of all".
It was a frame hitch, not the liquid animation: the frame a roll lands on is also the frame the
tip starts, and it was 3x a normal frame.

Game screen, composition + layout of the landing frame (ms, warm rolls):

| | Before | After |
|---|---|---|
| First roll of the session | ~36 | ~17 |
| Typical roll | 6-12 | 3-6 |
| Normal frame, for comparison | ~1 | ~1 |

Causes and fixes (`DiceTray.kt`'s `ScatterArea`, `GameScreen.kt`):
- The pick-up and the toss called `Moving(...)` from two `when` branches, so on landing every die's
  nodes (shadow, tossed cube, its offscreen face layers) were disposed and rebuilt. One call site
  now takes either move's arguments.
- A turn's first dice weren't composed until they landed. They're now composed off the mat during
  the shake (where a later roll's dice already are), moving that cost to the tap frame, which the
  shake's onset hides.
- The cup starts pouring `CUP_POUR_LEAD_MILLIS` (64ms) before the dice land, so the tip is under
  way before the heavy frame arrives.

The water cups looked worst because the liquid trails the cup on a spring: a dropped frame shows
as the liquid jumping. The cups themselves cost the same to draw as any other (below).

Things ruled out on the way: the shake/tip animations restarting (the liquid spring retargets
cleanly every frame), text layout (the score previews re-lay out on the tap and settle frames, not
the landing one), and the pre-accessibility-pass commit (identical landing cost).

### 2. Dice styles, while tossed (Marble and Frosted fixed)

Five tossed dice, ms per frame, warm (minimum of three passes) and first pass:

| Style | Before | After | First pass |
|---|---|---|---|
| Marble White / Black | ~23 | 2.2 | ~6 (includes painting each face once) |
| Frosted White / Ice | ~11.5 | 2.0 | ~5-6 |
| D20 White / Blue | ~5 | 3.3-3.5 | ~6 |
| Numeral (Roman, Arabic, Japanese...) | | 2.2-2.9 | 4-7 |
| Googly Eyes, Text, LCD, Misprint, Multicolour, Irish | | 2.0-2.6 | 2.3-5 |
| Ivory, Metal, Retro, Classic, Barrel | | 1.7-2.0 | 3.4-10.6 (Ivory runs first, so its cold number is JIT) |
| Plain cubes | | 0.6-0.8 | 1.0-1.4 |

Cause: Marble's veins (dozens of layered soft strokes along long random walks) and Frosted's grain
(hundreds of dots) were repainted on every frame, for both faces of every tossed die. Fix:
`drawCachedSurface` in `Patterns.kt` paints a pattern once per key and size into an `ImageBitmap`
and stamps it after that - an LRU of 128, about 90KB an image at a phone's die size. **Any new
style with a costly but unchanging surface should draw through it**, with a key saying everything
its painter depends on besides the size.

### 3. The tap frame on a reroll (fixed)

The frame the cup is tapped on for a turn's 2nd or 3rd roll was the heaviest of the roll, 8-15ms of
composition + layout (warm) against ~3ms for a turn's 1st. A quarter of its samples were
`layoutText`: every score cell's preview hides the moment a reroll starts, and each one's text was
laid out again (a number to "-", and the gold ones bold to plain). `CategoryCell` in `ScoreGrid.kt`
now keeps the last number laid out, hidden by alpha behind an always-laid-out "-", so the tap only
flips which is visible; the number is laid out again only when it changes, as the dice settle.
After: 3.5-7.7ms. What's left is the dice switching from resting to their pick-up (below).

### 4. Cups, backgrounds and mats (nothing to fix)

Board plus tray, ms per frame, idle / shake / toss. Every entry was within ~20% of the rest, so
only the range is kept here:

| | Idle | Shake | Toss |
|---|---|---|---|
| Backgrounds (heaviest: Floating Dice, Starry Midnight, Planks) | 2.2-3.0 | 2.3-3.0 | 3.4-4.1 |
| Mats (heaviest: Hardwood, Marble) | 2.2-2.8 | 2.3-3.1 | 3.4-4.1 |
| Cups (heaviest: Cauldron, Takeaway, Flowerpot) | 2.2-2.5 | 2.3-2.6 | 3.3-3.7 |

Drawn alone, one at a time, every cup was 0.8-1.7ms - the glass and liquid cups no more than the
rest. The toss column is higher everywhere because of the dice, not the art.

### 5. The Styles page (one fix, not a frame-cost one)

Everything unlocked, phone-sized screen:
- **Idle:** 0.5ms of composition a frame, and under 0.5% of main-thread samples in app code -
  almost all of it the page backdrop's drifting dice watermark (`BrandBackdrop`). No tile redraws
  while nothing changes.
- **Scrolling** (every tile in all four rows): 1-2ms of composition a frame, app code under 1% of
  samples. What draws every frame is only the art meant to move - the Takeaway's steam, the
  Cauldron's bubbles, the starry backgrounds.
- **Off screen:** a tile scrolled out of its row is disposed, animation and all - with every row
  scrolled to its end the Cauldron, Takeaway and starry tiles draw every frame, and scrolled back to
  the start they don't run at all.
- **Didn't fit a small phone:** at 360x640dp the Background row started at 606dp and was cut off
  with no way to scroll to it (the page is sized to fit, and wasn't scrollable). Its column now
  scrolls vertically when it has to - 94dp there - and is laid out exactly as before when it fits
  (411x891dp, or 2x font on that screen).
- **A row's first scroll stuttered** (reported on a device). The first time each tile scrolled in,
  its frame spent 6-12ms composing it (4-5ms once warm) - up to 35ms for the Mat row, whose
  textured mats decode their images - and its draw jumped from ~43ms to 60-97ms; mostly building
  the tile for the first time, the D20 dice and the textured mats most of all. `StylesWarmUp`
  had only drawn the tiles each row opens on. Three changes, in `StylesScreen.kt`:
  - `StylesWarmUp` now draws **every** tile, `WARM_UP_TILES_PER_PASS` (3) at a time over two frames
    each, a category at a time. Its first pass is the heaviest (loading and compiling code for the
    first time: ~164ms here, where the old one-category-a-frame pass was ~255ms); later passes are
    6-31ms (the old ones up to 39ms), just more of them.
  - Each row's `LazyListState` has a `LazyLayoutCacheWindow` (first a tile wide either side, later
    the whole row - see below), so tiles are composed and measured in the idle time between frames
    before they scroll in. The harness has no idle time between its frames, so it can't show the
    prefetching - only a device can.
  - A tile that's built but not on screen (checked from `layoutInfo.visibleItemsInfo`) is held
    still by providing `LocalReduceMotion` true to it - every animated piece of art already stops
    under reduced motion. Checked: the Cauldron's code never runs at any scroll position where its
    tile isn't showing.

  After the warm-up, a row's first scroll costs the same as its second: 4-7ms of composition per
  new tile, and no draw spikes (the Mat row's 35ms/97ms gone).
- **On a device, the idle page was over budget every frame** (HWUI profile bars, `Profile HWUI
  rendering`): mostly red and orange - issuing and drawing on the GPU, not the app's own work. A
  device redraws the whole window whenever anything in it moves, so every animated tile (the
  Takeaway's steam, the Cauldron, twinkling stars, Floating Dice) and the backdrop's drift had all
  ~40 tiles' art drawn again each frame. Proven by holding everything still: the phone then drew
  nothing at all while idle. Fixes:
  - Each tile's preview is drawn into its own layer
    (`graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }` in `StylePreview`),
    which the device keeps and re-uses until that tile changes - a still tile costs next to nothing
    per frame, and scrolling moves layers rather than redrawing art. Scrolling felt clearly better.
  - The Floating Dice background ignored reduced motion, so it kept drifting (and kept the page
    redrawing) when everything else had stopped - in the game too. It now holds still, like the
    menu's backdrop.

  Robolectric can't show any of this: it doesn't model the device's GPU, and a capture replays
  the whole window regardless. The HWUI bars (or `adb shell dumpsys gfxinfo <package>`) on a real
  phone are the measure for drawing cost.
- **A fling still spiked** on a device even with two tiles built ahead: a fast fling outruns
  anything built between frames, and each tile arriving was built on its frame. Measured (warm,
  composition + layout only, per tile scrolling in; the harness's own overhead left out): Compose's
  bookkeeping for creating and disposing items ~40%, **laying out the tile's name label ~33%**,
  semantics ~10%, the tile's boxes ~6%, the art ~5%, colour pop-up + dots + clicks + icons ~4%. So:
  - Every tile is built ahead and kept. First tried as a whole-row `LazyLayoutCacheWindow` on the
    `LazyRow`, but on the device a fling still spiked green, and counting built tiles showed why: the
    cache window built **nothing** ahead - 16 tiles after opening, still 16 after five seconds idle
    and after the row began to scroll; each tile was built only on the frame it came on screen. So
    each row is now a plain `Row` with `horizontalScroll`, built deterministically: the tiles on
    screen at open (the pick and as many either side as fit) straight away, then the rest one every
    `TILE_BUILD_FRAMES` (4) frames once the page has faded in, nearest the pick first, each row
    staggered a frame from the last; unbuilt tiles are same-sized empty placeholders, silent to a
    screen reader, so nothing moves. Measured: 24 of 42 tiles built at open (the lazy rows built 16),
    all 42 by ~480ms; after the launch warm-up the building frames peak at 7.8ms (JVM), most under
    3ms; a fling afterwards builds nothing (worst frame 5ms). Tile positions after opening match the
    lazy version exactly, less two stale off-screen items the lazy row still reported to screen
    readers over the visible ones.
  - Labels go through `TileLabel`: one `TextMeasurer` per page (with a cache) lays each name out
    once, drawn with `drawText`, with the same `text` semantics a `Text` has - pixel-identical
    captures and identical semantics text.
  - Per tile scrolling in: 2.01ms before, 1.58ms keeping every tile, 0.83ms with cached labels,
    **0.56ms with both**.
- **Opening grew with the number of styles, and was redone every time** (reported on a device: the
  menu's drifting dice froze for a few frames on every open). Measured with a stress catalog (every
  dice family three times over, the rest twice - 98 tiles), warm, per opening frame: 48-80ms, against
  37-49ms for the real 42. Every unbuilt tile had its own placeholder, name label and all, on that
  frame; about a third of the frame was laying out names, from a text measurer made afresh each
  opening; and every tile on screen was built on it. Fixes, in `StylesScreen.kt`:
  - The unbuilt tiles either side of the built run (built nearest the pick first, so always one run)
    are one `PlaceholderRun` each - empty tile outlines in a single `Canvas`, no names.
  - One label measurer for the process (`rememberTileLabelMeasurer`), shared with `StylesWarmUp`, so
    names are laid out once per launch; its cache is sized to every style there is.
  - The first frame builds only each row's pick, then a tile either side per frame until the screen
    is full; the row is shown once it is.

  Opening frame now 16-24ms warm with the stress catalog, the same as with the real one - what's
  left is the page shell (app bar, card titles, scrollbars) - and the three building frames after it
  4-8ms.
- **Opening:** the first frame composes the page (~160-200ms here, before JIT warm-up); the
  `StylesWarmUp` pre-draw from the menu already exists for this - see its comment in
  `StylesScreen.kt`.
- **The first open after a launch still lagged** (reported on a device, with the gallery view's
  changes in). Measured as the menu would see it - `StylesWarmUp` run to the end, then the page opened,
  each in a fresh JVM - the opening frame was ~105ms before the gallery work and 110-120ms with it
  (the rest of the opening ~70ms either way). Sampled: almost none of it was tile art (the warm-up's
  job) - it was class loading and Compose building, for the first time, everything *around* the
  tiles: the page's scaffold and app bar, the rows' scroll containers, placeholders and scrollbars,
  `BoxWithConstraints`, the gallery toggles. The warm-up only ever drew tiles in a plain `Row`. Now it
  goes on to build each category's real card (one a pass), then the screen's empty frame, then the
  whole screen (`StylesScaffold`, with `ScreenScaffold(driftingDice = false)` - the drift is shared
  with the menu's). Opening frame **42-44ms** (three runs), the opening 118-121ms in all; the
  warm-up's own frames on the menu stay as they were (its first pass, the tile art, ~240ms cold; the
  rest under ~60ms) - the whole screen in one pass made an 70-87ms menu frame, hence the separate
  frame pass. The gallery itself was never part of it: it isn't composed until opened, and opening
  one moves the row's tiles (`movableContentOf`) rather than building them again.

### The Treasure Chest and the Gems dice (designed to it)

Measured when they were added: the cup alone at its game size (idle 20 frames, shake 26, open 40),
and five tossed dice, warm, ms per frame, against neighbours in the same run (the machine's baseline
drifted between runs, so compare within a row's run only).

| | Shake | Open | Notes |
|---|---|---|---|
| Classic Gold cup | 3.8-4.3 | 3.8-4.6 | baseline |
| Treasure Chest, everything drawn live | 4.2-5.0 | 5.9-6.3 | body ~70 draws repainted every frame - the lid's rattle and swing redraw the canvas |
| Treasure Chest, body and hoard cached | +0.4 over Gold | +1.1 over Gold | what shipped |
| Gems, tossed | | | 11.2-11.4 against 9.9-11 for Ivory, Marble, Numeral |

The chest's hoard was never drawn live past its first design: ~180 pieces were cut to one cached
image plus 14 live ones before measuring. A one-off 28ms frame in one pass was GC (six passes
otherwise steady).

Re-measured once the hoard became a settled pile (~580 coins, each a projected disc with its edge,
simulated on first use - `settlePile`) and the shake was smoothed: warm, within 1-2ms of the Gold cup
shaking or opening; the first opening of a run (simulating the pile and painting it) ~20ms at worst
against ~17ms for the Gold cup's own first open. That cost is once per launch, not per roll.

Then reported as lag when continuing a game with the chest already open: that first frame had to
simulate the pile (19ms), paint it (74ms - every coin was four projected 16-point polygons) and the
body (2ms), 158ms in all. The pile now settles and paints on a background thread as soon as the
chest is composed (a gold fill stands in until it's ready), and coins are drawn as rotated ovals:
the same first frame is ~66ms, all of it first-run code warm-up - a second resume in the same run is
23ms.

### The Urn, Volcano and Picnic Basket cups, and seven dice (designed to it)

Measured when they were added, the second of two passes in one run (warm), ms per frame: the cup
alone at its game size (idle 20 frames, shake 26, open 40), and five tossed dice.

| Cup | Idle | Shake | Open (max) |
|---|---|---|---|
| Classic Gold (baseline) | 3.5-3.7 | 5.0-5.1 | 5.2-5.9 (7.2) |
| Treasure Chest (now drawn 20% larger) | 4.7-5.0 | 7.2-8.0 | 6.8-7.3 (9.6) |
| Urn | 4.3-4.7 | 6.0-6.1 | 6.0-6.1 (7.8) |
| Volcano (top half swaying in 24 bands of its cached cone) | 3.7-4.0 | 4.1-4.6 | 5.2-5.3 (6.2) |
| Picnic Basket (20% larger) | 3.8-4.0 | 5.0-5.3 | 5.4-5.8 (7.8) |

Re-measured (two runs, after the Volcano's rework and the 20% enlargement of the chest and basket):
a third run at the same time put the chest's open at 10.4 (19.9 worst) and the Urn's idle at 7.3, but
the Urn hadn't changed and two re-runs agreed with the rows above - one noisy run, not a regression.
Compare within a run.

Dice tossed: Ivory 10.7, Gems 13.3; Cake 13.7, Meadow 11.7, Poker 13.0, Obsidian 12.3, Mahjong
11.4-13.9, Tally 13.0-13.6, Garden 12.8. A style's first frame (painting its cached faces) was 12-33ms
warm; Poker's very first in the run was 159ms - the bundled MathJax font loading, once per launch,
as for the Maths dice. Live per frame: the Volcano's crater, five lava ribbons and (while erupting)
about 30 bombs and ash puffs; the Basket's two lids, handle, ~8 pieces of food and up to 3 apples -
their bodies are painted once.

---

### Bestagon, Pyramid, Glitch, Ribbon, Neon and Glitter, with their mats, backgrounds and cups (designed to it)

Measured when they were added, the second of two passes in one run (warm), ms per frame, interquartile
range (max). Five dice at 48dp, at rest (20 frames) and tossed (30):

| Dice | First | Rest | Tossed |
|---|---|---|---|
| Ivory (baseline) | 3.4 | 0.9 | 3.4-4.3 (7.0) |
| Bestagon | 4.0 | 0.7-0.9 | 1.4-1.7 (2.0) |
| Pyramid | 3.7 | 0.7-0.9 | 1.2-1.4 (1.9) |
| Glitch (animated at rest) | 2.6 | 1.1-1.2 | 4.1-5.0 (8.1) |
| Ribbon (painted once per face) | 3.3 | 0.8 | 3.1-4.1 (6.5) |
| Neon (pulsing at rest) | 3.6 | 1.6-1.7 | 3.7-5.2 (7.9) |
| Glitter (sparkling at rest) | 2.9 | 1.3 | 3.8-4.9 (13.6 - a cold face's glitter being painted) |

Bestagon and Pyramid toss cheaper than a cube: they spin one flat face rather than rolling two.
Cups alone at their game size - idle 20 frames, shake 26, poured 40: Classic Gold 0.6-0.7 / 1.6-2.2 /
1.3-1.9; Glitter 1.2-1.5 / 2.0-2.5 / 1.5-3.1 (5.1); Neon 1.0-1.1 / 1.6-1.8 / 1.4-1.9; Gift Box
0.6-0.7 / 1.4-1.9 / 1.3-1.5. Board plus tray, idle (first frame, then 20): mats - Classic Blue 3.1,
3.0-3.4; Honeycomb 9.6, 4.0-4.8 (the bee); Hex Tiles 8.0, 3.0-3.5; Sand 7.4, 3.0-3.6; Circuit 9.4,
3.1-3.7; Neon 8.9, 3.4-3.7; Gift Wrap 7.5, 3.0-3.3; Glitter 7.4, 3.2-3.5. Backgrounds - Navy 6.4,
1.9-2.0; Honeycomb Honey 6.7, 2.4; Brick 7.0, 2.4-2.5; Glitch 7.3, 3.1-3.3; Desert 7.6, 2.7-2.8;
Gift Wrap 7.6, 2.6-3.0; Glitter 7.4, 3.5-4.1. Each first frame is its pattern being painted once
(`drawCachedSurface`). Live per frame: 2 sparkles per glitter die, 6 on the glitter mat, 5 on its
background, 2 on the cup; a neon tube's five strokes per die, the mat's two framed tubes, the cup's two
rings; the bee's ~40 small shapes; the glitch's band slices only during a burst; the gift box's lid
only while it rattles or falls.

---

### Third Wind's stacked scores (designed to it)

Third Wind shows three scores beside every box - about 50 more text nodes on the board. Measured on
the whole game screen (set-up above, 411dp, a part-filled card, the third of three rolls), composition
ms per frame, Standard vs Third Wind: idle 0.44 vs 0.58, over a roll 1.0 vs 1.5 on average, the cup-tap
frame 3.9 vs 6.1 and the landing frame 3.3 vs 4.5; draw (mostly the capture) the same in both.
Counting recompositions per frame: no score cell or line recomposes on a shake or toss frame; the tap
recomposes the 13 boxes and only the preview lines, since each line keeps its number laid out
(`SlotScore`) and `ShownScore` compares by value (it was a plain class, so every filled line recomposed
too - the peak barely moved for it under Robolectric, but it's work for nothing). The flat ~0.15-0.4ms on
every frame tracks node count, not recomposition; it couldn't be told apart from the harness's own
per-node work here. Not measured on a device.

### 7 Dice Stud: every dice style, at rest and tumbling (RGB and Neon reworked)

The Stud tray rolls seven dice, so the dice styles were measured seven at a time: a `Row` of seven 54dp dice
(`LocalDieIndex` 0-6, faces 6 3 1 5 2 4 6), **only that row captured** (`onNodeWithTag(...).captureToImage()`), one style at
a time, 80 frames at rest and 120 tumbling (`style.TossedDie`, `roll` stepped 0 to 3.9 and round again), against a
`_blank` style whose `Die` is an empty `Box`. **Percentiles over every frame** (p50/p90/p95/max), not averages - a mean
or a best-of hides the frames a player sees. Judge a style by **p95**.

Two traps that cost an afternoon:

- **Don't capture the whole screen or the whole tray.** Capturing `onRoot()` (411x891dp) or the Stud `DiceTray` (mat,
  cup, shadows) put a floor of 9-12ms under every style, the blank one included, which buried every difference.
  Capturing just the row gives the blank die 2.7ms at rest and 6.3 tumbling.
- **One full-catalogue run is noise.** On a shared host the same style swung 7.5 to 14.9ms p95 between runs, and a run's
  "slow" list shared almost nothing with the last one. Run the catalogue three times and take each style's median.
  Running a few styles alone reads lower than running all 84 in one process. Pass the styles in an environment variable and use `--rerun`,
  or Gradle serves the last results from cache (an environment variable isn't a task input).

At rest, the median of three full runs: **no style over 10ms p95** (highest Tally Forestry 9.1, Tally Western 8.6, Glitter
8.6; the blank die 3-4; Classic ivory ~6.5). RGB: Rainbow 7.8, Wave 7.9, White Rainbow 7.7, White Wave 8.2, Neon 7.1.
Tumbling is a different story: **every style is over 10ms p95** - median style 15.4, range 12 to 20, and the blank die 6.9
(see "The tumble").

**What was reworked.** The first RGB version (a bloom, a patch and a wash, each a radial gradient built per lamp per
frame) measured 10-12ms; Neon (nine strokes of halo per digit rebuilt every frame) ~10. Now:

- Neon paints its tube once per face and colour (`cachedSurface`) and the pulse is only the image's opacity.
- RGB paints its *light* once per face as white and tints it as it's drawn (`ColorFilter.tint(.., SrcIn)`): all of Rainbow's
  light in one image tinted once per die; a dark Wave keeps one small gradient per lamp (each lamp's own colour); a pale
  Wave takes the face's light in the middle's colour, so the colour wave reads in its lenses only. Lenses and sockets are
  plain circles - a tinted image per lamp was slower than a gradient (0.18ms each, 25 lamps), and a cached sockets image
  was slower than two small circles.

Quality given up: Neon's core dims a little at the bottom of the pulse (the whole tube fades, not just the halo); a pale
Wave's glow is one hue across the face. Not measured on a device.

### The tumble (first step done: faces drawn once, into layers)

Seven tumbling dice cost 12-20ms p95 for any style and 6.9 for a die that draws nothing, so the cost is the tumble
(`TossedCube`, `RollingDie.kt`), not the styles. Composition and layout are ~0.8ms of a ~13ms frame (measured separately):
it is all draw. Every frame, for each die's two faces in view:

- Each face goes in a `graphicsLayer` with `CompositingStrategy.Offscreen`, `requiredSize` **twice the die's size** in each
  direction (so a tipped-up face isn't clipped) - four times the area, allocated, cleared and blended back, 14 times a frame.
- Inside it the face's own art is **drawn again** under a perspective `Matrix` (`withTransform`) - through `clipPath` of a
  fresh anti-aliased silhouette `Path` - so every gradient, path and image a style draws is re-rasterised, twice per die per
  frame, under perspective (which has no fast path), though the face looks the same on every frame. That is why heavy
  styles cost 2-3x more tumbling than the blank die, and why the p50 of a tumble is 2-3x the p50 of rest.
- A fresh shade `drawRect` with `SrcAtop` over each, and a body `Canvas` under both.
- `roll` is a parameter, so `TossedCube` (a `BoxWithConstraints`, which subcomposes) recomposes every frame: the faces,
  `cubeFaceProjection`, `convexHull`, `insetConvex` and `roundedConvexPath` are rebuilt in lists and `Path`s each time.
- In the tray itself (`DiceTray.kt`), `toss.value` is read in composition, so each column's whole `ScatterArea` recomposes
  every frame with `offset(x, y)` (a layout change, not a layout-lambda), and `GroundShadow` re-creates its `Outline` and draws it four times per die.

The p95 is not much above the p50 in the row harness (1-2ms), so these are not spikes but a steady heavy frame; the larger
spikes seen in full-tray runs (the blank die's max of 57ms) were the harness's own capture.

**Done:** `TossedCube` now records each face once into its own `GraphicsLayer` at the die's size (`FaceLayers`, offscreen,
released with the die) and each frame only shades (a `ColorFilter` matrix on the layer, the same darkening) and draws that
layer under the perspective matrix, clipped to the silhouette. No 2x layers, no `SrcAtop` rect, no face redrawn into a bigger
buffer. The renders match the old ones frame for frame. Seven tumbling dice, p95 (ms), before -> after:
blank 7-9 -> 5; Classic ivory 14-16 -> 12; Glitch black 14-16 -> 11-12; Neon 16-17 -> 13; RGB Rainbow 17 -> 15;
Marble 19-22 -> 16; Obsidian lava 20 -> 15-16; Cake chocolate 18-21 -> 16.

**Then:** (1) the Cake's frosting and berries are one cached image per face, not two - same pixels (rendered before and
after, held ring and tumble included), one image less to turn per face. (2) **Animation is held still through the tumble**:
`TossedCube` provides `LocalArtFrozen` to a face, true unless it is the face the die lands on and the roll is in its last
quarter-turn; `rememberArtSeconds` then stops updating (so a face's art isn't re-recorded every frame), a clock made mid-roll
starts from the last tick (`latestArtSeconds`) instead of 0, and Glitch draws its clean face while frozen (never stuck
mid-burst). The cost: when the landing face thaws its clock jumps by the toss's elapsed time (an RGB hue, a Neon pulse phase).
Against Classic ivory in the same run (this host swung ivory from 11.7 to 16.8 between runs, so only same-run differences
mean anything), median of three, tumbling p95: Glitch black -1.7, Neon +0.3, RGB White Wave +1.8, Cake chocolate +2.3, RGB
Rainbow +4.3 - they were each 3-5 above before.

**Still over 10ms**, and where it is: with the layers not drawn at all, every style tumbles in 5-6ms - that is the floor now
(composition, shadow-less layout, the body `Canvas`, the clip). The other 6-10ms is drawing the 14 layers under the matrix.
Under Robolectric's CPU Skia that replays each layer's recorded art through the perspective every frame; on a device the layer
is an offscreen texture and it is a textured quad, so this part is probably overstated here (not measured on a device).
The clip is not the cost (the same with the clip removed). **Next, untried:** snapshot each face to an `ImageBitmap`
(`GraphicsLayer.toImageBitmap`, suspend, with a fallback to the layer until it's ready) so the toss draws a bitmap, not
the style's art - at the price of a style's animation (neon's pulse, RGB's cycle, glitch) holding still through the 0.9s toss
unless the snapshot is refreshed; and the tray's own per-frame work (`toss.value` read in composition, `offset(x, y)`, the
four-pass `GroundShadow`), which the row harness leaves out.

### The Settings page opening (fit cache)

Reported as "a little slow to render" once the Animations row (a `FittedSegmentedChoiceRow`) was added. Opening the
page, frame by frame (the "Opening a page" set-up with `SettingsScreen`, 411x891dp), composition + layout of its first
frame, warm (later openings):

| | First frame |
|---|---|
| Before the Animations row | ~22-28ms |
| With it | ~26-35ms |
| With it, and the fit cache | ~26-30ms |

Sampling the first frame: about 38% is Compose's own text layout, 10% was `ShrinkThenWrapText` measuring each setting's
label to choose its size and 5% the segmented row doing the same for its four labels - on every opening, though the
answer never changes. Fix: `FontFitCache` (`ShrinkThenWrapText.kt`), an LRU of 256 fits keyed on the texts, style,
limits, width and screen scale, shared by both; a page opened again skips the measuring (samples down ~19%). Every
page with a `ShrinkThenWrapText` label gets it. The rest is the page's base cost (Scaffold, cards, list items,
switches, text), as on any page. A debug build on a phone is several times slower than this again; a release build,
with the Baseline Profile (whose Settings visit now taps a level too), is the one to judge.

### The Rules page (tab taps, page builds, missed taps)

Reported on a device as lag moving between pages, by tab or by swipe, and tabs that needed tapping more than once.
Set-up: `RulesScreen` at 411x891dp xxhdpi with the frame loop above; tabs tapped by their `OnClick` semantics action
(or by real touches, `performTouchInput { click() }`, for the missed taps), 40 frames each, warm (third pass).

- **A tab animated the pager through every page between** (`animateScrollToPage`), building each: Overview to Hit List
  built Stud, Third Wind and Hit List, 19ms + 52ms of composition in two frames. Tabs now snap (`scrollToPage`), as
  the maintainer asked; a swipe still slides. Every tap is one build frame, total composition per tap down 25-45%.
- **The page built all at once** - Hit List, the longest, ~13-18ms warm in its first frame, though only its first
  screenful shows. `rememberShownBlocks` builds the title and 4 blocks in that frame, then 2 more a frame (Hit List
  whole within ~7 frames, each 4-13ms). The maintainer's idea; a `LazyColumn` would do the same but only estimate the
  page's height, and the page's draggable margin scrollbar needs it exact.
- **Every page change rebuilt both tab rows**: `RulesScreen` read `pagerState.currentPage` itself, and each tab's
  modifiers were new objects, so no tab could skip. The current page is now read only where it's shown (each page
  tab's `selected`, through `derivedStateOf`; the indicator's layout; `PageScrollbar`; `PageCountFooter`;
  `KeepTabInView`), and tab modifiers are remembered: the tab rows went from roughly a third of a simple page's tap
  frame to ~7% of it.
- **Formatting**: `formatList` built a new `ListFormatter`, and `isRightToLeft` (asked by every string with an argument,
  through `fill`) parsed the language tag, on every call; both are now kept per language, as the number formats already
  were (Android and iOS). `spokenPoints` built its pattern per paragraph; it's remembered.
- **Missed taps were not frame cost**: a scrolling container takes a tap made while it's scrolling as "stop", not as a
  tap on what's under it. Material's scrollable tab rows re-centre the selected tab with a scroll animation on every
  selection, so a second tap within ~300ms of the first was lost (pinned by real-touch tests, 50ms apart); the group
  row was scrollable by 1px of rounding, so it did the same. Fixed by giving both rows a `selectedTabIndex` that never
  changes and keeping the selected tab in view ourselves (`KeepTabInView`, which moves the row at once, only when the
  tab is under an edge), and by sizing the group tabs in whole pixels to exactly the row.

What's left of a tap frame is the page's own build (about half, in `HorizontalPager`) and Compose creating and measuring
nodes - ~6-8ms warm on desktop for the heaviest page. The user's debug build is several times slower again; a release
build (R8, Baseline Profile) is the one to judge.

## Still on the table

- **The toss itself** costs ~1.5x a normal frame for its 900ms: each tossed die's position and
  tumble is read in composition, so `ScatterArea` and its `TossedCube` recompose every frame.
  Reading `toss.value` in layout/draw instead (`Modifier.offset { }`, `graphicsLayer { }`) would
  cut most of it, but it's a sizeable rework of `DiceTray.kt` and `RollingDie.kt`.
- **Dice switching mode** still rebuilds each die's nodes twice a roll: resting to pick-up at the
  tap (`ScatterArea`'s `else` branch to `Moving`), and toss back to resting as they settle. Drawing
  the pick-up with the resting composable (it's a pure slide - no roll or yaw) and switching to
  `Moving` once the die is off the mat would move the first into the middle of the shake, where
  it's hidden. The settle frame also lays out the new score previews.
