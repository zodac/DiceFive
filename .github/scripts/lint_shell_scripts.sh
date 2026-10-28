#!/bin/bash
# ------------------------------------------------------------------------------
# Script Name:  lint_shell_scripts.sh
#
# Description:  Lints every tracked shell script (*.sh, and the git hooks in .githooks) with ShellCheck, from its
#               official Docker image. gradlew is Gradle's generated wrapper, so it is left out.
#
#               The rules are code-quality-config/shellscript/.shellcheckrc (the code-quality-config submodule),
#               passed with --rcfile: ShellCheck would otherwise look for a .shellcheckrc above each script.
#
#               The image is pinned HERE, like actionlint's in lint_workflows.sh, so the monthly dependency
#               update can bump it; .github/scripts/update_dependency_versions.sh keeps SHELLCHECK_IMAGE at the
#               latest ShellCheck release.
#
# Usage:        .github/scripts/lint_shell_scripts.sh      (from the repository root)
#
# Requirements: docker, git, and the code-quality-config submodule checked out
# ------------------------------------------------------------------------------
set -euo pipefail

SHELLCHECK_IMAGE="koalaman/shellcheck:v0.11.0"
SHELLCHECKRC="code-quality-config/shellscript/.shellcheckrc"

if [[ ! -f "${SHELLCHECKRC}" ]]; then
    echo "❌ ${SHELLCHECKRC} not found: run \`git submodule update --init\`" >&2
    exit 1
fi

scripts=()
mapfile -t scripts < <(git ls-files '*.sh' '.githooks/*')

docker run --rm -v "${PWD}:/repo:ro" -w /repo "${SHELLCHECK_IMAGE}" --rcfile "${SHELLCHECKRC}" "${scripts[@]}"
