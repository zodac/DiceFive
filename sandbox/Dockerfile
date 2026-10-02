# syntax=docker/dockerfile:1.7
# Sandbox image for running Claude Code with --dangerously-skip-permissions
# against the project this folder sits in, fully isolated from the host.
#
# The runtime is a plain Debian 13 (trixie) base. The JDK, Maven and Node
# toolchains are pulled from their official images as multi-stage build stages
# and the relevant directories copied straight in — so we track upstream image
# tags instead of hand-rolling tarball / NodeSource downloads. On top we also
# add: a NESTED Docker engine (so anything the project spins up runs inside this
# throwaway container, never touching the host daemon), an Android SDK (this is
# an Android app — see the "Android SDK" and emulator blocks below) and the system libraries
# Playwright's Chromium needs.
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
# jdk|maven|node` stage names, the BEGIN/END DEBIAN PACKAGES markers, and the
# cmdline-tools URL + sha1 line.
#
# The nested Docker engine is the one toolchain NOT copied from an image: the
# binaries in the official `docker`/`docker:dind` images are musl-linked (Alpine),
# so they can't be dropped onto this glibc Debian base — Docker's own Debian APT
# repo is the portable path, so it stays a normal apt install (see below).
#
# ── LAYER ORDER IS DELIBERATE: THIS FILE IS OPTIMISED FOR REBUILD TIME ────────
# sandbox.sh rebuilds this image before EVERY launch, so a cache miss is not a
# one-off cost — a contributor pays it on every single session until something
# else invalidates the layer. The dominant source of churn (see `git log -p
# -- sandbox/Dockerfile`) is version-pin bumps: the apt pins and the node tag move
# on almost every bump, while the JDK and Maven tags and the user setup are
# near-static. The Claude Code pin moves most often of all - it tracks whatever
# the registry published today.
#
# Layers are therefore ordered STABLE -> VOLATILE, and each step sits as late as
# its real dependencies allow:
#
#   1. apt cache config            static
#   2. bootstrap apt (3 packages)  rarely bumped; only what the Docker repo needs
#   3. Docker repo + engine        volatile pins, but `usermod -aG docker` needs it
#   4. COPY jdk / maven            large but near-static tags
#   5. COPY node                   large, volatile tag -> below the static COPYs
#   6. user creation               UID/GID build args -> below every large COPY
#   7. tooling apt (the big one)   volatile pins; nothing below depends on it
#   8. Android SDK                 needs `unzip` from step 7; occasional pin bumps
#      + emulator and its AVD      separate layers below it: the emulator image is ~5 GB unpacked, and
#                                  re-downloading it must not ride on a platform-tools bump
#   9. Claude Code install         MOST volatile (a release most days); needs only
#                                  node + the dev user, so it sits below the apt block
#  10. COPY scripts                most-edited files, but the cheapest layer
#
# The rule when adding a step: put it as far DOWN as its dependencies permit, and
# never above a large COPY unless something below genuinely needs it. In
# particular do NOT fold new packages into the bootstrap block just because it
# comes first — that is the block whose invalidation is most expensive.
#
# BuildKit cache mounts back the apt and npm downloads, so even a genuine cache
# miss re-installs from local disk instead of re-fetching over the network.

# ── Toolchain source stages (their payload is copied into the Debian base) ────
# Each of these is only mined for its self-contained toolchain directory; none of
# their base OS reaches the final image.
#   JDK 25  — self-contained under /opt/java/openjdk, portable onto glibc Debian.
#             LTS; see the file-header note above for how a new major is adopted.
#   Maven   — pure Java + shell under /usr/share/maven; the image's own JDK is unused.
#   Node 26 — installed under /usr/local; the prebuilt binaries are glibc-portable
#             (the -trixie tag matches this base's glibc exactly).
FROM eclipse-temurin:25.0.4.1_1-jdk AS jdk
FROM maven:3.9.16-eclipse-temurin-25 AS maven
FROM node:26.10.0-trixie AS node

# ── Runtime base ─────────────────────────────────────────────────────────────
FROM debian:13.7

ARG UID=1000
ARG GID=1000

