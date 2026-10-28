# syntax=docker/dockerfile:1.7
# Sandbox image for running Claude Code with --dangerously-skip-permissions
# against the project this folder sits in, fully isolated from the host.
#
# The runtime is a plain Debian 13 (trixie) base. The JDK and Node toolchains
# are pulled from their official images as multi-stage build stages and the
# relevant directories copied straight in — so we track upstream image tags
# instead of hand-rolling tarball / NodeSource downloads. The Android SDK, NDK,
# emulator and ImageMagick are downloaded in build stages of their own and copied
# in the same way. On top we also add: a NESTED Docker engine (so anything the
# project spins up runs inside this throwaway container, never touching the host
# daemon) and the system libraries Playwright's Chromium needs (for an occasional
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
# NOTE: every version below is pinned, and bumped by running
# .github/scripts/update_dependency_versions.sh from the repo root (nothing runs it
# on a schedule), so a build that fails on a version Debian has since dropped means
# it is time for a run. That script relies on this file's shape: the `FROM … AS
# jdk|node` stage names, the BEGIN/END DEBIAN PACKAGES markers, the
# cmdline-tools URL + sha1 line, the `ARG EMULATOR_API` line and the
# `ENV IMAGEMAGICK_VERSION`/`IMAGEMAGICK_SHA256` lines.
#
# The nested Docker engine is the one toolchain NOT copied from an image: the
# binaries in the official `docker`/`docker:dind` images are musl-linked (Alpine),
# so they can't be dropped onto this glibc Debian base — Docker's own Debian APT
# repo is the portable path, so it stays a normal apt install (see below).
#
# ── LAYER ORDER IS DELIBERATE: THIS FILE IS OPTIMISED FOR REBUILD TIME ────────
# sandbox.sh rebuilds this image before EVERY launch, so a cache miss is not a
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
# package pin, an ENV or the user setup only ever rebuilds the cheap layers below.
# They used to sit below the apt blocks, and a one-line `tzdata` bump or an ENV
# edit re-downloaded the lot.
#
# Plain COPY, not `COPY --link`: on the classic overlay2 image store (the default
# on a Linux host's Docker Engine) a linked COPY is still re-copied once a layer
# above it changes, so the order is what keeps them cached. Within the runtime
# stage:
#
#   1. COPY the downloaded toolchains  rarely change; ordered JDK -> emulator -> NDK -> SDK
#                                      -> ImageMagick, by how often their pins move
#   2. apt cache config                static
#   3. Playwright's Chromium libs      ~70 packages; reruns only when the playwright-deps
#                                      stage's package list changes
#   4. ENV block                       edited by hand now and then
#   5. bootstrap apt                   3+1 packages, rarely bumped
#   6. user creation                   UID/GID build args
#   7. Docker repo + engine            volatile pins
#   8. tooling apt                     volatile pins
#   9. COPY node                       bumped on most update runs
#  10. ImageMagick link, AVD           tiny
#  11. Claude Code install             MOST volatile (a release most days)
#  12. COPY scripts                    most-edited files, but the cheapest layer
#
# The rule when adding a step: a download of any size goes in a stage of its own
# and is COPY'd in at the top, and so does any slow work whose result can be
# copied; anything else goes as far DOWN as its dependencies permit.
#
# BuildKit cache mounts back the apt and npm downloads, so even a genuine cache
# miss in the runtime stage re-installs from local disk instead of re-fetching over
# the network.

