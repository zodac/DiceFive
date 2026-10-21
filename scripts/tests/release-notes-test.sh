#!/usr/bin/env bash
# Tests for scripts/release-notes.sh. Run from anywhere: scripts/tests/release-notes-test.sh
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

sectioned='## Shared

- New mode for both apps

## Android

### Fixes
- Back gesture on the scorecard

## iOS

- Sounds play on the silent switch'

printf '%s\n' "${sectioned}" > "${RELEASE_NOTES_FILE}"
check "extract android is Shared then Android" '- New mode for both apps

### Fixes
- Back gesture on the scorecard' "$("${script}" extract android)"
check "extract ios is Shared then iOS" '- New mode for both apps

- Sounds play on the silent switch' "$("${script}" extract ios)"

cp "${RELEASE_NOTES_FILE}" "${work}/released.md"
"${script}" clear android "${work}/released.md"
check "clear android empties Shared and Android, keeps iOS" '## Shared

## Android

## iOS

- Sounds play on the silent switch' "$(cat "${RELEASE_NOTES_FILE}")"
check "extract after clear has only the other platform's notes" '' "$("${script}" extract android)"

printf '%s\n' "${sectioned}" > "${RELEASE_NOTES_FILE}"
cp "${RELEASE_NOTES_FILE}" "${work}/released.md"
printf '%s\n' "${sectioned/- New mode for both apps/- New mode for both apps
- Written during the release}" > "${RELEASE_NOTES_FILE}"
"${script}" clear android "${work}/released.md" 2> /dev/null
check "clear keeps a section changed during the release" '## Shared

- New mode for both apps
- Written during the release

## Android

## iOS

- Sounds play on the silent switch' "$(cat "${RELEASE_NOTES_FILE}")"

printf '%s\n' 'Plain notes, no headings' > "${RELEASE_NOTES_FILE}"
check "text without headings is Shared" 'Plain notes, no headings' "$("${script}" extract ios)"

: > "${RELEASE_NOTES_FILE}"
check "an empty file has no notes" '' "$("${script}" extract android)"

rm -f "${RELEASE_NOTES_FILE}"
check "a missing file has no notes" '' "$("${script}" extract android)"

if [[ "${failures}" -gt 0 ]]; then
    echo "${failures} failed"
    exit 1
fi
echo "All passed"
