#!/bin/bash
# ------------------------------------------------------------------------------
# Script Name:  watch_checks.sh
#
# Description:  Watches other jobs of this workflow run and stops this one's long work if any of them
#               fails. release.yml's baseline-profile job runs it in the background: the profile takes
#               ~20 minutes and is useless once a check has failed, since the release won't be made.
#               (GitHub can cancel a whole run but not one job of it, and cancelling the run would also
#               cut short the other checks, whose results are still wanted.)
#
#               Polls the run's jobs every 30 seconds. When a named job ends in anything but success, it
#               writes the marker file, stops Gradle's daemons and the emulator (which fails the Gradle
#               build in progress), and exits. When every named job has succeeded, it exits quietly.
#               A failed API call is retried on the next poll, never taken as a failed check.
#
# Usage:        .github/scripts/watch_checks.sh <marker file> <job name>...   (from the repository root)
#
# Requirements: gh (GH_TOKEN with actions: read), GITHUB_REPOSITORY, GITHUB_RUN_ID
# ------------------------------------------------------------------------------
set -uo pipefail

marker="${1:?usage: $0 <marker file> <job name>...}"
shift
interval="${WATCH_INTERVAL_SECONDS:-30}"
(( $# > 0 )) || { echo "usage: $0 <marker file> <job name>..." >&2; exit 2; }

while true; do
    if jobs=$(gh api --paginate "repos/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}/jobs" \
            --jq '.jobs[] | "\(.name)\t\(.status)\t\(.conclusion)"' 2>/dev/null); then
        succeeded=0
        for name in "$@"; do
            line=$(grep -P "^\Q${name}\E\t" <<< "${jobs}" | head -1 || true)
            IFS=$'\t' read -r _ status conclusion <<< "${line}"
            [[ "${status:-}" == "completed" ]] || continue
            if [[ "${conclusion}" == "success" ]]; then
                succeeded=$((succeeded + 1))
            else
                echo "::error::${name} ended '${conclusion}' - stopping the Baseline Profile, which can no longer be released"
                echo "${name}" > "${marker}"
                ./gradlew --stop >/dev/null 2>&1 || true
                .github/scripts/ci_emulator.sh stop || true
                exit 0
            fi
        done
        (( succeeded == $# )) && exit 0
    fi
    sleep "${interval}"
done
