# DiceFive: iOS support reference

Making the app run on iOS as well as Android without rewriting it in Swift, keeping as much of
the existing Kotlin as possible.

**Status (2026-09-28): Phases 1-4 are done.** The game, its UI and its persistence all live in
the Kotlin Multiplatform `app/shared` module and compile for Android *and* iOS on every build; the
Android app is a thin shell around them. The iOS side of every platform service is written and
compiles, but no iOS app has been linked or run yet - that needs a Mac (Phase 5).

**The one-line version**: Kotlin Multiplatform (KMP) + Compose Multiplatform (CMP). Game logic
*and* UI are shared Kotlin; only a handful of platform services (sound, haptics, the
accelerometer, transient messages, storage paths, the licence text view) have per-platform
implementations, and those are Kotlin too - Swift is only the ~20-line iOS app shell.

---

## Layout

All the code lives under `app/`, split by platform: `app/shared` is everything platform-neutral,
`app/android` the Android app, and `app/ios` (Phase 5) will be the Xcode project. They're separate
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
│       ├── commonMain/composeResources/   drawable/ (3 vectors), font/ (Sora)  - Res.*
│       ├── commonTest/              the unit tests (kotlin.test) - JVM now, iOS simulator later
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
│                                    TextViewLicenceDocument (see DESIGN.md Phase 17)
├── licensing/                       licence records for everything either platform ships: asset
│                                    sources, library/asset entries, licence texts, the font's own
│                                    OFL file (see its README)
└── ios/                             (Phase 5) Xcode project hosting MainViewController()
```

The same package means the same job on each platform: the entry point sits in the root package
(`MainActivity`, `MainViewController`), and everything a platform implements is in `device/`,
prefixed with the platform's name (`AndroidSoundPlayer`, `IosSoundPlayer`).

**Deviations from the original plan, and why:**

- **Both modules sit under `app/`** (`app/shared`, `app/android`) rather than side by side at the
  root, so all the code has one home and the split inside it is by platform. The licence records
  sit beside them in `app/licensing/`, not inside either module, because they cover files from
  both and an iOS build will need them too; `app/android` is just the one that reads them today.
- **Android's platform implementations live in `app/android` (`device/`), not in
  `app/shared/src/androidMain`.** They
  need the app's own resources (`R.raw` audio, the generated licence JSON) and its `BuildConfig`,
  which the library can't see. iOS's live in `app/shared/src/iosMain`, because the iOS app is
  Swift and can only call into the Kotlin framework the shared module builds.
- **iOS targets were added in Phase 2, not Phase 5.** Kotlin/Native compiles iOS `.klib`s on
  Linux - Apple platform libraries (UIKit, Foundation, CoreMotion...) included - so every build
  now proves `commonMain` has no JVM/Android API in it, and that `iosMain` compiles against the
  real Apple APIs. Only linking a binary and running tests needs macOS (those tasks are skipped
  elsewhere - `kotlin.native.ignoreDisabledTargets` in `gradle.properties`).
- **No data was carried over.** The app is pre-release, so the Room schema was collapsed into a
  single initial version 1 (no migrations, no destructive fallback - an old pre-release install must
  clear its data). From now on, schema changes need a version bump and a migration.

---

## What was done (Phases 1-4)

### Phase 1 - Android-only APIs behind seams (commit `4241b5c`)

- `org.json` → a small hand-written JSON reader/writer, `data/Json.kt` (`GameStateJson`, same
  keys; licence notices). First done with kotlinx.serialization, which turned out to cost ~49KB of
  the release APK for these two uses. The JVM tests still need `org.json` on their classpath,
  because AboutLibraries' Android parser uses it internally.
- `java.time` / `NumberFormat` → `formatTimestamp` as an `expect fun` (Android: the same
  `java.time` formatter as before; iOS: `NSDateFormatter`) and a common `grouped()`, in
  `ui/common/Formatting.kt`. First done with kotlinx-datetime - ~180 classes in the APK for one
  pattern.
- `System.currentTimeMillis()` → `game/Clock.kt`'s `nowEpochMillis()` (`kotlin.time.Clock`).
- `platform/PlatformServices`: `SoundPlayer`, `HapticsPlayer`, `Accelerometer`,
  `showTransientMessage` (was `Toast`). `SoundEffects` / `DiceHaptics` are shared wrappers;
  `ShakeDetector` is now pure logic fed samples (and unit tested - `ShakeDetectorTest`).
- ViewModel factories take an `AppContainer` (via `LocalAppContainer`) instead of a `Context`.
- `BuildConfig.DEBUG` / `VERSION_NAME` → `BuildInfo`. The superuser-mode tests pass
  `isDebugBuild = true` explicitly, and a new test pins that a release build never activates it.

### Phase 2 - The shared module (commit `97476d6`; moved to `app/shared` later)

- `model/`, `game/` and `AchievementsState` into `commonMain`; their tests into `commonTest`.
- `IrishEasterEgg`'s `Normalizer` → `expect fun stripDiacritics` (iOS: Foundation's
  `stringByFoldingWithOptions(NSDiacriticInsensitiveSearch)`).
- `:app:shared` registers a `testDebugUnitTest` task (JVM host tests + both iOS compiles + the iOS
  test compile), so CI, the dependency-update script and CLAUDE.md's habits cover it unchanged.

### Phase 3 - Persistence (commit `745d9bb`)

- Room's multiplatform build: `@ConstructedBy(AppDatabaseConstructor)`, KSP per target, schema
  export to `app/shared/schemas/`. Android opens it on the framework SQLite (no driver set); iOS uses
  `BundledSQLiteDriver` (`createIosAppDatabase`, in Application Support).
- DataStore KMP: repositories take a `DataStore<Preferences>`; `createPreferencesDataStore(path)`
  plus `PreferencesFile` names. Android uses the same `files/datastore/` directory as before.
- `AndroidAppContainerTest` (Robolectric) opens the real Android storage, including the
  leaderboard tie-break SQL.

### Phase 4 - The UI (this commit)

- `ui/`, `navigation/` and `platform/` into `commonMain`, on JetBrains' Compose Multiplatform
  artifacts. On Android each resolves to the androidx artifact it wraps, so the APK runs the same
  Compose/Material3 versions as before.
- Resources: the three vectors and Sora → `composeResources/`, read as `Res.drawable.*` /
  `Res.font.sora` (`SoraFontFamily` is now a `@Composable` getter, as CMP's `Font()` is).
  `verifyAssetSources` scans both modules; `asset-sources.json` is keyed from the repo root.
- `BackHandler`: `ui/common/BackHandler.kt`, a stand-in built on the multiplatform
  navigation-event API (`NavigationBackHandler`) - CMP's own `BackHandler` is deprecated.
- Licences: the dialog, report parsing and link detection are common; the platform supplies
  the JSON (`loadLicenceReports`) and the document view (`PlatformServices.LicenceDocument`).
  Android keeps its `TextView`; iOS and previews use `ComposeLicenceDocument`.
- `MainActivitySmokeTest` (Robolectric) launches the real app: menu → setup → game → system
  back → "Leave game?" → menu, exercising resources, navigation, storage and back handling.
- iOS: `IosPlatformServices` (AVAudioPlayer, UIImpactFeedbackGenerator, CoreMotion, a snackbar
  `TransientMessageHost`), `IosStorage`, and `MainViewController()` - all compile-checked only.

---

## Gotchas worth not re-learning

- **The KMP library plugin creates no lint tasks for its main code** unless `com.android.lint` is
  also applied - so `app/shared` applies it, and `app/android`'s lint sets `checkDependencies = true`,
  which makes the `lintDebug` CI already runs analyse the shared code under the app's rules. Verified
  by planting a lint error in `commonMain`: it failed `lintDebug`, which it silently hadn't before.
- **`app/shared` reads `compileSdk`, `compileSdkMinor` and `minSdk` from
  `app/android/build.gradle.kts`** (the file the dependency-update and SDK-package scripts edit), so
  the two modules can't compile against different SDKs. Keep those as plain `name = <number>` lines.

- **Watch the release APK size before adding a multiplatform library.** It was 1.93MB before the
  port and reached 2.03MB; dropping kotlinx.serialization and kotlinx-datetime for small
  hand-written/platform code brought it to 1.95MB. What remains (~16KB) is Compose Multiplatform's
  resources runtime and the platform seams. Compare R8 mappings (`build/outputs/mapping/release/`)
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

## Phase 5 - iOS app (needs macOS)

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
  behave on iOS (they didn't on Android - `DESIGN.md` Phase 17); if not, a `UITextView` via
  `UIKitView`.
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

## Phase 6 - CI and release

- A macOS job for the iOS build and `iosSimulatorArm64Test`; TestFlight / App Store Connect
  upload if wanted. The Linux jobs already compile iOS on every run (via `testDebugUnitTest`),
  with the Kotlin/Native toolchain (`~/.konan`) cached by both workflows.
- `update_dependency_versions.sh` already bumps the new catalog entries generically; the
  JetBrains/androidx version pairing above is the thing to watch in its build verification.
- New commit category `[iOS]` for iOS-only work (add it to CLAUDE.md's table in the same commit).

---

## Open decisions

- **Mac access** - a local Mac, or macOS CI runners only? Without one, Phase 5 can't be tested.
- **Licences page on iOS** - keep `ComposeLicenceDocument`, or a native `UITextView`?