# ── The emulator's Android version ────────────────────────────────────────────
# EMULATOR_API is the one place the emulator's Android version is chosen, and it FOLLOWS compileSdk: the
# API level the app is compiled against (gradle/android-sdk.properties), so the emulator runs the newest
# Android the project targets. It is written as the image package names it ("37.0": from API 37 even the
# base release carries a minor; older ones are plain, "34"), and it is the newest stable Google APIs image
# of compileSdk's API level - Google publishes no image for minor releases such as 37.2, so the emulator
# can match the level but not the minor. .github/scripts/update_dependency_versions.sh moves it whenever
# compileSdk moves, fails the run if the two drift apart or sandbox/emulator.sh's fallback disagrees with
# this value, and fails it if the image is gone from Google's manifest. Google APIs, not Google Play: only
# the former allows `adb root`, which the Baseline Profile run and the renderer workaround need (see
# .claude/EMULATOR.md).
#
# A global ARG, because two stages need it: the emulator stage installs its image, and the runtime stage
# makes the virtual device and exports it as ENV (entrypoint.sh forwards it to `dev`, since sudo strips the
# environment). CI's emulator reads this line too (ci_emulator.sh).
ARG EMULATOR_API=37.0

# ── Toolchain source stages (their payload is copied into the Debian base) ────
# Each of these is only mined for its self-contained toolchain directory; none of
# their base OS reaches the final image.
#   JDK 25  — self-contained under /opt/java/openjdk, portable onto glibc Debian.
#             LTS; see the file-header note above for how a new major is adopted.
#   Node 26 — installed under /usr/local; the prebuilt binaries are glibc-portable
#             (the -trixie tag matches this base's glibc exactly).
FROM eclipse-temurin:25.0.4.1_1-jdk AS jdk
FROM node:26.12.1-trixie AS node

# ── Download stage: curl + unzip for the Android and ImageMagick stages ───────
# Unpinned on purpose, unlike every other package in this file: nothing installed
# here reaches the runtime image, only what the stages below download with it. An
# unchanged instruction is a cache hit, so this layer is rebuilt only when its own
# text or the Debian base changes, and pinning it would only add bumps that
# re-download everything built on it. No BEGIN/END markers, so the update script
# leaves it alone.
#
# Its apt cache mounts have ids of their own, so its unpinned state never mixes
# with the runtime stage's. docker-clean is removed for the same reason as in the
# runtime stage below: it would empty them.
FROM debian:13.7 AS fetch
SHELL ["/bin/bash", "-o", "pipefail", "-c"]
# hadolint ignore=DL3008,DL3009
RUN --mount=type=cache,id=fetch-apt-cache,target=/var/cache/apt,sharing=locked --mount=type=cache,id=fetch-apt-lib,target=/var/lib/apt,sharing=locked \
    rm -f /etc/apt/apt.conf.d/docker-clean \
    && apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -yqq --no-install-recommends \
        ca-certificates curl unzip

# ── Android cmdline-tools (the installer the Android stages below share) ──────
# There is no official upstream Docker image that is just "the Android SDK" the way
# eclipse-temurin/node are for their toolchains, so — unlike those two — this
# is a direct download + checksum + unzip rather than a COPY --from stage.
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

# ── Android emulator + its system image (~6 GB: so `sandbox/emulator.sh start` boots at once) ─────────
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
# because AGP 9.4.1 enforces build-tools >= 36.0.0 regardless of compileSdk and
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
# runtime, not present at build time, so npx fetches the CLI here). It runs on a bare
# copy of the runtime base, and the packages it marks as manually installed are its
# list; the runtime stage installs exactly those near its top. A node or Playwright
# bump reruns this stage, but the runtime layer only reruns when the list it writes
# changes.
#
# /var/lib/apt is NOT a cache mount here: it holds apt's manual/auto marks, which
# must start from the base's own for the list to be right.
#
# The project declares no Playwright; a session that wants a headless browser (e.g.
# to screenshot an HTML preview) runs `npx playwright@<this version> install
# chromium` itself. The browser BINARY stays out of the image (it lands in the
# persisted ms-playwright volume) to avoid baking ~150MB of Chromium into every layer.
FROM debian:13.7 AS playwright-deps
SHELL ["/bin/bash", "-o", "pipefail", "-c"]
COPY --from=node /usr/local /usr/local
# hadolint ignore=DL3008,DL3009
RUN --mount=type=cache,id=playwright-apt-cache,target=/var/cache/apt,sharing=locked --mount=type=cache,id=playwright-npm,target=/root/.npm,sharing=locked \
    rm -f /etc/apt/apt.conf.d/docker-clean \
    && apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -yqq --no-install-recommends ca-certificates libatomic1 \
    && apt-mark showmanual | sort > /tmp/before \
    && DEBIAN_FRONTEND=noninteractive npx --yes playwright@1.64.1 install-deps chromium \
    && apt-mark showmanual | sort | comm -13 /tmp/before - > /playwright-packages.txt \
    && test -s /playwright-packages.txt

