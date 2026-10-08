# DiceFive: UI reference

How the app's interface is put together, and why. `.claude/DESIGN.md` covers the build plan
and the game rules; this file covers the look, the shared chrome, and the decisions that are
easy to undo by accident.

**The one-line version**: everything outside the game board is stock Material 3 wearing a
brand-seeded palette. The board is the exception - it owns its own art and does not use
colour roles.

All of the UI described here is shared Compose Multiplatform code in
`app/shared/src/commonMain/kotlin/net/zodac/dicefive/ui/` - package paths below (`ui/common/` and so on)
are relative to that - and it must stay platform-neutral: no Android or JVM API (the iOS compile
fails the build), resources through `Res.*` from `app/shared/src/commonMain/composeResources/`, and
anything the device has to do through `platform/PlatformServices`. See `IOS_SUPPORT.md`.

---

## Two visual systems, deliberately separate

| | App chrome | Game board |
|---|---|---|
| Where | menu, setup, scores, settings, achievements, about, results | `ui/game/` while a game is in progress |
| Colours from | `MaterialTheme.colorScheme` roles | fixed values in `Color.kt`'s game-table block |
| Swapped by | nothing - there is one colour scheme, dark, in `Theme.kt` | `ui/game/style/GameVisualTheme` |

The board is a rendered object (felt, ivory dice, a casino shaker cup), not chrome. Recolouring it
would mean re-drawing the art, so it holds its own palette and its own swap point.
Don't "fix" the board by moving it onto colour roles; don't hardcode chrome colours to match
the board.

The board's first skins keep their colours as named constants in `Color.kt`'s game-table block.
The Styles catalog's later styles (dozens of colour variants) keep theirs as literal palettes
beside their entries in `ui/game/style/StyleCatalog.kt` instead - each is only ever used by that one
entry, and a few hundred one-use names in `Color.kt` would bury the ones that are shared. Either
way they're fixed values, never colour roles.

## Colour

`ui/theme/Color.kt` holds M3 tonal-palette values. Each palette is one hue at a fixed chroma
across the standard tones (0-100, where the number is CIELAB lightness), and every role is one
tone from one palette, following M3's role-to-tone mapping. That's what makes the contrast
pairs - `primary`/`onPrimary`, `surface`/`onSurface` - reliable instead of eyeballed.

Seeds:

| Role | Seed | What it is |
|---|---|---|
| primary | `#FFC14D` | the gold the board already uses for "press this" |
| secondary | `#1E4B86` | the felt blue of the table |
| tertiary | `#1B5E20` | DiceFive's original brand green |
| neutral / neutral variant | the same blue at very low chroma | keeps greys cool rather than dead |
| error | M3 baseline red | no reason to brand "something went wrong" |

**Don't nudge individual values by hand** - that's how a palette stops being a palette. To
change the look, change a seed and regenerate the whole set (Material Theme Builder, or the
tonal maths in the M3 spec), then paste the result in.

**Dark only.** There is no light scheme and no theme option in Settings (both were removed):
`DiceFiveTheme` takes no arguments and ignores the system's dark-mode setting. Two things outside
Compose follow from that and are easy to miss: `MainActivity` passes `SystemBarStyle.dark` to
`enableEdgeToEdge` (the default follows the system, so a phone in light mode would draw dark
status bar icons over the dark page), and `themes.xml`'s window theme is the dark
`Theme.Material.NoActionBar`, so the window behind the first frame isn't white.

**No dynamic colour.** M3 recommends deriving the palette from the user's wallpaper. The
navy-and-gold table is the game's identity and shouldn't change per device, so `Theme.kt` uses
a static scheme. Swapping in `dynamicDarkColorScheme(context)` on API 31+ is a two-line
change if that's ever wanted.

**No typography or shape overrides.** The M3 type and shape scales are used as shipped.
`Type.kt` used to override two styles out of fifteen with hand-set sizes and was deleted -
that's how an app quietly loses the system's sizing, tracking and optical corrections.
Per-use deviations (`style = MaterialTheme.typography.titleMedium` on a button label) belong
at the call site.

**The brand face, Sora (`SoraFontFamily` in `AppLogo.kt`), is a call-site choice for brand
marks, not body text.** It's on the wordmark, page and dialog titles, Rules page headings, the
Leaderboard's mode-card titles, an unlock banner's title, the labels in the board's totals
tooltip, the New Game screen's player count (`SegmentedChoiceRow(brandFont = true)`) and
User/CPU chips, the game mode and modifier names and every modifier value - their picker rows,
the timer lengths, the steppers ("4 rolls", "20%") and the typed stored-rolls cap - plus the New
Game fields' headlines ("Standard", "3 enabled"). The descriptions beneath those names stay in the
system font: bold-only Sora made them heavy and wrapped three of them. Then the game's own marks: scorecard tile labels ("3x", "2+2", "Alibi"), Hit List targets,
corner badges, the rolls left by the cup, and the scores on the player tabs and Game Over.
Everything else - body, buttons, labels, text fields, small grey section headings, tooltips'
text - stays on the system font. **Columns of numbers stay on the system font too** (the
Leaderboard's scores, Statistics, the totals tooltip's values, with thousands commas via
`grouped()`): Sora's digits are proportional, so right-aligned numbers don't stack digit under
digit. The maintainer turned Sora down there for exactly that; it has tabular digits (`tnum`) if
it's ever wanted. Two limits: the file holds only weight
700 (ask for `FontWeight.Bold`), and only ASCII plus Latin-1, so **never put a player's name or
other free typed text in Sora** - a name in another script or with an emoji would mix two faces.
(The stored-rolls cap is the one typed field in Sora: it only ever takes the digits 0-9.) A new
fixed string in Sora must stay inside U+0020-007E / U+00A0-00FF, or the font must be re-cut (the
recipe is on `SoraFontFamily`).

**`primary` is the one brand accent, and it means "this is gold" everywhere it's used** - the
Statistics max score, the Leaderboard/Statistics scrollbar thumb, the backdrop's spotlight, a
page title (`ScreenScaffold`'s top app bar). When something needs to read as "branded" or
"emphasised," reach for `colorScheme.primary` (text/icon tint) before anything else - not
`primaryContainer` as a background fill, which is a *different, paler* tone meant to sit behind
`onPrimaryContainer` content, not to read as "the gold." Using it for something plain like a
title's own container just introduces a second, unfamiliar accent tone next to the one the rest
of the app already uses - two brand colours where there should be one. If gold-as-background is
ever wanted somewhere, match an existing use (a filled button, the unlock achievement banner)
rather than inventing a new container/tint combination for it.

**The one deliberate exception: fixed, non-role colours for a fixed meaning M3 has no role
for.** `Color.kt`'s game-table palette (the board's felt/ivory/leather) and `PlayerColors`
(cycled by player-tab index) are both like this already - colours that mean one specific thing
regardless of theme, not a themeable role. The Leaderboard's podium follows the same pattern:
1st place reuses `primary` (it already *is* the brand gold), but 2nd/3rd read as silver/bronze,
which nothing in the M3 role set provides - so `Silver` and `Bronze` exist as fixed values,
picked for contrast against the dark page background the same way `Primary` is. Reach for this pattern only when
a role genuinely doesn't exist for what you mean (a medal colour, a fixed player identity) -
not as a way around picking the right *existing* role, which was the mistake above.

### Material 3 Expressive: why we're not on it

`material3` 1.4.0 is the newest **stable** release and ships the Expressive-era components,
but its `MaterialExpressiveTheme` is `internal`. The public expressive theme entry point -
with `MotionScheme` and `ButtonGroup` - only exists in the `1.5.0-alpha` line, whose AAR
declares `minCompileSdk=37` and `minAndroidGradlePluginVersion=9.1.0`.

So Expressive costs an AGP 9 migration and a compileSdk bump, for an alpha dependency in the
release pipeline. Not worth it yet. The Compose BOM is pinned at `2026.06.01`, which is the
newest BOM whose Compose UI still runs on AGP 8.13.2 / compileSdk 35 (2026.08+ pulls UI 1.12,
which needs compileSdk 37). Revisit when 1.5.0 is stable.

## Shared chrome (`ui/common/`)

| File | What it is |
|---|---|
| `BrandBackdrop.kt` | the app's one piece of scenery: surface gradient, `primary` spotlight, faint dice watermark (`showDice = false` drops the dice: the in-game screen sits on the plain gradient). Built from colour roles, so it tracks the theme. Quiet enough that ordinary components sit on it unmodified. |
| `ScreenScaffold.kt` | the frame for every non-menu page: backdrop + M3 top app bar with a back arrow . The bar itself stays transparent over the backdrop; its title is bold and tinted `primary` (see "Colour" above) rather than left at the M3 default. Its content (not the app bar) fades in over `PAGE_CONTENT_FADE_IN_MILLIS` (100ms) when a page opens, so whatever lands a frame or two late - a loaded list, a Styles row centring on its pick - eases in with the rest rather than popping in. Also holds `PageColumn`, and takes an optional `footer` (a `FooterPill`, for scrollable pages) floated at the bottom centre with the content padded by its measured height - Settings' version. Its app bar is also `PageTopBar` on its own (back arrow optional), which the results screen uses so its title sits where every page's does. A scrollable page can narrow its side margin with `horizontalPadding` (24dp by default); the scorecard review uses the game screen's 16dp so the board's pieces come out the size they were in play. |
| `DiceFiveDialog.kt` | the app's one dialog shape, so the menu and the board ask questions the same way. Its title is set like a page title - Sora, bold, `primary` gold, at `headlineSmall` - as are the pickers' (`PickerDialog`) and the About and Licences dialogs'; a new dialog's title should match. |
| `AppLogo.kt` | placeholder app mark, built from the game's own dice via `IvoryDiceStyle`. |
| `FooterPill.kt` | the small gold pill pinned over the bottom of a page: the Rules page's "2 of 6" and Settings' version. Spoken forms and live regions go on its `modifier`. |
| `Scrollbar.kt` | `LazyListScrollbar`, a `BoxScope` extension drawing a minimal scroll indicator over a `LazyColumn` - stock Compose has none for Android. Shared by the Leaderboard and Statistics screens. Also `VerticalScrollbar`, a draggable bar for a plain `verticalScroll` column (the Styles page, placed in its side margin), and `HorizontalScrollbar`, a bar placed under a horizontally scrolling row: one overload for a plain `Row` (`ScrollState`, exact) and one for a `LazyRow` (`LazyListState`, estimated from near-uniform items) - the Styles screen's colour pop-up and tile rows. |
| `SegmentedChoiceRow.kt` | the app's one segmented-button row, generic over the option type. Every use drops the stock M3 checkmark-on-select icon (`icon = {}`) - reserving space for it crowded a label out at some of the widths this app uses it at (AI difficulty, three options in a third-width column). Used by player count, AI difficulty and turn timer. Takes `enabled` for a choice the rest of the form overrides - the turn timer while a mode with its own timer (Quickfire) is picked. It takes an optional per-option glyph: the turn timer's "None" is a crossed-out timer icon with a "No timer" content description, not a word. |

### PageColumn

A page body that fills the screen but scrolls when it can't fit (landscape, split-screen, a
large display font). The inner column is held to at least the viewport height, so on a normal
portrait phone nothing scrolls and `Modifier.weight` spacers inside still have real space to
divide up.

**The trap it exists to avoid**: a `weight` inside an unbounded scroll collapses to nothing.
If you put weighted children in a scrolling column without a `heightIn(min = ...)`, they
silently vanish.

