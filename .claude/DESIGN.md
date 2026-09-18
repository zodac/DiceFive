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
- `./gradlew compileDebugAndroidTestKotlin` — androidTest sources compile
  against the current screens/ViewModels.
- **No emulator available in this sandbox**: no `emulator` package, no AVD
  system images, no connected device, and `/dev/kvm` alone isn't enough to
  stand one up without a multi-GB download outside this pre-baked image.
  So there is no manual/screenshot smoke test per phase — verification is
  compile + full unit test coverage of the pure logic (`game`/`model`
  packages) plus careful reading of each screen's Compose code. If the user
  runs this on a real device/emulator later and finds a UI issue, fix it
  then.

---

## Phases

### Phase 0 — Build setup
- [x] Add Navigation Compose, Room (+ KSP), DataStore dependencies to
      `gradle/libs.versions.toml`, `app/build.gradle.kts`,
      `settings.gradle.kts` / root `build.gradle.kts`.
- [x] Confirm `./gradlew assembleDebug` still builds with no app changes yet
      (dependency wiring only). `./gradlew testDebugUnitTest` also green.

### Phase 1 — Domain models & pure game logic
- [x] `model/GameType.kt`, `model/PlayerType.kt`, `model/Difficulty.kt`,
      `model/PlayerConfig.kt`, `model/PlayerState.kt`, `model/TurnPhase.kt`.
      Renamed `ScoreCategory.FIVE_OF_A_KIND` → `YAHTZEE`.
- [x] Rewrite `model/GameState.kt` for multi-player/turn/phase shape.
- [x] `game/YahtzeeScoring.kt` — per-category scoring functions.
- [x] `game/ScoreCalculator.kt` — upper bonus (via `PlayerState`) + Yahtzee
      joker rule (available categories + scoring + bonus-chip eligibility).
- [x] `game/GameEngine.kt` — newGame/rollDice/toggleHold/commitScore
      reducers (commitScore calls advanceTurn internally).
- [x] `game/AiTurnPlayer.kt` — basic auto-play strategy + pure `playTurn`
      helper for tests.
- [x] `game/AiNameGenerator.kt` — themed name pool (12 names).
- [x] Unit tests: `YahtzeeScoringTest` (9), `ScoreCalculatorTest` (4, joker
      rule branches), `GameEngineTest` (10), `AiTurnPlayerTest` (3),
      `AiNameGeneratorTest` (2). All 30 tests pass; `assembleDebug` green.
- [x] Update `GameStateTest` for new shape.

### Phase 2 — Navigation scaffold & Menu
- [x] `navigation/Screen.kt` route constants.
- [x] `navigation/DiceFiveNavHost.kt` with all destinations wired: menu,
      nested "play" graph (play/setup -> play/game), scores, achievements,
      settings, about. Setup/scores/achievements/settings/about are thin
      stub screens for now (each notes which later phase fills it in).
- [x] `ui/menu/MenuScreen.kt` (Play/Scores/Achievements/Settings/About),
      "DiceFive" as the header text.
- [x] `MainActivity.kt` hosts `DiceFiveNavHost` instead of `GameScreen`
      directly.
- [x] `MainActivityTest` needed no change - menu header text is still
      "DiceFive" so `onNodeWithText("DiceFive")` keeps passing; confirmed
      `compileDebugAndroidTestKotlin` + `assembleDebug` + unit tests green.
      (The "play" nested graph doesn't yet share a scoped `GameViewModel`
      across setup/game - that lands in Phase 3 when setup actually has
      state worth sharing.)

### Phase 3 — Game setup screen
- [x] `GameViewModel`: `GameSetupState`/`PlayerSetupSlot` (player count 1-4,
      per-slot type/name/difficulty, game type), plus a nullable
      `game: StateFlow<GameState?>` populated by `startGame()`. Slot 1 is
      hardcoded Human (setPlayerType rejects slot 1).
- [x] `ui/setup/GameSetupScreen.kt`: player count stepper, per-slot
      Human/AI toggle (slot 1 fixed) + name field or disabled difficulty
      row, Classic/Extended (disabled) radio group, Start Game button.
- [x] `DiceFiveNavHost` now scopes one `GameViewModel` to the "play" nav
      graph entry (`navController.getBackStackEntry(Screen.PLAY_GRAPH)`),
      shared by both `play/setup` and `play/game` — Start Game populates
      `game`, then navigates; `GameScreen` reads the same instance's `game`
      state. `GameScreen` itself is still a minimal placeholder (full UI in
      Phase 4).
- [x] `assembleDebug`, `compileDebugAndroidTestKotlin`, and
      `testDebugUnitTest` all green.

### Phase 4 — Game screen & turn flow
- [x] `ui/game/DiceRow.kt` (tappable hold toggles), `ui/game/ScorecardView.kt`
      (category x player grid; current human player's open, available
      boxes are tappable score-preview buttons, others show a dash).
- [x] `GameScreen.kt`: current player banner, dice tray, roll button
      ("Roll (n left)"), scorecard grid, game-over ranked results summary
      with a Back to Menu button.
- [x] `GameViewModel`: `rollDice()`/`toggleHold()`/`commitScore()` call
      `GameEngine`, gated to a no-op unless it's the human's turn.
