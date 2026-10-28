# DiceFive: multiplatform layout

The app is Kotlin Multiplatform and Compose Multiplatform. The shared code has two targets: Android, which the app is
built from, and the JVM (desktop), which no app is built from yet.
- The game, UI and persistence live in `app/shared`; only the platform services are per platform.
- The split is for segregation: platform code sits behind the seams below, so another app (a desktop one, say) can be
  added beside Android without moving the shared code.
- The JVM target keeps the split honest: `commonMain` and `commonTest` are compiled for it on every build.

## Layout

```
build-logic/     an included build: the convention plugins every module applies, and the custom tasks
gradle/
├── android-sdk.properties       compileSdk (and its minor), minSdk, targetSdk, build-tools, NDK - their one home
└── gradle-daemon-jvm.properties the Java version (toolchainVersion)
app/
├── shared/      :app:shared - KMP library (com.android.kotlin.multiplatform.library, and jvm())
│   ├── schemas/                 Room's exported schemas (commit every new version)
│   └── src/
│       ├── commonMain/kotlin/net/zodac/dicefive/
│       │   ├── model/ game/     the rules engine; game/style/ is which styles exist and what unlocks them (no art)
│       │   ├── data/            Room (scores), DataStore (settings, achievements, in-progress game), GameStateJson
│       │   ├── app/             AppContainer, BuildInfo, and the services every screen shares: AchievementUnlocker,
│       │   │                    the achievement events (AchievementEvents, announce), ProgressResets
│       │   ├── platform/        PlatformServices and its parts (+ SilentPlatformServices for previews/tests)
│       │   ├── online/          the Play Games seam (PLAY_GAMES.md)
│       │   ├── purchases/       the store seam, for the one purchase (PURCHASES.md)
│       │   └── navigation/ ui/  Compose; ui/DiceFiveApp.kt is the root. ui/ draws and asks; a rule or constraint it
│       │                        needs lives below it (a check the form shows is model/'s or game/'s to own)
│       ├── commonMain/composeResources/   strings, drawables, fonts, files (Res.*)
│       ├── androidMain/         Android's actuals
│       ├── jvmMain/             the JVM's actuals (formatting through ICU4J); in no app yet
│       ├── commonTest/          logic, view-model and UI tests (kotlin.test), for both targets (see "Tests")
│       ├── androidHostTest/     Android's test actuals, the shared tests that hold only on Android, and the checks
│       │                        that read the source tree (the i18n guards, the perfect-play table)
│       └── jvmTest/             the JVM's test actuals
├── android/     :app:android - the APK: MainActivity.kt; device/ (AndroidPlatformServices: SoundPool, Vibrator,
│                SensorManager, Toast, licence JSON; AndroidAppContainer; TextViewLicenceDocument); the play and
│                github flavours (src/play: Play Games and Play Billing; src/github: AndroidProgressFiles). Its tests are of it alone.
├── baselineprofile/   :app:baselineprofile - the Baseline Profile journey (com.android.test)
├── audio/       the sound effects' sources; each app packages them its own way (Android: normalised into res/raw)
└── licensing/   licence records for everything the app ships (see its README)
```

- **Two modules**, because AGP 9 won't let one module be both KMP and an Android application.
- **Naming.** Entry points sit in the root package, and platform implementations in `device/`, prefixed with the
  platform (`AndroidSoundPlayer`). An actual's file is named for its platform (`Formatting.jvm.kt`).
- **Android's implementations live in `app/android`**, because they need `R.raw`, the generated licence JSON and
  `BuildConfig`.
- **`app/audio/` and `app/licensing/` sit outside both modules**, because what they hold isn't any one module's.

## Build logic

`build-logic/` is compiled before the modules' build scripts, so each script holds only what is its own.

- **Convention plugins** (`build-logic/src/main/kotlin/net/zodac/dicefive/buildlogic/`):
  - `dicefive.kotlin.multiplatform` (`:app:shared`): the Android library and JVM targets, host tests, and the JVM
    target's Compose desktop runtime for its tests (the build for the machine they run on).
  - `dicefive.android.application` (`:app:android`) and `dicefive.android.test` (`:app:baselineprofile`).
  - Each sets the SDK levels, the Java version, and the strict rules (`StrictChecks.kt`: compiler warnings and lint
    warnings are errors). A module adds its namespace, dependencies and anything only it has.