# ── Runtime base ─────────────────────────────────────────────────────────────
FROM debian:13.7

# pipefail for every RUN in this stage so a failure in the middle of a `getent … | cut` pipe (or any
# other) aborts the layer instead of being masked by the pipe's last command (debian base has bash).
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

# ── apt cache configuration (static) ─────────────────────────────────────────
# Debian base images ship /etc/apt/apt.conf.d/docker-clean, which deletes every
# downloaded .deb the moment apt finishes — that would empty the BuildKit cache
# mounts below on each run and defeat the point. Removing it and setting
# Keep-Downloaded-Packages lets the mounts retain the archives between builds, so
# a bumped pin re-installs from local disk instead of re-downloading.
#
# The apt RUNs below therefore deliberately do NOT `apt-get clean` or
# `rm -rf /var/lib/apt/lists/*` (that is what DL3009 asks for): both paths ARE the
# cache mounts, so cleaning them would throw away exactly what we want to keep.
# Nothing is left in the image layer either way — a cache mount is not part of the
# filesystem it is mounted over.
#
# FORMATTING CONSTRAINT: keep BOTH --mount flags on the same physical line as `RUN`, and
# keep the BEGIN/END DEBIAN PACKAGES markers around each list. Tooling that bumps these
# pins harvests package names from the block with `grep -oP '^\s*[a-z0-9.+-]+(?==)'`, which
# would otherwise pick up a continuation line beginning with `--mount=` as a package called
# "--mount" and silently stop updating the whole block.
RUN rm -f /etc/apt/apt.conf.d/docker-clean \
    && echo 'Binary::apt::APT::Keep-Downloaded-Packages "true";' > /etc/apt/apt.conf.d/keep-cache

# ── Playwright Chromium OS shared libs (the playwright-deps stage's list) ──────
# Baked rather than re-installed on every sandbox open: as an IMAGE layer these libs
# survive container recreation (the runtime writable layer does not), so setup.sh
# does not have to re-assert them each open.
#
# Above the ENV block and every pinned package, so only a change to the list itself
# (or to the layers above) reinstalls them. The list is bind-mounted, and a read-only
# mount is cached by its content: a rebuilt playwright-deps stage that produces the
# same list is a cache hit here. Unpinned, the same as `install-deps` itself installs
# them; DEBIAN_FRONTEND is inline because the ENV block comes after.
# hadolint ignore=DL3008,DL3009
RUN --mount=type=bind,from=playwright-deps,source=/playwright-packages.txt,target=/tmp/playwright-packages.txt --mount=type=cache,target=/var/cache/apt,sharing=locked --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update \
    && xargs -a /tmp/playwright-packages.txt env DEBIAN_FRONTEND=noninteractive apt-get install -yqq --no-install-recommends

ARG EMULATOR_API

