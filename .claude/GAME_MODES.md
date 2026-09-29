# Game modes

How game modes are built, and how to add one, including one quite unlike the modes so far. A mode
is a set of rules picked on the New Game screen and carried on `GameState` (and each `PlayerState`)
for the whole game.

Modes so far:

| Mode        | `id`        | What's different from Standard                                                                                      |
|-------------|-------------|---------------------------------------------------------------------------------------------------------------------|
| `STANDARD`  | `standard`  | Nothing - the official rules. The default.                                                                          |
| `TRICOLOUR` | `tricolour` | Dice also roll red/yellow/blue; four colour boxes join the card                                                     |
| `QUICKFIRE` | `quickfire` | One roll per turn, made automatically; a fixed 10s timer replaces the Turn Timer pick; a timeout scores the lowest open box |

History: `DESIGN.md` Phase 14 (Tricolour, and how modes were first modelled) and Phase 20
(Quickfire).

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
`turnTimerSeconds`, `autoRollAtTurnStart`) and read that. The only checks for a named mode are
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
| `diceCount`, `dieValues`, `dieColours`   | `GameEngine.newGame`/`rollDice`/`cycleDieValue`, `DiceTray` (incl. the rolling scramble), `AiTurnPlayer`    |
| `rollsPerTurn`                           | `GameEngine` (turn reset) and the `GameState` default set `rollsRemaining`, which the cup's `xN` badge and the AI loop read; `GameViewModel`'s `fullRolls`/`rollsRemainingAfter*` helpers; Impatient/Naturally Gifted's guard |
| `categories`                             | `PlayerState` (card, totals, completeness), `ScoreCalculator`, `ScoreGrid`, `GameStateJson`, `AiTurnPlayer` baselines, "How Do You Play This Game?" |
| `upperBonus*`, `fiveOfAKindBonusAmount`  | `PlayerState` totals, `ScoreCalculator`, the 5x tile's bonus preview                                       |
| `maxPossibleScore`                       | `HIGHEST_POSSIBLE_SCORE` (Leaderboard/Statistics column width), `GameModeTest`                              |
| `usesPlayerDiceStyle` (from `dieColours`)| `DiceTray` (style vs `ColouredDie`), Fresh Coat Of Paint                                                     |
| `turnTimerSeconds`                       | `GameState.turnSeconds` → `GameViewModel.syncTurnTimer`; the setup screen disables the Turn Timer row      |
| `timeoutPick`                            | `ScoreCalculator.timeoutCategory` ← `GameViewModel.autoScoreOnTimeout`                                      |
| `autoRollAtTurnStart`                    | `GameState.awaitsAutoRoll` → `GameScreen`'s auto-tap `LaunchedEffect`                                       |

## First, work out what kind of mode it is

Most of the work depends on which of these the mode touches. A mode can touch several.

- **Only numbers that are already fields** (more rolls, a different bonus): the entry, rules page,
  achievements and tests. Everything else follows.
