#!/usr/bin/env bash
# Tests for .github/scripts/stamp_privacy_date.sh. Run from anywhere: scripts/tests/stamp-privacy-date-test.sh
# Each command substitution is a check's subject: a command that fails gives the wrong output, which fails the check.
# shellcheck disable=SC2312
set -euo pipefail

script="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/.github/scripts/stamp_privacy_date.sh"
failures=0

check() {
    local name="$1" expected="$2" actual="$3"
    if [[ "${expected}" == "${actual}" ]]; then
        echo "ok   ${name}"
    else
        echo "FAIL ${name}: expected '${expected}', got '${actual}'"
        failures=$((failures + 1))
    fi
}

work="$(mktemp -d)"
trap 'rm -r "${work}"' EXIT
cd "${work}"
git init -q
git config user.name test
git config user.email test@example.com

printf 'Intro\n\n**Last updated:** set when published\n\nBody\n' > policy.md
printf 'Index\n' > index.md
git add .
GIT_COMMITTER_DATE="2026-03-04T12:00:00+13:00" git commit -qm first
first="$(git rev-parse HEAD)"
printf 'New wording\n' >> policy.md
git add .
GIT_COMMITTER_DATE="2026-05-06T12:00:00+12:00" git commit -qm "policy changed"
policy_changed="$(git rev-parse HEAD)"
printf 'More\n' >> index.md
git add .
GIT_COMMITTER_DATE="2026-07-08T12:00:00+12:00" git commit -qm "index changed"

printf '<p><strong>Last updated:</strong> 9 October 2026</p>\n' > live.html
live="file://${work}/live.html"
missing="file://${work}/missing.html"

# Runs the script, and gives the date line it leaves, or "failed". The policy is put back after.
stamped() {
    local line=""
    if STAMP_TODAY="${STAMP_TODAY:-1 January 2030}" "${script}" policy.md "$1" "$2" > /dev/null 2>&1; then
        line="$(grep '^\*\*Last updated' policy.md)"
    else
        line="failed"
    fi
    git checkout -q -- policy.md
    echo "${line}"
}

check "a push that changed the policy stamps today" "**Last updated:** 1 January 2030" "$(stamped "${first}" "${live}")"
check "a push that changed only another page keeps the live date" "**Last updated:** 9 October 2026" "$(stamped "${policy_changed}" "${live}")"
check "a manual run keeps the live date" "**Last updated:** 9 October 2026" "$(stamped "" "${live}")"
check "a new branch's all-zero before keeps the live date" "**Last updated:** 9 October 2026" "$(stamped "0000000000000000000000000000000000000000" "${live}")"
check "a before commit that isn't fetched keeps the live date" "**Last updated:** 9 October 2026" "$(stamped "1234567890123456789012345678901234567890" "${live}")"
check "no live page falls back to the policy's last commit, not another page's" "**Last updated:** 6 May 2026" "$(stamped "" "${missing}")"

printf '<p>No date here</p>\n' > live.html
check "a live page with no date falls back too" "**Last updated:** 6 May 2026" "$(stamped "${policy_changed}" "${live}")"
printf '<p><strong>Last updated:</strong> set when published</p>\n' > live.html
check "a live date that isn't a date fails rather than publishing it" "failed" "$(stamped "" "${live}")"
printf '<p><strong>Last updated:</strong> 9 October 2026</p>\n' > live.html

sed -i '/Last updated/d' policy.md
git add policy.md
git commit -qm "no date line"
check "a policy with no date line fails" "failed" "$(stamped "" "${live}")"

if (( failures > 0 )); then
    echo "${failures} check(s) failed"
    exit 1
fi
echo "All checks passed"
