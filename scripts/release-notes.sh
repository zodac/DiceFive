#!/usr/bin/env bash
# Reads and empties RELEASE_NOTES.md for a release. The whole file is the release description's notes, as written.
#
#   scripts/release-notes.sh extract               # the notes, without leading or trailing blank lines
#   scripts/release-notes.sh clear RELEASED.md     # empties the file, only if it still matches RELEASED.md
#                                                  # (a copy taken when the release started)
#
# A file that changed since RELEASED.md was taken holds notes for the next release, and is left alone.
# RELEASE_NOTES_FILE overrides the file (for tests).
set -euo pipefail

notes_file="${RELEASE_NOTES_FILE:-RELEASE_NOTES.md}"

usage() { sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 2; }

# The file's text, without the blank lines around it. A missing file has no notes.
notes() {
    local file="$1"
    [[ -f "${file}" ]] || return 0
    awk '
        { lines[++n] = $0 }
        END {
            first = 1; while (first <= n && lines[first] ~ /^[ \t]*$/) first++
            last = n; while (last >= first && lines[last] ~ /^[ \t]*$/) last--
            for (i = first; i <= last; i++) print lines[i]
        }
    ' "${file}"
}

[[ $# -ge 1 ]] || usage
case "$1" in
    extract)
        [[ $# -eq 1 ]] || usage
        notes "${notes_file}"
        ;;
    clear)
        [[ $# -eq 2 ]] || usage
        current="$(notes "${notes_file}")"
        released="$(notes "$2")"
        if [[ "${current}" == "${released}" ]]; then
            : > "${notes_file}"
        else
            echo "RELEASE_NOTES.md changed during this release - leaving it for the next one" >&2
        fi
        ;;
    *) usage ;;
esac
