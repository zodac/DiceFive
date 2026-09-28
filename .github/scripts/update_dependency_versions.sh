#!/bin/bash
# ------------------------------------------------------------------------------
# Script Name:  update_dependency_versions.sh
#
# Description:  Updates pinned tool and library versions across this Android (Gradle) project:
#                 - The Gradle version catalog (gradle/libs.versions.toml): every [versions] entry -
#                   AGP, Kotlin, KSP, AndroidX, the Compose BOM, test libraries - to its latest final
#                   release, majors included, but only to a version this project can actually build
#                   against (see "Android compatibility" below)
#                 - The Gradle wrapper (gradle/wrapper/gradle-wrapper.properties), majors included
#                 - Plugin versions declared in settings.gradle.kts (`id("…") version "…"`), majors included
#               Every Gradle-side bump is then proven by the build at the end: one that breaks it is
#               found, taken back and stepped down to the newest version that builds (see "Build
#               verification"), which is what makes crossing a major safe to do unattended.
#                 - sandbox/Dockerfile toolchain stages:
#                     JDK   - eclipse-temurin:X.Y.Z_B-jdk: the newest patch, and a newer LTS major only
#                             once the project is proven to build on it (see "Java" below)
#                     Maven - maven:X.Y.Z-eclipse-temurin-<jdk major>, within the Maven major
#                     Node  - node:X.Y.Z-trixie, within the current major, or to a newer major only once
#                             that major has reached LTS
#                 - sandbox/Dockerfile's Debian runtime base (FROM debian:X.Y, within the current major)
#                 - sandbox/Dockerfile's Debian packages (# BEGIN/END DEBIAN PACKAGES blocks)
#                 - sandbox/Dockerfile's Android cmdline-tools download (URL + sha1, from Google's own SDK
#                   repository manifest) and the Playwright CLI pin used for `install-deps`
#                 - GitHub Actions `uses:` references (keeping the pin style: `@v4` stays a major tag) -
#                   or, with --no-workflow-edits, only reported
#                 - The actionlint image pinned in .github/scripts/lint_workflows.sh
#               Then two checks (NOT best-effort - either one failing fails the run):
#                 - a consistency guard over values that must agree across files (JDK major, Android
#                   platform, build-tools)
#                 - a real build (assembleDebug, unit tests, instrumented-test compile, lint - the CI
#                   gates) whenever a Gradle input changed,
#                   so a bump that breaks the build is caught here rather than on the next push to main
#
#               Also proven by a build rather than looked up (see their sections): compileSdk, and the
#               Java version (see "Java" below).
#
#               Deliberately NOT updated (they are product decisions, not version bumps): targetSdk (it
#               opts the app into runtime behaviour changes a unit test cannot see), minSdk and
#               buildToolsVersion (AGP dictates its minimum). The Claude Code CLI version in
#               sandbox/Dockerfile is resolved by sandbox.sh at build time and is not pinned in the file.
#
#               Every resolved version is filtered through is_prerelease() first: a release candidate,
#               alpha/beta, milestone, preview/early-access, nightly or snapshot is NEVER a valid update,
#               whatever the upstream calls "latest".
#
# Java:         ONE Java version, owned by the Gradle toolchain: `toolchainVersion` in
#               gradle/gradle-daemon-jvm.properties. Gradle runs itself on that JDK, compiles and tests
#               with it, and the app is compiled FOR it (app/android/build.gradle.kts reads it) - and Gradle
#               downloads it wherever it is missing, so builds never depend on the installed java. The
#               sandbox's JDK stage follows it (a head start, so nothing needs downloading there; the
#               guard at the end fails the run if the two disagree), and the workflows' setup-java reads
#               it at run time, so no workflow file ever pins it.
#               It moves in two steps, because a JDK major cannot be vetted from metadata -
#               sandbox/Dockerfile's header records the last blind attempt (JDK 26) crashing inside
#               Gradle's kotlin-dsl:
#                 1. Always: the newest patch of the current major (the eclipse-temurin tag in the
#                    sandbox; the toolchain itself always takes the newest patch of its major).
#                 2. Only when proven: after everything else has been updated and built, each newer LTS
#                    (newest first) is written into the toolchain (`./gradlew updateDaemonJvm`) and the
#                    project is built and unit-tested from scratch on it; the first that passes is kept
#                    and written into the sandbox (jdk + maven stages). Non-LTS majors are never tried:
#                    they reach end-of-life six months later.
#
# Android compatibility:
#               AndroidX libraries declare the oldest compileSdk and AGP they can be consumed by (the
#               aar-metadata.properties inside each AAR). A library newer than this project's compileSdk
#               does not just warn - the build fails ("requires libraries and applications that depend on
#               it to compile against version 36 or later"). So each catalog entry is walked from its
#               newest candidate downwards and the first one whose every artifact (for a BOM: every
#               artifact the app takes from it) fits the project's compileSdk and AGP is the one pinned.
#
# Usage:        .github/scripts/update_dependency_versions.sh [--skip-build]
#               (Run from the repository root.)
#                 --skip-build          do not run the Gradle build at the end (the version bumps still
#                                       apply, but a JDK major or compileSdk is never adopted, since
#                                       nothing proved it builds)
#                 --no-workflow-edits   leave .github/workflows/ untouched: newer GitHub Actions are only
#                                       REPORTED (in the summary, and UPDATE_NOTES_FILE). For a run that
#                                       pushes with GITHUB_TOKEN, which may not change workflow files -
#                                       see .github/workflows/update-dependencies.yml. Nothing else this
#                                       script updates lives in a workflow file.
#               Environment:
#                 UPDATE_SUMMARY_FILE   if set, every change made ("what: old → new", one per line) is
#                                       written there too (.github/workflows/update-dependencies.yml
#                                       uses it as its commit message)
#                 UPDATE_NOTES_FILE     if set, updates found but deliberately not applied (newer actions
#                                       under --no-workflow-edits) are written there, one per line
#                 GITHUB_TOKEN          if set, used for GitHub API lookups (a higher rate limit)
#
# Requirements: bash, awk, curl, docker, git, jq, sed, sha256sum, sort (GNU, for -V), unzip; and, for
#               the builds, any JDK to launch Gradle with (Gradle fetches the toolchain JDK itself), the
#               Android SDK and ffmpeg (the sandbox image has all of these)
#
# Exit codes:   0 - success (including best-effort partial updates)
#               1 - hard failure: a missing required file, a guarded group of versions that disagree
#                   across files, or a Gradle build that failed after the updates
# ------------------------------------------------------------------------------

# This script is deliberately best-effort under `set -e`: every external step is a predicate
# (hub_tag_exists) or is guarded with `|| warn`/`|| true` so one failure never aborts the run
# (only a missing required file does). That intentional pattern - invoking functions in a
# condition while `set -e` is active - is exactly what SC2310 flags, so disable it file-wide here.
# `set -e` is kept on purpose: it still aborts on unexpected failures inside the apply sections.
# shellcheck disable=SC2310
set -euo pipefail

SANDBOX_DOCKERFILE="./sandbox/Dockerfile"
WORKFLOWS_DIR=".github/workflows"
VERSION_CATALOG="./gradle/libs.versions.toml"
GRADLE_WRAPPER_PROPERTIES="./gradle/wrapper/gradle-wrapper.properties"
SETTINGS_GRADLE="./settings.gradle.kts"
APP_BUILD_GRADLE="./app/android/build.gradle.kts"

# Everything the final build depends on. Hashed before and after the updates, so the (slow) Gradle
# build only runs when one of them actually changed.
GRADLE_DAEMON_JVM_PROPERTIES="./gradle/gradle-daemon-jvm.properties"
GRADLE_INPUTS=("${VERSION_CATALOG}" "${GRADLE_WRAPPER_PROPERTIES}" "${SETTINGS_GRADLE}" "${APP_BUILD_GRADLE}"
               "${GRADLE_DAEMON_JVM_PROPERTIES}")

GOOGLE_MAVEN="https://dl.google.com/android/maven2"
MAVEN_CENTRAL="https://repo1.maven.org/maven2"
GRADLE_PLUGIN_PORTAL="https://plugins.gradle.org/m2"
ANDROID_SDK_REPOSITORY="https://dl.google.com/android/repository"

SKIP_BUILD=false
NO_WORKFLOW_EDITS=false
for arg in "${@}"; do
    case "${arg}" in
        --skip-build) SKIP_BUILD=true ;;
        --no-workflow-edits) NO_WORKFLOW_EDITS=true ;;
        -h|--help)
            sed -n '2,/^# ----/p' "${0}" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            echo "❌ Unknown argument: ${arg} (see --help)" >&2
            exit 1
            ;;
    esac
done

# Scratch space for downloaded Maven metadata and AARs (a candidate is often checked for several
# libraries, and a BOM's artifacts for several candidates, so downloads are cached per run).
WORK_DIR=$(mktemp -d)
trap 'rm -rf "${WORK_DIR}"' EXIT

# ── Output helpers ────────────────────────────────────────────────────────────

ok()   { echo "  ✅ ${*}"; }
warn() { echo "  ⚠️  ${*}" >&2; }
err()  { echo "  ❌ ${*}" >&2; }

# ── curl wrappers ─────────────────────────────────────────────────────────────

curl_get() { curl -fsSL --connect-timeout 10 --max-time 60 "${@}"; }

if [[ -n "${GITHUB_TOKEN:-}" ]]; then
    github_curl() { curl -fsSL --connect-timeout 10 --max-time 60 -H "Authorization: Bearer ${GITHUB_TOKEN}" "${@}"; }
else
    github_curl() { curl -fsSL --connect-timeout 10 --max-time 60 "${@}"; }
fi

# Returns 0 if the Docker Hub tag exists, 1 otherwise.
hub_tag_exists() {
    local repo="${1}" tag="${2}"
    local status
    # HEAD only (-I, no body): the response body is otherwise streamed to /dev/null, which on WSL2
    # can abort mid-transfer with "curl: (23) client returned ERROR on write of N bytes". A HEAD has
    # nothing to write. `-f` is deliberately omitted (a 404 is the expected "does not exist" answer,
    # not an error): with it, curl aborts a 404 as "curl: (22) ... 404" on stderr instead of handing
    # back the status code. stderr is suppressed and a hard curl failure (network) returns 1.
    status=$(curl -sIL -o /dev/null -w '%{http_code}' \
        --connect-timeout 10 --max-time 30 \
        "https://hub.docker.com/v2/repositories/${repo}/tags/${tag}" 2>/dev/null) || return 1
    [[ "${status}" == "200" ]]
}

# ── Version helpers ───────────────────────────────────────────────────────────

# Returns 0 if ${1} is a strictly higher version than ${2}.
version_gt() {
    [[ "${1}" != "${2}" ]] || return 1
    local highest
    highest=$(printf '%s\n%s\n' "${1}" "${2}" | sort -V | tail -1) || return 1
    [[ "${highest}" == "${1}" ]]
}

# The leading numeric component: 2.2.21 -> 2, 21.0.12_8 -> 21, 2026.06.01 -> 2026.
major_of() {
    local version="${1}"
    echo "${version%%[!0-9]*}"
}

# ── Pre-release filtering ─────────────────────────────────────────────────────
# Nothing that is not a final release may ever be pinned: a release candidate, alpha/beta,
# milestone, preview, early-access build, nightly or snapshot is unstable by definition, and an
# unattended bump is exactly where one must not slip in. Upstreams cannot be trusted to say so
# themselves - Apache Maven published `maven-3.10.0-rc-1` WITHOUT GitHub's pre-release flag, so
# `releases/latest` served it as the latest release - hence the marker check below.
#
# A marker only counts as one when it is a whole token (delimited by a non-alphanumeric, or the
# start/end of the string) optionally followed by digits: `3.10.0-rc-1`, `1.0.0-alpha02`,
# `2.3.0-Beta1` and `2.0_beta` are rejected, while `1.5.0-0.1`, `21.0.12_8` and
# `5:29.7.2-1~debian.13~trixie` are not. A bare `m` is deliberately NOT a marker (far too easy to
# hit by accident) - only the `M1`-style milestone form with digits is.
PRERELEASE_MARKERS='alpha|beta|rc|cr|snapshot|milestone|preview|pre|ea|nightly|canary|dev|unstable|m[0-9]+'

