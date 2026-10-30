# syntax=docker/dockerfile:1.7
# DiceFive's layer of the sandbox image: the toolchains this project needs, on top of the shared
# ai-sandbox base (Debian 13, nested Docker, Node, git, gh, jq, python3 - ai-sandbox/image/base/).
# Claude Code and the sandbox scripts are added ABOVE this layer by ai-sandbox/image/final/, so a
# Claude release never rebuilds anything here. Built by ai-sandbox/sandbox.sh, with this directory
# as the build context and BASE_IMAGE set to the shared base.
#
# The JDK is pulled from its official image as a multi-stage build stage and its directory copied
# straight in — so we track an upstream image tag instead of hand-rolling a tarball download. The
# Android SDK, NDK, emulator and ImageMagick are downloaded in build stages of their own and copied in
# the same way. On top we also add the system libraries Playwright's Chromium needs (for an occasional
# headless-browser screenshot).
#
# The JDK is an LTS release, and only moves to a newer major once this project is
# proven to build on it: the update script builds and unit-tests the project on
# the candidate before writing it here (JDK 25 was adopted that way). A blind bump
# has bitten before - JDK 26, once pinned here, crashed Gradle 8.9's kotlin-dsl
# script compiler on its version string ("26.0.2" -> IllegalArgumentException in
# org.jetbrains.kotlin...JavaVersion.parse), so *.gradle.kts builds could not
# configure at all. The Java version itself is owned by the project's Gradle
# toolchain (toolchainVersion in gradle/gradle-daemon-jvm.properties): Gradle runs
# and compiles on that JDK and downloads it if missing. This stage only follows it,
# so a sandbox build never has to download a JDK - keep the two on the same major
# (the update script's consistency check enforces it).
#
# NOTE: every version below is pinned, and bumped by
# .github/scripts/update_dependency_versions.sh (the monthly update-dependencies
# workflow, or a run from the repo root), so a build that fails on a version Debian
# has since dropped means it is time for a run. That script relies on this file's shape: the `FROM … AS
# jdk` stage name, the BEGIN/END DEBIAN PACKAGES markers, the cmdline-tools URL +
# sha1 line, the `ARG EMULATOR_API` line and the `ENV IMAGEMAGICK_VERSION`/
# `IMAGEMAGICK_SHA256` lines. The shared base's own pins (Debian, Node, the Docker
# engine, the everyday tools) belong to ai-sandbox: `ai-sandbox/sandbox.sh update-versions`.
#
# ── LAYER ORDER IS DELIBERATE: THIS FILE IS OPTIMISED FOR REBUILD TIME ────────
# sandbox.sh rebuilds the image before EVERY launch, so a cache miss is not a
# one-off cost — it is paid on every session until something else invalidates the
# layer. A changed layer rebuilds every layer below it in its stage, so a stage
# orders its layers by how much they cost to rebuild, the heaviest first, and the
# file's edits (pin bumps and hand edits alike) land in the cheap layers below them.
#
# About 10 GB of the image is downloaded content: the emulator and its system image
# (~6 GB), the NDK (~2.3 GB), the rest of the Android SDK, the JDK and ImageMagick.
# Each is fetched in a build stage of its own, cached on its own (a stage rebuilds
# only when its own text or base changes), and COPY'd into the runtime stage FIRST,
# straight after its FROM, so nothing else in this file can invalidate them: a
# package pin or an ENV only ever rebuilds the cheap layers below. A rebuilt shared
# base (an ai-sandbox pin bump) re-runs these COPYs too, but as local copies from the
# cached stages - never as downloads.
#
# Plain COPY, not `COPY --link`: on the classic overlay2 image store (the default
# on a Linux host's Docker Engine) a linked COPY is still re-copied once a layer
# above it changes, so the order is what keeps them cached. Within the runtime
# stage:
#
#   1. COPY the downloaded toolchains  rarely change; ordered JDK -> emulator -> NDK -> SDK
#                                      -> ImageMagick, by how often their pins move
#   2. Playwright's Chromium libs      ~70 packages; reruns only when the playwright-deps
#                                      stage's package list changes
#   3. ENV block                       edited by hand now and then
#   4. tooling apt                     volatile pins
#   5. ImageMagick link, AVD           tiny
#
# The rule when adding a step: a download of any size goes in a stage of its own
# and is COPY'd in at the top, and so does any slow work whose result can be
# copied; anything else goes as far DOWN as its dependencies permit.
#
# BuildKit cache mounts back the apt and npm downloads, so even a genuine cache
# miss in the runtime stage re-installs from local disk instead of re-fetching over
# the network.

