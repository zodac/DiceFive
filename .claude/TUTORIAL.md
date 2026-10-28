# Tutorials

How the guided Standard tutorial is built, the rules its script follows, and how to add one for another mode. The
player-facing summary is in `DESIGN.md`, "The tutorial"; the New Game lock it sits beside is in `DESIGN.md`, "Game
setup".

## What it is

A solo game of Standard where every roll's dice and every move are fixed, so it plays the same each time (13 turns,
ending on 382). A coach card in place of the player tabs says what to do; only that move works; the control for it is
outlined in pulsing gold. Nothing in it is kept.

- **Entry points.** Rules → Gameplay → How to Play (a Start Tutorial button), and New Game's "New to the game? Play the
    tutorial" (shown only until a first game is finished). Both open the `tutorial` route.
- **Exit.** Back always asks "Leave the tutorial?", whatever the confirm-before-leaving setting. Its Game Over offers
    Done (back where it was started) and New Game.

## The pieces

| Where | What |
| --- | --- |
| `game/Tutorial.kt` | Pure: `TutorialScript` (mode + steps, `turns`, `turnOf`, `turnStarts`, `replay`), `TutorialStep`, `TutorialMessage`, `TutorialDice`. `TutorialScript.STANDARD` is the script |
| `ui/game/GameViewModel.kt` | `tutorial: TutorialScript?` (null in real games), `tutorialFactory`, `startTutorial`, `tutorialStep`/`tutorialTurn`, `tutorialTotalsOpened`, `jumpToTutorialTurn` (the progress itself is `TutorialProgress`'s), and the gates (below) |
| `ui/game/TutorialGuide.kt` | `TutorialGuide` (the shown step, the pulse, totals callback, whether the cup is rolling), `LocalTutorialGuide`, `Modifier.tutorialOutline`, `rememberTutorialPulse` |
| `ui/game/CoachCard.kt` | `CoachCard` and `coachCardHeight`; the `TutorialMessage` → string map |
| `ui/game/GameScreen.kt` | Provides the guide; the card replaces `PlayerHeaderBar`; the cup and tray are gated on the step; tutorial Game Over |
| `ScoreGrid.kt`, `DiceTray.kt`, `DiceCupPanel.kt`, `TotalsButton.kt`, `UndoButton.kt` | Each reads `LocalTutorialGuide`: outline when it's the step's control, otherwise no tap and no TalkBack action |
| `ui/game/LeaveGameConfirmation.kt` | `request(tutorial = true)` words the dialog as the tutorial's |
| `ui/game/GameOverScreen.kt` | `tutorialFinished`: the line under the scores, Done / New Game |
| `ui/rules/RulesScreen.kt` | `RulesTutorialStart` block on How to Play, `onStartTutorial` (via `LocalStartTutorial`) |
| `navigation/DiceFiveNavHost.kt` | The `tutorial` route: its own `GameViewModel` (never the play graph's), started on first composition |

`LocalTutorialGuide` is a `staticCompositionLocalOf` that is null in every real game, so the board pays one null check
per piece and nothing else.

## Steps

| Step | The player | Moves on |
| --- | --- | --- |
| `Roll(message, dice)` | taps the cup; the five dice land as `dice` (held ones already show their value) | as the roll lands |
| `Hold(message, dice)` | holds each die at those positions, any order; nothing else can be held, nothing released | once all are held |
| `Score(message, category)` | taps that box, the only one that takes a tap | at once |
| `Undo(message)` | taps Undo, which only this step allows (solo games only) | at once |
| `OpenTotals(message)` | taps the totals button, which only these steps allow | at once |

There are **no words-only steps**: a card that explains (the welcome, the totals, the bonus) is a `Roll`'s message and
ends "Tap the cup to roll", so one tap both carries on and rolls. A turn ends at a `Score` that isn't followed by
`Undo`; `turnOf`/`turns` count those.

**Timing.** What the player may do moves on the moment a roll lands (`currentTutorialStep`, the script index). What
they see - the card, the outlines - moves on once the dice have settled (`tutorialStep`, published after
`pacing.diceTossMillis`, 0 under reduced motion). The cup's own outline goes as soon as it's tapped.

## Scripting rules (from review - follow them)

- **All three rolls.** Every turn reaches its target only on the last roll, except one turn whose point is scoring on
    the first roll (Standard: Fours). `TutorialScriptTest` checks the count per turn.
- **Scatter the dice.** The dice to hold land in mixed positions, not 0, 1, 2.
- **Only hold what helps.** Never hold a die that doesn't serve the target (no 6 for a 2-3-5 small straight), and say
    why the others are rolled.
- **No accidental better options.** A first roll mustn't already give, or nearly give, something better than the lesson
    (turn 3 once had a 1 that could have been kept for the run). Check every roll for unplanned straights, 3x/4x, full
    houses.
- **Only the lesson's box looks live.** The other dice in a turn should be numbers whose boxes are already filled, so
    nothing else on the card tempts (turn 10's "Fives is still open" had a 6 and a 1 beside it while Sixes and Ones were
    open too). Where another box would also take the hand, say why the lesson's is better.
- **Logical order.** Never chase a box already filled (the missed-straight Chance turn comes before the Large Straight
    is scored). A zero needs its safety nets already used.
- **Solo wording.** No "your turn": it's always theirs. Use "A new turn."
- **Accurate rules text.** The upper bonus is 63, i.e. three of each number *on average*; give the points still needed
    and how to make them (21: three 1s and three 6s).
- **A mistake, then Undo.** The Undo lesson scores the turn in a plausible wrong box, then "Actually, there's a better
    box…", and says Undo is solo-only.
- **Keep the card short.** Its height is the longest message's, so trim the longest few.

## The coach card

- In place of the player tabs: "Coach" and "Turn N of 13" on one line, the words under them.
- **Fixed height**: `coachCardHeight` measures every message at the standard font size (`fontScale = 1`), so nothing
    moves between steps. At a larger font the words scroll inside the card, with the app's `VerticalScrollbar` (drawn only
    when there's something to scroll).
- **TalkBack**: the name and words are one node and a polite live region (read out as each step arrives); the turn is a
    node of its own so it isn't repeated.
- Markup as the Rules pages': `backticks` for a box's name (gold), **bold** for a number.

## Back and forward

The card's edges are two full-height strips, 48dp wide: back on the start side, forward on the end side (mirrored in
Arabic). They move between **turns**: back goes to the start of this turn, or the previous one if already there; forward
to the start of the next. Back is off at the very start, forward on the last turn.

A jump rebuilds the game with `TutorialScript.replay(start, turnStarts[n])` - every step before it played as written,
an Undo taking its score back - drops any roll still shaking or landing, and shows that turn's first card. The totals
pop-up closes. Nothing is written (the same gates as play). `TutorialScriptTest` checks replaying every step equals
playing through, and that each turn start has that many scores and three rolls left; `TutorialGameTest` jumps back and
forward and plays on to 382.

## Outlines and gates

- `Modifier.tutorialOutline(pulse)` draws just **outside** its control (clear of the cup's roll count). Put it before any
    `clip` in the chain, or the clip cuts it off (Undo and totals had this bug).
- The pulse is read while drawing, so it redraws without recomposing; steady under reduced motion.
- The totals pop-up isn't focusable in the tutorial (`AppTooltip(focusable = false)`), so the tap on the cup that
    carries on reaches the cup; the roll closes the pop-up if that tap didn't.

## Nothing is kept

The tutorial's `GameViewModel` uses the real repositories (for the player's name and table) but writes nothing:

- `persistInProgressGame`/`saveTurnTimerProgress`: no saved game (Continue stays on the player's own).
- `unlockAchievements`, `checkGameStartAchievements`, `checkInProgressAchievements`, `finishGame`: no achievements,
    counters, banners, scores or first-game unlock.
- No golden egg, superuser cheat, turn timer, or undo but at an `Undo` step; `confirmBeforeLeavingGame` is false (the
    tutorial asks anyway, with its own wording).

`TutorialGameTest` plays the whole script against counting stores and checks every one; each guard was removed in turn to
see the test fail. Keep that true for any new gate.

## Strings

- One string per `TutorialMessage`: `game_tutorial_<message lowercased>`, in `values`, `values-es`, `values-ar`, mapped
    in `CoachCard.kt`'s `resource`. Plus `game_tutorial_coach_name`, `game_tutorial_turn`, the leave dialog's
    `game_leave_tutorial_*`, Game Over's `gameover_tutorial_*`, and Rules' `rules_how_to_play_tutorial` /
    `rules_tutorial_start`.
- Banned words apply: 3x/4x/5x, never "of a kind".
- Arabic: digits Arabic-Indic, the marks (3x/4x/5x) Latin; a button is named as it reads («تراجع»).
- The scores a message quotes must match the script: `TutorialScriptTest` asserts each turn's score.

## Tests

- `TutorialScriptTest` (pure): replays the script through `GameEngine` (Undo restores the state before the last score):
    each turn's score, the final total, upper section and bonus, rolls per turn, `turnOf`.
- `TutorialGameTest` (view model): plays it through; only the step's move is taken; the shown step waits for the dice;
    Undo only at its step; nothing kept; the saved name.
- `TutorialScreenTest` (Robolectric): the card's spoken text word for word and live region; which controls take a tap
    at each step; Undo; one tap with the totals open; the leave dialog; How to Play's button.
- **Harness traps.** Give each screen its own `LeaveGameConfirmation` (the default is a shared global, and a dialog left
    pending holds the next test's game). Call `setForeground(true)`, and step both clocks (`compose.mainClock` and
    `ShadowLooper.idleMainLooper`). Robolectric doesn't route taps through pop-up windows as a phone does, so the totals
    pass-through needs checking on a device.
- Render every changed card at 100%, 130% and 200% font and on a small phone before an APK (`render` skill).

## Adding a tutorial for another mode

1. **Write the script** as a new `TutorialScript` (e.g. `TutorialScript.TRICOLOUR`) beside `STANDARD`, with its own
   `TutorialMessage` entries. Follow the scripting rules; plan turn by turn on paper first (dice per roll, holds, box,
   score), then let `TutorialScriptTest` (one per script) prove it.
2. **Check what the mode draws at random.** `TutorialDice` only feeds die *values* (six-sided, no colours, no Unlucky
   Dice). Before a mode can be scripted:
   - Tricolour draws a colour per die after its value - extend `TutorialDice` to feed both.
   - Quickfire switches boxes off and Hit List draws its targets in `GameEngine.newGame` (`random`) - give
     `startTutorial` a fixed `Random` or the script's own choice.
   - 7 Dice Stud scores only held dice (`HandCompletion`); a `Score` step may need its hand held first.
   - D20's dice run 1-20 (`dieValues`), and its card has two halves and a bonus each.
   Add a test per mode that every roll's dice are faces of that mode.
3. **Mode-specific UI**: anything the mode adds to the board (colour boxes, targets, slots) needs the same treatment as
   the boxes and dice here: an outline when it's the step's control, no tap or TalkBack action otherwise.
4. **Entry**: a Start Tutorial button on the mode's own Rules page (a `RulesTutorialStart` block), and a route that
   takes the script (today the `tutorial` route hard-codes `STANDARD`; pass the mode's id as an argument).
5. **Strings** in all three languages, and the scores they quote checked by the script test.
6. **Tests**: the three classes above for the new script, and the "nothing kept" run.
7. **Docs**: this file, `DESIGN.md`'s tutorial section, and `GAME_MODES.md`'s checklist if a mode gains a tutorial.
