# DiceFive: iOS support reference

The app runs on iOS as well as Android without a Swift rewrite: Kotlin Multiplatform (KMP) + Compose Multiplatform
(CMP). The game, its UI and its persistence all live in the `app/shared` module and compile for Android *and* iOS on
every build; the Android app is a thin shell around them. Only a handful of platform services (sound, haptics, the
accelerometer, transient messages, storage paths, the licence text view) have per-platform implementations, and those
are Kotlin too - Swift will only be the ~20-line iOS app shell.

**Status**: the iOS side of every platform service is written and compiles, but no iOS app has been linked or run - that
needs a Mac. What's left is "The iOS app (needs macOS)" and "CI and release" below.

---

## Layout

All the code lives under `app/`, split by platform: `app/shared` is everything platform-neutral,
`app/android` the Android app, and `app/ios` (still to come) will be the Xcode project. They're separate
Gradle modules (`:app:shared`, `:app:android`) because AGP 9 won't let one module be both Kotlin
Multiplatform and an Android application.

```
DiceFive/app/
├── shared/                          :app:shared - KMP library (com.android.kotlin.multiplatform.library)
│   ├── schemas/                     Room's exported schemas (commit every new version)
│   └── src/
│       ├── commonMain/kotlin/net/zodac/dicefive/
│       │   ├── model/  game/        the rules engine, unchanged
│       │   ├── data/                Room KMP (scores), DataStore KMP (settings, achievements,
│       │   │                        in-progress game), GameStateJson
│       │   ├── app/                 AppContainer, BuildInfo - what each entry point builds
│       │   ├── platform/            PlatformServices and its parts - the interfaces a platform
│       │   │                        implements (plus SilentPlatformServices, for previews)
│       │   ├── navigation/  ui/     Compose Multiplatform; ui/DiceFiveApp.kt is the root
│       ├── commonMain/composeResources/   strings, drawables, fonts, files  - Res.*
│       ├── commonTest/              the unit tests (kotlin.test) - JVM now, iOS simulator later
│       ├── androidHostTest/         JVM-only tests (resource files, the perfect-play table)
│       ├── androidMain/             stripDiacritics actual only
│       └── iosMain/kotlin/net/zodac/dicefive/
│           ├── MainViewController.kt    the iOS entry point (MainActivity's counterpart)
│           ├── device/              IosPlatformServices, IosAppContainer, IosStorage,
│           │                        TransientMessageHost
│           └── model/               stripDiacritics actual
├── android/                         :app:android - com.android.application, the APK
│   └── src/main/kotlin/net/zodac/dicefive/
│       ├── MainActivity.kt          builds the container + platform services, calls DiceFiveApp
│       └── device/                  AndroidPlatformServices (SoundPool, Vibrator, SensorManager,
│                                    Toast, licence JSON from res/raw), AndroidAppContainer, and
│                                    TextViewLicenceDocument (see UI.md)
├── licensing/                       licence records for everything either platform ships: asset
│                                    sources, library/asset entries, licence texts, the font's own
│                                    OFL file (see its README)
└── ios/                             (to come) Xcode project hosting MainViewController()
```

The same package means the same job on each platform: the entry point sits in the root package
(`MainActivity`, `MainViewController`), and everything a platform implements is in `device/`,
prefixed with the platform's name (`AndroidSoundPlayer`, `IosSoundPlayer`).

**Decisions:**

- **Both modules sit under `app/`** (`app/shared`, `app/android`), so all the code has one home and the split inside it
  is by platform. The licence records sit beside them in `app/licensing/`, not inside either module, because they cover
  files from both and an iOS build will need them too; `app/android` is the one that reads them today.
- **Android's platform implementations live in `app/android` (`device/`), not in `app/shared/src/androidMain`.** They
  need the app's own resources (`R.raw` audio, the generated licence JSON) and its `BuildConfig`, which the library can't
  see. iOS's live in `app/shared/src/iosMain`, because the iOS app is Swift and can only call into the Kotlin framework
  the shared module builds.
- **The iOS targets compile on Linux.** Kotlin/Native compiles iOS `.klib`s here - Apple platform libraries (UIKit,
  Foundation, CoreMotion...) included - so every build proves `commonMain` has no JVM/Android API in it and that
  `iosMain` compiles against the real Apple APIs. Only linking a binary and running tests needs macOS (those tasks are
  skipped elsewhere - `kotlin.native.ignoreDisabledTargets` in `gradle.properties`). `:app:shared` registers a
  `testDebugUnitTest` task (JVM host tests + both iOS compiles + the iOS test compile), so CI and the dependency-update
  script cover it with no iOS-specific step.

## How the seams work

- **JSON**: `data/Json.kt`, a small hand-written reader/writer (`GameStateJson`, licence notices) - kotlinx.serialization
  cost ~49KB of the release APK for those two uses. The JVM tests still need `org.json` on their classpath, because
  AboutLibraries' Android parser uses it internally.
