# Reference docs

- `.claude/DESIGN.md` — the build plan, game rules, and phase-by-phase log.
- `.claude/UI.md` — how the interface is put together: the Material 3 colour system and how to
  regenerate it, the shared chrome in `ui/common/`, inset and layout rules, component
  conventions, the mandatory accessibility checklist for any UI change, the player-name cap and why
  it exists, and the gotchas worth not re-learning.
  Read this before changing anything visual.
- `.claude/PUBLISHING.md` — Play Store submission requirements not covered by the build or by
  `DESIGN.md`'s GitHub release pipeline (Phase 12), such as the store listing's separate hi-res
  icon. Little in it is done yet (the in-app open-source licenses page is); it's a running checklist for when that submission happens. It
  also records the app's own license (proprietary, all rights reserved - see `LICENSE`), why it was
  chosen, and how it must stay scoped to exclude third-party parts (the bundled font must stay OFL).
- `.claude/GAME_MODES.md` — how game modes are built (every per-mode rule is a field on `GameMode`,
  never an `if (mode == X)`) and how to add one, including a mode unlike the existing ones: where
  each mode field is read, what to do for new categories, new dice, a changed turn flow (the one
  roll path, the AI loop, timers, undo) or an overridden setup option, the achievement audit (which
  existing achievements each mode blocks, and which needed a guard so the mode doesn't hand them out
  for free), and the test helpers and traps. Read this before adding or changing a mode.
- `.claude/IOS_SUPPORT.md` — the iOS port via Kotlin Multiplatform + Compose Multiplatform: the
  `app/shared` (platform-neutral) / `app/android` module layout, what lives where (and why
  Android's platform code is in `app/android`), gotchas (e.g. no commas or parentheses in `commonTest` names), and what's left.
  Phases 1-4 are done - the game, UI and persistence are shared and compile for iOS; the iOS app
  itself (Phase 5) needs macOS.

# Working agreements

- **Never use the word "Yahtzee" in this application.** It is a trademark, and it is banned from
  source code, comments, identifiers, filenames, resources and anything a player can see. The
  established term for five matching dice is **"5x"** in user-facing text (that is what the
  scorecard tile has always shown) and **`FIVE_OF_A_KIND` / `fiveOfAKind`** in code. Where the
  game's rules need naming generically, say "the rules" or "a five-dice scorecard game".

  The only permitted exceptions are `.claude/*.md` reference documentation, which needs the word
  to explain what the game is, and the single guard assertion in `AchievementEngineTest` that
  checks no achievement title, description or category label contains it. `README.md` is public
  and is **not** an exception.

- **Every new or changed UI element must be checked for accessibility before it's done** - what
  TalkBack says for it, whether visual-only meaning (colour, fades, position) has a spoken twin, and
  whether every action is reachable without a custom gesture. Stock components are not assumed to be
  enough. The checklist, and how to verify it, is in `.claude/UI.md`'s "Accessibility" section; the
  report back to the user says what TalkBack does with the change, and what wasn't heard on a device.

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
  | `[Settings]`     | The Settings screen and the preferences behind it                    |
  | `[Styles]`       | The Styles screen and the table art - dice, cups, mats, backgrounds  |
  | `[UI]`           | App-wide layout and navigation - main menu, shared chrome, dialogs   |

# Pending

(None currently)
