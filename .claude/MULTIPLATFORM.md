# DiceFive: multiplatform layout

The app is Kotlin Multiplatform and Compose Multiplatform with one target, Android.
- The game, UI and persistence live in `app/shared`; only the platform services are per platform.
- The split is for segregation: platform code sits behind the seams below, so another target (a desktop app, say)
  can be added beside Android without moving the shared code.

## Layout

```
app/
├── shared/      :app:shared - KMP library (com.android.kotlin.multiplatform.library)
│   ├── schemas/                 Room's exported schemas (commit every new version)
│   └── src/
│       ├── commonMain/kotlin/net/zodac/dicefive/
│       │   ├── model/ game/     the rules engine
│       │   ├── data/            Room (scores), DataStore (settings, achievements, in-progress game), GameStateJson
│       │   ├── app/             AppContainer, BuildInfo
│       │   ├── platform/        PlatformServices and its parts (+ SilentPlatformServices for previews/tests)
│       │   ├── online/          the Play Games seam (PLAY_GAMES.md)
│       │   └── navigation/ ui/  Compose; ui/DiceFiveApp.kt is the root
│       ├── commonMain/composeResources/   strings, drawables, fonts, files (Res.*)
│       ├── commonTest/          unit tests (kotlin.test), run on the JVM
│       ├── androidHostTest/     JVM-only tests (resource files, the perfect-play table)
│       └── androidMain/         stripDiacritics actual and other small actuals
├── android/     :app:android - the APK: MainActivity.kt; device/ (AndroidPlatformServices: SoundPool, Vibrator,
│                SensorManager, Toast, licence JSON; AndroidAppContainer; TextViewLicenceDocument); the play and
│                github flavours (src/play: Play Games; src/github: AndroidProgressFiles)
└── licensing/   licence records for everything the app ships (see its README)
```

- **Two modules**, because AGP 9 won't let one module be both KMP and an Android application.
- **Naming.** Entry points sit in the root package, and platform implementations in `device/`, prefixed with the
  platform (`AndroidSoundPlayer`).
- **Android's implementations live in `app/android`**, because they need `R.raw`, the generated licence JSON and
  `BuildConfig`.
- **`app/licensing/` sits outside both modules**, because it covers both.
- **Nothing compiles `commonMain` for a non-Android target**, so an Android or JVM API there still builds. Keeping it
  out is a review rule: platform code goes in `androidMain` or `app/android`, behind an `expect` or `PlatformServices`.
- `:app:shared`'s `testDebugUnitTest` runs its JVM tests (`testAndroidHostTest`).

## The seams

- **JSON.** `data/Json.kt` is hand-written: `kotlinx.serialization` cost ~49KB of APK. JVM tests still need `org.json`
  on the classpath for AboutLibraries.
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
  - Room KMP: `@ConstructedBy(AppDatabaseConstructor)`, KSP per target. Android uses the framework's SQLite.
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
- **SDK levels.** `app/shared` reads `compileSdk`, `compileSdkMinor` and `minSdk` from `app/android/build.gradle.kts`.
  Keep them as plain `name = <number>` lines; the scripts edit them.
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
- **Room's KSP warning about `List<Boolean?>`** on `outcomesForPlayer` is harmless. `NULL` maps to `null` (a solo game),
  which the streak logic relies on.