# Returns 0 if the given version string is a pre-release, 1 if it is a final release.
is_prerelease() {
    local version="${1,,}"
    [[ "${version}" =~ (^|[^a-z0-9])(${PRERELEASE_MARKERS})[0-9]*([^a-z0-9]|$) ]]
}

# Reads candidate versions on stdin, writes out only the final releases (order preserved).
filter_stable_versions() {
    local version
    while IFS= read -r version; do
        [[ -n "${version}" ]] || continue
        is_prerelease "${version}" || printf '%s\n' "${version}"
    done
}

# Reads versions on stdin, writes out only those sharing ${1}'s major (order preserved).
filter_same_major() {
    local want version
    want=$(major_of "${1}")
    while IFS= read -r version; do
        [[ "${version%%[!0-9]*}" == "${want}" ]] && printf '%s\n' "${version}"
    done
    return 0
}

# Echoes the latest STABLE release tag of a GitHub repo (empty if none could be resolved).
# `releases/latest` alone is not enough: GitHub only skips releases the project remembered to
# FLAG as a pre-release, so an unflagged one is served as latest. When that happens, fall back to
# the HIGHEST-VERSIONED unflagged, non-pre-release entry in the release list.
#
# The fallback deliberately sorts by version (sort -V) rather than taking the API's newest-first
# order: release dates do not run in version order, so a patch cut on an older branch AFTER a
# newer release (a backport, a security fix on an LTS line) is the most recent release while
# being the lower version, and picking it would silently walk the pin BACKWARDS.
latest_stable_github_release() {
    local repo="${1}"

    local tag
    tag=$(github_curl "https://api.github.com/repos/${repo}/releases/latest" | jq -r '.tag_name // empty') || tag=""
    if [[ -n "${tag}" ]] && ! is_prerelease "${tag}"; then
        printf '%s\n' "${tag}"
        return 0
    fi
    [[ -n "${tag}" ]] && warn "Ignoring pre-release '${tag}' offered as ${repo}'s latest release"

    # sort -V compares the numeric runs as numbers, so a shared tag prefix is harmless
    # (`maven-3.9.16` > `maven-3.9.9`) - and `tail` drains its input, so no SIGPIPE under pipefail.
    github_curl "https://api.github.com/repos/${repo}/releases?per_page=100" \
        | jq -r '.[] | select(.draft == false and .prerelease == false) | .tag_name // empty' \
        | filter_stable_versions \
        | sort -V | tail -1
}

# ── Maven repository helpers ──────────────────────────────────────────────────
# The same three repositories settings.gradle.kts resolves from, in the same order.

# Echoes every published version of group:name (one per line, unordered), merged across all three
# repositories. Merged rather than first-found: a repository can hold a stale copy of another's
# artifact - Google's Maven has the KSP plugin marker frozen at its 2021 state (newest 1.5.30-1.0.0),
# so reading only the first repository that knew KSP silently reported it as up to date.
maven_versions() {
    local group="${1%%:*}" name="${1#*:}"
    local path="${group//.//}/${name}/maven-metadata.xml"
    local repo metadata
    for repo in "${GOOGLE_MAVEN}" "${MAVEN_CENTRAL}" "${GRADLE_PLUGIN_PORTAL}"; do
        if metadata=$(curl_get "${repo}/${path}" 2>/dev/null) && [[ -n "${metadata}" ]]; then
            grep -oP '(?<=<version>)[^<]+' <<< "${metadata}" || true
        fi
    done | sort -u
}

# Downloads group:name:version's .<ext> into the run's cache and echoes its path; returns 1 if no
# repository has it. A miss is remembered too, so a missing .aar is only ever asked for once.
maven_fetch() {
    local group="${1}" name="${2}" version="${3}" ext="${4}"
    local rel="${group//.//}/${name}/${version}/${name}-${version}.${ext}"
    local file="${WORK_DIR}/m2/${rel}"

    [[ -f "${file}" ]] && { echo "${file}"; return 0; }
    [[ -f "${file}.missing" ]] && return 1

    mkdir -p "$(dirname "${file}")"
    local repo
    for repo in "${GOOGLE_MAVEN}" "${MAVEN_CENTRAL}"; do
        if curl_get -o "${file}" "${repo}/${rel}" 2>/dev/null; then
            echo "${file}"
            return 0
        fi
    done
    rm -f "${file}"
    touch "${file}.missing"
    return 1
}

# Echoes the aar-metadata.properties of group:name:version (empty for a plain jar, or anything
# without one). Kotlin Multiplatform artifacts (lifecycle, room, compose, …) publish no AAR under
# their root name - their Gradle module metadata redirects Android consumers to a `-android`
# sibling (`available-at`), which is where the AAR, and so the metadata, actually lives.
aar_metadata() {
    local group="${1}" name="${2}" version="${3}"
    local aar module target

    if aar=$(maven_fetch "${group}" "${name}" "${version}" aar); then
        unzip -p "${aar}" META-INF/com/android/build/gradle/aar-metadata.properties 2>/dev/null || true
        return 0
    fi

    module=$(maven_fetch "${group}" "${name}" "${version}" module) || return 0
    target=$(jq -r 'first(.variants[]? | .["available-at"]? // empty
                    | select(.module | endswith("-android"))
                    | "\(.group) \(.module) \(.version)") // empty' "${module}" 2>/dev/null) || target=""
    [[ -n "${target}" ]] || return 0

    local t_group t_name t_version
    read -r t_group t_name t_version <<< "${target}"
    if aar=$(maven_fetch "${t_group}" "${t_name}" "${t_version}" aar); then
        unzip -p "${aar}" META-INF/com/android/build/gradle/aar-metadata.properties 2>/dev/null || true
    fi
    return 0
}

# Returns 0 if group:name:version can be consumed by this project (its compileSdk and AGP), 1 with a
# one-line reason on stdout if not.
android_compatible() {
    local group="${1}" name="${2}" version="${3}"
    local metadata min_sdk min_agp
    metadata=$(aar_metadata "${group}" "${name}" "${version}")
    [[ -n "${metadata}" ]] || return 0

    min_sdk=$(grep -oP '^minCompileSdk=\K[0-9]+' <<< "${metadata}" || true)
    min_agp=$(grep -oP '^minAndroidGradlePluginVersion=\K\S+' <<< "${metadata}" || true)

    if [[ -n "${min_sdk}" ]] && (( min_sdk > PROJECT_COMPILE_SDK )); then
        echo "${group}:${name}:${version} needs compileSdk ${min_sdk} (project: ${PROJECT_COMPILE_SDK})"
        return 1
    fi
    local agp
    agp=$(catalog_agp_version)
    if [[ -n "${min_agp}" && -n "${agp}" ]] && version_gt "${min_agp}" "${agp}"; then
        echo "${group}:${name}:${version} needs AGP ${min_agp} (project: ${agp})"
        return 1
    fi
    return 0
}

# ── Change summary ────────────────────────────────────────────────────────────
# Every change the run actually makes, as "what: old → new", printed at the end - and written to
# $UPDATE_SUMMARY_FILE when set, which is how the update-dependencies workflow gets its commit message.
# Gradle-side bumps are not recorded as they are made: verify_build may take one back or step it down,
# so they are read from GRADLE_CHANGES and the files' final state when the summary is printed.

UPDATE_SUMMARY=()
UPDATE_NOTES=()

record_update() {
    UPDATE_SUMMARY+=("${1}")
}

# An update that was found but deliberately not applied (see --no-workflow-edits).
record_note() {
    UPDATE_NOTES+=("${1}")
}

# The version a Gradle-side change (see record_gradle_change) currently stands at.
current_gradle_version() {
    local kind="${1}" key="${2}"
    case "${kind}" in
        wrapper) grep -oP '^distributionUrl=.*gradle-\K[0-9.]+(?=-(bin|all)\.zip)' "${GRADLE_WRAPPER_PROPERTIES}" || true ;;
        plugin)  grep -oP "id\\(\"${key//./\\.}\"\\)\\s+version\\s+\"\\K[^\"]+" "${SETTINGS_GRADLE}" || true ;;
        catalog) echo "${CATALOG_VERSION[${key}]:-}" ;;
        *) ;;
    esac
}

print_update_summary() {
    local lines=() change kind key old new now
    for change in "${GRADLE_CHANGES[@]}"; do
        IFS='|' read -r kind key old new <<< "${change}"
        now=$(current_gradle_version "${kind}" "${key}")
        [[ -n "${now}" && "${now}" != "${old}" ]] && lines+=("${key}: ${old} → ${now}")
    done
    lines+=("${UPDATE_SUMMARY[@]}")

    echo
    echo "📋 Changes made:"
    if [[ "${#lines[@]}" -eq 0 ]]; then
        echo "   (none)"
    else
        printf '   - %s\n' "${lines[@]}"
    fi
    if [[ -n "${UPDATE_SUMMARY_FILE:-}" ]]; then
        if [[ "${#lines[@]}" -eq 0 ]]; then
            : > "${UPDATE_SUMMARY_FILE}"
        else
            printf '%s\n' "${lines[@]}" > "${UPDATE_SUMMARY_FILE}"
        fi
    fi

    if [[ "${#UPDATE_NOTES[@]}" -gt 0 ]]; then
        echo "📋 Available but not applied:"
        printf '   - %s\n' "${UPDATE_NOTES[@]}"
    fi
    if [[ -n "${UPDATE_NOTES_FILE:-}" ]]; then
        if [[ "${#UPDATE_NOTES[@]}" -eq 0 ]]; then
            : > "${UPDATE_NOTES_FILE}"
        else
            printf '%s\n' "${UPDATE_NOTES[@]}" > "${UPDATE_NOTES_FILE}"
        fi
    fi
}

# ── Android SDK helpers ───────────────────────────────────────────────────────

# Google's SDK repository manifest (the one sdkmanager reads), fetched once per run.
sdk_repository_manifest() {
    local file="${WORK_DIR}/repository2-3.xml"
    [[ -s "${file}" ]] || curl_get -o "${file}" "${ANDROID_SDK_REPOSITORY}/repository2-3.xml" 2>/dev/null || return 1
    cat "${file}"
}

# The SDK Gradle builds against: local.properties' sdk.dir when that directory exists (it is often a
# host path that does not exist in the sandbox), else ANDROID_HOME / ANDROID_SDK_ROOT.
android_sdk_dir() {
    local dir=""
    [[ -f ./local.properties ]] && dir=$(grep -oP '^sdk\.dir=\K.*' ./local.properties | head -1 || true)
    [[ -n "${dir}" && -d "${dir}" ]] || dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    [[ -n "${dir}" && -d "${dir}" ]] && echo "${dir}"
    return 0
}

# Installs an SDK package (e.g. "platforms;android-36") into that SDK if it is not already there, via
# sudo when the SDK is not writable (the sandbox's /opt/android-sdk is root-owned). ${2} is the
# directory under the SDK that proves it landed.
install_sdk_package() {
    local package="${1}" check_dir="${2}"
    local sdk sdkmanager
    sdk=$(android_sdk_dir)
    [[ -n "${sdk}" ]] || { warn "No Android SDK found (local.properties sdk.dir / ANDROID_HOME)"; return 1; }
    [[ -d "${sdk}/${check_dir}" ]] && return 0

    sdkmanager="${sdk}/cmdline-tools/latest/bin/sdkmanager"
    [[ -x "${sdkmanager}" ]] || { warn "No sdkmanager at ${sdkmanager}"; return 1; }
    local runner=()
    if [[ ! -w "${sdk}" ]]; then
        sudo -n true 2>/dev/null || { warn "${sdk} is not writable and sudo needs a password"; return 1; }
        runner=(sudo -n)
    fi
    # `yes` answers the licence prompt; it dies of SIGPIPE once sdkmanager stops reading, so the
    # exit status is ignored and the directory check below is what decides.
    yes 2>/dev/null | "${runner[@]}" "${sdkmanager}" --sdk_root="${sdk}" "${package}" >/dev/null 2>&1 || true
    [[ -d "${sdk}/${check_dir}" ]]
}

