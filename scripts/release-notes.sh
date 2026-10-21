#!/usr/bin/env bash
# Reads and empties RELEASE_NOTES.md for one platform's release. The file has three sections, each headed by exactly
# one of these lines:
#
#   ## Shared      notes for both apps (shared code), released with each
#   ## Android     Android only
#   ## iOS         iOS only
#
# Text above the first heading counts as Shared, so a file without headings is all Shared. Other headings (### and
# below, or any other ## line) are part of the section they sit in.
#
#   scripts/release-notes.sh extract android             # Shared, then Android: the release description's notes
#   scripts/release-notes.sh clear android RELEASED.md   # empties Shared and Android, each only if it still matches
#                                                        # RELEASED.md (a copy taken when the release started)
#
# `clear` rewrites the file as the three headings with their remaining text. A section that changed since RELEASED.md
# was taken holds notes for the next release, and is left alone. RELEASE_NOTES_FILE overrides the file (for tests).
set -euo pipefail

notes_file="${RELEASE_NOTES_FILE:-RELEASE_NOTES.md}"
sections=(Shared Android iOS)

usage() { sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 2; }

# One section's text, without its heading or the blank lines around it. A missing file has empty sections.
section() {
    local file="$1" name="$2"
    [[ -f "${file}" ]] || return 0
    awk -v want="${name}" '
        /^## (Shared|Android|iOS)[ \t]*$/ { current = $2; next }
        { if (current == "") current = "Shared"; if (current == want) lines[++n] = $0 }
        END {
            first = 1; while (first <= n && lines[first] ~ /^[ \t]*$/) first++
            last = n; while (last >= first && lines[last] ~ /^[ \t]*$/) last--
            for (i = first; i <= last; i++) print lines[i]
        }
    ' "${file}"
}

[[ $# -ge 2 ]] || usage
command="$1"
case "$2" in
    android) platform=Android ;;
    ios) platform=iOS ;;
    *) usage ;;
esac

case "${command}" in
    extract)
        shared="$(section "${notes_file}" Shared)"
        own="$(section "${notes_file}" "${platform}")"
        if [[ -n "${shared}" ]]; then printf '%s\n' "${shared}"; fi
        if [[ -n "${shared}" && -n "${own}" ]]; then echo; fi
        if [[ -n "${own}" ]]; then printf '%s\n' "${own}"; fi
        ;;
    clear)
        [[ $# -eq 3 ]] || usage
        released="$3"
        output=""
        for name in "${sections[@]}"; do
            text="$(section "${notes_file}" "${name}")"
            if [[ "${name}" == Shared || "${name}" == "${platform}" ]]; then
                if [[ "${text}" == "$(section "${released}" "${name}")" ]]; then
                    text=""
                else
                    echo "RELEASE_NOTES.md's ${name} section changed during this release - leaving it for the next one" >&2
                fi
            fi
            output+="## ${name}"$'\n\n'
            if [[ -n "${text}" ]]; then output+="${text}"$'\n\n'; fi
        done
        printf '%s' "${output%$'\n'}" > "${notes_file}"
        ;;
    *) usage ;;
esac
