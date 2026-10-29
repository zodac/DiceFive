#!/usr/bin/env bash
# Exercises .claude/hooks/guard-release-notes.sh the way Claude Code calls it - a JSON payload on
# stdin - against a throwaway git repo (via CLAUDE_PROJECT_DIR), never this project's own
# RELEASE_NOTES.md. Run by ai-sandbox/image/final/setup.sh before every session, which reports the last line;
# exits non-zero if any case fails. Needs jq, like the guard itself.
# Each command substitution is a case's subject: a command that fails gives the wrong output, which fails the case.
# shellcheck disable=SC2312
set -uo pipefail

guard="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/guard-release-notes.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
export CLAUDE_PROJECT_DIR="${work}/project" TMPDIR="${work}/tmp"
mkdir -p "${CLAUDE_PROJECT_DIR}" "${TMPDIR}"
notes="${CLAUDE_PROJECT_DIR}/RELEASE_NOTES.md"

git -C "${CLAUDE_PROJECT_DIR}" init -q
printf 'Committed notes\n' > "${notes}"
git -C "${CLAUDE_PROJECT_DIR}" -c user.email=t@t -c user.name=t add RELEASE_NOTES.md
git -C "${CLAUDE_PROJECT_DIR}" -c user.email=t@t -c user.name=t commit -q -m "[Test] Notes"

passed=0
failed=0
pass() { passed=$((passed + 1)); }
fail() { failed=$((failed + 1)); echo "FAIL: $1" >&2; }

# file_tool_output <tool> <path> <extra-json-fields>
file_tool_output() {
  jq -cn --arg tool "$1" --arg path "$2" \
    '{hook_event_name: "PreToolUse", session_id: "s", tool_use_id: "t", tool_name: $tool, tool_input: {file_path: $path, new_string: "see RELEASE_NOTES.md"}}' \
    | "${guard}"
}

# run_bash <call-id> <command> [post-event] - the Pre hook, the command itself, then the Post hook;
# prints what the Post hook said.
run_bash() {
  local id="$1" command="$2" post="${3:-PostToolUse}"
  payload() { jq -cn --arg event "$1" --arg id "${id}" --arg command "${command}" \
    '{hook_event_name: $event, session_id: "s", tool_use_id: $id, tool_name: "Bash", tool_input: {command: $command}}'; }
  [[ -z "$(payload PreToolUse | "${guard}")" ]] || fail "PreToolUse on Bash should never answer (${command})"
  (cd "${CLAUDE_PROJECT_DIR}" && bash -c "${command}") > /dev/null 2>&1 || true
  payload "${post}" | "${guard}"
}

expect_ask() { if [[ "$(jq -r '.hookSpecificOutput.permissionDecision' <<< "$2" 2> /dev/null)" = "ask" ]]; then pass; else fail "$1: expected ask, got '${2}'"; fi; }
expect_silent() { if [[ -z "$2" ]]; then pass; else fail "$1: expected no output, got '$2'"; fi; }
expect_block() { if [[ "$(jq -r '.decision' <<< "$2" 2> /dev/null)" = "block" ]]; then pass; else fail "$1: expected block, got '${2}'"; fi; }
expect_notes() { if [[ "$(cat "${notes}" 2> /dev/null || echo '<absent>')" = "$2" ]]; then pass; else fail "$1: notes are '$(cat "${notes}" 2> /dev/null || echo '<absent>')', expected '$2'"; fi; }

# ---- File tools ----
expect_ask "Edit RELEASE_NOTES.md" "$(file_tool_output Edit "${notes}")"
expect_ask "Write RELEASE_NOTES.md" "$(file_tool_output Write "${notes}")"
expect_ask "MultiEdit RELEASE_NOTES.md" "$(file_tool_output MultiEdit "${notes}")"
expect_silent "Edit another file whose text mentions it" "$(file_tool_output Edit "${CLAUDE_PROJECT_DIR}/.claude/CLAUDE.md")"
expect_silent "Write RELEASE_NOTES.md.bak" "$(file_tool_output Write "${CLAUDE_PROJECT_DIR}/RELEASE_NOTES.md.bak")"

# ---- Bash: mentioning is fine, changing is not ----
expect_silent "command only mentions the file" "$(run_bash c1 'echo "see RELEASE_NOTES.md"; cat RELEASE_NOTES.md')"
expect_notes "...and it is untouched" "Committed notes"

