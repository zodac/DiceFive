# DiceFive: design reference

What the app is, how it works, and the decisions behind it. The interface itself (colour, shared chrome,
layout, accessibility) is in `.claude/UI.md`; game modes in `.claude/GAME_MODES.md`; the table art in
`.claude/STYLE_ART.md`. Read the relevant one before changing anything in its area.

## The app

A local (no netplay) dice game for 1-4 players, human and/or CPU, following the rules of the classic five-dice
scorecard game, with house variants (game modes and modifiers), score history, statistics, achievements and
unlockable table art. Android today; the game, its UI and its persistence are shared Kotlin Multiplatform code
that also compiles for iOS (see `.claude/IOS_SUPPORT.md`).

**The trademarked name is banned from the application entirely** - source, comments, identifiers, filenames and
anything a player can see (see `CLAUDE.md`). The term is **"5x"** in user-facing text and
**`FIVE_OF_A_KIND`/`fiveOfAKind`** in code. These `.claude/*.md` files are the only exception, because explaining
what the game is needs the word; `README.md` is public and is not an exception.

## Stack and layout

- Kotlin Multiplatform, Compose Multiplatform, Material 3, Navigation Compose, MVVM (`ViewModel` + `StateFlow`).
- Room (score history) and DataStore Preferences (everything else), both multiplatform.
- `app/shared` holds everything platform-neutral; `app/android` is the Android app (`MainActivity`, the `device/`
  platform services, the licence `TextView`); `app/baselineprofile` is the Baseline Profile journey;
  `app/licensing` holds the licence records. The package map is in `README.md`.
- Platform code sits behind `platform/PlatformServices` (sound, haptics, accelerometer, transient messages,
  licences), `BuildInfo` (version, debug flag) and `AppContainer` (what each platform's entry point builds).
  View models take an `AppContainer`, never a `Context`.
- No `kotlinx.serialization` or `kotlinx-datetime`: `data/Json.kt` is a small strict JSON reader/writer, and
  timestamps go through an `expect fun formatTimestamp` on each platform's own formatter. Both were dropped to keep
  the APK small.

## Screens

```
NavHost(start = "menu")
  "menu"         MenuScreen - Play (New Game | Continue), Achievements, Styles, Leaderboard, Statistics, Rules, Settings
  "play" graph   one GameViewModel scoped to the graph, shared by:
    "play/setup"   GameSetupScreen (optional `resume` argument resumes the saved game)
    "play/game"    GameScreen, then GameOverScreen and the scorecard review
  "scores"       ScoresScreen (the Leaderboard)
  "statistics"   StatisticsScreen
  "achievements" AchievementsScreen
  "styles"       StylesScreen
  "rules"        RulesScreen
  "settings"     SettingsScreen (About and Licences open from its footer)
```

Sharing one `GameViewModel` across setup and game avoids passing player lists through nav arguments. The app has a
single dark theme - no light scheme and no theme setting.

## Game setup

- 1-4 players. Player 1 is always human ("You") and is the only seat that earns achievements. Players 2-4 are
  Human or CPU.
- A human's name field is empty by default; it shows "Player N" in the app's language as a hint, and a game started
  with it empty uses that. Names are sanitised as they're typed (`model/PlayerName.kt`) and capped by width to fit the
  player tabs at the chosen player count (`GameSetupState.maxPlayerNameLength`: 14/12/10/8 for 1-4 players; a CPU's
  generated name is shorter still, to leave room for its chip icon). See `UI.md` for why.
- A CPU has a difficulty (Easy/Medium/Hard, default Medium) and a name generated at Start Game, unique within the game
  (`AiNameGenerator`).
- Each player picks a colour (`PlayerColour`); no two players share one.
- The game mode and the modifiers are chosen here. Everything on the form - names, types, difficulties, colours,
  player count, mode and modifiers - is remembered for next time in `SettingsRepository`.

## The turn and the rules

Numbers here are Standard's; every one comes from the game's `GameMode` (see "Game modes" below).

- A turn starts with 5 dice, 3 rolls, nothing held.
- Rolling rolls every die not held. Every roll, human or CPU, lands through `GameViewModel.performRoll` after the
  cup has shaken for `CUP_SHAKE_MILLIS`: a human's from the cup tap (`onCupTap` - a finger or shaking the phone), a
  CPU's from its own turn loop. A tap while a roll is still shaking or its dice are still landing is ignored.
- Holding toggles a die, once the turn has rolled. It's still allowed after the last roll (it does nothing then, but
  disabled dice looked broken).
