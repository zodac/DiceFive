#!/usr/bin/env bash
# Runs as root (needed to start the nested dockerd), then drops to the `dev`
# user to run whatever command was passed (default: an interactive claude session).
set -euo pipefail

# ── Start the nested Docker daemon ───────────────────────────────────────────
# Lives entirely inside this container; its containers/images/volumes are wiped
# when the sandbox is removed. Never touches the host's /var/run/docker.sock.
echo "[sandbox] starting nested dockerd..."
dockerd \
  --host=unix:///var/run/docker.sock \
  >/var/log/dockerd.log 2>&1 &

# Wait for it to come up.
tries=0
until docker info >/dev/null 2>&1; do
  tries=$((tries + 1))
  if [[ "${tries}" -gt 60 ]]; then
    echo "[sandbox] dockerd failed to start; last log lines:" >&2
    tail -n 30 /var/log/dockerd.log >&2 || true
    exit 1
  fi
  sleep 0.5
done
echo "[sandbox] nested dockerd is up."

# ── Hand the persisted mount points to `dev` ─────────────────────────────────
# Docker mounts named volumes root-owned, and a bind-mounted host directory can
# also land root-owned if it didn't already exist when the mount was set up.
# chown the mount points (non-recursive, so existing contents are untouched) so
# the dev user can write into them. /home/dev/.claude is a bind mount to a
# project-local directory (see sandbox.sh); the other two are named volumes.
mkdir -p /home/dev/.cache/ms-playwright
chown dev:dev /home/dev/.claude /home/dev/.cache /home/dev/.cache/ms-playwright 2>/dev/null || true

# ── Drop to the unprivileged user for the actual work ────────────────────────
# If no command was given, start an interactive Claude session with permissions
# skipped (safe: the sandbox is disposable and only /work is mounted from host).
if [[ "$#" -eq 0 ]]; then
  set -- claude --dangerously-skip-permissions
fi

# Run the per-open setup only for interactive sessions (a TTY on stdin), so quick
# scripted `./sandbox.sh run <cmd>` invocations aren't slowed by the setup checks.
if [[ -t 0 ]]; then RUN_SETUP=1; else RUN_SETUP=0; fi
export RUN_SETUP

# JAVA_HOME/MAVEN_HOME/ANDROID_HOME/ANDROID_SDK_ROOT must be preserved explicitly too:
# `sudo` resets the environment by default, and PATH alone (assigned directly below,
# same as HOME) is not enough for a tool that reads one of these as a variable rather
# than just finding its binary via PATH — AGP is exactly that case, and fails with
# "SDK location not found" if ANDROID_HOME does not survive this hop.
cd /work
exec sudo -u dev \
  --preserve-env=ANTHROPIC_API_KEY,CLAUDE_CONFIG_DIR,PLAYWRIGHT_BROWSERS_PATH,RUN_SETUP,JAVA_HOME,MAVEN_HOME,ANDROID_HOME,ANDROID_SDK_ROOT \
  HOME=/home/dev PATH="${PATH}" /usr/local/bin/sandbox-launch.sh "$@"