- **Dates and numbers**: `formatTimestamp` is an `expect fun` (Android: `java.time`; iOS: `NSDateFormatter`), number
  grouping a common `grouped()`, in `ui/common/Formatting.kt` - kotlinx-datetime was ~180 classes for one pattern.
- **Time**: `game/Clock.kt`'s `nowEpochMillis()` (`kotlin.time.Clock`).
- **Platform services**: `platform/PlatformServices` - `SoundPlayer`, `HapticsPlayer`, `Accelerometer`,
  `showTransientMessage`, `LicenceDocument`, `loadLicenceReports`, `reduceMotion`, `capFrameRate`. `SoundEffects` /
  `DiceHaptics` are shared wrappers; `ShakeDetector` is pure logic fed samples (`ShakeDetectorTest`).
  `SilentPlatformServices` serves previews and tests.
- **Build info**: `BuildInfo` (version name, debug flag). Superuser-mode tests pass `isDebugBuild` explicitly, and a test
  pins that a release build never activates it.
- **Text**: `IrishEasterEgg`'s accent folding is `expect fun stripDiacritics` (iOS: Foundation's
  `stringByFoldingWithOptions(NSDiacriticInsensitiveSearch)`).
- **Persistence**: Room's multiplatform build (`@ConstructedBy(AppDatabaseConstructor)`, KSP per target, schemas
  exported to `app/shared/schemas/`); Android opens it on the framework SQLite, iOS on `BundledSQLiteDriver`
  (`createIosAppDatabase`, in Application Support). DataStore repositories take a `DataStore<Preferences>` from
  `createPreferencesDataStore(path)` and the `PreferencesFile` names. `AndroidAppContainerTest` (Robolectric) opens the
  real Android storage.
- **UI**: on Android each Compose Multiplatform artifact resolves to the androidx one it wraps, so the APK runs the same
  Compose/Material3. Resources are `Res.*` from `composeResources/` (`SoraFontFamily` is a `@Composable` getter, as CMP's
  `Font()` is). `BackHandler` is `ui/common/BackHandler.kt` on the multiplatform navigation-event API. Licences: the
  dialog, report parsing and link detection are common; Android supplies its `TextView` document, iOS and previews
  `ComposeLicenceDocument`. `MainActivitySmokeTest` (Robolectric) launches the real app end to end.
- **iOS side**: `IosPlatformServices` (AVAudioPlayer, UIImpactFeedbackGenerator, CoreMotion, a snackbar
  `TransientMessageHost`), `IosStorage`, `IosAppContainer` and `MainViewController()` - all compile-checked only.

---

## Gotchas worth not re-learning

- **The KMP library plugin creates no lint tasks for its main code** unless `com.android.lint` is
  also applied - so `app/shared` applies it, and `app/android`'s lint sets `checkDependencies = true`,
  which makes the `lintDebug` CI runs analyse the shared code under the app's rules. Without both, lint silently skips
  the shared code.
- **`app/shared` reads `compileSdk`, `compileSdkMinor` and `minSdk` from
  `app/android/build.gradle.kts`** (the file the dependency-update and SDK-package scripts edit), so
  the two modules can't compile against different SDKs. Keep those as plain `name = <number>` lines.

- **Watch the release APK size before adding a multiplatform library** - see "How the seams work" for the two that
  were dropped for it. Compare R8 mappings (`build/outputs/mapping/release/`)
  class counts per package to see what a change retains.

- **Kotlin/Native rejects some characters in function names**: no commas, parentheses (and
  `.`, `:`, `/`, `<`, `>`, `[`, `]`) in backticked test names in `commonTest`. Use " - " instead.
- **JetBrains material3 has its own version line**: 1.9.x = androidx material3 1.4.x. Keep
  `composeMultiplatformMaterial3` matched to the Compose BOM's material3 (and `composeMultiplatform`
  to its Compose UI) - see the comment in `gradle/libs.versions.toml`.
- **JetBrains navigation-compose 2.9 pulls androidx navigation 2.9 on Android**; `shared`'s
  `androidMain` depends on androidx `navigation-compose` directly to stay on the newer line.
- **CMP's `BackHandler` and the `compose.*` dependency accessors are deprecated** - with
  deprecations as errors, use `ui/common/BackHandler.kt` and plain catalog coordinates.
- **A `@Composable` member of `PlatformServices` must not share a name with the top-level
  composable it delegates to** (hence `TextViewLicenceDocument`) - it would call itself.
- **Room's KSP warning about `List<Boolean?>`** on `outcomesForPlayer` is harmless: the generated
  code does map SQL `NULL` to `null` (a solo game), which the win-streak logic relies on.

---

## The iOS app (needs macOS)

