# DiceFive: the game screen (UI detail)

Board, tray and cup layout and decisions. General UI rules are in `UI.md`, and mode rules in `GAME_MODES.md`. Unless
noted, every layout here has been checked only as Robolectric renders (411dp, 360dp, and for fonts 100/130/200%), not
on a device.

## The scorecard grid

**Layout.** The board is **four equal columns in rows shared by two panes** (`boardLayout`).

- **`ScoreGrid`** is the left pane: the upper section down column 1, beside the lower section in column 2, for
  whatever card it's given (`PlayerState.categories`).
- **`DiceCupPanel`** is the right pane, in the same rows:
  - 5x is one wide tile across columns 3-4 in row 1;
  - up to two rows of boxes sit under it (`BoardLayout.sideRows`): Tricolour's 2x2, or Extended Scores' Evens | Odds /
    Two Pair;
  - the cup is laid over rows 4-5;
  - Totals and Undo are in the last row.
- D20 is the exception: four columns, not two panes (see **Per-mode cards**).
- **Contention.** Extended Scores wins the place under 5x. Tricolour's colours then go after the lower section, two to
  a row (eight rows).

**Tile size.**
- Totals and Undo are a tile in size: 40dp when compact, below 48dp like the compact tiles.
- More than six rows switches every tile to `COMPACT_TILE_SIZE` (40dp from 48) and grows the board
  (`scoreBoardHeight`, 390dp for eight rows). The cup keeps `CUP_SIZE` (it needs rows 4-6; Totals/Undo move below it).
- Standard and Tricolour are six rows, 380dp.
- **A text mark is always one line** (`BadgeLabel`). Where a large font would wrap it, it's measured and shrunk to fit
  the tile less 3dp each side, down to 6sp (D20's "11-20" is about 7.8sp at 200% on a compact tile). Marks that fit
  keep their size (`BadgeLabelFitTest`).

**The featured box.**
- **With nothing under it**, 5x is a large square over rows 1-2 (`squareSize`, 16dp corners, score and bonus beside
  it).
- **Otherwise** it's a wide tile (`CategoryTile(wide = true)`).
  - Its right edge lines up with column 4's tile, and its score sits where column 4's scores are.
  - A repeat 5x's bonus is written beside the box it scored in, not on this tile (see **Joker bonus** below).
  - The cup is drawn over its two rows, offset from the row height and gap, not sized by weights (which wouldn't line
    up).

**Joker bonus.** Each joker's +100 is written beside the box that joker scored in (`PlayerState.jokerBonusSlots`: box -> slot
indexes), not summed on the 5x tile.
- A one-slot box shows it as a gold `labelSmall` line under its score; a roll that would be a joker previews it on every legal box.
- A Third Wind slot shows it after its score on the same line ("30 +100"; the lines already fill the tile). All three slots of a
  box can carry one, since the joker is forced into the matching upper box while it has a slot open.
