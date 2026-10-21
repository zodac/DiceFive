---
name: ios-build
description: Get the iOS app's CI results - the IPA to sideload (from an ios-v* GitHub release), the simulator screenshots, the iOS test report - and send them to the user. Use after iOS-touching work is pushed, or when asked for an IPA or iOS screenshots. There is no Mac; everything iOS runs on macOS GitHub runners.
---

# iOS builds from CI

The sandbox can't link or run iOS code. The iOS release workflow does it on macOS runners (`IOS_SUPPORT.md`), so every iOS
result comes from a workflow run on a pushed commit. Never push to start one; ask the user to push, or to let you
push.

- **`ios-release.yml`** ("iOS release"): a push to `main` changing what the iOS app ships (`app/ios`, `app/shared`,
  the build) runs the iOS tests and builds the Release IPA. Then it publishes GitHub release `ios-v<version>` and bumps
  `app/ios/VERSION`. Below 1.0.0 it's a pre-release. On demand: `gh workflow run ios-release.yml`.
- **The simulator screen tour** (its `screenshots` job) runs for full releases (1.0.0+). For a pre-release, dispatch
  it: `gh workflow run ios-release.yml -f force_screenshots=true` (releases the version on `main`, as any run does).

1. **Find the run**: `gh run list --workflow ios-release.yml -L 5`.
2. **Wait for it**: `gh run watch <id> --exit-status --interval 60` (in the background).
3. **Download** into the scratchpad, never the repo.
   - The IPA: `gh release download ios-v<version> -D <scratchpad>/ios-v<version>` gives `DiceFive-<version>.ipa`
     and its `-dSYM.zip`. `gh release list -L 5` shows the latest tags.
   - The screenshots: `gh run download <id> -D <scratchpad>/ios-<id>` gives `ios-screenshots/NN-<screen>.png`.
     `missing-*.png` is what was on screen when the tour couldn't find something.
   - On a test failure, the same download has `ios-test-report/`, the HTML report of `iosSimulatorArm64Test`.
4. **On failure**: `gh run view <id> --log-failed | tail -200`. Fix, commit, and ask for a push. A failed release
   bumps nothing, so the next push releases the same version.
5. **Timings**: `gh api repos/zodac/DiceFive/actions/runs/<id>/jobs` has each step's start and end.
6. **Screenshots**: look at each PNG (Read) before sending. Check safe areas (Dynamic Island, home indicator),
   clipped text and anything unlike the Android renders.
7. **Send** the IPA (and any screenshots worth seeing) with `SendUserFile`.
8. **In the report**, say what to check on the iPhone, including what VoiceOver should say for changed UI, and what
   wasn't seen on a device.

Installing (the user does this): Sideloadly on Windows, their Apple ID, iPhone in Developer Mode. A free Apple ID's
signature lasts 7 days; installing over the top keeps the app's data.
