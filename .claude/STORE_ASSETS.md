# DiceFive: Play Store listing assets

How the listing's graphics are made, what to change, and the traps. **Running the script and the release steps** are
the `store-assets` skill. The rest of the Play submission is in `PUBLISHING.md`.

## Output (`assets/<VERSION>/`, gitignored)

| File | Play's requirement |
|---|---|
| `icon-512.png` | 512x512, 32-bit PNG, ≤1MB |
| `feature-graphic-1024x500.png` | 1024x500, JPEG or 24-bit PNG (no alpha) |
| `phone/phone-1-game.png` ... `phone-5-achievements.png` | 1440x2560 |
| `tablet-7/tablet-7-1-game.png` ... `tablet-7-4-achievements.png` | 1440x2560 |
| `promo-video-1080p60.mp4` | ~25s, 1920x1080, 60fps, H.264; the app's sounds over music (AAC) |
| `video-credits.txt` | not uploaded: credits for the YouTube description |
| `contact-sheet.png` | not uploaded: every still, for review |

- **File names** start with the device, so the two folders can't be mixed up in an upload.
- **`assets/full-description.txt`** is the hand-written listing text. It's the only tracked file under `assets/`
  (`.gitignore` makes an exception for it), and the script refuses to run if anything else there is tracked.

**Why this many.** Play accepts 2-8 screenshots per device. A listing is considered for featuring only with **4+ at
1080px or more**, and most people see only the first two or three.
- **Phone, 5**: game, Tricolour at a dressed table, the Styles dice, results, achievements.
- **7-inch, 4**: game, styles, results, achievements.
- **10-inch: none**, by the maintainer's choice. To add it, copy the tablet test with `w800dp-h1280dp-xhdpi` into a
  `tablet-10` folder and add it to the script.
- **One ~25s video**, per Play's advice.
- **Captions** are part of the screenshot: a one-line headline on brand navy above the screen, at exactly 9:16. That
  also makes a 20:9 phone screen uploadable.

**The feature graphic** keeps the logo left of centre, because Play overlays a play button in the middle when there's a
video.

## Changing what's shown

- **Captions**: `scripts/store-assets/captions.txt`, keyed by screen name (shared by the stills and the video). Rerun
  with `--no-render`. The banned-words rule applies, and each caption must stay true of the app.
- **Screens and order**: `PHONE_SHOTS`/`TABLET_SHOTS` and `screenshots()` in
  `app/android/src/test/kotlin/net/zodac/dicefive/store/StoreScreensGeneratorTest.kt`. A new screen needs a caption.
- **Staged games**: `Scenes` in the same file.
  - Each is a real game, played by the CPU up to the moment shown. A seed search finds one that fits, and only the
    rolls shown are dealt (`DealtRandom`).
  - The dressed table is `Scenes.SHOWPIECE`. Every style it names must be unlocked by the seed, or the app silently
    draws the default.
- **The video**: `promo video frames` (a `FrameRecorder` per scene, with `hold(ms)` and `tap(node)`). The script lays
  frames on captions, joins them with fades, and ends on `StoreArtGeneratorTest`'s `video end card`.
- **The icon** is the launcher icon itself, cropped roughly as a launcher shows it. **The feature graphic** is
  `BannerScene` (`StoreArtGeneratorTest.kt`): the `AppLogo` and a spread of unlockable dice (`TOSSES`).
- **The history** is `StoreState.seed` (`StoreAssets.kt`): about 36 games over two months for Sam, Jo, Alex and the CPU
  Ada, with every counted achievement except a deliberately locked few.

## How it works, and the traps

The generators are unit tests in `app/android/src/test/.../store/`, skipped without `-PgenerateStoreAssets`.
- Each is tagged `@StorePart(...)`. `-PstoreAssetParts` picks parts, and `-PstoreAssetsDir` sets where raw renders go.
- They check nothing, so CI never runs them.

The traps:

- **`MainActivity.useCappedFrameClock = false`** must be set before launch. Otherwise the test clock never moves the
  app: the menu doesn't update, `recreate()` takes minutes, and no game appears. Every symptom looks like something
  else.
- **Overlay windows.** Robolectric puts every window top-left, and a root `captureToImage` omits overlays.
  `captureScreen()` draws each window's view tree and places it by `LayoutParams.gravity`, via reflection on
  `WindowManagerGlobal.mViews`.
- **Windows attach only as the clock moves.** `waitUntil` never sees a banner; `stepUntil` advances Compose *and* idles
  the looper a frame at a time.
- **Unwanted banners.** Staged play unlocks whatever it can, mid-shot.
  - The seed unlocks everything counted except `STILL_LOCKED`.
  - The video also leaves `FIRST_UNLOCK` locked, so its 5x unlocks it for real.
  - The count after that must not land on a style step (75, 80, 85, 95, 100), or a styles banner joins in. Recheck when
    achievements or thresholds change.
- **Saving a staged game.** Writes are queued, so `saveGame` waits for *that* game to be on disk before Continue.
- **Rigged dice.** `AppContainer.diceRandom` is the one production hook; only the video replaces it, then restores
  `Random.Default`. A roll takes one `nextInt` per unheld die, plus one for its colour.
- **Timing.** The test clock moves in 16ms frames, so the video is encoded at 60fps and plays 4% slow (unnoticeable).
  Taps draw a fading ring.
- **Times on screen** use the JVM's timezone (the host's). Run with `TZ=<zone>` for another.
- **Locked styles** have no tile and draw as the default. Use ones the seed unlocks.
- **Sound.** `RecordingSoundPool` reports every clip as loaded (Robolectric's never does) and logs each play to
  `sounds.txt` with its frame and volume. The script lays the build's normalised clips
  (`app/android/build/generated/normalizedAudioRes/raw/`) at those frames.
- **Credits.** The hold and unhold clicks are CC-BY 4.0. The script writes `video-credits.txt` from `app/licensing/`,
  skipping CC0.
- **Music.** "Happy Vibes" by Ruskerdax (OpenGameArt, CC0), chosen by the maintainer.
  - It isn't in the repo: the script fetches it into the ignored `assets/music/` and checks a pinned SHA-256
    (`DEFAULT_MUSIC_*`). It doesn't belong on the in-app Licences screen.
  - `--music <file>`: its licence must allow promo use, and its credit is yours to write.
  - Tracks are looped, faded and normalised to -24 LUFS.
  - **Avoid Pixabay music**: some of it is in YouTube's Content ID.
- **Levels.** The mix is lifted 4dB to about -16 LUFS, with a limiter at -2dB and its auto-level off.