# Every ENV in one static block at the top. These are free layers that never
# change, so grouping them here keeps them from sitting between (and needlessly
# invalidating) the expensive steps below.
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
# PATH — the dev-owned npm prefix goes FIRST so the self-updated `claude` wins over
#   any other copy on the path.
# ANDROID_HOME / ANDROID_SDK_ROOT — both set to the same path: AGP and most current
#   tooling read ANDROID_HOME, but ANDROID_SDK_ROOT is still checked by some older
#   Gradle/CI tooling, so set both rather than assume which one a given plugin reads.
# KONAN_DATA_DIR — Kotlin/Native's toolchain (a few hundred MB, needed because every build
#   compiles app/shared for iOS) lives inside the persisted ~/.gradle volume instead of its
#   default ~/.konan, which nothing persists - so it downloads once per volume, not once per
#   container. Not baked into the image: it's pinned to the Kotlin version, which the monthly
#   dependency update moves, and a stale copy would just be dead weight.
ENV DEBIAN_FRONTEND=noninteractive \
    JAVA_HOME=/opt/java/openjdk \
    MAVEN_HOME=/opt/maven \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    PLAYWRIGHT_BROWSERS_PATH=/home/dev/.cache/ms-playwright \
    CLAUDE_CONFIG_DIR=/home/dev/.claude \
    KONAN_DATA_DIR=/home/dev/.gradle/konan \
    PATH=/home/dev/.npm-global/bin:/opt/java/openjdk/bin:/opt/maven/bin:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:$PATH

# pipefail for every RUN in this stage so a failure in the middle of a `getent … | cut` pipe (or any
# other) aborts the layer instead of being masked by the pipe's last command (debian base has bash).
SHELL ["/bin/bash", "-o", "pipefail", "-c"]

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

# ── Bootstrap packages (kept deliberately minimal) ───────────────────────────
# ONLY what later BUILD steps need — everything required merely at runtime lives in
# the tooling block at the BOTTOM of this file. This block is the most expensive one
# to invalidate (every layer below rebuilds), so it stays as small as possible: do
# not add a package here unless a RUN below genuinely cannot proceed without it.
#
#   curl + gnupg + ca-certificates → fetch and trust the Docker APT repo signing key
#     in the next step; ca-certificates also provides the TLS chain for the npm
#     install further down.
#   libatomic1 → the Node binary copied from the node stage is dynamically linked
#     against libatomic.so.1, which the bare Debian base does NOT ship. Without it
#     `node` exits 127 ("error while loading shared libraries") and the Claude Code
#     install fails. This used to be satisfied by accident — it arrives as a
#     transitive dependency of the tooling block, which previously ran first — so it
#     is now pinned explicitly rather than relying on install order.
# hadolint ignore=DL3009
# BEGIN DEBIAN PACKAGES
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update && apt-get install -yqq --no-install-recommends \
        ca-certificates="20250419" \
        curl="8.14.1-2+deb13u5" \
        gnupg="2.4.7-21+deb13u1" \
        libatomic1="14.2.0-19"
# END DEBIAN PACKAGES

# ── Nested daemon config: keep the build cache from growing without limit ────
# The `-docker` volume (/var/lib/docker) is by far the largest of the five persistent volumes - every
# gate run leaves image layers and BuildKit cache behind, and nothing reclaims them by default. Left
# alone it reaches tens of GB: measured 77GB on a two-month-old sandbox, 61GB of it build cache.
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

# Sits this high only because the `docker` group it creates is required by the
# `usermod -aG docker dev` below; nothing else above depends on it.
# hadolint ignore=DL3009
# BEGIN DEBIAN PACKAGES
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update && apt-get install -yqq --no-install-recommends \
        docker-ce="5:29.8.1-1~debian.13~trixie" \
        docker-ce-cli="5:29.8.1-1~debian.13~trixie" \
        containerd.io="2.3.6-1~debian.13~trixie" \
        docker-buildx-plugin="0.37.1-1~debian.13~trixie" \
        docker-compose-plugin="5.5.1-1~debian.13~trixie"
# END DEBIAN PACKAGES

# ── JDK 25 / Maven / Node (copied from the toolchain stages above) ───────────
# The three toolchains are self-contained directory trees; drop them into the
# glibc base at the paths JAVA_HOME / MAVEN_HOME / PATH already point at.
#
# Three separate COPY layers, ordered by how often their source tag moves: the JDK
# and Maven tags are near-static, whereas node is bumped on most update runs — so a
# node bump re-copies only node, leaving the (larger) JDK layer cached. Keep node
# LAST of the three, and keep all three ABOVE the user setup so a contributor whose
# host UID/GID differs from the 1000/1000 default does not re-copy ~550MB.
COPY --from=jdk   /opt/java/openjdk /opt/java/openjdk
COPY --from=maven /usr/share/maven  /opt/maven
COPY --from=node  /usr/local        /usr/local

