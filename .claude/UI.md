# DiceFive: UI reference

How the interface is built, and the decisions that are easy to undo by accident. Two companion files hold the detail
for particular screens:

- `UI_BOARD.md`: the game screen. The scorecard grid per mode, tiles, tray, cup, totals and undo, tablets and
  landscape, player-name caps ("Player names and the header"), the one roll path, a disabled scorecard box.
- `UI_SCREENS.md`: "The achievements list", "Achievement banners", "Style locks", "The Styles screen", "The
  Leaderboard's two views", and "The menu's backdrop and logo".

Code comments that cite one of those sections under `UI.md` now find it in the companion file.

All UI is shared Compose Multiplatform code in `app/shared/src/commonMain/kotlin/net/zodac/dicefive/ui/`; the paths
below are relative to it. It must stay platform-neutral: no Android or JVM API (the iOS compile fails), resources
through `Res.*`, and device work through `PlatformServices`.

**In one line**: everything outside the game board is stock Material 3 wearing a brand-seeded palette. The board owns
its own art and doesn't use colour roles.

## Two visual systems, deliberately separate

| | App chrome | Game board (`ui/game/`) |
|---|---|---|
| Colours from | `MaterialTheme.colorScheme` roles | fixed values in `Color.kt`'s game-table block |
| Swapped by | nothing: one dark scheme, `Theme.kt` | `ui/game/style/GameVisualTheme` |

- **Keep them apart.** Don't move the board onto colour roles, and don't hardcode chrome colours to match the board.
- **Where fixed colours live.** Classic styles' colours are named constants in `Color.kt`. Every other style's palette
  is a literal beside its entry in `StyleCatalog.kt`; each is used once, and hundreds of names would bury the shared
  ones.

## Colour

`ui/theme/Color.kt` holds M3 tonal palettes: one hue at fixed chroma per palette, and each role one tone from one
palette per M3's mapping. That's what makes `primary`/`onPrimary` and the other pairs reliable.

| Role | Seed | What it is |
|---|---|---|
| primary | `#FFC14D` | the board's "press this" gold |
| secondary | `#1E4B86` | the table's felt blue |
| tertiary | `#1B5E20` | the original brand green |
| neutral / neutral variant | the blue at very low chroma | cool greys |
| error | M3 baseline red | |

- **Never nudge single values by hand.** Change a seed and regenerate the whole set (Material Theme Builder or the M3
  tonal maths).
- **Dark only.** `DiceFiveTheme` takes no arguments and ignores system dark mode. Two things outside Compose depend on
  this:
  - `MainActivity` passes `SystemBarStyle.dark` to `enableEdgeToEdge`, or light-mode phones get dark status icons.
  - The window theme in `themes.xml` is dark `Theme.Material.NoActionBar`, so the first frame isn't white.
- **No dynamic colour.** The navy-and-gold table is the identity. `dynamicDarkColorScheme` is a two-line change if
  it's ever wanted (deferred).
- **No typography or shape overrides**, and no `Type.kt`. Make per-use changes at the call site
  (`style = MaterialTheme.typography.titleMedium`).
- **Sora (`SoraFontFamily`, `AppLogo.kt`) is for brand marks, never body text.**
  - It's used on:
    - the wordmark, and page and dialog titles;
    - Rules headings, Leaderboard mode-card titles, and unlock-banner titles;
    - totals-tooltip labels, the New Game player count (`SegmentedChoiceRow(brandFont = true)`) and the User/CPU chips;
    - mode and modifier names and values (pickers, timer lengths, steppers, the stored-rolls cap), and New Game field
      headlines;
    - the game's marks: tile labels ("3x", "2+2", "Alibi"), Hit List targets, corner badges, rolls left, and scores on
      tabs and Game Over.
  - Everything else uses the system font. That includes the descriptions under Sora names (bold Sora wrapped them).
  - **Columns of numbers stay in the system font.** Sora's digits are proportional; the maintainer declined `tnum`.
  - **The font file is weight 700 only**: ask for `FontWeight.Bold`.
  - **ASCII and Latin-1 only.** Never put a player's name or other free text in Sora. The stored-rolls cap is the one
    typed field in Sora, because it takes only 0-9. A new fixed Sora string must stay in U+0020-007E / U+00A0-00FF, or
    the font must be re-cut (the recipe is on `SoraFontFamily`).
