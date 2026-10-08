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
#               - The device's API level is written into its config (target=android-<api>) and Vulkan is checked
#                 once it's up: the runner's avdmanager wrote a target the emulator read as API 3, so it left
#                 Vulkan off and every app's renderer aborted (see set_target and check_vulkan below).
#               - /data is set to 6 GB at boot and checked once it's up: the runner's tools left it at 800 MB,
#                 which a first boot fills, crash-looping system_server (see data_mb below).
#
# Usage:        .github/scripts/ci_emulator.sh start|stop   (from the repository root)
#               .github/scripts/ci_emulator.sh screenshot <file.png>   (what is on its screen - for a failed run)
#               .github/scripts/ci_emulator.sh logcat <file.txt>       (its whole log - for a failed run)
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
# The size of the device's /data, set at boot with -partition-size (not in the device's config.ini, which
# the emulator rewrites in its own format). Left alone, avdmanager picks it, and the runner's picks 800 MB (the sandbox's: 10 GB),
# which a first boot of this image fills in about 30 seconds: every write then fails ("No space left on
# device", SQLITE_FULL) and system_server crash-loops, which showed up as a missing package service, an
# install refused with "not allowed to perform GET_USAGE_STATS", or a framework that never settled. A
# first boot plus a lap of the journey uses about 2 GB. The disk image is sparse, so the runner only
# stores what is used.
data_mb=6144
# Set explicitly so avdmanager writes the device where the emulator reads it: each tool otherwise works the
# folder out from its own list of variables, and on a runner the two can disagree (the emulator then
# reports "Unknown AVD name").
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-${HOME}/.android/avd}"
export PATH="${sdk}/platform-tools:${PATH}"

is_up() { [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; }

# Whether Android's framework is running, not just booted: the package and activity services the test
# runner installs and starts the app through, and an install session can be opened (the step that has
# failed on a runner with "android from uid 1000 not allowed to perform GET_USAGE_STATS" while both services
# were listed). They vanish or misbehave while system_server restarts (seen on a runner once the device had
# booted), and an install then fails without failing Gradle.
# What failed is left in ${not_up_because} for ready() to report.
framework_up() {
    local service session out
    for service in package activity; do
        if ! adb shell service check "${service}" 2>/dev/null | grep -q ': found'; then
            not_up_because="the ${service} service is missing"
            return 1
        fi
    done
    out=$(adb shell pm install-create 2>&1 | tr -d '\r')
    if ! session=$(grep -oP '^Success: created install session \[\K[0-9]+' <<<"${out}"); then
        not_up_because="no install session: $(grep -m1 -E 'Exception|Error|Failure' <<<"${out}" || head -n1 <<<"${out}")"
        return 1
    fi
    adb shell pm install-abandon "${session}" >/dev/null 2>&1 || true
}

system_server_pid() { adb shell pidof system_server 2>/dev/null | tr -d '\r' || true; }

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
    set_target

    echo "Booting ${avd_name} (log: ${log})..."
    nohup "${sdk}/emulator/emulator" -avd "${avd_name}" \
        -no-window -no-audio -no-boot-anim -no-snapshot \
        -gpu swiftshader_indirect -memory 4096 -cores "$(nproc)" -partition-size "${data_mb}" \
        -verbose \
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
            save_logcat
            exit 1
        fi
    done

    adb root >/dev/null 2>&1 || true
    adb wait-for-device
    until is_up; do sleep 1; done
    adb shell setprop debug.hwui.renderer skiavk
    check_data_size
    grep -m1 -oE "Deciding if GLDirectMem/Vulkan.*API level: [0-9]+" "${log}" | sed 's/^/Emulator: /' || true
    check_vulkan
    if ! ready; then
        echo "The emulator booted, but its framework never settled:" >&2
        tail -n 50 "${log}" >&2
        save_logcat
        exit 1
    fi
    echo "Ready: Android $(adb shell getprop ro.build.version.release | tr -d '\r'), renderer $(adb shell getprop debug.hwui.renderer | tr -d '\r')."
}

# Fails the boot if /data came up smaller than asked for, rather than leaving it to fill up and take the
# framework down with it (see data_mb).
check_data_size() {
    local size_mb
    size_mb=$(adb shell df -k /data 2>/dev/null | awk 'NR == 2 { print int($2 / 1024) }' | tr -d '\r' || true)
    echo "/data: ${size_mb:-unknown} MB."
    if [[ -z "${size_mb}" ]] || (( size_mb < data_mb * 9 / 10 )); then
        echo "The device's /data is ${size_mb:-of unknown size} MB, not the ${data_mb} MB asked for" >&2
        save_logcat
        exit 1
    fi
}

