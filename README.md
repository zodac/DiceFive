# DiceFive

Android DiceFive game - a local (no netplay) five-dice scorecard game for
1-4 players, human and/or AI.

## Stack

- Kotlin Multiplatform: the game, its UI and its persistence are shared Kotlin, built for Android
  today and compiled for iOS on every build (the iOS app itself is still to come - see
  `.claude/IOS_SUPPORT.md`)
- Compose Multiplatform, Material 3, Navigation Compose
- MVVM (`ViewModel` + `StateFlow`)
- Room (score history) + DataStore Preferences (settings), both multiplatform
- Gradle version catalog (`gradle/libs.versions.toml`)
- Android: minSdk 26, targetSdk / compileSdk 37

## Structure

All the code is under `app/`, split by platform:

```
app/
  shared/     platform-neutral Kotlin Multiplatform library - the game, its UI, its storage
  android/    the Android app that packages it
  ios/        (to come) the Xcode project for the iOS app
  licensing/  licence records for every third-party library and asset either platform ships
```

```
app/shared/src/commonMain/kotlin/net/zodac/dicefive/    everything platform-neutral
  model/            game domain types: GameState, PlayerState/PlayerConfig,
                    Die/DieColour, ScoreCategory, GameMode (every per-mode rule:
                    dice, rolls, scorecard, bonuses, max score), PlayerType,
                    Difficulty, TurnPhase
  game/             pure rules engine: DiceScoring, ScoreCalculator (joker rule),
                    GameEngine (roll/hold/score/turn-advance reducers),
                    AchievementEngine, AiTurnPlayer, AiNameGenerator
  data/
    scores/         Room: ScoreEntry, ScoreDao, AppDatabase, ScoreRepository
    achievements/   DataStore: AchievementStore/AchievementsRepository, AchievementEvents
    settings/       DataStore: SettingsRepository
    game/           DataStore: the in-progress game, as JSON
  app/              AppContainer, BuildInfo - what each platform's entry point builds
  platform/         PlatformServices (sound, haptics, accelerometer, messages, licences) -
                    the interfaces each platform implements
  navigation/       Screen route constants + DiceFiveNavHost
  ui/
    DiceFiveApp.kt  the root composable every platform hosts
    menu/           MenuScreen (Play/Achievements/Styles/Leaderboard/Statistics/Rules/Settings)
    setup/          GameSetupScreen (player count/type/name, game mode, turn timer)
    game/           GameScreen + GameViewModel (setup form + live game state,
                    AI auto-play), dice tray, scorecard
    scores/         ScoresScreen + ScoresViewModel (paginated leaderboard)
    statistics/     StatisticsScreen + StatisticsViewModel
    achievements/   AchievementsScreen + AchievementsViewModel, AchievementBannerHost
    styles/         StylesScreen (preview tiles for dice/cup/mat styles)
    settings/       SettingsScreen + SettingsViewModel, LicensesDialog
    theme/          Compose theme (colour)
app/shared/src/iosMain/kotlin/net/zodac/dicefive/   iOS
  MainViewController.kt   the iOS entry point
  device/           iOS PlatformServices (AVAudioPlayer, UIKit haptics, CoreMotion), storage
app/android/src/main/kotlin/net/zodac/dicefive/              the Android app
  MainActivity.kt   hosts DiceFiveApp
  device/           Android PlatformServices (SoundPool, Vibrator, sensors, the licence
                    TextView) + AndroidAppContainer
```

See `.claude/DESIGN.md` for the full feature scope, design decisions, and
phased build log.

## Commits and releases

Every commit's first line must be `[Category] Short description`, e.g.
`[Gameplay] Add the Tricolour game mode` - later lines are free-form. A `commit-msg` hook enforces
it; enable it once per clone:

```
./scripts/install-git-hooks.sh
```

Every push to `main` publishes a GitHub release for the version in `VERSION`. Its description is
`RELEASE_NOTES.md` (a hand-written summary - fill it in before pushing a release you want
described), followed by every commit since the previous release, grouped by category. Preview that
list with `./scripts/release-changelog.sh`. After releasing, the workflow bumps `VERSION` and empties
`RELEASE_NOTES.md` for the next one.

## Build

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

`testDebugUnitTest` also compiles the shared module for iOS, which is what keeps its common code
free of Android and JVM APIs. Building and running the iOS app itself needs macOS.

## License

Copyright (c) 2026 zodac.net. **All rights reserved** - DiceFive's own code and artwork are
proprietary. This repository is public so the source can be read; that does not grant permission
to copy, modify, redistribute or publish it. See [`LICENSE`](LICENSE).

Third-party components are not covered by that notice and remain under their own licenses - the
libraries the app is built with, the Sora and MathJax TeX fonts (SIL Open Font License 1.1), and the sound effects
(modified Freesound recordings under CC0 1.0 and CC BY 4.0). Each is listed, with
its license text, in the app under **Settings > Licences**; the records behind that list
live in [`app/licensing/`](app/licensing/).
