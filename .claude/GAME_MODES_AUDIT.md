# Game modes: the achievement audit

Which existing achievements each mode blocks or guards, and how to audit a new one. Mode mechanics are in
`GAME_MODES.md`.

**Modifiers block everything.** A game with any modifier on (the turn timer included) earns only the modifiers' own
achievements (`Achievement.earnableWithModifiers`: Rainy Day Fund, Bad Omen, and Everything Everywhere All At Once),
moves no counter, and adds nothing to Statistics, career points, or Grand Tour. The older per-modifier guards (roll
modifiers, Extended Scores, Unlucky Dice, the timeout guards) are still in the engine and its tests, but no longer
decide anything. A new modifier needs no audit of its own; a new modifier achievement sets `earnableWithModifiers`.

## How to audit

Many achievements assume three rolls, holding between rolls, no timer, or the Standard card. For each achievement a new
mode changes, decide:

- **Can't be earned in this mode.** Add the mode to its `Achievement.impossibleIn`. That alone gives no tip on the
    Achievements screen: an impossibility explains itself. The engine keeps it out of those modes too.
- **Earned for free in this mode.** **Add a guard** and a test for it: a row in `AchievementEngineTest`'s guarded
    table (each mode and modifier's cards, and the feats each must not earn), and the mode in its `notEarnableIn` (or
    `mode`, for one kept to a single mode). A mode's row (its name first) fails without that. A deliberate limit gets
    the Achievements screen's tip, naming the modes left after both these and `impossibleIn`. **Prove the test** by
    removing that one guard and watching it fail. Strip guards one at a time; stripping them all at once proves nothing
    about each. A guard against a modifier stays only in code, since the tip names modes alone.
- **Easier but not free.** Don't guard it. **Report the "easier" list to the user** rather than deciding alone.
- **Count the thing, not its proxy.** Bonus chips equal "2nd/3rd 5x" only while the 5x box has one slot;
    `fiveOfAKindCount` counts the 5x themselves.

| Family           | Achievements                                                                                                                                                                      | Ask                                                                       |
| ---------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| Roll count       | Impatient, Almost Famous, Natural 5x, The Dice Hate Me, Déjà Vu, Time To Let It Go, What Was The Point Of That?, Green Fingers, Probability? Never Heard Of Her | Does "one roll used" or "every roll used" become automatic or impossible? |
| First roll       | House Call, Straight Away                                                                                                                                                         | Is every roll now a first roll, or none?                                  |
| Holds            | A Cunning Strategy, Decisions Decisions, Time Wasting, Commitment Issues, Hands Off! (Standard only)                                                                              | Can dice still be held?                                                   |
| Timer            | The timeout guards on low or zeroed cards (`GAME_MODES.md`)                                                                                                                       | Always or never a timer?                                                  |
| First turn       | Slow Start                                                                                                                                                                        | Can a first turn still be scored zero, by choice?                         |
| Card contents    | How Do You Play This Game?, Spotless, Bonus Round, Upper/Lower Class, Exact Change, the 5x ones                                                                                   | Is each box still there, and do totals mean the same?                     |
| Score thresholds | Solid Round ... Dice Deity (Standard and Tricolour only), Cold Dice, Low Rolls, Ton!, Nice, Rock Bottom, the score bands                                                          | Trivial or impossible at this ceiling?                                    |
| Dice look        | Fresh Coat Of Paint, Luck Of The Irish                                                                                                                                            | Does the mode draw the player's dice style?                               |

The roll helpers `rollsRemainingAfterFirst`/`AfterSecond` are `rollsPerTurn - 1`/`- 2`. With fewer than three rolls
they reach 0 or below, so read each comparison against them.

**Guard predicates in use:**

- `PlayerState.hasFullCard` (no box off);
- `scoresRolledHand` (`!scoresHeldDiceOnly`);
- `scoresPerCategory == 1`;
- `countsOnLeaderboard`;
- `GameMode.hasHitList`;
- `hasStandardCard` (ONES on the card);
- `hasComparableTotals` (every box on the card, and boxes, not targets);
- `rollsModified`;
- `GameState.unluckyDice`.

## Quickfire (5x and six random boxes off)

The draw is always three upper and three lower boxes. A short game with small totals.

**Guarded** (each test fails without its guard):

| Achievement                       | Why                                                            |
|-----------------------------------|----------------------------------------------------------------|
| Spotless                          | Six turns without a zero, not thirteen                         |
| Cold Dice, Low Rolls, Rock Bottom | The ceiling is 210, so a low total is normal                   |
| How Do You Play This Game?        | Fewer boxes to zero                                            |
| Solid Round ... Dice Deity        | A total over a different card measures nothing                 |
| Upper Class, Lower Class          | Section totals over different boxes                            |
| Bonus Round                       | The threshold shrinks with the boxes (18 with only 1s-3s left) |

**Can't be earned**:

