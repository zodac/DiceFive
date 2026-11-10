# DiceFive: screen-specific UI detail

Achievements, banners, Styles, Leaderboard and menu decisions. The general rules are in `UI.md`, the game screen is in
`UI_BOARD.md`, and what the features do is in `DESIGN.md`.

## The achievements list

**One list, grouped by theme, easiest first** (the declaration order of `Achievement`). Winning, Misfortune, and
Miscellaneous run in alphabetical order instead, and so do the Easter Eggs, which come last.

- **Unlocking doesn't move a row.** It's highlighted in place, with its own icon and a raised `secondaryContainer`
    card, so a ladder reads as a ladder.
- **Never split the list** into locked and unlocked parts, add a "hide unlocked" filter, or sort the whole list alphabetically.
- **A row's title** (`ShrinkThenWrapText`) is one line at `titleSmall` when it fits, stepped down to the 12sp floor, then
    wraps to two lines at 12sp: unlike the banner, which keeps one line down to 11sp. `AchievementsTextTest` pins it. The
    row sits under `IntrinsicSize.Min` (equal cards in landscape), so `ShrinkThenWrapText` answers intrinsic heights.
- **The count is "earned of total", and the Easter Eggs are in earned but not in total**: a player with everything
    reads "110 of 100 earned". This is intended, not a bug (`AchievementsTextTest`). It is not about secrecy: every
    Easter Egg's row (title shown, description "???") is in the list from the start.
- The unlocked/total count sits centred above the list. In landscape, where the list has two cards to a row, it moves
    into the app bar after the back arrow, with the Unearned switch at the bar's end (see `UI.md`, "Landscape pages").
- **While signed in to the online service, the count is a button** to the service's own achievements screen, with the
    service's mark (`GameServices.serviceIcon`, untinted) centred in the gap between the text's end and the card's. The
    text sits as it would centred together with the mark 6dp after it. TalkBack:
    "42 of 100 earned, button, double-tap to open Google Play Games"; the mark is decorative. Signed out it's plain
    text, with no ripple, button role, or mark. A tap still counts toward a debug build's superuser mode.

**Category headers** are `stickyHeader`s styled as a plain `Card`. A bare `Surface` has square corners.

- Up and down arrows glide to the previous or next header (`smoothScrollToItem`: one eased animation, 350-800ms by
    distance; the stock `animateScrollToItem` spring ends in a quarter of a second and reads as a snap). Each frame moves
    its share of what's left. A sticky header is laid out pinned as soon as the list reaches its category, so its own
    offset never says how far is left (`offsetWhereItLies`). The arrow with nowhere to go is greyed, not removed.
- They're stock `IconButton`s, so the header is 48dp tall.
- Each header carries its category's earned/total (`3/12`), counted over the whole category even while the Unearned switch is on
    hides rows. Label and count are `categoryHeaderStyle` (Sora, `titleSmall`, as written), as on the Styles screen. The
    label is `onSurface`, and the whole header turns brand gold (`primary`) once every one is earned. TalkBack reads
    "Milestones, 3 of 12 earned" as the heading; the drawn count is hidden from it. Every category has a count, Easter
    Eggs included: their rows are all listed, so their number is no secret.
- Rows slide under the pinned header and are cut at its middle (`Modifier.hiddenUnderPinnedHeader`, from `layoutInfo` in
    the draw pass), so they show past its rounded bottom corners. Cut at its bottom edge, their square corners stick out
    past its curve like a lip; left uncut, the top of the list cuts them flat in its top corners.