- Scoring: any open box, at any point after the first roll. `ScoreCalculator` applies the upper bonus (63 or more in
  the upper section earns 35) and the joker rule: every 5x after the first scored 50 adds a +100 chip, and if the
  5x box is already filled the score must go in the matching upper box if open, else any open lower box (Full House
  and the straights at their fixed values), else a zero in any open box.
- Scoring ends the turn; a CPU then plays its rolls and its score with short pauses, so it's visibly animated.
- **Turn timer** (a modifier): when it runs out on a rolled turn, the turn is scored in the first open box (in Stud,
  the hand is completed first). When it runs out before the first roll, the player isn't rolled for: the turn is a
  zero in the first open box, with no last roll and nothing kept for Stored Rolls (`GameEngine.forfeitTurn`). CPUs
  are timed out the same way.
- **Undo** (solo games only): one step back over the last human roll, hold or score. A CPU move clears it, and there
  is no undo on a finished game.
- The game ends when every card is full. Each **human** player's total is recorded (CPU scores never are), and the
  results screen shows the ranking with the tie-break captions.

`DiceScoring`, `ScoreCalculator` and `GameEngine` are pure functions with no platform dependencies.

### Maximum scores

`GameMode.maxPossibleScore`, each derivation a doc comment on its entry and locked by `GameModeTest` playing the
perfect game through the real engine: Standard and Stud 1575, Tricolour 2120, Third Wind 4725, D20 1350, Hit List
785, Quickfire 210. The Leaderboard pads scores to the digit width of the highest (`GameMode.HIGHEST_POSSIBLE_SCORE`,
4 digits) so the column stays one width.

### Tie-break house rule

Not in the official rules, which call a tie at the top a shared win. Equal scores are separated by whoever did it
with more handicaps, in this order: fewest 5x, most zeroed boxes, fewest of Tricolour's four colour boxes scored
(Tricolour only), lowest upper section (without its bonus), lowest Chance, lowest 3x, lowest 4x. The first that
differs decides it; if all match it's a true tie and a shared rank ("=2").

`game/TieBreak.kt` holds the criteria, `TieBreakStats`, and `TieBreak.rank` - the one ranking a finished game's
players, shared by the results screen (which captions the deciding criterion: "Won on fewer 5x") and the Tie Break
achievement. The Leaderboard ranks with the same criteria but leaves out the Tricolour one, as its Combined table
mixes Standard and Tricolour; its SQL `ORDER BY` mirrors the comparator. This only changes ranking: a raw-score tie at
the top still counts as a win for achievements and statistics.

During a game the player tabs show each player's place by total alone ("=1st" when level); once every card is full
they take `TieBreak.rank`, so the scorecard review agrees with the results.

## Game modes

`model/GameMode.kt`. **Every rule that can differ between modes is a field on the mode** - dice count, how many score,
rolls per turn, die faces and colours, the card, how many times each box is scored, bonuses, the 5x chip, boxes
switched off, targets, whether it goes on the Leaderboard, whether Extended Scores is allowed, the ceiling. The engine,
the CPU, the achievements, persistence and the board all read the rules from there; there is never an
`if (mode == X)`. A box's own scoring rule is fixed and mode-independent (`ScoreCategory`); a mode chooses which boxes
are on its card. `.claude/GAME_MODES.md` is how to add or change one.