# The shared ai-sandbox base image - always passed by ai-sandbox/sandbox.sh.
ARG BASE_IMAGE

# ── The emulator's Android version ────────────────────────────────────────────
# EMULATOR_API is the one place the emulator's Android version is chosen, and it FOLLOWS compileSdk: the
# API level the app is compiled against (gradle/android-sdk.properties), so the emulator runs the newest
# Android the project targets. It is written as the image package names it ("37.0": from API 37 even the
# base release carries a minor; older ones are plain, "34"), and it is the newest stable Google APIs image
# of compileSdk's API level - Google publishes no image for minor releases such as 37.2, so the emulator
# can match the level but not the minor. .github/scripts/update_dependency_versions.sh moves it whenever
# compileSdk moves, fails the run if the two drift apart or bin/emulator.sh's fallback disagrees with
# this value, and fails it if the image is gone from Google's manifest. Google APIs, not Google Play: only
# the former allows `adb root`, which the Baseline Profile run and the renderer workaround need (see
# .claude/EMULATOR.md).
#
# A global ARG, because two stages need it: the emulator stage installs its image, and the runtime stage
# makes the virtual device and exports it as ENV (which reaches every session as-is). CI's emulator reads
# this line too (ci_emulator.sh).
ARG EMULATOR_API=37.0

# ── Toolchain source stage (its payload is copied into the base) ──────────────
# Only mined for its self-contained toolchain directory; none of its base OS reaches the final image.
#   JDK 25  — self-contained under /opt/java/openjdk, portable onto glibc Debian.
#             LTS; see the file-header note above for how a new major is adopted.
# (Node, for Claude Code and Playwright, is in the shared base.)
FROM eclipse-temurin:25.0.4.1_1-jdk AS jdk

# ── Download stage: curl + unzip for the Android and ImageMagick stages ───────
# Unpinned on purpose, unlike every other package in this file: nothing installed
# here reaches the runtime image, only what the stages below download with it. An
# unchanged instruction is a cache hit, so this layer is rebuilt only when its own
# text or the Debian base changes, and pinning it would only add bumps that
# re-download everything built on it. No BEGIN/END markers, so the update script
# leaves it alone. Keep its Debian tag on the shared base's (ai-sandbox/image/base/Dockerfile).
#
# Its apt cache mounts have ids of their own, so its unpinned state never mixes
# with the runtime stage's. docker-clean is removed for the same reason as in the
# shared base: it would empty them.
FROM debian:13.7 AS fetch
SHELL ["/bin/bash", "-o", "pipefail", "-c"]
# hadolint ignore=DL3008,DL3009
RUN --mount=type=cache,id=fetch-apt-cache,target=/var/cache/apt,sharing=locked --mount=type=cache,id=fetch-apt-lib,target=/var/lib/apt,sharing=locked \
    rm -f /etc/apt/apt.conf.d/docker-clean \
    && apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -yqq --no-install-recommends \
        ca-certificates curl unzip

# ── Android cmdline-tools (the installer the Android stages below share) ──────
# There is no official upstream Docker image that is just "the Android SDK" the way
# eclipse-temurin is for the JDK, so — unlike that one — this is a direct
# download + checksum + unzip rather than a COPY --from stage.
#
# Extracted with `unzip`, which keeps the archive's Unix permission bits — `jar xf`
# silently drops the executable bit on every tool under cmdline-tools/*/bin, which
# then fails with "Permission denied" the first time one of them runs.
#
# cmdline-tools is pinned to the build Google's own repository manifest
# (https://dl.google.com/android/repository/repository2-3.xml) currently serves as
# `cmdline-tools;latest` (tool revision 23.0), sha1-verified against that same
# manifest. Its installer, `android`, is a native binary that needs no JDK. A bump
# here rebuilds the three stages below, which are built on it.
FROM fetch AS android-cli
# pipefail is set: the stage inherits fetch's SHELL, which hadolint does not follow.
# hadolint ignore=DL4006
RUN set -eux; \
    curl -fsSL -o /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip"; \
    echo "e025545c62a8e64c7559119566a569fb1dec5f60  /tmp/cmdline-tools.zip" | sha1sum -c -; \
    mkdir -p /opt/android-sdk/cmdline-tools; \
    unzip -q /tmp/cmdline-tools.zip -d /opt/android-sdk/cmdline-tools; \
    mv /opt/android-sdk/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest; \
    rm -f /tmp/cmdline-tools.zip

