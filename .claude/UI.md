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
- **Back navigation is on screen**, via the top app bar - not the system gesture alone.

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
