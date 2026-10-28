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
#   - PostToolUse on a file tool aimed at RELEASE_NOTES.md: an edit the user approved. What it wrote
#     is remembered (its checksum, in the checkout's .git), so a shell command still running in any
#     session meanwhile doesn't take the file for one it changed, and put it back.
#
# Who changed the file can't be seen, only that it changed: an edit the user makes themselves while a
# long command runs (a Gradle build) looks just like one the command made. So nothing put back is ever
# lost: the version replaced is saved first (in .git/release-notes-guard/), and Claude is told where.
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
  # One jq for every field (the hook runs around each tool call), joined by the unit separator: unlike a
  # tab, it isn't whitespace to `read`, so an empty field doesn't collapse into the next.
  IFS=$'\x1f' read -r -d '' event tool path session call < <(jq -j '[
      .hook_event_name // "PreToolUse",
      .tool_name // "",
      .tool_input.file_path // .tool_input.notebook_path // "",
      .session_id // "session",
      .tool_use_id // "call"
    ] | join("\u001f")' <<< "$payload") || true
fi

# Shared by every session on this checkout, and never committed: the approved edits, and the versions put back.
git_dir="$(git -C "$project_dir" rev-parse --absolute-git-dir 2> /dev/null || true)"
state_dir="${git_dir:-${TMPDIR:-/tmp}/claude-release-notes-guard-state}/release-notes-guard"
approved="$state_dir/approved"

# The notes' checksum, or "absent".
checksum() { if [ -e "$notes" ]; then cksum < "$notes" | cut -d' ' -f1-2; else echo absent; fi; }

ask() {
  jq -cn --arg reason "$1" \
    '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "ask", permissionDecisionReason: $reason}}' 2> /dev/null \
    || printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"ask","permissionDecisionReason":"%s"}}\n' "$1"
}

# ---- File tools: ask before the edit, remember it after ---------------------------------------
if [ "$tool" != "Bash" ]; then
  [ "$(basename -- "${path:-.}")" = "$target" ] || exit 0
  case "$event" in
    PreToolUse) ask "RELEASE_NOTES.md is only changed with the maintainer's explicit confirmation (see CLAUDE.md)." ;;
    PostToolUse)
      # The last 20 approved versions are plenty: a command only needs the ones written while it ran.
      mkdir -p "$state_dir"
      { checksum; tail -n 19 "$approved" 2> /dev/null || true; } > "$approved.new" && mv -f -- "$approved.new" "$approved"
      ;;
  esac
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

    # An edit the user approved, made while the command ran (in this session or another): theirs to keep.
    if grep -qxF -- "$(checksum)" "$approved" 2> /dev/null; then
      rm -f -- "$snapshot"
      exit 0
    fi

    # Saved before it's put back, in case the change was the user's own: who made it can't be seen.
    mkdir -p "$state_dir"
    saved="$state_dir/replaced-$(date +%Y%m%d-%H%M%S)-$call.md"
    if [ -e "$notes" ]; then cp -- "$notes" "$saved"; else saved="(none: the command deleted the file)"; fi

    if [ "$before_existed" = true ]; then
      cp -- "$snapshot" "$notes"
      rm -f -- "$snapshot"
    else
      rm -f -- "$notes"
    fi
    jq -cn --arg saved "$saved" '{
      decision: "block",
      reason: ("RELEASE_NOTES.md changed while that command ran, so it was put back as it was before: the file is only changed with the maintainer'"'"'s explicit confirmation (see CLAUDE.md). If the command did not write it, the maintainer may have been editing it meanwhile: the version replaced is saved at " + $saved + " - tell them, and restore it only if they say so, with the Edit/Write tool. If the command did, ask them first; once they agree, make the change with the Edit/Write tool."),
      systemMessage: ("RELEASE_NOTES.md changed during a shell command and has been put back as it was. If that was your own edit, it is saved at " + $saved + ".")
    }'
    ;;
esac
