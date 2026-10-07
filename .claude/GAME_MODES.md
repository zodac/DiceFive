# Game modes

How game modes are built, and how to add one, including one quite unlike the modes so far. A mode
is a set of rules picked on the New Game screen and carried on `GameState` (and each `PlayerState`)
for the whole game.

Modes so far:

| Mode        | `id`        | What's different from Standard                                                                                      |
|-------------|-------------|---------------------------------------------------------------------------------------------------------------------|
| `STANDARD`  | `standard`  | Nothing - the official rules. The default.                                                                          |
| `TRICOLOUR` | `tricolour` | Dice also roll red/yellow/blue; four colour boxes join the card                                                     |
| `QUICKFIRE` | `quickfire` | 5x and six random other boxes are switched off every game: six turns, a scaled upper bonus, off the Leaderboard |
| `STUD`      | `stud`      | Shown to players as "7 Dice Stud" (the enum and id stay `STUD` / `stud`). Seven dice rolled, five hold slots; only the five held dice score, but a box can be tapped with fewer held - the hand is completed for it (`HandCompletion`)                   |
| `THIRD_WIND` | `third_wind` | Every box scored three times (39 turns); one upper bonus, 189 earns 105; joker once the 5x box's 3 slots are used; off the Leaderboard |
| `HIT_LIST`  | `hit_list`  | No Standard boxes: 12 targets drawn each game (numbers by place, up to 2 any) plus the Alibi; exact order doubles; partial hits (2+ numbers rolled) score half the share rolled; no Extended Scores; off the Leaderboard |

History: `DESIGN.md` Phase 14 (Tricolour, and how modes were first modelled), Phase 20
(the first Quickfire, replaced), Phase 25 (Stud), Phase 26 (Third Wind), Phase 30 (the new Quickfire) and Phase 31
(Hit List).

## The one rule: a mode is data

**Everything that can differ between modes is a field on `model/GameMode.kt`**, even where every mode
agrees on it. The engine, AI, achievements, persistence and board read the rules from the mode, so
they already handle a new one. A new mode should be:

1. a new `GameMode` entry, plus
2. any rule that's genuinely new: a new field on `GameMode` (with a default that leaves every other
   mode as it was), a new `ScoreCategory`, or a new property on `Die`, plus
3. the player-facing content in the checklist below.

Nothing else should need to learn the mode exists. **Don't write `if (mode == QUICKFIRE)` in engine,
view-model or UI code** - add a field that says what the rule *is* (`rollsPerTurn`,
`disabledCategories`) and read that. The only checks for a named mode are
achievements that name one ("Win a game of 'Quickfire' mode", and the non-Standard/non-default
checks for Rules? and I Did It My Way), Tricolour's easter egg (`IrishEasterEgg`), and `TieBreak`'s
Tricolour-only colour-box criterion. If you find a hard-coded `3` rolls or `5` dice, move it onto
the mode.

**`id` is a storage key** (settings, saved in-progress games). Never change one; rename
`displayName` instead.

### Every field, and what reads it

When a new rule needs a field, add it here too. This is the map of where each rule takes effect.

