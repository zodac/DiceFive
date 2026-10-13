---
name: render
description: Render any screen, art or animation to PNG with Robolectric (no device) for review or inspection - scratch test template, phone sizes, font scales, animation frame sheets, zoomed close-ups, and the debug-colour gap counter. Use for all visual work before building an APK.
---

# Rendering without a device

Robolectric draws real pixels: every unit test runs with native graphics on SDK 36
(`app/android/src/test/resources/robolectric.properties`). The workflow is a throwaway test in
`app/android/src/test/kotlin/net/zodac/dicefive/`, which is **deleted before committing**. Run it with
`./gradlew -q :app:android:testDebugUnitTest --tests '*Scratch*' --rerun` and write the PNGs to the scratchpad.

```kotlin
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")   // ALWAYS a phone width: the 320dp default squeezes rows silently
class ArtScratch {
    @get:Rule val compose = createComposeRule()   // the junit4.v2 one; the old one is deprecated (-Werror)
    @Test fun render() {
        compose.setContent { DiceFiveTheme { /* the composable, in a Box with a background */ } }
        compose.waitForIdle()
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File("<scratchpad>/art.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
```

Then Read the PNG to look at it, and send it to the user (SendUserFile) when it's for review.

## Variants

- **Sizes and fonts.** Use qualifiers such as `w360dp-h800dp-xxhdpi`, `w891dp-h411dp`, `w600dp-h960dp` and
  `w800dp-h1280dp-xhdpi`. For a large font, wrap the content in
  `CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f))`. The usual
  checks are 100%, 130% and 200%.
- **Whole game screen.** Wrap `GameScreen(viewModel)` in `LocalPlatformServices provides SilentPlatformServices`. Roll
  with a scripted `Random`, hold, then step both clocks (`compose.mainClock` and `ShadowLooper.idleMainLooper`) before
  you capture.
- **Animations.** Set `compose.mainClock.autoAdvance = false`, then change state with
  `compose.runOnIdle { x = y; Snapshot.sendApplyNotifications() }`. Step with `mainClock.advanceTimeBy(ms)` and
  capture each step. Stitch the captures into **one labelled sheet** with `android.graphics.Canvas`, using `drawText`
  for the labels.
- **Close-ups.** Draw the art several times its size in a clipped box and shift it:
  `Box(Modifier.size(300.dp).clipToBounds()) { Box(Modifier.wrapContentSize(unbounded = true, align = Alignment.TopStart).offset(x, y)) { art(Modifier.requiredSize(big)) } }`.
  The maintainer reviews close-ups, so check every fix at the zoom they flagged.
- **Background-thread art** (`rememberHoardArt`) isn't waited for by `waitForIdle`. Sleep the test thread, then
  advance the clock, before capturing.
- **Overlay windows** (banners, dialogs) aren't in a root `captureToImage`. Use the store tests' `captureScreen()`
  (`app/android/src/test/.../store/`).

## Finding gaps: paint the background a debug colour

When something shows through, temporarily paint whatever sits behind it pure green (`Color.Green`), and any second
suspect layer magenta. Render, then count the pixels:

```sh
python3 -I .claude/skills/render/count_green.py <scratchpad>/art.png
```

Iterate until the count is about 0; a few antialiased edge pixels are fine. Then restore the colours and
`grep` for `Color.Green`/`Magenta` before committing.

## Review etiquette

Send renders of every state (an animation as a sheet) and iterate before building any APK. The maintainer's comments
are literal, so re-read them before guessing. Look at a render that seems wrong before chasing a bug: a dimmed 0 can be
a preview. Delete the scratch test afterwards.
