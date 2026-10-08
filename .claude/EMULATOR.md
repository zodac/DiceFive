# Android emulator in the sandbox

`sandbox/emulator.sh` boots a headless Android emulator **inside the sandbox**, on demand, so an agent
can run instrumented tests, drive the app and look at its screen without a phone. Run everything here
from inside the sandbox (it is a container; the host's phones and emulators are not reachable from it).

```
sandbox/emulator.sh start               # boot (~20 s once installed), wait until ready
sandbox/emulator.sh status              # exit 0 and a line if running, exit 1 if not
sandbox/emulator.sh screenshot out.png  # what is on its screen right now (then Read the PNG)
sandbox/emulator.sh stop
```

`start` is idempotent and does everything needed (below), in about 20 seconds: the emulator, its system
image and a virtual device are baked into the sandbox image. It leaves the emulator running detached, so
it outlives the shell that started it; **stop it when finished**, since it holds ~4 GB of RAM and 4 cores.

## When to use it

- **Running or debugging the Baseline Profile journey** (`app/baselineprofile`) - the main use. It turns
  a "tell the maintainer, wait for their phone" loop into minutes. See `DESIGN.md` Phase 19. The release
  workflow runs the same journey on an emulator of its own (below), so a journey that fails here will fail
  the release too.
- **Looking at the real app**: install a build, launch it, `screenshot`, Read the PNG. Useful to check a
  layout or what a UiAutomator selector will see. (Rendering is software-drawn: fine for layout and
  behaviour, not for judging smoothness or art quality - for art, follow `STYLE_ART.md`.)
- **Not for timings.** The benchmarks (`connectedBenchmarkReleaseAndroidTest`) measure the machine
  running the emulator, not a phone, so their numbers mean nothing. Those stay on real hardware.
- **Not needed for** unit tests, which run on the JVM under Robolectric.

## Running the Baseline Profile journey on it

```
sandbox/emulator.sh start
adb uninstall net.zodac.dicefive 2>/dev/null
./gradlew :app:android:generateBaselineProfile -Pandroid.testInstrumentationRunnerArguments.journeyLaps=1
sandbox/emulator.sh stop
```

- `journeyLaps=1` keeps a run to one lap (~1.5 minutes); drop it for the default (up to 15 laps,
  about 20 minutes). A one-lap pass doesn't prove the full run: timing races (a dialog still closing)
  only showed up on laps 4 and 5.
- The `adb uninstall` matters: a debug build installed earlier has a higher version code than the
  benchmark build, so the benchmark install is refused ("version downgrade") and the run is a no-op.
- The generated profile lands in `app/android/src/release/generated/baselineProfiles/`, which is
  gitignored: the release workflow generates its own for every APK (below), so one made here is only for
  benchmarking or checking the journey locally.
- A failing step names itself (`Baseline Profile journey: 'X' never appeared`); the report is
  `app/baselineprofile/build/reports/androidTests/connected/nonMinifiedRelease/index.html`. Reproduce it
  here, `screenshot` to see the screen it was stuck on, fix `Journeys.kt`, rerun.
- The four benchmark tests show as skipped during `generateBaselineProfile`. That is expected: the
  plugin sets `androidx.benchmark.enabledRules=BaselineProfile`, and `MacrobenchmarkRule` then assumes
  itself out. Not a failure.

## The same journey in the release workflow

`release.yml`'s `baseline-profile` job runs this journey (10 laps) on a GitHub-hosted runner for every
release APK, and the `apk` job builds with the profile it makes. `.github/scripts/ci_emulator.sh` is
`emulator.sh`'s CI twin: same Android version (it reads `ENV EMULATOR_API` from `sandbox/Dockerfile` at run
time), same Google APIs image, same `-gpu swiftshader_indirect` boot and the same Vulkan renderer setting
after it. It differs only where the runner does: `/dev/kvm` is opened with a udev rule, packages go in
without `sudo` (the runner's SDK is its own), and the emulator and image are cached by the workflow
(keyed on the API level, and saved even when the journey fails). It runs alongside the release's checks, and `.github/scripts/watch_checks.sh` stops it (Gradle and
the emulator) as soon as one of them fails. A failing journey fails the job, and the release, straight away - only an
emulator that fails to boot is retried, once. The generate step stops after 150 minutes and the job after 170
(a lap is ~2 minutes on a 4-core sandbox emulator and at least three times that on a hosted runner; each
lap's start is echoed in the step's log), so a hang can't hold the release for long; the `baseline-profile-report` artifact holds the test report, a
screenshot of where the journey stopped and the emulator's log. To debug one, reproduce it here as above - the two emulators are the same.

## What `start` does, and why

| Step | Why |
|---|---|
| `sudo chmod 666 /dev/kvm` if it isn't read-write | The sandbox is `--privileged` so the host's KVM is present, but the node is `root:<host's kvm gid>` and `dev` isn't in that group. Without KVM the emulator falls back to software CPU emulation - far too slow to drive an app. Only this container's copy of the node changes. |
| Installs the `emulator` package, the system image or the virtual device **only if missing** | They are baked into `sandbox/Dockerfile`, so normally nothing happens here. It is the fallback for a container from an image built before they were added, or for another `EMULATOR_API`: the SDK is root-owned, so `android sdk install` runs under `sudo` and the permissions are then fixed (it unpacks without the other-user execute bit); about 1.5 GB to download (5 GB unpacked), for that container only. |
| Boots with `-no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect` | Headless; `-no-snapshot` for a clean boot every time. |
| `adb root`, then `setprop debug.hwui.renderer skiavk` | **The important one** - see below. |

