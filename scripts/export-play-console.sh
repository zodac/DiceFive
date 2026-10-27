#!/usr/bin/env bash
# Writes what Play Console needs to set up the achievements and leaderboards to assets/play-console/ at the repo root,
# which git ignores:
# - achievements/achievements-import.zip: for Achievements > Import achievements (metadata, translations, icon
#   mappings, icons);
# - achievements/achievements.csv and achievements/achievement-icons/: the same achievements as one sheet with a
#   header, and their 512px icons, to review;
# - leaderboards/leaderboards.csv and leaderboards/leaderboard-icons/: one leaderboard per game mode, made by hand
#   (Play has no import for them), and their 512px icons.
# - product-icons/: the one-time product's 512px icon, for Monetise with Play > Products (.claude/PURCHASES.md).
# See .claude/PLAY_GAMES.md, "Play Console".
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root_dir"

./gradlew -q :app:android:testPlayDebugUnitTest --tests '*PlayConsoleExportTest' --tests '*PlayConsoleLeaderboardsExportTest' \
    --tests '*PlayConsoleProductExportTest' -PexportPlayConsole

echo "Written to $root_dir/assets/play-console/"
