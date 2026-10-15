# Source review - open items

From the from-scratch source review of 2026-10-14. The fixes in section 1 of that review, the `AchievementTracker`
extraction, the single owner for the roll timeline, the `AchievementUnlocker` and the single style-category list are
being done first. Everything below is still to do. File references are as of the review.

## Decided - do these next

The maintainer's answers of 2026-10-15 to the "Same kind of action, different behaviour" questions below. Visual
changes go through the `render` skill for review before any APK.

1. **Removing a player from Statistics:** a visible delete button on each card, opening the existing confirmation.
   Drop the long-press from Statistics; TalkBack gets an ordinary button.
2. **"Why is this locked?":** one rule everywhere - tap shows the short tooltip, long-press opens the full dialog (as
   Styles already does). Achievements' reward rows and "missing" rows change to match.
3. **CPU turns:** keep the CPU's roll sounds, but don't vibrate for a turn the player didn't take. Vibration stays for
   the player's own rolls and holds.
4. **Superuser modes:** keep both as they are (debug builds only). No change.

## Extract into one place

| What | Where it's duplicated | Suggestion |
|---|---|---|
| The gold title text style | `DiceFiveDialog`, `PageTopBar`, `LargeDialogTitle`, about 20 `fontFamily = SoraFontFamily` uses | Named text styles (`pageTitle`, `dialogTitle`). |
| Faded dice beside the column outline | The outline is shared (`drawColumnOutline`), but unused dice fade to 0.45 in the tray and 0.35 in Rules, though Rules is meant to look like the game | Pick one (a visible change, so render it first). |
| Tap plus long-press with matching TalkBack actions | Statistics, Styles (twice), Achievements each wire the gesture and the accessibility actions by hand | `Modifier.accessibleCombinedClickable(...)` - once the long-press rules below are decided. |

Looked at and left: `ScoreGrid.overflowFade` and `Rules.fadeOffscreenEdges` are different effects (a vertical edge
fade vs a horizontal mask with a clear zone and easing), so one helper would only add switches.

## GameViewModel

- The setup form (setup state, its restore in `init`, `persistGameConfig`, `persistHumanNames`) could be its own
  ViewModel; it doesn't need the game loop.

## Same kind of action, different behaviour

- (Decided above.) **Long-press means four different things:** Statistics deletes the player (destructive, hidden behind the gesture);
  Styles picks a colour variant or shows the unlock requirement; Achievements does the same as a tap; banners go to
  the achievement or style.
- (Decided above.) **"Show me why this is locked" varies:** Styles - tap shows a tooltip, long-press opens a dialog. Achievements
  reward rows - tap and long-press both show the tooltip. Achievements "missing" rows - both open a dialog. Pick one
  rule (e.g. tap = short tooltip, long-press = full dialog) and apply it everywhere.
- (Decided above.) **Destructive actions:** Settings uses visible red rows with a confirmation; Statistics' only way to dismiss a
  player is the hidden long-press.
- **Sound vs vibration settings:** sound is set up once for the whole app (`LocalSoundEffects`), vibration once per
  game screen. On/off for both is pushed in by each screen during composition (`GameScreen.kt:366-368`,
  `GameOverScreen.kt:104`). The root already has the sound setting as a flow and can apply it itself.
- (Decided above.) **CPU turns give mixed feedback:** a CPU roll plays the shake sound and vibrates the phone, but CPU holds make no
  click. Vibrating for an action the player didn't take is worth a deliberate decision.
- **About vs Licences:** both long, scrolling dialogs; About has no app scrollbar, Licences does.
- **Buttons:** the menu uses `FilledTonalButton` for destinations, Game Over uses `OutlinedButton`. If intentional,
  `UI.md` should say so.
- (Decided above.) **Two debug "superuser" modes**, each with its own entry gesture and toast, inside production ViewModels
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