### Insets and edge-to-edge

`MainActivity` calls `enableEdgeToEdge()` - the platform default at targetSdk 35, not an
opt-in. Each piece then handles its own insets, and the rule is **exactly one** of these
applies to any given page:

- a page inside `ScreenScaffold` gets the Scaffold's `innerPadding` (insets + app bar height),
  passed to `PageColumn` as `contentPadding`;
- a standalone page (menu, results) takes `PageColumn`'s default, which is the system bars.

Passing both insets it twice; passing neither puts content under the status bar clock.

No pinned bottom bar for a page's main action: a button fixed to the foot of the screen is
exactly where an achievement banner lands on top of it. The New Game screen's Start Game button
follows the form instead - the form scrolls inside `Modifier.weight(1f, fill = false)` with the
button after it, so the button sits right under a short form and only ends up held at the
bottom once the form is too tall to fit and has to scroll.

## Component conventions

- **`collectAsStateWithLifecycle`, never `collectAsState`**, for a flow read into a composable, so
  collection stops while the app is in the background instead of carrying on for nothing (a
  `Flow` needs `initialValue =`; a `StateFlow` starts from its own value). It's common code -
  `lifecycle-runtime-compose` - so iOS gets the same.
- **Stock M3 components**, themed by the scheme. No hand-built buttons. A gradient-and-gloss
  button set was written and then deleted - it re-implemented state layers, ripple and
  disabled colours worse than the framework does.
- **One filled button per screen**, tonal for the rest (the menu: filled Play, tonal
  destinations). That's M3's emphasis hierarchy, and it stops five identical slabs competing.
- **The menu's Play button splits when there's a saved game** (`MenuScreen.PlayButton`): the same
  56dp pill, cut down the middle by a 2dp gap into New Game | Continue, each half its own stock
  `Button` with the pill's rounded end on its outer side and a straight inner edge. It replaced a
  "Resume game?" dialog behind a single Play. The nearest M3 pattern is the Expressive *connected
  button group* (equal buttons, a 2dp gap, flattened inner corners) - not M3's *split button*, which
  is one action plus a menu, nor *segmented buttons*, which are for choosing an option and which M3
  says not to use for actions. Both halves stay filled `primary`: neither is destructive (New Game
  only opens setup; the save is replaced only when a new game starts), so neither is played down.
  Two stock buttons give each half its own ripple, state layer, touch target and TalkBack node. The
  Expressive group's inner-corner press morph needs `ButtonGroup` (1.5.0, see above) - not built by
  hand.
- **Segmented buttons** (`ui/common/SegmentedChoiceRow.kt`) for small exclusive sets that fit one
  line: player count 1-4, AI difficulty, turn timer.
- **`ChoicePicker` and `ModifierPicker`** (`ui/common/ChoicePicker.kt`) for options that are long or
  need a description each. Closed, a picker is one outlined field (headline, supporting line,
  dropdown arrow), so the form doesn't grow with the option count. Tapped, it opens a full-screen page
  (a `Dialog` window around `ScreenScaffold`, so the setup screen and its state stay put; the top bar's back arrow and a bottom button both close it) whose list fills the room and scrolls with `LazyListScrollbar`. `ChoicePicker` picks one (game mode: radio
  rows, chosen = closed at once, Cancel). `ModifierPicker` is for modifiers - each in a card of its own (no heading; the switch row names it) holding a switch row,
  plus a `SegmentedChoiceRow` of values, a `ModifierStepper` (Number of Rolls: nine segments don't fit) or a `ModifierNumberField` (Stored Rolls' cap; digits only) (shown only while the modifier is on, opening beneath its switch over 220ms - expand and fade, none under reduced motion; the switch itself never moves, only the cards below it), described by a `ModifierSetting`; changes apply
  live and the modal closes with Done; a mode that overrides a modifier locks it with a note. **All
  styling lives in the file's two shells, `PickerField` and `PickerDialog`** (and `PickerRowText`),
  so re-theming is one place - add new pickers as contents of those, not new chrome. To add a
  modifier: a `ModifierSetting` in the setup screen's list, plus a page of its own at the end of the Rules
  page's Modifiers group (`RULES_GROUPS`), titled with the modifier's name. TalkBack: the field is a `DropdownList` named by title with its value as state;
  rows are radio/switch with collection positions. Tested in `ChoicePickerTest`; not heard on a device.
- **An option the rest of the form overrides is disabled, not hidden** - the Turn Timer row was, while
  the first Quickfire was picked (`ModifierSetting.lockedNote` is the hook; no mode uses it now). Hiding it would move everything below; disabling
  it keeps the layout still and shows the choice doesn't apply. The player's pick is kept, so it's
  back when they pick another mode.
- **Cards** group a section. A `ListItem` inside a Card needs
  `ListItemDefaults.colors(containerColor = Color.Transparent)`, or it paints a second,
  slightly different surface on top of the card's.
- **`CONTENT_MAX_WIDTH` (460dp)** caps page width so text doesn't stretch into an unreadable
  line on a tablet.
- **Back navigation is on screen**, via the top app bar - not the system gesture alone. That's
  for sub-screens pushed onto the stack. The Menu is the nav graph's start destination and
  intentionally has no `BackHandler`: with nothing left on the stack, the system gesture/button
  falls through to closing the app, which is standard Android/Material behaviour for a root
  screen (a confirmation dialog or an in-app "Quit" button on the root are both explicitly
  discouraged by Android's back-navigation guidance - the gesture/button/home already do that
  job). Don't add either. The one in-game exception (`GameScreen`'s `BackHandler`) confirms
  before *leaving a game*, not before exiting the app - it's guarding against losing in-progress
  state, which the Menu has none of.

## The achievements list

One list, not two. Achievements are grouped by theme with a quiet all-caps subheader - pinned to
the top of the list as a `stickyHeader` while its group scrolls by, styled as a plain `Card` like
everything else on the screen rather than a bare `Surface` (which has no default shape, hence
square corners if you reach for it) - and run easiest-first within each theme, which is just
`Achievement`'s declaration order, so the catalogue is the single place that ordering is decided.
The exception is Easter Eggs, which have no ladder and run alphabetically by title instead.
Unlocking one doesn't move it: it stays in its ladder and is highlighted in place instead (its own
icon in place of the generic question mark every locked row shows, plus a raised
`secondaryContainer` card), so a ladder always reads as a ladder, earned rungs and all, rather
than the earned ones jumping out to a separate section. There is no "hide unlocked" filter - it
was tried and removed; the unlocked/total count above the list is centred now that nothing else
shares that row.

Every achievement has its own icon (`ui/achievements/AchievementIcons.kt`, a `@Composable` `when`
over the enum, composable so a branch can use `ImageVector.vectorResource` for a bespoke drawable
such as `ic_stairs` or `ic_cowboy_hat` where Material has nothing that fits), picked to hint at what it's about - not a field on `Achievement` itself, so the model stays
a plain data catalogue with no Compose dependency, the same reason `ui/game/CategoryIcon.kt` maps
`ScoreCategory` to a glyph externally rather than the enum carrying one. Locked rows all show the
same generic question-mark glyph regardless of which achievement they are, so a locked row is
never a spoiler for what it takes to unlock it - only the achievement's own icon (once unlocked)
and its title/description (gated separately by `AchievementVisibility`) reveal that.

**Every Easter Eggs achievement's icon is a fixed colour, not the ambient tint every other
achievement's icon takes.** An unlocked icon is normally re-tinted by whatever row/banner it sits
in (`Achievement.iconTintOrUnspecified` just passes the given tint straight through); an Easter Egg
overrides that with its own literal colour instead, because these are the one category whose icon
*means* a specific colour regardless of container - Luck of the Irish's flag (real green/white/
orange fills, `Color.Unspecified` so `Icon` skips its colour filter and shows them) and Big Fan's
heart (forced red via `iconTintOrUnspecified`, since `Icons.Filled.Favorite` is a single-colour
vector with no fills of its own to preserve). A new Easter Egg's icon should follow the same rule -
add its case to `iconTintOrUnspecified` rather than leaving it to inherit the ambient tint.

An earlier version *did* split locked-first/unlocked-after (unlocked flat and newest-first, a
history rather than a to-do list) - reverted because it scattered a themed ladder in two: an
earned achievement disappeared from its group into an unrelated timeline, so seeing "how far along
this ladder am I" meant checking two different parts of the screen.

Each category header carries up/down arrows on its right that jump to the previous/next category's
header (`animateScrollToItem` to that header's index, where it then pins) - the list is long enough
that getting from Milestones to Collection was a lot of flinging. At the first/last category the
arrow that has nowhere to go is greyed out rather than removed, so the pair never shifts. They're
stock `IconButton`s, so the header is the 48dp minimum touch height, not the bare label's.

**The list deliberately scrolls past its last row** - a trailing blank item (`tail-space`) sized
as viewport - pinned header - last row, so the lowest scroll leaves the final row resting just
under the "x of y unlocked" card and the pinned category header, with blank space below. This is a
design choice for this page only, not a general pattern (no other list does it, and none should
without the same reason). Without it the list clamps at its end, so a row near the bottom - the
Easter Eggs, which are last - can never reach the middle of the screen, and a banner long press
(which centres the row) left it sitting under the unlock banner. The height is measured from the
real header and last row once they've been on screen (estimates only cover the first frames).
It has to be a trailing *item*, not `contentPadding`: bottom padding shrinks
`layoutInfo.viewportEndOffset`, which `centeredScrollOffset` reads, so centring would drift.
The spacer is empty, so TalkBack has nothing to read and gains no focus stop.

Rows are clipped at the pinned header's bottom edge (`Modifier.hiddenUnderPinnedHeader`, read
from `layoutInfo` in the draw pass via the `contentType` tags) instead of sliding under it:
otherwise they show through its rounded corners. The scroll position is kept in a process-lifetime
`object` (`AchievementsScrollMemory`), because popping the screen discards both its ViewModel and
anything `rememberSaveable`d. It is restored only after the list has loaded, since a `LazyColumn`
that first lays out empty clamps any requested position back to the top. The same restore
approach applies to any other list screen that needs to remember its position.

Don't sort the list alphabetically. That was the very first version, and it put "Dice Deity" nine
rows from "High Roller".

## Achievement banners

`AchievementBannerHost` wraps the **whole app** in `MainActivity`, outside the `NavHost` rather
than inside a destination. That placement is the point: the end of a game can unlock a dozen at
once, and the burst has to keep playing as the player moves from the board to the results screen
and on to the menu. A per-screen overlay would cut it off at the first navigation.

It listens on `AchievementEvents`, a process-wide `SharedFlow` — the two ends have no scope in
common (a "play"-graph-scoped `GameViewModel` raises them; an overlay above the `NavHost` shows
them), and achievements are per device, which is the same scope as the process.

The stack behaviour, and why each number is what it is (the constants are at the top of
`AchievementBannerHost.kt`):

| | Value | Why |
|---|---|---|
| Position | bottom-anchored, overlapping stack: the longest-queued banner in front, each newer one peeking out 14dp above the one in front of it | keeps the board, the dice tray and the scorecard clear; a burst no longer fills the screen |
| Fade in | 180ms, as soon as it joins the stack | every banner in a burst is visible right away, even the ones waiting behind |
| Hold | 4s at full opacity - 5s on screen in all, with the fades | long enough to read a title and description, short enough not to sit in the way |
| Fade out | 820ms | a snap-out in the middle of a burst reads as a glitch |
| Stagger | 300ms between arrivals | a burst deals like cards instead of landing as a wall |
| Cap | 4 on screen | the rest **wait** rather than being dropped — the collector suspends on `snapshotFlow { banners.size }` until a slot frees |
| Swipe | 15% of the banner's width, either direction; it leaves in 180ms | clears one early; the event flow's buffer holds the backlog meanwhile. 25% needed too firm a swipe |

