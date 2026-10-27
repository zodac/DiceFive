# Game modes

How modes are built and how to add one, including a mode unlike any so far. A mode is picked on New Game and carried
on `GameState` and each `PlayerState`. The achievement audit (what each mode blocks or guards) is in
`GAME_MODES_AUDIT.md`.

| Mode         | `id`         | Different from Standard                                                                                                                                                                                                                                                                                                          |
|--------------|--------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `STANDARD`   | `standard`   | The official rules. The default.                                                                                                                                                                                                                                                                                                 |
| `TRICOLOUR`  | `tricolour`  | Dice also roll red/yellow/blue; four colour boxes join the card                                                                                                                                                                                                                                                                  |
| `STUD`       | `stud`       | Shown as "7 Dice Stud". Seven dice rolled, five hold slots; only the held five score, but a box can be tapped with fewer held and `HandCompletion` completes the hand. Off the Leaderboard                                                                                                                                       |
| `THIRD_WIND` | `third_wind` | Every box scored three times (39 turns); one upper bonus, 189 earns 105; joker once the 5x box's 3 slots are used. Off                                                                                                                                                                                                           |
| `D20`        | `d20`        | Twenty-sided dice (`dieValues = 1..20`), 5 dice, 5 rolls. Boxes `D20_1`..`D20_20` (each adds the dice showing it) and two wildcards (`D20_WILD_LOW`/`HIGH`: every die in that half, added up, counting toward its half). No lower section, no 5x. Each half earns 75 (at 138 and 388; `sectionBonuses`). No Extended Scores. Off |
| `HIT_LIST`   | `hit_list`   | No Standard boxes: 12 targets drawn each game (numbers by place, up to 2 "any") plus the Alibi. Exact order doubles; partial hits (2+ numbers rolled) score half the share rolled. No Extended Scores. Off                                                                                                                       |
| `QUICKFIRE`  | `quickfire`  | 5x plus three random upper and three random lower boxes switched off each game: six turns, a scaled upper bonus. Off                                                                                                                                                                                                             |

## The one rule: a mode is data

**Everything that can differ between modes is a field on `model/GameMode.kt`**, even where every mode agrees. The
engine, AI, achievements, persistence and board read the rules from the mode. A new mode is:

1. a `GameMode` entry;
2. plus, only where the rule is genuinely new: a field (whose default leaves other modes unchanged), a
   `ScoreCategory`, or a `Die` property;
3. plus the player-facing content in the checklist below.

**Never write `if (mode == QUICKFIRE)`** in engine, view-model or UI code. Add a field that says what the rule *is*,
and move any hard-coded `3` rolls or `5` dice onto the mode.

The only checks for a named mode are:
- achievements that name one;
- Tricolour's `IrishEasterEgg`;
- `TieBreak`'s Tricolour-only criterion.

**`id` is a storage key.** Never change it once released; rename `displayName` instead.

### Every field, and what reads it

When a new rule adds a field, add it here.

