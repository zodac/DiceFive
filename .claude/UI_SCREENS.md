# DiceFive: screen-specific UI detail

Achievements, banners, Styles, Leaderboard and menu decisions. The general rules are in `UI.md`, the game screen is in
`UI_BOARD.md`, and what the features do is in `DESIGN.md`.

## The achievements list

**One list, grouped by theme, easiest first** (the declaration order of `Achievement`). Easter Eggs come last, in
alphabetical order.
- **Unlocking doesn't move a row.** It's highlighted in place, with its own icon and a raised `secondaryContainer`
  card, so a ladder reads as a ladder.
- **Never split the list** into locked and unlocked parts, add a "hide unlocked" filter, or sort it alphabetically.
- The unlocked/total count sits centred above the list. In landscape, where the list has two cards to a row, it moves
  into the app bar after the back arrow, with Locked only at the bar's end (see `UI.md`, "Landscape pages").

**Category headers** are `stickyHeader`s styled as a plain `Card`. A bare `Surface` has square corners.
- Up and down arrows jump to the previous or next header (`animateScrollToItem`). The arrow with nowhere to go is
  greyed, not removed.
- They're stock `IconButton`s, so the header is 48dp tall.
- Each header carries its category's earned/total (`3/12`), counted over the whole category even while "Locked only"
  hides rows. The label is `onSurface`, and the whole header turns brand gold (`primary`) once every one is earned.
  TalkBack reads "Milestones, 3 of 12 unlocked" as the heading; the drawn count is hidden from it. Easter Eggs have no
  count, as their total is a secret.
- Rows are clipped at the pinned header's bottom (`Modifier.hiddenUnderPinnedHeader`, from `layoutInfo` in the draw
  pass). Otherwise they'd show through its rounded corners.

**The list scrolls past its last row, on purpose.** A trailing blank item (`tail-space`, measured as the viewport less
the header and the last row) lets the final rows reach the middle, so a banner long press can centre them.
- It's for this page only.
- It must be an item, not `contentPadding`, which would shift `centeredScrollOffset`.
- It's empty, so TalkBack has nothing to stop on.

**Scroll memory.** The position is kept in a process-lifetime `object` (`AchievementsScrollMemory`), because popping
the screen discards its ViewModel and anything `rememberSaveable`d. It's restored only after the list has loaded, since
an empty `LazyColumn` clamps to the top. The Locked only switch is kept there too. Reuse this approach for any list
that must remember its place, with a reset for tests (`resetAchievementsScrollMemory`, `resetRulesPageMemory`): every
Robolectric test shares one process, so a test that opens the screen calls it first.

**Icons** (`ui/achievements/AchievementIcons.kt`) are a `@Composable` `when` over the enum.
- Bespoke drawables (`ic_stairs`, `ic_cowboy_hat`) are allowed.
- An icon is not a field on `Achievement`, so the model stays free of Compose.
- **Locked rows all show the same question mark**, so they never spoil the achievement.
- **Easter Egg icons keep a fixed colour** via `iconTintOrUnspecified`, rather than the row's tint. Luck of the Irish
  shows its real flag fills (`Color.Unspecified`); Big Fan's heart is forced red. A new Easter Egg adds its case there.
- When adding one, grep that the glyph is unused and that it exists in `material-icons-extended`.

## Achievement banners

`AchievementBannerHost` wraps the **whole app** in `MainActivity`, outside the `NavHost`.
- A burst at game end keeps playing across the results page and the menu. A per-screen overlay would cut it off.
- It listens on `AchievementEvents`, a process-wide `SharedFlow`.

The constants are at the top of `AchievementBannerHost.kt`:

|          | Value                                                                                | Why                                                                                         |
|----------|--------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| Position | bottom-anchored stack; the oldest banner in front, each newer one peeking 14dp above | keeps the board clear                                                                       |
| Fade in  | 180ms on joining the stack                                                           | every banner in a burst shows at once                                                       |
| Hold     | 4s (5s on screen with the fades), or the system's "Time to take action" if longer    | readable without lingering                                                                  |
| Fade out | 820ms                                                                                | a snap mid-burst reads as a glitch                                                          |
| Stagger  | 300ms                                                                                | deals like cards                                                                            |
| Cap      | 4 on screen                                                                          | the rest **wait** (the collector suspends on `snapshotFlow { banners.size }`), none dropped |
| Swipe    | 15% of the width, either way; leaves in 180ms                                        |                                                                                             |

