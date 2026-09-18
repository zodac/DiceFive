# DiceFive

Android DiceFive game - a local (no netplay) Yahtzee-based dice game for
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
  game/             pure Yahtzee rules engine (no Android dependencies):
                    YahtzeeScoring, ScoreCalculator (joker rule), GameEngine
                    (roll/hold/score/turn-advance reducers), AiTurnPlayer,
                    AiNameGenerator
  data/
    scores/         Room: ScoreEntry, ScoreDao, AppDatabase, ScoreRepository
    settings/       DataStore: Theme, SettingsRepository
  navigation/       Screen route constants + DiceFiveNavHost
  ui/
    menu/           MenuScreen (Play/Scores/Achievements/Settings/About)
    setup/          GameSetupScreen (player count/type/name, game type)
    game/           GameScreen + GameViewModel (setup form + live game state,
                    AI auto-play), DiceRow, ScorecardView
    scores/         ScoresScreen + ScoresViewModel (paginated leaderboard)
    achievements/   AchievementsScreen (placeholder - TBC)
    settings/       SettingsScreen + SettingsViewModel (theme picker)
    about/          AboutScreen (GitHub link)
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
