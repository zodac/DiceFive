#!/usr/bin/env bash
# Points this clone's git at the committed hooks in .githooks/ (today: commit-msg, which enforces the
# "[Category] Short description" subject line). Git never runs hooks from a repository on its own,
# so every fresh clone needs this once. Safe to re-run.
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
git -C "$root_dir" config core.hooksPath .githooks
echo "Git hooks enabled: core.hooksPath -> .githooks"
