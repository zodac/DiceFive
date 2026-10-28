#!/usr/bin/env bash
# Sets the app's version, given an exact major.minor.patch value. The VERSION file at the repo root is read
# directly by the build (build-logic's AppVersion), so today this just writes that file - but it's the one
# designated place to add any future doc/reference sync, so callers (bump-patch-version.sh, CI, you) never need to
# know the details.
#
# Usage: scripts/set-version.sh <major.minor.patch>

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <major.minor.patch>" >&2
  exit 1
fi

version="$1"
if ! [[ "${version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Version must be major.minor.patch (e.g. 1.2.3), got: ${version}" >&2
  exit 1
fi

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
echo "${version}" > "${root_dir}/VERSION"
echo "Version set to ${version}"
