# Source review - open items

From the from-scratch source review of 2026-10-14. What was done is in the git history (`b7748f3`, `7707da0`,
`5956a98`); only what is still open is listed here. File references are as of the review.

## Extract into one place

Looked at and left: `ScoreGrid.overflowFade` and `Rules.fadeOffscreenEdges` are different effects (a vertical edge
fade vs a horizontal mask with a clear zone and easing), so one helper would only add switches.

## Layers and platform separation (iOS, Steam/desktop)

- **The data layer reaches into UI concerns:** `AchievementEvents` / `UnlockedStyle` carry Compose
  `StringResource`s; the scroll-request singletons are navigation state; `SavedStyles` carries `stylesPageFit` (a
  layout measurement); `OnlineAccount` holds an `ImageBitmap`.
- **App-wide pieces live under `ui/game`:** `SoundEffects`, `LeaveGameConfirmation` and the sound wiring are provided
  at the root. The style catalog (ids, unlock rules) is used by game logic and ViewModels but lives in
  `ui/game/style`. Consider a UI-free style registry in `model/`, with the drawing code registered from `ui/`.
- **No shared JVM source set.** The Android `Diacritics` and `PlayerName` actuals are plain `java.text`, and Room and
  DataStore both run on desktop. An intermediate `jvmMain` shared by Android and desktop avoids copying those. The
  five formatter caches in `Formatting.android.kt` / `Formatting.ios.kt` repeat one pattern; one small cache helper
  per platform would do.
- **Android names in shared code:** haptic durations explained by `GameScreen`'s `CUP_SHAKE_MILLIS`.
- **iOS gaps beyond `IOS_SUPPORT.md`:** `reduceMotion()` isn't implemented (iOS has a system setting);
  `IosSoundPlayer` has no pause/resume, and `release()` only stops playback without freeing anything.
- **Startup:** `stringsLanguageConfiguration()` calls `runBlocking` on the main thread in `attachBaseContext`
  (`StringsLanguage.android.kt:41`). Worth measuring.

## Smaller best-practice items

- `AppContainer.diceRandom` is a public `var` only the store-asset generator uses. Kept: the generator swaps it
  mid-test on the one process-wide container, so a constructor parameter would only move the `var` into a test hook.
- `material-icons-extended` is frozen at 1.7.3; the app uses about 108 icons from it. Material Symbols vector files
  would get off a dead library and help APK size.