**The list scrolls past its last row, on purpose.** A trailing blank item (`tail-space`, measured as the viewport less
the header, the last row, and the list's gap before it) lets the final rows reach the middle, so a banner long press can
centre them. At the furthest scroll the last row rests whole, directly under the pinned header (`AchievementsLastRowTest`).

- It's for this page only.
- It must be an item, not `contentPadding`, which would shift `centeredScrollOffset`.
- It's empty, so TalkBack has nothing to stop on.

**Scroll memory.** The position is kept in a process-lifetime `object` (`AchievementsScrollMemory`), because popping
the screen discards its ViewModel and anything `rememberSaveable`d. It's restored only after the list has loaded, since
an empty `LazyColumn` clamps to the top. Reuse this approach for any list
that must remember its place, with a reset for tests (`resetAchievementsScrollMemory`, `resetRulesPageMemory`): every
Robolectric test shares one process, so a test that opens the screen calls it first.

**Icons** (`ui/achievements/AchievementIcons.kt`) are a `@Composable` `when` over the enum.

- Glyphs are Material Symbols, Rounded and filled, from `ui/icons/Symbols.kt`, which `scripts/material-symbols.sh`
    generates from Google's path data. Add a glyph to the script's list and run it; never edit `Symbols.kt` by hand.
- Bespoke drawings are allowed where Symbols has nothing that fits: `DrawnAchievementIcons.kt` (path data on the 24 grid,
    one colour with details cut out), and drawables (`ic_stairs`, `ic_cowboy_hat`).
- An icon is not a field on `Achievement`, so the model stays free of Compose.
- **A mode or modifier named in a description is in `backticks`**, drawn bold gold (`tooltipMarkup`) on the list and the
    banner. TalkBack and the Play Console export drop the backticks. `AchievementTextTest` checks they pair, and that no
    mode's name (in the string's own language) sits outside them, "non-`Standard`" included.
- **Locked rows all show the same question mark**, so they never spoil the achievement.
- **Fixed colours** via `iconTintOrUnspecified`, rather than the row's tint:
    - every Easter Egg (Luck Of The Irish shows its real flag fills with `Color.Unspecified`; Big Fan's heart is forced
        red). A new Easter Egg adds its case there;
    - and the few whose point is a colour: Tricolour's red/yellow/blue (Tricolourful's traffic light, Tricolour Me
        Impressed's dice, which are also Tricolour's Play leaderboard icon), Everything Everywhere's googly eye, and
        Completionist's crystal trophy.