# The three Android stages below share this installer step's shape:
#
# `android sdk install` replaces the deprecated sdkmanager and asks nothing, so no `yes |`.
#
# `|| true` on that command, not a bare `set -eux` check: the `test` lines right after are what
# actually verify success (and, unlike trusting the installer's exit code, they check the one thing
# that matters: did the packages land on disk); a genuine install failure leaves one of them false
# and `set -e` stops the build there.
#
# The installer, run as root, unpacks some packages (the emulator among them) without the other-user
# execute bit, so the `chmod -R a+rX` at the end is what lets `dev` run them (X grants execute only
# where someone already had it). COPY keeps the modes.

# ── Android emulator + its system image (~6 GB: so `emulator.sh start` boots at once) ─────────
# The emulator and the system image are named by package only - the installer has no way to pin a
# revision, the same as `platform-tools` below - so what is baked is whatever Google serves on the day
# this stage is built; it is rebuilt only when its text changes, which is how `EMULATOR_API` moves it.
# The virtual device is made in the runtime stage, as the user that boots it.
FROM android-cli AS android-emulator
ARG EMULATOR_API
RUN set -eux; \
    /opt/android-sdk/cmdline-tools/latest/bin/android sdk install --sdk=/opt/android-sdk \
        "emulator" "system-images;android-${EMULATOR_API};google_apis;x86_64" </dev/null >/dev/null || true; \
    test -x /opt/android-sdk/emulator/emulator; \
    test -d "/opt/android-sdk/system-images/android-${EMULATOR_API}/google_apis/x86_64"; \
    chmod -R a+rX /opt/android-sdk

# ── Android NDK (the release build extracts native symbol tables with it) ────────────────────────────
# About 2.3 GB. `ndk;30.0.16248370` matches `ndkVersion` in gradle/android-sdk.properties; without it a
# release build cannot extract the symbols (`debugSymbolLevel`) and no build can strip native libraries.
# .github/scripts/update_dependency_versions.sh moves both together and its consistency check fails the
# run if they drift.
FROM android-cli AS android-ndk
RUN set -eux; \
    /opt/android-sdk/cmdline-tools/latest/bin/android sdk install --sdk=/opt/android-sdk \
        "ndk;30.0.16248370" </dev/null >/dev/null || true; \
    test -d /opt/android-sdk/ndk/30.0.16248370; \
    chmod -R a+rX /opt/android-sdk

# ── Android SDK (this project is an Android app) ──────────────────────────────
# `platform-tools`, `platforms;android-37.2` and `build-tools;36.0.0` are pinned to
# match this project's compileSdk + compileSdkMinor (37.2 - a minor SDK release is
# its own platform package, and from API 37 even the base release is named with a
# minor, android-37.0) and its `buildToolsVersion` (gradle/android-sdk.properties) —
# keep those three in step if either changes, or a build will reach out to the
# network for whatever AGP defaults to instead of what is baked in here (the update
# script's consistency check fails the run if they drift). build-tools is 36.0.0
# because AGP 9.5.0 enforces build-tools >= 36.0.0 regardless of compileSdk and
# overrides (with a network fetch) anything pinned lower.
#
# The runtime stage copies this stage's whole SDK directory (cmdline-tools, the
# installer's own state in .sdk/ and licenses/ included) but only the package
# directories of the two stages above.
FROM android-cli AS android-sdk
RUN set -eux; \
    /opt/android-sdk/cmdline-tools/latest/bin/android sdk install --sdk=/opt/android-sdk \
        "platform-tools" "platforms;android-37.2" "build-tools;36.0.0" </dev/null >/dev/null || true; \
    test -x /opt/android-sdk/platform-tools/adb; \
    test -d /opt/android-sdk/platforms/android-37.2; \
    test -d /opt/android-sdk/build-tools/36.0.0; \
    chmod -R a+rX /opt/android-sdk

# ── ImageMagick 7 (scripts/generate-store-assets.sh's `magick`) ──────────────
# ImageMagick's own AppImage, not Debian's package, so the sandbox renders store assets with exactly the
# build CI's README-assets workflow uses (.github/scripts/install_imagemagick.sh): the same release, checked
# against the same SHA-256. .github/scripts/update_dependency_versions.sh moves the pins in both files
# together, and its consistency check fails the run if they drift. Extracted rather than mounted (no FUSE
# in a container); its AppRun runs the tool named by the link it was called through, so the runtime
# stage's `magick` link is `magick`.
FROM fetch AS imagemagick
ENV IMAGEMAGICK_VERSION=7.1.2-34
ENV IMAGEMAGICK_SHA256=6e9edfd35d5f30b57c27533afe003d6085faaf8607b207426f398d11751acf93
# --appimage-extract unpacks into the working directory.
WORKDIR /opt
# pipefail is set: the stage inherits fetch's SHELL, which hadolint does not follow.
# hadolint ignore=DL4006
RUN set -eux; \
    curl -fsSL -o /tmp/magick.AppImage \
        "https://github.com/ImageMagick/ImageMagick/releases/download/${IMAGEMAGICK_VERSION}/ImageMagick-${IMAGEMAGICK_VERSION}-gcc-x86_64.AppImage"; \
    echo "${IMAGEMAGICK_SHA256}  /tmp/magick.AppImage" | sha256sum -c -; \
    chmod +x /tmp/magick.AppImage; \
    /tmp/magick.AppImage --appimage-extract > /dev/null; \
    mv /opt/squashfs-root /opt/imagemagick; \
    rm -f /tmp/magick.AppImage; \
    chmod -R a+rX /opt/imagemagick

