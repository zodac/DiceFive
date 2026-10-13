# DiceFive: making table art (dice, cups, mats, backgrounds, frames)

The working method and the drawing techniques. Elsewhere:
- what a style *is* (catalogs, ids, locks): `DESIGN.md` "Table art" and `UI_SCREENS.md` "Style locks";
- rendering for review: the `render` skill;
- frame cost: `BENCHMARKS.md` and the `benchmark` skill.

## Working with the maintainer on art

- **Render for review before any APK.** Send every state, with animations as labelled sheets, and expect several
  rounds. Comments are specific and literal ("the coins at the back lay flat on the air"), so re-read them before
  guessing.
- **They look at close-ups.** Render a zoom of anything they circle, and check fixes at that zoom.
- **They care about frame cost.** Hundreds of live draws per frame get refused even when the numbers are fine. Say
  plainly what's painted once and what's live, with counts.
- **Unlocks.**
  - If none is given, pick an unused `AchievementCount` (`StyleCatalogTest` keeps it earnable) and say which.
  - One achievement unlocks a style through `SpecificAchievement`, shown padlocked.
  - A secret colour uses `StyleColour.secretAchievement`, not offered until earned.
  - The star and tooltip follow from `styleRewards` automatically.
- **Style over realism, where asked.** Realistic gems were "too real"; the Gems dice are flat facet tones, ink outlines
  and a crisp highlight. Offer the stylised version when it's ambiguous.

## Drawing techniques that worked

- **The cup grid.** Every cup draws in a `CupDrawScope` on its `CupShape` grid (`gx`/`gy`), side-on from ~22°
  (`CUP_VIEW_SQUASH`).
  - Round cups use `CupCanvas`, which owns the pour.
  - A cup that moves differently (the chest opens) draws its own `Canvas` and shakes in a `graphicsLayer`, reading the
    shake state inside the layer lambda.
- **Non-round shapes in 3D.** `TreasureChestCup.kt` keeps a tiny projector.
  - Points `(x, y, z)` are turned by a yaw and squashed like the cups.
  - `facing(normal)` culls hidden surfaces, and painter's order sorts by distance.
  - Extruded profiles are strips between profile points.
  - Give solids thickness (an inner profile plus the edge between), or they read as paper.
- **Things must visibly rest on something.** Floating was the most repeated complaint. Use hinges, contact shadows,
  shadows shaped like the footprint (not a generic oval), and edges that meet at corners.
- **Simulate rather than place.** Height-formula placement hovers. A seeded drop simulation (`settlePile`) doesn't:
  each piece lands on the highest point under its footprint and rolls downhill past a repose slope.
  - The tilt and roll checks must sample *outside* the piece's own footprint, or pieces tower.
  - The roll check must use where the piece will rest.
  - Print the peak height and counts while tuning.
- **Natural variety.** Seed per face and per die with `naturalPatternSeed(value)` (it uses `LocalDieIndex`). Small
  variations in rotation, jittered outlines, shade and which facets catch the light read as organic.
- **Tilted discs are ovals.** Get the ellipse axes once from two projected in-plane directions (the eigenvalues of
  `M Mᵀ`), and draw rotated ovals. That's several times cheaper than polygons.
- **Inside `rotate { }`/`clipPath { }`, `gx`/`gy` aren't reachable** (a DSL scope violation). Compute the values first,
  or declare local `fun x(v: Float) = gx(v)` helpers.
- **Text dice.** Copy the font's glyph metrics from fontTools (`BoundsPen`), lay the text out once at a fixed size, then
  scale it (`MathsDice.kt`). For bundling a font, see `app/licensing/README.md` and `PUBLISHING.md` (OFL; Reserved Font
  Names mean ship unmodified, or rename).
- **Recoloured.** Every `DiceStyle.recoloured(palette)` draws in the roll's colours. Multi-coloured pips all take
  `palette.pip`.

## Heavy art: paint once, keep it off the frame

- **Static, costly surfaces** go through `drawCachedSurface(key) { ... }` (`Patterns.kt`). The key captures everything
  but size.
- **Very heavy one-time painting** must not happen on the frame where it first shows.
  - The pattern is `rememberHoardArt`: `produceState` + `withContext(Dispatchers.Default)`, painting into an
    `ImageBitmap` via `CanvasDrawScope().draw(...)`, cached in a small main-thread map by (style, size), with a cheap
    stand-in until it's ready.
  - Start it only when needed: the first shake, or already open. Never for a Styles preview that's merely shown.
  - **Wrap it in `runCatching`** and keep the stand-in on failure. An uncaught failure in `produceState` crashes the
    screen.
- **Live per frame**: only what moves (a lid, a burst, a dozen top pieces). Say the count.
- **Measure** against an existing style in the same run, and measure first appearance separately.

## The cup contract, as a cup sees it

- **`Cup(rolling, tilted)`**: `rolling` is the shake (forced false under reduced motion), and `tilted` means this
  turn's dice have been poured.
  - On the 2nd or 3rd roll, `tilted` is **still true** when the next shake starts, so `rolling` must override it.
- **Composed with `tilted` already true** means a continued game. The chest stays shut until the next shake, because its
  hoard can't be ready on that frame.
- **Open-state things key off the open *state*, not the lid angle** (the shake lifts the lid too), and go when the state
  closes. Anything heaped above the rim waits until the lid is clear of it.
- **Instance swaps.** A cup can be handed another instance of its own class in the same place (a Styles tile changing
  colour), keeping `remember`ed state. Keep per-colour state out of `remember` initialisers.
- **Reduced motion** (`LocalReduceMotion`, `UI.md`): stop decorative motion and keep the outcome (the lid still opens,
  spilled pieces appear at rest). Don't rely on Compose's animation scale alone.

## Shipping a style

1. **Add it.** The class, or colours of an existing one, plus its `StyleFamily` in `StyleCatalog.kt`.
   - The id is the family's string key plus the colour's (`style_family_glitter` + `style_colour_gold` =
     `glitter_gold`), and it's permanent.
   - `StyleCatalogTest` covers the invariants. Add tests for anything special.
2. **Render every state** for review (the `render` skill), and iterate.
3. **Measure** frame cost if it draws every frame or paints anything heavy, and record it in `BENCHMARKS.md`.
4. **Accessibility.** The tile reads "Family, Colour", and the tray and cup are spoken regardless of style. Say so, and
   that it wasn't heard on a device.
5. **Note what later changes must know**: secret unlocks and easter eggs in `DESIGN.md` "Table art", reduced-motion
   behaviour in `UI.md`.
6. **Finish.** Delete scratch tests and debug colours, run the gate (`test` skill), and send the APK (`debug-apk`).