- [x] AI auto-play: `maybeStartAiTurn()` launches a `viewModelScope`
      coroutine (600ms/step) that rolls 3x then scores via `AiTurnPlayer`,
      re-checking itself afterward so back-to-back AI players chain
      automatically.
- [ ] Game-over → persist human scores: **deferred to Phase 5** (no
      `ScoreRepository` yet) - `GameScreen` already renders the ranked
      results screen once `isGameOver`.
- [x] New tests: `GameViewModelTest` (4 tests, incl. AI auto-play via
      `kotlinx-coroutines-test`'s `StandardTestDispatcher` +
      `advanceUntilIdle`). `assembleDebug`, `compileDebugAndroidTestKotlin`,
      `testDebugUnitTest` all green.

### Phase 5 — Persistence
- [x] `data/settings/Theme.kt`, `data/settings/SettingsRepository.kt`
      (DataStore: `theme` + per-slot `playerNameFor(slot)`/`setPlayerName`).
- [x] `data/scores/ScoreEntry.kt`, `ScoreDao.kt`, `AppDatabase.kt`,
      `ScoreRepository.kt` (Room).
- [x] `GameViewModel` now takes `scoreRepository`/`settingsRepository` as
      **nullable constructor params** (not `AndroidViewModel`) so it stays
      constructible/testable on a plain JVM with no `Context`; a
      `GameViewModel.factory(context)` (via `viewModelFactory { initializer {...} }`)
      builds the real ones and is passed at both `play/setup` and
      `play/game` call sites in `DiceFiveNavHost` (`LocalContext.current`).
      `startGame()` persists human slot names; `setGameState()` persists
      each human player's final score exactly once, on the not-over ->
      over transition.
- [x] Unit tests: `ScoreRepositoryTest` (3 tests) against a hand-written
      `FakeScoreDao` rather than a real Room/SQLite instance - no
      Robolectric/emulator available in this sandbox (see Verification).
      Room's own `@Query` SQL (ORDER BY/LIMIT/OFFSET/COUNT) is simple and
      low-risk; worth double-checking on a real device later.
- [x] `assembleDebug`, `compileDebugAndroidTestKotlin`, `testDebugUnitTest`
      all green (37 unit tests total).

### Phase 6 — Scores screen
- [x] `ui/scores/ScoresViewModel.kt`: `ScoresUiState` (entries/pageIndex/
      totalCount/isLoading + derived totalPages/hasNext/hasPrevious),
      nullable `ScoreRepository` ctor param + `factory(context)` (same
      pattern as `GameViewModel`).
- [x] `ui/scores/ScoresScreen.kt`: rank/name/score `LazyColumn` (100/page),
      Previous/Next + "Page X of Y" controls, empty state. Each row is
      wrapped in a Material3 `TooltipBox` (`PlainTooltip`, built-in
      long-press-to-show on touch) showing the game's date - the date
      column itself stays hidden per the clarified requirement. Date
      formatted with `java.time` (available unshimmed at minSdk 26).
- [x] Wired into `DiceFiveNavHost` via `ScoresViewModel.factory(LocalContext.current)`.
- [x] `assembleDebug`, `compileDebugAndroidTestKotlin`, `testDebugUnitTest`
      all green (37 unit tests, unchanged - no new pure-logic surface here
      beyond what Phase 5's `ScoreRepositoryTest` already covers).

### Phase 7 — Settings screen
- [x] `ui/settings/SettingsViewModel.kt`: `theme: StateFlow<Theme>` (default
      SYSTEM), `setTheme()`; same nullable-repository + `factory(context)`
      pattern.
- [x] `ui/settings/SettingsScreen.kt`: Light/Dark/System radio group.
- [x] `MainActivity` now builds a `SettingsRepository` directly (not via a
      ViewModel - it's a simple top-level read, and Activity recreation
      re-reads DataStore fresh anyway) and resolves `darkTheme` for
      `DiceFiveTheme`: LIGHT->false, DARK->true, SYSTEM->`isSystemInDarkTheme()`.
- [x] `assembleDebug`, `compileDebugAndroidTestKotlin`, `testDebugUnitTest`
      all green (37 unit tests, unchanged).

### Phase 8 — Achievements screen (stub)
- [x] `ui/achievements/AchievementsScreen.kt` placeholder screen wired into
      nav - already built and wired in Phase 2 (it never needed a
      ViewModel or later-phase rework), so there's nothing further to do
      here. No trigger/persistence infrastructure, per the task's own
      "TBC later".

### Phase 9 — About screen
- [x] `ui/about/AboutScreen.kt`: app name, `BuildConfig.VERSION_NAME`, and a
      clickable "View on GitHub" text opening
      `https://github.com/zodac/dicefive` via `LocalUriHandler`. Enabled
      `buildFeatures.buildConfig = true` in `app/build.gradle.kts` for the
      version string.
- [x] `assembleDebug`, `compileDebugAndroidTestKotlin`, `testDebugUnitTest`
      all green (37 unit tests, unchanged).

### Phase 10 — Final verification
- [ ] `./gradlew test` green.
- [ ] `./gradlew assembleDebug` green.
- [ ] Manual smoke pass via `run` skill (see Verification above).
- [ ] Update `README.md` structure section to match the final package
      layout.
