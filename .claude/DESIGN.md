# DiceFive: Yahtzee game — design & phased plan

Status legend: `[ ]` not started · `[~]` in progress · `[x]` done

## Context

The repo started as a skeleton: a single `GameScreen` showing "DiceFive" and
"Rolls remaining: 3", a `GameState` with no scoring/turn logic, and no
navigation, persistence, or other screens. This document tracks the full v1
build: a menu-driven Android app implementing local (no netplay) Yahtzee for
1-4 players (human and AI), with score history, a settings screen (theme,
plus a version + GitHub link footer), and a stub achievements screen.

Update this file's checkboxes as work lands, so the build can be resumed or
revisited across sessions without re-deriving the plan.

The interface itself is documented separately in `.claude/UI.md` — the
Material 3 colour system, the shared chrome in `ui/common/`, and the layout
decisions behind it. Read that before changing anything visual.

## Decisions (locked in)

- **Persistence**: Room DB for score history, Jetpack DataStore
  (Preferences) for settings (theme) and remembered human player names.
- **Navigation**: Navigation Compose (`NavHost`), with a nested "play" graph
  sharing one scoped `GameViewModel` across the setup and in-game screens.
- **AI difficulty**: the setup-screen selector is live (Easy/Medium/Hard per AI slot). `AiTurnPlayer`
  strategy per tier - EASY: never holds individual dice, but stops rerolling (all together) once
  any open category scores above zero, then scores the highest-value open category. MEDIUM: holds
  dice by rule of thumb (a forming straight, else the largest matching
  group) between rolls; ties in category choice toward an upper box "on pace" for the 63-point
  bonus. It also doesn't always spend every roll: a fixed set of "good enough" shapes - Full House,
  Large Straight, a Small Straight once Large Straight is no longer open, or three-plus dice on a
  4/5/6 with that upper box still open - gets banked immediately instead of gambled on a reroll.
  HARD: exhaustively evaluates all 32 hold/reroll subsets each roll via exact expected value
  (every possible outcome of the freed dice, weighted equally), and picks the open category whose
  score most exceeds its own average value on a single random roll (so a rare category like Full
  House can beat a nominally higher-scoring but easy-to-satisfy-later one like Chance).
- **Leaderboard screen**: one global leaderboard (not split by player or game
  type), sorted score-descending, paginated 50/page (originally 100; halved
  alongside a compact row style, so a page is a shorter scroll). No date
  column — date is shown via a long-press tooltip.
- **Statistics screen**: one card per distinct human player *name* (a rename starts a new "user",
  same as the leaderboard). Win/loss and the current win streak are only tracked for multiplayer
  games — a solo game has nobody to beat, so it's recorded with a null outcome that counts toward
  games played but neither wins, losses, nor breaks a streak, mirroring the existing
  `WIN_STREAK`/`GAMES_WON` achievement counters. Rows recorded before this feature shipped get the
  same null treatment, since their outcome was never captured.
- **GitHub link**: shown with the app version, in a footer at the bottom of
  the Settings screen (there is no separate About screen); `https://github.com/zodac/DiceFive`.
- **Table art styles**: `ui/game/style/DiceStyle`/`DiceCupStyle`/`TableBackground` are
  independently swappable and independently persisted (`SettingsRepository.diceStyleId`/
  `diceCupStyleId`/`tableBackgroundId`, plain string ids, defaulted and resolved through each
  category's own catalog object in `ui/game/style/StyleCatalog.kt` - `DiceStyles`/`DiceCupStyles`/
  `TableBackgrounds`, each a `byId` lookup falling back to that category's `default`). The Styles
  screen (`ui/styles/`) is the picker; `GameScreen` reads the three ids and builds the active
  `GameVisualTheme` from them on every recomposition. Shipped skins: Ivory/Leather/Midnight Felt
  (the originals) and a second, fully independent "fire" skin per category - a red die with orange
  pips (`FireDiceStyle`), a plain straight-tapered cup with two flame licks (`FireDiceCupStyle`,
  no flared foot or brass bands, unlike Leather), and a red felt/tray background trimmed with a
  band of pointed flame tongues along the tray's bottom third (`FireTableBackground`, drawn as two
  overlapping wavy bands offset half a cycle apart - see its `flameTongueLift` doc comment for why
  a plain sine reads as a dune, not flame). Picking "fire" for one category doesn't imply the
  others - a fire die can sit in a leather cup on a midnight felt mat.
- **Game type**: only `CLASSIC` is playable in v1; `EXTENDED` exists as an
  enum value shown disabled in the UI.
