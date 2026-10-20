---
name: store-assets
description: Generate, review and upload the Play Store listing graphics (hi-res icon, feature graphic, phone and 10-inch tablet screenshots, promo video) with scripts/generate-store-assets.sh. Details and traps in .claude/STORE_ASSETS.md.
---

# Store listing assets

Everything is rendered from the real app by Robolectric tests, then captioned, encoded and checked by the script. The
output goes into the gitignored `assets/<VERSION>/`, and none of it is committed. Read `.claude/STORE_ASSETS.md`
before changing scenes, captions or the generators.

```sh
scripts/generate-store-assets.sh                     # everything, ~5-10 min (mostly the video)
scripts/generate-store-assets.sh --parts phone,icon  # some of: icon, feature, phone, tablet, video
scripts/generate-store-assets.sh --no-render         # re-caption/re-encode the last renders, no Gradle
scripts/generate-store-assets.sh --music track.mp3   # other music (--music-loudness -24); --no-music for none
```

The script checks every file against Play's rules and exits non-zero on a failure. Intermediate files go to
`build/store-assets/`.

## Each release

1. Run the script. Look at `contact-sheet.png` and watch the video with sound.
2. **Send both for review** before anything is uploaded, and expect a round or two.
3. Check that `assets/full-description.txt`, the short description in `.claude/PUBLISHING.md` and
   `scripts/store-assets/captions.txt` are still true (the counts of modes, achievements and styles). The banned-word
   rule applies to all of them.
4. Upload in Play Console. The video goes to YouTube first (public or unlisted, ads off, no age restriction, with
   `video-credits.txt` pasted into its description); Play takes the URL.
