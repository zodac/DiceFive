# Reference docs

- `.claude/DESIGN.md` — the build plan, game rules, and phase-by-phase log.
- `.claude/UI.md` — how the interface is put together: the Material 3 colour system and how to
  regenerate it, the shared chrome in `ui/common/`, inset and layout rules, component
  conventions, the mandatory accessibility checklist for any UI change, the player-name cap and why
  it exists, and the gotchas worth not re-learning.
  Read this before changing anything visual.
- `.claude/PUBLISHING.md` — Play Store submission requirements not covered by the build or by
  `DESIGN.md`'s GitHub release pipeline (Phase 12), such as the store listing's separate hi-res
  icon. Little in it is done yet (the in-app open-source licenses page is, and the store descriptions are drafted); it's a running checklist for when that submission happens. It
  also records the app's own license (proprietary, all rights reserved - see `LICENSE`), why it was
  chosen, and how it must stay scoped to exclude third-party parts (the bundled font must stay OFL).
- `.claude/GAME_MODES.md` — how game modes are built (every per-mode rule is a field on `GameMode`,
  never an `if (mode == X)`) and how to add one, including a mode unlike the existing ones: where
  each mode field is read, what to do for new categories, new dice, a changed turn flow (the one
  roll path, the AI loop, timers, undo) or an overridden setup option, the achievement audit (which
  existing achievements each mode blocks, and which needed a guard so the mode doesn't hand them out
  for free), and the test helpers and traps. Read this before adding or changing a mode.
- `.claude/STYLE_ART.md` — how to make new table art (dice, cups, mats, backgrounds): working with the
  maintainer on art (render for review before any APK; they check close-ups and frame cost), rendering
  stills and animation sheets in Robolectric, the debug-colour trick for finding gaps, drawing
  techniques (3D projection for non-round cups, simulating a pile instead of placing it, making things
  visibly rest on something), painting heavy art once and off the frame, and the cup contract as a cup
  sees it. Read this before creating or reworking a style.
- `.claude/BENCHMARKS.md` — how animation frame cost is measured without a device (Robolectric
  harness, set-ups, pitfalls), the results for every dice, cup, mat and background style and the
  Styles page, what was fixed, and what's still open. Only needed for performance work: a
  reported stutter or slowdown, or new art that draws every frame.
- `.claude/EMULATOR.md` — the Android emulator that boots on demand inside the sandbox
  (`sandbox/emulator.sh start|stop|status|screenshot`): running and debugging the Baseline Profile
  journey (`app/baselineprofile`) without a phone, looking at the real app's screen, why it needs the
  Vulkan renderer (the default one segfaults the emulator while drawing the app), and what to do when
  it dies. Read this before using an emulator or touching the journey; it is not for benchmark timings.