# ── Gradle build helpers ──────────────────────────────────────────────────────
# One build = the release workflow's own gates. The output goes to a log
# rather than the terminal - a run can build a dozen times - and only the failure is shown.

BUILD_LOG="${WORK_DIR}/gradle-build.log"

# The same gates CI applies (.github/workflows/release.yml): the debug build, the unit tests, the
# instrumented tests compiling, and lint - so a bump that, say, deprecates an API only a test uses is
# caught here rather than on the next push. No JAVA_HOME juggling: the Gradle toolchain
# (gradle/gradle-daemon-jvm.properties) decides which JDK the build runs and compiles on, and Gradle
# fetches it if this machine lacks it. Extra Gradle arguments may be passed.
run_gradle_build() {
    ./gradlew --no-daemon --console=plain "${@}" \
        assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin lintDebug > "${BUILD_LOG}" 2>&1
}

# Prints the "What went wrong" part of the last build's output (or its tail, if there is none).
show_build_failure() {
    local what line
    what=$(awk '/^\* What went wrong/ { f = 1 } /^\* Try:/ { f = 0 } f' "${BUILD_LOG}" | head -25 || true)
    [[ -n "${what}" ]] || what=$(tail -25 "${BUILD_LOG}" || true)
    while IFS= read -r line; do
        echo "       ${line}" >&2
    done <<< "${what}"
}

# ── 0. compileSdk (proven by a build) ─────────────────────────────────────────
# compileSdk decides which AndroidX releases the catalog step may take (each declares a minimum), so
# leaving it behind freezes most of the catalog. It is adopted the same way as a JDK major: for each
# newer stable Android platform (newest first), install it, set compileSdk, and build + unit-test the
# project exactly as committed; the first that passes is kept, and written into sandbox/Dockerfile's
# sdkmanager install + its check (the workflows pin no platform - .github/scripts/android_sdk_packages.sh
# reads it from app/android/build.gradle.kts at run time). Runs FIRST, before any dependency moves, so a failure is the platform's alone - and so the
# catalog step then vets libraries against the new value.
#
# Minor SDK releases (36.1, 37.2) are platforms in their own right - each its own SDK package, adding
# APIs - so a platform is identified as API.MINOR throughout ("36" is 36.0): compileSdk carries the API
# level and compileSdkMinor the minor (the line is omitted for .0). Only compileSdk has a minor;
# targetSdk and minSdk are whole API levels in AGP's DSL. Package names differ by era - android-36 for
# a base release before API 37, android-37.0 from API 37, android-36.1 / android-37.2 for minors - so
# the name is always taken from the manifest, never built. Canary/preview platforms (which Google also
# lists on the stable channel, with a codename) and the -extN extension-level packages are never taken.
#
# compileSdk only changes the API surface the app is compiled against. targetSdk is NOT moved: it
# opts the app into each release's runtime behaviour changes, which unit tests cannot catch and which
# need testing on a device - that stays a manual decision.

# compileSdk's API level alone (what AndroidX's minCompileSdk is compared with).
read_compile_sdk() {
    grep -oP '^\s*compileSdk\s*=\s*\K[0-9]+' "${APP_BUILD_GRADLE}" | head -1 || true
}

# compileSdk as API.MINOR (compileSdkMinor defaults to 0).
read_compile_sdk_version() {
    local api minor
    api=$(read_compile_sdk)
    [[ -n "${api}" ]] || return 0
    minor=$(grep -oP '^\s*compileSdkMinor\s*=\s*\K[0-9]+' "${APP_BUILD_GRADLE}" | head -1 || true)
    echo "${api}.${minor:-0}"
}

# Echoes "<API.MINOR> <package>" for every stable platform in the SDK manifest, newest first
# (e.g. "37.2 android-37.2", "37.0 android-37.0", "36.1 android-36.1", "36.0 android-36").
stable_android_platforms() {
    local manifest
    manifest=$(sdk_repository_manifest) || return 0
    awk '
        /<remotePackage path="platforms;/ {
            path = $0; sub(/.*path="platforms;/, "", path); sub(/".*/, "", path)
            in_pkg = 1; stable = 0; codename = ""
        }
        in_pkg && /<codename>[^<]+<\/codename>/ { codename = $0 }
        in_pkg && /<channelRef ref="channel-0"\/>/ { stable = 1 }
        in_pkg && /<\/remotePackage>/ {
            in_pkg = 0
            if (stable && codename == "" && path ~ /^android-[0-9]+(\.[0-9]+)?$/) {
                version = path; sub(/^android-/, "", version)
                if (version !~ /\./) version = version ".0"
                print version, path
            }
        }
    ' <<< "${manifest}" | sort -V -r -k1,1 | awk '!seen[$1]++'
}

# Writes API.MINOR into app/android/build.gradle.kts: compileSdk = API, and compileSdkMinor = MINOR (the line
# added after compileSdk if missing, removed for a .0 release).
set_compile_sdk() {
    local api="${1%%.*}" minor="${1#*.}"
    sed -i -E "s|^([[:space:]]*compileSdk[[:space:]]*=[[:space:]]*)[0-9]+|\\1${api}|" "${APP_BUILD_GRADLE}"
    if [[ "${minor}" == "0" ]]; then
        sed -i -E '/^[[:space:]]*compileSdkMinor[[:space:]]*=/d' "${APP_BUILD_GRADLE}"
    elif grep -qE '^[[:space:]]*compileSdkMinor[[:space:]]*=' "${APP_BUILD_GRADLE}"; then
        sed -i -E "s|^([[:space:]]*compileSdkMinor[[:space:]]*=[[:space:]]*)[0-9]+|\\1${minor}|" "${APP_BUILD_GRADLE}"
    else
        sed -i -E "s|^([[:space:]]*)(compileSdk[[:space:]]*=[[:space:]]*[0-9]+)[[:space:]]*$|\\1\\2\\n\\1compileSdkMinor = ${minor}|" "${APP_BUILD_GRADLE}"
    fi
}

update_compile_sdk() {
    echo
    echo "🔍 Checking for a newer Android platform (compileSdk)..."

    local current
    current=$(read_compile_sdk_version)
    if [[ -z "${current}" ]]; then
        warn "Could not read compileSdk from ${APP_BUILD_GRADLE}, skipping"
        return 0
    fi
    echo "  Current: compileSdk ${current}"

    local candidates=() version package platforms
    platforms=$(stable_android_platforms)
    while read -r version package; do
        [[ -n "${version}" ]] && version_gt "${version}" "${current}" && candidates+=("${version} ${package}")
    done <<< "${platforms}"
    if [[ "${#candidates[@]}" -eq 0 ]]; then
        echo "  compileSdk=${current} (already the newest stable platform)"
        return 0
    fi
    if [[ "${SKIP_BUILD}" == "true" ]]; then
        warn "Android ${candidates[0]%% *} is available, but --skip-build means it cannot be proven - not adopted"
        return 0
    fi

    local candidate backup="${WORK_DIR}/app-build.gradle.kts.orig"
    cp "${APP_BUILD_GRADLE}" "${backup}"

    for candidate in "${candidates[@]}"; do
        read -r version package <<< "${candidate}"
        echo "  --- compileSdk ${version} (platforms;${package})"
        if ! install_sdk_package "platforms;${package}" "platforms/${package}"; then
            warn "Could not install platforms;${package}, skipping it"
            continue
        fi

        set_compile_sdk "${version}"
        echo "    Building..."
        if run_gradle_build; then
            # The sandbox's platform pin, whatever era its name is from (android-35, android-37.0,
            # android-36.1). The workflows pin none - they ask .github/scripts/android_sdk_packages.sh,
            # which reads app/android/build.gradle.kts.
            sed -i -E "s#platforms([;/])android-[0-9]+(\\.[0-9]+)?([^0-9.a-z-]|$)#platforms\\1${package}\\3#g" "${SANDBOX_DOCKERFILE}"
            PROJECT_COMPILE_SDK="${version%%.*}"
            record_update "compileSdk: ${current} → ${version}"
            ok "compileSdk ${current} → ${version}: the build and unit tests pass on it (targetSdk is not moved - see above)"
            return 0
        fi
        warn "The build fails with compileSdk ${version}:"
        show_build_failure
        cp "${backup}" "${APP_BUILD_GRADLE}"
    done

    echo "  compileSdk=${current} (no newer platform builds)"
    return 0
}

# ── 1. Java (sandbox/Dockerfile jdk stage) ────────────────────────────────────
# Step 1 of the two in the header's "Java" note: the newest eclipse-temurin:<major>…-jdk tag for the
# major ALREADY pinned. It also lists every newer LTS major that has a -jdk tag on Docker Hub in
# JDK_MAJOR_CANDIDATES; try_jdk_major_upgrade (step 2, after the build) decides whether any is adopted.

# The Java version the project is on: the Gradle toolchain's toolchainVersion.
read_toolchain_version() {
    grep -oP '^toolchainVersion=\K[0-9]+' "${GRADLE_DAEMON_JVM_PROPERTIES}" 2>/dev/null | head -1 || true
}

# "<major> <tag>" per newer LTS, newest first.
JDK_MAJOR_CANDIDATES=()

# Echoes the newest eclipse-temurin:<major>…-jdk tag on Docker Hub (empty if none).
# Resolved from Adoptium's own list of GA releases (newest first), each mapped to the tag Temurin
# publishes for it - `21.0.12.1+1-LTS` -> `21.0.12.1_1-jdk`, a first GA `25+36-LTS` -> `25_36-jdk` -
# and taken from the first one Docker Hub confirms (images can land a little after the release).
# Docker Hub's tag listing is NOT used to find it: its `ordering` parameter is ignored, so a major with
# hundreds of tags only ever returns its OLDEST hundred, and the "newest" found there is years stale.
latest_temurin_jdk_tag() {
    local major="${1}"
    local releases release semver build tag
    releases=$(curl_get "https://api.adoptium.net/v3/info/release_versions?release_type=ga&version=%5B${major}%2C$((major + 1))%29&sort_order=DESC&page_size=10" \
        | jq -r '.versions[]?.openjdk_version // empty' 2>/dev/null || true)

    while IFS= read -r release; do
        [[ "${release}" =~ ^([0-9][0-9.]*)\+([0-9]+) ]] || continue
        semver="${BASH_REMATCH[1]}"
        build="${BASH_REMATCH[2]}"
        [[ "${semver%%[!0-9]*}" == "${major}" ]] || continue
        is_prerelease "${release}" && continue
        tag="${semver}_${build}-jdk"
        if hub_tag_exists "library/eclipse-temurin" "${tag}"; then
            echo "${tag}"
            return 0
        fi
    done <<< "${releases}"
    return 0
}

