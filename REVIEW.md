# Source review - open items

From the from-scratch source review of 2026-10-14. The fixes in section 1 of that review, the `AchievementTracker`
extraction, the single owner for the roll timeline, the `AchievementUnlocker` and the single style-category list are
being done first. Everything below is still to do. File references are as of the review.

## Extract into one place

| What | Where it's duplicated | Suggestion |
|---|---|---|
| The labels "3x" / "4x" / "5x" / "Alibi" | `BoardSemantics.kt:50-68`, `CategoryIcon.kt:95,127,272`, `TotalsButton.kt:139`, `ScoresScreen.kt:368` | One `ScoreCategory.mark` property. |
| Large-dialog frame (sizing, close button, title) | `AboutDialog.kt:109-127` and `LicensesDialog.kt:242-270` are near copies | A shared `LargeDialog(title, closeLabel, onDismiss) { ... }`. |
| The gold title text style | `DiceFiveDialog`, `PageTopBar`, About, Licences, about 25 `fontFamily = SoraFontFamily` uses | Named text styles (`pageTitle`, `dialogTitle`). The theme's comment at `Theme.kt:46-58` gives compileSdk 37 as a blocker for Material 3 1.5, but the app now targets 37. |
| Column outline drawing | `DiceTray.kt:580` and `RulesScreen.kt:1225` are the same code | Share it. The fade on unused dice also differs: 0.45 in the tray, 0.35 in Rules, though Rules is meant to look like the game. |
| Edge fades, colour swatches, sparkles | `ScoreGrid.overflowFade` vs `Rules.fadeOffscreenEdges`; `Swatch` in Styles and PlayerColourPicker; `drawSparkle` in Gem and Egg dice | Shared helpers in `ui/common` and `style/`. |
| Watching the app go to the background | `GameScreen.kt:151`, `Haptics.kt:52`, `rememberScopedSoundEffects` add and remove a lifecycle observer by hand | `LifecycleEventEffect` / `LifecycleResumeEffect` from lifecycle-runtime-compose. |
| Tap plus long-press with matching TalkBack actions | Statistics, Styles (twice), Achievements each wire the gesture and the accessibility actions by hand | `Modifier.accessibleCombinedClickable(...)`. |
| Scroll-to-item requests | `AchievementScrollRequests` and `StyleScrollRequests` are the same object with different APIs (`hasPending()` vs `pending()`) | One generic class, moved from `data/` to `navigation/`. |
| Paging fields | `ModeBoard` and `ScoresUiState` both define `totalPages`, `hasNextPage`, `hasPreviousPage` | `ScoresUiState` holds a `ModeBoard` for the combined board. |

## GameViewModel

- The setup form (setup state, its restore in `init`, `persistGameConfig`, `persistHumanNames`) could be its own
  ViewModel; it doesn't need the game loop.

## Same kind of action, different behaviour

- **Long-press means four different things:** Statistics deletes the player (destructive, hidden behind the gesture);
  Styles picks a colour variant or shows the unlock requirement; Achievements does the same as a tap; banners go to
  the achievement or style.
- **"Show me why this is locked" varies:** Styles - tap shows a tooltip, long-press opens a dialog. Achievements
  reward rows - tap and long-press both show the tooltip. Achievements "missing" rows - both open a dialog. Pick one
  rule (e.g. tap = short tooltip, long-press = full dialog) and apply it everywhere.
- **Destructive actions:** Settings uses visible red rows with a confirmation; Statistics' only way to dismiss a
  player is the hidden long-press.
- **Sound vs vibration settings:** sound is set up once for the whole app (`LocalSoundEffects`), vibration once per
  game screen. On/off for both is pushed in by each screen during composition (`GameScreen.kt:366-368`,
  `GameOverScreen.kt:104`). The root already has the sound setting as a flow and can apply it itself.
- **CPU turns give mixed feedback:** a CPU roll plays the shake sound and vibrates the phone, but CPU holds make no
  click. Vibrating for an action the player didn't take is worth a deliberate decision.
- **Missing accessibility details:** `ChoicePicker.kt:128` has no `Role.Button`; the Settings reset row has no click
  label. Most other tappable rows set both.
- **About vs Licences:** both long, scrolling dialogs; About has no app scrollbar, Licences does.
- **Buttons:** the menu uses `FilledTonalButton` for destinations, Game Over uses `OutlinedButton`. If intentional,
  `UI.md` should say so.
- **State collection:** `OnlineAccountCard.kt:62` uses `collectAsState`; everywhere else uses
  `collectAsStateWithLifecycle`.
- **Two debug "superuser" modes**, each with its own entry gesture and toast, inside production ViewModels
  (`GameViewModel`, `AchievementsViewModel`).

## Layers and platform separation (iOS, Steam/desktop)

- **The platform layer depends on the UI.** `platform/PlatformServices.kt:8-10` imports `ui.settings.LicenseReport`,
  `LicenceScroll`, `SelectionClearer`, and declares a `@Composable` method. Move those types into `platform/` (or a
  `licences/` package), or split out a `LicenceUi` interface so device services stay UI-free.
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
- **Android names in shared code:** "an Android toast", `MainActivity` in `AchievementEvents`' doc, haptic durations
  explained by `GameScreen`'s `CUP_SHAKE_MILLIS`.
- **iOS gaps beyond `IOS_SUPPORT.md`:** `reduceMotion()` isn't implemented (iOS has a system setting);
  `IosSoundPlayer` has no pause/resume, and `release()` only stops playback without freeing anything.
- **Startup:** `stringsLanguageConfiguration()` calls `runBlocking` on the main thread in `attachBaseContext`
  (`StringsLanguage.android.kt:41`). Worth measuring.

## Smaller best-practice items

- ViewModels take nullable repositories so they can be built in tests, spreading `?: return` checks. The
  `AchievementStore` interface shows the alternative: required dependencies, fakes in tests.
- `AppContainer.diceRandom` is a public `var` only the store-asset generator uses; make it a constructor parameter.
- `DiceFiveNavHost.kt:130-152` builds `GameSetupScreen` with the same arguments in two branches.
- Rapid next/previous taps on the Leaderboard can let an older page load overwrite a newer one (no cancel of the
  previous load).
- The leaderboard tie-break order is written twice, in SQL and as a Kotlin comparator, kept in step by a comment.
- `material-icons-extended` is frozen at 1.7.3; the app uses about 108 icons from it. Material Symbols vector files
  would get off a dead library and help APK size.
- `ScoreRepository.recordScore` is only used by tests.
- Out-of-date doc comments: the `SettingsRepository` class comment, `AchievementEvents` (says the host is in
  `MainActivity`), the theme's Material 3 version note.

## Found while doing the first batch

- **The view model's timing fields are still public** (`cupShakeMillis`, `diceAnimated`, `diceTossMillis`), written by
  the screen and set by tests. The screen could pass these in rather than writing them into the view model; the
  tests that set them (`GameViewModelTest`, `AiReleasePacingTest`, `GameAchievementsWiringTest`) would change with it.
