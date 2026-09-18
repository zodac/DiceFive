#!/usr/bin/env bash
# Increments just the patch component of the current version (e.g.
# 0.1.0 -> 0.1.1) via set-version.sh. Run by the release workflow after
# every successful build, so development always continues one patch
# ahead of the last released version.
#
# For a minor/major bump (e.g. starting the 0.1.0 line), edit VERSION by
# hand instead, or run set-version.sh directly - this script only ever
# increments patch.
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
current="$(tr -d '[:space:]' < "$root_dir/VERSION")"

IFS='.' read -r major minor patch <<< "$current"
next="$major.$minor.$((patch + 1))"

"$root_dir/scripts/set-version.sh" "$next"
