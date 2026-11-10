# DiceFive: multiplatform layout

The app is Android-only (decided 2026-11-03), built with Kotlin Multiplatform and Compose Multiplatform. The shared
code has one target, Android. A JVM (desktop) target was dropped then; "Adding a target back" says how to restore it.

- The game, UI and persistence live in `app/shared`; only the platform services are per platform.
- The split is for segregation: platform code sits behind the seams below, so another target can be added beside
    Android without moving the shared code.
- `CommonCodeIsPlatformNeutralTest` keeps the split honest. With one target, the compiler lets `commonMain` and
    `commonTest` see Android's APIs and the JDK's; the test fails on either.

## Layout

```text
build-logic/     an included build: the convention plugins every module applies, and the custom tasks
gradle/
├── android-sdk.properties       compileSdk (and its minor), minSdk, targetSdk, build-tools, NDK - their one home
└── gradle-daemon-jvm.properties the Java version (toolchainVersion)
app/
├── shared/      :app:shared - KMP library (com.android.kotlin.multiplatform.library)
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
│       ├── commonTest/          logic, view-model and UI tests (kotlin.test), platform-neutral (see "Tests")
│       └── androidHostTest/     Android's test actuals, the shared tests that hold only on Android, and the checks
│                                that read the source tree (the i18n guards, the perfect-play table)
├── android/     :app:android - the APK: MainActivity.kt; device/ (AndroidPlatformServices: SoundPool, Vibrator,
│                SensorManager, Toast, licence JSON; AndroidAppContainer; TextViewLicenceDocument); the play and
│                github flavours (src/play: Play Games and Play Billing; src/github: AndroidProgressFiles). Its tests are of it alone.
├── baselineprofile/   :app:baselineprofile - the Baseline Profile journey (com.android.test)
├── audio/       the sound effects' sources; each app packages them its own way (Android: normalised into res/raw)
└── licensing/   licence records for everything the app ships (see its README)
```

- **Two modules**, because AGP 9 won't let one module be both KMP and an Android application.
- **Naming.** Entry points sit in the root package, and platform implementations in `device/`, prefixed with the
    platform (`AndroidSoundPlayer`). An actual's file is named for its platform (`Formatting.android.kt`).
- **Android's implementations live in `app/android`**, because they need `R.raw`, the generated licence JSON and
    `BuildConfig`.
- **`app/audio/` and `app/licensing/` sit outside both modules**, because what they hold isn't any one module's.

## Build logic

`build-logic/` is compiled before the modules' build scripts, so each script holds only what is its own.

- **Convention plugins** (`build-logic/src/main/kotlin/net/zodac/dicefive/buildlogic/`):
    - `dicefive.kotlin.multiplatform` (`:app:shared`): the Android library target and its host tests.
    - `dicefive.android.application` (`:app:android`) and `dicefive.android.test` (`:app:baselineprofile`).
    - Each sets the SDK levels, the Java version, and the strict rules (`StrictChecks.kt`: compiler warnings are errors,
        and lint reads `code-quality-config/android/lint.xml`, where its warnings are errors). A module adds its
        namespace, dependencies and anything only it has.
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

- **The compiler doesn't keep common code common.** With Android the one target, Kotlin compiles `commonMain` and
    `commonTest` as Android code, so Android's APIs and the JDK's both compile there.
- **`CommonCodeIsPlatformNeutralTest` (`androidHostTest`) does**, on every `testDebugUnitTest`. It fails on:
    - an import from outside the multiplatform libraries the module uses (its `MULTIPLATFORM_IMPORTS`: a new
        multiplatform dependency adds its package there);
    - an Android-only part of one of them (`LocalContext`, `AndroidView`, `androidx.compose.ui.res`, `nativeCanvas`,
        the `*Res` annotations, and the like);
    - a platform API named without an import: `android.*`, `java.*`, `R.string`, `BuildConfig`, `System`, `Thread`,
        `String.format`, `::class.java`, `synchronized`.
- **Platform code** goes in `androidMain` or `app/android`, behind an `expect` or `PlatformServices`. The `expect`s stay
    even with one actual each: they're the seams another target fills.

## Tests

`./gradlew :app:shared:testDebugUnitTest` runs `:app:shared`'s tests as Android runs them (`testAndroidHostTest`:
`commonTest` and `androidHostTest`). The root `testDebugUnitTest` adds `:app:android`'s.

