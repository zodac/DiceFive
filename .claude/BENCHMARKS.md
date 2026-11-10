# DiceFive: frame cost

The patterns that keep frames cheap, what the art costs today, and what's still open. Read this for a reported
stutter, or before adding art that draws every frame. **How to measure** is the `benchmark` skill.

The short version: **keep per-frame work out of what a toss or shake redraws.** The cup's shake and tip is where a
player notices first.

## Patterns that keep frames cheap

Each of these fixed a reported stutter. New work must keep them.

- **Paint costly, unchanging surfaces once.**
    - `drawCachedSurface(key)` (`Patterns.kt`) paints once per key and size into an `ImageBitmap`, then stamps it. It's
        an LRU of 128 and at most 48MB (~90KB each at die size, ~8MB for a phone-sized background), released when Android
        trims memory in the background (`DiceFiveApplication.onTrimMemory`). The key captures everything except size.
        Marble and Frosted cost 10x more
        when repainted every frame.
    - A tossed die's faces are recorded once into `GraphicsLayer`s (`FaceLayers`, `RollingDie.kt`); only shading and
        perspective change per frame.
- **Paint very heavy art off the frame.** The Treasure Chest pile settles and paints on a background thread
    (`rememberHoardArt`), with a stand-in until it's ready. Painting it on the frame hitched ~160ms. Draw tilted discs as
    rotated ovals.
- **Hold art still where nobody sees it move.**
    - A tumbling face is frozen (`LocalArtFrozen`), except the landing face in its last quarter-turn.
    - Off-screen Styles tiles get `LocalReduceMotion`.
    - Ambient loops obey the Animations levels (`UI.md`).
- **Animate opacity or tint, not geometry.** Neon paints its tube once and pulses the alpha. RGB paints once in white
    and tints (`ColorFilter.tint(.., SrcIn)`). A tinted image per lamp is slower than a small gradient.
    - A layer's animated alpha takes an offscreen pass per layer by default. Where its content doesn't overlap itself,
        use `CompositingStrategy.ModulateAlpha`, as a score tile's glow does: up to one lit ring per open box, every frame.
- **Draw art on its own clock in `BoundedArt`, not a `Canvas`.** A state read while drawing re-records the whole layer it's
    drawn in, and any change to a Compose layer redraws the whole window on the GPU (see "Still on the table"). `BoundedArt`
    is a `RenderNode` clipped to the art, re-recorded when what it read changes, so neither happens. Give it `overflow` for
    art past its box: a clip cuts off anything further. Art that only fades is cheaper as `PulsingArt`, recorded once.
    Used by a board's background, an animated mat, `CupCanvas` and the three cups with a canvas of their own, a die's
    `PipShape.CUSTOM`, and Glitch. `BoundedArtTest` holds it.
- **Art that fills the screen gets a layer instead** (`Modifier.redrawsAlone`, on a sibling under what it sits behind): the
    menu's drifting dice, where a node would bound nothing. It saves the recording, not the redraw. `Modifier.clip` is a
    layer too. `RedrawsAloneTest` holds the tray and backdrop to it.
- **Keep a shape that only changes colour.** `drawStars` builds its paths once per seed, size, and density, and a twinkle
    only changes their alpha. It used to rebuild up to ~190 paths a frame on a phone-sized board.
- **Don't rebuild nodes on a state change.**
    - One `Moving(...)` call site takes either move's arguments, so landing doesn't rebuild every die.
    - A turn's first dice are composed off the mat during the shake.
    - The cup starts pouring `CUP_POUR_LEAD_MILLIS` (64ms) before the dice land.
- **Don't re-lay out unchanged text.**
    - Score cells keep their number laid out and swap visibility with "-" (`CategoryCell`, `SlotScore`, `ShownScore`
        compares by value).
    - `FontFitCache` keeps each label's chosen size.
    - Styles tile names share one `TextMeasurer` (`rememberTileLabelMeasurer`).
    - `D20DiceStyle` shares one measurer, with no colour in the measured style.
    - `formatList`, `isRightToLeft` and the number formats are cached per language.
