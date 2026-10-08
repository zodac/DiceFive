# DiceFive: making table art (dice, cups, mats, backgrounds)

How to design, draw, check and ship a new style, from the Maths, Gems and Treasure Chest work. What a
style *is* (catalogs, families, locks) is in `UI.md` ("Style locks") and `DESIGN.md`; frame cost is in
`BENCHMARKS.md`. This is the working method, plus the traps worth not re-learning.

---

## Working with the maintainer on art

- **Render it for review before building an APK.** For anything visual, send renders first (still
  frames of every state, an animation as a labelled frame sheet) and build the APK once the look is
  agreed. Expect several rounds; each round's comments are specific and literal ("the coins at the
  back lay flat on the air") - re-read them before guessing what was meant.
- **They look at close-ups.** Render a zoomed view of anything they circle, and check a fix at that
  zoom before sending it - a full-size render hides exactly the problem they're pointing at.
- **They care about frame cost.** Hundreds of live draws per frame will be refused even when the
  measurements are fine. Say plainly what's painted once and what's live, with counts.
- **Unlocks:** when none is given, pick an unused `AchievementCount` (`StyleCatalogTest` keeps them
  distinct and within the earnable count) and say which in the report. A style tied to an easter egg
  is `StyleUnlock.SpecificAchievement` - secret, hidden until earned - and the achievement's star and
  tooltip follow automatically from `styleRewards`.
- **Style over realism, where asked:** the realistic gems were "too real"; what shipped is flat facet
  tones, ink outlines and a crisp highlight. Offer the stylised version when realism is ambiguous.

## Rendering without a device

Robolectric renders for real: every unit test runs with native graphics on SDK 36, set once in
`app/android/src/test/resources/robolectric.properties` (a class names neither). A throwaway test in
`app/android/src/test/kotlin/net/zodac/dicefive/` (delete it before committing), run with
`./gradlew :app:android:testDebugUnitTest --tests '*Scratch*'`:

```kotlin
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")   // phone width - see below
class ArtScratch {
    @get:Rule val compose = createComposeRule()             // the junit4.v2 one; the old one is deprecated (-Werror)
    @Test fun render() {
        compose.setContent { /* style.Die(...) / style.Cup(...) in a Box with a background */ }
        compose.waitForIdle()
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File("<scratchpad>/art.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
```

- **Always set a phone width.** The default screen is 320dp; a row of six 50dp dice overflows it
  and the last die is silently squeezed into a sliver - which looked like a bug in the die.
- **Animations:** `compose.mainClock.autoAdvance = false`, change state with
  `compose.runOnIdle { x = y; Snapshot.sendApplyNotifications() }`, step with
  `mainClock.advanceTimeBy(ms)`, capture each step, and stitch the captures into one labelled sheet
  with an `android.graphics.Canvas` (`drawText` for labels). One image per sequence is far easier to
  review than a dozen.
- **Zooming on a detail:** draw the art at several times its size inside a clipped box and shift it:
  `Box(Modifier.size(300.dp).clipToBounds()) { Box(Modifier.wrapContentSize(unbounded = true,
  align = Alignment.TopStart).offset(x, y)) { art(Modifier.requiredSize(big)) } }`.
- **Work done on a background thread** (see "Heavy art") isn't waited for by `waitForIdle`; sleep the
  test thread, then advance the clock, before capturing.
- **Timing:** `println` from the art or the test lands in the JUnit XML's `system-out`
  (`app/android/build/test-results/testDebugUnitTest/TEST-*.xml`). Desktop JVM numbers are relative
  only - compare against an existing style in the same run (`BENCHMARKS.md`, "Reading the numbers").

## Finding gaps: paint the background a debug colour

When something "shows through" - a fill between coins, a floor, a dark inside - temporarily paint
whatever is behind in pure green (and any second suspect layer magenta), render, and count:

