# DiceFive

Android DiceFive game.

## Stack

- Kotlin, Jetpack Compose, Material 3
- MVVM (`ViewModel` + `StateFlow`)
- Single `:app` module, Gradle version catalog (`gradle/libs.versions.toml`)
- minSdk 26, targetSdk / compileSdk 35

## Structure

```
app/src/main/kotlin/net/zodac/dicefive/
  model/       game state and domain types (dice, score categories)
  ui/game/     screen composable + ViewModel
  ui/theme/    Compose theme (color, typography)
  MainActivity.kt
```

This is a skeleton: the scorecard, dice rolling/holding logic, and turn flow are
not implemented yet. See project discussion for the phased delivery plan.

## Build

```
./gradlew assembleDebug
./gradlew test
```