printf 'Draft\n' > "${notes}" # an uncommitted draft, as the maintainer would have while writing notes
expect_block "overwrite via redirect" "$(run_bash c2 'echo hijacked > RELEASE_NOTES.md')"
expect_notes "...draft restored" "Draft"
expect_block "sed over a glob, never naming the file" "$(run_bash c3 "sed -i 's/Draft/Changed/' RELEASE_*.md")"
expect_notes "...draft restored" "Draft"
expect_block "delete" "$(run_bash c4 'rm RELEASE_NOTES.md')"
expect_notes "...draft restored" "Draft"
expect_block "change in a command that then failed" "$(run_bash c5 'echo x > RELEASE_NOTES.md; false' PostToolUseFailure)"
expect_notes "...draft restored" "Draft"

rm "${notes}"
expect_block "create while absent" "$(run_bash c6 'echo new > RELEASE_NOTES.md')"
expect_notes "...removed again" "<absent>"

# ---- Git moving the tree is not a notes edit ----
printf 'Draft\n' > "${notes}"
expect_silent "git checkout back to HEAD's version" "$(run_bash c7 'git checkout -- RELEASE_NOTES.md')"
expect_notes "...kept" "Committed notes"

# ---- Parallel calls keep separate snapshots ----
payload() { jq -cn --arg event "$1" --arg id "$2" '{hook_event_name: $event, session_id: "s", tool_use_id: $id, tool_name: "Bash", tool_input: {command: "x"}}'; }
printf 'Draft\n' > "${notes}"
payload PreToolUse p1 | "${guard}"
payload PreToolUse p2 | "${guard}"
expect_silent "parallel call finishing without changes" "$(payload PostToolUse p2 | "${guard}")"
printf 'hijacked\n' > "${notes}"
expect_block "parallel call whose command changed it" "$(payload PostToolUse p1 | "${guard}")"
expect_notes "...restored" "Draft"

# ---- An edit made while a command runs ----
edit_post() { jq -cn --arg path "${notes}" '{hook_event_name: "PostToolUse", session_id: "other", tool_use_id: "e", tool_name: "Edit", tool_input: {file_path: $path}}' | "${guard}"; }
printf 'Draft\n' > "${notes}"
payload PreToolUse long1 | "${guard}"
printf 'Draft\nApproved line\n' > "${notes}"
expect_silent "an approved Edit's PostToolUse" "$(edit_post)"
expect_silent "a command that ran over an approved Edit" "$(payload PostToolUse long1 | "${guard}")"
expect_notes "...the approved edit kept" "$(printf 'Draft\nApproved line')"

payload PreToolUse long2 | "${guard}"
printf 'Draft\nApproved line\nThe maintainer typed this\n' > "${notes}"
output="$(payload PostToolUse long2 | "${guard}")"
expect_block "a change no approved edit made, during a command" "${output}"
expect_notes "...put back" "$(printf 'Draft\nApproved line')"
saved="$(find "${CLAUDE_PROJECT_DIR}/.git/release-notes-guard" -name 'replaced-*-long2.md' 2> /dev/null | head -1)"
if [[ -n "${saved}" ]] && [[ "$(cat "${saved}")" = "$(printf 'Draft\nApproved line\nThe maintainer typed this')" ]]; then pass; else fail "...the replaced version saved in .git/release-notes-guard/ (got '${saved}')"; fi
if [[ -n "${saved}" ]] && [[ "$(jq -r '.reason' <<< "${output}")" == *"${saved}"* ]]; then pass; else fail "...and Claude told where"; fi

expect_block "a command's own change after an approved edit" "$(run_bash long3 'echo hijacked >> RELEASE_NOTES.md')"
expect_notes "...put back" "$(printf 'Draft\nApproved line')"

expect_silent "a file tool's PostToolUse on another file" "$(jq -cn '{hook_event_name: "PostToolUse", session_id: "s", tool_use_id: "e2", tool_name: "Write", tool_input: {file_path: "/elsewhere/NOTES.md"}}' | "${guard}")"

if [[ "${failed}" -gt 0 ]]; then
  echo "${failed} of $((passed + failed)) cases FAILED"
  exit 1
fi
echo "All ${passed} cases passed"
