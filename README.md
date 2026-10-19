# DiceFive

**Roll five dice, fill your scorecard, and outscore friends or AI. Fully offline, unless you sign in to Google Play Games.**

DiceFive is a classic five-dice scorecard game. Roll up to three times a turn, hold the dice you want to keep, and
decide where each roll scores. Chase a full house, a large straight or a 5x, and try to make the upper-section bonus
before your boxes run out.

Play solo to beat your best score, pass the phone around with up to four players, or fill the empty seats with
computer opponents on Easy, Medium or Hard.

New to the rules? A guided tutorial walks you through a game of Standard, step by step. The other modes and the house
rules open up once you've finished your first game.

## Seven ways to play

- **Standard** - the classic rules: 13 boxes, three rolls a turn, a 35-point upper bonus and 100-point bonuses for
  every extra 5x.
- **Tricolour** - every die also lands red, yellow or blue, and four colour boxes join the card. Five of one colour,
  or a coloured full house, opens up new ways to score.
- **Quickfire** - a six-turn game: the 5x box and six random boxes are switched off, and the upper bonus shrinks to
  match.
- **7 Dice Stud** - roll seven dice, but only the five you hold can score.
- **Third Wind** - every box is scored three times, with a bigger upper bonus to match.
- **D20** - twenty-sided dice and five rolls a turn, with a card of the numbers 1 to 20 and a bonus for each half.
- **Hit List** - a fresh list of dice targets every game. Roll a target's numbers to hit it, or land them in order
  for double.

## House rules

Mix in optional modifiers with any mode: a turn timer, more or fewer rolls a turn, unused rolls that carry over, extra
boxes for Two Pair, Evens and Odds, and unlucky dice that lock in place.

## Achievements to chase

Over 100 achievements, from your first win and your first 5x to long win streaks, near-perfect cards and unlikely runs
of bad luck. Some keep their details hidden until you earn them, and a few are secret Easter eggs you'll have to find
for yourself.

## Make the table your own

Unlock over 90 styles as you earn achievements, most in several colours:

- **Dice** - gemstones, marble, mahjong tiles, LCD digits, googly eyes, Roman numerals, cake and more
- **Dice cups** - a top hat, a treasure chest, a volcano, a flowerpot, a cauldron, a fishbowl and more
- **Dice mats** - leather, wood, casino felt, stone, a sunlit lagoon, circuit boards, honeycomb and more
- **Backgrounds** - wooden planks, rolling ocean waves, a river meadow, stone, circuit boards, neon grids and more
- **Scorecard frames** - floral, laurel wreaths, Celtic knots, rope and chain, marquee lights, starry skies, pixel art and neon

## Track every game

- **Leaderboard** of your highest scores, with the date and number of 5x in each game
- **Statistics** for each player, including games played, win rate, best and average scores, and current and best
  win streaks, overall and for each game mode

## Get it