- **`commonTest`** is the default home, for UI tests too. It stays platform-neutral, like `commonMain` (the same
    guard). A UI test extends `UiTest` and runs each test in `uiTest { }`, where `compose` drives it as an Android
    test's rule would (`Showcase`, `UiTestSupport.kt` for the in-memory view models and `AppContainer`).
    - It runs under Robolectric (`PlatformUiTest.android.kt`), behind the `runUiTest` test `expect`.
    - `uiTest(TestScreen(360, 800, 3f))` sets the screen. With none given, it's Robolectric's default (320 by 470dp).
    - A platform call a test needs goes behind a test `expect` too (`setTimeZone`).
- **`androidHostTest`** is for a shared test that holds only on Android. Each says why, or shows it in its code:
    - another language (`@Config(qualifiers = "es")`), or an Android API (`ShadowLooper`, `Bitmap`, `android.icu`);
    - what a desktop target's test harness couldn't do, kept here so one can be added back: a wait on the test clock
        inside a composition (`delayWhileResumed` in an effect or a gesture), which waits in real time there; seconds of
        the clock over a busy page (the Styles tests), which it draws frame by frame in software.
- **The checks that read the source tree from disk** are in `androidHostTest` too, as they need nothing from a
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
    `LicenceDocument`, `loadLicenceReports`, `reduceMotion`, `capFrameRate`, `defaultDensity` (the device's own pixels
    to a dp, for `isDisplayZoomed`; null where a platform can't tell), and `progressFiles` (null: only a build with no
    online service has them; `DESIGN.md`, "Distribution").
    - `SoundEffects` and `DiceHaptics` are shared wrappers.
    - `ShakeDetector` is pure logic (`ShakeDetectorTest`).
- **Build info.** `BuildInfo` (version, debug flag). Superuser tests pass `isDebugBuild` explicitly, and a test pins
    that release builds never activate it.
- **Text.** `expect fun stripDiacritics`.
- **Persistence.**
    - Room KMP: `@ConstructedBy(AppDatabaseConstructor)`, KSP per target (`kspAndroid`). Android uses the framework's
        SQLite; a desktop app would bring a driver of its own (bundled SQLite).
    - DataStore: `createPreferencesDataStore(path)` with the `PreferencesFile` names; `AppContainer.create(dataDir,
        databaseBuilder, buildInfo)` builds the container for every platform.
    - Tests: `AndroidAppContainerTest`.
- **UI.**
    - On Android, the CMP artifacts resolve to androidx, so it's the same Compose.
    - `SoraFontFamily` is a `@Composable` getter, as CMP's `Font()` is.
    - `BackHandler` is `ui/common/BackHandler.kt`, on the navigation-event API.
    - Licences: Android supplies `TextView`; previews use `ComposeLicenceDocument`.
    - `PulsingArt` and `BoundedArt` (`expect`, no provider needed): a `RenderNode` on Android 10 up, a Compose layer
        otherwise.
    - Tests: `MainActivitySmokeTest` launches the real app.

## Gotchas

- **Lint skips the shared code** unless `com.android.lint` is applied to `app/shared` **and** `app/android`'s lint sets
    `checkDependencies = true`. Both are in place.
- **Lint runs twice on the shared code**, though there's one target: `com.android.lint` adds `lintJvm` of the common
    code beside the Android target's lint. The first reads `com.android.lint`'s own settings, the second the
    `android { lint }` ones; the convention plugin gives both the strict rules.
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

## Adding a target back

The JVM target was removed in the commit that made the app Android-only
(`git log --grep "drop the shared code's JVM target"`); its files are in that commit's parent. To add it, or another target, back:

- **Build.** `jvm()` (with `jvmTarget`) in `KotlinMultiplatformConventionPlugin`, `kspJvm` for Room's compiler in
    `app/shared/build.gradle.kts`, and the desktop UI test runtime (`jetbrains-compose-desktop-jvm`, in the build for
    the host's OS and architecture) for `jvmTest`.
- **Actuals.** One per `expect`: the seven in `jvmMain` (formatting through ICU4J, the library `android.icu`
    repackages, so both format alike) and the two test ones in `jvmTest` (`PlatformUiTest`, with a test
    `Dispatchers.Main` on the test clock, and `setTimeZone`).
- **Lint.** With a JVM target, `lintJvm` reads Room's generated code for it as the module's: restore
    `app/shared/lint.xml`'s `RestrictedApi` exception for `build/generated/ksp/**`. `FontScale.kt` and `CupDrawScope`
    already suppress the desktop target's `RestrictedApi`.
- **Tests.** `testDebugUnitTest` compiles them for the new target (`compileTestKotlinJvm`); once an app ships there,
    `android-release.yml`'s `shared-tests` and the update gates run them (`:app:shared:jvmTest`). The tests in
    `androidHostTest` for a desktop harness limit stay there.
- **Path filters.** `android-release.yml` and `readme-assets.yml` exclude the new target's test source sets, and its
    main source set until an app ships from it.
