#!/usr/bin/env bash
# Sets one app's version, given an exact major.minor.patch value. Each app has its own VERSION file -
# app/android/VERSION and app/ios/VERSION - read directly by its build (app/android/build.gradle.kts, and the
# iOS workflows for Xcode), so today this just writes that file - but it's the one designated place to add any
# future doc/reference sync, so callers (bump-patch-version.sh, CI, you) never need to know the details.
#
# Usage: scripts/set-version.sh <android|ios> <major.minor.patch>

set -euo pipefail

if [ $# -ne 2 ] || [[ "$1" != android && "$1" != ios ]]; then
  echo "Usage: $0 <android|ios> <major.minor.patch>" >&2
  exit 1
fi

platform="$1"
version="$2"
if ! [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Version must be major.minor.patch (e.g. 1.2.3), got: $version" >&2
  exit 1
fi

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
echo "$version" > "$root_dir/app/$platform/VERSION"
echo "$platform version set to $version"
