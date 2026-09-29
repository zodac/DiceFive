# Game modes

How game modes are built, and the checklist for adding a new one. A mode is a set of rules picked on
the New Game screen and carried on `GameState` (and each `PlayerState`) for the whole game.

Modes so far:

| Mode        | `id`        | What's different from Standard                                              |
|-------------|-------------|-----------------------------------------------------------------------------|
| `STANDARD`  | `standard`  | Nothing - the official rules. The default.                                  |
| `TRICOLOUR` | `tricolour` | Dice also roll red/yellow/blue; four colour boxes join the card             |
| `QUICKFIRE` | `quickfire` | One roll per turn; a fixed 10s turn timer that replaces the Turn Timer pick |

See `DESIGN.md` Phase 14 (Tricolour, and how modes were first modelled) and Phase 20 (Quickfire).

## The one rule: a mode is data

**Everything that can differ between modes is a field on `model/GameMode.kt`**, even where every
mode agrees on it: dice count, rolls per turn, die faces, die colours, the scorecard's categories,
the upper bonus, the 5x bonus chip, the max possible score, and a fixed turn timer
(`turnTimerSeconds`). The engine, AI, achievements, persistence and board read the rules from there,
so they already handle a new mode. A new mode should be:

1. a new `GameMode` entry, plus
2. any rule that's genuinely new (a new field on `GameMode`, or a new `ScoreCategory`), plus
3. the four pieces of player-facing content below.

Nothing else should need to learn the mode exists. If you find a constant like `3` rolls or `5`
dice hard-coded somewhere, move it onto the mode rather than adding a `when (mode)`.

**`id` is a storage key** (settings, saved in-progress games). Never change one; rename
`displayName` instead.

## Checklist

### 1. The mode itself (`model/GameMode.kt`)

- Add the entry with every field set explicitly (copy `STANDARD` and change what differs).
- `description` is the one line shown under the mode's name on the New Game screen. Say what's
  *different*.
- `maxPossibleScore` is declared, with its derivation written as a doc comment on the entry.
  `GameModeTest` plays a perfect game through the real engine for every mode and fails if the number
  is wrong. If scoring is unchanged, it's the same as the mode it copies.
- A new rule that doesn't fit an existing field gets a new field, with a default that keeps the other
  modes as they were (as `turnTimerSeconds: Int? = null` did). Then make the one place that applies
  it read the field. Quickfire's timer: `GameState.turnSeconds` resolves "mode's timer, else the
  setup pick", and `GameViewModel.syncTurnTimer` reads that.