- **One size for the whole board** (`LocalBonusFontSize`, from `fitBonus` in `BonusFit.kt`): the largest the tightest box allows,
  at most `labelSmall`'s 11sp and at least 8sp (`BONUS_MIN_SIZE`), so a screen never shows two. It's a highlight beside a score,
  gold and bold, and spoken in full, not text to read at length. Below 8sp every bonus on the board is a bare gold "+".
  - Room is measured: a bonus may spill past its column into the gap before the next column's tile, or in the cup panel's last
    column into the board's padding (kept `BOARD_EDGE_MARGIN` off the edge). Third Wind's also gives up a two-digit score's width.
  - Roughly: 11sp at 411dp on every card; Tricolour and Extended about 9-10sp at 360-362dp; Third Wind about 8sp at 360dp and "+" a
    little narrower or at large fonts. (Measured in Robolectric; the phone's Roboto is a little wider.)
- Spoken with the score ("Scored 30 plus 100 bonus, 24"), and as "plus 100 bonus" on a previewed box.
- **Pane split** (`fitBonus`). The cup panel and the grid trade width to make room for the bonus:
  - nothing under the large 5x tile (Standard, Third Wind, Stud, Quickfire): the grid takes what's spare beside the tile, up to
    16dp, so the tile never shrinks;
  - boxes under 5x (Tricolour's colours, Extended Scores'), whose tile is the pane's width: the split moves up to 12dp either way
    to even the grid's room against the panel's (about 10dp toward the cup panel at 360dp);
  - any other card keeps equal halves.

### Per-mode cards

- **D20.** No featured box, seven rows of compact tiles, laid out by column (`BoardLayout.columns`, drawn by
  `ColumnBoard`), so each half keeps to its own side.
  - Column 1: the `1-10` wildcard then 1-6; column 2: 7-10. Column 3: the `11-20` wildcard then 11-13; column 4: 14-20.
  - The cup is centred under columns 2-3 over rows 5-6, with Totals and Undo centred under it in row 7. The board
    grows (about 407dp) so those two rows hold the whole `CUP_SIZE`.
  - Each column is a traversal group, so TalkBack reads a half's boxes in order, then the cup and buttons.
  - Tiles are number badges: 1st half sky blue (`TileSky*`), 2nd half indigo (`TileIndigo*`). The colour repeats the
    number, so it needs no spoken twin.
  - The Totals popup shows each half against its target ("95 / 138", gold once reached, wildcard included) plus the
    Bonus. The glyph lights its top half for the 1st bonus and its bottom half for the 2nd (`litHalves`). Spoken: "1st
    half 95 of 138".
  - Dice past six are D20s (`d20Like`).
- **Hit List.** No 5x.
  - The Alibi is the featured box (`ScoreCategory.featured`, `BoardLayout.featured`), and the twelve targets fill the
    grid two to a row, easiest at the top.
  - A target tile (`CategoryIcon`'s `TargetIcon`) shows its five places (a number, or a dot for "any") over its points.
    Everything is sized as fractions of the tile, not the font, so five places fit at any system font.
  - While a roll is previewed, each named place the dice show gets a short bar under it: white, or gold with the
    number when the die is in its own column. It's a bar per place because a text underline merged neighbouring places.
  - Spoken state: "3 of 4 rolled, 1 in place", "partial hit, ...", "hit, 0 of 3 in place", "exact hit".
  - A tile glows only for a hit. A partial is a gold score beside an unlit tile (`tileLit`), because a board of lit
    partials hid the hits.
  - Totals shows Targets and Alibi (`HitListTotalsButton`).
- **Third Wind: stacked scores** (`StackedScores` in `ScoreGrid.kt`).
  - Each slot is a line beside the tile, in fill order: each filled slot's score, then the preview in the next open slot
    (gold when it's worth picking), then "-" in the rest.
  - Lines are `labelMedium` beside a grid tile (3 x 16sp fills 48dp) and `bodyMedium` beside 5x.
  - The tile greys only once every slot is used.
  - Each `SlotScore` keeps its number laid out under the "-", and `ShownScore` compares by value, so a roll recomposes
    only the preview lines.
  - TalkBack hears one node: "Scored 15, 10, would score 20" (`stackedSpokenState`).
  - The lines exactly fill the tile, so a large font overflows it. Then:
    - the stack scrolls within the tile (`heightIn` + `verticalScroll`), kept on the next slot to score, or on a full
      box's last score (`stackedScrollTarget`);
    - the edge with more beyond it fades (a `DstOut` gradient layer, drawn only while overflowing);
    - the stack takes `wrapContentWidth(unbounded = true)`, or the clip cuts off spilled digits.
- **Tricolour: coloured dice keep the player's style**, `recoloured(palette)`.
  - Every `DiceStyle` must implement it. Classic becomes `ColouredClassicDiceStyle`; the others rebuild from
    `DieColourPalette`.
  - Pips and digits always use the palette's `pip`, and the held ring its `heldRing`, because a style's own can vanish
    on the roll's colour.
  - Neon LCD keeps its dark body and lights the digit in `swatch`. Googly sockets go white. Striped Multicolour dice
    become plain dice of their shape.
  - `DiceTray` builds each recoloured style once per game (`TrayDiceStyles`).
  - Colour boxes are flat `swatch` squares.
  - Coloured House is Full House's glyph over three equal-*width* diagonal stripes in the `stripe` shades, with a soft
    shadow under the glyph. Equal areas made the yellow band look thin.
  - These colours live in `Color.kt`'s game-table block.

## Buttons

**Totals** (`TotalsButton`). An icon-only Σ left of Undo.
- A tap toggles `TotalsTable` in a persistent `AppTooltip` (open until a second tap or an outside tap, no timeout); a long
  press shows it: Sora labels left, system-font numbers right with
  thousands commas, and the Bonus line gold once earned.
- The Upper line is shown against its threshold ("45 / 63", spoken "Upper 45 of 63") wherever the card has an upper
  bonus.
- The glyph turns gold once the bonus is earned.
- **Earning a bonus pops the button** (scale to 1.5 and back over 1s: full size exactly halfway, the same time shrinking, eased), once for the upper bonus and once for
  each D20 half (`bonusJustEarned`). Solo games only (`flourish`, from `showUndo`): with more players the board moves on
  to the next player and the pop would go unseen. Not on first composition (a resumed game), not when an undo takes one away, and not
  below the Medium Animations level (`scorePulse`, so Low, Off and reduced motion never pop). Purely visual: TalkBack hears the same state.
- TalkBack hears the totals as the button's state ("Upper 61, no bonus yet, lower 91"), and "Show totals" as its
  action.

**Undo and Totals are icon-only** (`BoardButtonIcon`), by the maintainer's choice. A large font changes nothing.
- The glyph is 26dp, shrinking only if the row is shorter.
- Both keep the 48dp target where there's room.
- Undo's glyph carries the name "Undo".

## The tray

- **A die on the mat is its held size**: column width less `HELD_DIE_INSET` each side (`matDieSize`).
- **Stud (more dice than slots)** uses `SlottedDice`.
  - Five hold slots run across the top. The mat is seven narrower columns, 6dp apart, a die at most 86% of its column.
  - Holding and releasing are separate targets: tap a mat column to hold, into the free slot nearest it; tap a slot to
    release.
  - A held die **never moves**. An order-preserving slide was rejected.
  - A slot is only as big as a mat column, so a held die is mat-sized. The tray keeps the usual height. Stud's slot row
    uses `MAX_SLOT_DIE_SIZE`.
  - Both layouts share the `columnPresses` gesture. Each slot and die is its own TalkBack node (`SlottedDiceTrayTest`).
  - **Ambiguity hints.** Pressing a box marks the dice it would take (a solid gold lane) and the unheld dice that would
    score the same (dashed). The rest fade (`ProjectedHand`, `LocalHandPreview`) to `FADED_DIE_ALPHA` (0.45), which the Rules' example dice
    share so they look like the game. This is sighted-only, because the
    score is identical either way.
- **A die locked by Unlucky Dice** (`LockedChains`, drawn in `DieFace`).
  - The look: a red veil in the die's own outline (`shadowShape`), with a red cross of two chains over it. Each chain is
    five links, alternately face-on and edge-on, edged dark. The egg and D20 use `lockedChainReach`.
  - Timing: drawn only once the die has landed. It slams in over 260ms and simply appears under reduced motion. The
    pips are covered on purpose.
  - Spoken: "Locked in chains, can't be held or scored", with no Hold action, sound or haptic (`BoardSemanticsTest`).
  - The modifier card has two stepper rows (`ModifierSetting.steppers`): the odds and the cap.

## Every roll is one cup tap

A human's roll (a finger on the cup, or a shake) goes through `GameScreen.onCupTap`: a `CUP_SHAKE_MILLIS` shake, the
sound and haptics, then `GameViewModel.rollDice`.
- A CPU's roll shakes the same (`aiRolling`) and lands through the same `performRoll`.
- No roll starts while another is in hand (`rollInHand`, from the shake until the dice settle). In that window the cup
  ignores taps and is `disabled()` to TalkBack, and the CPU waits out `GamePacing.diceTossMillis`.
- **Don't add another way to roll.** See `GAME_MODES.md`, "Turn flow".

## Tablets and landscape

**The game screen scales as one object** (`gameLayout`).
- It's laid out at a reference width, then drawn larger through a scaled `LocalDensity` with the font scale kept, capped
  at `MAX_GAME_SCALE` (2x). The whole width scrolls it (the empty sides too), with a `VerticalScrollbar` in the right margin
  while it doesn't fit.
- **Portrait** (width <= height): header, board, tray, laid out at 411dp (`REFERENCE_WIDTH`), never below 1x, and at most
  `STACKED_MAX_WIDTH` (480dp) wide, so squarish screens centre it.
- **Landscape** (any width > height, phones included): laid out at `LANDSCAPE_WIDTH` (720dp).
  - The header's tabs are one line each (`PlayerHeaderBar(singleLine)`: dot, name, score, place).
  - The board is drawn `LANDSCAPE_BOARD_SCALE` (1.35x) larger than the tray (`GameBoard(landscape)`, `LandscapeBoard`),
    and under it the tray takes two thirds of the row and the cup (`GameCup`, count on its left) the last third, as large as
    that third allows.
  - It may be drawn below 1x on a short screen, down to tiles and dice of a 48dp touch target (`MIN_TOUCH_TARGET`); past
    that it scrolls. On an 891x411 phone Standard just about fits; four- and five-row cards scroll a little.
- **The landscape board** (`landscapeLayout`): six columns. Each section is its own row(s) in card order, but for a group
  that fits whole in the row before it (Extended Scores' Evens and Odds, or Two Pair, grouped as `EXTENDED_SIDE_ROWS`). The
  last row has the featured box (5x, the Alibi) as a wide tile across columns 3-4 and Totals and Undo across 5-6; a last
  row of up to three boxes moves in before it (with three, the featured box is a normal tile in column 4).
  - Standard: upper row, lower row, then 5x. Tricolour: the colours get a row. Tricolour + Extended: colours with Evens and
    Odds, then Two Pair beside 5x. Standard + Extended: Evens, Odds, Two Pair, 5x (narrow) and the buttons in one row.
  - D20 (no featured box): each half in two rows (1-6; 7-10 and the 1-10 wildcard; and so on), with Totals in the spare
    cell of the 1-10 row and Undo in the 11-20 row's.
- The Orientation setting (`DESIGN.md`, "Settings") can hold either layout whatever way the device is turned.
- **A header other than the player tabs** (the tutorial's coach card, `coachHeaderHeight`) is measured at the width
  the game is laid out at and passed to `gameLayout`, so the game is sized for it. Where even the smallest game leaves
  it short in portrait, the tray's mat is made shallower first (`LocalScatterHeight`, down to `MIN_SCATTER_HEIGHT`
  56dp, each die's resting place scaled to match), then the card is held to `GameLayout.headerRoom` (at least
  `MIN_COACH_HEIGHT`) and its words scroll. A 360x776 phone shows the whole card.
- `gameLayout` keeps `ROUNDING_SLACK` (4dp) of the height spare, as pieces round to whole pixels when scaled.
- Tests: `GameLayoutTest`.

Renders cover 1280x800, 960x600, 891x411 and 800x360 in every mode, and 360x640 portrait.

## Player names and the header

- **The cap varies with player count** (`maxPlayerNameLength`, `model/NameWidth.kt`: 14/12/10/8 characters at 1-4 players).
  - The binding constraint is the in-game header, where every seat shares one row. It's a layout limit only.
  - It's enforced in `GameSetupForm.setPlayerName` against the current count, so restored names are clamped too.
    `setPlayerCount` re-clamps every slot either way.
  - Width counts, not UTF-16 units (`model/NameWidth.kt`: full-width characters count 2, combining marks 0).
- **CPU names are tighter** (`maxAiNameLength`, which allows for the chip icon) and are **never truncated**.
  `AiNameGenerator` has a pool per player count (2/3/4), each entry already fitting. `AiNameGeneratorTest` sweeps every
  pool against its cap.
- **Sanitising** (`sanitizePlayerName`) happens as names are typed and again in `startGame`.
  - Nothing is censored.
  - Removed: control characters (whitespace collapses to one space), zero-width and bidi override/isolate characters,
    soft hyphens, BOMs, replacement/private-use/non-characters, unpaired surrogates and Hangul fillers.
  - Kept: ZWJ/ZWNJ and LRM/RLM.
  - Capped: at most 4 combining marks per character (8 tag characters for flags), none on nothing, and at most 64 code
    points.
  - The result is NFC (`normalizeNfc`).
  - Not trimmed on the way in, because a trailing space must survive typing.
  - Compared with `playerNameKey`: sanitised, trimmed, lower-cased.
  - Tests: `PlayerNameTest`.
- **`PlayerHeaderBar`** steps names down to `labelMedium` at 3+ players.
  - The name row keeps `labelLarge`'s line height, so the tabs and the board under them are the same height at any
    player count (`PlayerTabHeightTest`). A frame draws behind the tab and never adds to its size.
  - CPU tabs carry `CpuPlayerIcon` (Material `Memory`, ~14dp of a ~72dp tab), and tab names use `ShrinkThenWrapText`.
  - The caps were checked by estimate, not on a device: at 360dp, 4 players leaves ~72dp for a human name (~63 needed)
    and ~58dp for a CPU (~42 needed). 320dp is the tightest case.

## A disabled scorecard box

Quickfire's off boxes (`PlayerState.disabledCategories`) stay on the board, so the layout never moves. Every other
state is a filled tile (teal open, gold good pick, grey scored), so a disabled box is **only an outline**:
- a dashed border, the glyph at 30% with a slash through it, and the word **Off** (`CategoryTile(disabled)`);
- never a fill or a solid border, which would read as "scored";
- spoken "Disabled for this game, can't be scored", with `disabled()` and no Score action (`BoardSemanticsTest`).
  Compare "Scored N" and "Open".