# Every ENV in one block, BELOW the copies above: an edit here rebuilds only the
# cheap layers that follow.
#
# PLAYWRIGHT_BROWSERS_PATH — let Playwright manage its own Chromium, cached under
#   $HOME and persisted via a volume rather than baked into the image.
# CLAUDE_CONFIG_DIR — keep ALL of Claude Code's config in the persisted ~/.claude
#   volume. By default Claude splits its state: the ~/.claude/ directory (history,
#   OAuth credentials) AND a separate ~/.claude.json file in the home root
#   (onboarding/terminal-setup state + login). Only the directory is volume-mounted,
#   so without this the .claude.json file is recreated empty on every run — forcing
#   terminal reconfiguration + re-login each time. This redirects .claude.json (and
#   credentials) INTO the mounted dir, so it all persists. (entrypoint.sh already
#   forwards this var to the `dev` user via --preserve-env.)
# DISABLE_AUTOUPDATER — Claude Code is pinned per build (see the install block near
#   the bottom); a runtime self-update would only land in the container's writable
#   layer, which `--rm` deletes, so every launch re-downloaded the same update.
# PATH — the dev-owned npm prefix goes FIRST so the image's `claude` wins over any
#   other copy on the path.
# ANDROID_HOME / ANDROID_SDK_ROOT — both set to the same path: AGP and most current
#   tooling read ANDROID_HOME, but ANDROID_SDK_ROOT is still checked by some older
#   Gradle/CI tooling, so set both rather than assume which one a given plugin reads.
# EMULATOR_API — the global ARG at the top, for sandbox/emulator.sh.
ENV DEBIAN_FRONTEND=noninteractive \
    JAVA_HOME=/opt/java/openjdk \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    EMULATOR_API=${EMULATOR_API} \
    PLAYWRIGHT_BROWSERS_PATH=/home/dev/.cache/ms-playwright \
    CLAUDE_CONFIG_DIR=/home/dev/.claude \
    DISABLE_AUTOUPDATER=1 \
    PATH=/home/dev/.npm-global/bin:/opt/java/openjdk/bin:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:$PATH

# ── Bootstrap packages (kept deliberately minimal) ───────────────────────────
# ONLY what later BUILD steps need — everything required merely at runtime lives in
# the tooling block below. Every layer below this one rebuilds when it changes, so
# it stays as small as possible: do not add a package here unless a RUN below
# genuinely cannot proceed without it.
#
#   curl + gnupg + ca-certificates → fetch and trust the Docker APT repo signing key
#     in the Docker repo step; ca-certificates also provides the TLS chain for the npm
#     installs further down.
#   libatomic1 → the Node binary copied from the node stage is dynamically linked
#     against libatomic.so.1, which the bare Debian base does NOT ship. Without it
#     `node` exits 127 ("error while loading shared libraries") and the Claude Code
#     install fails. It also arrives as a transitive dependency of the tooling
#     block, but is pinned explicitly rather than relying on that.
# hadolint ignore=DL3009
# BEGIN DEBIAN PACKAGES
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update && apt-get install -yqq --no-install-recommends \
        ca-certificates="20250419+deb13u1" \
        curl="8.14.1-2+deb13u7" \
        gnupg="2.4.7-21+deb13u1" \
        libatomic1="14.2.0-19"
# END DEBIAN PACKAGES

# ── Non-root user `dev` matching the host UID/GID, with passwordless sudo ────
# The Debian base ships no user at ${UID}/${GID}, so this normally just creates
# `dev`. The getent guards keep it robust if a base ever does pre-populate the
# UID/GID: rename whatever owns them to `dev` and point its home at /home/dev.
#
# UID/GID are declared HERE, not at the top of the stage: every RUN after an ARG's
# declaration gets it as an environment variable, so a value that differs from the
# last build (a macOS host is typically 501) misses the cache from the first RUN
# after the ARG, not from the first one that uses it.
#
# The `docker` group is created here rather than by the docker-ce package below (whose
# install reuses an existing group), so this block does not have to wait for it.
#
# /etc/sudoers.d is created explicitly rather than relying on the `sudo` package —
# sudo installs in the tooling block BELOW this one, so the directory would not
# otherwise exist yet.
#
# ~/.m2 and ~/.gradle are pre-created dev-owned because sandbox.sh mounts named volumes
# there (so the caches survive the container's --rm — for Gradle that means the downloaded
# distribution itself plus every AGP/Kotlin/AndroidX/Compose dependency, the slow part of a
# first build; ~/.m2 is where Robolectric keeps the android-all jars it downloads, ~600MB). Docker seeds a volume from the image path it covers and
# inherits that path's ownership; if the directory did not exist, the volume would be
# created root-owned and every `gradle`/`mvn` run would fail to write its local cache.
ARG UID=1000
ARG GID=1000
RUN set -eux; \
    if ! getent group "${GID}" >/dev/null; then groupadd -g "${GID}" dev; \
    else groupmod -n dev "$(getent group ${GID} | cut -d: -f1)" || true; fi; \
    if getent passwd "${UID}" >/dev/null; then \
      usermod -l dev -d /home/dev -m -s /bin/bash "$(getent passwd ${UID} | cut -d: -f1)"; \
    else \
      useradd -l -m -u "${UID}" -g "${GID}" -s /bin/bash dev; \
    fi; \
    getent group docker >/dev/null || groupadd -r docker; \
    usermod -aG docker dev; \
    install -d -o dev -g dev /home/dev/.m2; \
    install -d -o dev -g dev /home/dev/.gradle; \
    install -d -m 0755 /etc/sudoers.d; \
    echo 'dev ALL=(ALL) NOPASSWD:ALL' > /etc/sudoers.d/dev; \
    chmod 0440 /etc/sudoers.d/dev

