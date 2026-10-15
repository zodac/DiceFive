#!/usr/bin/env bash
# Writes what Play Console needs to set up the achievements to play-console/ at the repo root, which git ignores:
# - achievements-import.zip: for Achievements > Import achievements (metadata, translations, icon mappings, icons);
# - achievements.csv and icons/: the same achievements as one sheet with a header, and their 512px icons, to review.
# See .claude/PLAY_GAMES.md, section 2.
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root_dir"

./gradlew -q :app:android:testDebugUnitTest --tests '*PlayConsoleExportTest' -PexportPlayConsole

echo "Written to $root_dir/play-console/"
