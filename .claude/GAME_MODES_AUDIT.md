# Game modes: the achievement audit

Which existing achievements each mode (or roll-changing modifier) blocks or guards, and how to audit a new one. Mode
mechanics are in `GAME_MODES.md`.

## How to audit

Many achievements assume three rolls, holding between rolls, no timer, or the Standard card. For each achievement a new
mode changes, decide:

- **Can't be earned in this mode.** Leave it: no code. It's still earnable in other modes.
- **Earned for free in this mode.** **Add a guard** and a test for it: a row in `AchievementEngineTest`'s guarded
  table (each mode and modifier's cards, and the feats each must not earn). **Prove the test** by removing that one
  guard and watching it fail. Strip guards one at a time; stripping them all at once proves nothing about each.
- **Easier but not free.** Don't guard it. **Report the "easier" list to the user** rather than deciding alone.
- **Count the thing, not its proxy.** Bonus chips equal "2nd/3rd 5x" only while the 5x box has one slot;
  `fiveOfAKindCount` counts the 5x themselves.

| Family           | Achievements                                                                                                                                                        | Ask                                                                       |
|------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------|
| Roll count       | Impatient, Naturally Gifted, Almost Famous, Natural 5x, The Dice Hate Me, Déjà Vu, Time To Let It Go, Pointless Roll, Greenfingers, Probability? Never Heard of Her | Does "one roll used" or "every roll used" become automatic or impossible? |
| First roll       | House Call, Straight Away, I Can Count!                                                                                                                             | Is every roll now a first roll, or none?                                  |
| Holds            | A Cunning Strategy, Decisions Decisions, Time Wasting, Commitment Issues, Hands Off! (Standard only)                                                                | Can dice still be held?                                                   |
| Timer            | The timeout guards on low or zeroed cards (`GAME_MODES.md`)                                                                                                         | Always or never a timer?                                                  |
| Card contents    | How Do You Play This Game?, Spotless, Bonus Round, Upper/Lower Class, Exact Change, the 5x ones                                                                     | Is each box still there, and do totals mean the same?                     |
| Score thresholds | Solid Round ... Dice Deity, Cold Dice, Low Rolls, Ton!, Nice, Rock Bottom, the score bands                                                                          | Trivial or impossible at this ceiling?                                    |
| Dice look        | Fresh Coat Of Paint, Luck of the Irish                                                                                                                              | Does the mode draw the player's dice style?                               |

The roll helpers `rollsRemainingAfterFirst`/`AfterSecond` are `rollsPerTurn - 1`/`- 2`. With fewer than three rolls
they reach 0 or below, so read each comparison against them.

**Guard predicates in use:**
- `PlayerState.hasFullCard` (no box off);
- `scoresRolledHand` (`!scoresHeldDiceOnly`);
- `scoresPerCategory == 1`;
- `countsOnLeaderboard`;
- `GameMode.hasHitList`;
- `hasStandardCard` (ONES on the card);
- `hasStandardTotals`;
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
- every 5x achievement that needs the box (First 5x, Encore, Hat Trick, Scratched, Twice in a Lifetime);
- Natural 5x (`fiveOfAKindScorable` is never true);
- the score bands and New Personal Best (off the board);
- Exact Change and Both Straights, unless the draw keeps those boxes.

**Still earnable** (report these):
- Impatient and Naturally Gifted;
- Greenfingers (18 rolls rather than 39);
- Nice and Ton! (likelier);
- Zero To Hero (harder);
- Landslide and the wins.

The mode's own: Quick On The Draw (a multiplayer win) and Six Of The Best (150+, not with extra rolls or Extended
Scores).

## Modifier achievements

- **Rainy Day Fund** (a turn started with 100+ rolls under Stored Rolls) needs about 8+ rolls a turn in a 13-turn
  mode (9 a turn with one roll used each turn reaches 105 on the last turn). Third Wind's 39 turns make it easier;
  Quickfire's six turns make it impossible.
- **Bad Omen** (a Game Modes achievement, unlocking the Devil dice: every die chained at once) needs Unlucky Dice's
  cap at 5 and nothing held. Impossible in Stud: seven
  dice, at most five chained.
- **Grand Tour** counts a finished game in each mode, modifiers or not. A new mode raises its target
  (`GameMode.entries.size`); anyone already holding it keeps it.

## One roll a turn (Number of Rolls at 1)

Impatient, Naturally Gifted and Almost Famous are guarded.

## Stud (seven rolled, five held)

A roll isn't a hand, so:
- achievements judged at scoring read `scoringDice`: Wasted 5x, Why Did You Do That?, Empty/Fuller House, Time To Let
  It Go, Twice in a Lifetime;
- achievements judged on the roll as it lands are guarded.

**Guarded** (`!scoresHeldDiceOnly` / `scoresRolledHand`, plus `singleScoreCard` and Zero To Hero's own condition; Twice
in a Lifetime is guarded in `GameViewModel`):

