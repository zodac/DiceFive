#!/bin/bash
# ------------------------------------------------------------------------------
# Script Name:  stamp_privacy_date.sh
#
# Description:  Sets the privacy policy's "Last updated" line just before pages.yml builds the site, as the source
#               holds no date of its own. The policy promises the date changes when the policy does, so it's today's
#               date only when the policy changed in the push being published (<before sha> to HEAD). Otherwise, as for
#               a change to another page or a manual re-publish, it keeps the date on the live page, or failing that
#               the date of the last commit to change the policy.
#
#               Today is New Zealand's, the maintainer's: a runner's UTC can be the day before.
#
# Usage:        .github/scripts/stamp_privacy_date.sh <policy file> <before sha, or empty> <live page URL>
#               (from the repository root; STAMP_TODAY sets today's date, for a test)
#
# Requirements: git, with the before commit fetched; curl; grep; sed; date
# ------------------------------------------------------------------------------
# A date read from the live page or git may come back empty, which falls through to the next source.
# shellcheck disable=SC2310,SC2312
set -euo pipefail

policy="${1:?usage: $0 <policy file> <before sha, or empty> <live page URL>}"
before="${2:-}"
live_url="${3:?usage: $0 <policy file> <before sha, or empty> <live page URL>}"

long_date() { LC_ALL=C TZ=Pacific/Auckland date "$@" +'%-d %B %Y'; }

# Whether the policy changed in this push. A push with no before commit (a new branch, or a manual run) can't say.
changed_in_push() {
    [[ -n "${before}" && ! "${before}" =~ ^0+$ ]] && git cat-file -e "${before}^{commit}" 2>/dev/null \
        && ! git diff --quiet "${before}" HEAD -- "${policy}"
}

date_on_live_page() {
    curl -fsSL --max-time 30 "${live_url}" 2>/dev/null | grep -oP 'Last updated:</strong> \K[^<]+' | head -n 1 || true
}

date_of_last_commit() {
    local committed
    committed=$(git log -1 --format=%cI -- "${policy}")
    [[ -z "${committed}" ]] || long_date -d "${committed}"
}

if changed_in_push; then
    stamped="${STAMP_TODAY:-$(long_date)}"
    source_of_date="the policy changed in this push"
else
    stamped=$(date_on_live_page)
    source_of_date="the live page"
    if [[ -z "${stamped}" ]]; then
        stamped=$(date_of_last_commit)
        source_of_date="the policy's last commit"
    fi
fi

if ! [[ "${stamped}" =~ ^[0-9]{1,2}\ [A-Z][a-z]+\ [0-9]{4}$ ]]; then
    echo "::error::No date to stamp on the privacy policy (got '${stamped}')" >&2
    exit 1
fi
# Exactly one line to set, or a reworded one would publish without a date.
if [[ $(grep -c '^\*\*Last updated:\*\* ' "${policy}") -ne 1 ]]; then
    echo "::error::${policy} must have exactly one '**Last updated:** ' line" >&2
    exit 1
fi
sed -i "s/^\*\*Last updated:\*\* .*/**Last updated:** ${stamped}/" "${policy}"
echo "Privacy policy last updated: ${stamped} (${source_of_date})"
