# DiceFive: UI reference

How the app's interface is put together, and why. `.claude/DESIGN.md` covers the build plan
and the game rules; this file covers the look, the shared chrome, and the decisions that are
easy to undo by accident.

**The one-line version**: everything outside the game board is stock Material 3 wearing a
brand-seeded palette. The board is the exception - it owns its own art and does not use
colour roles.

---

## Two visual systems, deliberately separate

| | App chrome | Game board |
|---|---|---|
| Where | menu, setup, scores, settings, achievements, about, results | `ui/game/` while a game is in progress |
| Colours from | `MaterialTheme.colorScheme` roles | fixed values in `Color.kt`'s game-table block |
| Swapped by | the light/dark colour schemes in `Theme.kt` | `ui/game/style/GameVisualTheme` |
| Follows the theme setting | yes | no - the felt looks the same in both |

The board is a rendered object (felt, ivory dice, a leather cup), not chrome. Recolouring it
per theme would mean re-drawing the art, so it holds its own palette and its own swap point.
Don't "fix" the board by moving it onto colour roles; don't hardcode chrome colours to match
the board.

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

**No dynamic colour.** M3 recommends deriving the palette from the user's wallpaper. The
navy-and-gold table is the game's identity and shouldn't change per device, so `Theme.kt` uses
the static schemes. Swapping in `dynamicDarkColorScheme(context)` on API 31+ is a two-line
change if that's ever wanted.

**No typography or shape overrides.** The M3 type and shape scales are used as shipped.
`Type.kt` used to override two styles out of fifteen with hand-set sizes and was deleted -
that's how an app quietly loses the system's sizing, tracking and optical corrections.
Per-use deviations (`style = MaterialTheme.typography.titleMedium` on a button label) belong
at the call site.

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
| `ScreenScaffold.kt` | the frame for every non-menu page: backdrop + M3 top app bar with a back arrow + optional pinned bottom bar. Also holds `PageColumn` and `PinnedActionBar`. |
| `DiceFiveDialog.kt` | the app's one dialog shape, so the menu and the board ask questions the same way. |
| `AppLogo.kt` | placeholder app mark, built from the game's own dice via `IvoryDiceStyle`. |
| `Scrollbar.kt` | `LazyListScrollbar`, a `BoxScope` extension drawing a minimal scroll indicator over a `LazyColumn` - stock Compose has none for Android. Shared by the Leaderboard and Statistics screens. |

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
`PinnedActionBar` consumes the navigation bar inset itself, as a Scaffold bottom bar is
expected to.

## Component conventions

- **Stock M3 components**, themed by the scheme. No hand-built buttons. A gradient-and-gloss
  button set was written and then deleted - it re-implemented state layers, ripple and
  disabled colours worse than the framework does.
- **One filled button per screen**, tonal for the rest (the menu: filled Play, tonal
  destinations). That's M3's emphasis hierarchy, and it stops five identical slabs competing.
- **Segmented buttons** for small exclusive sets that fit one line: theme choice, player count
  1-4, Human/AI. Radio rows are for options that need a visible disabled state, like
  "Extended (coming soon)".
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

One list, not two. Achievements are grouped by theme with a quiet all-caps subheader, and run
easiest-first within each theme — which is just `Achievement`'s declaration order, so the
catalogue is the single place that ordering is decided. Unlocking one doesn't move it: it stays in
its ladder and is highlighted in place instead (a trophy icon, a raised `secondaryContainer` card),
so a ladder always reads as a ladder, earned rungs and all, rather than the earned ones jumping out
to a separate section. The "Hide unlocked" chip filters them out of their groups rather than
un-splitting anything, since there's no split left to undo.

An earlier version *did* split locked-first/unlocked-after (unlocked flat and newest-first, a
history rather than a to-do list) - reverted because it scattered a themed ladder in two: an
earned achievement disappeared from its group into an unrelated timeline, so seeing "how far along
this ladder am I" meant checking two different parts of the screen.

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

The stack behaviour, and why each number is what it is:

| | Value | Why |
|---|---|---|
| Position | bottom half only, stacked upward | keeps the board, the dice tray and the scorecard clear |
| Hold | 1.5s at full opacity | long enough to read a title, short enough not to sit in the way |
| Fade out | 900ms | a snap-out in the middle of a burst reads as a glitch |
| Stagger | 300ms between arrivals | a burst deals like cards instead of landing as a wall |
| Cap | 4 on screen | the rest **wait** rather than being dropped — the collector suspends on `snapshotFlow { banners.size }` until a slot frees |
| Swipe | 25% of the banner's width, either direction | clears one early; the event flow's buffer holds the backlog meanwhile |

Two variants, deliberately unequal: an **unlock** banner is `primaryContainer` with a trophy and
a two-line body; a **progress** banner is quieter (`surfaceContainerHigh`, one line plus a thin
`LinearProgressIndicator`), so a run of "2 of 3" nudges can never be mistaken for the real thing.

## Motion

Screen transitions are 350ms fades, set on the `NavHost` for all four directions. Navigation
Compose's own default is `fadeIn/fadeOut(tween(700))`, which reads as the app thinking between
a tap and the screen arriving. Set in one place so destinations can't drift apart.

## Constraints worth knowing

**Player names cap at 10 characters** (`GameSetupState.MAX_PLAYER_NAME_LENGTH`). The binding
constraint is not the setup form - it's the in-game header, where four tabs share one row,
leaving roughly 72dp of text per tab on a 360dp phone. It's a layout limit, not a gameplay or
storage one.

Two consequences:

- Enforced in `GameViewModel.setPlayerName`, not just the text field, so it also applies to
  names restored from preferences saved before the cap existed.
- **AI names obey the same cap.** They're players in the same header. The pool in
  `AiNameGenerator` was rewritten to fit ("The Probability Engine" was 22 characters), and
  `AiNameGeneratorTest` sweeps the whole pool so a long one added later fails the build
  instead of showing up ellipsised in a real game.

`PlayerHeaderBar` also steps names down to `labelMedium` at 3+ players, which is what makes
the cap actually deliver a full name on one line on a narrow phone.

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
- Real logo artwork. `AppLogo` is a placeholder; swap its body for a drawable but keep the
  "DiceFive" text, which `MainActivityTest` asserts on.
