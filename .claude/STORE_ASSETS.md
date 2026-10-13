# DiceFive: Play Store listing assets

How the store listing's graphics are made: the hi-res icon, the feature graphic, phone and 7-inch tablet
screenshots, and the promo video. All of it is rendered from the real app - its own screens, art and icon - by
Robolectric tests, then captioned, flattened, encoded and checked by a script. The output is gitignored; nothing it
makes goes in the repo. The rest of the Play submission (descriptions, console set-up) is in `PUBLISHING.md`.

## Making a fresh set for a release

```
scripts/generate-store-assets.sh                     # everything, about 5-10 minutes (most of it the video)
scripts/generate-store-assets.sh --parts phone,icon  # just some: icon, feature, phone, tablet, video
scripts/generate-store-assets.sh --no-render         # re-caption / re-encode the last renders, without Gradle
scripts/generate-store-assets.sh --music track.mp3   # different music under the video (--music-loudness -24)
scripts/generate-store-assets.sh --no-music          # the video with the app's sounds only
```

It writes `assets/<VERSION>/` (the version from `VERSION`), so each release's set sits beside the last:

| File | What Play wants |
|---|---|
| `icon-512.png` | Hi-res icon: 512x512, 32-bit PNG, at most 1MB |
| `feature-graphic-1024x500.png` | Feature graphic: 1024x500, JPEG or 24-bit PNG (no alpha) |
| `phone/phone-1-game.png` ... `phone-5-achievements.png` | Phone screenshots, 1440x2560 |
| `tablet-7/tablet-7-1-game.png` ... `tablet-7-4-achievements.png` | 7-inch tablet screenshots, 1440x2560 |
| `promo-video-1080p60.mp4` | Promo video: about 25s, 1920x1080, 60fps, H.264; the app's own sounds over music (AAC) |
| `video-credits.txt` | Not for upload - the credits the video's sounds (and music) need, for its YouTube description |
| `contact-sheet.png` | Not for upload - every still on one page, to review the set at a glance |

Each screenshot's name starts with its device, so files from the two folders can't be mixed up in an upload.
Beside the version folders, `assets/full-description.txt` is the listing's full description - written by hand, not
generated, and the one file under `assets/` that's tracked in git (`.gitignore` ignores the folder's contents, with an
exception for it, and the script refuses to run if anything else there is tracked). See `PUBLISHING.md`.

The script ends by checking each file against Play's rules (sizes, the 2:1 limit, no alpha, file sizes, 2-8
screenshots per device) and exits non-zero if anything fails. Intermediate files go to `build/store-assets/`.

**Before uploading, look at them** - `contact-sheet.png` and the video. Per the maintainer's working agreement for
visual work (`STYLE_ART.md`), send the contact sheet and the video for review before anything is uploaded or committed,
and expect a round or two.

**Uploading the video**: Play takes a YouTube URL, not a file. Upload the `.mp4` to YouTube (public or unlisted, with
ads off and no age restriction), paste `video-credits.txt` into its description, then paste its URL into the listing. Play shows the feature graphic with a play
button over its middle when there's a video - which is why the feature graphic keeps the logo left of centre.

## Why this many

Play accepts 2-8 screenshots per device type and needs at least 2 to publish; a listing is only considered for
featuring with at least **4 screenshots of 1080px or more** (9:16 or 16:9). Most people see only the first two or
three, so the set is a few strong ones, not all eight:

- **Phone: 5** - the game, Tricolour at a dressed-up table, the Styles screen's dice, the results page, achievements.
- **7-inch tablet: 4** - the featuring minimum: the game, styles, results, achievements.
- **10-inch tablet: none** - the maintainer chose 7-inch only. To add it, copy the tablet test with
  `w800dp-h1280dp-xhdpi` and a `tablet-10` folder, and add it to the script's screenshot step.
- **One video, about 25 seconds.** Play's own advice is to keep it short.

Captions are part of the screenshot, as most listings do: the real screen sits under a one-line headline on the brand
navy, at an exact 9:16. That's also what makes a modern phone's screen uploadable at all - a 20:9 capture is more
than twice as tall as it is wide, which Play rejects.

## Changing what's shown

- **Captions** are in `scripts/store-assets/captions.txt`, keyed by the screen's name (the screenshot and the video
  scene share them). Edit and rerun with `--no-render`. The `CLAUDE.md` trademark rule applies: never the banned
  word, and "3x/4x/5x", never "three/four/five of a kind". Keep each true of the app as it ships.
- **Which screens, in what order**: `PHONE_SHOTS` / `TABLET_SHOTS` and `screenshots()` in
  `app/android/src/test/kotlin/net/zodac/dicefive/store/StoreScreensGeneratorTest.kt`. A new screen needs a caption.
- **The staged games**: `Scenes` in the same file. Each is a real game, played by the CPU (`AiTurnPlayer`) up to the
  moment shown - a seed search finds one that fits ("Sam to play, 4x and 5x still open") - and only the rolls the
  scene shows are dealt (`DealtRandom`). The look of the dressed-up table is `Scenes.SHOWPIECE`; every style it names
  must be unlocked by the seeded achievements, or the app quietly draws the default instead.
- **The video's scenes**: `promo video frames` in the same file - a `FrameRecorder` per scene, with `hold(ms)` and
  `tap(node)`. The script lays each scene's frames on its caption and joins them with short fades, then holds the
  closing card (`StoreArtGeneratorTest`'s `video end card`, the feature graphic's scene at 16:9).
