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
| `BrandBackdrop.kt` | the app's one piece of scenery: surface gradient, `primary` spotlight, faint dice watermark. Built from colour roles, so it tracks the theme. Quiet enough that ordinary components sit on it unmodified. |
| `ScreenScaffold.kt` | the frame for every non-menu page: backdrop + M3 top app bar with a back arrow . The bar itself stays transparent over the backdrop; its title is bold and tinted `primary` (see "Colour" above) rather than left at the M3 default. Also holds `PageColumn`. |
| `DiceFiveDialog.kt` | the app's one dialog shape, so the menu and the board ask questions the same way. |
| `AppLogo.kt` | placeholder app mark, built from the game's own dice via `IvoryDiceStyle`. |
| `Scrollbar.kt` | `LazyListScrollbar`, a `BoxScope` extension drawing a minimal scroll indicator over a `LazyColumn` - stock Compose has none for Android. Shared by the Leaderboard and Statistics screens. |
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
- **Segmented buttons** (`ui/common/SegmentedChoiceRow.kt`) for small exclusive sets that fit one
  line: player count 1-4, AI difficulty. Radio rows are for options that need more
  than a word each - the setup screen's game mode, where each row carries `GameMode.description`
  as a second line, since "Tricolour" alone doesn't say what it changes. A disabled option (a mode
  that isn't ready yet) would also go in a radio row, for its visible disabled state.
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

Long-pressing the front banner jumps to that achievement on the Achievements screen. Mid-game,
with "confirm before leaving" on, that asks first - and every banner's countdown is paused while the
confirmation is up, restarting at a full hold when it closes.

The stack lives in its own non-modal `Dialog` window, not in the app's content, so a banner that
fires while another dialog is open still draws above it.

Two variants, deliberately unequal: an **unlock** banner is `primaryContainer` with the
achievement's own icon (`Achievement.icon`, the same one its unlocked row shows - a generic trophy
made a burst of unlocks read as a stack of identical cups) and a title plus up to two lines of
description, with a star at its right when the achievement unlocks a style (see "Style locks"); a
**progress** banner is quieter (`surfaceContainerHigh`, one line plus a thin
`LinearProgressIndicator` whose count and bar climb from the old value to the new over 700ms),
so a run of "2 of 3" nudges can never be mistaken for the real thing.

Both show the achievement's title on one line, shrunk to fit rather than cut off (`BannerTitle`,
Compose's `TextAutoSize.StepBased`): titleMedium's 16sp when it fits, stepping down 0.5sp at a time
to a 10sp floor, and only ellipsised past that. The longest title - "Rules? Where We're Going, We
Don't Need Rules" - needs ~12.4sp on a typical phone, so it fits there; a narrower screen shrinks
it further before resorting to "…". The unlock banner's description shrinks the same way
(`BannerDescription`: bodySmall down to 9sp in 0.25sp steps, across its two lines).

## Style locks

Every category's Classic style is free; every other style (`StyleFamily.unlock`, a `StyleUnlock`)
waits on either a number of achievements or one specific achievement. The lock is on the style, not
the colour: once it's met, all of its colours are available. Secret achievements never count
towards a number (`AchievementsState.countedUnlocks`) - they're easter eggs, not checklist items.
For now every lock is a distinct, arbitrarily picked count up to the number of non-secret
achievements; `StyleCatalogTest` keeps them distinct and earnable.

A style locked behind one **secret** achievement is a secret style (`StyleUnlock.hiddenWhileLocked`):
it isn't on the Styles screen at all, padlock or not, until that achievement is earned - the Irish
dice (the flag in thirds, in Luck of the Irish's Tricolour colours) are the first. Any achievement
that unlocks a specific style (`Achievement.unlocksStyle`) carries a star at the right of its row and
its unlock banner (`StyleRewardStar`), so the player knows to go and look. On the row, a tap or long
press on the star shows a plain tooltip (not a dialog) naming the style - "You've unlocked the
'Irish' dice style!", from `Achievement.styleRewards` and the catalog's `noun`. The banner's star
has no tooltip: the banner's own gestures (swipe, long press) already cover it.

On the Styles screen a locked tile shows its first colour under a translucent scrim and a faded padlock, can't be picked,
and long-pressing it opens a `DiceFiveDialog` saying what unlocks it. **A saved pick whose style is
locked is never overwritten** - everything that draws a style (the game, the menu logo, "Fresh Coat
Of Paint", the Styles screen's check badge) goes through `StyleCatalog.unlockedById`, which draws
the category's default instead. So resetting achievements re-locks without losing a player's pick,
and earning it back restores it. A new place that draws a saved style must use `unlockedById`, not
`byId`.

## Accessibility

Everything a screen reader needs is added as semantics, never by changing what's drawn:

- **The board's art speaks for itself through semantics** (`BoardSemantics.kt` holds the spoken
  names, as the Rules pages give them): each die is its own node - "Die 2, 5", held or not, with
  hold/release as its action, since the tray's hand-rolled gesture is invisible to TalkBack; each
  score box is one cleared-and-set node with its name, what it scored or would score, and "Score"
  as its action; the cup is "Dice cup, 3 rolls left", a Roll button; player tabs are tabs, the
  scorecard on view selected. `BoardSemanticsTest` pins the dice and score box actions.
- **An on/off setting is its whole row** (`SwitchSetting`: `toggleable(role = Role.Switch)`, the
  `Switch` itself taking no clicks), so the label and switch are one target and one announcement.
- **A field with no visible label gets an accessibility-only one**, and its error as `error(...)`
  - see the New Game name fields.
- **Banners are polite live regions** announcing a fixed summary, not their animated text.
- **Titles are headings**: page titles, Styles categories, achievement category headers, Rules
  pages, Credits sections.

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

`ScoreGrid` doesn't hard-code a scorecard: `scoreGridRows(gameMode)` lays out whatever
`GameMode.categories` holds - the upper section down the left column beside the lower section
(5x excluded, it has its own tile by the cup), then any leftovers two to a row underneath. Standard
is the original six rows. Tricolour's four colour boxes add two more rows (Reds | Yellows, Blues |
Coloured House).

