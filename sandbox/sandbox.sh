#!/usr/bin/env bash
# Launch the isolated project sandbox (every docker name is derived from the project directory).
#
#   ./sandbox.sh build          # (re)build the image
#   ./sandbox.sh                # start an interactive Claude session in the sandbox
#   ./sandbox.sh shell          # drop into a bash shell instead of Claude
#   ./sandbox.sh run <cmd...>   # run an arbitrary command in the sandbox
#   ./sandbox.sh stop           # stop & remove a running sandbox (one-click teardown)
#   ./sandbox.sh prune          # reclaim disk in the nested docker (build cache, images, volumes)
#
# A launch REPLACES any sandbox that is already running (they cannot coexist — same name, same port,
# same ~/.claude bind mount), stopping it only once the new image has been built. Every docker name is
# derived from the project directory (see SLUG below), so a DIFFERENT project's sandbox shares none of
# that state.
#
# Only the project directory is mounted from the host. No $HOME, no SSH keys,
# no other projects, and NOT the host Docker socket. The sandbox runs its own
# nested Docker daemon, so everything the project spins up (dev DB, Testcontainers,
# Playwright) lives and dies inside this disposable container.
set -euo pipefail

# This script lives in <project>/sandbox/, so the project root is its parent dir.
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${PROJECT_DIR:-$(dirname "${HERE}")}"
# Claude's own state (~/.claude — auth, history, memory) is bind-mounted from a plain directory INSIDE
# this sandbox folder (see CLAUDE_HISTORY_DIR below), not a named volume. That directory lives at a path
# under THIS checkout, so it is inherently scoped per checkout with no naming to get right — copying this
# launcher into another repo, or checking out the same repo twice, can never make two sandboxes share one
# ~/.claude by accident the way a name-derived volume could.
#
# EVERY docker name below is still DERIVED from the project directory, never hardcoded - the container,
# the image, the hostname and the four remaining CACHE volumes (docker/m2/gradle/pw). This launcher gets
# copied into other repos, and a hardcoded name travels with the copy: the copy mounts ITS OWN project at
# /work but attaches THE ORIGINAL project's cache volumes. Deriving the names makes a copy self-scoping on
# its first launch. Set SANDBOX_NAME to pin one explicitly (e.g. two checkouts of the SAME repo that must
# not share caches).
CLAUDE_HISTORY_DIR="${HERE}/.claude-history"
SLUG="$(basename "${PROJECT_DIR}")"
SLUG="${SLUG,,}"                     # docker image names must be lowercase
SLUG="${SLUG//[^a-z0-9_.-]/-}"       # ... and hold only [a-z0-9_.-]
[[ "${SLUG}" =~ ^[a-z0-9] ]] || SLUG="sandbox${SLUG}"  # ... and start alphanumeric
NAME="${SANDBOX_NAME:-${SLUG}}-sandbox"
IMAGE="${NAME}"
CONTAINER="${NAME}"
# Ports published to the HOST, space-separated `host:container` pairs - the other resource a copied
# launcher would collide on (two sandboxes cannot both hold the same host port). Unlike the volume
# names this one fails LOUDLY ("port is already allocated"), so it is a default rather than a derivation.
# THIS project runs no in-sandbox server, so the default publishes NOTHING - an unused published port is
# only a collision risk against the host's own stack. Set SANDBOX_PORTS to expose one.
read -ra PUBLISH_PORTS <<< "${SANDBOX_PORTS-}"
PUBLISH=()
for port in ${PUBLISH_PORTS+"${PUBLISH_PORTS[@]}"}; do PUBLISH+=(-p "${port}"); done
# Git Bash on Windows resolves paths as /c/Users/... but Docker Desktop needs C:\Users\...
if command -v cygpath &>/dev/null; then
  HERE="$(cygpath -w "${HERE}")"
  PROJECT_DIR="$(cygpath -w "${PROJECT_DIR}")"
fi

