# DiceFive: Yahtzee game — design & phased plan

Status legend: `[ ]` not started · `[~]` in progress · `[x]` done

## Context

The repo started as a skeleton: a single `GameScreen` showing "DiceFive" and
"Rolls remaining: 3", a `GameState` with no scoring/turn logic, and no
navigation, persistence, or other screens. This document tracks the full v1
build: a menu-driven Android app implementing local (no netplay) Yahtzee for
1-4 players (human and AI), with score history, a settings screen (theme
only for now), a stub achievements screen, and an about screen linking to
the GitHub repo.

Update this file's checkboxes as work lands, so the build can be resumed or
revisited across sessions without re-deriving the plan.

## Decisions (locked in)

- **Persistence**: Room DB for score history, Jetpack DataStore
  (Preferences) for settings (theme) and remembered human player names.
- **Navigation**: Navigation Compose (`NavHost`), with a nested "play" graph
  sharing one scoped `GameViewModel` across the setup and in-game screens.
- **AI v1 behavior**: difficulty selector is present but disabled/greyed
  out. AI always rolls all 3 times holding nothing, then scores the
  highest-value open category on its last roll. Real difficulty logic is
  future work.
- **Scores screen**: one global leaderboard (not split by player or game
  type), sorted score-descending, paginated 100/page. No date column — date
  is shown via a long-press tooltip.
- **About link**: `https://github.com/zodac/dicefive`.
- **Game type**: only `CLASSIC` is playable in v1; `EXTENDED` exists as an
  enum value shown disabled in the UI.
- **Achievements**: v1 is a placeholder screen only — no trigger/persistence
  infrastructure yet (nothing defined to trigger on).

## New Gradle dependencies

- `androidx.navigation:navigation-compose`
- Room: `androidx.room:room-runtime`, `androidx.room:room-ktx`, plus
  `androidx.room:room-compiler` via the **KSP** plugin
  (`com.google.devtools.ksp`, version matched to Kotlin 2.0.20).
- `androidx.datastore:datastore-preferences`
- No kotlinx.serialization — settings are individual Preference keys, not
  serialized JSON.

## Package layout (target shape)

```
net.zodac.dicefive/
  MainActivity.kt                      — hosts NavHost, applies theme from SettingsRepository
  data/
    settings/
      Theme.kt                         — enum LIGHT/DARK/SYSTEM
      SettingsRepository.kt            — DataStore-backed: theme Flow, remembered player names (slots 1-4)
    scores/
      ScoreEntry.kt (Room @Entity)     — id, playerName, score, timestampEpochMillis
      ScoreDao.kt                      — pagedScores(limit, offset), count(), insert()
      AppDatabase.kt                   — Room database, singleton via Application
      ScoreRepository.kt               — wraps DAO, exposes page loads
  model/
    Die.kt                             — existing, unchanged
    ScoreCategory.kt                   — existing enum, unchanged
    GameType.kt                        — CLASSIC (usable), EXTENDED (placeholder, disabled in UI)
    PlayerType.kt                      — HUMAN, AI
    Difficulty.kt                      — EASY, MEDIUM, HARD (stored, UI disabled)
    PlayerConfig.kt                    — setup-time: slot, type, name, difficulty
    PlayerState.kt                     — in-game: name, type, difficulty, scorecard (Map<ScoreCategory, Int?>), yahtzeeBonusCount
    GameState.kt                       — rewritten: gameType, players: List<PlayerState>, currentPlayerIndex,
                                          dice: List<Die>, rollsRemaining, phase (AWAITING_ROLL / ROLLED), isGameOver
  game/
    YahtzeeScoring.kt                  — pure functions: score(category, dice) and isUpperBonusEligible etc.
    ScoreCalculator.kt                 — resolves a category pick against current scorecard incl. upper bonus (63+ => +35)
                                          and Yahtzee joker rule (extra Yahtzee => +100 bonus, forced placement rules)
    GameEngine.kt                      — pure reducer-style functions: rollDice, toggleHold, commitScore, advanceTurn
    AiTurnPlayer.kt                    — basic auto-play: roll x3 (no holds) then pick max-scoring open category
    AiNameGenerator.kt                 — static themed name pool, random pick without duplicates per game
  navigation/
    Screen.kt                          — sealed route constants (menu, play/setup, play/game, scores, achievements, settings, about)
    DiceFiveNavHost.kt                 — NavHost wiring, "play" nested graph shares GameViewModel via getBackStackEntry
  ui/
    menu/MenuScreen.kt                 — Play / Scores / Achievements / Settings / About buttons
    setup/
      GameSetupScreen.kt               — player count 1-4, per-slot human/AI + name field, game type radio (Extended disabled)
    game/
      GameScreen.kt                    — dice tray w/ hold toggles, roll button, scorecard grid, current player banner
      GameViewModel.kt                 — owns setup config AND live GameState; phase drives which screen renders;
                                          drives AI auto-play via viewModelScope coroutine with short delays between steps
      ScorecardView.kt / DiceRow.kt    — shared composables for the scorecard grid and dice display
    scores/
      ScoresScreen.kt                  — paginated table (100/page), long-press row shows date tooltip
      ScoresViewModel.kt               — talks to ScoreRepository, tracks current page
    achievements/AchievementsScreen.kt — thin placeholder ("Coming soon"), no trigger infrastructure yet
    settings/
      SettingsScreen.kt                — theme radio group (Light/Dark/System)
      SettingsViewModel.kt             — reads/writes SettingsRepository.theme
    about/AboutScreen.kt               — app name/version + GitHub link via UriHandler
    theme/                             — existing Theme.kt/Color.kt/Type.kt, unchanged except MainActivity now
                                          passes darkTheme resolved from the stored Theme setting
```

