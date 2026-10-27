---
name: emulator
description: Boot the sandbox's Android emulator to look at the real app's screen or run/debug the Baseline Profile journey (app/baselineprofile). Not for timings or unit tests. Troubleshooting and CI details are in .claude/EMULATOR.md.
---

# The sandbox emulator

Run everything inside the sandbox. The host's phones and emulators can't be reached from it.

```sh
sandbox/emulator.sh start               # idempotent; ~20s; waits until ready, applies the Vulkan renderer fix
sandbox/emulator.sh status              # exit 0 + a line if running
sandbox/emulator.sh screenshot out.png  # then Read the PNG
sandbox/emulator.sh stop                # ALWAYS stop when done: it holds ~4GB RAM and 4 cores
```

**Use it for:**
- **The Baseline Profile journey.** The release workflow runs the same journey, so one that fails here fails the
  release.
- **The real app's screen.** Install a build, launch it, take a screenshot. Drawing is in software, so it shows layout
  and behaviour, not smoothness or art quality; for art, use the `render` skill.

**Not for:**
- Benchmark timings (they measure this machine).
- Unit tests (those run under Robolectric).
- TalkBack, haptics, the accelerometer or real frame times. Those need the maintainer's phone.

## Look at the app

```sh
sandbox/emulator.sh start
./gradlew -q assembleGithubDebug -PdevStamp && adb install -r "$(ls -t app/android/build/outputs/apk/github/debug/*.apk | head -1)"
adb shell monkey -p net.zodac.dicefive.github -c android.intent.category.LAUNCHER 1
sandbox/emulator.sh screenshot <scratchpad>/screen.png
```

## Run the Baseline Profile journey

```sh
sandbox/emulator.sh start
adb uninstall net.zodac.dicefive 2>/dev/null   # a debug build's higher versionCode makes the benchmark install a no-op
./gradlew :app:android:generatePlayReleaseBaselineProfile -Pandroid.testInstrumentationRunnerArguments.journeyLaps=1
sandbox/emulator.sh stop
```

- `journeyLaps=1` takes ~5 min. `journeyLaps=10` (CI's soak of a changed journey) takes ~45 min. The default is up to
  15 laps (~70 min). One lap doesn't prove a full run, because timing races can take several laps to show.
- After the uninstall, the first lap starts like CI's fresh install: modes are locked, so it first plays a solo game to
  the end to unlock them (`unlockModes`).
- A failing step names itself (`Baseline Profile journey: 'X' never appeared`). The report is at
  `app/baselineprofile/build/reports/androidTests/connected/nonMinifiedRelease/index.html`. The screen and UI hierarchy
  as it failed are saved as `journey-failure-lap<N>.png`/`.xml` under
  `app/baselineprofile/build/outputs/connected_android_test_additional_output/`. Fix `Journeys.kt`, and rerun.
- The four benchmark tests show as skipped during `generatePlayReleaseBaselineProfile`. That's expected.
- The generated profile (`app/android/src/playRelease/generated/baselineProfiles/`, the play app's; CI copies it to the
  github app's) is gitignored, because CI generates its own.

**Write journey steps for a slow device.** A hosted runner takes 2-4s per UiAutomator lookup.
- Wait for things rather than checking once: `hasText` only after an `await`.
- A tap whose effect later steps rely on goes through `tapUntil` (or `startGame`, `roll`, `setChecked`, `selectTab`),
  which taps again until that effect shows: a starved emulator drops taps.
- Open screens with `openFromMenu`, which retries until the "DiceFive" wordmark is gone.
- Never race anything that times out on its own, such as the 4s banner.
- To shake out races, pin the emulator and two busy loops to two cores (`taskset -c 0-1`) and run two laps.

If the emulator dies (`adb: device offline`, exit 139), see `.claude/EMULATOR.md`, "If the emulator dies". **Never
remove the `debug.hwui.renderer=skiavk` step**: without it the emulator segfaults when DiceFive draws.