- **`primary` is the one brand accent and means "gold".** Examples: the Statistics max, the scrollbar thumbs, the
  backdrop spotlight, page titles. Use it as a text or icon tint for emphasis, not `primaryContainer` as a fill, which
  would be a second accent. For gold as a background, copy an existing use (a filled button, the unlock banner).
- **Fixed non-role colours only where M3 has no role**: the board palette, `PlayerColour`, and the Leaderboard's
  `Silver` and `Bronze` (1st reuses `primary`). Never use one to dodge picking the right existing role.
- **M3 Expressive is deferred.** `MaterialExpressiveTheme` is internal in material3 1.4.x. The public API (with
  `MotionScheme` and `ButtonGroup`) is only in the 1.5.0 pre-releases, and alpha libraries stay out of releases.
  Revisit when 1.5.0 is stable.

## Shared chrome (`ui/common/`)

| File | What it is |
|---|---|
| `BrandBackdrop.kt` | The app's one scenery, built from colour roles: a surface gradient, a `primary` spotlight and a faint dice watermark. `showDice = false` drops the dice (the game screen). |
| `ScreenScaffold.kt` | The frame for every non-menu page: backdrop plus a transparent M3 top app bar with a back arrow and a bold `primary` title. Its content fades in over `PAGE_CONTENT_FADE_IN_MILLIS` (100ms), so late-arriving content eases in. Takes an optional `footer` (`FooterPill`), floated with the content padded by its height, and `horizontalPadding` (24dp default; the scorecard review uses 16). `PageTopBar` is its bar on its own (the results page). |
| `DiceFiveDialog.kt` | The one dialog shape. Its title is Sora, bold, `primary`, `headlineSmall`, as are `PickerDialog`, About and Licences. New dialogs match. |
| `AppLogo.kt` | The wordmark under a fan of dice and a cup, drawn live in the player's picks. |
| `FooterPill.kt` | The gold pill over a page's bottom (Rules' "2 of 6", Settings' version). Spoken forms and live regions go on its `modifier`. |
| `Scrollbar.kt` | `LazyListScrollbar` (over a `LazyColumn`), `VerticalScrollbar` (a draggable bar in a page margin) and `HorizontalScrollbar` (under a `Row` or `LazyRow`). See "Scrollbars". |
| `SegmentedChoiceRow.kt` | The one segmented row, generic over the option type. No checkmark icon (`icon = {}`; it crowded labels out). Takes `enabled` and optional per-option glyphs (the timer's "None" is a crossed-out timer, spoken "No timer"). |
| `ChoicePicker.kt` | `ChoicePicker` and `ModifierPicker`. All styling lives in `PickerField`, `PickerDialog` and `PickerRowText`. |
| `AppTooltip.kt` | Every tooltip (`AppTooltip`, `OnDemandTooltip`). See the gotchas. |
| `ShrinkThenWrapText.kt` | Shrinks a label to 12sp at most, then wraps it. Used for tab names, banner titles and Settings labels. |
| `ForegroundDelay.kt`, `ReduceMotion.kt` | See "Nothing runs in the background" and "Reduced motion". |

### PageColumn, insets, page actions

- **`PageColumn`** fills the screen but scrolls when the content can't fit. The inner column is held to at least the
  viewport height, so `weight` spacers work.
  - **The trap**: a `weight` inside an unbounded scroll collapses to nothing without `heightIn(min = ...)`.
- **Edge-to-edge** (`enableEdgeToEdge()`). **Exactly one** inset source applies per page:
  - inside `ScreenScaffold`, pass its `innerPadding` to `PageColumn` as `contentPadding`;
  - a standalone page (the menu, the results) uses `PageColumn`'s system-bar default.

  Both insets the page twice; neither puts content under the clock.
- **No pinned bottom bar for a main action.** Achievement banners land there. Start Game follows the form instead: the
  form scrolls in `Modifier.weight(1f, fill = false)` with the button after it.
- **An added element must not move or re-space existing ones.**

## Component conventions

- **Collect flows** with `collectAsStateWithLifecycle`, never `collectAsState`. A `Flow` needs `initialValue =`.
- **Use stock M3 components**, themed by the scheme. No hand-built buttons.
- **One filled button per screen**, tonal for the rest (the menu: filled Play, tonal destinations).
- **The menu's Play button splits when a game is saved** (`MenuScreen.PlayButton`).
  - It becomes New Game | Continue: the same 56dp pill cut by a 2dp gap, each half a stock `Button` with straight
    inner edges. Both halves stay filled `primary`, because neither is destructive.
  - It is not a "Resume?" dialog. It isn't M3's split button (an action plus a menu) or segmented buttons (choices).
    It's closest to Expressive's connected button group, whose press morph waits for `ButtonGroup` (1.5.0).
- **Segmented buttons** are for small exclusive sets on one line: player count, AI difficulty, turn timer, the
  Animations level.
  - The end segments' labels are inset `SEGMENT_CAP_INSET` (4dp) on the outer side, because those ends are half
    circles.
  - `FittedSegmentedChoiceRow` shrinks the labels together, then switches to `_short` initials.
- **Pickers** (`ChoicePicker`, `ModifierPicker`) are for long options or ones needing descriptions.
  - Closed, a picker is one outlined field: headline, supporting line and arrow.
  - Tapped, it opens a full-screen page: a `Dialog` around `ScreenScaffold`, with `LazyListScrollbar`.
  - `ChoicePicker` (game mode) uses radio rows; picking one closes it.
  - In `ModifierPicker`, each modifier is a card with a switch row, plus a segmented row, a `ModifierStepper` or a
    `ModifierNumberField`. These show only while the modifier is on, opening beneath the switch over 220ms (none under
    reduced motion). The switch never moves.
  - Changes apply live; Done closes it.
  - **To add a modifier**: a `ModifierSetting` in the setup list, plus a Rules page at the end of the Modifiers group.
  - TalkBack: the field is a `DropdownList` named by its title, with its value as state; rows are radio buttons or
    switches with collection positions (`ChoicePickerTest`).
- **An option the form overrides is locked, not hidden** (`ModifierSetting.lockedNote`, `SegmentedChoiceRow`'s
  `enabled`). The layout stays still, and the player's pick is kept for when it applies again.
- **Cards group sections.** A `ListItem` in a Card needs `ListItemDefaults.colors(containerColor = Color.Transparent)`,
  or it paints a second surface.
- **`CONTENT_MAX_WIDTH` (460dp)** caps page width for tablets.
- **Back navigation is on screen** (the top app bar) for sub-screens.
  - The menu has **no `BackHandler`**, and gets no quit button or exit confirmation; Android guidance discourages both.
  - The one exception is `GameScreen`'s `BackHandler`, which confirms before leaving a game in progress.
- **Screen transitions** are 350ms fades, set once on the `NavHost`. Navigation Compose's 700ms default felt sluggish.

## Accessibility

**Mandatory for every new or changed UI element.** A change isn't done until it has been through this checklist, and
the report says what TalkBack does. Stock components are not assumed to be enough: M3 tabs, for example, never say
how many tabs there are.

### The checklist

1. **Check what a stock component actually sets.** Read its source, or `javap` it from the Gradle cache, then fill in
   what's missing.
2. **What does TalkBack say on landing?** A name, a role, and any state ("held", "3 rolls left"). Action icons get a
   `contentDescription` naming the action, without the word "button". Decorative icons get `null`.
3. **Any visual-only meaning needs a spoken twin**: colour, an edge fade, a badge, a position, an animation.
4. **One of a set not all in view?** Give the container `CollectionInfo` and each item `CollectionItemInfo`.
5. **Every action must be reachable by TalkBack.** Drags, swipes, long presses and raw `pointerInput` are invisible to
   it. Expose them as semantics actions (`onClick` with a label, `customActions`).
6. **One control, one target, one announcement.** Merge a label with its control, or make the row the control
   (`SwitchSetting`: `toggleable(role = Role.Switch)`).
7. **Titles are headings** (`semantics { heading() }`).
8. **Things that appear and go by themselves** are polite live regions with a fixed summary, not their animated text.
9. **Text must read well aloud.** Give `5-5-5-2-1`, `15pts` or a "- " bullet a spoken form where it matters.
10. **Sizes.** Keep the 48dp minimum touch target. Large system fonts must scroll, not clip.

**Verifying**: add a Robolectric test asserting the semantics (`BoardSemanticsTest` and `RulesScreenAccessibilityTest`
are the patterns). Real TalkBack speech can't be checked here, so the report says it wasn't heard on a device.

### Patterns in place (reuse them)

- **All semantics are added, never drawn differently.** `ui/game/BoardSemantics.kt` holds the board's spoken names.
  The board itself is in `UI_BOARD.md`.
- **`clearAndSetSemantics` only overrides what's inside it**, so put it *before* `clickable`/`combinedClickable` in
  the chain. Otherwise the clickable's `onClick` leaks through (`ScreenReaderSemanticsTest`).
- **"Why is this locked?" is one rule everywhere**: a tap shows a short tooltip, a long press opens the full dialog,
  and TalkBack's one action opens that dialog (a tooltip isn't announced). Styles and Achievements both do this.
