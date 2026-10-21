# DiceFive: iOS support

The app targets iOS through Kotlin Multiplatform and Compose Multiplatform, with no Swift rewrite.
- The game, UI and persistence live in `app/shared`, which compiles for Android *and* iOS on every build.
- Only the platform services are per platform, and those are Kotlin too. Swift will be just the ~20-line app shell.

**Status.** The iOS side of every platform service is written and compiles. No iOS app has been linked or run yet.
There is no Mac: macOS GitHub runners build the app, and Sideloadly (or SideStore) installs it on an iPhone. The phases
are under "Roadmap"; the next one to start is marked there.

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
└── ios/         the XcodeGen spec (project.yml), the Swift shell hosting MainViewController(), the icon
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

## Roadmap

Each phase ends with something checkable on the iPhone or in CI. Tick items off here as they land, and move the
**Next** marker. Commits go under `[iOS]`.

### How the Mac-less loop works

- **Build.** A macOS GitHub runner (free for this public repo) runs Gradle for the Kotlin framework and `xcodebuild` for
  the app. The repo holds an XcodeGen spec (`app/ios/project.yml`), never a hand-edited `.pbxproj`: the runner generates
  the project.
- **Package.** `xcodebuild ... CODE_SIGNING_ALLOWED=NO`, then zip `Payload/DiceFive.app` as `DiceFive.ipa`. Upload it
  and the dSYMs as workflow artifacts (`gh run download`).
- **Install.** Sideloadly (Windows or macOS) re-signs the IPA with an Apple ID and installs it over USB or Wi-Fi.
  SideStore is the on-phone alternative: after a one-off pairing from a computer, it refreshes the signature itself.
  - A **free Apple ID** works for development builds:
    - The signature lasts 7 days; re-sign over the top to keep the app's data.
    - Only a few sideloaded apps at once, and limited new app ids per week.
    - No Game Center, iCloud or push entitlements.
  - The iPhone needs **Developer Mode** on (Settings > Privacy & Security), and the Apple ID's profile trusted once.
- **See it fail.** No Xcode console.
  - Logs come from `idevicesyslog` (libimobiledevice, works on Linux) or Sideloadly's log.
  - Crash reports are in Settings > Privacy & Security > Analytics & Improvements > Analytics Data, symbolicated
    against the run's dSYM.
- **See it look right.** The simulator on the runner stands in for the `render` skill: CI boots it, installs the
  simulator build and saves `xcrun simctl io booted screenshot` PNGs as artifacts.
- **Shipping** to the App Store needs the paid Apple Developer Program. Signing works without a Mac (Phase 6).

### Phase 0: decisions (done)

- [x] **Apple account**: a free Apple ID for Phases 1-5; the paid programme only from Phase 6.
- [x] **Installer**: Sideloadly on Windows.
- [x] **Bundle id**: `net.zodac.dicefive`, as on Android. Free signing may append a suffix; the shipped id stays this
  one and is permanent once released.
