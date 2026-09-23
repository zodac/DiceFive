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
                    Die, ScoreCategory, GameType, PlayerType, Difficulty, TurnPhase
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
    setup/          GameSetupScreen (player count/type/name, game type)
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

## Build

```
./gradlew assembleDebug
./gradlew test
```
