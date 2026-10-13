#!/usr/bin/env bash
# Makes the Google Play store listing's assets from the real app: the hi-res icon, the feature graphic, captioned
# phone and 7-inch tablet screenshots, and a promo video - into assets/<version>/, which is gitignored. See
# .claude/STORE_ASSETS.md.
#
#   scripts/generate-store-assets.sh                     # everything (about 10 minutes, most of it the video)
#   scripts/generate-store-assets.sh --parts phone,icon  # just those: icon, feature, phone, tablet, video
#   scripts/generate-store-assets.sh --no-render         # re-caption/re-encode the last renders, without Gradle
#   scripts/generate-store-assets.sh --music track.mp3   # different background music under the video's sound effects
#   scripts/generate-store-assets.sh --no-music          # the video with the app's sounds only
#                                    [--music-loudness -24]  # its level in LUFS - any track ends up as loud as any other
#
# Needs ImageMagick 7 (`magick`) and ffmpeg, both in the sandbox image.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

PARTS="icon,feature,phone,tablet,video"
RENDER=true
MUSIC=""
USE_MUSIC=true
MUSIC_LOUDNESS="-24"
while [[ $# -gt 0 ]]; do
    case "$1" in
        --parts) PARTS="$2"; shift 2 ;;
        --no-render) RENDER=false; shift ;;
        --music) MUSIC="$(realpath "$2")"; shift 2 ;;
        --no-music) USE_MUSIC=false; shift ;;
        --music-loudness) MUSIC_LOUDNESS="$2"; shift 2 ;;
        -h|--help) sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Unknown option: $1 (see --help)" >&2; exit 2 ;;
    esac
done
wants() { [[ ",${PARTS}," == *",$1,"* ]]; }

[[ -z "${MUSIC}" || -f "${MUSIC}" ]] || { echo "No such music file: ${MUSIC}" >&2; exit 1; }
for tool in magick ffmpeg ffprobe python3 curl sha256sum; do
    command -v "${tool}" >/dev/null || { echo "Missing ${tool} - see .claude/STORE_ASSETS.md" >&2; exit 1; }
done

# The output must never end up in the repo: refuse to run if assets/ isn't ignored, or if anything generated in it is
# tracked.
if ! git check-ignore -q assets/probe.png; then
    echo "assets/ is not gitignored - add it to .gitignore before generating store assets." >&2
    exit 1
fi
# The listing's hand-written full description is the one tracked file there.
TRACKED_IN_ASSETS="assets/full-description.txt"
if [[ -n "$(git ls-files assets | grep -vxF "${TRACKED_IN_ASSETS}")" ]]; then
    echo "Generated files under assets/ are tracked by git - remove them from the index (git rm --cached) first:" >&2
    git ls-files assets | grep -vxF "${TRACKED_IN_ASSETS}" >&2
    exit 1
fi

VERSION="$(tr -d '[:space:]' < VERSION)"
RAW="${ROOT}/build/store-assets/raw"
OUT="${ROOT}/assets/${VERSION}"
WORK="${ROOT}/build/store-assets/work"
FONT="${ROOT}/app/shared/src/commonMain/composeResources/font/sora.ttf"
CAPTIONS="${ROOT}/scripts/store-assets/captions.txt"

# Brand colours - see UI.md's Colour section and the launcher icon.
NAVY_TOP="#16305A"
NAVY="#0B1E3A"
NAVY_DEEP="#06122A"
GOLD="#F2C46A"

# The video's default music: "Happy Vibes" by Ruskerdax, CC0 (public domain - no credit required), chosen by the
# maintainer. Not in the repo: fetched once into the ignored assets/music/, and checked against its known checksum.
DEFAULT_MUSIC_URL="https://opengameart.org/sites/default/files/ruskerdax_-_happy_vibes.mp3"
DEFAULT_MUSIC_SHA256="0213de5247b59c468cf7484bb9b7482a2b07558c9302ffa7a1ceb1bae05fd112"
DEFAULT_MUSIC_CREDIT="Happy Vibes by Ruskerdax - https://opengameart.org/node/160228 - CC0 1.0 (no credit required; listed as a courtesy)"
MUSIC_CREDIT=""
if ! ${USE_MUSIC}; then
    MUSIC=""