- **Read changing state where it's used.**
    - The Rules screen reads the current page only where it's shown (`derivedStateOf`), and remembers its tab modifiers.
    - The turn timer's seconds are read only by its badge (`RunningTurnTimer`): a tick recomposes the badge, not the
        board.
    - A cup's shake and pour angle is read only by its `graphicsLayer` and by art that draws with its `CupPose` (a
        liquid), and the liquid's spring is aimed from its own small `Slosh` composable (`CupRotation.kt`). A cup recomposes
        3 times a roll, not every frame, and a cup without a liquid is only turned, not repainted, as it shakes. Measured
        2026-10-28, desktop JVM: no change in frame time, as the harness repaints every frame anyway; unmeasured on a phone.
        Shake and pour frames are pixel-identical to before.
    - Settings, achievements and the saved-game flag emit only when their own value changes (`distinctUntilChanged`).
- **Load ahead of the first use.** The sound clips load a second after launch, off the main thread, not on a game's
    first roll.
- **Warm a page up off screen with `OffscreenWarmUp`** (`ui/common`): a 1dp clipped box under an opaque screen,
    hidden from screen readers, that steps through passes a frame or two each once the screen above has settled.
    `GameWarmUp`, `StylesWarmUp` and `ScoresWarmUp` are each one; a new warm-up is too. Tests see it by
    `OFFSCREEN_WARM_UP_TAG`.
- **Paint a game's art before it opens.** `GameWarmUp` (`GameScreen.kt`), under the setup form, draws the board,
    cup, mat and every face of every die in the player's art, once, 600ms after the form settles. It lays them out with
    the game's own `GamePage` and `GameTable`, so each surface is cached at the exact size the game uses.
    - A die has its own face per value (`naturalPatternSeed`): five dice need 30 faces, so the values are rotated a
        pass at a time.
    - `GameWarmUpTest` holds the sizes to the game's: no surface is painted on opening or on the first roll. A
        change to the game's layout that the warm-up doesn't share fails it.
    - Continue has no setup form, so `SavedGameWarmUp` does the same for the saved game on the menu, 1.2s after it
        settles (after the Styles warm-up starts). `GameWarmUpTest` holds it too.
- **Build a page behind the page cover** (`UI.md`, "Component conventions"). A page's first frames (composition, first
    paint, data arriving) are its costliest. They now land while the cover is opaque, and only one page is ever drawn at
    a time. Before, a 350ms cross-fade drew both pages every frame and started only after the next page's first frame.
    Measured 2026-10-27, desktop JVM, real `MainActivity` (compose and layout of the opening frame, ms):
    - The first opening after launch: Setup 117-138, Rules 85, Achievements 55-60, Settings 56-59, Start Game 51, Styles
        31-41. Later openings: 5-20. All of these now land with the cover at full strength; the fade-in frames are under
        5, apart from Styles' tile building.
    - Continue used to make two navigations, the board arriving part-way through two overlapping cross-fades. It now
        opens the game under one cover.
    - Going back isn't covered. Back to a game is 18-25 then 9-16 over two frames, back to the menu 8-11 then 4-10.