- every 5x achievement that needs the box (5x!, Encore, Hat Trick, Scratched, Twice in a Lifetime);
- Natural 5x (`fiveOfAKindScorable` is never true);
- the score bands and New Personal Best (off the board);
- Exact Change and Straight Talker, unless the draw keeps those boxes.

**Still earnable** (report these):

- Impatient;
- Green Fingers (18 rolls rather than 39);
- Nice and Ton! (likelier);
- Zero To Hero (harder);
- Landslide and the wins.

The mode's own: Quick On The Draw (a multiplayer win) and Six Of The Best (150+, not with extra rolls or Extended
Scores).

## Modifier achievements

- **Everything Everywhere All At Once** needs every modifier the mode offers: D20 and Hit List have no Extended
    Scores, so the rest are all of them there.
- **Rainy Day Fund** (a turn started with 100+ rolls under Stored Rolls) needs about 8+ rolls a turn in a 13-turn
    mode (9 a turn with one roll used each turn reaches 105 on the last turn). Third Wind's 39 turns make it easier;
    Quickfire's six turns make it impossible.
- **Bad Omen** (a Game Modes achievement, unlocking the Devil dice: every die chained at once) needs Unlucky Dice's
    cap at 5 and nothing held. Impossible in Stud: seven
    dice, at most five chained.
- **Grand Tour** counts a finished game in each mode, with no modifier on. A new mode raises its target
    (`GameMode.entries.size`); anyone already holding it keeps it.

## One roll a turn (Number of Rolls at 1)

Impatient and Almost Famous are guarded, though any modifier now blocks them anyway.

## Stud (seven rolled, five held)

A roll isn't a hand, so:

- achievements judged at scoring read `scoringDice`: Wasted Fortune, Size Isn't Everything, Empty/Fuller House, Time To Let
    It Go, Twice in a Lifetime;
- achievements judged on the roll as it lands are guarded.

**Guarded** (`!scoresHeldDiceOnly` / `scoresRolledHand`, plus `singleScoreCard` and Zero To Hero's own condition; Twice
in a Lifetime is guarded in `AchievementTracker.onCommit`):

| Achievement                                                 | Why                                                                            |
|-------------------------------------------------------------|--------------------------------------------------------------------------------|
| House Call, Straight Away                                   | Seven dice hold these far more often, and nothing is held yet                  |
| 5x!, Encore, Hat Trick, Twice in a Lifetime, Dice Whisperer | The best five of seven makes 5x easy; Stud's 5x don't count toward `SCORED_5X` |
| Straight Talker, Taking A Chance, Six Appeal                | Routine with the best five of seven                                            |
| Bonus Round, Upper/Lower Class, Solid Round ... Dice Deity  | Totals run well above Standard's                                               |
| Spotless, Zero To Hero                                      | Zeros are rare                                                                 |
| New Personal Best                                           | Off the Leaderboard                                                            |

**Can't be earned**:

- Product Placement (seven dice never equal a five-dice sequence);
- What Was The Point Of That? (two dice always roll).