```python
# Counts pure-green pixels in a render. zlib only - there's no PIL in the sandbox.
import zlib, struct, sys
d = open(sys.argv[1], 'rb').read(); i = 8; idat = b''
while i < len(d):
    n, t = struct.unpack('>I4s', d[i:i + 8]); c = d[i + 8:i + 8 + n]
    if t == b'IHDR': w, h, _, ct = struct.unpack('>IIBB', c[:10])
    if t == b'IDAT': idat += c
    i += 12 + n
raw = zlib.decompress(idat); bpp = 4 if ct == 6 else 3; stride = w * bpp
prev = bytearray(stride); p = 0; green = 0
for y in range(h):
    f = raw[p]; line = bytearray(raw[p + 1:p + 1 + stride]); p += 1 + stride
    for x in range(stride):
        a = line[x - bpp] if x >= bpp else 0; b = prev[x]; cc = prev[x - bpp] if x >= bpp else 0
        if f == 1: line[x] = (line[x] + a) & 255
        elif f == 2: line[x] = (line[x] + b) & 255
        elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
        elif f == 4:
            pa, pb, pc = abs(b - cc), abs(a - cc), abs(a + b - 2 * cc)
            line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else cc)) & 255
    green += sum(1 for x in range(0, stride, bpp) if line[x + 1] > 180 and line[x] < 90 and line[x + 2] < 90)
    prev = line
print("green pixels:", green, "of", w * h)
```

Iterate until the count is ~0 (a handful of antialiased edge pixels is fine), then restore the
colour - `grep` for `Color.Green`/`Magenta` before committing.

## Drawing techniques that worked

- **Cup grid:** every cup draws in a `CupDrawScope` on its `CupShape` grid (`gx`/`gy`), seen side-on
  from ~22° (`CUP_VIEW_SQUASH`). Round cups use `CupCanvas`, which owns the tip-over pour. A cup that
  moves differently (the chest opens instead) draws its own `Canvas` and its own shake in a
  `graphicsLayer`, reading the shake's state inside the layer lambda so it only moves the layer.
- **Non-round shapes in 3D:** `TreasureChestCup.kt` keeps a tiny projector - points `(x, y, z)` in
  the object's own space, turned by a yaw, projected with the cups' squash - plus `facing(normal)` for
  which surfaces show (cull the rest), and painter's order by distance towards the viewer. Extruded
  profiles (the lid) are drawn as strips between profile points; give solids thickness (an inner
  profile, the edge between them) or they read as paper.
- **Things must visibly rest on something.** Floating was the most repeated complaint: a lid with no
  hinge, coins over a flat fill, a pile above the walls. Hinges, contact shadows, shadows shaped like
  the object's footprint (not a generic oval), and edges that meet at corners all matter.
- **Simulate rather than place.** Coins placed by a height formula always looked like they hovered,
  however the gaps were patched. A seeded drop simulation (`settlePile`) fixed it at once: each piece
  lands on the highest point under its footprint and rolls downhill past a repose slope. Traps: a
  piece's tilt and roll check must sample the surface *outside* its own footprint (inside, the last
  piece there already raised it - nothing ever rolled, and the coins built a tower), and the roll
  check must use where the piece will actually rest, not the ground under its centre. Print peak
  height and counts from the simulation while tuning - it's quicker than reading renders.
- **Natural variety:** seed per face and per die with `naturalPatternSeed(value)` (uses
  `LocalDieIndex`), so no two dice repeat. Small variations - rotation, a jittered outline, shade,
  which facets catch the light - read as "the same stone, but organic".
- **Draw tilted discs as ovals:** a disc's projection is an ellipse; get its axes once from two
  projected in-plane directions (eigenvalues of `M Mᵀ`) and draw rotated ovals. Several times cheaper
  than projecting a polygon per disc.
- **Inside `rotate { }` / `clipPath { }`, `gx`/`gy` aren't reachable** (DSL scope violation) - work
  values out before the block, or declare local `fun x(v: Float) = gx(v)` helpers.