# ── Non-root user `dev` matching the host UID/GID, with passwordless sudo ────
# The Debian base ships no user at ${UID}/${GID}, so this normally just creates
# `dev`. The getent guards keep it robust if a base ever does pre-populate the
# UID/GID: rename whatever owns them to `dev` and point its home at /home/dev.
#
# This is the first layer to consume the UID/GID build args, so it is also the
# cache-invalidation boundary for a contributor on a non-1000 UID (macOS hosts are
# typically 501). Everything expensive and UID-independent is deliberately above it.
#
# /etc/sudoers.d is created explicitly rather than relying on the `sudo` package —
# sudo now installs in the tooling block BELOW this one, so the directory would not
# otherwise exist yet.
#
# ~/.m2 and ~/.gradle are pre-created dev-owned because sandbox.sh mounts named volumes
# there (so the Maven/Gradle caches survive the container's --rm — for Gradle that means
# the downloaded distribution itself plus every AGP/Kotlin/AndroidX/Compose dependency, the
# slow part of a first build). Docker seeds a volume from the image path it covers and
# inherits that path's ownership; if the directory did not exist, the volume would be
# created root-owned and every `gradle`/`mvn` run would fail to write its local cache.
RUN set -eux; \
    if ! getent group "${GID}" >/dev/null; then groupadd -g "${GID}" dev; \
    else groupmod -n dev "$(getent group ${GID} | cut -d: -f1)" || true; fi; \
    if getent passwd "${UID}" >/dev/null; then \
      usermod -l dev -d /home/dev -m -s /bin/bash "$(getent passwd ${UID} | cut -d: -f1)"; \
    else \
      useradd -l -m -u "${UID}" -g "${GID}" -s /bin/bash dev; \
    fi; \
    usermod -aG docker dev; \
    install -d -o dev -g dev /home/dev/.m2; \
    install -d -o dev -g dev /home/dev/.gradle; \
    install -d -m 0755 /etc/sudoers.d; \
    echo 'dev ALL=(ALL) NOPASSWD:ALL' > /etc/sudoers.d/dev; \
    chmod 0440 /etc/sudoers.d/dev

# ── Runtime tooling (the most churn-prone block: kept last) ──────────────────
# Everything the sandbox needs at RUNTIME but nothing above needs at BUILD time.
# These pins move more often than anything else in this file, so the block sits at
# the bottom where its invalidation costs only itself and the two cheap layers
# below — NOT the ~550MB of toolchain COPYs or the Docker engine install.
#
# python3 (+pip/venv) with python3-fonttools, imagemagick, librsvg2-bin and optipng are an
# image-asset toolchain (font/SVG rendering, PNG/ICO rasterising) carried over from the project this
# sandbox was copied from. fonttools now earns its keep in THIS repo too: `app/shared/src/commonMain/composeResources/font/sora.ttf`
# is a static weight-700 instance produced with `python3 -m fontTools.varLib.instancer`, subset to
# printable ASCII with `python3 -m fontTools.subset` (see AppLogo.kt's SoraFontFamily doc) - a one-off,
# not a build-time step, so regenerating it later still needs this package. imagemagick/librsvg2-bin/
# optipng remain unused by this repo; kept because the layer is already built and python3 is generally
# useful in a session. Drop them from the list if the image size ever matters. fonttools is the distro
# `python3-fonttools` package, NOT pip: the base image's Python is PEP-668 "externally managed", so
# `pip install fonttools` is refused.
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
        imagemagick="8:7.1.1.43+dfsg1-1+deb13u12" \
        iproute2="6.15.0-1" \
        jq="1.7.1-6+deb13u4" \
        librsvg2-bin="2.60.0+dfsg-1" \
        lsof="4.99.4+dfsg-2" \
        optipng="0.7.8+ds-1+b1" \
        python3="3.13.5-1" \
        python3-fonttools="4.57.0-1+deb13u1" \
        python3-pip="25.1.1+dfsg-1" \
        python3-venv="3.13.5-1" \
        sudo="1.9.16p2-3+deb13u2" \
        tzdata="2026c-0+deb13u1" \
        unzip="6.0-29+deb13u1"
# END DEBIAN PACKAGES

