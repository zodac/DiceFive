#!/usr/bin/env bash
# Tests for scripts/release-notes.sh. Run from anywhere: scripts/tests/release-notes-test.sh
# Each command substitution is a check's subject: a command that fails gives the wrong output, which fails the check.
# shellcheck disable=SC2312
set -euo pipefail

script="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/release-notes.sh"
work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT
export RELEASE_NOTES_FILE="${work}/RELEASE_NOTES.md"
failures=0

check() {
    local name="$1" expected="$2" actual="$3"
    if [[ "${expected}" == "${actual}" ]]; then
        echo "ok   ${name}"
    else
        echo "FAIL ${name}"
        diff <(printf '%s\n' "${expected}") <(printf '%s\n' "${actual}") | sed 's/^/     /' || true
        failures=$((failures + 1))
    fi
}

notes='

## Gameplay

- New mode

### Fixes
- Back gesture on the scorecard
'

printf '%s\n' "${notes}" > "${RELEASE_NOTES_FILE}"
check "extract is the whole file, headings included, without the blank lines around it" '## Gameplay

- New mode

### Fixes
- Back gesture on the scorecard' "$("${script}" extract)"

cp "${RELEASE_NOTES_FILE}" "${work}/released.md"
"${script}" clear "${work}/released.md"
check "clear empties the file" '' "$(cat "${RELEASE_NOTES_FILE}")"
check "extract after clear has no notes" '' "$("${script}" extract)"

printf '%s\n' "${notes}" > "${RELEASE_NOTES_FILE}"
cp "${RELEASE_NOTES_FILE}" "${work}/released.md"
printf '%s\n- Written during the release\n' "${notes}" > "${RELEASE_NOTES_FILE}"
"${script}" clear "${work}/released.md" 2> /dev/null
check "clear keeps a file changed during the release" "$(printf '%s\n- Written during the release\n' "${notes}")" \
    "$(cat "${RELEASE_NOTES_FILE}")"

: > "${RELEASE_NOTES_FILE}"
check "an empty file has no notes" '' "$("${script}" extract)"

rm -f "${RELEASE_NOTES_FILE}"
check "a missing file has no notes" '' "$("${script}" extract)"

check "an unknown command is refused" 2 "$("${script}" publish 2> /dev/null; echo $?)"

if [[ "${failures}" -gt 0 ]]; then
    echo "${failures} failed"
    exit 1
fi
echo "All passed"