- **Xcode project** (`app/ios/`): a SwiftUI app hosting `MainViewControllerKt.MainViewController()`
  (sample in `MainViewController.kt`'s KDoc); link the `shared` framework - add an
  `iosTarget.binaries.framework { baseName = "Shared"; isStatic = true }` to
  `app/shared/build.gradle.kts`, and an Xcode build phase running `embedAndSignAppleFrameworkForXcode`.
  Info.plist: `CFBundleShortVersionString` from `VERSION` (read by `BuildInfo` on iOS).
- **Audio**: iOS doesn't decode Ogg Vorbis. Extend `NormalizeOggAudioTask` to also write AAC
  `.m4a` copies and bundle them in the Xcode project; `IosSoundPlayer` looks for
  `<clip>.m4a` in the main bundle and stays silent without them. The copies are still the same
  Freesound recordings, so they need the same credits: generate them from `rawAudioSource/` (never
  commit hand-made ones), and have iOS's licence report include the `freesound-*` entries from
  `app/licensing/libraries/` (already platform-neutral) - arguably the clips themselves should move
  to `app/shared` once two platforms use them. Consider an `AVAudioSession`
  category (ambient, so it respects the silent switch).
- **Licences on iOS**: `IosPlatformServices.loadLicenceReports` returns an empty report. iOS needs
  its own generated report (AboutLibraries has a multiplatform Gradle plugin; the notices task is
  JVM-classpath-based) and the copyleft guard applied to the iOS dependency graph - the Android
  report (built from `app/licensing/`) already covers everything app/shared uses on Android, but
  not iOS-only dependencies such as `sqlite-bundled` - see
  `PUBLISHING.md`. Check whether `ComposeLicenceDocument`'s links inside `SelectionContainer`
  behave on iOS (they didn't on Android - see `DESIGN.md`, "Settings"); if not, a `UITextView` via
  `UIKitView`.
- **Language: no partial translations** (`I18N.md`, "Layout direction and plural forms"). Declare
  `CFBundleLocalizations` (en, es...) with `CFBundleDevelopmentRegion` en, so iOS's own text follows the app's
  languages. Then fix plurals: Compose on iOS reads `NSLocale.preferredLanguages` (the device's list), so an iPhone in a
  language with no translation picks the English plural forms by that language's rules ("21 roll" in Russian). The iOS
  `alignPlatformLanguage` is a no-op waiting for this; check what Compose resources' iOS environment reads, and run
  `StringsLanguageTest`'s cases by hand (Russian phone: "21 solo games played").
- **No system back on iOS**: `GameScreen` (leave game) and `ScorecardReviewScreen` rely on
  `BackHandler`. Give both a visible way out (iOS's edge-swipe back may also need wiring).
- **Insets**: re-verify `UI.md`'s inset rules against safe areas, the Dynamic Island and the home
  indicator; `TransientMessageHost` already pads by `safeDrawing`.
- **Accelerometer axes**: `Accelerometer` promises readings along the *screen's* axes as currently
  turned (Android remaps by the display's rotation), with Android's sign; `IosAccelerometer` already
  negates CoreMotion's gravity-signed readings, but doesn't yet turn them for a rotated interface. If
  the app can rotate on iOS, remap by the interface orientation, or the menu logo's googly pupils
  (the only user of the direction - shake-to-roll uses magnitude alone) will fall sideways.
- **Run the tests on iOS**: `./gradlew :app:shared:iosSimulatorArm64Test` on a Mac runs `commonTest`
  natively - the first real check of `stripDiacritics`, Room/DataStore on iOS, etc.
- **Haptics tuning**: the impact styles in `IosHapticsPlayer` are a first guess.
- **Reduce Motion**: `PlatformServices.reduceMotion()` defaults to `flowOf(false)`, so on iOS the
  shared `LocalReduceMotion` is only on through the app's own "Animations" set to Off, and the
  levels' frame caps (`PlatformServices.capFrameRate`, Android's `CappedFrameClock`) do nothing there
  yet. Implement it in `IosPlatformServices` from
  `UIAccessibilityIsReduceMotionEnabled()`, re-emitting on
  `UIAccessibilityReduceMotionStatusDidChangeNotification` - not written here because it can't be
  compiled without a Mac. `UI.md`'s "Reduced motion" section says what it changes (and why the cup's
  shake window stays while sound or vibration is on - re-check the m4a copies' lengths match the
  ogg ones, since the window is timed to them).

## CI and release

- A macOS job for the iOS build and `iosSimulatorArm64Test`; TestFlight / App Store Connect
  upload if wanted. The Linux jobs already compile iOS on every run (via `testDebugUnitTest`),
  with the Kotlin/Native toolchain (`~/.konan`) cached by both workflows.
- `update_dependency_versions.sh` already bumps the new catalog entries generically; the
  JetBrains/androidx version pairing above is the thing to watch in its build verification.
- New commit category `[iOS]` for iOS-only work (add it to CLAUDE.md's table in the same commit).

---

## Open decisions

- **Mac access** - a local Mac, or macOS CI runners only? Without one, the iOS app can't be built or tested.
- **Licences page on iOS** - keep `ComposeLicenceDocument`, or a native `UITextView`?