# ── Android SDK (this project is an Android app) ──────────────────────────────
# Sits here, not up with the JDK/Maven/Node toolchain COPYs above, because it needs
# `unzip` (just installed above) to extract the cmdline-tools archive with its Unix
# permission bits intact — `jar xf` (the JDK's own zip extractor, which would let this
# live in the JDK-adjacent stable region) silently drops the executable bit on every
# tool under cmdline-tools/*/bin, which then fails with "Permission denied" the first
# time sdkmanager shells out to one of them. Confirmed by hand before pinning below.
#
# There is no official upstream Docker image that is just "the Android SDK" the way
# eclipse-temurin/maven/node are for their toolchains, so — unlike those three — this
# is a direct download + checksum + unzip rather than a COPY --from stage.
#
# cmdline-tools is pinned to the build Google's own repository manifest
# (https://dl.google.com/android/repository/repository2-3.xml) currently serves as
# `cmdline-tools;latest` (tool revision 23.0), sha1-verified against that same
# manifest. `platform-tools`, `platforms;android-37.2` and `build-tools;36.0.0` are
# pinned to match this project's compileSdk + compileSdkMinor (37.2 - a minor SDK
# release is its own platform package, and from API 37 even the base release is
# named with a minor, android-37.0) and its `android.buildToolsVersion`
# (app/android/build.gradle.kts) — keep
# those three in step if either changes, or a build will reach out to the network for
# whatever AGP defaults to instead of what is baked in here (the update script's
# consistency check fails the run if they drift). build-tools is 36.0.0 because AGP
# 9.4.1 enforces build-tools >= 36.0.0 regardless of compileSdk and overrides (with a
# network fetch) anything pinned lower.
#
# `yes |` answers the SDK Terms-of-Service prompt sdkmanager still shows on first
# run (piped, not interactive, same technique as the license-acceptance pattern
# common in CI); `--sdk_root` is required because $ANDROID_HOME does not exist as a
# real directory yet at the point sdkmanager runs.
#
# `|| true` on that pipeline, not a bare `set -eux` check: this stage's SHELL runs
# with `pipefail` (see the SHELL directive near the top), and `yes` always dies of
# SIGPIPE (exit 141) the moment sdkmanager stops reading stdin — a SUCCESSFUL run
# still trips pipefail and aborts the build. The three `test` lines right after are
# what actually verify success (and, unlike trusting sdkmanager's exit code, they
# check the one thing that matters: did the packages land on disk); a genuine
# install failure leaves one of them false and `set -e` stops the build there. The
# final chmod is defensive: a no-op after unzip (permissions are already correct),
# it just guards against a future change back to a non-permission-preserving
# extractor going unnoticed.
RUN set -eux; \
    curl -fsSL -o /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip"; \
    echo "e025545c62a8e64c7559119566a569fb1dec5f60  /tmp/cmdline-tools.zip" | sha1sum -c -; \
    mkdir -p /opt/android-sdk/cmdline-tools; \
    unzip -q /tmp/cmdline-tools.zip -d /opt/android-sdk/cmdline-tools; \
    mv /opt/android-sdk/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest; \
    rm -f /tmp/cmdline-tools.zip; \
    yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk \
        "platform-tools" "platforms;android-37.2" "build-tools;36.0.0" >/dev/null || true; \
    test -x /opt/android-sdk/platform-tools/adb; \
    test -d /opt/android-sdk/platforms/android-37.2; \
    test -d /opt/android-sdk/build-tools/36.0.0; \
    chmod -R a+rX /opt/android-sdk

