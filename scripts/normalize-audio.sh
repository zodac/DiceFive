#!/usr/bin/env bash
# Builds the app's sound effects from their sources in app/audio/: each clip is peak-normalised to the same level
# and written in the format one app plays. Both apps' builds call it, so the clips sound the same on each:
#
#   ogg  Ogg Vorbis, for Android (app/android's NormalizeOggAudioTask).
#   m4a  AAC, for iOS, which can't decode Ogg (app/ios/project.yml's build phase).
#
# The clips (dice cup, hold/unhold clicks, the win fanfare) come from different sources and were never mixed
# against each other, so they land at wildly different loudnesses - e.g. hold.ogg peaks 16dB quieter than
# celebration.ogg, which reads as "some sounds are broken" rather than "some sounds are naturally quieter." Rather
# than hand-normalising each file (which would need re-doing by hand every time one is re-recorded or swapped), the
# builds bring every clip to the same PEAK level, so the sources can keep being replaced freely.
#
# Peak normalisation, not loudness/RMS normalisation: these are all short (150ms-3.3s) one-shot clicks/fanfares,
# well below the multi-second window ffmpeg's own loudnorm (EBU R128) needs for a stable measurement, so matching
# peaks is both simpler and more predictable here - and because none of the peaks moved out from under their
# natural transient, this needs no compressor (the applied gain is chosen to land exactly on TARGET_PEAK_DB, never
# past it).
#
# Usage: scripts/normalize-audio.sh <ogg|m4a> <output-dir> <source.ogg>...
# Needs ffmpeg (with libvorbis for ogg) and python3.

set -euo pipefail

# Matches roughly where celebration.ogg (the loudest original clip) already peaked - every other clip is brought UP
# to this, never down, so nothing gets quieter than it shipped.
TARGET_PEAK_DB=-0.8

if [ $# -lt 3 ] || [[ "$1" != ogg && "$1" != m4a ]]; then
  echo "Usage: $0 <ogg|m4a> <output-dir> <source.ogg>..." >&2
  exit 1
fi
format="$1"
out_dir="$2"
shift 2

if ! command -v ffmpeg > /dev/null; then
  echo "ffmpeg is required to build the sound effects (scripts/normalize-audio.sh) - install it and put it on PATH" >&2
  exit 1
fi

case "$format" in
  ogg) codec=(-c:a libvorbis -q:a 6) ;;
  m4a) codec=(-c:a aac -b:a 128k) ;;
esac

mkdir -p "$out_dir"
for source in "$@"; do
  # ffmpeg's own volumedetect filter rather than hand-rolled PCM analysis: it already decodes every format ffmpeg does.
  peak_db="$(ffmpeg -hide_banner -i "$source" -af volumedetect -f null - 2>&1 \
    | sed -n 's/.*max_volume: *\(-\{0,1\}[0-9.]*\) dB.*/\1/p' | head -n 1)"
  if [ -z "$peak_db" ]; then
    echo "Could not read max_volume for $source from ffmpeg" >&2
    exit 1
  fi
  # Python's float printing (the shortest exact form) matches what the Android build used to pass ffmpeg.
  gain_db="$(python3 -c "print($TARGET_PEAK_DB - ($peak_db))")"
  name="$(basename "$source" .ogg)"
  ffmpeg -nostdin -loglevel error -y -i "$source" \
    -af "volume=${gain_db}dB,alimiter=limit=0.99" \
    "${codec[@]}" \
    "$out_dir/$name.$format"
done