| Achievement                                                | Why                                                                            |
|------------------------------------------------------------|--------------------------------------------------------------------------------|
| House Call, Straight Away                                  | Seven dice hold these far more often, and nothing is held yet                  |
| Natural 5x                                                 | Same, on a reroll                                                              |
| The Dice Hate Me                                           | Judged on the roll, which isn't what scores                                    |
| Almost Famous                                              | A 5x among seven never shows as one, so it was always true                     |
| First 5x, Encore, Hat Trick, Twice in a Lifetime, Total 5x | The best five of seven makes 5x easy; Stud's 5x don't count toward `SCORED_5X` |
| Both Straights, Chance 30, Six Appeal                      | Routine with the best five of seven                                            |
| Bonus Round, Upper/Lower Class, Solid Round ... Dice Deity | Totals run well above Standard's                                               |
| Spotless, Zero To Hero                                     | Zeros are rare                                                                 |
| New Personal Best                                          | Off the Leaderboard                                                            |

**Can't be earned**:
- I Can Count! and Product Placement (seven dice never equal a five-dice sequence);
- What Was The Point Of That? (two dice always roll).

**Still earnable**:
- A Cunning Strategy and Commitment Issues count "all five" as the hold slots;
- Déjà Vu compares all seven dice (harder);
- Well Rolled counts seven dice a roll;
- Greenfingers works as in Standard.

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
| Spotless                   | Replaced by Third Time's The Charm          |
| Encore, Hat Trick          | Three times the turns make them routine     |

**Can't be earned**: Rock Bottom (the minimum is 15), and the score bands.

**Still earnable**:
- Bonus Round (105, as hard as Standard's 35);
- Landslide (easier);
- Dice Whisperer (three times the turns);
- Cold Dice, Low Rolls, Ton! and Nice (harder);
- Professional Roller;
- Greenfingers (117 rolls).

The mode's own: Gone With The Wind (a multiplayer win) and Third Time's The Charm (all 39 slots without a zero).

## Hit List (twelve targets and the Alibi)

There are no upper or lower boxes and no 5x. The ceiling is 785, and an average game is ~200. All points are multiples
of 5.

**Guarded** (`hasHitList` / `hasStandardTotals`):

| Achievement                | Why                                    |
|----------------------------|----------------------------------------|
| Solid Round ... Dice Deity | Different card; 200 is an average game |
| Cold Dice, Low Rolls       | Under 100 is an ordinary run of misses |
| Zero To Hero               | Misses are normal                      |
| Spotless                   | Partials leave under one zero a game   |

**Can't be earned**:
- the 5x ones, Upper/Lower Class, Bonus Round, Exact Change, Six Appeal, Taking A Chance and Both Straights;
- the house and straight roll feats;
- Nice (69 isn't a multiple of 5) and Rock Bottom;
- New Personal Best and the bands.

**Still earnable**:
- How Do You Play This Game?;
- Ton!;
- Wasted 5x (always a zero);
- The Dice Hate Me (likelier);
- Greenfingers (39 rolls).

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

**Guarded** (`!hasStandardCard`): Solid Round ... Dice Deity, Cold Dice, Low Rolls, Zero To Hero, Spotless.

**Can't be earned**:
- the 5x ones, Upper/Lower Class, Bonus Round, Exact Change, Six Appeal, Taking A Chance and Both Straights;
- Time To Let It Go and Commitment Issues (they read the upper boxes only);
- Greenfingers (5 rolls a turn);
- New Personal Best and the bands;
- Nice and Rock Bottom.

**Still earnable**:
- Impatient and Naturally Gifted;
- the roll-pattern feats (House Call, Straight Away, I Can Count!, The Dice Hate Me), far rarer on 20 faces;
- Landslide and the wins.

The mode's own: Nat 20 (a multiplayer win) and Critical Success (both half bonuses, judged mid-game, not with roll
modifiers).

**Tuning data.** At thresholds 150 / 400, with the wildcards counting toward their half, Hard reaches the first bonus
in 37% of games, the second in 83% and both in 27% (60 seeded games); Medium reaches only the second, in 22%. With
the wildcards in, the half medians were 144 / 429 for Hard and 99 / 366 for Medium.

## Tricolour (coloured dice, four colour boxes)

Nothing guarded, nothing blocked.
- Déjà Vu compares number *and* colour.
- Fresh Coat Of Paint counts the style, which is recoloured.
- How Do You Play This Game? needs the colour boxes zeroed too.

## The "Unlock all styles" purchase (not a mode)

Owning every style (`PURCHASES.md`) changes what a player can pick, not any rule, so no guard:
- Fresh Coat Of Paint counts a bought style, as `StyleRegistry.unlockedId` draws it.
- Eggcellent Discovery: buying gives the Egg dice and the Chicken cup early, so the 1-in-500 golden egg can come
  sooner. It still needs the roll; the gold Egg colour itself is bought outright.
