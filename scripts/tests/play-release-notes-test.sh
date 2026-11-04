#!/usr/bin/env bash
# Tests for scripts/play-release-notes.sh. Run from anywhere: scripts/tests/play-release-notes-test.sh
# Each command substitution is a check's subject: a command that fails gives the wrong output, which fails the check.
# shellcheck disable=SC2312
set -euo pipefail

script="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/play-release-notes.sh"
url="https://github.com/zodac/DiceFive/releases/tag/android-v1.4.1"
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

body='A summary paragraph, which is not a bullet.

- A maintainer bullet

## Changes since 1.4.0

### Achievements
- [[e840c835]](https://github.com/zodac/DiceFive/commit/e840c835) Easter Eggs count
- [[9bd81417]](https://github.com/zodac/DiceFive/commit/9bd81417) Well Rolled takes 50,000 dice

### UI
- [[602e819c]](https://github.com/zodac/DiceFive/commit/602e819c) Arrows glide'

check "the link, then every bullet in order, without commit links, when all fit" "${url}

• A maintainer bullet
• Easter Eggs count
• Well Rolled takes 50,000 dice
• Arrows glide" "$("${script}" "${url}" <<< "${body}")"

# The full text is 61 + 21 + 19 + 31 + 14 = 146 characters, and 5 newlines (the blank line included): 151.
check "exactly at the limit, every bullet fits" "${url}

• A maintainer bullet
• Easter Eggs count
• Well Rolled takes 50,000 dice
• Arrows glide" "$(PLAY_NOTES_LIMIT=151 "${script}" "${url}" <<< "${body}")"

# One short: the last bullet doesn't fit, so it becomes "..." - which still fits where it was.
check "a last bullet that doesn't fit becomes ..." "${url}

• A maintainer bullet
• Easter Eggs count
• Well Rolled takes 50,000 dice
..." "$(PLAY_NOTES_LIMIT=150 "${script}" "${url}" <<< "${body}")"

# 61 + 21 + 19 and 3 newlines = 104 for two bullets, and 4 more for the "..." line: a bullet is kept only with room for that after it.
check "a bullet is dropped rather than leave no room for ..." "${url}

• A maintainer bullet
• Easter Eggs count
..." "$(PLAY_NOTES_LIMIT=108 "${script}" "${url}" <<< "${body}")"
check "one character short of that, the second bullet goes too" "${url}

• A maintainer bullet
..." "$(PLAY_NOTES_LIMIT=107 "${script}" "${url}" <<< "${body}")"

check "a later shorter bullet isn't pulled forward past one that didn't fit" "${url}

..." "$(PLAY_NOTES_LIMIT=70 "${script}" "${url}" <<< "- A bullet far too long to fit in what is left
- Short")"

check "no bullets, just the link" "${url}" "$("${script}" "${url}" <<< "Only prose here.")"

check "characters are counted, not bytes" "${url}

• ééé" "$(PLAY_NOTES_LIMIT=68 "${script}" "${url}" <<< "- ééé")"

check "CRLF line endings are read like LF" "${url}

• One
• Two" "$("${script}" "${url}" <<< $'- One\r\n- Two\r')"

# A command substitution strips trailing newlines, so the "x" keeps one visible. Play counts it towards the limit.
check "the text ends without a newline" "${url}x" "$("${script}" "${url}" <<< "Only prose here."; printf 'x')"
check "text exactly at the limit ends without a newline" "${url}

• A maintainer bullet
• Easter Eggs count
• Well Rolled takes 50,000 dice
• Arrows glidex" "$(PLAY_NOTES_LIMIT=151 "${script}" "${url}" <<< "${body}"; printf 'x')"

check "a URL longer than the limit fails" "failed" "$(PLAY_NOTES_LIMIT=10 "${script}" "${url}" <<< "- x" 2>/dev/null || echo failed)"

if (( failures > 0 )); then
    echo "${failures} check(s) failed"
    exit 1
fi
echo "All checks passed"
