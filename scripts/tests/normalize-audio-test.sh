#!/usr/bin/env bash
# Tests for scripts/normalize-audio.sh: both apps' copies of every clip in app/audio/ exist, keep their source's
# length (the shake window is timed to the clips, so an iOS clip padded by AAC's encoder delay would drift), and peak
# near the target level. Needs ffmpeg and python3. Run from anywhere: scripts/tests/normalize-audio-test.sh
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
script="$root/scripts/normalize-audio.sh"
work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT
failures=0

# Lengths may differ by a single AAC frame's rounding at most; peaks by what lossy coding moves them.
MAX_LENGTH_DIFF_SECONDS=0.005
TARGET_PEAK_DB=-0.8
MAX_PEAK_DIFF_DB=1.5

duration() { ffprobe -v error -show_entries format=duration -of csv=p=0 "$1"; }
peak() {
    ffmpeg -hide_banner -i "$1" -af volumedetect -f null - 2>&1 | sed -n 's/.*max_volume: *\(-\{0,1\}[0-9.]*\) dB.*/\1/p'
}
within() { python3 -c "import sys; sys.exit(0 if abs($1 - ($2)) <= $3 else 1)"; }

check() {
    local name="$1"
    shift
    if "$@"; then
        echo "ok   ${name}"
    else
        echo "FAIL ${name}"
        failures=$((failures + 1))
    fi
}

sources=("$root"/app/audio/*.ogg)
for format in ogg m4a; do
    "$script" "$format" "$work/$format" "${sources[@]}"
    for source in "${sources[@]}"; do
        name="$(basename "$source" .ogg)"
        out="$work/$format/$name.$format"
        check "$format: $name exists" test -s "$out"
        [ -s "$out" ] || continue
        check "$format: $name keeps its length ($(duration "$source")s)" \
            within "$(duration "$out")" "$(duration "$source")" "$MAX_LENGTH_DIFF_SECONDS"
        check "$format: $name peaks near ${TARGET_PEAK_DB} dB" within "$(peak "$out")" "$TARGET_PEAK_DB" "$MAX_PEAK_DIFF_DB"
    done
done

check "an unknown format is refused" bash -c "! '$script' wav '$work/wav' '${sources[0]}' 2> /dev/null"

if [[ "${failures}" -gt 0 ]]; then
    echo "${failures} failed"
    exit 1
fi
echo "All passed"
