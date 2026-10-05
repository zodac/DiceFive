#!/bin/bash
# ------------------------------------------------------------------------------
# Script Name:  android_sdk_packages.sh
#
# Description:  Prints the Android SDK packages this project builds against, space-separated and ready
#               to hand to `android sdk install`: the platform for compileSdk (+ compileSdkMinor) and the
#               build-tools for buildToolsVersion, both read from app/android/build.gradle.kts. The workflows
#               call this instead of pinning the packages themselves, so a compileSdk or build-tools
#               change never has to edit a workflow file (which the monthly dependency update, pushing
#               with GITHUB_TOKEN, is not allowed to do).
#
#               The platform's package NAME is not derivable from the API level alone: a minor release
#               is android-<api>.<minor>, and a base release is android-<api> before API 37 but
#               android-<api>.0 from API 37 on. So the candidates are checked against Google's SDK
#               repository manifest (the one the installer itself reads), and the first one it lists wins.
#               (Not `sdkmanager --list` / `android sdk list`: it needs a writable SDK just to take its lock, and it is being
#               replaced by the `android sdk` CLI, so its output format is not one to depend on.)
#
# Usage:        .github/scripts/android_sdk_packages.sh      (from the repository root)
#               e.g. "$SDKMANAGER" $(.github/scripts/android_sdk_packages.sh)
#
# Requirements: bash, curl, grep (if the manifest cannot be fetched, the first candidate name is
#               printed unchecked, with a warning)
# ------------------------------------------------------------------------------
set -euo pipefail

build_file="app/android/build.gradle.kts"

api=$(grep -oP '^\s*compileSdk\s*=\s*\K[0-9]+' "${build_file}" | head -1 || true)
minor=$(grep -oP '^\s*compileSdkMinor\s*=\s*\K[0-9]+' "${build_file}" | head -1 || true)
build_tools=$(grep -oP '^\s*buildToolsVersion\s*=\s*"\K[^"]+' "${build_file}" | head -1 || true)
if [[ -z "${api}" || -z "${build_tools}" ]]; then
    echo "Could not read compileSdk / buildToolsVersion from ${build_file}" >&2
    exit 1
fi

candidates=()
if [[ -n "${minor}" && "${minor}" != "0" ]]; then
    candidates+=("android-${api}.${minor}")
else
    candidates+=("android-${api}" "android-${api}.0")
fi

platform=""
if manifest=$(curl -fsSL --connect-timeout 10 --max-time 60 \
        "https://dl.google.com/android/repository/repository2-3.xml" 2>/dev/null); then
    for candidate in "${candidates[@]}"; do
        if grep -qF "<remotePackage path=\"platforms;${candidate}\">" <<< "${manifest}"; then
            platform="${candidate}"
            break
        fi
    done
fi
if [[ -z "${platform}" ]]; then
    platform="${candidates[0]}"
    echo "Could not confirm the platform package name against the SDK manifest; using ${platform}" >&2
fi

echo "platforms;${platform} build-tools;${build_tools}"
