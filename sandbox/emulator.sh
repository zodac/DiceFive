#!/usr/bin/env bash
# An Android emulator inside the sandbox, started on demand - for running the Baseline Profile journey
# (`./gradlew :app:android:generateBaselineProfile`) or any instrumented test without a phone.
#
#   sandbox/emulator.sh start     # install what's missing, boot headless, wait until it's ready
#   sandbox/emulator.sh status
#   sandbox/emulator.sh screenshot out.png   # what's on the emulator's screen right now
#   sandbox/emulator.sh stop
#
# How it works, what it's for and how to debug it: .claude/EMULATOR.md. Run it INSIDE the sandbox. The first `start` in a fresh container downloads the emulator and a system
# image (~1.5 GB: they live in the container's SDK, not in the image) and creates the virtual device;
# later starts in the same container just boot it (~20 s).
#
# Two things here are not obvious, and both are why this script exists rather than a one-line command:
#
# - /dev/kvm is root:<the host's kvm gid> inside the container and `dev` isn't in that group, so without
#   a fix the emulator falls back to software CPU emulation, far too slow to drive an app. The sandbox is
#   --privileged and `dev` has passwordless sudo, so the device node is opened up (this container's copy
#   only - the host's is untouched).
# - The emulator's software GLES path (gfxstream + SwiftShader) segfaults in its render thread while
#   drawing DiceFive, silently killing the emulator (exit status 139, no crash report) a few seconds
#   after the app opens - it shows up as `adb: device offline`. Its Vulkan path doesn't, so the app's UI
#   renderer is switched to Vulkan after boot (debug.hwui.renderer=skiavk, which resets on every boot, so
#   this script sets it every time). The app draws correctly under it.
set -euo pipefail

avd_name="${EMULATOR_AVD_NAME:-dicefive34}"
api="${EMULATOR_API:-34}"
image="system-images;android-${api};google_apis;x86_64"
memory_mb="${EMULATOR_MEMORY_MB:-4096}"
cores="${EMULATOR_CORES:-4}"
sdk="${ANDROID_HOME:?ANDROID_HOME is not set - run this inside the sandbox}"
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-${HOME}/.android/avd}"
log="${HOME}/.android/emulator-${avd_name}.log"

is_up() { [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; }

ensure_kvm() {
  [[ -e /dev/kvm ]] || { echo "No /dev/kvm: the sandbox host must pass it through (the sandbox runs --privileged)." >&2; exit 1; }
  if [[ ! -r /dev/kvm || ! -w /dev/kvm ]]; then
    echo "[emulator] opening /dev/kvm to this user (this container only)..."
    sudo chmod 666 /dev/kvm
  fi
}

# The SDK is root-owned in the image, so packages go in through sudo - and sdkmanager, run as root,
# unpacks them without the other-users read/execute bits the emulator then needs.
ensure_packages() {
  if [[ -x "${sdk}/emulator/emulator" && -d "${sdk}/system-images/android-${api}/google_apis/x86_64" ]]; then
    return
  fi
  echo "[emulator] installing the emulator and the API ${api} system image (one-off, ~1.5 GB)..."
  yes | sudo -E env "PATH=${PATH}" "${sdk}/cmdline-tools/latest/bin/sdkmanager" "emulator" "${image}" >/dev/null
  sudo chmod -R a+rX "${sdk}/emulator" "${sdk}/system-images"
  sudo find "${sdk}/emulator" -type f -perm -u+x -exec chmod a+x {} +
}

ensure_avd() {
  if [[ -d "${ANDROID_AVD_HOME}/${avd_name}.avd" ]]; then
    return
  fi
  echo "[emulator] creating the virtual device '${avd_name}'..."
  mkdir -p "${ANDROID_AVD_HOME}"
  echo no | "${sdk}/cmdline-tools/latest/bin/avdmanager" create avd -n "${avd_name}" -k "${image}" -d pixel_6 --force >/dev/null
}

start() {
  if is_up; then
    echo "[emulator] already running: $(adb devices | awk 'NR==2 {print $1}')"
    return
  fi
  ensure_kvm
  ensure_packages
  ensure_avd

  adb kill-server >/dev/null 2>&1 || true
  mkdir -p "$(dirname "${log}")"
  echo "[emulator] booting '${avd_name}' (log: ${log})..."
  # setsid + redirected stdio: detached from this shell, so it outlives the command that started it.
  setsid nohup "${sdk}/emulator/emulator" -avd "${avd_name}" \
    -no-window -no-audio -no-boot-anim -no-snapshot \
    -gpu swiftshader_indirect -memory "${memory_mb}" -cores "${cores}" \
    >"${log}" 2>&1 </dev/null &
  disown

  local waited=0
  until is_up; do
    sleep 3
    waited=$((waited + 3))
    if (( waited > 300 )); then
      echo "[emulator] did not finish booting in 5 minutes; see ${log}" >&2
      exit 1
    fi
  done

  # See the header: without this the app crashes the emulator. Root is available on google_apis images.
  adb root >/dev/null 2>&1 || true
  adb wait-for-device
  adb shell setprop debug.hwui.renderer skiavk
  echo "[emulator] ready: $(adb devices | awk 'NR==2 {print $1}'), Android $(adb shell getprop ro.build.version.release | tr -d '\r')."
}

stop() {
  if adb devices 2>/dev/null | grep -q '^emulator-'; then
    adb emu kill >/dev/null 2>&1 || true
    echo "[emulator] stopped."
  else
    echo "[emulator] not running."
  fi
}

screenshot() {
  local out="${1:?usage: $0 screenshot <file.png>}"
  is_up || { echo "[emulator] not running - start it first." >&2; exit 1; }
  adb exec-out screencap -p >"${out}"
  echo "[emulator] saved ${out}"
}

status() {
  if is_up; then
    echo "running: $(adb devices | awk 'NR==2 {print $1}'), renderer $(adb shell getprop debug.hwui.renderer | tr -d '\r')"
  else
    echo "not running"
    exit 1
  fi
}

case "${1:-}" in
  start)  start ;;
  stop)   stop ;;
  status) status ;;
  screenshot) screenshot "${2:-}" ;;
  *) echo "usage: $0 start|stop|status|screenshot <file.png>" >&2; exit 2 ;;
esac