# ── Playwright Chromium's OS shared libs: the package LIST (libnspr4, libnss3, …) ─
# The libraries themselves are apt packages, so they have to be installed in the
# runtime stage itself; what this stage works out is WHICH packages. We do NOT
# enumerate the apt package names by hand (they drift across Debian releases);
# Playwright's own `install-deps` resolves the correct set for this base - pinned to
# an explicit version so the build stays reproducible (the repo is mounted at
# runtime, not present at build time, so npx fetches the CLI here). It runs on a copy
# of the shared base (which has Node), and the packages it marks as manually installed
# are its list; the runtime stage installs exactly those near its top. A base or
# Playwright bump reruns this stage, but the runtime layer only reruns when the list
# it writes changes.
#
# /var/lib/apt is NOT a cache mount here: it holds apt's manual/auto marks, which
# must start from the base's own for the list to be right.
#
# The project declares no Playwright; a session that wants a headless browser (e.g.
# to screenshot an HTML preview) runs `npx playwright@<this version> install
# chromium` itself. The browser BINARY stays out of the image (it lands in the
# persisted `pw` cache volume, sandbox.conf) to avoid baking ~150MB of Chromium into
# every layer.
FROM ${BASE_IMAGE} AS playwright-deps
SHELL ["/bin/bash", "-o", "pipefail", "-c"]
# hadolint ignore=DL3008,DL3009
RUN --mount=type=cache,id=playwright-apt-cache,target=/var/cache/apt,sharing=locked --mount=type=cache,id=playwright-npm,target=/root/.npm,sharing=locked \
    apt-get update \
    && apt-mark showmanual | sort > /tmp/before \
    && npx --yes playwright@1.64.1 install-deps chromium \
    && apt-mark showmanual | sort | comm -13 /tmp/before - > /playwright-packages.txt \
    && test -s /playwright-packages.txt

# ── Runtime: the shared base + this project's toolchains ──────────────────────
FROM ${BASE_IMAGE}

# pipefail for every RUN in this stage so a failure in the middle of a pipe aborts the layer instead of
# being masked by the pipe's last command (the base sets it too; restated for hadolint).
SHELL ["/bin/bash", "-o", "pipefail", "-c"]

# ── The downloaded toolchains (first, so nothing below can invalidate them) ───
# Ordered by how often their source moves, the most stable first: a change to one
# re-copies it and the ones below it (a local copy, not a download - their stages
# stay cached), never the ones above. The JDK moves quarterly; the emulator only
# with EMULATOR_API; the NDK a few times a year; the SDK with compileSdk,
# build-tools or cmdline-tools; ImageMagick on most update runs. All of them sit at
# the paths JAVA_HOME / ANDROID_HOME / PATH already point at.
COPY --from=jdk              /opt/java/openjdk               /opt/java/openjdk
COPY --from=android-emulator /opt/android-sdk/emulator       /opt/android-sdk/emulator
COPY --from=android-emulator /opt/android-sdk/system-images  /opt/android-sdk/system-images
COPY --from=android-ndk      /opt/android-sdk/ndk            /opt/android-sdk/ndk
COPY --from=android-sdk      /opt/android-sdk                /opt/android-sdk
COPY --from=imagemagick      /opt/imagemagick                /opt/imagemagick

# ── Playwright Chromium OS shared libs (the playwright-deps stage's list) ──────
# Baked rather than re-installed on every sandbox open: as an IMAGE layer these libs
# survive container recreation (the runtime writable layer does not).
#
# Above the ENV block and every pinned package, so only a change to the list itself
# (or to the layers above) reinstalls them. The list is bind-mounted, and a read-only
# mount is cached by its content: a rebuilt playwright-deps stage that produces the
# same list is a cache hit here. Unpinned, the same as `install-deps` itself installs
# them. (The base's apt config keeps downloaded packages for the cache mounts.)
# hadolint ignore=DL3008,DL3009
RUN --mount=type=bind,from=playwright-deps,source=/playwright-packages.txt,target=/tmp/playwright-packages.txt --mount=type=cache,target=/var/cache/apt,sharing=locked --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update \
    && xargs -a /tmp/playwright-packages.txt apt-get install -yqq --no-install-recommends

