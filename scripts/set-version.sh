#!/usr/bin/env bash
# Sets the app version everywhere it's referenced, given an exact
# major.minor.patch value. app/android/build.gradle.kts already reads the root
# VERSION file directly at build time (there is no separate hardcoded
# Gradle version to keep in sync), so today this just writes that file -
# but it's the one designated place to add any future doc/reference sync,
# so callers (bump-patch-version.sh, CI, you) never need to know the details.
#
# Usage: scripts/set-version.sh <major.minor.patch>
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "Usage: $0 <major.minor.patch>" >&2
  exit 1
fi

version="$1"

if ! [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Version must be major.minor.patch (e.g. 1.2.3), got: $version" >&2
  exit 1
fi

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "$version" > "$root_dir/VERSION"

echo "Version set to $version"