- **The Styles pages.**
    - The Styles page draws six previews (one per category's pick); each category's page builds only its own tiles,
        all at once (40 or so at most, the dice), in place of every category's rows on one page (about 150 tiles).
    - Each tile's preview is drawn in its own offscreen layer.
    - `StylesWarmUp` pre-draws the Styles page on the menu's idle frames, and `StyleCategoriesWarmUp` the category pages
        on the Styles page's (`UI_SCREENS.md`).
- **The Leaderboard and Statistics.** Neither page's frames grow with the scores behind them: every board reads
    1,000 rows a page, Statistics is a `LazyColumn` (only the cards on screen are built), and every Leaderboard board is
    one `LeaderboardTable`, read when first picked. What grew was elsewhere:
    - The old Game Mode view built a screenful of cards first, then one a frame (`ScreenfulFirstColumn`). Built all at
        once, seven cards (~10-12ms each) held the switch up while only two or three could be seen. The drop-down shows
        one board at a time, so that view and its warm-up are gone.
    - Statistics reads every player in three queries (`ScoreDao.playerRecords`), not two more per player.
    - Statistics finds whether every card fits open from the real list laid out with them open, which builds only the
        cards on screen, rather than from a hidden copy with every card open.
    - A Statistics card's open content is built ahead (`OpenAhead`): each closed card on screen builds it, unseen and
        taking no room, one card a frame while the page is still (cover gone, not scrolling, no card moving). A tap then
        only gives it its room. Built on the tap, its first text layout held the opening's first frame. A card that scrolls
        away takes what it built with it; building every card's would undo the lazy list.
- **The Rules screen.**
    - A tab snaps to its page (`scrollToPage`) without building the pages between.
    - A page builds its first screenful, then the rest over the following frames (`rememberShownBlocks`).
    - Tab rows never re-centre (`KeepTabInView`).

## Current costs

Desktop JVM, warm, in ms. These are relative only: compare within a run.

- **Dice, seven at a time** (row harness, median of three runs, p95):
    - At rest, no style is over 10: Tally ~9, Glitter ~8.6, RGB ~8, Classic ~6.5, blank 3-4.
    - Tumbling, every style is over 10: Classic ~12, the heaviest ~16, blank ~5.
    - Composition and layout are under 1ms; the rest is drawing 14 face layers under perspective. That's probably
        overstated versus a device's textured quads (unmeasured).
    - Bestagon is cheaper: it spins one flat face.
    - Devil, Starry, Wireframe and Ocean (all drawn live but Ocean's waves, which are painted) sit inside that range: at
        rest p95 6.6-8.1 against Classic ~6.4 and Glitter ~9.7, tumbling p95 14-16 against Classic ~17.9 and Glitter ~19.5.
- **Cups, backgrounds, mats** (board plus tray): 2-4 a frame in idle, shake and toss for every one, within ~20% of each
    other. A cup alone is 0.8-1.7.
    - The RGB Halo with the player's dice inside (the cup alone, one run, p95, measured when they were 15dp and 14dp): 1.2
        empty, 1.5 with five Classic and 1.7 with seven, up to 3.1 at rest and 2.9 shaken with seven RGB or Glitter. Its
        dice are live while it stands and gone once poured.
    - Its dice painted from nothing, the first frame's worst: seven Stone 43, seven Glitter 26, and 12 at most in a shake
        that shows new faces. A game's warm-up paints the tray's 44dp faces first, which the cup then stamps; a seven-dice
        tray's smaller faces aren't the cup's, so only the faces the warm-up's cup showed are ready there.
    - A patterned mat's or background's first frame is 17-55. Stone stamps three textured flags to stay there.
    - First paint, whole-screen harness (411x891dp xxhdpi, ~25 floor): the Leather and Granite backgrounds add ~50,
        Black Marble, Meadow and the Honeycomb one 30-38; Honeycomb and Stone mats ~24; Leather and Stone dice ~22.
    - The game's opening frame, heavy art (Granite background and mat, Leather dice; or Oxblood, Black Marble, White
        Marble) against Classic, three runs: 128-157 cold, 73-95 after `GameWarmUp`, Classic 71-78. The warm-up's own
        worst frame on the setup screen is 55-91 (Classic 26-36), the board pass, which paints the background.
    - The Stone, Meadow and Circuit backgrounds are painted once and sit within ~3.5ms of the plain Classic one (whole
        screen, captured: 25-26ms against 22), level with Pinstripe and Starry. Neon is ~29ms, level with
        Honeycomb and under Glitter and Glitch (32-34ms).
    - Treasure Chest, Urn, Volcano and Picnic Basket are within a few ms of Classic Gold.
    - A cup alone, 110x96dp (median of three runs, p95): the Fishbowl is ~6.1 in idle, ~6.7 shaking and ~6.6 pouring,
        level with the Cauldron (~7.1, ~7.2, ~6.2) and Classic Gold in the same runs. Clipping its fish to the water with
        two nested clips, not a `Path.combine` intersection built every frame, took its shake and pour from ~9.2-9.5.
    - A background alone, 411x560dp (median of three runs, p95): Ocean Waves is ~19, under Glitch ~22 and Glitter ~20,
        level with Starry ~17.5 and Classic ~16-20. It draws only its seven moving crest lines each frame; its glow is
        painted once. A gradient band hanging under each crest cost ~5 more and was dropped. Ocean Lagoon is painted once
        and sits with Neon; the Ocean mats sit with Classic and Stone.
- **Score frames** (one draw of the active tab's frame, 3x, desktop JVM, median ms): a plain ring 0.03-0.06; Neon,
    Pixel, Greek Key and the tribal and floral ones 0.1-0.5; Celtic Knot 0.62. Drawn live, the wreath was 0.61, Rope 1.08,
    Chain 0.90, and Starry Sparkles, Constellation and Marquee Bulbs 0.54-0.58. Those nine are `painted` and cost 0.013.
- **Live per frame**, for moving art:
    - glitter: 2 sparkles a die, 6 on its mat, 5 on its background, 2 on its cup;
    - neon strokes, and the Neon background's horizon glow (one gradient rect; its grid is painted once);
    - the Honeycomb bee (~40 shapes);
    - the Volcano's crater, 5 lava ribbons and eruption;
    - the Fishbowl's water, clownfish (body, 3 bands of up to 16 pieces, fins, eyes), 5 fronds, 3 rocks and 3 bubbles;
    - the Ocean Waves background's 7 crest lines;
    - the Basket's lids and food;
    - the Chest's lid, top coins and spill;
    - glitch bands only during a burst.
- **Game screen.** A landing frame is 3-6 (~17 for the first of a session). A reroll's tap frame is 3.5-8. Third Wind
    stacks add 0.15-0.4 a frame and ~2 to the tap. No score line recomposes on shake or toss frames.
- **Leaderboard and Statistics at scale** (2026-10-27, warm, compose and layout only; in-memory Room on Robolectric's
    SQLite, 4 to 400 players and 40 to 10,000 games, ~25,000 rows):
    - Opening either page is 10-30 at every size. Scrolling either, or a mode card, is under 1 a frame. The next page of
        a board was 13-23 (measured on Combined when it held Standard's and Tricolour's games, before 1.5.0).
    - The switch to Game Mode: 40-100 in one frame with every card built at once, flat with the scores. Now 11-36, then
        4-15 for each frame that builds one more card.
    - Statistics' read: 7 at 30 players, 24 at 150, 35-42 at 400 with two queries a player. Now 2.7, 8 and 22.
    - Statistics with 12 players (not all fitting open): 72-87 then 19-25, behind the page cover. Now 31-35 then 22-25.
    - Leaderboard reads are under 1 a page, 2-4 for all seven cards.
    - Opening a Statistics card: 14-17 on the tap frame, ~63% of it first text layout, then ~1 a frame. Built ahead,
        2-2.4. Building ahead costs 17-26 a card, on every other frame once the page is still (seven cards on a 411x891dp
        screen). Closing is 2-4, then ~1, and ~3 when a card moves into view below. A card moves on a 300ms eased tween: the
        default spring took ~30 frames, its last dozen a pixel or so each.
- **A Leaderboard page by size** (2026-11-05, desktop JVM limited to C1 for its boxing, 360x800dp, in-memory Room, 50 to
    100,000 rows). Reading a page is linear, off the main thread: 1 at 50 rows, 15 at 1,000, 75 at 5,000, 153 at 10,000,
    ~2,000 at 100,000. It holds ~265 bytes a row. Ranking, on the main thread, is 0.1 at 1,000, 1.4 at 10,000, and 12 at
    100,000. Opening and scrolling compose flat (~1 a scrolling frame). `LazyListScrollbar`'s draw used to walk every
    item: 87KB allocated a draw at 1,000, 950KB at 10,000, and 9.6MB at 100,000, with a whole frame's draw going from 5.5
    to ~10 at 100,000. C2 hid most of that allocation. Given no `contentTypeOf` or `sizeOf`, it now walks only the items
    it has measured (`measuredOrAverageSize`): a frame's draw is ~5.4 and allocates 44KB at every size.
    Pulling the thumb (warm, compose and layout): ~1.2 a frame at about 3 new rows, ~2.4 at 12, and 4-6 with every row new,
    against ~0.7 for a finger scroll.
- **Recording an always-on frame** (2026-11-05, sandbox emulator, debug build, a scratch Activity's `FrameMetrics`
    `DRAW_DURATION` over 300 idle frames, three runs each; p50 then p95). The only numbers here from real RenderNodes, as
    Robolectric repaints everything each capture. Compare within this list only. CPU recording alone, not the GPU.
    - A board and tray with no ambient art, the tile glow alone: 0.01 and 0.01, before and after. The glow only sets a
        layer's alpha.
    - Starry background and mat: 2.2 and ~4.2, now ~0.87 and ~1.4.
    - Floating Dice background: 1.43 and ~2.7, now ~0.52 and ~0.8. No stars in it, so this is the layer's saving alone.
    - The menu: 0.95 and ~2.5, now ~0.80 and ~1.4. Its content was already in layers; a backdrop with nothing on it is
        ~0.75, so what's left is the drifting dice drawing themselves.
- **Pages.** Styles opens at ~42-44 the first time after launch, then 16-24. A tile scrolling in costs ~0.6. Settings'
    first frame is ~26-30. A Rules tap frame is ~6-8.

A debug build on a phone is several times slower than any of this. Judge on a release build (R8 plus the Baseline
Profile).

- **Box styles** (2026-11-10, the board alone at 411dp, idle, 80 frames after 10, p95 draw incl. a ~17ms capture floor):
    Classic 19.6, Paper 19.2, Wood 22.2, Neon 25.0, RGB Rainbow 23.2 and Wave 24.3 on Standard's card; 20.2-22.6 for all
    on D20's. Within the run's noise. RGB adds 0.4-0.6ms of composition a frame: a `rememberArtSeconds` clock per open
    box. A clock shared by the board was tried and saved nothing: the cost is re-recording each box's `BoundedArt` node
    (four ring strokes) a tick, in line with the RGB dice (seven at rest ~1.5 over Classic). Neon's pulse is `PulsingArt`:
    the glow recorded once and only faded.
    - A board's opening frame (compose and layout, then the board's capture; Standard and D20, ten styles): the first
        board of the run costs ~450-500 compose whatever its style, Classic included, and later first openings fall in
        run order, not by style (checked by reversing the order). Warm, every style opens within noise of Classic
        (compose 10-21, draw 23-33) and a roll's frame too. Box styles paint no cached surfaces, so `GameWarmUp` composing
        the board in the player's box style is all a game needs; nothing to preload on a pick, and no longer cover.

## Still on the table

- **The tumble.** ~5-6ms with no face layers drawn; drawing them is the other 6-10. Untried: snapshot each face to an
    `ImageBitmap` (`GraphicsLayer.toImageBitmap`). The cost is that animated styles freeze through the 0.9s toss.
- **The ground shadow.** `GroundShadow` (`DiceTray.kt`) rebuilds its `Outline` and draws it four times each frame a die
    moves. Untried, and unmeasured on its own.
- **Any animating Compose layer redraws the whole window on the GPU** (2026-11-05). `redrawsAlone` saves recording, not
    this. HWUI bounds a node's damage only with `clipToBounds`; without it the damage is effectively infinite
    (`RenderNode::damageSelf`). Compose 1.12's `GraphicsLayerV29` sets `clipToBounds` only with `clip` on and no outline,
    and `clip = true` on the ring's `graphicsLayer` didn't narrow it. Show view updates on the emulator: one 48dp pulsing
    box on an empty Compose screen flashed a far corner of the window, as did a tile, the board, and the menu; a still
    screen flashed nothing and drew no frames; the same box as a plain `View` in a plain Activity left that corner alone.
    The trace's `Drawing` rectangle says the same: `atrace -t 2 gfx view`, with a screen's rectangle per frame.
    - On a phone at 120Hz (HWUI bars, read by eye), the rings alone were ~1.7x the green line, nearly all red and orange.
    - A score tile's glow is therefore `PulsingArt`, a `RenderNode` of its own with `clipToBounds`, faded through
        `onDescendantInvalidated` (Android 10 up). Emulator rectangles, real board: 100% of the window to 14% with four
        rings on the left, and 30% with the 5x tile too, as damage is one rectangle round them all. Its phone numbers are
        still to be taken. A hosted `View` didn't help: an `AndroidView` still drew the whole window.
    - `BoundedArt` does the same for art that changes shape. Emulator rectangles: any animated background (Starry, Floating
        Dice, Ocean Waves) 42% of the window, the Starry mat 21%, RGB, neon, and glitter dice ~11%, a neon, fishbowl, or
        cauldron cup ~5%. Moving together, they cover one rectangle round them all: Starry background and mat with rings,
        64%.
    - Still the whole window: the menu's dice (they roam it), a cup's shake and a die's toss (Compose layers turning),
        a score's count-up, and Material's drop-downs. Each but the dice is under a second.
    - **The HWUI bars can't show any of this.** Profile HWUI rendering makes every frame redraw the whole window
        (`FrameInfoVisualizer::unionDirty`), so it reads the same either way. Time it with `FrameMetrics` and no overlay.
- **One glow clock for all the lit tiles.** Each lit tile runs its own `InfiniteTransition`: ~0.1ms of a frame's animation
    phase per tile (2026-11-05, same emulator set-up; four lit, ~0.87 against ~0.46 with none). Sharing one has to stop
    when no tile is lit, so a still board keeps asking for no frames. Not built: reference-counted machinery for a debug
    build's 0.1ms. Measure a release build first.
- **Dice switching mode** rebuilds each die's nodes twice a roll. Drawing the pick-up with the resting composable and
    switching to `Moving` once off the mat would hide the first rebuild in the shake.
- **Always-on animations' battery cost.** All of them stop after 60s without a touch (`ScreenIdle`). The menu drift skips a
    120Hz screen's every other refresh and sleeps between its frames. Starry,
    steam, cauldron, and the tile glow run at the display rate at High. Measure on a real 120Hz phone (Macrobenchmark
    `PowerMetric`, or Android Studio's Power Profiler) before capping. Not by frame-rate votes (see "Tried and
    dropped").
- **Baseline Profile benchmarks on a phone.**
    - Measure the generated profile's before and after
        (`./gradlew :app:baselineprofile:connectedBenchmarkReleaseAndroidTest`).
    - Decide whether the hand-written wildcard `app/android/src/main/baseline-prof.txt` (all app code hot, ~1.2MB
        compiled) can go.
    - Emulator numbers mean nothing here.

## Tried and dropped

- **Bounding the menu's drifting dice** (2026-11-06, considered, not built). Damage is one rectangle per frame, and four dice
    spread over the screen need nearly all of it, whether or not each has a node of its own. Moving one die a frame doesn't
    help: `computeDirtyRect` joins the last few frames' damage for buffer reuse, and each die would move less often than the
    60fps floor. Keeping the dice together, or resting between moves, would change how the backdrop looks. Caching the menu's
    content in a layer would cost ~10MB for an unmeasured saving. Left at the whole window by the maintainer's choice.
- **A 60Hz vote for ambient art at High** (`Modifier.preferredFrameRate(FrameRateCategory.Normal)`, 2026-11-03,
    investigated, not built). From Android 15, Compose takes the highest vote among the layers that redrew in a frame;
    a layer with no vote, and Compose's own scrolling and flings, add nothing. So a vote on always-running art (stars,
    steam, the cauldron, Neon) would pull whatever moves with it, unvoted, down to 60: the roll on those tables, and
    flings on the Styles page once the finger lifts. Keeping them at the screen's rate means a High vote on every
    other animation, kept up for each new one. Dropped by the maintainer's choice: High stays fully uncapped. Only
    the score tile glow, lit while the dice are still, would have been low-risk, and it's the smallest saving.
- **Moving `stringsLanguageConfiguration()`'s `runBlocking` off `attachBaseContext`** (2026-10-20, measured only).
    Sandbox emulator, debug build: 8-71ms on a cold start (most runs 10-35ms). About 2-4ms is coroutines' first load and
    nearly all the rest Compose resources' first read; a second call takes 0.2ms. The first screen would pay that first
    read anyway, so moving it wouldn't shorten start-up.
- **Replacing `material-icons-extended` (frozen at 1.7.3, 118 icons used) with Material Symbols** (2026-10-20). As
    vector drawables in `composeResources/drawable/`, R8'd dex fell 45 KB but the 118 XML files added 36 KB compressed
    plus ~27 KB of zip headers: the release APK grew 13.9 KB (download +10.9 KB). Path strings in Kotlin were estimated
    ~30 KB smaller (0.5% of the APK), not worth restyling every icon. The library is plain path code with almost no API
    to break. Symbols also redraws some glyphs (`whatshot` became a trend arrow; its flame, `mode_heat`, matches
    `local_fire_department`, which `PlayConsoleExportTest` rejects).
    - **Done after all** (2026-10-27), for the maintained, Material 3 look rather than for size: Rounded, filled, as path
        strings in Kotlin (`ui/icons/Symbols.kt`, `scripts/material-symbols.sh`), 117 glyphs. Play release APK 6,538,307 →
        6,537,839 bytes (-468), download 3,432,231 → 3,421,524 (-10.7 KB): R8'd code fell ~55 KB and the path strings
        added about as much. `minifyPlayReleaseWithR8` alone, warm, 8 runs each after an untimed one: median 24.5s before,
        24.3s after - no change worth the name, though R8 no longer reads the library's 11,123 classes.

- **Reading the toss in layout and drawing, not composition** (2026-10). Built, verified and discarded.
    - **The idea.** A tossed die's pose was read in composition, so each moving die (`Moving`) and its `TossedCube`
        recomposed every frame. Reading the pose only where the die and its shadow are placed and drawn, with the cube
        rebuilt only as a face comes into or leaves view, would avoid that.
    - **What it changed.** Per toss (five dice), `Moving` went from ~275 recompositions to 5 and `TossedCube` from ~265 to
        ~30. `ScatterArea` itself already recomposed only once per die per toss.
    - **What it saved.** Toss frames' compose and layout went from p50 ~0.90ms to ~0.85ms, p95 ~1.83ms to ~1.73ms (whole
        game screen, two runs each). That's within run-to-run noise, and drawing, the bulk of a toss frame, was unchanged.
        Each recomposition removed cost microseconds.
    - **Why dropped.** No measurable gain, at the price of trickier code. A slower phone slows every part of the frame by
        a similar factor, so the share saved stays ~5% of compose and layout. A toss's cost is in drawing its face layers
        (see "The tumble" above).
    - **The trap it found.** Pose readers passed as references to local functions (`::x`) compare equal whatever state they
        capture, so Compose skipped the shadow and it kept the pick-up's pose: moving dice lost their shadows. The test suite
        passed; only a frame-by-frame pixel diff of the tray (every dice style, Tricolour, D20, Stud, Unlucky; ~32,000 frames)
        caught it. Pass lambdas, not references, to a skippable composable.