## Screen flow (Navigation Compose)

```
NavHost(start = "menu")
  "menu"                    -> MenuScreen
  navigation(route = "play", start = "play/setup") {
    "play/setup"            -> GameSetupScreen (GameViewModel scoped to "play" graph)
    "play/game"             -> GameScreen (same scoped GameViewModel)
  }
  "scores"                  -> ScoresScreen
  "achievements"            -> AchievementsScreen
  "settings"                -> SettingsScreen
  "about"                   -> AboutScreen
```

Sharing one `GameViewModel` across `play/setup` and `play/game` (scoped to
the parent "play" nav graph entry) avoids serializing player lists through
nav arguments or introducing a singleton holder.

## Game setup rules

- Player count stepper/selector: 1-4.
- Player 1 is always Human, locked (no type toggle) — 1P mode forces Human.
- Players 2-4: toggle Human/AI.
  - Human: text field for name, default `"Player N"`; prefilled from
    `SettingsRepository` if previously entered for that slot, saved back on
    change.
  - AI: no name field (generated at Start Game); a disabled difficulty
    selector (Easy/Medium/Hard) defaulting to Medium, greyed out.
- Game type: radio group, Classic selectable, Extended visible but disabled.
- "Start Game": builds initial `GameState` — generates AI names via
  `AiNameGenerator` (no duplicates within the game) at this point — and
  flips `GameViewModel` phase from CONFIGURING to PLAYING, navigating from
  `play/setup` to `play/game`.

## Yahtzee turn engine rules

- Start of turn: 5 dice, 3 rolls remaining, all unheld.
- Roll: rolls all non-held dice, decrements `rollsRemaining`.
- Hold: toggles a die's `isHeld` — only after ≥1 roll this turn and while
  `rollsRemaining > 0`.
- Score: player may pick any open `ScoreCategory` at any point once ≥1 die
  has been rolled this turn (not only after 3 rolls). `ScoreCalculator`
  computes the value including upper-section 63+ bonus (+35) and the
  Yahtzee joker rule (second-or-later Yahtzee always adds +100; if the
  Yahtzee box is already filled, score must go in the matching upper box if
  open, else any open lower box via joker free-fill, else zeroed into any
  open box).
- After scoring: advance to next player, reset dice/rolls/phase. If the new
  current player is AI, `GameViewModel` drives `AiTurnPlayer` through
  roll → roll → roll → score automatically (brief coroutine delays so it's
  visibly animated, not instant).
- Game ends when every player's scorecard is full; `GameViewModel` persists
  each **human** player's final total to `ScoreRepository` (one row per
  human player; AI scores are not saved) and sets `isGameOver = true` so
  `GameScreen` shows a results summary.

`YahtzeeScoring`/`GameEngine` are pure functions with no Android
dependencies — most unit tests live here.

## Scores screen

- `ScoreDao`: `ORDER BY score DESC LIMIT :limit OFFSET :offset`, plus a
  `COUNT(*)` query for total pages.
- `ScoresViewModel`: current page index (0-based), 100 rows/page, next/prev
  availability.
- `ScoresScreen`: scrollable rank/name/score table; long-press a row shows a
  popup/tooltip with the formatted date from `timestampEpochMillis`.

## Settings & theme

- `SettingsRepository.theme: Flow<Theme>` (LIGHT/DARK/SYSTEM, default
  SYSTEM), backed by DataStore Preferences.
- `MainActivity` collects this at the top and passes resolved
  `darkTheme: Boolean` into `DiceFiveTheme` — SYSTEM defers to
  `isSystemInDarkTheme()`.
- `SettingsScreen`: radio group writing back to the repository.

## Achievements & About

- `AchievementsScreen`: placeholder "Coming soon" empty state only.
- `AboutScreen`: app name + version (from `BuildConfig`), text link to
  `https://github.com/zodac/dicefive` opened via Compose's `UriHandler`.

## Tests

- Update `GameStateTest` for the new `GameState` shape (preserve intent: new
  game → 5 dice, 3 rolls).
- Update `MainActivityTest`: keep "DiceFive" as the `MenuScreen` header text
  so `onNodeWithText("DiceFive")` still passes.
- New: `YahtzeeScoringTest` (every category + upper bonus + joker rule
  branches), `GameEngineTest` (roll/hold/score/turn advance/game-over),
  `AiTurnPlayerTest` (always legal, max-value open category),
  `ScoreDaoTest`/`ScoreRepositoryTest` (pagination ordering, in-memory Room).

