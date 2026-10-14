# DiceFive: design reference

What the app does and why. The interface is in `UI.md` (plus `UI_BOARD.md` and `UI_SCREENS.md`), modes in
`GAME_MODES.md`, and art in `STYLE_ART.md`.

## Stack

- Platform code sits behind three seams. View models take an `AppContainer`, never a `Context`.
  - `platform/PlatformServices`: sound, haptics, accelerometer, transient messages, licences, reduce motion, frame cap.
  - `BuildInfo`: version and debug flag.
  - `AppContainer`: what each platform's entry point builds.
- There is no `kotlinx.serialization` or `kotlinx-datetime`; both were dropped to keep the APK small.
  - `data/Json.kt` is a small strict JSON reader and writer.
  - Timestamps go through `expect fun formatTimestamp`.
- One dark theme only.

## Screens

```
NavHost(start = "menu")
  "menu"         MenuScreen - Play (New Game | Continue), Achievements, Styles, Leaderboard, Statistics, Rules, Settings
  "play" graph   one GameViewModel scoped to the graph (no player lists through nav args), shared by:
    "play/setup"   GameSetupScreen (optional `resume` argument resumes the saved game)
    "play/game"    GameScreen, then GameOverScreen and the scorecard review
  "scores"  ScoresScreen (the Leaderboard)   "statistics"  "achievements"  "styles"  "rules"
  "settings"     SettingsScreen (About and Licences open from its footer)
```

## Game setup

- **Players.** There are 1-4.
  - Player 1 is always human and is the only seat that earns achievements.
  - Players 2-4 are Human or CPU.
  - A CPU has a difficulty (Easy/Medium/Hard, default Medium). Its name is generated at Start Game, unique in the game
    (`AiNameGenerator`).
- **Names.**
  - An empty name field shows a localised "Player N" hint, and the game uses that name.
  - Names are sanitised as they're typed (`model/PlayerName.kt`).
  - Names are capped by width per player count (`maxPlayerNameLength` 14/12/10/8; see `UI_BOARD.md`).
- **Colours.** Each player picks a distinct colour (`PlayerColour`).
- **Remembered.** The mode, the modifiers and everything on the form are saved for next time in `SettingsRepository`.

## The turn and the rules

All numbers here are Standard's. Each comes from the game's `GameMode`.

- **A turn** starts with 5 dice, 3 rolls and nothing held. A roll rolls every unheld die.
- **One roll path.** Every roll, human or CPU, lands through `GameViewModel.performRoll` after the cup has shaken for
  `CUP_SHAKE_MILLIS`.
  - A human's roll comes from the cup tap (`onCupTap`): a finger or a phone shake, which calls
    `GameViewModel.shakeAndRoll`.
  - A CPU's roll comes from its own turn loop. Both shakes are timed in the view model and pause with the game.
  - A tap while a roll is shaking or landing is ignored.
- **Holding** toggles a die once the turn has rolled. It's still allowed after the last roll: it does nothing then,
  but disabled dice looked broken.
- **Scoring** is any open box after the first roll. `ScoreCalculator` applies two rules:
  - The upper bonus: 63 or more in the upper section earns 35.
  - The joker rule. Every 5x after a scored 50 adds a +100 chip, recorded on the box it scores in (`jokerBonusSlots`) and shown there. If the 5x box is filled, the score goes in the
    matching upper box if it's open, else any open lower box (Full House and the straights at their fixed values),
    else a zero in any open box.
- **The CPU** scores, then plays its rolls with short pauses so it's visibly animated.
- **Turn timer** (a modifier):
  - Running out on a rolled turn scores the first open box (in Stud, the hand is completed first).
  - Running out before the first roll forfeits the turn as a zero in the first open box, with no roll
    (`GameEngine.forfeitTurn`).
  - CPUs time out the same way.
- **Undo** exists in solo games only. It steps back one human roll, hold or score. Any CPU move clears it, and a
  finished game has none.
- **The end.** The game ends when every card is full. **Human** totals are recorded; CPU scores never are. The results
  screen shows the ranking with tie-break captions.