What's already mode-driven and needs no change for a mode that only changes these fields:
`GameEngine` (dice, rolls, faces, colours), `ScoreCalculator`/`PlayerState` (card, bonuses),
`AiTurnPlayer` (all three difficulties, the baselines are computed per mode, lazily), `ScoreGrid`
(layout from `categories`), `DiceTray` (coloured dice vs the player's dice style), the dice cup's
roll count, `GameStateJson`/`SettingsRepository` (by `id`), and `TieBreak`.

### 2. Rules page (`ui/common/RulesDialog.kt`)

Add a `RulesPage` titled `"Mode: <Name>"` to the end of `RULES_PAGES`. Describe it relative to
Standard ("A custom mode extending the *Standard* game mode...") and say what stays the same. Use
`parseInlineMarkup`'s markers (`*italic*`, `**bold**`, `` `mono` ``) like the other pages. The page
indicator and pager size themselves from the list.

### 3. Achievements (`AchievementCategory.GAME_MODES`)

- Add the mode's achievements to `model/Achievement.kt`, in the Game Modes block, after the previous
  mode's. So far every mode has two: a win (multiplayer, like every win) and something only that
  mode can do.
- Give each an `id` (stable, never changed), a title, and a description that names the mode in
  quotes (`"Win a game of 'Quickfire' mode"`).
- Judge them in `game/AchievementEngine.kt`: `earnedDuringPlay` if a scorecard mid-game already
  settles it (Tricolour Me Impressed), `earnedBy` if it needs the finished game. If it needs
  something the final `GameState` can't show, add a field to `GameAchievementContext` and track it
  in `GameViewModel`: a `private var`, reset in `resetAchievementTracking`, passed in
  `recordEndOfGameAchievements`. Quickfire's `playerOneTimedOut` is an example.
- Give each an icon in `ui/achievements/AchievementIcons.kt`. The `when` there is exhaustive, so a
  missing one won't compile. Pick a glyph no other achievement uses.
- `NON_STANDARD_MODE` and `I_DID_IT_MY_WAY` already fire for any non-Standard mode. Nothing to do.
- **Audit every existing achievement against the new rules** - see
  [Existing achievements a mode affects](#existing-achievements-a-mode-affects). A mode can make one
  impossible (fine, leave it) or make one free (not fine: add a guard).
- Update `AchievementEngineTest`'s "Game Modes achievements are ordered" list.

### 4. New Game screen (`ui/setup/GameSetupScreen.kt`)

The Game Mode card lists `GameMode.entries` as radio rows. A new mode appears automatically with its
`description`. Only a mode that changes *another* setup option needs work here. Quickfire disables
the Turn Timer row (`enabled = setup.gameMode.turnTimerSeconds == null`, through
`SegmentedChoiceRow`'s `enabled`), because its own timer replaces that choice. The overridden pick is
still kept and saved, so it comes back when another mode is chosen. `GameViewModel.startGame` starts
the game with `TurnTimer.NONE` in that case, so the saved game doesn't carry a pick that isn't in
play.

Don't add new text or rows to the form for a mode. Each mode's `description` and rules page explain
it, and an added element must not move or re-space the existing ones.

### 5. Tests

- `GameModeTest`: a test pinning what the mode is (which fields differ from the mode it extends),
  and its ceiling in the max-score test. The perfect-game test covers every entry automatically.
- `AchievementEngineTest`: each new achievement earned, and not earned in a loss, a solo game or
  another mode. Also a test for any guard added in the audit.
- `GameViewModelTest` / `GameAchievementsWiringTest`: anything the view model does differently
  (Quickfire: the 10s countdown, one roll, the form's timer ignored, Almost Famous not firing).
- **No commas or parentheses in `commonTest` test names** - Kotlin/Native rejects them (see
  `IOS_SUPPORT.md`).

### 6. Docs and commit

- This file: the mode table at the top, and the mode's column in the audit below.
- `DESIGN.md`: the "Game modes" bullet under Decisions, the setup rules, the max-score line, the
  achievement count, and a phase entry for the mode.
- Commit as `[Game Mode] ...`.

## Existing achievements a mode affects

Many achievements assume three rolls, holding between rolls, or no timer. For each new mode, go
through this list. For every achievement the mode changes, decide:

- **Can't be earned in this mode** - leave it. It just never unlocks in that mode, and can still be
  earned in the others. No code needed.
- **Earned for free in this mode** - the mode makes a feat automatic. **Add a guard** so the mode
  can't unlock it, and a test for the guard.

Watch for the second kind in checks that ask "were all rolls spent?" or "was only one roll used?".
With one roll per turn, both are always true.

### Quickfire (1 roll per turn, 10s timer)

Holding dice still works after the only roll (`GameScreen.canHold` doesn't check rolls left). So the
hold-only achievements can still be earned, and only the ones that need a *reroll* can't.

**Guarded - would be free in Quickfire, so they can't be earned there:**

| Achievement                         | Why it would be free                                   | Guard                                                                     |
|-------------------------------------|--------------------------------------------------------|---------------------------------------------------------------------------|
| Impatient (`IMPATIENT`)             | "Never rolled more than once in any turn" - every turn | `AchievementEngine`: `state.gameMode.rollsPerTurn > 1`                    |
| Naturally Gifted (`NATURALLY_GIFTED`) | Same, plus a win                                     | Same condition                                                            |
| Almost Famous (`ALMOST_FAMOUS`)     | Any first-roll 4x "held to the last roll" with no 5x - the first roll *is* the last | `GameViewModel.checkPreCommitAchievements`: `state.fullRolls > 1` |

The Almost Famous guard is load-bearing. The check is "a first-roll 4x, every roll spent, never
became a 5x". After one roll, "every roll spent" is already true and nothing was rerolled to break
the hold, so without the guard any first-roll 4x unlocks it. The test
``a first-roll four of a kind in Quickfire does not unlock Almost Famous`` in
`GameAchievementsWiringTest` fails without the guard (checked by removing it).

**Can't be earned in Quickfire - no guard needed, they just never fire:**

| Achievement                                 | Needs                                             |
|---------------------------------------------|---------------------------------------------------|
| Natural 5x (`NATURAL_5X`)                   | A 5x on the 2nd or 3rd roll                        |
| The Dice Hate Me (`DICE_HATE_ME`)           | A 2nd and a 3rd roll                               |
| Déjà Vu (`DEJA_VU`)                         | Two rolls in the same turn                         |
| Are These Loaded Dice? (`LOADED_DICE`)      | Two rerolls                                        |
| Time To Let It Go (`TIME_TO_LET_IT_GO`)     | A die held after the 1st and 2nd rolls            |
| What Was The Point Of That? (`POINTLESS_ROLL`) | Rolling with all five held - the only roll comes before any hold |

**Still earnable, and worth knowing:**

- The first-roll feats (House Call, Straight Away, Straight Out Of The Cup, I Can Count!) - every
  Quickfire roll is a first roll.
- The hold-only ones: A Cunning Strategy, Decisions, Decisions, Time Wasting, Commitment Issues.
- No More Rolls - tapping the empty cup works as usual.
- Out Of Time - more likely than anywhere else.
- Well Rolled (10,000 dice) - slower, at most 5 dice a turn.

### Tricolour (coloured dice, four colour boxes)

Nothing guarded, nothing blocked. Déjà Vu and Are These Loaded Dice? compare number *and* colour, and
Fresh Coat Of Paint ignores the dice style (Tricolour draws its own coloured dice). See `DESIGN.md`
Phase 14.
