# Reference docs

- `.claude/DESIGN.md` — the build plan, game rules, and phase-by-phase log.
- `.claude/UI.md` — how the interface is put together: the Material 3 colour system and how to
  regenerate it, the shared chrome in `ui/common/`, inset and layout rules, component
  conventions, the player-name cap and why it exists, and the gotchas worth not re-learning.
  Read this before changing anything visual.

# Working agreements

- **Never use the word "Yahtzee" in this application.** It is a trademark, and it is banned from
  source code, comments, identifiers, filenames, resources and anything a player can see. The
  established term for five matching dice is **"5x"** in user-facing text (that is what the
  scorecard tile has always shown) and **`FIVE_OF_A_KIND` / `fiveOfAKind`** in code. Where the
  game's rules need naming generically, say "the rules" or "a five-dice scorecard game".

  The only permitted exception is the `.claude/*.md` reference documentation, which needs the
  word to explain what the game is. `README.md` is public and is **not** an exception.

- After implementing a code change (a fix, feature, or refactor the user asked for), build a
  debug APK with `./gradlew assembleDebug` and send the resulting `.apk` from
  `app/build/outputs/apk/debug/` to the user via SendUserFile so they can install/download it.
  Do this once the change is verified (compiles, relevant tests pass) rather than after every
  intermediate edit.
