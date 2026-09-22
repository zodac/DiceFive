# Reference docs

- `.claude/DESIGN.md` — the build plan, game rules, and phase-by-phase log.
- `.claude/UI.md` — how the interface is put together: the Material 3 colour system and how to
  regenerate it, the shared chrome in `ui/common/`, inset and layout rules, component
  conventions, the player-name cap and why it exists, and the gotchas worth not re-learning.
  Read this before changing anything visual.

# Working agreements

- After implementing a code change (a fix, feature, or refactor the user asked for), build a
  debug APK with `./gradlew assembleDebug` and send the resulting `.apk` from
  `app/build/outputs/apk/debug/` to the user via SendUserFile so they can install/download it.
  Do this once the change is verified (compiles, relevant tests pass) rather than after every
  intermediate edit.