**Judged on any five of the seven** (the maintainer's call, 2026-10-28):

- Natural 5x: five of the seven matching on any roll with none held (about 16 times likelier than five dice);
- The Dice Hate Me: a scoring option means some five of the seven score in an open box;
- Almost Famous: a first-roll 4x held to the last roll, with no five of the seven ever matching.

**Still earnable**:

- A Cunning Strategy and Commitment Issues count "all five" as the hold slots;
- Déjà Vu compares all seven dice (harder);
- Well Rolled counts seven dice a roll;
- Green Fingers works as in Standard.

The mode's own: Lucky Seven (all seven showing one number).

## Third Wind (every box three times)

**Per-slot rules.** A one-turn feat needs one slot; a feat across boxes needs every slot:

- Six Appeal and Taking A Chance need a 30 in one slot;
- Scratched needs a zero in one 5x slot;
- Exact Change needs every slot of each upper box to match (1 in all three Ones slots, and so on), which costs the
    105 bonus as Standard's ladder costs its 35;
- Straight Talker needs all three slots of both straights non-zero.

**Zeros and 5x.**

- Every zero slot counts as a zero (Zero To Hero, How Do You Play This Game?, the tie-break).
- "Scored a 5x" means `fiveOfAKindCount`: every 50 in the box plus chips. A 5x in the 2nd or 3rd slot earns no chip.

**Guarded** (`scoresPerCategory == 1` or `countsOnLeaderboard`):

| Achievement                | Why                                         |
|----------------------------|---------------------------------------------|
| Solid Round ... Dice Deity | Totals triple (Hard averages over 700)      |
| Upper Class, Lower Class   | Pass as a matter of course over 18/21 slots |
| New Personal Best          | Read off the Leaderboard                    |
| Zero To Hero               | Three zeros in 39 turns is normal           |
| Encore, Hat Trick          | Three times the turns make them routine     |

**Can't be earned**: Rock Bottom (the minimum is 15), and the score bands.

**Still earnable**:

- Bonus Round (105, as hard as Standard's 35);
- Spotless (all 39 slots without a zero and the 105 bonus);
- Landslide (easier);
- Dice Whisperer (three times the turns);
- Twice in a Lifetime (three times the turns, and a 5x in the 2nd or 3rd slot needs no earlier 50; kept, the maintainer's
    call, 2026-10-29);
- Cold Dice, Low Rolls, Ton! and Nice (harder);
- Professional Roller;
- Green Fingers (117 rolls).

The mode's own: Gone With The Wind (a multiplayer win) and Chance Encounter (25 or more in all three
Chance slots).

## Hit List (twelve targets and the Alibi)

There are no upper or lower boxes and no 5x. The ceiling is 785, and an average game is ~200. All points are multiples
of 5.

**Guarded** (`hasHitList` / `hasComparableTotals`):

| Achievement                | Why                                    |
|----------------------------|----------------------------------------|
| Solid Round ... Dice Deity | Different card; 200 is an average game |
| Cold Dice, Low Rolls       | Under 100 is an ordinary run of misses |
| Zero To Hero               | Misses are normal                      |
| Spotless                   | Partials leave under one zero a game   |

**Can't be earned**:

- the 5x ones, Upper/Lower Class, Bonus Round, Exact Change, Six Appeal, Taking A Chance, and Straight Talker;
- I Can Count! (the card has no Chance box);
- the house and straight roll feats;
- Nice (69 isn't a multiple of 5) and Rock Bottom;
- New Personal Best and the bands.

**Still earnable**:

- How Do You Play This Game?;
- Ton!;
- Wasted Fortune (always a zero);
- The Dice Hate Me (likelier);
- Green Fingers (39 rolls).

The mode's own: Contract Fulfilled (a multiplayer win) and Right On Target (an exact hit on a five-number target,
judged mid-game).

## D20 (twenty-sided dice)

**Drawing.** Faces past 6 have no pips, so D20 is always drawn in a D20 style (`d20Like`, `TrayDiceStyles`): the
player's pick if it's one (white or blue, faces in `D20_VIEWS`), else white (`D20WhiteDiceStyle`).

- The saved pick is never changed.
- The Rules page draws examples past 6 with `numberedLike`.

**Code rules.**

- Use `ScoreCategory.number`, or `ScoreCategory.upperFor(value)` (null for 7+), never `ScoreCategory.UPPER[value - 1]`,
    which throws for 7+.
- `hasStandardCard` (ONES on the card) is what the Standard-card guards ask, not `!hasHitList`.

**Board.** `d20Layout`, with sky and indigo halves (`UI_BOARD.md`).

**The style unlock.** Nat 20 opens white and black (white numerals); Critical Success opens blue (`AnyAchievement` plus
each colour's `secretAchievement`). The mode always has white.

**Guarded** (`!hasStandardCard`): Zero To Hero, Spotless. Solid Round ... Dice Deity are Standard and Tricolour only.
Cold Dice and Low Rolls count: under 100 needs a disastrous game, as on Standard's card.

**Can't be earned**:

- the 5x ones, Upper/Lower Class, Bonus Round, Exact Change, Six Appeal, Taking A Chance, and Straight Talker;
- I Can Count! (the card has no Chance box);
- Time To Let It Go and Commitment Issues (they read the upper boxes only);
- New Personal Best and the bands;
- Nice and Rock Bottom.

**Still earnable**:

- Impatient;
- Green Fingers (110 rolls: every roll of its 22 turns of five);
- Probability? Never Heard Of Her, on a half's bonus: the last box one of a half's numbers that one die would have
    taken to its threshold (never a wildcard);
- the roll-pattern feat The Dice Hate Me, far rarer on 20 faces (House Call and Straight Away can't
    be earned: the card has no full house or straights);
- Landslide and the wins.

The mode's own: Nat 20 (a multiplayer win) and Critical Success (both half bonuses, judged mid-game, not with roll
modifiers).

**Tuning data.** At thresholds 150 / 400, with the wildcards counting toward their half, Hard reaches the first bonus
in 37% of games, the second in 83% and both in 27% (60 seeded games); Medium reaches only the second, in 22%. With
the wildcards in, the half medians were 144 / 429 for Hard and 99 / 366 for Medium.

## Tricolour (coloured dice, four colour boxes)

Nothing guarded. Solid Round ... Dice Deity are earnable here as in Standard (`alsoEarnableIn`), and in no other mode.

- Déjà Vu compares number *and* colour.
- Fresh Coat Of Paint counts the style, which is recoloured.
- How Do You Play This Game? needs the colour boxes zeroed too.

## The "Unlock all styles" purchase (not a mode)

Owning every style (`PURCHASES.md`) changes what a player can pick, not any rule, so no guard:

- Fresh Coat Of Paint counts a bought style, as `StyleRegistry.unlockedId` draws it.
- Eggcellent Discovery: buying gives the Egg dice and the Chicken cup early, so the 1-in-500 golden egg can come
    sooner. It still needs the roll; the gold Egg colour itself is bought outright.