- **Achievements**: 84 of them, **player 1 only** (`state.players[0]`, "You" on the setup
  screen) rather than any human at the table - the one exception is the ledger (the score-band and
  career-points achievements at the tail of `AchievementCategory.COLLECTION`), which stays measured
  against the leaderboard as a whole, i.e. every human who has played on this device, not just
  player 1 - see the "Player 1 only" phase entry below for why, and for the Google Play Games
  question this raises. Local only for now but shaped so each maps onto a Google Play Games
  achievement later (see Phase 13). One (`CHEATER_CHEATER`) is secret: `Achievement.isSecret`
  keeps it out of the list - and its unlocked/total counts - until it's
  actually earned, since seeing "finish with the maximum possible score"
  sitting on the to-do list would rather give the game away.
  Table-art style ones (`STYLE_DICE`/`STYLE_CUP`/`STYLE_BACKGROUND`) are judged at game START, not
  the end - the player picked the style before a die was ever rolled, so there's no reason to make
  them finish playing to hear about it. This is its own evaluation entry point,
  `AchievementEngine.evaluateAtGameStart(context: GameStartContext, ...)`, alongside `evaluate`/
  `evaluateInProgress`/`unlockNow` - added when the first game-start achievement showed the ad hoc
  `unlockAchievements(buildSet {...})` GameViewModel had been doing inline wasn't going to scale to
  a second one: **the engine, not the call site, decides what counts as earned**, everywhere else
  in this file, and game-start achievements are no exception. `GameStartContext` carries a plain
  boolean per style category rather than a style id, so `game/`'s pure engine never has to import
  the UI-layer style catalog just to compare a string; `GameViewModel.checkGameStartAchievements`
  (called from `startGame`/`resumeGame`) is only responsible for the async `SettingsRepository`
  read that builds that context and for persisting/announcing whatever the engine decides. A future
  game-start achievement is a new field on `GameStartContext` and a line in `evaluateAtGameStart` -
  never a new ad hoc check at a `GameViewModel` call site. `WASTED_5X` ("Roll a 5x but score a zero
  with it anyway") is checked at the moment of `commitScore`, not from the finished scorecard: a
  filled-in `FIVE_OF_A_KIND` box already answers "was it ever a real 5x", but not "were the dice a
  5x right when THIS zero went in" - that needs the dice and the chosen category together, which
  only `GameViewModel` sees before the commit changes anything. `DOUBLE_TON`/`TRIPLE_TON` (exactly
  200/300) sit right after `SCORE_200`/`SCORE_300` on the Scoring ladder, not before - despite the
  same threshold, hitting it exactly is harder than clearing it by any margin. `EXACT_CHANGE` needs
  every upper box scored with precisely its own pip count (1 in Ones, ..., 6 in Sixes) in one game.
  A second, much larger batch (25 more) covers interaction patterns a finished scorecard can't
  reconstruct at all - rolls, holds and commits watched turn-by-turn - so `GameViewModel` grew a
  large block of per-turn tracking state (reset in `resetPerTurnTracking`, called from `rollDice`
  whenever `rollsRemaining == FULL_ROLLS_REMAINING`) alongside the existing per-game fields.
  Highlights, and the traps found building them:
  - **`COMMITMENT_ISSUES` is "hold N of one number (1-4, not a 5x), let it go, then hold M of a
    different number (1-4) and score it"** - not specifically a pair, despite the name (a single
    die or a four of a kind both count, as long as every held die shares that one value and nothing
    else is held alongside it). Its state machine has to survive releasing a held group ONE TAP AT
    A TIME, since the real UI can only unhold one die per tap (each is its own tap target): "hold a
    group, then let it go" passes through intermediate states that aren't themselves an exact group
    of the SAME size - a first version treated any such intermediate state as "not holding an exact
    group anymore" and cleared the tracked value right there, before the last tap ever arrived, so
    the achievement could never fire from real play (a test written to release the group the same
    way a finger would caught this immediately). The fix tracks the group's own die indices
    (`pendingCommitmentGroupIndices`) and only clears the pending value when the held set stops
    being a SUBSET of that group - shrinking down one die at a time doesn't count as "different",
    only holding something outside the original group does.
    A held group of all 5 dice sharing a value is a real 5x, not indecision, and must never become
    the baseline the next group is compared against - even once it's *released* back down through
    4, 3, 2, 1 held, which looks identical, held-set-wise, to shrinking a plain 4-group. A
    `commitmentGroupTainted` flag set the instant the held set ever reaches all 5 (and cleared only
    once the held set returns to genuinely empty, or a fresh, unrelated group starts) is what tells
    the empty-handed moment not to credit that release.
  - **`TWICE_IN_A_LIFETIME`/streak-style per-player state is keyed by player index, not "any
    human"**, because "two turns in a row" means one player's own consecutive turns, which in a
    2+ player game are never chronologically adjacent (opponents go in between) - only that
    player's own map entry is ever written or read, so an intervening AI or other human's turn
    can't accidentally reset or fake the streak.
  - **`IMPATIENT`/`NATURALLY_GIFTED` track a `Set<Int>` of player indices that used an extra roll**,
    not a boolean - a human index absent from that set means every one of THEIR turns was a single
    roll, checked at game end against `state.players` by index rather than by name (names aren't
    unique, human vs AI matters, and the set is built purely from `GameViewModel.rollDice` noticing
    `rollsRemaining < FULL_ROLLS_REMAINING`).
  - **`WHY_DID_YOU_DO_THAT`, `ALMOST_FAMOUS`, `DICE_HATE_ME` and `COMMITMENT_ISSUES` all fire from
    `checkPreCommitAchievements`, called before `GameEngine.commitScore` changes anything** - same
    reasoning as `WASTED_5X`: the dice and the chosen category have to be read together, at the
    exact moment of commit, before the scorecard and turn move on.
  - **The dice cup stays clickable even with zero rolls left** (`DiceCupPanel`'s `enabled` was
    `canRoll`, now unconditional) purely so `NO_MORE_ROLLS` has a tap to count - `onCupTap` itself
    (in `GameScreen`) decides whether a tap rolls the dice or just increments the counter, as two
    separate `if`s rather than an `if`/`else if`: chaining them as `else if` made Kotlin infer the
    whole lambda's type as the join of `Job` (from `launch`) and `Unit`, i.e. `Any` - a type the
    `() -> Unit` callback slot doesn't accept.
  - **`Achievement.TIME_WASTING` is tracked in every build, debug or release** - only the actual
    superuser-mode effect (`GameViewModel.trackSuperuserSequence`'s `_superuserModeActive.value =
    true` and its toast) stays behind `BuildConfig.DEBUG`, so the achievement rewards performing
    the hidden hold sequence itself, whether or not this build lets it do anything.
  - **`NOT_THOSE_DICE` (tapping the menu's own logo dice) needed a `MenuViewModel`** purely to hold
    the one-line achievement unlock `MenuScreen` otherwise has no repository to reach - `AppLogo`
    gained an `onDiceTap` callback wrapping just the dice `Row`, not the wordmark below it.
  - **The Achievements screen has its own, unrelated superuser mode**, entirely
    `BuildConfig.DEBUG`-gated (`AchievementsViewModel`) - unlike the in-game one, this isn't a
    discoverable easter egg tied to an achievement, just a tester's shortcut, so nothing about it
    runs at all in a release build. Tapping the unlocked-count banner
    `AchievementsViewModel.SUPERUSER_TAP_TARGET` times arms it; a long press on any row then
    (`AchievementsScreen`'s hand-rolled hold-to-repeat gesture, the same "launch a ticking
    coroutine on down, cancel it on up" shape `DiceTray`'s die-cycling uses) toggles a plain
    achievement locked/unlocked on the first 500ms tick, or - for one with a progress bar - adds 1
    per tick (a real progress banner pops, same as earning it for real) until either it reaches its
    target or the press has been held 10s, at which point it jumps straight to a full unlock -
    `PROFESSIONAL_ROLLER`'s target is 100,000, and nobody's holding a row that long one tick at a
    time. The bump is a purely in-memory overlay (`AchievementsViewModel.superuserProgressOverride`)
    added on top of the real stored progress only for this display/decision, never persisted - the
    real `AchievementCounter`s are shared across several achievements (`GAMES_PLAYED` backs
    `GAMES_10`/`50`/`100`) and the leaderboard-backed ones (`PROFESSIONAL_ROLLER`, the score-band
    ledger) are read from real recorded scores, so actually writing a fake counter or leaderboard
    row would corrupt real state rather than just performing a test unlock. Locking an already
    unlocked achievement back up needed a new `AchievementStore.forceLock`, since the only existing
    lock-direction operation was `resetAll` - the Settings screen's all-or-nothing wipe. The banner
    itself also carries a bulk version, a plain `combinedClickable` long press (nothing repeating
    needed here, so no reason to reach for the row's hold-to-repeat gesture): unlocks every
    achievement still locked in one go, and the *next* long press - once nothing is left locked -
    relocks every one of them instead, decided fresh from what's actually locked each time rather
    than a remembered flag.
  - Not covered by an automated test: `CONTINUED_GAME` ("leave a game, come back to finish it") and
    the real, end-to-end "picked a non-default style" path for `STYLE_DICE`/`STYLE_CUP`/
    `STYLE_BACKGROUND` - specifically the `SettingsRepository` read that feeds `GameStartContext`.
    Both need `SettingsRepository`/`InProgressGameRepository` - concrete DataStore-backed classes,
    not interfaces, so neither can be faked on the plain-JVM test setup the way
    `AchievementStore`/`ScoreDao` are. What tests do cover on that setup: `checkGameStartAchievements`
    returning early (no unlock, no crash) when there's no settings repository at all, and - fully,
    since it needs no repository - `AchievementEngine.evaluateAtGameStart`'s own decision logic
    once `GameStartContext`'s booleans are already known (`AchievementEngineTest`).
- **The trademarked name is banned from the application entirely** - source,
  comments, identifiers, filenames and anything a player can see. See the
  rule in `CLAUDE.md`. The term is **"5x"** in user-facing text (what the
  scorecard tile has always shown) and **`FIVE_OF_A_KIND`/`fiveOfAKind`** in
  code. These `.claude/*.md` files are the one exception, because explaining
  what the game is requires the word; `README.md` is public and is not.

## New Gradle dependencies

- `androidx.navigation:navigation-compose`
- Room: `androidx.room:room-runtime`, `androidx.room:room-ktx`, plus
  `androidx.room:room-compiler` via the **KSP** plugin
  (`com.google.devtools.ksp`, version matched to the Kotlin version in
  `gradle/libs.versions.toml`).
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
      ScoreEntry.kt (Room @Entity)     — id, playerName, score, timestampEpochMillis, won (nullable;
                                          null for a solo game or a pre-migration row)
      ScoreDao.kt                      — pagedScores(limit, offset), count(), bestScore(), insert(),
                                          playerSummaries() (GROUP BY playerName), outcomesForPlayer(name)
      AppDatabase.kt                   — Room database, singleton via Application; MIGRATION_1_2 adds `won`
      ScoreRepository.kt               — wraps DAO, exposes page loads and playerStatistics() (adds the
                                          per-player current/best win streaks, walked separately from
                                          the SQL aggregate)
    achievements/
      AchievementsState.kt             — unlock timestamps + counters, as read back
      AchievementsRepository.kt        — own DataStore file, so a reset can't touch settings
      AchievementEvents.kt             — process-wide SharedFlow feeding the banner host
      AchievementStore.kt              — the interface GameViewModel depends on, so the path is testable
  model/
    Die.kt                             — existing, unchanged
    ScoreCategory.kt                   — existing enum, unchanged
    GameType.kt                        — CLASSIC (usable), EXTENDED (placeholder, disabled in UI)
    PlayerType.kt                      — HUMAN, AI
    Difficulty.kt                      — EASY, MEDIUM, HARD (stored, UI disabled)
    PlayerConfig.kt                    — setup-time: slot, type, name, difficulty
    PlayerState.kt                     — in-game: name, type, difficulty, scorecard (Map<ScoreCategory, Int?>), fiveOfAKindBonusCount
    GameState.kt                       — rewritten: gameType, players: List<PlayerState>, currentPlayerIndex,
                                          dice: List<Die>, rollsRemaining, phase (AWAITING_ROLL / ROLLED), isGameOver
  game/
    DiceScoring.kt                     — pure functions: score(category, dice), isFiveOfAKind etc.
    ScoreCalculator.kt                 — resolves a category pick against current scorecard incl. upper bonus (63+ => +35)
                                          and Yahtzee joker rule (extra Yahtzee => +100 bonus, forced placement rules)
    GameEngine.kt                      — pure reducer-style functions: rollDice, toggleHold, commitScore, advanceTurn
    AiTurnPlayer.kt                    — basic auto-play: roll x3 (no holds) then pick max-scoring open category
    AiNameGenerator.kt                 — static themed name pool, random pick without duplicates per game
    AchievementEngine.kt               — pure: what a game has earned so far (mid-game) and at the end
  navigation/
    Screen.kt                          — sealed route constants (menu, play/setup, play/game, scores, achievements, styles, settings)
    DiceFiveNavHost.kt                 — NavHost wiring, "play" nested graph shares GameViewModel via getBackStackEntry
  ui/
    menu/MenuScreen.kt                 — Play / Leaderboard / Statistics / Achievements / Styles / Settings buttons
    setup/
      GameSetupScreen.kt               — player count 1-4, per-slot human/AI + name field, game type radio (Extended disabled)
    game/
      GameScreen.kt                    — dice tray w/ hold toggles, roll button, scorecard grid, current player banner
      GameViewModel.kt                 — owns setup config AND live GameState; phase drives which screen renders;
                                          drives AI auto-play via viewModelScope coroutine with short delays between steps
      ScorecardView.kt / DiceRow.kt    — shared composables for the scorecard grid and dice display
    scores/
      ScoresScreen.kt                  — paginated table (50/page), long-press row shows date tooltip
      ScoresViewModel.kt               — talks to ScoreRepository, tracks current page
    statistics/
      StatisticsScreen.kt              — one card per distinct human player name: max score, games
                                          played/won/lost, current/best win streak, first-played date
                                          + time; a drawn scrollbar hints at cards below the fold
      StatisticsViewModel.kt           — talks to ScoreRepository.playerStatistics()
    achievements/                       — AchievementsScreen (one themed list, easiest-first per
                                          theme; unlocked ones highlighted in place rather than
                                          split out + "Hide unlocked"), AchievementsViewModel,
                                          AchievementBannerHost (the overlay above the whole
                                          NavHost - see .claude/UI.md)
    styles/StylesScreen.kt             — preview tiles (dice / dice cup / mat & background),
                                          one horizontally-scrolling row per category
    settings/
      SettingsScreen.kt                — theme radio group (Light/Dark/System); footer shows
                                          app version + GitHub link via UriHandler
      SettingsViewModel.kt             — reads/writes SettingsRepository.theme
    theme/                             — Theme.kt (M3 colour schemes, no dynamic colour) + Color.kt (tonal-palette
                                          roles, plus the separate game-table palette). MainActivity passes
                                          darkTheme resolved from the stored Theme setting. No Type.kt: the M3
                                          type scale is used as-is rather than overridden.
    common/                            — chrome shared by the menu and every non-game page: BrandBackdrop,
                                          ScreenScaffold (top app bar + back), PageColumn, AppLogo
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
  "statistics"              -> StatisticsScreen
  "achievements"            -> AchievementsScreen
  "styles"                  -> StylesScreen
  "settings"                -> SettingsScreen
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

`DiceScoring`/`GameEngine` are pure functions with no Android
dependencies — most unit tests live here.

- **Maximum possible score: 1575** (`PlayerState.MAX_POSSIBLE_SCORE`, with the derivation as a doc
  comment there and a locking test in `PlayerStateTest`) — the "perfect game": every upper box
  maxed plus the 63+ bonus, every other lower box maxed, and every one of the other 12 turns also
  landing a 5x for its +100 bonus chip. Every screen that shows a score (Leaderboard, Statistics'
  max score) pads it to this constant's digit width (4) so the column stays a fixed width
  regardless of how many digits a given score has; current-game score displays (`PlayerHeaderBar`,
  `GameOverScreen`) don't need this — the player-name column next to them is already capped at 10
  characters, so there's nothing there for a short score to let grow sideways.

## Scores screen

- `ScoreDao`: `ORDER BY score DESC LIMIT :limit OFFSET :offset`, plus a
  `COUNT(*)` query for total pages.
- `ScoresViewModel`: current page index (0-based), `SCORES_PAGE_SIZE` rows
  per page (50), next/prev availability.
- `ScoresScreen`: scrollable rank/name/score table in a compact row style;
  long-press a row shows a popup/tooltip with the formatted date from
  `timestampEpochMillis`. The next/prev controls only render when there is
  more than one page, but their height is always reserved, so the table ends
  in the same place either way.

## Settings & theme

- `SettingsRepository.theme: Flow<Theme>` (LIGHT/DARK/SYSTEM, default
  SYSTEM), backed by DataStore Preferences.
- `MainActivity` collects this at the top and passes resolved
  `darkTheme: Boolean` into `DiceFiveTheme` — SYSTEM defers to
  `isSystemInDarkTheme()`.
- `SettingsScreen`: radio group writing back to the repository. A quiet
  footer below the Reset card shows the app version (from `BuildConfig`)
  and a "View on GitHub" link to `https://github.com/zodac/DiceFive`,
  opened via Compose's `UriHandler` - this used to be its own `AboutScreen`,
  folded in here so the menu has one less destination.

## Achievements

- `AchievementsScreen`: the full catalogue, locked first. See Phase 13 for
  the rules, storage and banner behaviour.

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

## Ad hoc debug build versioning

When handing the user a `DiceFive-debug.apk` directly (e.g. attached in chat, to sideload over
whatever debug build they already have installed), two separate things determine whether that
install-over-existing succeeds:

- **Signing certificate** — must match exactly, or Android refuses the install outright
  ("conflicts with an existing package") regardless of version numbers. All debug builds are
  signed with the local Gradle-managed `~/.android/debug.keystore`, which is created once and
  reused for every build afterwards - so this holds automatically *unless* that keystore file is
  deleted/regenerated between builds (e.g. a fresh container/sandbox). Sanity-check with
  `keytool -list -keystore ~/.android/debug.keystore -storepass android` and compare the SHA-256
  fingerprint to the previous build if there's any doubt.
- **`versionCode`** — must be >= whatever's currently installed, or Android refuses the install as
  a downgrade. The release scheme (`major*10000 + minor*100 + patch`, from the root `VERSION`
  file) is usually *unchanged* between two ad hoc debug builds in the same chat session, since
  bumping `VERSION` is part of the release flow, not something to do just to hand over a debug
  build. Reusing that scheme for debug builds would give repeated builds the *same* versionCode,
  which is a same-version reinstall (works today, but is one accidental `VERSION` edit away from
  becoming a refused downgrade).

  Fix: `app/build.gradle.kts` gives the `debug` build type its own `versionCode`/`versionName` via
  `androidComponents { onVariants(selector().withBuildType("debug")) { ... } }`, derived from
  wall-clock minutes-since-epoch instead of the `VERSION` file. Every fresh `assembleDebug` this
  way gets a strictly-increasing versionCode independent of whether `VERSION` changed, so handing
  over a new debug APK always installs cleanly over the last one. This only touches the `debug`
  variant - `assembleRelease` (what CI/the release workflow uses) still gets the clean
  `VERSION`-derived versionCode/versionName untouched.

  **Rule of thumb**: never hand-edit `VERSION` just to make an ad hoc debug build installable -
  that file is the release pipeline's source of truth (see Phase 12) and bumping it outside a
  real release desyncs it from the patch-bump automation. The debug versionCode scheme above
  already handles it.

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
      `https://github.com/zodac/DiceFive` via `LocalUriHandler`. Enabled
      `buildFeatures.buildConfig = true` in `app/build.gradle.kts` for the
      version string.
- [x] `assembleDebug`, `compileDebugAndroidTestKotlin`, `testDebugUnitTest`
      all green (37 unit tests, unchanged).

### Phase 10 — Final verification
- [x] `./gradlew clean assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin lint`
      all green (37 unit tests, 0 failures).
- [x] Ran `./gradlew lint`: fixed the one finding introduced by this work
      (`ModifierParameter` in `GameScreen.kt` - `modifier` wasn't the first
      optional param; reordered ahead of `onBackToMenu`). The remaining ~22
      warnings (GradleDependency/NewerVersionAvailable/AGP-version nags,
      plus a few pre-existing launcher-icon/target-SDK findings from the
      original skeleton) are pre-existing or out of scope - not touched.
- [x] Manual smoke pass via `run` skill: **not possible** - no
      emulator/AVD/device in this sandbox (see Verification above);
      substituted with full unit test coverage of the pure logic and a
      careful reading of every screen.
- [x] Updated `README.md`'s Structure section to match the final package
      layout, and pointed it at this file for the full design/phase log.

### Phase 11 — Post-release review follow-ups
- [x] **Back confirmation during a game**: `GameScreen` installs a
      `BackHandler` (this also fixes a latent gap - previously an actual
      back-press/gesture would default to popping just one nav entry,
      landing back on the setup form instead of Menu). While a game is in
      progress and the new Settings toggle "Confirm before leaving a game
      in progress" (`SettingsRepository.confirmBeforeLeavingGame`, default
      on) is enabled, back shows an AlertDialog ("Leave"/"Cancel") before
      exiting to Menu; once the game is over, back always goes straight to
      Menu with no prompt.
- [x] **Continue / New Game**: added `data/game/GameStateJson.kt` (hand-rolled
      `org.json` (de)serialization of `GameState` - `testImplementation(libs.org.json)`
      added since Android's org.json is stubbed on the unit-test classpath)
      and `data/game/InProgressGameRepository.kt` (DataStore-backed, at most
      one saved game, cleared on finish). `GameViewModel.applyGameState()`
      now autosaves after every state change (human, AI, or undo) and clears
      the save when a game finishes, so a game survives navigating away or
      even process death. `MenuScreen`'s Play button shows a
      Continue/New Game dialog when `InProgressGameRepository.hasInProgressGame`
      is true; `Screen.PLAY_SETUP_ROUTE` gained an optional `resume` nav arg
      - `DiceFiveNavHost` uses it to call `GameViewModel.resumeGame()` behind
      a small loading spinner before landing on `play/game`, falling back to
      the normal setup form if there was nothing to resume after all.
      Fixed a latent bug found while wiring this up: AI turns previously
      mutated `_game` directly rather than through the shared
      apply-state path, so if an AI player's move happened to be the one
      that completed the game, human players' final scores were never
      persisted to the leaderboard. Routing AI moves through the same
      `applyGameState()` fixed this as a side effect.
- [x] **Undo**: `GameViewModel` tracks a single pre-action snapshot
      (`undoSnapshot`/`canUndo`) captured before every human roll/hold/score.
      `undo()` restores it, cancels any AI turn job it would have triggered
      (closes a race where a pending AI coroutine could otherwise clobber
      the reverted state ~600ms later), and clears the snapshot (single-use,
      no redo). Any AI action clears the snapshot outright - only the most
      recent *human* move is ever undoable. An "Undo" button sits next to
      Roll on `GameScreen`, enabled only while `canUndo` is true; there's no
      Undo on the game-over screen (undoing a finished game would also need
      to retract an already-persisted score - out of scope here).
- [x] New/updated tests: `GameStateJsonTest` (3, round-trips including nulls
      and a finished game), `GameViewModelTest` (+5: undo of a roll, undo of
      a scored category, undo unavailable before any move and after an AI
      move, `resumeGame()` with no repository configured). Found and fixed a
      real bug during this work: `canUndo` was originally a
      `_undoSnapshot.map {}.stateIn(...)` derived flow, which doesn't update
      synchronously (only after the coroutine dispatcher actually runs the
      collector) - a plain `_canUndo` `MutableStateFlow` updated in lockstep
      with the snapshot, matching how `_game`/`_setup` already work in this
      class, both fixed the test failures and is simpler.
- [x] `assembleDebug`, `compileDebugAndroidTestKotlin`, `testDebugUnitTest`
      (45 unit tests), and `lint` all green; no new lint findings beyond the
      pre-existing/version-nag set from Phase 10.

### Phase 12 — Release pipeline
- [x] Root `VERSION` file (currently `0.0.1`) is the single source of truth
      for the app version. `app/build.gradle.kts` reads it at configure
      time: `versionName` = the file's contents, `versionCode` =
      `major*10_000 + minor*100 + patch` (deterministic, reproducible
      locally and in CI, no extra state to track).
- [x] Release signing: `app/build.gradle.kts` builds a `release`
      `signingConfig` from four env vars (`ANDROID_RELEASE_KEYSTORE_PATH`,
      `_KEYSTORE_PASSWORD`, `_KEY_ALIAS`, `_KEY_PASSWORD`). If any are
      unset, the `release` build type simply gets no signing config
      (`./gradlew assembleRelease` still works locally, producing an
      unsigned APK) - CI supplies all four from repository secrets.
      Generated a real release keystore (`keytool`, RSA 2048, 10000-day
      validity, alias `dicefive-release`) - **not committed**; handed to
      the user out-of-band (base64 + passwords) with instructions to add
      as repo secrets (`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
      `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`). `.gitignore` gained
      `*.jks`/`*.keystore`/`keystore.properties` as a defensive backstop.
      Verified end-to-end locally: `assembleRelease` with these env vars
      set produces an APK whose signing cert SHA-256 matches the generated
      keystore's, and `versionCode`/`versionName` land correctly (verified
      via `aapt dump badging`).
- [x] `.github/workflows/release.yml`: triggers on every push to `main`.
      Steps: checkout, JDK 21 (matches the sandbox's pin), Android SDK
      (explicit `sdkmanager` call installing `platforms;android-35` /
      `build-tools;35.0.0` against the SDK already on the `ubuntu-latest`
      runner - see the CI-failure fix note below), Gradle
      caching (`gradle/actions/setup-gradle@v4`), run `testDebugUnitTest`
      as a release gate (a broken build never gets published), decode the
      keystore secret to `$RUNNER_TEMP` (outside the checkout, never
      persisted), `assembleRelease`, then `softprops/action-gh-release@v2`
      creates/updates the GitHub Release tagged `v<VERSION>` with the APK
      attached. Re-pushing without bumping `VERSION` updates that same
      release (idempotent) rather than failing on a duplicate tag - only
      bump `VERSION` when a new release entry is actually wanted.
      `concurrency: group: release` prevents two overlapping runs from
      racing on the same release.
- [x] Scope note: no lint step in this workflow (kept the pipeline focused
      on what "release" needs: correctness gate + build + publish - lint
      is a code-quality check already exercised in Phase 10, not a release
      gate the user asked for).
- [x] **Secrets scope**: recommended repository (not organization) secrets
      - this keystore signs only this one app's identity, no other repo
        should ever need it.
- [x] **CI failure fix (first real run)**: `android-actions/setup-android@v3`
      failed on the "Set up Android SDK" step with `Failed to find package
      'tools'` - that action always tries to install the legacy monolithic
      "tools" SDK package, which Google removed from the repository years
      ago (superseded by cmdline-tools), so it now fails on every fresh
      install. GitHub's `ubuntu-latest` runners already ship a full Android
      SDK pre-installed with licenses accepted (confirmed by the failure
      log itself - the broken action found and ran `sdkmanager` from
      `/usr/local/lib/android/sdk/...`, the runner image's standard
      location), so the action added no value anyway. Replaced both the
      "Set up Android SDK" and "Install required SDK packages" steps with
      one step that calls `$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager`
      directly to accept licenses and install just the two packages this
      project pins.
- [x] **Pre-1.0 releases**: a "Read version" step derives `stable` (major
      version `>= 1`) alongside the version string. While `stable != true`:
      the keystore-decode/`assembleRelease`/APK-rename steps are skipped
      entirely (`if: steps.version.outputs.stable == 'true'`) - no signing,
      no build, just the version bump - and the GitHub Release is created
      with `prerelease: true` and no file attached (`files:` sourced from
      the skipped rename step's now-empty output, which
      `softprops/action-gh-release` treats as "no assets"). Unit tests
      still run regardless of stability, since correctness gating shouldn't
      depend on version number. Once `VERSION` reaches `1.0.0`, the full
      build+sign+attach path resumes and releases stop being marked
      pre-release.
- [x] **Automatic patch-version bump**: `scripts/set-version.sh <version>`
      is the one designated place that writes the app version everywhere
      it's referenced - today that's just the `VERSION` file (Gradle
      already single-sources from it, and no doc hardcodes a "live"
      version number), but it's the extension point if that ever changes.
      `scripts/bump-patch-version.sh` reads the current version and calls
      `set-version.sh` with its patch component incremented
      (`0.1.0 -> 0.1.1`) - it never touches major/minor; those are only
      ever changed by hand (edit `VERSION` directly, or run
      `set-version.sh` with an exact value) to start a new minor/major
      line. The release workflow's last step runs `bump-patch-version.sh`
      after every successful release (re-syncing with `origin/main` first
      in case it moved mid-run) and commits+pushes the bump with the
      default `GITHUB_TOKEN`, which GitHub does not use to re-trigger
      workflows on push - so this can't create an infinite loop. Verified
      both scripts locally (patch bump, manual set to a new minor line,
      resumed patch-bumping from there, and invalid-input rejection) in an
      isolated temp directory before wiring them into CI.
- [x] **Output APK naming**: `app/build.gradle.kts` sets
      `android.base.archivesName = "DiceFive"`, so output filenames are
      `DiceFive-debug.apk` / `DiceFive-release.apk` (or
      `DiceFive-release-unsigned.apk` when built locally with no signing
      env vars set) instead of AGP's `app-*.apk` default. Updated the
      release workflow's "Rename APK for release" step to copy from the
      new `DiceFive-release.apk` path - it was still referencing the old
      `app-release.apk`, which would have broken that step on the next
      full (>=1.0.0) release. Verified both `assembleDebug` and
      `assembleRelease` locally produce the expected filenames.

### Phase 13 — Achievements
- [x] **Scope**: 51 achievements (50 + the later-added secret `CHEATER_CHEATER`),
      replacing the Phase 8 placeholder screen.
      Local only for now, but every piece is shaped for a later Google Play
      Games migration: `Achievement.id` is a stable snake_case external key
      (**never change one** — it is the storage key and will be the Play
      achievement key; rename `title`/`description` instead), and the
      incremental ones carry a `counter` + `target` that map onto Play's
      incremental type. `ProgressStyle.STREAK` ones deliberately do *not*
      map — Play's `setSteps` can't go backwards — so they become plain
      unlock-only achievements there and keep their progress bar locally.
- [x] `model/Achievement.kt` — the catalogue, plus `AchievementCounter`
      (the five device-wide running totals) and `ProgressStyle`. Counters
      are shared, not per-achievement: "finish 10 / 50 / 100 games" is one
      stored number, not three.
- [x] `data/achievements/` — `AchievementsRepository` on its **own**
      DataStore file (`achievements`), so "Reset achievements" is a
      `clear()` that can't take the theme or the remembered player names
      with it; `AchievementsState` (unlock timestamps + counters); and
      `AchievementEvents`, a process-wide `SharedFlow` the banner host
      listens on.
- [x] `game/AchievementEngine.kt` — pure, no Android/persistence/clock, in
      the same style as `GameEngine`: `GameViewModel` reads the stored
      state, calls `evaluate`, writes the result back. `unlockNow` handles
      the achievements earned mid-turn (a full house, large straight or 5x
      rolled straight out of the cup).
- [x] **Rules that needed deciding** (all documented on the engine):
      - *Per device, not per player*: any human at the table satisfies an
        achievement; AI results only ever count as the opposition. A
        consequence worth knowing: in an **all-human** game the device
        always "wins", so win streaks keep climbing — that's what a
        device-scoped streak means.
      - *Solo games* count for games played, feats and score thresholds,
        but neither extend nor break a win streak (nobody to beat).
      - *A tie at the top counts as a win* for the human — nobody beat them.
      - *Cheating disqualifies the whole game*: if superuser mode was
        activated, nothing at all is counted, not even games played.
      - *Undo can't inflate anything*: end-of-game achievements are derived
        from the final scorecard, and the only live counter (dice rolled)
        is only ever incremented by a human roll, which is itself not
        undoable.
      - *`PERSONAL_BEST` reads the leaderboard before this game's own rows
        are inserted* — hence `ScoreDao.bestScore()` and the reordering of
        `GameViewModel.finishGame`, which now sequences read → persist →
        evaluate in one coroutine instead of firing persistence and
        forgetting about it.
      - *`I_ROBOT` is not currently earnable* (AI difficulty is deferred and
        every AI plays the same strategy), so it is excluded from
        `COMPLETIONIST`'s requirements via `countsTowardCompletion = false`.
        Flip that back when difficulty lands.
      - *`EXTREME_LOW_ROLLS` (finish on exactly 5) is* earnable: five 1s
        taken as Chance is legal play, and holding 1s makes it grindable.
        It stays a `COMPLETIONIST` requirement.
- [x] **When achievements are evaluated**: three moments, not one.
      `AchievementEngine.evaluateInProgress` runs after **every scored
      category** (and at game start, for "Full Table"), so a maxed Sixes box
      or a second Yahtzee lands the instant it happens rather than on the
      results screen. It only judges what a later turn cannot take away — a
      filled box, a banked bonus, a running total past a threshold (a total
      only ever grows), which is why the score-threshold wording is "Score
      200 or more in a game", not "Finish a game with". `evaluate` runs at
      game over for everything else (a complete card, a final score, a
      result, every counter). `unlockNow` covers the moment-in-time ones -
      a full house, a large straight or a 5x on the first of a turn's three
      rolls, which is the only thing a scorecard can't show after the fact.
      Counters stay **end-of-game only** so an undo can't inflate them, and
      the in-progress pass is skipped once the game is over so it can't race
      the final one into double-popping the same banner. A `Mutex` in
      `GameViewModel` serialises all three paths' read-decide-write cycles
      for the same reason.
- [x] **Progress banners**: `STREAK` achievements announce every single step
      (a streak is fragile and slow to build); `CUMULATIVE` ones only at
      quarter marks, or a 10,000-dice total would pop a banner every game.
      Neither fires for something already unlocked, or for one being
      unlocked in the same breath — the unlock banner says it all.
- [x] **Ordering**: `Achievement`'s own declaration order *is* the display
      order — grouped by `AchievementCategory`, and easiest-first within a
      category so a ladder's rungs stay adjacent and ascending
      ("Sharpshooter" → "High Roller" → "Dice Deity"). This replaced an
      alphabetical locked list, which scattered every ladder. Streaks are
      their own category rather than part of Winning, being the only ones
      that can fall back to zero. A new achievement therefore goes where it
      belongs in that reading order, not on the end;
      `AchievementEngineTest` fails the build if a category ends up split
      across the list. Nothing reads the ordinal and storage is keyed by
      `id`, so reordering never disturbs stored unlocks.
- [x] `ui/achievements/` — `AchievementsViewModel` (locked grouped by theme,
      unlocked newest-first and flat, "Hide unlocked" toggle), the rewritten
      `AchievementsScreen`, and `AchievementBannerHost` (see `.claude/UI.md`
      for why it sits above the `NavHost` and how the stack behaves).
- [x] Settings gained a "Reset achievements" row behind a `DiceFiveDialog`
      confirmation.
- [x] **Isolating the side effects**: `finishGame` wraps the leaderboard
      read and write in `runCatching`. They used to be sequential and
      unguarded, so a throw from either silently took the achievement
      evaluation down with it — the kind of failure that leaves no trace at
      all. `AchievementStore` was extracted from `AchievementsRepository`
      for the same reason: the path from a finished game to a stored unlock
      now has JVM tests (`GameAchievementsWiringTest`), where before only
      the pure engine did and the wiring was unverified.
- [x] Tests: `AchievementEngineTest` (25, covering win/loss/tie, streak
      reset, margins, per-device feats, cheat disqualification, both
      progress-banner policies, the Completionist cascade and id
      uniqueness). `ScoreRepositoryTest`'s `FakeScoreDao` gained
      `bestScore()`. 81 unit tests total, all green; `assembleDebug`,
      `compileDebugAndroidTestKotlin` and `lint` also green, with no new
      lint findings (the three in `DiceTray.kt` are pre-existing).
      Later grown to 95 tests total with the mid-game evaluation and the
      `GameAchievementsWiringTest` suite.
- [x] **"5x", not the trademark**: `Achievement`'s titles, descriptions and
      ids were renamed off the word ("5x!", `5x_first`, `SCRATCHED_5X`,
      `AchievementCounter.SCORED_5X`), matching what the scorecard tile has
      always shown. A test asserts no title, description or category label
      contains it, so it can't creep back in. The ids changed, which resets
      those unlocks on any device that already had them - acceptable
      pre-release, and the reason the "never change an id" rule starts
      properly from here.
- [x] **Renamed the word out of the source entirely** - landed as its own
      commit *ahead of* this phase, so it could ship without waiting for
      achievements: `ScoreCategory.YAHTZEE`
      → `FIVE_OF_A_KIND` (its original Phase 1 name, reverted),
      `YahtzeeScoring.kt` → `DiceScoring.kt`, `YahtzeeCupPanel.kt` →
      `DiceCupPanel.kt`, `isYahtzee` → `isFiveOfAKind`,
      `PlayerState.yahtzeeBonusCount` → `fiveOfAKindBonusCount`,
      `awardsYahtzeeBonus` → `awardsFiveOfAKindBonus`, plus every comment.
      `README.md` too - it is public, so it is not covered by the
      reference-documentation exception.
      **Known cost, accepted:** `GameStateJson` writes scorecard keys and
      the bonus count by their enum/property names, so any game that was
      in progress across this upgrade no longer resumes.
      `InProgressGameRepository.load` already swallows a decode failure and
      falls back to the setup form, so it degrades to "Continue does
      nothing" rather than crashing.
- [x] **The ledger** (the tail of `AchievementCategory.COLLECTION`): six achievements
      for having recorded *every* score in a 50-point band on the
      leaderboard - 5-50 (46 scores, since the band is inclusive and 5 is the
      lowest total the rules allow), then 51-100, 101-150, 151-200, 201-250,
      251-300 (50 each). Tally → Bookkeeper → Registrar → Auditor →
      Archivist → Historian, then Completionist after them: they are the
      longest haul in the game, so the whole group sits at the very end of
      the list. "Practice Makes Perfect" and "Full Table" moved the other
      way, out of Collection and into Milestones, where the rest of the
      "shape of a game you played" achievements already live.
      These are the one group **not** backed by a stored counter: progress is
      derived from the scores table via `ScoreDao.distinctScores()`, so they
      are retroactive (scores already on the leaderboard count the moment the
      next game finishes) and the progress bar can never disagree with what
      the Scores screen shows. `GameViewModel.finishGame` reads the distinct
      scores *before* inserting, for the same reason it reads the best score
      there, and `AchievementEngine` adds the finished game's own human
      totals itself - one read gives it both the before and after state of a
      band, which is what the quarter-mark progress banner needs.
      "Professional Roller" (100,000 career points) is measured the same
      way, off `SUM(score)`, which is why both it and the bands now travel
      together in a `LeaderboardTotals` carrier rather than as loose
      parameters.
      Two consequences worth knowing: a reset re-locks them but the
      underlying scores remain, so they re-earn on the next qualifying game
      (the same is already true of `PERSONAL_BEST`); and they count toward
      `COMPLETIONIST`, which makes that achievement a ~296-game commitment -
      flip `countsTowardCompletion` on the six if that is not wanted.
- [x] **Player 1 only, not any human**: achievements used to be per-device -
      any human at the table satisfied one. Now only `state.players[0]` -
      "You" on the setup screen, always `HUMAN` - earns anything at all;
      another human seat is just an opponent, same as AI. This is
      `AchievementEngine`'s first rule now, replacing the old "per device"
      one, and it touches three layers:
      - `AchievementEngine.evaluate`/`evaluateInProgress` bail out entirely
        unless `players[0]` is human, then build their `humans` list from
        just that one player - almost every existing check (`anyHuman`,
        `bestHumanScore`, `humanWon`, `winningMargin`) needed no further
        change, since they already just walked whatever list they were
        given.
      - `GameAchievementContext.extraRollPlayerIndices` (a `Set<Int>`) and
        `diceRolledByHumans` shrank to `playerOneTookExtraRoll: Boolean` and
        `diceRolledByPlayerOne`, since only one player's index was ever
        going to matter again. `GameViewModel` now guards every mid-turn
        achievement check (`rollDice`/`toggleHold`/`commitScore`/
        `tapCupWithNoRollsLeft`/`trackFinalRoundPosition`) with an
        `isPlayerOneTurn` (`currentPlayerIndex == 0`) check - the underlying
        game actions themselves stay ungated, so a second human seat still
        plays normally, it just earns nothing. `TIME_WASTING` is the one
        partial exception: the hidden hold sequence and superuser-mode
        activation itself still work "any player's turn" (that cheat's own
        design), only the achievement unlock is now player-1-gated.
        `previousTurnFiveOfAKindByPlayer` (keyed by player index, for
        `TWICE_IN_A_LIFETIME`) collapsed to a plain
        `playerOnePreviousTurnWasFiveOfAKind: Boolean` for the same reason.
      - **`SINGULARITY` needed an explicit fix, not just a narrower `humans`
        list**: it's the one Misfortune achievement whose description names
        an opponent type ("Lose a game to an AI"), but its condition was
        just `!humanWon`. Under the old "any human" rule that was safe -
        in an all-human game *someone* human is always at the top, so
        `!humanWon` could only be true when an AI actually won. Restricting
        `humans` to player 1 alone breaks that: player 1 losing to a
        *second human* now also makes `humanWon` false. Fixed by requiring
        an AI to actually hold `topScore` (`aiWon`) before awarding it.
        `PIPPED_TO_THE_POST`/`JAWS_OF_VICTORY` needed no such fix - their
        own descriptions never named an opponent type, so "player 1 simply
        lost" was already the right reading.
      - **`PERSONAL_BEST` needed a data-layer change**: "beat your best
        score" only makes sense per-name once a second human at the table
        stops counting as "you". `ScoreDao.bestScore()`/
        `ScoreRepository.bestScore()` (a global `MAX(score)` across every
        recorded human, its one and only caller) were replaced with
        `bestScoreForPlayer(playerName)` (`WHERE playerName = :playerName`),
        and `GameViewModel.finishGame` now reads player 1's own name before
        looking it up. Same rule as `PlayerStatistics` already followed:
        renaming player 1 between games starts a fresh "personal best"
        under the new name.
      - **The one exception, confirmed rather than changed**: the ledger
        (`TALLY`...`HISTORIAN`, `PROFESSIONAL_ROLLER`) already measured
        itself against `LeaderboardTotals`, built from *every* human's
        score via `ScoreRepository.leaderboardTotals()` and
        `GameViewModel.persistHumanScores` - both untouched. `evaluate` now
        computes that from a separate `allHumans` list, kept apart from the
        player-1-only `humans` used everywhere else.
- [ ] **Google Play Games**: not started. The mapping is designed for, not
      built — no Play Games SDK dependency, no sign-in, no server-side
      achievement definitions.
      **Decided, to apply once sync is actually wired up**:
      - The ledger keeps reading the whole device's leaderboard (`ScoreRepository.leaderboardTotals()`,
        every human's recorded score, not just player 1's) - unchanged from today's behaviour, and
        matching how the Leaderboard/Statistics screens already work. What changes is only *who* the
        unlock is reported to: even though a second human's games can contribute to `TALLY`/
        `PROFESSIONAL_ROLLER`/etc. reaching their target, the resulting unlock still pops on
        whichever Google account is signed in as player 1 - the same account every other achievement
        already reports to, since only one account is ever signed in per device. Nothing about the
        engine changes for this; it only matters for whatever thin syncing layer eventually calls
        the Play Games SDK off `AchievementUpdate.newlyUnlocked`.
      - Secret achievements (`Achievement.isSecret` - today just `CHEATER_CHEATER`) are **not**
        registered as Google Play Games achievements at all, hidden or otherwise - they stay a
        local-only surprise. Whatever mapping table eventually pairs `Achievement.id` with a Play
        Games achievement id should simply omit every `isSecret` entry, and the sync call that
        reports `newlyUnlocked` achievements needs to skip them too.
