#!/bin/bash
# ------------------------------------------------------------------------------
# Script Name:  install_imagemagick.sh
#
# Description:  Puts ImageMagick 7's `magick` on a GitHub runner's PATH, for scripts/generate-store-assets.sh:
#               ubuntu-latest ships ImageMagick 6, which has no `magick`. It is ImageMagick's own AppImage,
#               checked against its pinned SHA-256 and extracted rather than mounted (the runners have no
#               FUSE 2). The AppImage's AppRun runs the tool named by the link it was called through, so a
#               `magick` link to it is `magick`.
#
#               The version is pinned HERE rather than in a workflow for the same reason as
#               lint_workflows.sh's image. sandbox/Dockerfile installs the same AppImage from its own copy
#               of the two pins (the image's build context can't reach this file), so the sandbox and CI
#               render with one build: .github/scripts/update_dependency_versions.sh moves IMAGEMAGICK_VERSION
#               and IMAGEMAGICK_SHA256 in both files together to ImageMagick's latest release, and its
#               consistency check fails the run if they drift.
#
# Usage:        .github/scripts/install_imagemagick.sh      (in a GitHub Actions step)
#
# Requirements: curl, sha256sum; RUNNER_TEMP and GITHUB_PATH (set by Actions)
# ------------------------------------------------------------------------------
set -euo pipefail

IMAGEMAGICK_VERSION="7.1.2-34"
IMAGEMAGICK_SHA256="6e9edfd35d5f30b57c27533afe003d6085faaf8607b207426f398d11751acf93"

dir="${RUNNER_TEMP:?set by GitHub Actions}/imagemagick"
mkdir -p "${dir}/bin"
cd "${dir}"
curl -fsSL --retry 3 -o magick.AppImage \
    "https://github.com/ImageMagick/ImageMagick/releases/download/${IMAGEMAGICK_VERSION}/ImageMagick-${IMAGEMAGICK_VERSION}-gcc-x86_64.AppImage"
echo "${IMAGEMAGICK_SHA256}  magick.AppImage" | sha256sum -c -
chmod +x magick.AppImage
./magick.AppImage --appimage-extract > /dev/null
ln -sf "${dir}/squashfs-root/AppRun" bin/magick
echo "${dir}/bin" >> "${GITHUB_PATH:?set by GitHub Actions}"
bin/magick -version | head -n 1