- `DiceScoring`, `ScoreCalculator` and `GameEngine` are pure, with no platform dependencies.

**Maximum scores.** These are `GameMode.maxPossibleScore`, each with its derivation as a doc comment.
`GameModeTest` locks each one by playing the perfect game through the engine.

| Mode | Maximum |
|---|---|
| Standard, Stud | 1575 |
| Tricolour | 2120 |
| Third Wind | 4725 |
| D20 | 1350 |
| Hit List | 785 |
| Quickfire | 210 |

The Leaderboard pads scores to the digit width of `HIGHEST_POSSIBLE_SCORE` (4 digits).

### Tie-break house rule

The official rules call a tie a shared win. Here, equal scores are separated by whoever had more handicaps, checked in
this order:

1. Fewest 5x.
2. Most zeroed boxes.
3. Fewest Tricolour colour boxes scored (Tricolour only).
4. Lowest upper section (without its bonus).
5. Lowest Chance.
6. Lowest 3x.
7. Lowest 4x.

The first criterion that differs decides it. If all match, it's a true tie with a shared rank ("=2").

- **Where it's used.** `game/TieBreak.kt` holds the criteria and `TieBreak.rank`. They're shared by the results
  screen (its caption: "Won on fewer 5x") and the Tie Break achievement.
- **The Leaderboard** uses the same criteria minus the Tricolour one, because its Combined table mixes modes. Its SQL
  `ORDER BY` mirrors the comparator.
- **Ranking only.** A raw-score tie at the top still counts as a win for achievements and statistics.
- **Player tabs** rank by total during play ("=1st"), then take `TieBreak.rank` once every card is full.

## Game modes

`model/GameMode.kt`. **Every rule that can differ between modes is a field on the mode. There is never an
`if (mode == X)`.** A box's own scoring is fixed (`ScoreCategory`); a mode only chooses which boxes are on its card.
`GAME_MODES.md` covers adding or changing a mode.

| Mode | Rules beyond Standard | Leaderboard |
|---|---|---|
| Standard | The official rules. | Yes |
| Tricolour | Dice also land red, yellow or blue. Four colour boxes: Reds/Yellows/Blues (40 for all five that colour) and Coloured House (25, a joker free-fill like Full House). | Yes |
| Third Wind | Every box scored three times (39 turns). One upper bonus, tripled (189 earns 105). The joker starts once the 5x box's three slots are used with a 50 among them. | No |
| Stud | Seven dice rolled, five hold slots; only the held hand scores. A box can be scored with fewer held, and `HandCompletion` completes the hand. | No |
| D20 | Twenty-sided dice, five rolls. A box per number 1-20 plus a wildcard per half. A 75 bonus per half (at 138 and 388). No 5x, no lower section, no Extended Scores. Always drawn in a D20 style. | No |
| Hit List | Twelve targets drawn per game plus the Alibi. Hits, exact hits (double) and partial hits. No Extended Scores. | No |
| Quickfire | 5x plus three random upper and three random lower boxes are off each game: six turns. The upper bonus threshold scales to the upper boxes left. | No |

A mode off the Leaderboard still records its games for Statistics and career points. The Leaderboard's Game Mode view
lists it on its own card.

## Modifiers

Optional extras on top of any mode. **Any modifier takes a game off the Leaderboard** (`GameState.hasModifiers`;
`ScoreEntry.playedUnmodified` records it).

- **Turn Timer.** A whole-turn limit: 30, 60 or 120 seconds, or any typed length from 5 to 999.
- **Number of Rolls.** 1-9 rolls a turn instead of the mode's (remembered while off).
- **Stored Rolls.**
  - A player's unused rolls carry over to their own next turn, optionally capped (an empty cap is no cap).
  - `GameState.turnRolls` is what the turn started with.
- **Extended Scores.**
  - Three extra boxes in their own section, counting toward the total only: Two Pair (the four dice making two
    different pairs), Evens and Odds.
  - Locked (with a note, the pick kept) in D20 and Hit List.
  - Always read a card from `PlayerState.categories`/`GameState.categories` (`categoriesWith`), never
    `gameMode.categories`.
