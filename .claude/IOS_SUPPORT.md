# DiceFive: iOS support

The app targets iOS through Kotlin Multiplatform and Compose Multiplatform, with no Swift rewrite.
- The game, UI and persistence live in `app/shared`, which compiles for Android *and* iOS on every build.
- Only the platform services are per platform, and those are Kotlin too. Swift will be just the ~20-line app shell.

**Status.** The iOS side of every platform service is written and compiles. No iOS app has been linked or run; that
needs a Mac.

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
│       │   ├── online/          the Play Games / Game Center seam (PLAY_GAMES.md)
│       │   └── navigation/ ui/  Compose; ui/DiceFiveApp.kt is the root
│       ├── commonMain/composeResources/   strings, drawables, fonts, files (Res.*)
│       ├── commonTest/          unit tests (kotlin.test) - JVM now, iOS simulator later
│       ├── androidHostTest/     JVM-only tests (resource files, the perfect-play table)
│       ├── androidMain/         stripDiacritics actual and other small actuals
│       └── iosMain/.../         MainViewController.kt (entry point); device/ (IosPlatformServices, IosAppContainer,
│                                IosStorage, TransientMessageHost); actuals
├── android/     :app:android - the APK: MainActivity.kt; device/ (AndroidPlatformServices: SoundPool, Vibrator,
│                SensorManager, Toast, licence JSON; AndroidAppContainer; TextViewLicenceDocument)
├── licensing/   licence records for everything either platform ships (see its README)
└── ios/         (to come) the Xcode project hosting MainViewController()
```

- **Two modules**, because AGP 9 won't let one module be both KMP and an Android application.
- **Naming.** The same package means the same job on each platform: entry points sit in the root package, and platform
  implementations in `device/`, prefixed with the platform (`AndroidSoundPlayer`, `IosSoundPlayer`).
- **Android's implementations live in `app/android`**, because they need `R.raw`, the generated licence JSON and
  `BuildConfig`. iOS's live in `iosMain`, because Swift can only call the Kotlin framework.
- **`app/licensing/` sits outside both modules**, because it covers both platforms.
- **iOS compiles on Linux.** Kotlin/Native builds iOS `.klib`s against the real Apple APIs, so every build proves
  `commonMain` is platform-neutral.
  - Only linking and running tests need macOS (`kotlin.native.ignoreDisabledTargets`).
  - `:app:shared`'s `testDebugUnitTest` runs the JVM tests plus both iOS compiles and the iOS test compile.

## The seams

- **JSON.** `data/Json.kt` is hand-written: `kotlinx.serialization` cost ~49KB of APK. JVM tests still need `org.json`
  on the classpath for AboutLibraries.
- **Dates and numbers.** `expect fun formatTimestamp`, with `grouped()` and friends in `ui/common/Formatting.kt`.
  `kotlinx-datetime` was ~180 classes.
- **Time.** `game/Clock.kt`'s `nowEpochMillis()` (`kotlin.time.Clock`).
- **Platform services.** `PlatformServices`: `SoundPlayer`, `HapticsPlayer`, `Accelerometer`, `showTransientMessage`,
  `LicenceDocument`, `loadLicenceReports`, `reduceMotion`, `capFrameRate`.
  - `SoundEffects` and `DiceHaptics` are shared wrappers.
  - `ShakeDetector` is pure logic (`ShakeDetectorTest`).
- **Build info.** `BuildInfo` (version, debug flag). Superuser tests pass `isDebugBuild` explicitly, and a test pins
  that release builds never activate it.
- **Text.** `expect fun stripDiacritics` (iOS: `stringByFoldingWithOptions(NSDiacriticInsensitiveSearch)`).
- **Persistence.**
  - Room KMP: `@ConstructedBy(AppDatabaseConstructor)`, KSP per target. Android uses framework SQLite; iOS uses
    `BundledSQLiteDriver` (`iosAppDatabaseBuilder`, in Application Support).
  - DataStore: `createPreferencesDataStore(path)` with the `PreferencesFile` names; `AppContainer.create(dataDir,
    databaseBuilder, buildInfo)` builds the container for every platform.
  - Tests: `AndroidAppContainerTest`.
- **UI.**
  - On Android, the CMP artifacts resolve to androidx, so it's the same Compose.
  - `SoraFontFamily` is a `@Composable` getter, as CMP's `Font()` is.
  - `BackHandler` is `ui/common/BackHandler.kt`, on the navigation-event API.
  - Licences: Android supplies `TextView`; iOS and previews use `ComposeLicenceDocument`.
  - Tests: `MainActivitySmokeTest` launches the real app.

## Gotchas

- **Lint skips the shared code** unless `com.android.lint` is applied to `app/shared` **and** `app/android`'s lint sets
  `checkDependencies = true`. Both are in place.
- **SDK levels.** `app/shared` reads `compileSdk`, `compileSdkMinor` and `minSdk` from `app/android/build.gradle.kts`.
  Keep them as plain `name = <number>` lines; the scripts edit them.
- **Watch the release APK size** before adding a multiplatform library. Compare the R8 mapping class counts per package
  (`build/outputs/mapping/release/`).
- **Test names in `commonTest`**: no commas, parentheses, `.`, `:`, `/`, `<`, `>`, `[` or `]` (Kotlin/Native). Use
  " - ".
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

## The iOS app (needs macOS)

- **Xcode project** (`app/ios/`): a SwiftUI app hosting `MainViewControllerKt.MainViewController()` (a sample is in its
  KDoc).
  - Add `iosTarget.binaries.framework { baseName = "Shared"; isStatic = true }` to `app/shared/build.gradle.kts`, and
    an `embedAndSignAppleFrameworkForXcode` build phase.
  - Set `CFBundleShortVersionString` from `VERSION`.
- **Audio.** iOS can't decode Ogg.
  - Extend `NormalizeOggAudioTask` to write AAC `.m4a` copies, generated and never hand-made.
  - Bundle them; `IosSoundPlayer` looks for `<clip>.m4a`.
  - iOS's licence report must include the `freesound-*` entries. Consider moving the clips to `app/shared`.
  - Use an ambient `AVAudioSession`, so the silent switch is respected.
  - The shake window is timed to the clips, so check the m4a lengths match.
- **Licences.** `loadLicenceReports` returns empty on iOS. Generate an iOS report (AboutLibraries has a multiplatform
  plugin) and apply the copyleft guard to iOS-only dependencies (`sqlite-bundled`). Check that `ComposeLicenceDocument`'s
  links work in a `SelectionContainer`; if not, use a `UITextView` via `UIKitView`.
- **Language** (`I18N_LANGUAGES.md`).
  - Declare `CFBundleLocalizations` with `CFBundleDevelopmentRegion` en.
  - Fix `alignPlatformLanguage` (currently a no-op): Compose reads `NSLocale.preferredLanguages`. Check that a Russian
    iPhone shows "21 solo games played".
- **No system back.** `GameScreen` and `ScorecardReviewScreen` rely on `BackHandler`. Give them a visible way out, and
  maybe wire the edge swipe.
- **Insets.** Re-verify safe areas, the Dynamic Island and the home indicator. `TransientMessageHost` already pads by
  `safeDrawing`.
- **Accelerometer.** `IosAccelerometer` negates gravity but doesn't remap for a rotated interface. If the app rotates,
  remap it, or the logo's googly pupils fall sideways (shake-to-roll uses magnitude only).
- **Tests.** `./gradlew :app:shared:iosSimulatorArm64Test` on a Mac. It's the first real check of `stripDiacritics` and
  of Room and DataStore on iOS.
- **Haptics.** `IosHapticsPlayer`'s impact styles are a first guess.
- **Reduce Motion.** `reduceMotion()` follows `UIAccessibilityIsReduceMotionEnabled()` and its change notification,
  unchecked on a device. `capFrameRate` does nothing on iOS yet.
- **Background.** `IosSoundPlayer` pauses and resumes the clips that were playing, unchecked on a device.
  `HapticsPlayer.cancel` does nothing: an impact tap has no length to cut short. `TransientMessageHost` uses a plain
  `delay`.

## CI and release

- **A macOS job** for the build and `iosSimulatorArm64Test`, plus TestFlight if wanted. The Linux jobs already compile
  iOS, with `~/.konan` cached.
- **`update_dependency_versions.sh`** bumps the entries generically, except those marked `# @update` in
  `libs.versions.toml`: the JetBrains Compose entries match the Compose BOM, and sqlite follows Room.
- **Commits** go under the `[iOS]` category.

## Open decisions

- **Mac access**: a local Mac, or macOS CI runners only?
- **Licences page on iOS**: keep `ComposeLicenceDocument`, or use a native `UITextView`?