Only the front banner is interactive, and only it counts down: one waiting behind it starts its
own full hold once it's promoted, so a burst plays out one banner after another rather than all
clearing together. A finger on the front banner pauses its countdown too (and pulls it back to
full opacity if it had started fading), with a fresh full hold once the finger lifts - so a slow
swipe, or a press to keep it up, can't lose it mid-gesture. An unlock always takes the front over a progress nudge; otherwise a new
arrival joins the back, so it can't cut the queue.

**Tap the front banner to pause its countdown, tap again to resume.** A pause/play glyph (a dark
circle, top-right) pops up on each tap and fades after 1s (`HOLD_INDICATOR_MILLIS`). The glyph is a
child of the banner, so it shares the banner's alpha: resuming with under a second left, it goes
with the banner rather than outliving it. Resuming carries on with the time that was left
(`holdRemainingMillis`, measured with a monotonic mark, so a stretch in the background counts
against it); a swipe or long press starts a fresh hold. Once the glyph fades nothing shows that the
banner is held - by design. TalkBack gets the tap as a "Pause countdown"/"Resume countdown"
action on the banner, since the raw gesture isn't reachable otherwise; not heard on a device.

Long-pressing the front banner jumps to that achievement on the Achievements screen (a styles
banner opens the Styles screen instead). Mid-game,
with "confirm before leaving" on, that asks first - and every banner's countdown is paused while the
confirmation is up, restarting at a full hold when it closes.

The stack lives in its own non-modal `Dialog` window, not in the app's content, so a banner that
fires while another dialog is open still draws above it. The window is full width but wraps the
stack's height; it must never be full-screen (`fillMaxSize`), or it swallows every touch meant for the
screen beneath - the not-touch-modal flag only passes touches outside the window's bounds. Nor may it
dim: `FLAG_DIM_BEHIND` is cleared, not set to a dim of 0. With the flag on, the system lays an invisible
full-screen dim layer under the banner, and from Android 12 it drops taps on the window beneath as an
untrusted occlusion - the About dialog's close button did nothing while "Who Made This?" was showing.

Two achievement variants, deliberately unequal: an **unlock** banner is `primaryContainer` with the
achievement's own icon (`Achievement.icon`, the same one its unlocked row shows - a generic trophy
made a burst of unlocks read as a stack of identical cups) and a title plus up to two lines of
description, with a small star badge on the icon's corner when the achievement unlocks a style (see "Style locks"); a
**progress** banner is quieter (`surfaceContainerHigh`, one line plus a thin
`LinearProgressIndicator` whose count and bar climb from the old value to the new over 700ms),
so a run of "2 of 3" nudges can never be mistaken for the real thing.

A third, the **styles** banner (`AchievementEvent.StylesUnlocked`), says the player has earned
enough achievements to unlock one or more styles by count (`StyleUnlock.AchievementCount`). It
looks unlike an achievement on purpose: `tertiaryContainer`, and the style star itself as its icon,
in a circle rather than the achievement's square. It's the same size as an unlock banner (title
"Style Unlocked"/"Styles Unlocked", two-line description "Earned 23 achievements: the 'Frosted'
dice style", naming two and counting the rest). **One update makes at most one styles banner**,
however many styles and categories it unlocks: `announce(update)` (`ui/achievements/
AchievementAnnouncements.kt`) - which every place that records an unlock calls - emits the unlocks,
then one styles banner for every count lock crossed between `AchievementUpdate.countedUnlocksBefore`
and `countedUnlocksAfter` (`stylesUnlockedByCount`), then the progress nudges. A style behind one
specific achievement never gets one - that achievement's own banner carries the star. It sorts with
the unlocks, ahead of progress nudges. Long-pressing it opens the Styles screen (behind the same
leave-game confirmation). TalkBack hears every style it unlocked, not just the two drawn: "Styles
unlocked: Frosted dice, Velvet mat, Oak background. Earned 30 achievements"
(`StylesUnlockedBannerSemanticsTest`).

Both show the achievement's title on ONE line (`BannerTitle`, via `ShrinkThenWrapText` with a
one-line limit): titleMedium's 16sp when it fits, stepping down 0.5sp at a time to 12sp, and only
ellipsised past that - never wrapped, so every unlock banner is the same height. That is what
`MAX_ACHIEVEMENT_TITLE_LENGTH` (38 characters, enforced by `AchievementTextTest`) is for; the longest
title, "Where We're Going, We Don't Need Rules", is ~221dp at 12sp against a ~244dp text column on a
360dp phone (estimated from Roboto's metrics, not measured), so a narrower screen ellipsises it
first. The unlock banner's description is bodySmall (12sp, never shrunk), always exactly two lines,
ellipsised past that (`BannerDescription`) - at 360dp the longest current ones just fit two lines
(estimated); at 320dp about eight need a third and are cut.

## Style locks

Every category's Classic style is free; every other style (`StyleFamily.unlock`, a `StyleUnlock`)
waits on either a number of achievements or one specific achievement. The lock is on the style, not
the colour: once it's met, all of its colours are available. Secret achievements never count
towards a number (`AchievementsState.countedUnlocks`) - they're easter eggs, not checklist items.
For now every lock is a distinct, arbitrarily picked count up to the number of non-secret
achievements; `StyleCatalogTest` keeps them distinct and earnable.

A style locked behind one **secret** achievement is a secret style (`StyleUnlock.hiddenWhileLocked`):
it isn't on the Styles screen at all, padlock or not, until that achievement is earned. Every easter
egg has one: the Martini cup (Shaken, Not Tapped), the Floating Dice background (Not Those Dice!,
the main menu's backdrop - same colours, same drifting dice - as a table background) and the Maths
dice, all three colours (The Solution). Others are
secret single **colours** of otherwise ordinary, count-locked styles (`StyleColour.secretAchievement`),
which the tile's colour row and dots leave out until it's earned: the Irish dice in Multicolour (Luck
of the Irish), the blue/gold Googly dice (Big Fan), the Flowerpot's plant-stage pots (Greenfingers -
a pot held at each of seedling, bud, opening, sunflower) and the Top Hat with its rabbit always out,
sliding about the opening as the hat is shaken (The Magician's Secret).
Any achievement
that unlocks a specific style (`Achievement.unlocksStyle`) shows a plain tooltip (not a dialog) when
its row is tapped or long-pressed, naming the style - "You've unlocked the 'Multicolour' dice
style!", from `Achievement.styleRewards` and the catalog's `noun` (`StyleRewardTooltip`). The row
and the unlock banner also carry a star (`StyleRewardStar`) - **a badge over the corner of the icon
square, not a widget beside the text**: a star in the text row narrowed the text column and made
titles and descriptions wrap, so it must never take layout width. It's decorative to TalkBack: the
row's one action says "Show which style this unlocks" and the banner's announcement ends "Unlocks a
style". Superuser mode's long press on a row takes precedence, so the tooltip is off while it's on.

On the Styles screen a locked tile shows its first colour under a translucent scrim and a faded padlock, can't be picked.
Tapping it shows an `AppTooltip` with just the core requirement ("`23` achievements needed", "`Big Fan`
achievement needed"); long-pressing it opens a `DiceFiveDialog` with more (and how far along the player
is). A tooltip isn't announced, so TalkBack's one action on the tile is the dialog. **A saved pick whose style is
locked is never overwritten** - everything that draws a style (the game, the menu logo, "Fresh Coat
Of Paint", the Styles screen's check badge) goes through `StyleCatalog.unlockedById`, which draws
the category's default instead. So resetting achievements re-locks without losing a player's pick,
and earning it back restores it. A new place that draws a saved style must use `unlockedById`, not
`byId`.

**The cards are shrunk to fit a screen they only just overflow - worked out in the warm-up, never on
opening.** `StylesWarmUp` (below), after drawing the tiles, also lays the whole page out off screen at full
size and, if that scrolls, at `MIN_PAGE_FIT_SCALE` (75%). The text keeps its size (the cards get a
`Density` with smaller dp but the same sp), so the two heights split the page into the part that scales and
the part that doesn't, and `fittedPageScale` gives the scale that just fits. That `PageFit` (keyed on the
window size, density and font scale) is **saved with the Styles picks** (`styles_page_fit`, in
`SavedStyles`), which load at launch, so every launch after the first knows it from its first frame;
opening the page just reads it - no measuring, no relayout. The warm-up measures again each launch and
saves only a change (a new version of the page can change its height). A page that fits, or that would
need less than 75%, stays full size (the latter scrolls); one opened before any measure exists (a fresh
install, within a couple of seconds of launch) or on a screen it wasn't measured for is full size, and the
warm-up measures again (only that) when the menu next shows on a changed screen. Measuring first in the
warm-up was rejected: it would load the whole page's first-time code onto the menu 0.6s after launch,
the hitch the warm-up's order exists to avoid. Asked for by the maintainer, whose phone (~369 x 816dp)
needs about 76%; a runtime fit measured on opening was rejected as too costly for an already heavy page.
`PageFitTest`, `StylesPageFitTest`; checked on the sandbox emulator at 1080 x 2400, 470dpi.

**Each category's tile row is a `LazyRow`, not a `horizontalScroll` `Row`.** Every tile's art is its
own drawing code, some of it animated, and composing every tile of every category at once (40-odd
and growing) made the first open after a restart hold the menu for several frames before the page
came up. The lazy row composes only what's on screen. It opens with the current pick as its first
item, then centres it once its size is known, and stays invisible until then, fading in over
`PAGE_CONTENT_FADE_IN_MILLIS` like the rest of the page (see `ScreenScaffold`). The picks themselves come from `AppContainer.savedStyles`, one process-lifetime copy of the four ids
and the achievements, loaded when the container is built and shared with the menu's logo - so the
page normally has them on its first frame rather than waiting on its own load.

**Each category card can open as a gallery.** A small grid icon at the end of the card's title
(`GalleryToggle`) switches it from its scrolling row to every tile at once (`GalleryGrid`): the same
tiles at full size, left-aligned like the row, the page scrolling down through them. **The row and the gallery space tiles identically** (`TileSpacing`, computed once from the
card's width, which is why the tiles sit under a `BoxWithConstraints`): each tile takes its
*preview's* width - a name wider than that (at a large font; the cups "Treasure Chest" and "Picnic
Basket" were renamed "Treasure" and "Picnic" so none is at the normal size) centres under it and spills into the gaps rather than widening the tile - with the same gap between. The gap
is `TILE_SPACING` (12dp) where a gallery line has room for it, closing up to `GALLERY_MIN_TILE_SPACING`
(4dp) where that fits one more tile on a line; the tiles' sides are `TILES_SIDE_PADDING` (8dp) in
both. They used to differ (12dp from each tile's full width in the row, the computed gap in the
gallery) - `StylesGalleryTest` now checks every category's step matches. Together that fits four
dice or cups across a 360dp phone (its card leaves ~304dp: 4 x 72dp and 5dp gaps); three mats and
four backgrounds fit too. **A mat's tile is 97 x 65dp** (the dice tray's wide shape, cut 10% from 108 x 72 so three fit
across a gallery - the row matches), and **a background's is 72 x 80dp**: the score board's shape, about
as tall as it's wide (380dp tall, the screen's width less padding), not the mat's - the two used to
share one. **The page has a vertical scrollbar** (`VerticalScrollbar`, `ui/common/Scrollbar.kt`) whenever
it scrolls - an open gallery, a small phone - in ScreenScaffold's 20dp right-hand margin, beside the
cards rather than over them. The whole margin is its handle: dragging it scrolls the page in
proportion, so a long gallery is quick to get through. Silent to TalkBack, which scrolls by its own actions. Rejected on review: tiles shrunk to
0.7x to fit more across; an even grid with every column as wide as the widest tile (wide gaps around
the cups), centred or spread; and a `FlowRow` at each tile's own width with an 8dp gap, which left
almost a whole empty column on a 360dp phone. The toggle is no taller than the title's own line, so
adding it moved nothing; it's off whenever the page opens (`rememberSaveable`, per card). The tiles
are `movableContentOf`, moved between the row and the gallery rather than rebuilt, so switching is
cheap and a cup mid-shake carries on. To TalkBack the toggle is a switch, "Dice gallery, off"
(`StylesGalleryTest`); the tiles keep their row's position in the set ("3 of 21") either way.

**The menu warms the Styles page up (`StylesWarmUp`).** Even lazily, the first open after a launch
was slow in a release build too: it's the first time each tile's drawing code runs, and that's a
one-off cost per process. So once the menu has settled (600ms), `StylesWarmUp` draws the page's
five category cards one per frame, at screen width, in a 1dp clipped box under the menu's opaque
backdrop, then drops them - once per process. Compose doesn't cull clipped content, so the art
really is drawn. The screen and the warm-up share `StyleCategorySection`, so a new category or a
change to how tiles draw is warmed automatically; keep it that way rather than giving the warm-up
its own copy. After the tiles it builds each category's real card, then the screen's empty frame, then the whole
screen (`StylesScaffold`, with `ScreenScaffold(driftingDice = false)` so the menu's shared drift isn't
moved along by a second copy): that first-time code, not the art, was most of the first open's lag
once the tiles were warm. See `BENCHMARKS.md`.

## Accessibility

**Mandatory for every new or changed UI element.** Accessibility is part of building an element, not
a later pass: a change that adds or alters something a player sees or touches isn't done until it's
been through the checklist below, and the report back to the user says what TalkBack now does with
it. The Rules page's tab row is why this is a rule and not a habit: it shipped with stock M3 tabs on
the assumption that stock meant accessible, and TalkBack never said there were more than the three
in view.

### The checklist

1. **Don't assume a stock component is enough - check what it actually sets.** M3 gives a `Tab`
   `Role.Tab` and nothing about its position in the row. Read the component's source (or `javap` its
   classes in the Gradle cache) for the semantics it adds, then fill in what's missing.