- **Raw gestures that shouldn't be buttons** use `detectTapGestures`, so TalkBack isn't handed an unnamed button. The
  superuser counter and the logo dice do this.
- **A field with no visible label** gets an accessibility-only label, and its error as `error(...)`.
- **A pager announces where it lands.** The Rules footer is spoken "Modes, page 2 of 6" as a polite live region.
- **A scrolling tab row says its count.** Each row sets `CollectionInfo` ("Tab, 1 of 6"), and its edge chevrons are
  cleared from semantics.
- **Anything floated over scrolling content** needs matching padding at the content's end. Bring-into-view
  (`performScrollTo`, TalkBack focus) still scrolls under the float.
- **The turn timer** speaks its seconds in words. Once it flashes, it's a polite live region announced once.
- **Headings**: page titles, Styles categories, achievement category headers, Rules pages, About sections.

### Known gaps (say so rather than claim coverage)

- **Rules notation.** "3x"/"5x" are read as written. "pts" is spoken "points", and the example dice are read in full.
- **System reduced motion** is honoured on Android only. On iOS, `reduceMotion()` returns false until it's wired up.
  None of it has been seen on a device.
- **Large fonts** have never been checked on a device. `ShrinkThenWrapTextTest` pins the sizes chosen.
  - What adapts:
    - other `maxLines = 1` sites allow two lines;
    - the Scores columns re-split above 1.15x;
    - the New Game player row stacks above 1.05x.
  - Gaps: `ScoreGrid` numbers stay on one line by design, and 11sp `labelSmall` captions remain.
  - On a device, try 130% and 200% with: a 4-player game with 8-character names, the longest banners, Settings, New
    Game, the Leaderboard and Statistics.
