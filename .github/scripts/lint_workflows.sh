#!/bin/bash
# ------------------------------------------------------------------------------
# Script Name:  lint_workflows.sh
#
# Description:  Lints every GitHub workflow under .github/workflows with actionlint, from its official
#               Docker image. actionlint also runs ShellCheck over the workflows' `run:` blocks.
#
#               The image is pinned HERE rather than in a workflow file on purpose: the monthly
#               dependency update pushes with GITHUB_TOKEN, which may not change files under
#               .github/workflows/, so a pin in a workflow could never be bumped by it.
#               .github/scripts/update_dependency_versions.sh keeps ACTIONLINT_IMAGE at the latest
#               actionlint release.
#
# Usage:        .github/scripts/lint_workflows.sh      (from the repository root)
#
# Requirements: docker
# ------------------------------------------------------------------------------
set -euo pipefail

ACTIONLINT_IMAGE="rhysd/actionlint:1.7.12"

docker run --rm -v "${PWD}:/repo" -w /repo "${ACTIONLINT_IMAGE}" -no-color
