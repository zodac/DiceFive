# DiceFive: iOS support reference

A plan for making the app run on iOS as well as Android, without rewriting it in Swift and while
keeping as much of the existing Kotlin as possible. **Nothing in this file is done yet** - it's a
plan to pick up whenever the port actually starts.

The survey below was taken on 2026-09-28 (at commit `b4bb383`). File names, line counts and
library versions will drift; re-check the "Android-only code" list before starting, and re-check
the tooling claims (AGP, Room, Compose Multiplatform) against their current docs, since this
area moves quickly.

**The one-line version**: Kotlin Multiplatform (KMP) + Compose Multiplatform (CMP). Game logic
*and* UI live in shared Kotlin; only a handful of platform services (sound, haptics, shake,
storage paths, the licence text view) get per-platform implementations, and those are written
in Kotlin too - Swift is only the ~20-line iOS app shell.

---

## Where the code stands today

| Layer | ~Lines | Android/JVM-only code in it |
|---|---|---|
| `model/` | 985 | One import: `java.text.Normalizer` in `IrishEasterEgg.kt` (diacritic stripping) |
| `game/` | 1,400 | None - `kotlin.random.Random` is already multiplatform |
| `data/` | 890 | Room, DataStore, `org.json`, `Context` |
| `navigation/` + `ui/` | 10,500 | ~95% plain Compose; the Android-only pieces are listed below |

Android-only code in `ui/` and `navigation/`:

- **ViewModel factories** - every `*ViewModel.factory(context: Context)` builds its repositories
  from an application `Context`. The ViewModels themselves already take repositories as
  constructor parameters (see `GameViewModel`), so only the factories need replacing.
- **`ui/game/SoundEffects.kt`** - `SoundPool`, loading `R.raw.*`.
- **`ui/game/Haptics.kt`** - `Vibrator` / `VibratorManager` / `VibrationEffect`.
- **`ui/game/ShakeDetector.kt`** - `SensorManager` accelerometer plus the beat-detection logic.
- **`Toast`** - `GameScreen`, `AchievementsScreen`, `LicenceDocument`.
- **`BackHandler`** (`androidx.activity.compose`) - `GameScreen`, `ScorecardReviewScreen`.
- **`java.time` date formatting** - `ScoresScreen`, `StatisticsScreen`, `AchievementsScreen`;
  **`java.text.NumberFormat`** - `AchievementsScreen.grouped()`.
- **`System.currentTimeMillis()`** - several ViewModels, `ScoreRepository`, `ShakeDetector`.
- **`ui/settings/LicenceDocument.kt`** - the one genuine rewrite: a full Android
  `TextView`/`ScrollView` with spans, clipboard and a context menu, hosted via `AndroidView`.
- **`ui/settings/LicensesDialog.kt`** - reads `R.raw.aboutlibraries` and
  `R.raw.third_party_notices`, parsed with `org.json`.
- **Resources** - `R.drawable.*` (custom vectors), `R.font.sora`, `R.raw.*` audio.
- **Material icons** - 98 distinct icons from `material-icons-extended`.

Everything else (screens, game board art, theme, dialogs, navigation graph) is ordinary Compose
and moves across largely unchanged.

---

## Target structure

Two decisions shape the layout:

1. **Share the UI too, not just the game logic.** UI is ~70% of the code and already Compose.
2. **Platform-ness is expressed by source set, not by package.** Keep the existing
   `model/ game/ data/ ui/` packages rather than renaming files; renaming only adds churn and
   breaks `git blame`. A `platform/` *package* is still useful - but for the common
   interfaces, with implementations in `androidMain` / `iosMain`.