elif [[ -z "${MUSIC}" ]] && [[ ",${PARTS}," == *",video,"* ]]; then
    MUSIC="${ROOT}/assets/music/happy-vibes.mp3"
    MUSIC_CREDIT="${DEFAULT_MUSIC_CREDIT}"
    if [[ ! -f "${MUSIC}" ]]; then
        echo "==> Fetching the video's music"
        mkdir -p "$(dirname "${MUSIC}")"
        curl -fsSL -o "${MUSIC}.part" "${DEFAULT_MUSIC_URL}"
        mv "${MUSIC}.part" "${MUSIC}"
    fi
    if [[ "$(sha256sum "${MUSIC}" | cut -d' ' -f1)" != "${DEFAULT_MUSIC_SHA256}" ]]; then
        echo "${MUSIC} isn't the expected track (checksum differs) - delete it to fetch it again, or pass --music." >&2
        exit 1
    fi
fi

# ---- Render ---------------------------------------------------------------------------------------------------------

if ${RENDER}; then
    echo "==> Rendering ${PARTS} from the app (Robolectric)"
    ./gradlew --quiet :app:android:testDebugUnitTest --tests 'net.zodac.dicefive.store.*' \
        -PgenerateStoreAssets -PstoreAssetParts="${PARTS}" -PstoreAssetsDir="${RAW}"
fi

mkdir -p "${OUT}" "${WORK}"

caption_for() {
    local key="$1" line
    line="$(grep -E "^${key}=" "${CAPTIONS}" | head -n 1 || true)"
    [[ -n "${line}" ]] || { echo "No caption for '${key}' in ${CAPTIONS}" >&2; exit 1; }
    printf '%s' "${line#*=}"
}

# The navy backdrop every caption sits on: a vertical gradient with a soft glow behind the headline.
backdrop() {
    local width="$1" height="$2" out="$3"
    magick -size "${width}x${height}" gradient:"${NAVY_TOP}-${NAVY_DEEP}" \
        \( -size "${width}x${height}" radial-gradient:"rgba(242,196,106,0.16)-rgba(242,196,106,0)" \
           -geometry "+0-$((height * 2 / 5))" \) -compose over -composite \
        -alpha off "${out}"
}

# A screen with rounded corners, a thin rim and a soft shadow, scaled to fit width x height.
framed_screen() {
    local in="$1" width="$2" height="$3" out="$4"
    local w h radius
    magick "${in}" -filter Lanczos -resize "${width}x${height}" -alpha off "${WORK}/scaled.png"
    read -r w h < <(magick identify -format '%w %h\n' "${WORK}/scaled.png")
    radius=$(( w / 22 ))
    magick "${WORK}/scaled.png" \
        \( -size "${w}x${h}" xc:black -fill white -draw "roundrectangle 0,0 $((w - 1)),$((h - 1)) ${radius},${radius}" \) \
        -alpha off -compose CopyOpacity -composite -compose over \
        -fill none -stroke "rgba(255,255,255,0.18)" -strokewidth 3 \
        -draw "roundrectangle 1,1 $((w - 2)),$((h - 2)) ${radius},${radius}" \
        \( +clone -background black -shadow 60x28+0+18 \) +swap -background none -layers merge +repage \
        "${out}"
}

# ---- Icon and feature graphic ---------------------------------------------------------------------------------------

if wants icon; then
    echo "==> Icon"
    # 512x512, 32-bit PNG, at most 1MB. Full square: Play rounds the corners and adds the shadow itself.
    magick "${RAW}/icon.png" -filter Lanczos -resize 512x512 -strip PNG32:"${OUT}/icon-512.png"
fi