| Mode | Rules beyond Standard | Leaderboard |
|---|---|---|
| Standard | The official rules. | Yes |
| Tricolour | Each die also lands red, yellow or blue; four colour boxes - Reds/Yellows/Blues (40, all five that colour) and Coloured House (25, a joker free-fill like Full House). | Yes |
| Third Wind | Every box scored three times (39 turns); one upper bonus, tripled (189 earns 105); the joker starts once the 5x box's three slots are used with a 50 among them. | No |
| Stud | Seven dice rolled, five hold slots, only the held hand scores; a box can be scored with fewer held and `HandCompletion` completes the hand with the best dice for it. | No |
| D20 | Twenty-sided dice, five rolls; a box per number 1-20 plus a wildcard per half; 75 bonus per half (at 138 and 388); no 5x or lower section; no Extended Scores. Dice always drawn as a D20 style. | No |
| Hit List | Twelve targets drawn per game plus the Alibi; hits, exact hits (double) and partial hits; no Extended Scores. | No |
| Quickfire | Seven boxes off each game - 5x always, three upper and three lower at random - so six turns; the upper bonus threshold scales to the upper boxes left. | No |

A mode off the Leaderboard still records its games for Statistics and career points; the Leaderboard's Game Mode
view lists it on its own card.

## Modifiers

Optional extras on top of any mode, picked on the setup screen. **Any modifier takes a game off the Leaderboard**
(`GameState.hasModifiers`; `ScoreEntry.playedUnmodified` records it).

- **Turn Timer** - a whole-turn limit: 30, 60 or 120 seconds, or any typed length from 5 to 999. See the turn rules
  above for what a timeout does.
- **Number of Rolls** - 1-9 rolls a turn instead of the mode's (remembered while off).
- **Stored Rolls** - a player's unused rolls carry over to their own next turn, optionally capped (an empty cap is no
  cap). `GameState.turnRolls` is what the turn started with, so first-roll checks still mean the first roll.
- **Extended Scores** - three more boxes after the mode's own: Two Pair (the four dice making two different pairs),
  Evens and Odds, in their own section that counts toward the total only. Not in D20 or Hit List, where the setup row
  is locked with a note and the player's pick kept. A card is always read from `PlayerState.categories` /
  `GameState.categories` (`GameMode.categoriesWith`), never `gameMode.categories`.
- **Unlucky Dice** - after each roll every rolled die is `oddsPercent` likely (10-50%, steps of 10) to land locked in
  chains, at most `maxDice` (1-5) a roll. A locked die can't be held or scored and rerolls with the rest. A game
  without the modifier deals exactly the dice it always did.

## The CPU

`game/AiTurnPlayer.kt`, from the player's own mode and card.

- **Easy** never holds individual dice: it rerolls everything until any open box scores above zero, then takes the
  highest-scoring box.
- **Medium** holds by rule of thumb (a forming straight, else the largest matching group; colour sets in Tricolour,
  the best open number in D20) and banks a good-enough hand early (Full House, Large Straight, a Small Straight once
  Large Straight is gone, three or more 4s/5s/6s with that box open). Category ties lean toward an upper box on pace
  for the bonus.
- **Hard** plans the whole turn exactly: every distinct hold, every outcome of the freed dice, through every reroll
  left (`DiceSpace`/`HandScoring` - every hand and held set of a mode's dice numbered once, scored once, so a
  decision is a few array passes). It values a finished hand by:
  - in Standard with no Extended Scores, the bundled **perfect-play table** (`StandardPerfectPlayTable`,
    `composeResources/files/standard_perfect_play.bin`): the optimal expected value of every start-of-turn state,
    solved by the same engine and scoring the game plays with. Hard plays Standard perfectly - about 254 a game.
    `StandardPerfectPlayTableTest` re-works a sample of states from the table's own successors and fails if the rules
    drift from it; `-PregeneratePerfectPlayTable` rewrites it.
  - everywhere else, an estimate: the score less the box's baseline (its average when a whole turn chases it), plus a
    share of any section bonus still reachable. Tricolour's space (~10 MB) is built in the background when a game with
    a Hard CPU starts.
  - Stud maps every seven-dice roll to its best five-dice hand (`RollSpace`); Hit List has its own exact play
    (`HitListPlay.kt`, every difficulty); with a die locked by Unlucky Dice, Hard plays the turn as Medium.