Environment overrides: `EMULATOR_API`, `EMULATOR_AVD_NAME` (default `sandbox-api<API>`), `EMULATOR_MEMORY_MB`,
`EMULATOR_CORES`.
It is a **Google APIs** image (not Play Store): that is what allows `adb root`, which profile generation
and the renderer setting both need.

## The renderer setting (do not remove it)

The emulator's software OpenGL ES path (gfxstream + SwiftShader) **segfaults in its render thread** while
drawing DiceFive. The emulator process exits with status 139, with no crash report, a few seconds after
the app opens - and everything above it reports a vague symptom: `adb: device offline`, a vanished
device, a Gradle `connectedNonMinifiedReleaseAndroidTest` that fails ~15 s in having run no steps. Other
apps (Settings) draw without trouble; the debug build installs fine; only *launching DiceFive* kills it.

Its Vulkan path does not crash, and the app draws correctly through it. So after every boot the script
sets `debug.hwui.renderer=skiavk`. That property resets on each boot (set it again if you boot the
emulator some other way), and a boot-time `-prop debug.hwui.renderer=skiavk` flag does **not** take
effect - it has to be set over adb once the device is up. Tried and still crashing: `-gpu guest`, 
`swiftshader_indirect` with `-feature -Vulkan`, `debug.hwui.renderer=skiagl`, sensors and audio
disabled in the AVD.

## If the emulator dies anyway

1. **Get its exit status**, not just "the device went away". Start it from a wrapper that records it:
   `emulator -avd sandbox-api37.0 ...; echo "EXIT $?" >> log`. 139 is a segfault; 137 is a kill (memory).
   (`start` writes the emulator's own output to `~/.android/emulator-sandbox-api37.0.log`.)
2. **Get a backtrace.** There is no crash dump (crashpad writes nothing here and core files aren't kept).
   `sudo apt-get update && sudo apt-get install -y gdb`, then run the emulator under it, following the
   *parent* (the emulator forks a crash-handler child; following the child loses the real process):
   `gdb -batch -ex "set follow-fork-mode parent" -ex "handle all nostop noprint pass" -ex "handle SIGSEGV stop print" -ex run -ex "bt 25" -ex "thread apply all bt 6" --args /opt/android-sdk/emulator/emulator -avd sandbox-api37.0 -no-window -no-audio -no-snapshot -gpu swiftshader_indirect`,
   boot, launch the app over adb, and read the stack. A crash in `libgfxstream_backend.so` / the GLES
   translator is the known one above.
3. **Don't trust a process-listing check for "is it alive"** - a `case`/`grep` for `qemu-system` can match
   your own command line. Use `adb shell true` / `sandbox/emulator.sh status`.
4. `adb kill-server` and a re-`start` fixes a wedged adb connection; `-wipe-data` on the emulator fixes
   a virtual device left in a bad state by an abrupt exit ("Skipping PackageSetting ... missing metadata"
   in logcat).

## Limits and things worth knowing

- **Slower and software-drawn.** The journey's fixed waits (e.g. `Thread.sleep` after a roll) were tuned
  on phones; they hold up here, but anything timing-sensitive may behave differently.
- **One Android version at a time**, the one `EMULATOR_API` names (below): currently API 37.0 (Android 17).
- **Image size.** The emulator and system image add about 5 GB (1.5 GB downloaded, then unpacked) to the
  sandbox image. They sit in their own layers below the SDK block, so a platform-tools or build-tools bump
  does not re-download them.

## Versions: the emulator follows compileSdk

- `ENV EMULATOR_API=37.0` in `sandbox/Dockerfile` is the one place the emulator's Android version is
  chosen. It is written as the image package names it (`37.0`: from API 37 even the base release carries
  a minor; older ones are plain, `34`). The Dockerfile derives the system image
  (`system-images;android-<API>;google_apis;x86_64`) and the virtual device (`sandbox-api<API>`) from it,
  `entrypoint.sh` forwards it to `dev` (sudo strips the environment), and `sandbox/emulator.sh` reads it,
  with a fallback literal (`${EMULATOR_API:-37.0}`) for a shell that doesn't have it.
- It **tracks compileSdk's API level** (`app/android/build.gradle.kts`): the newest stable Google APIs
  image of that level. Google publishes no image for most minor releases (there is none for compileSdk's
  37.2), so it matches the level, not the minor. Running on the newest Android the project compiles
  against is the point: it is where targetSdk's runtime behaviour changes show up.
- **`.github/scripts/update_dependency_versions.sh` keeps it there**: `sync_emulator_api` runs right after
  the compileSdk step and moves `EMULATOR_API` (Dockerfile and the `emulator.sh` fallback) to the newest
  image of compileSdk's level, whether or not compileSdk moved. When the platform is published before its
  image, the emulator stays where it is. The guard at the end then fails the run if the Dockerfile and the
  fallback disagree, if the image is no longer a stable image in Google's manifest, or if an image for
  compileSdk's level exists but the emulator isn't on it; if none exists yet it only warns.
- The emulator and image are named by package only; `android sdk install` cannot pin a revision (same as
  `platform-tools`), so the revision baked in is whatever Google served when the layer was last built. The
  layer is rebuilt only when its text changes, i.e. when `EMULATOR_API` does.
- **To test on a different Android version** (an older one, say, to reproduce a bug): `EMULATOR_API=<N>
  sandbox/emulator.sh start` installs that image and creates its virtual device for this container only
  (about 1.5 GB). The Vulkan renderer workaround above is applied on any version, but a different image can
  behave differently; the symptom of it not working is the emulator dying (exit 139) a few seconds after the
  app opens. The journey has been run on API 34 and 37.0.

- **Maintainer's phones remain the reference** for anything the emulator can't show: real frame times,
  haptics, the accelerometer-driven googly eyes, how it feels.