DiceFive is for Android 8.0 and up, in English, Spanish and Arabic. It's coming to Google Play, and from version 1.0
each release's APK is also attached to this repository's [Releases](https://github.com/zodac/DiceFive/releases) page.
It needs no account and no internet connection, and your games stay on your device unless you sign in to Google Play
Games, which saves your progress to your account for your other devices - see the
[privacy policy](https://zodac.github.io/DiceFive/privacy-policy).

---

## For developers

### Stack

- Kotlin Multiplatform: the game, its UI and its persistence are shared Kotlin, built for Android today and compiled
  for iOS on every build (the iOS app itself is still to come - see `.claude/IOS_SUPPORT.md`)
- Compose Multiplatform, Material 3, Navigation Compose
- MVVM (`ViewModel` + `StateFlow`)
- Room (score history) + DataStore Preferences (settings, achievements, the saved game), both multiplatform
- Gradle version catalog (`gradle/libs.versions.toml`)
- Android: minSdk 26, targetSdk / compileSdk 37

### Structure

All the code is under `app/`, split by platform:

```
app/
  shared/          platform-neutral Kotlin Multiplatform library - the game, its UI, its storage
  android/         the Android app that packages it
  baselineprofile/ the Baseline Profile journey and benchmarks
  ios/             (to come) the Xcode project for the iOS app
  licensing/       licence records for every third-party library and asset either platform ships
```

```
app/shared/src/commonMain/kotlin/net/zodac/dicefive/    everything platform-neutral
  model/            game domain types: GameState, PlayerState/PlayerConfig, Die/DieColour,
                    ScoreCategory, GameMode (every per-mode rule), Achievement, modifiers
  game/             pure rules engine: DiceScoring, ScoreCalculator (joker rule), GameEngine
                    (roll/hold/score/turn-advance reducers), TieBreak, AchievementEngine,
                    AiTurnPlayer (and the perfect-play table), AiNameGenerator
  data/
    scores/         Room: ScoreEntry, ScoreDao, AppDatabase, ScoreRepository
    achievements/   DataStore: AchievementStore/AchievementsRepository, AchievementEvents
    settings/       DataStore: SettingsRepository, SavedStyles
    game/           DataStore: the in-progress game, as JSON
  online/           the seam for an online game service (not yet connected to one)
  app/              AppContainer, BuildInfo - what each platform's entry point builds
  platform/         PlatformServices (sound, haptics, accelerometer, messages, licences) -
                    the interfaces each platform implements
  navigation/       Screen route constants + DiceFiveNavHost
  ui/
    DiceFiveApp.kt  the root composable every platform hosts
    common/         shared chrome: backdrop, scaffold, dialogs, pickers, scrollbars, formatting
    menu/           MenuScreen (Play/Achievements/Styles/Leaderboard/Statistics/Rules/Settings)
    setup/          GameSetupScreen (players, game mode, modifiers)
    game/           GameScreen + GameViewModel, the board, dice tray and cup, Game Over
    game/style/     the table art: every dice, cup, mat, background and frame style
    scores/         ScoresScreen + ScoresViewModel (the Leaderboard)
    statistics/     StatisticsScreen + StatisticsViewModel
    achievements/   AchievementsScreen + AchievementsViewModel, AchievementBannerHost
    styles/         StylesScreen (pick and preview the table art)
    rules/          RulesScreen
    settings/       SettingsScreen + SettingsViewModel, About, Licences
    theme/          Compose theme (colour)
app/shared/src/iosMain/kotlin/net/zodac/dicefive/   iOS
  MainViewController.kt   the iOS entry point
  device/           iOS PlatformServices (AVAudioPlayer, UIKit haptics, CoreMotion), storage
app/android/src/main/kotlin/net/zodac/dicefive/              the Android app
  MainActivity.kt   hosts DiceFiveApp
  device/           Android PlatformServices (SoundPool, Vibrator, sensors, the licence
                    TextView) + AndroidAppContainer
```

`.claude/DESIGN.md` describes how the app works and the decisions behind it; the other files in `.claude/` cover the
UI, game modes, table art, internationalisation and the rest.

### Build

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

`testDebugUnitTest` also compiles the shared module for iOS, which is what keeps its common code free of Android and
JVM APIs. Building and running the iOS app itself needs macOS.

### Commits and releases

Every commit's first line must be `[Category] Short description`, e.g. `[Gameplay] Add the Tricolour game mode` -
later lines are free-form. A `commit-msg` hook enforces it; enable it once per clone:

```
./scripts/install-git-hooks.sh
```

Every push to `main` publishes a GitHub release for the version in `VERSION`. Its description is `RELEASE_NOTES.md` (a
hand-written summary - fill it in before pushing a release you want described), followed by every commit since the
previous release, grouped by category. Preview that list with `./scripts/release-changelog.sh`. After releasing, the
workflow bumps `VERSION` and empties `RELEASE_NOTES.md` for the next one.

## License

Copyright (c) 2026 zodac.net. **All rights reserved** - DiceFive's own code and artwork are proprietary. This
repository is public so the source can be read; that does not grant permission to copy, modify, redistribute or
publish it. See [`LICENSE`](LICENSE).

Third-party components are not covered by that notice and remain under their own licenses - the libraries the app is
built with, the Sora and MathJax TeX fonts (SIL Open Font License 1.1), and the sound effects (modified Freesound
recordings under CC0 1.0 and CC BY 4.0). Each is listed, with its license text, in the app under
**Settings > Licences**; the records behind that list live in [`app/licensing/`](app/licensing/).
