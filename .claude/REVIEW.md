# Source review - open items

From the from-scratch source review of 2026-10-14. What was done is in the git history (`b7748f3`, `7707da0`,
`5956a98`); only what is still open is listed here. File references are as of the review.

## Extract into one place

Looked at and left: `ScoreGrid.overflowFade` and `Rules.fadeOffscreenEdges` are different effects (a vertical edge
fade vs a horizontal mask with a clear zone and easing), so one helper would only add switches.

## Layers and platform separation (iOS, Steam/desktop)

- **No shared JVM source set.** The Android `Diacritics` and `PlayerName` actuals are plain `java.text`, and Room and
  DataStore both run on desktop. An intermediate `jvmMain` shared by Android and desktop avoids copying those. The
  five formatter caches in `Formatting.android.kt` / `Formatting.ios.kt` repeat one pattern; one small cache helper
  per platform would do.

Looked at and left: `stringsLanguageConfiguration()`'s `runBlocking` in `attachBaseContext`. Measured on the sandbox
emulator (debug build, 2026-10-20): 8-71ms on a cold start (most runs 10-35ms), of which about 2-4ms is coroutines' first load and nearly
all the rest Compose resources' first read; a second call takes 0.2ms. The first screen would pay that first read
anyway, so moving it off the main thread wouldn't shorten start-up.

## Smaller best-practice items

- `AppContainer.diceRandom` is a public `var` only the store-asset generator uses. Kept: the generator swaps it
  mid-test on the one process-wide container, so a constructor parameter would only move the `var` into a test hook.
- `material-icons-extended` is frozen at 1.7.3; the app uses about 108 icons from it. Material Symbols vector files
  would get off a dead library and help APK size.
