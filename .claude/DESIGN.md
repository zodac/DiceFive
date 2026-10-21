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
  "tutorial"     GameScreen on a GameViewModel of its own, playing TutorialScript.STANDARD (Rules' How to Play page, or New Game)
  "settings"     SettingsScreen (About and Licences open from links at its foot)
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
- **Locked until a first game is finished.** Until any game reaches its end (any mode, solo or not, won or lost; a
  game left unfinished doesn't count), the Game Mode and Modifiers cards show only their titles over "Finish a game to
  unlock game modes" / "... modifiers" (no picker), and the form plays Standard with no modifiers. A "New to the game? Play the tutorial" button, which starts it,
  sits above Start Game, only while locked.
  - The flag is `first_game_completed` (`SettingsRepository.firstGameCompleted`). It's synced, only ever set, and no
    reset clears it. The tutorial game won't set it.
  - It costs nothing once unlocked. It comes in the setup form's single settings read (`GameSetupState.modesUnlocked`),
    and it's written once, at the first game over.
  - While locked, the saved mode and modifier picks are neither restored nor overwritten.

## The tutorial

How it's built, its scripting rules and how to add one for another mode: `TUTORIAL.md`.

- **What it is.** A guided solo game of Standard (`game/Tutorial.kt`, `TutorialScript.STANDARD`): every roll's dice
  and every move are fixed, so it plays the same each time and ends on 382. Its 13 turns show holding and rerolling to a
  5x, Chance for a missed straight, a Large Straight, the upper section, a zero for the next missed straight, scoring
  on the first roll, 4x adding all five dice (first scored in Fives, taken back with Undo, which the card says is
  solo-only), a Full House, the totals, a joker (+100), the upper bonus and 3x. Every turn but the first-roll one (Fours)
  takes all three rolls to get there (`TutorialScriptTest`).
- **How it plays.** It's the real game screen and engine. `GameViewModel(tutorial = ...)` takes each roll's dice from
  the script (`TutorialDice`) and moves on step by step (`tutorialStep`, shown after a roll once its dice have settled): only the step's move is taken (a roll, the
  dice to hold, the box to score, Undo, or the totals; there are no words-only steps, so a card that explains says to roll), and `LocalTutorialGuide` outlines that control in pulsing gold
  (steady under reduced motion) and switches the rest off for TalkBack.
- **The coach card** (`CoachCard`) takes the player tabs' place: "Coach" and "Turn 3 of 13" over the step's words. It's
  held at the height the longest step needs at the standard font size, so nothing moves between steps; at a larger font
  the words scroll inside it. The name and words are a polite live region, read out as each step arrives; the turn is a
  node of its own. The totals button only takes a tap at the steps that ask for it; its pop-up then stays up while the
  card explains it, but isn't focusable, so the one tap on the cup rolls (and the roll closes it).
- **Nothing is kept.** No saved game (Continue stays on the player's own), scores, achievements, counters, settings or
  first-game unlock; no cheat or golden egg, and Undo only at its step. `TutorialGameTest` plays it through against counting stores.
- **Leaving** always asks ("Leave the tutorial?"), whatever the confirm-before-leaving setting. Its Game Over offers
  Done (back where it was started) or New Game.

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
- **Undo** exists in solo games only. It takes back the last score; a roll or hold clears it (they aren't undoable),
  as does any CPU move, and a finished game has none. It's saved with the game, so a resumed game can still undo.
- **The end.** The game ends when every card is full. **Human** totals are recorded; CPU scores never are. The results
  screen shows the ranking with tie-break captions.
- `DiceScoring`, `ScoreCalculator` and `GameEngine` are pure, with no platform dependencies.

**Maximum scores.** These are `GameMode.maxPossibleScore`, each with its derivation as a doc comment.
`GameModeTest` locks each one by playing the perfect game through the engine.

| Mode           | Maximum |
|----------------|---------|
| Standard, Stud | 1575    |
| Tricolour      | 2120    |
| Third Wind     | 4725    |
| D20            | 1350    |
| Hit List       | 785     |
| Quickfire      | 210     |

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

| Mode       | Rules beyond Standard                                                                                                                                                                          | Leaderboard |
|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|
| Standard   | The official rules.                                                                                                                                                                            | Yes         |
| Tricolour  | Dice also land red, yellow or blue. Four colour boxes: Reds/Yellows/Blues (40 for all five that colour) and Coloured House (25, a joker free-fill like Full House).                            | Yes         |
| Third Wind | Every box scored three times (39 turns). One upper bonus, tripled (189 earns 105). The joker starts once the 5x box's three slots are used with a 50 among them.                               | No          |
| Stud       | Seven dice rolled, five hold slots; only the held hand scores. A box can be scored with fewer held, and `HandCompletion` completes the hand.                                                   | No          |
| D20        | Twenty-sided dice, five rolls. A box per number 1-20 plus a wildcard per half. A 75 bonus per half (at 138 and 388). No 5x, no lower section, no Extended Scores. Always drawn in a D20 style. | No          |
| Hit List   | Twelve targets drawn per game plus the Alibi. Hits, exact hits (double) and partial hits. No Extended Scores.                                                                                  | No          |
| Quickfire  | 5x plus three random upper and three random lower boxes are off each game: six turns. The upper bonus threshold scales to the upper boxes left.                                                | No          |

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
  - Room, `dicefive.db`, `AppDatabase` version 2, with each version's schema exported to `app/shared/schemas/`.
  - Tables:
    - `scores` (`ScoreEntry`): name, score, time, `won` (null for a solo game), `isPrimaryPlayer`, the tie-break stats,
      `gameModeId`, `playedUnmodified`, `onLeaderboard`, `playerKey` and a random `uuid`. Every column but `won` is NOT
      NULL.
    - `dismissed_player_stats`: name, when it was dismissed, and `playerKey` (the primary key: one dismissal a player).
  - **`uuid` is the primary key**, a row's id on every device and in the cloud save. It is a `kotlin.uuid.Uuid`, stored
    as its 16 bytes (`UuidConverters`) and written to the cloud save as 32 hex digits. There is no device-local id.
  - **Names are matched by `playerKey`**, never by `playerName` or `COLLATE NOCASE` (SQLite folds only ASCII case, so
    "Ángel" and "ángel" would be two players). It is `playerNameKey(playerName)`, derived in the entity, so a copy with
    another name gets that name's key.
  - **`onLeaderboard` is derived too**: the mode's `countsOnLeaderboard` and `playedUnmodified`, so it can't disagree with
    them. The cloud save still writes it, as format 1's readers require it, but it is worked out again on reading.
  - **Every read made while the app is in use comes from an index**, never a scan or a sort of the table. Statistics
    (`playerRecords`) adds up every game, so reads a whole index, but never the table; the cloud save (`allScores`) reads
    every row by design. `ScoreQueryPlanTest` checks each read's query plan against the real SQLite. The indices:
    - `index_scores_leaderboard_order` and `index_scores_mode_order` hold the Combined table and each mode's card in
      `LEADERBOARD_ORDER`, with the same ASC/DESC per column, so a page is read in order. Room's annotation can't be built
      from `TieBreak.leaderboardOrder`, so a change to the tie-break order must change both (`ScoreQueryPlanTest` fails
      until it does).
    - `playerKey, onLeaderboard, score`: a player's best.
    - `index_scores_primary_player`: player 1's career points and modes played.
    - `index_scores_player_mode_totals` and `index_scores_player_history`: Statistics (below).
  - **Statistics is added up by SQLite.** `playerRecords` reads each player's games summed per mode (a `GROUP BY` over
    a covering index), their latest spelling, and only the outcomes of their decided games, newest first, for the win
    streaks; window functions, which could do the streaks too, need Android 11. `ScoreRepository` adds the modes up
    and walks the streaks. `PlayerStatisticsQueryTest` checks the result equals walking every game.
  - **Leaderboard order is total.** Rows equal on every criterion follow in `rowid` (recording) order, so `LIMIT/OFFSET`
    pages never repeat or skip a row.
  - **Multi-statement reads and writes are one transaction**: a game's rows (`insertGame`), a reset (`clearAll`), the
    achievements' figures (`leaderboardTotals`) and the cloud merge (`mergeCloud`).
  - Every schema change needs a version bump and a migration in `MIGRATIONS` (`data/scores/Migrations.kt`), tested by
    `ScoreDatabaseMigrationTest` from the exported schema it migrates from. There is **deliberately no destructive
    fallback**: a missing migration must fail loudly, never wipe scores. A change to `playerNameKey` changes stored
    keys, so it needs a migration too.
- **Preferences.** Four DataStore files (`PreferencesFile`), kept apart so a reset can clear one:
  - `settings`. Every key but the Animations level and the Styles page fit is synced
    (`SettingsRepository.SYNCED_KEYS`) and stamped `synced_at:<key>` when it changes.
  - `achievements`: unlock times and counters, plus the cloud save's counter shares (this file's own share id and the
    others' counts) and the streak's stamp. "Reset achievements" clears only this file.
  - `in_progress_game`, stamped `saved_at` on every save and clear.
  - `online`: Play Games choices and the cloud save's bookkeeping (leaderboard reset marker, "Last saved").
    No reset clears it.
- **Cloud save** (signed in only): one gzipped JSON snapshot of all of the above but the Animations level and the
  Styles page fit, merged, never picked. `PLAY_GAMES.md`, "Cloud save", is the source.
- **Progress files** (the github app only): the same snapshot, sealed, in a file the player keeps. "Distribution",
  below.
- **The in-progress game.** `InProgressGameRepository` holds at most one, saved as JSON (`GameStateJson`).
  - It's saved after every change and cleared when the game ends.
  - Writes are queued and conflated in a process-lifetime scope, so leaving a game can't lose its last move.
  - An undecodable save is treated as nothing to resume: a missing required field, an unknown mode, or a mode's draw
    missing.
  - Fields a modifier adds are left out when it's off.
  - What Undo goes back to is saved as a nested game (`undoSnapshot`), and the box an undo took back as
    `undoneCategory`. One that can't be read only loses the undo; the game still loads.
- **Style picks.** Five ids (dice, cup, mat, background, frame) in `settings`. An id nothing recognises draws the
  default.

## Leaderboard and Statistics

- **Leaderboard** (`ScoresScreen`). A switch picks one of two views; details are in `UI_SCREENS.md`.
  - **Combined**: every on-board score, score-descending, 50 a page, 1st-3rd in gold/silver/bronze, "=" only for a true
    tie.
  - **Game Mode**: one card per mode, each ranked and paged on its own, no medals. Only games with no modifier on
    (`playedUnmodified`), the same ones its online leaderboard takes.
  - Rows show rank, name, 5x count and score. A long press shows the date (and the mode on Combined), which is also the
    row's spoken state.
- **Statistics** (`StatisticsScreen`). One card per distinct human *name*; a rename starts a new player.
  - Closed, a card is the name and best score ("1,287pts", the unit at 0.75 of the number's size). Its header opens it
    onto an Overview / By mode switch.
  - **Open by default** when every card fits on screen opened (measured unseen, `StatsFitMeasurer`; more than 12
    players skip the measure and don't fit). Otherwise all start closed but the top card of each column (the first
    one in portrait, the first two in landscape), which always open. Opening a card never closes another.
  - **Sort** (`StatisticsSort`): a button at the app bar's end opens a menu of Name, First played, Highest score,
    Highest average and Most games. The one in use is marked with an arrow, up for ascending and down for descending;
    picking it reverses it, picking another starts it in its own direction (A to Z and oldest first for Name and First
    played, highest first for the rest). Ties go by name. A new order deals the cards out afresh: the top card of each
    column opens, the rest as at the start. The choice is the `statistics_sort` preference (`<id>:asc` or `<id>:desc`,
    `StatisticsSortOrder`), loaded with the app container and carried in the cloud save and the GitHub app's export.
  - **Overview**: games, win rate and average (in points), then a won/lost/solo bar with its key, then the current and best win
    streak, 5x scored, total points and first played (date and time).
  - **By mode**: a row per mode played, in mode order: games, win rate, solo games and best score, the name in the
    mode's colour (`UI.md`, "Mode colours"). Under it, a bar of each mode's share of the games.
  - Above 1.3x font, the header's score goes under the name, Overview's details put the label over the value, and
    each By mode name takes its own line over its numbers.
  - **Win rate** is won / (won + lost), a whole percentage rounded half up. Solo games aren't in it; with only solo
    games it's shown as "-" and not spoken.
  - TalkBack: the header is one stop (name, best score, its state and action). Overview is two stops, and each By mode
    row is one, in full sentences. The headings and bars are silent.
  - Every recorded game counts. A solo game's outcome is null: it isn't a win or a loss, and it doesn't break a
    streak.
  - Each card has a delete button (top end, open or closed) that dismisses it after a confirmation; the scores stay.
    Playing again under that name brings it back.
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

There are five independently saved categories (`ui/style/`), bundled as `GameVisualTheme`:

- dice (`DiceStyle`)
- cups (`DiceCupStyle`)
- mats (`DiceMat`)
- backgrounds (`TableBackground`)
- frames (`ScoreFrame`, around the active player's tab)

**Frames** are drawn in the active player's own colour behind their tab (`ScoreFrames.kt`, in dp), and stay 1.5dp off the
text (`ScoreFrameClearanceTest`). One made of many parts (the wreaths, Starry, Nautical, Marquee) is `painted`: drawn once
per colour and tab size, then stamped, as the tab redraws every frame while a score counts up. The wreath goes all the
way round the tab, thinning its inward leaves beside the name, and `FrameCoverageTest` holds those frames to filling the
band along every edge. Unlocks run from Floral at 10 to Marquee at 100, with Nautical at 15 as part of the water set and
Starry at 45, which opens by count and not with the Starry dice, mat and background (Rolling The Midnight Dice).

`StyleCatalog.kt` groups each category's art into `StyleFamily`s (a shape or pattern, which is one tile on the Styles
screen) of `StyleColour`s.

- **Ids** are the family's string key, then the colour's (`classic_gold`, `bestagons_honeycomb`). `StyleCatalogTest`
  enforces this.
  - **One exception: a style folded into another family keeps the id it was released with.** The Beaker cups are
    colours of Glass (`Beaker Blue`, `Beaker Green`) but are still `beaker_blue` and `beaker_green`, because ids are
    permanent. They're listed in the test.
- **A style with a counterpart of the same name is unlocked by the same thing** in every category that has one: Stone
  (dice, mat, background) opens at 65 achievements, Circuit at 80, Neon at 100, Meadow at 75. `StyleCatalogTest` checks
  every family name across dice, cups, mats and backgrounds.
- **The water set opens together, at 15 achievements** (`WATER_SET_UNLOCK`): the Ocean dice, mat and background, the
  Fishbowl cup and the Nautical frame. No count lock is above 100, and the 50 and 100 locks are fixed points.
  - The Ocean background's Waves roll (`TwinkleClock`); its Lagoon is painted once. The dice, mat and Lagoon share
    `drawWaveCrests` and `paintCaustics` (`OceanArt.kt`).
  - The Fishbowl's clownfish swims a rail (`clownfishOnRail`) as a turning solid, its face always swinging towards the
    viewer. Its three rocks roll with the water's lagging tilt, and its fronds lean back against the bowl's tilt and
    swing with the slosh.
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
  - Egg dice (any colour but gold) with the Chicken cup roll a golden egg one time in 500, earned or not, in any
    mode or modifier except D20 (its dice are drawn as D20s).
  - Naming player 1 "Ireland" or "Éire" turns Tricolour green, white and orange.
  - The menu logo's dice roll when tapped, and its googly pupils follow the phone.
  - Seven quick taps on the Settings version pill (each within 1.5s) earn Are You A Developer?, which unlocks the
    Wireframe dice and mat. Raw taps, so TalkBack announces no button.
  - Starting a new game (not resuming one) between midnight and 6am on the phone's clock (`localHourOfDay`) earns
    Rolling The Midnight Dice, which unlocks the Starry dice, mat and background.

## Sound, haptics and motion

- **Clips.** `cup_shake`, `mat_landing`, `hold`, `unhold` (at 0.35 volume), and `celebration` (a multiplayer game won
  by a human, with gold fireworks).
  - The Chicken cup shakes with `cup_shake_chicken` instead of `cup_shake`. A golden egg landing adds
    `chicken_egg_lay` after `mat_landing`.
  - A roll with every die held (What Was The Point Of That?) shakes with `empty_cup_shake` and lands with no
    `mat_landing`.
  - The choice is `shakeSound`/`landingSounds` (`ui/game/RollSounds.kt`).
  - The sources are `app/audio/*.ogg`, shared by both apps.
  - They're peak-normalised to -0.8 dBFS by ffmpeg at build time (`scripts/normalize-audio.sh`): never `res/raw/`,
    never hand-adjusted. Android packages Ogg copies (`NormalizeOggAudioTask`); iOS bundles AAC `.m4a` copies (an
    Xcode build phase in `app/ios/project.yml`). `scripts/tests/normalize-audio-test.sh` checks both keep each
    clip's length (the shake window is timed to them) and peak.
  - The app has one player (`rememberAppSoundEffects`, provided at `DiceFiveApp` as `LocalSoundEffects`). With sound
    on it loads its clips off the main thread a second after launch; otherwise on its first sound. It's held in the
    background and released with the app, so a sound started on one screen carries on to the next.
  - The app follows the Settings switch itself; no screen passes the setting in.
- **Haptics.** A tick on hold or unhold, and a buzz for the shake. Settings switches sound and vibration off
  separately. Vibration is wired like sound: one `DiceHaptics` (`rememberAppHaptics`, `LocalDiceHaptics`) that follows
  the switch. Only the player's own rolls and holds vibrate: a CPU's roll still plays its sounds but never buzzes.
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

- **Contents.** The online account card (the play app) or the progress files card (the github app), at the top; switches
  for Sound effects, Vibration and Confirm leaving game; the Animations level; the Orientation; and two resets behind
  confirmations (achievements, leaderboard).
- **Orientation** (`ScreenOrientation`): Device (the default: turns as the system allows), Portrait or Landscape. Not
  synced, as a phone and a tablet can want different ones. `DiceFiveApp` hands it to `PlatformServices.lockOrientation`;
  on Android `MainActivity` sets `requestedOrientation` (sensor portrait or landscape, so either way up). The manifest's
  `android:appCategory="game"` is what lets the lock hold on a large screen: Android 16+ ignores it there otherwise.
  Tests: `ScreenOrientationSettingTest`, `OrientationLockTest`, `SettingsRowsTest`.
- **Foot of the page.** The version pill is the last thing on the page (not pinned), after About and **Licences**: every library, font and sound, with its licence
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

## Distribution

Two apps from one source, one per way the app reaches players: product flavours of `app/android` (dimension
`distribution`), each with its own source set (`src/play`, `src/github`) and one seam, `Distribution`, which
`MainActivity` and `DiceFiveApplication` call.

| | `play` | `github` |
|---|---|---|
| Ships as | The App Bundle, uploaded to Google Play by hand | The APK on each GitHub release |
| `applicationId` | `net.zodac.dicefive` | `net.zodac.dicefive.github` |
| Google Play Games | Yes (`PLAY_GAMES.md`): the SDK, its manifest entries, Sidekick | None: no SDK, no manifest entries, `NoGameServices` |
| Progress files | None, signed in or not | Export and Import, in Settings |

- **Kept apart so nothing from a file reaches Play Games.** A file's progress can only be imported into the github
  app, which has no Play Games code, and the github app's data can't reach the play app's: they're separate packages
  (data, backups and installs are per package), signed by different keys (Play re-signs the bundle). Pinned by
  `GithubFlavourTest` and `PlayFlavourTest`.
- **The github app is a different app.** It installs beside the play app, not over it; a GitHub install from before
  the split (`net.zodac.dicefive`, 1.0.0) doesn't update to it.
- **Progress files** (`online/ProgressFileCodec`, `ProgressTransfer`; on Android `AndroidProgressFiles`, through the
  system's pickers, with no storage permission).
  - **What's in one**: everything a cloud save holds (`CloudSaveStore.snapshot`), as its own bytes (`CloudSaveCodec`).
  - **An import is the cloud save's merge from another source**: `CloudSaveStore.merge`, then what the merged totals
    reach (`unlockReachedTargets`), so the same rules apply (`PLAY_GAMES.md`, "Cloud save"). Nothing is picked or
    thrown away, and importing a file twice changes nothing. A newer app's file is refused whole, with "update the app".
  - **Sealed, not plain**: `DiceFive`, a format byte and a random nonce, then the save XORed with an HMAC-SHA256
    keystream, then an HMAC-SHA256 of all of it. A file changed by one bit, or sealed by another build, isn't a progress
    file. The key is CI's `PROGRESS_FILE_KEY` secret (64 hex digits), never in the source; a build without it uses an
    all-zero key, and the release workflow refuses to build the APK without it. **The key is permanent**: files made
    with one can't be opened with another.
  - **What it stops**: editing a file by hand, or with a tool written from this public source. Not someone who pulls
    the key out of the APK, or edits a rooted phone's own data, which no offline app can stop. Neither reaches Play
    Games, as above.
  - Named `DiceFive-<yyyy-MM-dd>.dicefive` (the player's day). Tests: `ProgressFileCodecTest`, `ProgressTransferTest`,
    `ProgressFilesViewModelTest`, `ProgressFilesSettingsTest`.
- **Settings card** (`ProgressFilesCard`): Export progress and Import progress, each a whole-row button, where the online
  card would be. Import asks first ("Import Progress?"). Under the rows, while an import runs, its step and count over
  a bar, drawn by the same `StepProgress` as the online card's sync ("Opening the file", then the same four merge
  steps, `MergeStep`, named the same: `MergeStepTest` keeps the two flows in step), for at least 1s
  (`IMPORT_SHOWN_MILLIS`, shared out between the steps, so a quick import's bar still visibly runs: the card catches up
  with the import a step at a time, `nextShownStatus`, counting only while the app is in front); then
  how the last export or import ended, in red if it failed, until the next starts. No toasts. The line is a polite live
  region: TalkBack hears "Importing progress" (not each step), then the outcome.

## Release pipeline

- **Version.** Each app has its own: `app/android/VERSION` and `app/ios/VERSION` (`IOS_SUPPORT.md`).
  - Android's is the single source: `versionName` is its contents, and `versionCode` is
    `major*10000 + minor*100 + patch`.
  - `scripts/set-version.sh <android|ios> X.Y.Z` writes one, and `scripts/bump-patch-version.sh <android|ios>`
    bumps its patch.
  - Debug builds are signed with the repo's `app/android/debug.keystore`, so every debug APK shares one SHA-1 (which
    has no Play Games credential, as the key is public: `PLAY_GAMES.md`, "Play Console"). Built with `-PdevStamp`,
    one also takes the minute it was built as its `versionCode` (`<version>-dev<minute>`), so each installs over the
    last. Without it, a debug build keeps `VERSION`'s, so test
    and lint runs stay up to date. The minute is read through a `ValueSource` when the build runs, never while the
    script is evaluated, or the configuration cache would hand every build the same one.
- **On every push to `main` that changes what Android ships**, `.github/workflows/android-release.yml` runs:
  - parallel jobs: Android tests (a runner per flavour), shared tests with the iOS compile, lint, the Baseline Profile
    (generated fresh on an emulator from the play app, and given to both; see `EMULATOR.md`), and the github APK plus
    the play App Bundle;
  - then a `release` job that publishes GitHub release `v<version>` once every check has passed, with
    `DiceFive-<version>.apk` (github), `.aab` (play), `-mapping.txt` (the APK's) and `-play-mapping.txt` (the bundle's);
  - then a commit that bumps the patch and empties `RELEASE_NOTES.md`'s Shared and Android sections (`[Release] Bump
    Android version to X [skip ci]`).
- **Beside it, not part of it**, `readme-assets.yml` renders the README's feature graphic and screenshots from the
  same push and replaces the files on the `readme-assets` release (`STORE_ASSETS.md`, "The README's images").
- **iOS** has the same flow in `ios-release.yml`: iOS tests and the IPA (plus the simulator screen tour from 1.0.0,
  or with the `force_screenshots` dispatch input), then GitHub release `ios-v<version>` (a pre-release below 1.0.0,
  never "Latest") and a bump of `app/ios/VERSION`. A change to the shared code releases
  both apps; the two release jobs share a `version-bump` concurrency group, so their bump commits don't collide.
- **Release description.** `RELEASE_NOTES.md` (the maintainer's), then "## Changes since X" from
  `scripts/release-changelog.sh`, grouped by each commit's `[Category]`.
  - The notes have three sections, headed `## Shared`, `## Android` and `## iOS`. A platform's release takes Shared
    plus its own, then empties those two; the other platform's waits for its release (`scripts/release-notes.sh`,
    tested by `scripts/tests/release-notes-test.sh`).
  - The changelog is per app: commits since that app's previous tag, leaving out the other app's own code.
- **Signing.** Four `ANDROID_RELEASE_*` env vars (repo secrets in CI); without them the build is unsigned. The keystore
  (alias `upload`) is the Play upload key, and signs both the bundle and the github APK.
- **`PROGRESS_FILE_KEY`** (a repo secret): the github APK's progress file key ("Distribution"). The apk job fails
  without it.
- **R8** shrinks and obfuscates release builds (`app/android/r8-rules.pro`). Release dex is stored uncompressed.
  - It lays the dex out from the startup profile (`dexLayoutOptimization`): the code a cold start runs to the menu
    (`StartupProfileGenerator`) goes first. The release workflow fails if it generates no `startup-prof.txt`.
- **Dependencies.** `.github/workflows/update-dependencies.yml` bumps every pinned version monthly, re-runs the gates,
  commits `[Dependencies] Update dependency versions` and starts a release. It never moves targetSdk.
- **Warnings are errors**: deprecated APIs, build-script warnings, and lint's `Deprecated`/`ObsoleteSdkInt`.
- **Two known plugin warnings** can't be silenced. Revisit each on a plugin upgrade.
  - AboutLibraries resolves configurations during configuration time ("`playDebugCompileClasspath` was resolved during
    configuration time").
  - AGP calls the deprecated `Configuration.setVisible`. It must be gone before Gradle 11.

## Verification

- **Gates and tests**: the `test` skill.
- **Renders**: the `render` skill.
- **The real app on the sandbox emulator**: the `emulator` skill.
- **A real device is still needed** for TalkBack's speech, real frame times, haptics, the accelerometer and gestures
  Robolectric doesn't simulate. Say what was and wasn't seen.