- **SDK levels** are read from `gradle/android-sdk.properties` (`BuildSettings.kt`'s `androidSdk`). The dependency
  update script and `.github/scripts/android_sdk_packages.sh` read and edit it, so keep each a plain `name=value` line.
  A module that needs another minSdk (the Baseline Profile's 28) sets it itself.
- **The Java version** is `toolchainVersion` in `gradle/gradle-daemon-jvm.properties` (`javaVersion`).
- **The app's version** is the `VERSION` file (`appVersion`).
- **Custom tasks** (`tasks/`): the audio normalising, and the licence checks (`VerifyLicenseReportTask`,
  `CollectThirdPartyNoticesTask`, `VerifyAssetSourcesTask`). `LicencePolicy` lists the licences any app may ship;
  `app/android` hands it to the AboutLibraries plugin.
- **The plugins it compiles against** (AGP, Kotlin) are `compileOnly` catalog entries on the same versions as the
  `[plugins]` the root build declares, so they move together.

## Targets, and what keeps common code common

- **An Android API in `commonMain` or `commonTest` fails to compile**, because both are compiled for the JVM target.
- **A JDK API doesn't**: both targets run on a JVM, so Kotlin lets common code see the JDK. `CommonCodeIsPlatformNeutralTest`
  (`androidHostTest`) fails on a `java.*` import, `System`, `Thread`, `String.format` and the like there.
  - A target without a JVM would catch it at compile time, but none can be added yet: Room and DataStore have no web
    target, and Apple's targets need a Mac to compile.
- **Platform code** goes in `androidMain`/`jvmMain` or `app/android`, behind an `expect` or `PlatformServices`.
- **The JVM's actuals** format through ICU4J, the library Android's `android.icu` repackages, so both format alike. It's
  never in the APK. `ConfigureOverlayDialogWindow` does nothing there yet: how a desktop dialog passes clicks through is
  for whoever builds the desktop app.

## Tests

`./gradlew :app:shared:testDebugUnitTest` runs `:app:shared`'s tests as Android runs them (`testAndroidHostTest`:
`commonTest` and `androidHostTest`) and compiles them for the JVM. The root `testDebugUnitTest` adds `:app:android`'s.

- **On the JVM they run monthly**, not on every push: `./gradlew :app:shared:jvmTest`, the last step of
  `update-dependencies.yml`. No app ships on the JVM yet, so running them on every push guarded nothing a player has,
  at more than double the shared tests' time; monthly, a test that comes to depend on Android is still caught soon
  after. **When an app for another platform is started, they go back into `android-release.yml`'s `shared-tests`
  (and `testDebugUnitTest`).**

- **`commonTest`** is the default home, for UI tests too. Write it to pass on both targets. A UI test extends `UiTest` and runs each test in `uiTest { }`,
  where `compose` drives it as an Android test's rule would (`Showcase`, `UiTestSupport.kt` for the in-memory view
  models and `AppContainer`).
  - On Android it runs under Robolectric (`PlatformUiTest.android.kt`); on the JVM, on Compose's desktop runtime, with a
    test `Dispatchers.Main` that shares the test clock.
  - `uiTest(TestScreen(360, 800, 3f))` sets the screen on both. With none given, both use Robolectric's default
    (320 by 470dp).
  - A platform call a test needs goes behind a test `expect` too (`setTimeZone`).
- **`androidHostTest`** is for a shared test that holds only on Android. Each says why, or shows it in its code:
  - another language (`@Config(qualifiers = "es")`), or an Android API (`ShadowLooper`, `Bitmap`, `android.icu`);
  - sizes chosen against the room Android's system bars leave (`StylesPageFitTest`);
  - a wait on the test clock inside a composition (`delayWhileResumed` in an effect or a gesture): on the desktop
    harness those coroutines wait in real time instead;
  - seconds of the clock over a busy page (the Styles tests): the desktop harness draws every frame in software, so
    they take minutes there.