- **Timer flash rate** hasn't been checked against the three-flashes-per-second limit (`TURN_TIMER_FLASH_PERIOD_MILLIS`;
  it's a colour fade).
- **Contrast** (dark theme; WCAG 3:1 for graphics, 4.5:1 for text). Only these pairs have been computed:
  - Fine:
    - tile icon on teal: 8.5-12.7:1;
    - gold on a highlighted tile: 7.3:1;
    - `onSurfaceVariant` on the surface: 10.9:1;
    - the selected style's border: 10.9:1.
  - Borderline, but exempt or spoken:
    - a scored tile's icon: 3.2-3.5:1;
    - disabled Undo: 2.4-2.9:1;
    - a scored tile against the page: 1.4:1 (its state is spoken).
  - Below 3:1:
    - an unselected style tile's `outlineVariant` border: 2.0:1 (`outline` would be 5.8:1);
    - the locked padlock over light art: ~2.1:1 (it's spoken "Locked").
- **Not looked at**: whether a title-less dialog announces anything (the locked-style dialog passes `title = null`),
  and the Achievements rows' locked state beyond the icon.

## Motion

- **A looping animation must not run while there's nothing to show.** A `rememberInfiniteTransition` requests every
  frame while it's composed, and reading it with `by` recomposes every frame.
  - Compose the clock only while it's needed: a highlighted tile's `GlowBorder`, `rememberShakeWobble` while rolling,
    the timer's last seconds.
  - Read the value in the draw phase (`graphicsLayer { }`, `drawBehind { }`).
- **Anything new that loops while nothing is happening** must read `ambientMotion` (see "Reduced motion").

## Nothing runs in the background

The process and its coroutines stay alive when the app is in the background. **No shared code uses a plain `delay`**
(`NoBackgroundTimersTest` enforces this).

- **Turn timer and CPU loop**: `GameViewModel`'s `pausableDelay`, driven by `setForeground`. `GameScreen` calls it from
  lifecycle callbacks, not from composition (a backgrounded app draws nothing), and also when another screen opens
  over the game.
- **Every other wait**: `Lifecycle.delayWhileResumed` (`ui/common/ForegroundDelay.kt`). This covers banners, the
  shake and settle, face flicker, the rabbit, the flowerpot, row flashes, warm-ups and superuser ticks. A wait cut off
  by backgrounding restarts in full on return.
- **Also stopped in the background**:
  - sounds (`SoundPlayer.pause`/`resume`, `SoundPool.autoPause`) and vibration (`HapticsPlayer.cancel`), through
    lifecycle observers;
  - the shake sensor and the logo's pupil sensor;
  - the reduced-motion observer.
- The app has no services, receivers or background work.
- **iOS**: pause, resume and cancel do nothing yet (`AVAudioPlayer.pause()` is the fix), and `TransientMessageHost`
  still uses `delay`.

## Reduced motion

**The player's Animations level** (`AnimationLevel`, `SettingsRepository.animationLevel`) is a `SegmentedSetting` card
in Settings, between the switches and the resets.
- It runs Off to High from the start edge, so it's mirrored in Arabic. It uses `FittedSegmentedChoiceRow`, with Off as
  a crossed-out motion icon.
- It has no description, by the maintainer's choice.
- It isn't inside a `ListItem`, whose intrinsic measuring throws on `BoxWithConstraints`.

| Level | Ambient decoration | Gameplay motion | Score tile pulse | Frames |
|---|---|---|---|---|
| **High** (default) | moves | plays | pulses | the screen's rate |
| **Medium** | still | plays | pulses | ≤60 |
| **Low** | still | dice flick through faces in place (`simpleDiceRoll`) | steady | ≤30 |
| **Off** | still | none (`LocalReduceMotion`) | steady | ≤30 |

**Ambient decoration** is anything that keeps an idle screen redrawing:
- the menu's drift, `FloatingDiceBackground`, `TwinkleClock`/`rememberArtSeconds` art, the RGB Halo's rings,
  `rememberAmbientCycle` cups, and googly pupils;
- each reads `ambientMotion` (`ui/common/ReduceMotion.kt`), which is true only at High. Below High it holds its
  reduced-motion pose, so no new art is needed;
- a cup whose ambient art travels (the Fishbowl's fish) holds where it was when its cycle stops, the cup spent, and
  sets off from there next turn (`HeldLap`), rather than snapping to its resting pose;
- the score tile reads `scorePulse`;
- both are false under a nested `LocalReduceMotion`, which is how an off-screen Styles tile is held still.

**Frames.**
- `MainActivity` builds the recomposer on `CappedFrameClock` (app/android), which `PlatformServices.capFrameRate` sets
  from the level.
- The cap skips whole refreshes: a 60 cap gives 45 on 90Hz. So a capped level also asks the window for the lowest
  refresh rate of at least 60Hz (`preferRefreshRateFor`, `RefreshRateTest`). Never below 60Hz, so touch stays
  responsive.
- Only the player's level caps frames. iOS doesn't cap yet.
- **The 60fps floor holds for High and Medium**; 30fps happens only when the player picks Low or Off.

**Two sources, one switch.** `LocalReduceMotion` (provided once in `DiceFiveApp`, beside `LocalAnimationLevel`) is
true when the system asks for less motion **or** the level is Off.
- The system setting also sets Compose's `MotionDurationScale`, which the app's Off can't. Under the app's Off,
  ripples, switch thumbs and flings still run, at the 30fps cap.

**Two layers.**
- **What Compose handles.** On Android, Compose follows the system animation scale by itself, so every `tween`,
  `animate*AsState` and `Animatable` started from composition finishes instantly.
  - An `InfiniteTransition` is not safe: it ends at its **target**, which may not be a resting pose. That's why the
    cup wobble, timer flash and tile glow are gated explicitly.
- **What needs `LocalReduceMotion`**: `withFrameNanos` loops, `delay`-driven sequences and physics, and decoration that
  should stop outright.

**The rule when it's true: decoration stops, the outcome still happens, instantly and at rest. What the game does
never changes.** Every cup, die, mat and background follows it (`CupReducedMotionTest` checks that every cup snaps).
Examples:
- no tile glow (a steady gold border), and a steady red timer;
- no drift, twinkle, sparkle, neon pulse or glitch;
- cup lids, doors and baskets snap open, and spills and lava simply appear;
- the rabbit appears without a rise;
- the dice aren't tossed: they stay put through the shake and snap to their results. The board cup stands while the
  roll is in it, then is simply tipped;
- page and banner fades are off: a banner burst is all there at once, and a progress count doesn't climb;
- scores appear rather than count up, and there are no fireworks;
- **game pacing**: scoring doesn't wait for a toss, and neither does the CPU (`GameViewModel.diceAnimated`), which also
  drops `RELEASE_GAP_MS`;
- **loading**: the Styles page builds every on-screen tile in its first frame, and continuing a game shows only the
  plain backdrop (a spinner after `RESUME_SPINNER_DELAY_MILLIS`).

**Audio and haptics are not motion, and stay.** The shake sound (~400ms), the shake buzz (336ms) and the landing sound
(~490ms) are timed to the `CUP_SHAKE_MILLIS` window, and for a TalkBack user they *are* the roll.
- `cupShakeMillis` (`ui/game/CupShake.kt`) keeps the full window whenever sound or vibration is on. With both off it
  shrinks to 100ms (not 0, so `isRolling` is seen changing).
- **Never shorten the window without re-checking the clips and `SHAKE_HAPTIC_MILLIS`.**
- The CPU's following pauses (`ROLL_GAP_MS`, `AI_STEP_DELAY_MS`, `AI_REACTION_DELAY_MS`) never change. Only its
  animation waits go: `diceTossMillis`, `RELEASE_GAP_MS`, and the one-die-at-a-time hold steps (`AI_HOLD_STEP_MS`,
  `AI_RELEASE_TO_HOLD_GAP_MS`; holds then land together via `changeHolds`).
- The Game Over fanfare and hold ticks follow their own settings.

## Scrollbars

- **Every scrolling thing uses the app's own bars**, never a styled platform scrollbar.
  - Plain scrolling pages (Styles, each Rules page) put a `VerticalScrollbar` in the right margin. The Rules pages share
    one beside the pager.
  - The Licences list on Android passes pixel position, max and `scrollBy` (`LicenceScroll`), with the platform
    scrollbar off.
- **Key a scrollbar's "anything to scroll?" check on the state it's handed.** Unkeyed, it keeps the first page's answer.
- **`LazyListScrollbar`** goes over any `LazyColumn` that can outgrow the screen: a `primary` thumb on a
  `surfaceContainerHighest` track. Its rules, each fixing a real bug on lists with mixed heights:
  - Show or hide from `canScrollForward`/`canScrollBackward`, never `visibleItemsInfo.size` vs `totalItemsCount`.
    Prefetching breaks that comparison.
  - Size and position are pixel-based (each visible item's `size`/`offset`), not counts of items.
  - Item sizes are remembered per index in a map that only grows, never re-averaged each frame.
  - The size estimate is frozen at the very first layout; the position uses exact remembered sizes.
  - Remembered state resets when `totalItemsCount` changes. This is checked in the `Canvas` draw phase
    (`ScrollbarMemory.totalItemsAtLastCheck`), not by keying a `remember`.
  - The endpoints are pinned to `canScrollForward`/`canScrollBackward`, which are exact.

## Player colours

**A player's colour is chosen on New Game and read from `PlayerState.colour`.**
- It's a `PlayerColour` (eight of them), turned into a `Color` only in `PlayerColour.color` (`ui/theme/Color.kt`).
- Tab, name, score and the last-scored box outline all use it. Never use a seat index.
- **The active tab's frame** is the player's `ScoreFrame` in their colour. It's decoration only; the "Current turn"
  state is unchanged.
- **Every tab sits on a panel**: `surfaceContainer` in the tab's 10dp shape, tinted 8% with the player's colour
  (`PANEL_TINT`).
  - The tint is capped by contrast: at 8% the lowest is Purple at 4.63:1, and at 10% Purple fails (4.49:1).
  - Re-check all eight colours before raising it.
- **Frames stay off the tab's text.**
  - The name sits `NAME_TOP_DROP` (3dp) down.
  - Every frame keeps 1.5dp clear of name, score, place and dot, at 360dp with 1-4 players, the widest names and 1.3x
    font (`ScoreFrameClearanceTest`).
  - Side padding grows with the font scale (2 to 10dp).
  - The dot row keeps its height even solo.
  - A frame that fails is drawn smaller or further out, never given more room.
- **Setup.** A 28dp circle (48dp target) opens a 4x2 `DropdownMenu` of the eight colours (`ui/setup/PlayerColourPicker.kt`).
  - It's a curated set, not a free picker: M3 has no colour picker, and a free choice could clash or be unreadable.
    Add a colour only if it's light enough for the dark page and distinct from the others.
- **Always four distinct.** `setPlayerColour` swaps with whichever slot holds the colour, across all four slots. The
  defaults (`PlayerColour.defaultFor`) are cyan, green, purple and amber.
- **Persisted** per slot (`player_colour_N`, falling back to the defaults if not distinct) and in a saved game.
- **TalkBack**: "Player 2 colour, Pink", action "Choose colour". Choices are radio buttons, and a taken one says
  "swaps with player 1" (`PlayerColourPickerTest`).

## Gotchas

- **A scrolling container eats a tap made while it scrolls.** Anything that scrolls itself after a tap, such as M3's
  scrollable tab rows re-centring, loses the next tap.
  - The Rules rows never re-centre (`KeepTabInView` moves them only when the tab is hidden).
  - A row that fits must not be scrollable even by a pixel.
  - Test with real touches a few frames apart (`performTouchInput { click() }`, `autoAdvance = false`); semantics
    clicks skip the problem.
- **An edge fade isn't a "there's more" hint on its own**, because it only shows when content happens to be under it.
  Pair it with something always there (the Rules chevrons).
- **`Modifier.align` needs the Box to be the actual parent.** In a `Scaffold` body it's accepted and silently ignored.
  Use a real `Box(contentAlignment = ...)`.
- **`padding` before `size` shrinks the node.** Use `offset` to move without resizing.
- **Every tooltip is `AppTooltip`**, never `TooltipBox` directly.
  - `rememberAppTooltipState()` lets a tap show one.
  - Pass `message`, or `body` for a custom layout.
  - The deprecated `rememberPlainTooltipPositionProvider` (no replacement until 1.5.0) is suppressed once, there.
  - `TooltipBox`'s anchor merges its content and drops the role, so put `Role.Button` on the tooltip's modifier too.
  - For long lists of rarely-pressed rows, use `OnDemandTooltip`: a plain gesture plus a "Show details" action until
    first use.
- **`ViewModelConstructorInComposable`** fires on `@Preview`s that build a view model. It's suppressed on that
  preview.
- **The launcher icon's safe zone is a floor, not a guarantee.** The spec's 66dp circle passed on paper, but a device
  still cropped the dice.
  - Keep the baked-in margin (everything inside one `<group scaleX="0.8" scaleY="0.8" pivotX="54" pivotY="54">`), and
    get an on-device look for icon work.
  - `ic_launcher_monochrome.xml` (the themed icon) is a flat silhouette: pips and band are even-odd cut-outs, and its
    geometry is copied from the foreground. Change the two together.
  - `ic_launcher.xml` is the only wrapper (`roundIcon` too).

## Verifying UI work

Run the `test` skill's gate. Render screens with the `render` skill: real screens at given sizes and fonts. A
behaviour a screen drives is tested with the real screen and view model (`GameScreenCupGateTest`). The `emulator`
skill shows the real app. A device is still needed for TalkBack's speech, frame times, haptics and the accelerometer.
Report what was and wasn't seen, and prefer layouts that degrade safely on screens you can't check.
