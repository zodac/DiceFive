# Source review - open items

From the from-scratch source review of 2026-10-14. What was done is in the git history (`b7748f3`, `7707da0`,
`5956a98`); only what is still open is listed here. File references are as of the review.

## Extract into one place

Looked at and left: `ScoreGrid.overflowFade` and `Rules.fadeOffscreenEdges` are different effects (a vertical edge
fade vs a horizontal mask with a clear zone and easing), so one helper would only add switches.

## Layers and platform separation (iOS, Steam/desktop)

- **No shared JVM source set.** The Android `Diacritics` and `PlayerName` actuals are plain `java.text`, and Room and
  DataStore both run on desktop. An intermediate `jvmMain` shared by Android and desktop avoids copying those. Worth
  doing with the desktop target, not before.

Looked at and left: `stringsLanguageConfiguration()`'s `runBlocking` in `attachBaseContext`. Measured on the sandbox
emulator (debug build, 2026-10-20): 8-71ms on a cold start (most runs 10-35ms), of which about 2-4ms is coroutines' first load and nearly
all the rest Compose resources' first read; a second call takes 0.2ms. The first screen would pay that first read
anyway, so moving it off the main thread wouldn't shorten start-up.

## Smaller best-practice items

Looked at and left: `material-icons-extended`, frozen at 1.7.3 (the app uses 118 icons from it). Tried on 2026-10-20
as Material Symbols vector drawables in `composeResources/drawable/`: R8'd dex fell 45 KB, but the 118 XML files
added 36 KB compressed plus about 27 KB of zip headers, so the release APK grew 13.9 KB (download +10.9 KB). Path
strings in Kotlin were estimated at about 30 KB smaller (0.5% of the APK), not worth restyling every icon for. The
library's icons are plain path code with almost no API to break, so staying on it is low risk. Symbols also
redraws some glyphs (`whatshot` became a trend arrow; its flame, `mode_heat`, matches `local_fire_department`, which
`PlayConsoleExportTest` rejects).