- **The checks that read the source tree from disk** are in `androidHostTest` too, as they need nothing from either
  target: the i18n guards (`StringChecks`), the perfect-play table, `NoBackgroundTimersTest`,
  `CommonCodeIsPlatformNeutralTest`.
- **`:app:android`'s tests** are of the app itself: `MainActivity`, `device/`, the manifest, the flavours, Room on
  Android's SQLite, and the store-asset generators. It runs `src/test` once per flavour.

## The seams

- **JSON.** `data/Json.kt` is hand-written: `kotlinx.serialization` cost ~49KB of APK. Android host tests still need
  `org.json` on the classpath (Android's is a stub there) for AboutLibraries.
- **Dates and numbers.** `expect fun formatTimestamp`, with `grouped()` and friends in `ui/common/Formatting.kt`.
  `kotlinx-datetime` was ~180 classes.
- **Time.** `game/Clock.kt`'s `nowEpochMillis()` (`kotlin.time.Clock`).
- **Platform services.** `PlatformServices`: `SoundPlayer`, `HapticsPlayer`, `Accelerometer`, `showTransientMessage`,
  `LicenceDocument`, `loadLicenceReports`, `reduceMotion`, `capFrameRate`, and `progressFiles` (null: only a build with
  no online service has them; `DESIGN.md`, "Distribution").
  - `SoundEffects` and `DiceHaptics` are shared wrappers.
  - `ShakeDetector` is pure logic (`ShakeDetectorTest`).
- **Build info.** `BuildInfo` (version, debug flag). Superuser tests pass `isDebugBuild` explicitly, and a test pins
  that release builds never activate it.
- **Text.** `expect fun stripDiacritics`.
- **Persistence.**
  - Room KMP: `@ConstructedBy(AppDatabaseConstructor)`, KSP per target. Android uses the framework's SQLite; a desktop
    app would bring a driver of its own (bundled SQLite), as the JVM target has none.
  - DataStore: `createPreferencesDataStore(path)` with the `PreferencesFile` names; `AppContainer.create(dataDir,
    databaseBuilder, buildInfo)` builds the container for every platform.
  - Tests: `AndroidAppContainerTest`.
- **UI.**
  - On Android, the CMP artifacts resolve to androidx, so it's the same Compose.
  - `SoraFontFamily` is a `@Composable` getter, as CMP's `Font()` is.
  - `BackHandler` is `ui/common/BackHandler.kt`, on the navigation-event API.
  - Licences: Android supplies `TextView`; previews use `ComposeLicenceDocument`.
  - Tests: `MainActivitySmokeTest` launches the real app.

## Gotchas

- **Lint skips the shared code** unless `com.android.lint` is applied to `app/shared` **and** `app/android`'s lint sets
  `checkDependencies = true`. Both are in place.
- **Lint runs twice on the shared code**: per target. The Android target's run reads the `android { lint }` settings,
  the JVM target's (`lintJvm`) `com.android.lint`'s own; the convention plugin gives both the strict rules. Only the
  JVM run reads Room's generated code as the module's, hence the one exception in `app/shared/lint.xml`.
- **Watch the release APK size** before adding a multiplatform library. Compare the R8 mapping class counts per package
  (`build/outputs/mapping/release/`).
- **Version lines.** JetBrains material3 1.9.x = androidx material3 1.4.x. Keep `composeMultiplatformMaterial3` matched
  to the BOM's material3, and `composeMultiplatform` to Compose UI (see the comment in `libs.versions.toml`).
- **Navigation.** JetBrains navigation-compose 2.9 pulls androidx 2.9, so `androidMain` depends on androidx
  `navigation-compose` directly.
- **Deprecations.** CMP's `BackHandler` and the `compose.*` accessors are deprecated. Use `ui/common/BackHandler.kt` and
  plain catalog coordinates.
- **A `@Composable` member of `PlatformServices` must not share its name** with the top-level composable it delegates
  to, or it calls itself (hence `TextViewLicenceDocument`).
- **The multiplatform test API is experimental.** `app/shared/build.gradle.kts` opts every test source set into
  `ExperimentalTestApi` once, rather than each test.