# ── Nested daemon config: keep the build cache from growing without limit ────
# The `-docker` volume (/var/lib/docker) is by far the largest of the four named volumes - every
# nested build or run leaves image layers and BuildKit cache behind, and nothing reclaims them by default.
# Left alone it reaches tens of GB: measured 77GB on a two-month-old sandbox of another project, 61GB of
# it build cache.
# BuildKit's own GC is the fix, but it is off unless configured, so the policy is baked into the image
# (rather than left to the host) - a copied sandbox inherits the bound with no setup.
# `maxUsedSpace` keeps enough cache to make a re-run of the docker gate a cache hit while capping the
# total; `keepDuration` drops anything untouched for a week. Reclaim the rest with `sandbox.sh prune`.
COPY daemon.json /etc/docker/daemon.json

# ── Docker engine + compose plugin (the nested daemon) ───────────────────────
# The base is pinned to Debian, so the repo distro is hardcoded as `debian`; only the
# release codename is derived (from /etc/os-release, via sed rather than a sourced shell
# var) so a base point/major bump follows automatically.
RUN install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
    && chmod a+r /etc/apt/keyrings/docker.asc \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(sed -n 's/^VERSION_CODENAME=//p' /etc/os-release) stable" \
         > /etc/apt/sources.list.d/docker.list

# hadolint ignore=DL3009
# BEGIN DEBIAN PACKAGES
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update && apt-get install -yqq --no-install-recommends \
        docker-ce="5:29.9.2-1~debian.13~trixie" \
        docker-ce-cli="5:29.9.2-1~debian.13~trixie" \
        containerd.io="2.4.2-3~debian.13~trixie" \
        docker-buildx-plugin="0.38.1-1~debian.13~trixie" \
        docker-compose-plugin="5.6.1-1~debian.13~trixie"
# END DEBIAN PACKAGES