| Field                                    | Read by                                                                                                    |
|------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `diceCount`, `dieValues`, `dieColours`   | `GameEngine.newGame`/`rollDice`/`cycleDieValue`, `DiceTray` (incl. the rolling scramble, and a coloured die's `DiceStyle.recoloured`), `AiTurnPlayer` |
| `scoringDiceCount` (`scoresHeldDiceOnly`) | `GameState.scoringDice`/`hasFullHand` (what the board previews, what `GameEngine.commitScore` scores, and whether it may); `GameEngine.toggleHold`/`canHold`/`fillHand` (hold slots, `Die.heldSlot`); `DiceTray` (`SlottedDice`); `AiTurnPlayer` (`RollSpace`, `chooseHand`); the timeout; achievements judged on a roll (guarded) or a hand (read `scoringDice`); A Cunning Strategy's "all five" |
| `rollsPerTurn` (the game's own is `GameState.rollsPerTurn`: the Number of Rolls modifier, else this) | `GameEngine` (turn reset) and the `GameState` default set `rollsRemaining`, which the cup's `xN` badge and the AI loop read; `GameViewModel`'s `fullRolls`/`rollsRemainingAfter*` helpers; Impatient/Naturally Gifted's guard |
| `categories` (a game's own card is `GameMode.categoriesWith(extendedScores)`, read as `PlayerState.categories`; the Extended Scores modifier adds Two Pair, Evens, Odds - `DESIGN.md` Phase 28) | `PlayerState` (card, totals, completeness), `ScoreCalculator`, `ScoreGrid`, `GameStateJson`, `AiTurnPlayer` baselines, "How Do You Play This Game?" |
| `upperBonus*`, `fiveOfAKindBonusAmount`  | `PlayerState` totals, `ScoreCalculator`, the 5x tile's bonus preview                                       |
| `disabledCategories`, `randomDisabledCategories` (a game's own set is `GameState`/`PlayerState.disabledCategories`, drawn by `drawDisabledCategories` in `GameEngine.newGame`) | `PlayerState.isOpen` (never open - so `ScoreCalculator`, `HandScoring.filledMask` and the AI skip them), `turnsPerGame`, `upperBonusThreshold`/`hasUpperBonus`, `GameStateJson`, `CategoryCell`/`CategoryTile` (drawn as an outline), the achievement guards (`hasFullCard`) |
| `maxPossibleScore`                       | `HIGHEST_POSSIBLE_SCORE` (Leaderboard/Statistics column width), `GameModeTest`                              |
| `scoresPerCategory` (`turnsPerGame`)     | `PlayerState` (`scorecard` is a list per box; `isOpen`, `turnsTaken`/`turnsLeft`, `isScorecardComplete`, totals, `fiveOfAKindJokerActive`), `ScoreCalculator` (open boxes, the joker), `HandScoring.filledMask` (a full box), `AiTurnPlayer` (upper-bonus reach), `TieBreak` (every zero slot counts), `GameStateJson`, `CategoryCell` (`StackedScores`, spoken state), `GameViewModel` (turn timer's turn count, final round), the achievement guards, Luck Of The Draw's turn count |
| `countsOnLeaderboard` (also off whenever a modifier, i.e. the turn timer, is on - `GameState.countsOnLeaderboard`) | `GameViewModel.persistHumanScores` → `ScoreEntry.onLeaderboard`, which `ScoreDao`'s Leaderboard page/count, best score and distinct scores filter on (Statistics and career points don't); New Personal Best's guard |
| `allowsRollModifiers` (every mode allows them now) | `GameViewModel.startGame` (starts a game with `RollModifiers()`), the setup screen's locked Number of Rolls / Stored Rolls rows |
| `maxRollsPerGame`                        | `flowerpotGrowthStage` - the Flowerpot's stages are spread evenly over it, and the sunflower blooms on its last roll (Greenfingers); `GameModeTest` |
| `hitListShapes` (`hasHitList`; a game's own targets are `GameState`/`PlayerState.hitList`, drawn by `drawHitList` in `GameEngine.newGame`) | `ScoreCalculator.scoreFor` (targets and the Alibi - `DiceScoring` refuses them), `GameStateJson`, `AiTurnPlayer` (hands the whole turn to `HitListPlay`; `prepareHard` does nothing), `CategoryCell`/`CategoryIcon` (the target drawn on its tile, with progress bars), the achievement guards and Right On Target |
| `allowsExtendedScores`                   | `categoriesWith` (ignores the modifier), `GameEngine.newGame`, `GameViewModel.startGame`, the setup screen's locked Extended Scores row |

## First, work out what kind of mode it is

Most of the work depends on which of these the mode touches. A mode can touch several.

- **Only numbers that are already fields** (more rolls, a different bonus): the entry, rules page,
  achievements and tests. Everything else follows.
- **New scoring** (new boxes): see [New categories](#new-categories).
- **New dice** (a new property, faces, or dice count): see [New dice](#new-dice).
- **More dice rolled than score** (a hand picked from the roll): see
  [More dice than score](#more-dice-than-score). This is where Stud's problems were.
- **A box scored more than once** (more turns, a bigger card): see
  [More than one score a box](#more-than-one-score-a-box). This is where Third Wind's problems were.
- **Boxes switched off for a game** (fixed or drawn at random): see
  [Boxes switched off](#boxes-switched-off).
- **Scores that shouldn't sit with the others** (off the Leaderboard): see
  [Off the Leaderboard](#off-the-leaderboard).
- **The turn's flow** (when rolls happen, timing, what's automatic): see
  [Turn flow](#turn-flow-rolls-timers-undo). This is where Quickfire's problems were.
- **Another setup option** (it fixes or overrides one): see [New Game screen](#new-game-screen).

Then audit the existing achievements against it - see
[Existing achievements a mode affects](#existing-achievements-a-mode-affects). Every mode so far has
changed something there.

## Checklist

### The mode itself (`model/GameMode.kt`)

- Add the entry with every field set explicitly (copy `STANDARD` and change what differs).
- `description` is the one line under the mode's name on the New Game screen. Say what's
  *different*.
- `maxRollsPerGame` is declared too (boxes x rolls per turn, derivation on the entry); `GameModeTest`
  plays a game using every roll through the engine and fails if it's wrong. The Flowerpot's sunflower
  grows through its stages evenly over `maxRollsPerGame` and blooms only on the last of them - and only
  in a mode with 3 rolls a turn (`growsSunflower`), so any other mode can't earn Greenfingers.
- `maxPossibleScore` is declared, with its derivation as a doc comment on the entry. `GameModeTest`
  plays a perfect game (every turn a 5x) through the real engine for every mode and fails if the
  number is wrong. If scoring is unchanged, it's the same as the mode it copies. A rule that changes
  what a "perfect game" is (no 5x possible, say) may need that test's `perfectGame` taught about it.
- Doc comments on the entry and fields state the rule. Don't write down *why* the user wanted a rule
  unless they said why - a guessed rationale in a doc reads as fact later.

### Rules page (`ui/rules/RulesScreen.kt`)

Add a `RulesPage` titled with the mode's name, and the same (or a shorter form) as its `tabLabel`, to the end of
the Modes group in `RULES_GROUPS` (after its overview and the other modes, before the Modifiers group). If the mode's
scores go on the Leaderboard (`countsOnLeaderboard`), add it to the list of those in the overview (`rules_modes_3`).
Describe it relative to
Standard ("A custom mode extending the *Standard* game mode...") and say what stays the same. Build
it from the page's block types, not hand-typed markup: `text(...)` for a paragraph, `RulesStep` for
an ordered list, and `RulesCategory` (name, what it takes, an example `dice(...)` row) for each new
scoring category - its points go in the example row's score, not the text. Style text to the one
convention in `RulesPage`'s doc comment: `` `backticks` `` (gold monospace) for a scoring category's
name, `*italic*` for a section, mode or setting name, `**bold**` only for points or a count, no dashes as bullets or asides, no dice written out as
text. The pager,
both tab rows and the footer's count size themselves from the groups. Update the page each time the mode's rules change -
Quickfire's changed three times after it was written.

### New categories

A category's scoring rule is mode-independent; a mode only chooses which categories are on its card.

- `model/ScoreCategory.kt`: the entry, with its `section` (a new `ScoreSection` if it shouldn't count
  towards the upper or lower totals - Tricolour's `COLOUR` kept "Lower Class" meaning the same in
  every mode), `fixedScore`, `jokerFreeFill` (does a repeat 5x fill it at full value?) and
  `matchingColour`.
- `game/DiceScoring.score` - an exhaustive `when`, so it won't compile until the rule is there.
- `PlayerState`: a new section needs its own total, added to `totalScore`.
- Board: `CategoryIcon` (exhaustive `when`), any special tile art in `CategoryTile` (Coloured House's
  stripes), the spoken name in `BoardSemantics`. `ScoreGrid` lays out whatever `categories` holds,
  and switches to compact tiles past six rows - see `UI.md`'s "The scorecard grid and game modes"
  before adding more than two rows.
- AI: Easy needs nothing, and Hard nothing beyond the scoring itself - it scores every hand once
  through `DiceScoring`, in `HandScoring`, which mirrors `ScoreCalculator`'s joker rule as plain
  arithmetic. **A change to that rule (or a new free-fill box) goes in `HandScoring.forEachLegal`
  too**; `HandScoringTest` compares the two in every mode and fails until it does. Medium's rules of
  thumb need to know how to chase the new box, and `CATEGORY_RESTRICTIVENESS` needs it ranked.
- **Standard's perfect-play table** (`StandardPerfectPlayTable`, bundled in `composeResources/files/`)
  is worked out from Standard's rules: anything that changes how Standard scores - its boxes, a box's
  scoring, its bonuses or rolls - means regenerating it (`./gradlew :app:shared:testAndroidHostTest
  --tests '*StandardPerfectPlayTableTest*' -PregeneratePerfectPlayTable`, about 20s), or
  `StandardPerfectPlayTableTest` fails. Other modes have no table; Hard estimates in them.
- Tie-breaks (`game/TieBreak.kt`): a mode-only stat (Tricolour's colour-box count) goes in the live
  game's criteria but is left out of the leaderboard's, because the leaderboard mixes every mode and
  other modes have no value for it.
- The rules page lists each new box and its value.

### New dice

- A new property on `Die` (as `colour` was) must be written and read by `GameStateJson`, or a resumed
  game loses it. Keep reading old saves that don't have it.
- `GameEngine.rollDice` should only draw the new property in modes that use it, so other modes' dice
  come out of a seeded `Random` exactly as before - tests depend on that.
- `DiceTray` draws the dice (and a random face while rolling), and superuser mode's
  `GameEngine.cycleDieValue` steps through faces - both need to know the new faces.
- Achievements that compare rolls (Déjà Vu, Are These Loaded Dice?) compare whole faces via
  `faces()`, so a new property is included automatically - check that's what "the same roll" should
  mean.
- AI Hard plans over every distinct hand and every set of dice that can be held from one
  (`DiceSpace`, numbered once into flat arrays). More faces multiply both: Tricolour's 18 faces are
  26,334 hands and 33,649 held sets - about 10 MB of arrays, most of a second to build on a laptop
  (`GameViewModel.prepareHardCpus` builds it in the background when a game with a Hard CPU starts),
  then ~2ms a decision. Check a new face count's build time, memory and decision time stay sane.

### More dice than score

Stud rolls seven dice and scores the five held (`scoringDiceCount < diceCount`, i.e.
`scoresHeldDiceOnly`). Almost everything was written assuming "the dice" and "the hand" are the same
list. They aren't here, so:

- **Score `GameState.scoringDice`, never `state.dice`.** That's the board's preview, `commitScore`,
  the AI's category choice, the timeout's category, and every achievement judged on the scored hand.
  `state.dice` is still right for what's *on the table*: `lastRoll`, Déjà Vu, Loaded Dice, Lucky
  Seven, die indices. Grep each `state.dice` read and decide which it means.
- **A part-held hand is scored and previewed**, so scoring must cope with fewer dice than a full
  hand. `DiceScoring.isFiveOfAKind` needed a dice-count check: three held 6s were "all the same" and
  previewed a 5x, and could have triggered the joker rule.
- **Scoring needs a full hand** (`hasFullHand`): `GameEngine.commitScore` throws without one, but the
  view model never gives it a part-held one: `HandCompletion` completes it first (see below).
- **Holding is capped** (`canHold`). Anything that changes holds in bulk must release before it
  holds: the AI's `applyHolds` swapped dice in the wrong order and hit the cap.
- **The AI**: `AiTurnPlayer` used "every die held" to mean "stop rolling", and five held no longer
  is. Hard's exact search plans over hands. `RollSpace` maps each seven-dice roll to its best five-dice
  hand, and `DiceSpace(maxHeld)` caps the held sets, so the same search works. After the last roll the
  CPU must pick and hold its hand (`chooseHand`) before scoring, visibly and with a reaction delay.
- **The tray**: more dice than a column each need their own layout (`DiceTray`'s `SlottedDice`).
  - Holding and releasing are separate targets: tap the mat to hold, tap a slot to release.
  - The maintainer's calls on how it looks:
    - A held die is the same size in its slot as on the mat, so a slot is only as big as a mat column.
    - The tray keeps the usual height.
    - A held die goes to the *free* slot nearest its own column and **never moves once it's there**.
      An order-preserving version that slid held dice along to make room was rejected.
  - Render it at 411dp and 360dp for review before any APK. Both sizing problems were caught on
    the renders.
- **Achievements**: a roll isn't a hand, so anything judged on the roll as it lands needs a guard -
  see [Stud](#stud-seven-dice-rolled-five-held-to-score).

### More than one score a box

Third Wind scores every box three times (`scoresPerCategory`). Everything had assumed one score a
box, one turn a box. `PlayerState.scorecard` is now a list of scores per box in every mode (empty
while untouched), so:

- **Never read a box as `scorecard[c]`.** Use `scoresIn(c)` (its scores), `isOpen(c)` (a slot left),
  `hasScore(c, n)`, `allScores`, and `turnsTaken`/`turnsLeft`/`isScorecardComplete`. **Trap:**
  `scorecard[c] != null` and `scorecard.values.count { it != null }` still *compile* against a list -
  and are always true. Changing the type turned most misuses into compile errors; those silent ones
  had to be found by grepping, in tests above all (a "CPU took a turn" check that could never fail).
- **"One turn per box" is gone.** A game is `GameMode.turnsPerGame` turns. Everything that counted
  boxes to mean turns needed it: the turn timer's new-turn detection (`turnsTaken`), the final-round
  check (`turnsLeft == 1`), Luck Of The Draw's "3 or fewer of your own turns", and test helpers that
  loop `categories.size` times.
- **Rules that look at "the box" need deciding per slot.** The joker is "the 5x box is full and one
  slot holds 50" (`fiveOfAKindJokerActive`), so the 2nd and 3rd 5x go in the box for 50 with no chip.
  Hard's `HandScoring.filledMask` means "no slot left" - the joker and the mask both have to agree with
  `ScoreCalculator`, and `HandScoringTest` now builds part-used boxes too.
- **Totals scale, so does everything measured on them**: the ceiling (`maxPossibleScore` - the
  perfect-game test fills each box's slots in turn), the upper bonus (its own threshold and amount
  fields), the Hard estimate's upper-bonus reach (counted per open slot), and the score-threshold
  achievements (guarded - see the audit below).
- **Saves**: each box is written as a list; old saves (a number or null) still load.
- **The AI**: no new code - Hard estimates outside Standard (the bundled table is Standard's alone, and
  a Third Wind table would need ~4^13 card states before the upper total). Measure each difficulty in
  the mode against its Standard per-turn average before tuning: two plausible tweaks (no upper-bonus
  pull; a cheaper baseline while slots remain) both made Hard worse - `DESIGN.md` Phase 26.
- **The board** (`StackedScores`): one line per slot beside the tile, the preview only in the next open
  slot. See `UI.md` - the lines fill a tile exactly, so a large font scrolls them.

### A card of targets (Hit List)

Hit List's boxes (`ScoreSection.HIT_LIST`: `TARGET_1`..`TARGET_12` and `ALIBI`) have no rule of their own - what each
target calls is drawn per game (`HitTarget`, from the mode's `TargetShape`s), so:

- **Score them through `ScoreCalculator.scoreFor`, which has the player and so their `hitList`.** `DiceScoring.score`
  throws for them. Anything that scores every box of a card up front (Hard's `HandScoring`, `categoryBaseline`) must
  never be built for this card - `HandScoringTest` skips it, and `prepareHard` returns early.
- **Order matters for the first time**: an exact hit needs each named number on the die in its own column, so the
  dice must be passed in column order (`GameState.scoringDice` is). A hand short of a die (Unlucky Dice) can hit but
  never be exact.
- **A partial hit** (short of a hit) scores half the points times the share of the target's numbers rolled, rounded
  to the nearest 5, a half rounding up (`HitTarget.partialPoints`) - but only with at least two of its numbers rolled
  (`HitTarget.MIN_PARTIAL_ROLLED`); one alone scores 0, however the rounding would fall (1 of 3 on a 15 is 2.5, which
  would round up to 5). Always less than a hit, so a box's score still tells which it was. It
  leaves few zeros (under one a game for a CPU), which is why Spotless is guarded. A CPU must count the partial its
  held dice keep when a chase fails: without that, Hard stopped on partials and fell behind Medium.
- **The Alibi** is worth the best open target's points the dice hit, never doubled, and leaves that target open. Once
  every target is closed it can only score 0 - a CPU that keeps it to the end wastes it (Medium did, until it was
  taught to use it on a plain hit of a five-number target).
- **The board's featured box** (`ScoreCategory.featured`) is 5x on every other card and the Alibi here, the large
  square over rows 1-2. The targets have no upper section to pair with, so they fill the grid two to a row.
- **The AI** is `HitListPlay` at every difficulty: the odds of hitting a target's remaining numbers in the rolls left
  (`hitOdds`, exact and memoised) and of an exact hit (`exactOdds`), and what keeping a box open is worth.
  `HitListPlayTest` checks Hard > Medium > Easy on average (about 255 / 227 / 188).

### Boxes switched off

Quickfire switches off 5x and six random boxes (`disabledCategories`, `randomDisabledCategories`). A disabled box
stays on the card - the board lays out as it always does - but is never open:

- **Ask `PlayerState.isOpen`, never "has no score".** An unscored disabled box is empty like an open one, so
  `scoresIn(c).isEmpty()` or `scorecard[c]` can't tell them apart. `isOpen` is false for it, which is all that
  `ScoreCalculator`, `HandScoring.filledMask` (so every Hard AI search) and the turn counts needed. A new "open
  box" check that doesn't go through `isOpen` is the bug to look for.
- **The set belongs to the game, not the mode**: drawn once in `GameEngine.newGame` (from the `Random` it's given,
  and only when the mode has random ones, so other modes' seeded games are unchanged), copied to every player, and
  saved by `GameStateJson`. A mode that draws a set must fail to decode a save without one.
- **A game's length is its own**: use `PlayerState.turnsPerGame`/`turnsLeft`, never `categories.size`. A test that
  loops over `mode.categories` must skip the boxes that are off.
- **Anything scaled to the card scales to the boxes still on**: the upper bonus threshold
  (`PlayerState.upperBonusThreshold`), `maxRollsPerGame` per player, and the ceiling (`maxPossibleScore` is the best
  draw, tested by playing all of them). With none of a section left there's no bonus to earn.
- **No 5x on the card means no joker**: `fiveOfAKindJokerActive` stays false, and the roll-a-5x achievements can't
  fire since a 5x can't be scored as one.
- **The achievement audit is about totals and zeroes** - see Quickfire's section below. `PlayerState.hasFullCard`.
- **Draw it differently from the board**: a disabled tile is an outline with a slash and "Off" (`UI.md`), because
  every other state is a filled tile.

### Unlucky Dice (a modifier that changes what a roll is)

Unlucky Dice (`UnluckyDice`, `DESIGN.md` Phase 29) locks rolled dice (`Die.isUnlucky`): they can't be held and
don't score, in every mode. It's a modifier, not a mode field, but it changes assumptions a mode can too:

- **A hand can be smaller than `scoringDiceCount`.** Score `GameState.scoringDice` and ask `hasFullHand`
  (`handSize`), never `dice.size` or `scoringDiceCount` - the same rule as [More dice than score](#more-dice-than-score).
- **Never hold a locked die**: `GameEngine.toggleHold`/`canHold`/`fillHand` refuse it; anything that picks holds
  (the AI, a new auto-hold) must leave them out. `AiTurnPlayer.withoutUnluckyDice` does it for the AI and plays
  Hard as Medium, because Hard's search is over whole hands.
- **Achievements judged on a roll** need `dice.none { it.isUnlucky }` (see `checkPostRollAchievements`); a hand is
  `scoringDice`. Luck-driven zeroes (Zero To Hero) are guarded on `GameState.unluckyDice`.
- A new draw from the `Random` happens only when the modifier is on, so seeded tests of every other game are unchanged.

### Off the Leaderboard

`countsOnLeaderboard = false` records a game's scores with `ScoreEntry.onLeaderboard = false`: the
Leaderboard, a personal best and the score-collection bands skip it (`ScoreDao`'s queries filter on
it); Statistics and career points don't. That took a database version (2, an `AutoMigration` with
`@ColumnInfo(defaultValue = "1")` so every existing row stays on the board). The SQL never runs in the
sandbox - the fake DAOs in `ScoreRepositoryTest` and `GameAchievementsWiringTest` mirror its filters, so
**a new filter has to be added to both fakes too**, and the real one is only checked on a device. A
mode off the board also needs New Personal Best guarded: the "previous best" comes from the board.

### Turn flow: rolls, timers, undo

The Number of Rolls and Stored Rolls modifiers (`RollModifiers`, Phase 27) change how many rolls a turn has
in every mode. **Read the rolls a turn started with from
`GameState.turnRolls`, not `gameMode.rollsPerTurn`** - stored rolls make it bigger - and the game's rolls
per turn from `GameState.rollsPerTurn`. Totals-based achievements are guarded on `PlayerState.rollsModified`.

How a roll happens today, in every mode:

- **Every roll lands through one function, `GameViewModel.performRoll`**, human or CPU.
- **A human's roll always starts with a cup tap** - `GameScreen`'s `onCupTap`. A finger, a phone
  shake and Quickfire's automatic roll all call it, so they all shake for `CUP_SHAKE_MILLIS`, play
  the same sound and haptics, then call `rollDice()`, where player 1's achievement tracking runs.
- **A CPU's roll is driven by `GameViewModel.maybeStartAiTurn`**, which shakes for the same
  `CUP_SHAKE_MILLIS` (through `aiRolling`) and then calls `performRoll`. It stays in the view model,
  not the screen, so a recomposition can't interrupt it - but it does **not** run while the game is
  out of sight: `GameScreen` calls `setForeground` from lifecycle callbacks (resumed / paused, and
  leaving composition), and the CPU loop and the turn timer wait through `pausableDelay`, which
  doesn't run down in the background and restarts a wait already under way on return. **Any new wait
  in the CPU loop or the timer must use `pausableDelay`, never `delay`** - a plain one keeps running
  with the app in the background and would forfeit a turn or play a CPU's out behind the player's
  back (`GameViewModelTest` pins both).

Rules that came out of Quickfire:

- **Don't add a second kind of roll.** An automatic or special roll should go through the same tap
  (for a human) or the same `performRoll` (for the CPU). A separate view-model roll with its own
  delay was built for Quickfire and replaced on review, because it made auto-rolls look and behave
  slightly differently from real ones.
- **Never change the game state under the AI loop.** `maybeStartAiTurn` plays from its own copy of
  the state (`current`) across delays. Anything that rolls, holds or scores on a CPU's turn from
  outside that loop puts the game out of step with it. That's why the first Quickfire's auto-roll
  (`awaitsAutoRoll`, since removed) was false on a CPU's turn - the CPU's loop already starts with a roll.
- **Screen-driven rules need the screen.** Quickfire's auto-roll only happens while `GameScreen` is
  composed. That's acceptable for a human turn: if the screen isn't showing, the turn timer still
  ends it (a timeout rolls first if needed). A rule that must happen with no screen belongs in the
  view model.
- **A roll can't start while another is in hand.** `onCupTap` ignores a tap from the shake until
  the dice have settled (`rollInHand`), and Quickfire's auto-roll `LaunchedEffect` is keyed on it, so
  a turn that begins while the last dice are still settling rolls once they've landed rather than
  never. A new automatic tap must wait on the same thing.
- **A roll can land after its turn is gone.** The cup shakes before the roll lands, and Undo stays
  enabled meanwhile. Undo in that window brings back the previous turn, possibly with 0 rolls left,
  so `rollDice` ignores a roll with none remaining (`GameEngine.rollDice` throws on it). Any new
  delayed action should re-check the state it acts on.
- **Undo only reaches a committed score, and any roll clears it.** In a mode that rolls
  automatically, that roll comes right after the score, so the window to undo a score is the length
  of the shake.
- **The timer**: `syncTurnTimer` restarts it for each new turn (human or CPU); running out calls
  `autoScoreOnTimeout`, which rolls if the turn hasn't, then scores `ScoreCalculator.timeoutCategory`.
  A timeout's score is final - it clears the undo snapshot.
- `GameScreen.canHold` doesn't check rolls left, so dice can still be held after the last roll. That
  keeps the hold-only achievements alive in a one-roll mode.

### New Game screen

The Game Mode card lists `GameMode.entries` as radio rows, so a new mode appears automatically with
its `description`. Only a mode that changes *another* setup option needs work here:

- **Disable the option the mode overrides; don't hide it.** The first Quickfire disabled the Turn Timer row
  and locked the roll modifiers (through `SegmentedChoiceRow`'s and `ModifierSetting`'s `enabled`/`lockedNote`;
  the mode fields behind it were removed in Phase 30 - see `git log` if a mode needs them again).
- **Keep and save the player's pick**, so it comes back when they choose another mode.
- **Start the game with the neutral value** (`GameViewModel.startGame` passes `TurnTimer.NONE`), so a
  saved game doesn't carry a setting that isn't in play. Whether it counts as "customised" for I Did
  It My Way follows from that.
- Don't add text or rows to the form for a mode. The `description` and rules page explain it, and an
  added element must not move or re-space the existing ones.

### Achievements

- Add the mode's achievements to `model/Achievement.kt` in the Game Modes block, after the previous
  mode's. So far each mode has two: a win (multiplayer, like every win) and something only that mode
  can do.
- Each needs a stable `id` (never changed), a title, and a description naming the mode in quotes
  (`"Win a game of 'Quickfire' mode"`).
- Judge them in `game/AchievementEngine.kt`: `earnedDuringPlay` if a scorecard mid-game already
  settles it (Tricolour Me Impressed), `earnedBy` if it needs the finished game. If it needs
  something the final `GameState` can't show, add a field to `GameAchievementContext` and track it in
  `GameViewModel`: a `private var`, reset in `resetAchievementTracking`, passed in
  `recordEndOfGameAchievements`. Prefer a count to a flag (`playerOneTimeouts` started as a
  `playerOneTimedOut` flag; Luck Of The Draw then needed the count). Only track player 1's turns.
- Per-game counters restart from zero when a saved game is resumed. Design each achievement so that
  can only make it harder, never hand it out.
- Icon in `ui/achievements/AchievementIcons.kt` (exhaustive `when`). **Grep that the glyph isn't
  already used** - Quickfire's first two picks (Speed, then ElectricBolt) clashed with Solid Round
  and Dice Deity. Check the icon exists in `material-icons-extended`.
- `NON_STANDARD_MODE` and `I_DID_IT_MY_WAY` already fire for any non-Standard mode.
- A Miscellaneous achievement must be `HIDDEN`, and a hidden one must be Miscellaneous -
  `AchievementEngineTest` enforces both ways.
- Update the "Game Modes achievements are ordered" list in `AchievementEngineTest`, and the
  achievement count in `DESIGN.md`.
- **Audit the existing achievements** - next section.

## Existing achievements a mode affects

Many achievements assume three rolls, holding between rolls, no timer, or the Standard card. For each
new mode, go through the families below. For each achievement the mode changes, decide:

- **Can't be earned in this mode** - leave it. It never unlocks in that mode and can still be earned
  in others. No code needed.
- **Earned for free in this mode** - the mode makes the feat automatic. **Add a guard** so the mode
  can't unlock it, and a test for the guard. **Prove the test by removing the guard and watching it
  fail** - that's how Almost Famous's guard was shown to be needed rather than assumed.

Families to check:

| Family              | Achievements                                                                                 | What to ask                                                                 |
|---------------------|----------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| Roll count          | Impatient, Naturally Gifted, Almost Famous, Natural 5x, The Dice Hate Me, Déjà Vu, Loaded Dice, Time To Let It Go, Pointless Roll, Greenfingers, Probability? Never Heard of Her | Does "only one roll used" or "every roll used" become automatic or impossible? |
| First roll          | House Call, Straight Away, Five on the Fly, I Can Count!                             | Is every roll now a first roll, or none?                                    |
| Holds               | A Cunning Strategy, Decisions Decisions, Time Wasting, Commitment Issues                     | Can dice still be held?                                                     |
| Timer               | Out Of Time, Beat The Clock, Luck Of The Draw                                                | Is there always, or never, a timer?                                         |
| Card contents       | How Do You Play This Game?, Spotless, Bonus Round, Upper/Lower Class, Exact Change, the 5x ones | Is each box still on the card, and do totals still mean the same?            |
| Score thresholds    | Solid Round ... Dice Deity, Cold Dice, Low Rolls, Ton!, Nice, Rock Bottom, the score bands   | Is a threshold now trivial or impossible for this ceiling?                  |
| Dice look           | Fresh Coat Of Paint, Luck of the Irish                                                       | Does the mode draw the player's dice style?                                  |

The rolls-remaining helpers in `GameViewModel` (`rollsRemainingAfterFirst`/`AfterSecond`) are
`rollsPerTurn - 1`/`- 2`, so with fewer than three rolls they go to 0 or below. Checks that compare
against them may start matching the wrong roll, or never match - read each one.

### Quickfire (5x and six random boxes switched off)

A card with boxes off is a short game with small totals, so anything measured on a whole card, a total or a
zero needs deciding. `PlayerState.hasFullCard` (no box off) is the guard.

**Guarded - would be free on a six-box card** (`AchievementEngine`; each test in `AchievementEngineTest` fails
without its guard):

| Achievement                                           | Why it would be free                                                    |
|-------------------------------------------------------|-------------------------------------------------------------------------|
| Spotless (`NO_ZEROES`)                                | Six turns without a zero, not thirteen                                  |
| Cold Dice (`SCORE_UNDER_100`), Low Rolls (`LOW_ROLLS`), Rock Bottom (`EXTREME_LOW_ROLLS`) | The ceiling is 225 - a low total is the usual run of things |
| How Do You Play This Game? (`ALL_ZEROES`)             | Fewer boxes to zero                                                     |
| Solid Round ... Dice Deity (`SCORE_200`-`500`)        | Not free, but a total over a different card measures nothing            |
| Upper Class (`UPPER_84`), Lower Class (`LOWER_150`)   | Section totals over a different set of boxes                            |
| Bonus Round (`UPPER_BONUS`)                           | The threshold shrinks with the boxes - 18 with only the 6s left         |

**Can't be earned in Quickfire - no guard needed**: every 5x achievement that needs the 5x scored (First 5x,
Encore, Hat Trick, Scratched, Twice in a Lifetime) and the rolled-a-5x ones (Natural 5x, Five on the Fly), which
need a 5x that "could be scored as one" (`fiveOfAKindScorable`) - with the box off and no joker it never can.
The score-collection bands (the games aren't on the Leaderboard) and New Personal Best (`countsOnLeaderboard`).
Exact Change and Both Straights need those boxes to have come up in the draw.

**Still earnable, and worth knowing - report these to the user rather than deciding alone**: Impatient and
Naturally Gifted (three rolls, so a real choice), Greenfingers (grows over 18 rolls rather than 39, so easier but
still every roll of every turn), Nice and Ton! (a total of 69 or 100 is likelier on a smaller card), Zero To Hero
(three zeroes in six turns is harder), Luck Of The Draw (all but 3 of 6 turns), Landslide and the win ones.
The mode's own: Quick On The Draw (`QUICKFIRE_WIN`, a multiplayer win) and Six Of The Best (`QUICKFIRE_SCORE`,
a total of 150 or more, not with extra rolls or Extended Scores). It used to have Beat The Clock, which went with
the timer.

### The old Quickfire (1 roll per turn, 10s timer) - gone

Its achievement guards (Impatient, Naturally Gifted, Almost Famous for one roll) are still in the engine, now
reachable only with the Number of Rolls modifier set to 1. Its notes on turn flow above still apply to any mode
that automates a roll.

### Stud (seven dice rolled, five held to score)

**Completing the hand.** After the last roll "which five?" is bookkeeping - the box tapped says what
the player means - so a box can be tapped with fewer than five held. `HandCompletion` tries every
completion of the held dice (at most C(7,k), 21 for one held) and takes the one worth most in *that*
box (score plus any 5x bonus; first - leftmost dice - on a tie). The board previews every box with its
best completion (`LocalProjectedHands`, human turns only; a CPU's board still reads what it holds), the
view model holds the completing dice (they glide into slots for `HAND_COMPLETION_MS`, then it scores),
undo goes back to the dice as the player held them, and a turn timeout scores the first box any
completion can score, completed. Holds still matter mid-turn: they decide what is rerolled. While a
completion is gliding `toggleHold` and a second `commitScore` are ignored, and the delayed commit
drops itself if the game moved on (`_game.value !== completed`).
**Ambiguity**: pressing a box marks, on the mat, the dice it would take (solid gold lane) and the
unheld ones that would have scored just the same with other faces (dashed lane); the rest fade. It is
`ProjectedHand.used/alternatives`, written by `CategoryCell` into `LocalHandPreview` and drawn by
`SlottedDice`. Same-faced swaps aren't alternatives. Holding one of the dashed dice first makes it the
hand. Sighted-only, no spoken twin - the score is identical either way.

In Stud a roll isn't a hand - nothing is held straight out of the cup, and the hand is whichever
five dice the player holds. So achievements that judge a hand at the moment of scoring read
`GameState.scoringDice` (Wasted 5x, Why Did You Do That?, Empty/Fuller House, Time To Let It Go,
Twice in a Lifetime), and the ones that judge the roll as it lands are guarded, since seven dice
make the pattern far easier (or meaningless):

**Guarded - can't be earned in Stud** (`!scoresHeldDiceOnly`, in `GameViewModel`; each test fails
without its guard):

| Achievement                                            | Why it would come too easily                                      |
|--------------------------------------------------------|-------------------------------------------------------------------|
| House Call, Straight Away, Five on the Fly (first roll) | Seven dice hold a straight or five matching far more often - and nothing is held yet, so it isn't a hand |
| Natural 5x (`NATURAL_5X`)                               | Same, on a reroll with nothing held                               |
| The Dice Hate Me (`DICE_HATE_ME`)                       | Judged on the roll, which isn't what scores                        |
| Almost Famous (`ALMOST_FAMOUS`)                         | A 5x among seven dice never shows as one, so "never landed the 5x" was always true |

**Can't be earned in Stud - no guard needed**: I Can Count! and Product Placement (seven dice never
equal a five-dice sequence); What Was The Point Of That? (every die can never be held - with five
held, two still roll).

**Still earnable, and worth knowing**: A Cunning Strategy and Commitment Issues count "all five" as
every hold slot (`scoringDiceCount`), so they work as in Standard. Déjà Vu and Are These Loaded
Dice? compare all seven dice, so they're harder. Well Rolled counts seven dice a roll. Greenfingers
grows as in Standard (3 rolls a turn, 39 a game). Lucky Seven (`STUD_LUCKY_SEVEN`) is Stud's own:
all seven dice showing one number after a roll - most often five held and the last two matching.

### Third Wind (every box scored three times)

A box "holds" a score if any one of its slots does - Six Appeal and Taking A Chance need a 30 in one
slot (three slots adding to 30 don't count), Exact Change a 1 in one Ones slot and so on, Scratched a
zero in one 5x slot, Straight Talker a non-zero slot in each straight. Every zero slot is a zero (Zero
To Hero's count, How Do You Play This Game?, the tie-break). "Scored a 5x" counts every 50 in the 5x
box plus chips (`fiveOfAKindCount`), so Encore and Hat Trick now read that count rather than bonus
chips - the same thing in a one-slot mode, but a Third Wind 5x in the box's 2nd or 3rd slot earns no chip.

**Guarded - would be free in Third Wind** (`scoresPerCategory == 1`, or `countsOnLeaderboard`; each
test in `AchievementEngineTest` fails without its guard):

| Achievement                                      | Why it would be free                                                    |
|--------------------------------------------------|-------------------------------------------------------------------------|
| Solid Round ... Dice Deity (`SCORE_200`-`500`)   | Totals are three times Standard's - Hard averages over 700              |
| Upper Class (`UPPER_84`), Lower Class (`LOWER_150`) | Section totals over 18/21 slots pass them as a matter of course       |
| New Personal Best (`PERSONAL_BEST`)              | The best is read off the Leaderboard, which a Third Wind total dwarfs   |
| Zero To Hero (`ZERO_TO_HERO`)                    | Three zeroes over 39 turns is the usual run of things                    |
| Spotless (`NO_ZEROES`)                           | Not free, but replaced: Third Time's The Charm is Third Wind's own       |

**Can't be earned in Third Wind - no guard needed**: Rock Bottom (the lowest total is 15, Chance's
three slots); the score-collection bands (the rows aren't on the Leaderboard).

**Still earnable, and worth knowing**: Bonus Round (the 105 bonus, as hard to reach as Standard's 35);
Landslide (margins triple, so easier); Encore, Hat Trick and Dice Whisperer (three times the turns);
Cold Dice, Low Rolls, Ton! and Nice (harder); Luck Of The Draw counts all 39 turns (36 timeouts);
Professional Roller counts Third Wind points, which are earned a turn at a time like any others;
Greenfingers grows over 117 rolls. The mode's own two: Gone With The Wind (`THIRD_WIND_WIN`, a win,
multiplayer) and Third Time's The Charm (`THIRD_WIND_NO_ZEROES`), all 39 slots scored without a zero.

### Third Wind: what it showed

- **Easier isn't free.** Landslide, Encore, Hat Trick and Dice Whisperer all come more easily over 39
  turns but still need the dice, so they weren't guarded; the totals-based ones were, because any
  finished game passes them. Report the "easier" list to the user rather than deciding it alone.
- **Prove a guard per guard.** One test covered both the score ladder and Upper/Lower Class; stripping
  every guard at once made it fail, which proved nothing about each. Strip them one at a time.
- **Count the thing, not its proxy.** Encore and Hat Trick read bonus chips, which equalled "2nd/3rd
  5x" only while the 5x box had one slot. They now count `fiveOfAKindCount`.

### Hit List (twelve targets and the Alibi)

No upper or lower boxes, no 5x, and totals (ceiling 785, about 200 a game) that mean nothing beside Standard's.
Points, partial hits included, are multiples of five.

**Guarded - would be free, or measure something else, on a card of targets** (`GameMode.hasHitList`; each test in
`AchievementEngineTest` fails without its guard):

| Achievement                                           | Why                                                                      |
|-------------------------------------------------------|--------------------------------------------------------------------------|
| Solid Round ... Dice Deity (`SCORE_200`-`500`)        | A total over a different card measures nothing - and 200 is an average game |
| Cold Dice (`SCORE_UNDER_100`), Low Rolls (`LOW_ROLLS`) | A total under 100 is an ordinary run of misses (`hasStandardTotals`)    |
| Zero To Hero (`ZERO_TO_HERO`)                         | Misses (and partial hits) are the usual run of things                     |
| Spotless (`NO_ZEROES`)                                | Partial hits leave under one zero a game                                  |

**Can't be earned in Hit List - no guard needed**: every 5x one (no box, no joker), Upper/Lower Class, Bonus Round,
Exact Change, Six Appeal, Taking A Chance, Both Straights, the house and straight roll feats (their boxes aren't on
the card), Nice (69 isn't a multiple of five), Rock Bottom (5 isn't reachable), New Personal Best and the
score-collection bands (off the Leaderboard).

**Still earnable, and worth knowing**: How Do You
Play This Game? (zeroing every box on purpose, as in Standard); Ton!; Wasted 5x (five matching dice never hit a target,
so scoring them is always a zero); The Dice Hate Me (more likely - chasing an exact hit can lose the plain one);
Greenfingers (39 rolls, as Standard); Luck Of The Draw (13 turns). The mode's own: Contract Fulfilled
(`HIT_LIST_WIN`, a multiplayer win) and Right On Target (`HIT_LIST_RIGHT_ON_TARGET`, an exact hit on a target naming
all five numbers - judged mid-game, from the score its box holds).

### Tricolour (coloured dice, four colour boxes)

Nothing guarded, nothing blocked. Déjà Vu and Are These Loaded Dice? compare number *and* colour.
Fresh Coat Of Paint counts the dice style as in any mode - Tricolour draws the player's style,
recoloured per die (it once drew its own fixed coloured dice, and the dice style didn't count). How
Do You Play This Game? needs the colour boxes zeroed too. See `DESIGN.md` Phase 14.

## Tests

Where each kind of change is tested:

| Change                         | Test                                                                                              |
|--------------------------------|---------------------------------------------------------------------------------------------------|
| What the mode is, its ceiling  | `GameModeTest` (pin the fields that differ; the perfect-game test covers every entry)             |
| Scoring and timeout choice     | `DiceScoringTest`, `ScoreCalculatorTest`                                                          |
| Engine turn rules              | `GameEngineTest`                                                                                  |
| AI                             | `AiTurnPlayerTest`; `HandScoringTest` (Hard's copy of the joker rule); `StandardPerfectPlayTableTest` (the bundled table still matches Standard's rules) |
| Achievement rules              | `AchievementEngineTest` - earned, and not in a loss, solo game or another mode; each guard        |
| View-model flow, timers        | `GameViewModelTest`, `GameAchievementsWiringTest` (achievements through a real game)              |
| Anything `GameScreen` drives   | A Robolectric test with the real screen and view model - `GameScreenAutoRollTest` is the pattern  |
| Saved games                    | `GameStateJsonTest` (new per-game or per-die state round-trips; old saves still load)             |

Useful tools, and what tripped this work up:

- **Scripted dice**: `LoadedDice` (every die one value), `ScriptedDice` (a fixed sequence) and
  `CountingDice` in `GameAchievementsWiringTest`; `FixedValueRandom` in `GameViewModelTest`. Pick
  dice that make the mode's rule give a *different* answer from the old rule - a timeout test with
  dice where "lowest" and "first open" pick the same box proves nothing.
- **Virtual time**: view-model tests run on a `StandardTestDispatcher`; pass it as `aiDispatcher`
  too, or the AI's work never runs. Use `advanceTimeBy` + `runCurrent` for timers.
  `advanceUntilIdle` in a timed game plays every turn out through timeouts.
- **The screen harness**: `GameScreen` needs `LocalPlatformServices provides SilentPlatformServices`.
  The view model's coroutines delay on the Android main looper, which Robolectric keeps paused, so
  step it with `ShadowLooper.idleMainLooper(...)` alongside `compose.mainClock` - see
  `GameScreenAutoRollTest.waitFor`.
- **Only do what the UI allows.** The engine throws on impossible actions (a roll with none left, a
  hold before rolling). A test calling `rollDice()` twice in a one-roll mode failed for that reason,
  not because of a bug.
- **Work out expected values by hand before asserting.** A tie test assumed dice 1-2-3-4-5 scored
  above zero everywhere; Sixes scores 0.
- **Timing constants**: a test that measures an AI turn's length should use `CUP_SHAKE_MILLIS`
  (internal) rather than a copied number.
- **No commas or parentheses in `commonTest` test names** - Kotlin/Native rejects them (see
  `IOS_SUPPORT.md`). Stud's first run broke the iOS compile on two of them; the JVM tests don't catch
  it, only the root `testDebugUnitTest` does.
- **Scorecards in tests**: `oneScoreEach(map)` (in `commonTest`) turns the old "a score or null per
  box" map into the list-per-box scorecard, for tests of one-slot modes.
- **A render that looks wrong may be right.** Third Wind's renders showed zeros in boxes that were never
  scored - they were the roll's dimmed 0 *previews*, in the next open slot. Print the card alongside a
  render before chasing a bug. And re-run a scratch test with `--rerun`, then check the PNG's
  timestamp: Gradle skips an unchanged test, leaving the old picture in place.
- **Measuring a board change's frame cost**: the frame loop in `BENCHMARKS.md`, plus a throwaway
  counter (a `var` on an `object`, bumped at the top of the composables in question) printed per frame,
  shows *which* frames recompose what - Third Wind's showed only the tap and landing frames do, which
  the timings alone couldn't.
- **A tray screenshot for review**: a throwaway Robolectric test with `@GraphicsMode(NATIVE)` and
  `@Config(qualifiers = "w411dp-h891dp-xxhdpi")`. It sets up `GameScreen` as in the screen harness,
  rolls with a scripted `Random`, holds, steps the clocks, then `onRoot().captureToImage()` to a PNG
  in the scratchpad. Delete it afterwards.

## Docs, verification and commit

- This file: the mode table, the field map if a field was added, and the mode's section in the
  achievement audit.
- `DESIGN.md`: the "Game modes" bullet under Decisions, the setup rules, the max-score line, the
  achievement count, and a phase entry for the mode.
- `UI.md`, if the board or a shared component changed.
- Verify with `./gradlew assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin lint` (the
  root `testDebugUnitTest` also compiles the iOS code), then send the debug APK.
- Commit as `[Game Mode] ...`. A change that applies to every mode (like CPU rolls sharing the tap's
  shake) goes under its own category instead - `[Gameplay]`, `[Achievements]`.