- **Unlucky Dice.**
  - After each roll, each rolled die has an `oddsPercent` chance (10-50%) of landing locked in chains, at most
    `maxDice` (1-5) a roll.
  - A locked die can't be held or scored, and rerolls with the rest.
  - Without the modifier, the dice are dealt exactly as before.

## The CPU

`game/AiTurnPlayer.kt` plays from the player's own mode and card.

- **Easy** never holds. It rerolls everything until some open box scores above zero, then takes the highest.
- **Medium** plays by rules of thumb.
  - It holds a forming straight, else the largest matching group (colour sets in Tricolour, the best open number in
    D20).
  - It banks a good-enough hand early: Full House, Large Straight, a Small Straight once Large is gone, or three or
    more 4s/5s/6s with that box open.
  - Ties lean toward an upper box that's on pace for the bonus.
- **Hard** plans the whole turn exactly: every hold, every outcome, every reroll left. `DiceSpace`/`HandScoring`
  number and score each hand once, so a decision is a few array passes. It values a finished hand as follows.
  - **Standard without Extended Scores.** The bundled **perfect-play table** (`StandardPerfectPlayTable`,
    `composeResources/files/standard_perfect_play.bin`) holds the optimal expected value of every start-of-turn state.
    - Hard plays Standard perfectly, about 254 a game.
    - `StandardPerfectPlayTableTest` fails if the rules drift from the table. Regenerating it is in the `test` skill.
  - **Elsewhere.** An estimate: the score less the box's baseline, plus a share of any reachable section bonus.
    Tricolour's ~10 MB space is built in the background when a game with a Hard CPU starts.
  - **Special cases.**
    - Stud maps each seven-dice roll to its best five (`RollSpace`).
    - Hit List has its own exact play at every difficulty (`HitListPlay.kt`).
    - With a die locked by Unlucky Dice, Hard plays as Medium.

## Persistence

Everything is in app-private storage. **Every stored id is permanent once released**, because achievements and modes
will map to Play Games.

- **Scores.**
  - Room, `dicefive.db`, `AppDatabase` version 1, with its schema exported to `app/shared/schemas/`.
  - Tables:
    - `scores` (`ScoreEntry`): name, score, time, `won` (null for a solo game), `isPrimaryPlayer`, the tie-break stats,
      `onLeaderboard`, `gameModeId` and `playedUnmodified`. Every column but `won` is NOT NULL.
    - `dismissed_player_stats`.
  - Every schema change needs a version bump and a migration. There is **deliberately no destructive fallback**: a
    missing migration must fail loudly, never wipe scores.
- **Preferences.** Four DataStore files (`PreferencesFile`), kept apart so a reset can clear one:
  - `settings`.
  - `achievements`: unlock times and counters. "Reset achievements" clears only this file.
  - `in_progress_game`.
  - `online`: Play Games choices. No reset touches it.
- **The in-progress game.** `InProgressGameRepository` holds at most one, saved as JSON (`GameStateJson`).
  - It's saved after every change and cleared when the game ends.
  - Writes are queued and conflated in a process-lifetime scope, so leaving a game can't lose its last move.
  - An undecodable save is treated as nothing to resume: a missing required field, an unknown mode, or a mode's draw
    missing.
  - Fields a modifier adds are left out when it's off.
- **Style picks.** Five ids (dice, cup, mat, background, frame) in `settings`. An id nothing recognises draws the
  default.

## Leaderboard and Statistics

- **Leaderboard** (`ScoresScreen`). A switch picks one of two views; details are in `UI_SCREENS.md`.
  - **Combined**: every on-board score, score-descending, 50 a page, 1st-3rd in gold/silver/bronze, "=" only for a true
    tie.
  - **Game Mode**: one card per mode, each ranked and paged on its own, no medals.
  - Rows show rank, name, 5x count and score. A long press shows the date (and the mode on Combined), which is also the
    row's spoken state.
- **Statistics** (`StatisticsScreen`). One card per distinct human *name*; a rename starts a new player.
  - It shows best score, first played, played/won/lost, current and best win streak, total and average, 5x scored and
    solo games.
  - Every recorded game counts. A solo game's outcome is null: it isn't a win or a loss, and it doesn't break a
    streak.
  - A long press dismisses a card (the scores stay). Playing again under that name brings it back.
