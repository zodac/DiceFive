#!/usr/bin/env bash
# First-run / per-open setup for the project sandbox. Runs as the `dev` user with
# CWD = /work. Every step is GUARDED so re-opening the environment is a fast no-op
# when nothing has changed; real work happens only on a fresh sandbox or after a
# relevant file changes. Failures are non-fatal (warn + continue) so the session
# still opens if you're offline.
set -uo pipefail

STATE_DIR="${HOME}/.claude/.sandbox-state"
mkdir -p "${STATE_DIR}"

step()  { printf '\033[36m[setup]\033[0m %s\n' "$*"; }
skip()  { printf '\033[2m[setup] %s (up to date)\033[0m\n' "$*"; }
warn()  { printf '\033[33m[setup] WARN: %s\033[0m\n' "$*" >&2; }
# A step this project has nothing for - distinct from "up to date", which implies it applies here.
none()  { printf '\033[2m[setup] %s (not applicable)\033[0m\n' "$*"; }

cd /work || { warn "no /work mount"; exit 0; }

# ── 1. Git submodules — initialise any the project declares ──────────────────
if git submodule status 2>/dev/null | grep -q '^-'; then
  step "initialising git submodules..."
  git submodule update --init || warn "submodule init failed"
else
  skip "git submodules"
fi

# ── 2. Claude Code PreToolUse guards — prove they work BEFORE the session ────
# A project's guards (.claude/hooks/) are often the only thing standing between an agent and an edit it
# must never make, and they fail SILENTLY: an unregistered, non-executable or jq-less hook simply never
# blocks anything, and nothing in the session says so. So they are exercised here, before any work
# happens, in the environment that will actually run them.
#
# Guarded like every other step here, so a project without the guards reports "not applicable" rather
# than failing - this script is meant to be copy-pasted.
GUARD_TESTS=".claude/hooks/tests/run-hook-tests.sh"
if [[ ! -x "${GUARD_TESTS}" ]]; then
  none "Claude Code hook guards"
elif ! command -v jq >/dev/null 2>&1; then
  warn "jq is missing - the PreToolUse guards cannot parse a hook payload and will NOT block anything"
elif guard_output="$("${GUARD_TESTS}" 2>&1)"; then
  # Last line only (the "All N cases passed" summary), via parameter expansion rather than `| tail -1`, so
  # there is no pipeline whose exit status would be masked inside the message.
  skip "Claude Code hook guards (${guard_output##*$'\n'})"
else
  warn "the PreToolUse guards are NOT behaving as expected - they may not be protecting this session:"
  printf '%s\n' "${guard_output}" >&2
fi

step "ready."