if wants feature; then
    echo "==> Feature graphic"
    # 1024x500, JPEG or 24-bit PNG - no alpha.
    magick "${RAW}/feature-graphic.png" -filter Lanczos -resize '1024x500!' -alpha off -strip PNG24:"${OUT}/feature-graphic-1024x500.png"
fi

# ---- Screenshots ----------------------------------------------------------------------------------------------------

# Every screenshot is 1440x2560 (9:16): Play's own featuring size, and room for a tall phone's 20:9 screen under the
# headline without breaking Play's 2:1 limit.
SHOT_W=1440
SHOT_H=2560

screenshots() {
    local device="$1"
    local dir="${OUT}/${device}"
    rm -rf "${dir}"
    mkdir -p "${dir}"
    backdrop "${SHOT_W}" "${SHOT_H}" "${WORK}/shot-backdrop.png"
    local band=$(( SHOT_H * 15 / 100 ))
    # Named for the device as well as the screen ("phone-1-game.png"), so files from different folders can't be mixed up.
    for raw in "${RAW}/${device}"/*.png; do
        local name key text
        name="$(basename "${raw}" .png)"
        key="${name#*-}"
        text="$(caption_for "${key}")"
        framed_screen "${raw}" $(( SHOT_W * 84 / 100 )) $(( SHOT_H - band - SHOT_H * 3 / 100 )) "${WORK}/screen.png"
        magick "${WORK}/shot-backdrop.png" \
            \( -background none -fill "${GOLD}" -font "${FONT}" -size "$(( SHOT_W * 86 / 100 ))x$(( band * 70 / 100 ))" \
               -gravity center caption:"${text}" \) -gravity north -geometry "+0+$(( band * 18 / 100 ))" -composite \
            "${WORK}/screen.png" -gravity north -geometry "+0+${band}" -composite \
            -alpha off -strip PNG24:"${dir}/${device}-${name}.png"
    done
}

if wants phone; then
    echo "==> Phone screenshots"
    screenshots phone
fi
if wants tablet; then
    echo "==> 7-inch tablet screenshots"
    screenshots tablet-7
fi

# ---- Video ----------------------------------------------------------------------------------------------------------

VIDEO_W=1920
VIDEO_H=1080
FPS=60
FADE=0.4

if wants video; then
    echo "==> Promo video"
    video_work="${WORK}/video"
    rm -rf "${video_work}"
    mkdir -p "${video_work}"

    # The phone's screen on the right, the scene's headline on the left. Its size, from the first frame.
    first_frame="$(find "${RAW}/video" -mindepth 2 -name '00000.png' | sort | head -n 1)"
    read -r src_w src_h < <(magick identify -format '%w %h\n' "${first_frame}")
    phone_h=$(( VIDEO_H * 88 / 100 ))
    phone_w=$(( src_w * phone_h / src_h / 2 * 2 ))
    phone_x=$(( VIDEO_W * 64 / 100 - phone_w / 2 ))
    phone_y=$(( (VIDEO_H - phone_h) / 2 ))
    radius=$(( phone_w / 22 ))

    magick -size "${phone_w}x${phone_h}" xc:black -fill white \
        -draw "roundrectangle 0,0 $((phone_w - 1)),$((phone_h - 1)) ${radius},${radius}" "${video_work}/mask.png"
    backdrop "${VIDEO_W}" "${VIDEO_H}" "${video_work}/backdrop.png"
    # The phone's shadow and rim, painted once into the backdrop.
    magick "${video_work}/backdrop.png" \
        \( -size "${phone_w}x${phone_h}" xc:none -fill black -draw "roundrectangle 0,0 $((phone_w - 1)),$((phone_h - 1)) ${radius},${radius}" \
           -background black -shadow 60x24+0+16 \) -geometry "+$((phone_x - 48))+$((phone_y - 32))" -composite \
        -fill none -stroke "rgba(255,255,255,0.22)" -strokewidth 4 \
        -draw "roundrectangle $((phone_x - 2)),$((phone_y - 2)) $((phone_x + phone_w + 1)),$((phone_y + phone_h + 1)) ${radius},${radius}" \
        -alpha off "${video_work}/backdrop-phone.png"

    segments=()
    scene_dirs=()
    for scene in "${RAW}/video"/*/; do
        scene="${scene%/}"
        name="$(basename "${scene}")"
        key="${name#*-}"
        text="$(caption_for "${key}")"
        magick "${video_work}/backdrop-phone.png" \
            \( -background none -fill "${GOLD}" -font "${FONT}" -size "$(( VIDEO_W * 40 / 100 ))x$(( VIDEO_H * 40 / 100 ))" \
               -gravity center caption:"${text}" \) -gravity northwest -geometry "+$(( VIDEO_W * 6 / 100 ))+$(( VIDEO_H * 30 / 100 ))" -composite \
            -alpha off "${video_work}/${name}-backdrop.png"
        ffmpeg -loglevel error -y \
            -loop 1 -framerate "${FPS}" -i "${video_work}/${name}-backdrop.png" \
            -framerate "${FPS}" -i "${scene}/%05d.png" \
            -i "${video_work}/mask.png" \
            -filter_complex "[1:v]scale=${phone_w}:${phone_h}:flags=lanczos,format=rgba[screen];[2:v]format=gray[mask];[screen][mask]alphamerge[rounded];[0:v][rounded]overlay=${phone_x}:${phone_y}:shortest=1,format=yuv420p[v]" \
            -map '[v]' -c:v libx264 -preset slow -crf 12 -r "${FPS}" "${video_work}/${name}.mp4"
        segments+=("${video_work}/${name}.mp4")
        scene_dirs+=("${scene}")
    done

    # The closing card: the feature graphic's scene, held still.
    ffmpeg -loglevel error -y -loop 1 -framerate "${FPS}" -i "${RAW}/video-end-card.png" -t 3 \
        -vf "scale=${VIDEO_W}:${VIDEO_H}:flags=lanczos,format=yuv420p" -c:v libx264 -preset slow -crf 12 "${video_work}/end.mp4"
    segments+=("${video_work}/end.mp4")
    scene_dirs+=("")

    # Joined with short cross-fades, a fade in from black and out to it.
    inputs=()
    filter=""
    offset=0
    starts=()
    previous="[0:v]"
    for i in "${!segments[@]}"; do
        inputs+=(-i "${segments[$i]}")
        starts+=("${offset}")
        duration="$(ffprobe -v error -show_entries format=duration -of csv=p=0 "${segments[$i]}")"
        if [[ ${i} -gt 0 ]]; then
            filter+="${previous}[${i}:v]xfade=transition=fade:duration=${FADE}:offset=${offset}[x${i}];"
            previous="[x${i}]"
        fi
        offset="$(awk -v o="${offset}" -v d="${duration}" -v f="${FADE}" 'BEGIN { printf "%.3f", o + d - f }')"
    done
    total="$(awk -v o="${offset}" -v f="${FADE}" 'BEGIN { printf "%.3f", o + f }')"
    filter+="${previous}fade=t=in:st=0:d=0.5,fade=t=out:st=$(awk -v t="${total}" 'BEGIN { printf "%.3f", t - 0.6 }'):d=0.6,format=yuv420p[v];"

    # The sound: the app's own clips - the build's loudness-normalised copies, what the app plays - each at the frame
    # the app played it (sounds.txt, logged by the generator) and at its volume, placed on the joined timeline.
    clips="${ROOT}/app/android/build/generated/normalizedAudioRes/raw"
    next_input=${#segments[@]}
    sounds=()
    used_clips=()
    for i in "${!scene_dirs[@]}"; do
        log="${scene_dirs[$i]}/sounds.txt"
        [[ -n "${scene_dirs[$i]}" && -s "${log}" ]] || continue
        while read -r frame clip volume; do
            [[ -f "${clips}/${clip}.ogg" ]] || { echo "No ${clips}/${clip}.ogg - run without --no-render to build it" >&2; exit 1; }
            delay="$(awk -v s="${starts[$i]}" -v f="${frame}" -v r="${FPS}" 'BEGIN { printf "%d", (s + f / r) * 1000 }')"
            inputs+=(-i "${clips}/${clip}.ogg")
            filter+="[${next_input}:a]aformat=sample_rates=48000:channel_layouts=stereo,volume=${volume},adelay=${delay}:all=1[s${#sounds[@]}];"
            sounds+=("[s${#sounds[@]}]")
            used_clips+=("${clip}")
            next_input=$((next_input + 1))
        done < "${log}"
    done
    if [[ -n "${MUSIC}" ]]; then
        inputs+=(-stream_loop -1 -i "${MUSIC}")
        filter+="[${next_input}:a]aformat=sample_rates=48000:channel_layouts=stereo,loudnorm=I=${MUSIC_LOUDNESS}:TP=-3:LRA=11,aresample=48000,atrim=0:${total},afade=t=in:d=1.5,afade=t=out:st=$(awk -v t="${total}" 'BEGIN { printf "%.3f", t - 2.5 }'):d=2.5[music];"
        sounds+=("[music]")
    fi
    # Lifted 4dB, the limiter catching the dice's peaks (at -2dB, with its auto-level off - on, it scales back up to full scale): the mix then lands near the -16 LUFS or so YouTube plays at, which
    # turns a loud video down but never a quiet one up.
    audio=()
    if [[ ${#sounds[@]} -gt 0 ]]; then
        filter+="$(printf '%s' "${sounds[@]}")amix=inputs=${#sounds[@]}:normalize=0:duration=longest,apad,atrim=0:${total},afade=t=out:st=$(awk -v t="${total}" 'BEGIN { printf "%.3f", t - 0.6 }'):d=0.6,volume=4dB,alimiter=limit=0.8:level=0[a]"
        audio=(-map '[a]' -c:a aac -b:a 192k -ar 48000)
    else
        filter="${filter%;}"
    fi

    # H.264 High at 60fps, about 16Mbps: well over what YouTube asks for 1080p60, so its own re-encode has plenty to work from.
    ffmpeg -loglevel error -y "${inputs[@]}" -filter_complex "${filter}" -map '[v]' "${audio[@]}" \
        -c:v libx264 -preset slow -profile:v high -crf 16 -maxrate 24M -bufsize 48M -r "${FPS}" -movflags +faststart \
        -t "${total}" "${OUT}/promo-video-1080p60.mp4"

    # What the video's description must credit: any clip whose licence asks for it (from the same records the app's
    # Licences screen is built from), and the music, whose licence only its source knows.
    python3 - "${ROOT}" "${OUT}/video-credits.txt" "${MUSIC}" "${MUSIC_CREDIT}" "${used_clips[@]}" <<'PY'
import json, os, sys
root, out, music, music_credit, clips = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sorted(set(sys.argv[5:]))
sources = json.load(open(os.path.join(root, "app/licensing/asset-sources.json")))
lines = []
for clip in clips:
    entry = next((v for k, v in sources.items() if k.endswith(f"/rawAudioSource/{clip}.ogg")), None)
    if entry is None or entry["license"] == "CC0-1.0":
        continue
    for library in entry.get("libraries", []):
        record = json.load(open(os.path.join(root, "app/licensing/libraries", f"{library}.json")))
        line = record["description"]
        if line not in lines:
            lines.append(line)
with open(out, "w") as f:
    f.write("Paste into the video's YouTube description - its licences require the credit.\n\n")
    f.write("Sound effects:\n" + ("\n".join(f"- {l}" for l in lines) if lines else "- none needed (all CC0)") + "\n")
    if music_credit:
        f.write(f"\nMusic: {music_credit}\n")
    elif music:
        f.write(f"\nMusic: {os.path.basename(music)} - add the credit its licence asks for (title, artist, licence, link).\n")
PY
fi

# ---- Check against Play's rules -------------------------------------------------------------------------------------

echo "==> Checking against Play's requirements"
problems=0
problem() { echo "  PROBLEM: $*"; problems=$((problems + 1)); }
check_image() {
    local file="$1" rule="$2"
    local w h alpha bytes
    read -r w h alpha < <(magick identify -format '%w %h %A\n' "${file}")
    bytes="$(stat -c %s "${file}")"
    case "${rule}" in
        icon)
            [[ "${w}x${h}" == "512x512" ]] || problem "${file}: ${w}x${h}, must be 512x512"
            (( bytes <= 1024 * 1024 )) || problem "${file}: over 1MB" ;;
        feature)
            [[ "${w}x${h}" == "1024x500" ]] || problem "${file}: ${w}x${h}, must be 1024x500"
            [[ "${alpha}" == "Undefined" || "${alpha}" == "False" ]] || problem "${file}: has an alpha channel"
            (( bytes <= 15 * 1024 * 1024 )) || problem "${file}: over 15MB" ;;
        screenshot)
            local long=$(( w > h ? w : h )) short=$(( w < h ? w : h ))
            (( short >= 320 && long <= 3840 )) || problem "${file}: ${w}x${h}, sides must be 320-3840px"
            (( long <= 2 * short )) || problem "${file}: ${w}x${h}, long side over twice the short"
            (( short >= 1080 )) || echo "  note: ${file}: under 1080px, too small for Play's featuring"
            [[ "${alpha}" == "Undefined" || "${alpha}" == "False" ]] || problem "${file}: has an alpha channel"
            (( bytes <= 8 * 1024 * 1024 )) || problem "${file}: over 8MB" ;;
    esac
}
[[ -f "${OUT}/icon-512.png" ]] && check_image "${OUT}/icon-512.png" icon
[[ -f "${OUT}/feature-graphic-1024x500.png" ]] && check_image "${OUT}/feature-graphic-1024x500.png" feature
for device in phone tablet-7; do
    [[ -d "${OUT}/${device}" ]] || continue
    count=$(find "${OUT}/${device}" -maxdepth 1 -name '*.png' | wc -l)
    (( count >= 2 && count <= 8 )) || problem "${device}: ${count} screenshots, Play takes 2-8"
    (( count >= 4 )) || echo "  note: ${device}: fewer than 4 screenshots, so not considered for featuring"
    for shot in "${OUT}/${device}"/*.png; do check_image "${shot}" screenshot; done
done
if [[ -f "${OUT}/promo-video-1080p60.mp4" ]]; then
    ffprobe -v error -select_streams v:0 -show_entries stream=width,height,r_frame_rate:format=duration \
        -of default=noprint_wrappers=1 "${OUT}/promo-video-1080p60.mp4" | sed 's/^/  video /'
fi

# ---- A contact sheet to review it all at a glance -------------------------------------------------------------------

sheet_inputs=()
for device in phone tablet-7; do
    [[ -d "${OUT}/${device}" ]] && sheet_inputs+=("${OUT}/${device}"/*.png)
done
[[ -f "${OUT}/feature-graphic-1024x500.png" ]] && sheet_inputs+=("${OUT}/feature-graphic-1024x500.png")
[[ -f "${OUT}/icon-512.png" ]] && sheet_inputs+=("${OUT}/icon-512.png")
if [[ ${#sheet_inputs[@]} -gt 0 ]]; then
    magick montage "${sheet_inputs[@]}" -tile 6x -geometry 360x640+12+12 -background '#222' "${OUT}/contact-sheet.png"
fi

echo
echo "Store assets for ${VERSION}: ${OUT}"
find "${OUT}" -maxdepth 2 -type f | sort | sed "s|${ROOT}/||; s/^/  /"
if (( problems > 0 )); then
    echo "${problems} problem(s) found - see above." >&2
    exit 1
fi