| Field                                                                                                                                                   | Read by                                                                                                                                                                                                                                                                                                                                       |
|---------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `diceCount`, `dieValues`, `dieColours`                                                                                                                  | `GameEngine.newGame`/`rollDice`/`cycleDieValue`, `DiceTray` (incl. the rolling scramble and `recoloured`), `AiTurnPlayer`                                                                                                                                                                                                                     |
| `scoringDiceCount` (`scoresHeldDiceOnly`)                                                                                                               | `GameState.scoringDice`/`hasFullHand` (preview, `commitScore`); `GameEngine.toggleHold`/`canHold`/`fillHand` (`Die.heldSlot`); `DiceTray` (`SlottedDice`); `AiTurnPlayer` (`RollSpace`, `chooseHand`); the timeout; achievements on a roll (guarded) or a hand (`scoringDice`); A Cunning Strategy's "all five"                               |
| `rollsPerTurn` (the game's own: `GameState.rollsPerTurn`, the Number of Rolls modifier else this)                                                       | `GameEngine` turn reset and the `GameState` default `rollsRemaining` (the cup's `xN`, the AI loop); `AchievementTracker`'s `fullRolls`/`rollsRemainingAfter*`; Impatient/Naturally Gifted's guard                                                                                                                                             |
| `categories` (a game's card: `categoriesWith(extendedScores)`, read as `PlayerState.categories`)                                                        | `PlayerState` (card, totals, completeness), `ScoreCalculator`, `ScoreGrid`, `GameStateJson`, `AiTurnPlayer` baselines, How Do You Play This Game?                                                                                                                                                                                             |
| `upperBonus*`, `fiveOfAKindBonusAmount`                                                                                                                 | `PlayerState` totals, `ScoreCalculator`, the 5x tile's bonus preview                                                                                                                                                                                                                                                                          |
| `disabledCategories`, `randomUpper/LowerDisabledCategories` (a game's set: `GameState`/`PlayerState.disabledCategories`, drawn in `GameEngine.newGame`) | `PlayerState.isOpen` (so `ScoreCalculator`, `HandScoring.filledMask`, the AI skip them), `turnsPerGame`, `upperBonusThreshold`/`hasUpperBonus`, `GameStateJson`, `CategoryCell`/`CategoryTile`, guards (`hasFullCard`)                                                                                                                        |
| `onlineLeaderboardKey`                                                                                                                                  | `online/GameServices`; `OnlineLeaderboardKeyTest` pins every key, so a new mode needs one (`PLAY_GAMES.md`, "Leaderboards")                                                                                                                                                                                                                   |
| `maxPossibleScore`                                                                                                                                      | `HIGHEST_POSSIBLE_SCORE` (column width), `GameModeTest`                                                                                                                                                                                                                                                                                       |
| `scoresPerCategory` (`turnsPerGame`)                                                                                                                    | `PlayerState` (`scorecard` is a list per box; `isOpen`, `turnsTaken`/`turnsLeft`, `isScorecardComplete`, totals, `fiveOfAKindJokerActive`), `ScoreCalculator`, `HandScoring.filledMask`, `AiTurnPlayer` (bonus reach), `TieBreak`, `GameStateJson`, `CategoryCell` (`StackedScores`), `GameViewModel` (timer turn count, final round), guards |
| `countsOnLeaderboard` (off too whenever a modifier is on: `GameState.countsOnLeaderboard`)                                                              | `ScoreEntry.onLeaderboard`, derived from the row's mode and `playedUnmodified`, which `ScoreDao`'s board page/count, best score and distinct scores filter on (Statistics and career points don't); New Personal Best's guard                                                                                                                                              |
| `maxRollsPerGame`                                                                                                                                       | `flowerpotGrowthStage` (stages spread over it; blooms on its last roll); `GameModeTest`                                                                                                                                                                                                                                                       |
| `hitListShapes` (`hasHitList`; a game's targets: `GameState`/`PlayerState.hitList`, drawn in `newGame`)                                                 | `ScoreCalculator.scoreFor` (`DiceScoring` refuses them), `GameStateJson`, `AiTurnPlayer` (hands the turn to `HitListPlay`; `prepareHard` does nothing), `CategoryCell`/`CategoryIcon`, guards and Right On Target                                                                                                                             |
| `sectionBonuses` (`SectionBonus`)                                                                                                                       | `PlayerState.sectionBonusTotal`/`hasSectionBonus`, Hard's bonus stake, Critical Success, the Totals Bonus line                                                                                                                                                                                                                                |
| `allowsExtendedScores`                                                                                                                                  | `categoriesWith`, `GameEngine.newGame`, `GameViewModel.startGame`, the setup screen's locked row                                                                                                                                                                                                                                              |
| `hasPerfectPlayTable` (`playsByPerfectPlayTable(extendedScores)`)                                                                                       | `AiTurnPlayer` (Hard's table), `GameViewModel` (reading the table ahead, `perfectPlayFor`)                                                                                                                                                                                                                                                    |

## What kind of mode is it?

A mode can touch several of these. Then audit the existing achievements (`GAME_MODES_AUDIT.md`); every mode so far
has changed something there.

- **Only numbers that are already fields**: the entry, Rules page, achievements and tests.
- **New boxes**: "New categories".
- **New dice** (a property, faces or count): "New dice".
- **More dice rolled than score**: "More dice than score".
- **A box scored more than once**: "More than one score a box".
- **A card of drawn targets**: "A card of targets".
- **Boxes switched off**: "Boxes switched off".
- **Off the Leaderboard**: "Off the Leaderboard".
- **A changed turn flow**: "Turn flow".
- **Overriding another setup option**: "New Game screen".

## Checklist

### The mode itself

- Copy `STANDARD` and set every field explicitly.
- `description` is the one line on New Game. Say what's *different*.
- `maxRollsPerGame`: boxes x rolls per turn, with the derivation on the entry. `GameModeTest` plays every roll.
  - The Flowerpot blooms only in 3-roll modes (`growsSunflower`), so any other mode can't earn Greenfingers.
- `maxPossibleScore`, with the derivation as a doc comment. `GameModeTest` plays a perfect game (every turn a 5x)
  through the engine.
  - A rule that changes what "perfect" means (no 5x possible, say) may need `perfectGame` taught about it.
- Doc comments state the rule. **Don't write a guessed rationale**; it reads as fact later.
- **Its colour** in `GameMode.color` (`ui/theme/Color.kt`), added to the table in `UI.md` ("Mode colours").
- **Its online leaderboard** (`PLAY_GAMES.md`, "Leaderboards"): a new `onlineLeaderboardKey`, an icon in
  `PlayConsoleLeaderboards.kt`, a board made by hand in Play Console from `scripts/export-play-console.sh`'s
  `leaderboards.csv`, and its id in `playLeaderboardIds` (`PlayGamesIdsTest` fails without it).

### Rules page (`ui/rules/RulesScreen.kt`)

- **Placement.** Add a `RulesPage` (title the mode's name; `tabLabel` the same or shorter) at the end of the Modes group
  in `RULES_GROUPS`. A Leaderboard mode also joins the overview list (`rules_modes_3`).
- **Content.** Describe it relative to Standard and say what stays the same.
- **Block types, never markup.** Use `text(...)` for a paragraph, `RulesStep` for an ordered list, and `RulesCategory`
  (name, what it takes, an example `dice(...)` row whose score carries the points) per new box.
- **Text conventions** (per `RulesPage`'s doc comment):
  - `` `backticks` `` for a box name;
  - `*italic*` for a section, mode or setting;
  - `**bold**` only for points or counts;
  - no dashes as bullets or asides;
  - no dice written as text.
- **Every language.** Update the page in every language whenever the rules change.

### New categories

A box's scoring is mode-independent; a mode only chooses which boxes it uses.

- **`model/ScoreCategory.kt`**: the entry, with its fields.
  - `section`: a new `ScoreSection` if it shouldn't count toward upper or lower. Tricolour's `COLOUR` kept "Lower
    Class" meaning the same everywhere.
  - `fixedScore`.
  - `jokerFreeFill`: whether a repeat 5x fills it at full value.
  - `matchingColour`.
- **`game/DiceScoring.score`.** An exhaustive `when`, so it won't compile until the rule is there.
- **Totals.** A new section needs its own total in `PlayerState.totalScore`.
- **Board.**
  - `CategoryIcon` (exhaustive).
  - Special art in `CategoryTile`.
  - The spoken name in `BoardSemantics`.
  - Past six rows, `ScoreGrid` goes compact. Read `UI_BOARD.md` before adding rows.
- **AI.**
  - Easy needs nothing. Hard needs nothing beyond scoring (`HandScoring` scores every hand through `DiceScoring`).
  - **A change to the joker rule, or a new free-fill box, also goes in `HandScoring.forEachLegal`.** `HandScoringTest`
    compares the two in every mode.
  - Medium needs to know how to chase the box, and `CATEGORY_RESTRICTIVENESS` needs it ranked.
- **Standard's perfect-play table.** Anything changing how Standard scores (boxes, scoring, bonuses, rolls) means
  regenerating it (`test` skill), or `StandardPerfectPlayTableTest` fails. No other mode has a table.
- **Tie-breaks.** A mode-only stat goes in the live game's criteria, not the Leaderboard's, which mixes modes.
- **Rules page.** List each new box and its value.

### New dice

- **Saves.** A new `Die` property must be written and read by `GameStateJson`. Read its absence (an older save) as the
  old behaviour.
- **Seeded draws.** `GameEngine.rollDice` draws the property only in modes that use it, so other modes' seeded dice are
  unchanged; tests depend on this.
- **Faces.** `DiceTray` (including the rolling scramble) and superuser `cycleDieValue` must know the new faces.
- **Same-roll checks.** Déjà Vu compares whole faces (`faces()`). Check that's what "the same roll" should mean.
- **Hard's cost.** Hard's `DiceSpace` grows with the faces.
  - Tricolour's 18 faces make 26,334 hands and 33,649 held sets: ~10 MB, built in under a second in the background
    (`prepareHardCpus`), then ~2ms a decision.
  - Check a new face count's build time, memory and decision time.

### More dice than score (Stud)

"The dice" and "the hand" are different lists.

- **Score `GameState.scoringDice`, never `state.dice`.** That covers the preview, `commitScore`, the AI's choice, the
  timeout, and achievements judged on the hand.
  - `state.dice` is still right for what's *on the table*: `lastRoll`, Déjà Vu, Lucky Seven, die indices.
  - Grep each `state.dice` read and decide which it means.
- **Part-held hands** are scored and previewed.
  - `DiceScoring.isFiveOfAKind` checks the count, or three held 6s would read as a 5x.
  - `commitScore` throws without a full hand (`hasFullHand`). The view model completes the hand first
    (`HandCompletion`).
- **Holding is capped** (`canHold`). Bulk changes release before they hold (the AI's `applyHolds`).
- **The AI.**
  - "Every die held" doesn't mean stop.
  - `RollSpace` maps each roll to its best five, and `DiceSpace(maxHeld)` caps the held sets.
  - After the last roll the CPU must visibly pick and hold its hand (`chooseHand`), with a reaction delay.
- **Completing the hand.** The box tapped says what the player means.
  - `HandCompletion` tries every completion of the held dice and takes the best for that box (score plus any 5x bonus;
    on a tie, the leftmost dice).
  - The board previews each box completed (`LocalProjectedHands`, human turns only).
  - The view model holds and scores in one tap; a glide-then-score step read as lag.
  - Undo returns to the dice as held.
  - A timeout scores the first box any completion can score.
- **Tray.** `SlottedDice` (`UI_BOARD.md`). Render it at 411dp and 360dp for review before any APK.

### More than one score a box (Third Wind)

`PlayerState.scorecard` is a list of scores per box in every mode (empty while untouched).

- **Never read `scorecard[c]`.** Use `scoresIn(c)`, `isOpen(c)`, `hasScore(c, n)`, `allScores`, `turnsTaken`,
  `turnsLeft` and `isScorecardComplete`.
  - **Trap**: `scorecard[c] != null` still compiles and is always true, so a test using it can never fail.
- **A box isn't a turn.** Use `turnsPerGame`. This affects the timer's new-turn detection, the final round
  (`turnsLeft == 1`), and test loops over `categories.size`.
- **Rules about "the box" are decided per slot.** The joker is "5x full and one slot holds 50", so the 2nd and 3rd 5x
  score 50 with no chip. `HandScoring.filledMask` means "no slot left". `HandScoringTest` builds part-used boxes.
- **Totals scale, and so does everything measured on them**: the ceiling, the bonus fields, Hard's bonus reach (per
  open slot), and the score-threshold achievements (guarded).
- **The AI** needs no new code. Hard estimates (a table would need ~4^13 states).
  - Measure each difficulty against its Standard per-turn average before tuning.
  - Two plausible tweaks made Hard worse.
- **The board** uses `StackedScores` (`UI_BOARD.md`).

### A card of targets (Hit List)

The boxes (`ScoreSection.HIT_LIST`: `TARGET_1..12`, `ALIBI`) have no fixed rule: each target is drawn per game
(`HitTarget`, from the mode's `TargetShape`s).

- **Score through `ScoreCalculator.scoreFor`**, which has the player's `hitList`. `DiceScoring.score` throws for these
  boxes. Anything that scores a whole card up front (`HandScoring`, `categoryBaseline`) must never be built for it.
- **Order matters.** An exact hit needs each named number in its own column, so dice pass in column order
  (`scoringDice` is). A hand short of a die can hit but never be exact.
- **A partial hit** scores half the points times the share of the target's numbers rolled, rounded to the nearest 5
  with halves up (`HitTarget.partialPoints`).
  - It needs at least two numbers rolled (`MIN_PARTIAL_ROLLED`).
  - It's always less than a hit.
  - It leaves few zeros, hence Spotless's guard.
  - A CPU must count the partial its held dice keep when a chase fails.
- **The Alibi** scores the best open target the dice hit, never doubled, and leaves that target open. Once every target
  is closed it can only score 0, so the CPU uses it before then.
- **Featured box.** The Alibi takes 5x's place; the targets fill the grid two to a row.
- **The AI** is `HitListPlay` at every difficulty: the exact, memoised odds of a hit (`hitOdds`) and an exact hit
  (`exactOdds`), plus the value of keeping a box open. `HitListPlayTest` expects Hard > Medium > Easy (~255/227/188).

### Boxes switched off (Quickfire)

A disabled box stays on the card (the layout is unchanged) but is never open.

- **Ask `PlayerState.isOpen`, never "has no score".** A new "open box" check that bypasses `isOpen` is the bug to look
  for.
- **The set belongs to the game.** It's drawn once in `GameEngine.newGame` (only when the mode has random boxes, so
  other seeded games are unchanged), copied to every player and saved. A save without it must fail to decode.
- **Game length.** Use `turnsPerGame`/`turnsLeft`, never `categories.size`. Test loops skip the off boxes.
- **Scaling.** The bonus threshold (`upperBonusThreshold`), `maxRollsPerGame` and the ceiling (the best draw) all scale
  to the boxes still on. A section with no boxes left has no bonus.
- **No 5x on the card means no joker**, and the roll-a-5x achievements can't fire.
- **Drawn** as an outline with a slash and "Off" (`UI_BOARD.md`).

### Unlucky Dice (a modifier that changes what a roll is)

Locked dice (`Die.isUnlucky`) can't be held and don't score, in every mode.

- **A hand can be smaller than `scoringDiceCount`.** Score `scoringDice` and ask `hasFullHand`.
- **Never hold a locked die.** `toggleHold`/`canHold`/`fillHand` refuse it, and anything that picks holds must too.
  `AiTurnPlayer.withoutUnluckyDice` plays Hard as Medium.
- **Achievements judged on a roll** need `dice.none { it.isUnlucky }`. Zero To Hero is guarded on
  `GameState.unluckyDice`.
- **Seeded draws.** It draws from the `Random` only when on, so other seeded games are unchanged.

### Off the Leaderboard

`countsOnLeaderboard = false` records `ScoreEntry.onLeaderboard = false`. The Combined table, a personal best and the
score bands skip those rows; the Game Mode view, Statistics and career points don't. A game with any modifier on is on
no card and no online board, whatever its mode (`ScoreEntry.playedUnmodified`), but Statistics and career points
still count it.

- **Fakes.** The fake DAOs in `ScoreRepositoryTest` and `GameAchievementsWiringTest` mirror the real filters, so **a
  new filter goes in both fakes**. `AndroidAppContainerTest` runs the real database.
- **Personal best.** Guard New Personal Best.
- **Migration.** Moving a shipped mode on or off the board needs a migration.

### Turn flow: rolls, timers, undo

- **Rolls per turn.** Read what a turn started with from `GameState.turnRolls` (Stored Rolls makes it bigger), and the
  game's rolls per turn from `GameState.rollsPerTurn`, never `gameMode.rollsPerTurn`. Totals achievements are guarded
  on `PlayerState.rollsModified`.
- **Every roll lands through `GameViewModel.performRoll`.**
  - **A human's roll** is always a cup tap (`GameScreen.onCupTap`; a finger or a shake): a `CUP_SHAKE_MILLIS` shake,
    sound and haptics, then `rollDice()`, where player 1's achievement tracking runs.
  - **A CPU's roll** comes from `GameViewModel.maybeStartAiTurn`: the same shake (`aiRolling`), then `performRoll`. It
    lives in the view model, so recomposition can't interrupt it, but it pauses when out of sight (`setForeground`
    from lifecycle callbacks).
  - **Any new wait in the CPU loop or the timer uses `pausableDelay`, never `delay`.** A plain one forfeits turns or
    plays CPUs out in the background (`GameViewModelTest` pins this).
- **Rules for changing the flow:**
  - **No second kind of roll.** Special rolls go through the same tap or `performRoll`; a separate roll with its own
    delay was rejected on review.
  - **Never change state under the AI loop.** It plays from its own copy (`current`) across delays.
  - **Screen-driven rules need the screen.** Something `GameScreen` does happens only while it's composed. A rule that
    must happen with no screen belongs in the view model.
  - **No roll while one is in hand.** A new automatic tap waits on `rollInHand`, keyed on it, so a turn that starts
    mid-settle rolls once the dice land.
  - **A roll can land after its turn has gone.** Undo stays enabled during the shake, so `rollDice` ignores a roll with
    none left (`GameEngine.rollDice` throws on one). Any delayed action re-checks the state it acts on.
  - **Undo** reaches only the last human action, and any CPU move clears it.
  - **The timer.** `syncTurnTimer` restarts it each turn. A timeout calls `autoScoreOnTimeout`, which scores a rolled
    turn in `timeoutCategory` (completing the hand in Stud) or forfeits an unrolled one (`forfeitTurn`). A timeout's
    score is final: it clears the undo.
  - **Holding after the last roll.** `GameScreen.canHold` ignores rolls left, so dice can still be held then. That keeps
    the hold achievements alive at 1 roll.

### New Game screen

The mode picker lists `GameMode.entries`, so a new mode appears automatically. Only a mode that overrides another
option needs work:

- **Lock the option; don't hide it.** Use `allowsExtendedScores = false` → `ModifierSetting.lockedNote`, or
  `SegmentedChoiceRow`'s `enabled`.
- **Keep and save the player's pick.**
- **Start with the neutral value.** `startGame` and `newGame` play without it, so a save doesn't carry a setting that's
  out of play.
- **Add no text or rows for a mode.** An added element must not move existing ones.

### Achievements for the mode

- **Placement.** Add them to the Game Modes block of `model/Achievement.kt`, after the previous mode's. So far each mode
  has two: a multiplayer win, and something only that mode can do.
- **Text.** The title fits the cap. The description names the mode in quotes ("Win a game of 'Quickfire' mode").
  - The constant is the title in snake case, and the `id` is that in lower case. It's permanent.
- **Judging.**
  - `earnedDuringPlay` if a scorecard mid-game settles it; `earnedBy` if it needs the finished game.
  - If it needs something the final state can't show, add a field to `GameAchievementContext`, tracked in
    `game/AchievementTracker`: a `private var`, reset in `startGame`, passed in `endOfGameContext`.
  - Prefer counts to flags. Track only player 1's turns.
- **Timeouts.** An unrolled timeout scores a zero with no roll, so a card can be zeroed by doing nothing.
  - `playerOneTimeouts` must be 0 for How Do You Play This Game?, Rock Bottom, Low Rolls, Cold Dice, Impatient and
    Naturally Gifted.
  - Zero To Hero and Scratched leave out `playerOneTimedOutZeroes`.
  - A new low, zeroed or barely-rolled card achievement needs the same guard.
- **Resuming.** Per-game counters restart on resume, so design each achievement so that only makes it harder.
- **Icon.** Add it to `AchievementIcons.kt`, unused, from Material Symbols (`scripts/material-symbols.sh`) or drawn
  (`UI_SCREENS.md`, "Icons").
- **Already handled.** `WHERE_WERE_GOING_WE_DONT_NEED_RULES` already fires for any non-Standard mode.
- **Hidden.** Miscellaneous ⇔ `HIDDEN`, and `AchievementEngineTest` enforces both directions.
- **Bookkeeping.** Update the "Game Modes achievements are ordered" list in `AchievementEngineTest`, and the count in
  `DESIGN.md`.
- **Audit.** Run the audit in `GAME_MODES_AUDIT.md`.

## Tests

| Change                       | Test                                                                                    |
|------------------------------|-----------------------------------------------------------------------------------------|
| The mode, its ceiling        | `GameModeTest` (pin the fields that differ; the perfect game covers every entry)        |
| Scoring, timeout choice      | `DiceScoringTest`, `ScoreCalculatorTest`                                                |
| Engine turn rules            | `GameEngineTest`                                                                        |
| AI                           | `AiTurnPlayerTest`, `HandScoringTest`, `StandardPerfectPlayTableTest`                   |
| Achievement rules            | `AchievementEngineTest`: earned, and not in a loss, solo game or other mode; each guard |
| View-model flow, timers      | `GameViewModelTest`, `GameAchievementsWiringTest`                                       |
| Anything `GameScreen` drives | Robolectric with the real screen and view model (`GameScreenCupGateTest`)               |
| Saved games                  | `GameStateJsonTest` (new state round-trips; a missing required field fails)             |

Mode-specific test notes (the general traps are in the `test` skill):

- `oneScoreEach(map)` (commonTest) turns "a score or null per box" into list-per-box scorecards.
- Use `CUP_SHAKE_MILLIS` (internal), never a copied number, when timing an AI turn.
- A Third Wind render shows dimmed 0 *previews* in the next open slot. Print the card beside a render before chasing a
  bug.
- To see which frames recompose what on a board change, use the `benchmark` skill's counter.

## Finishing

- **Docs.**
  - This file: the mode table, and the field map if a field was added.
  - `GAME_MODES_AUDIT.md`: the mode's section.
  - `DESIGN.md`: the mode table, maximum scores and achievement count.
  - `UI_BOARD.md`, if the board changed.
- **Verify** with the `test` skill's full gate, then send the debug APK.
- **Commit** as `[Game Mode] ...`. A change to every mode goes under its area instead.