- **The icon** is the launcher icon itself (`ic_launcher_foreground.xml` on `@color/ic_launcher_background`), cropped
  to roughly what a launcher shows - so changing the app's icon changes this one. **The feature graphic** is
  `BannerScene` in `StoreArtGeneratorTest.kt`: the menu's `AppLogo` and a spread of unlockable dice (`TOSSES`).
- **The history behind it all** - names, games, achievements - is `StoreState.seed` in `StoreAssets.kt`: about 36
  real games over two months for Sam, Jo, Alex and the CPU, Ada, and every achievement the Achievements screen counts
  except a deliberately locked handful.

## How it works, and the traps

The generators are ordinary unit tests in `app/android/src/test/.../store/`, skipped unless Gradle is given
`-PgenerateStoreAssets` (the script does that). Each test is tagged `@StorePart(...)`; `-PstoreAssetParts` picks
parts and `-PstoreAssetsDir` says where raw renders go. They check nothing, so CI never runs them.

- **`MainActivity.useCappedFrameClock = false`**, before the activity launches. Without it the app runs on its own
  frame clock and the test's clock never moves it: the menu doesn't update, a `recreate()` takes minutes, a game
  never appears. Every symptom looks like something else.
- **Banners and other overlay windows.** Robolectric puts every window at the top-left (ignoring gravity), and a
  root's `captureToImage` doesn't return an overlay's own pixels. `captureScreen()` instead draws each window's view
  tree itself and places it by its `LayoutParams.gravity` - so the achievement banner sits at the bottom, as on a
  phone. It reads the windows from `WindowManagerGlobal.mViews` (reflection; fine on Robolectric's JVM).
- **A new window only attaches as the clock moves.** Robolectric's looper runs on its own clock, so waiting for a
  banner with `waitUntil` never sees it. `stepUntil` advances the Compose clock *and* idles the main looper a frame
  at a time.
- **Unwanted banners.** Anything the staged play can unlock, it will - in the middle of a shot. The seed unlocks
  every achievement the screen counts (hidden ones too) except `STILL_LOCKED`, which nothing here can earn. The video
  leaves one more locked (`FIRST_UNLOCK`, the 5x) so its 5x unlocks it for real. The count after that must not land
  on a style-unlock step (75, 80, 85, 95, 100), or a "Styles unlocked" banner joins it: recheck when achievements or
  unlock thresholds change.
- **Saving a staged game**: the repository queues writes, and leaving a game keeps it saved, so `saveGame` waits for
  *that* game to be on disk before Continue is pressed - otherwise the previous scene's game comes back.
- **Rigged dice**: `AppContainer.diceRandom` is the one production hook: the game's ViewModel rolls with it, and only
  the video replaces it (then puts `Random.Default` back). A roll takes one `nextInt` per unheld die, plus one for its
  colour in a mode with coloured dice.
- **Timing**: the test clock moves in 16ms frames, so the video is encoded at 60fps and plays 4% slower than the
  app - not noticeable, and smooth whatever the machine, since every frame is exactly one frame on. Taps draw a
  fading ring, like Android's "Show taps".
- **Times on screen** (the Achievements screen's "Unlocked ..." dates) are in the JVM's timezone - the sandbox's,
  which is the host's (`sandbox/README.md`, Notes). Run with `TZ=<zone>` to render another.
- **Locked styles** have no tile to tap and draw as the default when picked - choose ones the seed unlocks.
- **Sound** is the app's own. `RecordingSoundPool` (the video test's shadow for `SoundPool`) reports every clip loaded
  at once - Robolectric's own never does, and the app waits for that before playing anything - and logs each play to
  `sounds.txt` beside the scene's frames, with its frame and volume. The script lays the build's loudness-normalised
  clips (`app/android/build/generated/normalizedAudioRes/raw/`, what the app actually plays) at those frames on the
  joined timeline, so the sound follows the picture through the cross-fades. A screen that plays nothing (Styles)
  is silent, as in the app.
- **Credits**: the hold/unhold clicks are CC-BY 4.0, so the video needs a credit: the script writes it into
  `video-credits.txt` from `app/licensing/` (the records the Licences screen uses), skipping CC0 clips.
- **Music**: "Happy Vibes" by Ruskerdax ([OpenGameArt](https://opengameart.org/node/160228)), chosen by the
  maintainer from three CC0 candidates. CC0 is a public-domain dedication: no credit is required (the credits file
  lists it as a courtesy), and it doesn't belong on the in-app Licences screen, which covers only what ships in the
  app - the build's asset check scans the app's source sets, never the root `assets/`. It isn't in the repo: the
  script fetches it once into the ignored `assets/music/` and checks it against a pinned SHA-256
  (`DEFAULT_MUSIC_*` in the script). `--music <file>` uses another track - its licence must allow use in a promo
  video, and its credit is the one thing the script can't write - and `--no-music` leaves it out. Any track is looped
  if short, faded in and out, and normalised to -24 LUFS under the effects (`--music-loudness`), so a quiet track and
  a loud one end up alike. **Avoid Pixabay's music**: some of its tracks are registered with YouTube's Content ID, so
  the video can be claimed despite a valid licence.
- **Levels**: the finished mix is lifted 4dB to land near -16 LUFS (YouTube turns loud videos down but never quiet
  ones up), with a limiter at -2dB - its auto-level off, since on it scales the output back up to full scale.

## Each release

1. Run the script (with `--music` if there's a track); look at `contact-sheet.png` and watch the video with sound;
   send both for review.
2. Check `assets/full-description.txt`, the short description in `PUBLISHING.md` and the captions are still true
   (counts of modes, achievements, styles).
3. Upload in Play Console (and the video to YouTube). Nothing to commit - the assets stay local.
