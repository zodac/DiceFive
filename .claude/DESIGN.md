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

```text
NavHost(start = "menu")
  "menu"         MenuScreen - Play (New Game | Continue), Achievements, Styles, Leaderboard, Statistics, Rules, Settings
  "play" graph   one GameViewModel scoped to the graph (no player lists through nav args), shared by:
    "play/setup"   GameSetupScreen
    "play/game"    GameScreen, then GameOverScreen and the scorecard review (Continue comes straight here and loads
                   the saved game)
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
    - Names are capped by width per player count (`model/NameWidth.kt`'s `maxPlayerNameLength` 14/12/10/8; see
        `UI_BOARD.md`).
    - `GameEngine.newGame` holds every table to its rules: 1-4 players, a Human in seat 1 whenever any play, distinct
        colours, and no two Human names alike (`duplicateHumanNameSlots`; the Leaderboard and Statistics tell players apart
        by name). The New Game form asks the same function, and `startGame` refuses a clash.
- **Colours.** Each player picks a distinct colour (`PlayerColour`).
- **Remembered.** The mode, the modifiers and everything on the form are saved for next time in `SettingsRepository`.
- **Locked until a first game is finished.** Until any game reaches its end (any mode, solo or not, won or lost; a
    game left unfinished doesn't count), the Game Mode and Modifiers cards show only their titles over "Finish a game to
    unlock game modes" / "... modifiers" (no picker), and the form plays Standard with no modifiers. A "New to the game?
    Play the tutorial" button, which starts it, sits above Start Game, only while locked.
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
    the script (`TutorialDice`) and moves on step by step (`tutorialStep`, shown after a roll once its dice have
    settled): only the step's move is taken (a roll, the dice to hold, the box to score, Undo, or the totals; there are
    no words-only steps, so a card that explains says to roll), and `LocalTutorialGuide` outlines that control in
    pulsing gold (steady under reduced motion) and switches the rest off for TalkBack.
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
    - The joker rule. Every 5x after a scored 50 adds a +100 chip, recorded on the box it scores in (`jokerBonusSlots`)
      and shown there. If the 5x box is filled, the score goes in the
        matching upper box if it's open, else any open lower box (Full House and the straights at their fixed values),
        else a zero in any open box.
- **The CPU** scores, then plays its rolls with short pauses so it's visibly animated.
- **Turn timer** (a modifier):
    - Running out on a rolled turn scores the first open box (in Stud, the hand is completed first).
    - Running out before the first roll forfeits the turn as a zero in the first open box, with no roll
        (`GameEngine.forfeitTurn`). Both are `GameEngine.timeOut`.
    - CPUs time out the same way.
- **Undo** exists in solo games only. It takes back the last score; a roll or hold clears it (they aren't undoable),
    as does any CPU move, and a finished game has none. It's saved with the game, so a resumed game can still undo.
- **The end.** The game ends when every card is full. **Human** totals are recorded; CPU scores never are. The results
    screen shows the ranking with tie-break captions.
- `DiceScoring`, `ScoreCalculator` and `GameEngine` are pure, with no platform dependencies.

**Maximum and minimum scores.** These are `GameMode.maxPossibleScore` and `minPossibleScore`, each with its derivation
as a doc comment. `GameModeTest` locks each one by playing that game through the engine: the perfect game, and the
lowest. Chance is the only box that can't score 0, so a mode's minimum is its Chance slots at five 1s each. Quickfire's
is 0, because some of its cards have Chance switched off. The game screen marks a running total as a new Leaderboard
score only from the minimum up (`newLeaderboardScoreSeats`).

| Mode           | Maximum | Minimum |
|----------------|---------|---------|
| Standard, Stud | 1575    | 5       |
| Tricolour      | 2120    | 5       |
| Third Wind     | 4725    | 15      |
| D20            | 1350    | 0       |
| Hit List       | 785     | 0       |
| Quickfire      | 210     | 0       |

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
- **The Leaderboard** uses the same criteria minus the Tricolour one, as every board shares one SQL `ORDER BY`, which
    mirrors the comparator.
- **Who won.** `GameState.wonBy` (`game/Standings.kt`): rank 1 after the tie-break, so only a true tie shares a win.
    The one rule for Statistics' `won`, the win streak, every win-gated achievement and One More Time. Score rows
    recorded up to Android 1.2.1 keep the raw-score rule they were saved with.
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
| D20        | Twenty-sided dice, five rolls. A box per number 1-20 plus a wildcard per half. A 75 bonus per half (at 150 and 400). No 5x, no lower section, no Extended Scores. Always drawn in a D20 style. | No          |
| Hit List   | Twelve targets drawn per game plus the Alibi. Hits, exact hits (double) and partial hits. No Extended Scores.                                                                                  | No          |
| Quickfire  | 5x plus three random upper and three random lower boxes are off each game: six turns. The upper bonus threshold scales to the upper boxes left.                                                | No          |

A mode off the Leaderboard still records its games for Statistics and career points. The Leaderboard's drop-down
lists it as a board of its own.

## Modifiers

Optional extras on top of any mode. **Any modifier takes a game off the Leaderboard and out of Statistics**
(`GameState.hasModifiers`; `ScoreEntry.playedUnmodified` records it), **and earns no achievement** but the modifiers'
own: Rainy Day Fund, Bad Omen, and Everything Everywhere All At Once (`Achievement.earnableWithModifiers`). The row is
still recorded, for the cloud save. The New Game screen's note and each modifier's Rules page say so.

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
    - Room, `dicefive.db`, `AppDatabase`, with each version's schema exported to `app/shared/schemas/`.
    - Tables:
        - `scores` (`ScoreEntry`): name, score, time, `won` (null for a solo game), `isPrimaryPlayer`, the tie-break stats,
            `gameModeId`, `playedUnmodified`, `onLeaderboard`, `playerKey`, `diceRolled`, and a random `uuid`. Every column
            but `won` is NOT NULL. `diceRolled` (`PlayerState.diceRolled`: each roll's unheld dice, saved with the game)
            came in database version 6; older games have 0, and the cloud save's rows add it at the end, read as 0
            when missing.
        - `dismissed_player_stats`: name, when it was dismissed, and `playerKey` (the primary key: one dismissal a player).
    - **`uuid` is the primary key**, a row's id on every device and in the cloud save. It is a `kotlin.uuid.Uuid`, stored
        as its 16 bytes (`UuidConverters`) and written to the cloud save as 32 hex digits. There is no device-local id.
    - **Names are matched by `playerKey`**, never by `playerName` or `COLLATE NOCASE` (SQLite folds only ASCII case, so
        "Ángel" and "ángel" would be two players). It is `playerNameKey(playerName)`, derived in the entity, so a copy with
        another name gets that name's key.
    - **`onLeaderboard` is derived too**: the mode's `countsOnLeaderboard` and `playedUnmodified`, so it can't disagree with
        them. The cloud save doesn't hold it from format 2 on; a format 1 save's is ignored, and it is worked out on reading.
    - **Every read made while the app is in use comes from an index**, never a scan or a sort of the table. Statistics
        (`playerRecords`) adds up every game, so reads a whole index, but never the table; the cloud save (`allScores`) reads
        every row by design. `ScoreQueryPlanTest` checks each read's query plan against the real SQLite. The indices:
        - `index_scores_unmodified_order` and `index_scores_mode_order` hold the Combined board and each mode's board in
            `LEADERBOARD_ORDER`, with the same ASC/DESC per column, so a page is read in order. Room's annotation can't be built
            from `TieBreak.leaderboardOrder`, so a change to the tie-break order must change both (`ScoreQueryPlanTest` fails
            until it does).
        - `playerKey, onLeaderboard, score`: a player's best.
        - `index_scores_primary_player`: player 1's career points and modes played.
        - `index_scores_player_mode_totals` and `index_scores_player_history`: Statistics (below).
    - **Statistics is added up by SQLite.** `playerRows` reads, in one transaction, each player's games summed per mode (a
        `GROUP BY` over a covering index), their latest spelling, and only the outcomes of their decided games, unordered,
        for the win streaks; window functions, which could do the streaks too, need Android 11. That's three reads for
        every player together, never a read per player (`ScoreQueryPlanTest`). `playerRecords` then puts each player's
        outcomes newest first and walks the streaks, and `ScoreRepository` adds the modes up, all after the transaction and
        off the main thread, so a long history holds up no write. `PlayerStatisticsQueryTest` checks the result equals
        walking every game.
    - **Leaderboard order is total.** Rows equal on every criterion follow in `rowid` (recording) order, so `LIMIT/OFFSET`
        pages never repeat or skip a row.
    - **Multi-statement reads and writes are one transaction**: a game's rows (`insertGame`), a reset (`clearAll`), the
        achievements' figures (`leaderboardTotals`), Statistics' reads (`playerRows`) and the cloud merge (`mergeCloud`).
    - Every schema change needs a version bump and a migration in `MIGRATIONS` (`data/scores/Migrations.kt`), tested by
        `ScoreDatabaseMigrationTest` from the exported schema it migrates from. There is **deliberately no destructive
        fallback**: a missing migration must fail loudly, never wipe scores. A change to `playerNameKey` changes stored
        keys, so it needs a migration too.
    - **Failing loudly is a page, not a crash**: `DatabaseFailurePage` (see "Errors").
- **Preferences.** Five DataStore files (`PreferencesFile`), kept apart so a reset can clear one. Each is a
    `ResilientDataStore`: a write that fails (a full disk) is skipped and a read that fails is empty, never thrown into
    the coroutine that asked. A failed read is retried three times, after 1s, 3s and 5s, and a retry that reads the file
    gives it to the collector; after the third, it stops.
    - `settings`. Every key but the Animations level, the Orientation, and the two list filters is synced
        (`SettingsRepository.SYNCED_KEYS`) and stamped `synced_at:<key>` when it changes. A key no version reads any more is listed in
        `RETIRED_KEYS` and removed at launch (`forgetRetired`).
    - `achievements`: unlock times and counters, plus the cloud save's counter shares (this file's own share id and the
        others' counts) and the streak's stamp.
    - `in_progress_game`, stamped `saved_at` on every save and clear.
    - `online`: Play Games choices and the cloud save's bookkeeping (leaderboard reset marker, "Saved").
        No reset clears it.
    - `purchases`: `owns_all_styles`, what the store last said (`PURCHASES.md`). No reset clears it, and it isn't in
        the cloud save.
- **Cloud save** (signed in only): one gzipped JSON snapshot of all of the above but the Animations level, the
    Orientation, and the two list filters, merged, never picked. `PLAY_GAMES.md`, "Cloud save", is the source.
- **Progress files** (the github app only): the same snapshot, sealed, in a file the player keeps. "Distribution",
    below.
- **The in-progress game.** `InProgressGameRepository` holds at most one, saved as JSON (`GameStateJson`).
    - It's saved after every change and cleared when the game ends.
    - Each game has an id (`GameState.id`, from `GameEngine.newGame`), and `rollsMade` only goes up. The cloud save
        keeps the copy of a game with more rolls, whatever the stamps say, and never brings back a game finished on this
        device (`PLAY_GAMES.md`, "Cloud save"). A save stamp is never earlier than the last, even with the clock set back.
    - Writes are queued and conflated in a process-lifetime scope, so leaving a game can't lose its last move.
    - An undecodable save is treated as nothing to resume: a missing required field, an unknown mode, a mode's draw
        missing, or a game the board can't play (`checkPlayable`: the seats, the dice count, each die within the mode's
        `dieValues`, the hold slots, the rolls). A cloud save or progress file can bring in what this app never wrote.
    - Fields a modifier adds are left out when it's off.
    - What Undo goes back to is saved as a nested game (`undoSnapshot`), and the box an undo took back as
        `undoneCategory`. One that can't be read only loses the undo; the game still loads.
- **Style picks.** Five ids (dice, cup, mat, background, frame) in `settings`. An id nothing recognises draws the
    default.

## Errors

The rule: **an error may cost a game, never the app.** What can be recovered from is caught where it happens. What
can't is a page saying so, with the stack trace to report. The app never just closes.

- **Recovered where it happens**, so nothing reaches the pages:
    - storage: `ResilientDataStore` (see "Persistence"), and `orNullOnFailure` (`data/Failures.kt`, which rethrows a
        `CancellationException`) around writes and loads that can fail;
    - a cloud or progress-file game that can't be read or played (`GameStateJson.checkPlayable`) is never taken;
    - a failed cloud sync costs that sync (`DiceFiveApp`), and a progress file that won't open is reported on its card;
    - Hard's perfect-play table that can't be read leaves Hard estimating (`AppContainer`);
    - Leaderboard or Statistics data that can't be read leaves what's on view, or opens empty with nothing on view yet.
- **The scores database won't open** (a migration missing or failing): `AppContainer` opens it as it's built and keeps
    the stack trace in `databaseFailure`. `DiceFiveApp` then shows `DatabaseFailurePage` in place of every page: what
    happened, that nothing was deleted, to update, and the trace. The process stays up.
- **An error nothing caught**, on any thread (a coroutine's included): `CrashHandler` (`device/`), installed by
    `DiceFiveApplication`, opens `CrashActivity` with a `CrashReport` (`device/`). Then it logs the error and ends the
    app's process itself.
    - It doesn't hand the error to the platform's handler. That handler puts Android's "has stopped" dialog over the
        page (on some phones at the first crash) and reports to Play's crash reports, so those reports don't see these
        crashes: the GitHub issues and the log are their record.
    - An error with no page goes to the platform's handler, as before there was a page: one whose page couldn't be
        opened, and one while the app isn't on screen (`CrashHandler.isOnScreen`), where Android quietly refuses to open
        a page and shows no dialog either.
    - The report holds the build and flavour, the Android version, the device, the screen and font scale, the language,
        the thread, the last actions, and the stack trace, cut to 100,000 characters to fit an Intent. It's developer
        text, never translated.
    - **Last actions** are `Breadcrumbs` (`app/`): the last 12 pages opened (`DiceFiveApp`) and moves made (a game
        started or resumed, a roll, a hold or release, a score, an undo, and a CPU's roll, in `GameViewModel`), oldest
        first. They hold ids and numbers, never a name or anything typed.
    - Only a process's first error opens the page; another thread's waits for it, then ends with it. Installing again
      (each Robolectric test's Application) replaces the
        handler rather than stacking it. With no platform handler (a plain JVM), the error is printed.
    - `CrashActivity` runs in a process of its own (`:crash`, the manifest's), since the app's own is on its way down.
        `DiceFiveApplication` sets up nothing there except Compose resources' context. Their ContentProvider gives it
        only to the app's own process, so `CrashActivity.provideComposeResourcesContext` sets the library's field by
        name. `r8-rules.pro` keeps it, and `CrashActivityTest` fails if a library update moves it.
    - It shows `CrashPage`: a heading, what happened, what's kept, the buttons, and the report, collapsed.
        `CrashPage`, `DatabaseFailurePage`, and their shared layout (`FailurePage`, `FailureDetails`) are in `ui/common`.
        - It opens as a page from the menu does: under the page cover, which fades away once it has settled
            (`RevealedPage`). Android's own window animation is off (`FLAG_ACTIVITY_NO_ANIMATION`). The crash process
            doesn't read the settings, so it's handed the Animations level the app last drew with (`LastAnimationLevel`).
        - **Restart** (filled) and **Report on GitHub** (tonal) sit side by side, one above the other at a large font.
            Restart starts the app in a fresh process and ends the crash page's.
        - **Report on GitHub** opens the browser at a new issue on the repo, filled in with the report
            (`CrashReport.issueUrl`), titled `[Crash]` and the error's first line: a line for the player to say what they
            were doing, a table of the details, the last actions, and the trace. The player reads it and posts it
            themselves; the app sends nothing. The link stays within 7,500 characters, under GitHub's limit, by cutting the
            trace from its end and saying so. With no browser, nothing opens.
        - **Show Details** opens the collapsed section (Hide Details closes it): what the details are for, **Copy
            details** (tonal), then the report. Copy details puts the report on the clipboard, then reads "Copied", a
            polite live region; both labels are laid out, so the button keeps its width. The section opens downward
            under the page, never moving what's above it (`FailurePage`'s `below`): that part is centred as the page first
            opens and stays where it is. The section fills the room left down to the screen's foot and scrolls in it, the
            whole of it (`ScrollingColumn`, its bar in the page's margin); on a screen too short to leave 160dp it gets that
            much, and the page scrolls too. The database page's details are
            always shown.
- **A saved game that can't be resumed** (an upgrade across versions, say) goes to the crash page, then goes for good.
    - Continue offers any saved game. `loadToResume` throws `UnresumableGameException` for one it can't read.
    - `ResumeAttempt` marks what counts: Continue's load and the board's first frames (`DiceFiveNavHost`), and the menu's
        off-screen drawing of the saved game (`SavedGameWarmUp`), which would otherwise fail at every start. Any error nothing
        caught during one, on any thread, counts. One after it, such as on the next roll, is an ordinary crash.
    - `CrashHandler` then writes `SavedGameDiscardFile`, whether or not a page opens, and `CrashPage` says the game will be
        removed (`gameRemoved`).
    - `InProgressGameRepository` removes it at the app's next start, before anything reads the save, however the page was
        closed: Restart, Back, reported or not, or swiped away. No copy of it comes back from the cloud (`FINISHED_KEY`).
- **Not caught**: native crashes and ANRs.
- **Trying it**: a debug build's crash mode on Settings > About (see "Superuser modes"). The taps on the name are
    raw gestures, attached only in a debug build, so no other build has them and TalkBack is never handed a dead link.
    For a crash anywhere else, throw from a temporary line, and take it out again.
- **Tests**: `ResumeAttemptTest`, `InProgressGameRepositoryTest`, `CrashHandlerTest`, `CrashReportTest`, `CrashActivityTest`, `CrashPageTest`, `BreadcrumbsTest`,
    `GameBreadcrumbsTest`, `AboutDebugCrashesTest`, and `DatabaseFailureTest`.

## Leaderboard and Statistics

**Time played** is a line at the top of Statistics, on every page and with no players yet: `PlayTimeLine`, seconds
counting up for the first minute ("42s"), then minutes, hours and minutes ("12h 34m"), and from 24 hours days, hours,
and minutes, however many days ("Time played: 3d 4h 12m", spoken "3 days, 4 hours, and 12 minutes"). Its numbers are
gold in the brand font (Arabic's digits fall back to the system font, in gold). `LivePlayTimeLine` reads the total
again only when the line would next change, and only while resumed, and holds its state itself, so an update
recomposes only that line. `PlayTimeTracker` counts the app in front of the player, from `ON_RESUME` to `ON_PAUSE` on
the monotonic clock, and saves at each pause and every five minutes while resumed. It's the counter
`AchievementCounter.PLAY_SECONDS`, for the per-device `SUM` in the cloud save and progress files, but stays out of
`AchievementsState`, so it never moves achievement flows or a game's counter write, and out of `changeStamp`, so it
goes with the next save that has something else to send. It started at zero in the version that added it.

Both pages pick what they show from a drop-down at the top: a page for each game mode, Standard's first and the one
they open on, then **Combined**, every mode's games together, last (a divider after Standard and before Combined).

- **Leaderboard** (`ScoresScreen`). The drop-down (`ModePage`) picks a board. The pick is the
    `leaderboard_choice` preference (the mode's id, or `combined`), restored on every open and carried in the cloud save
    and the GitHub app's export; a mode no longer known opens Standard's. Details are in `UI_SCREENS.md`.
    - **Every board is the same `LeaderboardTable`**: score-descending, 1,000 a page, 1st-3rd in gold/silver/bronze, "="
        only for a true tie, paged on its own.
    - Every board has only games with no modifier on (`playedUnmodified`). A **mode's board** has its mode's, the same
        ones its online leaderboard takes. **Combined** has every mode's (Stud's and Third Wind's run far above
        Standard's), each name in its mode's colour (`UI.md`, "Mode colours").
    - Rows show rank, name, 5x count and score. A long press shows the date, and on Combined the mode; that is also the
        row's spoken state.
- **Statistics** (`StatisticsScreen`). One card per distinct human *name*; a rename starts a new player.
    - **Pages** (`ModePage`). The drop-down picks one; the pick is the `statistics_choice` preference (as the
        Leaderboard's). A mode's page has that mode's games alone: its own numbers, win streaks, best score and first game
        (`PlayerStatistics.forMode`), and a player with no game in it has no card there. Combined has every mode's games
        together, and every player.
    - Closed, a card is the name and the page's best score ("1,287pts", the unit at 0.75 of the number's size). Its
        header opens it.
    - **Open**: games, win rate and average (in points), then a won/lost/solo bar with its key, then the current and
        best win streak, 5x scored, dice rolled, total points, and first played (date and time), all of the page's games.
        On Combined, a table follows: a row per mode played, in mode order (games, win rate, solo games and best score, the name in the
        mode's colour). Its total points are the player's (by name); Professional Roller's are seat 1's.
    - **Open by default** when every card fits on screen opened (the list is laid out with every card open under the page cover, which
        builds only the cards on screen, and they fit if it has nothing to scroll; more than 12
        players skip the measure and don't fit). Otherwise all start closed but the top card of each column (the first
        one in portrait, the first two in landscape), which always open. Opening a card never closes another. A new page
        deals them out afresh.
    - **A closed card's open content is built ahead** while the page is still, one card on screen a frame, and kept
        built, unseen, silent and taking no room (`OpenAhead`, `BENCHMARKS.md`); opening only gives it its room.
    - **Sort** (`StatisticsSort`), **each page its own**: a button at the app bar's end opens a menu of Name, First
        played, Highest score, Highest average and Most games, each by the page's numbers. The one in use is marked with
        an arrow, up for ascending and down for descending; picking it reverses it, picking another starts it in its own
        direction (A to Z and oldest first for Name and First played, highest first for the rest). Ties go by name. A new
        order deals the cards out afresh: the top card of each column opens, the rest as at the start. Each page's sort is
        a preference (`<id>:asc` or `<id>:desc`, `StatisticsSortOrder`): `statistics_sort_<mode id>` for a mode's page,
        and `statistics_sort` for Combined's (the one sort there was before 1.5.0, when every card held every mode).
        They're loaded with the app container and carried in the cloud save and the GitHub app's export.
    - Above 1.3x font, the header's score goes under the name, the details put the label over the value, and each mode
        table's name takes its own line over its numbers. A mode's name, or a heading, shrinks to stay on one line; each
        stacked column's share of the row is its widest text.
    - **Win rate** is won / (won + lost), a whole percentage rounded half up. Solo games aren't in it; with only solo
        games it's shown as "-" and not spoken.
    - TalkBack: the drop-down is one ("Statistics, Standard"). A card's header is one stop (name, best score, its state
        and action), its numbers two, and each mode table row one, in full sentences. The headings and bars are silent.
    - Every recorded game with no modifier on counts (`playedUnmodified`, in both Statistics indices). A solo game's
        outcome is null: it isn't a win or a loss, and it doesn't break a streak.
    - Each card has a delete button (top end, open or closed) that dismisses it after a confirmation; the scores stay.
        Playing again under that name brings it back. It goes from every page, as the confirmation says.
- Settings' "Reset leaderboard" deletes every score, and with them the Statistics.

## Achievements

`model/Achievement.kt` (110), `game/AchievementEngine.kt` (pure), `data/achievements/`, `ui/achievements/`.

- **Ids.**
    - The enum constant is the English title in snake case; the stored `id` is that in lower case
        (`GETTING_COMFORTABLE`/`"getting_comfortable"`). `AchievementEngineTest` enforces it.
    - String keys follow the constant: `achievement_<constant>_title`.
- **Player 1 only.** Other humans are opponents like CPUs. Two exceptions:
    - The ledger (score-band achievements Tally to Historian: every score in a 50-point band) reads the whole device's
        Standard board, and is earned in Standard only. From 1.5.0; Tricolour scores counted before, so progress can drop
        on update, though an earned one stays earned.
    - Professional Roller (career points) sums player 1's rows (`ScoreEntry.isPrimaryPlayer`, set from the seat, never
        from a name).
- **Rules.**
    - A solo game counts for games played, feats and thresholds, but neither extends nor breaks a win streak.
    - A tie at the top is a win only for whoever the tie-break ranks first (`wonBy`); a true tie is a shared win.
    - Superuser mode doesn't disqualify a game.
    - Undo can't inflate a counter: running totals move only at game end, and only human rolls count dice. The one total
        that moves sooner is Dice Whisperer's (`SCORED_5X`): each 5x counts the moment it's scored, with its banner (or,
        on the tenth, the unlock), so one in a game then left still counts. `PlayerState.fiveOfAKindsCounted` keeps the
        game's count through an undo and a resume, so a 5x undone and scored again counts once, and one undone and never
        scored again stays counted.
    - New Personal Best is per name, and reads the best from before this game's rows are written.
    - Full Set's counter (`FIVE_X_NUMBERS`) and Full Houses' (`FULL_HOUSES`, one bit per hand in
        `Achievement.FULL_HOUSES_HANDS`) are sets of flags; their progress is the bits set (`AchievementCounter.progressOf`).
        A flag is set the moment its box is scored, with its banner. The game keeps what it has collected on the player
        (`PlayerState.fiveOfAKindNumbers`, `fullHouseHands`), so a resumed game still has it. A flag stays set if its
        score is then undone.
    - Full Houses takes all 30 Full Houses. Until 2026-11-03 it took two (1-1-1-2-2 and 5-5-6-6-6), and those keep bits
        0 and 1; the rest follow, never reordered. The Achievements screen lists them by `FULL_HOUSES_LISTED`, five to a
        line (one number's three). Anyone who earned it with the two keeps it earned, with no list or progress shown.
    - Every Collection achievement but Completionist (Full Houses, Full Set, Grand Tour, and the ledger) has a progress
        banner for each new entry (`progressStepSize = 1`; `AchievementEngineTest`).
    - The ledger, career points and Grand Tour (player 1's modes played) are derived from the scores table, so they
        always agree with the Leaderboard and re-earn after a reset. Career points and Grand Tour read only unmodified
        rows.
    - **No modifiers.** A game with any modifier on earns only `earnableWithModifiers` achievements, moves no counter, and
        adds nothing to the scores-table measures. The engine filters its own results (`earnableIn(GameState)`), and
        `GameViewModel.unlockAchievements` filters the tracker's.
- **When they're judged.** The engine decides, never a call site.
    - `evaluateAtGameStart`: style and mode picks (Fresh Coat Of Paint).
    - `evaluateInProgress`, after each scored box: only what a later turn can't take away, and the counters whose
        `AchievementCounter.timing` is `AS_SCORED` (the collection flags and the 5x total). Score thresholds wait for game
        end. Making a counter `AS_SCORED` also needs a way to stop an undo inflating it and game end counting it again.
    - `unlockNow` and the pre-commit checks: moments a finished card can't show, such as a hand straight out of the cup
        or the dice and chosen box together (Wasted Fortune, Size Isn't Everything, Almost Famous, The Dice Hate Me,
        Commitment Issues).
    - `evaluate` at game end: everything else, and every counter (of the 5x total, only what the last turn scored).
    - Every unlock goes through the app's one `AchievementUnlocker` (`app/`, `AppContainer.achievementUnlocker`), whose
        locks (its own and the store's `locked`) serialise them all across screens, so nothing is announced twice.
- **Modes.** Each mode's own achievements are in the Game Modes category. Achievements a mode would make trivial are
    guarded (`GAME_MODES_AUDIT.md`).
- **Display.**
    - Declaration order is display order: grouped by `AchievementCategory`, easiest first, so ladders sit together.
        `AchievementEngineTest` fails if a category is split. Winning, Misfortune, and Miscellaneous are alphabetical
        by English title instead (`AchievementTextTest`).
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
    - On the Styles page, tapping the unlocked count `PRETEND_TAP_TARGET` times turns "pretend all styles are owned" on
        or off (`AppContainer.pretendOwnsAllStyles`), with a toast: for the process, never written. Turning it on blocks
        online mirroring, as superuser mode does.
    - On Settings > About, tapping the author's name (`zodac`) `DebugCrashes.TAP_TARGET` (5) times turns on the crash
        mode, with a toast, for as long as the page is open. Each link then crashes the app instead of opening, each its
        own way (`DebugCrashes.Kind`): the GitHub link on the main thread, the inspiration link in a coroutine off it,
        and the privacy link while the page is drawn. See "Errors".
- **Untested automatically.** Let's Finish This (leave and resume), and the end-to-end settings read behind the style
    achievements.

## Table art (Styles)

There are six independently saved categories (`ui/style/`), bundled as `GameVisualTheme`:

- dice (`DiceStyle`)
- cups (`DiceCupStyle`)
- mats (`DiceMat`)
- backgrounds (`TableBackground`)
- frames (`ScoreFrame`, around the active player's tab)
- boxes (`ScoreBoxStyle`, the scorecard's boxes and its Totals and Undo buttons)

**Frames** are drawn in the active player's own colour behind their tab (`ScoreFrames.kt`, in dp), and stay 1.5dp off the
text (`ScoreFrameClearanceTest`). One made of many parts (the wreaths, Starry, Nautical, Marquee) is `painted`: drawn once
per colour and tab size, then stamped, as the tab redraws every frame while a score counts up. The wreath goes all the
way round the tab, thinning its inward leaves beside the name, and `FrameCoverageTest` holds those frames to filling the
band along every edge. Unlocks run from Floral at 10 to Marquee at 100, with Nautical at 15 as part of the water set and
Starry at 45, which opens by count and not with the Starry dice, mat and background (Rolling The Midnight Dice).

**Boxes** (`ScoreBoxes.kt`) colour the scorecard: an open box, D20's two halves (`BoxTone`), and a scored box, each a
`BoxPaint` (fill, rim, and the ink its glyph takes), with an optional mark font, shape, and texture. What a box *means*
stays the same in every style: the gold of a good pick, the last scored box's outline, a disabled box's dashed outline,
and the dark discs of the run and 5x badges. A lit glyph on a light style (Paper, White Marble) takes a darker gold
(`goldOn`). Totals and Undo are drawn as open boxes (`boxSurface`). `ScoreBoxesTest` holds every style to readable
marks (3:1 on both ends of each fill), halves that differ, and a scored box unlike an open one. Families named like a dice
style open with it (Wood 10, Gems and Stone 65, Neon and RGB 100); Paper 5, Chalkboard 20, Pixel 75. RGB's strip is lit on
the RGB dice's clock, D20's second half half a turn of the hues from the first, and goes out once scored. Neon's glow
pulses on the Neon dice's beat, each box on its own, its core steady. Both move only at High, like their dice
(`MovingBoxArt`; `BoxStylesMotionTest`).

Each category is two lists in the same order:

- **The registry** (`game/style/StyleRegistries.kt`, pure): `StyleGroup`s (a shape or pattern, one tile on the Styles
    screen) of `StyleVariant`s, with every id, name and lock. Anything that decides what a player may use (the unlocker,
    the resets, achievements) reads only this, through `StyleCategory.registry`.
- **The art** (`ui/style/StyleCatalog.kt`): each variant's `StyleArt` (swatch and drawing). `StyleCatalog` joins it to
    the registry by id into the Styles screen's `StyleFamily`s of `StyleColour`s, and refuses a mismatch at load.

- **Ids** are the family's string key, then the colour's (`classic_gold`, `bestagons_honeycomb`). `StyleRegistryTest`
    enforces this.
    - **One exception: a style folded into another family keeps the id it was released with.** The Beaker cups are
        colours of Glass (`Beaker Blue`, `Beaker Green`) but are still `beaker_blue` and `beaker_green`, because ids are
        permanent. They're listed in the test.
- **A style with a counterpart of the same name is unlocked by the same thing** in every category that has one: Stone
    (dice, mat, background) opens at 65 achievements, Circuit at 80, Neon at 100, Meadow at 75. `StyleRegistryTest` checks
    every family name across dice, cups, mats and backgrounds.
- **The water set opens together, at 15 achievements** (`WATER_SET_UNLOCK`): the Ocean dice, mat and background, the
    Fishbowl cup and the Nautical frame. No count lock is above 100, and the 50 and 100 locks are fixed points.
    - The Ocean background's Waves roll (`TwinkleClock`); its Lagoon is painted once. The dice, mat and Lagoon share
        `drawWaveCrests` and `paintCaustics` (`OceanArt.kt`).
    - The Fishbowl's clownfish swims a rail (`clownfishOnRail`) as a turning solid, its face always swinging towards the
        viewer. Its three rocks roll with the water's lagging tilt, and its fronds lean back against the bowl's tilt and
        swing with the slosh.
- **The Classic family** comes first in every category and is free. Its first colour is the default: ivory dice, the
    gold casino shaker (the launcher icon's cup), the blue mat with the icon's gold ring, the icon's lit navy felt, a
    plain ring.
- **Locks.** Every other family is locked behind an achievement count (`StyleUnlock.AchievementCount`) or a specific
    achievement (`SpecificAchievement`, `AnyAchievement`).
    - A colour can also be secret (`StyleVariant.secretAchievement`): not offered at all until earned.
    - Every lock takes a `StyleAccess`: the achievements, and `ownsAllStyles`, the "Unlock all styles" purchase
        (`PURCHASES.md`), which meets every lock and secret colour.
    - A locked saved pick draws the default but stays saved. Resetting achievements resets the picks it locks; an
        owner's are kept.
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
        every turn, in any mode, with no roll modifier. Green Fingers is earned only with that growing pot picked as the cup
        (not the fixed-stage pots it unlocks).
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
    - The sources are `app/audio/*.ogg`.
    - They're peak-normalised to -0.8 dBFS by ffmpeg at build time (`scripts/normalize-audio.sh`): never `res/raw/`,
        never hand-adjusted. Android packages Ogg copies (`NormalizeOggAudioTask`). `scripts/tests/normalize-audio-test.sh`
        checks they keep each clip's length (the shake window is timed to them) and peak.
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
    for Sound effects, Vibration, Confirm leaving game, and Tricolour shapes; the Animations level; the Orientation; and two resets behind
    confirmations (achievements, leaderboard).
- **Tricolour shapes** (`tricolour_marks`, synced, off by default): a triangle, circle, or square for red, yellow, or
    blue, on a Tricolour die's corner (`DieColourMark`), in the game and the Rules page's examples, and in its
    colour's box (`BoxColourMark`), for telling the dice apart without their colours. Not while a die is tossed, and
    not spoken, as a die and a box already say their colour. Tests: `ColourMarksTest`, `RulesScreenAccessibilityTest`.
- **Orientation** (`ScreenOrientation`): Device (the default: turns as the system allows), Portrait or Landscape. Not
    synced, as a phone and a tablet can want different ones. `DiceFiveApp` hands it to `PlatformServices.lockOrientation`;
    on Android `MainActivity` sets `requestedOrientation` (sensor portrait or landscape, so either way up). The manifest's
    `android:appCategory="game"` is what lets the lock hold on a large screen: Android 16+ ignores it there otherwise.
    Tests: `ScreenOrientationSettingTest`, `OrientationLockTest`, `SettingsRowsTest`.
- **Foot of the page.** The version pill is the last thing on the page (not pinned), after About and **Licences**: every
  library, font and sound, with its licence
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

`ui/rules/RulesContent.kt` (the pages: `RULES_GROUPS` and its block types) and `ui/rules/RulesScreen.kt` (drawing them).

- **Layout.** Two tab rows: groups (**Gameplay**, **Modes**, **Modifiers**), then the pages of the chosen group. One
    pager runs across every page.
- **Accuracy.** The text is kept in step with the engine: `ScoreCategory` values, the joker rule, the tie-break order.
- **New mode or modifier.** It gets its own page; see `GAME_MODES.md`.

## Distribution

Two apps from one source, one per way the app reaches players: product flavours of `app/android` (dimension
`distribution`), each with its own source set (`src/play`, `src/github`) and one seam, `Distribution`, which
`MainActivity` and `DiceFiveApplication` call.

| | `play` | `github` |
| --- | --- | --- |
| Ships as | The App Bundle, uploaded to Google Play on demand (`play-release.yml`) | The APK on each GitHub release |
| `applicationId` | `net.zodac.dicefive` | `net.zodac.dicefive.github` |
| Google Play Games | Yes (`PLAY_GAMES.md`): the SDK, its manifest entries, Sidekick | None: no SDK, no manifest entries, `NoGameServices` |
| Progress files | None, signed in or not | Export and Import, in Settings |

- **Kept apart so nothing from a file reaches Play Games.** A file's progress can only be imported into the github
    app, which has no Play Games code, and the github app's data can't reach the play app's: they're separate packages
    (data, backups and installs are per package), signed by different keys (Play re-signs the bundle). Nor can an
    edited `adb` backup: up to Android 11 the play app's backup holds only the settings (`PLAY_GAMES.md`, "Account and
    sign-in"). Pinned by `GithubFlavourTest` and `PlayFlavourTest`.
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
- **Settings card** (`ProgressFilesCard`): Export progress and Import progress, each a whole-row button (a compact
    `SettingRow`, its label alone), where the online card would be. Import asks first ("Import progress?"). Under the
    rows, while an import runs, its step and count over a bar, drawn by the same `StepProgress` as the online card's
    sync ("Opening the file", then the same four merge steps, `MergeStep`, named the same: `MergeStepTest` keeps the two
    flows in step), for at least 1s (`IMPORT_SHOWN_MILLIS`, shared out between the steps, so a quick import's bar still
    visibly runs: the card catches up with the import a step at a time, `nextShownStatus`, counting only while the app
    is in front); then how the last export or import ended, in red if it failed, until the next starts. No toasts. The
    line is a polite live region: TalkBack hears "Importing progress" (not each step), then the outcome.

## Release pipeline

- **Version.** `VERSION` at the repo root, the one app's single source: `versionName` is its contents, and
    `versionCode` is `major*1000000 + minor*1000 + patch` (`AppVersion`). Up to 1.2.1 it was `major*10000 + minor*100 +
      patch`; every new code is above every old one, so updates install over them.
    - `scripts/set-version.sh X.Y.Z` writes it, and `scripts/bump-patch-version.sh` bumps its patch. The `android-v` tag
        prefix leaves room for another app.
    - Debug builds are signed with the repo's `app/android/debug.keystore`, so every debug APK shares one SHA-1 (which
        has no Play Games credential, as the key is public: `PLAY_GAMES.md`, "Play Console"). Built with `-PdevStamp`,
        one also takes the minute it was built as its `versionCode` (`<version>-dev<minute>`), so each installs over the
        last. Without it, a debug build keeps `VERSION`'s, so test
        and lint runs stay up to date. The minute is read through a `ValueSource` when the build runs, never while the
        script is evaluated, or the configuration cache would hand every build the same one.
- **On every push to `main` that changes what Android ships**, `.github/workflows/android-release.yml` runs:
    - a `version` job that picks what's built: `main` as it is when the run starts, never the pushed commit. Runs queue
        one at a time, so a push made during a run builds after that run's bump, not the version just released. It fails
        when `android-v<VERSION>` already exists (a release's files are never replaced), and releases nothing when the
        only commits since the last release are the bot's own (every other job is then skipped);
    - parallel jobs, each on that commit: Android tests (a runner per flavour), shared tests, lint, the upgrade check
        (a game on the last release's APK must resume on this one; `EMULATOR.md`, "The upgrade check"), the Baseline Profile
        (generated fresh on an emulator from the play app, and given to both; see `EMULATOR.md`), and the github APK plus
        the play App Bundle;
    - then a `release` job that publishes GitHub release `android-v<version>` once every check has passed, with
        `DiceFive-<version>.apk` (github), `.aab` (play), `-mapping.txt` (the APK's) and `-play-mapping.txt` (the bundle's),
        tagging the commit built;
    - then a commit that bumps the patch and empties `RELEASE_NOTES.md` (`[Release] Bump Android version to X
        [skip ci]`). A `VERSION` set by hand on `main` during the run is left as it is (`[Release] Clear the release notes
        after X [skip ci]`).
- **To Google Play, on demand**, `.github/workflows/play-release.yml` (run by hand: a release tag, blank for the newest,
    and a track) uploads a GitHub release's `.aab` and its play mapping to that track as a completed release. Nothing is
    built.
    - **`publish`** (boolean, default on) decides whether the update is sent for review. Off, it is only prepared: the
        upload lands in Play Console with `changesNotSentForReview`, to be sent from Publishing overview.
    - **What's new** (en-GB): the GitHub release's URL, a blank line, then as many of its bullets as fit whole in Play's 500
        characters, without their commit links, then "..." for the rest (`scripts/play-release-notes.sh`, tested by
        `scripts/tests/play-release-notes-test.sh`).
    - The commit sends the edit for review with Play's default, which "send[s] all the changes for publishing". Whether
        that includes listing changes saved in Play Console isn't confirmed yet. Play Games achievements and boards are
        published in Play Console: the API can't.
    - Signs in with the `PLAY_SERVICE_ACCOUNT_JSON` secret, a service account invited in Play Console with release
        rights. Play never takes a `versionCode` twice, so each release goes to Play once.
- **Beside it, not part of it**, `readme-assets.yml` renders the README's feature graphic and screenshots from the
    same push and replaces the files on the `readme-assets` release (`STORE_ASSETS.md`, "The README's images").
- **Release description.** `RELEASE_NOTES.md` (the maintainer's), then "## Changes since X" from
    `scripts/release-changelog.sh`, grouped by each commit's `[Category]`.
    - The notes are the whole file, as written; it's kept blank between releases. A release takes it all, then empties
        it (`scripts/release-notes.sh`, tested by `scripts/tests/release-notes-test.sh`).
    - The changelog lists the commits since the previous `android-v` tag.
- **Signing.** Four `ANDROID_RELEASE_*` env vars (repo secrets in CI); without them the build is unsigned. The keystore
    (alias `upload`) is the Play upload key, and signs both the bundle and the github APK.
- **`PROGRESS_FILE_KEY`** (a repo secret): the github APK's progress file key ("Distribution"). The apk job fails
    without it.
- **`PLAY_LICENCE_KEY`** (a repository variable, not a secret: it's a public key): the play app's licence key, which
    purchase signatures are checked with (`PURCHASES.md`, "Entitlement"). The apk job fails without it, or on one that
    isn't one-line Base64.
- **R8** shrinks and obfuscates release builds (`app/android/r8-rules.pro`). Release dex is stored uncompressed.
    - It lays the dex out from the startup profile (`dexLayoutOptimization`): the code a cold start runs to the menu
        (`StartupProfileGenerator`) goes first. The release workflow fails if it generates no `startup-prof.txt`.
- **Dependencies.** `.github/workflows/update-dependencies.yml` bumps every pinned version monthly, re-runs the gates,
    commits `[Dependencies] Update dependency versions` and starts a release. It never moves targetSdk. The update and
    its gates run read-only, with no credentials in the checkout; a separate job that runs no build applies their patch,
    pushes and starts the release.
- **The Gradle wrapper** pins `distributionSha256Sum`, which the update script moves with the version.
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
