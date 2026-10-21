# DiceFive

## The project

A local (no netplay) five-dice scorecard game for Android, with iOS to follow. Its tagline: "Roll five dice, fill your
scorecard, and outscore friends or AI. Fully offline, unless you sign in to Google Play Games."

- **Players.** 1-4 per game: solo, pass-and-play, or CPU opponents (Easy/Medium/Hard).
- **Content.** Seven game modes, optional house-rule modifiers, a Leaderboard, per-player Statistics, 100+
  achievements (some hidden, some Easter eggs) and 90+ unlockable table styles.
- **Aims.**
  - A polished, offline-first game for the Play Store: no account, no network, data stays on the device.
  - Accessible: TalkBack and large fonts are first-class.
  - Smooth: 60fps minimum unless the player picks a lower Animations level.
  - Small APK.
  - Shared code, ready for an iOS app.
- **Google Play Games Services** (`PLAY_GAMES.md`) is built on Android: sign-in, achievements, leaderboards, cloud save
  and Sidekick. Its Play Console release remains.
- **Planned, not built.** The iOS app (`IOS_SUPPORT.md`), an "Unlock all styles" purchase on Android, the app's only
  purchase (`PURCHASES.md`), and possibly ads (`PUBLISHING.md`).
- **Audience.** `README.md` is the public, player-facing description; keep it true to the app.

## Repo metadata

| | |
|---|---|
| Repo | `github.com/zodac/DiceFive`, default branch `main`. Every push to `main` that changes what an app ships cuts a GitHub release for that app (`v*` Android, `ios-v*` iOS; `DESIGN.md`, "Release pipeline") |
| Package | `net.zodac.dicefive` (Android `applicationId`, Kotlin root package) |
| Version | One per app: `app/android/VERSION` (released from 1.0.0) and `app/ios/VERSION` (pre-releases below 1.0.0). Never hand-edit for a debug install |
| Platforms | Android minSdk 26, targetSdk/compileSdk 37 (`app/android/build.gradle.kts`); iOS compiles but no app yet |
| Stack | Kotlin Multiplatform, Compose Multiplatform, Material 3 (dark only), Navigation Compose, MVVM (`ViewModel` + `StateFlow`), Room (scores), DataStore (everything else) |
| Versions | `gradle/libs.versions.toml`, bumped monthly by CI (an entry tied to another is marked `# @update`); sandbox pins in `sandbox/Dockerfile` |
| Languages | English (`en-GB` base, British spelling), Spanish, Arabic (RTL) - all player text in Compose resources (`I18N.md`) |
| Licence | Proprietary, all rights reserved (`LICENSE`), source public read-only; no outside contributions. Third-party parts keep their own licences (the Sora font stays OFL) |
| Sandbox | Claude runs in the Docker sandbox (`sandbox/README.md`); an emulator boots on demand |

## Code standards

- **Layout.**
  - `app/shared` holds the game, UI and persistence. It must compile for iOS: no Android or JVM APIs, resources
    through `Res.*`, devices through `platform/PlatformServices`.
  - `app/android` is the APK shell and Android's platform code (`device/`).
  - `app/baselineprofile` is the profile journey; `app/licensing` holds the licence records.
  - The package map is in `README.md`.
- **Warnings are errors** throughout: Kotlin `allWarningsAsErrors`, lint `warningsAsErrors`, deprecations included.
- **Pure core.** The game logic (`model/`, `game/`) is pure and unit-tested. Every per-mode rule is a `GameMode`
  field, never `if (mode == X)`.
- **Behaviour comes with tests.** A change comes with tests of its behaviour, and spoken text is asserted word for
  word.
- **Dependencies.** Keep them lean (APK size matters; `kotlinx.serialization` and `kotlinx-datetime` were dropped for
  it). Any new library must pass the build's licence guard.
- **Style.**
  - Match the surrounding code's comment density and idiom.
  - Doc comments state rules, not guessed rationale.
  - British spelling in player text.

## Rules that always apply