# Writes the device's Android version into its .ini and config.ini as `target=android-<api>`, whatever
# avdmanager wrote. The emulator reads its API level from that line, and only enables Vulkan (and the
# GLDirectMem host memory it needs) from API 29: the runner's avdmanager wrote a target the emulator read
# as API 3 ("not enabling Vulkan because API level is < 29" in its -verbose log), so no app could draw (see
# check_vulkan). What avdmanager wrote is printed, for the record.
set_target() {
    local file
    for file in "${ANDROID_AVD_HOME}/${avd_name}.ini" "${ANDROID_AVD_HOME}/${avd_name}.avd/config.ini"; do
        echo "avdmanager's target in $(basename "${file}"): $(grep -m1 '^target' "${file}" || echo none)"
        sed -i '/^target[[:space:]]*=/d' "${file}"
        echo "target=android-${api}" >>"${file}"
    done
}

# Fails the boot if the device has no Vulkan GPU: every app's UI is drawn through Vulkan (skiavk, set
# above), and without one each app's RenderThread aborts on launch ("Assertion failed: !gpuCount") - SystemUI,
# the launcher and DiceFive alike. The emulator decides at boot whether to offer Vulkan, from the API level
# (see set_target); its -verbose log, in the report artifact, records what it decided and why. Forcing it
# on with `-feature Vulkan` is not enough: without GLDirectMem, apps then crash allocating Vulkan memory.
check_vulkan() {
    if ! adb shell cmd gpu vkjson 2>/dev/null | grep -q '"deviceName"'; then
        echo "The device has no Vulkan GPU, which every app's UI is drawn through here; the emulator said:" >&2
        grep -iE "Vulkan" "${log}" | grep -viE "androidboot|initHostFeatureAndParseDefault|gfxstreamFeature" \
            | head -n 20 >&2 || true
        save_logcat
        exit 1
    fi
    echo "Vulkan: $(adb shell cmd gpu vkjson 2>/dev/null | grep -m1 '"deviceName"' | tr -d '\r\t' | sed 's/.*: *//')"
}

# Waits up to five minutes for the framework (see framework_up), which can trail sys.boot_completed by
# minutes on a slow runner, to be up and to stay up - the same system_server, still answering - for 30
# seconds in a row. Each new reason it isn't, and any system_server restart (with the crash log that
# explains it), is printed as it is seen, so the step's log says why even when the wait then succeeds.
ready() {
    local waited=0 stable=0 pid last_pid="" said=""
    not_up_because=""
    while (( stable < 30 )); do
        (( waited >= 300 )) && return 1
        pid=$(system_server_pid)
        if [[ -n "${last_pid}" && "${pid}" != "${last_pid}" ]]; then
            echo "[${waited}s] system_server restarted (pid ${last_pid} -> ${pid:-none}); its crash log:"
            adb logcat -d -b crash 2>/dev/null | tail -n 40 || true
        fi
        if [[ -z "${pid}" ]]; then
            not_up_because="system_server is not running"
        elif framework_up; then
            [[ "${pid}" == "${last_pid}" ]] && stable=$((stable + 5))
            not_up_because=""
        fi
        if [[ -n "${not_up_because}" ]]; then
            stable=0
            if [[ "${not_up_because}" != "${said}" ]]; then
                echo "[${waited}s] Not ready: ${not_up_because}"
                said="${not_up_because}"
            fi
        fi
        last_pid="${pid}"
        sleep 5
        waited=$((waited + 5))
    done
    (( waited > 30 )) && echo "Framework settled after ${waited}s."
    return 0
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

# Every log buffer (crash and events included: a system_server restart shows there), for a failed run.
logcat() {
    adb logcat -d -b all -v time >"${1:?usage: $0 logcat <file.txt>}"
}

# The same, from a failed start, where release.yml's report artifact picks it up (logcat-*.txt).
save_logcat() {
    logcat "${RUNNER_TEMP:-/tmp}/logcat-boot-$(date +%H%M%S).txt" 2>/dev/null || true
}

case "${1:-}" in
    start) start ;;
    stop)  stop ;;
    screenshot) screenshot "${2:-}" ;;
    logcat) logcat "${2:-}" ;;
    *) echo "usage: $0 start|stop|screenshot <file.png>|logcat <file.txt>" >&2; exit 2 ;;
esac