## Persistence

All in each platform's app-private storage. **Every stored id - achievement, style, game mode, preference key - is
permanent once released**: it's what's on players' devices, and achievements and modes will map to Play Games.

- **Scores** - Room, `dicefive.db`, `AppDatabase` version 1 (the schema the app first shipped with, exported to
  `app/shared/schemas/`). Tables `scores` (`ScoreEntry`: name, score, time, `won` - null for a solo game -,
  `isPrimaryPlayer`, the tie-break stats, `onLeaderboard`, `gameModeId`, `playedUnmodified`; every column but `won`
  NOT NULL) and `dismissed_player_stats`. Every later schema change needs a version bump and a migration, and there is
  deliberately no destructive fallback: a missing migration must fail loudly, never wipe scores.
- **Preferences** - four DataStore files (`PreferencesFile`), kept apart so a reset can clear one without the
  others: `settings`, `achievements` (unlock times and counters - "Reset achievements" clears this file only),
  `in_progress_game`, and `online` (Play Games choices, which no reset touches).
- **The in-progress game** - `InProgressGameRepository`, at most one, saved as JSON (`GameStateJson`) after every
  change and cleared when the game ends. Writes are queued and conflated in a process-lifetime scope, so leaving a game
  can't strand its last move. A save that can't be decoded (a missing required field, an unknown mode, a mode's draw
  missing) is treated as nothing to resume. Fields a modifier adds are left out when it's off.
- **Style picks** - the five ids (dice, cup, mat, background, frame) in `settings`, resolved through the catalogs;
  an id nothing recognises draws the category's default.

## Leaderboard and Statistics

- **Leaderboard** (`ScoresScreen`): a switch picks **Combined** - one table of every on-board score, score-descending,
  50 a page, 1st-3rd in gold/silver/bronze, "=" only for a true tie - or **Game Mode**, one card per mode with a score,
  each ranked and paged on its own, no medals. A mode that never counts on Combined is listed only here; a Standard or
  Tricolour game with modifiers is off both. Rows show rank, name, 5x count and score; a long press shows the date (and
  on Combined the mode), which is also the row's spoken state. Not split by player.
- **Statistics** (`StatisticsScreen`): one card per distinct human *name* (a rename starts a new player) - best score,
  first played, played/won/lost, current and best win streak, total and average score, 5x scored, solo games. Every
  recorded game counts, on the Leaderboard or not. A solo game is recorded with a null outcome: played, but not a win,
  a loss, or a break in a streak. A long press dismisses a card (the scores stay); playing again under that name brings
  it back.
- Settings' "Reset leaderboard" deletes every score (and so Statistics).

## Achievements

`model/Achievement.kt` (103), `game/AchievementEngine.kt` (pure: no platform, persistence or clock),
`data/achievements/`, `ui/achievements/`.

- **Ids**: each achievement's enum constant is its English title in snake case, and its stored `id` is that constant
  in lower case (`GETTING_COMFORTABLE` / `"getting_comfortable"`); `AchievementEngineTest` holds every id to it. The id
  is the storage key and will be the Play Games key, so it never changes once released, even if a title's wording
  does. String keys follow the same name (`achievement_getting_comfortable_title`).
- **Player 1 only**: only `players[0]` earns anything; another human is an opponent like a CPU. The exceptions are the
  ledger - the score-band achievements (Tally to Historian: every score in a 50-point band recorded) - which read the
  whole device's Leaderboard, and Professional Roller (career points), which sums only player 1's rows
  (`ScoreEntry.isPrimaryPlayer`, set from the seat, never inferred from a name).
- **Rules**: a solo game counts for games played, feats and thresholds but neither extends nor breaks a win streak; a
  tie at the top is a win; superuser mode (debug builds) doesn't disqualify a game; undo can't inflate a counter, as
  counters move only at game end and only a human roll counts dice. New Personal Best is per name and reads the
  player's best before this game's rows are written. The ledger and career points are derived from the scores table,
  not a counter, so they always agree with the Leaderboard and re-earn from existing scores after a reset.