- **Banned words.** The trademark in `StringChecks.TRADEMARK` (the original five-dice game's name) never appears
  anywhere: source, comments, identifiers, filenames, resources, docs (this file included), anything a player sees.
  - Five matching dice are **"5x"** to players and `FIVE_OF_A_KIND`/`fiveOfAKind` in code. Say "the rules" or "a
    five-dice scorecard game" generically.
  - Players (the app and the store listing) also never see "three/four/five of a kind", spaced or hyphenated. Use
    **3x/4x/5x**.
  - Only exception: the one guard pattern in `StringChecks`
    (`app/shared/src/androidHostTest/…/i18n/`).
- **Fix every build warning and error**: compiler, lint, tests, Gradle. That includes ones that were there before you
  started.
  - If one truly shouldn't be fixed (a deliberate choice, a false positive), suppress it at the narrowest scope with a
    comment saying why, and say so in the report.
  - If the fix is large or changes behaviour beyond the task, ask rather than skip.
- **Accessibility is part of done.** Every new or changed UI element goes through the checklist in `.claude/UI.md`.
  The report says what TalkBack does with it, and what wasn't heard on a device.
- **Achievement titles fit ONE line of the unlock banner at 11sp or larger.**
  - English titles are at most `MAX_ACHIEVEMENT_TITLE_LENGTH` (38) characters (`AchievementTextTest`). A translation
    may set its own `achievement_title_max_length`.
  - Shorten a title that doesn't fit; never wrap or shrink it. A description gets exactly two lines.
- **Stored ids are permanent once released**: achievement, style, game mode and preference key ids, and
  `onlineLeaderboardKey`. Rename what players see, never the id.
- **`RELEASE_NOTES.md` belongs to the maintainer.** Never create, edit, empty or tidy it without their explicit
  agreement in this conversation. Once they agree, use Edit/Write, not the shell.
  - `.claude/hooks/guard-release-notes.sh` backs this up: an edit waits for a permission prompt, and a shell change is
    undone. The prompt is a backstop, not permission.
  - Only the release workflow empties it. Hook tests: `.claude/hooks/tests/run-hook-tests.sh`.
  - It has `## Shared`, `## Android` and `## iOS` sections; each platform's release uses Shared plus its own
    (`DESIGN.md`, "Release pipeline").
- **Commits**: the subject is `[Category] Short description` (`.githooks/commit-msg`). Use the `commit` skill; the
  category list lives there.
- **After a requested code change is verified** (it compiles and the relevant tests pass), build and send the debug
  APK with the `debug-apk` skill.
  - Visual work goes through the `render` skill first. The APK follows once the look is agreed.

## Skills (procedures, loaded when invoked)

| Skill | For |
|---|---|
| `test` | Running tests, lint and the full gate; one class; reading failures; test traps |
| `debug-apk` | Building and sending the debug APK |
| `commit` | Commit format, category list, what never to commit |
| `render` | PNG renders of screens and art via Robolectric, animation sheets, close-ups, gap finding |
| `benchmark` | Measuring frame cost without a device |
| `emulator` | Sandbox emulator: seeing the real app, the Baseline Profile journey |
| `store-assets` | Generating and reviewing the Play Store graphics and video |
| `ios-build` | The iOS app's CI results: the IPA to sideload (`ios-v*` releases), simulator screenshots, iOS test failures |

## Reference docs (read the one for an area before changing it)

| Doc | Read when |
|---|---|
| `.claude/DESIGN.md` | Rules of play, modes and modifiers in brief, the CPU, persistence, Leaderboard/Statistics, achievements, table art, sound, settings, release pipeline |
| `.claude/UI.md` | Any visual change: colour, the brand font, shared chrome, insets, component rules, **the accessibility checklist**, motion and reduced motion, scrollbars, player colours, gotchas |
| `.claude/UI_BOARD.md` | The game screen: scorecard grid per mode, tiles, tray, cup, totals and undo, tablets and landscape, name caps |
| `.claude/UI_SCREENS.md` | Achievements list and banners, Styles screen and locks, Leaderboard views, menu logo and backdrop |
| `.claude/GAME_MODES.md` | Adding or changing a game mode or modifier: the mode-is-data rule, field map, checklist, turn flow, tests |
| `.claude/GAME_MODES_AUDIT.md` | Which achievements each mode or modifier blocks or guards, and how to audit a new one |
| `.claude/TUTORIAL.md` | The guided tutorial: its script, steps, coach card, gates and tests, and how to add one for another mode |
| `.claude/I18N.md` | Adding or moving any player-visible or spoken string, or a formatter or platform text API |
| `.claude/I18N_LANGUAGES.md` | Translating: shipped languages, glossaries, adding a language |
| `.claude/STYLE_ART.md` | Creating or reworking dice, cups, mats, backgrounds or frames |
| `.claude/BENCHMARKS.md` | Performance: patterns that keep frames cheap, current costs, open items |
| `.claude/EMULATOR.md` | Emulator internals, CI's twin, troubleshooting a dying emulator, versions |
| `.claude/STORE_ASSETS.md` | Changing store screenshots, scenes, captions or the video, and the generator traps |
| `.claude/PUBLISHING.md` | Play Store submission checklist, the app's licence and third-party licence scope |
| `.claude/PLAY_GAMES.md` | Anything touching Google Play Games Services: sign-in, achievement mirror, leaderboards, cloud save, Sidekick |
| `.claude/PURCHASES.md` | The planned "Unlock all styles" purchase: Play Billing, the entitlement, the Styles button, the maintainer's Play Console steps, in phases |
| `.claude/IOS_SUPPORT.md` | Module layout and platform seams, KMP gotchas, the phased iOS roadmap (Mac-less: CI builds, sideloaded) |

## Pending

- **The iOS app.** Follow the roadmap in `.claude/IOS_SUPPORT.md`; the phase marked **Next** is where to resume.