**Interaction.**
- Only the front banner is interactive and counting down. A promoted one starts a full hold.
- A finger on it pauses the countdown (back to full opacity), with a fresh hold on release.
- An unlock takes the front over a progress nudge; anything else joins the back.
- **Tapping toggles pause and resume.**
  - A pause/play glyph flashes for 1s (`HOLD_INDICATOR_MILLIS`). It's a child of the banner, so it shares the banner's
    alpha.
  - Resuming keeps the remaining time (`holdRemainingMillis`, monotonic, so background time counts).
  - Nothing shows that a banner is held once the glyph fades. That's by design.
  - TalkBack gets "Pause countdown" / "Resume countdown" actions.
- **A long press** jumps to the achievement on the Achievements screen; a styles banner opens Styles instead. Mid-game,
  with "confirm before leaving" on, it asks first, and every countdown pauses while the dialog is up.

**Window.** The stack lives in its own non-modal `Dialog` window, so it draws above other dialogs.
- The window is full width but wraps the stack's height. **Never `fillMaxSize`**: not-touch-modal only passes touches
  outside the window's bounds.
- **Never dim.** Clear `FLAG_DIM_BEHIND`; don't set a dim of 0. Otherwise Android 12+ drops taps beneath as an
  untrusted occlusion (the About dialog's close button broke this way).

**Three variants, deliberately unequal.**
- **Unlock.**
  - `primaryContainer`, with the achievement's own icon (not a generic trophy).
  - A one-line title and two lines of description.
  - A star badge when the achievement unlocks a style.
- **Progress.**
  - Quieter: `surfaceContainerHigh`, one line, and a thin `LinearProgressIndicator`.
  - The count and bar climb from the old value to the new over 700ms.
- **Styles** (`AchievementEvent.StylesUnlocked`), raised when an achievement count crosses a style lock, or when an
  achievement with styles locked behind it is earned (`StylesEarnedBy`).
  - `tertiaryContainer`, with the style star in a circle. It's the size of an unlock banner.
  - Title "Style(s) Unlocked"; description "Earned 23 achievements: the 'Frosted' dice style", naming two styles and
    counting the rest.
  - **Order.** `announce(update)` (`AchievementAnnouncements.kt`) emits each unlock followed by its own styles banner if
    it has styles behind it (`styleRewards`: "Earned 'Big Fan': 'Glitch' dice and background", or "the hidden
    'Multicolour' dice colour"); then **at most one** styles banner for every count lock crossed
    (`stylesUnlockedByCount`, from `countedUnlocksBefore` to `countedUnlocksAfter`); then the progress nudges.
  - The achievement's own banner keeps its star. The styles banner is what remains when Play Games' pop-up replaces the
    unlock banner (`PLAY_GAMES.md`, "Achievements").
  - One achievement's styles of one name are named once ("'Starry' dice, mat and background"), so they fit the two
    lines in Spanish.
  - TalkBack hears every style unlocked (`StylesUnlockedBannerSemanticsTest`).

**Text.**
- **The title is always one line** (`BannerTitle`, `ShrinkThenWrapText` with a one-line limit). It starts at 16sp and
  steps down 0.5sp to 11sp (`BANNER_TITLE_MIN_FONT_SIZE`, below the app-wide 12sp floor), then ellipsises; it never
  wraps.
  - That's what the 38-character cap is for. The longest title ("Where We're Going, We Don't Need Rules") fits at 11sp
    on the maintainer's ~345dp phone with about a character to spare. On a 360dp render (a 244dp column) it fits at
    11sp only at font scale 1.0: at 1.15 it is ellipsised, and at 12sp it was already cut at 1.0.
- **The description** is bodySmall (12sp), always exactly two lines, then ellipsised (`BannerDescription`). At 320dp
  about eight descriptions are cut.

## Style locks

A lock is on the style (`StyleFamily.unlock`), not the colour: once it's met, all its colours are available.
`StyleCatalogTest` keeps the counts within what can be earned.

- **Behind a specific achievement**, shown padlocked:
  - Googly dice (Completionist);
  - Glitch dice and background (Big Fan);
  - Maths dice (The Solution);
  - Wireframe dice and mat (Are You A Developer?);
  - Devil dice (Bad Omen);
  - Starry dice, mat and background (Rolling The Midnight Dice);
  - the Floating Dice background (Not Those Dice!: the menu's backdrop as a table background);
  - the Martini cup (Shaken, Not Tapped);
  - the D20 dice (Nat 20 or Critical Success, `AnyAchievement`).
- **Secret colours** (`StyleColour.secretAchievement`) are left out entirely until earned:
  - the Irish Multicolour dice (Luck of the Irish);
  - the white and black D20 (Nat 20) and the blue D20 (Critical Success);
  - the Flowerpot's plant-stage pots (Greenfingers);
  - the Top Hat with its rabbit always out (The Magician's Secret);
  - the gold Egg dice (Eggcellent Discovery).
- **The golden egg.** With Egg dice (any colour but gold) and the Chicken cup, even once earned, each human-rolled
  die has a 1-in-500 chance of landing golden (`GOLDEN_EGG_ONE_IN`). Any mode or modifier except D20, whose dice are
  drawn as D20s.
  - The roll: `Die.isGolden`, set by `GameEngine.rollDice(goldenOneIn = ...)` from `performRoll`. It's saved with the
    game and drawn as `GoldenEggDiceStyle`.
  - Only player 1 earns it, and only once the dice have landed.
- **On the Achievements screen**, a row follows the same rule as Styles: a tap shows a short tooltip, a long press
  opens a dialog. The tooltip holds the style reward line ("You've unlocked the 'Multicolour' dice style!") and, for a
  collection row, how many are missing ("Modes with no game finished yet: 4 left"). The dialog is the missing list,
  or for a reward-only row the same reward line. A row with neither has no gesture.
  - Rows and banners carry `StyleRewardStar`, **a badge over the icon's corner, never in the text row** (which would
    wrap titles).
  - The star is decorative to TalkBack: the row's action says "Show which style this unlocks", and the banner says
    "Unlocks a style".
  - Superuser mode's long press takes precedence over both.
- **On the Styles screen**, a locked tile shows its first colour under a scrim, with a faded padlock.
  - A tap shows a tooltip with the core requirement ("23 achievements needed").
  - A long press opens a dialog with progress. That dialog is TalkBack's one action, since tooltips aren't announced.
- **A locked saved pick is never overwritten.** Everything that draws a saved style goes through
  `StyleCatalog.unlockedById` (never `byId`), which draws the default instead. A reset re-locks a style without losing
  the pick.

## The Styles screen

**Counts.** Every colour of a style counts as one (`StyleCatalog.unlockCount`). A secret colour isn't counted until
it's earned, so the total doesn't hint at it.
- The unlocked/total count is drawn like the Achievements page's: a full-width card over the cards, or compact after
  the back arrow in landscape.
- Each category's title carries its own `46/96` before the gallery toggle. Title and count turn brand gold (`primary`)
  once every one is unlocked. TalkBack reads "Dice, 46 of 96 unlocked" as the heading; the drawn count is hidden.

**Unlocked only** sits beside the count, drawn and placed as the Achievements page's Locked only switch: after the count
in portrait, at the app bar's end in landscape. On, it hides every locked style; each row is built afresh, opening on
its pick. It's kept for the process (`StylesPageMemory`, reset by `resetStylesPageMemory` for tests).

**Tiles and rows.**
- One tile per family. A long press opens a row of previews, one per colour. Colour names are spoken, not shown.
- Picking a cup shakes and tips it (`CupPickShake`); picking a die rolls it through six faces (`DicePickRoll`). These
  are only visual.
- Tiles are radio buttons: style and colour name, selected state, and position in the row.

**Rows build gradually.** Each row is a plain scrolling `Row`. The tiles on screen at open (the pick, centred, and its
neighbours) build first, then the rest over the following frames, nearest the pick first.
- Unbuilt tiles are same-sized silent placeholders.
- A `LazyRow` built nothing ahead on a device; see `BENCHMARKS.md`.
- Rows fade in with the page.
- The picks come from `AppContainer.savedStyles`, loaded at launch and shared with the menu logo.

**Fit-to-screen scaling** is worked out in the warm-up, never on opening.
- `StylesWarmUp` lays the page out off screen at full size and, if that scrolls, at `MIN_PAGE_FIT_SCALE` (70%).
  This is its first step, laid out but not drawn, so the fit is saved within moments of the menu settling.
- The cards get smaller dp but the same sp, and `fittedPageScale` finds the scale that just fits.
- The resulting `PageFit` is keyed on window size, density and font scale, and saved with the picks
  (`styles_page_fit`). Opening just reads it.
- A page that fits, or would need less than 70%, stays full size. A page with no measure yet is full size until the
  next warm-up.
- Tests: `PageFitTest`, `StylesPageFitTest`.

**Gallery.** A grid icon on each card's title (`GalleryToggle`, a switch to TalkBack: "Dice gallery, off") shows every
tile at once (`GalleryGrid`).
- **Row and gallery space tiles identically** (`TileSpacing`, from the card's width).
  - Each tile takes its preview's width; a longer name spills into the gaps.
  - The gap is `TILE_SPACING` (12dp), closing to `GALLERY_MIN_TILE_SPACING` (4dp) when that fits one more tile.
  - Sides are `TILES_SIDE_PADDING` (8dp).
  - On a 360dp phone this fits four dice or cups, three mats or four backgrounds (`StylesGalleryTest`).
- **Tile sizes.** Mats are 97x65dp (the tray's shape, cut 10% so three fit). Backgrounds are 72x80dp (the board's
  shape). The cups "Treasure Chest" and "Picnic Basket" were renamed "Treasure" and "Picnic" so their names fit.
- **Rejected on review**: shrunk tiles, an even grid, and a `FlowRow` (which left an empty column).
- **Behaviour.**
  - The toggle is no taller than the title line.
  - It's off on every open (`rememberSaveable`, per card).
  - Tiles are `movableContentOf`, so a shaking cup carries on when switching.
- **The page scrollbar** (`VerticalScrollbar`) sits in the 20dp right margin, and the whole margin is its drag handle.
  It's silent to TalkBack.

**The warm-up** (`StylesWarmUp`): the first open after launch is slow, because the drawing code runs for the first
time.
- 600ms after the menu settles, it measures the page's fit (above), then draws every tile a few at a time, then each
  card, then the empty frame, then the whole screen (`ScreenScaffold(driftingDice = false)`).
- It draws in a 1dp clipped box under the menu backdrop, once per process. Compose doesn't cull clipped content.
- The screen and the warm-up share `StyleCategorySection`, so new categories are warmed automatically. Keep it that
  way.

## The Leaderboard's two views

A `SegmentedChoiceRow` (Combined | Game Mode) at the top, always shown.
- **`CombinedLeaderboard`** is one table with the podium. Its card is as tall as its rows.
- **`GameModeLeaderboard`** is a card per mode with a score, in a scrolling page, with **no podium colours**. A card
  lists only games played with no modifier on (`playedUnmodified`), as the Combined table and online boards do.
  - A card is capped at ten rows (`modeCardListHeight`, from `bodyMedium`'s line height) and scrolls inside. Its
    Previous/Next are the list's last item.
  - A card keeps the rest of a drag or fling once it reaches its end (`KeepScrollInside`). A card too short to scroll
    passes the swipe to the page.
  - The page's scrollbar is in the 20dp side margin.
- **TalkBack.** Card titles are headings, and a row's `stateDescription` carries what its long press shows.
- **Global** (signed in to a service): a text button with a globe on each card's title row, opening the mode's board
  on the service (`GlobalLeaderboardButton`). It takes **no height**: the title sets the row's, and the button is
  centred on it, spilling into the card's padding, so cards don't move on signing in. Where the title and the label
  won't share the line (large fonts, narrow phones) it's the globe alone, never a wrapped title. TalkBack hears
  "Global leaderboard for <mode> on <service>", a button. Only cards with a local score exist, so only those have it.
- Rows use `OnDemandTooltip`, because a `TooltipBox` per row cost real first-frame time.
- **`ScoresWarmUp`.** 2s after the menu settles, it draws both views once from a made-up board
  (`ScoresViewModel(initialState = ...)`, no database), the same way as `StylesWarmUp` (`ScoresWarmUpTest`). The lag
  was first-run code, not the number of entries.

## The menu's backdrop and logo

**Drifting dice.** `BrandBackdrop`'s watermark dice drift on the menu and on every screen off it. The game itself has
none.
- One `DriftState`, held in `DiceFiveApp` and shared through `LocalDriftState`, so the dice keep their places across
  screens.
- It resets only after a game left unfinished, or on a fresh launch.
- `DiceDrift` holds the logic, free of Compose (`DiceDriftTest`):
  - five dice, each crossing in a straight line, up and to one side, turning slower than it travels;
  - a die that leaves re-enters just below the bottom on a new path, size and face;
  - the lean is capped (~25 degrees upright) so dice don't bunch low;
  - a returning die picks whichever of a dozen candidate paths stays furthest from the others over 12s;
  - "gone" uses each die's current extent, which keeps three to five showing.
- It redraws at most ~30 times a second (`DriftState.onFrame`) without changing speed.

**`AppLogo` is live Compose art**, drawn in the player's own dice and cup picks (`MenuViewModel.logoStyles`). It's not
a copy of the launcher icon.
- It's held invisible, but still taking its space, until the picks load, so it never flashes the defaults.
- **The cup takes no layout space** (`noLayoutSpace`), so adding or switching cups never moves the dice or the
  wordmark.
  - Only a tall cup's overhang is reserved, as top padding.
  - Every cup is drawn at the same scale per grid unit, hanging from one top line. Standing on a shared base hid squat
    cups.
- Keep the "DiceFive" text: `MainActivitySmokeTest` asserts on it.
- **Tapping the cup shakes it** (`shakeCupOnTap`, the menu only): the same `CupCanvas` shake, ending upright, with no
  sound and no reward.
  - The hit area is the logo's `detectTapGestures` plus `isOnLogoCup`'s rectangle.
  - A tap on a die is consumed first.
  - Not exposed to TalkBack, like the dice tap.
