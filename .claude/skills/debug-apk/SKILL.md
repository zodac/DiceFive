---
name: debug-apk
description: Build the debug APK and send it to the user. Use once a requested code change is verified (compiles, relevant tests pass) - not after every intermediate edit.
---

# Build and send the debug APK

1. `./gradlew -q assembleDebug` from `/work`. The build needs `ffmpeg` on PATH, because it normalises the sound clips.
2. Find the file: `ls -t app/android/build/outputs/apk/debug/*.apk | head -1`.
3. Send it with the `SendUserFile` tool (load it via ToolSearch `select:SendUserFile` if it's deferred).
4. In the report, say what to check by hand on the device, including what TalkBack should say for changed UI. Name
   anything not seen on a device.

Notes:
- Visual work: get renders agreed first (`render` skill), then send the APK.
- Don't touch `VERSION` to make an APK installable. Each debug build gets a wall-clock `versionCode` and is signed
  with the repo's `app/android/debug.keystore`, so every debug APK installs over the last.
