# DiceFive

Android DiceFive game - a local (no netplay) five-dice scorecard game for
1-4 players, human and/or AI.

## Stack

- Kotlin, Jetpack Compose, Material 3, Navigation Compose
- MVVM (`ViewModel` + `StateFlow`)
- Room (score history) + Jetpack DataStore Preferences (settings)
- Single `:app` module, Gradle version catalog (`gradle/libs.versions.toml`)
- minSdk 26, targetSdk / compileSdk 35

## Structure

```
app/src/main/kotlin/net/zodac/dicefive/
  model/            game domain types: GameState, PlayerState/PlayerConfig,
                    Die/DieColour, ScoreCategory, GameMode (every per-mode rule:
                    dice, rolls, scorecard, bonuses, max score), PlayerType,
                    Difficulty, TurnPhase
  game/             pure rules engine (no Android dependencies):
                    DiceScoring, ScoreCalculator (joker rule), GameEngine
                    (roll/hold/score/turn-advance reducers), AchievementEngine,
                    AiTurnPlayer, AiNameGenerator
  data/
    scores/         Room: ScoreEntry, ScoreDao, AppDatabase, ScoreRepository
    achievements/   DataStore: AchievementStore/AchievementsRepository, AchievementEvents
    settings/       DataStore: Theme, SettingsRepository
  navigation/       Screen route constants + DiceFiveNavHost
  ui/
    menu/           MenuScreen (Play/Scores/Achievements/Styles/Settings)
    setup/          GameSetupScreen (player count/type/name, game mode, turn timer)
    game/           GameScreen + GameViewModel (setup form + live game state,
                    AI auto-play), DiceRow, ScorecardView
    scores/         ScoresScreen + ScoresViewModel (paginated leaderboard)
    achievements/   AchievementsScreen + AchievementsViewModel, AchievementBannerHost
    styles/         StylesScreen (preview tiles for dice/cup/mat styles)
    settings/       SettingsScreen + SettingsViewModel (theme picker, version + GitHub link)
    theme/          Compose theme (color, typography)
  MainActivity.kt
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
./gradlew test
```