- **New scoring** (new boxes): see [New categories](#new-categories).
- **New dice** (a new property, faces, or dice count): see [New dice](#new-dice).
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
- `maxPossibleScore` is declared, with its derivation as a doc comment on the entry. `GameModeTest`
  plays a perfect game (every turn a 5x) through the real engine for every mode and fails if the
  number is wrong. If scoring is unchanged, it's the same as the mode it copies. A rule that changes
  what a "perfect game" is (no 5x possible, say) may need that test's `perfectGame` taught about it.
- Doc comments on the entry and fields state the rule. Don't write down *why* the user wanted a rule
  unless they said why - a guessed rationale in a doc reads as fact later.

### Rules page (`ui/common/RulesDialog.kt`)

Add a `RulesPage` titled `"Mode: <Name>"` to the end of `RULES_PAGES`. Describe it relative to
Standard ("A custom mode extending the *Standard* game mode...") and say what stays the same. Use
`parseInlineMarkup`'s markers (`*italic*`, `**bold**`, `` `mono` ``) like the other pages. The pager
and page dots size themselves from the list. Update the page each time the mode's rules change -
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
- AI: Easy and Hard need nothing (Hard scores every outcome with `DiceScoring`). Medium's rules of
  thumb need to know how to chase the new box, and `CATEGORY_RESTRICTIVENESS` needs it ranked.
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
- AI Hard enumerates every distinct roll. More faces multiply its work: Tricolour's 18 faces are
  26,334 distinct rolls of five dice (kept exact by enumerating unordered outcomes). Check a new
  face count stays fast.

### Turn flow: rolls, timers, undo

How a roll happens today, in every mode:

- **Every roll lands through one function, `GameViewModel.performRoll`**, human or CPU.
- **A human's roll always starts with a cup tap** - `GameScreen`'s `onCupTap`. A finger, a phone
  shake and Quickfire's automatic roll all call it, so they all shake for `CUP_SHAKE_MILLIS`, play
  the same sound and haptics, then call `rollDice()`, where player 1's achievement tracking runs.
- **A CPU's roll is driven by `GameViewModel.maybeStartAiTurn`**, which shakes for the same
  `CUP_SHAKE_MILLIS` (through `aiRolling`) and then calls `performRoll`. It stays in the view model,
  not the screen, so CPU turns keep playing when the screen isn't showing.

Rules that came out of Quickfire:

- **Don't add a second kind of roll.** An automatic or special roll should go through the same tap
  (for a human) or the same `performRoll` (for the CPU). A separate view-model roll with its own
  delay was built for Quickfire and replaced on review, because it made auto-rolls look and behave
  slightly differently from real ones.
- **Never change the game state under the AI loop.** `maybeStartAiTurn` plays from its own copy of
  the state (`current`) across delays. Anything that rolls, holds or scores on a CPU's turn from
  outside that loop puts the game out of step with it. That's why `awaitsAutoRoll` is false on a
  CPU's turn - the CPU's loop already starts with a roll.
- **Screen-driven rules need the screen.** Quickfire's auto-roll only happens while `GameScreen` is
  composed. That's acceptable for a human turn: if the screen isn't showing, the turn timer still
  ends it (a timeout rolls first if needed). A rule that must happen with no screen belongs in the
  view model.
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

- **Disable the option the mode overrides; don't hide it.** Quickfire disables the Turn Timer row
  (`enabled = setup.gameMode.turnTimerSeconds == null`, through `SegmentedChoiceRow`'s `enabled`).
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
| Roll count          | Impatient, Naturally Gifted, Almost Famous, Natural 5x, The Dice Hate Me, Déjà Vu, Loaded Dice, Time To Let It Go, Pointless Roll | Does "only one roll used" or "every roll used" become automatic or impossible? |
| First roll          | House Call, Straight Away, Straight Out Of The Cup, I Can Count!                             | Is every roll now a first roll, or none?                                    |
| Holds               | A Cunning Strategy, Decisions Decisions, Time Wasting, Commitment Issues                     | Can dice still be held?                                                     |
| Timer               | Out Of Time, Beat The Clock, Luck Of The Draw                                                | Is there always, or never, a timer?                                         |
| Card contents       | How Do You Play This Game?, Spotless, Bonus Round, Upper/Lower Class, Exact Change, the 5x ones | Is each box still on the card, and do totals still mean the same?            |
| Score thresholds    | Solid Round ... Dice Deity, Cold Dice, Low Rolls, Ton!, Nice, Rock Bottom, the score bands   | Is a threshold now trivial or impossible for this ceiling?                  |
| Dice look           | Fresh Coat Of Paint, Luck of the Irish                                                       | Does the mode draw the player's dice style?                                  |

The rolls-remaining helpers in `GameViewModel` (`rollsRemainingAfterFirst`/`AfterSecond`) are
`rollsPerTurn - 1`/`- 2`, so with fewer than three rolls they go to 0 or below. Checks that compare
against them may start matching the wrong roll, or never match - read each one.

### Quickfire (1 roll per turn, 10s timer)

Holding still works after the only roll, so the hold-only achievements can still be earned; only the
ones that need a *reroll* can't.

**Guarded - would be free in Quickfire, so they can't be earned there:**

| Achievement                           | Why it would be free                                                              | Guard                                                              |
|---------------------------------------|-----------------------------------------------------------------------------------|--------------------------------------------------------------------|
| Impatient (`IMPATIENT`)               | "Never rolled more than once in any turn" - every turn                            | `AchievementEngine`: `state.gameMode.rollsPerTurn > 1`             |
| Naturally Gifted (`NATURALLY_GIFTED`) | Same, plus a win                                                                  | Same condition                                                     |
| Almost Famous (`ALMOST_FAMOUS`)       | Any first-roll 4x "held to the last roll" with no 5x - the first roll *is* the last | `GameViewModel.checkPreCommitAchievements`: `state.fullRolls > 1` |

Almost Famous's check is "a first-roll 4x, every roll spent, never became a 5x". After one roll,
"every roll spent" is already true and nothing was rerolled to break the hold, so without the guard
any first-roll 4x unlocks it. ``a first-roll four of a kind in Quickfire does not unlock Almost
Famous`` in `GameAchievementsWiringTest` fails without the guard.

**Can't be earned in Quickfire - no guard needed:**

| Achievement                                    | Needs                                                            |
|------------------------------------------------|------------------------------------------------------------------|
| Natural 5x (`NATURAL_5X`)                      | A 5x on the 2nd or 3rd roll                                      |
| The Dice Hate Me (`DICE_HATE_ME`)              | A 2nd and a 3rd roll                                             |
| Déjà Vu (`DEJA_VU`)                            | Two rolls in the same turn                                       |
| Are These Loaded Dice? (`LOADED_DICE`)         | Two rerolls                                                      |
| Time To Let It Go (`TIME_TO_LET_IT_GO`)        | A die held after the 1st and 2nd rolls                           |
| What Was The Point Of That? (`POINTLESS_ROLL`) | Rolling with all five held - the only roll comes before any hold |

**Still earnable, and worth knowing:**

- The first-roll feats (House Call, Straight Away, Straight Out Of The Cup, I Can Count!) - every
  Quickfire roll is a first roll.
- The hold-only ones: A Cunning Strategy, Decisions, Decisions, Time Wasting, Commitment Issues.
- No More Rolls - tapping the empty cup works as usual.
- Out Of Time - more likely than anywhere else. The timed-out turn is scored in the lowest-scoring
  open box, not the first open one.
- Luck Of The Draw - a win with 3 or fewer boxes scored yourself. Any timed game can earn it, but
  Quickfire always has a timer, and it times out onto the lowest-scoring box, which makes the win
  harder.
- Well Rolled (10,000 dice) - slower, at most 5 dice a turn.

### Tricolour (coloured dice, four colour boxes)

Nothing guarded, nothing blocked. Déjà Vu and Are These Loaded Dice? compare number *and* colour, and
Fresh Coat Of Paint ignores the dice style (Tricolour draws its own coloured dice). How Do You Play
This Game? needs the colour boxes zeroed too. See `DESIGN.md` Phase 14.

## Tests

Where each kind of change is tested:

| Change                         | Test                                                                                              |
|--------------------------------|---------------------------------------------------------------------------------------------------|
| What the mode is, its ceiling  | `GameModeTest` (pin the fields that differ; the perfect-game test covers every entry)             |
| Scoring and timeout choice     | `DiceScoringTest`, `ScoreCalculatorTest`                                                          |
| Engine turn rules              | `GameEngineTest`                                                                                  |
| AI                             | `AiTurnPlayerTest`                                                                                |
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
  `IOS_SUPPORT.md`).

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