# ── Android emulator + one virtual device (so `sandbox/emulator.sh start` boots at once) ───────────────
# Its own layers, below the SDK block, so a bump to the SDK pins above never re-downloads the ~1.5 GB
# image and a change here never re-downloads the SDK. The emulator and the system image are named by
# package only - sdkmanager has no way to pin a revision, the same as `platform-tools` above - so what
# is baked is whatever Google serves on the day this layer is built; it is rebuilt only when this text
# changes, which is how `EMULATOR_API` below moves it.
#
# EMULATOR_API is the one place the emulator's Android version is chosen, and it FOLLOWS compileSdk: the
# API level the app is compiled against (app/android/build.gradle.kts), so the emulator runs the newest
# Android the project targets. It is written as the image package names it ("37.0": from API 37 even the
# base release carries a minor; older ones are plain, "34"), and it is the newest stable Google APIs image
# of compileSdk's API level - Google publishes no image for minor releases such as 37.2, so the emulator
# can match the level but not the minor. .github/scripts/update_dependency_versions.sh moves it whenever
# compileSdk moves, fails the run if the two drift apart or sandbox/emulator.sh's fallback disagrees with
# this value, and fails it if the image is gone from Google's manifest. Google APIs, not Google Play: only
# the former allows `adb root`, which the Baseline Profile run and the renderer workaround need (see
# .claude/EMULATOR.md). entrypoint.sh forwards this variable to `dev` (sudo strips the environment).
#
# The SDK was installed root-owned, and sdkmanager unpacks the emulator without the other-user execute
# bit, so the `chmod -R a+rX` at the end is what lets `dev` run it (X grants execute only where someone
# already had it). The virtual device is made as `dev` - the user that boots it - under dev's own
# ~/.android/avd, which no volume mounts over. Same `yes | ... || true` shape and for the same reason as
# the SDK block: the `test` lines are what verify the install, since `yes` dies of SIGPIPE under pipefail.
ENV EMULATOR_API=37.0
RUN set -eux; \
    yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk \
        "emulator" "system-images;android-${EMULATOR_API};google_apis;x86_64" >/dev/null || true; \
    test -x /opt/android-sdk/emulator/emulator; \
    test -d "/opt/android-sdk/system-images/android-${EMULATOR_API}/google_apis/x86_64"; \
    chmod -R a+rX /opt/android-sdk
RUN runuser -u dev -- env HOME=/home/dev bash -c \
    'echo no | /opt/android-sdk/cmdline-tools/latest/bin/avdmanager create avd \
        -n "sandbox-api${EMULATOR_API}" -k "system-images;android-${EMULATOR_API};google_apis;x86_64" -d pixel_6' \
    && test -d "/home/dev/.android/avd/sandbox-api${EMULATOR_API}.avd"

# ── Playwright Chromium OS shared libs (libnspr4, libnss3, …) ────────────────
# Baked here rather than re-installed on every sandbox open: as an IMAGE layer
# these libs survive container recreation (the runtime writable layer does not),
# so setup.sh no longer has to re-assert them each open. We do NOT enumerate the
# apt package names in the DEBIAN PACKAGES block above (they drift across Debian
# releases); instead Playwright's own `install-deps` resolves the correct set for
# this base image - version-proof AND cached. Pinned to an explicit version so the
# build stays reproducible (the repo is mounted at runtime, not present at build
# time, so npx fetches the CLI here); keep it in step with any Playwright version
# the project itself declares. Runs as root
# (build default) - no sudo needed, unlike the setup.sh runtime path. The browser
# BINARY itself stays out of the image (persisted in the ms-playwright volume via
# setup.sh) to avoid baking ~150MB of Chromium into every layer.
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked --mount=type=cache,target=/var/lib/apt,sharing=locked \
    npx --yes playwright@1.63.0 install-deps chromium

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
# PINNED, to the version sandbox.sh resolved from the npm registry at build time.
# The runtime self-update CANNOT be relied on to keep the sandbox current: it
# writes into the CONTAINER's writable layer, which `docker run --rm` deletes on
# teardown — so every launch came back on the image's version, downloaded the same
# update again, and showed the same "Update installed. Restart to apply" banner
# that a restart could never clear. Leaving the install unpinned did not help,
# because an unchanged instruction is a cache HIT: the layer was resolved once and
# then reused for months. The build arg is what changes when a release lands, and
# it is therefore the ONLY thing that rebuilds this layer.
#
# `latest` is the default only so a direct `docker build` still works; sandbox.sh
# always passes an explicit version (and falls back to leaving the arg unset, i.e.
# keeping the cached install, when it cannot reach the registry).
#
# The npm cache is a BuildKit cache mount owned by the dev UID, so re-running this
# after an unrelated invalidation resolves from disk rather than the registry.
ARG CLAUDE_CODE_VERSION=latest
RUN --mount=type=cache,target=/home/dev/.npm,uid=${UID},gid=${GID},sharing=locked \
    install -d -o dev -g dev /home/dev/.npm-global \
    && runuser -u dev -- npm config set prefix /home/dev/.npm-global \
    && runuser -u dev -- npm install -g "@anthropic-ai/claude-code@${CLAUDE_CODE_VERSION}"

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
# dockerd started by entrypoint.sh: without it every containerised part of the
# project (dev DB, Testcontainers, the E2E/smoke stacks) is dead, even though the
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