```
DiceFive/
├── shared/                          KMP library (com.android.kotlin.multiplatform.library)
│   └── src/
│       ├── commonMain/kotlin/net/zodac/dicefive/
│       │   ├── model/  game/        moved as-is
│       │   ├── data/                Room KMP entities/DAO, DataStore repos, JSON
│       │   ├── platform/            interfaces: SoundPlayer, Haptics, ShakeSensor, AppContainer
│       │   └── navigation/  ui/     Compose Multiplatform
│       ├── commonMain/composeResources/   drawable/ font/ files/  (R.* → Res.*)
│       ├── commonTest/              nearly all current unit tests - run on JVM and iOS
│       ├── androidMain/             SoundPool, Vibrator, SensorManager, DB/DataStore paths, LicenceDocument
│       ├── androidUnitTest/         Robolectric tests (LicensesDialogTest, ScoreRepositoryTest)
│       └── iosMain/                 AVAudioPlayer, UIImpactFeedbackGenerator, CoreMotion, UITextView
├── androidApp/                      com.android.application: MainActivity, manifest, signing, icon, audio task
└── iosApp/                          Xcode project; ~20 lines of Swift hosting the shared UI
```

**Why a separate `androidApp` module is mandatory, not tidiness:** AGP 9 (which this project is
on) no longer allows the Kotlin Multiplatform plugin in a `com.android.application` module. The
shared code must be a KMP library using `com.android.kotlin.multiplatform.library`, and the APK
comes from a thin app module that depends on it.

**Swift is barely needed.** Kotlin in `iosMain` calls Apple frameworks (AVFoundation, CoreMotion,
UIKit, Foundation) directly through Kotlin/Native's Objective-C interop. The only Swift required
is the `iosApp` entry point that hosts the shared Compose UI (`ComposeUIViewController`). Reach
for Swift only if a needed Apple API turns out to be Swift-only.

---

## Phases

Ordered so the app stays a normal, shippable **Android-only** app through Phase 4. iOS work is
purely additive at the end and never blocks an Android release. Phases 1-2 are worth doing even
if the iOS port never happens: cleaner seams, and the logic tests gain a second platform.

### Phase 1 - De-Androidify in place

Still one module, still Android-only. Each step is independently shippable and covered by the
existing tests.

- `org.json` → `kotlinx.serialization`. Use its `JsonObject` tree API so `GameStateJson` maps
  line-for-line and **keeps the same keys** - a saved in-progress game from the previous version
  must still load. Same for `LicensesDialog`'s JSON parsing.
- `java.time` / `NumberFormat` → `kotlinx-datetime` plus a small formatting helper.
- `System.currentTimeMillis()` → `kotlin.time.Clock` (stdlib).
- Put sound, haptics and shake behind interfaces in `platform/`. Move `ShakeDetector`'s
  beat-detection algorithm into common code, so each platform only feeds it accelerometer
  samples `(x, y, z, timestamp)` and both platforms share the tuning.
- Replace the `factory(context)` methods with a small hand-rolled `AppContainer` that owns the
  repositories and platform services. No DI framework is needed at this size.
- `Toast` → an in-app snackbar, or a transient message through the existing
  `AchievementBannerHost`-style overlay. iOS has no toast.

### Phase 2 - Module split (Android target only)

- Create `:shared` (KMP library, Android target only for now) and `:androidApp`.
- Move `model/` and `game/` into `commonMain`.
- Move the pure unit tests to `commonTest`, swapping `org.junit` assertions for `kotlin.test`.
  **Keep the name `AchievementEngineTest`** - CLAUDE.md's banned-word exception refers to the
  guard assertion in it by that name.
- `IrishEasterEgg`'s `Normalizer` becomes an `expect fun` with `actual`s: `java.text.Normalizer`
  on Android, `NSString.decomposedStringWithCanonicalMapping` on iOS.
- Move the custom Gradle tasks (`NormalizeOggAudioTask`, `VerifyLicenseReportTask`,
  `CollectThirdPartyNoticesTask`, `VerifyAssetSourcesTask`) and signing/versioning logic into
  whichever module now owns the inputs they read.

### Phase 3 - Persistence

The riskiest phase, because it touches existing players' data.

- **Room KMP** (Room 2.7+): `@ConstructedBy` with an `expect object` constructor, the bundled
  SQLite driver, KSP configured per target (`kspAndroid`, `kspIosArm64`,
  `kspIosSimulatorArm64`), and the five migrations (`MIGRATION_1_2` … `MIGRATION_5_6`) rewritten
  against `SQLiteConnection` instead of `SupportSQLiteDatabase`. DAO functions are already
  `suspend`, which KMP Room requires. Good time to switch on `exportSchema`.