- **When they're judged** - and the engine, never a call site, decides what's earned:
  - `evaluateAtGameStart` (`GameStartContext`): style and mode picks, such as Fresh Coat Of Paint.
  - `evaluateInProgress`, after every scored box: only what a later turn can't take away (a filled box, a banked bonus).
    Score thresholds are judged only at game end, never off a total an abandoned game would throw away.
  - `unlockNow` and the pre-commit checks: moments a finished card can't show - a hand straight out of the cup, the
    dice and the chosen box together at the moment of a score (Wasted Fortune, Size Isn't Everything, Almost Famous,
    The Dice Hate Me, Commitment Issues).
  - `evaluate` at game end: everything else, and every counter.
  A `Mutex` in `GameViewModel` serialises the read-decide-write of all of them, so two paths can't double-announce.
- **Modes and modifiers**: each mode's own achievements sit in the Game Modes category, and an achievement a mode or
  modifier would make trivial is guarded - the audit is in `GAME_MODES.md`.
- **Display**: declaration order is display order - grouped by `AchievementCategory`, easiest first within each, so a
  ladder's rungs sit together. `AchievementEngineTest` fails if a category is split. Every Miscellaneous and Easter Egg
  achievement is `HIDDEN` (title shown, description "???" until earned) and only those are. The eight Easter Eggs are
  alphabetical, last, and outside Completionist.
- **Progress banners**: a streak announces every step; a running total only at quarter marks, or every
  `progressStepSize` for the very large ones; never for one being unlocked at the same moment.
- **Superuser modes (debug builds only)**: in a game, a hidden hold sequence lets the player set dice (the Time Wasting
  achievement is earned for the sequence in any build); on the Achievements screen, tapping the count banner
  `SUPERUSER_TAP_TARGET` times lets a long press lock/unlock a row or step its progress (an in-memory overlay, never
  written to the shared counters), and a long press on the banner unlocks or relocks everything.
- **Not covered by automated tests**: Let's Finish This (leave and resume) and the end-to-end settings read behind the
  style achievements - both need the concrete DataStore repositories.

Banners, the list and their accessibility are in `UI.md`. Play Games mapping is planned in `.claude/PLAY_GAMES.md`.

## Table art (Styles)

Five independently swappable, independently saved categories (`ui/game/style/`): dice (`DiceStyle`), cups
(`DiceCupStyle`), mats (`DiceMat`), backgrounds (`TableBackground`) and frames (`ScoreFrame`, round the active player's
tab), bundled as `GameVisualTheme`. `StyleCatalog.kt` lists every one: each catalog groups its art into `StyleFamily`s
(a shape or pattern, the tile on the Styles screen) of `StyleColour`s (for frames, designs).

- **Ids** are the family's name then the colour's, from their string keys (`classic_gold`, `world_roman`,
  `bestagons_honeycomb`); `StyleCatalogTest` holds every id to it.
- Every category's first family is **Classic** and free; Classic's first colour is the default (ivory dice, the gold
  casino shaker - the cup on the launcher icon -, the blue mat, the navy felt, the plain ring). Every other family is
  locked behind a number of achievements earned (`StyleUnlock.AchievementCount`) or a specific one
  (`SpecificAchievement`, `AnyAchievement`). A colour can carry its own secret achievement on top
  (`StyleColour.secretAchievement`) and isn't offered until it's earned. A saved pick that's locked draws the default
  but stays saved; resetting achievements also resets picks it locks.