- Settings' "Reset leaderboard" deletes every score, and with them the Statistics.

## Achievements

`model/Achievement.kt` (110), `game/AchievementEngine.kt` (pure), `data/achievements/`, `ui/achievements/`.

- **Ids.**
  - The enum constant is the English title in snake case; the stored `id` is that in lower case
    (`GETTING_COMFORTABLE`/`"getting_comfortable"`). `AchievementEngineTest` enforces it.
  - String keys follow the constant: `achievement_<constant>_title`.
- **Player 1 only.** Other humans are opponents like CPUs. Two exceptions:
  - The ledger (score-band achievements Tally to Historian: every score in a 50-point band) reads the whole device's
    Leaderboard.
  - Professional Roller (career points) sums player 1's rows (`ScoreEntry.isPrimaryPlayer`, set from the seat, never
    from a name).
- **Rules.**
  - A solo game counts for games played, feats and thresholds, but neither extends nor breaks a win streak.
  - A tie at the top is a win.
  - Superuser mode doesn't disqualify a game.
  - Undo can't inflate a counter: counters move only at game end, and only human rolls count dice.
  - New Personal Best is per name, and reads the best from before this game's rows are written.
  - Full Set's counter (`FIVE_X_NUMBERS`) is a set of flags, one bit per number; its progress is the bits set
    (`AchievementCounter.progressOf`).
  - The ledger, career points and Grand Tour (player 1's modes played) are derived from the scores table, so they always agree with the Leaderboard and
    re-earn after a reset.
- **When they're judged.** The engine decides, never a call site.
  - `evaluateAtGameStart`: style and mode picks (Fresh Coat Of Paint).
  - `evaluateInProgress`, after each scored box: only what a later turn can't take away. Score thresholds wait for game
    end.
  - `unlockNow` and the pre-commit checks: moments a finished card can't show, such as a hand straight out of the cup
    or the dice and chosen box together (Wasted Fortune, Size Isn't Everything, Almost Famous, The Dice Hate Me,
    Commitment Issues).
  - `evaluate` at game end: everything else, and every counter.
  - Every unlock goes through `AchievementUnlocker`, whose locks (its own and the store's `locked`) serialise them
    all across screens, so nothing is announced twice.
- **Modes.** Each mode's own achievements are in the Game Modes category. Achievements a mode would make trivial are
  guarded (`GAME_MODES_AUDIT.md`).
- **Display.**
  - Declaration order is display order: grouped by `AchievementCategory`, easiest first, so ladders sit together.
    `AchievementEngineTest` fails if a category is split.
  - Every Miscellaneous and Easter Egg achievement is `HIDDEN` (the title is shown, the description is "???" until
    earned), and only those are.
  - The ten Easter Eggs are alphabetical, last, and outside Completionist.
- **Progress banners.** A streak announces every step. A running total announces only at quarter marks (or every
  `progressStepSize`), and never alongside its own unlock.
- **Superuser modes (debug builds only).**
  - In a game, a hidden hold sequence lets the player set dice. Time Wasting is earned for the sequence in any build.
  - On the Achievements screen, tapping the count banner `SUPERUSER_TAP_TARGET` times turns on long-press editing:
    - a long press on a row locks, unlocks or steps it (in-memory, never written);
    - a long press on the banner toggles everything.
- **Untested automatically.** Let's Finish This (leave and resume), and the end-to-end settings read behind the style
  achievements.

## Table art (Styles)

There are five independently saved categories (`ui/game/style/`), bundled as `GameVisualTheme`:

- dice (`DiceStyle`)
- cups (`DiceCupStyle`)
- mats (`DiceMat`)
- backgrounds (`TableBackground`)
- frames (`ScoreFrame`, around the active player's tab)

`StyleCatalog.kt` groups each category's art into `StyleFamily`s (a shape or pattern, which is one tile on the Styles
screen) of `StyleColour`s.

- **Ids** are the family's string key, then the colour's (`classic_gold`, `bestagons_honeycomb`). `StyleCatalogTest`
  enforces this.
- **The Classic family** comes first in every category and is free. Its first colour is the default: ivory dice, the
  gold casino shaker (the launcher icon's cup), the blue mat, navy felt, a plain ring.
- **Locks.** Every other family is locked behind an achievement count (`StyleUnlock.AchievementCount`) or a specific
  achievement (`SpecificAchievement`, `AnyAchievement`).
  - A colour can also be secret (`StyleColour.secretAchievement`): not offered at all until earned.
  - A locked saved pick draws the default but stays saved. Resetting achievements resets the picks it locks.
  - Which styles and colours are locked, and how locks look, is in `UI_SCREENS.md`, "Style locks".
- **Tricolour** draws a die in the player's style recoloured (`DiceStyle.recoloured`). Its pips and held ring always
  use the palette's readable colours.
- **Held-dice slots** belong to the mat (`slotSocketBrush`, `slotSocketBorder`).
- **Cups** draw inside `CupCanvas`, which handles the shared shake, pour and pivot.
  - The view is side-on from about 22 degrees (`CUP_VIEW_SQUASH`), on a `CupShape` grid: TALL 58x84, MEDIUM 66x76,
    SQUAT 76x66.
  - Liquids, plants and loose pupils share physics.
  - A few cups draw their own canvas and never tip: Treasure Chest, Volcano, Chicken, Picnic Basket, Shipping
    container.
  - The cup contract is in `STYLE_ART.md`.
- **Easter eggs in the art:**
  - The Top Hat's rabbit peeks out after a quiet spell.
  - The Flowerpot grows a sunflower over the current player's rolls. It blooms only for one who uses every roll of
    every turn in a three-roll mode.
  - Egg dice with the Chicken cup roll a golden egg one time in 1000.
  - Naming player 1 "Ireland" or "Éire" turns Tricolour green, white and orange.
  - The menu logo's dice roll when tapped, and its googly pupils follow the phone.
  - Seven quick taps on the Settings version pill (each within 1.5s) earn Are You A Developer?, which unlocks the
    Wireframe dice and mat. Raw taps, so TalkBack announces no button.
  - Starting a new game (not resuming one) between midnight and 6am on the phone's clock (`localHourOfDay`) earns
    Rolling The Midnight Dice, which unlocks the Starry dice, mat and background.

## Sound, haptics and motion

- **Clips.** `cup_shake`, `mat_landing`, `hold`, `unhold` (at 0.35 volume), and `celebration` (a multiplayer game won
  by a human, with gold fireworks).
  - The sources are `app/android/src/main/rawAudioSource/*.ogg`.
  - They're peak-normalised to -0.8 dBFS by ffmpeg at build time (`NormalizeOggAudioTask`): never `res/raw/`, never
    hand-adjusted.
  - The app has one player (`rememberAppSoundEffects`, provided at `DiceFiveApp` as `LocalSoundEffects`). With sound
    on it loads its clips off the main thread a second after launch; otherwise on its first sound. It's held in the
    background and released with the app, so a sound started on one screen carries on to the next.
- **Haptics.** A tick on hold or unhold, and a buzz for the shake. Settings switches sound and vibration off
  separately.
- **The roll.**
  - As the cup shakes, unheld dice are swept into it.
  - On landing they're thrown up their own columns, bounce off the far wall, and tumble to rest on their results. A
    flat-faced die tumbles as a cube of its own faces, starting from the face it showed, so no face ever jumps.
  - The toss starts in the frame its dice arrive (`RollTracker`), so the result never flashes first.
  - Scoring and the next roll both wait for the dice to land.
  - Each die casts one shadow from a fixed light.
- **Animations setting** (`AnimationLevel`): High (uncapped), Medium (60fps, ambient decoration still), Low (30fps,
  simple roll) or Off. The system's remove-animations setting counts as Off. Details are in `UI.md`, "Reduced motion".

## Settings

- **Contents.** Switches for Sound effects, Vibration and Confirm leaving game; the Animations level; and two resets
  behind confirmations (achievements, leaderboard).
- **Footer.** It shows the version and opens About and **Licences**: every library, font and sound, with its licence
  text, generated at build time (`app/licensing/README.md`).
- **Licence guards.** The build fails on a copyleft or unknown licence, a missing text or copyright line, or an
  unlisted bundled asset.
- **The Licences list** is one card per licence. On Android each card is **one platform `TextView`**
  (`TextViewLicenceDocument`), in one `ScrollView`, with the app's scrollbar.
  - Only that gives selection across a card's rows without running into the next card, system long-press and smart
    URL selection, and a Copy link / Copy text menu.
  - Compose failed on a device:
    - a `LinkAnnotation` opens on every press inside a `SelectionContainer`;
    - a custom link menu raced selection and crashed;
    - a selection in a dialog doesn't clear;
    - a selection can't span separate texts.
  - The `AndroidView` needs `clipToBounds()`, or scrolled text draws over the title.
  - Tests: `LicensesDialogTest`.

## Rules screen

`ui/rules/RulesScreen.kt`.

- **Layout.** Two tab rows: groups (**Gameplay**, **Modes**, **Modifiers**), then the pages of the chosen group. One
  pager runs across every page.
- **Accuracy.** The text is kept in step with the engine: `ScoreCategory` values, the joker rule, the tie-break order.
- **New mode or modifier.** It gets its own page; see `GAME_MODES.md`.

## Release pipeline

- **Version.**
  - `VERSION` is the single source: `versionName` is its contents, and `versionCode` is
    `major*10000 + minor*100 + patch`.
  - `scripts/set-version.sh` writes it, and `scripts/bump-patch-version.sh` bumps the patch.
  - Debug builds take a wall-clock `versionCode` and the repo's `app/android/debug.keystore`. Every debug APK therefore
    installs over another and shares one SHA-1 (which Play Games testing registers).
- **On every push to `main` that changes what ships**, `.github/workflows/release.yml` runs:
  - parallel jobs: Android tests, shared tests with the iOS compile, lint, the Baseline Profile (generated fresh on an
    emulator; see `EMULATOR.md`), and the APK plus App Bundle (from 1.0.0, or with `force_apk`);
  - then a `release` job that publishes GitHub release `v<VERSION>` once every check has passed, with
    `DiceFive-<version>.apk`, `.aab` and `-mapping.txt` (one R8 run, one mapping). Before 1.0.0 the release is a
    pre-release with no files;
  - then a commit that bumps the patch and empties `RELEASE_NOTES.md` (`[Release] Bump version to X [skip ci]`).
- **Release description.** `RELEASE_NOTES.md` (the maintainer's), then "## Changes since X" from
  `scripts/release-changelog.sh`, grouped by each commit's `[Category]`.
- **Signing.** Four `ANDROID_RELEASE_*` env vars (repo secrets in CI); without them the build is unsigned. The keystore
  (alias `upload`) is the Play upload key.
- **R8** shrinks and obfuscates release builds (`app/android/r8-rules.pro`). Release dex is stored uncompressed.
  - It lays the dex out from the startup profile (`dexLayoutOptimization`): the code a cold start runs to the menu
    (`StartupProfileGenerator`) goes first. The release workflow fails if it generates no `startup-prof.txt`.
- **Dependencies.** `.github/workflows/update-dependencies.yml` bumps every pinned version monthly, re-runs the gates,
  commits `[Dependencies] Update dependency versions` and starts a release. It never moves targetSdk.
- **Warnings are errors**: deprecated APIs, build-script warnings, and lint's `Deprecated`/`ObsoleteSdkInt`.
- **Two known plugin warnings** can't be silenced. Revisit each on a plugin upgrade.
  - AboutLibraries resolves configurations during configuration time ("`debugCompileClasspath` was resolved during
    configuration time").
  - AGP calls the deprecated `Configuration.setVisible`. It must be gone before Gradle 11.

## Verification

- **Gates and tests**: the `test` skill.
- **Renders**: the `render` skill.
- **The real app on the sandbox emulator**: the `emulator` skill.
- **A real device is still needed** for TalkBack's speech, real frame times, haptics, the accelerometer and gestures
  Robolectric doesn't simulate. Say what was and wasn't seen.