## Verification

- `./gradlew test` — all unit tests pass.
- `./gradlew assembleDebug` — builds with new dependencies (Room/KSP,
  Navigation, DataStore).
- Manual smoke check via the `run` skill: play a full 1P game to completion,
  verify score saved and shown on Scores screen; play a 2-4P game mixing
  Human/AI, confirm AI auto-play; toggle theme in Settings; confirm About
  link opens the GitHub repo.

---

## Phases

### Phase 0 — Build setup
- [x] Add Navigation Compose, Room (+ KSP), DataStore dependencies to
      `gradle/libs.versions.toml`, `app/build.gradle.kts`,
      `settings.gradle.kts` / root `build.gradle.kts`.
- [x] Confirm `./gradlew assembleDebug` still builds with no app changes yet
      (dependency wiring only). `./gradlew testDebugUnitTest` also green.

### Phase 1 — Domain models & pure game logic
- [ ] `model/GameType.kt`, `model/PlayerType.kt`, `model/Difficulty.kt`,
      `model/PlayerConfig.kt`, `model/PlayerState.kt`.
- [ ] Rewrite `model/GameState.kt` for multi-player/turn/phase shape.
- [ ] `game/YahtzeeScoring.kt` — per-category scoring functions.
- [ ] `game/ScoreCalculator.kt` — upper bonus + Yahtzee joker rule.
- [ ] `game/GameEngine.kt` — rollDice/toggleHold/commitScore/advanceTurn
      reducers.
- [ ] `game/AiTurnPlayer.kt` — basic auto-play strategy.
- [ ] `game/AiNameGenerator.kt` — themed name pool.
- [ ] Unit tests: `YahtzeeScoringTest`, `GameEngineTest`, `AiTurnPlayerTest`.
- [ ] Update `GameStateTest` for new shape.

### Phase 2 — Navigation scaffold & Menu
- [ ] `navigation/Screen.kt` route constants.
- [ ] `navigation/DiceFiveNavHost.kt` with all destinations wired (screens
      can be stubs initially).
- [ ] `ui/menu/MenuScreen.kt` (Play/Scores/Achievements/Settings/About).
- [ ] `MainActivity.kt` hosts `DiceFiveNavHost` instead of `GameScreen`
      directly.
- [ ] Update `MainActivityTest` to match ("DiceFive" on menu header).

### Phase 3 — Game setup screen
- [ ] `GameViewModel` CONFIGURING phase: player count, per-slot type/name/
      difficulty, game type selection state.
- [ ] `ui/setup/GameSetupScreen.kt` UI per the setup rules above.
- [ ] Wire "Start Game" → build initial `GameState` → phase flips to
      PLAYING → nav to `play/game`.

### Phase 4 — Game screen & turn flow
- [ ] `ui/game/DiceRow.kt`, `ui/game/ScorecardView.kt`.
- [ ] `GameScreen.kt`: dice tray, hold toggles, roll button, scorecard grid,
      current player banner, game-over results summary.
- [ ] `GameViewModel` PLAYING phase: wire roll/hold/score actions to
      `GameEngine`.
- [ ] AI auto-play coroutine driver in `GameViewModel` using
      `AiTurnPlayer`.
- [ ] Game-over → persist human scores (stub call until Phase 5 repo
      exists, or sequence Phase 5 first if easier).

### Phase 5 — Persistence
- [ ] `data/settings/Theme.kt`, `data/settings/SettingsRepository.kt`
      (DataStore).
- [ ] `data/scores/ScoreEntry.kt`, `ScoreDao.kt`, `AppDatabase.kt`,
      `ScoreRepository.kt` (Room).
- [ ] Wire `GameViewModel` game-over path to `ScoreRepository` (human scores
      only) and to `SettingsRepository` (remembered player names).
- [ ] Unit tests: `ScoreDaoTest`/`ScoreRepositoryTest` (in-memory Room).

### Phase 6 — Scores screen
- [ ] `ui/scores/ScoresViewModel.kt` (pagination state).
- [ ] `ui/scores/ScoresScreen.kt` (table, long-press date tooltip, page
      controls).

### Phase 7 — Settings screen
- [ ] `ui/settings/SettingsViewModel.kt`.
- [ ] `ui/settings/SettingsScreen.kt` (theme radio group).
- [ ] `MainActivity` resolves `darkTheme` from `SettingsRepository.theme`.

### Phase 8 — Achievements screen (stub)
- [ ] `ui/achievements/AchievementsScreen.kt` placeholder screen wired into
      nav.

### Phase 9 — About screen
- [ ] `ui/about/AboutScreen.kt` with GitHub link
      (`https://github.com/zodac/dicefive`).

### Phase 10 — Final verification
- [ ] `./gradlew test` green.
- [ ] `./gradlew assembleDebug` green.
- [ ] Manual smoke pass via `run` skill (see Verification above).
- [ ] Update `README.md` structure section to match the final package
      layout.