- [x] **Minimum iOS**: 16.0 (Compose Multiplatform's own floor is lower).
- [x] **Orientation**: rotates as on Android (portrait and both landscapes). The Orientation setting and the
  accelerometer remap are Phase 3.
- [x] **Licences page**: keep `ComposeLicenceDocument` unless its links fail on the phone (Phase 3).

### Phase 1: first IPA on the phone (Next)

Exit: the app launches on the iPhone, a game can be played, and it survives being killed and reopened.

- [x] **Framework.** A static `Shared` framework for both iOS targets in `app/shared/build.gradle.kts`.
- [x] **`app/ios/`.**
  - XcodeGen `project.yml`: one app target, iPhone only, a pre-build phase running
    `./gradlew :app:shared:embedAndSignAppleFrameworkForXcode`. The generated project and `Info.plist` are ignored.
  - `DiceFive/DiceFiveApp.swift` hosting `MainViewControllerKt.MainViewController()`, edge to edge.
  - `Info.plist` (in `project.yml`): version from `VERSION` and build number from the run, both passed by `ios.yml`;
    dark only; the three languages; `CADisableMinimumFrameDurationOnPhone`; no non-exempt encryption.
  - App icon: `scripts/generate-store-assets.sh --parts icon` writes `AppIcon.png` (1024px, no alpha) with the Play
    icon. The launch screen is `window_background`, as on Android.
- [x] **`.github/workflows/ios.yml`.**
  - `workflow_dispatch` only, Debug (default) or Release. `release.yml` ignores `app/ios/**`.
  - `macos-latest`, Xcode unpinned for now (the log prints its version). `build-setup` puts GNU grep on the PATH on
    macOS, for its `grep -P` scripts.
  - `xcodegen`, `xcodebuild` for device (unsigned), the IPA and dSYM zip uploaded as one artifact.
- [ ] **First CI run.** Not run yet: the workflow must be on `main` before it can be dispatched. Likely first-run
  fixes: the Android SDK set-up on macOS, Gradle's configuration cache under Xcode, Compose resources in the app
  bundle.
- [ ] **Smoke on the phone**: launch, play a solo game, settings and achievements persist after a kill (Room and
  DataStore on iOS for the first time), Application Support paths are right.

### Phase 2: iOS tests and screenshots in CI

Exit: every iOS-touching change has a CI job that runs it, not just compiles it.

- [ ] **`./gradlew :app:shared:iosSimulatorArm64Test`** in `ios.yml`. It's the first real check of `stripDiacritics`,
  `OrdinalIosTest`, and Room and DataStore on iOS. Fix what fails.
- [ ] **Simulator screenshots**: a simulator build, booted on an iPhone and an iPad, a screenshot per main screen
  (menu, game, scorecard, achievements, settings), uploaded as artifacts.
  - Drive it with launch arguments or a small XCUITest.
  - Same purpose as the `render` skill: visual review before an IPA goes to the phone.
- [ ] **Trigger**: on PRs/pushes touching `app/shared/**` or `app/ios/**`, as well as on demand.
- [ ] **Skill**: an `ios-build` skill (or a section in `debug-apk`) for "dispatch the workflow, download the IPA,
  send it".

### Phase 3: platform parity

Exit: everything Android does offline, iOS does, checked on the phone.

- [ ] **Audio.** iOS can't decode Ogg.
  - Extend `NormalizeOggAudioTask` to write AAC `.m4a` copies, generated and never hand-made.
  - Bundle them; `IosSoundPlayer` looks for `<clip>.m4a`.
  - iOS's licence report must include the `freesound-*` entries. Consider moving the clips to `app/shared`.
  - Use an ambient `AVAudioSession`, so the silent switch is respected.
  - The shake window is timed to the clips, so check the m4a lengths match.
- [ ] **No system back.** `GameScreen` and `ScorecardReviewScreen` rely on `BackHandler`. Give them a visible way out,
  and maybe wire the edge swipe.
- [ ] **Insets.** Re-verify safe areas, the Dynamic Island and the home indicator. `TransientMessageHost` already pads
  by `safeDrawing`.
- [ ] **Language** (`I18N_LANGUAGES.md`).
  - `CFBundleLocalizations` and `CFBundleDevelopmentRegion` are declared (Phase 1).
  - Fix `alignPlatformLanguage` (currently a no-op): Compose reads `NSLocale.preferredLanguages`. Check that a Russian
    iPhone shows "21 solo games played".
  - Arabic lays out right to left.
- [ ] **Frame rate.**
  - `CADisableMinimumFrameDurationOnPhone` is set (Phase 1), so ProMotion phones can go above 60.
  - `capFrameRate` does nothing on iOS yet; it must honour the Animations level as Android does.
- [ ] **Accelerometer.** `IosAccelerometer` negates gravity but doesn't remap for a rotated interface. If the app
  rotates, remap it, or the logo's googly pupils fall sideways (shake-to-roll uses magnitude only).
- [ ] **Haptics.** `IosHapticsPlayer`'s impact styles are a first guess; tune them on the phone.
- [ ] **Background.** `IosSoundPlayer` pauses and resumes the clips that were playing, unchecked on a device.
  `HapticsPlayer.cancel` does nothing: an impact tap has no length to cut short. `TransientMessageHost` uses a plain
  `delay`.
- [ ] **Licences.** `loadLicenceReports` returns empty on iOS.
  - Generate an iOS report (AboutLibraries has a multiplatform plugin).
  - Apply the copyleft guard to iOS-only dependencies (`sqlite-bundled`).
  - Check that `ComposeLicenceDocument`'s links work in a `SelectionContainer`; if not, use a `UITextView` via
    `UIKitView`.
- [ ] **Version and debug flag.** `BuildInfo` on iOS reports the `VERSION` and a debug flag that is false for release
  builds (the superuser test's rule).

### Phase 4: accessibility and polish on the phone

Exit: the `.claude/UI.md` checklist passes with VoiceOver and the largest text sizes, and the report says what was and
wasn't heard.

- [ ] **VoiceOver.** Walk every screen: the dice tray, holding dice, scoring a row, banners, dialogs, the tutorial.
  Compare with what TalkBack says, word for word.
- [ ] **Text size.** Dynamic Type at the largest standard and accessibility sizes. Check names, the scorecard grid and
  achievement titles (one line in the banner).
- [ ] **Reduce Motion.** `reduceMotion()` follows `UIAccessibilityIsReduceMotionEnabled()` and its change
  notification; check it on a device.
- [ ] **Frame cost.** 60fps minimum at the default Animations level on an older supported iPhone (Instruments isn't
  available; use a debug frame counter overlay).
- [ ] **iPad.** The tablet and landscape layouts from `UI_BOARD.md`, if iPad is supported.
- [ ] **Size.** IPA size and the Kotlin framework's share of it.

### Phase 5: online seam without Play Games

Exit: the app is honest about having no online features on iOS.

- [ ] **The online seam** (`online/`, `PLAY_GAMES.md` "Later: iOS"): iOS gets the "no online" implementation, so
  sign-in, cloud save and Sidekick UI don't appear. Capabilities stay `GameMode`-style data, never `if (iOS)`.
- [ ] **Player text.** Nothing on iOS mentions Google Play Games. Check the strings each screen shows.

### Phase 6: App Store readiness (paid Apple Developer Program)

Exit: a signed build reaches TestFlight from CI.

- [ ] **Signing without a Mac.**
  - Generate the CSR and key with `openssl` on Linux.
  - Create the distribution certificate and App Store profile in the developer portal.
  - Store the `.p12`, its password and the profile as GitHub secrets.
  - Or use an App Store Connect API key with fastlane `match`.
- [ ] **Release job**: archive signed, export with an `ExportOptions.plist`, upload to TestFlight (App Store Connect
  API key as a secret).
- [ ] **Store requirements.**
  - A privacy manifest (`PrivacyInfo.xcprivacy`) with the required-reason APIs the app and its libraries use (file
    timestamps, disk space, `UserDefaults` if any). No data collected, no tracking.
  - `ITSAppUsesNonExemptEncryption` = `false` (set in Phase 1).
  - Age rating, App Privacy answers, support URL (the GitHub Pages site).
  - Screenshots at the sizes App Store Connect requires: extend `store-assets` with the simulator screenshots from
    Phase 2.
- [ ] **Licences** (`PUBLISHING.md`): iOS's own report and copyleft guard before release.

### Phase 7: Game Center and iCloud (optional)

- [ ] **Game Center** through the online seam: achievements mirror and leaderboards keyed by the same permanent ids.
  `GKAccessPoint` raises the same placement question as Sidekick.
- [ ] **Cloud save** through iCloud (CloudKit or key-value store), the same merge rules as Play Games' snapshot.
- [ ] Both need the paid programme's entitlements, so they can't be tested on a free-signed build.

### Phase 8: release pipeline

- [ ] **`release.yml`** gains an iOS job: on a push that changes what ships, build, test and upload to TestFlight beside
  the Android release. The GitHub release attaches the IPA only if it's useful (it can't be installed unsigned).
- [ ] **Versions.** `VERSION` drives both apps; the build number is monotonic.
- [ ] **`update_dependency_versions.sh`** bumps the entries generically, except those marked `# @update` in
  `libs.versions.toml`: the JetBrains Compose entries match the Compose BOM, and sqlite follows Room. Add the Xcode
  pin to whatever it checks.
- [ ] **Docs.** `README.md` (player-facing) mentions iOS only once it's in the App Store. `CLAUDE.md`'s project section
  moves iOS from "Planned" to built.

### Later

- **A shared `jvmMain`, with a desktop target only.** The Android `Diacritics` and `PlayerName` actuals are plain
  `java.text`, and Room and DataStore both run on desktop, so an intermediate `jvmMain` shared by Android and desktop
  would avoid copying them. With Android as its only consumer it shares nothing, so it waits for the desktop target.
- **Ads and a Pro purchase** (`PUBLISHING.md`) would use StoreKit on iOS, through a seam like the online one.
