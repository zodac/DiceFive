#!/usr/bin/env bash
# Claude Code guard for RELEASE_NOTES.md: it's the maintainer's own summary of a release, and Claude
# must never change it without their say-so (see CLAUDE.md). Registered in .claude/settings.json for
# three events, and tells them apart by the payload's hook_event_name:
#
#   - PreToolUse on a file tool (Edit/Write/MultiEdit/NotebookEdit) aimed at RELEASE_NOTES.md:
#     answers "ask", so the edit waits for the user's approval - even in bypass-permissions mode,
#     which is what an "ask" from a hook is for.
#   - PreToolUse on Bash: asks nothing. It records what RELEASE_NOTES.md holds right now...
#   - PostToolUse / PostToolUseFailure on Bash: ...and compares afterwards. If the command changed
#     it (edited, created or deleted), the recorded version is put back and Claude is told the
#     change needs the user's confirmation first.
#
# Shell commands are judged by what they DID rather than what they SAY: an earlier version asked
# whenever a command merely mentioned the file name, which prompted for a commit message, a doc
# edit or a scratch repo that happened to contain the words, and still missed a command that
# changed the file without naming it (a sed over a glob).
#
# One exception, for git: if after the command the file matches the version committed at HEAD
# (a pull, checkout or reset brought it there), that's git moving the tree, not Claude writing
# notes, and it's left alone.
#
# Needs jq to read the payload (the sandbox's setup step warns if it's missing). Without it, a file
# edit whose payload mentions the file still asks, and shell commands go unchecked.
set -euo pipefail

payload="$(cat)"
target="RELEASE_NOTES.md"
project_dir="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
notes="$project_dir/$target"

if ! command -v jq > /dev/null 2>&1; then
  if [[ "$payload" == *'"tool_name":"Bash"'* || "$payload" == *'"tool_name": "Bash"'* ]]; then exit 0; fi
  [[ "$payload" == *"$target"* ]] || exit 0
  event="PreToolUse"; tool="Edit"; path="$target"
else
  event="$(jq -r '.hook_event_name // "PreToolUse"' <<< "$payload")"
  tool="$(jq -r '.tool_name // ""' <<< "$payload")"
  path="$(jq -r '.tool_input.file_path // .tool_input.notebook_path // ""' <<< "$payload")"
  session="$(jq -r '.session_id // "session"' <<< "$payload")"
  call="$(jq -r '.tool_use_id // "call"' <<< "$payload")"
fi

ask() {
  jq -cn --arg reason "$1" \
    '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "ask", permissionDecisionReason: $reason}}' 2> /dev/null \
    || printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"ask","permissionDecisionReason":"%s"}}\n' "$1"
}

# ---- File tools: ask before the edit ----------------------------------------------------------
if [ "$tool" != "Bash" ]; then
  if [ "$event" = "PreToolUse" ] && [ "$(basename -- "${path:-.}")" = "$target" ]; then
    ask "RELEASE_NOTES.md is only changed with the maintainer's explicit confirmation (see CLAUDE.md)."
  fi
  exit 0
fi

# ---- Bash: snapshot before, compare after ----------------------------------------------------
# One snapshot per tool call (by tool_use_id), so parallel commands can't overwrite each other's.
snapshot_dir="${TMPDIR:-/tmp}/claude-release-notes-guard/$(printf '%s' "$project_dir" | cksum | cut -d' ' -f1)"
snapshot="$snapshot_dir/$session-$call"

case "$event" in
  PreToolUse)
    mkdir -p "$snapshot_dir"
    # A snapshot whose command never reported back (interrupted, or the hooks were reloaded
    # mid-call) is never read again; clear out any older than a day. Plain cp, not cp -p, so a
    # snapshot's age is when it was taken, not when the notes were last edited.
    find "$snapshot_dir" -type f -mmin +1440 -delete 2> /dev/null || true
    if [ -e "$notes" ]; then cp -- "$notes" "$snapshot"; else : > "$snapshot.absent"; fi
    ;;

  PostToolUse | PostToolUseFailure)
    if [ -e "$snapshot.absent" ]; then
      rm -f -- "$snapshot.absent"
      [ -e "$notes" ] || exit 0
      before_existed=false
    elif [ -e "$snapshot" ]; then
      if [ -e "$notes" ] && cmp -s -- "$notes" "$snapshot"; then rm -f -- "$snapshot"; exit 0; fi
      before_existed=true
    else
      exit 0 # No snapshot (e.g. the hooks were registered mid-command) - nothing to compare with.
    fi

    # Git moved the file to what's committed at HEAD: not a notes edit.
    if [ -e "$notes" ] && git -C "$project_dir" show "HEAD:$target" 2> /dev/null | cmp -s - "$notes"; then
      rm -f -- "$snapshot"
      exit 0
    fi

    if [ "$before_existed" = true ]; then
      cp -- "$snapshot" "$notes"
      rm -f -- "$snapshot"
    else
      rm -f -- "$notes"
    fi
    jq -cn '{
      decision: "block",
      reason: "That command changed RELEASE_NOTES.md, so the change was undone: the file is only changed with the maintainer'"'"'s explicit confirmation (see CLAUDE.md). Ask them first; once they agree, make the change with the Edit/Write tool.",
      systemMessage: "A shell command changed RELEASE_NOTES.md without your confirmation - it has been put back as it was."
    }'
    ;;
esac
