---
name: commit
description: Make a git commit in this repo - the required "[Category] Short description" subject, the category list, attribution and what never to commit. Use for every commit.
---

# Committing

1. Check `git config user.name`/`user.email` match the maintainer's identity before the first commit of a session.
2. Stage only what the task changed. Never stage `RELEASE_NOTES.md` unless the maintainer agreed to its change in
   this conversation. Never commit scratch tests, debug colours (`grep` for `Color.Green`/`Magenta` after art work), or
   anything under `assets/` (except `assets/full-description.txt`).
3. Subject: `[Category] Short description`, enforced by `.githooks/commit-msg` (enable per clone with
   `scripts/install-git-hooks.sh`). Later lines are free-form; the attribution trailers go at the very end.
4. Never push unless asked.

## Categories

The category is what `scripts/release-changelog.sh` groups a release's changes under. Reuse one where it fits rather
than inventing a near-duplicate. If none fits, pick a short, specific new one, **add it to this table in the same
commit**, and tell the user so they can rename it before it spreads.

| Category         | Covers                                                                  |
|------------------|-------------------------------------------------------------------------|
| `[Achievements]` | Achievement rules, the achievements list and unlock banners             |
| `[AI]`           | Claude's set-up - CLAUDE.md, skills, hooks, reference docs, the sandbox |
| `[CI]`           | GitHub workflows, release pipeline, git hooks and changelog scripts     |
| `[Dependencies]` | Version bumps - libraries, SDK, JDK, sandbox pins, actions              |
| `[Game Mode]`    | Game modes - adding or changing one, with its rules and achievements    |
| `[Gameplay]`     | The in-game screen - dice, cup, scorecard, player tabs - and rules      |
| `[Game Over]`    | The end-of-game results page and its celebration                        |
| `[Icon]`         | The app's launcher icon                                                 |
| `[Leaderboard]`  | The Leaderboard (scores) screen and the score records behind it         |
| `[Performance]`  | Start-up, loading, storage and frame cost across the app                |
| `[Play Games]`   | Google Play Games Services - sign-in, online sync, cloud save, Sidekick |
| `[Project]`      | Repo-wide setup that isn't any one feature (tooling, docs, skills)      |
| `[Purchases]`    | The "Unlock all styles" purchase - Play Billing and the entitlement     |
| `[Sandbox]`      | The Docker sandbox and its emulator tooling (`sandbox/`)                |
| `[Settings]`     | The Settings screen and the preferences behind it                       |
| `[Statistics]`   | The Statistics screen - per-player cards, Overview and By mode          |
| `[Styles]`       | The Styles screen and the table art - dice, cups, mats, backgrounds     |
| `[UI]`           | App-wide layout and navigation - main menu, shared chrome, dialogs      |

A change that applies to every mode goes under its own area (`[Gameplay]`, `[Achievements]`), not `[Game Mode]`.
