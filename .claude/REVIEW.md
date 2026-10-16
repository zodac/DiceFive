# Source review - open items

From the from-scratch source review of 2026-10-14. What was done is in the git history (`b7748f3`, `7707da0`,
`5956a98`); only what is still open is listed here. File references are as of the review.

## Extract into one place

| What                      | Where it's duplicated                                                                           | Suggestion                                      |
|---------------------------|-------------------------------------------------------------------------------------------------|-------------------------------------------------|
| The gold title text style | `DiceFiveDialog`, `PageTopBar`, `LargeDialogTitle`, about 20 `fontFamily = SoraFontFamily` uses | Named text styles (`pageTitle`, `dialogTitle`). |

Looked at and left: `ScoreGrid.overflowFade` and `Rules.fadeOffscreenEdges` are different effects (a vertical edge
fade vs a horizontal mask with a clear zone and easing), so one helper would only add switches.

## GameViewModel

- The setup form (setup state, its restore in `init`, `persistGameConfig`, `persistHumanNames`) could be its own
  ViewModel; it doesn't need the game loop.

## Same kind of action, different behaviour

- **About vs Licences:** both long, scrolling dialogs; About has no app scrollbar, Licences does.
- **Buttons:** the menu uses `FilledTonalButton` for destinations, Game Over uses `OutlinedButton`. If intentional,
  `UI.md` should say so.

## Layers and platform separation (iOS, Steam/desktop)

- **The data layer reaches into UI concerns:** `AchievementEvents` / `UnlockedStyle` carry Compose
  `StringResource`s; the scroll-request singletons are navigation state; `SavedStyles` carries `stylesPageFit` (a
  layout measurement); `OnlineAccount` holds an `ImageBitmap`.
- **App-wide pieces live under `ui/game`:** `SoundEffects`, `LeaveGameConfirmation` and the sound wiring are provided
  at the root. The style catalog (ids, unlock rules) is used by game logic and ViewModels but lives in
  `ui/game/style`. Consider a UI-free style registry in `model/`, with the drawing code registered from `ui/`.
- **Building the app container is copied per platform.** `AndroidAppContainer` and `IosAppContainer` build the same
  four DataStores and the database. Add a common `AppContainer.create(dataDir, databaseBuilder, buildInfo)`; a
  Steam/desktop build would otherwise be the third copy.
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

- ViewModels take nullable repositories so they can be built in tests, spreading `?: return` checks. The
  `AchievementStore` interface shows the alternative: required dependencies, fakes in tests.
- `AppContainer.diceRandom` is a public `var` only the store-asset generator uses. Kept: the generator swaps it
  mid-test on the one process-wide container, so a constructor parameter would only move the `var` into a test hook.
- The leaderboard tie-break order is written twice, in SQL and as a Kotlin comparator, kept in step by a comment.
- `material-icons-extended` is frozen at 1.7.3; the app uses about 108 icons from it. Material Symbols vector files
  would get off a dead library and help APK size.

## Found while doing the first batch

- **The view model's timing fields are still public** (`cupShakeMillis`, `diceAnimated`, `diceTossMillis`), written by
  the screen and set by tests. The screen could pass these in rather than writing them into the view model; the
  tests that set them (`GameViewModelTest`, `AiReleasePacingTest`, `GameAchievementsWiringTest`) would change with it.
