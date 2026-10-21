---
name: debug-apk
description: Build the debug APK and send it to the user. Use once a requested code change is verified (compiles, relevant tests pass) - not after every intermediate edit.
---

# Build and send the debug APK

1. `./gradlew -q assembleGithubDebug -PdevStamp` from `/work`. The build needs `ffmpeg` on PATH, because it normalises
   the sound clips.
   - That's the **github** app (`net.zodac.dicefive.github`), the only debug APK ever sent: everything a debug build
     can show. The play app is tested only as a Play-signed build from an internal testing
     track (`PLAY_GAMES.md`, "Testing"); its debug variant exists for its unit tests and lint, never to hand out.
2. Find the file: `ls -t app/android/build/outputs/apk/github/debug/*.apk | head -1`.
3. Send it with the `SendUserFile` tool (load it via ToolSearch `select:SendUserFile` if it's deferred).
4. In the report, say what to check by hand on the device, including what TalkBack should say for changed UI. Name
   anything not seen on a device.

Notes:
- Visual work: get renders agreed first (`render` skill), then send the APK.
- Don't touch `app/android/VERSION` to make an APK installable. `-PdevStamp` gives the build the minute it was made as its
  `versionCode` (and `<version>-dev<minute>` as its name and file name), and it is signed with the repo's
  `app/android/debug.keystore`, so every stamped APK installs over the last.
- Never send an APK built without `-PdevStamp`. It keeps `app/android/VERSION`'s `versionCode`, so it won't install over a
  stamped one, and its name doesn't say which build it is. Plain builds stay unstamped so test and lint runs remain
  up to date.