# ── Runtime tooling ───────────────────────────────────────────────────────────
# Everything the sandbox needs at RUNTIME but nothing above needs at BUILD time.
#
# python3 (+pip/venv) with python3-fonttools: `app/shared/src/commonMain/composeResources/font/sora.ttf`
# is a static weight-700 instance produced with `python3 -m fontTools.varLib.instancer`, subset to
# printable ASCII with `python3 -m fontTools.subset` (see AppLogo.kt's SoraFontFamily doc) - a one-off,
# not a build-time step, so regenerating it later still needs this package. scripts/subset-maths-fonts.sh
# (the Maths dice's cut-down DiceFive Maths fonts) needs it too. Image rasterising is ImageMagick, its own
# stage above (the same build CI uses). fonttools is the distro `python3-fonttools` package, NOT pip: the
# base image's Python is PEP-668 "externally managed", so `pip install fonttools` is refused.
#
# ffmpeg joins that same asset toolchain for audio: this project ships .ogg sound effects
# (app/android/src/main/res/raw/) and re-encoding/gain-adjusting one occasionally comes up (e.g. boosting a
# clip that plays too quiet at SoundPool's volume ceiling of 1.0).
#
# lsof + iproute2 (`ss`) find whatever is LISTENing on a port inside the sandbox — the usual way to
# clear a stale process holding a port before rerunning something.
#
# fontconfig + tzdata back the copied-in Temurin JDK: the temurin base image normally supplies these
# (the JDK itself only bundles freetype), so on this bare Debian base we add them explicitly — tzdata
# so `app.timezone` / TZ date boundaries resolve, fontconfig for the reflective java.desktop paths.
#
# sudo provides the runtime `sudo` binary; /etc/sudoers.d/dev is already written above.
#
# gh is the GitHub CLI, so a session can read the CI run it just triggered (`gh run view --log-failed`)
# rather than asking someone to paste the log in. Debian's own package, not GitHub's apt repo: the repo
# would cost a keyring + sources step and a volatile pin for a tool nothing builds against, and the
# Docker engine above is the deliberate exception (its binaries are musl-linked, so apt is the only
# portable path). The tradeoff is that trixie ships a version behind upstream, which reads runs, PRs and
# logs perfectly well - swap in the upstream repo if a session ever needs a genuinely new subcommand.
# It ships NO credentials, and nothing persists any: a `gh auth login` is entered per session and dies
# with the container. Deliberate - see the README's Notes before adding a token passthrough or a volume
# for ~/.config/gh, because that is a policy decision that has already been taken the other way.
#
# unzip: .github/scripts/update_dependency_versions.sh reads AAR metadata with it. The Android
# stages above bring their own.
#
# Chromium's own shared-library deps are NOT listed here (the package names drift
# across Debian releases); they are installed just below via the version-proof
# `playwright install-deps chromium`, which resolves the correct set itself.
# hadolint ignore=DL3009
# BEGIN DEBIAN PACKAGES
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update && apt-get install -yqq --no-install-recommends \
        bash="5.2.37-2+b10" \
        ffmpeg="7:7.1.5-0+deb13u1" \
        fontconfig="2.15.0-2.3" \
        gh="2.46.0-3" \
        git="1:2.47.3-0+deb13u1" \
        iproute2="6.15.0-1" \
        jq="1.7.1-6+deb13u4" \
        lsof="4.99.4+dfsg-2" \
        python3="3.13.5-1" \
        python3-fonttools="4.57.0-1+deb13u1" \
        python3-pip="25.1.1+dfsg-1" \
        python3-venv="3.13.5-1" \
        sudo="1.9.16p2-3+deb13u2" \
        tzdata="2026e-0+deb13u1" \
        unzip="6.0-29+deb13u1"
# END DEBIAN PACKAGES

# ── Node (copied from the node stage above) ───────────────────────────────────
# Down here rather than with the other toolchains at the top: its tag moves on most
# update runs, and it is small enough (~230MB) to re-copy cheaply. Nothing above
# needs it; the Claude Code install below does.
COPY --from=node /usr/local /usr/local

# ── ImageMagick's `magick`, and the emulator's virtual device ─────────────────
# `magick -version` runs here, after the tooling block, so the check sees the
# runtime image's libraries.
#
# The virtual device is made as `dev` - the user that boots it - under dev's own
# ~/.android/avd, which no volume mounts over (so `sandbox/emulator.sh start` boots
# at once). Single quotes on purpose: the inner bash expands EMULATOR_API from the
# ENV block.
# hadolint ignore=SC2016
RUN ln -s /opt/imagemagick/AppRun /usr/local/bin/magick \
    && magick -version \
    && runuser -u dev -- env HOME=/home/dev bash -c \
    'echo no | /opt/android-sdk/cmdline-tools/latest/bin/avdmanager create avd \
        -n "sandbox-api${EMULATOR_API}" -k "system-images;android-${EMULATOR_API};google_apis;x86_64" -d pixel_6' \
    && test -d "/home/dev/.android/avd/sandbox-api${EMULATOR_API}.avd"

