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
./gradlew testDebugUnitTest :app:shared:lint lintDebug lintRelease assembleDebug compileDebugAndroidTestKotlin :app:android:assembleRelease \
  --console=plain 2>&1 | grep -v -E '^> Task|^$'
```

- The root `testDebugUnitTest` runs both modules' test suites: `:app:shared`'s as Android runs them (`commonTest`
  and `androidHostTest`; compiled for the JVM too), and `:app:android`'s. `:app:shared:jvmTest` runs `commonTest` on
  the JVM: monthly in CI, and by hand after changing a `commonTest` UI test or the harness.
- `app/android` has two flavours, `play` and `github` (`.claude/DESIGN.md`, "Distribution"). Its `testDebugUnitTest`,
  `lintDebug` and `compileDebugAndroidTestKotlin` run both (`testPlayDebugUnitTest`, `testGithubDebugUnitTest`, ...),
  so `src/test` runs twice, once in each app, with `src/testPlay` and `src/testGithub` once in their own.
- `lintRelease` is both release builds' full lint (`lintPlayRelease`, `lintGithubRelease`): the release-only source
  sets and manifests, which `lintDebug` never sees.
- `:app:android:assembleRelease` is the release job's build for both apps: R8, and the release lint
  (`lintVitalPlayRelease`, `lintVitalGithubRelease`), which `lintDebug` doesn't run. Without the signing secrets it
  builds unsigned, which is fine here.
- No `-q`, so Gradle's warnings show. Read what's left after the task lines. Known and left (third-party, already
  on their newest versions): AGP's `Configuration.setVisible` deprecation, and the AboutLibraries plugin's
  "resolved during configuration time" lines. Anything else is new: fix it.

## While iterating

| What | Command |
|---|---|
| One shared test as Android runs it (`commonTest`, `androidHostTest`; Robolectric for the UI) | `./gradlew -q :app:shared:testAndroidHostTest --tests '*BoardSemanticsTest'` |
| One shared test on the JVM target (`commonTest`; not in the gate) | `./gradlew -q :app:shared:jvmTest --tests '*GameEngineTest'` |
| One app test (`app/android/src/test`, or `src/testPlay`) | `./gradlew -q :app:android:testPlayDebugUnitTest --tests '*MainActivitySmokeTest'` |
| One in the github app (`src/testGithub`, or `src/test` as the github app runs it) | `./gradlew -q :app:android:testGithubDebugUnitTest --tests '*GithubFlavourTest'` |
| One test method | `--tests '*BoardSemanticsTest.a score box*'` |
| Lint per module | `./gradlew -q :app:shared:lint :app:android:lintPlayDebug :app:android:lintGithubDebug` |
| Regenerate Hard AI's perfect-play table (after any change to how Standard scores; about 20s) | `./gradlew :app:shared:testAndroidHostTest --tests '*StandardPerfectPlayTableTest*' -PregeneratePerfectPlayTable` |

Failures and `println` output are in `app/android/build/test-results/test{Play,Github}DebugUnitTest/TEST-*.xml` and
`app/shared/build/test-results/{testAndroidHostTest,jvmTest}/TEST-*.xml`. Grep for `<failure`; output is under `system-out`.
Add `--rerun` to re-run a test Gradle thinks is up to date. Otherwise it serves the old result - from the build cache too
(`FROM-CACHE`), even for source you put back as it was - and a scratch render keeps the old PNG, so check its timestamp.
Always `--rerun` when proving a guard can fail.

`:app:android`'s classes run in two JVMs (`maxParallelForks` in `app/android/build.gradle.kts`), so a test can't count
on the classes before it in the same JVM; nor can a shared one on what another class left behind. The configuration cache is on (`gradle.properties`).

## Where tests live

The tiers, and why a test isn't in the one above it, are in `.claude/MULTIPLATFORM.md`, "Tests". In short:

- **`app/shared/src/commonTest`**, for both targets, is the default: logic, view models, and UI. A UI test extends
  `UiTest`, runs each test in `uiTest { }` (`uiTest(TestScreen(360, 800))` for a screen size) and drives `compose` as
  an Android test's rule. Robolectric draws it on Android (SDK 36, native graphics, `robolectric.properties`), Compose's
  desktop runtime on the JVM. `Res` resolves in both.
- **`app/shared/src/androidHostTest`**: a shared test that holds only on Android - another language (`@Config`), an
  Android API, or a desktop harness limit; it says which. And the checks that read the source tree from disk (the i18n
  guards, the perfect-play table).
- **`app/android/src/test`**: the app itself - `MainActivity`, `device/`, the manifest, Room on Android's SQLite, the
  store-asset generators. What only one app has goes in `src/testPlay` (Play Games, Sidekick, the Play Console sheets)
  or `src/testGithub` (progress files on Android).

## Traps

- **A screen test** (`GameScreen`) needs `LocalPlatformServices provides SilentPlatformServices`. Under Robolectric,
  step both `compose.mainClock` and the paused main looper (`ShadowLooper.idleMainLooper`), because the view model's
  coroutines delay on the looper. `GameScreenCupGateTest.waitFor` is the pattern.
- **The desktop harness** (a `commonTest` UI test on the JVM) draws every frame in software, and a composition's own
  coroutines (`delayWhileResumed` in an effect or a gesture) wait in real time there, not on the test clock. A test that
  depends on either belongs in `androidHostTest`.
- **View-model tests** run on a `StandardTestDispatcher`, which must also be passed as `aiDispatcher` (`gameViewModel()`
  requires it). Use `advanceTimeBy` + `runCurrent` for timers. In a timed game, `advanceUntilIdle` plays every turn out
  through timeouts.
- **Nothing real by default.** The test `gameViewModel()`s deal seeded dice and read a 2pm clock (`TEST_LOCAL_HOUR` for a
  screen test), so no result depends on when it runs - Rolling The Midnight Dice unlocks from midnight to 6am. Pass
  `localHour` to test the time of day.
- **Only do what the UI allows.** The engine throws on impossible actions.
- **Pick dice that tell the rules apart.** Use `LoadedDice` or `ScriptedDice` (`commonTest/.../TestDice.kt`), and work
  out the expected values by hand before asserting. They fail a draw outside the range asked for, so script every draw
  a roll makes - Unlucky Dice's odds and shuffle too (`AchievementTrackerTest`'s Bad Omen).
- **Process-wide memories.** A screen that remembers its place for the process (`AchievementsScrollMemory`,
  `RulesPageMemory`, the Scores warm-up) has a reset; call it before opening that screen.
- **Fakes.** `InMemoryPreferences`, `InMemoryScoreDao` and `InMemoryAchievementStore` (commonTest) are the shared ones,
  with `UiTestSupport.kt`'s view models and `inMemoryAppContainer()`; reuse them rather than writing another.
  `:app:android` can't see them.
- **Prove a guard can fail.** Remove the fix, watch the test fail, then put it back. Do this one guard at a time.
- **Cached formatters leak between tests in one JVM.** Run a locale test on its own when checking that it can fail.
- **Semantics-cleared text can't be found.** Use `useUnmergedTree = true`, or say in the report that it is untested.
- Canvas drawing only runs in native graphics mode, which is already the default here.
