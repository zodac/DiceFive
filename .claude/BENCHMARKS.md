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

### Finding what the time is spent on

- **A sampling profiler in the test**: a daemon thread reading `mainThread.stackTrace` every
  0.2ms while a flag is set around the frames of interest, counting the innermost `net.zodac`
  frame (what app code is running) or every frame on the stack (which Compose phase:
  `performRecompose`, `measureAndLayout`, `applyChanges`, `layoutText`, `composeInitial`...).
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
  - Each row's `LazyListState` has a `LazyLayoutCacheWindow` a tile wide either side, so the next
    tile is composed and measured in the idle time between frames before it scrolls in. The
    harness has no idle time between its frames, so it can't show this part - only a device can.
  - A tile that's built but not on screen (checked from `layoutInfo.visibleItemsInfo`) is held
    still by providing `LocalReduceMotion` true to it - every animated piece of art already stops
    under reduced motion. Checked: the Cauldron's code never runs at any scroll position where its
    tile isn't showing.

  After the warm-up, a row's first scroll costs the same as its second: 4-7ms of composition per
  new tile, and no draw spikes (the Mat row's 35ms/97ms gone).
- **Opening:** the first frame composes the page (~160-200ms here, before JIT warm-up); the
  `StylesWarmUp` pre-draw from the menu already exists for this - see its comment in
  `StylesScreen.kt`.

---

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