# ── Claude Code CLI into a dev-owned npm prefix ──────────────────────────────
# Installing `-g` into npm's default /usr prefix makes the package and the
# /usr/bin/claude launcher root-owned, so the unprivileged `dev` user can't
# write to them — /doctor reports "npm global folder isn't writable". Instead,
# point npm's global prefix at a dev-owned dir (persisted in the image layer,
# baked via .npmrc so it needs no runtime env) and install AS dev. The prefix's
# bin dir is already on PATH (see the ENV block).
# runuser (util-linux) drops to `dev` for the install without invoking sudo (hadolint DL3004); like
# `sudo -H` it resets HOME to /home/dev, so npm writes its config to the dev-owned ~/.npmrc.
#
# PINNED, to the version sandbox.sh resolved from the npm registry at build time,
# with the runtime self-update off (DISABLE_AUTOUPDATER in the ENV block). The
# self-update CANNOT be relied on to keep the sandbox current: it
# writes into the CONTAINER's writable layer, which `docker run --rm` deletes on
# teardown — so every launch came back on the image's version, downloaded the same
# update again, and showed the same "Update installed. Restart to apply" banner
# that a restart could never clear. Leaving the install unpinned did not help,
# because an unchanged instruction is a cache HIT: the layer was resolved once and
# then reused for months. The build arg is what changes when a release lands, and
# it is therefore the ONLY thing that rebuilds this layer.
#
# `latest` is the default only so a direct `docker build` still works; sandbox.sh
# always passes an explicit version. When it cannot reach the registry it passes the
# version on the existing image's label (below), so the layer stays a cache hit.
#
# The npm cache is a BuildKit cache mount owned by the dev UID, so re-running this
# after an unrelated invalidation resolves from disk rather than the registry.
ARG CLAUDE_CODE_VERSION=latest
RUN --mount=type=cache,target=/home/dev/.npm,uid=${UID},gid=${GID},sharing=locked \
    install -d -o dev -g dev /home/dev/.npm-global \
    && runuser -u dev -- npm config set prefix /home/dev/.npm-global \
    && runuser -u dev -- npm install -g "@anthropic-ai/claude-code@${CLAUDE_CODE_VERSION}"
# Read back by sandbox.sh's offline fallback.
LABEL net.zodac.sandbox.claude-code-version="${CLAUDE_CODE_VERSION}"

# ── Entrypoint + helper scripts (most-edited files, cheapest layer) ──────────
# Last on purpose: these three are edited far more often than any pin, and landing
# them at the bottom means an edit rebuilds nothing but this COPY. --chmod sets the
# mode inline, avoiding a second layer for a `chmod +x`.
COPY --chmod=0755 entrypoint.sh /usr/local/bin/entrypoint.sh
COPY --chmod=0755 launch.sh     /usr/local/bin/sandbox-launch.sh
COPY --chmod=0755 setup.sh      /usr/local/bin/sandbox-setup.sh

WORKDIR /work

# ── Healthcheck ──────────────────────────────────────────────────────────────
# Purely for consistency with the runtime image (and any tooling that watches
# `docker ps` health), not something the sandbox itself acts on.
#
# The sandbox runs no service, so the one thing worth probing is the nested
# dockerd started by entrypoint.sh: without it anything containerised (here,
# .github/scripts/lint_workflows.sh's actionlint) fails, even though the
# container is still "up" from the host daemon's point of view. `docker info`
# talks to that nested daemon over /var/run/docker.sock and exits non-zero when
# it is unreachable; --format keeps the output to a single line (Docker retains
# the probe's output in the health log). Exec form so no shell is needed.
#
# The start period matches the entrypoint's own 30s (60 x 0.5s) dockerd wait, so a
# slow daemon start is never reported unhealthy.
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD ["docker", "info", "--format", "{{.ServerVersion}}"]

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
