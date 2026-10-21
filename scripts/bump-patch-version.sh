#!/usr/bin/env bash
# Increments just the patch component of one app's version (e.g.
# 0.1.0 -> 0.1.1) via set-version.sh. Run by that app's release workflow after
# every successful build, so development always continues one patch
# ahead of the last released version.
#
# For a minor/major bump (e.g. starting the 0.2.0 line), edit app/<platform>/VERSION
# by hand instead, or run set-version.sh directly - this script only ever
# increments patch.
#
# Usage: scripts/bump-patch-version.sh <android|ios>

set -euo pipefail

if [ $# -ne 1 ] || [[ "$1" != android && "$1" != ios ]]; then
  echo "Usage: $0 <android|ios>" >&2
  exit 1
fi

platform="$1"
root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
current="$(tr -d '[:space:]' < "$root_dir/app/$platform/VERSION")"

IFS='.' read -r major minor patch <<< "$current"
next="$major.$minor.$((patch + 1))"

"$root_dir/scripts/set-version.sh" "$platform" "$next"