- **DataStore KMP**: `PreferenceDataStoreFactory.createWithPath`, with the path supplied per
  platform.
- **On Android, keep the exact same database name and DataStore file paths**
  (`filesDir/datastore/<name>.preferences_pb`), so scores, statistics, achievements, settings
  and in-progress games survive the upgrade. Add a test that opens a version-6 database and
  migrates it.

### Phase 4 - UI into `commonMain`

- Swap `androidx.compose` BOM artifacts for the `org.jetbrains.compose` equivalents; use the
  multiplatform Navigation Compose, lifecycle ViewModel and lifecycle-compose artifacts.
- Resources to `composeResources`: vectors to `drawable/`, Sora to `font/`, the aboutlibraries
  JSON and third-party notices to `files/`. `R.*` → the generated `Res.*`. Update the licence and
  asset-source verification tasks for the new paths (see `PUBLISHING.md` and `DESIGN.md`
  Phase 17).
- `BackHandler` → Compose Multiplatform's back-handling API.
- `LicenceDocument` → an `expect` composable (Android keeps today's `TextView`; iOS wraps a
  `UITextView` via `UIKitView`), **or** rewrite it once in pure Compose if `SelectionContainer` +
  link annotations now cover its needs. The choice is still open - see below.
- Material icons: keep using the frozen JetBrains `material-icons-extended` artifact, or copy
  the 98 used icons in as vector resources. Neither should block the migration.
- Recheck `UI.md`'s rules as things move - nothing in them should need to change, but the file
  paths it cites will.
- End state: `androidApp` is essentially `MainActivity` plus manifest, icon and signing.

### Phase 5 - iOS target

Needs a Mac with Xcode and a paid Apple developer account.

- Add `iosArm64` and `iosSimulatorArm64` targets; write the `iosMain` actuals (sound, haptics,
  shake, storage paths, licence view, `Normalizer`); create the `iosApp` Xcode project.
- **Audio format**: as far as is known, iOS doesn't decode Ogg Vorbis through AVFoundation.
  Extend `NormalizeOggAudioTask` (it already runs ffmpeg) to also emit `.m4a` / `.caf` copies
  for iOS, so `rawAudioSource/*.ogg` stays the single source.
- **No system back button on iOS**: every flow that currently relies on back-press (e.g.
  quitting from `GameScreen`, leaving `ScorecardReviewScreen`) needs a visible way out.
- Re-verify `UI.md`'s inset rules against iOS safe areas, the notch/Dynamic Island and the home
  indicator.
- Haptics: `UIImpactFeedbackGenerator` / `UINotificationFeedbackGenerator`. Shake:
  `CMMotionManager` accelerometer feeding the common beat detector from Phase 1.

### Phase 6 - CI and release

- iOS builds, tests and framework linking need a **macOS** runner. The Linux sandbox can still
  build and test Android and the common code, so the CLAUDE.md `assembleDebug` → APK flow
  keeps working unchanged.
- Extend `.github/scripts/update_dependency_versions.sh` for the new KMP/CMP/Room/DataStore
  pins, and `release.yml` for an iOS artifact if one is wanted (TestFlight / App Store Connect).
- Carry over the iOS items `PUBLISHING.md` already lists: the licences dialog,
  `asset-sources.json` and the copyleft guard.
- Commit categories: the restructure fits `[Project]`; iOS-only work may warrant a new `[iOS]`
  category (add it to CLAUDE.md's table in the same commit, per the working agreement).

---

## Open decisions

- **Mac access** - a local Mac, or macOS CI runners only? Without one, Phase 5 can't be tested.
- **Licences page** - one pure-Compose implementation for both platforms (less code, may lose
  some of today's text-selection behaviour) or a native text view per platform?
- **How far to go before iOS is committed to** - Phases 1-2 pay for themselves regardless;
  Phases 3-4 are the real investment.
