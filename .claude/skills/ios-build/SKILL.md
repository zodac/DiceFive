---
name: ios-build
description: Get the iOS app's CI results - the IPA to sideload, the simulator screenshots, the iOS test report - from .github/workflows/ios.yml, and send them to the user. Use after iOS-touching work is pushed, or when asked for an IPA or iOS screenshots. There is no Mac; everything iOS runs on macOS GitHub runners.
---

# iOS builds from CI

The sandbox can't link or run iOS code. `.github/workflows/ios.yml` does it on macOS runners (`IOS_SUPPORT.md`), so
every iOS result comes from a workflow run on a pushed commit. Never push to start one; ask the user to push, or to
let you push.

1. **Find or start the run.**
   - A push to `main` touching `app/shared`, `app/ios` or the build starts one: `gh run list --workflow ios.yml -L 5`.
   - On demand, on what's already pushed: `gh workflow run ios.yml -f configuration=Debug` (or `Release`), then
     `gh run list --workflow ios.yml -L 1` for its id.
2. **Wait for it**: `gh run watch <id> --exit-status` (in the background; a run takes 20-40 minutes).
3. **Download** into the scratchpad, never the repo: `gh run download <id> -D <scratchpad>/ios-<id>`.
   - `DiceFive-<version>-<configuration>/`: the `.ipa` and its `-dSYM.zip`.
   - `ios-screenshots/`: `NN-<screen>.png` from `ScreenTourTests`. `missing-*.png` is what was on screen when the
     tour couldn't find something.
   - `ios-test-report/` (only on failure): the HTML report of `iosSimulatorArm64Test`.
4. **On failure**: `gh run view <id> --log-failed | tail -200`. Fix, commit, and ask for a push.
5. **Screenshots**: look at each PNG (Read) before sending; check safe areas (Dynamic Island, home indicator), clipped
   text and anything unlike the Android renders.
6. **Send** the IPA (and any screenshots worth seeing) with `SendUserFile`.
7. **In the report**, say what to check on the iPhone, including what VoiceOver should say for changed UI, and what
   wasn't seen on a device.

Installing (the user does this): Sideloadly on Windows, their Apple ID, iPhone in Developer Mode. A free Apple ID's
signature lasts 7 days; installing over the top keeps the app's data.