- **Text dice:** to lay out formulas exactly, copy the font's glyph metrics (advance, ink bounds) into
  the code from fontTools (`BoundsPen`), lay text out once at a fixed size and scale it to the die
  (`MathsDice.kt`). Bundling a font: see `app/licensing/README.md` and `PUBLISHING.md` (OFL, Reserved
  Font Names - ship unmodified, or rename).
- **Recoloured:** every `DiceStyle.recoloured(palette)` must draw in the roll's colours (Tricolour) -
  multi-coloured pips (gems) all take `palette.pip`.

## Heavy art: paint once, keep it off the frame

- **Static, costly surfaces** go through `drawCachedSurface(key) { ... }` (`Patterns.kt`): painted once
  per key and size, stamped after. The key must capture everything the painting depends on except
  size. The chest's body, its shadow included, is one.
- **Very heavy one-time painting** (the chest's several-hundred-coin pile) must not happen on the frame
  it first shows: resuming a game with the chest open hitched for ~160ms. Pattern
  (`rememberHoardArt`): `produceState` + `withContext(Dispatchers.Default)` painting into an
  `ImageBitmap` via `CanvasDrawScope().draw(...)`, kept in a small main-thread map by (style, size),
  with a cheap stand-in drawn until it's ready. Start it only when it's needed (first shake, or
  already open) - never for a Styles preview that's merely shown (picking a cup there shakes and tips it, which counts) - and wrap it in `runCatching`, keeping the stand-in on
  failure: Robolectric's legacy graphics mode (most UI tests) can't create the bitmap, and an uncaught
  failure in a `produceState` crashes the whole screen.
- **Live per frame:** only what moves - a lid, a burst, a dozen top pieces. Say the count.
- **Measure** with the frame loop in `BENCHMARKS.md` against an existing style in the same run, and
  re-measure the first appearance separately - that's where heavy art hides.

## The cup contract, as a cup sees it

- `Cup(rolling, tilted)`: `rolling` is the shake (forced false under reduced motion - the game skips
  the drawn shake); `tilted` means this turn's dice have been poured. On a turn's 2nd/3rd roll,
  `tilted` is **still true** from the previous roll when the next shake starts - a cup must treat
  `rolling` as overriding it.
- A cup composed with `tilted` already true is a game being continued. The chest stays shut then until
  the next shake (its painted hoard can't be ready on that frame; appearing open meant empty-then-pop).
- Anything that belongs to "open" (the chest's spill) must key off the open *state*, not the lid
  angle - the shake lifts the lid too - and should go the moment the state closes. Anything heaped
  higher than the rim (the chest's treasure) must also wait until the lid is clear of it, or it pokes
  through a lid that's only just lifting.
- A cup can be handed another instance of its own class in the same place (a Styles tile switching
  colours), keeping any `remember`ed state; the Styles screen keys each tile's cup on its id, so keep
  per-colour state (a fixed plant stage, an always-out rabbit) out of `remember` initialisers anywhere a
  caller might not.
- Reduced motion (`LocalReduceMotion`, `UI.md`): stop decorative motion (shake, bursts, drift), keep
  the outcome (the lid still opens, spilled pieces still appear at rest). Don't rely on Compose's
  animation scale alone - see `UI.md`.

## Shipping a style

1. Add the style class (or colours of an existing one) and its `StyleFamily` in `StyleCatalog.kt`
   (unlock as above). `StyleCatalogTest`'s invariants cover ids, counts and secret-style rules; add a
   test for anything special (a secret unlock, a reward description).
2. Render every state for review; iterate.
3. Measure frame cost if it draws every frame or paints anything heavy; record it in `BENCHMARKS.md`.
4. Accessibility: the tile reads "Family, Colour"; the tray and cup announce values and rolls
   regardless of style - say so in the report, and that it wasn't heard on a device.
5. Note the style in `DESIGN.md`, and any reduced-motion behaviour in `UI.md`.
6. Delete the scratch tests, run `:app:shared:testAndroid :app:android:testDebugUnitTest
   :app:android:lintDebug :app:android:assembleDebug`, send the APK.