- Secret colours, not offered at all until their achievement is earned: the Irish multicolour dice (Luck of the
  Irish), the golden Egg (Eggcellent Discovery), the Flowerpot's held-stage pots (Greenfingers), the Top Hat with its
  rabbit out (The Magician's Secret) and the D20 dice (Nat 20, Critical Success). Families behind one particular
  achievement are shown padlocked: Googly dice (Completionist), Glitch dice and background (Big Fan), Maths dice (The
  Solution), the Floating Dice background (Not Those Dice!) and the Martini cup (Shaken, Not Tapped).
- A tricoloured die (Tricolour mode) is drawn in the player's style recoloured (`DiceStyle.recoloured`), its pips and
  held ring always in the palette's readable colour.
- The five held-dice slots belong to the mat (`slotSocketBrush`, `slotSocketBorder`), so a new mat can't borrow
  another's.
- Cups draw inside `CupCanvas` (shared shake, pour and pivot), side-on from about 22 degrees above
  (`CUP_VIEW_SQUASH`), on a grid set by their `CupShape` (TALL 58x84, MEDIUM 66x76, SQUAT 76x66). Liquids, plants and
  loose pupils follow shared physics. A few cups draw their own canvas and never tip (Treasure Chest, Volcano, Chicken,
  Picnic Basket, Shipping container). How to make new art, and the cup contract, is `.claude/STYLE_ART.md`; frame cost
  is `.claude/BENCHMARKS.md`.
- **Easter eggs in the art**: the Top Hat's rabbit peeks out after a quiet spell; the Flowerpot grows a sunflower over
  the current player's rolls, blooming only for one who uses every roll of every turn in a three-roll mode; the Egg
  dice with the Chicken cup roll a golden egg one time in a thousand; naming player 1 "Ireland" or "Éire" turns
  Tricolour's dice and boxes green, white and orange; the menu logo's dice roll when tapped and its googly pupils follow the phone.
- The Styles screen: one tile per family; a long press opens a row of previews per colour; picking a cup plays its
  whole roll on the tile. Colour names are spoken, not shown.

## Sound, haptics and motion

- Clips: `cup_shake`, `mat_landing`, `hold`, `unhold` (at 0.35 volume) and `celebration` (a multiplayer game won by a
  human, with gold fireworks). Source files are `app/android/src/main/rawAudioSource/*.ogg`, peak-normalised to
  -0.8 dBFS by ffmpeg at build time into a generated res dir (`NormalizeOggAudioTask`) - never `res/raw/`, never
  hand-adjusted. The build needs `ffmpeg` on PATH.
- Haptics: a tick on hold/unhold, a buzz for the shake. Settings' Sound effects and Vibration switch each off.
- The roll: as the cup shakes, unheld dice are swept off the mat into it; on landing they're thrown back up their own
  columns, bounce off the far wall and tumble to rest on their results - a flat-faced style as a cube of its own faces,
  starting on the face it was picked up showing, so no face ever jumps. A toss is under way in the frame its dice
  arrive (`RollTracker`), so the result never flashes first. Scoring and the next roll both wait for the dice to land.
  Each die casts one ground shadow from a fixed light, in its own outline.
- **Animations** setting (`AnimationLevel`): High (uncapped frame rate), Medium (60fps, ambient decoration still), Low
  (30fps, the simple roll - dice flick through faces where they lie - and a steady score glow), Off (no motion). The
  system's own remove-animations setting counts as Off. Details in `UI.md`, "Reduced motion".

## Settings

Switches (Sound effects, Vibration, Confirm leaving game), the Animations level, and two resets behind confirmations
(achievements, leaderboard). The footer shows the version and opens About and **Licences** - every library, font and
sound the app ships with its licence text, generated at build time (`app/licensing/README.md`). The licence build guards
fail the build on a copyleft or unrecognised licence, a missing licence text or copyright line, or an unlisted bundled
asset.

The licences page is one card per licence, each **one platform `TextView`** (`TextViewLicenceDocument`, Android) in one
platform `ScrollView`, drawn with the app's own scrollbar. Only a single `TextView` lets a selection run across a card's
rows without running into the next card, gives the system's own long-press selection and smart URL selection, and lets a
long press on a link show the browser-style Copy link / Copy text menu. Compose was tried first and failed on a device:
a `LinkAnnotation` opens on every press inside a `SelectionContainer`, long press included; a custom long-press link menu
races the selection gesture and crashed; clearing focus doesn't drop a `SelectionContainer` selection in a dialog; and a
selection can't span separate text elements. The `AndroidView` needs `clipToBounds()`, or scrolled text draws over the
dialog's title. `LicensesDialogTest` covers taps, long presses, toggles and selection under Robolectric.

## Rules screen

`ui/rules/RulesScreen.kt`: two tab rows - **Gameplay**, **Modes**, **Modifiers** - with the pages of the group under
them, one pager across every page. Text is kept in step with the engine (`ScoreCategory` values, the joker rule, the tie
-break order). A new mode or modifier gets its own page.

## Release pipeline

- `VERSION` is the single source of the version: `versionName` is its contents, `versionCode`
  `major*10000 + minor*100 + patch`. `scripts/set-version.sh` writes it; `scripts/bump-patch-version.sh` bumps the
  patch. Never hand-edit it to make a debug build installable: debug builds take a wall-clock `versionCode` of their
  own, so each installs over the last. Every debug build is signed with the repo's own `app/android/debug.keystore`,
  so a debug APK built anywhere installs over another (and has the one SHA-1 Play Games testing registers).
- Every push to `main` that changes what ships runs `.github/workflows/release.yml`: parallel jobs for the Android
  tests, the shared tests (with the iOS compile), lint, the Baseline Profile (generated fresh on an emulator - see
  `EMULATOR.md`), and the APK and App Bundle (from 1.0.0, or `force_apk`). A `release` job publishes the GitHub release
  `v<VERSION>` only once every check passed, attaching `DiceFive-<version>.apk`, `.aab` and `-mapping.txt` (one R8 run,
  one mapping). Before 1.0.0 a release is a pre-release with no files. The workflow then bumps the patch version and
  empties `RELEASE_NOTES.md` in a `[Release] Bump version to X [skip ci]` commit.
- The release description is `RELEASE_NOTES.md` (the maintainer's; Claude never edits it without being asked - see
  `CLAUDE.md`) then "## Changes since X" from `scripts/release-changelog.sh`: every commit since the last release,
  grouped by its `[Category]` prefix. `.githooks/commit-msg` enforces the prefix once `scripts/install-git-hooks.sh` has
  been run.
- Signing: four `ANDROID_RELEASE_*` env vars, from repository secrets in CI; without them the release build is
  unsigned. The keystore (alias `upload`) is the Play App Signing upload key.
- R8 shrinks, minifies and obfuscates release builds (`app/android/r8-rules.pro`); release dex is stored uncompressed
  so it runs from the APK.
- `.github/workflows/update-dependencies.yml` bumps every pinned version monthly (`update_dependency_versions.sh`),
  re-runs the gates, commits `[Dependencies] Update dependency versions` and starts a release. targetSdk is never moved
  by it.
- Warnings are errors: deprecated APIs fail compilation, build-script warnings fail the build, and lint treats
  `Deprecated`/`ObsoleteSdkInt` as errors.
- Two known warnings come from plugins, not this build's scripts, and can't be silenced per plugin: AboutLibraries
  resolves configurations while Gradle plans the build (the "`debugCompileClasspath` was resolved during configuration
  time" CI warning, from its own task inputs), and AGP calls the deprecated `Configuration.setVisible` (shown under
  `--warning-mode all`; it must be gone before Gradle 11). Revisit each on a plugin upgrade.

## Tests and verification

- Pure logic and view models: `app/shared/src/commonTest` (JVM, and compiled for iOS); anything needing Android or a
  rendered screen: `app/android/src/test` under Robolectric (SDK 36, native graphics, set once in
  `robolectric.properties`) - which can also save a screen to a PNG to look at. A test is one behaviour or one screen,
  its cases in sequence; `Showcase` composes several things in one test.
- `./gradlew testDebugUnitTest` runs both and compiles the shared module for iOS; `./gradlew lint` and
  `./gradlew assembleDebug` complete the gates. The store-asset generators run only with `-PgenerateStoreAssets`.
- An Android emulator can boot inside the sandbox (`EMULATOR.md`) for the Baseline Profile journey and screenshots;
  it's not for timing. Real-device checks still matter for TalkBack and for gestures Robolectric doesn't simulate.