update_java() {
    echo
    echo "🔍 Fetching latest Java versions..."

    local current_tag major
    current_tag=$(grep -oP '^FROM eclipse-temurin:\K\S+(?= AS jdk)' "${SANDBOX_DOCKERFILE}" | head -1 || true)
    if [[ -z "${current_tag}" ]]; then
        warn "No 'FROM eclipse-temurin:… AS jdk' stage in ${SANDBOX_DOCKERFILE}, skipping"
        return 0
    fi
    # The toolchain owns the major; the sandbox follows it (and is realigned here if it drifted).
    major=$(read_toolchain_version)
    [[ -n "${major}" ]] || major=$(major_of "${current_tag}")
    echo "  Current: toolchain JDK ${major}, sandbox ${current_tag}"

    # Newer LTS majors: candidates for step 2, which only adopts one the project builds on.
    local lts
    while IFS= read -r lts; do
        if [[ -z "${lts}" ]] || (( lts <= major )); then
            continue
        fi
        local lts_tag
        lts_tag=$(latest_temurin_jdk_tag "${lts}")
        if [[ -n "${lts_tag}" ]]; then
            JDK_MAJOR_CANDIDATES+=("${lts} ${lts_tag}")
            echo "  JDK ${lts} LTS is available (${lts_tag}) - adopted only if the build passes on it"
        fi
    done < <(curl_get "https://api.adoptium.net/v3/info/available_releases" \
        | jq -r '.available_lts_releases[]? // empty' 2>/dev/null | sort -rn || true)

    local jdk_tag
    jdk_tag=$(latest_temurin_jdk_tag "${major}")
    if [[ -z "${jdk_tag}" ]]; then
        warn "No eclipse-temurin:${major}*-jdk tag confirmed on Docker Hub, skipping the patch update"
        return 0
    fi

    # Never move backwards. The version decides and the build number only breaks a tie - folding the
    # build in as one more component would rank 21.0.12_8 above 21.0.12.1_1.
    local current_cmp="${current_tag%-jdk}" latest_cmp="${jdk_tag%-jdk}" newer=false
    if [[ "${current_cmp%%[!0-9]*}" != "${major}" ]]; then
        newer=true   # the sandbox is on another major than the toolchain: realign it
    elif [[ "${latest_cmp%_*}" == "${current_cmp%_*}" ]]; then
        (( ${latest_cmp##*_} > ${current_cmp##*_} )) && newer=true
    elif version_gt "${latest_cmp%_*}" "${current_cmp%_*}"; then
        newer=true
    fi
    if [[ "${newer}" != "true" ]]; then
        echo "  eclipse-temurin=${current_tag} (already up-to-date)"
    else
        echo "  eclipse-temurin: ${current_tag} → ${jdk_tag}"
        sed -i "s|^FROM eclipse-temurin:[^ ]* AS jdk|FROM eclipse-temurin:${jdk_tag} AS jdk|" "${SANDBOX_DOCKERFILE}"
        record_update "Sandbox JDK image: ${current_tag} → ${jdk_tag}"
    fi

    ok "Java processed (jdk: ${jdk_tag})"
}

# ── 2. Maven (sandbox/Dockerfile maven stage) ─────────────────────────────────
# Only /usr/share/maven is copied out of this image - its own JDK is never used - so the tag's
# eclipse-temurin suffix is set to the jdk stage's major rather than tracked separately: an LTS
# suffix is the one Docker Hub keeps publishing, where a short-lived non-LTS one eventually stops
# getting new Maven tags and would silently stall this update. Stays within the Maven major.

update_maven() {
    echo
    echo "🔍 Fetching latest Maven version (within the current major)..."

    local current jdk_major
    current=$(grep -oP '^FROM maven:\K[0-9.]+(?=-eclipse-temurin-[0-9]+ AS maven)' "${SANDBOX_DOCKERFILE}" | head -1 || true)
    if [[ -z "${current}" ]]; then
        warn "No 'FROM maven:…-eclipse-temurin-N AS maven' stage in ${SANDBOX_DOCKERFILE}, skipping"
        return 0
    fi
    jdk_major=$(grep -oP '^FROM eclipse-temurin:\K[0-9]+(?=[._])' "${SANDBOX_DOCKERFILE}" | head -1 || true)
    if [[ -z "${jdk_major}" ]]; then
        warn "Could not read the jdk stage's major from ${SANDBOX_DOCKERFILE}, skipping Maven update"
        return 0
    fi
    echo "  Current: ${current}"

    local latest
    latest=$(maven_versions "org.apache.maven:apache-maven" \
        | filter_stable_versions \
        | filter_same_major "${current}" \
        | sort -V | tail -1)
    if [[ -z "${latest}" ]]; then
        warn "Could not fetch a stable Maven version from Maven Central, skipping"
        return 0
    fi

    local docker_tag="${latest}-eclipse-temurin-${jdk_major}"
    if ! hub_tag_exists "library/maven" "${docker_tag}"; then
        warn "maven:${docker_tag} not on Docker Hub, skipping Maven update"
        return 0
    fi
    echo "  maven:${docker_tag} confirmed on Docker Hub"

    local old_tag
    old_tag=$(grep -oP '^FROM maven:\K\S+(?= AS maven)' "${SANDBOX_DOCKERFILE}" | head -1 || true)
    if [[ "${old_tag}" != "${docker_tag}" ]]; then
        sed -i "s|^FROM maven:[^ ]* AS maven|FROM maven:${docker_tag} AS maven|" "${SANDBOX_DOCKERFILE}"
        record_update "Sandbox Maven image: ${old_tag} → ${docker_tag}"
    fi

    ok "Maven processed → ${docker_tag}"
}

# ── 3. Node (sandbox/Dockerfile node stage) ───────────────────────────────────
# Node runs Claude Code and Playwright in the sandbox; nothing in the app build uses it. It moves to
# the newest release of the current major, or of a NEWER major once that major has reached LTS - an
# odd-numbered or not-yet-LTS "Current" line is never adopted. Never moves backwards.
# Pre-condition: node:{version}-trixie exists (trixie = Debian 13, matching the runtime base's glibc).

update_node() {
    echo
    echo "🔍 Fetching latest Node.js version..."

    local current current_major
    current=$(grep -oP '^FROM node:\K[0-9.]+(?=-trixie AS node)' "${SANDBOX_DOCKERFILE}" | head -1 || true)
    if [[ -z "${current}" ]]; then
        warn "No 'FROM node:X.Y.Z-trixie AS node' stage in ${SANDBOX_DOCKERFILE}, skipping"
        return 0
    fi
    current_major=$(major_of "${current}")
    echo "  Current: ${current} (major ${current_major})"

    local latest
    latest=$(curl_get "https://nodejs.org/dist/index.json" \
        | jq -r --argjson m "${current_major}" '
            .[]
            | (.version | ltrimstr("v")) as $v
            | ($v | split(".")[0] | tonumber) as $maj
            | select($maj == $m or ($maj > $m and .lts != false))
            | $v' \
        | filter_stable_versions \
        | sort -V | tail -1 || true)
    if [[ -z "${latest}" ]]; then
        warn "Could not fetch a stable Node.js version from nodejs.org, skipping"
        return 0
    fi

    local trixie_tag="${latest}-trixie"
    if ! hub_tag_exists "library/node" "${trixie_tag}"; then
        warn "node:${trixie_tag} not on Docker Hub, skipping Node update"
        return 0
    fi
    echo "  node:${trixie_tag} confirmed on Docker Hub"

    sed -i "s|^FROM node:[0-9.]*-trixie AS node|FROM node:${trixie_tag} AS node|" "${SANDBOX_DOCKERFILE}"
    [[ "${current}" != "${latest}" ]] && record_update "Sandbox Node: ${current} → ${latest}"
    # The stage comment names the major; keep it truthful if an LTS move happened.
    local latest_major
    latest_major=$(major_of "${latest}")
    sed -i -E "s|^(#[[:space:]]+Node )[0-9]+( +—)|\\1${latest_major}\\2|" "${SANDBOX_DOCKERFILE}"

    ok "Node processed → ${latest}"
}

# ── 4. sandbox/Dockerfile runtime base image ──────────────────────────────────
# The sandbox runtime base (`FROM debian:X.Y`) sits outside every `# BEGIN/END ... PACKAGES`
# block, so the apt updater never touches it. MUST run before update_apt_packages: that updater
# resolves each package's latest candidate version by querying the base image named in the file's
# CURRENT `FROM` line, so bumping the base afterward would leave those candidates resolved against
# the stale base.
#
# Stays within the current major rather than ever jumping to the next Debian release on its own -
# a major bump changes the codename (trixie -> forky), which the node stage's -trixie tag and the
# Docker APT repo both follow, and is exactly the kind of thing the file's own "LAYER ORDER" comment
# says should be reviewed, not silently carried by an update run.

update_sandbox_base() {
    echo
    echo "🔍 Fetching latest Debian version (within the current major)..."

    local current major
    current=$(grep -oP '^FROM debian:\K[0-9]+\.[0-9]+' "${SANDBOX_DOCKERFILE}" | head -1 || true)
    if [[ -z "${current}" ]]; then
        warn "No FROM debian:X.Y pin found in ${SANDBOX_DOCKERFILE}, skipping"
        return 0
    fi
    major="${current%%.*}"
    echo "  Current: ${current} (major ${major})"

    local latest
    latest=$(curl_get "https://hub.docker.com/v2/repositories/library/debian/tags?name=${major}.&page_size=100" \
        | jq -r '.results[].name' \
        | grep -E "^${major}\.[0-9]+$" \
        | filter_stable_versions \
        | sort -V | tail -1 || true)
    if [[ -z "${latest}" ]]; then
        warn "Could not resolve latest debian ${major}.x from Docker Hub, skipping"
        return 0
    fi

    if ! hub_tag_exists "library/debian" "${latest}"; then
        warn "debian:${latest} not on Docker Hub, skipping"
        return 0
    fi

    if [[ "${current}" == "${latest}" ]]; then
        echo "  debian=${latest} (already up-to-date)"
    else
        echo "  debian: ${current} → ${latest}"
        record_update "Sandbox Debian base: ${current} → ${latest}"
    fi

    sed -i -E "s|^FROM debian:[0-9]+\.[0-9]+|FROM debian:${latest}|" "${SANDBOX_DOCKERFILE}"

    ok "sandbox Debian base processed → ${latest}"
}

# ── 5. Playwright CLI pin (sandbox/Dockerfile) ────────────────────────────────
# `npx --yes playwright@X.Y.Z install-deps chromium` bakes Chromium's shared libraries into the
# image. The project itself declares no Playwright version to keep in step with, so this follows
# the npm registry's latest final release.

update_playwright() {
    echo
    echo "🔍 Fetching latest Playwright version..."

    local current latest
    current=$(grep -oP 'playwright@\K[0-9][^ ]*' "${SANDBOX_DOCKERFILE}" | head -1 || true)
    if [[ -z "${current}" ]]; then
        echo "  No playwright@X.Y.Z pin in ${SANDBOX_DOCKERFILE}; nothing to update."
        return 0
    fi

    latest=$(curl_get "https://registry.npmjs.org/playwright/latest" | jq -r '.version // empty' || true)
    if [[ -z "${latest}" ]]; then
        warn "Could not fetch latest Playwright version, skipping"
        return 0
    fi
    # The `latest` dist-tag is a convention, not a guarantee - never pin a pre-release.
    if is_prerelease "${latest}"; then
        warn "Latest Playwright (${latest}) is a pre-release, skipping"
        return 0
    fi

    if [[ "${current}" == "${latest}" ]]; then
        echo "  playwright=${latest} (already up-to-date)"
    else
        echo "  playwright: ${current} → ${latest}"
        sed -i -E "s|playwright@[0-9][^ ]*|playwright@${latest}|g" "${SANDBOX_DOCKERFILE}"
        record_update "Sandbox Playwright: ${current} → ${latest}"
    fi

    ok "Playwright processed → ${latest}"
}

# ── 6. Android cmdline-tools (sandbox/Dockerfile) ─────────────────────────────
# The cmdline-tools archive is a direct download verified by sha1, both taken from Google's own SDK
# repository manifest - the same one sdkmanager reads - for the stable-channel `cmdline-tools;latest`
# package's Linux archive. The URL, the checksum and the "(tool revision N.M)" note in the comment
# above them move together or not at all.
#
# The SDK *packages* installed with it (`platforms;android-N`, `build-tools;X.Y.Z`) are NOT bumped:
# they must match compileSdk and buildToolsVersion in app/android/build.gradle.kts, which are code decisions.
# verify_version_sync checks they still agree.

update_android_cmdline_tools() {
    echo
    echo "🔍 Fetching latest Android cmdline-tools..."

    local current_url
    current_url=$(grep -oP 'commandlinetools-linux-[0-9]+_latest\.zip' "${SANDBOX_DOCKERFILE}" | head -1 || true)
    if [[ -z "${current_url}" ]]; then
        echo "  No commandlinetools-linux download in ${SANDBOX_DOCKERFILE}; nothing to update."
        return 0
    fi

    local manifest block
    manifest=$(sdk_repository_manifest || true)
    block=$(awk '/<remotePackage path="cmdline-tools;latest">/ { f = 1 } f { print } f && /<\/remotePackage>/ { exit }' <<< "${manifest}")
    if [[ -z "${block}" ]]; then
        warn "Could not find cmdline-tools;latest in the SDK repository manifest, skipping"
        return 0
    fi
    # channel-0 is "stable"; anything else is beta/dev/canary.
    if ! grep -q '<channelRef ref="channel-0"/>' <<< "${block}"; then
        warn "cmdline-tools;latest is not on the stable channel, skipping"
        return 0
    fi

    local revision archive url sha1
    revision=$(awk -F'[<>]' '/<major>/ { maj = $3 } /<minor>/ { min = $3 } /<\/revision>/ { print maj "." min; exit }' <<< "${block}")
    archive=$(awk -F'[<>]' '
        /<archive>/            { u = ""; c = ""; os = "" }
        /<checksum type="sha1">/ { c = $3 }
        /<url>/                { u = $3 }
        /<host-os>/            { os = $3 }
        /<\/archive>/          { if (os == "linux") { print u, c; exit } }
    ' <<< "${block}")
    read -r url sha1 <<< "${archive}"
    if [[ ! "${url}" =~ ^commandlinetools-linux-[0-9]+_latest\.zip$ || ! "${sha1}" =~ ^[0-9a-f]{40}$ ]]; then
        warn "Unexpected cmdline-tools archive entry (url='${url}', sha1='${sha1}'), skipping"
        return 0
    fi

    if [[ "${current_url}" == "${url}" ]]; then
        echo "  cmdline-tools=${url} (revision ${revision}, already up-to-date)"
        ok "Android cmdline-tools processed"
        return 0
    fi

    if ! curl -fsIL -o /dev/null --connect-timeout 10 --max-time 30 "${ANDROID_SDK_REPOSITORY}/${url}"; then
        warn "${ANDROID_SDK_REPOSITORY}/${url} is not downloadable, skipping"
        return 0
    fi

    echo "  cmdline-tools: ${current_url} → ${url} (revision ${revision})"
    record_update "Sandbox Android cmdline-tools: ${current_url} → ${url} (revision ${revision})"
    sed -i -E \
        -e "s|commandlinetools-linux-[0-9]+_latest\.zip|${url}|g" \
        -e "s|echo \"[0-9a-f]{40}  /tmp/cmdline-tools\.zip\"|echo \"${sha1}  /tmp/cmdline-tools.zip\"|" \
        -e "s|\(tool revision [0-9.]+\)|(tool revision ${revision})|" \
        "${SANDBOX_DOCKERFILE}"

    ok "Android cmdline-tools processed → revision ${revision}"
}

# ── Gradle change tracking ────────────────────────────────────────────────────
# Every Gradle-side bump (wrapper, settings plugin, catalog entry) is recorded, so that when the build
# fails with all of them applied, verify_build can take them back one at a time to find the one
# responsible - and then walk that one down through its intermediate minor releases.

declare -a GRADLE_CHANGES=()        # "kind|key|old|new", kind = wrapper | plugin | catalog
declare -A GRADLE_CHANGE_STEPS=()   # "kind|key" -> fallback versions between old and new, newest first

# Keeps the highest version of each major.minor line from a version list (any order); newest first.
latest_per_minor() {
    sort -V | awk -F. '{ k = $1 "." $2; if (!(k in last)) order[++n] = k; last[k] = $0 }
                       END { for (i = n; i >= 1; i--) print last[order[i]] }'
}

# ${1}=kind ${2}=key ${3}=old ${4}=new, stdin = every candidate version seen for it.
record_gradle_change() {
    local kind="${1}" key="${2}" old="${3}" new="${4}" version steps=""
    while IFS= read -r version; do
        [[ -n "${version}" ]] || continue
        if version_gt "${version}" "${old}" && version_gt "${new}" "${version}"; then
            steps+="${version}"$'\n'
        fi
    done
    GRADLE_CHANGES+=("${kind}|${key}|${old}|${new}")
    GRADLE_CHANGE_STEPS["${kind}|${key}"]=$(latest_per_minor <<< "${steps}" | grep . || true)
}

set_wrapper_version() {
    local version="${1}" dist_type sha256
    dist_type=$(grep -oP '^distributionUrl=.*gradle-[0-9.]+-\K(bin|all)(?=\.zip)' "${GRADLE_WRAPPER_PROPERTIES}") || return 1
    if grep -q '^distributionSha256Sum=' "${GRADLE_WRAPPER_PROPERTIES}"; then
        sha256=$(curl_get "https://services.gradle.org/distributions/gradle-${version}-${dist_type}.zip.sha256" || true)
        if [[ ! "${sha256}" =~ ^[0-9a-f]{64}$ ]]; then
            warn "Could not fetch the sha256 for gradle-${version}-${dist_type}.zip"
            return 1
        fi
        sed -i "s|^distributionSha256Sum=.*|distributionSha256Sum=${sha256}|" "${GRADLE_WRAPPER_PROPERTIES}"
    fi
    sed -i -E "s|gradle-[0-9.]+-(bin\|all)\.zip|gradle-${version}-\\1.zip|" "${GRADLE_WRAPPER_PROPERTIES}"
}

set_settings_plugin_version() {
    local id="${1}" version="${2}"
    sed -i -E "s|(id\(\"${id//./\\.}\"\)[[:space:]]+version[[:space:]]+)\"[^\"]+\"|\\1\"${version}\"|" "${SETTINGS_GRADLE}"
}

set_catalog_version() {
    local ref="${1}" version="${2}"
    sed -i -E "s|^(${ref//./\\.}[[:space:]]*=[[:space:]]*)\"[^\"]*\"|\\1\"${version}\"|" "${VERSION_CATALOG}"
    CATALOG_VERSION["${ref}"]="${version}"
}

set_gradle_version() {
    local kind="${1}" key="${2}" version="${3}"
    case "${kind}" in
        wrapper) set_wrapper_version "${version}" ;;
        plugin)  set_settings_plugin_version "${key}" "${version}" ;;
        catalog) set_catalog_version "${key}" "${version}" ;;
        *) return 1 ;;
    esac
}

# ── 7. Gradle wrapper ─────────────────────────────────────────────────────────
# Rewrites distributionUrl (and distributionSha256Sum, if one is pinned) to the newest final Gradle
# release, majors included - a Gradle major can remove APIs AGP/KGP or this build script still use,
# and verify_build then steps it back down to the newest Gradle that builds. Only the properties file
# is touched: gradle-wrapper.jar is compatible across versions and is refreshed by `./gradlew wrapper`
# whenever someone chooses to.

update_gradle_wrapper() {
    echo
    echo "🔍 Fetching latest Gradle version..."

    local current
    current=$(grep -oP '^distributionUrl=.*gradle-\K[0-9.]+(?=-(bin|all)\.zip)' "${GRADLE_WRAPPER_PROPERTIES}" || true)
    if [[ -z "${current}" ]]; then
        warn "No distributionUrl in ${GRADLE_WRAPPER_PROPERTIES}, skipping"
        return 0
    fi
    echo "  Current: ${current}"

    local all_versions latest
    all_versions=$(curl_get "https://services.gradle.org/versions/all" \
        | jq -r '.[] | select(.snapshot == false and .nightly == false and .releaseNightly == false
                         and .rcFor == "" and .milestoneFor == "" and .broken == false) | .version' \
        | filter_stable_versions || true)
    latest=$(sort -V <<< "${all_versions}" | tail -1)
    if [[ -z "${latest}" ]]; then
        warn "Could not fetch a stable Gradle version from services.gradle.org, skipping"
        return 0
    fi

    if ! version_gt "${latest}" "${current}"; then
        echo "  gradle=${latest} (already up-to-date)"
        ok "Gradle wrapper processed"
        return 0
    fi

    echo "  gradle: ${current} → ${latest}"
    if ! set_wrapper_version "${latest}"; then
        warn "Skipping the Gradle update"
        return 0
    fi
    # A here-string, not a pipe: a pipe would run record_gradle_change in a subshell and lose the record.
    record_gradle_change wrapper gradle "${current}" "${latest}" <<< "${all_versions}"

    ok "Gradle wrapper processed → ${latest}"
}

# ── 8. settings.gradle.kts plugins ────────────────────────────────────────────
# Plugins pinned directly in settings.gradle.kts (`id("…") version "…"`, e.g. the foojay toolchain
# resolver) rather than through the version catalog. Resolved from each plugin's marker artifact,
# majors included (proven by the build, like everything Gradle-side).

update_settings_plugins() {
    echo
    echo "🔍 Updating plugin versions in ${SETTINGS_GRADLE}..."

    local lines=() line
    mapfile -t lines < <(grep -oP 'id\("[^"]+"\)\s+version\s+"[^"]+"' "${SETTINGS_GRADLE}" || true)
    if [[ "${#lines[@]}" -eq 0 ]]; then
        echo "  No versioned plugins found."
        return 0
    fi

    for line in "${lines[@]}"; do
        local id current latest versions
        id=$(grep -oP 'id\("\K[^"]+' <<< "${line}")
        current=$(grep -oP 'version\s+"\K[^"]+' <<< "${line}")
        versions=$(maven_versions "${id}:${id}.gradle.plugin" | filter_stable_versions)
        latest=$(sort -V <<< "${versions}" | tail -1)
        if [[ -z "${latest}" ]]; then
            warn "Could not fetch a stable version of plugin '${id}', skipping"
            continue
        fi
        if ! version_gt "${latest}" "${current}"; then
            echo "  ${id}=${current} (already up-to-date)"
            continue
        fi
        echo "  ${id}: ${current} → ${latest}"
        set_settings_plugin_version "${id}" "${latest}"
        record_gradle_change plugin "${id}" "${current}" "${latest}" <<< "${versions}"
    done

    ok "Settings plugins processed"
}

# ── 9. Gradle version catalog ─────────────────────────────────────────────────
# Every [versions] entry is bumped to the highest final release (majors included) that fits the
# project: every library sharing the entry, and for a BOM every versionless library the
#     app takes from it, must accept this project's compileSdk and AGP (android_compatible), and a BOM
#     must still manage every one of those versionless libraries (a BOM that drops one - as newer ones
#     may drop material-icons-extended - would leave it with no version at all).
# Candidates are tried newest-first, so the first one that passes is the highest that fits; each one
# rejected along the way is printed with its reason. Whether it then BUILDS is verify_build's call.
#
# The catalog is parsed as it is written here: one-line inline tables with group/name (or module) and
# version.ref for libraries, id and version.ref for plugins. A library with an inline `version = "…"`
# instead of a version.ref is not bumped.

declare -a CATALOG_REFS=()
declare -A CATALOG_VERSION=()     # ref -> current version
declare -A CATALOG_COORDS=()      # ref -> space-separated group:name (plugins as their marker artifact)
declare -A CATALOG_IS_PLUGIN=()   # ref -> "true" if any user is a plugin
declare -a CATALOG_UNVERSIONED=() # group:name of libraries with no version (taken from a BOM)

parse_version_catalog() {
    local section="" line
    while IFS= read -r line || [[ -n "${line}" ]]; do
        line="${line%%#*}"
        [[ -z "${line//[[:space:]]/}" ]] && continue
        if [[ "${line}" =~ ^[[:space:]]*\[([a-z-]+)\] ]]; then
            section="${BASH_REMATCH[1]}"
            continue
        fi

        local key="" group="" name="" id="" ref=""
        [[ "${line}" =~ ^[[:space:]]*([A-Za-z0-9_.-]+)[[:space:]]*= ]] && key="${BASH_REMATCH[1]}"
        [[ "${line}" =~ version\.ref[[:space:]]*=[[:space:]]*\"([^\"]+)\" ]] && ref="${BASH_REMATCH[1]}"

        case "${section}" in
            versions)
                if [[ "${line}" =~ ^[[:space:]]*([A-Za-z0-9_.-]+)[[:space:]]*=[[:space:]]*\"([^\"]+)\" ]]; then
                    CATALOG_REFS+=("${BASH_REMATCH[1]}")
                    CATALOG_VERSION["${BASH_REMATCH[1]}"]="${BASH_REMATCH[2]}"
                fi
                ;;
            libraries)
                [[ "${line}" =~ group[[:space:]]*=[[:space:]]*\"([^\"]+)\" ]] && group="${BASH_REMATCH[1]}"
                [[ "${line}" =~ name[[:space:]]*=[[:space:]]*\"([^\"]+)\" ]] && name="${BASH_REMATCH[1]}"
                if [[ "${line}" =~ module[[:space:]]*=[[:space:]]*\"([^\":]+):([^\"]+)\" ]]; then
                    group="${BASH_REMATCH[1]}"
                    name="${BASH_REMATCH[2]}"
                fi
                if [[ -z "${group}" || -z "${name}" ]]; then
                    warn "Could not parse catalog library '${key}', ignoring it"
                    continue
                fi
                if [[ -n "${ref}" ]]; then
                    CATALOG_COORDS["${ref}"]+="${group}:${name} "
                elif [[ ! "${line}" =~ version[[:space:]]*= ]]; then
                    CATALOG_UNVERSIONED+=("${group}:${name}")
                fi
                ;;
            plugins)
                [[ "${line}" =~ id[[:space:]]*=[[:space:]]*\"([^\"]+)\" ]] && id="${BASH_REMATCH[1]}"
                if [[ -n "${id}" && -n "${ref}" ]]; then
                    CATALOG_COORDS["${ref}"]+="${id}:${id}.gradle.plugin "
                    CATALOG_IS_PLUGIN["${ref}"]="true"
                fi
                ;;
            *) ;;
        esac
    done < "${VERSION_CATALOG}"
}

# The AGP version currently in the catalog (as updated so far this run).
catalog_agp_version() {
    local ref
    for ref in "${CATALOG_REFS[@]}"; do
        if [[ " ${CATALOG_COORDS[${ref}]:-}" == *" com.android.application:"* ]]; then
            echo "${CATALOG_VERSION[${ref}]}"
            return 0
        fi
    done
}

# Echoes the version a BOM (bom_group:bom_name:bom_version) manages for group:name; empty if none.
bom_managed_version() {
    local bom_group="${1}" bom_name="${2}" bom_version="${3}" group="${4}" name="${5}"
    local pom
    pom=$(maven_fetch "${bom_group}" "${bom_name}" "${bom_version}" pom) || return 0
    awk -v g="${group}" -v n="${name}" '
        /<dependency>/                  { dg = ""; dn = ""; dv = "" }
        /<groupId>/                     { v = $0; gsub(/.*<groupId>|<\/groupId>.*/, "", v); dg = v }
        /<artifactId>/                  { v = $0; gsub(/.*<artifactId>|<\/artifactId>.*/, "", v); dn = v }
        /<version>/                     { v = $0; gsub(/.*<version>|<\/version>.*/, "", v); dv = v }
        /<\/dependency>/                { if (dg == g && dn == n) { print dv; exit } }
    ' "${pom}"
}

# Returns 0 if every artifact behind ${ref} accepts ${candidate}; otherwise prints why not and returns 1.
catalog_candidate_fits() {
    local ref="${1}" candidate="${2}"
    local coord group name reason

    # Plugins carry no aar-metadata; AGP/Kotlin/KSP compatibility is left to the final build.
    [[ "${CATALOG_IS_PLUGIN[${ref}]:-}" == "true" ]] && return 0

    for coord in ${CATALOG_COORDS[${ref}]}; do
        group="${coord%%:*}"
        name="${coord#*:}"
        if ! reason=$(android_compatible "${group}" "${name}" "${candidate}"); then
            echo "${reason}"
            return 1
        fi

        # A BOM: check what the app actually takes from it.
        if [[ "${name}" == *bom* ]]; then
            local managed m_group m_name m_version
            for managed in "${CATALOG_UNVERSIONED[@]}"; do
                m_group="${managed%%:*}"
                m_name="${managed#*:}"
                m_version=$(bom_managed_version "${group}" "${name}" "${candidate}" "${m_group}" "${m_name}")
                if [[ -z "${m_version}" ]]; then
                    echo "${group}:${name}:${candidate} no longer manages ${managed}"
                    return 1
                fi
                if ! reason=$(android_compatible "${m_group}" "${m_name}" "${m_version}"); then
                    echo "${reason} (via ${name} ${candidate})"
                    return 1
                fi
            done
        fi
    done
    return 0
}

update_version_catalog() {
    echo
    echo "🔍 Updating the Gradle version catalog (${VERSION_CATALOG})..."

    PROJECT_COMPILE_SDK=$(read_compile_sdk)
    if [[ -z "${PROJECT_COMPILE_SDK}" ]]; then
        # Without it no library candidate can be vetted, and an unvetted AndroidX bump is exactly the
        # thing that breaks the build - so skip the catalog rather than guess.
        warn "Could not read compileSdk from ${APP_BUILD_GRADLE}, skipping the version catalog"
        return 0
    fi
    echo "  Project: compileSdk ${PROJECT_COMPILE_SDK}"

    parse_version_catalog

    local ref
    # CATALOG_REFS is in file order, which puts agp first - so any AGP bump is already in place when
    # the libraries' minAndroidGradlePluginVersion is checked against it.
    for ref in "${CATALOG_REFS[@]}"; do
        local current="${CATALOG_VERSION[${ref}]}"
        local coords="${CATALOG_COORDS[${ref}]:-}"
        if [[ -z "${coords}" ]]; then
            warn "${ref}: not used by any library or plugin, skipping"
            continue
        fi

        local first_coord="${coords%% *}"
        local all_versions
        all_versions=$(maven_versions "${first_coord}" | filter_stable_versions)
        if [[ -z "${all_versions}" ]]; then
            warn "${ref}: could not fetch versions of ${first_coord}, skipping"
            continue
        fi

        local candidate_versions="${all_versions}"

        local candidate chosen="" reason newest_first
        newest_first=$(sort -V -r <<< "${candidate_versions}")
        while IFS= read -r candidate; do
            [[ -n "${candidate}" ]] || continue
            version_gt "${candidate}" "${current}" || break
            if reason=$(catalog_candidate_fits "${ref}" "${candidate}"); then
                chosen="${candidate}"
                break
            fi
            echo "    ${ref} ${candidate} skipped: ${reason}"
        done <<< "${newest_first}"

        if [[ -z "${chosen}" ]]; then
            echo "  ${ref}=${current} (up-to-date)"
            continue
        fi

        echo "  ${ref}: ${current} → ${chosen}"
        set_catalog_version "${ref}" "${chosen}"
        record_gradle_change catalog "${ref}" "${current}" "${chosen}" <<< "${candidate_versions}"
    done

    ok "Version catalog processed"
}

# ── 10. Debian (apt) packages ─────────────────────────────────────────────────
# For each # BEGIN/END <LABEL> PACKAGES block in the given Dockerfile:
#   - Determines the preceding FROM image and queries apt-cache policy in a fresh container
#   - If packages aren't in the base image's default repos (e.g. the Docker-engine packages, which
#     need the Docker APT repo configured), falls back to building a temporary context image from all
#     Dockerfile content up to that section, using the Dockerfile's own directory as the build
#     context (the file COPYs daemon.json before that point). Version pins in earlier <LABEL>
#     PACKAGES blocks are stripped so they install cleanly in the context image.
#   - Updates each package's pinned version in-place via sed
# Best-effort per section.

_parse_apt_candidates() {
    awk '
        /^[a-z0-9]/ { pkg = $1; sub(/:$/, "", pkg) }
        /Candidate:/ { if ($2 != "(none)") print pkg "=" $2 }
    '
}

update_apt_packages() {
    local dockerfile="${1}"
    local label="${2}"
    local start_marker="# BEGIN ${label} PACKAGES"
    local end_marker="# END ${label} PACKAGES"

    if ! grep -q "${start_marker}" "${dockerfile}"; then
        return 0
    fi
    if ! command -v docker >/dev/null 2>&1; then
        warn "docker not available, skipping ${label} packages in ${dockerfile}"
        return 0
    fi

    local section_count
    section_count=$(grep -c "${start_marker}" "${dockerfile}")

    echo
    echo "🔍 Updating ${label} packages in ${dockerfile} (${section_count} section(s))..."

    for ((section = 1; section <= section_count; section++)); do
        echo "  --- Section ${section}/${section_count}"

        # Resolve the FROM image that immediately precedes this section
        local base_image
        base_image=$(awk -v start="${start_marker}" -v n="${section}" '
            /^FROM / { img = $2 }
            $0 ~ start { count++; if (count == n) { print img; exit } }
        ' "${dockerfile}")

        if [[ -z "${base_image}" ]]; then
            warn "Could not determine base image for section ${section}, skipping"
            continue
        fi
        echo "    Base image: ${base_image}"

        if ! docker pull "${base_image}" >/dev/null 2>&1; then
            warn "Could not pull ${base_image}, skipping section ${section}"
            continue
        fi

        # Extract the package names from this section (part before '=')
        local package_block
        package_block=$(awk -v start="${start_marker}" -v end="${end_marker}" -v n="${section}" '
            $0 ~ start { count++; if (count == n) in_block = 1 }
            in_block
            $0 ~ end && in_block { in_block = 0 }
        ' "${dockerfile}")

        local package_names=()
        mapfile -t package_names < <(
            echo "${package_block}" | grep -oP '^\s*[a-z0-9.+-]+(?==)' | sed 's/^[[:space:]]*//' || true
        )

        if [[ "${#package_names[@]}" -eq 0 ]]; then
            warn "No packages found in section ${section}, skipping"
            continue
        fi

        # ── Quick path: query directly from the base image ────────────────────
        local versions_raw
        versions_raw=$(docker run --rm "${base_image}" sh -c \
            "apt-get update -qq -o Acquire::Languages=none 2>/dev/null \
             && apt-cache policy ${package_names[*]}" || true)

        unset apt_versions
        declare -A apt_versions
        while IFS='=' read -r pkg ver; do
            [[ -n "${pkg}" && -n "${ver}" ]] && apt_versions["${pkg}"]="${ver}"
        done < <(echo "${versions_raw}" | _parse_apt_candidates || true)

        # Detect any package with no Candidate (repo not configured in base image)
        local first_missing=""
        for package in "${package_names[@]}"; do
            if [[ -z "${apt_versions["${package}"]:-}" ]]; then
                first_missing="${package}"
                break
            fi
        done

        # ── Fallback: build a context image that includes the repo setup ──────
        # Extracts all Dockerfile content up to (not including) this section's BEGIN
        # marker and builds a temporary image. Version pins in earlier <label> PACKAGES
        # blocks are stripped (gsub) so they install as latest-available rather than
        # requiring exact versions that may no longer be in the apt cache.
        local temp_image=""
        if [[ -n "${first_missing}" ]]; then
            echo "    '${first_missing}' not in default repos, building context image for section ${section}..."

            local tmpdir
            tmpdir=$(mktemp -d)
            temp_image="dep-update-ctx-$$-${section}"

            awk -v start="${start_marker}" -v end="${end_marker}" -v n="${section}" '
                BEGIN { count = 0; in_prev_block = 0 }
                $0 ~ start {
                    count++
                    if (count == n) exit        # stop before the nth BEGIN
                    in_prev_block = 1; print; next
                }
                $0 ~ end { in_prev_block = 0; print; next }
                in_prev_block {
                    gsub(/="[^"]*"/, "")        # strip version pins → install unpinned
                    print; next
                }
                { print }
            ' "${dockerfile}" > "${tmpdir}/Dockerfile"

            if ! docker build -t "${temp_image}" -f "${tmpdir}/Dockerfile" "$(dirname "${dockerfile}")" >/dev/null 2>&1; then
                warn "Context image build failed for section ${section}, skipping"
                rm -rf "${tmpdir}"
                temp_image=""
                continue
            fi
            rm -rf "${tmpdir}"

            # Re-query from the context image (which has the extra repos configured)
            versions_raw=$(docker run --rm "${temp_image}" sh -c \
                "apt-get update -qq -o Acquire::Languages=none 2>/dev/null \
                 && apt-cache policy ${package_names[*]}" || true)

            unset apt_versions
            declare -A apt_versions
            while IFS='=' read -r pkg ver; do
                [[ -n "${pkg}" && -n "${ver}" ]] && apt_versions["${pkg}"]="${ver}"
            done < <(echo "${versions_raw}" | _parse_apt_candidates || true)
        fi

        # Clean up temp image regardless of outcome
        if [[ -n "${temp_image}" ]]; then
            docker image rm "${temp_image}" >/dev/null 2>&1 || true
        fi

        # Verify all packages resolved to a final release after the quick or fallback path
        local all_found=true
        for package in "${package_names[@]}"; do
            if [[ -z "${apt_versions["${package}"]:-}" ]]; then
                warn "Could not resolve version for '${package}', skipping section ${section}"
                all_found=false
                break
            fi
            if is_prerelease "${apt_versions[${package}]}"; then
                warn "Candidate '${package}=${apt_versions[${package}]}' is a pre-release, skipping section ${section}"
                all_found=false
                break
            fi
            echo "    ${package}=${apt_versions[${package}]}"
        done
        [[ "${all_found}" == "true" ]] || continue

        # Update each package version in-place
        for package in "${package_names[@]}"; do
            local old_version
            old_version=$(grep -oP "^\\s*${package//./\\.}=\"\\K[^\"]*" "${dockerfile}" | head -1 || true)
            sed -i "s|${package}=\"[^\"]*\"|${package}=\"${apt_versions[${package}]}\"|g" "${dockerfile}"
            if [[ "${old_version}" != "${apt_versions[${package}]}" ]]; then
                record_update "Sandbox ${label,,} package ${package}: ${old_version} → ${apt_versions[${package}]}"
            fi
        done
    done

    ok "${label} packages processed in ${dockerfile}"
}

# ── 11. GitHub Actions ────────────────────────────────────────────────────────
# Collects every `uses: owner/repo[/path]@ref` in the workflows and moves it to the action's latest
# stable release, keeping the existing pin style: a floating major tag (`@v4`) becomes the newer
# major tag (`@v5`, once that tag is confirmed to exist), a full tag stays a full tag. Never moves a
# reference backwards. Best-effort: actions that cannot be resolved are warned and left unchanged.
# Under --no-workflow-edits the newer versions are only reported (record_note), never written.

update_github_actions() {
    echo
    echo "🔍 Updating GitHub Actions versions..."

    if [[ ! -d "${WORKFLOWS_DIR}" ]]; then
        warn "No workflows directory at ${WORKFLOWS_DIR}, skipping"
        return 0
    fi

    local action_refs=()
    mapfile -t action_refs < <(
        grep -rhoP 'uses:\s+\K[A-Za-z0-9._-]+/[A-Za-z0-9._-]+(/[A-Za-z0-9._/-]+)?@\S+' "${WORKFLOWS_DIR}"/*.yml \
            | sort -u || true
    )

    if [[ "${#action_refs[@]}" -eq 0 ]]; then
        echo "  No action references found."
        return 0
    fi

    for ref in "${action_refs[@]}"; do
        local action current repo latest target
        action="${ref%@*}"
        current="${ref#*@}"
        repo=$(cut -d/ -f1,2 <<< "${action}")

        latest=$(latest_stable_github_release "${repo}" || true)
        if [[ -z "${latest}" ]]; then
            warn "Could not fetch a stable version for ${repo}, skipping"
            continue
        fi

        target="${latest}"
        if [[ "${current}" =~ ^v[0-9]+$ ]]; then
            local major_tag
            major_tag="v$(major_of "${latest#v}")"
            if github_curl -o /dev/null "https://api.github.com/repos/${repo}/git/ref/tags/${major_tag}" 2>/dev/null; then
                target="${major_tag}"
            else
                warn "${repo} has no ${major_tag} tag, pinning ${action} to the full tag ${latest}"
            fi
        fi

        if [[ "${current}" == "${target}" ]] || ! version_gt "${target#v}" "${current#v}"; then
            echo "  ${action}@${current} (already up-to-date)"
            continue
        fi

        if [[ "${NO_WORKFLOW_EDITS}" == "true" ]]; then
            echo "  ${action}: ${current} → ${target} available (not applied: --no-workflow-edits)"
            record_note "GitHub Action ${action}: ${current} → ${target}"
            continue
        fi
        echo "  ${action}: ${current} → ${target}"
        for workflow in "${WORKFLOWS_DIR}"/*.yml; do
            sed -i -E "s#${action//./\\.}@${current//./\\.}([[:space:]]|$)#${action}@${target}\\1#g" "${workflow}"
        done
        record_update "GitHub Action ${action}: ${current} → ${target}"
    done

    ok "GitHub Actions processed in ${WORKFLOWS_DIR}"
}

# ── 11b. actionlint image (.github/scripts/lint_workflows.sh) ─────────────────
# The workflow linter's Docker image, pinned in the lint script rather than a workflow (so a
# GITHUB_TOKEN push can bump it). Moved to actionlint's latest stable GitHub release whose Docker Hub
# tag is confirmed - the release is tagged vX.Y.Z, the image X.Y.Z.

LINT_WORKFLOWS_SCRIPT=".github/scripts/lint_workflows.sh"

update_actionlint() {
    echo
    echo "🔍 Fetching latest actionlint version..."

    local current
    current=$(grep -oP '^ACTIONLINT_IMAGE="rhysd/actionlint:\K[^"]+' "${LINT_WORKFLOWS_SCRIPT}" 2>/dev/null || true)
    if [[ -z "${current}" ]]; then
        warn "No ACTIONLINT_IMAGE pin in ${LINT_WORKFLOWS_SCRIPT}, skipping"
        return 0
    fi

    local tag latest
    tag=$(latest_stable_github_release "rhysd/actionlint" || true)
    latest="${tag#v}"
    if [[ -z "${latest}" ]]; then
        warn "Could not fetch a stable actionlint version, skipping"
        return 0
    fi
    if ! version_gt "${latest}" "${current}"; then
        echo "  actionlint=${current} (already up-to-date)"
        return 0
    fi
    if ! hub_tag_exists "rhysd/actionlint" "${latest}"; then
        warn "rhysd/actionlint:${latest} not on Docker Hub, skipping"
        return 0
    fi

    echo "  actionlint: ${current} → ${latest}"
    sed -i "s|^ACTIONLINT_IMAGE=\"rhysd/actionlint:[^\"]*\"|ACTIONLINT_IMAGE=\"rhysd/actionlint:${latest}\"|" "${LINT_WORKFLOWS_SCRIPT}"
    record_update "actionlint: ${current} → ${latest}"
    ok "actionlint processed → ${latest}"
}

# ── 12. Consistency guard: values that must agree across files ─────────────────
# Values pinned in more than one place that must say the same thing - a drift between them is a
# broken setup rather than a stale one:
#   - JDK major: the Gradle toolchain and the sandbox's jdk stage
#   - Android platform: app compileSdk + compileSdkMinor, and `platforms;android-…` in sandbox/Dockerfile,
#     compared as API.MINOR (android-36 = 36.0), so a minor-release mismatch is caught
#   - Build-tools: app buildToolsVersion, and `build-tools;X` / `build-tools/X` in sandbox/Dockerfile
#     (AGP enforces a minimum build-tools and silently fetches it over the network when the installed
#     one is lower, so a stale pin still "works" - which is exactly why it needs a check)
# The workflows are scanned too, so a pin re-added to one is held to the same value - but they are meant
# to pin none of these: they read the JDK from the toolchain and the SDK packages from
# app/android/build.gradle.kts at run time, so that the monthly update (pushing with GITHUB_TOKEN, which may not
# edit workflow files) never needs to change one. A divergence is a HARD failure, not a warning.

SYNC_FAILED=0

# Reports one group: ${1} = label, remaining args = "<value> <where>" pairs (empty values ignored).
check_group() {
    local label="${1}"
    shift
    local values=() entry
    for entry in "${@}"; do
        [[ -n "${entry%% *}" ]] && values+=("${entry}")
    done
    if [[ "${#values[@]}" -eq 0 ]]; then
        warn "${label}: no pins found to verify"
        return 0
    fi

    local distinct distinct_count
    distinct=$(printf '%s\n' "${values[@]}" | awk '{ print $1 }' | sort -u)
    distinct_count=$(grep -c . <<< "${distinct}" || true)
    if [[ "${distinct_count}" -gt 1 ]]; then
        err "${label} DIVERGED:"
        printf '       %s\n' "${values[@]}" >&2
        SYNC_FAILED=1
    else
        ok "${label} in sync → ${distinct}"
    fi
}

verify_version_sync() {
    echo
    echo "🔍 Verifying pinned values agree across files..."

    local entries=() v workflow

    # ── JDK major ──
    entries=()
    v=$(read_toolchain_version)
    entries+=("${v} (${GRADLE_DAEMON_JVM_PROPERTIES} toolchainVersion)")
    v=$(grep -oP '^FROM eclipse-temurin:\K[0-9]+(?=[._])' "${SANDBOX_DOCKERFILE}" | head -1 || true)
    entries+=("${v} (${SANDBOX_DOCKERFILE} jdk stage)")
    for workflow in "${WORKFLOWS_DIR}"/*.yml; do
        while IFS= read -r v; do
            entries+=("${v} (${workflow} java-version)")
        done < <(grep -oP "java-version:\s*['\"]?\K[0-9]+" "${workflow}" || true)
    done
    check_group "JDK major" "${entries[@]}"

    # ── Android platform (as API.MINOR, so 37.0 and 37.2 are told apart) ──
    entries=()
    v=$(read_compile_sdk_version)
    entries+=("${v} (${APP_BUILD_GRADLE} compileSdk + compileSdkMinor)")
    for f in "${SANDBOX_DOCKERFILE}" "${WORKFLOWS_DIR}"/*.yml; do
        while IFS= read -r v; do
            [[ "${v}" == *.* ]] || v="${v}.0"
            entries+=("${v} (${f})")
        done < <(grep -oP 'platforms[;/]android-\K[0-9]+(\.[0-9]+)?(?![0-9.a-z-])' "${f}" || true)
    done
    check_group "Android platform" "${entries[@]}"

    # ── Build-tools ──
    entries=()
    v=$(grep -oP '^\s*buildToolsVersion\s*=\s*"\K[^"]+' "${APP_BUILD_GRADLE}" | head -1 || true)
    entries+=("${v} (${APP_BUILD_GRADLE} buildToolsVersion)")
    for f in "${SANDBOX_DOCKERFILE}" "${WORKFLOWS_DIR}"/*.yml; do
        while IFS= read -r v; do
            entries+=("${v} (${f})")
        done < <(grep -oP 'build-tools[;/]\K[0-9][0-9.]*[0-9]' "${f}" || true)
    done
    check_group "Build-tools" "${entries[@]}"
}

# ── 13. Build verification ────────────────────────────────────────────────────
# Version metadata can only rule so much out: an AGP/Kotlin/KSP/Gradle combination, or a library's
# own behaviour, can still break the build. So whenever a Gradle input changed, build the debug APK
# and run the unit tests - the same gate the release workflow applies before publishing. When that
# fails, the bumps responsible are found, taken back, and stepped down to the newest versions that
# build (see verify_build), so the run still ends on a building tree with everything else updated.

gradle_inputs_hash() {
    cat "${GRADLE_INPUTS[@]}" 2>/dev/null | sha256sum | cut -d' ' -f1
}

BUILD_FAILED=0

verify_build() {
    local before="${1}"
    echo
    echo "🔍 Verifying the build..."

    local after
    after=$(gradle_inputs_hash)
    if [[ "${after}" == "${before}" ]]; then
        echo "  No Gradle inputs changed; nothing to verify."
        return 0
    fi
    if [[ "${SKIP_BUILD}" == "true" ]]; then
        warn "Gradle inputs changed but --skip-build was given - run './gradlew assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin lintDebug' before committing"
        return 0
    fi

    echo "  Building with every update applied..."
    if run_gradle_build; then
        ok "The build gates pass with the updated versions"
        return 0
    fi
    warn "The build fails with every update applied:"
    show_build_failure

    # Find the bumps responsible - there can be more than one (say, two majors that each break it):
    #   1. take bumps back one at a time, cumulatively, until the build passes: the last one taken back
    #      is a culprit;
    #   2. re-apply each of the others taken back before it - one that still builds was innocent and
    #      stays, one that breaks it again is another culprit and goes back;
    #   3. step each culprit down through its intermediate minor lines (majors included), keeping the
    #      newest that builds.
    # Each build runs on the tree exactly as it then stands, so the state this ends in is itself proven.
    local change kind key old new passed=false
    local -a reverted=() culprits=()
    echo "  Taking bumps back one at a time until it builds..."
    for change in "${GRADLE_CHANGES[@]}"; do
        IFS='|' read -r kind key old new <<< "${change}"
        set_gradle_version "${kind}" "${key}" "${old}" || continue
        reverted+=("${change}")
        echo "    without ${key} ${new} (back on ${old})..."
        if run_gradle_build; then
            passed=true
            break
        fi
    done
    if [[ "${passed}" != "true" ]]; then
        # Every bump is now back where it started, so what fails is not this run's doing.
        err "The build fails even with every Gradle bump taken back - not caused by this run's updates:"
        show_build_failure
        BUILD_FAILED=1
        return 0
    fi

    culprits+=("${reverted[-1]}")
    unset 'reverted[-1]'
    for change in "${reverted[@]}"; do
        IFS='|' read -r kind key old new <<< "${change}"
        set_gradle_version "${kind}" "${key}" "${new}" || continue
        echo "    re-applying ${key} ${new}..."
        if ! run_gradle_build; then
            set_gradle_version "${kind}" "${key}" "${old}"
            culprits+=("${change}")
        fi
    done

    for change in "${culprits[@]}"; do
        IFS='|' read -r kind key old new <<< "${change}"
        warn "${key} ${new} breaks the build - looking for the newest version that does not"
        local step chosen="${old}" reason
        while IFS= read -r step; do
            [[ -n "${step}" ]] || continue
            if [[ "${kind}" == "catalog" ]] && ! reason=$(catalog_candidate_fits "${key}" "${step}"); then
                continue
            fi
            set_gradle_version "${kind}" "${key}" "${step}" || continue
            echo "    trying ${key} ${step}..."
            if run_gradle_build; then
                chosen="${step}"
                break
            fi
        done <<< "${GRADLE_CHANGE_STEPS[${kind}|${key}]:-}"
        set_gradle_version "${kind}" "${key}" "${chosen}"
        if [[ "${chosen}" == "${old}" ]]; then
            warn "${key} held at ${old} - every newer version breaks the build (${new} is the newest)"
        else
            warn "${key} ${old} → ${chosen} instead: the newest that builds (${new} breaks it)"
        fi
    done
    ok "The build gates pass with the remaining updates"
}

# ── 14. JDK major upgrade (proven by a build) ─────────────────────────────────
# Step 2 of the header's "Java" note. For each newer LTS found by update_java (newest first): point the
# Gradle toolchain at it (`./gradlew updateDaemonJvm`, which also refreshes the per-platform download
# URLs) and build + unit-test from scratch (--rerun-tasks --no-build-cache, so nothing compiled on the
# old JDK is reused) - Gradle fetches the new JDK itself, and the app is then compiled for it too. The
# first major that passes is kept and written everywhere else the major lives; a failing one has the
# toolchain file put back. Runs last, on the fully updated tree, so what is proven is exactly what gets
# committed - and only when that tree already builds on the current JDK, so a failure here is the new
# JDK's alone. Never attempted under --skip-build.

JDK_MAJOR_ADOPTED=""

# Points the Gradle toolchain at JDK <major>, keeping its vendor.
set_toolchain_version() {
    local major="${1}" vendor
    vendor=$(grep -oP '^toolchainVendor=\K\S+' "${GRADLE_DAEMON_JVM_PROPERTIES}" | head -1 || true)
    ./gradlew --no-daemon --console=plain -q updateDaemonJvm --jvm-version="${major}" \
        ${vendor:+--jvm-vendor="${vendor}"} > "${BUILD_LOG}" 2>&1
}

# Writes JDK <major> (sandbox tag <tag>) into the sandbox, the one place outside the toolchain that pins it.
apply_jdk_major() {
    local new_major="${1}" new_tag="${2}" old_major="${3}"

    sed -i "s|^FROM eclipse-temurin:[^ ]* AS jdk|FROM eclipse-temurin:${new_tag} AS jdk|" "${SANDBOX_DOCKERFILE}"
    # The stage-comment and section-heading labels ("JDK 21  —", "── JDK 21 / Maven …").
    sed -i -E \
        -e "s|^(#[[:space:]]+JDK )${old_major}( +—)|\\1${new_major}\\2|" \
        -e "s|^(# ── JDK )${old_major}( /)|\\1${new_major}\\2|" \
        "${SANDBOX_DOCKERFILE}"

    # The maven stage's suffix follows the jdk major (see update_maven), if that tag exists.
    local maven_version
    maven_version=$(grep -oP '^FROM maven:\K[0-9.]+(?=-eclipse-temurin-[0-9]+ AS maven)' "${SANDBOX_DOCKERFILE}" | head -1 || true)
    if [[ -n "${maven_version}" ]]; then
        if hub_tag_exists "library/maven" "${maven_version}-eclipse-temurin-${new_major}"; then
            sed -i "s|^FROM maven:[^ ]* AS maven|FROM maven:${maven_version}-eclipse-temurin-${new_major} AS maven|" "${SANDBOX_DOCKERFILE}"
        else
            warn "maven:${maven_version}-eclipse-temurin-${new_major} not on Docker Hub, the maven stage keeps its suffix (its JDK is unused)"
        fi
    fi

    # The workflows pin no JDK: their setup-java reads the toolchain's version at run time.
}

try_jdk_major_upgrade() {
    [[ "${#JDK_MAJOR_CANDIDATES[@]}" -gt 0 ]] || return 0

    echo
    echo "🔍 Trying newer JDK majors (adopted only if the build passes on them)..."

    if [[ "${SKIP_BUILD}" == "true" ]]; then
        warn "--skip-build given: no newer JDK can be proven, so none is adopted"
        return 0
    fi
    if [[ "${BUILD_FAILED}" -ne 0 ]]; then
        warn "The build already fails on the current JDK, so a newer one cannot be judged - not tried"
        return 0
    fi

    local old_major
    old_major=$(read_toolchain_version)
    [[ -n "${old_major}" ]] || { warn "No toolchainVersion in ${GRADLE_DAEMON_JVM_PROPERTIES}, skipping"; return 0; }

    local candidate new_major new_tag backup="${WORK_DIR}/gradle-daemon-jvm.properties.orig"
    cp "${GRADLE_DAEMON_JVM_PROPERTIES}" "${backup}"
    for candidate in "${JDK_MAJOR_CANDIDATES[@]}"; do
        read -r new_major new_tag <<< "${candidate}"
        (( new_major > old_major )) || continue
        echo "  --- JDK ${new_major}"

        if ! set_toolchain_version "${new_major}"; then
            warn "Could not point the toolchain at JDK ${new_major}, skipping it:"
            show_build_failure
            cp "${backup}" "${GRADLE_DAEMON_JVM_PROPERTIES}"
            continue
        fi
        echo "    Building on the JDK ${new_major} toolchain (Gradle fetches it if needed)..."
        if run_gradle_build --rerun-tasks --no-build-cache; then
            apply_jdk_major "${new_major}" "${new_tag}" "${old_major}"
            JDK_MAJOR_ADOPTED="${new_major}"
            record_update "JDK (Gradle toolchain, sandbox, CI): ${old_major} → ${new_major}"
            ok "JDK ${old_major} → ${new_major} (${new_tag}): the build and unit tests pass on it"
            return 0
        fi
        warn "The build fails on JDK ${new_major} - staying on JDK ${old_major}:"
        show_build_failure
        cp "${backup}" "${GRADLE_DAEMON_JVM_PROPERTIES}"
    done
    return 0
}

# ── Entry point ───────────────────────────────────────────────────────────────

for f in "${SANDBOX_DOCKERFILE}" "${VERSION_CATALOG}" "${GRADLE_WRAPPER_PROPERTIES}" "${SETTINGS_GRADLE}" "${APP_BUILD_GRADLE}"; do
    if [[ ! -f "${f}" ]]; then
        echo "❌ Required file not found: ${f} (run this from the repository root)"
        exit 1
    fi
done

GRADLE_INPUTS_BEFORE=$(gradle_inputs_hash)

# compileSdk first, on the project exactly as committed (see its section for why).
update_compile_sdk      || warn "compileSdk update failed, continuing..."
update_gradle_wrapper   || warn "Gradle wrapper update failed, continuing..."
update_settings_plugins || warn "settings.gradle.kts plugin update failed, continuing..."
update_version_catalog  || warn "Version catalog update failed, continuing..."

# Maven reads the jdk stage's major, so Java goes first.
update_java                  || warn "Java update failed, continuing..."
update_maven                 || warn "Maven update failed, continuing..."
update_node                  || warn "Node update failed, continuing..."
update_playwright            || warn "Playwright update failed, continuing..."
update_android_cmdline_tools || warn "Android cmdline-tools update failed, continuing..."
# Before the apt section below: it resolves package candidates against the Dockerfile's CURRENT
# base image, so the base itself must already be at its target version by that point.
update_sandbox_base          || warn "sandbox Debian base update failed, continuing..."
update_apt_packages "${SANDBOX_DOCKERFILE}" "DEBIAN" || warn "Debian packages update failed for ${SANDBOX_DOCKERFILE}, continuing..."

update_github_actions || warn "GitHub Actions update failed, continuing..."
update_actionlint     || warn "actionlint update failed, continuing..."

# Final checks (NOT best-effort). Both run before either fails the run, so one report covers both.
# The JDK major trial sits between them: it needs the build's verdict on the current JDK, and the
# sync guard must then see whatever it changed.
verify_build "${GRADLE_INPUTS_BEFORE}"
try_jdk_major_upgrade
verify_version_sync
print_update_summary

if [[ "${SYNC_FAILED}" -ne 0 || "${BUILD_FAILED}" -ne 0 ]]; then
    echo
    [[ "${SYNC_FAILED}" -ne 0 ]] && echo "❌ Pinned values disagree across files (listed above) - fix before committing." >&2
    [[ "${BUILD_FAILED}" -ne 0 ]] && echo "❌ The Gradle build failed with the updated versions (see above)." >&2
    exit 1
fi

echo
echo "✅ Dependency version update complete."
if [[ -n "${JDK_MAJOR_ADOPTED}" ]]; then
    echo "   JDK major moved to ${JDK_MAJOR_ADOPTED}: review the JDK rationale in ${SANDBOX_DOCKERFILE}'s header."
fi
if ! git diff --quiet -- "${SANDBOX_DOCKERFILE}" 2>/dev/null; then
    echo "   sandbox/Dockerfile changed - the sandbox image is rebuilt on the next sandbox.sh launch."
fi