2. **What does TalkBack say when it lands here?** A name, a role (button, tab, switch, heading), and
   any state (selected, checked, disabled, "3 rolls left"). Icons that do something need a
   `contentDescription` that names the action, without the word "button" (the role says that);
   decorative ones get `null`.
3. **Does anything visual carry meaning that isn't spoken?** Colour, an edge fade, an icon, a
   position, an animation, a badge. Each needs a spoken twin: the Rules page row's fade is paired with its
   "Tab, 1 of 6", a die's held state with "held". If a sighted player could learn it from the
   screen, a TalkBack user must be able to learn it from the semantics.
4. **One of a set, not all in view?** Give the container `CollectionInfo` and each item
   `CollectionItemInfo`, so TalkBack gives the position and count.
5. **Is every action reachable by TalkBack?** A drag, a swipe-only or long-press gesture, or a
   hand-rolled `pointerInput` is invisible to it - expose it as a semantics action (`onClick` with a
   label, `customActions`), as the dice tray does for hold/release.
6. **One control, one target, one announcement.** A label and its control are one node (merge, or
   make the row the control - see `SwitchSetting`), not two stops that each say half.
7. **Titles are headings** (`semantics { heading() }`), so TalkBack's heading navigation can jump
   between them.
8. **Something that appears and goes by itself** (a banner, a toast-like message) is a polite live
   region with a fixed summary, not its animated text.
9. **Does the text read well aloud?** Notation written for the eye - `5-5-5-2-1`, `15pts`, a
   leading "- " bullet - can come out as "minus", letters or "dash". Where it matters, give TalkBack
   the spoken form.
10. **Sizes**: keep M3's 48dp minimum touch target (don't shrink an `IconButton` or `Tab`), and make
    sure the layout survives a large system font - it should scroll, not clip (see `PageColumn`).

**Verifying**: any semantics added for this get a Robolectric test asserting them, as
`BoardSemanticsTest` and `RulesScreenAccessibilityTest` do - that's the part that can be checked in
the sandbox. What real TalkBack actually speaks can't be, so it goes in the DESIGN.md phase's "Not yet
seen on a device" line and in the report to the user, not claimed as done.

### Known gaps

- **Item 9 on the Rules pages**: category names like "3x" and "5x" are read as written. "pts" is
  spoken as "points" (`spokenPoints`), the example dice rows in full ("Example: 5, 5, 5, 2, 6. The 2
  and 6 don't count. Scores 23 points."), and lists are real numbered steps rather than typed dashes.
- **The system's reduced motion is honoured on Android only** (the app's own "Animations" levels work
  everywhere, but their frame caps are Android-only). `PlatformServices.reduceMotion()` is true while the
  system animation scale is 0 ("Remove animations"); iOS says false until it's wired to
  `UIAccessibility.isReduceMotionEnabled` (see `IOS_SUPPORT.md`), and Compose Multiplatform on iOS has
  no equivalent of the Android behaviour below. **None of it has been seen on a device.** See
  "Reduced motion" below for what it does.
- **Text scaling**: the shrink-to-fit floors are gone - `ShrinkThenWrapText` (`ui/common`) shrinks a
  label to `MIN_READABLE_FONT_SIZE` (12sp) at most, then wraps it (2 lines) instead. It's used for
  player tab names, banner titles and Settings labels. Other `maxLines = 1` sites now allow a second
  line (Scores/Statistics/Game Over names, menu buttons), the Statistics stat cells and the Scores pager
  flow onto a second line, the Scores columns re-split above 1.15x font scale, and the New Game player
  row stacks its controls above 1.05x. What's left: `ScoreGrid`'s numbers stay on one line by design
  (a fixed grid, `overflow = Visible`); 11sp `labelSmall` (M3's smallest) is still used for
  captions; and **none of it has been seen at a large font on a device** -
  Robolectric's text engine never wraps, so `ShrinkThenWrapTextTest` pins only which size and line limit
  are chosen. Try 130% and 200% font on: a 4-player game with 8-character names (and CPUs), the longest
  achievement banners, Settings, New Game, the Leaderboard and Statistics.
- **Timer flash rate unchecked** against the three-flashes-per-second limit (period is
  `TURN_TIMER_FLASH_PERIOD_MILLIS`, a colour fade rather than an on/off flash). Its spoken twin is
  the one-off "Time running out" live region.
- **Contrast**, computed from the palette (dark theme; WCAG 3:1 for graphics, 4.5:1 for text). Not
  measured on the generated M3 roles beyond the pairs below, nor on any style's own art:
  - Fine: tile icon on teal 8.5-12.7:1; gold on the highlighted tile 7.3:1; `onSurfaceVariant` on
    the surface 10.9:1; the selected style's `primary` border 10.9:1.
  - Borderline, and exempt or spoken: a scored tile's icon (0.4 alpha) is 3.2-3.5:1; the disabled
    Undo (0.35 alpha) is 2.4-2.9:1 - disabled controls are exempt, and M3's own disabled is 38%. A
    scored tile against the page is 1.4:1, but its state is spoken and it isn't interactive.
  - **Below 3:1**: an unselected style tile's `outlineVariant` border is 2.0:1 (the art inside
    carries the tile, and the selected one is clear, so low impact - `outline` is 5.8:1 if it's
    ever raised); the locked-style padlock (0.8 alpha over a 0.55 scrim) is ~2.1:1 over light art,
    8.8:1 over dark. The lock is also spoken ("Locked").
- **Checked and fine, no change needed**: Undo is one merged button named "Undo" (its icon is
  decorative; `ScreenReaderSemanticsTest`); the dialog icons are decorative and their message and
  buttons are text; the Scores pager's "Previous" / "Next" sit beside "Page N of M", so they're
  clear in context - "Previous page" / "Next page" would be a nicety, not a fix.
- **Not looked at**: whether a dialog announces a title when it has none (the locked-style
  requirement dialog passes `title = null`), and the Achievements rows' locked/unlocked state
  beyond the icon.

### What's in place

Everything a screen reader needs is added as semantics, never by changing what's drawn:

- **The board's art speaks for itself through semantics** (`BoardSemantics.kt` holds the spoken
  names, as the Rules pages give them): each die is its own node - "Die 2, 5", held or not, with
  hold/release as its action, since the tray's hand-rolled gesture is invisible to TalkBack; each
  score box is one cleared-and-set node with its name, what it scored or would score, and "Score"
  as its action; the cup is "Dice cup, 3 rolls left", a Roll button (disabled while a roll is in hand,
  until its dice settle); player tabs are tabs, the
  scorecard on view selected, each saying the player's place ("tied 2nd place" - the drawn "=2nd"
  is cleared, as it reads "equals"); on another player's scorecard, the box their last turn went in
  adds "last turn's score" (drawn as an outline and bold score in their colour). `BoardSemanticsTest`
  pins the dice and score box actions, the places and the last score.
