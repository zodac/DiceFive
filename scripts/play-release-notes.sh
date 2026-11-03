#!/usr/bin/env bash
# Prints a Google Play release's "What's new" text from a GitHub release's description (on stdin):
#
#     https://github.com/zodac/DiceFive/releases/tag/android-v1.4.1
#
#     • Showing the unlocked/total count for the Easter Eggs category
#     • Well Rolled takes 50,000 dice
#     ...
#
# The first line is the GitHub release's URL, then a blank line, then every "- " bullet of the description, in order,
# without its commit link ("[[1f5dd9f2]](https://...) "), as many whole bullets as fit in Play's limit (500 characters
# per language). A bullet is never cut: the first that doesn't fit, and every bullet after it, become one "..." line,
# which is always left room for. Fails if the URL alone doesn't fit.
#
# Used by .github/workflows/play-release.yml. PLAY_NOTES_LIMIT overrides the limit (for tests).
#
# Usage: scripts/play-release-notes.sh <github-release-url> < release-body.md
set -euo pipefail
# Characters, not bytes: Play counts characters.
export LC_ALL=C.UTF-8

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <github-release-url> < release-body.md" >&2
    exit 2
fi
url="$1"
limit="${PLAY_NOTES_LIMIT:-500}"
more="..."

if (( ${#url} > limit )); then
    echo "The release URL alone is longer than ${limit} characters" >&2
    exit 1
fi

bullets=()
while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ "${line}" == "- "* ]] || continue
    text="${line#- }"
    # The commit link release-changelog.sh puts before each subject.
    if [[ "${text}" =~ ^\[\[[0-9a-f]+\]\]\([^\)]*\)\ +(.*)$ ]]; then
        text="${BASH_REMATCH[1]}"
    fi
    [[ -n "${text}" ]] && bullets+=("• ${text}")
done

notes="${url}"
count="${#bullets[@]}"
for ((i = 0; i < count; i++)); do
    # A blank line separates the URL from the first bullet (or from the "..." line, when no bullet fits).
    separator=$'\n'
    (( i == 0 )) && separator=$'\n\n'
    candidate="${notes}${separator}${bullets[i]}"
    # Room for the "..." line too, while bullets are left after this one.
    needed="${#candidate}"
    if (( i < count - 1 )); then
        needed=$((needed + 1 + ${#more}))
    fi
    if (( needed > limit )); then
        notes+="${separator}${more}"
        break
    fi
    notes="${candidate}"
done

printf '%s\n' "${notes}"