ARG EMULATOR_API

# Every ENV in one block, BELOW the copies above: an edit here rebuilds only the
# cheap layers that follow. They reach every session as-is: the sandbox's entrypoint
# switches to `dev` without resetting the environment, so nothing needs forwarding.
#
# PLAYWRIGHT_BROWSERS_PATH — let Playwright manage its own Chromium, cached under
#   $HOME and persisted via the `pw` cache volume rather than baked into the image.
# ANDROID_HOME / ANDROID_SDK_ROOT — both set to the same path: AGP and most current
#   tooling read ANDROID_HOME, but ANDROID_SDK_ROOT is still checked by some older
#   Gradle/CI tooling, so set both rather than assume which one a given plugin reads.
#   AGP fails with "SDK location not found" if ANDROID_HOME is missing.
# EMULATOR_API — the global ARG at the top, for bin/emulator.sh.
# PATH — the toolchains in front of the base's PATH (which keeps the image's `claude` first).
ENV JAVA_HOME=/opt/java/openjdk \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    EMULATOR_API=${EMULATOR_API} \
    PLAYWRIGHT_BROWSERS_PATH=/home/dev/.cache/ms-playwright \
    PATH=/opt/java/openjdk/bin:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:${PATH}

# ── Project tooling ───────────────────────────────────────────────────────────
# Everything DiceFive needs at RUNTIME beyond the shared base (which already has git,
# gh, jq, python3 + pip/venv, lsof, iproute2, sudo, tzdata and unzip).
#
# python3-fonttools: `app/shared/src/commonMain/composeResources/font/sora.ttf` is a static weight-700
# instance produced with `python3 -m fontTools.varLib.instancer`, subset to printable ASCII with
# `python3 -m fontTools.subset` (see AppLogo.kt's SoraFontFamily doc) - a one-off, not a build-time step,
# so regenerating it later still needs this package. scripts/subset-maths-fonts.sh (the Maths dice's
# cut-down DiceFive Maths fonts) needs it too. Image rasterising is ImageMagick, its own stage above (the
# same build CI uses). fonttools is the distro `python3-fonttools` package, NOT pip: the base image's
# Python is PEP-668 "externally managed", so `pip install fonttools` is refused.
#
# ffmpeg joins that same asset toolchain for audio: this project ships .ogg sound effects
# (app/android/src/main/res/raw/) and re-encoding/gain-adjusting one occasionally comes up (e.g. boosting a
# clip that plays too quiet at SoundPool's volume ceiling of 1.0).
#
# fontconfig backs the copied-in Temurin JDK: the temurin base image normally supplies it (the JDK
# itself only bundles freetype), so on this Debian base we add it explicitly, for the reflective
# java.desktop paths. (tzdata, which the JDK also needs, is in the shared base.)
#
# Chromium's own shared-library deps are NOT listed here (the package names drift
# across Debian releases); they are installed above via the version-proof
# `playwright install-deps chromium`, which resolves the correct set itself.
# hadolint ignore=DL3009
# BEGIN DEBIAN PACKAGES
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update && apt-get install -yqq --no-install-recommends \
        ffmpeg="7:7.1.5-0+deb13u1" \
        fontconfig="2.15.0-2.3" \
        python3-fonttools="4.57.0-1+deb13u1"
# END DEBIAN PACKAGES

# ── ImageMagick's `magick`, and the emulator's virtual device ─────────────────
# `magick -version` runs here, after the tooling block, so the check sees the
# runtime image's libraries.
#
# The virtual device is made as `dev` - the user that boots it - under dev's own
# ~/.android/avd, which no volume mounts over (so `emulator.sh start` boots at
# once). Single quotes on purpose: the inner bash expands EMULATOR_API from the
# ENV block.
# hadolint ignore=SC2016
RUN ln -s /opt/imagemagick/AppRun /usr/local/bin/magick \
    && magick -version \
    && runuser -u dev -- env HOME=/home/dev bash -c \
    'echo no | /opt/android-sdk/cmdline-tools/latest/bin/avdmanager create avd \
        -n "sandbox-api${EMULATOR_API}" -k "system-images;android-${EMULATOR_API};google_apis;x86_64" -d pixel_6' \
    && test -d "/home/dev/.android/avd/sandbox-api${EMULATOR_API}.avd"