- **Style tiles are radio buttons** (`StylesScreen`): one node each with the style (and colour) name,
  selected state and position in the row (picking a cup shakes and tips it - `CupPickShake` - and picking a die rolls it through all six
  faces - `DicePickRoll` - both only something to see: the pick is what's announced); the colour chooser is a long-click action, and a locked
  tile's action says how to unlock it. Stats cards read as one sentence with delete as their only
  action; the hidden superuser counter and the logo dice use raw `detectTapGestures`, so TalkBack
  isn't handed an unnamed button. The turn timer says its seconds in words and, once it starts
  flashing, is a polite live region with a fixed message (announced once, not every second).
  **Gotcha:** `clearAndSetSemantics` only overrides what sits *inside* it, so put it *before*
  `clickable`/`combinedClickable` in the chain - after it, the clickable's own `onClick` leaks
  through (`ScreenReaderSemanticsTest` pins this).
- **An on/off setting is its whole row** (`SwitchSetting`: `toggleable(role = Role.Switch)`, the
  `Switch` itself taking no clicks), so the label and switch are one target and one announcement.
- **A field with no visible label gets an accessibility-only one**, and its error as `error(...)`
  - see the New Game name fields.
- **Banners are polite live regions** announcing a fixed summary, not their animated text.
- **A pager announces where it lands**: the Rules page's "2 of 6" footer is spoken as "Modes, page 2 of 6"
  (its count is within the group showing, so it names the group) and is a polite live region, since a swipe between
  `HorizontalPager` pages - or past a group's last page into the next group - says nothing by itself.
- **Anything floated over scrolling content needs matching padding at the content's end**, or its
  last line can never scroll clear (the Rules footer pads each page by its measured height). Know the
  limit: bring-into-view - TalkBack focus, `performScrollTo` - only scrolls a node into the scroll
  area, which still runs under the float.
- **A scrolling tab row says how many tabs it has** (the Rules page's two rows): stock M3 `Tab`s carry only
  `Role.Tab`, so each row sets `CollectionInfo` and each tab `CollectionItemInfo`, for "Tab, 1 of 3" in the group
  row and "Tab, 1 of 6" in the page row - each counted within its own row.
  It's the spoken twin of the row's edge chevrons - sighted or not, nobody should take the tabs in
  view for all of them. The chevrons themselves are cleared from semantics: they'd only be stops
  that repeat what the tabs already say.
- **Titles are headings**: page titles, Styles categories, achievement category headers, Rules
  pages, About sections.

## Motion

Screen transitions are 350ms fades, set on the `NavHost` for all four directions. Navigation
Compose's own default is `fadeIn/fadeOut(tween(700))`, which reads as the app thinking between
a tap and the screen arriving. Set in one place so destinations can't drift apart.

**A looping animation must not run while there's nothing to show.** A `rememberInfiniteTransition`
asks for a frame every frame for as long as it's in the composition, whether or not its value is
used - and read with `by` in a composable, it recomposes that composable every frame too. So:
compose the clock only while it's needed (a highlighted score tile's `GlowBorder`, the cup's
`rememberShakeWobble` while rolling or fading out, the turn timer's last seconds), and where it
can, read the value in the draw phase (`graphicsLayer { }`, `drawBehind { }`) so each tick is a
repaint rather than a recomposition. Every score tile once ran its own glow clock and every cup its
shake clock at weight zero, which kept the whole board recomposing for the entire game.

## The scorecard grid and game modes

The board is **four equal columns in rows shared by two panes** (`boardLayout`). `ScoreGrid` is the left pane:
the upper section down column 1 beside the lower section in column 2, whatever card it's given
(`PlayerState.categories`). `DiceCupPanel` is the right pane, in the same rows: 5x as one wide tile across
columns 3-4 in row 1, then up to two rows of boxes under it (`BoardLayout.sideRows`: Tricolour's colours as a
2x2, or Extended Scores' Evens | Odds / Two Pair), the cup laid over rows 4-5 and Totals/Undo in row 6. The
cup is in the same place whether or not the boxes above it are there, and Totals/Undo are in the last row
(row 8 with eight rows), at the size of a tile in that row - 40dp when compact, below M3's 48dp touch target like
the compact tiles. The cup is centred in the rows between the boxes under 5x and the buttons. Extended Scores wins the place under 5x;
Tricolour's colours then go after the lower section in the grid, two to a row (eight rows).

More than six rows switches every tile to `COMPACT_TILE_SIZE` (40dp, from 48dp) and grows the board to fit
(`scoreBoardHeight`, 390dp for eight rows); the cup's two rows are then shorter than `CUP_SIZE`, so it's
given that height regardless (it needs rows 4-6; Totals/Undo moved below it). Standard and Tricolour are both
six rows and 380dp.

**Hit List's card** (no 5x) puts its featured box (`ScoreCategory.featured`, `BoardLayout.featured`) - the Alibi -
where 5x goes, and its twelve targets fill the grid two to a row in card order (easiest at the top). A target's tile
(`CategoryIcon`'s `TargetIcon`) shows its five places - a number, or a dot for an any place - over its points, sized as
fractions of the tile, not the font: five places must fit a fixed width at any system font, and the spoken name says
the target in full. While a roll is previewed, each named place the dice show gets a short bar under it (white; gold,
with the number, when the die is in its own column) - a bar per place, since a text underline ran neighbouring places
into one number on the renders. Spoken twin: the cell's state adds "3 of 4 rolled, 1 in place", "partial hit, 3 of 4
rolled, 1 in place", "hit, 0 of 3 in place" or "exact hit". A target's tile glows only for a hit; a partial score is gold beside an unlit tile
(`tileLit`), since nearly every target has one and a board of lit tiles hid the hits. The Totals button shows Targets and Alibi on this card (`HitListTotalsButton`). Rendered at
411dp and 360dp - Robolectric only.

**With nothing under it, 5x is a large square** over rows 1-2 (`squareSize`, as tall as both rows, 16dp
corners, its score and bonus beside it); the rows under it are empty.

**Otherwise the 5x tile is a wide tile**, not a square: `CategoryTile(wide = true)` takes the width it's given at the
usual tile height - its right edge level with the right edge of the tile in column 4, the score beside it where
column 4's scores are, and the 5x bonus written inside the tile (there's no room beside it). The cup is drawn over
the two rows it spans, offset from the pane's row height and gap, rather than sized by weights - which would
not line up with the grid's rows.

**Coloured dice keep the dice style, in the roll's colour.** In a mode with `GameMode.dieColours`,
each die's colour is part of the roll, so `DiceTray` draws it with the player's style
`recoloured(palette)` - every `DiceStyle` must implement it (Classic becomes `ColouredClassicDiceStyle`,
the `BeveledDie` in that colour; the rest rebuild themselves from the `DieColourPalette`). The pips,
numbers and digits always take the palette's `pip` and the held ring its `heldRing`, never the
style's own: those were picked to read on the style's own colour and can vanish on the roll's (the
blue D20's gold numbers on a yellow die). Choices worth knowing: neon LCD keeps its dark body and
lights its digit in the colour's `swatch`; Googly sockets go white; the striped Multicolour dice, being only
colours, become a plain die of their shape. `DiceTray` builds every recoloured style once per game
(`TrayDiceStyles`, remembered on the style, the mode's colours and Luck of the Irish - none change
mid-game), so no roll rebuilds one and a die gets the same style object every time it lands a colour.

The colour-box tiles are a flat square of `DieColourPalette.swatch`; Coloured House is Full House's
glyph over three equal-width diagonal stripes (red/yellow/blue corners-and-band, split at two-thirds
of each edge so the diagonal is cut in thirds - equal *areas* was tried first, and left the yellow
band looking much thinner than the corners) in the deeper `stripe` shades, with a soft shadow under
the glyph so white or gold still reads on the yellow band. These colours live in `Color.kt`'s
game-table block - a fixed meaning (the dice's own colours), not a theme role.

**More than one score a box (Third Wind) stacks them** (`StackedScores` in `ScoreGrid.kt`): one line per
slot beside the tile, top to bottom in the order they fill - each filled slot's score, the dice's
preview in the next open one (gold when worth picking), "-" in the rest - `labelMedium` beside a grid
tile (3 x 16sp fills its 48dp) and `bodyMedium` beside the 5x tile, with the bonus line under them. The
tile greys only once every slot is used; on another player's card, the box's last score is in their
colour. Each line (`SlotScore`) keeps its last number laid out under the "-", as a one-slot cell does,
and `ShownScore` compares by value so an unchanged line is skipped: a roll recomposes only the preview
lines (see `BENCHMARKS.md`). The box is one TalkBack node, "Scored 15, 10, would score 20"
(`stackedSpokenState`). The lines exactly fill the tile's height, so a larger system font overflows it:
then the stack scrolls within the tile's height instead (`heightIn` + `verticalScroll`), kept on the
line that matters - the next slot to score, or a full box's last score (`stackedScrollTarget`) - and
re-scrolled only when that changes; whichever edge has more beyond it fades out (an offscreen layer
with a `DstOut` gradient, drawn only while it overflows, read in the draw phase). The stack is as wide
as its numbers (`wrapContentWidth(unbounded = true)`), since the scroll's clip and the fade's layer
otherwise cut off the digits that spill into the gap beside the column. Rendered at 100%, 130% (two
lines show) and 200% (one) - Robolectric only, not seen on a device. Pre-existing and not Third
Wind's, and since fixed: the Upper/Bonus/Lower lines beside the cup wrapped at 130% and broke at 200%
- they're now behind the Totals button (below).

**The section totals are a button** (`TotalsButton`), not three lines beside the cup: an icon-only Σ,
styled like Undo and sitting just left of it (both at the right of the row), that shows Upper, Bonus
and Lower in the shared `AppTooltip` on a tap or a long press - as a two-column table (`TotalsTable`):
labels left in Sora, numbers right in the system font with thousands commas, the Bonus line gold once
earned. The glyph turns gold once the upper bonus is earned, as the old Bonus line did. A tooltip isn't announced, so TalkBack hears the totals as
the button's state ("Upper 61, no bonus yet, lower 91") and its action ("Show totals") opens the
tooltip too. **Gotcha:** `TooltipBox`'s anchor merges its content into one node of its own, which keeps
the name, state and actions but drops the role - so `Role.Button` goes on the tooltip's modifier as
well (`BoardSemanticsTest`).

**Undo and Totals are icon-only** (`BoardButtonIcon`): the small "Undo"/"Totals" labels under the
glyphs were dropped on review - the pair looked better without, and a label was what a large font
broke. Undo's glyph carries its name ("Undo") for TalkBack. With no text, a large font changes
nothing here; the glyph is 26dp, and only shrinks (staying square) if the row the board gives it is
shorter than that - a short screen. Both keep a 48dp minimum touch target where there's room. Rendered
at 411dp, 320dp and 200% font - Robolectric only.

**More dice than hold slots (Stud) gets its own tray layout** (`DiceTray`'s `SlottedDice`): the
five hold slots across the top, then the mat split into a column per die - seven narrower columns,
6dp apart, a die at most 86% of its column, so they never overlap. Holding and letting go are
separate targets (a tap in a mat column holds, into the free slot nearest that column, and a held die
never moves; a tap on a slot lets go),
where the usual layout's whole column toggles. Both layouts share one column gesture
(`columnPresses`), and the tray is the same height either way. Each slot and each die on the mat is
its own TalkBack node (`SlottedDiceTrayTest`). A slot is only as big as a mat column, so a held
die is drawn the same size as on the mat (`matDieSize`), the slot row keeping the usual layout's
height. Robolectric renders only - not seen on a device.

**A die locked by Unlucky Dice** (`LockedChains`, drawn by `DiceTray`'s `DieFace`) is the die with a red
veil (in the die's own outline, its style's `shadowShape`: rounded, square, egg, D20) and a red cross of two chains (reaching less far, `DiceStyle.lockedChainReach`, on the egg and D20 so they stay inside the outline) (five links each, alternately seen face-on and edge-on, edged dark so they
read on any die colour) laid over it, so it follows the die wherever it lies. It's drawn only once the die has
landed (a tumbling die is drawn plain), slams in over 260ms (larger and transparent to size and opaque) and just
appears under reduced motion (`LocalReduceMotion`). The pips are covered on purpose: a locked die's number
doesn't matter. Spoken twin: the die's state is "Locked in chains, can't be held or scored" in both tray layouts,
with no Hold action and no hold sound or haptic on a tap (`BoardSemanticsTest`). Modifier modal: Unlucky Dice is
a switch with two stepper rows beneath (`ModifierSetting.steppers`), the odds in 10% steps and the cap in dice.
Robolectric renders only (both layouts) - not seen on a device.

## Tablets and landscape

**The game screen scales as one object** (`GameScreen`'s `gameLayout`). It is laid out at a phone's size
(`REFERENCE_WIDTH`, 411dp) and then drawn larger through a scaled `LocalDensity` (font scale kept), so
art, text and touch targets grow together rather than the board's pieces drifting apart. The scale is
never below 1: a screen too short for the layout scrolls rather than shrinking tiles under 48dp. The
cap is `MAX_GAME_SCALE` (2x), and the width cap is `STACKED_MAX_WIDTH` (480dp before scaling), so a
squarish screen centres the board. It picks whichever of two arrangements draws larger:

- **Stacked** (phones, tablets held upright): header, board, tray - the original layout. A 411dp phone
  is unchanged.
- **Side by side** (landscape): header across the top; the board on the left with its cup column left
  empty (`showCup = false`, the 5x tile and Σ/Undo stay put); and on the right the cup (`GameCup`, the
  same `DiceCup` the board uses) with the tray under it. The maintainer asked for this: the cup stays on
  the right-hand side, with the dice pouring out below it. On review the cup was too small beside the
  tiles and dice, and too far from the edge to reach easily. So it's drawn `SIDE_CUP_SCALE` (1.5x)
  larger, at the pane's right edge, with its roll count on its left (`countFirst`). On a screen too
  short for even 1x (a phone on its side) it's enlarged only as far as keeps the pane level with the
  board (`GameLayout.cupScale`, about 1.2x). While another player's scorecard is up, the cup's space is kept empty so the tray doesn't move.

**A die on the mat is the size it is held**: column width less `HELD_DIE_INSET` either side
(`matDieSize`), so the two scale together. It was a fixed 44dp against a column-wide slot, which looked
mismatched on a phone and much worse on a tablet. The slot's old 52dp `sizeIn` cap sat after
`fillMaxWidth` and never took effect, so it was removed; Stud's slot row still uses `MAX_SLOT_DIE_SIZE`.

The game screen pads for `safeDrawing` top and sides (a landscape camera cutout or side navigation
buttons). Other pages already cap at `CONTENT_MAX_WIDTH` and scroll, and they looked fine on tablets and
in landscape. Rendered in Robolectric at 411x891, 891x411, 600x960, 960x600, 800x1280 and 1280x800, in
Standard, Tricolour and Stud. Not seen on a device.

## Constraints worth knowing

**Player names cap at a length that varies with player count** (`GameSetupState.
maxPlayerNameLength`: 14/12/10/8 characters at 1/2/3/4 players). The binding constraint is not the
setup form - it's the in-game header, where every seat shares one row, so the more of them there
are the less width (and, for a CPU seat, the less width left over once its chip icon takes its
own share) each tab - and so each name - gets. It's a layout limit, not a gameplay or storage one.

Consequences:

- Enforced in `GameViewModel.setPlayerName` against the *current* player count, not just the text
  field - so it also applies to names restored from preferences saved before the cap existed, or
  saved at a different player count. `setPlayerCount` re-clamps every slot's name whenever the
  count changes, in either direction.
- **AI names obey a tighter version of the same cap, never truncated.** They're players in the
  same header, but every AI tab also carries the CPU chip icon, so `GameSetupState.
  maxAiNameLength` knocks a couple of characters off `maxPlayerNameLength` for the icon's own
  width before `AiNameGenerator` picks a name - it holds three separate pools (one each for
  2/3/4 players, the count that actually decides how tight the tab is), every entry already
  short enough for that count's `maxAiNameLength`, rather than cutting a longer, shared pool down
  to fit - half a truncated word reads as a bug, not a tight layout. `AiNameGeneratorTest` sweeps
  each pool against its own cap so a name added later that doesn't fit fails the build instead of
  showing up ellipsised in a real game.

`PlayerHeaderBar` also steps names down to `labelMedium` at 3+ players, which is what makes
the cap actually deliver a full name on one line on a narrow phone.

CPU players carry a small processor-chip icon (`CpuPlayerIcon`, Material's `Memory` glyph) before
their name, in the header tabs and on the results page. In a header tab it costs ~14dp of that
~72dp, so tab names use `ShrinkThenWrapText` (down to 12sp, then a second line): a full-length CPU
name may shrink a little, but never below 12sp. The caps above were checked against that floor
(estimated from Roboto's metrics, not measured on a device): at 360dp, 4 players leaves ~72dp for a
human name and ~58dp for a CPU's, against ~63dp for a widest-ordinary 8-letter name and ~42dp for the
longest 4-player CPU pool name, so 8 (CPU 6) still fits on one line; 3, 2 and 1 players have more room
than their caps need. At 320dp a 4-player human name of 8 wide letters is the tightest case.

**Every roll is one cup tap.** A human's roll - a finger on the cup, or a phone shake - goes through
`GameScreen`'s `onCupTap`: the cup shakes for `CUP_SHAKE_MILLIS`, the shake sound and haptics play,
then `GameViewModel.rollDice`. A CPU's roll shakes for the same `CUP_SHAKE_MILLIS` (the view model
sets `aiRolling`, which the screen treats like its own tap) and lands through the same
`performRoll`. No roll starts while another is in hand - from the shake until the dice have settled
(`rollInHand` in `GameScreen`, the window scoring also waits out): the cup ignores taps then and is
`disabled()` to TalkBack, and the CPU loop waits out the toss (`diceTossMillis`) before it rolls again. Don't add another way to roll with its own timing or animation - `.claude/
GAME_MODES.md`'s "Turn flow" has why, and the traps (the AI loop's own copy of the state; Undo
during the shake).

## Nothing runs in the background

The process stays alive when the app is backgrounded, and so do coroutines: a plain `delay` keeps counting.
So **no shared code uses a plain `delay`** - `NoBackgroundTimersTest` fails the build on one. `GameViewModel`'s
turn timer and CPU turn loop use `pausableDelay` (driven by `setForeground`, which `GameScreen` calls from
lifecycle callbacks - not from composition, because a backgrounded app draws no frames and nothing would
recompose to say so - and which also pauses them when another screen opens over the game); everything else
- the banners' hold, stagger and long-press, the roll's shake and settle waits, the tray's face flicker, the
Top Hat rabbit's peek, the flowerpot's stand-up, the achievement row's flash, the Styles warm-up and the
superuser gestures' ticks - uses `Lifecycle.delayWhileResumed` (`ui/common/ForegroundDelay.kt`). A wait under
way when the app goes away starts again, in full, on return.

Also stopped in the background: a sound under way (`SoundPlayer.pause`/`resume`, `SoundPool.autoPause`) and a
vibration (`HapticsPlayer.cancel`), both via lifecycle observers in `rememberSoundEffects`/`rememberDiceHaptics`;
the shake sensor and the logo's pupil sensor (resume/pause observers); and the reduced-motion setting's
observer (collected with `collectAsStateWithLifecycle`, so it isn't registered). Frame-driven animation
stops on its own with no frames. The app has no services, receivers or background work of its own.
**iOS:** `SoundPlayer.pause`/`resume` and `HapticsPlayer.cancel` default to doing nothing, so the iOS players
don't yet stop (implement them with `AVAudioPlayer.pause()`), and `TransientMessageHost` (iosMain, not compiled
here) still uses a plain `delay`.

## Reduced motion

**The player's "Animations" level.** Settings has one row, **Animations** (`AnimationLevel`, saved by
`SettingsRepository.animationLevel`; it replaced the "Remove animations" switch, which itself replaced "Simple dice
roll" - a player who had the switch on starts at Off). It's a `SegmentedSetting` in a card of its own, between the
switches and the resets: icon and label as a switch row has them, and a segmented row of the four levels under the
label, Off to High from the start edge (so mirrored in Arabic) (`FittedSegmentedChoiceRow`, shared with the New Game AI difficulty: labels shrink together, then turn into
initials - their own `_short` strings, as Arabic's Medium and Low share a letter - with Off as a crossed-out motion
icon). No description line - the maintainer judged the levels self-explanatory; what each does is in the table below.
The segmented row isn't inside the `ListItem`: its supporting slot is measured intrinsically, which the fitted row's
`BoxWithConstraints` throws on.

| Level | Decoration that moves on its own | Gameplay motion | Score tile pulse | Frames |
|---|---|---|---|---|
| **High** (default) | moves | plays | pulses | the screen's own rate (90/120Hz where it has one) |
| **Medium** | still | plays | pulses | up to 60 |
| **Low** | still | plays | steady | up to 30 |
| **Off** | still | none (`LocalReduceMotion`) | steady | up to 30 |

"Decoration that moves on its own" is what keeps an idle screen drawing every refresh: the menu's drifting dice,
`FloatingDiceBackground`, everything on `TwinkleClock`/`rememberArtSeconds` (stars, sparkles, Neon, RGB, Glitch, the
bee...), the cups' ambient cycle (`rememberAmbientCycle` - steam, bubbles) and the googly dice's pupils. Each reads
`ambientMotion` (`ui/common/ReduceMotion.kt`), true only at High, and below it holds the same still pose it has
under reduced motion - so no new art. The score tile reads `scorePulse`. Both are false under a `LocalReduceMotion`
provided further down, which is how a Styles tile off screen is still held. **Anything new that loops while nothing
is happening must read `ambientMotion`**; anything that answers play reads `LocalReduceMotion`, as below.

**Frames:** `MainActivity` builds the window's recomposer itself on `CappedFrameClock` (app/android), which
`PlatformServices.capFrameRate` sets from the level; every Compose animation, and the recomposer, waits on it. It
only skips whole refreshes, so a 60 cap gives 60 on a 60 or 120Hz screen but 45 on 90Hz and 48 on 144Hz - which
is why a capped level also asks the window for the lowest refresh rate from 60Hz up (`preferRefreshRateFor`,
`RefreshRateTest`): a panel that can drop to 60Hz does, giving a true 60 and saving the panel's own power. Never below
60Hz, even at a 30 cap, so touch and scrolling stay responsive. Only the player's level caps frames - the system's
reduced motion doesn't. iOS doesn't cap yet (`capFrameRate` defaults to doing nothing). The 60fps floor (no
animation below 60) holds for High and Medium; 30 is only ever the player's own choice of Low or Off.

**Two sources, one switch.** `LocalReduceMotion` is true when the system asks for less motion (below) *or*
the player sets "Animations" to **Off**. `DiceFiveApp` provides it, and `LocalAnimationLevel` beside it (Off while
the system asks for less motion), once, so everything below applies to either. The app's level can't do what the
system's does to Compose's own `MotionDurationScale` (that's fixed per window by the system setting), so what Off
leaves moving - Material's own touch ripples and switch thumbs, a scroll's fling - runs at its 30fps cap instead.

**Two layers.** On Android, Compose itself already follows the system animation scale (read from the
1.12.1 bytecode, not run on a device): `WindowRecomposer` observes `animator_duration_scale` and puts
a `MotionDurationScale` in the recomposer's coroutine context, so every `tween`, `animate*AsState` and
`Animatable` started from a composition (banners' slide and fade, page fades, the row flash, score
count-ups, the plant's growth...) finishes instantly at scale 0. An `InfiniteTransition` does not just stop:
`skipToEnd()` leaves each value at its **target**, which is not always a resting pose (the cup's shake wobble
would freeze at full tilt, the timer flash at its muted end, the tile glow at full strength) - so those are
gated below rather than left to it. What Compose does **not** cover, and `LocalReduceMotion`
(`ui/common/ReduceMotion.kt`, provided once in `DiceFiveApp` from `PlatformServices.reduceMotion()`)
does: `withFrameNanos` loops, `delay`-driven sequences and physics, and anything decorative worth
stopping outright.

When it's true: the tile glow is steady gold, the turn timer is a steady red (its live-region warning is
unchanged), the menu's drifting dice and the twinkling stars stay still, the Cauldron's bubbling and the
Takeaway's steam (the cups' ambient animation, only for players who pick them) and the drawn shake stop (the Treasure Chest's, the Volcano's rumble and the Picnic Basket's too), its gold burst on opening doesn't play, and its lid, the Picnic Basket's lids and the Shipping container's doors snap
open and shut with no swing (the basket's apples are simply on the table), the Chicken doesn't flap or hop (no feathers either), the Gift Box's lid doesn't rattle and is simply lying on the table once it's poured, the Glitch dice don't glitch, the Neon dice, mat and cup don't pulse, nothing glitters with sparkles (dice, mat, background, cup), the Honeycomb mat's bee doesn't buzz, the Glitch background's roll bar and tears stop, the Volcano doesn't erupt (its lava is simply
there, at rest, and gone again with no cooling), a spent cup is simply grey with no fade, the Flowerpot's plant is simply
at its new stage, the Top Hat's always-out rabbit stays put (the peeking one still peeks - its sighting is an
achievement - but just appears and disappears, with no rise or ear twitches), a cup with liquid in it moves it with the
cup on the same frame, and `CupReducedMotionTest` checks every cup style snaps, never caught part-way, the googly dice's pupils stay centred instead of sliding with the
device (the menu logo and the tray), the logo dice and cup don't roll or shake when tapped (the tap still
counts), a cup picked on the Styles screen doesn't shake or tip and a die picked there doesn't roll (the pick still counts), the
dice aren't picked up or tossed - they stay where they lie through the shake and snap to their result as it lands
(so scoring doesn't wait for a toss, and nor does a CPU: `GameViewModel.diceAnimated`, which also drops the
CPU's longer pause after releasing a die, `RELEASE_GAP_MS`, as there's no drop to watch), the board's cup doesn't
shake or pour but stands while the roll is in it and is simply tipped once it lands (`rememberCupRotation` snaps, and
its liquid doesn't slosh), a highlighted tile's gold ring is a plain steady border, pages don't cross-fade or fade
their content in, achievement banners don't fade in or out or arrive one at a time (a burst is all there at once, each
banner simply there and then gone; a progress banner's count doesn't climb; a drag still follows the finger -
`BannerReducedMotionTest`), scores appear rather than count up, and the Game Over fireworks don't play. With no fades
left to cover loading, two things load differently: the Styles page builds every tile on screen on its first
frame (one slower frame, but nothing else is moving) and shows each row as soon as it's scrolled to the pick,
rather than building a pair a frame behind the fade; and continuing a game shows only the game's own plain
backdrop until the board is ready (the spinner after `RESUME_SPINNER_DELAY_MILLIS`, `GameScreen` the same
backdrop while its game or table settings load), where before the resume page, the menu and a blank frame
each flashed up in turn. **What the game does doesn't change.**

**Audio and haptics are not motion, and stay.** The shake sound (~400ms), the shake buzz (336ms) and the
landing sound (~490ms) are timed to the cup's `CUP_SHAKE_MILLIS` window - the first two start as it opens and
end as the dice land, where the third begins - and for a TalkBack user they *are* the roll's feedback. So
`cupShakeMillis` (`ui/game/CupShake.kt`) keeps the full window whenever sound or vibration is on, and only
shrinks it (to 100ms, not 0: `isRolling` and the roll tracker have to be seen changing) when both are off
and there's nothing to keep in step with. The same value is used for a human's tap (`GameScreen`) and a
CPU's roll (`GameViewModel.cupShakeMillis`, set from the screen). **Never shorten the window without
re-checking the clips and `AndroidHapticsPlayer`'s `SHAKE_HAPTIC_MILLIS`.** The CPU's other pauses
(`ROLL_GAP_MS`, `AI_STEP_DELAY_MS`, `AI_REACTION_DELAY_MS`) are for following what it did, not for an animation,
and never change; only the toss it waits out (`diceTossMillis`), `RELEASE_GAP_MS` (time to watch a released die drop) and the one-die-at-a-time
stepping of its hold changes (`AI_HOLD_STEP_MS`, `AI_RELEASE_TO_HOLD_GAP_MS` - each roll's releases and holds land
together, `GameViewModel.changeHolds`) go, as all are animation. They make no sound and aren't announced, so a
TalkBack user loses nothing by them landing at once.
The Game Over fanfare and hold ticks are governed by their own settings, not by this.

## The Leaderboard's two views

A `SegmentedChoiceRow` at the top (Combined | Game Mode; always shown) switches
`CombinedLeaderboard` - the original table, with the gold/silver/bronze podium, its card as tall as its rows until it has the page - and
`GameModeLeaderboard`: a card per mode that has a score (including modes that never reach the Combined table) in a scrolling page, **no podium colours** there. A card is
capped at ten rows (`modeCardListHeight`, from `bodyMedium`'s line height so it follows the font) and
scrolls inside; its Previous/Next are its list's last item, not pinned. The page's own scrollbar is in
the 20dp side margin (as on Styles) - the way to scroll the page when a finger is on a card's list.
Adding the switch shortens the Combined table by its own height; nothing else moved.
TalkBack: card titles are headings; the switch is the stock segmented row (radio buttons); a row's
`stateDescription` carries what its long press shows. Rows use `OnDemandTooltip` (`ui/common/AppTooltip.kt`) - a plain gesture and a "Show details" long-click action until the first long press, then a real `AppTooltip` - because a `TooltipBox` per row was a real share of the list's first frame; use it for any long list of rarely long-pressed rows.

`ScoresWarmUp` (on the menu, like `StylesWarmUp`) draws both views once from a made-up board, 2s after the menu
settles, in a 1dp clipped box under its backdrop: the Leaderboard's first-time code was the lag on opening it,
with only a couple of scores, so entry count had nothing to do with it. It needs no database - the page runs
off a `ScoresViewModel(initialState = ...)` with no repository. `ScoresWarmUpTest`. The first open within 2s
of launch isn't warmed.

## Scrollbars on long lists

Plain scrolling pages (Styles, and each Rules page) use `VerticalScrollbar`, in the page's right-hand
margin. So does the Licences dialog, in its right-hand margin: its list is a platform ScrollView on
Android, so it uses the overload that takes a pixel position, maximum and `scrollBy` (fed by
`LicenceScroll`) rather than a `ScrollState`, and the platform's own scrollbar is turned off. Anything
else that scrolls should do the same rather than styling a platform scrollbar to look similar. The Rules pages share one, beside the pager, following whichever page is showing - which is
why every scrollbar keys its "anything to scroll?" check on the state it's handed: unkeyed, it stayed
on the first page's answer and never appeared on a longer one.

Every page with a `LazyColumn` that can outgrow the screen (the Leaderboard, Statistics) wraps it
in a `Box` and overlays `ui/common/Scrollbar.kt`'s `LazyListScrollbar` - written once and shared,
rather than each screen drawing its own. It's `primary` (the app's gold) on a
`surfaceContainerHighest` track, both colour roles rather than hardcoded values.

Achievements used it too until its list was unified (see "The achievements list" above) and the
scrollbar was dropped from that screen specifically. It's still the reason the notes below mention
Achievements by name: that mixed-height list (section headers, group headers, cards with and
without a progress bar) is what exposed the bugs these design decisions fix, and the fixes remain
relevant to whatever heterogeneous list `LazyListScrollbar` is next used on.

**Show/hide is driven by `LazyListState.canScrollForward`/`canScrollBackward`, never by
comparing `layoutInfo.visibleItemsInfo.size` to `totalItemsCount`.** The item-count comparison
looks reasonable but is wrong: once scrolling has prefetched items past the viewport edge, a
shortish list can end up with every item simultaneously present in `visibleItemsInfo`, which
makes that check hide the bar partway through a scroll and never bring it back. `canScrollForward`
/`canScrollBackward` answer the actual question ("is there more this way") regardless of how many
items happen to be composed at once.

**The thumb's size and position are pixel-based, not item-count-based, for the same family of
reason.** An early version sized the thumb as `visibleItemCount / totalItemCount` and moved it by
`firstVisibleItemIndex / scrollableItemCount` - on the Statistics screen (a handful of cards, each
taller than half the viewport), that meant the thumb only updated once per whole card scrolled
past: it looked pinned near the top, then ballooned to nearly the full track as more cards entered
`visibleItemsInfo`, and the discrete jumps read as the whole screen scrolling janky rather than the
indicator being wrong. `LazyListScrollbar` reads each visible item's pixel `size` and `offset`, so
the thumb tracks the actual scroll position continuously - see the doc comment for the formula.

**Item sizes used for that estimate are remembered per index, not re-averaged from the current
viewport every frame.** A second version averaged `visibleItemsInfo`'s sizes fresh each frame,
which is fine for a uniform list (every Leaderboard row or Statistics card is about the same
height) but broke on Achievements: section headers, group headers, and cards with or without a
progress bar are all different-sized items in the same `LazyColumn`, so the average - and with it
the thumb's size - swung every time the mix of item types on screen changed, which read as the
thumb resizing while scrolling rather than just moving. `LazyListScrollbar` now keeps a
`remember`ed `index -> size` map that only ever grows: an item's size is fixed the first time it's
seen, so the running average firms up as more of the list is scrolled through instead of lurching
frame to frame.

**Even that running average never fully settles on Achievements**, because a card's own height
varies continuously - not just "with progress bar or without," but with however many lines its
description happens to wrap to - so every newly-seen card nudges the average a little. A first
attempt froze the *size* estimate (what sets the thumb's height) once a sample of items had been
seen - but on a page where only a handful of items fit on screen at rest, that sample was only
reached partway into the user's *first scroll*, so all the convergence meant to be spread out
happened at once, right when scrolling started, and read as a single jarring resize rather than
gradual settling. `LazyListScrollbar` instead freezes the size estimate from the very first layout,
before any scrolling at all, trading a bit of proportional accuracy (the first screenful may not
represent the whole list) for there being nothing left to converge once the user actually starts
scrolling. The thumb's *position* is unaffected by the freeze - it's still computed from the exact
remembered size of every item actually scrolled past, for the whole list, so it keeps tracking the
finger precisely throughout.

**All of that remembered state resets when `totalItemsCount` changes, not just when the
`LazyListState` instance does.** The same `LazyListState` survives Achievements' "Hide unlocked"
toggle and the Leaderboard's page navigation - only the *shape* of the list changes, so the cached
per-index sizes (and the frozen size estimate built from them) would otherwise keep describing a
list that no longer exists. A first version detected this via `derivedStateOf { listState.layoutInfo
.totalItemsCount }` read at the composable level, keying `remember(listState, totalItems) {...}` -
which worked at first, but wasn't reliable: `LazyListScrollbar`'s own parameters (`listState`,
`modifier`) never change across a toggle, only what it reads from `listState` internally does, and
that left the reset exposed to whether Compose happened to recompose a stable-parameter composable.
It eventually got stuck holding a stale frozen estimate after a hide/unhide cycle. The check now
lives as plain imperative code inside the `Canvas` draw phase instead - a `ScrollbarMemory` object
(`remember(listState) {...}` only) with a `totalItemsAtLastCheck` field, compared and reset on every
draw call. That draw phase already has to read every item's live pixel `offset` on every frame
regardless, for the *position* math - piggybacking the shape check on that same guaranteed-fresh
path removes the reliability question entirely rather than depending on it.

**The two endpoints are pinned to `canScrollForward`/`canScrollBackward`, not left to the pixel
estimate.** The estimate is extrapolated from a small early sample on a list whose real item
heights vary continuously, so it can end up a little larger than the list's true content size -
left alone, that shows up as the thumb never quite reaching the bottom (or top) of the track even
once the list genuinely has, and it gets worse each time Achievements' "Hide unlocked" toggle
re-freezes the estimate from a different, possibly differently-biased sample. Since
`canScrollForward`/`canScrollBackward` are exact (not estimated), `LazyListScrollbar` overrides the
calculated `scrollFraction` with `1f`/`0f` whenever Compose says there's genuinely nothing left to
scroll in that direction - the pixel math still governs everything in between, only the two true
edges are pinned.

## Player colours

**A player's colour is chosen on the New Game screen and read from `PlayerState.colour`** - a
`PlayerColour` (`model/PlayerColour.kt`, eight of them), drawn through `PlayerColour.color`
(`ui/theme/Color.kt`, the only place one becomes a `Color`). Their tab, name and score, and the outline on
the box they last scored in (`ReadOnlyScoreboard` reads `player.colour`) all use it, so they always match.
Anything new that marks a player in their colour should read it too, never a seat index.

- **The active tab's frame** is the player's chosen `ScoreFrame` (Styles > Frame; `DESIGN.md`), drawn
  behind the tab in their colour - Classic is the plain ring. It's decoration over meaning already said:
  the tab's "Current turn" state description is unchanged, whatever the frame.
- **Every tab sits on a panel**: a `surfaceContainer` fill in the tab's 10dp rounded shape, tinted with 8% of the
  player's colour (`PANEL_TINT`), the same for every frame, drawn under the frame. Without it the names and
  scores floated on the page. The tint is capped by contrast, not taste: at 10% Purple's name is 4.49:1, just
  under 4.5; at 8% the lowest is Purple at 4.63:1 (Red 4.87:1). Raise it only after re-checking all eight.
- **Frames stay off the tab's text.** The name sits `NAME_TOP_DROP` (3dp) below the tab's top padding, clear
  of the corner ornaments, and every frame keeps 1.5dp from the active tab's name, score, place and dot at
  360dp with four players and the widest names - which `ScoreFrameClearanceTest` measures from renders. A new
  frame that fails it is drawn smaller or further out (a frame may spill a few dp past the tab), not given
  more room: the wreaths stop below the name, and the rose, swirl and knots are tucked into the corners.
- **Setup**: a 28dp circle (48dp touch target) left of each player's name or difficulty
  (`ui/setup/PlayerColourPicker.kt`); tapping opens a `DropdownMenu` of the eight as a 4x2 grid, the
  current one ticked. It's a curated set, not a free picker: M3 has no colour-picker component, and
  Google's own apps (Calendar, Keep, Tasks) offer a small fixed palette, with a custom picker as an extra
  at most. Here a custom one would let two players look alike, or pick something unreadable on the dark
  table, so there isn't one. Add to the set only with a tone that's light enough for the dark page and
  clear of the others.
- **Always four distinct**: `GameViewModel.setPlayerColour` gives a colour another slot holds to that slot
  in exchange (all four slots, shown or not, so raising the player count can't produce a clash). Defaults
  are `PlayerColour.defaultFor(seat)`: cyan, green, purple, amber - what the seats were before it was a choice.
- **Persisted** per slot in settings (`player_colour_N`, written when a game starts; a saved set that isn't
  four different colours falls back to the defaults) and saved with an in-progress game (`colour` per
  player; an older save gets its seat's default).
- **TalkBack**: the circle is a button, "Player 2 colour, Pink", action "Choose colour"; the pop-up's choices
  are radio buttons named by colour, the current one selected, and one another player holds says "swaps with
  player 1". `PlayerColourPickerTest`. Not heard on a device.

## Gotchas hit while building this

- **A scrolling container eats a tap made while it scrolls.** Compose takes it as "stop scrolling" and never passes it
  to the child under the finger. So anything that scrolls itself after a tap - Material's scrollable tab rows re-centre
  the selected tab on every selection - loses the next tap for as long as it moves, which on a phone reads as controls
  that only work some of the time. The Rules tab rows never re-centre (`KeepTabInView` moves them at once, only when the
  tab is hidden), and a row that fits mustn't be scrollable even by a pixel of rounding. Test it with real touches a
  few frames apart (`performTouchInput { click() }` with `mainClock.autoAdvance = false`); semantics clicks skip it.
- **An edge fade isn't a "there's more" hint on its own.** It only shows when content happens to be
  under it, which depends on label widths, screen width and scroll position - the Rules tab row's
  fade sat over the empty gap between two tabs and hinted at nothing. Pair it with something that's
  there regardless (the Rules tab row's edge chevrons).
- **`Modifier.align` needs the Box to be the actual parent.** Inside a `Scaffold` body the
  enclosing `BoxScope` is captured but is no longer the parent layout, so `align` is accepted
  and silently ignored. Wrap in a real `Box` with `contentAlignment` instead.
- **`padding` before `size` shrinks the node.** `Modifier.size(34.dp).padding(top = 6.dp)`
  renders a 34x28 box, not a 34x34 one lowered by 6dp. Use `offset` to move something without
  resizing it - this is what made the logo dice non-square.
- **Every tooltip is `AppTooltip`** (`ui/common/AppTooltip.kt`) - never `TooltipBox` directly - so the
  look and behaviour change in one place. `rememberAppTooltipState()` lets a tap show one too. Most
  take a `message`; one that needs a layout of its own (the totals table) passes `body` instead, and
  keeps the same container, outline and padding. Material's
  `rememberPlainTooltipPositionProvider` is deprecated with no replacement in 1.4.0 (the successor
  arrives with 1.5.0); it's suppressed once, there.
- **`ViewModelConstructorInComposable`** fires on `@Preview` functions that build a view model.
  Suppressed on the preview in `GameScreen` - a preview has no host to scope one to.
- **The adaptive-icon "safe zone" is a floor, not a guarantee.** `ic_launcher_foreground.xml`'s
  artwork was checked by hand against the spec's nominal 66dp-diameter safe circle centred in the
  108dp canvas (and separately against a rounded-square mask), rendered and inspected both ways,
  and cleared with margin - but a real device screenshot still showed every die flat-cut at the
  edges. Whatever mask the actual launcher applies crops tighter than that nominal circle in
  practice. Don't trust the spec's safe-zone math alone for icon work; get an on-device look
  before calling it done, and when touching this file keep the real margin that's baked in now
  (everything sits inside one outer `<group scaleX="0.8" scaleY="0.8" pivotX="54"
  pivotY="54">`) rather than pushing content back out toward the theoretical 66dp edge.

- **The launcher icon has a themed (monochrome) layer**, `ic_launcher_monochrome.xml`: the foreground's
  cup and dice as one flat silhouette, because Android tints that layer by alpha alone. Pips and the
  cup's band are even-odd cut-outs, the mouth an outlined ellipse. Its geometry is copied from
  `ic_launcher_foreground.xml` (same rotations, same 0.8 zoom), so change them together. Rendered to a
  PNG and checked there; not seen on a device with themed icons on. `ic_launcher.xml` is the only icon
  wrapper (the manifest's `roundIcon` points at it too), so there is no `ic_launcher_round.xml`.

## Verifying UI work

**There is no emulator in the sandbox** (see DESIGN.md's Verification section). UI changes are
verified by `./gradlew assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin lint` plus
careful reading, and layout claims are arithmetic, not screenshots. Say so when reporting -
"compile-and-read verified, not seen" - and prefer layouts that degrade safely on a screen
size you can't check.

Behaviour a screen drives can be tested under Robolectric with the real screen and view model -
`GameScreenAutoRollTest` renders `GameScreen` (with `LocalPlatformServices provides
SilentPlatformServices`) and steps both the compose clock and the paused main looper
(`ShadowLooper.idleMainLooper`), since the view model's coroutines delay on the latter.

## Deliberately deferred

- Material 3 Expressive (see above).
- Dynamic colour, as a setting or a default.
- The per-player difficulty selector on the setup screen: it cost a control row per player and
  every option in it is disabled until AI difficulty exists, so it's one line of text for now.
  `PlayerSetupSlot.difficulty` and `setPlayerDifficulty` are untouched - only the UI went.
- `BrandBackdrop`'s watermark dice drift on the main menu and every screen off it (`driftingDice`,
  set by the menu, `ScreenScaffold` and the game-over page; the game itself has none, `showDice = false`). The drift
  is one `DriftState` held in `DiceFiveApp` and shared through `LocalDriftState`, so the dice keep
  their places and faces from screen to screen; it starts over only after a game left unfinished (the game
  destination's `onDispose` resets it unless the game is over, so the results page's dice carry on into the menu) or on a fresh launch. `DiceDrift` holds the logic, free of Compose so
  `DiceDriftTest` can pin it: five dice, each crossing in a straight line up and to one side and
  turning slower than it travels (its corners move slower than its centre), then coming straight
  back in just below the bottom edge on a new path, size and face - so there's no pool of dice
  waiting off screen, and only dice actually showing are drawn. A path's lean is held to what lets
  it cross most of the height before running out of width (about 25 degrees on an upright phone),
  or a narrow screen's dice bunch in its lower half; and a returning die takes whichever of a dozen
  candidate paths stays furthest from the others over the next twelve seconds. "Gone" and the entry
  point use each die's extent at its current turn, not its worst-case half-diagonal, so none is out
  of sight longer than it must be - which is what keeps three to five showing.
- `AppLogo` is deliberately live Compose art, not a drawable - the launcher icon
  (`ic_launcher_foreground.xml`) is the app's static artwork of the default dice and cup; the
  in-app logo is not meant to duplicate it. On the menu it's drawn in the player's own dice and cup
  picks (`MenuViewModel.logoStyles`), and held invisible - still taking its space - until they've
  loaded, so it never flashes the defaults first. The cup takes no layout space (`noLayoutSpace`),
  so the dice and wordmark sit exactly where they would without it and its base tucks behind the
  wordmark - adding or switching the cup must not move them. Only the part a tall cup stands above
  the dice is reserved, as top padding, because `PageColumn` scrolls and a scrolling column clips
  anything drawn outside it. Every cup is drawn at the same scale per grid unit (as in the game and
  on the Styles screen), and hangs from the same top line rather than standing on a shared base: a
  squat cup (`CupShape.SQUAT`) stood on the tall cup's base hid almost wholly behind the dice and
  wordmark. Keep the "DiceFive" text, which `MainActivitySmokeTest` asserts on.
  **Tapping the cup shakes it** (`shakeCupOnTap`, the menu only): the same `CupCanvas` shake as in a
  game, for `CUP_SHAKE_MILLIS`, so its wobble and any liquid slosh come with it, and it ends upright
  (`tilted` stays false). Nothing is earned, and it has no sound or buzz. The cup takes no layout
  space, and a zero-size node is never hit, so the target is the whole logo's own `detectTapGestures`
  plus `isOnLogoCup`'s rectangle; a die's tap is consumed by the fan's click first, so it never
  shakes the cup. Not exposed to TalkBack, like the dice tap.

## A disabled scorecard box

A box switched off for the game (`PlayerState.disabledCategories`, Quickfire) stays on the board, so the layout
never moves, but is drawn unlike every other state. Every other look is a filled tile - teal (open), gold (a good
pick), flat grey (scored) - so a disabled one is **only an outline**: no fill, a dashed border, the glyph at 30%,
a slash through it, and the word **Off** where an open box shows "-" and a scored one its number
(`CategoryTile(disabled)`, `CategoryCell`). Don't give it a fill or a solid border, or it reads as "scored".

- **TalkBack**: the cell says its name, "Disabled for this game, can't be scored", has the `disabled()` semantic
  and no Score action. A scored box says "Scored N" and an open one "Open", so the three can't be confused by
  ear either. `BoardSemanticsTest` pins it.
- Not seen on a device: the dashes and slash at 360dp and under a large font, and the dashed outline against
  every table background.