- When adding one, grep that the glyph is unused. `PlayConsoleExportTest` fails if two achievements draw the same.

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
    - **Order.** `announce(update, ownsAllStyles)` (`app/AchievementEvents.kt`) emits each unlock followed by its own styles banner if
        it has styles behind it (`styleRewards`: "Earned 'Big Fan': 'Glitch' dice and background", or "the hidden
        'Multicolour' dice colour"); then **at most one** styles banner for every count lock crossed
        (`stylesUnlockedByCount`, from `countedUnlocksBefore` to `countedUnlocksAfter`); then the progress nudges.
    - The achievement's own banner keeps its star.
    - **None for an owner of every style** (the purchase, `PURCHASES.md`): `AchievementUnlocker` reads `ownsAllStyles` as
        it announces, and only the unlock and progress banners show. The star stays on rows and unlock banners.
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

A lock is on the style (`StyleGroup.unlock`, in the registry), not the colour: once it's met, all its colours are
available. `StyleRegistryTest` keeps the counts within what can be earned.

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
- **Secret colours** (`StyleVariant.secretAchievement`) are left out entirely until earned:
    - the Irish Multicolour dice (Luck Of The Irish);
    - the white and black D20 (Nat 20) and the blue D20 (Critical Success);
    - the Flowerpot's plant-stage pots (Green Fingers);
    - the Top Hat with its rabbit always out (The Magician's Secret);
    - the gold Egg dice (Eggcellent Discovery).
- **The golden egg.** With Egg dice (any colour but gold) and the Chicken cup, even once earned, each human-rolled
    die has a 1-in-500 chance of landing golden (`GameEngine.GOLDEN_EGG_ONE_IN`). Any mode or modifier except D20, whose dice are
    drawn as D20s.
    - The roll: `Die.isGolden`, set by `GameEngine.rollDice(goldenOneIn = ...)` from `performRoll`. It's saved with the
        game and drawn as `GoldenEggDiceStyle`.
    - Only player 1 earns it, and only once the dice have landed.
- **On the Achievements screen**, a row follows the same rule as Styles: a tap shows a short tooltip, a long press
    opens a dialog. The tooltip **stays until the row is tapped again**, like the game screen's totals tip. It holds,
    in this order, which modes can earn it, for an achievement deliberately kept out of some mode
    (`Achievement.limitedModes`, unless it has a `mode`, whose description names its modes), the style reward line and,
    for a collection row, how many are missing ("Modes with no game finished yet: 4 left"). The dialog says the same,
    with a collection row's list under it. A row with none of them has no gesture.
    - The mode line always names the modes that can earn it, never those that can't: "Can only be earned in the
        'Standard' and 'Tricolour' modes", however long the list. It leaves out the modes it's kept out of and the ones
        whose rules rule it out (`impossibleIn`). Each mode's name stays on one line.
    - **No mode line** for an impossibility alone (Scratched can't be earned without a 5x box, but nothing keeps it out
        of a mode that has one), once it's earned, ever for a hidden row, or in the Game Modes category, whose
        descriptions name their mode.
    - **Rewards.** One sentence on the tooltip ("Earn this to unlock 'Starry' dice, mat, and background": one style's
        items named once, as the unlock banner does). For several, the dialog puts "Earn this to unlock:" over a
        `BulletedList` (`ui/common`): one style a line, after a drawn round bullet, capitalised and without "the"
        ("'Starry' dice style"). The bullet and its gap are in sp, so they grow with the font, and a wrapped line hangs
        clear of the bullets.
    - Rows and banners carry `StyleRewardStar`, **a badge over the icon's corner, never in the text row** (which would
        wrap titles).
    - The star is decorative to TalkBack: the row's action says "Show which style this unlocks", and the banner says
        "Unlocks a style".
    - Superuser mode's long press takes precedence over both.
- **On the Styles screen**, a locked tile shows its first colour under a scrim, with a faded padlock.
    - A tap shows a tooltip with the core requirement ("23 achievements needed").
    - A long press opens a dialog with progress. That dialog is TalkBack's one action, since tooltips aren't announced.
- **A locked saved pick is never overwritten.** Everything that draws a saved style goes through
    `StyleCatalog.unlockedById` (never `byId`; its rule is `StyleRegistry.unlockedId`), which draws the default instead.
    A reset re-locks a style without losing the pick.

## The Styles screen

Two levels (decided 2026-11-10, replacing one page of every category's rows, which was cluttered and slow to open): the
**Styles page**, a field per category showing its pick, and each **category's page**, every style of that category at
once. Routes `styles` and `styles/<category>` (`Screen.styleCategory`), one composable per category.

**Counts.** Every colour of a style counts as one (`StyleRegistry.unlockCount`). A secret colour isn't counted until
it's earned, so the total doesn't hint at it.

- The Styles page's unlocked/total count is drawn like the Achievements page's: a full-width card over the fields, or
    compact after the back arrow in landscape. A category's page has its own count there.
- Each field's heading carries its own `46/96` at its end. Title and count turn brand gold (`primary`) once every one is
    unlocked.

**The Styles page** (`StylesScreen`): three rows of two fields (two rows of three in landscape), filling the page.

- Each field is one grey `Card`, the whole of it the button: the category's name in New Game's small grey heading
    (`titleSmall`, `onSurfaceVariant`), the count at the heading's end; the pick's preview (`StylePickPreview`); and the
    pick's name in Sora bold with its colour under it (a blank line for a one-colour style, so a row's previews match),
    from the start, with the gold chevron at the end, mirrored right to left.
- Portrait: the names along the bottom, under the preview. Landscape, where a card is wide and short: beside it, so
    the preview has the card's height.
- The preview is the pick's tile, still: no badge or dots, scaled to fill its room (`MIN_PREVIEW_SCALE`..
    `MAX_PREVIEW_SCALE`), from the room's start, so every preview lines up with its heading whatever its shape. A
    frame's tab and a box's corner are centred in it (they sit high in a gallery tile, clear of its dots). Its words (a
    frame's "You 24") scale with it, not with the font.
- Rejected on review: a blue field inside the card as New Game's pickers (wasted space; then the previews too small),
    no cards (the art bare on the backdrop), a tight card round the art alone, the chevron on the art's edge, the names
    under the preview in landscape (the previews tiny), and centred previews (the narrow cup stood apart).
- TalkBack: each field is one button - "Dice, button, Ocean, Deep. 60 of 99 unlocked", action "Choose Dice"
    (`common_picker_state_spoken`, `common_picker_choose_action`); the heading is cleared.
- Large fonts (only above 100%): a row's headings put their counts on a second line if any one doesn't fit; titles and
    names shrink to their 100% size before ellipsising; and where the fields would be shorter than their words and a
    96dp preview (`MIN_PREVIEW_HEIGHT`), they keep that height and the page scrolls.

**A category's page** (`StyleCategoryScreen`): titled with the category, its count and the Unlocked switch over a card
of every tile (`StyleCategoryTiles`), the page scrolling. A pick is saved at once and the page stays open.

- **The Unlocked switch** (labelled "All" while off, "Unlocked" while on) sits beside the count, drawn and placed as
    the Achievements page's switch (labelled "All" / "Unearned"): after the count in portrait, at the app bar's end in
    landscape. On, it hides every locked style; it's one setting for every category's page, kept across restarts on
    this device (`SettingsRepository.unlockedOnlyStyles`), as the Unearned switch is (`lockedOnlyAchievements`); neither
    goes in the cloud save. It's left off a page once every style of it is unlocked.
- Hidden locked tiles stay composed out of sight (`HiddenTiles`), so turning it off moves them back rather than
    building them. Locked tiles come after the unlocked ones, so hiding them only shortens the run.
- **The page scrollbar** (`VerticalScrollbar`) sits in the 20dp right margin, and the whole margin is its drag handle.
    It's silent to TalkBack.

**A styles banner** (long-pressed) opens the category's page when its styles are all of one category, over the Styles
page so Back returns there; the styles are brought into view and flashed gold. Styles of several categories open the
Styles page, those fields flashed; opening one hands the request on (`CategoryFocus`), so its styles flash there.

**Unlock All** (the purchase, `PURCHASES.md`) is a tonal button at the Styles page's app bar end: the purchase's mark
(`UnlockAllStylesIcon`) and "Unlock All". No price: Play's sheet shows it.

- For sale: enabled. A tap opens a dialog first (the mark, "Unlock All", what it unlocks, and that every style
    can be earned for free, so paying is a shortcut and a way to support DiceFive); its Continue opens Play's sheet,
    Cancel or back nothing. Play not yet answered: disabled. A payment pending: disabled, "Payment
    pending". Owned (bought, cached or a debug build's pretence) or no store: no button.
- **The title never moves for it.** `PageTopBar` tells the page when the actions push its title off-centre or onto two
    lines (`onTitleCrowded`); the button then drops its words for that screen size, font scale and label, leaving the
    mark (a 320dp phone at 1.3x font; "Payment pending" until about 411dp).
- TalkBack: "Unlock all styles, button", with "Payment pending" as its state; the drawn words are cleared. It comes
    before the first field. A purchase completing on the page toasts "All styles unlocked" (`UnlockAllStylesButtonTest`).
- Debug builds: 5 taps on the Styles page's unlocked count pretend every style is owned (`DESIGN.md`, "Superuser modes").

**Tiles.**

- One tile per family. A long press opens a row of previews, one per colour. Colour names are spoken, not shown.
- Picking a cup shakes and tips it (`CupPickShake`); picking a die rolls it through six faces (`DicePickRoll`). These
    are only visual.
- Tiles are radio buttons: style and colour name, selected state, and position in the set.
- **Spacing** (`TileSpacing`, from the card's width): each tile takes its preview's width (a longer name spills into
    the gaps); the gap is `TILE_SPACING` (12dp), closing to `GALLERY_MIN_TILE_SPACING` (4dp) when that fits one more
    tile; sides are `TILES_SIDE_PADDING` (8dp). On a 360dp phone this fits four dice, cups or backgrounds, and three
    mats, frames or boxes (`StyleCategoryPageTest`).
- **Tile sizes.** Mats are 97x65dp (the tray's shape, cut 10% so three fit). Backgrounds are 72x80dp (the board's
    shape). Boxes are 97x76dp like frames, a corner of a scorecard at 62% of compact boxes (`BoxPreview`). The cups
    "Treasure Chest" and "Picnic Basket" were renamed "Treasure" and "Picnic" so their names fit.

**The warm-ups**: a page's first open after launch is slow, because the drawing code runs for the first time. Two, by
the maintainer's choice (2026-11-10): the menu warms only the Styles page, and the category pages are warmed only once
the Styles page has been opened.

- `StylesWarmUp`, on the menu: 600ms after it settles, the Styles page's empty frame, then the whole page
    (`driftingDice = false`), once per process.
- `StyleCategoriesWarmUp`, under the Styles page: 600ms after it opens, every tile three at a time, a category at a
    time, then each category's page. Once per process, carrying on from the pass it had reached
    (`categoryWarmUpDone`) when a category is opened, or the page left, before it's done (`StylesWarmUpTest`).
- Each draws in a 1dp clipped box under its page's opaque backdrop. Compose doesn't cull clipped content.
- The pages and the warm-ups share `StyleCategoryTiles`, `StyleCategoryScaffold` and `StylesScaffold`, so new
    categories are warmed automatically. Keep it that way.

## The Leaderboard's boards

A `ModePagePicker` (`ui/common`) at the top, always shown: a board for each game mode, then Combined
(`ModePage.all`), with a divider after Standard and before Combined, the one on view ticked. Statistics has the
same drop-down for its pages (`DESIGN.md`, "Leaderboard and Statistics"). The board picked is under it, and is remembered
(the `leaderboard_choice` preference, in the cloud save) through screen changes and restarts; a stored mode that no longer
exists opens Standard's.

- **One table for every board** (`LeaderboardTable`, `ui/scores`). Every board looks and behaves alike, so a
    board added later is a new `ModePage` and a source for its pages, nothing more:
    - A card titled with the board's name (a heading, Sora bold), the column headings, then the ranked rows. 1st-3rd are
        in gold, silver, and bronze on every board, with the same gap between podium rows and the same extra room when
        the card ends on one.
    - The card is as tall as its rows, growing until it has the page, then the list scrolls inside it. Its page controls
        are under it, in a row whose height is reserved so a one-page board ends where a two-page one does. In landscape
        they share the drop-down's row (`LeaderboardPagination`) over a 640dp table.
    - A long press on a row shows its date (a mode's board's title says the mode). That is the row's `stateDescription`.
    - **While a page scrolls** (whenever its scrollbar shows), its title row has gold up and down arrows
        (`UpDownButtons`, as on Achievements' category headers, there in the header's own colour) that glide to its top
        and bottom (`smoothScrollToItem`, or jump under reduced motion), each faded and disabled at its own end. They're
        at the row's end, in the same place signed in or out, and like Global take no height. Where all won't fit,
        Global goes to its globe first, then the title steps down in size (`ShrinkThenWrapText`). Only when even its
        smallest readable size won't fit does it take two lines, which grows the card ("Third Wind" at 200%). TalkBack
        hears "Go to the top of the page" and "Go to the bottom of the page".
    - **Combined** holds every mode's games: each player's name is in its mode's colour (`UI.md`, "Mode colours"), the
        podium rows keep their gold, silver, and bronze, and a long press adds the mode under the date.
    - A board with no scores shows a card saying so instead: "No scores in `<board>` yet". Every board is listed, scored
        or not.
- A board is read the first time it is picked and kept. The switch waits, with the board on view still up, until the new
    one is read, then gives way in one frame (no empty page).
- Every board lists only games played with no modifier on (`playedUnmodified`), as online boards do.
- **Global** (signed in to a service): a text button with a globe in the middle of a mode's title row, moving along only to
    clear a long title, opening that mode's board on the service (`GlobalLeaderboardButton`). It takes **no height**: the
    title sets the row's, and the button is centred on it, spilling into the card's padding, so the card doesn't move on
    signing in. Where the title and the label won't share the line (large fonts, narrow phones) it's the globe alone. On a
    board with no arrows, that keeps the title from wrapping. TalkBack hears "Global
    leaderboard for `<mode>` on `<service>`", a button. Combined has none: it has no board on the service.
- **TalkBack.** The field is one drop-down ("Leaderboard, Standard"), with state Collapsed or Expanded. Each menu row is
    a radio button, the picked one "Selected", with its place in the set. Board titles are headings.
- Rows use `OnDemandTooltip`, because a `TooltipBox` per row cost real first-frame time.
- **`ScoresWarmUp`.** 2s after the menu settles, it draws Standard's board, then Combined, once from a made-up board
    (`ScoresViewModel(WarmUpBoard)`, no database), the same way as `StylesWarmUp` (`ScoresWarmUpTest`). The lag was
    first-run code, not the number of entries.

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
- It skips a 120Hz screen's every other refresh (60 redraws a second; `DriftState.onFrame`) without changing speed,
    and never redraws under 60 where the screen can: 90Hz stays at 90. `DriftState.run` sleeps 7ms after each move, so
    the refresh it would skip doesn't wake the app (`DriftFrameRateTest`).
- It holds where it is on an idle screen, as all ambient decoration does (`ScreenIdle`; `UI.md`, "Reduced motion").

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
