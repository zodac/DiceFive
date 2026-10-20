---
name: test
description: Run this project's tests, lint and build gates (all or one class), read the failures, and avoid the known test traps. Use when verifying any change.
---

# Running the tests and gates

Run from `/work`. Compiler and lint warnings fail the build (`allWarningsAsErrors`, lint `warningsAsErrors`), but
Gradle's own warnings (deprecations, the manifest merger, plugins) don't, and `-q` hides them. Chain with `&&` so a
failure stops what follows.

## The full gate (before a commit)

```sh
./gradlew testDebugUnitTest lint assembleDebug compileDebugAndroidTestKotlin :app:android:assembleRelease --console=plain 2>&1 \
  | grep -v -E '^> Task|^$'
```

- The root `testDebugUnitTest` runs both test suites and compiles `app/shared` for iOS (both targets plus the iOS
  test code). It is the only task that catches Kotlin/Native-only failures, such as a `commonTest` name with a comma.
- `:app:android:assembleRelease` is the release job's build: R8, and the release lint (`lintVitalRelease`), which
  `lint` doesn't run. Without the signing secrets it builds unsigned, which is fine here.
- No `-q`, so Gradle's warnings show. Read what's left after the task lines. Known and left (third-party, already
  on their newest versions): AGP's `Configuration.setVisible` deprecation, and the AboutLibraries plugin's
  "resolved during configuration time" lines. Anything else is new: fix it.

## While iterating

| What | Command |
|---|---|
| One Robolectric class (`app/android/src/test`) | `./gradlew -q :app:android:testDebugUnitTest --tests '*BoardSemanticsTest'` |
| One shared test (`commonTest`, `androidHostTest`) on the JVM | `./gradlew -q :app:shared:testAndroidHostTest --tests '*GameEngineTest'` |
| One test method | `--tests '*BoardSemanticsTest.on a phone*'` |
| iOS compile only | `./gradlew -q :app:shared:compileKotlinIosSimulatorArm64 :app:shared:compileTestKotlinIosSimulatorArm64` |
| Lint per module | `./gradlew -q :app:shared:lint :app:android:lintDebug` |
| Regenerate Hard AI's perfect-play table (after any change to how Standard scores; about 20s) | `./gradlew :app:shared:testAndroidHostTest --tests '*StandardPerfectPlayTableTest*' -PregeneratePerfectPlayTable` |

Failures and `println` output are in `app/android/build/test-results/testDebugUnitTest/TEST-*.xml` and
`app/shared/build/test-results/testAndroidHostTest/TEST-*.xml`. Grep for `<failure`; output is under `system-out`.
Add `--rerun` to re-run a test Gradle thinks is up to date. Otherwise it serves the old result, and a scratch render
keeps the old PNG, so check its timestamp.

## Where tests live

- Pure logic and view models go in `app/shared/src/commonTest`: JVM now, compiled for iOS.
- JVM-only tests that read resource files from disk go in `app/shared/src/androidHostTest`.
- Anything that needs Android, a resolved string or a rendered screen goes in `app/android/src/test`, under Robolectric
  (SDK 36, native graphics, set in `robolectric.properties`).
- `Res` is internal to `:app:shared`. An `:app:android` test therefore reaches strings through a public composable,
  or reads English from disk (`englishText()`).

## Traps

- **No commas, parentheses, `.`, `:`, `/`, `<`, `>`, `[` or `]` in backticked `commonTest` names.** Kotlin/Native
  rejects them. Use " - ".
- **A screen test** (`GameScreen`) needs `LocalPlatformServices provides SilentPlatformServices`. Step both
  `compose.mainClock` and the paused main looper (`ShadowLooper.idleMainLooper`), because the view model's coroutines
  delay on the looper. `GameScreenCupGateTest.waitFor` is the pattern.
- **View-model tests** run on a `StandardTestDispatcher`, which must also be passed as `aiDispatcher`. Use
  `advanceTimeBy` + `runCurrent` for timers. In a timed game, `advanceUntilIdle` plays every turn out through timeouts.
- **Only do what the UI allows.** The engine throws on impossible actions.
- **Pick dice that tell the rules apart.** Use `LoadedDice`, `ScriptedDice`, `CountingDice` or `FixedValueRandom`,
  and work out the expected values by hand before asserting.
- **Prove a guard can fail.** Remove the fix, watch the test fail, then put it back. Do this one guard at a time.
- **Cached formatters leak between tests in one JVM.** Run a locale test on its own when checking that it can fail.
- **Semantics-cleared text can't be found.** Use `useUnmergedTree = true`, or say in the report that it is untested.
- Canvas drawing only runs in native graphics mode, which is already the default here.