# The Claude Code version to bake into the image, read from the npm registry.
#
# Needed because the sandbox container is `--rm`: Claude's runtime self-update writes into the
# container's writable layer, which teardown deletes, so without this every launch comes back on the
# image's version, re-downloads the same update, and shows an "Update installed. Restart to apply"
# banner that restarting can never clear. Leaving the Dockerfile's install unpinned does NOT fix that
# either - an unchanged instruction is a cache hit, so the layer keeps serving whatever version it
# resolved months ago. Passing the resolved version as a build arg is what makes the layer rebuild
# when (and only when) a new release exists.
#
# Reports the version in CLAUDE_VERSION, left EMPTY when the registry cannot be reached or curl is
# absent (a global rather than an exit status, for the same reason REMOVED_EXISTING below is one: a
# function called as an `if` condition runs with `set -e` disabled). The caller then omits the build
# arg entirely, so the Dockerfile default applies, the layer stays cached and an offline build still
# works. Parsed with grep rather than jq because this half runs on the HOST, where jq is not a given
# (Git Bash on Windows especially) - the /latest document opens with `{"name":...,"version":"x.y.z"`,
# so the first match is the one wanted.
CLAUDE_PACKAGE_URL='https://registry.npmjs.org/@anthropic-ai/claude-code/latest'
CLAUDE_VERSION=""
resolve_claude_version() {
  local document
  CLAUDE_VERSION=""
  command -v curl >/dev/null 2>&1 || return 0
  document="$(curl -fsSL --max-time 10 "${CLAUDE_PACKAGE_URL}" 2>/dev/null)" || return 0
  CLAUDE_VERSION="$(printf '%s' "${document}" \
    | grep -o '"version"[[:space:]]*:[[:space:]]*"[^"]*"' \
    | head -n 1 \
    | cut -d'"' -f4)"
}

build() {
  local uid gid
  uid="$(id -u)"
  gid="$(id -g)"

  local claude_arg=()
  resolve_claude_version
  if [[ -n "${CLAUDE_VERSION}" ]]; then
    echo "[sandbox] baking Claude Code ${CLAUDE_VERSION} into the image" >&2
    claude_arg=(--build-arg CLAUDE_CODE_VERSION="${CLAUDE_VERSION}")
  else
    echo "[sandbox] WARN: could not reach the npm registry - keeping the cached Claude Code install" >&2
  fi

  docker build -t "${IMAGE}" \
    --build-arg UID="${uid}" \
    --build-arg GID="${gid}" \
    ${claude_arg[@]+"${claude_arg[@]}"} \
    "${HERE}"
}

# Stop and remove whatever container is already holding our name, and wait until the name is actually
# free again. Reports what it did in REMOVED_EXISTING (1 = removed something, 0 = there was nothing) —
# a variable rather than an exit status, because a function called as an `if`/`||` condition runs with
# `set -e` disabled, which the docker calls in here should not.
#
# Two sandboxes CANNOT coexist: they share the container name, the published port and — worst of all —
# the persisted state, including /home/dev/.claude (bind-mounted from CLAUDE_HISTORY_DIR), whose
# login/session state Claude rewrites in place. Starting a second one while the first is up therefore
# takes BOTH down. So a launch does not compete
# with the running sandbox, it replaces it: the old one is killed here, deliberately AFTER build() has
# finished, so the outgoing session stays usable for the whole rebuild and the gap between the two is
# only the teardown itself.
REMOVED_EXISTING=0
remove_existing() {
  local existing waited=0
  REMOVED_EXISTING=0
  existing="$(docker ps -aq -f "name=^${CONTAINER}$")"
  if [[ -z "${existing}" ]]; then
    return 0
  fi
  REMOVED_EXISTING=1

  echo "[sandbox] stopping the existing ${CONTAINER} container..." >&2
  # `stop` first, with the same -t 10 grace as the teardown trap below (so the outgoing Claude still gets
  # to flush ~/.claude/.claude.json cleanly), then `rm -f` for a container that was NOT started with --rm
  # and would otherwise linger in `exited` state, still owning the name.
  docker stop -t 10 "${existing}" >/dev/null 2>&1 || true
  docker rm -f "${existing}" >/dev/null 2>&1 || true

  # `--rm` removal is asynchronous in the daemon: the name can stay taken for a moment after the client
  # exits, and `docker run --name` fails outright ("name is already in use") if we race it. Poll until the
  # name really is free rather than guessing at a sleep.
  while true; do
    existing="$(docker ps -aq -f "name=^${CONTAINER}$")"
    if [[ -z "${existing}" ]]; then
      return 0
    fi
    if (( waited >= 30 )); then
      echo "Timed out waiting for the existing ${CONTAINER} container to be removed." >&2
      exit 1
    fi
    sleep 1
    waited=$(( waited + 1 ))
  done
}

# Stop the container THIS launcher started, identified by the id docker wrote to the cidfile — never by
# name. Because remove_existing hands the name from an outgoing sandbox to an incoming one, a name-based
# teardown would let a departing launcher stop the container that replaced it.
#
# The path is a GLOBAL, set by run(). An EXIT trap fires in whatever scope the shell is in when it leaves:
# on the signal paths that is still inside run() (the INT/TERM trap's `exit`), but on a normal return it is
# the top level, where a `local` of run()'s is long out of scope — under `set -u` the trap body then dies
# with "cidfile: unbound variable" before it can stop anything or clean the file up.
CIDFILE=""
stop_own() {
  local cidfile="$1" cid=""
  if [[ -z "${cidfile}" ]]; then
    return 0
  fi
  if [[ -s "${cidfile}" ]]; then
    cid="$(<"${cidfile}")"
  fi
  if [[ -n "${cid}" ]]; then
    docker stop -t 10 "${cid}" >/dev/null 2>&1 || true
  fi
  rm -f "${cidfile}"
}

run() {
  if [[ ! -d "${PROJECT_DIR}" ]]; then
    echo "Project directory not found: ${PROJECT_DIR}" >&2
    exit 1
  fi

  # Always (re)build before launching so every session runs the latest image. Docker's layer
  # cache makes this a near-instant no-op when nothing in the build context has changed.
  echo "[sandbox] building ${IMAGE} before launch..." >&2
  build

  # Only now (image ready, downtime minimised) take the name off any sandbox that is already running.
  remove_existing

  # Allocate a TTY only when attached to one (so scripted `run` invocations work too).
  local tty=()
  if [[ -t 0 ]] && [[ -t 1 ]]; then tty=(-it); else tty=(-i); fi

  # Tie the container's lifetime to THIS launcher. `docker run --rm` removes the container only when it
  # *exits*; if the client is killed — IntelliJ stops the run configuration, or the terminal/console
  # is closed — the container would otherwise keep running in the daemon. So stop it whenever we leave.
  # Running `docker run` as `… & wait` (not in the foreground) is what lets the trap fire *immediately*
  # on the signal: a foreground command defers traps until it returns, by which point IntelliJ may have
  # already escalated to SIGKILL. With job control off (a script), the background-ed client stays in the
  # foreground process group, so the interactive TTY keeps working.
  #
  # BUT: in a non-interactive shell (job control off — exactly how IntelliJ's Shell Script config and
  # `bash sandbox.sh` invoke us), POSIX reassigns a background-ed command's stdin to /dev/null *unless it
  # is explicitly redirected*. Without that explicit redirect, `docker run -it … &` would see a
  # non-terminal stdin and fail with "cannot attach stdin to a TTY-enabled container". So save the real
  # stdin on fd 3 and feed it back into the background-ed client with `<&3`, which suppresses the
  # /dev/null default and keeps the PTY attached.
  # `-t 10` (not a tighter grace) gives Claude time to finish its atomic rewrite of
  # ~/.claude/.claude.json on SIGTERM before docker SIGKILL it; too short a grace
  # interrupts that rename and loses the login/onboarding state (launch.sh restores
  # it from backup as a safety net, but a clean flush is better than relying on it).
  #
  # The container is identified for teardown by the id docker writes to --cidfile (see stop_own), not by
  # name. `mktemp -u` because docker refuses to start if the cidfile already exists.
  CIDFILE="$(mktemp -u "${TMPDIR:-/tmp}/${CONTAINER}.cid.XXXXXX")"
  exec 3<&0
  trap 'stop_own "${CIDFILE}"' EXIT
  trap 'exit' INT TERM HUP

  # Nothing is published to the host by default - see SANDBOX_PORTS at the top.
  #
  # The Maven local repository, the Gradle cache and the Playwright browser cache get named volumes for
  # the same reason the Docker data dir does: without one they live in the container's writable layer,
  # which --rm deletes on teardown, so every fresh sandbox re-downloads the lot. A named volume — rather
  # than a bind to the host's ~/.m2 or ~/.gradle — keeps the "no $HOME from the host" rule above intact
  # while still persisting across sessions. The image pre-creates /home/dev/.m2 and /home/dev/.gradle
  # dev-owned so the volumes are writable (see the Dockerfile's user-creation block). THIS project is
  # Gradle/Android, not Maven, so the -m2 volume simply stays empty; it is kept so the launcher still
  # works unchanged when copied elsewhere. -gradle is the one that matters here: it holds the Gradle
  # distribution itself plus every AGP/Kotlin/AndroidX/Compose dependency, so a second launch does not
  # redownload them.
  #
  # /home/dev/.claude is different: it is a BIND MOUNT to CLAUDE_HISTORY_DIR, a plain directory inside
  # this sandbox folder, not a named (opaque, `docker volume`-only) volume like the four above. That
  # keeps Claude's auth, session transcripts and memory fully contained in the project checkout — visible
  # with a normal `ls`/`cp`/`tar`, backed up by copying a directory, wiped with `rm -rf`, and inherently
  # scoped to this one checkout with no name to collide on. mkdir it first: an unprivileged bind mount
  # source must already exist, or the daemon creates it root-owned and the in-container chown (see
  # entrypoint.sh) becomes the only thing standing between a fresh sandbox and a permission error.
  mkdir -p "${CLAUDE_HISTORY_DIR}"
  docker run "${tty[@]}" --rm \
    --name "${CONTAINER}" \
    --cidfile "${CIDFILE}" \
    --privileged \
    --hostname "${NAME}" \
    -v "${PROJECT_DIR}":/work \
    -v "${NAME}-docker":/var/lib/docker \
    -v "${CLAUDE_HISTORY_DIR}":/home/dev/.claude \
    -v "${NAME}-m2":/home/dev/.m2 \
    -v "${NAME}-gradle":/home/dev/.gradle \
    -v "${NAME}-pw":/home/dev/.cache/ms-playwright \
    ${PUBLISH[@]+"${PUBLISH[@]}"} \
    "${IMAGE}" "$@" <&3 &
  wait $!
}

# Reclaim disk inside the NESTED docker daemon - the `-docker` volume, by far the largest of the four
# (image layers and BuildKit cache from every gate run land there). The GC policy baked into the image
# (`daemon.json`) caps the build cache on its own; this is the manual, immediate version, and it also
# clears the images/containers/volumes that no policy covers.
#
# Prefers `docker exec` into a RUNNING sandbox so a live Claude session is never interrupted; only
# starts a container when none is up. Runs as root inside, since `dev` is not in the container's
# docker group.
PRUNE_CMD='docker system df; docker builder prune -af --max-used-space=20GB; docker image prune -af --filter until=336h; docker container prune -f; docker volume prune -f; echo; docker system df'
prune() {
  if docker inspect "${CONTAINER}" >/dev/null 2>&1; then
    echo "[sandbox] pruning inside the running ${CONTAINER} (session untouched)..."
    docker exec -u root "${CONTAINER}" bash -c "${PRUNE_CMD}"
  else
    echo "[sandbox] no ${CONTAINER} running - starting one to prune..."
    run sudo bash -c "${PRUNE_CMD}"
  fi
}

stop() {
  # Stopping the container triggers the running launcher's --rm + trap teardown (nested dockerd and
  # everything it spun up dies with it). A no-op if nothing is there.
  remove_existing
  if (( REMOVED_EXISTING == 1 )); then
    echo "Stopped ${CONTAINER}."
  else
    echo "No ${CONTAINER} container to stop."
  fi
}

case "${1:-}" in
  build) build ;;
  stop)  stop ;;
  prune) prune ;;
  shell) shift; run bash ;;
  run)   shift; run "$@" ;;
  "")    run ;;            # default: interactive claude (entrypoint default)
  *)     run "$@" ;;
esac