- `.claude/IOS_SUPPORT.md` — the iOS port via Kotlin Multiplatform + Compose Multiplatform: the
  `app/shared` (platform-neutral) / `app/android` module layout, what lives where (and why
  Android's platform code is in `app/android`), gotchas (e.g. no commas or parentheses in `commonTest` names), and what's left.
  Phases 1-4 are done - the game, UI and persistence are shared and compile for iOS; the iOS app
  itself (Phase 5) needs macOS.
- `.claude/I18N.md` — the plan for moving every visible and spoken string into Compose resources
  (i18n) so a language can be added later (l10n): the decisions (plurals in `<plurals>` using
  Compose resources' bundled CLDR rules; ordinals, number grouping and lists through each
  platform's formatter), string conventions, the guards, and the step-by-step status. Read this
  before adding or moving any player-visible string.

# Working agreements

- **Never use the word "Yahtzee" in this application.** It is a trademark, and it is banned from
  source code, comments, identifiers, filenames, resources and anything a player can see. The
  established term for five matching dice is **"5x"** in user-facing text (that is what the
  scorecard tile has always shown) and **`FIVE_OF_A_KIND` / `fiveOfAKind`** in code. Where the
  game's rules need naming generically, say "the rules" or "a five-dice scorecard game".

  Likewise, **"three of a kind", "four of a kind" and "five of a kind"** (spaced or hyphenated)
  never appear in anything a player can see - in the app or in the store listing. Use **"3x"**,
  **"4x"** and **"5x"**, as the scorecard does. The code identifiers (`FIVE_OF_A_KIND` etc.) are
  fine.

  The only permitted exceptions are `.claude/*.md` reference documentation, which needs the word
  to explain what the game is, and the single guard pattern in `StringChecks` (`app/shared/src/androidHostTest/…/i18n/`) that
  checks no achievement title, description or category label contains it. `README.md` is public
  and is **not** an exception.

- **Build warnings and errors get dealt with, even when they were there before you started.** That
  covers the compiler, `lint`, the tests and the Gradle build. "It fails the same without my change"
  explains where a problem came from; it isn't a reason to leave it. Fix it, or - where it truly
  can't or shouldn't be fixed (a deliberate design choice, a false positive) - suppress it at the
  narrowest scope with a comment saying why, and say so in the report. Never just note it and move on.
  If the fix is large or changes behaviour beyond the task, tell the user and let them decide, rather
  than skipping it silently.

- **Every new or changed UI element must be checked for accessibility before it's done** - what
  TalkBack says for it, whether visual-only meaning (colour, fades, position) has a spoken twin, and
  whether every action is reachable without a custom gesture. Stock components are not assumed to be
  enough. The checklist, and how to verify it, is in `.claude/UI.md`'s "Accessibility" section; the
  report back to the user says what TalkBack does with the change, and what wasn't heard on a device.

- **An achievement's title must fit on ONE line of the unlock banner, at 12sp or larger - never
  wrapped or shrunk further.** That caps it at `MAX_ACHIEVEMENT_TITLE_LENGTH` (38) characters
  (`model/Achievement.kt`), which `AchievementTextTest` enforces. Every banner is the same size, so
  a title that doesn't fit is shortened, not accommodated. A description gets exactly two lines.

- After implementing a code change (a fix, feature, or refactor the user asked for), build a
  debug APK with `./gradlew assembleDebug` and send the resulting `.apk` from
  `app/android/build/outputs/apk/debug/` to the user via SendUserFile so they can install/download it.
  Do this once the change is verified (compiles, relevant tests pass) rather than after every
  intermediate edit.

- **`RELEASE_NOTES.md` is the maintainer's, not Claude's.** It holds their own summary of the next
  release, and becomes the top of the GitHub release description (above the grouped commit list -
  see `DESIGN.md` Phase 12). Never create, edit, empty or rewrite it without the user's explicit
  confirmation in the conversation - not even to "tidy" it, and not as a side effect of another
  change. A guard hook (`.claude/hooks/guard-release-notes.sh`, registered in
  `.claude/settings.json`) backs this up: a file edit aimed at it waits for a permission prompt, and
  a shell command that changes it is undone afterwards (merely mentioning the name is fine). The
  prompt is a backstop, not the permission. Once the user has agreed in the conversation, make the
  change with Edit/Write, not the shell. Only the release workflow empties it on its own, after a
  release ships. Tests: `.claude/hooks/tests/run-hook-tests.sh`.

- **Every commit's first line is `[Category] Short description`** - enforced by
  `.githooks/commit-msg` (enable per clone with `scripts/install-git-hooks.sh`). The category is
  what a release's changes are grouped under, so reuse one from the list below where it fits rather
  than inventing a near-duplicate; later lines are free-form. The attribution trailers still go at
  the end. When no category fits, pick a short, specific new one, **add it to this list in the same
  commit**, and mention it to the user so they can rename it before it spreads.

  | Category         | Covers                                                               |
  |------------------|----------------------------------------------------------------------|
  | `[Achievements]` | Achievement rules, the achievements list and unlock banners          |
  | `[CI]`           | GitHub workflows, release pipeline, git hooks and changelog scripts  |
  | `[Dependencies]` | Version bumps - libraries, SDK, JDK, sandbox pins, actions           |
  | `[Game Mode]`    | Game modes - adding or changing one, with its rules and achievements |
  | `[Gameplay]`     | The in-game screen - dice, cup, scorecard, player tabs - and rules   |
  | `[Game Over]`    | The end-of-game results page and its celebration                     |
  | `[Icon]`         | The app's launcher icon                                              |
  | `[Leaderboard]`  | The Leaderboard (scores) screen and the score records behind it      |
  | `[Project]`      | Repo-wide setup that isn't any one feature (initial commit, tooling) |
  | `[Sandbox]`      | The Docker sandbox and its emulator tooling (`sandbox/`)             |
  | `[Settings]`     | The Settings screen and the preferences behind it                    |
  | `[Styles]`       | The Styles screen and the table art - dice, cups, mats, backgrounds  |
  | `[UI]`           | App-wide layout and navigation - main menu, shared chrome, dialogs   |

# Pending

(None currently)
