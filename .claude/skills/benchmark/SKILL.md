---
name: benchmark
description: Measure animation frame cost without a device (Robolectric frame-loop harness, set-ups, sampling profiler, reading the numbers). Use for a reported stutter/slowdown or new art that draws every frame. Patterns and current costs are in .claude/BENCHMARKS.md.
---

# Measuring frame cost

Read `.claude/BENCHMARKS.md` first: it holds the patterns to keep and today's costs to compare against. Benchmarks are
throwaway Robolectric tests in `app/android/src/test/kotlin/net/zodac/dicefive/`. They are never committed, because
they take minutes and prove nothing on their own. Run with
`./gradlew :app:android:testPlayDebugUnitTest --tests '*SomethingScratch*' --rerun` and `println` to the JUnit XML's
`system-out` (`app/android/build/test-results/testPlayDebugUnitTest/TEST-*.xml`).

## The frame loop

```kotlin
@RunWith(AndroidJUnit4::class)
class SomethingScratch {
    @get:Rule val compose = createComposeRule()
    // One frame in phases: main-looper coroutines/delays, composition + layout, draw (plus the capture).
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

Set `compose.mainClock.autoAdvance = false` before `setContent`, so each `frame()` is exactly one frame. Change state
with `compose.runOnIdle { x = y; Snapshot.sendApplyNotifications() }`. Leave out the apply notification and the change
shows up late. Leave out `runOnIdle` and it can be missed.

## Set-ups

- **Whole game screen.** Build `GameViewModel()`, call `setPlayerCount(1)` and `startGame()`, then show
  `GameScreen(viewModel)` inside `LocalPlatformServices provides SilentPlatformServices` and `DiceFiveTheme`. Roll with
  `onNodeWithContentDescription("Dice cup", substring = true).performSemanticsAction(SemanticsActions.OnClick)`, and
  score with `viewModel.commitScore(...)` once `rollsRemaining == 0`. The landing frame is the 27th after the tap
  (`CUP_SHAKE_MILLIS` 420).
- **Board and tray with any art.** Put `GameBoard(state, rolling, ...)` over `DiceTray(dice, ..., rolling)` in a
  411x780dp `Column`, under `LocalGameVisualTheme provides GameVisualTheme(background = ...)` (or `mat`,
  `diceCupStyle`). Flip `rolling` to walk through idle (20 frames), shake (26) and toss (40). Do this for each entry
  of `TableBackgrounds.all`, `DiceMats.all` and `DiceCupStyles.all`.
- **Dice (the reference).** Put seven 54dp dice in a `Row` (`LocalDieIndex` 0-6) and capture **only that row**
  (`onNodeWithTag(...).captureToImage()`). Run 80 frames at rest and 120 tumbling (`style.TossedDie`, `roll` stepped
  0 to 3.9 and round), against a `_blank` style whose `Die` is an empty `Box`. A whole-screen capture adds a 9-12ms
  floor that buries the differences.
- **Styles page.** Use `StylesScreen(StylesViewModel(savedStyles = flow))` with
  `AchievementsState(unlockedAt = Achievement.entries.associateWith { 1L })`, so every style is shown, under
  `@Config(qualifiers = "w411dp-h891dp-xxhdpi")`. Scroll a row with `SemanticsActions.ScrollToIndex`; `ScrollBy`
  does nothing on these rows.
- **Opening a page, frame by frame.** Use `setContent { key(generation) { if (generation % 2 == 1) StylesScreen(...) } }`
  and bump `generation` to open or close the page. Time the next ~40 `advanceTimeByFrame()` + `waitForIdle()` pairs.
  Advance a few seconds between openings. The first opening is cold (~400ms), so compare the later ones.
- **A stress catalog** (to see how a page scales before the art exists). In a throwaway file, repeat each
  `StyleFamily` N times, renaming each copy ("Marble 2") and giving each style a delegating copy with its own id
  (`class Copied(override val id: String, base: DiceStyle) : DiceStyle by base`). Apply it in `StyleCatalog`'s
  constructor. Copies share cached surfaces, so they understate first-time cost. Set N back to 1 before the suite.
  Never commit it.

## Finding where the time goes

- **Sampling profiler.** A daemon thread reads `mainThread.stackTrace` every 0.2ms around the frames of interest.
  Count the innermost `net.zodac` frame (app code), or every frame on the stack (which Compose phase:
  `performRecompose`, `measureAndLayout`, `applyChanges`, `layoutText`...). Samples with no `net.zodac` frame are
  Compose's own work; text layout hides under classes named `Text`/`Paragraph`. Keep the state in top-level
  `@Volatile` vars.
- **Which text re-lays out.** Add `onTextLayout = { println("TL file:line " + it.layoutInput.text) }` temporarily.
  The callbacks for a frame arrive after it.
- **Which composables recompose.** Bump a counter (a `var` on an `object`) at the top of each composable and print it
  per frame.
- **Recording vs replaying.** Capture the same frame twice. If the second capture is as slow as the first, the time
  is the harness's, not the app's.

## Reading the numbers

- They are relative, not a phone's. Compare with neighbouring frames and other styles **in the same run**.
- Warm up first: JIT makes first runs about 7x slower. Take percentiles (p50/p90/p95/max) after a throwaway pass,
  judge by **p95**, and report cold numbers separately.
- A full-catalogue run is noisy (7.5-14.9ms p95 swings). Run three times and take each style's median. Pass the styles
  to measure in an env var, with `--rerun`.
- Capture cost scales with screen size: ~10ms for an empty phone screen, ~43ms for a full Styles page.
- Robolectric doesn't model the GPU. Real drawing cost needs a device (HWUI bars,
  `adb shell dumpsys gfxinfo <package>`), and so do release-build numbers. The sandbox emulator's timings mean
  nothing.

Record new costs in `.claude/BENCHMARKS.md`, and delete the scratch test.
