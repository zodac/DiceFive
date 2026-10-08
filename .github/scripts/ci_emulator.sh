#!/bin/bash
# ------------------------------------------------------------------------------
# Script Name:  ci_emulator.sh
#
# Description:  Boots a headless Android emulator on a GitHub-hosted Linux runner, for release.yml's
#               baseline-profile job (`./gradlew :app:android:generateBaselineProfile`). The CI twin of
#               sandbox/emulator.sh, which does the same inside the sandbox; .claude/EMULATOR.md explains
#               both, and why each step below is there.
#
#               - The Android version is sandbox/Dockerfile's `ENV EMULATOR_API`, read at run time, so the
#                 monthly dependency update (which keeps it on compileSdk's level) moves CI with it and never
#                 has to edit a workflow file.
#               - It is a Google APIs image (not Play Store): profile generation and the renderer setting
#                 below both need `adb root`.
#               - /dev/kvm is opened to the runner user, or the emulator falls back to software CPU
#                 emulation, far too slow to drive the app.
#               - The app's UI renderer is switched to Vulkan after boot (debug.hwui.renderer=skiavk): the
#                 emulator's software GLES path segfaults while drawing DiceFive. The property resets on
#                 every boot, so `start` sets it every time.
#
# Usage:        .github/scripts/ci_emulator.sh start|stop   (from the repository root)
#               .github/scripts/ci_emulator.sh screenshot <file.png>   (what is on its screen - for a failed run)
#
# Requirements: ANDROID_HOME (the runner's pre-installed SDK), passwordless sudo, bash, grep
# ------------------------------------------------------------------------------
set -euo pipefail

api=$(grep -oP '^ENV EMULATOR_API=\K[0-9.]+' sandbox/Dockerfile | head -1 || true)
if [[ -z "${api}" ]]; then
    echo "Could not read ENV EMULATOR_API from sandbox/Dockerfile" >&2
    exit 1
fi
sdk="${ANDROID_HOME:?ANDROID_HOME is not set}"
avd_name="ci-api${api}"
image="system-images;android-${api};google_apis;x86_64"
log="${RUNNER_TEMP:-/tmp}/emulator-${avd_name}.log"
# Set explicitly so avdmanager writes the device where the emulator reads it: each tool otherwise works the
# folder out from its own list of variables, and on a runner the two can disagree (the emulator then
# reports "Unknown AVD name").
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-${HOME}/.android/avd}"
export PATH="${sdk}/platform-tools:${PATH}"

is_up() { [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; }

# Whether Android's framework is running, not just booted: the package and activity services the test
# runner installs and starts the app through. They vanish while system_server restarts (seen on a runner
# once the device had booted), and an install then fails without failing Gradle.
framework_up() {
    local service
    for service in package activity; do
        adb shell service check "${service}" 2>/dev/null | grep -q ': found' || return 1
    done
}

ensure_kvm() {
    [[ -e /dev/kvm ]] || { echo "No /dev/kvm on this runner" >&2; exit 1; }
    if [[ ! -r /dev/kvm || ! -w /dev/kvm ]]; then
        echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' \
            | sudo tee /etc/udev/rules.d/99-kvm4all.rules >/dev/null
        sudo udevadm control --reload-rules
        sudo udevadm trigger --name-match=kvm
    fi
}

# Skipped when release.yml's cache already restored them. Installed the same way as
# .github/actions/build-setup installs the build's packages: the `android` CLI if the runner has it,
# otherwise sdkmanager.
ensure_packages() {
    if [[ -x "${sdk}/emulator/emulator" && -d "${sdk}/system-images/android-${api}/google_apis/x86_64" ]]; then
        return
    fi
    echo "Installing the emulator and ${image}..."
    local android_cli="${sdk}/cmdline-tools/latest/bin/android"
    if [[ -x "${android_cli}" ]]; then
        "${android_cli}" sdk install --sdk="${sdk}" "emulator" "${image}" </dev/null >/dev/null
    else
        local sdkmanager="${sdk}/cmdline-tools/latest/bin/sdkmanager"
        # yes always exits non-zero here (sdkmanager stops reading: a broken pipe), which pipefail would take
        # as a failure; only sdkmanager's own status counts.
        { yes 2>/dev/null || true; } | "${sdkmanager}" --licenses >/dev/null
        "${sdkmanager}" "emulator" "${image}" >/dev/null
    fi
}

start() {
    ensure_kvm
    ensure_packages
    if [[ ! -f "${ANDROID_AVD_HOME}/${avd_name}.ini" ]]; then
        mkdir -p "${ANDROID_AVD_HOME}"
        # avdmanager can exit 0 without making the device, so the .ini is what's checked, and its output
        # is only shown when that check fails.
        local created
        created=$(echo no | "${sdk}/cmdline-tools/latest/bin/avdmanager" create avd \
            -n "${avd_name}" -k "${image}" -d pixel_6 --force 2>&1) || true
        if [[ ! -f "${ANDROID_AVD_HOME}/${avd_name}.ini" ]]; then
            echo "avdmanager did not create ${avd_name} in ${ANDROID_AVD_HOME}:" >&2
            echo "${created}" >&2
            exit 1
        fi
    fi

    echo "Booting ${avd_name} (log: ${log})..."
    nohup "${sdk}/emulator/emulator" -avd "${avd_name}" \
        -no-window -no-audio -no-boot-anim -no-snapshot \
        -gpu swiftshader_indirect -memory 4096 -cores "$(nproc)" \
        >"${log}" 2>&1 </dev/null &
    local pid=$!

    local waited=0
    until is_up; do
        # An emulator that has already quit will never boot: say so now rather than after the full wait.
        if ! kill -0 "${pid}" 2>/dev/null; then
            echo "The emulator quit before it finished booting:" >&2
            tail -n 50 "${log}" >&2
            exit 1
        fi
        sleep 3
        waited=$((waited + 3))
        if (( waited > 600 )); then
            echo "The emulator did not finish booting in 10 minutes:" >&2
            tail -n 50 "${log}" >&2
            exit 1
        fi
    done

    adb root >/dev/null 2>&1 || true
    adb wait-for-device
    until is_up; do sleep 1; done
    adb shell setprop debug.hwui.renderer skiavk
    if ! ready; then
        echo "The emulator booted, but its framework never came up:" >&2
        tail -n 50 "${log}" >&2
        exit 1
    fi
    echo "Ready: Android $(adb shell getprop ro.build.version.release | tr -d '\r'), renderer $(adb shell getprop debug.hwui.renderer | tr -d '\r')."
}

# Waits up to a minute for the framework (see framework_up), which can trail sys.boot_completed.
ready() {
    local waited=0
    until framework_up; do
        (( waited >= 60 )) && return 1
        sleep 2
        waited=$((waited + 2))
    done
}

stop() {
    adb emu kill >/dev/null 2>&1 || true
    local waited=0
    while adb devices 2>/dev/null | grep -q '^emulator-' && (( waited < 30 )); do
        sleep 1
        waited=$((waited + 1))
    done
}

screenshot() {
    adb exec-out screencap -p >"${1:?usage: $0 screenshot <file.png>}"
}

case "${1:-}" in
    start) start ;;
    stop)  stop ;;
    screenshot) screenshot "${2:-}" ;;
    *) echo "usage: $0 start|stop|screenshot <file.png>" >&2; exit 2 ;;
esac