More than six rows switches every grid tile to `COMPACT_TILE_SIZE` (40dp, from 48dp) and grows the
board to fit - `scoreBoardHeight`: one tile plus the 6dp row gap per row, inside the board's
padding, which is 396dp for Tricolour against Standard's 380dp. The alternatives were worse: a third
column doesn't fit (at 360dp wide each grid column is already ~60dp), and eight rows of 48dp tiles
would push the dice tray under the fold. A 2x2 block of colour boxes under the 5x tile, beside the
grid, was also built and tried - and reverted on review in favour of this.

**The 5x tile's top is level with Ones and 3x, in every mode.** It sits top-aligned in its space
beside the grid, `firstRowTileInset` down - worked out from the grid's own row count, tile size and
height (rows share the height equally and centre their tile), since those differ between Standard
(six 48dp rows, 380dp board) and Tricolour (eight 40dp rows, 396dp board), so no one hand-tuned
offset suits both. It used to sit centred in that space, noticeably lower than the first row.
Arithmetic only - not yet seen on a device.

**Coloured dice ignore the dice style.** In a mode with `GameMode.dieColours`, each die's colour is
part of the roll, so `DiceTray` draws it with `ColouredDie` (the same `BeveledDie` shape, in that
colour) instead of the Styles screen's pick; the cup, mat and background still follow the player's
styles. For the same reason, a non-default dice style doesn't count towards "Fresh Coat Of Paint"
in such a mode (`GameMode.usesPlayerDiceStyle`).

The colour-box tiles are a flat square of `DieColourPalette.swatch`; Coloured House is Full House's
glyph over three equal-width diagonal stripes (red/yellow/blue corners-and-band, split at two-thirds
of each edge so the diagonal is cut in thirds - equal *areas* was tried first, and left the yellow
band looking much thinner than the corners) in the deeper `stripe` shades, with a soft shadow under
the glyph so white or gold still reads on the yellow band. These colours live in `Color.kt`'s
game-table block - a fixed meaning (the dice's own colours), not a theme role.

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
~72dp, so tab names use `TextAutoSize.StepBased` (down to 9sp) and a full-length CPU name shrinks
a little rather than arriving ellipsised.

## Scrollbars on long lists

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

## Gotchas hit while building this

- **`Modifier.align` needs the Box to be the actual parent.** Inside a `Scaffold` body the
  enclosing `BoxScope` is captured but is no longer the parent layout, so `align` is accepted
  and silently ignored. Wrap in a real `Box` with `contentAlignment` instead.
- **`padding` before `size` shrinks the node.** `Modifier.size(34.dp).padding(top = 6.dp)`
  renders a 34x28 box, not a 34x34 one lowered by 6dp. Use `offset` to move something without
  resizing it - this is what made the logo dice non-square.
- **`rememberPlainTooltipPositionProvider` is deprecated** with no replacement in 1.4.0; the
  successor arrives with 1.5.0. Suppressed at the call site in `ScoresScreen`.
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

## Verifying UI work

**There is no emulator in the sandbox** (see DESIGN.md's Verification section). UI changes are
verified by `./gradlew assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin lint` plus
careful reading, and layout claims are arithmetic, not screenshots. Say so when reporting -
"compile-and-read verified, not seen" - and prefer layouts that degrade safely on a screen
size you can't check.

## Deliberately deferred

- Material 3 Expressive (see above).
- Dynamic colour, as a setting or a default.
- The per-player difficulty selector on the setup screen: it cost a control row per player and
  every option in it is disabled until AI difficulty exists, so it's one line of text for now.
  `PlayerSetupSlot.difficulty` and `setPlayerDifficulty` are untouched - only the UI went.
- `BrandBackdrop`'s watermark dice drift only on the main menu (`driftingDice`, which the menu
  alone sets; every other screen keeps them still). `DiceDrift` holds the logic, free of Compose so
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
