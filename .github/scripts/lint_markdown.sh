#!/bin/bash
# ------------------------------------------------------------------------------
# Script Name:  lint_markdown.sh
#
# Description:  Lints every tracked Markdown file with markdownlint, from markdownlint-cli2's official Docker image -
#               except RELEASE_NOTES.md: the maintainer's own text, published as the release's notes as written.
#
#               The rules are code-quality-config/markdown/.markdownlint.json (the code-quality-config submodule),
#               passed with --config.
#
#               The image is pinned HERE, like actionlint's in lint_workflows.sh, so the monthly dependency
#               update can bump it; .github/scripts/update_dependency_versions.sh keeps MARKDOWNLINT_IMAGE at the
#               latest markdownlint-cli2 tag.
#
# Usage:        .github/scripts/lint_markdown.sh      (from the repository root)
#
# Requirements: docker, git, and the code-quality-config submodule checked out
# ------------------------------------------------------------------------------
set -euo pipefail

MARKDOWNLINT_IMAGE="davidanson/markdownlint-cli2:v0.24.0"
MARKDOWNLINT_CONFIG="code-quality-config/markdown/.markdownlint.json"

if [[ ! -f "${MARKDOWNLINT_CONFIG}" ]]; then
    echo "❌ ${MARKDOWNLINT_CONFIG} not found: run \`git submodule update --init\`" >&2
    exit 1
fi

files=()
mapfile -t files < <(git ls-files '*.md' ':!:RELEASE_NOTES.md')

docker run --rm -v "${PWD}:/workdir:ro" -w /workdir "${MARKDOWNLINT_IMAGE}" \
    --config "${MARKDOWNLINT_CONFIG}" "${files[@]}"
